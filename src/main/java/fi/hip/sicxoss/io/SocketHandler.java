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
package fi.hip.sicxoss.io;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import org.apache.log4j.Logger;

public abstract class SocketHandler {
        
    private static final Logger log = Logger.getLogger(SocketHandler.class);

    protected NetworkEngine eng;
    protected SelectableChannel channel;
    protected SelectionKey key;

    /* utility */
    public static InetSocketAddress a2s(String str) {

        int p = str.indexOf(':');
        String addr = "";
        int port = 1234;
        if (p > -1) {
            addr = str.substring(0, p);
            port = Integer.parseInt(str.substring(p+1));
        } else
            addr = str;

        /* nah..
        if (Pattern.matches("^[0-9.]+$", addr)) {
            ia = new Inet4Address(
        // resolve.. manually.
        */
        return new InetSocketAddress(addr, port);
    }
    
    protected void setChannel(SelectableChannel channel) {
        this.channel = channel;
    }

    protected SelectableChannel getChannel() {
        return channel;
    }

    protected void setEngine(NetworkEngine eng) 
        throws Exception {
        
        this.eng = eng;
    }

    protected void setKey(SelectionKey key) {
        this.key = key;
    }

    protected NetworkEngine getEngine() {
        return this.eng;
    }

    /*
     * selector-related methods
     *
     */

    protected void handleRead() 
        throws Exception {
        log.debug("handle read");
    }
        
    protected void handleWrite()
        throws Exception {
        log.debug("handle write");
    }

    protected void handleConnect() 
        throws Exception {
        log.debug("handle connect");
    }

    protected void handleAccept()
        throws Exception {
        log.debug("handle accept");
    }
        
    protected abstract int getInterestSet();
        
    protected void updateInterests() {
        int interests = getInterestSet();

        if (interests == 0) {
            if (key != null)
                key.cancel();
            eng.removeHandler(this);
        } else if (key != null && key.interestOps() != interests) {
            key.interestOps(interests);
        }
    }

}
