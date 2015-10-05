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
package io.mapsquare.osmcontributor.type;

import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import de.greenrobot.event.EventBus;
import io.mapsquare.osmcontributor.OsmTemplateApplication;
import io.mapsquare.osmcontributor.R;
import io.mapsquare.osmcontributor.core.events.PleaseLoadPoiTypes;
import io.mapsquare.osmcontributor.core.model.PoiType;
import io.mapsquare.osmcontributor.core.model.PoiTypeTag;
import io.mapsquare.osmcontributor.map.BitmapHandler;
import io.mapsquare.osmcontributor.type.adapter.FavoriteContactAdapter;
import io.mapsquare.osmcontributor.type.adapter.PoiTypeTagAdapter;
import io.mapsquare.osmcontributor.type.dto.SuggestionsData;
import timber.log.Timber;

public class TypeListActivity extends AppCompatActivity {

    @InjectView(R.id.toolbar)
    Toolbar toolbar;

    @InjectView(R.id.progress_content_switcher)
    ViewSwitcher viewSwitcher;

    @InjectView(R.id.content)
    RelativeLayout content;

    @InjectView(R.id.progressbar)
    ProgressBar progressBar;

    @InjectView(R.id.list_switcher)
    HorizontalViewSwitcher listSwitcher;

    @InjectView(R.id.list_poi_types)
    RecyclerView recyclerTypes;

    @InjectView(R.id.list_poi_tags)
    RecyclerView recyclerTags;

    @Inject
    EventBus eventBus;

    @Inject
    BitmapHandler bitmapHandler;

    private TypeListActivityPresenter presenter;

    private boolean showingTypes = true;

    private FavoriteContactAdapter typesAdapter;

    private DragSwipeRecyclerHelper tagsHelper;
    private PoiTypeTagAdapter tagsAdapter;

    private MenuItem mSearchAction;
    private boolean isSearchOpened = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_type_list);
        ButterKnife.inject(this);
        ((OsmTemplateApplication) getApplication()).getOsmTemplateComponent().inject(this);

        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        presenter = new TypeListActivityPresenter(this, savedInstanceState);

        typesAdapter = new FavoriteContactAdapter(this, bitmapHandler);
        recyclerTypes.setLayoutManager(new LinearLayoutManager(this));
        recyclerTypes.addItemDecoration(new ItemDividerDecoration(this));
        recyclerTypes.setAdapter(typesAdapter);

        tagsAdapter = new PoiTypeTagAdapter(presenter.getListTagsCallback());
        tagsHelper = new DragSwipeRecyclerHelper(recyclerTags, tagsAdapter);

        listSwitcher.prepareViews(showingTypes);
    }

    @Override
    protected void onResume() {
        super.onResume();
        presenter.onResume();
    }

    @Override
    protected void onPause() {
        tagsHelper.onPause();
        presenter.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        tagsHelper.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        presenter.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.action_search) {
            handleMenuSearch();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        if (isSearchOpened) {
            handleMenuSearch();
        } else if (!presenter.onBackPressed()) {
            eventBus.post(new PleaseLoadPoiTypes());
            super.onBackPressed();
        }
    }

    @OnClick(R.id.fab_add)
    public void fabClick() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (showingTypes) {
            EditPoiTypeDialogFragment.display(fragmentManager);
        } else {
            EditPoiTagDialogFragment.display(fragmentManager);
        }
    }

    void showTypes(List<PoiType> poiTypes) {
        showingTypes = true;

        typesAdapter.setTypes(poiTypes);
        changeTitle(R.string.manage_poi_types, null);
        showContent();
        listSwitcher.showView(recyclerTypes);
        Timber.d("just loaded %s types ", poiTypes.size());
    }

    void showTags(Collection<PoiTypeTag> poiTypeTags, PoiType poiType) {
        showingTypes = false;

        tagsAdapter.clearAndAddAll(poiTypeTags);
        changeTitle(R.string.manage_poi_tags, poiType.getName());
        showContent();
        listSwitcher.showView(recyclerTags);
        Timber.d("just loaded %s tags ", poiTypeTags.size());
    }

    public void showContent() {
        if (viewSwitcher.getNextView() == content) {
            viewSwitcher.showNext();
        }
    }

    public void showProgressBar() {
        if (viewSwitcher.getNextView() == progressBar) {
            viewSwitcher.showNext();
        }
    }

    public Snackbar createSnackbar(CharSequence deletedItem) {
        CharSequence message = getString(R.string.item_removed, deletedItem);
        return Snackbar.make(content, message, Snackbar.LENGTH_LONG);
    }

    public void undoPoiTypeRemoval() {
//        typesAdapter.undoLastRemoval();
    }

    public void undoPoiTypeTagRemoval() {
        tagsAdapter.undoLastRemoval();
    }

    public void notifyPoiTypeDefinitivelyRemoved(PoiType poiType) {
//        typesAdapter.notifyLastRemovalDone(poiType);
    }

    public void notifyPoiTagDefinitivelyRemoved(PoiTypeTag poiTypeTag) {
        tagsAdapter.notifyLastRemovalDone(poiTypeTag);
//        typesAdapter.notifyTagRemoved(poiTypeTag);
    }

    public PoiType getPoiTypeById(Long id) {
        return typesAdapter.getItemById(id);
    }

    public void addNewPoiType(PoiType item) {
        typesAdapter.addItem(item);
    }

    public void addNewPoiTag(PoiTypeTag item) {
        tagsAdapter.addItem(item);
    }

    public void setTypeSuggestions(List<SuggestionsData> suggestions) {
        typesAdapter.setSuggestions(suggestions);
    }

    private void changeTitle(int title, CharSequence subtitle) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
            actionBar.setSubtitle(subtitle);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_type_list, menu);
        mSearchAction = menu.findItem(R.id.action_search);
        return true;
    }

    protected void handleMenuSearch() {
        ActionBar action = getSupportActionBar();
        if (action == null) {
            return;
        }

        boolean wasSearching = isSearchOpened;
        int iconRes;

        action.setDisplayShowCustomEnabled(!wasSearching);
        action.setDisplayShowTitleEnabled(wasSearching);

        if (wasSearching) {
            hideIME();
            typesAdapter.setSearchFilter(null);

            iconRes = R.drawable.abc_ic_search_api_mtrl_alpha;
        } else {
            action.setCustomView(R.layout.search_bar);

            EditText search = (EditText) action.getCustomView().findViewById(R.id.edtSearch);
            search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        hideIME();
                        return true;
                    }
                    return false;
                }
            });
            search.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String search = s.toString();
                    presenter.queryTypeSuggestions(search);
                    typesAdapter.setSearchFilter(search);
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
            search.requestFocus();

            // Open the keyboard focused on the edit text
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);

            iconRes = R.drawable.abc_ic_clear_mtrl_alpha;
        }

        // Update the search menu icon in the action bar
        mSearchAction.setIcon(getResources().getDrawable(iconRes));
        isSearchOpened = !wasSearching;
    }

    private void hideIME() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}

