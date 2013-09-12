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
package fi.hip.sicxoss.ident;

import java.io.*;

import fi.hip.sicxoss.io.*;

/**
 * Signable
 *
 * Data that can be electronically signed
 */
public abstract class Signable {

    /* the signature */
    protected byte[] signature;
    protected String signerId;
    
    protected abstract byte[] getSignableData();

    protected void storeSignature(byte[] signature, String signerId) {
        
        this.signature = signature;
        this.signerId = signerId;
    }
    
    protected final byte[] getSignature() {
        return signature;
    }

    public final byte[] getSignedData()
        throws IOException {

        byte[] signedData;
        if (this.signature != null) {
            byte[] data = getSignableData();
            
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeShort(signature.length);
            dos.write(signature);
            dos.writeShort(data.length);
            dos.write(data);
            dos.writeUTF(signerId);
            dos.flush();
            dos.close();
            bos.close();
            signedData = bos.toByteArray();

        } else
            signedData = null;

        return signedData;
    }

    private static final String TXT_HEAD = "### BEGIN SICXOSS SIGNED DATA\n";
    private static final String TXT_FOOT = "\n### END SICXOSS SIGNED DATA\n";

    public final String getSignedTextData() {

        String signedTextData;
        signedTextData = TXT_HEAD;
        signedTextData += new String(getSignableData());
        signedTextData += TXT_FOOT;
        signedTextData += signerId + "\n";
        signedTextData += DataUtil.toHex(signature);
        return signedTextData;
    }

    public final byte[] initFromSignedData(byte[] data) {

        String text = null;
        byte[] ret = null;
        try {
            text = new String(data);
            ret = initFromSignedData(text);
            if (ret != null)
                return ret;
        } catch (Exception ex) {
        }
        
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            DataInputStream dis = new DataInputStream(bis);
            int l = dis.readShort();
            signature = new byte[l];
            dis.readFully(signature);
            l = dis.readShort();
            ret = new byte[l];
            dis.readFully(ret);
            signerId = dis.readUTF();
            dis.close();
            bis.close();
        } catch (Exception ex) {
        }
        return ret;
    }

    public final byte[] initFromSignedData(String data) {
        
        int p = 0;
        byte[] ret = null;
        if (data.startsWith(TXT_HEAD) && ((p = data.indexOf(TXT_FOOT)) > -1)) {
            String content = data.substring(TXT_HEAD.length(), p);
            String sig = data.substring(p + TXT_FOOT.length());
            p = sig.indexOf("\n");
            if (p > -1) {
                signerId = sig.substring(0, p);
                signature = DataUtil.toBytes(sig.substring(p+1));
                ret = content.getBytes();
            }
        }
        return ret;
    }

    public final boolean isSigned() {
        return signerId != null;
    }

    public final String getSignerKeyId() {
        return signerId;
    }


}
