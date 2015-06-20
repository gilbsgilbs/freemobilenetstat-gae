/*
 * Copyright (C) 2012 Pixmob (http://github.com/pixmob)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pixmob.freemobile.netstat.gae.web.task;

import com.google.inject.Inject;
import com.google.sitebricks.headless.Reply;
import com.google.sitebricks.headless.Service;
import com.google.sitebricks.http.Get;
import com.google.sitebricks.http.Post;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.util.Closeable;
import org.pixmob.freemobile.netstat.gae.Constants;
import org.pixmob.freemobile.netstat.gae.repo.*;

import java.util.*;
import java.util.logging.Logger;

@Service
/**
 * Task updating known devices.
 * @author Pixmob
 */
public class UpdateKnownDevices {
    private final Logger logger = Logger.getLogger(UpdateKnownDevices.class.getName());
    private final ChartDataRepository cdr;
    private final DeviceStatRepository dsr;
    private final KnownDeviceRepository kdr;

    @Inject
    UpdateKnownDevices(final ChartDataRepository cdr, final DeviceStatRepository dsr, final KnownDeviceRepository kdr) {
        this.cdr = cdr;
        this.dsr = dsr;
        this.kdr = kdr;
    }

    @Get
    @Post
    public Reply updateKnownDevices() {
        try (Closeable service = ObjectifyService.begin()) {
            logger.info("Updating chart values");

            final long THRESHOLD_4G = 10; // Devices with 4G time > 10 will be marked as 4G ready
            final long fromDate = System.currentTimeMillis() - 86400 * 1000 * Constants.NETWORK_USAGE_DAYS;
            final Iterator<DeviceStat> i;
            final Objectify ofy = ObjectifyService.ofy();
            try {
                i = dsr.getAll(fromDate, null);
            } catch (DeviceNotFoundException e) {
                throw new RuntimeException("Unexpected error", e);
            }

            HashMap<KnownDevice, Long> timeOn4G = new HashMap<>();

            while (i.hasNext()) {
                final DeviceStat ds = i.next();
                Device statDevice = ofy.load().key(ds.device).now();
                KnownDevice statKnownDevice = ofy.load().key(statDevice.knownDevice).now();
                if (!statKnownDevice.is4g) {
                    if (!timeOn4G.containsKey(statKnownDevice)) {
                        timeOn4G.put(statKnownDevice, ds.timeOnFreeMobile4g);
                    }
                    else {
                        Long time = timeOn4G.get(statKnownDevice);
                        if (time <= THRESHOLD_4G) {
                            timeOn4G.put(statKnownDevice, time + ds.timeOnFreeMobile4g);
                        }
                    }
                }
            }

            for (Map.Entry<KnownDevice, Long> entry : timeOn4G.entrySet()) {
                if (entry.getValue() >= THRESHOLD_4G) {
                    try {
                        kdr.is4g(entry.getKey().id);
                    } catch (KnownDeviceNotFoundException e) {
                        // This should never happen
                        e.printStackTrace();
                    }
                }
            }

            return Reply.saying().ok();
        }
    }

    private void computeNetworkUsage() {
        logger.info("Updating network usage chart values");

        final long fromDate = System.currentTimeMillis() - 86400 * 1000 * Constants.NETWORK_USAGE_DAYS;
        final Iterator<DeviceStat> i;
        try {
            i = dsr.getAll(fromDate, null);
        } catch (DeviceNotFoundException e) {
            throw new RuntimeException("Unexpected error", e);
        }

        long totalOrange = 0;
        long totalFreeMobile = 0;
        long totalFreeMobile3g = 0;
        long totalFreeMobile4g = 0;
        long totalFreeMobileFemtocell = 0;
        final Set<String> deviceIds = new HashSet<String>(256);
        while (i.hasNext()) {
            final DeviceStat ds = i.next();
            totalOrange += ds.timeOnOrange;
            totalFreeMobile += ds.timeOnFreeMobile;
            totalFreeMobile3g += ds.timeOnFreeMobile3g;
            totalFreeMobile4g += ds.timeOnFreeMobile4g;
            totalFreeMobileFemtocell += ds.timeOnFreeMobileFemtocell;
            deviceIds.add(ds.device.getName());
        }

        cdr.put(Constants.CHART_NETWORK_USAGE_USERS, deviceIds.size());
        cdr.put(Constants.CHART_NETWORK_USAGE_ORANGE, totalOrange);
        cdr.put(Constants.CHART_NETWORK_USAGE_FREE_MOBILE, totalFreeMobile);
        cdr.put(Constants.CHART_NETWORK_USAGE_FREE_MOBILE_3G, totalFreeMobile3g);
        cdr.put(Constants.CHART_NETWORK_USAGE_FREE_MOBILE_4G, totalFreeMobile4g);
        cdr.put(Constants.CHART_NETWORK_USAGE_FREE_MOBILE_FEMTOCELL, totalFreeMobileFemtocell);

        logger.info("Network usage updated: " + deviceIds.size() + " active devices, " + totalOrange
                + " ms on Orange, " + totalFreeMobile + " ms on Free Mobile and "
                + totalFreeMobile3g + " ms on Free Mobile 3G, " + totalFreeMobile4g + " ms on Free Mobile 4G, "
                + totalFreeMobileFemtocell + " ms on Free Mobile Femtocell.");
    }
}
