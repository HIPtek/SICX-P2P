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
import java.util.StringTokenizer;

import fi.hip.sicxoss.ident.*;

/**
 * DataID class.
 *
 * @author koskela
 */
public class DataID 
    implements Comparable<DataID>, Serializable {

    private String checksum;
    private long length;
    static final long serialVersionUID = 3718795853730629760L;

    protected DataID(String checksum, long length) {
        this.checksum = checksum;
        this.length = length;
    }

    public static DataID parse(String idString) {
        StringTokenizer st = new StringTokenizer(idString, ":");
        String checksum = (String)st.nextToken();
        long length = Long.parseLong((String)st.nextToken());
        DataID ret = new DataID(checksum, length);
        return ret;
    }

    public long getLength() {
        return length;
    }

    public String getChecksum() {
        return checksum;
    }

    @Override
    public String toString() {
        return getChecksum() + ":" + getLength();
    }

    public String getFileName() {
        return getChecksum() + ".blob";
    }

    @Override
    public int compareTo(DataID other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public boolean equals(Object other) {
        return compareTo((DataID)other) == 0;
    }
        
    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
