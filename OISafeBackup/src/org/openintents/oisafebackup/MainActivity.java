/* 
 * Copyright (C) 2014 OpenIntents.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openintents.oisafebackup;

import java.io.File;
import java.util.List;

import org.openintents.intents.CryptoIntents;
import org.openintents.oisafebackup.dropbox.CheckForBackups;
import org.openintents.oisafebackup.dropbox.GetBackup;
import org.openintents.oisafebackup.dropbox.KeySecret;
import org.openintents.oisafebackup.dropbox.UploadBackup;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";
	private static final boolean debug = true;

	// In the class declaration section:
	private DropboxAPI<AndroidAuthSession> mDBApi;

	final static public String ACCOUNT_PREFS_NAME = "prefs";
	final static public String DB_ACCESS_KEY_NAME = "ACCESS_KEY";
	final static public String DB_ACCESS_SECRET_NAME = "ACCESS_SECRET";
	final static public String DB_REV_NAME = "Dropbox_Rev";
	final static public String DB_MODIFIED_NAME = "Dropbox_Modified";
	
	final static public String DB_APP_NAME = "OI%20Safe%20Backup";

	private boolean mLoggedIn;

	private Button mConnect;
	private TextView mLocalBackupStatus;
	private Button mSendToDropbox;
	private Button mCheckDropbox;
	private RelativeLayout mDropboxConnected;
	private TextView mDropboxModified;
	private TextView mOISafeNotInstalled;
	private ImageButton mOISafeButton;
	private ImageButton mDropboxLogo;
	private Button mGetFromDropbox;

	private static final int REQUEST_CODE_GET_BACKUP_NAME = 1;
	
	private boolean tryingToGetBackupPath = false;
	private String backupPath;
	
	public static final String PREFERENCE_BACKUP_PATH_DEFAULT_VALUE = Environment
			.getExternalStorageDirectory().getAbsolutePath() + "/oisafe.xml";
	public static final String DROPBOX_BACKUP_PATH_DEFAULT_VALUE = "/oisafe.xml";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		AndroidAuthSession session = buildSession();
		mDBApi = new DropboxAPI<AndroidAuthSession>(session);

		mConnect = (Button) findViewById(R.id.connect);
		mLocalBackupStatus = (TextView) findViewById(R.id.localBackupStatus);
		mSendToDropbox = (Button) findViewById(R.id.sendToDropbox);
		mCheckDropbox = (Button) findViewById(R.id.checkDropbox);
		mDropboxConnected = (RelativeLayout) findViewById(R.id.dropboxConnected);
		mDropboxModified = (TextView) findViewById(R.id.dropboxModified);
		mOISafeNotInstalled = (TextView) findViewById(R.id.notInstalled);
		mOISafeButton = (ImageButton) findViewById(R.id.oisafeButton);
		mDropboxLogo = (ImageButton) findViewById(R.id.dropboxLogo);
		mGetFromDropbox = (Button) findViewById(R.id.getFromDropbox);

		mDropboxModified.setText("");

		mConnect.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// This logs you out if you're logged in, or vice versa
				if (mLoggedIn) {
					logOut();
				} else {
					// Start the remote authentication
					mDBApi.getSession().startOAuth2Authentication(MainActivity.this);
				}
			}
		});
		mSendToDropbox.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				sendLocalToDropbox();
			}
		});
		mCheckDropbox.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				checkDropbox();
			}
		});
		mOISafeButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				startOISafe();
			}
		});
		mDropboxLogo.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				startDropbox();
			}
		});
		mGetFromDropbox.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				getFromDropbox();
			}
		});
		backupPath=PREFERENCE_BACKUP_PATH_DEFAULT_VALUE;
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (debug) Log.d(TAG,"onResume()");
		AndroidAuthSession session = mDBApi.getSession();

		// The next part must be inserted in the onResume() method of the
		// activity from which session.startAuthentication() was called, so
		// that Dropbox authentication completes properly.
		if (session.authenticationSuccessful()) {
			try {
				// Mandatory call to complete the auth
				session.finishAuthentication();

				// Store it locally in our app for later use
				storeAuth(session);
				setLoggedIn(true);
			} catch (IllegalStateException e) {
				showToast(getString(R.string.cancel)+":"
						+ e.getLocalizedMessage());
				Log.i(TAG, "Error authenticating", e);
			}
		}
		setLoggedIn(mDBApi.getSession().isLinked());
		checkOISafeInstalled();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	private void logOut() {
		// Remove credentials from the session
		mDBApi.getSession().unlink();

		// Clear our stored keys
		clearKeys();
		// Change UI state to display logged out version
		setLoggedIn(false);
	}

	/**
	 * Convenience function to change UI state based on being logged in
	 */
	private void setLoggedIn(boolean loggedIn) {
		mLoggedIn = loggedIn;
		if (loggedIn) {
			mConnect.setText(getString(R.string.disconnect));
			mDropboxConnected.setVisibility(View.VISIBLE);
			checkForLocalBackup();
		} else {
			mConnect.setText(getString(R.string.connect));
			mDropboxConnected.setVisibility(View.GONE);
		}
	}

	private void showToast(String msg) {
		Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
		error.show();
	}

	private void loadAuth(AndroidAuthSession session) {
		SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
		String key = prefs.getString(DB_ACCESS_KEY_NAME, null);
		String secret = prefs.getString(DB_ACCESS_SECRET_NAME, null);
		if (key == null || secret == null || key.length() == 0 || secret.length() == 0) return;
		
		if (key.equals("oauth2:")) {
			// If the key is set to "oauth2:", then we can assume the token is for OAuth 2.
			session.setOAuth2AccessToken(secret);
		} else {
			// Still support using old OAuth 1 tokens.
			session.setAccessTokenPair(new AccessTokenPair(key, secret));
		}
	}

	private void storeAuth(AndroidAuthSession session) {
		// Store the OAuth 2 access token, if there is one.
		String oauth2AccessToken = session.getOAuth2AccessToken();
		if (oauth2AccessToken != null) {
			SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
			Editor edit = prefs.edit();
			edit.putString(DB_ACCESS_KEY_NAME, "oauth2:");
			edit.putString(DB_ACCESS_SECRET_NAME, oauth2AccessToken);
			edit.commit();
			return;
		}
		// Store the OAuth 1 access token, if there is one.  This is only necessary if
		// you're still using OAuth 1.
		AccessTokenPair oauth1AccessToken = session.getAccessTokenPair();
		if (oauth1AccessToken != null) {
			SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
			Editor edit = prefs.edit();
			edit.putString(DB_ACCESS_KEY_NAME, oauth1AccessToken.key);
			edit.putString(DB_ACCESS_SECRET_NAME, oauth1AccessToken.secret);
			edit.commit();
			return;
		}
	}

	private void clearKeys() {
		SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
		Editor edit = prefs.edit();
		edit.clear();
		edit.commit();
	}

	private AndroidAuthSession buildSession() {
		AppKeyPair appKeyPair = new AppKeyPair(KeySecret.APP_KEY,
				KeySecret.APP_SECRET);

		AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
		loadAuth(session);
		return session;
	}

	private void checkForLocalBackup() {
		File restoreFile = new File(backupPath);
		if (!restoreFile.exists()) {
			mLocalBackupStatus.setText(R.string.nolocalbackup);
			mSendToDropbox.setEnabled(false);
			return;
		}
		mLocalBackupStatus.setText(R.string.localBackupExists);
		mSendToDropbox.setEnabled(true);
	}

	private void sendLocalToDropbox() {
		if (debug) {
			Log.d(TAG, "sending local to dropbox");
		}

		File file = new File(backupPath);
		UploadBackup upload = new UploadBackup(this, mDBApi, "/", file);
		upload.execute();

	}

	private void checkDropbox() {
		if (debug) {
			Log.d(TAG, "checking dropbox");
		}

		CheckForBackups check = new CheckForBackups(this, mDBApi,
				DROPBOX_BACKUP_PATH_DEFAULT_VALUE, this);
		check.execute();

	}

	private void getFromDropbox() {
		GetBackup getBackup = new GetBackup(this, mDBApi,
				DROPBOX_BACKUP_PATH_DEFAULT_VALUE, this);
		getBackup.execute();
	}

	private void checkOISafeInstalled() {
		boolean oiSafeIsInstalled = false;
		List<PackageInfo> packs = getPackageManager().getInstalledPackages(0);
		for (int i = 0; i < packs.size(); i++) {
			PackageInfo p = packs.get(i);
			if (p.packageName.contentEquals("org.openintents.safe")) {
				oiSafeIsInstalled = true;
				break;
			}
		}
		if (oiSafeIsInstalled) {
			mOISafeNotInstalled.setVisibility(View.INVISIBLE);
			mOISafeButton.setEnabled(true);
			getBackupName();
		} else {
			mOISafeNotInstalled.setVisibility(View.VISIBLE);
			mOISafeButton.setEnabled(false);
		}
	}

	private void getBackupName() {
		if (tryingToGetBackupPath==true) {
			tryingToGetBackupPath=false;
			return;
		}
		Intent i = new Intent();
		i.setAction(CryptoIntents.ACTION_CRYPTO_GET_BACKUP_NAME);
		PackageManager pm = getPackageManager();
		ComponentName cn = i.resolveActivity(pm);
		if (cn == null) {
			if (debug) Log.d(TAG, "unable to find ACTION_CRYPTO_GET_BACKUP_NAME");
			return;
		}
		try {
			if (debug)
				Log.d(TAG, "getBackupName: startActivity");
			tryingToGetBackupPath=true;
			startActivityForResult(i, REQUEST_CODE_GET_BACKUP_NAME);
		} catch (ActivityNotFoundException e) {
			if (debug)
				Log.d(TAG, "failed to get backup name");
		}
	}

	private void startOISafe() {
		Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage(
				"org.openintents.safe");
		if (LaunchIntent != null)
			startActivity(LaunchIntent);
	}
	
	private void startDropbox() {
		Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage(
				"com.dropbox.android");
		if (LaunchIntent != null) {
			startActivity(LaunchIntent);
		}else {
			String url = "https://www.dropbox.com/home/Apps/"+DB_APP_NAME;
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(url));
			startActivity(i);
		}
	}

	public static void storeEntry(Context context, Entry entry) {
		if (entry==null) return;
		SharedPreferences prefs = context.getSharedPreferences(
				MainActivity.ACCOUNT_PREFS_NAME, Context.MODE_PRIVATE);
		Editor edit = prefs.edit();
		if (debug) {
			Log.d(TAG, "storeEntry: entry.rev=" + entry.rev);
		}
		edit.putString(MainActivity.DB_REV_NAME, entry.rev);
		edit.putString(MainActivity.DB_MODIFIED_NAME, entry.modified);
		edit.commit();
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (debug)
			Log.d(TAG, "onActivityResult: Received requestCode " + requestCode
					+ ", resultCode " + resultCode);
		switch (requestCode) {
		case REQUEST_CODE_GET_BACKUP_NAME:
			if (resultCode == RESULT_OK && data != null) {
				
				String text = data
						.getStringExtra(CryptoIntents.EXTRA_TEXT);
				if (debug) Log.d(TAG,"EXTRA_TEXT="+text);
				if (text!=null) {
					backupPath=text;
				}
			}
		}
	}
}
