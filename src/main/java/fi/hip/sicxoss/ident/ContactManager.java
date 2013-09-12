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
package fi.hip.sicxoss.ident;

import java.io.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import java.security.cert.*;
import javax.security.auth.x500.*;

import org.apache.log4j.Logger;

/**
 * ContactManager
 * 
 * Class for keeping track of the user's info as well as remote
 * contacts.
 * @author koskela
 */
public class ContactManager {

    private static final Logger log = Logger.getLogger(ContactManager.class);

    private LocalUser localUser;
    private Map<String, User> contacts;
    private Map<String, User> knownKeys;
    private Map<String, X509Certificate> certs;
    
    public ContactManager(LocalUser local) {
        this.localUser = local;
        this.contacts = new Hashtable();
        this.knownKeys = new Hashtable();
        this.certs = new Hashtable();
    }

    public void writeTo(ObjectOutputStream oos)
        throws Exception {

        oos.writeObject(new Integer(contacts.size()));
        for (String s : contacts.keySet()) {
            oos.writeObject(s);
            oos.writeObject(contacts.get(s).getName());
            oos.writeObject(contacts.get(s).getData());
        }
        oos.writeObject(certs);
    }
    
    public void readFrom(ObjectInputStream ois)
        throws Exception {

        int size = ((Integer)ois.readObject()).intValue();
        while (size > 0) {
            String k = (String)ois.readObject();
            String n = (String)ois.readObject();
            String d = (String)ois.readObject();
            User u = User.fromData(d);
            u.nickName = n;
            contacts.put(k, u);
            addKey(u);
            size--;
        }
        certs = (Hashtable)ois.readObject();
    }

    /**
     * Returns the local user
     */
    public LocalUser getLocalUser() {
        return localUser;
    }

    /* sets the nickname for a contact. ensures that it is not in
     * use! */
    public synchronized void setContactName(User contact, String name) {
        
        int c = 0;
        String sugg = name;
        do {
            for (User u : contacts.values()) {
                if (u.getName().equals(sugg) && !u.equals(contact)) {
                    sugg = null;
                    break;
                }
            }
            if (sugg != null) {
                contact.nickName = sugg;
                break;
            } else {
                c++;
                sugg = name + "_" + c;
            }
        } while (true);

        log.debug("name changed from " + name + " to " + contact.getName());

        localUser.saveData();
    }

    /**
     * Adds a single contact to the contact database.
     */
    protected User addContact(User user) {

        addKey(user);
        User old = contacts.get(user.getId());
        if (old != null) {
            log.debug("found old, updating name " + user.getId() + " to " + old.getName());
            return old;
        } else {
            log.debug("new contact: " + user.getId());
            // add event to observers!
        }
        contacts.put(user.getId(), user);
        setContactName(user, user.getName()); // this will save it
        return user;
    }

    public User findUser(String userId) {
        
        if (userId.equals(localUser.getId()))
            return localUser;
        return contacts.get(userId);
    }

    public User findByKey(String keyId) {

        return knownKeys.get(keyId);
    }

    public void addCert(X509Certificate cert) {
        String key = cert.getSubjectX500Principal().getName();
        if (certs.containsKey(key))
            log.info("replacing cert for " + key);
        else
            log.info("adding cert for " + key);
        certs.put(key, cert);
    }

    public void addKey(User user) {

        // add the key somewhere ..
        if (!knownKeys.containsKey(user.keyId())) {
            log.debug("adding key " + user.keyId() + " for user " + user.getId());
            knownKeys.put(user.keyId(), user);
        }
        
        /* should we save? 

           no need; these are currently used only for verifying the
           signatures of shares' events. this implies that the user
           and/or user's key has been seen in a previous event of that
           share.

           which means that it has been added here previously during
           this running instance of the application.

           this applies to own, local, keys also.

           if we were to verify each network packet, then we should
           get an update of the key anyway beforehand.
        */
    }

    public boolean isTrusted(User u) {
        
        // returns whether the identity given is trusted, aka. signed
        // by someone we trust!

        if (findByKey(u.keyId()) != null)
            return true;

        X509Certificate cert = u.getCert();
        if (cert != null) {
            
            String key = cert.getIssuerX500Principal().getName();
            log.info("finding cert for " + key);
            X509Certificate root = certs.get(key);
            if (root != null) {
                // verify .. 
                try {
                    cert.verify(root.getPublicKey());
                    return true;
                } catch (Exception ex) {
                    log.warn("exception while verifying certificate: " + ex);
                }
            }
        }
        return false;
    }

    public boolean checkSignature(Signable s) {
        
        /* we have a problem with SLCS identities:

           the key is re-generated during each login. making
           it impossible to check the validity of stuff that has been signed 
           with an earlier key.

        */
        log.debug("verifying signature of " + s.getSignerKeyId());
        try {
            // todo: find by key..
            User user = findByKey(s.getSignerKeyId());
            if (user != null && user.verify(s))
                return true;
        } catch (Exception ex) {
            log.error("exception while verifying signature of " + s.getSignerKeyId() + ": " + ex);
        }
        return false;
    }

    /**
     * Removes a single contact to the contact database.
     */
    protected void removeContact(User user) {
        contacts.remove(user.getId());
        localUser.saveData();
        // remove user to observers!
    }

    /**
     * Returns a list of contacts
     */
    public List<User> getContacts() {
        return new ArrayList<User>(contacts.values());
    }
}
