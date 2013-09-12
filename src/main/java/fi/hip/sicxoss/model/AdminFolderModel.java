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
import fi.hip.sicxoss.*;

/**
 * AdminFolderModel
 *
 * The data model of an admin folder
 * @author koskela
 */
public class AdminFolderModel 
    extends MountableModel
    implements LocalUser.LocalUserObserver,
               LocalGateway.LocalGatewayObserver {

    private static final Logger log = Logger.getLogger(AdminFolderModel.class);

    private LocalGateway lg;
    private List<CollectionModel> contacts;
    
    public AdminFolderModel(LocalGateway lg) {

        this.lg = lg;
        lg.addObserver(this);

        CollectionModel root = new CollectionModel("", this);
        setMountRoot(root);

        for (LocalUser u : lg.getLocalUsers()) {
            userAdded(u);
        }
    }

    // from the gatewayobserver
    @Override
    public void userAdded(LocalUser u) {

        CollectionModel root = getMountRoot();
        ContactModel cm = new ContactModel(u, this);
        root.addChild(cm);
        
        ContactCollectionModel c = new ContactCollectionModel("Contacts (" + u.getFullName() + "'s)", u, this);
        root.addChild(c);

        // The invites
        for (Invite inv : u.getReceivedInvites()) {
            InviteModel im = new InviteModel(u, inv, this);
            root.addChild(im);
        }
        u.addObserver(this);
        
    }

    @Override
    public void shareAdded(LocalUser u, ShareModel sm) {
        // mm..
    }

    @Override
    public void shareRemoved(ShareModel sm) {
        // mm..
    }

    // local user obs
    @Override
    public void contactAdded(LocalUser user, User contact, ShareModel share) { }
    @Override
    public void contactRemoved(LocalUser user, User contact, ShareModel share) { }
    @Override
    public void inviteGot(LocalUser user, Invite invite) {
        CollectionModel root = getMountRoot();
        InviteModel im = new InviteModel(user, invite, this);
        root.addChild(im);
    }
    @Override
    public void inviteReplied(LocalUser user, Invite invite) {
        CollectionModel root = getMountRoot();
        for (ItemModel im : root.getChildrenList()) {
            if (im instanceof InviteModel) {
                InviteModel inm = (InviteModel)im;
                if (user == inm.getUser() && invite == inm.getInvite())
                    inm.delete();
            }
        }
    }

    @Override
    public void removeItem(ItemModel item) {
        log.info("remove item " + item.getFullName());

        // contacts can be removed, right?
        if (item instanceof ContactModel) {
            // remove from all shares also??
            // super.removeItem(item);
            // does this operation make sense at all?
        } else if (item instanceof InviteModel) {
            // decline!
            InviteModel im = (InviteModel)item;
            Invite invite = im.getInvite();
            LocalUser lu = im.getUser();
            lu.rejectInvite(invite);
            item.delete();
        }
    }

    @Override
    public ItemModel moveItem(ItemModel item, String newName, CollectionModel parent) {
        log.info("move item " + item.getFullName() + " to " + parent.getFullName());

        // we allow moves / copies of contact models.
        ItemModel cm = super.moveItem(item, newName, parent);
        if (item instanceof InviteModel) {
            // ok, we accept!
            InviteModel im = (InviteModel)item;
            Invite invite = im.getInvite();
            LocalUser lu = im.getUser();

            try {
                lu.acceptAndCreateInvite(invite, newName, "/" + newName);
                item.delete();
            } catch (Exception ex) {
                log.error("exception while creating share: " + ex);
                ex.printStackTrace();
            }
            cm = null;
        }
        return cm;
    }

    @Override
    public ItemModel createCopy(String newName, ItemModel item, CollectionModel parent) {
        log.info("copy item " + item.getFullName() + " to " + parent.getFullName());

        // we allow moves / copies of contact models.
        ItemModel cm = super.createCopy(newName, item, parent);
        if (item instanceof InviteModel) {
            cm = null;
        }
        return cm;
    }
    
    @Override
    public CollectionModel createFolder(String name, ItemID id, CollectionModel parent) {
        log.info("create folder " + name + " in " + parent.getFullName());
        return null;
    }
    
    @Override
    public ContentModel createFile(String name, ItemID id, CollectionModel parent) {
        log.info("create file " + name + " in " + parent.getFullName());
        
        if (parent instanceof ContactCollectionModel) {
            // .. ok..
            ContactModel ret = new ContactModel(name, this);
            parent.addChild(ret);
            return ret;
        }
        return null;
    }
   

}
