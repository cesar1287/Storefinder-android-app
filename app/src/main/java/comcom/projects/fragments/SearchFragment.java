package comcom.projects.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.AppCompatSeekBar;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import comcom.application.StoreFinderApplication;
import comcom.config.Config;
import comcom.db.Queries;
import comcom.libraries.asynctask.MGAsyncTaskNoDialog;
import comcom.libraries.dataparser.DataParser;
import comcom.libraries.location.MGLocationManagerUtils;
import comcom.libraries.usersession.UserAccessSession;
import comcom.libraries.utilities.MGUtilities;
import comcom.models.Category;
import comcom.models.Data;
import comcom.models.Photo;
import comcom.models.Store;
import comcom.projects.activities.SearchResultActivity;
import comcom.projects.storefinder.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class SearchFragment extends Fragment implements OnClickListener, StoreFinderApplication.OnLocationListener{
	
	private View viewInflate;
	private EditText txtKeywords;
	private AppCompatSeekBar seekbarRadius;
	private Spinner spinnerCategories;
	private SwitchCompat toggleButtonNearby;
	private MGAsyncTaskNoDialog task;
    Queries q;
	SwipeRefreshLayout swipeRefresh;
	TextView tvRadiusText;
	
	public SearchFragment() { }
	
	@Override
    public void onDestroyView()  {
        super.onDestroyView();
        if(task != null)
        	task.cancel(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		viewInflate = inflater.inflate(R.layout.fragment_search, null);
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

		Button btnSearch = (Button) viewInflate.findViewById(R.id.btnSearch);
		btnSearch.setOnClickListener(this);

		txtKeywords = (EditText) viewInflate.findViewById(R.id.txtKeywords);
		toggleButtonNearby = (SwitchCompat) viewInflate.findViewById(R.id.toggleButtonNearby);

		toggleButtonNearby.setOnClickListener(this);
		tvRadiusText = (TextView) viewInflate.findViewById(R.id.tvRadiusText);
		seekbarRadius = (AppCompatSeekBar) viewInflate.findViewById(R.id.seekbarRadius);

		float distance = UserAccessSession.getInstance(getActivity()).getFilterDistance();
		seekbarRadius.setMax((int)distance);
		seekbarRadius.setProgress((int)distance / 2);
		seekbarRadius.setEnabled(false);
		seekbarRadius.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar arg0) { }
			
			@Override
			public void onStartTrackingTouch(SeekBar arg0) { }
			
			@SuppressLint("DefaultLocale")
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				// TODO Auto-generated method stub
				updateRadius(progress);
			}
		});
		updateRadius(seekbarRadius.getProgress());

		ArrayList<String> categories = new ArrayList<String>();
		String allCategories = this.getActivity().getResources().getString(R.string.all_categories);
		categories.add(0, allCategories);
		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(
				getActivity(), android.R.layout.simple_spinner_item, categories);
         
		dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerCategories = (Spinner) viewInflate.findViewById(R.id.spinnerCategories);
		spinnerCategories.setAdapter(dataAdapter);

		getData();
	}

	private void updateRadius(int progress) {
		String strSeekVal = String.format("%s: %d %s",
				MGUtilities.getStringFromResource(getActivity(), R.string.radius),
				progress,
				MGUtilities.getStringFromResource(getActivity(), R.string.km));

		tvRadiusText.setText(strSeekVal);
	}

	public void showRefresh(boolean show) {
		swipeRefresh.setRefreshing(show);
		swipeRefresh.setEnabled(show);
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch(v.getId()) {
			case R.id.toggleButtonNearby:
				if(toggleButtonNearby.isChecked()) {
					if(StoreFinderApplication.currentLocation == null) {
						activateLocation();
					}
					seekbarRadius.setEnabled(true);
				}
				else {
					seekbarRadius.setEnabled(false);
				}
				break;
			case R.id.btnSearch:
				asyncSearch();
				break;
		}
	}

	private void asyncSearch() {
		task = new MGAsyncTaskNoDialog(getActivity());
		task.setMGAsyncTaskListener(new MGAsyncTaskNoDialog.OnMGAsyncTaskListenerNoDialog() {
			
			ArrayList<Store> arrayFilter;
			
			@Override
			public void onAsyncTaskProgressUpdate(MGAsyncTaskNoDialog asyncTask) { }
			
			@Override
			public void onAsyncTaskPreExecute(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
				showRefresh(true);
			}
			
			@Override
			public void onAsyncTaskPostExecute(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
				showRefresh(false);
				if(arrayFilter != null && arrayFilter.size() == 0) {
					Toast.makeText(getActivity(), R.string.no_results_found, Toast.LENGTH_SHORT).show();
					return;
				}
				
				Intent i = new Intent(getActivity(), SearchResultActivity.class);
				i.putExtra("searchResults", arrayFilter);
				getActivity().startActivity(i);
			}
			
			@Override
			public void onAsyncTaskDoInBackground(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
				arrayFilter = search();
			}
		});
		task.execute();
	}

	private ArrayList<Store> search() {
		String strKeywords = txtKeywords.getText().toString().toLowerCase().trim();
	    int radius = seekbarRadius.getProgress();
	    String category = spinnerCategories.getSelectedItem().toString();
	    
	    int countParams = strKeywords.length() > 0 ? 1 : 0;
	    countParams += radius > 0 && toggleButtonNearby.isChecked() ? 1 : 0;
	    countParams += category.length() > 0 ? 1 : 0;

	    ArrayList<Store> arrayStores = q.getStores();
	    ArrayList<Store> arrayFilter = new ArrayList<Store>();
	    for(Store store : arrayStores) {
	    	int qualifyCount = 0;
	        boolean isFoundKeyword = store.getStore_name().toLowerCase().contains(strKeywords) ||
	                               store.getStore_address().toLowerCase().contains(strKeywords);
	        
	        if( strKeywords.length() > 0  && isFoundKeyword)
	            qualifyCount += 1;
	        
	        if( category.length() > 0) {
	            Category storeCat = q.getCategoryByCategory(category);
	            boolean isFoundCat = false;
	            
	            if(storeCat != null && storeCat.getCategory_id() == store.getCategory_id())
	                isFoundCat = true;
	            
	            if(spinnerCategories.getSelectedItemPosition() == 0)
	                isFoundCat = true;
	            
	            if(isFoundCat)
	                qualifyCount += 1;
	        }
	        store.setDistance(-1);
	        if(toggleButtonNearby.isChecked()) {
				if(StoreFinderApplication.currentLocation!= null) {
					Location locStore = new Location("Store");
					locStore.setLatitude(store.getLat());
					locStore.setLongitude(store.getLon());
					
					double distance = locStore.distanceTo(StoreFinderApplication.currentLocation) / 1000;
					store.setDistance(distance);
					if(distance <= radius)
		                qualifyCount += 1;
				}
	        }
	        if(qualifyCount == countParams)
	        	arrayFilter.add(store);
	    }
		if(toggleButtonNearby.isChecked()) {
			Collections.sort(arrayFilter, new Comparator<Store>() {
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
	    return arrayFilter;
	}

	public void getData() {
		showRefresh(true);
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
                ArrayList<String> categories = q.getCategoryNames();
                String allCategories = getActivity().getResources().getString(R.string.all_categories);
                categories.add(0, allCategories);
                ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(
                        getActivity(), android.R.layout.simple_spinner_item, categories);

                dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerCategories = (Spinner) viewInflate.findViewById(R.id.spinnerCategories);
                spinnerCategories.setAdapter(dataAdapter);

				showRefresh(false);
			}

			@Override
			public void onAsyncTaskDoInBackground(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
				if(StoreFinderApplication.currentLocation != null) {
					try {
						UserAccessSession accessSession = UserAccessSession.getInstance(getActivity());
						String strUrl = String.format("%s?api_key=%s&lat=%f&lon=%f&radius=%f&fetch_category=1",
								Config.GET_STORES_NEWS_JSON_URL,
								Config.API_KEY,
								StoreFinderApplication.currentLocation.getLatitude(),
								StoreFinderApplication.currentLocation.getLongitude(),
								accessSession.getFilterDistanceMax());

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
								if (store.getPhotos() != null && store.getPhotos().size() > 0) {
									for (Photo photo : store.getPhotos()) {
										q.deletePhoto(photo.getPhoto_id());
										q.insertPhoto(photo);
									}
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
    public void onLocationChanged(Location prevLoc, Location currentLoc) {
        StoreFinderApplication app = (StoreFinderApplication) getActivity().getApplication();
        app.setOnLocationListener(null, getActivity());
        showRefresh(false);
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
                seekbarRadius.setEnabled(false);
                toggleButtonNearby.setChecked(false);
            }
        }
    }

    private void activateLocation() {
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
                    seekbarRadius.setEnabled(false);
                    toggleButtonNearby.setChecked(false);
                }
            });
            utils.showAlertView(
                    getActivity(),
                    R.string.location_error,
                    R.string.gps_not_on,
                    R.string.go_to_settings,
                    R.string.cancel,
                    false);
        }
        else {
            if(StoreFinderApplication.currentLocation == null)
                refetch();
        }
    }

    public void refetch() {
        showRefresh(true);
        StoreFinderApplication app = (StoreFinderApplication) getActivity().getApplication();
        app.setOnLocationListener(this, getActivity());
    }
}
