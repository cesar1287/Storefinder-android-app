package com.projects.activities;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.config.Config;
import com.libraries.utilities.MGUtilities;
import com.models.News;
import com.projects.storefinder.R;

public class NewsDetailActivity extends AppCompatActivity implements OnClickListener {

	private News news;
	private WebView mWebview;
	SwipeRefreshLayout swipeRefresh;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		setContentView(R.layout.activity_news_detail);
        setTitle(R.string.news_detail);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		news = (News)this.getIntent().getSerializableExtra("news");
		mWebview = (WebView) findViewById(R.id.webView);

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
		
		ImageView imgWebBack = (ImageView) findViewById(R.id.imgWebBack);
		imgWebBack.setOnClickListener(this);
		
		ImageView imgWebForward = (ImageView) findViewById(R.id.imgWebForward);
		imgWebForward.setOnClickListener(this);
		
		ImageView imgWebRefresh = (ImageView) findViewById(R.id.imgWebRefresh);
		imgWebRefresh.setOnClickListener(this);
		
		showRefresh(true);
		Handler h = new Handler();
		h.postDelayed(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				loadWebView();
			}
		}, Config.DELAY_SHOW_ANIMATION);
	}

	public void showRefresh(boolean show) {
		swipeRefresh.setRefreshing(show);
		swipeRefresh.setEnabled(show);
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch(v.getId()) {
			case R.id.imgWebBack:
				if(mWebview.canGoBack())
					mWebview.goBack();
				break;
			case R.id.imgWebForward:
				if(mWebview.canGoForward())
					mWebview.goForward();
				break;
			case R.id.imgWebRefresh:
				loadWebView();
				break;
		}
	}
	
	@SuppressLint("SetJavaScriptEnabled")
	private void loadWebView() {
		if(!MGUtilities.hasConnection(this)) {
			MGUtilities.showAlertView(
					this, 
					R.string.network_error,
					R.string.no_network_connection);
			showRefresh(false);
			return;
		}
		
		String strUrl = news.getNews_url();
		if(!news.getNews_url().contains("http")) {
			strUrl = "http://" + news.getNews_url();
		}

		mWebview.getSettings().setJavaScriptEnabled(true);
        mWebview.setWebViewClient(new WebViewClient() {
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Toast.makeText(NewsDetailActivity.this, description, Toast.LENGTH_SHORT).show();
            }

			@Override
			public void onPageFinished(WebView view, String url) {
				// TODO Auto-generated method stub
				super.onPageFinished(view, url);
				showRefresh(false);
			}
        });
        mWebview.loadUrl(strUrl);
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

}
