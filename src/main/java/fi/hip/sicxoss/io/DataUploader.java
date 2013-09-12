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

import java.util.*;
import java.io.*;

import org.apache.log4j.Logger;

import fi.hip.sicxoss.ident.*;
import fi.hip.sicxoss.model.*;
import fi.hip.sicxoss.io.message.*;


/**
 * DataUploader class.
 *
 * @author koskela
 */
public class DataUploader 
    implements DataSocketHandler.DataSocketStreamer {

    private static final Logger log = Logger.getLogger(DataUploader.class);

    private User contact;
    private DataID dataId;
    private long start; 
    private long finish;
    private InputStream in;
    private long tempFinish;
    private long sessionLimit = 1024 * 500; // per stream
    private byte[] buf = new byte[DataSocketHandler.NETBUF_SIZE];
    private MessageSocketHandler conn = null;
    private ShareModel share;

    private BandwidthMonitor monitor;

    public DataUploader(User contact,
                        DataID dataId, long start, long finish, ShareModel share) {

        this.start = start;
        this.finish = finish;
        this.dataId = dataId;
        this.contact = contact;
        this.share = share;
        this.monitor = new BandwidthMonitor();
    }

    public void start() 
        throws Exception {
            
        log.info("upload started");
        monitor.start();

        in = share.getStream(dataId, start, finish);

        // send small data items as a block, others as stream.
        long limit = finish-start;
        if (limit < 32 * 1024) {
            DataOutputStream dos = share.getConnectionManager().getContactDataStream(contact, false, false);
            if (dos != null) {
                dos.writeUTF(NetworkMessage.MessageType.DATA_BLOCK.toString());
                dos.writeUTF(share.getId().toString()); // the share id
                dos.writeUTF(dataId.toString());
                dos.writeLong(start);
                dos.writeLong(limit + start);
                    
                limit = DataUtil.transfer(in, dos, (int)limit);
                dos.close();
                start += limit;
                log.info("sent " + limit + " bytes");
                monitor.update((int)limit);
                streamingEnded();
            } else
                throw new Exception("error sending bytes, could not get contact data stream");
        } else {
            conn = share.getConnectionManager().getContactConnection(contact, false);
            if (conn != null)
                conn.queueForStreaming(this);
            else
                throw new Exception("no connection to contact available!");
        }
    }


    /* from data socket streamer */
        
    public boolean dataRequired() {

        //log.debug("more data required..");
        try {
            if (start < tempFinish) {
                int limit = (int)(tempFinish-start);
                if (limit > buf.length)
                    limit = buf.length;
                    
                int r = in.read(buf, 0, limit);
                if (r > 0) {
                    conn.stream(buf, 0, r);
                    start += r;
                    monitor.update(r);
                    return true;
                }
            }
        } catch (Exception ex) {
            log.error("error while streaming: " + ex);
        }
        return false;
    }

    public boolean streamingStarted() {
        //log.debug("stream session started");
            
        // set how much we are asking to stream in this session
        try {
            tempFinish = finish;
            if (tempFinish - start > sessionLimit)
                tempFinish = start + sessionLimit;
                
            // stream the header. we add the recipient so we are
            // able to stream stuff in exactly the same way,
            // whether through to lookup or direct.
            DataOutputStream dos = conn.sendDataStream(true);
            dos.writeUTF(NetworkMessage.MessageType.STREAM_START.toString());
            dos.writeUTF(contact.getId().toString());
            dos.writeUTF(share.getLocalUser().getId().toString());
            dos.writeUTF(share.getId().toString()); // the share id
            dos.writeUTF(dataId.toString());
            dos.writeLong(start);
            dos.writeLong(tempFinish);
            dos.close();
            return true;
        } catch (Exception ex) {
            log.error("error while streaming: " + ex);
        }
        return false;
    }
        
    public void streamingEnded() {
        if (start < finish) {
            log.debug("stream session ended, queueing for more.");
            conn.queueForStreaming(this);
        } else {
            log.info("upload ended.");
            monitor.stop();
            try {
                in.close();
            } catch (Exception ex) {}
                
            // notify observers or the share ..
            share.uploadComplete(this);
        }
    }
}
