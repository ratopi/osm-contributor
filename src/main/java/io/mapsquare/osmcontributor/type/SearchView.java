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
import android.graphics.Rect;
import android.support.v4.view.MenuItemCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import io.mapsquare.osmcontributor.R;
import timber.log.Timber;

public class SearchView extends FrameLayout implements MenuItemCompat.OnActionExpandListener {

    private OnSearchListener listener;
    private EditText searchText;
    private View searchClear;
    private SearchWatcher searchWatcher;
    private boolean iconified;
    private boolean clearingFocus;

    public SearchView(Context context) {
        super(context);
        init(context);
    }

    public SearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SearchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        searchWatcher = new SearchWatcher();
        iconified = true;

        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.search_bar, this, true);

        searchText = (EditText) findViewById(R.id.search_text);
        searchText.addTextChangedListener(searchWatcher);
        searchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    setImeVisibility(false);
                    return true;
                }
                return false;
            }
        });

        searchClear = findViewById(R.id.search_delete);
        searchClear.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                searchText.setText(null);
            }
        });

//        setFocusable(true);
    }

    public void setOnSearchListener(OnSearchListener l) {
        listener = l;
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        if (!iconified) {
            Timber.d("Ignored onMenuItemActionExpand as view is already expanded");
            return false;
        }
        iconified = false;

        searchWatcher.ignoreEvent(true);
        searchText.setText(null);
        searchWatcher.ignoreEvent(false);

        if (listener != null) {
            listener.onSearchStateChanged(true);
        }

//        searchText.requestFocus();
        requestFocus();
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        if (iconified) {
            Timber.d("Ignored onMenuItemActionCollapse as view is already collapsed");
            return false;
        }
        iconified = true;

        if (listener != null) {
            listener.onSearchStateChanged(false);
        }

        clearFocus();
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (iconified) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY) {
            int preferredWidth = getContext().getResources().getDimensionPixelSize(R.dimen.toolbar_search_preferred_width);
            if (widthMode == MeasureSpec.AT_MOST) {
                width = Math.min(preferredWidth, width);
            } else if (widthMode == MeasureSpec.UNSPECIFIED) {
                width = preferredWidth;
            }
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        // Don't accept focus if in the middle of clearing focus
        if (clearingFocus) {
            return false;
        }
        // Check if SearchView is focusable.
        if (!isFocusable()) {
            return false;
        }
        // If it is not iconified, then give the focus to the text field
        if (!iconified) {
            setImeVisibility(true);
            return searchText.requestFocus(direction, previouslyFocusedRect);
        } else {
            return super.requestFocus(direction, previouslyFocusedRect);
        }
    }

    @Override
    public void clearFocus() {
        clearingFocus = true;
        super.clearFocus();
        searchText.clearFocus();
        setImeVisibility(false);
        clearingFocus = false;
    }

    void updateVisibility(CharSequence searchText) {
        searchClear.setVisibility(searchText.length() > 0 ? VISIBLE : GONE);
    }

    private void setImeVisibility(boolean visible) {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);

        if (visible) {
            // Open the keyboard focused on the edit text
            searchText.requestFocus();
            imm.showSoftInput(searchText, InputMethodManager.SHOW_IMPLICIT);
        } else {
//            ContextThemeWrapper
//            Activity activity = (Activity) getContext();
//            View view = activity.getCurrentFocus();
//            if (view != null) {
//                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
//            } else {
                imm.hideSoftInputFromWindow(getWindowToken(), 0);
//            }
        }
    }

    /**
     * Callback to be implemented by components that want to watch the search query entered by the user.
     */
    public interface OnSearchListener {
        /**
         * Called when the search query changed.
         *
         * @param search The query, may be null if the search was cancelled.
         */
        void onSearchChanged(String search);

        /**
         * Called when the search state changed.
         *
         * @param started true if the search is just stated, false if it is finished.
         */
        void onSearchStateChanged(boolean started);
    }

    /**
     * Custom text watcher for the search field.<br/>
     * Propagation of the event to the listener can be temporarily skipped.
     */
    private class SearchWatcher implements TextWatcher {

        private boolean ignoreEvent;

        public void ignoreEvent(boolean ignore) {
            ignoreEvent = ignore;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateVisibility(s);

            if (!ignoreEvent && listener != null) {
                listener.onSearchChanged(s.toString());
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}
