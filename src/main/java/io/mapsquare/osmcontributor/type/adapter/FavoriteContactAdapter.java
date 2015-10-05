/**
 * Copyright (C) 2015 eBusiness Information
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
package io.mapsquare.osmcontributor.type.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.mapsquare.osmcontributor.R;
import io.mapsquare.osmcontributor.core.model.PoiType;
import io.mapsquare.osmcontributor.map.BitmapHandler;
import io.mapsquare.osmcontributor.type.dto.SuggestionsData;
import timber.log.Timber;

public class FavoriteContactAdapter extends RecyclerView.Adapter<FavoriteContactAdapter.Holder> {

    private static final int SECTION_HEADER = 0;
    private static final int SECTION_LOCAL = 1;
    private static final int SECTION_SUGGESTION = 2;

    private LayoutInflater layoutInflater;
    private BitmapHandler bitmapHandler;

    private Collection<PoiType> types;
    private Collection<SuggestionsData> suggestions;

    private List<TypeWrapper> visibleItems;
    private String filterQuery;

    public FavoriteContactAdapter(Context context, BitmapHandler bitmapHandler) {
        layoutInflater = LayoutInflater.from(context);
        this.bitmapHandler = bitmapHandler;

        types = new ArrayList<>();
        suggestions = new ArrayList<>();
        visibleItems = new ArrayList<>();

        setHasStableIds(true);
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layout = viewType == SECTION_SUGGESTION ? R.layout.single_poi_type_suggestion : R.layout.single_poi_type;
        View view = layoutInflater.inflate(layout, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        holder.bind(visibleItems.get(position), bitmapHandler);
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    @Override
    public long getItemId(int position) {
        return visibleItems.get(position).getId();
    }

    @Override
    public int getItemViewType(int position) {
        return isSuggestion(position) ? SECTION_SUGGESTION : SECTION_LOCAL;
    }

    private boolean isSuggestion(int position) {
        return filterQuery != null && visibleItems.get(position).isSuggestion();
    }

    public void setTypes(Collection<PoiType> types) {
        this.types = types;
        doFiltering();
    }

    public void setSuggestions(Collection<SuggestionsData> suggestions) {
        this.suggestions = suggestions;
        doFiltering();
    }

    public void setSearchFilter(String query) {
        if (query == null || query.trim().isEmpty()) {
            filterQuery = null;
        } else {
            filterQuery = query.toLowerCase();
        }
        suggestions.clear();
        doFiltering();
    }

    public PoiType getItemById(Long id) {
        for (PoiType type : types) {
            if (id.equals(type.getId())) {
                return type;
            }
        }
        return null;
    }

    public void addItem(PoiType item) {
        types.add(item);
        doFiltering();
    }

    private void doFiltering() {
        List<TypeWrapper> list = new ArrayList<>();

        if (filterQuery == null) {
            for (PoiType type : types) {
                list.add(new TypeWrapper(type));
            }
        } else {
            for (PoiType type : types) {
                String name = type.getName();
                if (name != null && name.toLowerCase().contains(filterQuery)) {
                    list.add(new TypeWrapper(type));
                }
            }
            for (SuggestionsData suggestion : suggestions) {
                list.add(new TypeWrapper(suggestion));
            }
        }

        Timber.i("Filtering '%s', %d items, %d items filtered", filterQuery, types.size(), list.size());

        Collections.sort(list);
        visibleItems = list;
        notifyDataSetChanged();
    }

    static class Holder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public final ImageView icon;
        private final TextView text;
        private final TextView details;

        public Holder(View itemView) {
            super(itemView);
            icon = (ImageView) itemView.findViewById(R.id.poi_type_icon);
            text = (TextView) itemView.findViewById(R.id.poi_type_name);
            details = (TextView) itemView.findViewById(R.id.poi_type_details);
        }

        public void bind(TypeWrapper item, BitmapHandler bitmapHandler) {
            text.setText(item.getName());
            icon.setImageDrawable(bitmapHandler.getDrawable(item.getIcon()));

            if (!item.isSuggestion()) {
                details.setText(itemView.getContext().getString(R.string.tag_number, item.getTagCount()));
            }
        }

        @Override
        public void onClick(View v) {
        }
    }

    private static class TypeWrapper implements Comparable<TypeWrapper> {

        private final long id;
        private final String name;
        private final String icon;
        private final int tagCount;
        private final boolean suggestion;

        public TypeWrapper(PoiType poiType) {
            id = poiType.getId();
            name = poiType.getName();
            icon = poiType.getIcon();
            tagCount = poiType.getTags().size();
            suggestion = false;
        }

        public TypeWrapper(SuggestionsData suggestionsData) {
            id = RecyclerView.NO_ID;
            name = suggestionsData.getKey();
            icon = name;
            tagCount = 0;
            suggestion = true;
        }

        public boolean isSuggestion() {
            return suggestion;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getIcon() {
            return icon;
        }

        public int getTagCount() {
            return tagCount;
        }

        @Override
        public int compareTo(@NonNull TypeWrapper another) {
            int result = name.compareTo(another.name);
            if (result == 0) {
                return suggestion == another.suggestion ? 0 : (suggestion ? 1 : -1);
            }
            return result;
        }
    }
}
