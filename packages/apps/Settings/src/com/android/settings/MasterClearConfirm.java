/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.provider.Settings;
import android.service.persistentdata.PersistentDataBlockManager;
import com.android.internal.os.storage.ExternalStorageFormatter;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.mediatek.settings.UtilsExt;

import android.os.SystemProperties;

/**
 * Confirm and execute a reset of the device to a clean "just out of the box"
 * state.  Multiple confirmations are required: first, a general "are you sure
 * you want to do this?" prompt, followed by a keyguard pattern trace if the user
 * has defined one, followed by a final strongly-worded "THIS WILL ERASE EVERYTHING
 * ON THE PHONE" prompt.  If at any time the phone is allowed to go to sleep, is
 * locked, et cetera, then the confirmation sequence is abandoned.
 *
 * This is the confirmation screen.
 */
public class MasterClearConfirm extends Fragment {
    private static final String TAG = "MasterClearConfirm";

    private View mContentView;
    private boolean mEraseSdCard;
   //HQ_jiangchao modify for HQ01361397	at 20150916 start
    public Context context;
    private Button.OnClickListener mBackupDataClickListener = new Button.OnClickListener() {
            public void onClick(View v) {
            	 Intent intent = new Intent("HuaweiBackupInterface");
		 startActivityForResult(intent, 0);
               
            }
        };
   //HQ_jiangchao modify for HQ01361397	at 20150916 start
    /**
     * The user has gone through the multiple confirmation, so now we go ahead
     * and invoke the Checkin Service to reset the device to its factory-default
     * state (rebooting in the process).
     */
    private Button.OnClickListener mFinalClickListener = new Button.OnClickListener() {

        public void onClick(View v) {
            if (Utils.isMonkeyRunning()) {
                return;
            }

            final PersistentDataBlockManager pdbManager = (PersistentDataBlockManager)
                    getActivity().getSystemService(Context.PERSISTENT_DATA_BLOCK_SERVICE);

            if (pdbManager != null && !pdbManager.getOemUnlockEnabled() &&
                    Settings.Global.getInt(getActivity().getContentResolver(),
                            Settings.Global.DEVICE_PROVISIONED, 0) != 0) {
                // if OEM unlock is enabled, this will be wiped during FR process. If disabled, it
                // will be wiped here, unless the device is still being provisioned, in which case
                // the persistent data block will be preserved.
                final ProgressDialog progressDialog = getProgressDialog();
                progressDialog.show();

                // need to prevent orientation changes as we're about to go into
                // a long IO request, so we won't be able to access inflate resources on flash
                final int oldOrientation = getActivity().getRequestedOrientation();
                getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        pdbManager.wipe();
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        progressDialog.hide();
                        getActivity().setRequestedOrientation(oldOrientation);
                        doMasterClear();
                    }
                }.execute();
            } else {
                doMasterClear();
            }
        }

        private ProgressDialog getProgressDialog() {
            final ProgressDialog progressDialog = new ProgressDialog(getActivity());
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setTitle(
                    getActivity().getString(R.string.master_clear_progress_title));
            progressDialog.setMessage(
                    getActivity().getString(R.string.master_clear_progress_text));
            return progressDialog;
        }
    };

    private void doMasterClear() {
        if (mEraseSdCard) {
			/*HQ_liugang delete for HQ01308655
            Intent intent = new Intent(ExternalStorageFormatter.FORMAT_AND_FACTORY_RESET);
            intent.putExtra(Intent.EXTRA_REASON, "MasterClearConfirm");
            intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
            Log.d(TAG, "startService FORMAT_AND_FACTORY_RESET");
            getActivity().startService(intent);
            */
            SystemProperties.set("sys.erase.card","1");
			
        } 
		else {
			/*HQ_liugang delete for HQ01308655
            Intent intent = new Intent(Intent.ACTION_MASTER_CLEAR);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            intent.putExtra(Intent.EXTRA_REASON, "MasterClearConfirm");
            Log.d(TAG, "send broadcast ACTION_MASTER_CLEAR");
            getActivity().sendBroadcast(intent);
            */
            // Intent handling is asynchronous -- assume it will happen soon.
            SystemProperties.set("sys.erase.card","0");
        }
			Log.d(TAG, "persist.erase.card " + SystemProperties.get("sys.erase.card"));
            Intent intent = new Intent(Intent.ACTION_MASTER_CLEAR);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            intent.putExtra(Intent.EXTRA_REASON, "MasterClearConfirm");
            Log.d(TAG, "send broadcast ACTION_MASTER_CLEAR");
            getActivity().sendBroadcast(intent);		
    }

    /**
     * Configure the UI for the final confirmation interaction
     */
    private void establishFinalConfirmationState() {
        mContentView.findViewById(R.id.execute_master_clear)
                .setOnClickListener(mFinalClickListener);
	//HQ_jiangchao modify for HQ01361397	at 20150916 start	
        mContentView.findViewById(R.id.backup_data)
                .setOnClickListener(mBackupDataClickListener);
	//HQ_jiangchao modify for HQ01361397	at 20150916 end 
   }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if (UserManager.get(getActivity()).hasUserRestriction(
                UserManager.DISALLOW_FACTORY_RESET)) {
            return inflater.inflate(R.layout.master_clear_disallowed_screen, null);
        }
        mContentView = inflater.inflate(R.layout.master_clear_confirm, null);
        establishFinalConfirmationState();
        return mContentView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
	context = this.getActivity();
        Bundle args = getArguments();
        mEraseSdCard = args != null && args.getBoolean(MasterClear.ERASE_EXTERNAL_EXTRA);
    }
}
