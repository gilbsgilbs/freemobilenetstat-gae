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

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.RetryOptions;
import com.google.inject.Inject;
import com.google.sitebricks.headless.Reply;
import com.google.sitebricks.headless.Request;
import com.google.sitebricks.headless.Service;
import com.google.sitebricks.http.Get;
import com.google.sitebricks.http.Post;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.cmd.Query;
import com.googlecode.objectify.util.Closeable;
import org.pixmob.freemobile.netstat.gae.Constants;
import org.pixmob.freemobile.netstat.gae.repo.*;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Service
/**
 * Task updating known devices.
 * @author Pixmob
 */
public class UpdateKnownDevices {
    private final Logger logger = Logger.getLogger(UpdateKnownDevices.class.getName());
    private final DeviceStatRepository dsr;

    @Inject
    UpdateKnownDevices(final DeviceStatRepository dsr) {
        this.dsr = dsr;
    }

    @Get
    @Post
    public Reply updateKnownDevices(Request<String> req) {
        try (Closeable service = ObjectifyService.begin()) {
            logger.info("Updating chart values");

            Cursor cursor = null;
            String cursorStr = req.param("cursor");
            if (cursorStr != null) {
                cursor = Cursor.fromWebSafeString(cursorStr);
            }

            long fromDate = System.currentTimeMillis() - 86400 * 1000;
            String fromDateStr = req.param("fromDate");
            if (fromDateStr != null) {
                fromDate = Long.parseLong(fromDateStr);
            }

            Cursor newCursor = doUpdates(fromDate, cursor);
            if (newCursor != null) {
                final Queue queue = QueueFactory.getQueue("update-queue");
                queue.add(
                        withUrl("/task/update-known-devices")
                            .retryOptions(RetryOptions.Builder.withTaskRetryLimit(0))
                            .param("cursor", newCursor.toWebSafeString())
                            .param("fromDate", Long.toString(fromDate))
                );
            }

            return Reply.saying().ok();
        }
    }

    private Cursor doUpdates(long fromDate, Cursor startCursor) {
        boolean finished = true;
        Query<DeviceStat> deviceStatsQuery = null;
        try {
            deviceStatsQuery = dsr.getAll(fromDate, null).limit(1000);
        } catch (DeviceNotFoundException e) {
            throw new RuntimeException("Unexpected error", e);
        }

        assert deviceStatsQuery != null;
        if (startCursor != null) {
            deviceStatsQuery = deviceStatsQuery.startAt(startCursor);
        }

        QueryResultIterator<DeviceStat> deviceStatIterator = deviceStatsQuery.iterator();
        while (deviceStatIterator.hasNext()) {
            finished = false;
            DeviceStat deviceStat = deviceStatIterator.next();

            if (deviceStat.timeOnFreeMobile4g == 0) {
                continue;
            }

            Device device = ofy().load().key(deviceStat.device).now();
            KnownDevice knownDevice = ofy().load().key(device.knownDevice).now();
            knownDevice.timeOn4g += deviceStat.timeOnFreeMobile4g;
            if (knownDevice.timeOn4g >= Constants.THRESHOLD_4G) {
                knownDevice.is4g = true;
            }
            ofy().save().entity(knownDevice).now();
        }

        if (!finished) {
            return deviceStatIterator.getCursor();
        }

        return null;
    }
}
