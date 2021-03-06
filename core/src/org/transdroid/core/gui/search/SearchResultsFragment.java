/* 
 * Copyright 2010-2013 Eric Kok et al.
 * 
 * Transdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Transdroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Transdroid.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.transdroid.core.gui.search;

import java.util.ArrayList;
import java.util.List;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.ItemClick;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.transdroid.core.R;
import org.transdroid.core.app.search.SearchHelper;
import org.transdroid.core.app.search.SearchHelper.SearchSortOrder;
import org.transdroid.core.app.search.SearchResult;
import org.transdroid.core.app.search.SearchSite;
import org.transdroid.core.app.settings.SystemSettings_;
import org.transdroid.core.gui.TorrentsActivity_;
import org.transdroid.core.gui.navigation.NavigationHelper_;
import org.transdroid.core.gui.navigation.SelectionManagerMode;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.SherlockListView;
import com.actionbarsherlock.view.SherlockListView.MultiChoiceModeListenerCompat;

/**
 * Fragment that lists the items in a specific RSS feed
 * @author Eric Kok
 */
@EFragment(resName = "fragment_searchresults")
public class SearchResultsFragment extends SherlockFragment {

	@InstanceState
	protected ArrayList<SearchResult> results = null;
	@Bean
	protected SearchHelper searchHelper;

	// Views
	@ViewById(resName = "searchresults_list")
	protected SherlockListView resultsList;
	@Bean
	protected SearchResultsAdapter resultsAdapter;
	@ViewById
	protected TextView emptyText;
	@ViewById
	protected ProgressBar loadingProgress;

	@AfterViews
	protected void init() {

		// On large screens where this fragment is shown next to the sites list; we show a continues grey vertical line 
		// to separate the lists visually
		if (!NavigationHelper_.getInstance_(getActivity()).isSmallScreen()) {
			if (SystemSettings_.getInstance_(getActivity()).useDarkTheme()) {
				resultsList.setBackgroundResource(R.drawable.details_list_background_dark);
			} else {
				resultsList.setBackgroundResource(R.drawable.details_list_background_light);
			}
		}

		// Set up the list adapter, which allows multi-select
		resultsList.setAdapter(resultsAdapter);
		resultsList.setMultiChoiceModeListener(onItemsSelected);
		if (results != null)
			showResults();

	}

	public void startSearch(String query, SearchSite site) {
		loadingProgress.setVisibility(View.VISIBLE);
		resultsList.setVisibility(View.GONE);
		emptyText.setVisibility(View.GONE);
		performSearch(query, site);
	}

	@Background
	protected void performSearch(String query, SearchSite site) {
		results = searchHelper.search(query, site, SearchSortOrder.BySeeders);
		showResults();
	}

	@UiThread
	protected void showResults() {
		loadingProgress.setVisibility(View.GONE);
		if (results == null || results.size() == 0) {
			resultsList.setVisibility(View.GONE);
			emptyText.setVisibility(View.VISIBLE);
			return;
		}
		resultsAdapter.update(results);
		resultsList.setVisibility(View.VISIBLE);
		emptyText.setVisibility(View.GONE);
	}

	@ItemClick(resName = "searchresults_list")
	protected void onItemClicked(SearchResult item) {
		// Don't broadcast this intent; we can safely assume this is intended for Transdroid only
		Intent i = TorrentsActivity_.intent(getActivity()).get();
		i.setData(Uri.parse(item.getTorrentUrl()));
		i.putExtra("TORRENT_TITLE", item.getName());
		startActivity(i);
	}

	private MultiChoiceModeListenerCompat onItemsSelected = new MultiChoiceModeListenerCompat() {

		SelectionManagerMode selectionManagerMode;

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			// Show contextual action bar to add items in batch mode
			mode.getMenuInflater().inflate(R.menu.fragment_searchresults_cab, menu);
			selectionManagerMode = new SelectionManagerMode(resultsList, R.plurals.search_resutlsselected);
			selectionManagerMode.onCreateActionMode(mode, menu);
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return selectionManagerMode.onPrepareActionMode(mode, menu);
		}

		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

			// Get checked torrents
			List<SearchResult> checked = new ArrayList<SearchResult>();
			for (int i = 0; i < resultsList.getCheckedItemPositions().size(); i++) {
				if (resultsList.getCheckedItemPositions().valueAt(i))
					checked.add(resultsAdapter.getItem(resultsList.getCheckedItemPositions().keyAt(i)));
			}

			int itemId = item.getItemId();
			if (itemId == R.id.action_addall) {
				// Start an Intent that adds multiple items at once, by supplying the urls and titles as string array
				// extras and setting the Intent action to ADD_MULTIPLE
				Intent intent = new Intent("org.transdroid.ADD_MULTIPLE");
				String[] urls = new String[checked.size()];
				String[] titles = new String[checked.size()];
				for (int i = 0; i < checked.size(); i++) {
					urls[i] = checked.get(i).getTorrentUrl();
					titles[i] = checked.get(i).getName();
				}
				intent.putExtra("TORRENT_URLS", urls);
				intent.putExtra("TORRENT_TITLES", titles);
				startActivity(intent);
				mode.finish();
				return true;
			} else if (itemId == R.id.action_showdetails) {
				SearchResult first = checked.get(0);
				// Open the torrent's web page in the browser
				if (checked.size() > 1)
					Toast.makeText(getActivity(), getString(R.string.search_openingdetails, first.getName()),
							Toast.LENGTH_LONG).show();
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(first.getDetailsUrl())));
				return true;
			} else {
				return false;
			}
		}

		@Override
		public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
			selectionManagerMode.onItemCheckedStateChanged(mode, position, id, checked);
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			selectionManagerMode.onDestroyActionMode(mode);
		}

	};

}
