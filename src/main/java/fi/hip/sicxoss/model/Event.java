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
import java.io.Serializable;

import fi.hip.sicxoss.ident.*;
import fi.hip.sicxoss.util.*;
import fi.hip.sicxoss.io.DataUtil;

/**
 * Event
 *
 * An event to the shared folder
 * @author koskela
 */
public class Event
    extends SignableProperties
    implements Comparable<Event>, 
               Serializable {

    static final long serialVersionUID = 2758241556737996938L;

    public enum EventType {
        ADD_USER, REMOVE_USER,
            CREATE_FILE, DELETE,
            CREATE_FOLDER,
            UPDATE_FILE, MOVE,
            ADD_USER_KEY
    }

    protected EventID parent;
    protected EventType type;
    protected EventID id;

    public Event(byte[] data) 
        throws Exception {
            
        if (!super.init(data))
            throw new IOException();
        
        type = EventType.valueOf(getProperty("e_type"));
        id = EventID.fromString(getProperty("e_id"));
        parent = EventID.fromString(getProperty("e_parent"));
    }

    private Event(EventType etype) {
        type = etype;
        setProperty("e_type", etype.toString());
        setProperty("e_id", "");
        setProperty("e_parent", "");
    }        

    private Event(EventType etype, ItemModel item) {
        this(etype);
        setProperty("item", item.getId().toString());
    }

    public void generateId(User user) {
        id = EventID.createNew(user);
        setProperty("e_id", id.toString());
    }

    public void setParent(Event parent) {
        if (parent != null)
            this.parent = parent.id;
        else
            this.parent = EventID.nullEvent;
        setProperty("e_parent", this.parent.toString());
    }
    
    public EventID getParentId() {
        return this.parent;
    }
    
    public boolean parentIs(Event p) {
        if (p == null)
            return parent.equals(EventID.nullEvent);
        else
            return parent.equals(p.id);
    }
    
    public EventID getId() {
        return this.id;
    }

    public EventType getType() {
        return this.type;
    }
    
    public ItemID getItemId() {
        String idstr = getProperty("item");
        if (idstr != null)
            return new ItemID(idstr);
        else
            return null;
    }

    public static Event addUserEvent(User target) {
        Event e = new Event(EventType.ADD_USER);
        e.setProperty("info", target.getData());
        return e;
    }

    public static Event addUserKeyEvent(LocalUser target) {
        Event e = new Event(EventType.ADD_USER_KEY);
        e.setProperty("info", target.getData());
        return e;
    }

    public static Event removeUserEvent(User target) {
        Event e = new Event(EventType.REMOVE_USER);
        e.setProperty("info", target.getData());
        return e;
    }

    public static Event createFolderEvent(FolderModel item) {
        Event e = new Event(EventType.CREATE_FOLDER, item);
        e.setProperty("parent", "" + (item.getParent() == null? "" : item.getParent().getId()));
        e.setProperty("name", "" + item.getName());
        e.setProperty("modified", DataUtil.dateToString(item.getModified()));
        return e;
    }

    public static Event deleteEvent(ItemModel item) {
        Event e = new Event(EventType.DELETE, item);
        return e;
    }

    // all of the following three might be good to have in one single event!
    public static Event createFileEvent(FileModel item) {
        Event e = new Event(EventType.CREATE_FILE, item);
        e.setProperty("parent", "" + item.getParent().getId());
        e.setProperty("name", "" + item.getName());
        e.setProperty("modified", DataUtil.dateToString(item.getModified()));
        return e;
    }

    public static Event moveEvent(ItemModel item) {
        Event e = new Event(EventType.MOVE, item);
        e.setProperty("parent", "" + item.getParent().getId());
        e.setProperty("name", "" + item.getName());
        e.setProperty("modified", DataUtil.dateToString(item.getModified()));
        return e;
    }

    public static Event updateFileEvent(FileModel item) {
        Event e = new Event(EventType.UPDATE_FILE, item);
        e.setProperty("size", "" + item.getLength());
        e.setProperty("data", "" + item.getDataId());
        e.setProperty("modified", DataUtil.dateToString(item.getModified()));
        e.setProperty("ct", item.getContentType());
        
        /* if we unify all the file- related events:
        e.props.setProperty("parent", "" + item.getParent().getId());
        e.props.setProperty("name", "" + item.getName());
        */
        return e;
    }

    @Override
    public String toString() {
        String ret = "event " + type + ", " + id;
        if (parent != null)
            ret += ", parent " + parent;
        return ret;
    }
    
    private void readObject(ObjectInputStream aStream) 
        throws IOException, ClassNotFoundException {
        
        byte[] data = (byte[])aStream.readObject();
        if (!init(data))
            throw new IOException();

        type = EventType.valueOf(getProperty("e_type"));
        id = EventID.fromString(getProperty("e_id"));
        parent = EventID.fromString(getProperty("e_parent"));
    }
    
    private void writeObject(ObjectOutputStream aStream)
        throws IOException {

        aStream.writeObject(getSignedData());
    }

    // for sorting the events
    @Override
    public int compareTo(Event other) {
        return id.compareTo(((Event)other).id);
    }
}
