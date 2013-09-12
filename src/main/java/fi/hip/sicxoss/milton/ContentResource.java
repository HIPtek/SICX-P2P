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
import fi.hip.sicxoss.io.DataUtil;

/**
 * ContentResource class.
 *
 * @author koskela
 */
public class ContentResource
    extends ItemResource
    implements PostableResource
{

    public ContentResource(ContentModel m) {
        super(m);
    }

    /* getable resource */

    // The length of the content in this resource.
    public Long getContentLength() {
        return new Long(((ContentModel)model).getLength());
    }
    
    // Given a comma separated listed of preferred content types acceptable for a client, return one content type which is the best.
    public String getContentType(String accepts) {
        return ((ContentModel)model).getContentType();
    }
    
    //How many seconds to allow the content to be cached for, or null if caching is not allowed The provided auth object allows this method to determine an appropriate caching time depending on authenticated context.
    public Long getMaxAgeSeconds(Auth auth) {
        return null;
    }
    
    // Send the resource's content using the given output stream.
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType) {
        
        // will we ever encounter this?
        if (range != null) {
            System.out.println("****\n*** note: web client requested range: " + range);
        }
        
        try {
            long start = 0;
            long finish = -1;
            if (range != null) {
                start = range.getStart();
                finish = range.getFinish();
            }
            InputStream in = ((ContentModel)model).getDataStream(start, finish);
            DataUtil.transfer(in, out);
            in.close();
        } catch (Exception ex) {
            System.out.println("Error while writing: " + ex);
            ex.printStackTrace();
        } finally {
            try {
                out.close();
            } catch (Exception ex) {
            }
        }
    }

    /* postable */

    public String processForm(Map<String, String> parameters, Map<String, FileItem> files) {
        return null;
    }

}
    
