package comcom.projects.activities;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import comcom.config.Config;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import comcom.libraries.asynctask.MGAsyncTask;
import comcom.libraries.dataparser.DataParser;
import comcom.libraries.twitter.TwitterApp;
import comcom.libraries.usersession.UserAccessSession;
import comcom.libraries.usersession.UserSession;
import comcom.libraries.utilities.MGUtilities;
import comcom.models.DataResponse;
import comcom.models.Status;
import comcom.models.User;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.auth.AccessToken;

public class LoginActivity extends AppCompatActivity implements OnClickListener {

	private TwitterApp mTwitter;
	MGAsyncTask task;
	private CallbackManager mCallbackManager;
	String _imageURL;
	String _name;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		setContentView(comcom.projects.storefinder.R.layout.activity_login);
		setTitle(comcom.projects.storefinder.R.string.login);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		Button btnLogin = (Button) this.findViewById(comcom.projects.storefinder.R.id.btnLogin);
		btnLogin.setOnClickListener(this);

		Button btnFacebook = (Button) this.findViewById(comcom.projects.storefinder.R.id.btnFacebook);
		btnFacebook.setOnClickListener(this);

		mTwitter = new TwitterApp(this, twitterAppListener);

		FacebookSdk.sdkInitialize(this.getApplicationContext());
		mCallbackManager = CallbackManager.Factory.create();

