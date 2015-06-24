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
import com.google.inject.Inject;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.cmd.QueryKeys;

/**
 * {@link Device} repository.
 * @author Pixmob
 */
public class DeviceRepository {
    private final Logger logger = Logger.getLogger(DeviceRepository.class.getName());

    private final KnownDeviceRepository kdr;

    @Inject
    DeviceRepository(final KnownDeviceRepository kdr) {
        this.kdr = kdr;
    }
    /**
     * Create an user device in the datastore.
     * @throws DeviceException
     *             if the device id is already used
     */
    public Device create(String deviceId, String brand, String model) throws DeviceException {
        if (deviceId == null) {
            throw new IllegalArgumentException("Device identifier is required");
        }

        if (ofy().load().type(Device.class).id(deviceId).now() != null) {
            throw new DeviceException("Cannot create device: device identifier conflict", deviceId);
        }

        KnownDevice kd = kdr.create(brand, model);

        final Device ud = new Device();
        ud.id = deviceId;
        ud.knownDevice = Key.create(kd);

        ofy().save().entity(ud).now();

        logger.info("Device created: " + deviceId);

        return ud;
    }

    public void delete(String deviceId) {
        if (deviceId != null) {
            // Get all statistics records for this device.
            Device device = ofy().load().type(Device.class).id(deviceId).now();

            if (device != null) {
                final QueryKeys<DeviceStat> deviceStats = ofy().load().type(DeviceStat.class).filter("device", device).keys();

                // Delete records related to this device.
                ofy().delete().keys(deviceStats).now();
                ofy().delete().entity(device).now();

                logger.info("Device deleted: " + deviceId);
            }
            else {
                logger.info("Device not found.");
            }
        }
    }

    public Device get(String deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("Device identifier is required");
        }

        return ofy().load().type(Device.class).id(deviceId).now();
    }
}
