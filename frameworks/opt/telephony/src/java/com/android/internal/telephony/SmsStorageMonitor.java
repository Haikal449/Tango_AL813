/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;

import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;

/**
 * Monitors the device and ICC storage, and sends the appropriate events.
 *
 * This code was formerly part of {@link SMSDispatcher}, and has been moved
 * into a separate class to support instantiation of multiple SMSDispatchers on
 * dual-mode devices that require support for both 3GPP and 3GPP2 format messages.
 */
public final class SmsStorageMonitor extends Handler {
    private static final String TAG = "SmsStorageMonitor";

    /** SIM/RUIM storage is full */
    private static final int EVENT_ICC_FULL = 1;

    /** Memory status reporting is acknowledged by RIL */
    private static final int EVENT_REPORT_MEMORY_STATUS_DONE = 2;

    /** Radio is ON */
    private static final int EVENT_RADIO_ON = 3;

    // MTK-START
    /** ME storage is full and receiving a new SMS from network */
    private static final int EVENT_ME_FULL = 100;
    // MTK-END

    /** Context from phone object passed to constructor. */
    private final Context mContext;

    /** Wake lock to ensure device stays awake while dispatching the SMS intent. */
    private PowerManager.WakeLock mWakeLock;

    private boolean mReportMemoryStatusPending;

    // MTK-START
    /**
     * ME/SIM storage is full, Subscription controller ready sync lock.
     * This lock avoids two thread access mIsSubscriptionReady at same time.
     * This lock is for waiting Subscription controller ready till subscription ready.
     */
    private final Object mLock = new Object();
    /** Subscription controller ready flag. this indicates if Subscription is ready. */
    private boolean mIsSubscriptionReady = false;
    // MTK-END

    /** it is use to put in to extra value for SIM_FULL_ACTION and SMS_REJECTED_ACTION */
    PhoneBase mPhone;

    final CommandsInterface mCi;                            // accessed from inner class
    boolean mStorageAvailable = true;                       // accessed from inner class

    /**
     * Hold the wake lock for 5 seconds, which should be enough time for
     * any receiver(s) to grab its own wake lock.
     */
    private static final int WAKE_LOCK_TIMEOUT = 5000;

