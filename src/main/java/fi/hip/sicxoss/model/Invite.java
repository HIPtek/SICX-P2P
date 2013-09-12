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

import java.io.*;
import java.util.*;

import fi.hip.sicxoss.ident.*;

/* a small class for containing invitation-related info. */
/**
 * Invite class.
 *
 * @author koskela
 */
public class Invite 
    implements Serializable {

    static final long serialVersionUID = 7880432852599408236L;

    // we might get invited to a share by multiple contacts!
    public List<String> contactIds;
    public String shareId;
    public String name;
    public String description;
    public Date invited;
    public Boolean verdict;

    // this should be created by the UI when accepting the invite
    public transient ShareModel share;
    public transient LocalUser user;

    public Invite(LocalUser u, User contact, ShareID shareid, String name, String description) {
        this.user = u;
        this.name = name;
        this.description = description;
        this.invited = new Date();
        this.verdict = null;
        this.shareId = shareid.toString();
        this.contactIds = new ArrayList();
        addContact(contact);
    }
    
    public String toString() {
        String str = "Invite " + name + " from ";
        List<User> contacts = getContacts();
        if (contacts.size() > 1)
            str += contacts.size() + " contacts";
        else if (contacts.size() > 0)
            str += contacts.get(0).getFullName();
        else
            str += "an unknown user";
        return str;
    }

    public String getDescription() {

        String str = "Invite to the shared folder '" + name + "' from:\n";
        for (User c : getContacts())
            str += "  " + c.getFullName() + " (" + c.getId().toString() + ")\n";
        str += "Invited " + invited.toString() + "\n";
        str += "With the greeting '" + description + "'\n";
        str += "Share id: " + shareId + "\n";
        return str;
    }
    
    /* return false if the contact was there already */
    public boolean addContact(User contact) {
        if (!contactIds.contains(contact.getId().toString())) {
            contactIds.add(contact.getId().toString());
            return true;
        } else
            return false;
    }

    public boolean hasContact(User contact) {
        return contactIds.contains(contact.getId().toString());
    }

    public void removeContact(User contact) {
        contactIds.remove(contact.getId().toString());
    }
    
    public List<User> getContacts() {
        List<User> ret = new ArrayList();
        for (String cid : contactIds) {
            User u = user.contactManager().findUser(cid);
            if (u != null)
                ret.add(u);
        }
        return ret;
    }

    public ShareID getShareId() {
        return ShareID.fromString(this.shareId);
    }
}
