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
 *
 * edit: updated to reflect the thoughts above. An interface, with the
 * 'normal' disk store implementation separate.
 * @author koskela
 */
public interface DataStore {

    public void start() throws Exception;
    public void stop();
    public void load(String path, Properties p, LocalGateway gw)
        throws Exception;
    
    public String getName();

    public void deleteStore();

    public DataID store(InputStream in, long length)
        throws Exception;
    
    /**
     * Signals that the data is no longer used by a single file. Each
     * call will decrease the counter, leading the to data being
     * removed if no one is longer using it.
     */
    public void release(DataID id);
    
    /**
     * Signals that the data is now used by a single file. May be
     * called multiple times, which will increase the 'counter' on the
     * data.
     */
    public void acquire(DataID id);

    /**
     * Resets the data-use counters. No actions will be taken on the
     * data until a 'batchUpdateComplete' is called
     */
    public void initBatchUpdate();
    public void batchUpdateComplete();
    
    public InputStream getStream(DataID id, long start, long finish)
        throws Exception;

    public boolean hasData(DataID id, long start, long finish);

    /**
     * Moves a complete, ready, resource into the store.
     */
    public File importFile(DataID id, File file);

    /**
     * returns the data tracker for the given data
     */
    public DataTracker getDataTracker(DataID id);

}
