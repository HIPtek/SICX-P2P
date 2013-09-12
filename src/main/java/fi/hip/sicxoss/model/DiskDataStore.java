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
import java.io.*;
import java.security.*;

import org.apache.log4j.Logger;

import fi.hip.sicxoss.io.DataUtil;
import fi.hip.sicxoss.LocalGateway;

/**
 * DataStore
 *
 * The data 'vault'. Maintains data blobs for local caching. Can be,
 * in principle, attached to any shared folder, although for security
 * reasons each share should have its own.
 *
 * We could also imagine this being totally non-local; residing, for
 * instance, on a dropbox account. The DataStore should actually be
 * just an interface, with a 'LocalDataStore' implementation + any
 * other we may think of.
 *
 * We might also consider using multiple of these for a single
 * share. Local cache vs. the dropbox one.
 *
 * .. and build some sort of MultiStoreManager (implements DataStore)
 * that takes care of managing the cache and deciding how files get
 * uploaded.
 * @author koskela
 */
public class DiskDataStore 
    implements DataStore {

    public static final String CHECKSUM_ALG = "SHA-1";

    private Logger log;
    
    // an index is unneccesary as the filenames are formed from the
    // hash directly.
    private File root;
    private String name;

    // for the metadata of each item
    private Hashtable<DataID, DiskDataTracker> trackers;
    private volatile boolean batchUpdateInProgress;

    // the quotas
    private long quotaMax; // the hard-limit MAX we are allowed to store
    private long quotaOptimal; // the optimum we strive for

    /**
     * Tracks the status of the blobs, so we know which ones we can
     * drop.
     */
    static class DiskDataTracker 
        implements Serializable, 
                   DataTracker {

        static final long serialVersionUID = -8320710576891624201L;

        // when last accessed
        public Date getAccessed() { return accessed; }
        // when it was added
        public Date getAdded() { return added; }
        // when it was last acquired
        public Date getAcquired() { return acquired; }
        // when it was imported
        public Date getStored() { return stored; }
        
        // the number of entries using it right now
        public int getUseCounter() { return useCounter; }
        
        // the id of this thing
        public DataID getDataId() { return id; }

        // when last accessed
        protected Date accessed;
        // when it was added
        protected Date added;
        // when it was last acquired
        protected Date acquired;
        // when it was imported
        protected Date stored;

        // the number of entries using it right now
        protected int useCounter;

        // the id of this thing
        protected DataID id;

        // file, cached
        protected transient File file;

        public DiskDataTracker(DataID id) {
            this.id = id;
            this.added = new Date();
        }

        public synchronized File getFile(File root) {
            if (file == null)
                file = new File(root.getAbsolutePath() + File.separator + id.getFileName());
            return file;
        }
    }

    private synchronized void checkQuota(DiskDataTracker immune) {

        long storeSize = 0;
        ArrayList<DiskDataTracker> unused = new ArrayList();
        ArrayList<DiskDataTracker> used = new ArrayList();
        for (DiskDataTracker dt : trackers.values()) {
            if (dt != immune)
                if (dt.useCounter < 1)
                    unused.add(dt);
                else
                    used.add(dt);
            
            if (dt.getFile(root).exists())
                storeSize += dt.getFile(root).length();
        }

        // how we prioritize data blocks:
        Comparator cmp = new Comparator<DiskDataTracker>() {
            public int compare(DiskDataTracker o1,
                               DiskDataTracker o2) {
                
                // prioritizing logic: those that *have* been accessed, or LRU
                if (o1.accessed == null && o2.accessed != null)
                    return -1;
                if (o1.accessed != null && o2.accessed == null)
                    return 1;
                if (o1.accessed != null && o2.accessed != null)
                    return o1.accessed.compareTo(o2.accessed);
                
                // acquired? for simplicity, just use stored..
                if (o1.stored != null && o2.stored != null)
                    return o1.stored.compareTo(o2.stored);
                if (o1.stored == null)
                    return -1;
                return 1;
            }
            
            public boolean equals(DiskDataTracker obj) {
                return false;
            }
        };

        if (storeSize > quotaOptimal && quotaOptimal > -1) {
            // sort the unused ones, start deleting.
            Collections.sort(unused, cmp);
            while (unused.size() > 0 && storeSize > quotaOptimal) {
                DiskDataTracker dt = unused.get(0);
                if (dt.getFile(root).exists()) {
                    log.debug("quota trimming: deleting " + dt.id + ", " + dt.getFile(root).length() + " bytes");
                    storeSize -= dt.getFile(root).length();
                    dt.getFile(root).delete();
                }
                unused.remove(0);
                trackers.remove(dt);
            }
        }
        
        if (storeSize > quotaMax && quotaMax > -1) {
            // sort the used ones, start deleting.
            Collections.sort(used, cmp);
            while (used.size() > 0 && storeSize > quotaMax) {
                DiskDataTracker dt = used.get(0);
                if (dt.getFile(root).exists()) {
                    log.debug("quota overrun: deleting " + dt.id + ", " + dt.getFile(root).length() + " bytes");
                    storeSize -= dt.getFile(root).length();
                    dt.getFile(root).delete();
                }
                used.remove(0);
            }
        }

        log.info("quota check complete. total entries: " + trackers.size() + " occupying " + storeSize + " bytes");
        saveTrackers();
    }

    public DiskDataStore(String name) {
        log = Logger.getLogger(getClass().getName() + ":" + name);
        this.name = name;
        this.trackers = new Hashtable();
        this.batchUpdateInProgress = false;
    }

    @Override
    public String toString() {
        return "DataStore " + name + " at " + root;
    }
   
    /* init a new, empty data store */
    public void init(String path, Properties p, LocalGateway gw) 
        throws Exception {
        
        root = new File(path);
        if (root.exists())
            throw new Exception("Root directory already exists: " + path);
        
        quotaMax = -1; // unlimited
        quotaOptimal = -1; // unlimited

        root.mkdirs();
        saveTrackers();
        p.setProperty("quota_max", "" + quotaMax);
        p.setProperty("quota_optimal", "" + quotaOptimal);
    }

    private synchronized void saveTrackers() {
        DataUtil.writeObject(trackers, root.getAbsolutePath() + File.separator + "trackers.db");
    }

    private File createNewBlobFile() 
        throws Exception {
        
        File ret = File.createTempFile("tmp", "blob", root);
        return ret;
    }

    private File getBlob(DataID id) {
        
        String fname = root.getAbsolutePath() + File.separator + id.getFileName();
        return new File(fname);
    }

    /* from datastore */

    /* load an existing store from the given path */
    @Override
    public void load(String path, Properties p, LocalGateway gw) 
        throws Exception {

        root = new File(path);
        if (!root.exists())
            throw new Exception("Root directory is missing: " + path);

        trackers = (Hashtable)DataUtil.readObject(root.getAbsolutePath() + File.separator + "trackers.db");
        if (trackers == null) {
            log.warn("missing trackers");
            trackers = new Hashtable();
        }
        quotaMax = Long.parseLong(p.getProperty("quota_max"));
        quotaOptimal = Long.parseLong(p.getProperty("quota_optimal"));
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public synchronized DataTracker getDataTracker(DataID id) {
        
        DiskDataTracker dt = trackers.get(id);
        if (dt == null) {
            dt = new DiskDataTracker(id);
            trackers.put(id, dt);
            saveTrackers();
        }

        return dt;
    }

    @Override
    public DataID store(InputStream in, long length) {

        try {
            MessageDigest digest = MessageDigest.getInstance(CHECKSUM_ALG);
            DigestInputStream dis = new DigestInputStream(in, digest);

            File outfile = createNewBlobFile();
            FileOutputStream fos = new FileOutputStream(outfile);

            int total = DataUtil.transfer(dis, fos);
            fos.close();
            dis.close();

            byte[] hash = dis.getMessageDigest().digest();
            String checksum = DataUtil.toHex(hash);

            DataID ret = new DataID(checksum, total);

            // check for overwrites
            File targetFile = getBlob(ret);
            if (targetFile.exists())
                log.warn("file already exists!");
            outfile.renameTo(targetFile);
            
            DiskDataTracker dt = (DiskDataTracker)getDataTracker(ret);
            if (dt.stored == null)
                dt.stored = new Date();
            
            checkQuota(dt);
            return ret;
        } catch (Exception ex) {
            log.warn("error while storing: " + ex);
            return null;
        }
    }

    @Override
    public synchronized void release(DataID id) {

        DiskDataTracker dt = (DiskDataTracker)getDataTracker(id);
        dt.useCounter--;
        if (dt.useCounter == 0)
            log.info("data item " + id + " is not used anymore");
        log.info("released data " + id + ", counter: " + dt.useCounter);

        if (!batchUpdateInProgress)
            checkQuota(null);
    }

    @Override
    public synchronized void acquire(DataID id) {

        DiskDataTracker dt = (DiskDataTracker)getDataTracker(id);
        dt.useCounter++;
        dt.acquired = new Date();
        log.info("acquired data " + id + ", counter: " + dt.useCounter);
        
        if (!batchUpdateInProgress)
            checkQuota(dt);
    }

    @Override
    public synchronized void initBatchUpdate() {
        
        batchUpdateInProgress = true;
        for (DiskDataTracker dt : trackers.values()) {
            dt.useCounter = 0;
        }
    }

    @Override
    public synchronized void batchUpdateComplete() {

        batchUpdateInProgress = false;
        checkQuota(null);
    }

    @Override
    public InputStream getStream(DataID id, long start, long finish) 
        throws Exception {

        DiskDataTracker dt = (DiskDataTracker)getDataTracker(id);
        dt.accessed = new Date();
        saveTrackers();
        File f = getBlob(id);
        if (f.exists()) {
            FileInputStream fis = new FileInputStream(f);
            return fis;
        } else
            return null;
    }

    @Override
    public boolean hasData(DataID id, long start, long finish) {
        return getBlob(id).exists();
    }

    @Override
    public File importFile(DataID id, File file) {
        
        File targetFile = new File(root.getAbsolutePath() + File.separator + id.getFileName());
        if (targetFile.exists())
            log.warn("file already exists!");
        file.renameTo(targetFile);

        // todo: check whether the contents of the file actually
        // matches the id!

        DiskDataTracker dt = (DiskDataTracker)getDataTracker(id);
        if (dt.stored == null)
            dt.stored = new Date();
        
        checkQuota(dt);
        return targetFile;
    }

    @Override
    public void deleteStore() {
        // delete all the data?
        log.info("deleting the disk data store " + getName());
        for (DiskDataTracker dt : trackers.values()) {
            File f = dt.getFile(root);
            if (f != null && f.exists())
                f.delete();
        }

        File f = new File(root.getAbsolutePath() + File.separator + "trackers.db");
        f.delete();
    }
}
