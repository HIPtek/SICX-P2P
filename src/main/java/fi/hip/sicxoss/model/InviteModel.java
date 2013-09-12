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
import fi.hip.sicxoss.ident.*;
import fi.hip.sicxoss.io.*;

import org.apache.log4j.Logger;

/**
 * InviteModel
 *
 * try and guess.
 * @author koskela
 */
public class InviteModel 
    extends ContentModel {

    private static final Logger log = Logger.getLogger(InviteModel.class);

    private LocalUser user;
    private Invite invite;

    protected InviteModel(LocalUser user, Invite invite, MountableModel share) {
        super(invite.toString() + " for " + user.getFullName(), share);
        this.user = user;
        this.invite = invite;
    }

    @Override
    public boolean store(InputStream in, long length, String contentType) {
        return false;
    }

    @Override
    public InputStream getDataStream(long start, long finish) 
        throws Exception {

        // create the data representation ..

        return new ByteArrayInputStream(invite.getDescription().getBytes());
    }

    @Override
    public String getContentType() {
        return "text/plain";
    }

    @Override
    public void setContentType(String ct) {
    }

    @Override
    public long getLength() {
        return invite.getDescription().getBytes().length;
    }
    
    public Invite getInvite() {
        return invite;
    }
    
    public LocalUser getUser() {
        return user;
    }

    @Override
    public ItemModel duplicate(String newName, CollectionModel parent) {
        return null;
    }
}
