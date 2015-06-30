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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.channels.Channels;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.RetryOptions;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.tools.cloudstorage.*;
import com.google.sitebricks.headless.Request;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.cmd.Query;
import com.googlecode.objectify.util.Closeable;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.pixmob.freemobile.netstat.gae.Constants;
import org.pixmob.freemobile.netstat.gae.repo.*;

import com.google.inject.Inject;
import com.google.sitebricks.headless.Reply;
import com.google.sitebricks.headless.Service;
import com.google.sitebricks.http.Get;
import com.google.sitebricks.http.Post;

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.googlecode.objectify.ObjectifyService.ofy;

@Service
/**
 * Task updating charts.
 * @author Pixmob
 */
public class UpdateChartsTask {
    private final Logger logger = Logger.getLogger(UpdateChartsTask.class.getName());
    private final GcsService gcsService = GcsServiceFactory.createGcsService(RetryParams.getDefaultInstance());
    private final AppIdentityService appIdentityService = AppIdentityServiceFactory.getAppIdentityService();
    private final ChartDataRepository cdr;
    private final DeviceStatRepository dsr;

    @Inject
    UpdateChartsTask(final ChartDataRepository cdr, final DeviceStatRepository dsr) {
        this.cdr = cdr;
        this.dsr = dsr;
    }

    @Get
    @Post
    public Reply updateCharts(Request<String> req) {
        try (Closeable service = ObjectifyService.begin()) {
            logger.info("Updating chart values");

            long fromDate = System.currentTimeMillis() - 86400 * 1000 * Constants.NETWORK_USAGE_DAYS;
            Set<String> deviceIds = null;
            Set<String> device4gIds = null;
            long totalOrange = 0;
            long totalFreeMobile = 0;
            long totalFreeMobile3g = 0;
            long totalFreeMobile4g = 0;
            long totalFreeMobileFemtocell = 0;
            Cursor startCursor = null;
            String continueParam = req.param("continue");
            if (continueParam != null && Boolean.TRUE.equals(Boolean.parseBoolean(continueParam))) {
                fromDate = Long.parseLong(req.param("fromDate"));
                deviceIds = (Set<String>) readObjectFromBlobstore("deviceIds");
                device4gIds = (Set<String>) readObjectFromBlobstore("device4gIds");
                totalOrange = Long.parseLong(req.param("totalOrange"));
                totalFreeMobile = Long.parseLong(req.param("totalFreeMobile"));
                totalFreeMobile3g = Long.parseLong(req.param("totalFreeMobile3g"));
                totalFreeMobile4g = Long.parseLong(req.param("totalFreeMobile4g"));
                totalFreeMobileFemtocell = Long.parseLong(req.param("totalFreeMobileFemtocell"));
                startCursor = Cursor.fromWebSafeString(req.param("cursor"));
            }
            else {
                deviceIds = new HashSet<>(15000);
                device4gIds = new HashSet<>(15000);
            }

            // Update network usage.
            computeNetworkUsage(startCursor, fromDate, totalOrange, totalFreeMobile, totalFreeMobile3g,
                    totalFreeMobile4g, totalFreeMobileFemtocell, deviceIds, device4gIds);

            return Reply.saying().ok();
        }
    }

