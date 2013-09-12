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

import fi.hip.sicxoss.ident.*;

/**
 * ContactCollectionModel class.
 *
 * @author koskela
 */
public class ContactCollectionModel 
    extends CollectionModel
    implements LocalUser.LocalUserObserver {
    
    private LocalUser user;
    
    protected ContactCollectionModel(String name, LocalUser user, MountableModel share) {
        //super((share instanceof ShareModel? "Shared Users" : "Contacts (" + user.getFullName() + "'s)"), share);
        super(name, share);
        this.user = user;
        
        update();
        user.addObserver(this);
    }

    public LocalUser getLocalUser() {
        return user;
    }

    public boolean hasContact(User contact) {
        return getContact(contact) != null;
    }

    public ContactModel getContact(User contact) {

        for (ItemModel im : getChildren()) {
            ContactModel cm = (ContactModel)im;
            if (contact.equals(cm.getUser())) {
                return cm;
            }
        }
        return null;
    }

    public void update() {
        
        removeAllChildren();
        if (getModel() instanceof ShareModel) {
            ShareModel sm = (ShareModel)getModel();
            for (User uc : sm.getUsers()) {
                this.addChild(new ContactModel(uc, sm));
            }
            
            for (User uc : sm.getPendingInvites()) {
                ContactModel cm = new ContactModel(uc, sm, "Invite pending - ");
                this.addChild(cm);
            }

            for (User uc : sm.getSyncUsers()) {
                ContactModel cm = new ContactModel(uc, sm, "Awaiting initial sync - ");
                this.addChild(cm);
            }
        } else {
            for (User uc : user.contactManager().getContacts()) {                
                this.addChild(new ContactModel(uc, getModel()));
            }
        }
        modified();
    }
    
    @Override
    public void delete() {
        // nada.
    }

    @Override
    public void contactAdded(LocalUser user, User contact, ShareModel share) {
        update();
    }

    @Override
    public void contactRemoved(LocalUser user, User contact, ShareModel share) {
        update();
    }

    @Override
    public void inviteGot(LocalUser user, Invite invite) {
    }

    @Override
    public void inviteReplied(LocalUser user, Invite invite) {
    }

    @Override
    public ItemModel duplicate(String newName, CollectionModel parent) {
        return null;
    }
}
