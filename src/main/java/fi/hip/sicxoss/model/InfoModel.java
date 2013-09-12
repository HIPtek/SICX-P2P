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

import java.io.*;

import org.apache.log4j.Logger;

/**
 * InfoModel
 *
 * A model representing a single file in the shared folder.
 * @author koskela
 */
public class InfoModel 
    extends ContentModel {

    private static final Logger log = Logger.getLogger(InfoModel.class);

    private String fullname;
    private ItemModel item;
    
    protected InfoModel(String name, ItemModel item, MountableModel share) {
        super(name, share);

        this.fullname = item.getFullName() + PATH_SEP + name;
        this.item = item;
    }

    public boolean store(InputStream in, long length, String contentType) {
        return false;
    }

    public InputStream getDataStream(long start, long finish) 
        throws Exception {

        String info = item.getFullName() + "\n";
        info += "Unique ID: " + item.getId() + "\n";
        info += "Created: " + item.getCreated() + " by " + item.getCreator() + "\n";
        info += "Last modified: " + item.getModified() + " by " + item.getModifier() + "\n";
        
        if (item instanceof FileModel) {
            FileModel m = (FileModel)item;
            
            info = "File " + info;
            info += "Content type: " + m.getContentType() + "\n";
            info += "Size: " + m.getLength() + "\n";
            DataTracker dt = m.getShare().getDataTracker(m.getDataId());

            info += "Contents (" + m.getDataId() + ") details:\n";
            info += "First known: " + dt.getAdded() + "\n";
            info += "First used by a file: " + dt.getAcquired() + "\n";
            info += "Last access: " + dt.getAccessed() + "\n";
            info += "Stored (by us): " + dt.getStored() + "\n";
            info += "Files currently using the data: " + dt.getUseCounter() + "\n";

        } else if (item instanceof FolderModel) {
            FolderModel m = (FolderModel)item;
            info = "Folder " + info;
            

        }
        return new ByteArrayInputStream(info.getBytes());
    }

    public void setContentType(String contentType) {
    }

    public String getContentType() {
        return "text/plain";
    }

    public long getLength() {
        return -1;
    }

    @Override
    public String getFullName() {
        return fullname;
    }

    @Override
    public ItemModel duplicate(String newName, CollectionModel parent) {
        return null;
    }
}
