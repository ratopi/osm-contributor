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
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.mapsquare.osmcontributor.R;
import io.mapsquare.osmcontributor.core.model.PoiType;
import io.mapsquare.osmcontributor.map.BitmapHandler;
import io.mapsquare.osmcontributor.type.dto.SuggestionsData;
import timber.log.Timber;

public class FavoriteContactAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private enum Section {
        TITLE, LOCAL_TYPE, SUGGESTION, EMPTY, LOADING
    }

    private LayoutInflater layoutInflater;
    private BitmapHandler bitmapHandler;

    private List<PoiType> types;
    private List<PoiType> visibleTypes;
    private String filterQuery;

    private List<SuggestionsData> suggestions;
    private List<SuggestionsData> visibleSuggestions;
    private boolean queryingSuggestions;

    public FavoriteContactAdapter(Context context, BitmapHandler bitmapHandler) {
        layoutInflater = LayoutInflater.from(context);
        this.bitmapHandler = bitmapHandler;

        visibleTypes = new ArrayList<>();
        visibleSuggestions = new ArrayList<>();

        setHasStableIds(true);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (Section.values()[viewType]) {
            case TITLE:
                return TitleHolder.create(layoutInflater, parent);
            case LOCAL_TYPE:
                return LocalTypeHolder.create(layoutInflater, parent);
            case SUGGESTION:
                return SuggestionHolder.create(layoutInflater, parent);
            case EMPTY:
                return new SimpleHolder(layoutInflater.inflate(R.layout.single_section_empty, parent, false));
            case LOADING:
                return new SimpleHolder(layoutInflater.inflate(R.layout.single_section_loading, parent, false));
            default:
                return null; // Shall not happen ('-')
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (getItemType(position)) {
            case TITLE:
//                int title = position == 0 ? R.string.location_not_found : R.string.edit_poi_type;
                String title = position == 0 ? "Local types" : "Suggestions";
                ((TitleHolder) holder).title.setText(title);
                break;
            case LOCAL_TYPE:
                ((LocalTypeHolder) holder).bind(visibleTypes.get(position - 1), bitmapHandler);
                break;
            case SUGGESTION:
                int visibleTypeCount = visibleTypes.size();
                if (visibleTypeCount == 0) {
                    visibleTypeCount = 1; // Add offset for "no item" text
                }
                ((SuggestionHolder) holder).bind(visibleSuggestions.get(position - visibleTypeCount - 2), bitmapHandler);
                break;
        }
    }

    @Override
    public int getItemCount() {
        int count;

        if (visibleTypes.isEmpty()) {
            // "no item" text
            count = 1;
        } else {
            count = visibleTypes.size();
        }

        // Add "local" and "suggestions" titles
        count += 2;

        int suggestionCount = visibleSuggestions.size();
        if (queryingSuggestions || filterQuery == null || suggestionCount == 0) {
            // Add "no item" or "loading" text
            count++;
        } else {
            count += suggestionCount;
        }

        return count;
    }

    @Override
    public long getItemId(int position) {
        if (getItemType(position) == Section.LOCAL_TYPE && !visibleTypes.isEmpty()) {
            return visibleTypes.get(position - 1).getId();
        }
        return RecyclerView.NO_ID;
    }

    @Override
    public int getItemViewType(int position) {
        return getItemType(position).ordinal();
    }

    private Section getItemType(int position) {
        int visibleTypeCount = visibleTypes.size();
        boolean noType = visibleTypeCount == 0;

        if (noType) {
            visibleTypeCount = 1; // Add offset for "no item" text
        }

        if (position == 0 || position == visibleTypeCount + 1) {
            // Title
            return Section.TITLE;
        } else if (position < visibleTypeCount + 1) {
            // Local type
            if (noType) {
                return Section.EMPTY;
            } else {
                return Section.LOCAL_TYPE;
            }
        } else {
            // Suggestion
            if (queryingSuggestions) {
                return Section.LOADING;
            } else if (filterQuery == null || visibleSuggestions.isEmpty()) {
                return Section.EMPTY;
            } else {
                return Section.SUGGESTION;
            }
        }
    }

    public void setTypes(List<PoiType> types) {
        this.types = types;

        List<PoiType> list = new ArrayList<>(types.size());
        Collections.sort(types);

        if (filterQuery == null) {
            list.addAll(types);
        } else {
            for (PoiType type : types) {
                String name = type.getName();
                if (name != null && name.toLowerCase().contains(filterQuery)) {
                    list.add(type);
                }
            }
            // TODO update suggestions
        }

        visibleTypes = list;
        notifyDataSetChanged();
    }

    public void setSuggestions(List<SuggestionsData> suggestions) {
        this.suggestions = suggestions;
        queryingSuggestions = false;

        if (filterQuery != null) {
            List<SuggestionsData> list = new ArrayList<>(suggestions.size());
            Collections.sort(suggestions);

            for (SuggestionsData suggestion : suggestions) {
                list.add(suggestion);
            }

            visibleSuggestions = list;
//            notifyItemRangeChanged(1, 1);
            notifyDataSetChanged();
        }
    }

    public void setSearchFilter(String query) {
        if (query == null || query.trim().isEmpty()) {
            filterQuery = null;
        } else {
            filterQuery = query.toLowerCase();
            queryingSuggestions = true;
        }
        setTypes(types);
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

        String name = item.getName();
        if (filterQuery == null || (name != null && name.toLowerCase().contains(filterQuery))) {
            visibleTypes.add(item);
            Collections.sort(visibleTypes);

            int insertedPosition = visibleTypes.indexOf(item);
            if (insertedPosition == -1) {
                throw new IllegalStateException("Added item not found");
            }
            insertedPosition++; // Add title offset

            int removedPosition = -1;
            if (name != null) {
                int position = visibleTypes.size();
                if (position == 0) {
                    position = 1;
                }
                position += 2; // Add 2 titles offset

                // Remove this item's suggestion, if it exist
                Iterator<SuggestionsData> iterator = visibleSuggestions.iterator();
                while (iterator.hasNext()) {
                    SuggestionsData suggestion = iterator.next();
                    if (name.equals(suggestion.getKey())) {
                        iterator.remove();
                        removedPosition = position;
                        break;
                    }
                    position++;
                }
            }

            if (removedPosition != -1) {
                notifyItemMoved(removedPosition, insertedPosition);
            } else {
                notifyItemInserted(insertedPosition);
            }
        }
    }

    private void doFiltering() {
        List<PoiType> list;

        if (filterQuery == null) {
            list = types;
        } else {
            list = new ArrayList<>();
            for (PoiType type : types) {
                String name = type.getName();
                if (name != null && name.toLowerCase().contains(filterQuery)) {
                    list.add(type);
                }
            }

            visibleSuggestions = new ArrayList<>();
            visibleSuggestions.clear();
            visibleSuggestions.addAll(suggestions);
            for (SuggestionsData suggestion : suggestions) {
            }
        }

        Timber.i("Filtering '%s', %d items, %d items filtered", filterQuery, types.size(), list.size());

        Collections.sort(list);
        visibleTypes = list;
        notifyDataSetChanged();
    }

    /**
     * Describe a section header layout.
     */
    private static class TitleHolder extends RecyclerView.ViewHolder {

        public final TextView title;

        public TitleHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.title);

            // Used by the item decorator
            itemView.setEnabled(false);
        }

        public static TitleHolder create(LayoutInflater layoutInflater, ViewGroup parent) {
            View root = layoutInflater.inflate(R.layout.single_section_title, parent, false);
            return new TitleHolder(root);
        }
    }

    /**
     * Describe a local POI type layout.
     */
    static class LocalTypeHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public final ImageView icon;
        private final TextView text;
        private final TextView details;

        public LocalTypeHolder(View itemView) {
            super(itemView);
            icon = (ImageView) itemView.findViewById(R.id.poi_type_icon);
            text = (TextView) itemView.findViewById(R.id.poi_type_name);
            details = (TextView) itemView.findViewById(R.id.poi_type_details);
            itemView.setOnClickListener(this);
        }

        public void bind(PoiType item, BitmapHandler bitmapHandler) {
            text.setText(item.getName());
            icon.setImageDrawable(bitmapHandler.getDrawable(item.getIcon()));
            details.setText(itemView.getContext().getString(R.string.tag_number, item.getTags().size()));
        }

        @Override
        public void onClick(View v) {
            Toast.makeText(v.getContext(), "view tags", Toast.LENGTH_SHORT).show();
        }

        public static LocalTypeHolder create(LayoutInflater layoutInflater, ViewGroup parent) {
            View root = layoutInflater.inflate(R.layout.single_poi_type, parent, false);
            return new LocalTypeHolder(root);
        }
    }

    /**
     * Describe a type suggestion layout.
     */
    static class SuggestionHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final ImageView icon;
        private final TextView text;
        private final View download;

        public SuggestionHolder(View itemView) {
            super(itemView);
            icon = (ImageView) itemView.findViewById(R.id.poi_type_icon);
            text = (TextView) itemView.findViewById(R.id.poi_type_name);
            download = itemView.findViewById(R.id.download);
            download.setOnClickListener(this);
        }

        public void bind(SuggestionsData item, BitmapHandler bitmapHandler) {
            String name = item.getKey();
            text.setText(name);
            icon.setImageDrawable(bitmapHandler.getDrawable(name));
        }

        @Override
        public void onClick(View v) {
            Toast.makeText(v.getContext(), "download", Toast.LENGTH_SHORT).show();
        }

        public static SuggestionHolder create(LayoutInflater layoutInflater, ViewGroup parent) {
            View root = layoutInflater.inflate(R.layout.single_poi_type_suggestion, parent, false);
            return new SuggestionHolder(root);
        }
    }

    /**
     * Describe a simple section layout.
     */
    private static class SimpleHolder extends RecyclerView.ViewHolder {

        public SimpleHolder(View itemView) {
            super(itemView);
        }
    }
}
