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

/**
 * MountableModel
 *
 * The data model for anything that can be mounted as a filesystem
 */
public abstract class MountableModel {

    private static final Logger log = Logger.getLogger(MountableModel.class);

    protected Map<String, ItemModel> items;
    protected Map<ItemID, ItemModel> itemsById;
    protected CollectionModel mountRoot;

    protected MountableModel() {
        this.items = new Hashtable();
        this.itemsById = new Hashtable();
    }

    public void setMountRoot(CollectionModel mr) {
        this.mountRoot = mr;
    }

    public CollectionModel getMountRoot() {
        return this.mountRoot;
    }

    public void registerPath(ItemModel ret) {
        items.put(ret.getFullName(), ret);
        itemsById.put(ret.getId(), ret);
    }

    public void deregisterPath(ItemModel ret) {
        items.remove(ret.getFullName());
        itemsById.remove(ret.getId());
    }

    public ItemModel getItem(String p) {
        if (p == null || p.length() == 0)
            return getMountRoot();
        else
            return items.get(p);
    }

    public void removeItem(ItemModel item) {
        
        if (item == getMountRoot())
            log.warn("trying to delete the root. Ignoring");
        else
            item.delete();
    }
    
    public ItemModel createCopy(String newName, ItemModel item, CollectionModel parent) {
        log.info("copy item " + item.getFullName() + " to " + parent.getFullName());

        // we allow moves / copies of contact models.
        ContactModel cm = null;
        if (item instanceof ContactModel) {
            cm = (ContactModel)item;
            User u = cm.getUser();
            if (parent.getModel() instanceof ShareModel) {
                ShareModel sm = (ShareModel)parent.getModel();
                log.info("adding " + u + " to share " + sm);
                sm.inviteUser(u);
            } else if (parent instanceof ContactCollectionModel) {
                log.info("copy the contact to some other collection!");
                ContactCollectionModel ccm = (ContactCollectionModel)parent;
                if ((cm = ccm.getContact(u)) == null) {
                    cm = new ContactModel(u, parent.getModel());
                    ccm.addChild(cm);
                }
            } else
                cm = null;
        } else if (parent instanceof ContactCollectionModel) {
            log.error("trying to put a non-contact into a contact collection!");
        }
        
        return cm;
    }

    public ItemModel moveItem(ItemModel item, String newName, CollectionModel parent) {
        log.info("move item " + item.getFullName() + " to " + parent.getFullName());

        // we allow moves / copies of contact models.
        ContactModel cm = null;
        if (item instanceof ContactModel) {
            cm = (ContactModel)item;
            if (parent == item.getParent()) {
                ContactCollectionModel ccm = (ContactCollectionModel)parent;
                // moves within the contacts folder => renaming the
                // contact. the model fill follow automatically!
                parent.removeChild(cm);
                if (cm.getName() != newName)
                    ccm.getLocalUser().contactManager().setContactName(cm.getUser(), newName);
                parent.addChild(cm);
            } else if (parent.getModel() instanceof ShareModel ||
                       parent instanceof ContactCollectionModel) {
                cm = (ContactModel)createCopy(newName, item, parent);
            } else
                cm = null;
        } else if (parent instanceof ContactCollectionModel) {
            log.error("trying to put a non-contact into a contact collection!");
        }
        
        return cm;
    }
    
    public abstract CollectionModel createFolder(String name, ItemID id, CollectionModel parent);
    
    public abstract ContentModel createFile(String name, ItemID id, CollectionModel parent);
    

}
