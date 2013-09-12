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

/**
 * Properties, but with hierarchical structuring support. This
 * supports getting a child-Properties of a specific prefix. All of
 * these are interlinked; changing a value in the child will be
 * noticed when saving the parent.
 *
 * There's two ways of doing this: by simply referring all the gets /
 * sets to the parent (prepending the prefix), or by keeping a local
 * copy of the values, as well as informing the parent. The second
 * allows us to do lists / stores / propertyNames more easily, but
 * requires syncing between parent and children.
 *
 * Note: only setProperty should be used, not the Hashtable- inherited
 * setter- methods.
 * @author koskela
 */
public class HierarchicalProperties
    extends Properties {

    private static final char SEPARATOR = '.';

    private HierarchicalProperties parent;
    private String prefix;
    private Hashtable<String, HierarchicalProperties> children;
    
    public HierarchicalProperties() {
        super();
        children = new Hashtable();
    }

    private HierarchicalProperties(HierarchicalProperties parent, String prefix) {
        this();
        
        // 'sync' with parent
        String pp = prefix + SEPARATOR;
        for (Enumeration e = parent.propertyNames(); e.hasMoreElements();) {
            String k = (String)e.nextElement();
            if (k.startsWith(pp))
                setProperty(k.substring(pp.length()), parent.getProperty(k), false, false);
        }
        
        this.parent = parent;
        this.prefix = prefix;
    }

    @Override
    public Object setProperty(String key,
                              String value) {

        return setProperty(key, value, true, true);
    }
    
    protected Object setProperty(String key,
                                 String value,
                                 boolean syncParent, boolean syncChilds) {
        
        if (syncParent && parent != null)
            parent.setProperty(prefix + SEPARATOR + key, value, true, false);
        
        if (syncChilds) {
            String p = getPrefix(key);
            if (p != null) {
                HierarchicalProperties hp = getChildren(p, false);
                if (hp != null)
                    hp.setProperty(key.substring(0, (p + SEPARATOR).length()), value, false, true);
            }
        }
        
        if (value != null)
            return super.setProperty(key, value);
        else
            return super.remove(key);
    }

    @Override
    public void clear() {
        // a bit hackish again. setProperty with null as value == deleting the value.
        for (Enumeration e = super.propertyNames() ; e.hasMoreElements() ;) {
            setProperty((String)e.nextElement(), null);
        }
    }
    
    public Enumeration childrenNames() {

        ArrayList<String> uniq = new ArrayList();
        for (Object n : keySet()) {
            String p = getPrefix((String)n);
            if (p != null && !uniq.contains(p))
                uniq.add(p);
        }

        String chstr = null;
        for (String s : uniq) {
            if (chstr == null)
                chstr = s;
            else
                chstr += SEPARATOR + s;
        }
        if (chstr == null)
            return new StringTokenizer("");
        else
            return new StringTokenizer(chstr, SEPARATOR + "");
    }

    private String getPrefix(String key) {
        int p = key.indexOf(SEPARATOR);
        if (p > -1)
            return key.substring(0, p);
        return null;
    }

    public HierarchicalProperties getChildren(String prefix) {
        return getChildren(prefix, true);
    }

    /* returns the child properties of a specific prefix */
    private synchronized HierarchicalProperties getChildren(String prefix, boolean returnEmpty) {
        HierarchicalProperties ret = children.get(prefix);
        if (ret == null && returnEmpty) {
            ret = new HierarchicalProperties(this, prefix);
            children.put(prefix, ret);
        }
        return ret;
    }
}
