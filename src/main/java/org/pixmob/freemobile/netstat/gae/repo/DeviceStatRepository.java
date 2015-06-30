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
package org.pixmob.freemobile.netstat.gae.repo;

import java.util.logging.Logger;

import static com.googlecode.objectify.ObjectifyService.ofy;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;
import org.pixmob.freemobile.netstat.gae.Constants;

/**
 * {@link DeviceStat} repository.
 * @author Pixmob
 */
public class DeviceStatRepository {
    private final Logger logger = Logger.getLogger(DeviceStatRepository.class.getName());

    public DeviceStat update(String deviceId, long date, long timeOnOrange, long timeOnFreeMobile,
                             long timeOnFreeMobile3g, long timeOnFreeMobile4g, long timeOnFreeMobileFemtocell)
            throws DeviceNotFoundException {
        if (deviceId == null) {
            throw new IllegalArgumentException("Device identifier is required");
        }

        final Device ud = ofy().load().type(Device.class).id(deviceId).now();
        if (ud == null) {
            throw new DeviceNotFoundException(deviceId);
        }

        DeviceStat ds = ofy().load().type(DeviceStat.class).filter("device", ud).filter("date", date).first().now();
        if (ds == null) {
            ds = new DeviceStat();
            ds.device = Key.create(Device.class, deviceId);
            ds.date = date;

            logger.info("Creating statistics for device " + deviceId);
        } else {
            logger.info("Updating statistics for device " + deviceId);
        }

        if (timeOnFreeMobile4g > 0) {
            Device dv = ofy().load().type(Device.class).id(deviceId).now();
            KnownDevice kdv = ofy().load().key(dv.knownDevice).now();
            kdv.timeOn4g += timeOnFreeMobile4g;

            if (kdv.timeOn4g >= Constants.THRESHOLD_4G) {
                kdv.is4g = true;
            }

            ofy().save().entity(kdv).now();
        }

        ds.timeOnOrange = timeOnOrange;
        ds.timeOnFreeMobile = timeOnFreeMobile;
        ds.timeOnFreeMobile3g = timeOnFreeMobile3g;
        ds.timeOnFreeMobile4g = timeOnFreeMobile4g;
        ds.timeOnFreeMobileFemtocell = timeOnFreeMobileFemtocell;
        ofy().save().entity(ds);

        return ds;
    }

    public Query<DeviceStat> getAll(long fromDate, String deviceId) throws DeviceNotFoundException {
        final Query<DeviceStat> deviceStats;
        Device device = null;
        if (deviceId != null) {
            device = ofy().load().type(Device.class).id(deviceId).now();
            if (device == null) {
                throw new DeviceNotFoundException(deviceId);
            }
            deviceStats = ofy().load().type(DeviceStat.class).filter("device", device).filter("date >=", fromDate);
                    // .prefetchSize(30).chunkSize(20); // ??
        } else {
            deviceStats = ofy().load().type(DeviceStat.class).filter("date >=", fromDate);
                    // .prefetchSize(30).chunkSize(20); // ??
        }

        return deviceStats;
    }
}
