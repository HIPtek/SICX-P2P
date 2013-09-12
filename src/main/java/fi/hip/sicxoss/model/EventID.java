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
import java.io.Serializable;
import fi.hip.sicxoss.ident.*;


/**
 * EventID class.
 *
 * @author koskela
 */
public class EventID 
    implements Comparable<EventID>, Serializable {

    static final long serialVersionUID = 6911792011514997074L;

    private UUID id;
    private String userId;

    private EventID() { }
    
    public static EventID createNew(User user) {

        EventID ret = new EventID();
        ret.id = UUID.randomUUID();
        ret.userId = user.getId();
        return ret;
    }

    public static EventID fromString(String idString) {
        EventID ret = new EventID();

        StringTokenizer st = new StringTokenizer(idString, ":");
        if (st.countTokens() == 2) {
            ret.userId = (String)st.nextToken();
            ret.id = UUID.fromString((String)st.nextToken());
        }
        return ret;
    }

    public String getUserId() {
        return userId;
    }
    
    public static final EventID nullEvent;
    static {
        nullEvent = new EventID();
        nullEvent.id = null;
    }

    public String toString() {
        if (id != null)
            return userId + ":" + id.toString();
        else
            return "";
    }

    @Override
    public int compareTo(EventID other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public boolean equals(Object other) {
        return compareTo((EventID)other) == 0;
    }
        
    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
