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

import com.bradmcevoy.http.ResourceFactory;
import com.bradmcevoy.http.Resource;

import fi.hip.sicxoss.model.*;
import fi.hip.sicxoss.LocalGateway;

/**
 * SICXOSSResourceFactory class.
 *
 * @author koskela
 */
public class SICXOSSResourceFactory
    implements ResourceFactory {

    private Hashtable<String, MountableModel> models;
    private LocalGateway lg;

    public SICXOSSResourceFactory(LocalGateway lg) {
        
        this.models = new Hashtable();
        this.lg = lg;
    }

    public void addModel(MountableModel model, String prefix) {
        this.models.put(prefix, model);
    }

    public void removeModel(MountableModel model) {
        String p = null;
        for (String pref : models.keySet()) {
            MountableModel m = models.get(pref);
            if (m == model) {
                p = pref;
                break;
            }
        }
        
        this.models.remove(p);
    }
    
    public Resource getResource(String host, String p) { 

        if (p.length() == 0 || p.equals("/"))
            return new ShareListResource(p, this.models, lg);

        MountableModel sm = null;
        String prefix = "";
        for (Enumeration<String> e = models.keys(); sm == null && e.hasMoreElements();) {
            prefix = e.nextElement();
            if (p.startsWith(prefix)) {
                sm = models.get(prefix);
                p = p.substring(prefix.length());
            }
        }

        ItemModel m = null;
        if (sm != null) {
            m = sm.getItem((p.endsWith("/")? p.substring(0, p.length() - 1) : p));
            return ItemResource.fromModel(m, prefix);
        } else
            return null;
    }
}
