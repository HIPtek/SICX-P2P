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
 * ContactModel
 *
 * A model representing a contact
 * @author koskela
 */
public class ContactModel 
    extends ContentModel {

    private static final Logger log = Logger.getLogger(ContactModel.class);

    private User user;
    private String prefix;

    protected ContactModel(User user, MountableModel share, String prefix) {
        super(user.getId(), share);
        this.user = user;
        this.prefix = prefix;
    }

    protected ContactModel(User user, MountableModel share) {
        super(user.getId(), share);
        this.user = user;
        this.prefix = "";
    }
 
    /*
    protected ContactModel(User user, MountableModel share) {
        super(user.getName() + " (" + user.getFullName() + ")", share);
        this.user = user;
    }

    protected ContactModel(LocalUser user, MountableModel share) {
        super("Public profile (" + user.getFullName() + ")", share);
        this.user = user;
    }
    */
    
    protected ContactModel(String name, MountableModel share) {
        super(name, share);
    }

    @Override
    public String getName() {
        String ret = "";
        if (user == null)
            ret = super.getName();
        else if (user instanceof LocalUser)
            ret = "Public profile (" + user.getFullName() + ")";
        else
            ret = user.getName() + " (" + user.getFullName() + ")";

        if (prefix != null)
            ret = prefix + ret;
        return ret;
    }

    public boolean store(InputStream in, long length, String contentType) {

        if (user != null)
            return false;

        try {
            // ok .. store 
            byte[] data = DataUtil.toBuf(in);
            User u = User.fromData(new String(data));
            ContactCollectionModel ccm = (ContactCollectionModel)getParent();

            // if this is a share.. add to it!
            ccm.removeChild(this);
            this.user = ccm.getLocalUser().addContact(u, null);
            if (ccm.getModel() instanceof ShareModel) {
                ShareModel sm = (ShareModel)ccm.getModel();
                log.info("adding " + u + " to share " + sm);
                sm.inviteUser(this.user);
            }
            return true;
        } catch (Exception ex) {
            log.error("Exception while creating: " + ex);
            ex.printStackTrace();
            delete();
        }

        return false;
    }
        
    public User getUser() {
        return user;
    }

    public InputStream getDataStream(long start, long finish) 
        throws Exception {
        
        if (user != null)
            return new ByteArrayInputStream(user.getData().getBytes());
        else
            return null;
    }

    public void setContentType(String contentType) {
    }

    public String getContentType() {
        return "text/plain";
    }

    public long getLength() {
        if (user != null)
            return user.getData().length();
        else
            return 0;
    }

    @Override
    public void delete() {
        // ??
        super.delete();
    }

    @Override
    public ItemModel duplicate(String newName, CollectionModel parent) {
        return null;
    }
}
