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
package io.mapsquare.osmcontributor.ui.presenters;

import android.graphics.Bitmap;
import android.util.Log;

import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.mapsquare.osmcontributor.OsmTemplateApplication;
import io.mapsquare.osmcontributor.model.entities.Note;
import io.mapsquare.osmcontributor.model.entities.Poi;
import io.mapsquare.osmcontributor.model.entities.PoiNodeRef;
import io.mapsquare.osmcontributor.model.entities.PoiType;
import io.mapsquare.osmcontributor.model.events.NotesLoadedEvent;
import io.mapsquare.osmcontributor.model.events.PleaseLoadNotesEvent;
import io.mapsquare.osmcontributor.model.events.PleaseLoadPoiTypes;
import io.mapsquare.osmcontributor.model.events.PleaseLoadPoisEvent;
import io.mapsquare.osmcontributor.model.events.PoiTypesLoaded;
import io.mapsquare.osmcontributor.model.events.PoisAndNotesDownloadedEvent;
import io.mapsquare.osmcontributor.model.events.PoisLoadedEvent;
import io.mapsquare.osmcontributor.model.events.RevertFinishedEvent;
import io.mapsquare.osmcontributor.rest.events.SyncDownloadPoisAndNotesEvent;
import io.mapsquare.osmcontributor.ui.events.map.PleaseChangeValuesDetailNoteFragmentEvent;
import io.mapsquare.osmcontributor.ui.events.map.PleaseChangeValuesDetailPoiFragmentEvent;
import io.mapsquare.osmcontributor.ui.events.map.PleaseInitializeDrawer;
import io.mapsquare.osmcontributor.ui.fragments.MapFragment;
import io.mapsquare.osmcontributor.ui.utils.MapMode;
import io.mapsquare.osmcontributor.ui.utils.views.map.marker.LocationMarker;
import io.mapsquare.osmcontributor.ui.utils.views.map.marker.LocationMarkerOptions;
import io.mapsquare.osmcontributor.utils.Box;
import io.mapsquare.osmcontributor.utils.ConfigManager;
import io.mapsquare.osmcontributor.utils.core.MapElement;
import timber.log.Timber;

public class MapFragmentPresenter {

    /*=========================================*/
    /*------------ATTRIBUTES-------------------*/
    /*=========================================*/
    private boolean forceRefreshPoi = false;

    private boolean forceRefreshNotes = false;

    private List<PoiType> poiTypes = null;

    private Float previousZoom;

    private LatLngBounds triggerReloadPoiLatLngBounds;

    private MapFragment mapFragment;

    @Inject
    EventBus eventBus;

    @Inject
    ConfigManager configManager;


    /*=========================================*/
    /*------------CONSTRUCTORS-----------------*/
    /*=========================================*/
    public MapFragmentPresenter(MapFragment mapFragment) {
        this.mapFragment = mapFragment;
        ((OsmTemplateApplication) mapFragment.getActivity().getApplication()).getOsmTemplateComponent().inject(this);
    }


    /*=========================================*/
    /*------------GETTER/SETTER----------------*/
    /*=========================================*/
    public int getNumberOfPoiTypes() {
        return poiTypes != null ? poiTypes.size() : 0;
    }

    public List<PoiType> getPoiTypes() {
        return poiTypes == null ? new ArrayList<PoiType>() : poiTypes;
    }

    public PoiType getPoiType(int id) {
        return poiTypes.get(id);
    }

    public void setForceRefreshPoi() {
        this.forceRefreshPoi = true;
    }

    public void setForceRefreshNotes() {
        this.forceRefreshNotes = true;
    }


