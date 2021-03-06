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
package fi.hip.sicxoss.servlet;

import java.net.*;
import java.io.*;
import java.util.*;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;

import org.apache.log4j.Logger;

import com.bradmcevoy.http.ServletRequest;
import com.bradmcevoy.http.ServletResponse;
import com.bradmcevoy.http.Request;
import com.bradmcevoy.http.Response;

import fi.hip.sicxoss.LocalGateway;

/**
 * GatewayServlet class.
 *
 * @author koskela
 */
public class GatewayServlet 
    extends HttpServlet {

    private static final Logger log = Logger.getLogger(GatewayServlet.class);
    private LocalGateway gw;

    public GatewayServlet(LocalGateway gw) {
        this.gw = gw;
    }

    @Override
    public void init(ServletConfig config)
        throws ServletException {

        log.info("initing the servlet");
        super.init(config);
    }

    @Override
    public void destroy() {

        log.info("destroying the servlet");
        super.destroy();
    }

    @Override
    protected void service(HttpServletRequest req,
                           HttpServletResponse resp) {

        PrintWriter out = null;
        try {
            Request request = new ServletRequest( req );
            Response response = new ServletResponse( resp ); 

            log.debug("Request " + req.getMethod() + " to " + req.getRequestURL());
            gw.getWebDAVManager().process(request, response); 
        } catch (Exception ex) {
            log.error("Error while servicing request: " + ex);
            ex.printStackTrace();
        } finally {
            try {
                resp.getOutputStream().flush();
                resp.flushBuffer();
            } catch (Exception iex) {
            }
        }
    }
}
