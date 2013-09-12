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
 * ItemModel
 *
 * The base model for all items stored in a shared folder.
 */
public abstract class ItemModel {
    
    public static final String PATH_SEP = "/";

    protected ItemID id;
    private String name;
    protected CollectionModel parent;
    protected Date created;
    protected Date modified;
    //private List<Event> eventLog;
    private MountableModel share;
    private User creator;
    private User modifier;

    protected ItemModel(String name, MountableModel share) {
        this.name = name;
        this.created = new Date();
        this.modified = new Date();
        this.share = share;
        this.id = new ItemID();
    }

    protected ItemModel(String name, ItemID id, MountableModel share) {
        this.name = name;
        this.created = new Date();
        this.modified = new Date();
        this.share = share;
        this.id = id;
    }
    
    public void setCreator(User user) {
        this.creator = user;
    }

    public User getCreator() {
        return this.creator;
    }

    public void setModifier(User user) {
        this.modifier = user;
    }

    public User getModifier() {
        if (this.modifier == null)
            return getCreator();
        return this.modifier;
    }

    public ItemID getId() {
        return id;
    }

    public MountableModel getModel() {
        return share;
    }

    protected void setModified(Date d) {
        modified = d;
        if (parent != null)
            parent.childModified(this);
    }

    protected void modified() {
        this.modified = new Date();
        if (parent != null)
            parent.childModified(this);
    }        

    protected void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        if (this.parent != null) {
            String p = this.parent.getFullName();
            if (p.endsWith(PATH_SEP))
                return p + this.getName();
            else                
                return p + PATH_SEP + this.getName();
        } else
            return this.getName();
    }

    public Date getCreated() {
        return created;
    }

    public Date getModified() {
        return modified;
    }

    protected void setParent(CollectionModel parent) {
        if (this.parent != null)
            getModel().deregisterPath(this);
        this.parent = parent;
        if (parent != null)
            getModel().registerPath(this);
        modified();
    }

    public CollectionModel getParent() {
        return this.parent;
    }

    /*
    public void addEvent(Event event) {
        eventLog.add(event);
        modified();
    }
    */

    public void delete() {
        if (this.parent != null)
            this.parent.removeChild(this);
        modified();
    }

    public abstract ItemModel duplicate(String newName, CollectionModel parent);
}
