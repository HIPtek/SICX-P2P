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

import java.util.*;
import java.io.*;

import org.apache.log4j.Logger;

import fi.hip.sicxoss.ident.*;
import fi.hip.sicxoss.model.*;
import fi.hip.sicxoss.io.message.*;

/**
 * Small class that monitors how much bandwidth something uses
 * @author koskela
 */
public class BandwidthMonitor {

    // for statistics
    private long statsStartTime = 0;
    private long statsTotalBytes = 0;
    
    private long statsPeriodStart = 0;
    private long statsPeriodBytes = 0;
    private long statsPeriodSpeed = 0;
    
    private long stopTime = 0;

    public void start() {
        if (statsStartTime == 0) {
            long now = System.currentTimeMillis();
            statsStartTime = now;
            statsPeriodStart = now;
        }
    }
    
    public void stop() {
        stopTime = System.currentTimeMillis();
    }

    public long avgSpeed() {
        long st = stopTime;
        if (st == 0)
            st = System.currentTimeMillis();
        return (statsTotalBytes*1000) / (System.currentTimeMillis()-statsStartTime);
    }

    public long currentSpeed() {
        return statsPeriodSpeed;
    }

    public void update(int bytes) {

        long now = System.currentTimeMillis();;
        statsTotalBytes += bytes;
        statsPeriodBytes += bytes;

        if (statsStartTime == 0)
            statsStartTime = now;
        if (statsPeriodStart == 0)
            statsPeriodStart = now;
        if (now - statsPeriodStart > 2000) {
            statsPeriodSpeed = ((statsPeriodBytes*1000) / (now - statsPeriodStart));
            //System.out.println("download rate: " + statsPeriodSpeed + "bps, " + ((statsTotalBytes*1000) / (now - statsStartTime)) + "bps total");
            statsPeriodBytes = 0;
            statsPeriodStart = now;
        }
    }

}
