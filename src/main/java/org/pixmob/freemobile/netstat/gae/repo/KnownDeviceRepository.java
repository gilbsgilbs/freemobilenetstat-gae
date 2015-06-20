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

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;

import java.util.Iterator;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * {@link Device} repository.
 * @author Pixmob
 */
public class KnownDeviceRepository {
    private final Logger logger = Logger.getLogger(KnownDeviceRepository.class.getName());

    /**
     * Create an user device in the datastore.
     * @throws DeviceException
     *             if the device id is already used
     */
    public KnownDevice create(String brand, String model) throws DeviceException {
        final Objectify ofy = ObjectifyService.ofy();


        KnownDevice ud = ofy.load().type(KnownDevice.class).filter("brand", brand).filter("model", model).first().now();
        if (ud != null) {
            logger.fine("Already known device " + brand + " " + model);
            return ud;
        }

        logger.fine("Unknown device. Initializing it.");

        ud = new KnownDevice();
        ud.id = UUID.randomUUID().getMostSignificantBits();
        ud.brand = brand;
        ud.model = model;

        ofy.save().entity(ud).now();

        return ud;
    }

    public void is4g(Long deviceId) throws KnownDeviceNotFoundException {
        if (deviceId != null) {
            // Get all statistics records for this device.
            final Objectify ofy = ObjectifyService.ofy();

            KnownDevice device = ofy.load().type(KnownDevice.class).id(deviceId).now();

            if (device == null) {
                throw new KnownDeviceNotFoundException(deviceId);
            }

            device.is4g = true;

            ofy.save().entity(device).now();

            logger.info("Known device " + deviceId + " marked as 4G compatible.");
        }
    }

    public KnownDevice get(String deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("Known Device identifier is required");
        }

        final Objectify ofy = ObjectifyService.ofy();
        return ofy.load().type(KnownDevice.class).id(deviceId).now();
    }
}
