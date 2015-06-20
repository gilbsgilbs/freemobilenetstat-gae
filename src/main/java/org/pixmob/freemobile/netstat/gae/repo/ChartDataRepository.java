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

import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.cmd.Query;

/**
 * {@link ChartData} repository.
 * @author Pixmob
 */
public class ChartDataRepository {
    private final Logger logger = Logger.getLogger(ChartDataRepository.class.getName());

    public long get(String name, long defaultValue) {
        if (name == null) {
            throw new IllegalArgumentException("Name is required");
        }

        final Objectify of = ObjectifyService.ofy();
        final Iterator<ChartData> i = of.load().type(ChartData.class).order("date").filter("name", name).iterator();
        if (!i.hasNext()) {
            return defaultValue;
        }
        return i.next().value;
    }

    public void add(String name, long value) {
        if (name == null) {
            throw new IllegalArgumentException("Name is required");
        }

        logger.fine("Adding chart value: " + name + "=" + value);

        final ChartData cd = new ChartData();
        cd.name = name;
        cd.value = value;
        cd.date = System.currentTimeMillis();
        ObjectifyService.ofy().save().entity(cd).now();
    }

    public void put(String name, long value) {
        if (name == null) {
            throw new IllegalArgumentException("Name is required");
        }

        logger.fine("Putting chart value: " + name + "=" + value);

        remove(name);

        final Objectify of = ObjectifyService.ofy();
        final ChartData cd = new ChartData();
        cd.name = name;
        cd.value = value;
        cd.date = System.currentTimeMillis();
        of.save().entity(cd).now();
    }

    public void remove(String name) {
        if (name != null) {
            logger.fine("Remove chart value: " + name);
            final Objectify of = ObjectifyService.ofy();
            Iterable<Key<ChartData>> chartData = of.load().type(ChartData.class).filter("name", name).keys();
            of.delete().keys(chartData);
        }
    }
}