		LoginManager.getInstance().registerCallback(mCallbackManager,
				new FacebookCallback<LoginResult>() {
					@Override
					public void onSuccess(LoginResult loginResult) {
						Log.d("LoginManager", "Login Success");
						getUserProfile(loginResult);
					}

					@Override
					public void onCancel() {
						Log.d("LoginManager", "Login Cancel");
					}

					@Override
					public void onError(FacebookException exception) {
						Log.d("LoginManager", exception.getMessage());
					}
				});
	}

	private void getUserProfile(LoginResult loginResult) {
		String accessToken = loginResult.getAccessToken().getToken();
		Log.i("accessToken", accessToken);
		GraphRequest request = GraphRequest.newMeRequest(
				loginResult.getAccessToken(), new GraphRequest.GraphJSONObjectCallback() {
					@Override
					public void onCompleted(JSONObject object, GraphResponse response) {
						syncFacebookUser(object, response);
					}
				});
		Bundle parameters = new Bundle();
		parameters.putString("fields", "id,name,email,gender, birthday");
		request.setParameters(parameters);
		request.executeAsync();
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		EditText txtUsername = (EditText) findViewById(comcom.projects.storefinder.R.id.txtUsername);
		EditText txtPassword = (EditText) findViewById(comcom.projects.storefinder.R.id.txtPassword);

		MGUtilities.dismissKeyboard(this, txtUsername);
		MGUtilities.dismissKeyboard(this, txtPassword);

		switch(v.getId()) {
			case comcom.projects.storefinder.R.id.btnLogin:
				login();
				break;
			case comcom.projects.storefinder.R.id.btnFacebook:
				LoginManager.getInstance().logOut();
				LoginManager.getInstance().logInWithReadPermissions(
						this, Arrays.asList("email", "public_profile"));
				break;
		}
	}

	public void login() {
		if(!MGUtilities.hasConnection(LoginActivity.this)) {
			MGUtilities.showAlertView(
					LoginActivity.this,
					comcom.projects.storefinder.R.string.network_error,
					comcom.projects.storefinder.R.string.no_network_connection);
			return;
		}

		EditText txtUsername = (EditText) findViewById(comcom.projects.storefinder.R.id.txtUsername);
		EditText txtPassword = (EditText) findViewById(comcom.projects.storefinder.R.id.txtPassword);

		if(txtUsername.getText().toString().isEmpty() || txtPassword.getText().toString().isEmpty()) {
			MGUtilities.showAlertView(
					LoginActivity.this,
					comcom.projects.storefinder.R.string.login_error,
					comcom.projects.storefinder.R.string.login_error_details);
			return;
		}

		task = new MGAsyncTask(LoginActivity.this);
		task.setMGAsyncTaskListener(new MGAsyncTask.OnMGAsyncTaskListener() {

			DataResponse response;

			@Override
			public void onAsyncTaskProgressUpdate(MGAsyncTask asyncTask) { }

			@Override
			public void onAsyncTaskPreExecute(MGAsyncTask asyncTask) {
				asyncTask.setMessage(
						MGUtilities.getStringFromResource(LoginActivity.this, comcom.projects.storefinder.R.string.logging_in));
			}

			@Override
			public void onAsyncTaskPostExecute(MGAsyncTask asyncTask) {
				// TODO Auto-generated method stub
				updateLogin(response, null, null);
			}

			@Override
			public void onAsyncTaskDoInBackground(MGAsyncTask asyncTask) {
				// TODO Auto-generated method stub
				response = syncData();
			}
		});
		task.execute();
	}

	public DataResponse syncData() {
		EditText txtUsername = (EditText) findViewById(comcom.projects.storefinder.R.id.txtUsername);
		EditText txtPassword = (EditText) findViewById(comcom.projects.storefinder.R.id.txtPassword);

		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("username", txtUsername.getText().toString()));
		params.add(new BasicNameValuePair("password", txtPassword.getText().toString() ));
		DataResponse response = DataParser.getJSONFromUrlWithPostRequest(Config.LOGIN_URL, params);
		return response;
	}

	public void syncFacebookUser(final JSONObject object, final GraphResponse response) {

		Log.i("syncFacebookUser", response.toString());
		final ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		try {
			String id = object.getString("id");
			String imageURL = "http://graph.facebook.com/" + id + "/picture?type=large";
			_imageURL = imageURL;
			try {
				URL profile_pic = new URL(imageURL);
				Log.i("profile_pic", profile_pic + "");

			} catch (MalformedURLException e) {
				e.printStackTrace();
			}

			String name = object.getString("name");
			_name = name;

			String email = object.getString("email");
			params.add(new BasicNameValuePair("facebook_id", id));
			params.add(new BasicNameValuePair("full_name", name));
			params.add(new BasicNameValuePair("thumb_url", imageURL ));
			params.add(new BasicNameValuePair("email", email ));
			params.add(new BasicNameValuePair("api_key", Config.API_KEY ));
			Log.e("FB IMAGE URL", imageURL);

		} catch (JSONException e) {
			e.printStackTrace();
		}

		task = new MGAsyncTask(LoginActivity.this);
		task.setMGAsyncTaskListener(new MGAsyncTask.OnMGAsyncTaskListener() {

			DataResponse response;

			@Override
			public void onAsyncTaskProgressUpdate(MGAsyncTask asyncTask) { }

			@Override
			public void onAsyncTaskPreExecute(MGAsyncTask asyncTask) {
				asyncTask.dialog.setMessage(
						MGUtilities.getStringFromResource(LoginActivity.this, comcom.projects.storefinder.R.string.logging_in) );
			}

			@Override
			public void onAsyncTaskPostExecute(MGAsyncTask asyncTask) {
				// TODO Auto-generated method stub
				updateLogin(response, _imageURL, _name);
			}

			@Override
			public void onAsyncTaskDoInBackground(MGAsyncTask asyncTask) {
				// TODO Auto-generated method stub
				response = DataParser.getJSONFromUrlWithPostRequest(Config.REGISTER_URL, params);
			}
		});
		task.execute();
	}

	public void syncTwitterUser(final AccessToken accessToken, final String screenName) {

		if(!MGUtilities.hasConnection(this)) {
			MGUtilities.showAlertView(
					LoginActivity.this,
					comcom.projects.storefinder.R.string.network_error,
					comcom.projects.storefinder.R.string.no_network_connection);
			return;
		}
		task = new MGAsyncTask(LoginActivity.this);
		task.setMGAsyncTaskListener(new MGAsyncTask.OnMGAsyncTaskListener() {

			DataResponse response;

			@Override
			public void onAsyncTaskProgressUpdate(MGAsyncTask asyncTask) { }

			@Override
			public void onAsyncTaskPreExecute(MGAsyncTask asyncTask) { }

			@Override
			public void onAsyncTaskPostExecute(MGAsyncTask asyncTask) {
				// TODO Auto-generated method stub
				updateLogin(response, _imageURL, _name);
			}

			@Override
			public void onAsyncTaskDoInBackground(MGAsyncTask asyncTask) {
				// TODO Auto-generated method stub
				@SuppressWarnings("static-access")
				Twitter tw = TwitterApp.getTwitterInstance();
				twitter4j.User user = null;
				try {
					user = tw.showUser(accessToken.getUserId());
				}
				catch (TwitterException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
				if(user != null) {
					String imageURL = user.getOriginalProfileImageURL();
					params.add(new BasicNameValuePair("thumb_url", imageURL ));
					Log.e("TWITTER IMAGE URL", imageURL);
					_imageURL = imageURL;
				}
				_name = screenName;
				params.add(new BasicNameValuePair("twitter_id", String.valueOf(accessToken.getUserId()) ));
				params.add(new BasicNameValuePair("full_name", String.valueOf(screenName) ));
				params.add(new BasicNameValuePair("email", "" ));
				response = DataParser.getJSONFromUrlWithPostRequest(Config.REGISTER_URL, params);
			}
		});
		task.execute();
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
		mCallbackManager.onActivityResult(requestCode, resultCode, data);
	}

	// ###############################################################################################
	// TWITTER INTEGRATION METHODS
	// ###############################################################################################
	public void loginToTwitter() {
		if (mTwitter.hasAccessToken() == true) {
			try {
				syncTwitterUser(mTwitter.getAccessToken(), mTwitter.getScreenName());
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		else {
			mTwitter.loginToTwitter();
		}
	}

	TwitterApp.TwitterAppListener twitterAppListener = new TwitterApp.TwitterAppListener() {

		@Override
		public void onError(String value)  {
			// TODO Auto-generated method stub
			Log.e("TWITTER ERROR**", value);
		}

		@Override
		public void onComplete(AccessToken accessToken) {
			// TODO Auto-generated method stub
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// TODO Auto-generated method stub
					syncTwitterUser(mTwitter.getAccessToken(), mTwitter.getScreenName());
				}
			});
		}
	};

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
		getMenuInflater().inflate(comcom.projects.storefinder.R.menu.menu_default, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(android.view.Menu menu) {
		// if nav drawer is opened, hide the action items
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onDestroy()  {
		super.onDestroy();
		if(task != null)
			task.cancel(true);
	}

	public void updateLogin(DataResponse response, String imageURL, String name) {

		if(response == null) {
			MGUtilities.showAlertView(
					LoginActivity.this,
					comcom.projects.storefinder.R.string.login_error,
					comcom.projects.storefinder.R.string.problems_encountered_login);
			return;
		}
		Status status = response.getStatus();
		if(response != null && status != null) {
			if (status.getStatus_code() == -1 && response.getUser_info() != null) {
				User user = response.getUser_info();
				UserAccessSession session = UserAccessSession.getInstance(LoginActivity.this);
				UserSession userSession = new UserSession();
				userSession.setEmail(user.getEmail());
				userSession.setFacebook_id(user.getFacebook_id());
				userSession.setFull_name(user.getFull_name());
				userSession.setLogin_hash(user.getLogin_hash());
				userSession.setPhoto_url(user.getPhoto_url());
				userSession.setThumb_url(user.getThumb_url());
				userSession.setTwitter_id(user.getTwitter_id());
				userSession.setUser_id(user.getUser_id());
				userSession.setUsername(user.getUsername());
				session.storeUserSession(userSession);
				finish();
			} else {
				MGUtilities.showAlertView(LoginActivity.this, comcom.projects.storefinder.R.string.network_error, status.getStatus_text());
			}
		}
	}
}
