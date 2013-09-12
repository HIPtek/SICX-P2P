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

/**
 * CollectionModel
 *
 * A model representing a collection of items. Be they files and
 * folders or 'magic' things.
 * @author koskela
 */
public class CollectionModel 
    extends ItemModel {

    protected Map<String, ItemModel> children;
    
    protected CollectionModel(String name, MountableModel share) {
        super(name, share);
        children = new Hashtable();
    }

    protected CollectionModel(String name, ItemID id, MountableModel share) {
        super(name, id, share);
        children = new Hashtable();
    }

    public Collection<ItemModel> getChildren() {
        return children.values();
    }

    public List<ItemModel> getChildrenList() {
        List<ItemModel> ret = new ArrayList();
        for (ItemModel im : children.values())
            ret.add(im);
        return ret;
    }
    
    public void addChild(ItemModel item) {
        item.setParent(this);
        children.put(item.getName(), item);
        modified();
    }

    public void removeChild(ItemModel item) {
        item.setParent(null);
        children.remove(item.getName());
        modified();
    }

    public void removeAllChildren() {
        for (ItemModel item : children.values()) {
            item.setParent(null);
        }
        children.clear();
        modified();
    }
    
    public ItemModel getChild(String name) {
        return children.get(name);
    }

    protected void childModified(ItemModel item) {
        this.modified = new Date();
    }

    @Override
    public ItemModel duplicate(String newName, CollectionModel parent) {
        return null;
    }

    @Override
    protected void setParent(CollectionModel parent) {

        for (ItemModel c : children.values()) {
            c.setParent(null);
        }
        
        super.setParent(parent);

        if (parent != null)
            for (ItemModel c : children.values()) {
                c.setParent(this);
            }
    }

}
