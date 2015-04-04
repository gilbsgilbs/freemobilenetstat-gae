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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import org.pixmob.freemobile.netstat.gae.Constants;
import org.pixmob.freemobile.netstat.gae.repo.ChartDataRepository;
import org.pixmob.freemobile.netstat.gae.repo.DeviceNotFoundException;
import org.pixmob.freemobile.netstat.gae.repo.DeviceStat;
import org.pixmob.freemobile.netstat.gae.repo.DeviceStatRepository;

import com.google.inject.Inject;
import com.google.sitebricks.headless.Reply;
import com.google.sitebricks.headless.Service;
import com.google.sitebricks.http.Get;
import com.google.sitebricks.http.Post;

@Service
/**
 * Task updating charts.
 * @author Pixmob
 */
public class UpdateChartsTask {
    private final Logger logger = Logger.getLogger(UpdateChartsTask.class.getName());
    private final ChartDataRepository cdr;
    private final DeviceStatRepository dsr;

    @Inject
    UpdateChartsTask(final ChartDataRepository cdr, final DeviceStatRepository dsr) {
        this.cdr = cdr;
        this.dsr = dsr;
    }

    @Get
    @Post
    public Reply<?> updateCharts() {
        logger.info("Updating chart values");

        // Update network usage.
        computeNetworkUsage();

        return Reply.saying().ok();
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
