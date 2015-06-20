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
import com.googlecode.objectify.annotation.*;

import java.util.List;

/**
 * User device datastore entity.
 * @author Pixmob
 */
@Cache(expirationSeconds = 60)
@Entity
@Index
public class Device {
    @Id public String id;
    public long registrationDate = System.currentTimeMillis();
    public Key<KnownDevice> knownDevice;
}
