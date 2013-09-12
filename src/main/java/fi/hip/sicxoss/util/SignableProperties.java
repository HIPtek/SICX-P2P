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
package fi.hip.sicxoss.util;


import java.util.*;
import java.io.*;
import org.json.*;

import fi.hip.sicxoss.ident.Signable;

/*
 * consistent, signed, properties - properties that remain in the same
 * order when serialized
 * @author koskela
 */
public class SignableProperties
    extends Signable {

    private Hashtable<String, String> values = new Hashtable();

    public void setProperty(String key, String value) {
        values.put(key, value);
    }
    
    public String getProperty(String key) {
        return values.get(key);
    }

    protected byte[] getSignableData() {

        try {
            Set<String> keys = values.keySet();
            ArrayList<String> keylist = new ArrayList();
            keylist.addAll(keys);
            Collections.sort(keylist);
            
            JSONArray arr = new JSONArray();
            for (String k : keylist) {
                JSONObject ob = new JSONObject();
                ob.put(k, values.get(k));
                arr.put(ob);
            }
            return arr.toString().getBytes();
        } catch (Exception ex) {
            return null;
        }
    }

    public boolean init(byte[] data) {
        
        byte[] content = initFromSignedData(data);
            
        try {
            JSONArray arr = new JSONArray(new String(content));
            for (int i=0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                for (Iterator it = obj.keys(); it.hasNext();) {
                    String k = (String)it.next();
                    setProperty(k, (String)obj.optString(k));
                }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
