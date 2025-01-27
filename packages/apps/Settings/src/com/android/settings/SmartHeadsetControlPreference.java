/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.preference.ListPreference;
import android.preference.Preference;
import android.widget.LinearLayout;
import android.util.Log;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.PreferenceManager;
import android.content.BroadcastReceiver;
import android.os.Bundle;
import android.net.Uri;
import android.content.ContentResolver;
import android.provider.Settings;

public class SmartHeadsetControlPreference extends Preference 
         implements PreferenceManager.OnActivityStopListener{
    private static final String LOG_TAG = "SmartHeadsetControlPreference";

    private Context mContext;
    private TextView mPrefStatusView;
    private final IntentFilter mIntentFilter;
    private int mState;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context ctx, Intent intent) {
            if (SmartEarphoneControlSettings.BROADCAST_INTENT_ACTION
                    .equals(intent.getAction())) {
                updateSuspendStatus(mPrefStatusView);
            }
        }
    };

    public SmartHeadsetControlPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mIntentFilter = new IntentFilter(SmartEarphoneControlSettings.BROADCAST_INTENT_ACTION);
        
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        final LayoutInflater layoutInflater =
            (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        Log.d(LOG_TAG, "onBindView");
        View viewGroup = layoutInflater.inflate(R.layout.preference_smartheadset_item, null);
        LinearLayout frame = (LinearLayout) viewGroup.findViewById(R.id.frame);
        mPrefStatusView = (TextView) frame.findViewById(R.id.pref_status);
        mContext.registerReceiver(mReceiver, mIntentFilter);

        return frame;
    }
	
    /*public void unregisterReceiver(){
	if (mReceiver != null && mContext!=null) {
            mContext.unregisterReceiver(mReceiver);
        }
     }*/
	
    public void onActivityStop() {
        if (mReceiver != null && mContext!=null) {
            mContext.unregisterReceiver(mReceiver);
        }
     }

   @Override
   protected void onBindView(View view) {
      super.onBindView(view);
        Log.d(LOG_TAG, "onBindView");
        updateSuspendStatus(mPrefStatusView);       
    }

     private void updateSuspendStatus(TextView prefStatusTextView) {
        
        mState = Settings.System.getInt(mContext.getContentResolver(), "smart_earphone_control", 0);
        if(mState == 1){
           prefStatusTextView.setText(R.string.switch_on_text);
        } else {
           prefStatusTextView.setText(R.string.switch_off_text);
	}
 	
     }


    @Override
    protected void onClick() {
        // Ignore this until an explicit call to click()
    }

    public void click() {
        super.onClick();
    }
}

