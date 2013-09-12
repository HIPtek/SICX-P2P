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

public abstract class DataSocketHandler 
    extends SocketHandler {

    public static final int NETBUF_SIZE = 64 * 1024;
        
    private static final Logger log = Logger.getLogger(DataSocketHandler.class);

    protected SocketChannel sc;
    protected boolean closed = false;
    protected boolean shouldClose = false;
    private SocketAddress sa;
    
    private ArrayList<ByteBuffer> inbuf;
    private ArrayList<ByteBuffer> outbuf;
        
    private long timeOut;

    // for the streaming
    private volatile int streamCount = -1;
    private DataSocketStreamer currentStreamer;
    private List<DataSocketStreamer> queuedStreamers;
    protected DataSocketStreamReceiver currentReceiver;
    private long bytesToDrain = 0;

    /**
     * Interface for classes that want to send continuous, prioritized
     * data (such as streams)
 * @author koskela
     */
    public interface DataSocketStreamer {

        /**
         * Called when the socket needs more data to stream. Return
         * false if we do not want to stream anymore. This method does
         * not need to stream any data right now, but should do so at
         * some point, or else the socket will be stuck waiting for
         * more streamed data.
         */
        public boolean dataRequired();
        
        /**
         * Called to signal that we may stream now.
         * Return false if we've changed our minds.
         */
        public boolean streamingStarted();
        
        /**
         * Called to signal that the streaming has been stopped.
         */
        public void streamingEnded();
    }

    /**
     * Interface for classes that want to be notified of new data
     * before any other handling
     */
    public interface DataSocketStreamReceiver {
        
        /**
         * @param data the data, or null if EOF
         * @return how many bytes were used. this will be !=
         * data.length only when the receiver does not want more data.
         */
        //public int dataReceived(byte[] data);

        /**
         * Notifies that data is now available.
         */
        public void dataAvailable(DataSocketHandler conn);
    }

    private void reinit() {
        this.inbuf = new ArrayList();
        this.outbuf = new ArrayList();
        this.closed = false;
        this.shouldClose = false;
        this.timeOut = -1;
        this.queuedStreamers = new ArrayList();
    }
    
    /**
     * create from a existing socket
     */
    public DataSocketHandler(SocketChannel sc) 
        throws Exception {
        reinit();

        this.sc = sc;
        this.sa = sc.socket().getRemoteSocketAddress();
        sc.configureBlocking(false);
        setChannel(sc);
    }

    /**
     * create an outgoing socket
     */
    public DataSocketHandler(SocketAddress sa) 
        throws Exception {

        this.sa = sa;
        reconnect();
    }

    /**
     * create an outgoing socket
     */
    public DataSocketHandler(String str) 
        throws Exception {
        this(a2s(str));
    }

    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress)this.sa;
    }

    public String getRemoteAddressAsString() {
        InetSocketAddress isa = (InetSocketAddress)this.sa;
        return isa.getAddress().getHostAddress() + ":" + isa.getPort();
    }

    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress)this.sc.socket().getLocalSocketAddress();
    }

    protected synchronized void reconnect() 
        throws Exception {

        reinit();
        if (key != null) {
            key.cancel();
            eng.removeHandler(this);
        }

        gotConnecting();
        log.info("connecting to " + sa);
        this.sc = SocketChannel.open();
        setChannel(sc);
        sc.configureBlocking(false);
        
        boolean conn = sc.connect(sa);
        if (eng != null) {
            eng.addHandler(this);
        }
        if (conn)
            handleConnect();
    }

    public boolean isConnected() {
        return sc.isConnected() && !closed;
    }
    
    public boolean isConnectionPending() {
        return sc.isConnectionPending();
    }


    /**
     * called when there's new data to be read
     */
    public abstract void gotData();

    public abstract void gotClose();
    
    public abstract void gotConnected();

    public abstract void gotConnecting();

    /* */

    /* sets the timeout for this socket - the exact time.
     * setting it to -1 disables it
     */
    protected void setTimeOut(long timeInMillis) {
        this.timeOut = timeInMillis;
    }

    protected void setTimeOutAfter(long millis) {
        setTimeOut(System.currentTimeMillis() + millis);
    }

    protected void disableTimeOut() {
        setTimeOut(-1);
    }

    protected long getTimeOut() {
        return timeOut;
    }

    /* called when the timeout fires */
    protected void fireTimeOut() {
        log.warn("timeout fired!");
        this.close();
    }

    public void close() {

        disableTimeOut();
        shouldClose = true;
        if (outbuf.size() == 0 || !sc.isConnected()) {
            closed = true;
            try {
                sc.socket().close();
            } catch (Exception ex) {}
            try {
                sc.close();
            } catch (Exception ex) {}

            if (key != null) {
                key.cancel();
                eng.removeHandler(this);
                key = null;
            }
            gotClose();
        }
    }

    public int write(String str) {
            
        return write(str.getBytes());
    }

    public int write(byte[] data) {

        return write(data, 0, data.length, false);
    }

    public int stream(byte[] data) {

        return write(data, 0, data.length, true);
    }

    public int write(byte[] data, int off, int len) {

        return write(data, off, len, false);
    }

    public int stream(byte[] data, int off, int len) {

        return write(data, off, len, true);
    }

    public synchronized int write(byte[] data, int off, int len, boolean priority) {
            
        if (shouldClose)
            return -1;
            
        ByteBuffer buf = ByteBuffer.wrap(data, off, len);

        if (priority) {
            if (streamCount < 0) {
                log.error("bad sockethandler state: we are not streaming");
                //throw new Exception("Bad socket state: we are not streaming");
                return -1;
            }
            outbuf.add(streamCount, buf);
            streamCount++;
        } else
            outbuf.add(buf);
        
        if (outbuf.size() == 1)
            updateInterests();
        return data.length;
    }

    public int read(byte[] buf) {

        return read(buf, 0, buf.length);
    }

    public synchronized int read(byte[] buf, int p, int l) {

        int startp = p;
        int max = p + l;
        while (p < max && inbuf.size() > 0) {
            ByteBuffer bb = inbuf.get(0);
            int r = bb.remaining();
            if (r > (max - p))
                r = max - p;
            bb.get(buf, p, r);
            p += r;
            if (!bb.hasRemaining())
                inbuf.remove(0);
        }
            
        if (p == 0 && closed)
            p = -1; // eof
        return p - startp;
    }

    public synchronized int available() {
        int total = 0;
        for (ByteBuffer bb : inbuf) {
            total += bb.remaining();
        }
        return total;
    }

    /*
     * selector-related methods
     *
     */

    private void performDrain() {
        
        byte[] buf = new byte[NETBUF_SIZE];
        while (bytesToDrain > 0) {
            int limit = (int)bytesToDrain;
            if (limit > buf.length)
                limit = read(buf);
            else
                limit = read(buf, 0, limit);
            if (limit > 0)
                bytesToDrain -= limit;
            else
                break;
        }
    }

    /**
     * 'Drains' the connection from the next x bytes
     */
    public synchronized boolean drainStream(long bytes) {
        bytesToDrain += bytes;
        performDrain();
        return true;
    }

    public synchronized boolean setStreamReceiver(DataSocketStreamReceiver rec) {

        if (currentReceiver != null)
            return false;

        currentReceiver = rec;
        if (available() > 0)
            currentReceiver.dataAvailable(this);
        return true;
    }

    public synchronized boolean removeStreamReceiver(DataSocketStreamReceiver rec) {

        if (currentReceiver != rec)
            return false;
        currentReceiver = null;
        if (available() > 0)
            gotData();
        return true;
    }

    public synchronized void queueForStreaming(DataSocketStreamer streamer) {

        // add to queue or start immediately, if nothing is going on
        if (outbuf.size() == 0 && currentStreamer == null) {
            currentStreamer = streamer;
            streamCount = 0;
            currentStreamer.streamingStarted();
        } else
            queuedStreamers.add(streamer);
    }


    protected synchronized void handleRead() 
        throws Exception {

        //log.debug("handle read");

        ByteBuffer bb = null;
        if (inbuf.size() > 0) {
            bb = inbuf.get(inbuf.size()-1);
        }
            
        int r = 1;
        int total = 0;
        while (r > 0) {
            if (bb == null || (bb.limit() == bb.capacity())) {
                bb = ByteBuffer.allocate(NETBUF_SIZE);
                bb.limit(0);
                inbuf.add(bb);
            }
            int op = bb.position();
            int ol = bb.limit();
            bb.position(ol);
            bb.limit(bb.capacity());
            r = sc.read(bb);
            bb.limit(ol + (r > 0? r : 0));
            bb.position(op);
            if (r > 0)
                total += r;
        }

        if (total > 0 && bytesToDrain > 0) {
            performDrain();
            total = available();
        }
            
        if (total > 0) {
            if (currentReceiver != null) {
                currentReceiver.dataAvailable(this);
            } else {
                gotData();
            }
        }
            
        if (r < 0) {
            try {
                sc.socket().close();
                sc.close();
            } catch (Exception ex) {}
            closed = true;
            gotClose();
            updateInterests();
        } 
        // todo: update interests if we want to take a break from receiving stuff!
    }


    protected synchronized void handleWrite()
        throws Exception {

        while (outbuf.size() > 0 || streamCount == 0) {

            // check if the currently streaming client has more to add
            if (streamCount == 0 && !currentStreamer.dataRequired()) {
                currentStreamer.streamingEnded();
                currentStreamer = null;
                streamCount = -1;
            }

            if (streamCount == 0 && outbuf.size() > 0)
                log.debug("ignoring queued packets, waiting for streaming data..");
            
            // if we are streaming, but have no data, do nothing.
            if (streamCount == 0 || outbuf.size() == 0)
                break;
            
            ByteBuffer bb = outbuf.get(0);
            int w = sc.write(bb);
            if (!bb.hasRemaining()) {
                outbuf.remove(0);
                if (streamCount > 0)
                    streamCount--;
            } else if (w < 1)
                break;
        }
            
        // if we have nothing to send, re-think our status
        if (outbuf.size() == 0 || streamCount == 0) {
            if (shouldClose)
                close();
            else {
                // initiate another streamer, if we have one queued.
                if (currentStreamer == null && queuedStreamers.size() > 0) {
                    currentStreamer = queuedStreamers.remove(0);
                    streamCount = 0;
                    currentStreamer.streamingStarted();
                }
            }

            updateInterests();
        }
    }

    protected void handleConnect() 
        throws Exception {

        log.debug("handle connect: " + sc.isConnected() + "/" + sc.isConnectionPending());
        if (!sc.isConnected())
            if (sc.isConnectionPending()) {
                try {
                    sc.finishConnect();
                } catch (Exception ex) {
                    log.warn("error completing the connection: " + ex);
                    close();
                }
            }

        if (sc.isConnected()) {
            gotConnected();
            updateInterests();
        }
    }

    protected int getInterestSet() {
        int ret = 0;
        if (closed)
            ret = 0;
        else if (sc.isConnectionPending())
            ret = SelectionKey.OP_CONNECT;
        else
            ret = (closed? 0 : (SelectionKey.OP_READ | (outbuf.size() > 0 && streamCount != 0? SelectionKey.OP_WRITE : 0)));
        return ret;
    }
}
