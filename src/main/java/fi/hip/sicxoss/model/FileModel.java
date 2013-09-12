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
import fi.hip.sicxoss.io.DataUtil;
import fi.hip.sicxoss.ident.*;

/**
 * FileModel
 *
 * A model representing a single file in the shared folder.
 * @author koskela
 */
public class FileModel 
    extends ContentModel {

    private static final Logger log = Logger.getLogger(FileModel.class);

    protected DataID dataId;
    protected String contentType;

    protected FileModel(String name, ShareModel share) {
        super(name, share);
        this.contentType = "none";
    }

    protected FileModel(String name, ItemID id, ShareModel share) {
        super(name, id, share);
        this.contentType = "none";
    }

    public ShareModel getShare() {
        return (ShareModel)getModel();
    }

    public void processUpdate(Event event) {

        DataID newId = null;
        try {
            newId = DataID.parse(event.getProperty("data"));
        } catch (Exception ex) {
            log.error("invalid data id: " + event.getProperty("data"));
        }

        changeDataId(newId);
        contentType = event.getProperty("ct");
        setModified(DataUtil.stringToDate(event.getProperty("modified")));
    }
    
    protected void changeDataId(DataID newId) {
        
        // if the content really was updated, then forewarn the storage!
        if (newId != null && (dataId == null || !newId.equals(dataId))) {
            if (newId != null)
                getShare().getStorage().acquire(newId);
            if (dataId != null)
                getShare().getStorage().release(dataId);
            dataId = newId;
        }
    }

    public void setDataId(DataID dataId, String contentType) {

        changeDataId(dataId);
        this.contentType = contentType;
        modified();
        getShare().fileUpdated(this);
    }

    public DataID getDataId() {
        return this.dataId;
    }

    @Override
    public boolean store(InputStream in, long length, String contentType) {

        DataID id = null;
        try {
            id = getShare().getStorage().store(in, length);
        } catch (Exception ex) {
            log.error("exception while storing data: " + ex);
        }
        if (id != null) {
            setDataId(id, contentType);
            return true;
        } else
            return false;
    }

    @Override
    public InputStream getDataStream(long start, long finish) 
        throws Exception {
        
        if (finish < 0)
            finish = dataId.getLength();
        if (dataId != null)
            return getShare().getStorage().getStream(dataId, start, finish);
        return null;
    }

    @Override
    public void setContentType(String contentType) {
        this.contentType = contentType;

        getShare().fileUpdated(this);
    }

    @Override
    public String getContentType() {
        
        int p = this.contentType.indexOf(',');
        if (p > -1)
            return this.contentType.substring(0, p);
        return this.contentType;
    }

    @Override
    public long getLength() {
        if (dataId == null)
            return 0;
        return dataId.getLength();
    }

    @Override
    public void delete() {
        super.delete();
        if (dataId != null)
            getShare().getStorage().release(dataId);
    }

    @Override
    public ItemModel duplicate(String newName, CollectionModel parent) {

        // we use the new parent's share, as it may differ from the current one
        ContentModel ret = (ContentModel)parent.getModel().createFile(newName, null, parent);
        if (ret != null && ret instanceof FileModel)
            ((FileModel)ret).setDataId(this.dataId, this.contentType);
        return ret;
    }
}
