package comcom.projects.fragments;

import android.annotation.SuppressLint;
import android.location.Address;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import comcom.application.StoreFinderApplication;
import comcom.config.Config;
import comcom.libraries.asynctask.MGAsyncTaskNoDialog;
import comcom.libraries.dataparser.DataParser;
import comcom.libraries.utilities.MGUtilities;
import comcom.models.DataWeather;
import comcom.models.Weather;

public class WeatherFragment extends Fragment {
	
	private View viewInflate;
	private DataWeather dataWeather;
	MGAsyncTaskNoDialog task;
	SwipeRefreshLayout swipeRefresh;
	
	public WeatherFragment() { }

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		viewInflate = inflater.inflate(comcom.projects.storefinder.R.layout.fragment_weather, null);
		return viewInflate;
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}
	
	@Override
	public void onDestroyView() {
		// TODO Auto-generated method stub
		super.onDestroyView();
		if(task != null)
			task.cancel(true);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onViewCreated(view, savedInstanceState);

		swipeRefresh = (SwipeRefreshLayout) viewInflate.findViewById(comcom.projects.storefinder.R.id.swipe_refresh);
		swipeRefresh.setClickable(false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			swipeRefresh.setProgressViewOffset(false, 0,100);
		}

		swipeRefresh.setColorSchemeResources(
				android.R.color.holo_blue_bright,
				android.R.color.holo_green_light,
				android.R.color.holo_orange_light,
				android.R.color.holo_red_light);

		if(StoreFinderApplication.currentLocation == null) {
			MGUtilities.showAlertView(
					getActivity(), 
					comcom.projects.storefinder.R.string.location_error,
					comcom.projects.storefinder.R.string.cannot_determine_location);
			return;
		}

		showRefresh(false);
		Handler h = new Handler();
		h.postDelayed(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				getData();
			}
		}, Config.DELAY_SHOW_ANIMATION);
	}

	public void showRefresh(boolean show) {
		swipeRefresh.setRefreshing(show);
		swipeRefresh.setEnabled(show);
	}

	public void getData() {

		showRefresh(true);
		task = new MGAsyncTaskNoDialog(getActivity());
		task.setMGAsyncTaskListener(new MGAsyncTaskNoDialog.OnMGAsyncTaskListenerNoDialog() {
			
			@Override
			public void onAsyncTaskProgressUpdate(MGAsyncTaskNoDialog asyncTask) { }
			
			@Override
			public void onAsyncTaskPreExecute(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void onAsyncTaskPostExecute(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
				showRefresh(false);
				updateView();
			}
			
			@SuppressLint("DefaultLocale")
			@Override
			public void onAsyncTaskDoInBackground(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
				Location location = StoreFinderApplication.currentLocation;
				String weatherUrl = String.format("%slat=%s&lon=%s&APPID=%s",
						Config.WEATHER_URL,
						String.valueOf(location.getLatitude()),
						String.valueOf(location.getLongitude()),
						Config.WEATHER_APP_ID);
				DataParser parser = new DataParser();
				dataWeather = parser.getDataWeather(weatherUrl);
			}
		});
		task.execute();
	}
	
	
	@SuppressLint("DefaultLocale")
	private void updateView() {
		TextView tvFarenheit = (TextView) viewInflate.findViewById(comcom.projects.storefinder.R.id.tvFarenheit);
		TextView tvCelsius = (TextView) viewInflate.findViewById(comcom.projects.storefinder.R.id.tvCelsius);
		TextView tvAddress = (TextView) viewInflate.findViewById(comcom.projects.storefinder.R.id.tvAddress);
		TextView tvDescription = (TextView) viewInflate.findViewById(comcom.projects.storefinder.R.id.tvDescription);
		
		tvFarenheit.setText(comcom.projects.storefinder.R.string.weather_placeholder);
		tvCelsius.setText(comcom.projects.storefinder.R.string.weather_placeholder);
		tvAddress.setText(comcom.projects.storefinder.R.string.weather_placeholder);
		tvDescription.setText(comcom.projects.storefinder.R.string.weather_placeholder);

		if(dataWeather == null)
			return;
		
		if(dataWeather.getMain() != null) {
			
			double kelvin = dataWeather.getMain().getTemp();
			double celsius = kelvin - 273.15;
			double fahrenheit = (celsius * 1.8) + 32 ;
			
			String farenheitStr = String.format("%.2f %s",
					fahrenheit, 
					MGUtilities.getStringFromResource(getActivity(), comcom.projects.storefinder.R.string.fahrenheit));
			
			String celsiusStr = String.format("%.2f %s",
					celsius, 
					MGUtilities.getStringFromResource(getActivity(), comcom.projects.storefinder.R.string.celsius));
			
			tvFarenheit.setText(farenheitStr);
			tvCelsius.setText(celsiusStr);
		}

		if(dataWeather.getWeather() != null && dataWeather.getWeather().size() > 0) {
			Weather weather = dataWeather.getWeather().get(0);
			tvDescription.setText(weather.getDescription());
		}
		
		if(StoreFinderApplication.address != null && StoreFinderApplication.address.size() > 0) {
			Address address = StoreFinderApplication.address.get(0);
			String locality = address.getLocality();
			String countryName = address.getCountryName();
			String addressStr = String.format("%s, %s", locality, countryName);
			tvAddress.setText(addressStr);
		}
	}
}
