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
package fi.hip.sicxoss.lookup;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.net.*;
import java.util.*;

import org.apache.log4j.Logger;

import fi.hip.sicxoss.io.*;
import fi.hip.sicxoss.ident.*;
import fi.hip.sicxoss.io.message.*;

/**
 * LookupServer
 *
 * The lookup server used in the SICX FOSS
 * @author koskela
 */
public class LookupServer 
    extends Thread {

    private static final Logger log = Logger.getLogger(LookupServer.class);
    
    private int port;
    private NetworkEngine ne;
    
    /* where the sockets for each of the clients go */
    private Hashtable<String, UserConnectionHandler> userHandlers;

    /**
     * UserConnectionHandler
     *
     * This class acts as a proxy for communicating with a user.
     * Usually this will just forward everything to the actual
     * connection (as there will usually be only one), but it can do
     * multiplexing in case we get multiple connection.
     */
    public class UserConnectionHandler {
        
        // the remote user
        public User user;

        // the users he is interested in hearing about
        private List<String> contacts;

        // the connections to him (might be more than one)
        private List<LookupSocketHandler> connections;

        private LookupServer ls;

        public UserConnectionHandler(User user, LookupServer ls) {
            this.user = user;
            contacts = new ArrayList();
            connections = new ArrayList();
            this.ls = ls;
            // we can do 'last seen' etc..
            
        }

        public boolean hasContact(User user) {
            return contacts.contains(user.getId());
        }

        public void addConnection(LookupSocketHandler sh) {
            connections.add(sh);
        }
        
        public void connectionClosed(LookupSocketHandler sh) {
            // if we have no more connections, send a 'disconnect' message
            // to all interested peers.
            connections.remove(sh);
            if (!hasConnection()) {
                ls.userDisconnected(getRegistrationInfo());
            }
        }

        public boolean hasConnection() {
            for (LookupSocketHandler lsh : connections)
                if (lsh.isConnected())
                    return true;
            return false;
        }

        public User getRegistrationInfo() {
            return this.user;
        }

        protected MultiplexingDataOutputStream getStream() 
            throws IOException {
            
            List<OutputStream> streams = new ArrayList();
            for (LookupSocketHandler lsh : connections)
                if (lsh.isConnected())
                    streams.add(lsh.sendDataStream());
            if (streams.size() > 0)
                return MultiplexingDataOutputStream.create(streams);
            else
                return null;
        }

        protected AuthenticatedSocketHandler getSocketHandler() {
            for (LookupSocketHandler lsh : connections)
                if (lsh.isConnected())
                    return lsh;
            return null;
        }

        /* Sends an introduction to all the connections on which one
         * has not been sent yet. */
        protected int sendIntroduction(User user) 
            throws Exception {
            
            List<OutputStream> streams = new ArrayList();
            List<LookupSocketHandler> handlers = new ArrayList();
            for (LookupSocketHandler lsh : connections)
                if (lsh.isConnected() && !lsh.isIntroduced(user)) {
                    streams.add(lsh.sendDataStream());
                    handlers.add(lsh);
                }
            if (streams.size() > 0) {
                MultiplexingDataOutputStream dos = MultiplexingDataOutputStream.create(streams);
                dos.writeUTF(NetworkMessage.MessageType.CONTACT_UPDATE.toString());
                sendContactUpdate(user, dos);
                dos.close();
                for (LookupSocketHandler lsh : handlers)
                    lsh.introductionSent(user);
            }
            return streams.size();
        }
        
        protected DataOutputStream sendContactUpdate(User u, DataOutputStream dos) 
            throws Exception {
            
            if (dos == null) {
                dos = getStream();
                dos.writeUTF(NetworkMessage.MessageType.CONTACT_UPDATE.toString());
            }
            dos.writeUTF(u.getData());
            return dos;
        }

        protected void sendContactDisconnect(User u) 
            throws Exception {
            
            DataOutputStream dos = getStream();
            dos.writeUTF(NetworkMessage.MessageType.CONTACT_DISCONNECT.toString());
            dos.writeUTF(u.getId());
            dos.close();
        }

        /**
         * Processes the packets received from the remove user
         */
        public synchronized void processDataStream(DataInputStream in, AuthenticatedSocketHandler conn)
            throws Exception {

            String cmd = in.readUTF();
            log.warn("processing msg type: " + cmd);

            DataOutputStream dos = null;
            NetworkMessage.MessageType type = NetworkMessage.MessageType.valueOf(cmd);
            switch (type) {
            case REGISTER: {
                String data = in.readUTF();
                User nu = User.fromData(data);
                if (nu.equals(user)) {
                    // update the user
                    this.user = nu;
                    ls.userRegistered(user);

                    // we send a network mirror-message
                    dos = getStream();
                    dos.writeUTF(NetworkMessage.MessageType.NETWORK_MIRROR.toString());
                    for (LookupSocketHandler lsh : connections) {
                        InetSocketAddress addr = lsh.getRemoteAddress();
                        dos.writeUTF(addr.getAddress().getHostAddress() + ":" + addr.getPort());
                    }
                }
                break;
            }

            case ADD_CONTACTS: {
                int cc = 0;
                while (in.available() > 0) {
                    String cid = in.readUTF();
                    log.debug("read contact " + cid);
                    if (!contacts.contains(cid)) {
                        contacts.add(cid);
                    }
                    
                    // get that contact's info. we should have some
                    // sort of access control here. only send the info
                    // if the other one has this one in his contacts
                    User u = ls.getUserRegistration(cid);
                    if (u != null) {
                        dos = sendContactUpdate(u, dos);
                    } else
                        log.debug("..who is unknown to me for now");
                    cc++;
                }
                log.debug("read " + cc + " contacts");
                break;
            }

            case REMOVE_CONTACTS: {
                
                while (in.available() > 0) {
                    String cid = in.readUTF();
                    log.debug("read contact " + cid);
                    contacts.remove(cid);
                }
                break;
            }

            case FORWARD: {
                // both have similar header & access rules
                
                String to = in.readUTF();
                String from = in.readUTF();
                UserConnectionHandler uch = getUserHandler(to);
                
                if (uch != null) {
                    // if the user receiving the packet has not been
                    // introduced to this user, do so now.

                    if (!from.equals(user.getId()))
                        log.warn("got a FORWARD from someone else than the connected user!");
                    int i = uch.sendIntroduction(user);
                    log.info("sent " + i + " introductions for " + user + " to " + to);

                    // note: we pass the message along untouched. this instead of
                    // changing it to a 'FORWARDED'- type of message, as we can do
                    // peer-forwarding or multihops using this.
                    
                    // ..although we will need some sort of loop prevention if we go
                    // into multihopping.
                    
                    // some sort of access control here, please. we shouldn't forward everything
                    // to everyone without permission to do so.
                    dos = uch.getStream();

                    // a bit hackish. just write the packet as-is
                    dos.write(((MessageSocketHandler.MessageDataInputStream)in).getData());
                } else
                    log.warn("got forward to an unknown user!");
                break;
            }

            case STREAM_START: {
                
                String to = in.readUTF();
                String from = in.readUTF();
                String sid = in.readUTF();
                String did = in.readUTF();
                long start = in.readLong();
                long finish = in.readLong();
                
                UserConnectionHandler uch = getUserHandler(to);
                if (uch != null) {
                    if (!from.equals(user.getId()))
                        log.warn("got a FORWARD from someone else than the connected user!");
                    int i = uch.sendIntroduction(user);
                    log.info("sent " + i + " introductions for " + user + " to " + to);

                    AuthenticatedSocketHandler sink = uch.getSocketHandler();
                    if (sink != null && conn.forwardTo(sink, (int)(finish-start),
                                                       ((MessageSocketHandler.MessageDataInputStream)in).getMessage())) {
                        log.info("started forwarding");
                    } else {
                        log.warn("could not forward stream, skipping");
                        conn.drainStream((int)(finish-start));
                    }
                } else {
                    log.warn("could not forward stream, unknown user. skipping");
                    conn.drainStream((int)(finish-start));
                }
                break;
            }

            default:
                // case INVITE:
                // case CONTACT_UPDATE:
                // case EVENT:
                log.warn("got invalid message type: " + type);
            }

            if (dos != null)
                dos.close();
        }
    }


    /**
     * Called when we get a new connection from a user. If we want any
     * sort of access control in the lookup server, this is where it
     * could be implemented: just return null here!
     */
    protected synchronized UserConnectionHandler gotNewConnection(User user, LookupSocketHandler sh) {
        
        UserConnectionHandler uch = userHandlers.get(user.getId());
        if (uch == null) {
            uch = new UserConnectionHandler(user, this);
            userHandlers.put(user.getId(), uch);
        }
        uch.addConnection(sh);

        // notify others? no. wait for 'user registered' packet.

        return uch;
    }

    /**
     * Called when a user's registration info has been updated
     */
    protected synchronized void userRegistered(User user) {
        
        // go through all the handlers, forward the message to everyone that is interested.
        log.debug("user regged, sending update");
        for (UserConnectionHandler uch : userHandlers.values()) {
            try {
                if (uch.hasContact(user)) {
                    log.debug("found one interested");
                    uch.sendContactUpdate(user, null).close();
                }
            } catch (Exception ex) {
                log.warn("error sending contact update: " + ex);
            }
        }
    }

    protected synchronized void userDisconnected(User user) {
        
        for (UserConnectionHandler uch : userHandlers.values()) {
            try {
                if (uch.hasContact(user))
                    uch.sendContactDisconnect(user);
            } catch (Exception ex) {
                log.warn("error sending deregistration update: " + ex);
            }
        }
        userHandlers.remove(user.getId());
    }

    protected User getUserRegistration(String uid) {
        UserConnectionHandler uch = userHandlers.get(uid);
        if (uch != null)
            return uch.getRegistrationInfo();
        return null;
    }

    protected UserConnectionHandler getUserHandler(String uid) {
        return userHandlers.get(uid);
    }

    public LookupServer(NetworkEngine ne) {
        this.ne = ne;
        userHandlers = new Hashtable();
    }

    public void init(int port) 
        throws Exception {

        log.info("initing the lookup server on port " + port);
        this.port = port;

	InetSocketAddress isa = new InetSocketAddress(port);
        LookupServerSocketHandler sh = new LookupServerSocketHandler(isa, this);
        ne.addHandler(sh);

        ne.addClient(this);

        // test
        /*
	isa = new InetSocketAddress(9998);
        sh = new LookupServerSocketHandler(isa);
        ne.addHandler(sh);
        sh.spawn = true;
        */
    }

    public void run() {
        
        log.info("doing the lookup server..");
        try {
            ne.join();
        } catch (Exception ex) {
        }
    }

    public class LookupServerSocketHandler 
        extends ServerSocketHandler {

        private LookupServer ls;

        public LookupServerSocketHandler(SocketAddress sa, LookupServer ls) 
            throws Exception {
            super(sa);
            this.ls = ls;
        }

        public void gotAccept(SocketChannel sc) {
            log.info("we accept!");
            try {
                LookupSocketHandler sh = new LookupSocketHandler(sc, ls);
                eng.addHandler(sh);
            } catch (Exception ex) {
                log.warn("error accepting socket: " + ex);
            }
        }
        
    }

    /** 
     * socket handler that tends to conversing with the clients/peers.
     * This does not, in itself, do much anything, except forward the
     * traffic to the UserConnectionHandler.
     */
    public class LookupSocketHandler 
        extends AuthenticatedSocketHandler {

        private LookupServer ls;
        private UserConnectionHandler uch;
        private List<String> introductions;

        public LookupSocketHandler(SocketChannel sc, LookupServer ls) 
            throws Exception {
            super(sc, null, null);
            this.ls = ls;
            this.introductions = new ArrayList();
        }

        public boolean isIntroduced(User user) {
            return introductions.contains(user.getId());
        }

        public void introductionSent(User user) {
            introductions.add(user.getId());
        }

        public void gotAuthenticatedDataStream(DataInputStream in)
            throws Exception {

            log.info("got data stream from: " + getRemoteUser());
            if (uch == null) {
                log.error("but we are not registered yet with the lookup!");

                DataOutputStream out = sendDataStream();
                out.writeUTF("not_registered");
                out.close();
                close();
            }

            uch.processDataStream(in, this);
        }

        public void authenticationComplete(boolean success) {
            log.info("authentication was completed: " + success);
            if (success) {
                log.info("remote user is: " + getRemoteUser());
                uch = ls.gotNewConnection(getRemoteUser(), this);
            } else {
                if (uch != null)
                    uch.connectionClosed(this);
                introductions.clear();
            }
        }
    }

}
