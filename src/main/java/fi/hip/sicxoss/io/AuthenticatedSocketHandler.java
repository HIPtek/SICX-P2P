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
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.net.*;
import java.util.*;

import org.apache.log4j.Logger;

import fi.hip.sicxoss.io.message.*;
import fi.hip.sicxoss.ident.*;
import fi.hip.sicxoss.util.*;

/**
 * Yet another socket handler. This one builds on
 * MessageSocketHandler's stream functions to implement nonce- based
 * authentication.
 */
public abstract class AuthenticatedSocketHandler 
    extends MessageSocketHandler {

    private static final Logger log = Logger.getLogger(AuthenticatedSocketHandler.class);

    // for session authentication
    private String localNonce;
    private String remoteNonce;

    protected User remoteUser;
    protected User realRemoteUser;
    protected LocalUser localUser;
    private boolean acceptAnon = false; // if we accept the remote being anonymous
    protected boolean isAuthenticated = false;
    private String addr;

    private int[] reconnect_timing;
    private int reconnectIndex;
    private ConnectionReconnector connRec;

/**
 * AuthenticatedSocketHandler class.
 *
 * @author koskela
 */
    public interface ConnectionReconnector {
        public void reschedule(AuthenticatedSocketHandler sh, long nextReconnect);
        public void cancelReschedule(AuthenticatedSocketHandler sh);
    }

    /**
     * create from a existing socket
     */
    public AuthenticatedSocketHandler(SocketChannel sc, LocalUser localUser, User remoteUser)
        throws Exception {
        super(sc);

        reconnectIndex = 0;
        initAuthentication(localUser, remoteUser);
    }

    /**
     * create an outgoing socket
     */
    public AuthenticatedSocketHandler(SocketAddress sa, LocalUser localUser, User remoteUser) 
        throws Exception {
        super(sa);

        reconnectIndex = 0;
        initAuthentication(localUser, remoteUser);
    }

    /**
     * create an outgoing socket
     */
    public AuthenticatedSocketHandler(String str, LocalUser localUser, User remoteUser) 
        throws Exception {
        super(str);

        addr = str;
        reconnectIndex = 0;
        initAuthentication(localUser, remoteUser);
    }

    public String getAddress() {
        return addr;
    }

    protected void setAcceptAnon(boolean accept) {
        this.acceptAnon = accept;
    }

    private void initAuthentication(LocalUser localUser, User remoteUser) {

        this.localUser = localUser;
        this.remoteUser = remoteUser;
        this.realRemoteUser = null;
        this.localNonce = null;
        this.remoteNonce = null;
        this.isAuthenticated = false;
    }

    /** sets the reconnect timer */
    protected void setReconnectTiming(int[] timing, ConnectionReconnector connRec) {
        this.reconnect_timing = timing;
        this.connRec = connRec;
    }

    /** passes the data to the client for processing */
    public abstract void gotAuthenticatedDataStream(DataInputStream in)
        throws Exception;

    /** called when the authentication is complete. or if we disconnect */
    public abstract void authenticationComplete(boolean success);

    private final void callAuthenticationComplete(boolean success) {

        if (success) {
            reconnectIndex = 0;
            disableTimeOut(); // and we're done!
        }

        authenticationComplete(success);

        if (!success) {
            if (reconnect_timing != null) {
                long nextReconnect = System.currentTimeMillis() + reconnect_timing[reconnectIndex];
                reconnectIndex++;
                if (reconnectIndex >= reconnect_timing.length)
                    reconnectIndex = reconnect_timing.length - 1;
                connRec.reschedule(this, nextReconnect);
            }
            
            initAuthentication(localUser, remoteUser);
        }
    }

    public void cancel() {
        if (connRec != null)
            connRec.cancelReschedule(this);
    }
                
    public void gotClose() {
        callAuthenticationComplete(false);
    }

    public void gotConnecting() {
        //log.info("we are connecting!");
    }

    public void gotConnected() {
        log.info("we connect!");
        startAuthentication();
    }

    /**
     * Called when authentication fails
     */
    protected void authError() 
        throws Exception {

        callAuthenticationComplete(false);
        close();
    }

    /**
     * called when there's new data to be read
     */
    public void gotDataStream(DataInputStream in)
        throws Exception {

        if (isAuthenticated) {

            gotAuthenticatedDataStream(in);

        } else if (remoteNonce == null) {

            // we might not have started the authentication!
            startAuthentication();

            //log.debug("processing remote nonce..");
            String greeting = in.readUTF();
            String ident = in.readUTF();
            if (ident.length() != 0 && ((localUser == null) || (localUser.getId().toString().compareTo(ident) != 0))) {
                log.error("the lookup server is  expecting someone else: " + ident);
                authError(); return;
            } else {
                remoteNonce = in.readUTF();
                
                DataOutputStream out = sendDataStream();
                if (localUser != null) {

                    SignableProperties sp = new SignableProperties();
                    sp.setProperty("nonce", remoteNonce);
                    localUser.sign(sp);

                    out.writeUTF(localUser.getSignedTextData());
                    out.writeUTF(sp.getSignedTextData());
                } else {
                    out.writeUTF("");
                    out.writeUTF("");
                }
                out.close();
                setTimeOutAfter(5000); // we don't want to wait forever
            }
            //log.debug("processing remote nonce.. done");
        } else if (realRemoteUser == null) {
                
            //log.debug("processing remote user");
            String users = in.readUTF();
            String props = in.readUTF();
            User user = null;
            if (users.length() > 0 && props.length() > 0) {

                user = User.fromData(users);
                SignableProperties sp = new SignableProperties();
                sp.init(props.getBytes());
                
                if (localNonce.compareTo(sp.getProperty("nonce")) != 0) {
                    log.error(": " + sp.getProperty("nonce"));
                    authError(); return;
                } else if (localNonce.compareTo(sp.getProperty("nonce")) != 0) {
                    log.error("the signed nonce does not match: " + sp.getProperty("nonce"));
                    authError(); return;
                } else if (!user.verify(sp)) {
                    log.error("signature on signed nonce does not verify");
                    authError(); return;
                }
            
            } else if (!acceptAnon || remoteUser != null) {
                log.error("we got an anon user, but we are not accepting it");
                authError(); return;
            }

            if (remoteUser != null && remoteUser.getId().compareTo(user.getId()) != 0) {
                log.error("we got connected to the wrong user");
                authError(); return;
            } else {
                log.error("ok, we are now connected to " + user);
                realRemoteUser = user;
                isAuthenticated = true;
                callAuthenticationComplete(true);
            }
        }
    }

    public User getRemoteUser() {
        return realRemoteUser;
    }

    public void startAuthentication() {
        
        if (localNonce != null)
            return;

        log.info("initiating a new authenticated session");
        try {
            localNonce = UUID.randomUUID().toString();
                
            // start: greeting, possible target (may be empty), nonce.
            DataOutputStream out = sendDataStream();
            out.writeUTF("sicxoss:0.1:Hello!");
            if (remoteUser != null)
                out.writeUTF(remoteUser.getId());
            else
                out.writeUTF(""); // we're not looking for anyone special
            out.writeUTF(localNonce);
            out.close();
            setTimeOutAfter(5000); // we don't want to wait forever
        } catch (Exception ex) {
            log.warn("exception while starting the dialog: " + ex);
        }
    }
}
