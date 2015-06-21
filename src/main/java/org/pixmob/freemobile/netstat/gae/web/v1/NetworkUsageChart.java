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
package org.pixmob.freemobile.netstat.gae.web.v1;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.util.Closeable;
import org.pixmob.freemobile.netstat.gae.Constants;
import org.pixmob.freemobile.netstat.gae.repo.ChartDataRepository;

import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.inject.Inject;
import com.google.sitebricks.client.transport.Json;
import com.google.sitebricks.headless.Reply;
import com.google.sitebricks.headless.Request;
import com.google.sitebricks.headless.Service;
import com.google.sitebricks.http.Get;

@Service
/**
 * EST service handling chart resources.
 * @author Pixmob
 */
public class NetworkUsageChart {
    private final Logger logger = Logger.getLogger(NetworkUsageChart.class.getName());
    private final ChartDataRepository cdr;
    private final MemcacheService memcacheService;

    @Inject
    NetworkUsageChart(final ChartDataRepository cdr, final MemcacheService memcacheService) {
        this.cdr = cdr;
        this.memcacheService = memcacheService;
    }

    @Get
    public Reply networkUsage(Request<String> req) {
        try (Closeable service = ObjectifyService.begin()) {
            final String networkUsageKey = "networkUsage";
            NetworkUsage nu = (NetworkUsage) memcacheService.get(networkUsageKey);
            if (nu == null) {
                logger.info("Get network usage from datastore");
                nu = new NetworkUsage();
                nu.orange = cdr.get(Constants.CHART_NETWORK_USAGE_ORANGE, 0);
                nu.freeMobile = cdr.get(Constants.CHART_NETWORK_USAGE_FREE_MOBILE, 0);
                nu.freeMobile3g = cdr.get(Constants.CHART_NETWORK_USAGE_FREE_MOBILE_3G, 0);
                nu.freeMobile4g = cdr.get(Constants.CHART_NETWORK_USAGE_FREE_MOBILE_4G, 0);
                nu.freeMobileFemtocell = cdr.get(Constants.CHART_NETWORK_USAGE_FREE_MOBILE_FEMTOCELL, 0);
                nu.users = (int) cdr.get(Constants.CHART_NETWORK_USAGE_USERS, 0);
                nu.users4g = (int) cdr.get(Constants.CHART_NETWORK_USAGE_4G_USERS, 0);
                nu.days = Constants.NETWORK_USAGE_DAYS;
                memcacheService.put(networkUsageKey, nu, Expiration.byDeltaSeconds(60 * 30));
            } else {
                logger.info("Get network usage from cache");
            }

            // Add cache headers to the response.
            final int cacheDuration = 60 * 60 * 2; // in seconds
            final Map<String, String> headers = new HashMap<>(3);
            headers.put("Cache-Control", "public, max-age=" + cacheDuration);
            headers.put("Pragma", "Public");
            headers.put("Age", "0");

            return Reply.with(nu).as(Json.class).headers(headers);
        }
    }

    /**
     * JSON data class for network usage.
     * @author Pixmob
     */
    public static class NetworkUsage implements Externalizable {
        public long orange;
        public long freeMobile;
        public long freeMobile3g;
        public long freeMobile4g;
        public long freeMobileFemtocell;
        public int users;
        public int users4g;
        public int days;

        public NetworkUsage() {
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            orange = in.readLong();
            freeMobile = in.readLong();
            freeMobile3g = in.readLong();
            freeMobile4g = in.readLong();
            freeMobileFemtocell = in.readLong();
            users = in.readInt();
            users4g = in.readInt();
            days = in.readInt();
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeLong(orange);
            out.writeLong(freeMobile);
            out.writeLong(freeMobile3g);
            out.writeLong(freeMobile4g);
            out.writeLong(freeMobileFemtocell);
            out.writeInt(users);
            out.writeInt(users4g);
            out.writeInt(days);
        }

        @Override
        public String toString() {
            return "orange=" + orange + ", freeMobile=" + freeMobile + "freeMobile3g=" + freeMobile3g
                    + ", freeMobile4g=" + freeMobile4g + "freeMobileFemtocell=" + freeMobileFemtocell
                    + ", users=" + users + ", users4g=" + users4g + ", days=" + days;
        }
    }
}
