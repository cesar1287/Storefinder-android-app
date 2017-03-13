package com.projects.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.application.StoreFinderApplication;
import com.config.Config;
import com.config.UIConfig;
import com.db.DbHelper;
import com.db.Queries;
import com.facebook.FacebookException;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.widget.ShareDialog;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.SnapshotReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.libraries.asynctask.MGAsyncTask;
import com.libraries.asynctask.MGAsyncTask.OnMGAsyncTaskListener;
import com.libraries.asynctask.MGAsyncTaskNoDialog;
import com.libraries.dataparser.DataParser;
import com.libraries.twitter.TwitterApp;
import com.libraries.twitter.TwitterApp.TwitterAppListener;
import com.libraries.usersession.UserAccessSession;
import com.libraries.usersession.UserSession;
import com.libraries.utilities.MGUtilities;
import com.models.Favorite;
import com.models.Photo;
import com.models.Rating;
import com.models.ResponseRating;
import com.models.ResponseStore;
import com.models.Store;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.projects.storefinder.MainActivity;
import com.projects.storefinder.R;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.w3c.dom.Text;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import twitter4j.auth.AccessToken;

public class DetailActivity extends AppCompatActivity implements OnClickListener, GoogleMap.OnMapLoadedCallback {

    private Store store;
    private ArrayList<Photo> photoList;
    private ResponseStore responseStore;
    private ResponseRating responseRating;
    boolean canRate = true;
    private SupportMapFragment mapFragment;
    private GoogleMap googleMap;
    private Queries q;
    private boolean isUserCanRate = false;
    MGAsyncTaskNoDialog taskRate;
    MGAsyncTask task;
    SwipeRefreshLayout swipeRefresh;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        setContentView(R.layout.activity_detail);
        setTitle(R.string.store_details);

        q = StoreFinderApplication.getQueriesInstance(this);

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
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(R.string.store_details);
        store = (Store) this.getIntent().getSerializableExtra("store");
        updateStore();

