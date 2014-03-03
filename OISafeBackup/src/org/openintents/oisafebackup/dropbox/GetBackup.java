/*
 * Copyright 2013 OpenIntents
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.openintents.oisafebackup.MainActivity;
import org.openintents.oisafebackup.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.DropboxFileInfo;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.exception.DropboxParseException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;

/**
 * This was originally taken from the Dropbox example.
 * 
 * This is used to retrieve the oisafe.xml backup file and store it locally to
 * be restored by OI Safe.
 */

public class GetBackup extends AsyncTask<Void, Long, Boolean> {
	private static final String TAG = "GetBackup";
	private static final boolean debug = true;

	private Context mContext;
	private final ProgressDialog mDialog;
	private DropboxAPI<?> mApi;
	private String mPath;

	private DropboxFileInfo info;

	private boolean mCanceled;
	private Long mFileLen;
	private String mErrorMsg;

	public GetBackup(Context context, DropboxAPI<?> api, String dropboxPath,
			Activity activity) {
		// We set the context this way so we don't accidentally leak activities
		mContext = context.getApplicationContext();

		mApi = api;
		mPath = dropboxPath;

		mDialog = new ProgressDialog(context);
		mDialog.setMessage(context.getString(R.string.gettingBackup));
		mDialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(R.string.cancel),
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						mCanceled = true;
						mErrorMsg = mContext.getString(R.string.canceled);
					}
				});

		mDialog.show();
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		FileOutputStream outputStream = null;
		try {
			if (mCanceled) {
				return false;
			}

			String localPath = MainActivity.PREFERENCE_BACKUP_PATH_DEFAULT_VALUE;
			File file = new File(localPath);
			outputStream = new FileOutputStream(file);
			if (debug) { Log.d(TAG,"getting "+mPath+" and saving to "+localPath); }
			info = mApi.getFile(mPath, null, outputStream, null);

			if (debug) {
				Log.d(TAG, "info=" + info);
			}
			if (info != null) {
				MainActivity.storeEntry(mContext, info.getMetadata());
			}
			return true;

		} catch (FileNotFoundException e) {
			mErrorMsg = e.getLocalizedMessage();
		} catch (DropboxUnlinkedException e) {
			// The AuthSession wasn't properly authenticated or user unlinked.
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
		} finally {
			if (outputStream != null) {
				try {
					outputStream.close();
				} catch (IOException e) {
				}
			}
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

		if (result) {
			showToast(mContext.getString(R.string.success));
		} else {
			// Couldn't download it, so show an error
			showToast(mErrorMsg);
		}
	}

	private void showToast(String msg) {
		Toast error = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
		error.show();
	}
}
