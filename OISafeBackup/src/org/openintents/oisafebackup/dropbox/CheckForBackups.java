/* 
 * Copyright (C) 2014 OpenIntents.org
 * 
 * Portions of code are: Copyright (c) 2010-11 Dropbox, Inc.
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

package org.openintents.oisafebackup.dropbox;

import org.openintents.oisafebackup.MainActivity;
import org.openintents.oisafebackup.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.exception.DropboxParseException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;

/**
 * Here we show getting metadata for a directory and downloading a file in a
 * background thread, trying to show typical exception handling and flow of
 * control for an app that downloads a file from Dropbox.
 */

public class CheckForBackups extends AsyncTask<Void, Long, Boolean> {
	private static final String TAG = "CheckForBackups";
	private static final boolean debug = true;

	private Context mContext;
	private Activity mActivity;
	private final ProgressDialog mDialog;
	private DropboxAPI<?> mApi;
	private String mPath;

	private Entry entryBackup;

	private boolean mCanceled;
	private Long mFileLen;
	private String mErrorMsg;

	public CheckForBackups(Context context, DropboxAPI<?> api,
			String dropboxPath, Activity activity) {
		// We set the context this way so we don't accidentally leak activities
		mContext = context.getApplicationContext();
		mActivity = activity;

		mApi = api;
		mPath = dropboxPath;

		mDialog = new ProgressDialog(context);
		mDialog.setMessage("Checking for backups");
		mDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						mCanceled = true;
						mErrorMsg = "Canceled";
					}
				});

		mDialog.show();
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		try {
			if (mCanceled) {
				return false;
			}

			// Get the metadata for a directory
			entryBackup = mApi.metadata(mPath, 1, null, false, null);

			if (debug) {
				Log.d(TAG, "entryBackup=" + entryBackup);
			}
			return true;

		} catch (DropboxUnlinkedException e) {
			// The AuthSession wasn't properly authenticated or user unlinked.
			mErrorMsg = "AuthSession error";
		} catch (DropboxPartialFileException e) {
			// We canceled the operation
			mErrorMsg = "Download canceled";
		} catch (DropboxServerException e) {
			// Server-side exception. These are examples of what could happen,
			// but we don't do anything special with them here.
			if (e.error == DropboxServerException._304_NOT_MODIFIED) {
				// won't happen since we don't pass in revision with metadata
			} else if (e.error == DropboxServerException._401_UNAUTHORIZED) {
				// Unauthorized, so we should unlink them. You may want to
				// automatically log the user out in this case.
			} else if (e.error == DropboxServerException._403_FORBIDDEN) {
				// Not allowed to access this
			} else if (e.error == DropboxServerException._404_NOT_FOUND) {
				if (debug) {
					Log.d(TAG, "Not found");
				}
			} else if (e.error == DropboxServerException._406_NOT_ACCEPTABLE) {
				// too many entries to return
			} else if (e.error == DropboxServerException._415_UNSUPPORTED_MEDIA) {
				// can't be thumbnailed
			} else if (e.error == DropboxServerException._507_INSUFFICIENT_STORAGE) {
				// user is over quota
			} else {
				// Something else
			}
			// This gets the Dropbox error, translated into the user's language
			mErrorMsg = e.body.userError;
			if (mErrorMsg == null) {
				mErrorMsg = e.body.error;
			}
		} catch (DropboxIOException e) {
			// Happens all the time, probably want to retry automatically.
			mErrorMsg = "Network error.  Try again.";
		} catch (DropboxParseException e) {
			// Probably due to Dropbox server restarting, should retry
			mErrorMsg = "Dropbox error.  Try again.";
		} catch (DropboxException e) {
			// Unknown error
			mErrorMsg = "Unknown error.  Try again.";
		}
		if (debug) {
			Log.d(TAG, "Error: " + mErrorMsg);
		}

		return false;
	}

	@Override
	protected void onProgressUpdate(Long... progress) {
		int percent = (int) (100.0 * (double) progress[0] / mFileLen + 0.5);
		mDialog.setProgress(percent);
	}

	@Override
	protected void onPostExecute(Boolean result) {
		mDialog.dismiss();
		TextView mBackupFileStatus = (TextView) mActivity
				.findViewById(R.id.dbBackupFileStatus);
		TextView mRev = (TextView) mActivity
				.findViewById(R.id.dbRev);
		Button mGetFromDropbox = (Button) mActivity.findViewById(R.id.getFromDropbox);
		
		if (result) {
			TextView mModified = (TextView) mActivity
					.findViewById(R.id.dropboxModified);
			mModified.setText(entryBackup.modified);
			String storedRev=getStoredRev();
			if (debug) {
				Log.d(TAG, "entryBackup.bytes=" + entryBackup.bytes);
				Log.d(TAG, "entryBackup.modified=" + entryBackup.modified);
				Log.d(TAG, "entryBackup.clientMtime=" + entryBackup.clientMtime);
				Log.d(TAG, "entryBackup.path=" + entryBackup.path);
				Log.d(TAG, "entryBackup.rev=" + entryBackup.rev);
				Log.d(TAG, "entryBackup.root=" + entryBackup.root);
				Log.d(TAG, "entryBackup.size=" + entryBackup.size);
				Log.d(TAG, "storedRev=" + storedRev);
			}
			// if we found an entry that has zero bytes, then it's deleted
			// So only store if file has data
			if (entryBackup.bytes > 0) {
				if (storedRev.equals("") || entryBackup.rev.equals(storedRev)) {
					MainActivity.storeEntry(mContext, entryBackup);
					mBackupFileStatus.setText(mContext
							.getString(R.string.dropboxHasBackupFile));
					mRev.setText(entryBackup.rev);
				} else {
					mBackupFileStatus.setText(mContext
							.getString(R.string.dropboxNewerBackup));
				}
				mGetFromDropbox.setEnabled(true);
			} else {
				mBackupFileStatus.setText(mContext
						.getString(R.string.dropboxHasBackupFileDeleted));
				mGetFromDropbox.setEnabled(false);
			}
		} else {
			// Couldn't download it, so show an error
			showToast(mErrorMsg);
			mBackupFileStatus.setText(mErrorMsg);
			mRev.setText("");
			mGetFromDropbox.setEnabled(false);
		}
	}

	/**
	 * Get the local stored file revision
	 * 
	 * @return stored Rev or empty string
	 */
	private String getStoredRev() {
		SharedPreferences prefs = mContext.getSharedPreferences(
				MainActivity.ACCOUNT_PREFS_NAME, Context.MODE_PRIVATE);
		String rev = prefs.getString(MainActivity.DB_REV_NAME, "");
		return rev;
	}

	private void showToast(String msg) {
		Toast error = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
		error.show();
	}

}
