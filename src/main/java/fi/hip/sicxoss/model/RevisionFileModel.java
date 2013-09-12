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

/**
 * RevisionFileModel
 *
 * @author koskela
 */
public class RevisionFileModel 
    extends FileModel {

    private static final Logger log = Logger.getLogger(RevisionFileModel.class);

    protected RevisionFileModel(String name, Event e, ShareModel share) {
        super(name, share);
        processUpdate(e);
        created = modified;
    }

    @Override
    protected void setParent(CollectionModel parent) {
        // this.parent = parent;
        this.parent = parent;
        // getModel().registerPath(this); // .. why?
    }

    @Override
    protected void changeDataId(DataID newId) {
        dataId = newId;
    }

    @Override
    public boolean store(InputStream in, long length, String contentType) {
        return false;
    }

    @Override
    public void delete() {
    }
}
