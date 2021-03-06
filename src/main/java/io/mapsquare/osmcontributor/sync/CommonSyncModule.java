/**
 * Copyright (C) 2016 eBusiness Information
 *
 * This file is part of OSM Contributor.
 *
 * OSM Contributor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OSM Contributor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OSM Contributor.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.mapsquare.osmcontributor.sync;

import com.google.gson.Gson;
import com.squareup.okhttp.OkHttpClient;

import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.transform.RegistryMatcher;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.mapsquare.osmcontributor.sync.converter.JodaTimeDateTimeTransform;

@Module
@Singleton
public class CommonSyncModule {

    @Provides
    OkHttpClient getOkHttpClient() {
        return new OkHttpClient();
    }

    @Provides
    Gson getGson() {
        return new Gson();
    }

    @Provides
    Persister getPersister() {
        RegistryMatcher matchers = new RegistryMatcher();
        matchers.bind(org.joda.time.DateTime.class, JodaTimeDateTimeTransform.class);

        Strategy strategy = new AnnotationStrategy();
        return new Persister(strategy, matchers);
    }
}
