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


public abstract class ServerSocketHandler 
    extends SocketHandler {
        
    private ServerSocketChannel ssc;
        
    /**
     * create an listener
     */
    public ServerSocketHandler(SocketAddress sa) 
        throws Exception {

        ssc = ServerSocketChannel.open();
        setChannel(ssc);
	ssc.configureBlocking(false);
	ssc.socket().bind(sa);
    }

    public ServerSocketHandler() 
        throws Exception {

        ssc = ServerSocketChannel.open();
        setChannel(ssc);
	ssc.configureBlocking(false);
        SocketAddress sa = new InetSocketAddress(0);
	ssc.socket().bind(sa);
    }

    public abstract void gotAccept(SocketChannel sc) throws Exception;

    protected void handleAccept() 
        throws Exception {
        gotAccept(ssc.accept());
    }
        
    protected int getInterestSet() {
        return SelectionKey.OP_ACCEPT;
    }

    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress)ssc.socket().getLocalSocketAddress();
    }
}
