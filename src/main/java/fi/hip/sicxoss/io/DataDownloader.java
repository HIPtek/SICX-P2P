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
 * A downloader for a single resource. This is used by the storage
 * elements / shares to 'transparently' fetch resources from remote
 * peers.
 *
 * It uses the shares to find peers who might have the data, and the
 * connectionmanagers to communicate with them. It will act as a
 * blocking InputStream for clients, feeding the data forward as it is
 * received.
 * @author koskela
 */
public class DataDownloader 
    implements DataSocketHandler.DataSocketStreamReceiver {

    private static final Logger log = Logger.getLogger(DataDownloader.class);
    
    public static final long MAX_DOWNLOAD_WAIT = 20 * 1000;

    // store where to deposit the data, when done
        
    // inputstream from which the data can be read. or actually an outputstream
    // where to write it as we progress.

    public DataID dataId;
    public ShareModel model;
    private List<DataDownloaderSourceStream> streams;
    private List<DataDownloaderSourceStream> waitingStreams;
    private List<CountingFileOutputStream> parts;

    private BandwidthMonitor monitor;

    // tmp buffer for receiving data
    private byte tmpbuf[] = new byte[DataSocketHandler.NETBUF_SIZE];
    private Hashtable<DataSocketHandler, long[]> streamingLimits;

    public DataDownloader(DataID dataId, ShareModel model) {
        this.dataId = dataId;
        this.model = model;
        this.streams = new ArrayList();
        this.streamingLimits = new Hashtable();
        this.monitor = new BandwidthMonitor();

        waitingStreams = new ArrayList();
        parts = new ArrayList();
        requests = new ArrayList();
    }
       
    // a simple data holder to keep track of what we have requested
    private class DownloadRequest {

        public long start;
        public long finish;
        public Date sent;
        public Date respondedTo;
        
        public DownloadRequest(long start, long finish) {
            this.start = start;
            this.finish = finish;
            this.sent = new Date(0);
        }

        public boolean isBeingServiced() {
            return respondedTo != null;
        }

        public long length() {
            return finish - start;
        }
        
        public String toString() {
            return " -- req " + start + ":" + finish + ", sent " + sent + ", resp: " + respondedTo;
        }
    }

    private List<DownloadRequest> requests;
 
    public static final int REQUEST_TIMEOUT = 60 * 1000;

    /**
     * initiates a download for the specific (or more..) data
     */
    public synchronized void requestData(long start, long finish) {

        log.info("we are requesting " + dataId + " from " + start + " to " + finish);
        monitor.start();

        /* future revisions:

           the downloaders should, in many ways, act as normal
           resources. if someone requests a chunk that we've downloaded, then
           respond and provide that to the requestor
        */

        // check whether we already have this data. if so, do nothing

        // check whether we already have requested that data chunk. if so, do nothing
        // if we've requested parts of it (either from the start or end), request
        // only the missing chunks.

        // for each user .. find someone who has the data, within that range.

        // after finding, request a temporary connection 'capture' from the connectino manager
        // this might turn out to be a lookup connection, so we should establish a new one

        // stream. every x bytes or so there could be some 'sign' whether to stop the streaming
        // => we should have some sort of 'stop() / pause()' call to this download

        // feed the data to the clients

        // when complete, return the connection to the connection manager
            
        // note: we need support for this in the lookupserver also!

        /* approaches: 

           1. (old): go through the current pending requests. if we
           have overlapping, combine everything into a single one.

           2. (new): go through the current pending requests. if we
           have overlapping, substract those from this request. this
           might end up into a zero-byte request, which we just
           discard.

           The thing with the second approach is that it is much
           easier to keep track of for which parts we have gotten ACK
           & ready-to-send- type responses.

           The first would require that we 1. find the request within
           the ACK fits (it may have been combined with others. and
           2. re-issue? a request. No, actually, we already have
           re-issued the request. So we just quietly ignore it, right?

           .. but what if we already have gotten some of the data .. ?

           we would need to constantly keep track of what we have
           vs. what we are requesting.
        */

        /* approaches, take 2: 

           when requesting, we create a request object and send a
           query for that data range. when receiving a reply for data
           within that range, we separate that part into a separate
           request. possibly creating 3 requests (one for the data
           before, one for after and the matching part).

           we mark the matching part as 'being taken care of'.

           and send re-requests for the other parts.

           we reply to the responses with some sort of 'ok, give it to
           me'. these have a timeout, after which the data range is
           returned to the pool of unanswered requests.
          
           take 3:

           the above gets really complex if we keep track of what
           we've requested and what we are donwloading.

           simpler approach: combine request trackings all into a big
           blob.
           
           when getting response, then .. should we do the splitting?


         */

        // check first whether we already have the data.


        // in this loop we try to resize the new requests so that it would not
        // overlap with any of the old ones.
        List <DownloadRequest> overlapping = new ArrayList();
        List <DownloadRequest> newreqs = new ArrayList();
        newreqs.add(new DownloadRequest(start, finish));
        for (DownloadRequest req : newreqs) {
            for (int i=0; i < requests.size(); i++) {
                DownloadRequest dr = requests.get(i);
                if (dr.finish > req.start && dr.start < req.finish) {
                    // ok, this one overlaps somehow. decrease the limits
                    // of this one
                    
                    if (req.start >= dr.start && req.finish <= dr.finish) { // this one is totally immersed.
                        req.start = 0;
                        req.finish = 0;
                    } else if (dr.start >= req.start && dr.finish <= req.finish) { // this one totally immerses it.
                        // if this one is not being serviced, then we can just engulf it
                        if (!dr.isBeingServiced()) {
                            requests.remove(dr);
                            i--;
                        } else {
                            // else.. create a new request, and shorten this one!
                            newreqs.add(new DownloadRequest(dr.finish, req.finish));
                            req.finish = dr.start;
                        }
                    } else if (req.finish > dr.finish)
                        req.start = dr.finish;
                    else if (req.start < dr.start)
                        req.finish = dr.start;
                
                    // these all overlap somehow
                    if (!overlapping.contains(dr))
                        overlapping.add(dr);
                }
            }
            
            // don't add it if it is < 1 bytes long
            if (req.length() > 0) {
                requests.add(req);
                overlapping.add(req);
            }
        }

        // go through and re-issue what we have been waiting for for too long
        for (DownloadRequest dr : overlapping)
            if (System.currentTimeMillis() - dr.sent.getTime() > REQUEST_TIMEOUT && !dr.isBeingServiced()) {
                if (model.broadcastDataQuery(dataId, dr.start, dr.finish))
                    dr.sent = new Date();
            }
        
        /* the old 
        // loop through the old requests. combine all that will be overlapping somehow.
        DownloadRequest req = new DownloadRequest(start, finish);
        List <DownloadRequest> overlapping = new ArrayList();
        for (DownloadRequest dr : requests) {
            if (dr.finish >= req.finish && dr.start <= req.start) {
                // we have one that engulfs our range completely. use it as-is.
                // and quit looping. we might / might not have picked up some overlapping
                // already, but that doesn't matter.
                req = dr;
                break;
            } else if (dr.finish > req.start && dr.start < req.finish) {
                // ok, this one overlaps somehow. get the extreme limits
                if (dr.finish > req.finish)
                    req.finish = dr.finish;
                if (dr.start < req.start)
                    req.start = dr.start;
                if (dr.sent.before(req.sent))
                    req.sent = dr.sent;
                overlapping.add(dr);
            }
        }
        
        for (DownloadRequest dr : overlapping)
            requests.remove(dr);

        if (!requests.contains(req))
            requests.add(req);
        
        // check this. we might have had an overlapping that had just recently been requested for
        if (System.currentTimeMillis() - req.sent.getTime() > REQUEST_TIMEOUT) {
            if (model.broadcastDataQuery(dataId, start, finish))
                req.sent = new Date();
        }
        */
    }

    /* sends an appropriate query to a contact .. */
    public boolean sendDataQuery(User contact) {
        // todo: we should ask only for what we need!
        boolean sent = false;
        for (DownloadRequest dr : requests) {
            if (model.sendDataQuery(dataId, dr.start, dr.finish, contact))
                sent = true;
        }
        return sent;
    }

    private void printReqs(List<DownloadRequest> reqs, String header) {
        System.out.println("******** " + header);
        for (DownloadRequest dr : reqs) {
            System.out.println("    from " + dr.start + " to " + dr.finish + " requested " + dr.sent + ", resp: " + dr.respondedTo);
        }
    }

    public synchronized void processDataResponse(User contact, long start, long finish) {
        
        // newreqs == the requests to get, now, from this user!
        //printReqs(requests, "before processing");
        List <DownloadRequest> newreqs = new ArrayList();
        newreqs.add(new DownloadRequest(start, finish));
        for (DownloadRequest req : newreqs) {
            for (int i=0; i < requests.size(); i++) {

                DownloadRequest dr = requests.get(i);
                
                // in this loop, the new request is kept: we change
                // the size of all others in order to fit this new one
                // here.

                // ..unless the old one is being taken care of; in
                // that case, we resize this one.

                if (dr.finish > req.start && dr.start < req.finish) {
                    
                    if (req.start >= dr.start && req.finish <= dr.finish) { // this one is totally immersed.
                        if (dr.isBeingServiced()) {
                            // zero this new request then.
                            req.start = 0;
                            req.finish = 0;
                        } else {
                            // ok. we need to split the old one up!
                            DownloadRequest newr = new DownloadRequest(req.finish, dr.finish);
                            if (newr.length() > 0) {
                                newr.sent = dr.sent;
                                // todo: copy over the list of people who has it etc..?
                                requests.add(newr);
                            }

                            req.sent = dr.sent;
                            dr.finish = req.start;
                            if (dr.length() < 1) {
                                requests.remove(dr);
                                i--;
                            }
                        }
                    } else if (dr.start >= req.start && dr.finish <= req.finish) { // this one totally immerses it.
                        // if this one is not being serviced, then we can just engulf it
                        if (!dr.isBeingServiced()) {
                            requests.remove(dr);
                            i--;
                        } else {
                            // else.. create a new request to-get, and shorten this one!
                            DownloadRequest newr = new DownloadRequest(dr.finish, req.finish);
                            if (newr.length() > 0) {
                                newreqs.add(newr);
                            }
                            req.finish = dr.start;
                        }
                    } else if (req.finish > dr.finish) {
                        if (!dr.isBeingServiced()) {
                            dr.finish = req.start;
                        } else {
                            req.start = dr.finish;
                        }
                    } else if (req.start < dr.start) {
                        if (!dr.isBeingServiced()) {
                            dr.start = req.finish;
                        } else {
                            req.finish = dr.start;
                        }
                    }
                }
            }
            
            // don't add it if it is < 1 bytes long
            if (req.length() > 0) {
                requests.add(req);
            }
        }
        
        // remove any zero-byte requests!
        for (int i=0; i < requests.size(); i++) {
            DownloadRequest dr = requests.get(i);
            if (dr.length() < 1) {
                requests.remove(dr);
                i--;
            }
        }

        //printReqs(requests, "after processing");

        // we assume that all data we have got / are getting has a request
        for (DownloadRequest req : newreqs) {
            if (req.length() > 0) {
                if (model.requestDataBlock(contact, dataId, req.start, req.finish))
                    req.respondedTo = new Date();
            } else
                log.warn("we have a zero-length request!");
        }
        //printReqs(requests, "at end");
    }

    public InputStream getStream(long start, long finish) {
        DataDownloaderSourceStream ddss = new DataDownloaderSourceStream(this, start, finish);
        streams.add(ddss);
        requestData(start, finish);
        return ddss;
    }
 
    private void streamClosed(DataDownloaderSourceStream ddss) {
        // log.info("download stream was closed!");
        streams.remove(ddss);
        if (streams.size() == 0) {
            log.info("no more clients for this download.");
        }
    }

    /**
     * A file output stream for a single part of a file that keeps
     * track of the position of this part within the whole file
     */
    public class CountingFileOutputStream
        extends FileOutputStream {

        // from where this part starts
        public long start;
            
        // where are we at now
        public long limit;

        // the file
        public File targetFile;

        public CountingFileOutputStream(File file, long start) 
            throws FileNotFoundException {
            super(file);

            this.start = start;
            this.limit = start;
            this.targetFile = file;
        }

        /* only write with bufs will be used */
            
        @Override
            public void write(int b)
            throws IOException {
            super.write(b);
            limit++;
        }
                  
        @Override
            public void write(byte[] b)
            throws IOException {
            super.write(b);
            limit += b.length;
        }
            
        @Override
            public void write(byte[] b,
                              int off,
                              int len)
            throws IOException {
            super.write(b, off, len);
            limit += len;
        }
    }

    /**
     * A file input stream that keeps track of the position
     */
    public class CountingFileInputStream
        extends FileInputStream {

        // from where this part starts
        public long pos;
            
        // the file
        public File targetFile;

        public CountingFileInputStream(File file, long pos) 
            throws FileNotFoundException {
            super(file);

            this.pos = pos;
            this.targetFile = file;
        }

        @Override
            public int read()
            throws IOException {
            int ret = super.read();
            if (ret != -1)
                pos++;
            return ret;
        }

        @Override
            public int read(byte[] b)
            throws IOException {
            int ret = super.read(b);
            if (ret > 0)
                pos += ret;
            return ret;
        }
            
        @Override
            public int read(byte[] b,
                            int off,
                            int len)
            throws IOException {
            int ret = super.read(b, off, len);
            if (ret > 0)
                pos += ret;
            return ret;
        }

        @Override
            public long skip(long n)
            throws IOException {
            long ret = super.skip(n);
            if (ret > 0)
                pos += ret;
            return ret;
        }
    }

    private File createPartFile(long start) 
        throws IOException {

        return File.createTempFile(dataId.toString() + "_" + start + "_", "part", model.getStorageRoot());
    }

    public synchronized void dataBlockGot(DataInputStream in, long start)
        throws Exception {
                
        int r = 0;
        while ((r = in.read(tmpbuf)) > 0) {
            dataGot(tmpbuf, start, r);
            start += r;
        }
    }

    public void dataStreamGot(DataSocketHandler conn, long start, long finish)
        throws Exception {
        
        long[] limits = new long[3];
        limits[0] = start;
        limits[1] = finish;
        limits[2] = start;
        streamingLimits.put(conn, limits);
        if (!conn.setStreamReceiver(this)) {
            streamingLimits.remove(conn);
            throw new Exception("multiple concurrent streams in a single connection!");
        }
    }

    public synchronized void dataAvailable(DataSocketHandler conn) {


        long[] limits = streamingLimits.get(conn);
        int limit = (int)(limits[1] - limits[0]);
        while (limit > 0) {
            if (limit > tmpbuf.length)
                limit = conn.read(tmpbuf);
            else
                limit = conn.read(tmpbuf, 0, limit);
            if (limit > 0) {
                try {
                    dataGot(tmpbuf, limits[0], limit);
                } catch (Exception ex) {
                    log.error("error while handling streamed bytes: " + ex);
                }
                limits[0] += limit;
            } else
                return;
            limit = (int)(limits[1]-limits[0]);
        }
        
        log.info("we have received data streamed to us from " + limits[2] + " to " +  limits[1]);
        streamingLimits.remove(conn);
        conn.removeStreamReceiver(this);
    }

    // internal: processing data that is received
    private synchronized void dataGot(byte[] buf, long pos, int len) 
        throws Exception {
            
        //log.debug("got data from " + pos + ", len " + len);
        // todo: forts: update the requests! remove this data chunk
        // from them.
        
        /*
          keeping track of requests, part 2:

          we need to have some sort of 'lastgot' time on the requests so that we know
          whether we are actively downloading something.
          
          every time we get some data, update it

          (old):
          the request limits should be decreased subsequently.
          
          (old): just ignore any responses that do not match a
          current, as we have sent out requests for the new already.

          - race? requests keep changing the data faster than we can
            react?

          - should we here split the request again back into what it
            was? request the data that they said they had?

            - although they will respond soon with having the larger
              set also. what then?

            - request only the start and end?

          -> so basically getting *any* sort of data response, we just
             split up the requests so we can mark that area.
             
             - ok. works for me.

             = todo: change back int othe old model!
          


         */


        //int len = buf.length;
        long limit = pos + len;
                        
        // find a part-stream that matches what we want.
        CountingFileOutputStream partOut = null;
        int toWrite = len;
        for (CountingFileOutputStream out : parts) {
            if (out.limit >= pos && limit > out.limit) { 
                // we have something to append to this stream
                toWrite = (int)(len - (out.limit - pos));
                partOut = out;
                break;
            } else if (out.start <= pos && out.limit >= limit) {
                // this shouldn't happen, but it means that we already have the 
                // data. just discard it
                toWrite = 0;
                break;
            }
        }
            
        // write the data
        if (toWrite > 0) {
            if (partOut == null) {
                // we aren't able to append, create a new part!
                partOut = new CountingFileOutputStream(createPartFile(pos), pos);
                parts.add(partOut);
            }
            partOut.write(buf, len-toWrite, toWrite);
            monitor.update(toWrite);
        }

        // see if we should combine some of the parts..
        List<CountingFileOutputStream> obsolete = new ArrayList();
        for (CountingFileOutputStream out : parts) {
            if (out == partOut)
                continue;
                
            if (partOut.start <= out.start && partOut.limit >= out.limit) {
                // ok, we've found a piece that is obsolete,
                // superceded by us.  lets dump it!
                    
                obsolete.add(out);
            } else if (partOut.limit >= out.start && partOut.limit < out.limit) {
                // ok, we should append that file into us!
                // starting from ..
                long skip = partOut.limit - out.start;
                out.close();
                FileInputStream fis = new FileInputStream(out.targetFile);
                fis.skip(skip);
                DataUtil.transfer(fis, partOut);    
                obsolete.add(out);
            }
        }
            
        // delete the obsolete ones!
        for (CountingFileOutputStream out : obsolete) {
            out.close();
            parts.remove(out);
            out.targetFile.delete();
        }
            
        //log.debug("we are now from " + partOut.start + " until " + partOut.limit);
        if (partOut.start == 0 && partOut.limit == dataId.getLength()) {
            // we have the whole file!
            monitor.stop();
            partOut.close();
            partOut.targetFile = model.downloadComplete(this, partOut.targetFile);

            log.info("download complete with avg speed " + monitor.avgSpeed());
        }

        // feed into the waiting streams. most data will be passed this way
        List<DataDownloaderSourceStream> readyStreams = new ArrayList();
        //synchronized (waitingStreams) {
        for (DataDownloaderSourceStream ds : waitingStreams) {
            // is this waiting for data that we've just received?
            if (ds.pos < limit && (ds.pos + ds.max) > pos) {
                int start = (int)(ds.pos - pos); // from where to start the data within the buf
                int clen = len - start; // how much data we have
                if (clen > ds.max) // but if this is more than the client wants..
                    clen = ds.max;
                System.arraycopy(buf, start, ds.buf, ds.off, clen);
                ds.read = clen;
                readyStreams.add(ds);
            }
        }
        
        // do this separately not to avoid concurrent modification exceptions
        for (DataDownloaderSourceStream ds : readyStreams)
            waitingStreams.remove(ds);
        this.notifyAll();
    }
    

    /** 
     * Called from the streams. read max bytes bytes starting from
     * from into the buffer at position off.
     *
     * @return 0 if there's nothing available right now, -1 if
     * we're at the end of the stream
     */
    /*
      private int read(byte[] buf, long from, long bytes, long off) {
            
      // if not enough data ..
      synchronized {waitingStreams) {
                

      return 0;
      }
    */

    private synchronized void read(DataDownloaderSourceStream ds) {
            
        // check if we have a cached filestream:
        try {
            if (ds.fis != null && ds.fis.targetFile.exists()) {
                if (ds.fis.pos == ds.pos && ds.fis.available() > 0) {
                    //log.debug("using the cached stream!");
                    ds.read = ds.fis.read(ds.buf, ds.off, ds.max);
                    return;
                } else {
                    ds.fis.close();
                    ds.fis = null;
                }
            }
        } catch (Exception ex) {
            log.error("exception while reading from file stream: " + ex);
            ds.fis = null;
        }

        // do we have any data already.. ?
        long climit = ds.pos + ds.max;
        for (CountingFileOutputStream out : parts) {
            if (out.start < climit && out.limit > ds.pos) {
                // found one. read as much as we can from it.
                int skip = (int)(ds.pos - out.start);
                    
                // we cache the fileinputstream, as 99.99% of the cases
                // will ask for data from the same file, in order.
                try {
                    ds.fis = new CountingFileInputStream(out.targetFile, out.start);
                    ds.fis.skip(skip);
                    ds.read = ds.fis.read(ds.buf, ds.off, ds.max);
                    return;
                } catch (Exception ex) {
                    log.error("exception while reading file part: " + ex);
                    ds.fis = null;
                    return;
                }
            }
        }
            
        // no?

        waitingStreams.add(ds);
        //log.debug("no data available, taking a nap then.");
        try {
            long waittime = MAX_DOWNLOAD_WAIT - (System.currentTimeMillis() - ds.lastBytesGot);
            while (waittime > 0 && waitingStreams.contains(ds)) {
                this.wait(waittime);
                waittime = MAX_DOWNLOAD_WAIT - (System.currentTimeMillis() - ds.lastBytesGot);
            }
            waitingStreams.remove(ds);
        } catch (Exception ex) {}
        //log.debug("good morning! i've been served " + ds.read + " bytes.");
    }

    /**
     * Called from the streams. returns the number of bytes available
     * from the given position.
     */
    private int available(long pos) {
        return 0;
    }

    /**
     * The blocking streams that the clients of this download
     * use. These are by *no* means thread safe!
     */
    public class DataDownloaderSourceStream 
        extends InputStream {

        private DataDownloader dl;

        /* data used by the downloader to know which streams to feed */
        public long pos; // the position we're at in the total data space
        public int max; // how much we want to get at this point
        public byte[] buf; // where to store it
        public int off; // at which point in the buf should it be deposited
        public int read; // downloader fills this: how much we got
        public CountingFileInputStream fis;
        public long lastBytesGot;

        // until where we want to read, totally
        private long limit;
            
        public DataDownloaderSourceStream(DataDownloader dl, long start, long finish) {
            this.dl = dl;
            this.pos = start;
            this.limit = finish;
            lastBytesGot = System.currentTimeMillis();
        }

        private int readFromDownloader(byte[] buf, int bytes, int off) {
            // this makes it not thread safe!
            this.buf = buf;
            this.max = bytes;
            this.off = off;
            this.read = 0;

            // call the blocking read.
            dl.read(this);

            if (this.read > 0)
                lastBytesGot = System.currentTimeMillis();
            else {
                // if we're over a maximum, report that!
                if ((System.currentTimeMillis() - lastBytesGot) > MAX_DOWNLOAD_WAIT) {
                    log.info("data retrieval timeout. we have nothing to offer!");
                    this.read = -1;
                }
            }
            return this.read;
        }

        @Override
            public int read()
            throws IOException {
                
            if (pos >= limit)
                return -1;
                
            byte[] buf = new byte[1];
            int r = readFromDownloader(buf, 1, 0);
            if (r < 0)
                return -1;
            pos++;
            return buf[0];
        }
                       
        @Override
            public int read(byte[] b)
            throws IOException {

            if (pos >= limit)
                return -1;

            // how much to read
            int tot = b.length;
            if ((tot + pos) > limit)
                tot = (int)(limit - pos);
                
            int r = readFromDownloader(b, tot, 0);
            if (r < 0)
                return -1;
            pos += r;
            return r;
        }
            
        @Override
            public int read(byte[] b,
                            int off,
                            int len)
            throws IOException {

            if (pos >= limit)
                return -1;

            // how much to read
            int tot = len;
            if ((tot + pos) > limit)
                tot = (int)(limit - pos);
                
            // read
            int r = readFromDownloader(b, tot, off);
            if (r < 0)
                return -1;
            pos += r;
            return r;
        }
            
        @Override
            public long skip(long n)
            throws IOException {
                
            // this is easy
            pos += n;
            if (pos > limit) {
                n -= (pos - limit);
                pos = limit;
            }
            return n;
        }
            
        @Override
            public int available()
            throws IOException {
            return dl.available(pos);
        }
            
        @Override
            public void close()
            throws IOException {
            // report to the downloader
            dl.streamClosed(this);
        }
            
        @Override
            public void mark(int readlimit) {
            // nothing
        }
            
        @Override
            public void reset()
            throws IOException {
            // nothing
        }
            
        @Override
            public boolean markSupported() {
            return false;
        }
    }
}