    private void writeObjectToBlobstore(String filename, Object obj) {
        try {
            GcsOutputChannel outputChannel = gcsService.createOrReplace(
                    new GcsFilename(appIdentityService.getDefaultGcsBucketName(), "chartData_" + filename),
                    GcsFileOptions.getDefaultInstance()
            );
            ObjectOutputStream oout = new ObjectOutputStream(Channels.newOutputStream(outputChannel));
            oout.writeObject(obj);
            oout.close();
        } catch (IOException e) {
            logger.info(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private Object readObjectFromBlobstore(String filename) {
        GcsInputChannel readChannel = gcsService.openPrefetchingReadChannel(
                new GcsFilename(appIdentityService.getDefaultGcsBucketName(), "chartData_" + filename),
                0, 1024 * 1024
        );
        try {
            try (ObjectInputStream oin = new ObjectInputStream(Channels.newInputStream(readChannel))) {
                return oin.readObject();
            }
        } catch (Exception e) {
            logger.info(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private boolean deleteObjectFromBlobstore(String filename) {
        try {
            return gcsService.delete(
                    new GcsFilename(appIdentityService.getDefaultGcsBucketName(), "chartData_" + filename)
            );
        } catch (IOException e) {
            logger.info(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void computeNetworkUsage(Cursor startCursor, long fromDate, long totalOrange, long totalFreeMobile,
                                     long totalFreeMobile3g, long totalFreeMobile4g, long totalFreeMobileFemtocell,
                                     Set<String> deviceIds, Set<String> device4gIds) {
        logger.info("Updating network usage chart values");

        boolean finished = true;
        Query<DeviceStat> deviceStats = null;
        try {
            deviceStats = dsr.getAll(fromDate, null).limit(300);
        } catch (DeviceNotFoundException e) {
            throw new RuntimeException("Unexpected error", e);
        }

        if (startCursor != null) {
            deviceStats = deviceStats.startAt(startCursor);
        }

        QueryResultIterator<DeviceStat> deviceStatsIterator = deviceStats.iterator();
        while (deviceStatsIterator.hasNext()) {
            finished = false;
            final DeviceStat deviceStat = deviceStatsIterator.next();

            totalOrange += deviceStat.timeOnOrange;
            totalFreeMobile += deviceStat.timeOnFreeMobile;
            final Device dv = ofy().load().key(deviceStat.device).now();
            final KnownDevice kdv = ofy().load().key(dv.knownDevice).now();
            if (kdv.is4g) {
                totalFreeMobile3g += deviceStat.timeOnFreeMobile3g;
                totalFreeMobile4g += deviceStat.timeOnFreeMobile4g;
                totalFreeMobileFemtocell += deviceStat.timeOnFreeMobileFemtocell;
                device4gIds.add(deviceStat.device.getName());
            }
            deviceIds.add(deviceStat.device.getName());
        }

        if (finished) {
            cdr.put(Constants.CHART_NETWORK_USAGE_USERS, deviceIds.size());
            cdr.put(Constants.CHART_NETWORK_USAGE_4G_USERS, device4gIds.size());
            cdr.put(Constants.CHART_NETWORK_USAGE_ORANGE, totalOrange);
            cdr.put(Constants.CHART_NETWORK_USAGE_FREE_MOBILE, totalFreeMobile);
            cdr.put(Constants.CHART_NETWORK_USAGE_FREE_MOBILE_3G, totalFreeMobile3g);
            cdr.put(Constants.CHART_NETWORK_USAGE_FREE_MOBILE_4G, totalFreeMobile4g);
            cdr.put(Constants.CHART_NETWORK_USAGE_FREE_MOBILE_FEMTOCELL, totalFreeMobileFemtocell);

            deleteObjectFromBlobstore("deviceIds");
            deleteObjectFromBlobstore("device4gIds");

            logger.info("Network usage updated: " + deviceIds.size() + " active devices, " + totalOrange
                    + " ms on Orange, " + totalFreeMobile + " ms on Free Mobile and "
                    + totalFreeMobile3g + " ms on Free Mobile 3G, " + totalFreeMobile4g + " ms on Free Mobile 4G, "
                    + totalFreeMobileFemtocell + " ms on Free Mobile Femtocell.");
        }
        else {
            final Cursor newCursor = deviceStatsIterator.getCursor();

            writeObjectToBlobstore("deviceIds", deviceIds);
            writeObjectToBlobstore("device4gIds", device4gIds);

            final Queue queue = QueueFactory.getQueue("update-queue");
            final TaskOptions taskOptions = withUrl("/task/update-charts")
                    .retryOptions(RetryOptions.Builder.withTaskRetryLimit(0))
                    .param("cursor", newCursor.toWebSafeString())
                    .param("fromDate", Long.toString(fromDate))
                    .param("totalOrange", Long.toString(totalOrange))
                    .param("totalFreeMobile", Long.toString(totalFreeMobile))
                    .param("totalFreeMobile3g", Long.toString(totalFreeMobile3g))
                    .param("totalFreeMobile4g", Long.toString(totalFreeMobile4g))
                    .param("totalFreeMobileFemtocell", Long.toString(totalFreeMobileFemtocell))
                    .param("continue", Boolean.toString(true));
            queue.add(taskOptions);
        }
    }
}