    /*=========================================*/
    /*----------------EVENTS-------------------*/
    /*=========================================*/
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onPoiTypesLoaded(PoiTypesLoaded event) {
        Timber.d("Received event PoiTypesLoaded");
        poiTypes = event.getPoiTypes();
        setForceRefreshPoi();
        setForceRefreshNotes();
        loadPoisIfNeeded();
        eventBus.post(new PleaseInitializeDrawer(poiTypes, mapFragment.getPoiTypeHidden()));
        mapFragment.loadPoiTypeSpinner();
        mapFragment.loadPoiTypeFloatingBtn();
        eventBus.removeStickyEvent(event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRevertFinishedEvent(RevertFinishedEvent event) {
        Timber.d("Received event RevertFinishedEvent");
        Object object = event.getRelatedObject();
        if (object instanceof Poi) {
            Poi poi = (Poi) object;
            LocationMarkerOptions markerOptions = mapFragment.getMarkerOptions(event.getMarkerType(), poi.getId());
            if (markerOptions == null) {
                markerOptions = new LocationMarkerOptions<Poi>().position(poi.getPosition()).relatedObject(poi);
                mapFragment.addMarker(markerOptions);
            } else {
                markerOptions.position(poi.getPosition()).relatedObject(poi);
            }
            setIcon(markerOptions, poi, false);
        } else {
            PoiNodeRef poiNodeRef = (PoiNodeRef) object;
            LocationMarkerOptions markerOptions = mapFragment.getMarkerOptions(event.getMarkerType(), poiNodeRef.getId()).position(poiNodeRef.getPosition()).relatedObject(poiNodeRef);
            mapFragment.switchMode(MapMode.WAY_EDITION);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPoisAndNotesDownloadedEvent(PoisAndNotesDownloadedEvent event) {
        mapFragment.showProgressBar(false);
        forceRefreshPoi = true;
        loadPoisIfNeeded();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPoisLoadedEvent(PoisLoadedEvent event) {
        List<Poi> pois = event.getPois();
        Timber.d("Received event PoisLoaded  : " + pois.size());
        forceRefreshPoi = false;
        List<MapElement> mapElements = new ArrayList<>(pois.size());
        for (Poi poi : pois) {
            mapElements.add(poi);
        }
        onLoaded(mapElements, LocationMarker.MarkerType.POI);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNotesLoadedEvent(NotesLoadedEvent event) {
        List<Note> notes = event.getNotes();
        List<MapElement> mapElements = new ArrayList<>(notes.size());
        for (Note note : notes) {
            mapElements.add(note);
        }
        Timber.d("Showing notes : " + notes.size());
        forceRefreshNotes = false;
        onLoaded(mapElements, LocationMarker.MarkerType.NOTE);
    }

    public void register() {
        eventBus.register(this);
    }

    public void unregister() {
        eventBus.unregister(this);
    }


    /*=========================================*/
    /*------------CODE-------------------------*/
    /*=========================================*/
    private static final String TAG = "MapFragmentPresenter";
    public void loadPoisIfNeeded() {
        if (poiTypes == null) {
            Timber.v("PleaseLoadPoiTypes");
            eventBus.post(new PleaseLoadPoiTypes());
        }

        LatLngBounds viewLatLngBounds = mapFragment.getViewLatLngBounds();
        if (viewLatLngBounds != null) {
            if (mapFragment.getZoomLevel() > 15) {
                if (shouldReload(viewLatLngBounds)) {
                    Timber.d("Reloading pois");
                    Log.i(TAG, "loadPoisIfNeeded: shouldReload");
                    previousZoom = mapFragment.getZoomLevel();
                    triggerReloadPoiLatLngBounds = enlarge(viewLatLngBounds, 1.5);
                    eventBus.post(new PleaseLoadPoisEvent(enlarge(viewLatLngBounds, 1.75)));
                    eventBus.post(new PleaseLoadNotesEvent(enlarge(viewLatLngBounds, 1.75)));
                }
            } else {
                if (mapFragment.hasMarkers()) {
                    Timber.d("area displayed is too big, hiding pois");
                    previousZoom = mapFragment.getZoomLevel();
                    mapFragment.removeAllMarkers();
                }
            }
        }
    }

    public void downloadAreaPoisAndNotes() {
        mapFragment.showProgressBar(true);
        eventBus.post(new SyncDownloadPoisAndNotesEvent(Box.convertFromLatLngBounds(enlarge(mapFragment.getViewLatLngBounds(), 1.75))));
    }


    /*=========================================*/
    /*------------PRIVATE CODE-----------------*/
    /*=========================================*/
    private boolean shouldReload(LatLngBounds viewLatLngBounds) {
        if (forceRefreshPoi || forceRefreshNotes) {
            forceRefreshPoi = false;
            forceRefreshNotes = false;
            return true;
        }
        return previousZoom != null && previousZoom < 15 ||
                triggerReloadPoiLatLngBounds == null || !triggerReloadPoiLatLngBounds.union(viewLatLngBounds).equals(triggerReloadPoiLatLngBounds);
    }

    private LatLngBounds enlarge(LatLngBounds viewLatLngBounds, double factor) {
        double n = viewLatLngBounds.getLatNorth();
        double e = viewLatLngBounds.getLonEast();
        double s = viewLatLngBounds.getLatSouth();
        double w = viewLatLngBounds.getLonWest();
        double f = (factor - 1) / 2;
        return new LatLngBounds.Builder()
                .include(new LatLng(n + f * (n - s), e + f * (e - w)))
                .include(new LatLng(s - f * (n - s), w - f * (e - w)))
                .build();
    }

    private void setIcon(LocationMarkerOptions markerOptions, Object relatedObject, boolean selected) {
        Bitmap bitmap;
        if (relatedObject instanceof Poi) {
            Poi poi = (Poi) relatedObject;
            bitmap = mapFragment.getBitmapHandler().getMarkerBitmap(poi.getType(), Poi.computeState(selected, false, poi.getUpdated()));
        } else {
            Note note = (Note) relatedObject;
            bitmap = mapFragment.getBitmapHandler().getNoteBitmap(Note.computeState(note, selected, note.getUpdated()));
        }

        if (bitmap != null) {
            markerOptions.icon(IconFactory.getInstance(mapFragment.getActivity()).fromBitmap(bitmap));
        }
    }

    private void onLoaded(List<MapElement> mapElements, LocationMarker.MarkerType markerType) {
        LocationMarker markerSelected = mapFragment.getMarkerSelected();
        List<Long> ids = new ArrayList<>(mapElements.size());

        Log.i(TAG, "onLoaded: " + mapElements.size() + " " + markerType.toString());

        for (MapElement mapElement : mapElements) {
            ids.add(mapElement.getId());
            LocationMarkerOptions markerOptions = mapFragment.getMarkerOptions(markerType, mapElement.getId());
            boolean selected = false;

            if (markerOptions == null) {
                markerOptions = new LocationMarkerOptions<>().relatedObject(mapElement).position(mapElement.getPosition());
                if (mapFragment.getSelectedMarkerType().equals(markerType) && mapElement.getId().equals(mapFragment.getMarkerSelectedId())) {
                    selected = true;
                    mapFragment.setMarkerSelected(markerOptions.getMarker());
                } else if (mapFragment.getSelectedMarkerType().equals(LocationMarker.MarkerType.POI) && markerSelected != null && mapElement.getId().equals(((Poi) markerSelected.getRelatedObject()).getId())) {
                    selected = true;
                }

                //the poi in edition should be hidden
                if (!(markerSelected != null && mapFragment.getMapMode() == MapMode.POI_POSITION_EDITION && markerSelected.equals(markerOptions.getMarker())) && (mapElement instanceof Poi && !((Poi) mapElement).getToDelete())) {
                    setIcon(markerOptions, mapElement, selected);
                    mapFragment.addMarker(markerOptions);
                }

                if (markerType == LocationMarker.MarkerType.NOTE) {
                    if (mapFragment.getSelectedMarkerType().equals(LocationMarker.MarkerType.NOTE) && mapElement.getId().equals(mapFragment.getMarkerSelectedId())) {
                        mapFragment.setMarkerSelected(markerOptions.getMarker());
                    }
                    setIcon(markerOptions, mapElement, selected);
                    mapFragment.addNote(markerOptions);
                }
            } else {
                markerOptions.relatedObject(mapElement).position(mapElement.getPosition());
                if (markerType == LocationMarker.MarkerType.POI) {
                    if (mapFragment.getSelectedMarkerType().equals(LocationMarker.MarkerType.POI)
                            && (mapElement.getId().equals(mapFragment.getMarkerSelectedId())
                            || markerSelected != null
                            && mapElement.getId().equals(((Poi) markerSelected.getRelatedObject()).getId()))) {
                        selected = true;
                    }
                } else {
                    if (mapFragment.getSelectedMarkerType().equals(LocationMarker.MarkerType.NOTE)
                            && markerSelected != null
                            && mapElement.getId().equals(((Note) markerSelected.getRelatedObject()).getId())) {
                        selected = true;
                    }
                    setIcon(markerOptions, mapElement, selected);
                }

                //update the detail banner data
                if (selected) {
                    if (mapFragment.getMapMode() == MapMode.DETAIL_NOTE) {
                        eventBus.post(new PleaseChangeValuesDetailNoteFragmentEvent((Note) mapElement));
                    } else {
                        Poi poi = (Poi) mapElement;
                        eventBus.post(new PleaseChangeValuesDetailPoiFragmentEvent(poi.getType().getName(), poi.getName(), poi.getWay()));
                    }
                }
            }
        }

        mapFragment.removeNoteMarkersNotIn(ids);

        if ((mapFragment.getMapMode() == MapMode.DEFAULT || mapFragment.getMapMode() == MapMode.POI_CREATION)) {
            mapFragment.reselectMarker();
        }

        if (mapFragment.getSelectedMarkerType().equals(markerType) && markerSelected == null) {
            mapFragment.setMarkerSelectedId(-1L);
        }
    }
}