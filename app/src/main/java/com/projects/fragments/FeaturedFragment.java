package com.projects.fragments;

import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.application.StoreFinderApplication;
import com.config.Config;
import com.config.UIConfig;
import com.db.Queries;
import com.libraries.adapters.MGListAdapter;
import com.libraries.adapters.MGListAdapter.OnMGListAdapterAdapterListener;
import com.libraries.adapters.MGRecyclerAdapter;
import com.libraries.asynctask.MGAsyncTask;
import com.libraries.asynctask.MGAsyncTaskNoDialog;
import com.libraries.dataparser.DataParser;
import com.libraries.imageview.MGImageView;
import com.libraries.location.MGLocationManagerUtils;
import com.libraries.usersession.UserAccessSession;
import com.libraries.utilities.MGUtilities;
import com.models.Category;
import com.models.Data;
import com.models.Favorite;
import com.models.Photo;
import com.models.Store;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.projects.activities.DetailActivity;
import com.projects.activities.StoreActivity;
import com.projects.storefinder.MainActivity;
import com.projects.storefinder.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class FeaturedFragment extends Fragment implements StoreFinderApplication.OnLocationListener{
	
	private View viewInflate;
	private ArrayList<Store> arrayData;
	private MGAsyncTaskNoDialog task;
	Queries q;
	SwipeRefreshLayout swipeRefresh;
	RecyclerView mRecyclerView;
	RecyclerView.LayoutManager mLayoutManager;
	
	public FeaturedFragment() { }

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		viewInflate = inflater.inflate(R.layout.fragment_list_swipe, null);
		return viewInflate;
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onViewCreated(view, savedInstanceState);


		q = StoreFinderApplication.getQueriesInstance(this.getActivity());

		mRecyclerView = (RecyclerView) viewInflate.findViewById(R.id.recyclerView);
		mRecyclerView.setHasFixedSize(true);

		mLayoutManager = new LinearLayoutManager(getActivity());
		mRecyclerView.setLayoutManager(mLayoutManager);

		swipeRefresh = (SwipeRefreshLayout) viewInflate.findViewById(R.id.swipe_refresh);
		swipeRefresh.setClickable(false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			swipeRefresh.setProgressViewOffset(false, 0,100);
		}

		swipeRefresh.setColorSchemeResources(
				android.R.color.holo_blue_bright,
				android.R.color.holo_green_light,
				android.R.color.holo_orange_light,
				android.R.color.holo_red_light);

        arrayData = new ArrayList<Store>();

		if(!MGUtilities.isLocationEnabled(getActivity()) && StoreFinderApplication.currentLocation == null) {
			MGLocationManagerUtils utils = new MGLocationManagerUtils();
			utils.setOnAlertListener(new MGLocationManagerUtils.OnAlertListener() {
				@Override
				public void onPositiveTapped() {
					startActivityForResult(
							new Intent(
									Settings.ACTION_LOCATION_SOURCE_SETTINGS),
							Config.PERMISSION_REQUEST_LOCATION_SETTINGS);
				}

				@Override
				public void onNegativeTapped() {
					showRefresh(false);
				}
			});
			utils.showAlertView(
					getActivity(),
					R.string.location_error,
					R.string.gps_not_on,
					R.string.go_to_settings,
					R.string.cancel);
		}
		else {
			refetch();
		}
	}

	public void refetch() {
		showRefresh(true);
		StoreFinderApplication app = (StoreFinderApplication) getActivity().getApplication();
		app.setOnLocationListener(this, getActivity());
	}

	public void showRefresh(boolean show) {
		swipeRefresh.setRefreshing(show);
		swipeRefresh.setEnabled(show);
	}

	private void showList() {
		showRefresh(false);
		if(arrayData != null && arrayData.size() == 0) {
			MGUtilities.showNotifier(this.getActivity(), MainActivity.offsetY);
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
						Intent i = new Intent(getActivity(), DetailActivity.class);
						i.putExtra("store", store);
						getActivity().startActivity(i);
					}
				});

				if(p != null) {
					StoreFinderApplication.getImageLoaderInstance(getActivity())
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
						getActivity().getResources().getString(R.string.average_based_on),
						store.getRating_count(),
						getActivity().getResources().getString(R.string.rating));

				RatingBar ratingBar = (RatingBar) v.view.findViewById(R.id.ratingBar);
				ratingBar.setRating(rating);

				TextView tvRatingBarInfo = (TextView) v.view.findViewById(R.id.tvRatingBarInfo);
				if(rating > 0)
					tvRatingBarInfo.setText(strRating);
				else
					tvRatingBarInfo.setText(
							getActivity().getResources().getString(R.string.no_rating));

				Favorite fave = q.getFavoriteByStoreId(store.getStore_id());

				ImageView imgViewFeatured = (ImageView) v.view.findViewById(R.id.imgViewFeatured);
				imgViewFeatured.setVisibility(View.VISIBLE);

				ImageView imgViewStarred = (ImageView) v.view.findViewById(R.id.imgViewStarred);
				imgViewStarred.setVisibility(View.VISIBLE);

				if(fave == null)
					imgViewStarred.setVisibility(View.INVISIBLE);

				if(store.getFeatured() == 0)
					imgViewFeatured.setVisibility(View.INVISIBLE);

				TextView tvDistance = (TextView) v.view.findViewById(R.id.tvDistance);
				tvDistance.setVisibility(View.INVISIBLE);

				if(MGUtilities.isLocationEnabled(getActivity()) && StoreFinderApplication.currentLocation != null) {
					if(store.getDistance() != -1) {
						tvDistance.setVisibility(View.VISIBLE);
						double km = store.getDistance();
						String format = String.format(
								"%.2f %s",
								km,
								MGUtilities.getStringFromResource(getActivity(), R.string.km));
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

	public void getData() {
		task = new MGAsyncTaskNoDialog(getActivity());
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
				showRefresh(false);

                if(arrayData != null && arrayData.size() == 0) {
                    MGUtilities.showNotifier(getActivity(), MainActivity.offsetY);
                    return;
                }
			}

			@Override
			public void onAsyncTaskDoInBackground(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
				if( MGUtilities.hasConnection(getActivity()) && StoreFinderApplication.currentLocation != null) {
					try {
						UserAccessSession accessSession = UserAccessSession.getInstance(getActivity());
						float radius = accessSession.getFilterDistance();
						if(radius == 0)
							radius = Config.DEFAULT_FILTER_DISTANCE_IN_KM;;

						String strUrl = String.format("%s?api_key=%s&lat=%s&lon=%s&radius=%s&featured=1",
								Config.GET_STORES_JSON_URL,
								Config.API_KEY,
								String.valueOf(StoreFinderApplication.currentLocation.getLatitude()),
								String.valueOf(StoreFinderApplication.currentLocation.getLongitude()),
								String.valueOf(radius));

						Log.e("URL", strUrl);
						DataParser parser = new DataParser();
						Data data = parser.getData(strUrl);
						MainActivity main = (MainActivity) getActivity();
						if (main == null)
							return;

						if (data == null)
							return;

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
                            UserAccessSession.getInstance(getActivity()).setFilterDistanceMax(data.getMax_distance());
                        }

						if(Config.AUTO_ADJUST_DISTANCE) {
							if(UserAccessSession.getInstance(getActivity()).getFilterDistance() == 0) {
								UserAccessSession.getInstance(getActivity()).setFilterDistance(data.getDefault_distance());
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
		UserAccessSession accessSession = UserAccessSession.getInstance(getActivity());
		float radius = accessSession.getFilterDistance();

		ArrayList<Store> arrayData1 = q.getStoresFeatured();
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

	@Override
	public void onDestroyView()  {
		super.onDestroyView();
		if(task != null)
			task.cancel(true);

		if (viewInflate != null) {
			ViewGroup parentViewGroup = (ViewGroup) viewInflate.getParent();
			if (parentViewGroup != null) {
				parentViewGroup.removeAllViews();
			}
		}
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		if(task != null)
			task.cancel(true);
	}

	@Override
	public void onLocationChanged(Location prevLoc, Location currentLoc) {
		StoreFinderApplication app = (StoreFinderApplication) getActivity().getApplication();
		app.setOnLocationListener(null, getActivity());
		getData();
	}


	@Override
	public void onLocationRequestDenied() {
		showRefresh(false);
		MGUtilities.showAlertView(getActivity(), R.string.permission_error, R.string.permission_error_details_location);
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Config.PERMISSION_REQUEST_LOCATION_SETTINGS) {
			if(MGUtilities.isLocationEnabled(getActivity()))
				refetch();
			else {
				showRefresh(false);
				Toast.makeText(getActivity(), R.string.location_error_not_turned_on, Toast.LENGTH_LONG).show();
			}
		}
	}
}
