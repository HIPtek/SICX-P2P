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
package fi.hip.sicx.slcs;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.io.File;
import java.security.KeyPair;

import org.glite.slcs.SLCSBaseClient;
import org.glite.slcs.SLCSException;
import org.glite.slcs.SLCSInit;
import org.glite.slcs.config.SLCSClientConfiguration;
import org.glite.slcs.shibclient.ShibbolethCredentials;
import org.glite.slcs.pki.CertificateKeys;
import java.lang.reflect.Field;

/**
 * SLCSClient class.
 *
 * @author koskela
 */
public class SLCSClient extends SLCSBaseClient {
	
    /*
	public static boolean slcsLogin(String username, String password, String privateKeyPassword) {
		return slcsLogin(username, password, privateKeyPassword, "/tmp", "");
	}
    */
    
    public static boolean slcsLogin(String username, String password, String privateKeyPassword, 
                                    String storeDirectory, String userPrefix, String configLoc) {

        return slcsLogin(username, password, privateKeyPassword, 
                         storeDirectory, userPrefix, configLoc,
                         null);
    }

    public static boolean slcsLogin(String username, String password, String privateKeyPassword, 
                                    String storeDirectory, String userPrefix, String configLoc,
                                    KeyPair keyPair) {
        
        String config = configLoc;
        String idpProviderId = "sicx";
        int keySize = 2048;
	
        // create client
        SLCSInit client = null;
        try {
            //SLCSClientConfiguration configuration = SLCSClientConfiguration.getInstance(config);
            SLCSClientConfiguration configuration = SLCSClientConfiguration.getInstance(config);
            ShibbolethCredentials credentials = new ShibbolethCredentials(
                    username, password, idpProviderId);
            client = new SLCSInit(configuration, credentials);
            client.setStoreDirectory(storeDirectory);
            client.setUserPrefix(userPrefix);
        } catch (SLCSException e) {
            System.err.println("ERROR: Failed to create SLCS client: " + e);
            return false;
        }

        // client shibboleth login
        try {
            client.shibbolethLogin();
        } catch (SLCSException e) {
            System.err.println("ERROR: " + e);
            return false;
        }

        // SLCS login request, get DN and authToken
        try {
            client.slcsLogin();
        } catch (SLCSException e) {
            System.err.println("ERROR: " + e);
            return false;
        }

        // generate key and CSR. unless we have those already!
        try {
            if (keyPair == null) {
                if (keySize != -1) {
                    client.setKeySize(keySize);
                }
                keySize = client.getKeySize();
                client.generateCertificateKeys(keySize, privateKeyPassword.toCharArray());
            } else {
                // introspection saves the day..
                try {
                    CertificateKeys certificateKeys = new CertificateKeys(privateKeyPassword.toCharArray());
                    Field f = certificateKeys.getClass().getDeclaredField("keyPair_");
                    f.setAccessible(true);
                    f.set(certificateKeys, keyPair);
                    
                    f = client.getClass().getDeclaredField("certificateKeys_");
                    f.setAccessible(true);
                    f.set(client, certificateKeys);
                } catch (Exception ex) {
                    System.err.println("ERROR: " + ex);
                    return false;
                }
            }
            client.generateCertificateRequest();
        } catch (GeneralSecurityException e) {
            System.err.println("ERROR: " + e);
            return false;
        }

        // submit CSR
        try {
            client.slcsCertificateRequest();
        } catch (SLCSException e) {
            System.err.println("ERROR: " + e);
            return false;
        }

        // store key + cert
        try {
            client.storePrivateKey();
            client.storeCertificate();

        } catch (IOException e) {
            System.err.println("ERROR: " + e);
            return false;
        }
        return true;
	}

}
