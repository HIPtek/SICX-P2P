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
package fi.hip.sicxoss.milton;

import java.util.*;
import com.bradmcevoy.http.*;

import fi.hip.sicxoss.model.*;

public abstract class ItemResource 
    implements CopyableResource, 
               DeletableResource, //DigestResource, 
               GetableResource, 
               //LockableResource, LockingCollectionResource, 
               MoveableResource, 
               PropFindableResource,
               Resource
{

    private String prefix;

    public static ItemResource fromModel(ItemModel m, String prefix) {

        ItemResource ret = null;
        if (m != null) {
            if (m instanceof FolderModel)
                ret = new DirResource((FolderModel)m);
            else if (m instanceof CollectionModel)
                ret = new DirResource((CollectionModel)m);
            else if (m instanceof FileModel)
                ret = new FileResource((FileModel)m);
            else if (m instanceof ContentModel)
                ret = new ContentResource((ContentModel)m);
        }
        if (ret != null)
            ret.setPrefix(prefix);
        return ret;
    }

    protected ItemModel model;
    
    public ItemResource(ItemModel model) {
        this.model = model;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    // Check the given credentials, and return a relevant object if accepted.
    public Object authenticate(String user, String password) {
        return "anon";
    }

    // Determine if a redirect is required for this request, and if so return the URL to redirect to.
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        return true;
    }
    
    // Determine if a redirect is required for this request, and if so
    // return the URL to redirect to.
    public String checkRedirect(Request request) {
        return null;
    }
    
    // The date and time that this resource, or any part of this
    // resource, was last modified.
    public Date getModifiedDate() {
        return model.getModified();
    }

    // Note that this name MUST be consistent with URL resolution in
    // your ResourceFactory If they aren't consistent Milton will
    // generate a different href in PropFind responses then what
    // clients have request and this will cause either an error or no
    // resources to be displayed
    public String getName() {

        String ret = model.getName();
        if (ret.length() == 0 && prefix.length() > 0)
            ret = prefix.substring(1);
        return ret;
    }

    // Return the security realm for this resource.
    public String getRealm() {
        return null;
    }

    // Returning a null value is allowed, and disables the ETag field.
    public String getUniqueId() {
        return model.getId().toString();
    }

    /* prop find */

    public Date getCreateDate() {
        return model.getCreated();
    }


    /* copyable */
    
    public void copyTo(CollectionResource toCollection, String name) {

        //
        ItemResource dest = (ItemResource)toCollection;
        model.getModel().createCopy(name, model, (CollectionModel)dest.model);
    }

    /* deletable */

    // Non-recursive delete of this resource.
    public void delete() {
        model.getModel().removeItem(model);
    }

    /* movable */

    public void moveTo(CollectionResource rDest, String name) {
        
        ItemResource dest = (ItemResource)rDest;
        model.getModel().moveItem(model, name, (CollectionModel)dest.model);
    }
}
