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
package fi.hip.sicxoss.io.message;

import java.io.DataInputStream;

/**
 * NetworkMessage class.
 *
 * @author koskela
 */
public class NetworkMessage {
    
    /**
     * The type of network messages. These are used both between the
     * lookup server and clients, and client and clients. */
    public enum MessageType {
        REGISTER, // register to a lookup
            ADD_CONTACTS, // adding contacts there
            REMOVE_CONTACTS, // removing
            FORWARD, // forward to one or multiple other recipients
            CONTACT_UPDATE, // a callback that a contact's status has been updated
            CONTACT_DISCONNECT, // 
            
            NETWORK_MIRROR, // for sending back info on how the peer looks from where i'm at.

            SYNC, // sync request ("please send data that I'm missing")
            SYNC_NOTIFY, // sync notification ("this is my current head")
            EVENT, // an event
            
            DATA_REQUEST, // request a piece of data
            DATA_QUERY, // find a piece of data
            DATA_RESPONSE, // request a piece of data
            DATA_BLOCK, // send a piece of data

            STREAM_START, // turn the socket into a streaming one

            INVITE, // invite to some share
            INVITE_RESPONSE // response to an invite
            }


    private byte[] data;

    public NetworkMessage(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }
    
    public static NetworkMessage fromData(byte[] data) {
        return new NetworkMessage(data);
    }

    public static NetworkMessage fromStream(DataInputStream in) 
        throws Exception {

        if (in.available() < 2)
            return null;
        byte[] num = new byte[2];
        in.read(num);
        int msgLen = (num[0] & 0xff) +
            ((num[1] & 0xff) << 8);
        
        if (in.available() < msgLen)
            return null;
        
        byte[] data = new byte[msgLen];
        in.readFully(data);
        return new NetworkMessage(data);
    }
    
}
