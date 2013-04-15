package org.openintents.oisafebackup;

import java.io.File;
import java.util.List;

import org.openintents.oisafebackup.dropbox.GetBackup;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.TokenPair;
import com.dropbox.client2.session.Session.AccessType;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
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

	final static private AccessType ACCESS_TYPE = AccessType.APP_FOLDER;
	// In the class declaration section:
	private DropboxAPI<AndroidAuthSession> mDBApi;

	final static public String ACCOUNT_PREFS_NAME = "prefs";
	final static public String DB_ACCESS_KEY_NAME = "ACCESS_KEY";
	final static public String DB_ACCESS_SECRET_NAME = "ACCESS_SECRET";
	final static public String DB_REV_NAME = "Dropbox_Rev";
	final static public String DB_MODIFIED_NAME = "Dropbox_Modified";

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
					mDBApi.getSession().startAuthentication(MainActivity.this);
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
	}

	@Override
	protected void onResume() {
		super.onResume();
		AndroidAuthSession session = mDBApi.getSession();

		// The next part must be inserted in the onResume() method of the
		// activity from which session.startAuthentication() was called, so
		// that Dropbox authentication completes properly.
		if (session.authenticationSuccessful()) {
			try {
				// Mandatory call to complete the auth
				session.finishAuthentication();

				// Store it locally in our app for later use
				TokenPair tokens = session.getAccessTokenPair();
				storeKeys(tokens.key, tokens.secret);
				setLoggedIn(true);
			} catch (IllegalStateException e) {
				showToast("Couldn't authenticate with Dropbox:"
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
		getMenuInflater().inflate(R.menu.activity_main, menu);
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

	/**
	 * Shows keeping the access keys returned from Trusted Authenticator in a
	 * local store, rather than storing user name & password, and
	 * re-authenticating each time (which is not to be done, ever).
	 * 
	 * @return Array of [access_key, access_secret], or null if none stored
	 */
	private String[] getKeys() {
		SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
		String key = prefs.getString(DB_ACCESS_KEY_NAME, null);
		String secret = prefs.getString(DB_ACCESS_SECRET_NAME, null);
		if (key != null && secret != null) {
			String[] ret = new String[2];
			ret[0] = key;
			ret[1] = secret;
			return ret;
		} else {
			return null;
		}
	}

	/**
	 * Shows keeping the access keys returned from Trusted Authenticator in a
	 * local store, rather than storing user name & password, and
	 * re-authenticating each time (which is not to be done, ever).
	 */
	private void storeKeys(String key, String secret) {
		// Save the access key for later
		SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
		Editor edit = prefs.edit();
		edit.putString(DB_ACCESS_KEY_NAME, key);
		edit.putString(DB_ACCESS_SECRET_NAME, secret);
		edit.commit();
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
		AndroidAuthSession session;

		String[] stored = getKeys();
		if (stored != null) {
			if (debug) {
				Log.d(TAG, "found stored keys");
			}
			AccessTokenPair accessToken = new AccessTokenPair(stored[0],
					stored[1]);
			session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE,
					accessToken);
		} else {
			session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE);
		}

		return session;
	}

	private void checkForLocalBackup() {
		File restoreFile = new File(PREFERENCE_BACKUP_PATH_DEFAULT_VALUE);
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

		File file = new File(PREFERENCE_BACKUP_PATH_DEFAULT_VALUE);
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
		} else {
			mOISafeNotInstalled.setVisibility(View.VISIBLE);
			mOISafeButton.setEnabled(false);
		}
	}

	private void startOISafe() {
		Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage(
				"org.openintents.safe");
		if (LaunchIntent != null)
			startActivity(LaunchIntent);
	}
	
	private void startDropbox() {
		String url = "https://www.dropbox.com/home/Apps/OI%20Safe%20Backup";
		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setData(Uri.parse(url));
		startActivity(i);
	}
}
