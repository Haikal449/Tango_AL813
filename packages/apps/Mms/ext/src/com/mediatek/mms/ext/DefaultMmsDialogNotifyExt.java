/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2012. All rights reserved.
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

package com.mediatek.mms.ext;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/// M:Code analyze 01,For new feature CMCC_Mms in ALPS00325381, MMS easy porting check in JB @{
public class DefaultMmsDialogNotifyExt extends ContextWrapper implements IMmsDialogNotifyExt {

    private static final String TAG = "Mms/MmsDialogNotifyImpl";

    public DefaultMmsDialogNotifyExt(Context context) {
        super(context);
    }

    public void notifyNewSmsDialog(Uri msgUri) {
        Log.d(TAG, "notifyNewSmsDialog:" + msgUri.toString());
        Context context = this;
        if (isHome(context)) {
            Log.d(TAG, "at launcher");
            Intent smsIntent = new Intent("com.android.mms.dialogmode.NEWMSGNOTIFY");

            smsIntent.putExtra("com.android.mms.transaction.new_msg_uri", msgUri.toString());
            smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(smsIntent);
        } else {
            Log.d(TAG, "not at launcher");
        }
    }

    public void closeMsgDialog() {
        Log.d(TAG, "MmsDialogNotifyExt.closeMsgDialog");
        Context context = this; //getApplicationContext();
        Context test = getApplicationContext();
        if (test == null) {
            Log.d(TAG, "test null");
        }
        Intent intent = new Intent();

        intent.setAction("com.android.mms.dialogmode.VIEWED");
        context.sendBroadcast(intent);
    }

    private List<String> getHomes(Context context) {
        Log.d(TAG, "SmsReceiverService.getHomes");

        List<String> names = new ArrayList<String>();
        PackageManager packageManager = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);

        for (ResolveInfo ri : resolveInfo) {
            names.add(ri.activityInfo.packageName);
            Log.d(TAG, "package name=" + ri.activityInfo.packageName);
            Log.d(TAG, "class name=" + ri.activityInfo.name);
        }
        return names;
    }

    public boolean isHome(Context context) {
        List<String> homePackageNames = getHomes(context);
        String packageName;
        String className;
        boolean ret = true;

        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningTaskInfo> rti = activityManager.getRunningTasks(2);

        packageName = rti.get(0).topActivity.getPackageName();
        className = rti.get(0).topActivity.getClassName();
        Log.d(TAG, "package0= " + packageName + " class0=" + className);

        //ret = homePackageNames.contains(packageName);//weather is launcher
     /**modify  by liruihong for HQ01382596 begin*/
      //  if (!ret) {
            if ("com.android.mms.ui.DialogModeActivity".equals(className)) {
                ret = false;
            }
     //   }
    /**modify by liruihong for HQ01382596 end*/

		if ("com.android.mms.ui.ComposeMessageActivity".equals(className)) {
			Log.d(TAG, "not show dialog and className = " + className);
			ret = false;
		}

        /// M: fix bug ALPS00569771, check RunningAppProcessInfo IMPORTANCE_FOREGROUND @{
        if (!ret) {
            List<RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
            if (appProcesses == null || appProcesses.size() == 0) {
                Log.d(TAG, "appProcesses == null || appProcesses.size() == 0");
                ret = false;
            }
            for (RunningAppProcessInfo appProcess : appProcesses) {
              if (appProcess.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                      && appProcess.processName.equals("com.android.launcher3.Launcher")) {
                Log.d(TAG, "IMPORTANCE_FOREGROUND == com.android.launcher3.Launcher");
                ret = true;
              }
            }
        }
        /// @}

        return ret;
    }
    /// @}
}
