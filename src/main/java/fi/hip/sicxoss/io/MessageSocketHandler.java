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

import fi.hip.sicxoss.io.message.*;
import fi.hip.sicxoss.ident.*;

/**
 * A SocketHandler that allows complete message blocks to be sent over
 * the wire.
 */
public abstract class MessageSocketHandler 
    extends DataSocketHandler {
        
    private static final Logger log = Logger.getLogger(MessageSocketHandler.class);

    private int msgLen = -1;

    /**
     * create from a existing socket
     */
    public MessageSocketHandler(SocketChannel sc) 
        throws Exception {
        super(sc);
    }

    /**
     * create an outgoing socket
     */
    public MessageSocketHandler(SocketAddress sa) 
        throws Exception {
        super(sa);
    }

    /**
     * create an outgoing socket
     */
    public MessageSocketHandler(String str) 
        throws Exception {
        this(a2s(str));
    }

    /** Data stream that encapsulates the traffic */
/**
 * MessageSocketHandler class.
 *
 * @author koskela
 */
    public class MessageDataOutputStream 
        extends DataOutputStream {
        
        private MessageSocketHandler handler;
        private ByteArrayOutputStream bos;
        private boolean priority;

        public MessageDataOutputStream(MessageSocketHandler handler, ByteArrayOutputStream bos, 
                                       boolean priority) {
            super(bos);
            this.bos = bos;
            this.handler = handler;
            this.priority = priority;
        }

        public void close()
            throws IOException {
            
            super.close();
            if (bos != null) {
                bos.close();
                byte[] data = bos.toByteArray();
                handler.sendMessage(NetworkMessage.fromData(data), priority);
                bos = null;
            }
        }
    }

    /** Data stream that de-encapsulates the traffic */
    public class MessageDataInputStream 
        extends DataInputStream {
        
        private NetworkMessage msg;

        public MessageDataInputStream(NetworkMessage msg) {
            super(new ByteArrayInputStream(msg.getData()));
            this.msg = msg;
        }

        public byte[] getData() {
            return msg.getData();
        }

        public NetworkMessage getMessage() {
            return msg;
        }
    }
    
    /**
     * Either this or the gotMessageDataStream should be overridden!
     */
    public void gotMessage(NetworkMessage msg) {

        MessageDataInputStream dis = new MessageDataInputStream(msg);
        try {
            gotDataStream(dis);
        } catch (Exception ex) {
            log.warn("exception while handling message data stream: " + ex);
            ex.printStackTrace();
            // close();
        } finally {
            try {
                dis.close();
            } catch (Exception e2) {
            }
        }
    }
    
    public void gotDataStream(DataInputStream in)
            throws Exception {

        // we shouldn't be here if everything is implemented ok!
    }

    /**
     * Use either send message or the get-datastream + send.
     */
    public synchronized void sendMessage(NetworkMessage msg, boolean priority) {

        // len as 2 bytes, then the message
        byte[] data = msg.getData();
        byte[] len = new byte[2];
        len[0] = (byte)(data.length & 0xff);
        len[1] = (byte)((data.length >> 8) & 0xff);
        write(len, 0, len.length, priority);
        write(data, 0, data.length, priority);
    }

    public DataOutputStream sendDataStream() {
        return sendDataStream(false);
    }

    public DataOutputStream sendDataStream(boolean priority) {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        return new MessageDataOutputStream(this, bos, priority);
    }

    public DataOutputStream sendForwardingDataStream(User to, LocalUser from) 
        throws Exception {
        return sendForwardingDataStream(to, from, false);
    }
        
    public DataOutputStream sendForwardingDataStream(User to, LocalUser from, boolean priority) 
        throws Exception {
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new MessageDataOutputStream(this, bos, priority);
        dos.writeUTF(NetworkMessage.MessageType.FORWARD.toString());
        dos.writeUTF(to.getId().toString()); // to whom
        dos.writeUTF(from.getId().toString()); // from whom
        return dos;
    }

    public void gotData() {

        while (currentReceiver == null) {
            if (msgLen < 0) {
                if (available() < 2)
                    return;
                byte[] num = new byte[2];
                read(num);
                msgLen = (num[0] & 0xff) +
                    ((num[1] & 0xff) << 8);
                //log.debug("we got a message length " + msgLen);
            }
            
            if (available() < msgLen)
                return;
            
            byte[] data = new byte[msgLen];
            read(data);
            NetworkMessage msg = NetworkMessage.fromData(data);
            msgLen = -1;
            gotMessage(msg);
            // loop until no more messages in queue
        }
    }

    /**
     * Calls to forward everything from this point on to the given
     * DataSocketHandler, with an optional header put in front.
     */
    public boolean forwardTo(MessageSocketHandler sink,
                             int bytes, NetworkMessage header) {

        StreamForwarder sf = new StreamForwarder(this, sink, bytes);
        return sf.start(header);
    }

    /**
     * Small utility class for forwarding a stream. Keeps a buffer
     * which it fills as data becomes available, and empties when the
     * sink is ready to receive more.
     */
    public class StreamForwarder 
        implements DataSocketStreamReceiver,
                   DataSocketStreamer {

        private MessageSocketHandler src;
        private MessageSocketHandler sink;
        private byte[] buf = new byte[NETBUF_SIZE];
        private int bytes;
        private int available;
        private boolean sinkReady;
        private boolean srcReady;
        private NetworkMessage header;

        public StreamForwarder(MessageSocketHandler source,
                               MessageSocketHandler sink,
                               int bytes) {
            this.src = source;
            this.sink = sink;
            this.bytes = bytes;
            this.available = 0;
            this.sinkReady = false;
            this.srcReady = false;
        }

        public boolean start(NetworkMessage header) {
            this.header = header;
            if (!src.setStreamReceiver(this)) {
                bytes = 0;
                return false;
            }
             
            sink.queueForStreaming(this);
            return true;
        }
            
        public synchronized void dataAvailable(DataSocketHandler conn) {
            
            // if we can fit it into the buffer, read it.
            srcReady = false;
            if (sinkReady && available < buf.length && bytes > 0) {
                int l = buf.length - available;
                if (l > bytes)
                    l = bytes;
                int r = conn.read(buf, available, l);
                //log.debug("got " + r + " bytes, still " + bytes + " to send");
                if (r > -1) {
                    available += r;
                    bytes -= r;

                    // there's probably more
                    if (r == l)
                        srcReady = true;
                    
                    // are we done yet?
                    if (bytes < 1)
                        removeStreamReceiver(this);
                } else
                    bytes = 0;
            } else
                srcReady = true;

            if (available > 0 && sinkReady)
                dataRequired();
        }
        
        public synchronized boolean dataRequired() {

            if (available > 0) {
                sinkReady = false;
                sink.stream(buf, 0, available); // we should wait buf really has been written!
                available = 0;
                //log.debug("wrote " + available + " bytes");
            } else {
                sinkReady = true;
                if (bytes > 0 && srcReady)
                    dataAvailable(src);
            }
            return bytes > 0 || available > 0;
        }
        
        public boolean streamingStarted() {
            log.info("starting forwarding stream for " + bytes + " bytes");
            if (header != null)
                sink.sendMessage(header, true);
            return bytes > 0;
        }
        
        public void streamingEnded() {
            log.info("ending forwarding stream for " + bytes + " bytes");
            // notify? nah, we'll just wait for the garbage collector.
        }
    }

}
