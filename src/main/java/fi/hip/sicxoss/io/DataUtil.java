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
package fi.hip.sicxoss.io;

import java.io.*;
import java.util.*;
import java.security.*;

import org.apache.log4j.Logger;

/**
 * DataUtil class.
 *
 * @author koskela
 */
public class DataUtil {

    private static final Logger log = Logger.getLogger(DataUtil.class);

    // simple object storage
    public static boolean writeObject(Serializable obj, String file) {
        try {
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(obj);
            oos.close();
            fos.close();
            return true;
        } catch (Exception ex) {
            log.error("error while writing object: " + ex);
            return false;
        }
    }

    public static Object readObject(String file) {
        Object ret = null;
        try {
            FileInputStream fis = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(fis);
            ret = ois.readObject();
            ois.close();
            fis.close();
        } catch (Exception ex) {
            log.error("error while reading object: " + ex);
        }
        return ret;
    }

    // common date formatting
    public static String dateToString(Date date) {
        return "" + date.getTime();
    }

    public static Date stringToDate(String str) {
        try {
            if (str != null)
                return new Date(Long.parseLong(str));
        } catch (Exception ex) {
            log.warn("invalid time format: " + str);
        }
        return new Date(0L); // make 1970 the default
    }

    public static File extractToFile(String resource, File target)
        throws Exception {
        
        if (!target.exists()) {
            target.getParentFile().mkdirs();
            
            InputStream in = resource.getClass().getResourceAsStream(resource);
            FileOutputStream fos = new FileOutputStream(target);
            DataUtil.transfer(in, fos);
            fos.close();
            in.close();
        }
        return target;
    }

    public static String extractAsString(String resource)
        throws Exception {
        
        InputStream in = resource.getClass().getResourceAsStream(resource);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataUtil.transfer(in, bos);
        bos.close();
        in.close();
        
        return new String(bos.toByteArray());
    }

    public static byte[] readToMem(InputStream in)
        throws Exception {
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataUtil.transfer(in, bos);
        bos.close();
        in.close();
        
        return bos.toByteArray();
    }

    public static int transfer(InputStream in, OutputStream out) 
        throws IOException {
        
        byte[] buf = new byte[10*1024];
        int r;
        int total = 0;
        while ((r = in.read(buf)) > -1) {
            out.write(buf, 0, r);
            total += r;
        }
        
        out.flush();
        return total;
    }

    public static int transfer(InputStream in, OutputStream out, int max) 
        throws IOException {
        
        byte[] buf = new byte[10*1024];
        int r;
        int total = 0;
        while (max > total && (r = in.read(buf, 0, ((max-total) > buf.length? buf.length : (max-total)))) > -1) {
            out.write(buf, 0, r);
            total += r;
        }
        
        out.flush();
        return total;
    }

    public static int transfer(InputStream in, byte[] buf, int off, int len) 
        throws IOException {
        
        int r;
        int total = 0;
        while ((r = in.read(buf, off, len)) > -1) {
            total += r;
            off += r;
            len -= r;
            if (len < 1)
                break;
        }
        return total;
    }

    public static byte[] toBuf(InputStream in) 
        throws IOException {
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        transfer(in, bos);
        bos.close();
        return bos.toByteArray();
    }

    public static byte[] serialize(Object s) 
        throws IOException {
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(s);
        oos.close();
        bos.close();
        return bos.toByteArray();
    }

    public static Object deserialize(byte[] data) 
        throws Exception {
        
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        Object ret = ois.readObject();
        ois.close();
        bis.close();
        return ret;
    }

    public static String toHex(byte[] data) {

        Formatter formatter = new Formatter();
        for (byte b : data)
            formatter.format("%02x", b);
        return formatter.toString();
    }

    public static byte[] toBytes(String data) {

        byte[] ret = new byte[data.length() / 2];
        for (int i = 0; i < ret.length; i++)
            ret[i] = (byte)Integer.parseInt(data.substring(2 * i, 2 * i + 2), 16);
        return ret;
    }

}
