/**
 * SICX OSS Gateway, Multi-Cloud Storage software. 
 * Copyright (C) 2012 Helsinki Institute of Physics, University of Helsinki
 * All rights reserved. See the copyright.txt in the distribution for a full 
 * listing of individual contributors.
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * 
 */
package fi.hip.sicxoss.io;

import java.io.*;
import java.security.*;
import java.net.*;
import java.util.*;
import java.nio.channels.*;

import org.apache.log4j.Logger;

import fi.hip.sicxoss.ident.*;
import fi.hip.sicxoss.model.*;
import fi.hip.sicxoss.util.*;
import fi.hip.sicxoss.*;
import fi.hip.sicxoss.io.message.*;

/**
 * ConnectionManager
 *
 * Manages the connections for a single local user
 * @author koskela
 */
public class ConnectionManager 
    extends Thread 
    implements AuthenticatedSocketHandler.ConnectionReconnector {

    private static final Logger log = Logger.getLogger(ConnectionManager.class);

    private LocalUser localUser;
    private NetworkEngine engine;
    private LocalGateway gw;
    
    private Hashtable<DataSocketHandler, Long> rescheduledHandlers;
    private long nextWakeUp;
    private Hashtable<String, CMLookupSocketHandler> lookupHandlers;

    // these maintain the connections with the remote peers
    private Hashtable<String, ContactConnectionManager> contacts;

    // for incoming connections
    private CMServerSocketHandler ssHandler;

    // the observers
    private List<ConnectionManagerObserver> observers;

    public ConnectionManager(LocalUser user, LocalGateway gw) {
        this.localUser = user;
        this.gw = gw;
        this.engine = gw.getNetworkEngine();
        this.rescheduledHandlers = new Hashtable();
        this.nextWakeUp = -1;
        this.lookupHandlers = new Hashtable();
        this.contacts = new Hashtable();
        this.observers = new ArrayList();
        engine.addClient(this);
        setDaemon(true);
    }

    /** connection manager observer */
    public interface ConnectionManagerObserver {
        // lookup service go up, down and around
        public void lookupServerState(CMLookupSocketHandler lookupServer, boolean online);
    }

    public void addObserver(ConnectionManagerObserver obs) {
        observers.add(obs);
    }

    /** the peer connection listener */
    public class CMServerSocketHandler 
        extends ServerSocketHandler {

        private ConnectionManager cm;

        public CMServerSocketHandler(ConnectionManager cm) 
            throws Exception {
            super();
            this.cm = cm;
            // we could do some sort of upnp magic, if we wanted..
        }

        public void gotAccept(SocketChannel sc) {
            log.info("we accept peer connection!");
            try {
                CMSocketHandler sh = new CMSocketHandler(sc, cm);
                eng.addHandler(sh);
                sh.startAuthentication();
            } catch (Exception ex) {
                log.warn("error accepting socket: " + ex);
            }
        }
    }

    /** the peer connection handler */
    public class CMSocketHandler 
        extends AuthenticatedSocketHandler {

        private ConnectionManager connMan;

        public CMSocketHandler(String str, User remote, ConnectionManager connMan) 
            throws Exception {
            super(str, connMan.getLocalUser(), remote);
            this.connMan = connMan;
        }

        public CMSocketHandler(SocketChannel sc, ConnectionManager connMan) 
            throws Exception {
            super(sc, connMan.getLocalUser(), null);
            this.connMan = connMan;
        }

        public void gotAuthenticatedDataStream(DataInputStream in)
            throws Exception {

            connMan.getContactConnectionManager(getRemoteUser()).handleDataStream(in, this);
        }

        public void authenticationComplete(boolean success) {
            log.info("peer authentication was completed: " + success);
            if (success) {
                log.info("remote user is: " + getRemoteUser());

                // now we need to check whether to trust the user!
                if (!localUser.contactManager().isTrusted(getRemoteUser())) {
                    log.warn("got a connection from someone we don't really trust!");
                    try { 
                        authError();
                    } catch (Exception ex) {
                        log.warn("error while trying to kill the connection: " + ex);
                    }
                } else {
                    connMan.getContactConnectionManager(getRemoteUser()).directConnectionEstablished(this);
                    
                    // reschedule only if we have successfully connected to this once
                    setReconnectTiming(new int[] { 20000, 20000, 60000 }, connMan);
                }
            } else if (getRemoteUser() != null) {
                // this may be an incoming from an untrusted. do not create a ccm!
                // (that would indicate that we trust him)
                ContactConnectionManager ccm = contacts.get(getRemoteUser().getId());
                if (ccm != null)
                    ccm.directConnectionFailed(this);
                else
                    log.warn("connection from " + getRemoteUser() + " failed!");
            } else {
                log.warn("anon connection failed!");
            }
        }
    }

    /** peer state manager */
    public class ContactConnectionManager {

        private Hashtable<ShareID, ShareModel> shares;
        private User contact;
        private ConnectionManager connMan;
        private Hashtable<String, CMSocketHandler> sockets;
        private List<CMLookupSocketHandler> activeLookups;
        private boolean hadLookupConnection;
        private boolean hadDirectConnection;

        public ContactConnectionManager(User user, ConnectionManager connMan) {
            this.connMan = connMan;
            sockets = new Hashtable();
            shares = new Hashtable();
            activeLookups = new ArrayList();
            hadLookupConnection = false;
            hadDirectConnection = false;
            contact = user;
            updateContactInfo(user, null);
        }

        public MessageSocketHandler getConnection(boolean requireDirect) {

            for (CMSocketHandler cmsh : sockets.values())
                if (cmsh.isConnected() && cmsh.getRemoteUser() != null)
                    return cmsh;
            if (requireDirect)
                return null;

            // if direct is not available, choose one of the lookups
            // where he has been seen
            for (CMLookupSocketHandler lsh : activeLookups)
                if (lsh.isConnected())
                    return lsh;

            return null;
        }

        public DataOutputStream sendDataStream(boolean requireDirect)
            throws Exception {

            for (CMSocketHandler cmsh : sockets.values())
                if (cmsh.isConnected() && cmsh.getRemoteUser() != null)
                    return cmsh.sendDataStream();
            if (requireDirect)
                return null;
        
            // create some sort of encapsulating data stream to the lookup!
            for (CMLookupSocketHandler lu : activeLookups) {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    DataOutputStream dos = lu.sendForwardingDataStream(contact, connMan.getLocalUser());
                    return dos;
                } catch (Exception ex) {
                    log.warn("error whlie creating the forwarding packet stream: " + ex);
                }
            }
            return null;
        }

        public synchronized void handleDataStream(DataInputStream in, AuthenticatedSocketHandler conn)
            throws Exception {

            String cmd = in.readUTF();
            log.info("got peer msg type: " + cmd);

            DataOutputStream dos = null;
            NetworkMessage.MessageType type = NetworkMessage.MessageType.valueOf(cmd);
            switch (type) {

                /* not applicable .. i guess. until v2.0
                   case FORWARD: {
                   break;
                   }
                */

            case INVITE: {

                // ok.... 
                String sid = in.readUTF();
                String name = in.readUTF();
                String description = in.readUTF();
                ShareID shareid = ShareID.fromString(sid);
                connMan.getLocalUser().shareInviteGot(contact, shareid, name, description);
                break;
            }

            case INVITE_RESPONSE: {

                String sid = in.readUTF();
                String verdict = in.readUTF();
                boolean accept = Boolean.valueOf(verdict).booleanValue();
                
                log.debug("the invite was responded to with " + verdict + ", which means " + accept);
                // ok.. find the share and do the magic
                ShareID shareId = ShareID.fromString(sid);
                ShareModel share = shares.get(shareId);
                if (share != null) {
                    share.inviteResponseGot(contact, accept);
                } else
                    log.warn("got a response to an invite of an unknown share");
                break;
            }

            case STREAM_START: {

                String to = in.readUTF();
                String from = in.readUTF();
                String sid = in.readUTF();
                String did = in.readUTF();
                long start = in.readLong();
                long finish = in.readLong();

                ShareID shareId = ShareID.fromString(sid);
                ShareModel share = shares.get(shareId);
                DataID dataId = DataID.parse(did);

                if (share == null || !share.dataStreamGot(contact, dataId, start, finish, conn)) {
                    log.warn("got a " + type + " to an unknown share (" + shareId + ") or data!");

                    // discard the stream
                    conn.drainStream(finish - start);
                }
                break;
            }

            case EVENT:
            case DATA_BLOCK:
            case DATA_REQUEST:
            case DATA_RESPONSE:
            case DATA_QUERY:
            case SYNC_NOTIFY:
            case SYNC: {
                
                /* all share-related operations. except streaming */

                String sid = in.readUTF();
                ShareID shareId = ShareID.fromString(sid);
                ShareModel share = shares.get(shareId);

                if (share == null) {
                    log.warn("got a " + type + " to an unknown share (" + shareId + ")");
                } else {
                    switch (type) {
                    case SYNC_NOTIFY:
                    case SYNC: {
                        while (in.available() > 0) {
                            String uid = in.readUTF();
                            String eventid = in.readUTF();
                            EventID eid = EventID.fromString(eventid);
                            share.syncGot(contact, uid, eid, type == NetworkMessage.MessageType.SYNC);
                        }
                        break;
                    }
                    case DATA_QUERY: {
                        String did = in.readUTF();
                        DataID dataId = DataID.parse(did);
                        long start = in.readLong();
                        long finish = in.readLong();
                        share.dataQueryGot(contact, dataId, start, finish);
                        break;
                    }
                    case DATA_RESPONSE: {
                        String did = in.readUTF();
                        DataID dataId = DataID.parse(did);
                        long start = in.readLong();
                        long finish = in.readLong();
                        share.dataResponseGot(contact, dataId, start, finish);
                        break;
                    }
                    case DATA_REQUEST: {
                        String did = in.readUTF();
                        DataID dataId = DataID.parse(did);
                        long start = in.readLong();
                        long finish = in.readLong();
                        share.dataRequestGot(contact, dataId, start, finish);
                        break;
                    }
                    case DATA_BLOCK: {
                        String did = in.readUTF();
                        DataID dataId = DataID.parse(did);
                        long start = in.readLong();
                        long finish = in.readLong();
                        share.dataBlockGot(contact, dataId, start, finish, in);
                        break;
                    }
                    case EVENT: {
                        List<Event> events = new ArrayList();
                        while (in.available() > 0) {
                            int l = in.readShort();
                            byte[] data = new byte[l];
                            in.readFully(data);
                            Event e = new Event(data);
                            events.add(e);
                        }
                        share.importEvents(events, contact);
                        log.info("read " + events.size() + " events!");
                        break;
                    }
                    default:
                    }
                }
                break;
            }
            default:
                log.warn("got invalid message type: " + type);
            }
            
            if (dos != null)
                dos.close();
        }

        // called when the contact has been disconnected from the
        // lookup or we disconnect from the lookup
        public void contactDisconnect(CMLookupSocketHandler lookup) {

            log.info("we got contact disconnect from the lookup server!");
            activeLookups.remove(lookup);
            updateStatusToShares();
        }

        private void updateStatusToShares() {

            /* we notify the shares only when we have something new to report */
            boolean hasl = hasLookupConnection();
            boolean hasd = hasDirectConnection();
            if (hasl != hadLookupConnection ||
                hasd != hadDirectConnection) {
                for (ShareModel sm : shares.values()) {
                    sm.contactStatusChanged(contact, hasl, hasd);
                }
                hadLookupConnection = hasl;
                hadDirectConnection = hasd;
            }
        }

        public void updateContactInfo(User user, CMLookupSocketHandler lookup) {
            
            log.debug("updating contact info for " + user);
            connMan.getLocalUser().contactManager().addKey(user);
            
            // check which one is more current ..
            Date oldc = DataUtil.stringToDate(contact.getProperty("modified"));
            Date newc = DataUtil.stringToDate(user.getProperty("modified"));
            
            if (oldc.before(newc))
                contact = user;
            else
                log.warn("but we already have a newer version of it!");

            if (lookup != null && !activeLookups.contains(lookup))
                activeLookups.add(lookup);
                
            // notify the shares
            updateStatusToShares();
            
            // if we don't have a connection, but have the ip address
            // etc, try to establish one!
            String addr = contact.getProperty("address");
            if (addr != null) {
                log.info("we should try and connect to " + addr + " if we haven't already!");
                
                // check if we have a connection to him & that address already
                // .. or have a connection which is pending.
                // .. and establish only if we have shares with him!
                if (/*!sockets.containsKey(addr) &&*/ !hasDirectConnection()
                    && shares.size() > 0) {

                    // clear out the old connections
                    for (String k : sockets.keySet()) {
                        CMSocketHandler cmsh = sockets.get(k);
                        cmsh.close();
                        cmsh.cancel();
                    }
                    sockets.clear();

                    try {
                        CMSocketHandler cmsh = new CMSocketHandler(addr, user, connMan);
                        sockets.put(addr, cmsh);
                        engine.addHandler(cmsh);
                    } catch (Exception ex) {
                        log.error("error connecting to " + user + " using address " + addr);
                    }
                }
            } else
                log.debug("no address included in update!");
        }
        
        public synchronized void directConnectionEstablished(CMSocketHandler cmsh) {
            log.info("direct connection established!");
            if (!sockets.contains(cmsh))
                sockets.put(cmsh.getRemoteAddressAsString(), cmsh);
            
            updateStatusToShares();
        }

        public synchronized void directConnectionFailed(CMSocketHandler cmsh) {
            log.info("direct connection failed!");
            updateStatusToShares();
        }

        public boolean hasDirectConnection() {
            for (CMSocketHandler cmsh : sockets.values()) {
                if (cmsh.isConnected() && cmsh.getRemoteUser() != null)
                    return true;
            }
            return false;
        }

        public boolean hasLookupConnection() {
            return activeLookups.size() > 0;
        }
        
        public boolean addShare(ShareModel share) {

            if (!shares.containsKey(share.getId())) {
                shares.put(share.getId(), share);

                // .. and try to establish a connection to this contact!
                if (shares.size() == 1)
                    updateContactInfo(contact, null);

                // if we are connected, then notify the new share!
                share.contactStatusChanged(contact, hasLookupConnection(), hasDirectConnection());
                return true;
            } else
                return false;
        }

        public boolean removeShare(ShareModel share) {

            // if no shares left, disconnect any connections we have

            if (shares.containsKey(share.getId())) {
                shares.remove(share.getId());
                
                // ..and if no more shares with this one, disconnect from him..
                // if we have an active connection.
                return true;
            } else
                return false;
        }

        public boolean hasShare(ShareModel share) {
            return shares.containsKey(share.getId());
        }

        public User getContact() {
            return contact;
        }
    }

    public void run() {
        log.info("starting the connection manager for " + localUser);

        // start the server socket
        try {
            this.ssHandler = new CMServerSocketHandler(this);
            engine.addHandler(this.ssHandler);
        } catch (Exception ex) {
            log.error("error initializing server socket: " + ex);
        }

        // connect to the lookup server(s) & maintain and monitor those connections
        while (true) {
            try {
                loop();
            } catch (Exception ex) {
                log.warn("error while looping: " + ex);
                //ex.printStackTrace();
                if (nextWakeUp < 0)
                    nextWakeUp = System.currentTimeMillis() + 1000;
            }

            try {
                synchronized (rescheduledHandlers) {
                    long now = System.currentTimeMillis();
                    if (nextWakeUp < 0)
                        rescheduledHandlers.wait();
                    else if (nextWakeUp > now)
                        rescheduledHandlers.wait(nextWakeUp - now);
                }
            } catch (Exception ex) {
                log.warn("error while waiting: " + ex);
            }
        }
    }

    public LocalUser getLocalUser() {
        return localUser;
    }

    public User getLocalRegistrationUser() 
        throws Exception {
        
        // create copy .. add all sorts of network interfaces etc.
        User ret = localUser.publicCopy();
        
        if (ssHandler != null) {
            InetSocketAddress sa = new InetSocketAddress(InetAddress.getLocalHost(), ssHandler.getLocalAddress().getPort());
            ret.setProperty("address", sa.getAddress().getHostAddress() + ":" + sa.getPort());
        }
        ret.setProperty("modified", DataUtil.dateToString(new Date()));
        localUser.sign(ret);
        return ret;
    }

    private void loop()
        throws Exception {

        String ls = gw.getConfig("lookup.server");
        CMLookupSocketHandler lookupConn = lookupHandlers.get(ls);
        if (lookupConn == null) {
            try {
                lookupConn = new CMLookupSocketHandler(ls, this);
                lookupHandlers.put(ls, lookupConn);
                engine.addHandler(lookupConn);
            } catch (Exception ex) {
                log.warn("error while trying to connect to the lookup server: " + ex);
            }
        }

        ArrayList<DataSocketHandler> wakeups = new ArrayList();
        synchronized (rescheduledHandlers) {
            long now = System.currentTimeMillis();
            long next = -1;
            for (DataSocketHandler sh : rescheduledHandlers.keySet()) {
                long wut = rescheduledHandlers.get(sh);
                if (wut < now) {
                    wakeups.add(sh);
                } else if (next == -1 || wut < next)
                    next = wut;
            }

            for (DataSocketHandler sh : wakeups)
                rescheduledHandlers.remove(sh);

            nextWakeUp = next;
        }
        for (DataSocketHandler sh : wakeups)
            try {
                sh.reconnect();
            } catch (Exception ex) {
                log.warn("error while trying to reconnect: " + ex);
            }
    }

    @Override
    public void reschedule(AuthenticatedSocketHandler sh, long millis) {
        synchronized (rescheduledHandlers) {
            rescheduledHandlers.put(sh, new Long(millis));
            if (nextWakeUp < 0 || millis < nextWakeUp)
                nextWakeUp = millis;
            rescheduledHandlers.notify();
        }
    }

    @Override
    public void cancelReschedule(AuthenticatedSocketHandler sh) {
        synchronized (rescheduledHandlers) {
            rescheduledHandlers.remove(sh);
        }
    }

    public class CMLookupSocketHandler 
        extends AuthenticatedSocketHandler {

        private ConnectionManager connMan;
        private boolean lastState;

        public CMLookupSocketHandler(String str, ConnectionManager connMan) 
            throws Exception {
            super(str, connMan.getLocalUser(), null);
            lastState = false;
            this.connMan = connMan;
            setAcceptAnon(true);
            // when to try and reconnect if the connection fails
            setReconnectTiming(new int[] { 1000, 1000, 1000, 2000, 4000, 10000,
                                           20000, 20000, 60000 }, connMan);
        }

        public void gotConnected() {
            log.info("we connect!");
            startAuthentication();
        }

        public void gotAuthenticatedDataStream(DataInputStream in)
            throws Exception {

            String cmd = in.readUTF();
            log.warn("processing msg type: " + cmd);

            DataOutputStream dos = null;
            NetworkMessage.MessageType type = NetworkMessage.MessageType.valueOf(cmd);
            switch (type) {

            case STREAM_START: {

                String to = in.readUTF();
                String from = in.readUTF();
                String sid = in.readUTF();
                String did = in.readUTF();
                long start = in.readLong();
                long finish = in.readLong();
                
                if (to.equals(connMan.getLocalUser().getId().toString())) {
                    User ru = connMan.getLocalUser().contactManager().findUser(from);
                    if (ru != null) { 
                        ContactConnectionManager ccm = connMan.getContactConnectionManager(ru);
                        in = new DataInputStream(new ByteArrayInputStream(((MessageSocketHandler.MessageDataInputStream)in).getData()));
                        ccm.handleDataStream(in, this);
                    } else
                        log.warn("got a forwarded packet from someone we do not know: " + from);
                } else {
                    log.warn("got a forwarded packet for someone else ("+to+") than me ("+connMan.getLocalUser().getId().toString()+")");
                }
                break;
            }

            case FORWARD: {
                String to = in.readUTF();
                String from = in.readUTF();
                if (to.equals(connMan.getLocalUser().getId().toString())) {
                    // we only accept stuff from people we know!
                    User ru = connMan.getLocalUser().contactManager().findUser(from);
                    if (ru != null) { 
                        ContactConnectionManager ccm = connMan.getContactConnectionManager(ru);
                        ccm.handleDataStream(in, this);
                    } else
                        log.warn("got a forwarded packet from someone we do not know: " + from);
                } else {
                    log.warn("got a forwarded packet for someone else ("+to+") than me ("+connMan.getLocalUser().getId().toString()+")");
                }
                break;
            }

            case NETWORK_MIRROR: {
                while (in.available() > 0) {
                    String addr = in.readUTF();
                    log.debug("read inet address: " + addr);
                }
                break;
            }

            case CONTACT_UPDATE: {
                while (in.available() > 0) {
                    String cupdate = in.readUTF();
                    log.debug("read contact update: " + cupdate);
                    try {
                        User u = User.fromData(cupdate);
                        ContactConnectionManager ccm = contacts.get(u.getId());
                        if (getLocalUser().contactManager().isTrusted(u) && ccm != null) {
                            ccm.updateContactInfo(u, this);
                        } else
                            log.warn("got contact update for someone we don't care for");
                    } catch (Exception ex) {
                        log.warn("exception while processing contact update: " + ex);
                    }
                }

                // store those somewhere, and 
                break;
            }

            case CONTACT_DISCONNECT: {
                String cid = in.readUTF();
                log.debug("got contact disconnect: " + cid);
                ContactConnectionManager ccm = contacts.get(cid);
                if (ccm != null) {
                    // todo: here we are assuming only ONE lookup
                    // server connection, which might not be the case
                    ccm.contactDisconnect(this);
                }
                break;
            }

            default:
                log.warn("got invalid message type: " + type);
            }
            
            if (dos != null)
                dos.close();
        }

        public boolean sendAddContact(User contact) {

            log.info("sending 'add contact' for " + contact);
            try {
                DataOutputStream dos = sendDataStream();
                dos.writeUTF(NetworkMessage.MessageType.ADD_CONTACTS.toString());
                dos.writeUTF(contact.getId());
                dos.close();
                return true;
            } catch (Exception ex) {
                log.warn("error while sending contact: " +ex);
                close();
            }
            return false;
        }

        public boolean sendRemoveContact(User contact) {

            try {
                DataOutputStream dos = sendDataStream();
                dos.writeUTF(NetworkMessage.MessageType.REMOVE_CONTACTS.toString());
                dos.writeUTF(contact.getId());
                dos.close();
                return true;
            } catch (Exception ex) {
                log.warn("error while sending contact: " +ex);
                close();
            }
            return false;
        }

        public void authenticationComplete(boolean success) {
            log.info("authentication was completed: " + success);
            if (success) {
                log.info("remote user is: " + getRemoteUser());
                
                // perform a REGISTER
                try {
                    User u = connMan.getLocalRegistrationUser();
                    DataOutputStream dos = sendDataStream();
                    dos.writeUTF(NetworkMessage.MessageType.REGISTER.toString());
                    dos.writeUTF(u.getData());
                    dos.close();
                } catch (Exception ex) {
                    log.warn("error while registering: " +ex);
                    close();
                }

                // send contacts..
                try {
                    // only those with which we have shares!
                    DataOutputStream dos = sendDataStream();
                    dos.writeUTF(NetworkMessage.MessageType.ADD_CONTACTS.toString());
                    for (String uid : connMan.getContactIds()) {
                        dos.writeUTF(uid);
                    }
                    dos.close();
                } catch (Exception ex) {
                    log.warn("error while sending contacts: " +ex);
                    success = false;
                    close();
                }
            } else {
                for (ContactConnectionManager ccm : contacts.values())
                    ccm.contactDisconnect(this);
            }

            // call on the connectionobservers..
            if (success != lastState) {
                for (ConnectionManagerObserver cmo : connMan.observers)
                    try { cmo.lookupServerState(this, success); } catch (Exception ex) { log.warn("observer failed: " + ex); }                
                lastState = success;
            }
        }
    }

    public synchronized Set<String> getContactIds() {
            return contacts.keySet();
    }

    /**
     * Adds a user to the connection management.  The
     * ConnectionManager should now add this user to the people with
     * which to maintain a sync relationship for the given share
     */
    public synchronized void addContact(User user, ShareModel share) {
        log.info("should add user " + user + " to share " + share);
 
        if (getContactConnectionManager(user).addShare(share)) {
            for (CMLookupSocketHandler lc : lookupHandlers.values())
                lc.sendAddContact(user);
        }
    }
    
    public void removeContact(User user, ShareModel share) {
        log.info("should remove user " + user + " from share " + share);
        
        if (getContactConnectionManager(user).removeShare(share)) {
            for (CMLookupSocketHandler lc : lookupHandlers.values())
                lc.sendRemoveContact(user);
        }
    }

    protected synchronized ContactConnectionManager getContactConnectionManager(User user) {
        ContactConnectionManager ccm = contacts.get(user.getId());
        if (ccm == null) {
            user = localUser.addContact(user, null);
            ccm = new ContactConnectionManager(user, this);
            contacts.put(user.getId(), ccm);
        }
        return ccm;
    }

    /**
     * Tries to geta dataoutputstream to the given person.
     * 
     */
    public DataOutputStream getContactDataStream(User user, 
                                                 boolean requireDirect, 
                                                 boolean blindAcceptable) 
        throws Exception {

        DataOutputStream ret = getContactConnectionManager(user).sendDataStream(requireDirect);
        if (ret == null && blindAcceptable) {
            // create a multiplexing for all our 
            List<OutputStream> ol = new ArrayList();
            for (CMLookupSocketHandler lh : lookupHandlers.values())
                ol.add(lh.sendForwardingDataStream(user, getLocalUser()));
            if (ol.size() > 0)
                ret = MultiplexingDataOutputStream.create(ol);
        }
        return ret;
    }

    /**
     * Tries to get a connection
     */
    public MessageSocketHandler getContactConnection(User user,
                                                     boolean requireDirect) 
        throws Exception {
        
        MessageSocketHandler ret = getContactConnectionManager(user).getConnection(requireDirect);
        return ret;
    }

    public List<String> getContacts(ShareModel sm) {
        // eh ..
        List<String> ret = new ArrayList();
        for (ContactConnectionManager ccm : contacts.values()) {
            if (ccm.hasShare(sm))
                ret.add(ccm.getContact().getId());
        }
        return ret;
    }
}
