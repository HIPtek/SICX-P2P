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

import java.security.*;
import java.security.spec.*;
import java.security.cert.*;
import javax.security.auth.x500.*;
import java.io.*;
import java.util.*;

import org.apache.log4j.Logger;

import fi.hip.sicxoss.LocalGateway;
import fi.hip.sicxoss.io.*;
import fi.hip.sicxoss.util.*;

/**
 * User
 * 
 * Class representing a user of the system
 * @author koskela
 */
public class User 
    extends SignableProperties
    implements Comparable<User> {

    private static final Logger log = Logger.getLogger(User.class);

    public static final String ID_ALGO = "SHA1";

    // key
    private PublicKey pubKey;
    private String userId;
    private String keyId;
    private X509Certificate cert;
    
    /* we have a separate 'nickname' that can be set, which does not
     * go into the signable details. The thinking is here that the
     * nickname is something the local user can set for a remote peer
     * (e.g., having two 'hessus' => 'hessu k' & 'hessu S'). the
     * nickname does however get set by the remote user initially (in
     * LocalUser), which serves as a default unless the local user
     * changes it. As these do not get put in the signable things, we
     * need to be careful with how we handle the in-memory
     * instances. when recieving updated identities, we should
     * actually just update the existing one to preserve the local
     * nick.*/
    protected String nickName;

    protected User() {}

    protected void loadFromData(String data) 
        throws Exception {

        if (!init(data.getBytes()))
            throw new Exception("invalid user format!");
        
        String key = getProperty("key");
        if (key != null) {
            String algo = getProperty("algo");
            byte[] keyb = DataUtil.toBytes(key);
            KeyFactory keyfac = KeyFactory.getInstance(algo);
            EncodedKeySpec kspec = new X509EncodedKeySpec(keyb);
            PublicKey pubKey = keyfac.generatePublic(kspec);
            setPublicKey(pubKey);
        } else {
            String certs = getProperty("cert");
            byte[] certb = DataUtil.toBytes(certs);
            ByteArrayInputStream bis = new ByteArrayInputStream(certb);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate)cf.generateCertificate(bis);
            setCert(cert);
        }
        nickName = getName();

    }

    /* creates a user from a text block of info containing the key and
     * other important data. */
    public static User fromData(String data) 
        throws Exception {

        User ret = new User();
        ret.loadFromData(data);

        if (!ret.keyId().equals(ret.getSignerKeyId()))
            throw new Exception("Invalid key in user " + ret.getId() + "'s data");
        if (!ret.verify(ret))
            throw new Exception("Signature does not verify for user " + ret.getId());

        return ret;
    }

    protected String getSignFormat() {
        return ID_ALGO + "with" + pubKey.getAlgorithm();
    }

    protected X509Certificate getCert() {
        return cert;
    }

    public boolean hasCert() {
        return cert != null;
    }

    protected void setCert(X509Certificate cert)
        throws Exception {
        
        // extract the key.
        this.cert = cert;
        this.pubKey = cert.getPublicKey();

        /* ok, apparently the SLCS provider will generate a new key for
           each login, making key-based ids obsolete.

           We will use the CN then. for cert-based identities!
        */

        MessageDigest digest = MessageDigest.getInstance(ID_ALGO);
        //digest.update(pubKey.getEncoded());

        X500Principal subj = cert.getSubjectX500Principal();
        digest.update(subj.getName().getBytes());
        this.userId = DataUtil.toHex(digest.digest());

        digest = MessageDigest.getInstance(ID_ALGO);
        digest.update(pubKey.getEncoded());
        this.keyId = DataUtil.toHex(digest.digest());

        log.info("hello, I am " + getId() + " (" + subj.getName() + ") with key " + keyId());
        setProperty("cert", DataUtil.toHex(cert.getEncoded()));
    }

    protected void setPublicKey(PublicKey pubKey) 
        throws Exception {

        this.pubKey = pubKey;

        // create fingerprint of key
        MessageDigest digest = MessageDigest.getInstance(ID_ALGO);
        digest.update(pubKey.getEncoded());
        this.keyId = DataUtil.toHex(digest.digest());
        this.userId = this.keyId;

        log.info("hello, I am " + getId());
        setProperty("key", DataUtil.toHex(pubKey.getEncoded()));
        setProperty("algo", pubKey.getAlgorithm());
    }        

    public String getData() {
        return getSignedTextData();
    }

    /* verifies a signature made, supposedly, by this user */
    public boolean verify(Signable s) 
        throws Exception {

        byte[] signature = s.getSignature();
        byte[] data = s.getSignableData();

        Signature sig = Signature.getInstance(getSignFormat());
        sig.initVerify(pubKey);
        sig.update(data);

        log.debug("verifying " + new String(data));
        
        // verify .. 
        return sig.verify(signature);
    }


    @Override
    public String toString() {
        return "User '" + getName() + "/" + getFullName() + ", id: " + getId();
    }
    
    public String getId() {
        return userId;
    }

    public String keyId() {
        return keyId;
    }

    public String getFullName() {
        return getProperty("fullname");
    }

    public String getName() {
        if (nickName != null)
            return nickName;
        return getProperty("name");
    }

    /*
    public void setName(String name) {
        nickName = name;
    }
    */

    @Override
    public int compareTo(User other) {
        return getId().compareTo(other.getId());
    }

    @Override
    public boolean equals(Object other) {
        return compareTo((User)other) == 0;
    }
        
    @Override
    public int hashCode() {
        return getId().hashCode();
    }

}
