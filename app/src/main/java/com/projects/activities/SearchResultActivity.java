package com.projects.activities;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.Spanned;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.TextView;

import com.application.StoreFinderApplication;
import com.config.UIConfig;
import com.db.DbHelper;
import com.db.Queries;
import com.libraries.adapters.MGListAdapter;
import com.libraries.adapters.MGListAdapter.OnMGListAdapterAdapterListener;
import com.libraries.adapters.MGRecyclerAdapter;
import com.libraries.imageview.MGImageView;
import com.libraries.utilities.MGUtilities;
import com.models.Category;
import com.models.Favorite;
import com.models.Photo;
import com.models.Store;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.projects.storefinder.MainActivity;
import com.projects.storefinder.R;

import java.util.ArrayList;

public class SearchResultActivity extends AppCompatActivity {
	
	private ArrayList<Store> arrayData;
	private Queries q;
	SwipeRefreshLayout swipeRefresh;
	RecyclerView mRecyclerView;
	RecyclerView.LayoutManager mLayoutManager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		setContentView(R.layout.fragment_list_swipe);
		setTitle(R.string.search_results);

		q = StoreFinderApplication.getQueriesInstance(this);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		arrayData = (ArrayList<Store>)this.getIntent().getSerializableExtra("searchResults");

		mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView);
		mRecyclerView.setHasFixedSize(true);

		mLayoutManager = new LinearLayoutManager(this);
		mRecyclerView.setLayoutManager(mLayoutManager);

		swipeRefresh = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh);
		swipeRefresh.setClickable(false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			swipeRefresh.setProgressViewOffset(false, 0,100);
		}

		swipeRefresh.setColorSchemeResources(
				android.R.color.holo_blue_bright,
				android.R.color.holo_green_light,
				android.R.color.holo_orange_light,
				android.R.color.holo_red_light);

		showRefresh(false);
		showList();
		
		if(arrayData != null && arrayData.size() == 0) {
			MGUtilities.showNotifier(this, MainActivity.offsetY);
			return;
		}
	}

	public void showRefresh(boolean show) {
		swipeRefresh.setRefreshing(show);
		swipeRefresh.setEnabled(show);
	}
	
	private void showList() {
		MGRecyclerAdapter adapter = new MGRecyclerAdapter(arrayData.size(), R.layout.store_search_entry);
		adapter.setOnMGRecyclerAdapterListener(new MGRecyclerAdapter.OnMGRecyclerAdapterListener() {

			@Override
			public void onMGRecyclerAdapterCreated(MGRecyclerAdapter adapter, MGRecyclerAdapter.ViewHolder v, int position) {
				final Store store = arrayData.get(position);
				Photo p = q.getPhotoByStoreId(store.getStore_id());
				MGImageView imgViewPhoto = (MGImageView) v.view.findViewById(R.id.imgViewPhoto);
				imgViewPhoto.setCornerRadius(0.0f);
				imgViewPhoto.setBorderWidth(UIConfig.BORDER_WIDTH);
				imgViewPhoto.setBorderColor(getResources().getColor(UIConfig.THEME_BLACK_COLOR));
				imgViewPhoto.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View arg0) {
						// TODO Auto-generated method stub
						Intent i = new Intent(SearchResultActivity.this, DetailActivity.class);
						i.putExtra("store", store);
						SearchResultActivity.this.startActivity(i);
					}
				});

				if(p != null) {
					StoreFinderApplication.getImageLoaderInstance(SearchResultActivity.this)
							.displayImage(
									p.getPhoto_url(),
									imgViewPhoto,
									StoreFinderApplication.getDisplayImageOptionsInstance());
				}
				else {
					imgViewPhoto.setImageResource(UIConfig.SLIDER_PLACEHOLDER);
				}

				Spanned name = Html.fromHtml(store.getStore_name());
				Spanned address = Html.fromHtml(store.getStore_address());

				TextView tvTitle = (TextView) v.view.findViewById(R.id.tvTitle);
				tvTitle.setText(name);

				TextView tvSubtitle = (TextView) v.view.findViewById(R.id.tvSubtitle);
				tvSubtitle.setText(address);

				// SETTING VALUES
				float rating = 0;
				if(store.getRating_total() > 0 && store.getRating_count() > 0)
					rating = store.getRating_total() / store.getRating_count();

				String strRating = String.format("%.2f %s %d %s",
						rating,
						SearchResultActivity.this.getResources().getString(R.string.average_based_on),
						store.getRating_count(),
						SearchResultActivity.this.getResources().getString(R.string.rating));

				RatingBar ratingBar = (RatingBar) v.view.findViewById(R.id.ratingBar);
				ratingBar.setRating(rating);

				TextView tvRatingBarInfo = (TextView) v.view.findViewById(R.id.tvRatingBarInfo);
				if(rating > 0)
					tvRatingBarInfo.setText(strRating);
				else
					tvRatingBarInfo.setText(
							SearchResultActivity.this.getResources().getString(R.string.no_rating));

				ImageView imgViewFeatured = (ImageView) v.view.findViewById(R.id.imgViewFeatured);
				imgViewFeatured.setVisibility(View.INVISIBLE);

				ImageView imgViewStarred = (ImageView) v.view.findViewById(R.id.imgViewStarred);
				imgViewStarred.setVisibility(View.INVISIBLE);

				if(store.getFeatured() == 1)
					imgViewFeatured.setVisibility(View.VISIBLE);

				Favorite fave = q.getFavoriteByStoreId(store.getStore_id());
				if(fave != null)
					imgViewStarred.setVisibility(View.VISIBLE);

				TextView tvDistance = (TextView) v.view.findViewById(R.id.tvDistance);
				tvDistance.setVisibility(View.INVISIBLE);

				if(MGUtilities.isLocationEnabled(SearchResultActivity.this) && StoreFinderApplication.currentLocation != null) {
					if(store.getDistance() != -1) {
						tvDistance.setVisibility(View.VISIBLE);
						double km = store.getDistance();
						String format = String.format(
								"%.2f %s",
								km,
								MGUtilities.getStringFromResource(SearchResultActivity.this, R.string.km));
						tvDistance.setText(format);
					}
					else {
						tvDistance.setText(R.string.empty_distance);
					}
				}
			}

		});
		mRecyclerView.setAdapter(adapter);
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar actions click
        switch (item.getItemId()) {
	        default:
	        	finish();	
	            return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_default, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        // if nav drawer is opened, hide the action items
        return super.onPrepareOptionsMenu(menu);
    }

}