    /**
     * Creates an SmsStorageMonitor and registers for events.
     * @param phone the Phone to use
     */
    public SmsStorageMonitor(PhoneBase phone) {
        mPhone = phone;
        mContext = phone.getContext();
        mCi = phone.mCi;

        createWakelock();

        mCi.setOnIccSmsFull(this, EVENT_ICC_FULL, null);
        // MTK-START
        mCi.setOnMeSmsFull(this, EVENT_ME_FULL, null);
        // MTK-END
        mCi.registerForOn(this, EVENT_RADIO_ON, null);

        // Register for device storage intents.  Use these to notify the RIL
        // that storage for SMS is or is not available.
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DEVICE_STORAGE_FULL);
        filter.addAction(Intent.ACTION_DEVICE_STORAGE_NOT_FULL);
        // MTK-START
        filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        // MTK-END
        mContext.registerReceiver(mResultReceiver, filter);
    }

    public void dispose() {
        mCi.unSetOnIccSmsFull(this);
        mCi.unregisterForOn(this);
        mContext.unregisterReceiver(mResultReceiver);
    }

    /**
     * Handles events coming from the phone stack. Overridden from handler.
     * @param msg the message to handle
     */
    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;

        switch (msg.what) {
            case EVENT_ICC_FULL:
                handleIccFull();
                break;

            case EVENT_REPORT_MEMORY_STATUS_DONE:
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    mReportMemoryStatusPending = true;
                    Rlog.v(TAG, "Memory status report to modem pending : mStorageAvailable = "
                            + mStorageAvailable);
                } else {
                    mReportMemoryStatusPending = false;
                }
                break;

            case EVENT_RADIO_ON:
                // MTK-START
                /***********************************************************
                 * There are 2 possible  scenarios will turn off modem
                 * 1) MTK_FLIGHT_MODE_POWER_OFF_MD, AP turns off modem while flight mode
                 * 2) MTK_RADIOOFF_POWER_OFF_MD, AP turns off modem while radio off
                 * In next time modem power on, it will missing stroage full notification,
                 * we need to re-send this notification while radio on
                 **********************************************************/
                // if (mReportMemoryStatusPending) {
                {
                // MTK-END
                    Rlog.v(TAG, "Sending pending memory status report : mStorageAvailable = "
                            + mStorageAvailable);
                    mCi.reportSmsMemoryStatus(mStorageAvailable,
                            obtainMessage(EVENT_REPORT_MEMORY_STATUS_DONE));
                }
                break;

            // MTK-START
            case EVENT_ME_FULL:
                handleMeFull();
                break;
            // MTK-END
        }
    }

    private void createWakelock() {
        PowerManager pm = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmsStorageMonitor");
        mWakeLock.setReferenceCounted(true);
    }

    /**
     * Called when SIM_FULL message is received from the RIL.  Notifies interested
     * parties that SIM storage for SMS messages is full.
     */
    // MTK-START
    public void handleIccFull() {
    // MTK-END
        // broadcast SIM_FULL intent
        // MTK-START
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            SimMeFullRunnable r = new SimMeFullRunnable(Intents.SIM_FULL_ACTION, -1);
            Thread thd = new Thread(r);
            thd.start();
        } else {
            Intent intent = new Intent(Intents.SIM_FULL_ACTION);
            mWakeLock.acquire(WAKE_LOCK_TIMEOUT);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, mPhone.getPhoneId());
            mContext.sendBroadcast(intent, android.Manifest.permission.RECEIVE_SMS);
        }
        // MTK-END
    }

    /** Returns whether or not there is storage available for an incoming SMS. */
    public boolean isStorageAvailable() {
        return mStorageAvailable;
    }

    private final BroadcastReceiver mResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_DEVICE_STORAGE_FULL)) {
                mStorageAvailable = false;
                mCi.reportSmsMemoryStatus(false, obtainMessage(EVENT_REPORT_MEMORY_STATUS_DONE));
            } else if (intent.getAction().equals(Intent.ACTION_DEVICE_STORAGE_NOT_FULL)) {
                mStorageAvailable = true;
                mCi.reportSmsMemoryStatus(true, obtainMessage(EVENT_REPORT_MEMORY_STATUS_DONE));
            // MTK-START
            } else if (intent.getAction().equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                synchronized (mLock) {
                    Rlog.v(TAG, "onReceive, mLock.notifyAll()");
                    mIsSubscriptionReady = true;
                    mLock.notifyAll();
                }
            // MTK-END
            }
        }
    };

    // MTK-START
    /**
     * Called when ME_FULL message is received from the RIL.  Notifies interested
     * parties that ME storage for SMS messages is full.
     */
    private void handleMeFull() {
        // broadcast SMS_REJECTED_ACTION intent

        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            SimMeFullRunnable r = new SimMeFullRunnable(Intents.SMS_REJECTED_ACTION,
                    Intents.RESULT_SMS_OUT_OF_MEMORY);
            Thread thd = new Thread(r);
            thd.start();
        } else {
            Intent intent = new Intent(Intents.SMS_REJECTED_ACTION);
            intent.putExtra("result", Intents.RESULT_SMS_OUT_OF_MEMORY);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, mPhone.getPhoneId());
            mWakeLock.acquire(WAKE_LOCK_TIMEOUT);
            mContext.sendBroadcast(intent, android.Manifest.permission.RECEIVE_SMS);
        }
    }

    /**
     * Called before notify interested parties when SIM/ME full.
     * Used to make sure the Subscription controller ready.
     */
    private void checkAndWaitSubscriptionReady() {
        synchronized (mLock) {
            if (!mIsSubscriptionReady) {
                try {
                    Rlog.v(TAG, "checkAndWaitSubscriptionReady, wait...");
                    mLock.wait();
                } catch (InterruptedException e) {
                    Rlog.v(TAG, "checkAndWaitSubscriptionReady, wait interrupted");
                    return;
                }
            }
        }
    }

    /**
     * A runnable for broadcast SIM/ME full.
     */
    class SimMeFullRunnable implements Runnable {
        private String mAction;
        private int mResult;
        SimMeFullRunnable(String act, int result) {
            mAction = act;
            mResult = result;
        }

        @Override
        public void run() {
            Rlog.v(TAG, "run(), mAction = " + mAction +
                    ", mIsSubscriptionReady = " + mIsSubscriptionReady);
            checkAndWaitSubscriptionReady();
            Rlog.v(TAG, "run(), new intent to broadcast" + ", mResult = " + mResult);
            Intent intent = new Intent(mAction);
            if (mResult > 0) {
                intent.putExtra("result", mResult);
            }
            mWakeLock.acquire(WAKE_LOCK_TIMEOUT);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, mPhone.getPhoneId());
            mContext.sendBroadcast(intent, android.Manifest.permission.RECEIVE_SMS);
        }
    }
    // MTK-END
}
