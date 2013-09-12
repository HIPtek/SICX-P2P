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
package fi.hip.sicxoss.model;

import java.util.*;
import java.io.*;

import org.apache.log4j.Logger;

import fi.hip.sicxoss.ident.*;
import fi.hip.sicxoss.io.*;
import fi.hip.sicxoss.io.message.*;
import fi.hip.sicxoss.LocalGateway;

/**
 * ShareModel
 *
 * The data model of a single shared folder
 * @author koskela
 */
public class ShareModel 
    extends MountableModel
    implements ConnectionManager.ConnectionManagerObserver,
               DataStore {

    private Logger log;

    public static final String revSuffix = "revisions";
    public static final String infoSuffix = "info";

    private DataStore store;
    
    // The events. We would need to index the events 1. by their
    // parent, 2. by their id, 3. in chronological order (in the order
    // they are applied) and finally 4. by user, cronologically
    // But we do just the first two for now.
    private Map<EventID, List<Event>> events;
    private Map<EventID, Event> eventsById;
    private Event head;

    // name of this share
    private String name;

    // mainly for testing .. mirroring shares.
    private List<ShareModel> mirrors;

    // the users sharing this share
    private Hashtable<String, User> users;

    // a list of the local keys added to the share
    private List<String> addedKeys;

    // users we have invited
    private Hashtable<String, Invite> pendingInvites;
    
    // users that has invited us. this is needed in order to accept
    // messages when we don't have event log yet
    private List<String> syncusers;

    // for persistency
    private File indexFile;
    private File root;

    // the id of this share
    private ShareID id;
    private LocalUser user;

    private ConnectionManager connectionManager;
    private List<ShareModelObserver> observers;
    private ContactCollectionModel contactsFolder;

    // file revisions
    private Hashtable<String, ItemModel> revisions;

    // the currently in-progress downloads
    private Hashtable<DataID, DataDownloader> downloaders;
    private List<DataUploader> uploaders;
    
    // tadaa..
    private LocalGateway gw;

    public interface ShareModelObserver {
        public void userAdded(User newUser, User eventIssuer, ShareModel share);
        public void userRemoved(User newUser, User eventIssuer, ShareModel share);
        public void folderCreated(CollectionModel ret, User eventIssuer, ShareModel share);
        public void fileCreated(ContentModel ret, User eventIssuer, ShareModel share);
        public void pathRemoved(ItemModel item, User eventIssuer, ShareModel share);
        public void fileUpdated(FileModel fi, User eventIssuer, ShareModel share);
        public void pathMoved(ItemModel item, String oldPath, User eventIssuer, ShareModel share);

        public void inviteSent(User newUser, ShareModel share);
        public void inviteReply(User newUser, boolean accept, ShareModel share);
        public void userStatusChanged(User newUser, boolean online, ShareModel share);
    }


    /**
     * the constructor
     */
    private ShareModel(String name, DataStore store, LocalUser user, String path, LocalGateway gw) {
        super();
        this.log = Logger.getLogger(getClass().getName() + ":" + name);
        this.store = store;
        this.name = name;
        this.mirrors = new ArrayList();
        this.user = user;
        this.connectionManager = user.connectionManager();
        this.root = new File(path);
        this.indexFile = new File(root.getAbsolutePath() + File.separator + "events.db");
        this.observers = new ArrayList();
        this.users = new Hashtable();
        this.pendingInvites = new Hashtable();
        this.syncusers = new ArrayList();
        this.contactsFolder = new ContactCollectionModel("Users sharing this folder", user, this);
        this.eventsById = new Hashtable();
        this.downloaders = new Hashtable();
        this.uploaders = new ArrayList();
        this.addedKeys = new ArrayList();
        this.revisions = new Hashtable();
        this.gw = gw;
        connectionManager.addObserver(this);
        setMountRoot(new FolderModel("", ItemID.nullItem(), this));
    }

    public LocalUser getLocalUser() {
        return user;
    }

    public File getStorageRoot() {
        return root;
    }

    public ShareID getId() {
        return id;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }
    
    public void addObserver(ShareModelObserver obs) {
        observers.add(obs);
    }

    @Override
    public void deregisterPath(ItemModel ret) {
        items.remove(ret.getFullName());
        if (ret instanceof ContactModel)
            itemsById.remove(ret.getId()); // don't remove files etc
    }

    @Override
    public String toString() {
        return "ShareModel " + name + " at " + root;
    }

    public static ShareModel load(String name, DataStore store, 
                                  LocalUser user, String path, LocalGateway gw) 
        throws Exception {
        
        ShareModel ret = new ShareModel(name, store, user, path, gw);
        ret.loadIndex();
        return ret;
    }

    public static ShareModel createNew(String name, DataStore store, 
                                       LocalUser user, String path, LocalGateway gw) 
        throws Exception {
        
        ShareModel ret = new ShareModel(name, store, user, path, gw);
        ret.initIndex();
        return ret;
    }

    public static ShareModel create(ShareID shareId, String name, DataStore store, 
                                    LocalUser user, String path, LocalGateway gw) 
        throws Exception {
        
        ShareModel ret = new ShareModel(name, store, user, path, gw);
        ret.initIndex(shareId);
        return ret;
    }

    private void initIndex(ShareID id)
        throws Exception {
        
        if (root.exists())
            throw new Exception("Root directory already exists: " + root);
        root.mkdirs();

        this.id = id;
        events = new Hashtable();
        
        //setMountRoot(createFolder("", null, null, true)); // we need to have something!
        user.addShare(this);
        saveIndex();
    }

    private void initIndex()
        throws Exception {
        
        if (root.exists())
            throw new Exception("Root directory already exists: " + root);
        root.mkdirs();

        id = ShareID.createNew(user);
        events = new Hashtable();

        //setMountRoot(createFolder("", null, null, true));
        addUser(user, true);
        user.addShare(this);
    }

    private void loadIndex() 
        throws Exception {
        
        if (!root.exists())
            throw new Exception("Root directory is missing: " + root);

        FileInputStream fis = new FileInputStream(indexFile);
        ObjectInputStream ois = new ObjectInputStream(fis);
        id = (ShareID)ois.readObject();
        events = (Hashtable)ois.readObject();

        // sort..
        eventsById.clear();
        for (List<Event> l : events.values())
            for (Event e : l)
                eventsById.put(e.id, e);

        // pending invites..
        pendingInvites = (Hashtable)ois.readObject();
        syncusers = (List)ois.readObject();
        log.info("we have " + pendingInvites.size() + " pending invites for this share");

        ois.close();
        fis.close();
        user.addShare(this);
    }

    private boolean saveIndexIgnore() {
        try {
            saveIndex();
            return true;
        } catch (Exception ex) {
            log.error("error saving index: " + ex);
        }
        return false;
    }

    private void saveIndex() 
        throws Exception {
        
        if (indexFile != null) {
            FileOutputStream fos = new FileOutputStream(indexFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(id);
            oos.writeObject(events);

            // write out the pending invites
            oos.writeObject(pendingInvites);
            oos.writeObject(syncusers);

            oos.close();
            fos.close();
        }
    }

    /* checks whether our currently used key has been added to the
       share.
     */
    private synchronized void addKey(User newUser, boolean issueEvent) 
        throws Exception {
        
        log.info("new key added for " + newUser);
        if (!addedKeys.contains(newUser.keyId())) {

            addedKeys.add(newUser.keyId());            
            if (issueEvent)
                issueEvent(Event.addUserKeyEvent(user));
        }
    }

    @Override
    public void start() 
        throws Exception {

        resetEvents();
        
        // share with mirrors..
        List<Event> all = new ArrayList();
        all.addAll(eventsById.values());

        // pass this through serialization
        all = (ArrayList)DataUtil.deserialize(DataUtil.serialize(all));
        for (ShareModel m : mirrors)
            m.importEvents(all, null);
    }
    
    @Override
    public void stop() {
    }

    public void addMirror(ShareModel model) {
        mirrors.add(model);
    }

    public String getName() {
        return name;
    }

    /*
     *
     * Events handling
     *
     */

    private synchronized void resetEvents() {
        
        log.debug("resetting all events!");
        //setMountRoot(null);
        items.clear();
        itemsById.clear();
        addedKeys.clear();
        revisions.clear();

        head = null;

        // batch commence
        initBatchUpdate();

        try {
            processEvent(null);
        } catch (Exception ex) {
            log.error("error performing batch update: " + ex);
        }

        // batch update complete
        batchUpdateComplete();

        // the magic contacts folder
        CollectionModel root = getMountRoot();
        if (root != null && contactsFolder != null) {
            root.addChild(contactsFolder);
            updateContactFolder();
        }
    }

    private synchronized void processEvent(Event event) {

        if (event != null)
            applyEvent(event);
        
        // the list should be sorted!
        List<Event> childs = null;
        if (event != null)
            childs = events.get(event.getId());
        else
            childs = events.get(EventID.nullEvent);
        if (childs != null) {
            Collections.sort(childs);
            for (Event e : childs) {
                processEvent(e);
            }
        }
    }

    private synchronized Hashtable<String, List<EventID>> getEventLines(Event event, 
                                                                        Hashtable<String, List<EventID>> lines) {
        
        if (lines == null) {
            lines = new Hashtable();
        } else {
            List<EventID> list = lines.get(event.id.getUserId());
            if (list == null) {
                list = new ArrayList();
                lines.put(event.id.getUserId(), list);
            }
            list.add(event.id);
        }

        // the list should be sorted!
        List<Event> childs = null;
        if (event != null)
            childs = events.get(event.getId());
        else
            childs = events.get(EventID.nullEvent);
        if (childs != null) {
            Collections.sort(childs);
            for (Event e : childs) {
                getEventLines(e, lines);
            }
        }
        return lines;
    }

    public void printEvents(Event event, PrintStream out, boolean verbose) 
        throws IOException {

        if (event != null) {
            out.println(event);
            if (verbose) {
                out.println(event.getSignedTextData());
                out.println();
            }
        }
                        
        // the list should be sorted!
        List<Event> childs = null;
        if (event != null)
            childs = events.get(event.getId());
        else
            childs = events.get(EventID.nullEvent);
        if (childs != null) {
            Collections.sort(childs);
            for (Event e : childs) {
                printEvents(e, out, verbose);
            }
        }
    }

    // here we actually apply the changes
    private synchronized boolean applyEvent(Event event) {
    
        // apply the changes!
        log.debug("applying event " + event);

        CollectionModel parent = null;
        String pid = null;
        try {
            pid = event.getProperty("parent");
            if (pid != null && pid.length() > 0) {
                ItemID piid = new ItemID(pid);
                parent = (FolderModel)itemsById.get(piid);
            }
        } catch (Exception ex) {
            log.warn(name + ":invalid parent '" + pid + "'");
        }
        
        User eventIssuer = user.contactManager().findByKey(event.getSignerKeyId());
        ItemID itemid = event.getItemId();
        ItemModel item = null;
        if (itemid != null)
            item = itemsById.get(itemid);
        User newUser;
        
        switch (event.getType()) {
        case ADD_USER:
            try {
                /* we need to trust it! ironically, self-signed ones are trusted
                   .. as otherwise we wouldn't be able to add those at all.

                   actually, trust everything at this point. this will be a problem
                   if we get a key update for someone signed by something we don't
                   trust!

                   The assumption here is that for us to have received this event, we need
                   to trust the signer (the one adding the user to the share). And if he
                   trusts this guy, so do we. Transitive trust.

                   The proper solution would be to check whether this is a new contact
                   (= contactmanager.isTrusted()). If not, then query the user.
                 */
                newUser = User.fromData(event.getProperty("info"));
                //if (!newUser.hasCert() || user.contactManager().isTrusted(newUser)) {
                addUser(newUser, false);
                if (newUser.equals(this.user))
                    addKey(newUser, false);
                    
                // notify
                for (ShareModelObserver smo : observers)
                    try {
                        smo.userAdded(newUser, eventIssuer, this); 
                    } catch (Exception ex) { log.warn("observer failed: " + ex); }
                // } else
                // log.warn("we were asked to add an untrusted user!");
            } catch (Exception ex) {
                log.error("error adding user: " + ex);
            }
            break;
        case ADD_USER_KEY:
            /* this is taken care of in storeEvent */
            log.info("add_user_key has been taken care of");
            /*
            try {
                newUser = User.fromData(event.getProperty("info"));
                if (users.containsKey(newUser.getId()) &&
                    user.contactManager().isTrusted(newUser)) {
                    
                    user.contactManager().addKey(newUser);
                    if (newUser.equals(this.user))
                        addKey(newUser, false);
                } else
                    log.warn("add key for a user which is not trusted or doesn't belong to this share");
            } catch (Exception ex) {
                log.error("error adding key: " + ex);
            }
            */
            break;
        case REMOVE_USER:
            try {
                newUser = User.fromData(event.getProperty("info"));
                // we need to trust it! ironically, self-signed ones are trusted
                if (!newUser.hasCert() || user.contactManager().isTrusted(newUser)) {
                    removeUser(newUser, false);
                    
                    // notify
                    for (ShareModelObserver smo : observers) 
                        try {
                            smo.userRemoved(newUser, eventIssuer, this);
                        } catch (Exception ex) { log.warn("observer failed: " + ex); }
                } else
                    log.warn("we were asked to add an untrusted user!");
            } catch (Exception ex) {
                log.error("error removing user: " + ex);
            }
            break;
        case CREATE_FILE: {
            if (parent == null) {
                log.warn("could not find parent (" + pid + ") of event's subject. Using the root");
                parent = getMountRoot();
            }
            ContentModel ret = createFile(event.getProperty("name"), event.getItemId(), parent, eventIssuer);
            ret.setModified(DataUtil.stringToDate(event.getProperty("modified")));

            // notify
            for (ShareModelObserver smo : observers)
                try {
                    smo.fileCreated(ret, eventIssuer, this);
                } catch (Exception ex) { log.warn("observer failed: " + ex); }
            break; }
        case DELETE: {
            if (item != null) {
                
                // notify
                for (ShareModelObserver smo : observers)
                    try {
                        smo.pathRemoved(item, eventIssuer, this);
                    } catch (Exception ex) { log.warn("observer failed: " + ex); }
                removeItem(item, false);
            } else
                log.warn("could not find the event's subject (" + event.getItemId() + ").");
            break; }
        case CREATE_FOLDER: {

            String name = event.getProperty("name");
            
            /* if we don't have the parent, then put it at root, IF it has a name (!= a new root) */
            if (parent == null && getMountRoot() != null && name.length() > 0) {
                log.warn("could not find parent (" + pid + ") of event's subject. Using the root");
                parent = getMountRoot();
            }
            
            if (parent != null || getMountRoot() == null) {
                CollectionModel ret = createFolder(name, event.getItemId(), parent, eventIssuer);
                ret.setModified(DataUtil.stringToDate(event.getProperty("modified")));

                // notify
                for (ShareModelObserver smo : observers)
                    try {
                        smo.folderCreated(ret, eventIssuer, this);
                    } catch (Exception ex) { log.warn("observer failed: " + ex); }
            } else {
                /* ok, so we are creating another root folder. This will not do. ignore. */
                log.warn("trying to re-create root. ignoring");
            }
            break; }
        case UPDATE_FILE: {
            FileModel fi = (FileModel)item;
            if (fi != null) {
                fi.processUpdate(event);
                addRevision(fi, event, eventIssuer);

                // notify
                for (ShareModelObserver smo : observers)
                    try {
                        smo.fileUpdated(fi, eventIssuer, this);
                    } catch (Exception ex) { log.warn("observer failed: " + ex); }
            } else
                log.warn("could not find the event's subject (" + event.getItemId() + ").");
            break; }
        case MOVE: {
            if (item != null) {
                if (parent == null) {
                    log.warn("could not find parent (" + pid + ") of event's subject. Using the root");
                    parent = getMountRoot();
                }
                String oldPath = item.getFullName();
                moveItem(item, event.getProperty("name"), parent, false);
                
                // notify
                for (ShareModelObserver smo : observers)
                    try {
                        smo.pathMoved(item, oldPath, eventIssuer, this);
                    } catch (Exception ex) { log.warn("observer failed: " + ex); }
            } else
                log.warn("could not find the event's subject (" + event.getItemId() + ").");
            break; }
        default:
            log.error("invalid event type!");
            break;
        }

        head = event;
        return true;
    }

    public synchronized void importEvents(List<Event> events, User source) {

        // if we are already dead, do nothing
        if (indexFile == null)
            return;

        log.info("importing " + events.size() + " events");

        File tf = indexFile; // prevent the index from being written after each!
        indexFile = null;
        boolean hasnew = false;
        try {

            // we try to apply the events, but that fails we reset
            // everything.
            boolean reset = false;
            for (Event e : events)
                if (storeEvent(e)) {
                    hasnew = true;
                    if (!reset && e.parentIs(head)) {
                        log.debug("ok, the event's parent is our head. applying!");
                        processEvent(e);
                    } else {
                        if (!reset) {
                            log.debug("the event's parent (" + e.parent + ") is not our head (" + head + "), we will reset!");
                            reset = true;
                        }
                        log.debug("queueing event for reset..");
                    }
                }
            indexFile = tf;
            if (hasnew)
                saveIndex();
            if (reset)
                resetEvents();
        } catch (Exception ex) {
            log.warn("error while importing " + ex);
        }
        indexFile = tf;

        // if we have something new, then notify all our peers
        if (hasnew && source != null) {
            // if would be better to be able to specify which lines of
            // events have been updated
            notifyContacts(null, source);
        }
    }

    /**
     * Add an event to the database
     */
    private synchronized boolean storeEvent(Event event) {
        
        // check for duplicates
        if (eventsById.containsKey(event.id)) {
            log.debug("ignoring duplicate event " + event.getId());
            return false;
        }

        // check signature

        /* add_key events are a bit different; we need to check the
         * contents - load the user from there, and check whether the
         * event is signed by that user. and the user needs to be in
         * this share!
         */
        boolean processed = true;
        if (event.type == Event.EventType.ADD_USER_KEY) {
            log.info("key add, checking content");

            try {
                User newUser = User.fromData(event.getProperty("info"));
                if (users.containsKey(newUser.getId()) &&
                    user.contactManager().isTrusted(newUser)) {
                    
                    user.contactManager().addKey(newUser);
                    if (newUser.equals(this.user))
                        addKey(newUser, false);
                    processed = true;
                } else
                    log.warn("add key for a user which is not trusted or doesn't belong to this share");
            } catch (Exception ex) {
                log.error("error adding key: " + ex);
            }
        } 
        
        if (!processed && !user.contactManager().checkSignature(event)) {
            log.warn("untrusted or invalid signature for event " + event.type + ". ignoring");
            return false;
        } else {

            List<Event> l = events.get(event.getParentId());
            if (l == null) {
                l = new ArrayList();
                events.put(event.getParentId(), l);
            }

            l.add(event);
            eventsById.put(event.id, event);
            saveIndexIgnore();
            return true;
        }
    }

    /**
     * This is used for issuing a locally-originated event
     */
    private synchronized void issueEvent(Event event) 
        throws Exception {

        // check if we (our current key) has been added to the share's log
        // but only after we've been added ourselves (after the first sync)
        if (addedKeys.size() > 0)
            addKey(user, true);
        else
            log.warn("we are issuing an event even though we aren't part of the share yet");
        
        event.generateId(user);
        event.setParent(head);
        user.sign(event);
        storeEvent(event);
        head = event;

        log.info("created event: " + event);

        // distribute the event!
        notifyContacts(user.getId(), user);
            
        // pass this through serialization
        if (mirrors.size() > 0) {
            event = (Event)DataUtil.deserialize(DataUtil.serialize(event));
            ArrayList<Event> list = new ArrayList();
            list.add(event);
            for (ShareModel m : mirrors) {
                m.importEvents(list, user);
            }
        }
    }


    /*
     *
     * User handling, including syncing and invites.
     *
     */


    public synchronized void inviteUser(User newUser) {
        log.info("inviting user " + newUser + " to join the fun!");

        // check: is user already a part of the share?
        if (users.containsKey(newUser.getId()))
            return;

        if (pendingInvites.containsKey(newUser.getId()))
            return;
        
        Invite invite = new Invite(user, newUser, this.id, this.name, "Hello!");
        pendingInvites.put(newUser.getId(), invite);
        newUser = user.addContact(newUser, this);
        updateContactFolder();
        saveIndexIgnore();

        
        // notify
        for (ShareModelObserver smo : observers)
            try {
                smo.inviteSent(newUser, this);
            } catch (Exception ex) { log.warn("observer failed: " + ex); }

        // .. should we try and send pending invites already?
        // yes, as we might have a connection to this guy already!
    }

    public synchronized void inviteResponseGot(User newUser, boolean accept) {

        // prevent users from tricking us into sharing
        if (!pendingInvites.containsKey(newUser.getId()))
            return;

        if (accept) {
            addUser(newUser, true);
            // try to sync immediately
            initiateSync(newUser, null, true);
        } else
            removeUser(newUser, true);


        // notify
        for (ShareModelObserver smo : observers)
            try {
                smo.inviteReply(newUser, accept, this);
            } catch (Exception ex) { log.warn("observer failed: " + ex); }
    }
    
    /** 
     * this is called by the networkengine when we have some sort of
     * connection to a user we've requested to follow up on
     */
    public synchronized void contactStatusChanged(User contact, boolean online, boolean direct) {
        
        log.info("contact " + contact + " state change: " + online + ", direct: " + direct);

        // notify
        for (ShareModelObserver smo : observers)
            try {
                smo.userStatusChanged(contact, online || direct, this);
            } catch (Exception ex) { log.warn("observer failed: " + ex); }
        
        if (!online && !direct)
            return;

        // we do everything in one big try-catch!
        DataOutputStream dos = null;
        try {
            // check pending invites 
            if (pendingInvites.containsKey(contact.getId())) {
                log.info("sending invite!");
                
                // 
                Invite invite = pendingInvites.get(contact.getId());
                if ((dos = connectionManager.getContactDataStream(contact, false, false)) != null) {
                    dos.writeUTF(NetworkMessage.MessageType.INVITE.toString());
                    dos.writeUTF(invite.shareId);
                    dos.writeUTF(invite.name);
                    dos.writeUTF(invite.description);
                    dos.close();
                } else
                    log.warn("we could not get a stream to the user, invite will have to wait!");
                
            } else if (syncusers.contains(contact.getId()) ||
                       users.containsKey(contact.getId())) {
                
                initiateSync(contact, null, true);
            } else
                log.warn("got a status update for someone we don't really care for!");

            // check if there are any pending downloads that we may want to retrieve
            if (online)
                for (DataDownloader dd : downloaders.values()) {
                    dd.sendDataQuery(contact);
                }
        } catch (Exception ex) {
            log.error("error handling status update: " + ex);
        }
    }
    
    /** notifies all contacts of an update */
    public synchronized void notifyContacts(String updated, User exclude) {
        
        log.debug("notifying everyone about " + updated + "'s updates");
        for (User u : users.values()) {
            log.debug("notify " + u + "?");
            if (exclude != null && u.equals(exclude))
                continue;
            initiateSync(u, updated, false);
        }
    }

    /** 
     * Initiate a sync. The logic is: we have two types of sync
     * messages: the request and notification. Requests are used for
     * polling new events (we basically inform the remote of what we
     * have, hoping he'll send anything we're missing). Notifications
     * are for notifying pre-emptively that we have something new (on
     * which the remote may respond with a request for those).
     *
     * @param contact The user with whom we want to sync
     * @param updated the user whom's events have been updated. or null (everyone)
     * @param request signals whether we are requesting or just notifying 
     */
    public synchronized void initiateSync(User contact, String updated, boolean request) {

        // send the head of all user's lines.
        log.debug("initiating sync with " + contact);
        DataOutputStream dos = null;
        try {
            if ((dos = connectionManager.getContactDataStream(contact, false, false)) != null) {
                Hashtable<String, List<EventID>> lines = getEventLines(null, null);
                if (request)
                    dos.writeUTF(NetworkMessage.MessageType.SYNC.toString());
                else
                    dos.writeUTF(NetworkMessage.MessageType.SYNC_NOTIFY.toString());
                dos.writeUTF(id.toString());
                for (String uid : lines.keySet()) {

                    // notify only about the ones that have been updated
                    if (updated != null && !updated.equals(uid))
                        continue;

                    List<EventID> list = lines.get(uid);
                    dos.writeUTF(uid);
                    dos.writeUTF(list.get(list.size()-1).toString()); // send head
                }
                dos.close();
            } else
                log.warn("we could not get a stream to the user, sync request will have to wait!");
        } catch (Exception ex) {
            log.error("error initiating sync: " + ex);
        }
    }
    
    public synchronized void syncGot(User contact, String uid, EventID eid, boolean isRequest) 
        throws Exception {
        
        log.info("got a sync request from user " + contact + " for " + uid + "'s events since " + eid);
        // check that this is a user we want to sync with
        if (!users.containsKey(contact.getId()) && 
            !pendingInvites.containsKey(contact.getId()) &&
            !syncusers.contains(contact.getId())) {
            
            log.warn("we got a sync request from someone who we don't share with!");
        }
        
        DataOutputStream dos = null;
        try {
            Hashtable<String, List<EventID>> lines = getEventLines(null, null);
            List<EventID> list = lines.get(uid);
            if (list == null) {
                log.debug("unknown user. we should request events!");
                
                // send a 'we don't have anything! both for requests and notifs!
                if ((dos = connectionManager.getContactDataStream(contact, false, false)) != null) {
                    dos.writeUTF(NetworkMessage.MessageType.SYNC.toString());
                    dos.writeUTF(id.toString());
                    dos.writeUTF(uid);
                    dos.writeUTF(EventID.nullEvent.toString());
                    dos.close();
                } else
                    log.warn("we could not get a stream to the user, sync will have to wait!");
            } else {
            
                if (eid.equals(EventID.nullEvent)) {

                    // mm.. or should we just send them over? perhaps..
                    log.debug("the user has no events, sending them all!");
                    if ((dos = connectionManager.getContactDataStream(contact, false, false)) != null) {
                        dos.writeUTF(NetworkMessage.MessageType.EVENT.toString());
                        dos.writeUTF(id.toString());
                        for (EventID ei : list) {
                            Event e = eventsById.get(ei);
                            byte[] data = e.getSignedData();
                            dos.writeShort(data.length);
                            dos.write(data);
                        }
                        dos.close();
                    } else
                        log.warn("we could not get a stream to the user, sync will have to wait!");
                } else {
                    int p = list.indexOf(eid);
                    if (p == (list.size()-1)) {
                        log.debug("found the event at HEAD (" + p + "). ignoring.");
                    } else if (p > -1) {

                        // we might consider not sending unless it is a request ..
                        log.debug("found the event at index " + p + ", sending " + (list.size()-p) + " events!");
                        if ((dos = connectionManager.getContactDataStream(contact, false, false)) != null) {
                            dos.writeUTF(NetworkMessage.MessageType.EVENT.toString());
                            dos.writeUTF(id.toString());
                            for (; p < list.size(); p++) {
                                EventID ei = list.get(p);
                                Event e = eventsById.get(ei);
                                byte[] data = e.getSignedData();
                                dos.writeShort(data.length);
                                dos.write(data);
                            }
                            dos.close();
                        } else
                            log.warn("we could not get a stream to the user, sync will have to wait!");
                    } else {
                        
                        if (!isRequest) {
                            log.debug("unknown event, let's ask for more!");
                            initiateSync(contact, uid, true);
                        } else {
                            // here the remote is ahead of us, but is requesting more
                            // we could request for those he has, but that should not
                            // be necessary as this should happen only on startup. and
                            // there, we have already requested new events, so let's just
                            // wait.
                            log.debug("unknown event, let's wait for an update!");
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("error handling status update: " + ex);
        }
    }
    
    /**
     * this is part of the connectionmanager's observer: called when the connection
     * to a lookupserver changes
     */
    @Override
    public void lookupServerState(ConnectionManager.CMLookupSocketHandler lookupServer, boolean online) {
        
        log.info("lookup server " + lookupServer.getAddress() + " state change: " + online);
        if (online) {
            for (User u : users.values()) {
                user.addContact(u, this);
            }

            for (String uid : pendingInvites.keySet()) {
                User u = user.contactManager().findUser(uid);
                user.addContact(u, this);
            }
            for (String uid : syncusers) {
                User u = user.contactManager().findUser(uid);
                user.addContact(u, this);
            }
        }
    }

    public Collection<User> getUsers() {
        return users.values();
    }

    public Collection<User> getPendingInvites() {
        
        ArrayList<User> ret = new ArrayList();
        for (String uid : pendingInvites.keySet()) {
            User u = user.contactManager().findUser(uid);
            ret.add(u);
        }
        return ret;
    }

    public Collection<User> getSyncUsers() {
        
        ArrayList<User> ret = new ArrayList();
        for (String uid : syncusers) {
            User u = user.contactManager().findUser(uid);
            ret.add(u);
        }
        return ret;
    }

    /* adds a user to the syncing 'acl'. this is used to establish a
     * sync relationship with the user, but without creating an event
     * for it. this is used in the beginning when someone invites us,
     * but we have none of the events yet.
     */
    public synchronized void syncWith(User newUser) {
        log.info("adding temp sync for " + newUser);
        syncusers.add(newUser.getId().toString());
        newUser = user.addContact(newUser, this);
        saveIndexIgnore();
        
        // send sync request?
        initiateSync(newUser, null, true);
    }

    private void updateContactFolder() {
        if (contactsFolder != null)
            contactsFolder.update();
    }

    /* adds a user to the share, creating an event (or event-
     * originated ) */
    private synchronized void addUser(User newUser, boolean local) {
        
        // remove these from the pending
        if (pendingInvites.containsKey(newUser.getId()) ||
            syncusers.contains(newUser.getId())) {

            pendingInvites.remove(newUser.getId());
            syncusers.remove(newUser.getId());
            updateContactFolder();
            saveIndexIgnore();
        }

        if (!users.containsKey(newUser.getId())) {

            if (!newUser.equals(this.user)) {
                newUser = user.addContact(newUser, this);
                users.put(newUser.getId(), newUser);
                updateContactFolder();
            } else {
                log.debug("that's me!");
            }
            
            if (local)
                try {
                    issueEvent(Event.addUserEvent(newUser));
                } catch (Exception ex) {
                    log.error("error adding user: " + ex);
                }
        }
    }

    /**
     * Removes a user, whether it is from the share or from the
     * invites or sync list. 
     */
    private synchronized void removeUser(User newUser, boolean local) {

        if (!newUser.equals(this.user) || local) {
            if (newUser.equals(this.user) || users.containsKey(newUser.getId())) {
                users.remove(newUser.getId());
                user.removeContact(newUser, this);
                
                if (local)
                    try {
                        issueEvent(Event.removeUserEvent(newUser));
                    } catch (Exception ex) {
                        log.error("Error adding user: " + ex);
                    }
            } else if (pendingInvites.containsKey(newUser.getId()) ||
                       syncusers.contains(newUser.getId())) {
                pendingInvites.remove(newUser.getId());
                syncusers.remove(newUser.getId());
                user.removeContact(newUser, this);
            }
            updateContactFolder();
            saveIndexIgnore();
        } else
            log.debug("that's me!");
    }

    /*
     * The MountableModel- related functionality
     *
     */

    /* added revision support for shares */
    @Override
    public ItemModel getItem(String p) {
        
        ItemModel ret = super.getItem(p);
        if (ret == null && p != null)
            ret = revisions.get(p);

        if (ret == null && p != null) {
            if (p.endsWith(ItemModel.PATH_SEP + infoSuffix)) {
                String shp = p.substring(0, p.length() - (ItemModel.PATH_SEP + infoSuffix).length());
                ItemModel item = super.getItem(shp);
                if (item == null)
                    item = revisions.get(shp);
                if (item != null) {
                    ret = new InfoModel(infoSuffix, item, this);
                    
                }
            }
        }

        return ret;
    }

    @Override
    public synchronized ContentModel createFile(String name, ItemID id, CollectionModel parent) {
        return createFile(name, id, parent, null);
    }

    private synchronized ContentModel createFile(String name, ItemID id, CollectionModel parent, User creator) {

        ContentModel ret;
        if (parent instanceof RevisionFolderModel)
            return null;
        if (parent instanceof ContactCollectionModel) {
            ret = new ContactModel(name, this);
            parent.addChild(ret);
            return ret;
        }

        if (id == null)
            ret = new FileModel(name, this);
        else
            ret = new FileModel(name, id, this);

        try {
            parent.addChild(ret);
            if (creator == null)
                issueEvent(Event.createFileEvent((FileModel)ret));
        } catch (Exception ex) {
            log.error("Error creating file: " + ex);
            ret.delete();
            ret = null;
        }
        ret.setCreator(creator == null? user : creator);
        return ret;
    }
    
    @Override
    public synchronized CollectionModel createFolder(String name, ItemID id, CollectionModel parent) {
        return createFolder(name, id, parent, null);
    }

    private synchronized CollectionModel createFolder(String name, ItemID id, CollectionModel parent, User creator) {

        FolderModel ret;
        if (id == null)
            ret = new FolderModel(name, this);
        else
            ret = new FolderModel(name, id, this);

        if (parent != null)
            parent.addChild(ret);
        else if (getMountRoot() == null) {
            registerPath(ret);
            setMountRoot(ret);
        }
        
        if (creator == null)
            try {
                issueEvent(Event.createFolderEvent(ret));
            } catch (Exception ex) {
                log.error("Error creating folder: " + ex);
                ret.delete();
                ret = null;
            }
        ret.setCreator(creator == null? user : creator);
        return ret;
    }

    @Override
    public synchronized void removeItem(ItemModel item) {
        removeItem(item, true);
    }

    public synchronized void removeItem(ItemModel item, boolean local) {
        
        if (item instanceof ContactModel) {
            if (!local) // we don't accept contact deletes from remote
                return;

            ContactModel cm = (ContactModel)item;
            // del invites also.
            removeUser(cm.getUser(), true);
        } else if (item instanceof ContactCollectionModel) {
            // nothing..
        } else {

            // are we trying to remove the whole share?
            if (item == getMountRoot() && local) {
                try {
                    gw.deleteShare(this, store);
                } catch (Exception ex) {
                    log.warn("error while removing the share: " + ex);
                }
            } else {
                super.removeItem(item);
                if (local)
                    try {
                        issueEvent(Event.deleteEvent(item));
                    } catch (Exception ex) {
                        log.error("Error deleting: " + ex);
                    }
            }
        }
    }

    public void deleteShare() {

        log.info("deleting share " + getName() + "..");
        removeUser(user, true);
        indexFile.delete();
        indexFile = null;
        store = null;
    }

    public DataStore getDataStore() {
        return store;
    }
    
    @Override
    public synchronized ItemModel createCopy(String newName, ItemModel item, CollectionModel parent) {
        // normal:
        if (item instanceof ContactModel)
            return super.createCopy(newName, item, parent);

        return item.duplicate(newName, parent);
    }

    @Override
    public synchronized ItemModel moveItem(ItemModel item, String newName, CollectionModel parent) {
        return moveItem(item, newName, parent, true);
    }

    public synchronized ItemModel moveItem(ItemModel item, String newName, CollectionModel parent, boolean local) {

        // we allow moves / copies of contact models.
        if (item instanceof ContactModel)
            return super.moveItem(item, newName, parent);
        
        // 
        if (item.getModel() != parent.getModel()) {
            ItemModel newitem = createCopy(newName, item, parent);
            removeItem(item, local);
            item = newitem;
        } else {
            CollectionModel oldParent = item.getParent();
            oldParent.removeChild(item);
            item.setName(newName);
            parent.addChild(item);
            
            if (local)
                try {
                    issueEvent(Event.moveEvent(item));
                } catch (Exception ex) {
                    log.error("Error moving: " + ex);
                }
        }
        return item;
    }

    /* called by the filemodel */
    protected void fileUpdated(FileModel fm) {
        
        try {
            Event e = Event.updateFileEvent(fm);
            issueEvent(e);
            addRevision(fm, e, user);
        } catch (Exception ex) {
            log.error("error issuing event while updating content: " + ex);
        }
    }
    
    private synchronized void addRevision(FileModel fi, Event event, User issuer) {

        // record the new revision
        String p = fi.getFullName() + ItemModel.PATH_SEP + revSuffix;
        RevisionFolderModel rfm = (RevisionFolderModel)revisions.get(p);
        if (rfm == null) {
            rfm = new RevisionFolderModel(revSuffix, p, this);
            revisions.put(rfm.getFullName(), rfm);
        }
        RevisionFileModel f = rfm.addRevision(event);
        f.setCreator(issuer); //fi.getCreator());
        f.setModifier(issuer);
        fi.setModifier(issuer);
        revisions.put(f.getFullName(), f);
        log.debug("added revision " + f.getFullName());
    }


    /*
     *
     * the data handling
     *
     */

    public DataStore getStorage() {
        // we will provide a layer above the disk storage to handle
        // missing files, proactive file fetches, caching etc.
        return this;
    }

    @Override
    public void load(String path, Properties p, LocalGateway gw)
        throws Exception {
    }
    
    @Override
    public DataID store(InputStream in, long length)
        throws Exception {

        return store.store(in, length);
    }
    
    @Override
    public void release(DataID id) {


        // .. check if another file is still using that data. if not,
        // then put it into some sort of 'unused' which has a quota on

        store.release(id);
    }
    
    @Override
    public void acquire(DataID id) {

        // .. check whether we already have the data. if so, then no
        // problem. otherwise; if it is a small file, initiate transfer
        // .. a bigger one; transfer part of if (?)

        // the problem here is that we don't want to have multiple
        // transfers of the same file in progress in case > 1 share is
        // using the same DataStore.  But that probably will not be an
        // issue for a while. In the future, we might consider
        // separating this to another 'wrapper' DataStore that the
        // Shares use directly

        // actually, we don't either want to have users sharing data
        // from other shares. So the download being share-specific
        // makes sense

        store.acquire(id);
        if (!store.hasData(id, 0, id.getLength())) {
            DataDownloader dl = getDownloader(id, true);
            //dl.requestData(0, id.getLength());
        }
    }

    @Override
    public void initBatchUpdate() {
        log.info("initing a batch update");
        store.initBatchUpdate();
    }

    @Override
    public void batchUpdateComplete() {
        log.info("completing a batch update");
        store.batchUpdateComplete();
    }
    
    @Override
    public boolean hasData(DataID id, long start, long finish) {
        // hopefully this will never be called.
        return true;
    }

    @Override
    public InputStream getStream(DataID id, long start, long finish)
        throws Exception {
        
        if (!store.hasData(id, start, finish)) {
            DataDownloader dl = getDownloader(id, true);
            return dl.getStream(start, finish);
        } else
            return store.getStream(id, start, finish);
    }

    @Override
    public File importFile(DataID id, File file) {
        return store.importFile(id, file);
    }

    @Override
    public void deleteStore() {
        // remove all the downloads .. todo
        log.info("we should cancel & remove all pending downloads!");
    }

    @Override
    public DataTracker getDataTracker(DataID id) {
        return store.getDataTracker(id);
    }

    /*
     * Data retrieval (downloading)- related functionality
     *
     */

    public void dataRequestGot(User contact, DataID dataId, long start, long finish) {
        log.info("got a data request from " + contact + " of " + dataId + " for bytes " + start + ":" + finish);
        
        // check if we have those bytes. create an uploader if so.
        if (hasData(dataId, start, finish)) {
            log.info("yes, we have the data.");
            
            try {
                DataUploader du = new DataUploader(contact, dataId, start, finish, this);
                du.start();
                uploaders.add(du);
            } catch (Exception ex) {
                log.error("exception while requesting data from peer: " + ex);
            }

        } else
            log.debug("sorry, nothing like that here.");
    }

    public void uploadComplete(DataUploader du) {

        log.info("data upload complete.");
        uploaders.remove(du);
    }



    public void dataResponseGot(User contact, DataID dataId, long start, long finish) {
        log.info("got a data response from " + contact + " of " + dataId + " for bytes " + start + ":" + finish);
        
        DataDownloader dl = getDownloader(dataId, false);
        if (dl != null) {
            dl.processDataResponse(contact, start, finish);
        } else
            log.warn("got a response to something we aren't downloading right now");
    }

    public void dataBlockGot(User contact, DataID dataId, long start, long finish, DataInputStream in) 
        throws Exception {
        
        log.info("got a data block from " + contact + " of " + dataId + " for bytes " + start + ":" + finish);
        
        DataDownloader dl = getDownloader(dataId, false);
        if (dl != null) {
            dl.dataBlockGot(in, start);
        } else
            log.warn("got a block to something we aren't downloading right now");
    }

    /**
     * @return true if this will accept the stream and read it dry
     */
    public boolean dataStreamGot(User contact, DataID dataId, long start, long finish, DataSocketHandler conn) 
        throws Exception {
        
        DataDownloader dl = getDownloader(dataId, false);
        if (dl != null) {
            dl.dataStreamGot(conn, start, finish);
            return true;
        } else
            log.warn("got a block to something we aren't downloading right now");

        return false;
    }

    public void dataQueryGot(User contact, DataID dataId, long start, long finish) {
        log.info("got a data query from " + contact + " of " + dataId + " for bytes " + start + ":" + finish);
        
        // check if we have those bytes. create an uploader if so.
        if (hasData(dataId, start, finish)) {
            log.info("yes, we have the data.");
            
            // send a 'got it'
            try {
                DataOutputStream dos = connectionManager.getContactDataStream(contact, false, false);
                if (dos != null) {
                    dos.writeUTF(NetworkMessage.MessageType.DATA_RESPONSE.toString());
                    dos.writeUTF(id.toString()); // the share id
                    dos.writeUTF(dataId.toString());
                    dos.writeLong(start);
                    dos.writeLong(finish);
                    dos.close();
                }
            } catch (Exception ex) {
                log.error("exception while querying data from peer: " + ex);
            }
        } else
            log.debug("sorry, nothing like that here.");

    }

    public boolean requestDataBlock(User contact, DataID dataId, long start, long finish) {
        log.info("requesting data for " + dataId + " bytes " + start + ":" + finish);

        // hmm.. this might be optimized at some point into a
        // 'broadcast' type of message.
        boolean sent = false;
        try {
            DataOutputStream dos = connectionManager.getContactDataStream(contact, false, false);
            if (dos != null) {
                dos.writeUTF(NetworkMessage.MessageType.DATA_REQUEST.toString());
                dos.writeUTF(id.toString()); // the share id
                dos.writeUTF(dataId.toString());
                dos.writeLong(start);
                dos.writeLong(finish);
                dos.close();
                sent = true;
            }
        } catch (Exception ex) {
            log.error("exception while requesting data from peer: " + ex);
        }
        return sent;
    }

    public boolean broadcastDataQuery(DataID dataId, long start, long finish) {
        log.info("broadcasting a query for " + dataId + " bytes " + start + ":" + finish);

        // hmm.. this might be optimized at some point into a
        // 'broadcast' type of message.
        boolean sent = false;
        for (User u : users.values()) {
            if (sendDataQuery(dataId, start, finish, u))
                sent = true;
        }
        return sent;
    }

    public boolean sendDataQuery(DataID dataId, long start, long finish, User u) {
        
        log.info("sending a query for " + dataId + " bytes " + start + ":" + finish + " to " + u);

        // hmm.. this might be optimized at some point into a
        // 'broadcast' type of message.
        boolean sent = false;
        try {
            DataOutputStream dos = connectionManager.getContactDataStream(u, false, false);
            if (dos != null) {
                dos.writeUTF(NetworkMessage.MessageType.DATA_QUERY.toString());
                dos.writeUTF(id.toString()); // the share id
                dos.writeUTF(dataId.toString());
                dos.writeLong(start);
                dos.writeLong(finish);
                dos.close();
                sent = true;
            }
        } catch (Exception ex) {
            log.error("exception while requesting data from peer: " + ex);
        }
        return sent;
    }

    public File downloadComplete(DataDownloader dd, File file) {

        // get id, get file, move everything into the store!
        // remove downloader, but keep it alive until all clients
        // have released their inputstreams

        // => which means making sure that the download has 
        // access to it!

        // forts
        // ok.. now we should also check whether the checksum matches!
        log.info("the download is now complete! located at " + file);
        downloaders.remove(dd.dataId);
        return store.importFile(dd.dataId, file);
    }

    private DataDownloader getDownloader(DataID id, boolean create) {

        DataDownloader dl = null;
        synchronized (downloaders) {
            dl = downloaders.get(id);
            if (dl == null && create) {
                dl = new DataDownloader(id, this);
                downloaders.put(id, dl);
                log.debug("created a new downloader for data id " + id);
            }
        }
        return dl;
    }

}
