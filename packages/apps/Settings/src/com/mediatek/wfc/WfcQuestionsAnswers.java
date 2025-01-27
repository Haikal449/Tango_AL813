/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */


package com.mediatek.wfc;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewManager;
import android.widget.Button;
import android.widget.TextView;


import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.SettingsActivity;


/* Fragment to display WFC QA screen */

public class WfcQuestionsAnswers extends SettingsPreferenceFragment {

    private static boolean DBG = true;
    private static final String TAG = "WfcQuestionsAnswers";

    private Context mContext;
    private SettingsActivity mActivity;

    private View mRootView;
    private TextView mQAView;
    private Button mNextButton;
    private String mWVString;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = (SettingsActivity) getActivity();
        mContext = mActivity.getApplicationContext();
        mWVString = getActivity().getApplicationContext().getString(R.string.Wfc_top_questions_page);
        mActivity.setTitle(getActivity().getApplicationContext().getString(R.string.Wfc_questions));
        if (DBG) Log.d(TAG, "activity:" + mActivity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                     Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.wfc_help_fragment_layout, container, false);
        return mRootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onActivityCreated(savedInstanceState);

        mQAView = (TextView) mRootView.findViewById(R.id.wfc_tutorial_view);
        mNextButton = (Button) mRootView.findViewById(R.id.tutorial_button);
        ((ViewManager) mRootView).removeView(mNextButton);
        mQAView.setText(Html.fromHtml(mWVString, new ImageGetter() {
            @Override
            public Drawable getDrawable(String source) {
                // TODO Auto-generated method stub
                Drawable d = null;
                try {
                    int resId = getResources().getIdentifier(source,
                            "drawable", mContext.getPackageName());
                    if (resId != 0) {
                        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
                        int width = metrics.widthPixels;
                        d = getResources().getDrawable(resId);
                        d.setBounds(0, 0, width, 5/*d.getIntrinsicWidth(), d.getIntrinsicHeight()*/);
                    }
                } catch (Resources.NotFoundException e) {
                    Log.e(TAG, "Image not found. Check the ID.", e);
                }
                return d;
            } }, null));
        if (DBG) Log.d(TAG, "onActivityCreated exit");
    }

}

