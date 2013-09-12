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
import java.util.*;
import java.security.*;
import java.security.cert.*;
import javax.security.auth.x500.*;
import javax.net.ssl.*;

import org.apache.log4j.Logger;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;
import org.bouncycastle.openssl.*;
import org.bouncycastle.util.encoders.Hex;

import fi.hip.sicxoss.LocalGateway;
import fi.hip.sicxoss.io.*;
import fi.hip.sicxoss.io.message.*;
import fi.hip.sicxoss.model.*;

import fi.hip.sicx.slcs.SLCSClient;

/**
 * LocalUser
 * 
 * Class representing the local user of the system
 * @author koskela
 */
public class LocalUser 
    extends User {

    private static final Logger log = Logger.getLogger(LocalUser.class);

    public interface LocalUserObserver {

        public void contactAdded(LocalUser user, User contact, ShareModel share);
        public void contactRemoved(LocalUser user, User contact, ShareModel share);
        public void inviteGot(LocalUser user, Invite invite);
        public void inviteReplied(LocalUser user, Invite invite);
    }

    // the key pair
    private KeyPair keyPair;
    
    private ContactManager contactManager;
    private transient LocalGateway gw;
    private transient ConnectionManager connMan;
    private Hashtable<ShareID, Invite> receivedInvites;
    private Hashtable<ShareID, ShareModel> shares;

    // the location where to save it all
    private File saveFile;
    
    private List<LocalUserObserver> observers;

    // save keypair?
    private boolean saveKeyPair;
    private boolean savePassword;
    private String account;
    private String accountPasswd;
    
    public LocalUser(String name, String path, LocalGateway gw) {
        this.saveFile = new File(path);
        this.gw = gw;
        this.contactManager = new ContactManager(this);
        this.connMan = new ConnectionManager(this, gw);
        this.observers = new ArrayList();
        this.receivedInvites = new Hashtable();
        this.shares = new Hashtable();
        setName(name);
    }

    public void addShare(ShareModel model) {
        shares.put(model.getId(), model);
    }

    public void setName(String name) {
        setProperty("name", name);
    }

    public void addObserver(LocalUserObserver luo) {
        observers.add(luo);
    }

    public boolean saveData() {
        log.debug("saving user: " + this + " in " + saveFile);
        try {
            if (!saveFile.exists())
                saveFile.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(saveFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(getData()); // the public profile

            oos.writeObject(new Boolean(saveKeyPair));
            if (saveKeyPair)
                oos.writeObject(keyPair);
            if (account != null) {
                oos.writeObject(new Boolean(true));
                oos.writeObject(account);
                oos.writeObject(new Boolean(savePassword));
                if (savePassword)
                    oos.writeObject(accountPasswd);
            } else
                oos.writeObject(new Boolean(false));

            contactManager.writeTo(oos);
            oos.writeObject(receivedInvites);
            oos.close();
            fos.close();
            return true;
        } catch (Exception ex) {
            log.error("error saving user: " + ex);
        }
        return false;
    }

    private boolean loadData() {
        try {
            FileInputStream fis = new FileInputStream(saveFile);
            ObjectInputStream ois = new ObjectInputStream(fis);
            loadFromData((String)ois.readObject()); // the public profile

            Boolean b = (Boolean)ois.readObject();
            saveKeyPair = b.booleanValue();
            if (saveKeyPair)
                keyPair = (KeyPair)ois.readObject();

            b = (Boolean)ois.readObject();
            if (b.booleanValue()) {
                account = (String)ois.readObject();
                b = (Boolean)ois.readObject();
                if (b.booleanValue()) {
                    accountPasswd = (String)ois.readObject();
                }
            }

            contactManager.readFrom(ois);
            receivedInvites = (Hashtable)ois.readObject();
            for (Invite inv : receivedInvites.values())
                inv.user = this;
            contactManager().addKey(this);
            ois.close();
            fis.close();
            return true;
        } catch (Exception ex) {
            log.error("error loading user: " + ex);
        }
        return false;
    }

    public static LocalUser load(String name, String path, LocalGateway gw) {
                
        LocalUser ret = new LocalUser(name, path, gw);
        if (ret.loadData())
            return ret;
        return null;
    }

    public boolean fetchSLCS(String slcsPasswd, boolean saveCerts)
        throws Exception {

        log.info("fetching SLCS key & cert..");
        
        if (slcsPasswd == null) {
            if (accountPasswd != null)
                slcsPasswd = accountPasswd;
            else
                return false;
        }

        String folder = saveFile.getParentFile().getAbsolutePath() + File.separator;
        String keyfile = folder + getName() + "trusted_client.priv";
        String certfile = folder + getName() + "trusted_client.cert";

        String initname = "slcs-init.xml";
        File slcsinit = new File(folder + initname);
        if (!slcsinit.exists()) {
            slcsinit.getParentFile().mkdirs();
            log.info("extracting " + initname + "..");
            String content = DataUtil.extractAsString("/" + initname);
            content = content.replaceAll("USERPATH", folder);
            FileOutputStream fos = new FileOutputStream(slcsinit);
            fos.write(content.getBytes());
            fos.close();
        }

        String trustedCerts = "provider.cert";
        for (String r : new String[] { "slcs-metadata.xml", "truststore.slcs.jks", trustedCerts }) {
            File rfile = new File(folder + r);
            if (!rfile.exists()) {
                log.info("extracting " + r + "..");
                DataUtil.extractToFile("/" + r, rfile);
            }
        }
        
        File certf = new File(certfile);
        File keyf = new File(keyfile);
        
        String keypasswd = "jeah";
        boolean success = false;
        if (!keyf.exists() || 
            !certf.exists()) {
            
            log.info("Retrieving credentials..");
            success = SLCSClient.slcsLogin(account, slcsPasswd, keypasswd, saveFile.getParentFile().getAbsolutePath(), getName(), slcsinit.getAbsolutePath());
            log.info("\rSLCS login: " + success);
        } else {
            success = true;
            log.warn("key and certificate already exists, re-using!");
        }
        
        if (success) {
            accountPasswd = slcsPasswd;
            success = loadFromPEM(keyfile, certfile, keypasswd);

            // add the root cert to the contactmanager
            if (saveCerts) {
                importRootCert(new File(folder + trustedCerts));
            }
            
            // add own key to index. do this AFTER the cert check.
            log.info("certificate check: " + contactManager().isTrusted(this)); // check
            contactManager().addKey(this);
        }
        
        try {
            certf.delete();
            keyf.delete();
        } catch (Exception ex) {}

        return success;
    }

    public boolean importRootCert(File file) {

        log.info("importing the root certificate into the contact manager from " + file);
        try {
            KeyStore ts = KeyStore.getInstance(KeyStore.getDefaultType());
            InputStream in = new FileInputStream(file);
            ts.load(in, null);
            in.close();
            
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);
            
            TrustManager tms[] = tmf.getTrustManagers();
            for (int i = 0; i < tms.length; i++) {
                if (tms[i] instanceof X509TrustManager) {
                    X509TrustManager tm = (X509TrustManager)tms[i];
                    for (X509Certificate cert : tm.getAcceptedIssuers()) {
                        log.info("found cert issued by: " + cert.getSubjectX500Principal().getName());
                    contactManager().addCert(cert);
                    }
                    
                }
            }
            return true;
        } catch (Exception ex) {
            log.warn("error loading the certificate from keystore: " + ex);
        }

        try {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            
            FileReader fileReader = new FileReader(file);
            PEMReader r = new PEMReader(fileReader);
            X509Certificate cert = (X509Certificate)r.readObject();
            log.info("found cert issued by: " + cert.getSubjectX500Principal().getName());
            contactManager().addCert(cert);
            return true;
        } catch (Exception ex) {
            log.warn("error loading the certificate from PEM: " + ex);
        }
        
        return false;
    }

    // creates a new user with a public / private key
    private boolean loadFromPEM(String pemKey, String pemCert, final String passwd) 
        throws Exception {

        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        log.info("reading private key from " + pemKey);
        FileReader fileReader = new FileReader(new File(pemKey));
        PEMReader r = new PEMReader(fileReader, new PasswordFinder() {
                public char[] getPassword() {
                    return passwd.toCharArray();
                }});
        KeyPair keyPair = (KeyPair)r.readObject();

        log.info("reading certificate from " + pemCert);
        fileReader = new FileReader(new File(pemCert));
        r = new PEMReader(fileReader);
        X509Certificate cert = (X509Certificate)r.readObject();
        X500Principal subj = cert.getSubjectX500Principal();
        
        StringTokenizer st = new StringTokenizer(subj.getName(), ",");
        Hashtable<String, String> comps = new Hashtable();
        while (st.hasMoreTokens()) {
            String tok = st.nextToken().trim();
            int p = tok.indexOf('=');
            if (p > -1) {
                String tokn = tok.substring(0, p).trim();
                String tokv = tok.substring(p+1).trim();
                comps.put(tokn, tokv);
            }
        }
        
        String fullName = comps.get("CN");
        log.info("found common name: " + fullName);
        
        setProperty("fullname", fullName);
        this.keyPair = keyPair;
        setCert(cert);

        sign(this);
        return true;
    }

    public static LocalUser createUsingSLCS(String slcsLogin, String slcsPasswd, String name, String path, LocalGateway gw) 
        throws Exception {

        log.info("creating from SLCS account..");
                
        LocalUser ret = new LocalUser(name, path, gw);
        ret.account = slcsLogin;
        if (ret.fetchSLCS(slcsPasswd, true)) {
            ret.saveKeyPair = false;
        } else
            ret = null;

        return ret;
    }

    public boolean requiresSLCS() {
        return keyPair == null && account != null;
    }

    public boolean hasPassword() {
        return accountPasswd != null;
    }

    public void setSaveKeys(boolean save) {
        saveKeyPair = save;
        saveData();
    }

    public void setSavePassword(boolean save) {
        savePassword = save;
        saveData();
    }

    public String getAccount() {
        return account;
    }
    
    // creates a new user with a public / private key
    public static LocalUser generate(String name, String fullName, String path, LocalGateway gw) 
        throws Exception {

        // these are the defaults
        // public static final String KEY_ALGO = "DSA";
        // public static final int KEY_SIZE = 1024;

        String KEY_ALGO = "DSA";
        int KEY_SIZE = 1024;

        LocalUser ret = new LocalUser(name, path, gw);
        ret.setProperty("fullname", fullName);
        ret.saveKeyPair = true;

        // create a new private key
        log.info("Generating new " + KEY_ALGO + " keys of length " + KEY_SIZE);
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KEY_ALGO);
        keyGen.initialize(KEY_SIZE);

        KeyPair keyPair = keyGen.generateKeyPair();
        PrivateKey privKey = keyPair.getPrivate();
        PublicKey pubKey  = keyPair.getPublic();
        
        ret.keyPair = keyPair;
        ret.setPublicKey(pubKey);

        log.info("Generated new " + KEY_ALGO + " keys of length " + KEY_SIZE);
        log.debug("private key format: " + privKey.getFormat());
        log.debug("public key format: " + pubKey.getFormat());
        
        // sign myself
        ret.sign(ret);
        // add own key to index.
        ret.contactManager().addKey(ret);
        if (ret.saveData())
            return ret;
        return null;
    }

    // signs and verifies a signature

    public boolean sign(Signable s) 
        throws Exception {

        s.storeSignature(null, keyId());
        byte[] data = s.getSignableData();

        log.debug("signing '" + new String(data) + "'");
        
        // sign..
        Signature sig = Signature.getInstance(getSignFormat());
        sig.initSign(keyPair.getPrivate());
        sig.update(data);
        
        byte[] signature = sig.sign();
        s.storeSignature(signature, keyId());
        return true;
    }

    public ContactManager contactManager() {
        return contactManager;
    }

    public ConnectionManager connectionManager() {
        return connMan;
    }

    /* creates a public copy of this that can be shared */
    public User publicCopy() 
        throws Exception {

        return User.fromData(getData());
    }

    /* returns an updated instance of the given contact */
    public synchronized User addContact(User user, ShareModel share) {
        log.info("should add user " + user + " to share " + share);

        user = contactManager.addContact(user);
        if (share != null)
            connMan.addContact(user, share);
        
        for (LocalUserObserver luo : observers)
            try { luo.contactAdded(this, user, share); } catch (Exception ex) { log.warn("observer failed: " + ex); }
        return user;
    }
    
    public void removeContact(User user, ShareModel share) {

        // remove the contact from ALL shares??
        //contactManager.removeContact(user);
        if (user == this && share != null) {
            log.info("should remove local user from share " + share);
            /* we could remove all the connections here, but actually,
             * we are better off not doing that. Instead, keep the
             * shares etc: that will allow us to sync the last
             * 'goodbye' to anyone we might be sharing the folder with.
             */
        } else {
            log.info("should remove user " + user + " from share " + share);
            if (share != null)
                connMan.removeContact(user, share);
            for (LocalUserObserver luo : observers)
                try { luo.contactRemoved(this, user, share); } catch (Exception ex) { log.warn("observer failed: " + ex); }
        }
    }

    /* invite processing. the assumptions here are that we might get
     * invites to the same share from multiple persons. furthermore,
     * we might get an invite to a share we already are in. and the
     * same user might keep sending invites to a share even though we
     * have replied to that
     *
     * we reject an invite by replying with an appropriate message. we
     * accept an invite by creating a local copy of the share and
     * sending a sync request. no wait, we accept by sending an
     * 'accept' message to one of the inviters. this inviter will add
     * us to the share. after that we are ready to sync.
     */

    public List<Invite> getReceivedInvites() {
        // we return only those that haven't been decided on!
        List<Invite> ret = new ArrayList();
        for (Invite inv : receivedInvites.values())
            if (inv.verdict == null)
                ret.add(inv);
        return ret;
    }

    /* when receiving an invite */
    public void shareInviteGot(User contact, ShareID shareid, String name, String description) {

        // do we have the share already?
        ShareModel share = shares.get(shareid);
        if (share != null) {
            // add this guy to that share. unless he is there
            // already. that will cause us to start syncing with him.
            sendInviteReply(shareid, true, contact);
            share.syncWith(contact);
            return;
        }
        
        // do we have the invite already?
        Invite invite = receivedInvites.get(shareid);
        boolean newContact = true;
        if (invite != null) {

            newContact = invite.addContact(contact);
            // have we decided already?
            if (invite.verdict != null) {
                replyToInvite(invite);
                invite = null;
            }
        } else {
            invite = new Invite(this, contact, shareid, name, description);
            receivedInvites.put(shareid, invite);
        }
        
        saveData();
        if (invite != null && newContact)
            for (LocalUserObserver luo : observers)
                try { luo.inviteGot(this, invite); } catch (Exception ex) { log.warn("observer failed: " + ex); }
    }

    /* one more layer above the invite handling; here we reply and
     * create the share */
    public synchronized void acceptAndCreateInvite(Invite invite, 
                                                   String localShare, String localPath) 
        throws Exception {

        invite.share = gw.createShare(this, invite.getShareId(), localShare, localPath, null);
        invite.share.start();
        invite.verdict = new Boolean(true);
        replyToInvite(invite);
    }

    /* reject the share */
    public synchronized void rejectInvite(Invite invite) {

        invite.verdict = new Boolean(false);
        replyToInvite(invite);
    }

    /* when replying to an invite */
    private synchronized void replyToInvite(Invite invite) {

        // if we have the share already, then just add the users and
        // start syncing
        sendInviteReply(invite);
        if (invite.share != null) {
            for (User u : invite.getContacts())
                invite.share.syncWith(u);
        }

        // lets remove the invite from mem - this way people can
        // invite us again.  actually, we should not, but keep this
        // accessible if someone wants to invite us again!
        receivedInvites.remove(invite.getShareId());

        // save & notify!
        saveData();
        for (LocalUserObserver luo : observers)
            try { luo.inviteReplied(this, invite); } catch (Exception ex) { log.warn("observer failed: " + ex); }
    }

    /* sending the reply. we send *one* reply when accepting, but to
     * *all* replies if we decline. the idea here is that we want to
     * have one remote user that adds us, while everyone that has
     * invited us need to know that we are not interested. 
     *
     * edit: no, we send the accept to all also. makes it more simple.
     * we might get added multiple times. but that's ok.
     */
    private void sendInviteReply(Invite invite) {

        for (User u : invite.getContacts()) {
            sendInviteReply(invite.getShareId(), invite.verdict.booleanValue(), u);
        }
    }

    private void sendInviteReply(ShareID share, boolean verdict, User contact) {

        try {
            DataOutputStream dos = connectionManager().getContactDataStream(contact, false, true);
            if (dos != null) {
                dos.writeUTF(NetworkMessage.MessageType.INVITE_RESPONSE.toString());
                dos.writeUTF(share.toString());
                dos.writeUTF(verdict + "");
                dos.close();
            }
        } catch (Exception ex) {
            log.warn("error while trying to send invite reply to " + contact);
        }
    }
}
