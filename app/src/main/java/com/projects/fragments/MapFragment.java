package com.projects.fragments;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.application.StoreFinderApplication;
import com.config.Config;
import com.config.UIConfig;
import com.db.Queries;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.libraries.asynctask.MGAsyncTask;
import com.libraries.asynctask.MGAsyncTaskNoDialog;
import com.libraries.dataparser.DataParser;
import com.libraries.directions.GMapV2Direction;
import com.libraries.drawingview.DrawingView;
import com.libraries.imageview.MGHSquareImageView;
import com.libraries.location.MGLocationManagerUtils;
import com.libraries.sliding.MGSliding;
import com.libraries.usersession.UserAccessSession;
import com.libraries.utilities.MGUtilities;
import com.models.Category;
import com.models.Data;
import com.models.DataResponse;
import com.models.Favorite;
import com.models.Photo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import com.models.Store;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.projects.activities.DetailActivity;
import com.projects.storefinder.MainActivity;
import com.projects.storefinder.R;

import org.w3c.dom.Document;

/**
 * Created by mg on 27/07/16.
 */
public class MapFragment extends Fragment implements OnMapReadyCallback, GoogleMap.OnInfoWindowClickListener, GoogleMap.OnMapClickListener,
        View.OnClickListener, DrawingView.OnDrawingViewListener, StoreFinderApplication.OnLocationListener {

    private View viewInflate;
    private GoogleMap googleMap;
    SwipeRefreshLayout swipeRefresh;
    private Location myLocation;
    private HashMap<String, Store> markers;
    private ArrayList<Marker> markerList;
    private DisplayImageOptions options;
    private MGSliding frameSliding;
    private DrawingView drawingView;
    private GMapV2Direction gMapV2;
    private ArrayList<Store> storeList;
    private ArrayList<Store> selectedStoreList;
    private Store selectedStore;
    Queries q;
    MGAsyncTaskNoDialog task;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        viewInflate = inflater.inflate(R.layout.fragment_map, null);
        return viewInflate;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if(task != null)
            task.cancel(true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onViewCreated(view, savedInstanceState);
        swipeRefresh = (SwipeRefreshLayout) viewInflate.findViewById(R.id.swipe_refresh);
        swipeRefresh.setClickable(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            swipeRefresh.setProgressViewOffset(false, 0,100);
        }

        q = StoreFinderApplication.getQueriesInstance(getActivity());

        swipeRefresh.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        showRefresh(false);

        frameSliding = (MGSliding) viewInflate.findViewById(R.id.frameSliding);
        Animation animationIn = AnimationUtils.loadAnimation(this.getActivity(),
                R.anim.slide_up2);

        Animation animationOut = AnimationUtils.loadAnimation(this.getActivity(),
                R.anim.slide_down2);

        frameSliding.setInAnimation(animationIn);
        frameSliding.setOutAnimation(animationOut);
        frameSliding.setVisibility(View.GONE);

        Button btnDraw = (Button)viewInflate.findViewById(R.id.btnDraw);
        btnDraw.setOnClickListener(this);

        Button btnRefresh = (Button)viewInflate.findViewById(R.id.btnRefresh);
        btnRefresh.setOnClickListener(this);

        Button btnRoute = (Button)viewInflate.findViewById(R.id.btnRoute);
        btnRoute.setOnClickListener(this);

        Button btnCurrentLocation = (Button)viewInflate.findViewById(R.id.btnCurrentLocation);
        btnCurrentLocation.setOnClickListener(this);

        Button btnNearby = (Button)viewInflate.findViewById(R.id.btnNearby);
        btnNearby.setOnClickListener(this);

        showRefresh(true);

        markers = new HashMap<String, Store>();
        markerList = new ArrayList<Marker>();
        selectedStoreList = new ArrayList<Store>();

        FragmentManager fManager = getActivity().getSupportFragmentManager();
        SupportMapFragment supportMapFragment = ((SupportMapFragment) fManager.findFragmentById(R.id.googleMap));
        if(supportMapFragment == null) {
            fManager = getChildFragmentManager();
            supportMapFragment = ((SupportMapFragment) fManager.findFragmentById(R.id.googleMap));
        }
        supportMapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap _googleMap) {
        googleMap = _googleMap;
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        try {
            googleMap.setMyLocationEnabled(true);
        }
        catch (SecurityException e) { }

        googleMap.setOnMapClickListener(this);
        googleMap.setOnInfoWindowClickListener(this);
        googleMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {

            @Override
            public void onMyLocationChange(Location location) {
                // TODO Auto-generated method stub
                myLocation = location;
            }
        });
        googleMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                if(frameSliding.getVisibility() == View.VISIBLE)
                    frameSliding.setVisibility(View.INVISIBLE);
            }
        });

        gMapV2 = new GMapV2Direction();
        drawingView = (DrawingView) viewInflate.findViewById(R.id.drawingView);
        drawingView.setBrushSize(5);
        drawingView.setPolygonFillColor(getResources().getColor(R.color.theme_main_color_alpha_66));
        drawingView.setColor(getResources().getColor(R.color.theme_main_color));
        drawingView.setPolylineColor(getResources().getColor(R.color.theme_main_color));
        drawingView.setGoogleMap(googleMap);
        drawingView.setOnDrawingViewListener(this);

        showRefresh(false);
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

    @SuppressLint("DefaultLocale")
    @Override
    public void onInfoWindowClick(Marker marker) {
        // TODO Auto-generated method stub
        final Store store = markers.get(marker.getId());
        selectedStore = store;
        if(myLocation != null) {
            Location loc = new Location("marker");
            loc.setLatitude(marker.getPosition().latitude);
            loc.setLongitude(marker.getPosition().longitude);

            double meters = myLocation.distanceTo(loc);
            double miles = meters * 0.000621371f;
            String str = String.format("%.1f %s",
                    miles,
                    MGUtilities.getStringFromResource(getActivity(), R.string.mi));

            TextView tvDistance = (TextView) viewInflate.findViewById(R.id.tvDistance);
            tvDistance.setText(str);
        }

        frameSliding.setVisibility(View.VISIBLE);
        ImageView imgViewThumb = (ImageView) viewInflate.findViewById(R.id.imgViewThumb);
        Photo p = q.getPhotoByStoreId(store.getStore_id());
        if(p != null) {
            StoreFinderApplication.getImageLoaderInstance(getActivity()).displayImage(p.getPhoto_url(), imgViewThumb, options);
        }
        else {
            imgViewThumb.setImageResource(UIConfig.SLIDER_PLACEHOLDER);
        }

        imgViewThumb.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent i = new Intent(getActivity(), DetailActivity.class);
                i.putExtra("store", store);
                getActivity().startActivity(i);
            }
        });

        TextView tvTitle = (TextView) viewInflate.findViewById(R.id.tvTitle);
        TextView tvSubtitle = (TextView) viewInflate.findViewById(R.id.tvSubtitle);

        tvTitle.setText(Html.fromHtml(store.getStore_name()));
        tvSubtitle.setText(Html.fromHtml(store.getStore_address()));

        ToggleButton toggleButtonFave = (ToggleButton) viewInflate.findViewById(R.id.toggleButtonFave);
        toggleButtonFave.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                checkFave(v, store);
            }
        });

        Favorite fave = q.getFavoriteByStoreId(store.getStore_id());
        toggleButtonFave.setChecked(true);
        if(fave == null)
            toggleButtonFave.setChecked(false);
    }

    @Override
    public void onMapClick(LatLng point) {
        // TODO Auto-generated method stub
        frameSliding.setVisibility(View.INVISIBLE);
    }

    private void checkFave(View view, Store store) {
        Favorite fave = q.getFavoriteByStoreId(store.getStore_id());
        if(fave != null) {
            q.deleteFavorite(store.getStore_id());
            ((ToggleButton) view).setChecked(false);
        }
        else {
            fave = new Favorite();
            fave.setStore_id(store.getStore_id());
            q.insertFavorite(fave);
            ((ToggleButton) view).setChecked(true);
        }
    }

    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        switch(v.getId()) {
            case R.id.btnDraw:
                drawingView.enableDrawing(true);
                drawingView.startDrawingPolygon(true);
                break;
            case R.id.btnRefresh:
                addStoreMarkers();
                break;
            case R.id.btnRoute:
                getDirections();
                break;
            case R.id.btnCurrentLocation:
                getMyLocation();
                break;
            case R.id.btnNearby:
                getNearby();
                break;
        }
    }

    ArrayList<Marker> markers1;

    @SuppressLint("DefaultLocale")
    @Override
    public void onUserDidFinishDrawPolygon(PolygonOptions polygonOptions) {
        // TODO Auto-generated method stub
        googleMap.clear();
        googleMap.addPolygon( polygonOptions );
        markers1 = getMarkersInsidePoly(polygonOptions, null, markerList);
        markers = new HashMap<String, Store>();
        markerList = new ArrayList<Marker>();
        selectedStoreList = new ArrayList<Store>();
        markerList.clear();
        markers.clear();
        for(Marker mark1 : markers1) {
            for(Store entry : storeList) {
                if(mark1.getTitle().toLowerCase().compareTo(entry.getStore_name().toLowerCase()) == 0) {
                    Marker mark = createMarker(entry);
                    markerList.add(mark);
                    markers.put(mark.getId(), entry);
                    selectedStoreList.add(entry);
                    break;
                }
            }
        }
        drawingView.enableDrawing(false);
        drawingView.resetPolygon();
        drawingView.startNew();
    }

    @Override
    public void onUserDidFinishDrawPolyline(PolylineOptions polylineOptions) { }

    public ArrayList<Marker> getMarkersInsidePoly(PolygonOptions polygonOptions,
                                                  PolylineOptions polylineOptions,  ArrayList<Marker> markers) {

        ArrayList<Marker> markersFound = new ArrayList<Marker>();
        for(Marker mark : markers) {
            Boolean isFound = polygonOptions != null ?
                    drawingView.latLongContainsInPolygon(mark.getPosition(), polygonOptions) :
                    drawingView.latLongContainsInPolyline(mark.getPosition(), polylineOptions);

            if(isFound) {
                markersFound.add(mark);
            }
        }
        return markersFound;
    }

    public void addStoreMarkers() {
        if(googleMap != null)
            googleMap.clear();

        try {
            storeList = getAllStores();
            markerList.clear();
            markers.clear();
            for(Store entry: storeList) {
                if(entry.getLat() == 0 || entry.getLon() == 0)
                    continue;

                Marker mark = createMarker(entry);
                markerList.add(mark);
                markers.put(mark.getId(), entry);
            }
            showBoundedMap();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    private ArrayList<Store> getAllStores() {
        UserAccessSession accessSession = UserAccessSession.getInstance(getActivity());
        float radius = accessSession.getFilterDistance();

        ArrayList<Store> arrayData1 = q.getStores();
        ArrayList<Store> arrayData = new ArrayList<Store>();
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

        return arrayData;
    }

    public void getDirections() {
        if(selectedStore == null) {
            Toast.makeText(getActivity(), R.string.select_one_store, Toast.LENGTH_SHORT).show();
            return;
        }

        MGAsyncTask asyncTask = new MGAsyncTask(getActivity());
        asyncTask.setMGAsyncTaskListener(new MGAsyncTask.OnMGAsyncTaskListener() {

            private ArrayList<ArrayList<LatLng>> allDirections;

            @Override
            public void onAsyncTaskProgressUpdate(MGAsyncTask asyncTask) { }

            @Override
            public void onAsyncTaskPreExecute(MGAsyncTask asyncTask) {
                // TODO Auto-generated method stub
                allDirections = new ArrayList<ArrayList<LatLng>>();
            }

            @Override
            public void onAsyncTaskPostExecute(MGAsyncTask asyncTask) {
                // TODO Auto-generated method stub
                for(ArrayList<LatLng> directions : allDirections) {
                    PolylineOptions rectLine = new PolylineOptions().width(3).color(Color.RED);
                    for(LatLng latLng : directions) {
                        rectLine.add(latLng);
                    }
                    googleMap.addPolyline(rectLine);
                }

                if(allDirections.size() <= 0) {
                    Toast.makeText(getActivity(), R.string.cannot_determine_direction, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAsyncTaskDoInBackground(MGAsyncTask asyncTask) {
                // TODO Auto-generated method stub
                if(myLocation != null && selectedStore != null) {
                    LatLng marker1 = new LatLng(myLocation.getLatitude(), myLocation.getLongitude());
                    LatLng marker2 = new LatLng(selectedStore.getLat(), selectedStore.getLon());

                    Document doc = gMapV2.getDocument1(
                            marker1, marker2, GMapV2Direction.MODE_DRIVING);

                    ArrayList<LatLng> directionPoint = gMapV2.getDirection(doc);

                    allDirections.add(directionPoint);
                }
            }
        });
        asyncTask.startAsyncTask();
    }

    private void getMyLocation() {
        if(myLocation == null) {
            MGUtilities.showAlertView(
                    getActivity(),
                    R.string.location_error,
                    R.string.cannot_determine_location);

            return;
        }

        addStoreMarkers();
        CameraUpdate zoom = CameraUpdateFactory.zoomTo(Config.MAP_ZOOM_LEVEL);
        googleMap.moveCamera(zoom);
        CameraUpdate center = CameraUpdateFactory.newLatLng(
                new LatLng(myLocation.getLatitude(), myLocation.getLongitude()));

        googleMap.animateCamera(center);
    }

    private void getNearby() {
        if(googleMap != null)
            googleMap.clear();

        if(myLocation == null) {
            MGUtilities.showAlertView(
                    getActivity(),
                    R.string.route_error,
                    R.string.route_error_details);
            return;
        }

        try {
            storeList = q.getStores();
            markerList.clear();
            markers.clear();
            for(Store entry: storeList) {
                Location destination = new Location("Origin");
                destination.setLatitude(entry.getLat());
                destination.setLongitude(entry.getLon());
                double distance = myLocation.distanceTo(destination);

                if(distance <= Config.MAX_RADIUS_NEARBY_IN_METERS) {
                    Marker mark = createMarker(entry);
                    markerList.add(mark);
                    markers.put(mark.getId(), entry);
                }
            }

            CameraUpdate zoom = CameraUpdateFactory.zoomTo(Config.MAP_ZOOM_LEVEL);
            googleMap.moveCamera(zoom);
            CameraUpdate center = CameraUpdateFactory.newLatLng(
                    new LatLng(myLocation.getLatitude(), myLocation.getLongitude()));

            googleMap.animateCamera(center);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void showBoundedMap() {
        if(markerList == null && markerList.size() == 0 ) {
            MGUtilities.showNotifier(this.getActivity(), MainActivity.offsetY, R.string.failed_data);
            return;
        }

        if(markerList.size() > 0) {
            LatLngBounds.Builder bld = new LatLngBounds.Builder();
            for (int i = 0; i < markerList.size(); i++) {
                Marker marker = markerList.get(i);
                bld.include(marker.getPosition());
            }

            LatLngBounds bounds = bld.build();
            googleMap.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds,
                            this.getResources().getDisplayMetrics().widthPixels,
                            this.getResources().getDisplayMetrics().heightPixels,
                            70));
        }
        else {
            MGUtilities.showNotifier(this.getActivity(), MainActivity.offsetY, R.string.no_results_found);
            Location loc = StoreFinderApplication.currentLocation;
            if(loc != null) {
                googleMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(new LatLng(loc.getLatitude(), loc.getLongitude()), 70));
            }
        }
    }

    private Marker createMarker(Store store) {
        final MarkerOptions markerOptions = new MarkerOptions();
        Spanned name = Html.fromHtml(store.getStore_name());
        name = Html.fromHtml(name.toString());
        Spanned storeAddress = Html.fromHtml(store.getStore_address());
        storeAddress = Html.fromHtml(storeAddress.toString());
        markerOptions.title( name.toString() );

        String address = storeAddress.toString();
        if(address.length() > 50)
            address = storeAddress.toString().substring(0,  50) + "...";

        markerOptions.snippet(address);
        markerOptions.position(new LatLng(store.getLat(), store.getLon()));
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.mipmap.map_pin_orange));

        Marker mark = googleMap.addMarker(markerOptions);
        mark.setInfoWindowAnchor(Config.MAP_INFO_WINDOW_X_OFFSET, 0);

        Category cat = q.getCategoryByCategoryId(store.getCategory_id());
        if(cat != null && cat.getCategory_icon() != null) {
            MGHSquareImageView imgView = new MGHSquareImageView(getActivity());
            imgView.setMarker(mark);
            imgView.setMarkerOptions(markerOptions);
            imgView.setTag(store);
            StoreFinderApplication.getImageLoaderInstance(getActivity()).displayImage(
                    cat.getCategory_icon(), imgView, options, new ImageLoadingListener() {

                        @Override
                        public void onLoadingStarted(String imageUri, View view) { }

                        @Override
                        public void onLoadingFailed(String imageUri, View view,
                                                    FailReason failReason) { }

                        @Override
                        public void onLoadingComplete(String imageUri, final View view, final Bitmap loadedImage) {
                            // TODO Auto-generated method stub
                            if(loadedImage != null) {
                                MGHSquareImageView v = (MGHSquareImageView)view;
                                Marker m = (Marker)v.getMarker();
                                m.remove();

                                MarkerOptions opt = (MarkerOptions)v.getMarkerOptions();
                                opt.icon(BitmapDescriptorFactory.fromBitmap(loadedImage));
                                Marker mark = googleMap.addMarker(opt);
                                Store s = (Store) v.getTag();

                                if(markers.containsKey(m.getId())) {
                                    markerList.remove(m);
                                    markerList.add(mark);
                                    markers.remove(m);
                                    markers.put(mark.getId(), s);
                                }
                                else {
                                    markers.put(mark.getId(), s);
                                }
                            }
                            else {
                                Log.e("LOADED IMAGE", "IS NULL");
                            }
                        }

                        @Override
                        public void onLoadingCancelled(String imageUri, View view) { }
                    });
        }

        return mark;
    }

    public void getData() {
        showRefresh(true);
        task = new MGAsyncTaskNoDialog(getActivity());
        task.setMGAsyncTaskListener(new MGAsyncTaskNoDialog.OnMGAsyncTaskListenerNoDialog() {

            @Override
            public void onAsyncTaskProgressUpdate(MGAsyncTaskNoDialog asyncTask) { }

            @Override
            public void onAsyncTaskPreExecute(MGAsyncTaskNoDialog asyncTask) { }

            @Override
            public void onAsyncTaskPostExecute(MGAsyncTaskNoDialog asyncTask) {
                // TODO Auto-generated method stub
                showRefresh(false);
                addStoreMarkers();
                showBoundedMap();
            }

            @Override
            public void onAsyncTaskDoInBackground(MGAsyncTaskNoDialog asyncTask) {
                // TODO Auto-generated method stub
                if(StoreFinderApplication.currentLocation!= null) {
                    try {
                        UserAccessSession accessSession = UserAccessSession.getInstance(getActivity());
                        float radius = accessSession.getFilterDistance();
                        if(radius == 0)
                            radius = Config.DEFAULT_FILTER_DISTANCE_IN_KM;

                        String strUrl = String.format("%s?api_key=%s&lat=%s&lon=%s&radius=%s&get_categories=1",
                                Config.GET_STORES_JSON_URL,
                                Config.API_KEY,
                                String.valueOf(StoreFinderApplication.currentLocation.getLatitude()),
                                String.valueOf(StoreFinderApplication.currentLocation.getLongitude()),
                                String.valueOf(radius));

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
            }
        });
        task.execute();
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
