package com.projects.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.application.StoreFinderApplication;
import com.config.Config;
import com.config.UIConfig;
import com.db.Queries;
import com.libraries.adapters.MGRecyclerAdapter;
import com.libraries.asynctask.MGAsyncTask;
import com.libraries.asynctask.MGAsyncTask.OnMGAsyncTaskListener;
import com.libraries.asynctask.MGAsyncTaskNoDialog;
import com.libraries.dataparser.DataParser;
import com.libraries.helpers.DateTimeHelper;
import com.libraries.imageview.MGImageView;
import com.libraries.location.MGLocationManagerUtils;
import com.libraries.slider.MGSlider;
import com.libraries.slider.MGSlider.OnMGSliderListener;
import com.libraries.slider.MGSliderAdapter;
import com.libraries.slider.MGSliderAdapter.OnMGSliderAdapterListener;
import com.libraries.usersession.UserAccessSession;
import com.libraries.utilities.MGUtilities;
import com.models.Data;
import com.models.News;
import com.models.Photo;
import com.models.Store;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.projects.activities.DetailActivity;
import com.projects.activities.NewsDetailActivity;
import com.projects.storefinder.MainActivity;
import com.projects.storefinder.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class HomeFragment extends Fragment implements StoreFinderApplication.OnLocationListener {
	
	private View viewInflate;
	DisplayImageOptions options;
	ArrayList<Store> storeList;
	ArrayList<News> newsList;
	MGAsyncTaskNoDialog task;
    Queries q;
	SwipeRefreshLayout swipeRefresh;
	RecyclerView mRecyclerView;
	RecyclerView.LayoutManager mLayoutManager;
	MGSlider slider;

	public HomeFragment() { }
	
	@SuppressLint("InflateParams")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		viewInflate = inflater.inflate(R.layout.fragment_home, null);
		return viewInflate;
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);		
	}
	
	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		if(task != null)
			task.cancel(true);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onViewCreated(view, savedInstanceState);

        storeList = new ArrayList<Store>();
        newsList = new ArrayList<News>();

		slider = (MGSlider) viewInflate.findViewById(R.id.slider);
        q = StoreFinderApplication.getQueriesInstance(getContext());

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
				if(!MGUtilities.hasConnection(getActivity())) {
                    ArrayList<Store> stores = q.getStoresFeatured();
                    ArrayList<News> news = q.getNews();
                    if (Config.HOME_STORE_FEATURED_COUNT != -1 && Config.RANK_STORES_ACCORDING_TO_NEARBY) {
                        int storeCount = stores.size() < Config.HOME_STORE_FEATURED_COUNT ?
                                stores.size() : Config.HOME_STORE_FEATURED_COUNT;

                        if (StoreFinderApplication.currentLocation != null) {
                            for (Store store : stores) {
                                Location locStore = new Location("Store");
                                locStore.setLatitude(store.getLat());
                                locStore.setLongitude(store.getLon());
                                double userDistanceFromStore = StoreFinderApplication.currentLocation.distanceTo(locStore) / 1000;
                                store.setDistance(userDistanceFromStore);
                            }

                            Collections.sort(stores, new Comparator<Store>() {
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
                        storeList = new ArrayList<Store>();
                        for (int x = 0; x < storeCount; x++) {
                            storeList.add(stores.get(x));
                        }
                    } else {
                        storeList = stores;
                    }

                    if (Config.HOME_NEWS_COUNT != -1 && Config.RANK_STORES_ACCORDING_TO_NEARBY) {
                        int newsCount = news.size() < Config.HOME_NEWS_COUNT ? news.size() : Config.HOME_NEWS_COUNT;
                        newsList = new ArrayList<News>();
                        for (int x = 0; x < newsCount; x++) {
                            newsList.add(news.get(x));
                        }
                    } else {
                        newsList = news;
                    }
                }

				createSlider();
				showList();
                showRefresh(false);
			}

			@Override
			public void onAsyncTaskDoInBackground(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
				if( MGUtilities.hasConnection(getActivity()) && StoreFinderApplication.currentLocation != null) {
					try {
                        UserAccessSession accessSession = UserAccessSession.getInstance(getActivity());
                        String strUrl = "";

						Location loc = StoreFinderApplication.currentLocation;
						if(accessSession.getFilterDistance() == 0) {
							strUrl = String.format("%s?api_key=%s&lat=%s&lon=%s&radius=%s&news_count=%s&featured_count=%s&default_store_count_to_find_distance=%s",
									Config.GET_HOME_STORES_NEWS_JSON_URL,
									Config.API_KEY,
									String.valueOf(loc.getLatitude()),
									String.valueOf(loc.getLongitude()),
									String.valueOf(accessSession.getFilterDistance()),
									String.valueOf(Config.HOME_NEWS_COUNT),
									String.valueOf(Config.HOME_FEATURED_COUNT),
									String.valueOf(Config.DEFAULT_STORE_COUNT_TO_FIND_DISTANCE));
						}
						else {
							strUrl = String.format("%s?api_key=%s&lat=%s&lon=%s&radius=%s&news_count=%s&default_store_count_to_find_distance=%s",
									Config.GET_HOME_STORES_NEWS_JSON_URL,
									Config.API_KEY,
									String.valueOf(loc.getLatitude()),
									String.valueOf(loc.getLongitude()),
									String.valueOf(accessSession.getFilterDistance()),
									String.valueOf(Config.HOME_NEWS_COUNT),
									String.valueOf(Config.DEFAULT_STORE_COUNT_TO_FIND_DISTANCE));
						}

						Log.e("strUrl", strUrl);
						DataParser parser = new DataParser();
						Data data = parser.getData(strUrl);
						MainActivity main = (MainActivity) getActivity();
						if (main == null)
							return;

						if (data == null)
							return;

						if(data.getMax_distance() > 0) {
							UserAccessSession.getInstance(getActivity()).setFilterDistanceMax(data.getMax_distance());
						}

						if(Config.AUTO_ADJUST_DISTANCE) {
							if(UserAccessSession.getInstance(getActivity()).getFilterDistance() == 0) {
								UserAccessSession.getInstance(getActivity()).setFilterDistance(data.getDefault_distance());
							}
						}

						if (data.getStores() != null && data.getStores().size() > 0) {

							int storeCount = data.getStores().size() < Config.HOME_STORE_FEATURED_COUNT ?
									data.getStores().size() : Config.HOME_STORE_FEATURED_COUNT;

							int x = 0;
							for (Store store : data.getStores()) {
                                q.deleteStore(store.getStore_id());
								q.insertStore(store);

								if(x < storeCount) {
									storeList.add(store);
									x += 1;
								}

                                if (store.getPhotos() != null && store.getPhotos().size() > 0) {
                                    for (Photo photo : store.getPhotos()) {
                                        q.deletePhoto(photo.getPhoto_id());
                                        q.insertPhoto(photo);
                                    }
                                }
							}
						}

                        if (data.getNews() != null && data.getNews().size() > 0) {

							int newsCount = data.getNews().size() < Config.HOME_NEWS_COUNT
									? data.getNews().size() : Config.HOME_NEWS_COUNT;

							int x = 0;
                            for (News news : data.getNews()) {
                                q.deleteNews(news.getNews_id());
                                q.insertNews(news);

								if(x < newsCount) {
									newsList.add(news);
									x += 1;
								}
                            }
                        }
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		});
		task.execute();
	}
	
	@Override
    public void onDestroyView()  {
        super.onDestroyView();
        if (viewInflate != null) {
            ViewGroup parentViewGroup = (ViewGroup) viewInflate.getParent();
            if (parentViewGroup != null) {
        		slider.pauseSliderAnimation();
                parentViewGroup.removeAllViews();
            }
        }

        if(task != null)
            task.cancel(true);
    }
	
	private void showList() {
		MGRecyclerAdapter adapter = new MGRecyclerAdapter(newsList.size(), R.layout.news_entry);
		adapter.setOnMGRecyclerAdapterListener(new MGRecyclerAdapter.OnMGRecyclerAdapterListener() {

			@Override
			public void onMGRecyclerAdapterCreated(MGRecyclerAdapter adapter, MGRecyclerAdapter.ViewHolder v, int position) {
				final News news = newsList.get(position);
				MGImageView imgViewPhoto = (MGImageView) v.view.findViewById(R.id.imgViewPhoto);
				imgViewPhoto.setCornerRadius(0.0f);
				imgViewPhoto.setBorderWidth(UIConfig.BORDER_WIDTH);
				imgViewPhoto.setBorderColor(getResources().getColor(UIConfig.THEME_BLACK_COLOR));

				if(news.getPhoto_url() != null) {
					StoreFinderApplication.getImageLoaderInstance(getActivity()).displayImage(news.getPhoto_url(), imgViewPhoto, options);
				}
				else {
					StoreFinderApplication.getImageLoaderInstance(getActivity()).displayImage(null, imgViewPhoto, options);
				}

				imgViewPhoto.setTag(position);
				Spanned name = Html.fromHtml(news.getNews_title());
				Spanned address = Html.fromHtml(news.getNews_content());

				TextView tvTitle = (TextView) v.view.findViewById(R.id.tvTitle);
				tvTitle.setText(name);

				TextView tvSubtitle = (TextView) v.view.findViewById(R.id.tvSubtitle);
				tvSubtitle.setText(address);

				String date = DateTimeHelper.getStringDateFromTimeStamp(news.getCreated_at(), "MM/dd/yyyy" );
				TextView tvDate = (TextView) v.view.findViewById(R.id.tvDate);
				tvDate.setText(date);

				v.view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
                        slider.stopSliderAnimation();
						Intent i = new Intent(getActivity(), NewsDetailActivity.class);
						i.putExtra("news", news);
						getActivity().startActivity(i);
					}
				});
			}

		});
		mRecyclerView.setAdapter(adapter);
	}
	
	// Create Slider
	private void createSlider() {
		if(storeList != null && storeList.size() == 0 && newsList != null && newsList.size() == 0) {
			MGUtilities.showNotifier(this.getActivity(), MainActivity.offsetY, R.string.failed_data);
			return;
		}

		slider.setMaxSliderThumb(storeList.size());
    	MGSliderAdapter adapter = new MGSliderAdapter(
    			R.layout.slider_entry, storeList.size(), storeList.size());
    	
    	adapter.setOnMGSliderAdapterListener(new OnMGSliderAdapterListener() {
			
			@Override
			public void onOnMGSliderAdapterCreated(MGSliderAdapter adapter, View v,
												   int position) {
				// TODO Auto-generated method stub
				final Store entry = storeList.get(position);
				Photo p = q.getPhotoByStoreId(entry.getStore_id());
				ImageView imageViewSlider = (ImageView) v.findViewById(R.id.imageViewSlider);
				if(p != null) {
					StoreFinderApplication.getImageLoaderInstance(getActivity()).displayImage(p.getPhoto_url(), imageViewSlider, options);
				}
				else {
					imageViewSlider.setImageResource(UIConfig.SLIDER_PLACEHOLDER);
				}
				
				imageViewSlider.setTag(position);
				imageViewSlider.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						// TODO Auto-generated method stub
						Intent i = new Intent(getActivity(), DetailActivity.class);
						i.putExtra("store", entry);
						getActivity().startActivity(i);
					}
				});
				
				Spanned name = Html.fromHtml(entry.getStore_name());
                name = Html.fromHtml(name.toString());

				Spanned address = Html.fromHtml(entry.getStore_address());
                address = Html.fromHtml(address.toString());

				TextView tvTitle = (TextView) v.findViewById(R.id.tvTitle);
				tvTitle.setText(name);
				
				TextView tvSubtitle = (TextView) v.findViewById(R.id.tvSubtitle);
				tvSubtitle.setText(address);

				TextView tvDistance = (TextView) v.findViewById(R.id.tvDistance);
				tvDistance.setVisibility(View.INVISIBLE);
				if(MGUtilities.isLocationEnabled(getActivity()) && StoreFinderApplication.currentLocation != null) {
					if(entry.getDistance() != -1) {
						tvDistance.setVisibility(View.VISIBLE);
						double miles = entry.getDistance();
						String format = String.format(
								"%.2f %s", miles,
								MGUtilities.getStringFromResource(getActivity(), R.string.km));
						tvDistance.setText(format);
					}
					else {
						tvDistance.setText(R.string.empty_distance);
					}
				}
			}
		});
    	
    	slider.setOnMGSliderListener(new OnMGSliderListener() {
			
			@Override
			public void onItemThumbSelected(MGSlider slider, ImageView[] buttonPoint,
											ImageView imgView, int pos) { }
			
			@Override
			public void onItemThumbCreated(MGSlider slider, ImageView imgView, int pos) { }
			
			
			@Override
			public void onItemPageScrolled(MGSlider slider, ImageView[] buttonPoint, int pos) { }
			
			@Override
			public void onItemMGSliderToView(MGSlider slider, int pos) { }
			
			@Override
			public void onItemMGSliderViewClick(AdapterView<?> adapterView, View v,
												int pos, long resid) { }

			@Override
			public void onAllItemThumbCreated(MGSlider slider, LinearLayout linearLayout) { }
			
		});
    	slider.setOffscreenPageLimit(storeList.size() - 1);
    	slider.setAdapter(adapter);
    	slider.setActivity(this.getActivity());
    	slider.setSliderAnimation(5000);
    	slider.resumeSliderAnimation();
	}
	
	@Override
	public void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		slider.resumeSliderAnimation();
	}
	
	@Override
	public void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		slider.pauseSliderAnimation();
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
