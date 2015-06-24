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

import static com.googlecode.objectify.ObjectifyService.ofy;
import com.google.inject.Inject;
import com.google.sitebricks.headless.Reply;
import com.google.sitebricks.headless.Service;
import com.google.sitebricks.http.Get;
import com.google.sitebricks.http.Post;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.cmd.Query;
import com.googlecode.objectify.util.Closeable;
import org.pixmob.freemobile.netstat.gae.Constants;
import org.pixmob.freemobile.netstat.gae.repo.*;

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

            final int THRESHOLD_4G = 120; // Devices with 4G time > 120 will be marked as 4G ready
            final long fromDate = System.currentTimeMillis() - 86400 * 1000 * Constants.NETWORK_USAGE_DAYS;

            for (KnownDevice knownDevice : kdr.getNon4GDevices()) {
                Query<DeviceStat> deviceStats = null;
                int time = 0;

                try {
                    deviceStats = dsr.getAll(fromDate, null);
                } catch (DeviceNotFoundException e) {
                    throw new RuntimeException("Unexpected error", e);
                }

                for (DeviceStat deviceStat : deviceStats) {
                    ofy().clear();
                    Device device = ofy().cache(false).load().key(deviceStat.device).now();
                    if (ofy().cache(false).load().key(device.knownDevice).now().id == knownDevice.id) {
                        time += deviceStat.timeOnFreeMobile4g;
                        if (time >= THRESHOLD_4G) {
                            knownDevice.is4g = true;
                            ofy().save().entity(knownDevice).now();
                            break;
                        }
                    }
                }
            }

            return Reply.saying().ok();
        }
    }
}
