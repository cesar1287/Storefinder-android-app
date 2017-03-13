package com.projects.activities;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.application.StoreFinderApplication;
import com.config.Config;
import com.config.UIConfig;
import com.db.Queries;
import com.libraries.adapters.MGListAdapter;
import com.libraries.adapters.MGListAdapter.OnMGListAdapterAdapterListener;
import com.libraries.adapters.MGRecyclerAdapter;
import com.libraries.asynctask.MGAsyncTask;
import com.libraries.asynctask.MGAsyncTask.OnMGAsyncTaskListener;
import com.libraries.asynctask.MGAsyncTaskNoDialog;
import com.libraries.dataparser.DataParser;
import com.libraries.helpers.DateTimeHelper;
import com.libraries.imageview.RoundedImageView;
import com.libraries.usersession.UserAccessSession;
import com.libraries.usersession.UserSession;
import com.libraries.utilities.MGUtilities;
import com.models.Category;
import com.models.ResponseReview;
import com.models.Review;
import com.models.Store;
import com.projects.storefinder.R;

public class ReviewActivity extends AppCompatActivity implements OnItemClickListener {

	private Store store;
	private int reviewCount;
	private ResponseReview response;
	private int NEW_REVIEW_REQUEST_CODE = 9901;
	MGAsyncTaskNoDialog task;
	SwipeRefreshLayout swipeRefresh;
	RecyclerView mRecyclerView;
	RecyclerView.LayoutManager mLayoutManager;
	Queries q;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		setContentView(R.layout.fragment_list_swipe);
		setTitle(R.string.store_reviews);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		store = (Store) this.getIntent().getSerializableExtra("store");
		reviewCount = Config.MAX_REVIEW_COUNT_PER_LISTING;
		response = (ResponseReview) this.getIntent().getSerializableExtra("response");

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

		Handler h = new Handler();
		h.postDelayed(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				if(response == null) {
					getReviews();
				}
				else {
					showList();
				}
			}
		}, Config.DELAY_SHOW_ANIMATION);
		showRefresh(true);
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
	        case R.id.menuNewReview:
	        	newReview();
	            return true;
	        default:
	        	finish();	
	            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_reviews, menu);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        // if nav drawer is opened, hide the action items
        return super.onPrepareOptionsMenu(menu);
    }
    
    private void newReview() {
    	UserAccessSession userAccess = UserAccessSession.getInstance(ReviewActivity.this);
		UserSession userSession = userAccess.getUserSession();
		if(userSession == null) {
			MGUtilities.showAlertView(ReviewActivity.this, R.string.login_error, R.string.login_error_review);
			return;
		}
		Intent i = new Intent(this, NewReviewActivity.class);
		i.putExtra("store", store);
		startActivityForResult(i, NEW_REVIEW_REQUEST_CODE);
    }
	
	public void getReviews() {
		if(!MGUtilities.hasConnection(this)) {
			MGUtilities.showAlertView(
					this, 
					R.string.network_error,
					R.string.no_network_connection);
			showRefresh(false);
			return;
		}
		
        task = new MGAsyncTaskNoDialog(ReviewActivity.this);
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
				showList();
				showRefresh(false);
			}
			
			@Override
			public void onAsyncTaskDoInBackground(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
				parseReviews();
			}
		});
        task.execute();
	}
	
	@SuppressLint("DefaultLocale")
	public void parseReviews() {
		String reviewUrl = String.format("%s?count=%d&store_id=%s",
				Config.REVIEWS_URL, reviewCount, store.getStore_id());

        response = DataParser.getJSONFromUrlReview(reviewUrl, null);
        if(response != null) {
        	if(response.getReturn_count() < response.getTotal_row_count()) {
                if(response.getReviews() != null) {
                	Review review = new Review();
                	review.setReview_id(-1);
                	response.getReviews().add(0, review);
                }
            }
        }
	}

	private void showList() {
		if(response.getReviews() == null)
			return;

		MGRecyclerAdapter adapter = new MGRecyclerAdapter(response.getReviews().size(), R.layout.review_entry);
		adapter.setOnMGRecyclerAdapterListener(new MGRecyclerAdapter.OnMGRecyclerAdapterListener() {

			@Override
			public void onMGRecyclerAdapterCreated(MGRecyclerAdapter adapter, MGRecyclerAdapter.ViewHolder v, int position) {
				final Review review = response.getReviews().get(position);
				LinearLayout linearLoadMore = (LinearLayout) v.view.findViewById(R.id.linearLoadMore);
				LinearLayout linearMain = (LinearLayout) v.view.findViewById(R.id.linearMain);
				linearLoadMore.setVisibility(View.VISIBLE);
				linearMain.setVisibility(View.VISIBLE);
				if(review.getReview_id() > 0) {
					linearLoadMore.setVisibility(View.GONE);
					Spanned details1 = Html.fromHtml(review.getReview());
					Spanned details2 = Html.fromHtml(details1.toString());
//					String reviewString = URLDecoder.decode(details2.toString());
					String reviewString = details2.toString();
					Spanned title = Html.fromHtml(review.getFull_name());
					Log.e("Review", reviewString);
					TextView tvTitle = (TextView) v.view.findViewById(R.id.tvTitle);
					tvTitle.setText(title);

					TextView tvDetails = (TextView) v.view.findViewById(R.id.tvDetails);
					tvDetails.setText(reviewString);

					RoundedImageView imgViewPhoto = (RoundedImageView) v.view.findViewById(R.id.imgViewThumb);
					imgViewPhoto.setCornerRadius(R.dimen.corner_radius_review);
					imgViewPhoto.setBorderWidth(UIConfig.BORDER_WIDTH);
					imgViewPhoto.setBorderColor(getResources().getColor(UIConfig.THEME_BLACK_COLOR));

					if(review.getThumb_url() != null) {
						StoreFinderApplication.getImageLoaderInstance(ReviewActivity.this).displayImage(
								review.getThumb_url(),
								imgViewPhoto,
								StoreFinderApplication.getDisplayImageOptionsThumbInstance());
					}

					String date = DateTimeHelper.getStringDateFromTimeStamp(review.getCreated_at(), "MM/dd/yyyy hh:mm a");
					TextView tvDatePosted = (TextView) v.view.findViewById(R.id.tvDatePosted);
					tvDatePosted.setText(date);
				}
				else if(review.getReview_id() == -1) {
					linearMain.setVisibility(View.GONE);
					int remaining = response.getTotal_row_count() - response.getReturn_count();
					String str = String.format("%s %d %s",
							MGUtilities.getStringFromResource(ReviewActivity.this, R.string.view),
							remaining,
							MGUtilities.getStringFromResource(ReviewActivity.this, R.string.comments));

					TextView tvTitle = (TextView) v.view.findViewById(R.id.tvLoadMore);
					tvTitle.setText(str);
				}
			}

		});
		mRecyclerView.setAdapter(adapter);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	    if (requestCode == NEW_REVIEW_REQUEST_CODE) {
	        if(resultCode == Activity.RESULT_OK) {
	        	getReviews();
	        }
	        else if (resultCode == Activity.RESULT_CANCELED) {
	            //Write your code if there's no result
	        }
	    }
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View v, int pos, long resid) {
		// TODO Auto-generated method stub
		if(!MGUtilities.hasConnection(this)) {
			MGUtilities.showAlertView(
					this, 
					R.string.network_error,
					R.string.no_network_connection);
			return;
		}
		Review review = response.getReviews().get(pos);
		if(review.getReview_id() == -1 ) {
			reviewCount += Config.MAX_REVIEW_COUNT_PER_LISTING;
			getReviews();
		}
	}
}
