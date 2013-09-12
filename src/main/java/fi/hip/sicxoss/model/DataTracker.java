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

import java.util.Date;

/**
 * Tracks the status of the blobs, so we know which ones we can
 * drop.
 */
interface DataTracker {

    // when last accessed
    public Date getAccessed();
    // when it was added
    public Date getAdded();
    // when it was last acquired
    public Date getAcquired();
    // when it was imported
    public Date getStored();

    // the number of entries using it right now
    public int getUseCounter();

    // the id of this thing
    public DataID getDataId();
}