        UserAccessSession userAccess = UserAccessSession.getInstance(this);
        UserSession userSession = userAccess.getUserSession();
        if(userSession != null) {
            checkUserCanRate();
            isUserCanRate = true;
        }
    }

    public void showRefresh(boolean show) {
        swipeRefresh.setRefreshing(show);
        swipeRefresh.setEnabled(show);
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        if(task != null)
            task.cancel(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // toggle nav drawer on selecting action bar app icon/title
        // Handle action bar actions click
        switch (item.getItemId()) {
            case R.id.menuReview:
                Intent i = new Intent(this, ReviewActivity.class);
                i.putExtra("store", store);
                startActivity(i);
                return true;
            default:
                finish();
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_details, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        // if nav drawer is opened, hide the action items
        return super.onPrepareOptionsMenu(menu);
    }

    @SuppressLint("InflateParams")
    public void showRatingDialog() {
        if(!MGUtilities.hasConnection(this)) {
            MGUtilities.showAlertView(
                    this,
                    R.string.network_error,
                    R.string.no_network_connection);
            return;
        }

        UserAccessSession userAccess = UserAccessSession.getInstance(this);
        UserSession userSession = userAccess.getUserSession();
        if(userSession == null) {
            MGUtilities.showAlertView(
                    this,
                    R.string.login_error,
                    R.string.login_error_rating);
            return;
        }

        if(!canRate) {
            MGUtilities.showAlertView(
                    this,
                    R.string.rating_error,
                    R.string.rating_error_finish);
            return;
        }

        if(responseRating != null && responseRating.getStore_rating() != null) {
            Rating rating = responseRating.getStore_rating();
            if(rating.getCan_rate() == -1) {
                MGUtilities.showAlertView(
                        this,
                        R.string.rating_error,
                        R.string.rating_error_finish);
                return;
            }
        }
        else {
            MGUtilities.showAlertView(
                    this,
                    R.string.rating_error,
                    R.string.rating_error_something_wrong);
            return;
        }

        LayoutInflater li = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View v = li.inflate(R.layout.rating_dialog, null);
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(this.getResources().getString(R.string.rate_store));
        alert.setView(v);
        alert.setPositiveButton(this.getResources().getString(R.string.rate),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO Auto-generated method stub
                        rateStore(v);
                    }
                });

        alert.setNegativeButton(this.getResources().getString(R.string.cancel),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO Auto-generated method stub
                    }
                });
        alert.create();
        alert.show();
    }

    public void rateStore(final View v) {
        if(!MGUtilities.hasConnection(this)) {
            MGUtilities.showAlertView(
                    this,
                    R.string.network_error,
                    R.string.no_network_connection);
            return;
        }

        task = new MGAsyncTask(this);
        task.setMGAsyncTaskListener(new OnMGAsyncTaskListener() {

            @Override
            public void onAsyncTaskProgressUpdate(MGAsyncTask asyncTask) { }

            @Override
            public void onAsyncTaskPreExecute(MGAsyncTask asyncTask) { }

            @Override
            public void onAsyncTaskPostExecute(MGAsyncTask asyncTask) {
                // TODO Auto-generated method stub
                updateStore();
            }

            @Override
            public void onAsyncTaskDoInBackground(MGAsyncTask asyncTask) {
                // TODO Auto-generated method stub
                syncRating(v);
            }
        });
        task.execute();
    }

    public void syncRating(View v) {
        RatingBar ratingBar = (RatingBar)v.findViewById(R.id.ratingBar);
        int rating = (int) ratingBar.getRating();

        UserAccessSession userAccess = UserAccessSession.getInstance(this);
        UserSession userSession = userAccess.getUserSession();

        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("rating", String.valueOf(rating) ));
        params.add(new BasicNameValuePair("store_id", String.valueOf(store.getStore_id()) ));
        params.add(new BasicNameValuePair("user_id", String.valueOf(userSession.getUser_id()) ));
        params.add(new BasicNameValuePair("login_hash", userSession.getLogin_hash()));
        responseStore = DataParser.getJSONFromUrlStore(Config.POST_RATING_URL, params);
        if(responseStore != null && responseStore.getStore() != null) {
            q.updateStore(responseStore.getStore());
            store = responseStore.getStore();
            canRate = false;
        }
    }

    public void checkUserCanRate() {
        if(!MGUtilities.hasConnection(this)) {
            showRefresh(false);
            return;
        }

        showRefresh(true);
        taskRate = new MGAsyncTaskNoDialog(this);
        taskRate.setMGAsyncTaskListener(new MGAsyncTaskNoDialog.OnMGAsyncTaskListenerNoDialog() {

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
            }

            @Override
            public void onAsyncTaskDoInBackground(MGAsyncTaskNoDialog asyncTask) {
                // TODO Auto-generated method stub
                UserAccessSession userAccess = UserAccessSession.getInstance(DetailActivity.this);
                UserSession userSession = userAccess.getUserSession();

                ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair("store_id", String.valueOf(store.getStore_id())));
                params.add(new BasicNameValuePair("user_id", String.valueOf(userSession.getUser_id())));
                params.add(new BasicNameValuePair("login_hash", userSession.getLogin_hash()));
                responseRating = DataParser.getJSONFromUrlRating(Config.GET_RATING_USER_URL, params);
            }
        });
        taskRate.execute();
    }

    private void setMap() {
        mapFragment = new SupportMapFragment();
        FragmentTransaction fragmentTransaction = this.getSupportFragmentManager().beginTransaction();
        fragmentTransaction.add(R.id.googleMapContainer, mapFragment);
        fragmentTransaction.commit();

        Handler h = new Handler();
        h.postDelayed(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                googleMap = mapFragment.getMap();
                if(googleMap != null)
                    googleMap.setOnMapLoadedCallback(DetailActivity.this);
            }
        }, 300);
    }

    @Override
    public void onMapLoaded() {
        if(!isUserCanRate)
            showRefresh(false);

        googleMap = mapFragment.getMap();
        googleMap.getUiSettings().setAllGesturesEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setScrollGesturesEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(false);
        googleMap.getUiSettings().setZoomControlsEnabled(false);

        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.title( Html.fromHtml(store.getStore_name()).toString() );

        Spanned storeAddress = Html.fromHtml(store.getStore_address());
        String address = storeAddress.toString();
        if(storeAddress.length() > 50)
            address = storeAddress.toString().substring(0,  50) + "...";

        markerOptions.snippet(address);
        markerOptions.position(new LatLng(store.getLat(), store.getLon()));
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.mipmap.map_pin_orange));

        Marker mark = googleMap.addMarker(markerOptions);
        mark.setInfoWindowAnchor(0.25f, 0);
        mark.showInfoWindow();

        CameraUpdate zoom = CameraUpdateFactory.zoomTo(Config.MAP_ZOOM_LEVEL);
        googleMap.moveCamera(zoom);
        CameraUpdate center = CameraUpdateFactory.newLatLng(
                new LatLng(store.getLat() + 0.0035, store.getLon()));

        googleMap.moveCamera(center);

        Handler h = new Handler();
        h.postDelayed(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                final ImageView imgViewMap = (ImageView) findViewById(R.id.imgViewMap);
                googleMap.snapshot(new SnapshotReadyCallback() {

                    @Override
                    public void onSnapshotReady(Bitmap snapshot) {
                        // TODO Auto-generated method stub
                        imgViewMap.setImageBitmap(snapshot);
                        FragmentTransaction fragmentTransaction =
                                DetailActivity.this.getSupportFragmentManager().beginTransaction();

                        fragmentTransaction.detach(mapFragment);
                        fragmentTransaction.commit();
                        showRefresh(false);
                    }
                });

            }
        }, 1500);
    }

    private void updateStore() {
        Photo p = q.getPhotoByStoreId(store.getStore_id());
        photoList = q.getPhotosByStoreId(store.getStore_id());
        ImageView imgViewPhoto = (ImageView) findViewById(R.id.imgViewPhoto);
        if(p != null) {
            StoreFinderApplication.getImageLoaderInstance(this)
                    .displayImage(
                            p.getPhoto_url(),
                            imgViewPhoto,
                            StoreFinderApplication.getDisplayImageOptionsInstance());
        }

        TextView tvTitle = (TextView) findViewById(R.id.tvTitle);
        TextView tvSubtitle = (TextView) findViewById(R.id.tvSubtitle);
        RatingBar ratingBar = (RatingBar) findViewById(R.id.ratingBar);
        TextView tvRatingBarInfo = (TextView) findViewById(R.id.tvRatingBarInfo);

        ImageView imgViewGallery = (ImageView) findViewById(R.id.imgViewGallery);
        imgViewGallery.setOnClickListener(this);

        TextView tvGalleryCount = (TextView) findViewById(R.id.tvGalleryCount);

        TextView btnRateIt = (TextView) findViewById(R.id.btnRateIt);
        btnRateIt.setOnClickListener(this);

        TextView tvDetails = (TextView) findViewById(R.id.tvDetails);

        ImageView imgViewCall = (ImageView) findViewById(R.id.imgViewCall);
        imgViewCall.setOnClickListener(this);

        ImageView imgViewEmail = (ImageView) findViewById(R.id.imgViewEmail);
        imgViewEmail.setOnClickListener(this);

        ImageView imgViewRoute = (ImageView) findViewById(R.id.imgViewRoute);
        imgViewRoute.setOnClickListener(this);

        Button imgViewShareFb = (Button) findViewById(R.id.imgViewShareFb);
        imgViewShareFb.setOnClickListener(this);

        Button imgViewShareTwitter = (Button) findViewById(R.id.imgViewShareTwitter);
        imgViewShareTwitter.setOnClickListener(this);

        ImageView imgViewSMS = (ImageView) findViewById(R.id.imgViewSMS);
        imgViewSMS.setOnClickListener(this);

        ImageView imgViewWebsite = (ImageView) findViewById(R.id.imgViewWebsite);
        imgViewWebsite.setOnClickListener(this);

        ToggleButton toggleButtonFave = (ToggleButton) findViewById(R.id.toggleButtonFave);
        toggleButtonFave.setOnClickListener(this);

        imgViewCall.setEnabled(true);
        imgViewRoute.setEnabled(true);
        imgViewEmail.setEnabled(true);
        imgViewSMS.setEnabled(true);
        imgViewWebsite.setEnabled(true);

        if( store.getPhone_no() == null || store.getPhone_no().trim().length() == 0 )
            imgViewCall.setEnabled(false);

        if(store.getLat() == 0 || store.getLon() == 0)
            imgViewRoute.setEnabled(false);

        if(store.getEmail() == null || store.getEmail().trim().length() == 0)
            imgViewEmail.setEnabled(false);

        if( store.getSms_no() == null || store.getSms_no().trim().length() == 0 )
            imgViewSMS.setEnabled(false);

        if(store.getWebsite() == null || store.getWebsite().trim().length() == 0)
            imgViewWebsite.setEnabled(false);


        Favorite fave = q.getFavoriteByStoreId(store.getStore_id());
        toggleButtonFave.setChecked(true);

        if(fave == null)
            toggleButtonFave.setChecked(false);

        // SETTING VALUES
        float rating = 0;

        if(store.getRating_total() > 0 && store.getRating_count() > 0)
            rating = store.getRating_total() / store.getRating_count();

        String strRating = String.format("%.2f %s %d %s",
                rating,
                this.getResources().getString(R.string.average_based_on),
                store.getRating_count(),
                this.getResources().getString(R.string.rating));

        Spanned name = Html.fromHtml(store.getStore_name());
        name = Html.fromHtml(name.toString());

        Spanned address = Html.fromHtml(store.getStore_address());
        address = Html.fromHtml(address.toString());

        tvTitle.setText(name.toString());
        tvSubtitle.setText(address.toString());

        ratingBar.setRating(rating);
        tvRatingBarInfo.setText(strRating);

        String strDesc = store.getStore_desc().replace("\\n", "[{~}]");
        strDesc = strDesc.replace("&quot;", "\"");
        Spanned details = Html.fromHtml(strDesc);
        details = Html.fromHtml(details.toString());
        strDesc = details.toString().replace("[{~}]", "\n");

        tvDetails.setText(strDesc);
        tvGalleryCount.setText("" + photoList.size());

        ImageView imgViewFeatured = (ImageView) findViewById(R.id.imgViewFeatured);
        imgViewFeatured.setVisibility(View.VISIBLE);
        if(store.getFeatured() == 0)
            imgViewFeatured.setVisibility(View.INVISIBLE);

        Handler h = new Handler();
        h.postDelayed(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                setMap();
            }
        }, Config.DELAY_SHOW_ANIMATION + 300);
    }

    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        switch(v.getId()) {
            case R.id.btnRateIt:
                showRatingDialog();
                break;
            case R.id.imgViewGallery:
                if(photoList != null && photoList.size() > 0) {
                    Intent i = new Intent(this, ImageViewerActivity.class);
                    i.putExtra("photoList", photoList);
                    startActivity(i);
                }
                else {
                    MGUtilities.showAlertView(
                            this,
                            R.string.action_error,
                            R.string.no_image_to_display);
                }
                break;
            case R.id.imgViewCall:
                call();
                break;
            case R.id.imgViewEmail:
                email();
                break;
            case R.id.imgViewRoute:
                route();
                break;
            case R.id.imgViewShareFb:
                shareFB();
                break;
            case R.id.imgViewShareTwitter:
                shareTwitter();
                break;
            case R.id.imgViewSMS:
                sms();
                break;
            case R.id.imgViewWebsite:
                website();
                break;
            case R.id.toggleButtonFave:
                checkFave(v);
                break;
        }
    }

    private void checkFave(View view) {
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

    private void call() {
        if( store.getPhone_no() == null || store.getPhone_no().length() == 0 ) {
            MGUtilities.showAlertView(
                    this,
                    R.string.action_error,
                    R.string.cannot_proceed);
            return;
        }
        PackageManager pm = this.getBaseContext().getPackageManager();
        boolean canCall = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        if(!canCall) {
            MGUtilities.showAlertView(
                    this,
                    R.string.action_error,
                    R.string.cannot_proceed);
            return;
        }

        checkPermissionCall(Config.REQUEST_CODE_PHONE_CALL);
    }

    private void route() {
        if(store.getLat() == 0 || store.getLon() == 0) {
            MGUtilities.showAlertView(
                    this,
                    R.string.action_error,
                    R.string.cannot_proceed);
            return;
        }

        String geo = String.format("http://maps.google.com/maps?f=d&daddr=%s&dirflg=d", store.getStore_address());
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(geo));
        intent.setComponent(new ComponentName("com.google.android.apps.maps", "com.google.android.maps.MapsActivity"));
        this.startActivity(intent);
    }

    private void email() {
        if(store.getEmail() == null || store.getEmail().length() == 0) {
            MGUtilities.showAlertView(
                    this,
                    R.string.action_error,
                    R.string.cannot_proceed);
            return;
        }

        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{ store.getEmail() } );
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, MGUtilities.getStringFromResource(this, R.string.email_subject) );
        emailIntent.putExtra(Intent.EXTRA_TEXT, MGUtilities.getStringFromResource(this, R.string.email_body) );
        emailIntent.setType("message/rfc822");
        this.startActivity(Intent.createChooser(emailIntent,
                MGUtilities.getStringFromResource(this, R.string.choose_email_client)) );
    }

    private void sms() {
        if( store.getSms_no() == null || store.getSms_no().length() == 0 ) {
            MGUtilities.showAlertView(
                    this,
                    R.string.action_error,
                    R.string.handset_not_supported);
            return;
        }

        PackageManager pm = this.getBaseContext().getPackageManager();
        boolean canSMS = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        if(!canSMS) {
            MGUtilities.showAlertView(
                    this,
                    R.string.action_error,
                    R.string.handset_not_supported);
            return;
        }

        checkPermissionSMS(Config.PERMISSION_REQUEST_SEND_SMS);
    }

    private void website() {
        if(store.getWebsite() == null || store.getWebsite().length() == 0) {
            MGUtilities.showAlertView(
                    this,
                    R.string.action_error,
                    R.string.cannot_proceed);
            return;
        }
        String strUrl = store.getWebsite();
        if(!strUrl.contains("http")) {
            strUrl = "http://" + strUrl;
        }
        Intent webIntent = new Intent(Intent.ACTION_VIEW);
        webIntent.setData(Uri.parse(strUrl));
        this.startActivity(Intent.createChooser(webIntent,
                MGUtilities.getStringFromResource(this, R.string.choose_browser)));
    }

    // FACEBOOK
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStart()  {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void shareFB() {
//		Photo p = photos != null && photos.size() > 0 ? photos.get(0) : null;
        ShareDialog shareDialog = new ShareDialog(this);
        String desc = String.format("%s", store.getStore_name() );
        if (ShareDialog.canShow(ShareLinkContent.class)) {
            ShareLinkContent linkContent = new ShareLinkContent.Builder()
                    .setContentTitle(MGUtilities.getStringFromResource(this, R.string.app_name))
                    .setContentDescription(desc)
                    .setContentUrl(Uri.parse(Config.SERVER_URL_DEFAULT_PAGE_FOR_FACEBOOK))
                    .setQuote(MGUtilities.getStringFromResource(this, R.string.download_app))
                    .build();

            shareDialog.show(linkContent);
        }
    }

    private void shareTwitter() {
        String tweet = String.format("%s %s",
                MGUtilities.getStringFromResource(this, R.string.download_app),
                Config.SERVER_URL_DEFAULT_PAGE_FOR_TWITTER);

        Intent tweetIntent = new Intent();
        tweetIntent.setType("text/plain");
        tweetIntent.putExtra(Intent.EXTRA_TEXT, tweet);
        final PackageManager packageManager = getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(tweetIntent, PackageManager.MATCH_DEFAULT_ONLY);

        String p = null;
        for (ResolveInfo resolveInfo : list) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (packageName != null && packageName.startsWith("com.twitter.android")) {
                p = packageName;
                tweetIntent.setPackage(p);
                startActivity(tweetIntent);
                break;
            }
        }

        if(p == null) {
            MGUtilities.showAlertView(this, R.string.twitter_app_error, R.string.twitter_app_error_details);
        }
    }

    private void checkPermissionCall(int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE);
            if(permissionCheck == PackageManager.PERMISSION_DENIED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CALL_PHONE)) {
                    ActivityCompat.requestPermissions(
                            this,
                            new String[]{Manifest.permission.CALL_PHONE},
                            Config.REQUEST_CODE_PHONE_CALL);
                    return;
                } else {
                    ActivityCompat.requestPermissions(
                            this,
                            new String[]{Manifest.permission.CALL_PHONE},
                            Config.REQUEST_CODE_PHONE_CALL);

                    return;
                }
            }
        }

        if(requestCode == Config.REQUEST_CODE_PHONE_CALL) {
            String phoneNo = store.getPhone_no().replaceAll("[^0-9]", "");
            String uri = "tel:" + phoneNo;
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse(uri));
            startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,  String permissions[], int[] grantResults) {
        switch (requestCode) {
            case Config.REQUEST_CODE_PHONE_CALL: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    checkPermissionCall(requestCode);
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    MGUtilities.showAlertView(this, R.string.permission_error, R.string.grant_permission_call);
                }
                return;
            }
            case Config.PERMISSION_REQUEST_SEND_SMS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    checkPermissionSMS(requestCode);
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    MGUtilities.showAlertView(this, R.string.permission_error, R.string.grant_permission_sms);
                }
                return;
            }
        }
    }

    private void checkPermissionSMS(int requestCode) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS);
            if(permissionCheck == PackageManager.PERMISSION_DENIED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, Config.PERMISSION_REQUEST_SEND_SMS);
                    return;
                } else {
                    // No explanation needed, we can request the permission.
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, Config.PERMISSION_REQUEST_SEND_SMS);
                    return;
                }
            }

            String smsNo = store.getSms_no().replaceAll("[^0-9]", "");
            Uri uri = Uri.parse("smsto:" + smsNo);
            Intent it = new Intent(Intent.ACTION_SENDTO, uri);
            it.putExtra("sms_body", MGUtilities.getStringFromResource(this, R.string.sms_body));
            startActivity(it);
        }
        else {
            String smsNo = store.getSms_no().replaceAll("[^0-9]", "");
            Intent smsIntent = new Intent(Intent.ACTION_VIEW);
            smsIntent.setType("vnd.android-dir/mms-sms");
            smsIntent.putExtra("address", smsNo);
            smsIntent.putExtra("sms_body", MGUtilities.getStringFromResource(this, R.string.sms_body) );
            this.startActivity(smsIntent);
        }
    }
}
