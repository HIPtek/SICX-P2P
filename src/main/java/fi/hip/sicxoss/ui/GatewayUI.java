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
package fi.hip.sicxoss.ui;

import java.util.*;
import java.io.*;

import org.apache.log4j.Logger;

import fi.hip.sicxoss.model.ShareModel;
import fi.hip.sicxoss.LocalGateway;
import fi.hip.sicxoss.ident.LocalUser;


/**
 * The interface all UIs should implement.
 * @author koskela
 */
public interface GatewayUI 
    extends ShareModel.ShareModelObserver,
            LocalGateway.LocalGatewayObserver,
            LocalUser.LocalUserObserver
{

    // initialization .. ?

    // invitation got ..

    // add share..
    
    // add store.. ?
    
    // init system?
    public void init(LocalGateway gw);
    public void close();

    public String queryPassword(String head, String body);
}
