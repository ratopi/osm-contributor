/**
 * ## Original work
 *
 * Copyright Mapbox Inc 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ## Modifications
 *
 * Copyright 2015 eBusiness Information
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
package io.mapsquare.osmcontributor.tileslayer;

import android.os.AsyncTask;

import com.mapbox.mapboxsdk.tileprovider.MapTile;
import com.mapbox.mapboxsdk.tileprovider.tilesource.TileLayer;
import com.mapbox.mapboxsdk.util.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Tweaked {@link com.mapbox.mapboxsdk.tileprovider.tilesource.BingTileLayer} at v0.7.3 in order to
 * zoom on a tile of provider zoom limit when the zoom level is superior to it.
 * <br/>
 * Corrected an issues in the constructor where metadata where downloaded without the possibility to set the tile.
 * <br/>
 * Our changes are marked with "Custom tweak:" comments.
 * <p/>
 * Implementation of {@link WebSourceTileLayer} for a Bing map.
 */
public class BingTileLayer extends WebSourceTileLayer {

    public static String TAG = "BingTileLayer";

    public static final String IMAGERYSET_AERIAL = "Aerial";
    public static final String IMAGERYSET_AERIALWITHLABELS = "AerialWithLabels";
    public static final String IMAGERYSET_ROAD = "Road";

    private static final String BASE_URL_PATTERN = "http://dev.virtualearth.net/REST/V1/Imagery/Metadata/%s?mapVersion=v1&output=json&key=%s";

    private String mBingMapKey = "";

    private String mStyle = IMAGERYSET_ROAD;

    private boolean mHasMetadata = false;

    // Custom tweak: Set the style in the constructor before calling getMetadata
    public BingTileLayer(String key, String style, int providerZoomLimit) {
        super("Bing Tile Layer", BASE_URL_PATTERN, false, providerZoomLimit);

        setBingMapKey(key);
        mStyle = style;

        // Default Bing Maps zoom levels.
        this.setMinimumZoomLevel(1);
        this.setMaximumZoomLevel(22);

        getMetadata();
    }

    @Override
    public String getTileURL(final MapTile aTile, boolean hdpi) {
        if (!mHasMetadata) {
            getMetadata();
        }
        // Custom tweak: If the zoom > zoom limit, create the url for the tile of zoom limit
        if (aTile.getZ() > getProviderZoomLimit()) {
            return mUrl.replace("{quadkey}", quadTree(new MapTile(getProviderZoomLimit(), aTile.getX() / (int) Math.pow(2, (aTile.getZ() - getProviderZoomLimit())), aTile.getY() / (int) Math.pow(2, (aTile.getZ() - getProviderZoomLimit())))));
        }

        return mUrl.replace("{quadkey}", quadTree(aTile));
    }

    @Override
    public String getCacheKey() {
        return "Bing " + getStyle();
    }

    public String getBingMapKey() {
        return mBingMapKey;
    }

    private void setBingMapKey(String key) {
        mBingMapKey = key;
    }

    public String getStyle() {
        return mStyle;
    }

    public TileLayer setStyle(String style) {
        if (!style.equals(mStyle)) {
            synchronized (mStyle) {
                mStyle = style;
                mHasMetadata = false;
            }
        }
        mStyle = style;
        return this;
    }

    private void getMetadata() {
        try {
            synchronized (this) {
                if (mHasMetadata) {
                    return;
                }
                RetrieveMetadata rm = new RetrieveMetadata(mBingMapKey, mStyle) {
                    @Override
                    protected void onPostExecute(Boolean success) {
                        mHasMetadata = success == Boolean.TRUE;
                    }
                };
                rm.execute();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class RetrieveMetadata extends AsyncTask<Void, Void, Boolean> {
        String mKey;
        String mStyle;

        public RetrieveMetadata(String key, String style) {
            mKey = key;
            mStyle = style;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                synchronized (BingTileLayer.this) {
                    if (mHasMetadata) {
                        return null;
                    }
                    String url = String.format(BASE_URL_PATTERN, mStyle, mKey);
                    HttpURLConnection connection = NetworkUtils.getHttpURLConnection(new URL(url));
                    BufferedReader rd = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), Charset.forName("UTF-8")));

                    String content = readAll(rd);

                    mUrl = getInstanceFromJSON(content).replace("{culture}", "en");

                    return Boolean.TRUE;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return Boolean.FALSE;
            }
        }
    }

    private String getInstanceFromJSON(final String jsonContent) throws Exception {
        if (jsonContent == null) {
            throw new Exception("JSON to parse is null");
        }

        final JSONObject json = new JSONObject(jsonContent);
        final int statusCode = json.getInt("statusCode");
        if (statusCode != 200) {
            throw new Exception("Status code = " + statusCode);
        }

        if ("ValidCredentials".compareToIgnoreCase(json.getString("authenticationResultCode")) != 0) {
            throw new Exception("authentication result code = " + json.getString("authenticationResultCode"));
        }

        final JSONArray resultsSet = json.getJSONArray("resourceSets");
        if (resultsSet == null || resultsSet.length() < 1) {
            throw new Exception("No results set found in json response");
        }

        if (resultsSet.getJSONObject(0).getInt("estimatedTotal") <= 0) {
            throw new Exception("No resource found in json response");
        }

        final JSONObject resource = resultsSet.getJSONObject(0).getJSONArray("resources").getJSONObject(0);

        if (resource.has("ZoomMin")) {
            super.mMinimumZoomLevel = (float) resource.getInt("ZoomMin");
        }
        if (resource.has("ZoomMax")) {
            super.mMaximumZoomLevel = (float) resource.getInt("ZoomMax");
        }

        String imageBaseUrl = resource.getString("imageUrl");

        return imageBaseUrl.replace("{subdomain}", resource.getJSONArray("imageUrlSubdomains").getString(0));
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    private String quadTree(final MapTile tile) {
        final StringBuilder quadKey = new StringBuilder();
        for (int i = tile.getZ(); i > 0; i--) {
            int digit = 0;
            final int mask = 1 << (i - 1);
            if ((tile.getX() & mask) != 0) {
                digit += 1;
            }
            if ((tile.getY() & mask) != 0) {
                digit += 2;
            }
            quadKey.append(digit);
        }
        return quadKey.toString();
    }
}