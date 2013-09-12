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

/**
 * DirResource class.
 *
 * @author koskela
 */
public class DirResource 
    extends ItemResource
    implements CollectionResource, 
               PutableResource,
               MakeCollectionableResource {
    
    public DirResource(CollectionModel r) {
        super(r);
    }

    /* collection resource */

    public Resource child(String childName) {
        return ItemResource.fromModel(((CollectionModel)this.model).getChild(childName), getPrefix());
    }
    
    public List<Resource> getChildren() {
        List<Resource> ret = new ArrayList();
        for (ItemModel i : ((CollectionModel)this.model).getChildren()) {
            ret.add(ItemResource.fromModel(i, getPrefix()));
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
        pw.println("<h1>" + this.model.getFullName() + "</h1><p><ul>");

        CollectionModel parent = this.model.getParent();
        if (parent != null) 
            pw.println("<li><a href='"+getPrefix()+parent.getFullName()+"'>..</a></li>");
        for (ItemModel i : ((CollectionModel)this.model).getChildren()) {
            pw.println("<li><a href='"+getPrefix()+i.getFullName()+(i instanceof CollectionModel? "/":"")+"'>" + i.getName() + "</a></li>");
        }
        pw.println("</ul>");
        pw.flush();
    }

    /* make collectionable */
    
    public CollectionResource createCollection(String newName) {

        CollectionModel fi = this.model.getModel().createFolder(newName, null, (CollectionModel)this.model);
        CollectionResource cr = (DirResource)ItemResource.fromModel(fi, getPrefix());
        return cr;
    }

    /* putable */

    // Create a new resource, or overwrite an existing one
    public Resource createNew(String newName, InputStream inputStream, Long length, String contentType) {

        System.out.println("putting " + newName + ", size " + length + ", ct: " + contentType);
        ItemModel current = ((CollectionModel)model).getChild(newName);
        ContentModel fm = null;
        if (current != null && current instanceof ContentModel) {
            fm = (ContentModel)current;
        } else
            fm = this.model.getModel().createFile(newName, null, (CollectionModel)this.model);

        if (fm.store(inputStream, length.longValue(), contentType)) {
            Resource ret = ItemResource.fromModel(fm, getPrefix());
            return ret;
        } else {
            fm.delete();
            return null;
        }
    }
}
