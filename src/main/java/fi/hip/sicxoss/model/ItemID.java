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


import java.util.UUID;
import java.io.Serializable;

/**
 * ItemID class.
 *
 * @author koskela
 */
public class ItemID 
    implements Comparable<ItemID>, Serializable {

    static final long serialVersionUID = 1659273443085747237L;
    private UUID id;
    
    public ItemID() {
        id = UUID.randomUUID();
    }

    public ItemID(String uuid) {
        id = UUID.fromString(uuid);
    }

    public static ItemID nullItem() {
        ItemID ret = new ItemID();
        ret.id = null;
        return ret;
    }
    
    @Override
    public String toString() {
        if (id == null)
            return "";
        return id.toString();
    }

    @Override
    public int compareTo(ItemID other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public boolean equals(Object other) {
        return compareTo((ItemID)other) == 0;
    }
        
    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
