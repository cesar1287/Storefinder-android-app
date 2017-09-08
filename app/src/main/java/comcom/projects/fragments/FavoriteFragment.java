package comcom.projects.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import comcom.application.StoreFinderApplication;
import comcom.config.Config;
import comcom.config.UIConfig;
import comcom.db.Queries;
import comcom.libraries.adapters.MGRecyclerAdapter;
import comcom.libraries.imageview.MGImageView;
import comcom.libraries.utilities.MGUtilities;
import comcom.models.Photo;
import comcom.models.Store;
import comcom.projects.activities.DetailActivity;
import comcom.projects.storefinder.MainActivity;
import comcom.projects.storefinder.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class FavoriteFragment extends Fragment {
	
	private View viewInflate;
	private ArrayList<Store> arrayData;

	SwipeRefreshLayout swipeRefresh;
	RecyclerView mRecyclerView;
	RecyclerView.LayoutManager mLayoutManager;
	Queries q;
	
	public FavoriteFragment() { }
	
	@SuppressLint("InflateParams")
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

		showRefresh(true);
		Handler h = new Handler();
		h.postDelayed(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				arrayData = q.getStoresFavorites();
				showList();
			}
		}, Config.DELAY_SHOW_ANIMATION);
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

		if(StoreFinderApplication.currentLocation != null && Config.RANK_STORES_ACCORDING_TO_NEARBY) {
			for(Store store : arrayData) {
				Location locStore = new Location("Store");
				locStore.setLatitude(store.getLat());
				locStore.setLongitude(store.getLon());
				double userDistanceFromStore = StoreFinderApplication.currentLocation.distanceTo(locStore) / 1000;
				store.setDistance(userDistanceFromStore);
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
					StoreFinderApplication.getImageLoaderInstance(
							getActivity()).displayImage(
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

				ImageView imgViewFeatured = (ImageView) v.view.findViewById(R.id.imgViewFeatured);
				imgViewFeatured.setVisibility(View.INVISIBLE);

				ImageView imgViewStarred = (ImageView) v.view.findViewById(R.id.imgViewStarred);
				imgViewStarred.setVisibility(View.VISIBLE);

				if(store.getFeatured() == 1)
					imgViewFeatured.setVisibility(View.VISIBLE);

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
}
