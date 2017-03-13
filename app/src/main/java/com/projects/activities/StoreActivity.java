package com.projects.activities;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
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
import com.config.Config;
import com.config.UIConfig;
import com.db.DbHelper;
import com.db.Queries;
import com.libraries.adapters.MGListAdapter;
import com.libraries.adapters.MGListAdapter.OnMGListAdapterAdapterListener;
import com.libraries.adapters.MGRecyclerAdapter;
import com.libraries.asynctask.MGAsyncTask;
import com.libraries.asynctask.MGAsyncTaskNoDialog;
import com.libraries.dataparser.DataParser;
import com.libraries.imageview.MGImageView;
import com.libraries.usersession.UserAccessSession;
import com.libraries.utilities.MGUtilities;
import com.models.Category;
import com.models.Data;
import com.models.Favorite;
import com.models.Photo;
import com.models.Store;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.projects.storefinder.MainActivity;
import com.projects.storefinder.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class StoreActivity extends AppCompatActivity {
	
	private ArrayList<Store> arrayData;
	private Queries q;
	MGAsyncTaskNoDialog task;
	Category category;

	SwipeRefreshLayout swipeRefresh;
	RecyclerView mRecyclerView;
	RecyclerView.LayoutManager mLayoutManager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		setContentView(R.layout.fragment_list_swipe);
		setTitle(R.string.results);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		q = StoreFinderApplication.getQueriesInstance(this);


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

		category = (Category)this.getIntent().getSerializableExtra("category");
		getData();
	}

	public void showRefresh(boolean show) {
		swipeRefresh.setRefreshing(show);
		swipeRefresh.setEnabled(show);
	}

	public void getData() {
		showRefresh(true);
		task = new MGAsyncTaskNoDialog(this);
		task.setMGAsyncTaskListener(new MGAsyncTaskNoDialog.OnMGAsyncTaskListenerNoDialog() {

			@Override
			public void onAsyncTaskProgressUpdate(MGAsyncTaskNoDialog asyncTask) { }

			@Override
			public void onAsyncTaskPreExecute(MGAsyncTaskNoDialog asyncTask) {

			}

			@Override
			public void onAsyncTaskPostExecute(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
				showList();
			}

			@Override
			public void onAsyncTaskDoInBackground(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
				arrayData = new ArrayList<Store>();
				if(StoreFinderApplication.currentLocation != null && MGUtilities.hasConnection(StoreActivity.this)) {
					try {
						UserAccessSession accessSession = UserAccessSession.getInstance(StoreActivity.this);
						float radius = accessSession.getFilterDistance();
						if(radius == 0)
							radius = Config.DEFAULT_FILTER_DISTANCE_IN_KM;

						String strUrl = String.format("%s?api_key=%s&lat=%s&lon=%s&radius=%s&category_id=%s",
								Config.GET_STORES_JSON_URL,
								Config.API_KEY,
								String.valueOf(StoreFinderApplication.currentLocation.getLatitude()),
								String.valueOf(StoreFinderApplication.currentLocation.getLongitude()),
								String.valueOf(radius),
								String.valueOf(category.getCategory_id()));

						DataParser parser = new DataParser();
						Data data = parser.getData(strUrl);
						if (data == null)
							return;

						if (data.getCategories() != null && data.getCategories().size() > 0) {
							for (Category cat : data.getCategories()) {
								q.deleteCategory(cat.getCategory_id());
								q.insertCategory(cat);
							}
						}

						if (data.getStores() != null && data.getStores().size() > 0) {
							for (Store store : data.getStores()) {
								q.deleteStore(store.getStore_id());
								q.insertStore(store);
								arrayData.add(store);

								if (store.getPhotos() != null && store.getPhotos().size() > 0) {
									for (Photo photo : store.getPhotos()) {
										q.deletePhoto(photo.getPhoto_id());
										q.insertPhoto(photo);
									}
								}
							}
						}

                        if(data.getMax_distance() > 0) {
                            UserAccessSession.getInstance(StoreActivity.this).setFilterDistanceMax(data.getMax_distance());
                        }

						if(Config.AUTO_ADJUST_DISTANCE) {
							if(UserAccessSession.getInstance(StoreActivity.this).getFilterDistance() == 0) {
								UserAccessSession.getInstance(StoreActivity.this).setFilterDistance(data.getDefault_distance());
							}
						}

					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				else {
					sortData();
				}
			}
		});
		task.execute();
	}

	private void sortData() {
        UserAccessSession accessSession = UserAccessSession.getInstance(StoreActivity.this);
        float radius = accessSession.getFilterDistance();

		ArrayList<Store> arrayData1 = q.getStoresByCategoryId(category.getCategory_id());
        arrayData = new ArrayList<Store>();
		if(StoreFinderApplication.currentLocation != null && Config.RANK_STORES_ACCORDING_TO_NEARBY) {
			for(Store store : arrayData1) {
				Location locStore = new Location("Store");
				locStore.setLatitude(store.getLat());
				locStore.setLongitude(store.getLon());
				double userDistanceFromStore = StoreFinderApplication.currentLocation.distanceTo(locStore) / 1000;
				store.setDistance(userDistanceFromStore);

                if(store.getDistance() <= radius)
                    arrayData.add(store);
			}

			Collections.sort(arrayData, new Comparator<Store>() {
				@Override
				public int compare(Store store, Store t1) {
					if (store.getDistance() < t1.getDistance())
						return -1;
					if (store.getDistance() > t1.getDistance())
						return 1;
					return 0;
				}
			});
		}
	}
	
	private void showList() {
		showRefresh(false);

		if(arrayData == null || arrayData.size() == 0) {
			MGUtilities.showNotifier(StoreActivity.this, MainActivity.offsetY);
			return;
		}

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
						Intent i = new Intent(StoreActivity.this, DetailActivity.class);
						i.putExtra("store", store);
						StoreActivity.this.startActivity(i);
					}
				});

				if(p != null) {
					StoreFinderApplication.getImageLoaderInstance(StoreActivity.this)
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

				float rating = 0;
				if(store.getRating_total() > 0 && store.getRating_count() > 0)
					rating = store.getRating_total() / store.getRating_count();

				String strRating = String.format("%.2f %s %d %s",
						rating,
						StoreActivity.this.getResources().getString(R.string.average_based_on),
						store.getRating_count(),
						StoreActivity.this.getResources().getString(R.string.rating));

				RatingBar ratingBar = (RatingBar) v.view.findViewById(R.id.ratingBar);
				ratingBar.setRating(rating);

				TextView tvRatingBarInfo = (TextView) v.view.findViewById(R.id.tvRatingBarInfo);
				if(rating > 0)
					tvRatingBarInfo.setText(strRating);
				else
					tvRatingBarInfo.setText(
							StoreActivity.this.getResources().getString(R.string.no_rating));

				Favorite fave = q.getFavoriteByStoreId(store.getStore_id());
				ImageView imgViewFeatured = (ImageView) v.view.findViewById(R.id.imgViewFeatured);
				imgViewFeatured.setVisibility(View.INVISIBLE);

				ImageView imgViewStarred = (ImageView) v.view.findViewById(R.id.imgViewStarred);
				imgViewStarred.setVisibility(View.INVISIBLE);

				if(fave != null)
					imgViewStarred.setVisibility(View.VISIBLE);

				if(store.getFeatured() == 1)
					imgViewFeatured.setVisibility(View.VISIBLE);

				TextView tvDistance = (TextView) v.view.findViewById(R.id.tvDistance);
				tvDistance.setVisibility(View.INVISIBLE);
				if(MGUtilities.isLocationEnabled(StoreActivity.this) && StoreFinderApplication.currentLocation != null) {
					if(store.getDistance() != -1) {
						tvDistance.setVisibility(View.VISIBLE);
						double km = store.getDistance();
						String format = String.format(
								"%.2f %s",
								km,
								MGUtilities.getStringFromResource(StoreActivity.this, R.string.km));
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
        // toggle nav drawer on selecting action bar app icon/title
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

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(task != null)
			task.cancel(true);
	}
}
