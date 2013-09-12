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
import java.io.*;
import com.bradmcevoy.http.*;

import fi.hip.sicxoss.model.*;
import fi.hip.sicxoss.LocalGateway;

/**
 * A simple resource for listing the available shares
 * @author koskela
 */
public class ShareListResource 
    implements CollectionResource, 
               //PutableResource,
               MakeCollectionableResource,
               //CopyableResource, 
               //DeletableResource, //DigestResource, 
               GetableResource, 
               //LockableResource, LockingCollectionResource, 
               //MoveableResource, 
               PropFindableResource,
               Resource
{

    private Map<String, MountableModel> models;
    private String name;
    private LocalGateway lg;

    public ShareListResource(String name, Map<String, MountableModel> models, LocalGateway lg) {
        this.models = models;
        this.name = name;
        this.lg = lg;
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
        return new Date();
    }

    // Note that this name MUST be consistent with URL resolution in
    // your ResourceFactory If they aren't consistent Milton will
    // generate a different href in PropFind responses then what
    // clients have request and this will cause either an error or no
    // resources to be displayed
    public String getName() {
        return name;
    }

    // Return the security realm for this resource.
    public String getRealm() {
        return null;
    }

    // Returning a null value is allowed, and disables the ETag field.
    public String getUniqueId() {
        return null;
    }

    /* prop find */

    public Date getCreateDate() {
        return new Date();
    }

    /* collection resource */

    public Resource child(String childName) {

        MountableModel model = models.get("/" + childName);
        if (model != null)
            return ItemResource.fromModel(model.getItem(null), "/" + childName);
        return null;
    }
    
    public List<Resource> getChildren() {
        List<Resource> ret = new ArrayList();
        for (String k : models.keySet()) {
            MountableModel model = models.get(k);
            ret.add(ItemResource.fromModel(model.getItem(null), k));
        }
        return ret;
    }
    
    /* getable resource */

    // The length of the content in this resource.
    public Long getContentLength() {
        return null;
    }
    
    // Given a comma separated listed of preferred content types acceptable for a client, return one content type which is the best.
    public String getContentType(String accepts) {
        return "text/html";
    }
    
    //How many seconds to allow the content to be cached for, or null if caching is not allowed The provided auth object allows this method to determine an appropriate caching time depending on authenticated context.
    public Long getMaxAgeSeconds(Auth auth) {
        return null;
    }
    
    // Send the resource's content using the given output stream.
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType) {

        PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
        pw.println("<h1>Shares</h1><p><ul>");
        for (String k : models.keySet()) {
            pw.println("<li><a href='"+k+"'>" + k + "</a></li>");
        }
        pw.println("</ul>");
        pw.flush();
    }

    // make collection
    @Override
    public CollectionResource createCollection(String name) {
    
        try {
            ShareModel share = lg.createShare(name, "/" + name);
            share.start();
            return (CollectionResource)ItemResource.fromModel(share.getItem(null), "/" + name);
        } catch (Exception ex) {
        }
        return null;
    }
}
