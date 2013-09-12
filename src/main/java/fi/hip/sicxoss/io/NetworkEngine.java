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

import org.apache.log4j.Logger;

/**
 * NetworkEngine
 *
 * The lookup server used in the SICX FOSS
 * @author koskela
 */
public class NetworkEngine 
    extends Thread {

    private static final Logger log = Logger.getLogger(NetworkEngine.class);
    
    private Selector selector;
    private ArrayList<SocketHandler> handlers;
    private volatile boolean waitForNewHandlers;
    private List<Thread> clients;
    
    public NetworkEngine() {
        handlers = new ArrayList();
        clients = new ArrayList();
    }

    protected Selector getSelector() {
        return selector;
    }

    public void init() 
        throws Exception {

        System.setProperty("java.net.preferIPv4Stack", "true");
        selector = SelectorProvider.provider().openSelector();

        // we could use the network thread for this also:
        Timer t = new Timer(true);
        t.scheduleAtFixedRate(new TimerTask() {


                public void run() {
                    try {
                        runCleanup();
                    } catch (Exception ex) {
                        log.warn("exception while doing cleanup: " + ex);
                    }
                }

                private void runCleanup() {
                    
                    long now = System.currentTimeMillis();
                    ArrayList<DataSocketHandler> expired = new ArrayList();
                    for (SocketHandler sh : handlers) {
                        if (sh instanceof DataSocketHandler) {
                            DataSocketHandler dsh = (DataSocketHandler)sh;
                            long to = dsh.getTimeOut();
                            if (to > -1 && to < now)
                                expired.add(dsh);
                        }
                    }

                    for (DataSocketHandler dsh : expired) {
                        try {
                            log.debug("firing timeout");
                            dsh.fireTimeOut();
                        } catch (Exception ex) {
                            log.error("exception while firing timeout: " + ex);
                        }
                    }
                }
            }, 1000, 1000);

    }

    public void addHandler(SocketHandler sh) 
        throws Exception {

        sh.setEngine(this);

        synchronized (handlers) {
            waitForNewHandlers = true;
            getSelector().wakeup();
            sh.setKey(sh.getChannel().register(getSelector(), sh.getInterestSet(), sh));
            handlers.add(sh);
        }
    }

    /**
     * adds a 'client' to the network engine. clients are objects that
     * serve no purpose unless this network engine is alive
     */
    public void addClient(Thread t) {
        synchronized (clients) {
            clients.add(t);
        }
        if (isAlive() && !t.isAlive())
            t.start();
    }

    public void run() {

        log.info("running the network engine thread .. ");
        
        // start all the clients
        synchronized (clients) {
            for (Thread t : clients)
                try {
                    if (!t.isAlive())
                        t.start();
                } catch (Exception e) {
                    log.warn("error starting client: " + e);
                }
        }

        try {
            selectLoop();
        } catch (Exception ex) {
            log.error("exception in the select loop: " + ex);
            ex.printStackTrace();
        }
        log.info("exiting the network engine thread .. ");
    }

    protected void removeHandler(SocketHandler sh) {
        log.debug("removing channel " + sh);
        handlers.remove(sh);
    }

    public void selectLoop()
        throws Exception {

        int keys = 0;
	while (true) {
            // we might get strange write-while-reading and other things
            // therefore we have a one second timeout on these just to avoid
            // too long lags
            if ((keys = selector.select(1000)) > 0) { 

                // we use an iterator to avoid concurrent edits to the list
                Set<SelectionKey> readyk = selector.selectedKeys();
                Iterator<SelectionKey> i = readyk.iterator();
                while (i.hasNext()) {
                    SelectionKey sk = i.next();
                    i.remove();
                    
                    try {
                        SocketHandler sh = (SocketHandler)sk.attachment();
                        //while (true) { // we might get new reads / writes while serving
                        if (sk.isValid() && sk.isReadable())
                            sh.handleRead();
                        if (sk.isValid() && sk.isWritable())
                            sh.handleWrite();
                        if (sk.isValid() && sk.isConnectable())
                            sh.handleConnect();
                        if (sk.isValid() && sk.isAcceptable())
                            sh.handleAccept();
                        // else
                        //     break;
                        // }
                    } catch (Exception ex) {
                        log.warn("error while processing channel: " + ex);
                        ex.printStackTrace();
                    }
                }
            }
            
            if (waitForNewHandlers) {
                synchronized (handlers) {
                    waitForNewHandlers = false;
                }
            }
	}
    }
}
