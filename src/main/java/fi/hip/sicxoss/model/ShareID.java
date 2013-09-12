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

import java.io.Serializable;
import java.util.*;

import fi.hip.sicxoss.ident.*;

/**
 * This is actually exactly the same as an event id for now. we'll
 * have to see if it needs something more still, or if they can be
 * combined.
 * @author koskela
 */
public class ShareID 
    implements Comparable<ShareID>, Serializable {

    static final long serialVersionUID = 6911792011514997074L;

    private UUID id;
    private String userId;

    private ShareID() { }
    
    public static ShareID createNew(User user) {

        ShareID ret = new ShareID();
        ret.id = UUID.randomUUID();
        ret.userId = user.getId();
        return ret;
    }

    public String getUserId() {
        return userId;
    }

    public static ShareID fromString(String idString) {
        ShareID ret = new ShareID();

        StringTokenizer st = new StringTokenizer(idString, ":");
        if (st.countTokens() == 2) {
            ret.userId = (String)st.nextToken();
            ret.id = UUID.fromString((String)st.nextToken());
        }
        return ret;
    }
    
    public static ShareID nullEvent() {
        ShareID ret = new ShareID();
        ret.id = null;
        return ret;
    }

    public String toString() {
        if (id != null)
            return userId + ":" + id.toString();
        else
            return "";
    }

    @Override
    public int compareTo(ShareID other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public boolean equals(Object other) {
        return compareTo((ShareID)other) == 0;
    }
        
    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
