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
 * See the License for the specific language governing permissions an
 * limitations under the License.
 */

package com.android.server.usb;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.util.Pair;
import android.util.Slog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

// USBIF
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.WindowManager;

import com.android.internal.annotations.GuardedBy;
import com.android.server.FgThread;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

import android.content.ActivityNotFoundException;
import android.util.Log;
import android.net.ConnectivityManager;
/* HQ_ChenWenshuai 2015-09-17 modified for HQ01352311*/
import android.os.storage.IMountService;
import android.os.ServiceManager;
import android.app.KeyguardManager;//chenwenshuai modify for HQ01482929

/**
 * UsbDeviceManager manages USB state in device mode.
 */
public class UsbDeviceManager {

    private static final String TAG = UsbDeviceManager.class.getSimpleName();
    private static final boolean DEBUG = true;

    private static final String OTG_STATE_MATCH =
            "DEVPATH=/devices/platform/nop_usb_xceiv";
    private static final String USB_STATE_MATCH =
            "DEVPATH=/devices/virtual/android_usb/android0";
    private static final String ACCESSORY_START_MATCH =
            "DEVPATH=/devices/virtual/misc/usb_accessory";
    //ALPS00428998
    private static final String MTP_STATE_MATCH =
            "DEVPATH=/devices/virtual/misc/mtp_usb";

    // USBIF
    private static final String USBIF_U3D_MATCH =
            "DEVPATH=/devices/virtual/misc/usbif_u3d_test_uevent";

    private static final String USBIF_U3H_MATCH =
            "DEVPATH=/devices/virtual/misc/usbif_u3h_uevent";

    private static final String USBIF_U3H_TEST_MATCH =
            "DEVPATH=/devices/virtual/misc/usbif_u3h_test_uevent";

    private static final String XHCI_MISC_UEVENT_MATCH =
            "DEVPATH=/devices/virtual/misc/xhci_misc_uevent";

    //ALPS00428998
    private static final String FUNCTIONS_PATH =
            "/sys/class/android_usb/android0/functions";
    private static final String STATE_PATH =
            "/sys/class/android_usb/android0/state";
    private static final String MASS_STORAGE_FILE_PATH =
            "/sys/class/android_usb/android0/f_mass_storage/lun/file";
    private static final String RNDIS_ETH_ADDR_PATH =
            "/sys/class/android_usb/android0/f_rndis/ethaddr";
    private static final String AUDIO_SOURCE_PCM_PATH =
            "/sys/class/android_usb/android0/f_audio_source/pcm";
    private static final String ACM_PORT_INDEX_PATH =
            "/sys/class/android_usb/android0/f_acm/port_index";

    private static final String IPO_POWER_ON  =
			"android.intent.action.ACTION_BOOT_IPO";
    private static final String IPO_POWER_OFF =
			"android.intent.action.ACTION_SHUTDOWN_IPO";

    private static final int MSG_UPDATE_STATE = 0;
    private static final int MSG_ENABLE_ADB = 1;
    private static final int MSG_SET_CURRENT_FUNCTIONS = 2;
    private static final int MSG_SYSTEM_READY = 3;
    private static final int MSG_BOOT_COMPLETED = 4;
    private static final int MSG_USER_SWITCHED = 5;
    private static final int MSG_UPDATE_DISCONNECT_STATE = 6;
    private static final int MSG_ENABLE_ACM = 7;
    //VIA-START VIA USB
    private static final int MSG_SET_BYPASS_MODE = 10;
    private static final int MSG_HANDLE_CTCLINET = 11;
    private static final int MSG_VIA_CDROM_EDJECT = 12;
    private static final int MSG_SET_VIA_CDROM = 13;
    private static final int MSG_SET_BYPASS = 14;
    //VIA-END VIA USB

    private static final int AUDIO_MODE_NONE = 0;
    private static final int AUDIO_MODE_SOURCE = 1;

    // Delay for debouncing USB disconnects.
    // We often get rapid connect/disconnect events when enabling USB functions,
    // which need debouncing.
    private static final int UPDATE_DELAY = 1000;
    private static final int UPDATE_DELAY_BICR = 3000;

    //Extended to 45000 for waiting the behavior of XP MTP transfer canceling timeout.
    private static final int RNDIS_UPDATE_DELAY = 45000;

   private static final int MTP_RESET_TIME = 60000;

    // Time we received a request to enter USB accessory mode
    private long mAccessoryModeRequestTime = 0;


    // Timeout for entering USB request mode.
    // Request is cancelled if host does not configure device within 10 seconds.
    private static final int ACCESSORY_REQUEST_TIMEOUT = 10 * 1000;

    private static final String BOOT_MODE_PROPERTY = "ro.bootmode";
    private static final boolean bMtkIpoSupport = SystemProperties.get("ro.mtk_ipo_support").equals("1");
    private static final boolean bEvdoDtViaSupport = SystemProperties.get("ro.mtk_c2k_support").equals("1");

    private UsbHandler mHandler;
    private boolean mBootCompleted;

    private final Object mLock = new Object();

    private final Context mContext;
    private final ContentResolver mContentResolver;
    @GuardedBy("mLock")
    private UsbSettingsManager mCurrentSettings;
    private NotificationManager mNotificationManager;
    private final boolean mHasUsbAccessory;
    private boolean mUseUsbNotification;
    private boolean mAdbEnabled;
    private boolean mAudioSourceEnabled;
    private Map<String, List<Pair<String, String>>> mOemModeMap;
    private String[] mAccessoryStrings;
    private UsbDebuggingManager mDebuggingManager;

    private boolean mAcmEnabled;
    private String mAcmPortIdx;
    private boolean mSettingUsbCharging;
    private boolean mSettingUsbBicr;
    /*
     * These 4 parameters are for built-in CD-ROM evolution(BICR evo).
     * @ What is BICR evo? The device only can show built-in CD-ROM, except receiving a specifc SCSI command from PC.
     *                     After that, user has ability to switch to other USB functions (UMS,MTP,PTP,Tethering.).
     *                     Charge only is excluded in this restriction.
     * 2. mIsUsbBicrEvo : True: this feature is on. Setting this flag in initial step.
     * 3. mIsPcKnowMe : True: the device has received the specific SCSI command. UsbDeviceManager can switch to others.
     * 4. mIsBicrSet : True: the device has done "sys.usb.bicr=no and sys.usb.config=none and bicr" once.
     * 5. mIsUserSwitch : True: the device is in the phase of switching USB functions A to B.
     */
    private boolean mIsUsbBicrEvo;
    private boolean mIsPcKnowMe;
    private boolean mIsBicrSet;
    private boolean mIsUserSwitch;
    private boolean mHwDisconnected;
    private boolean mBatteryChargingUnPlug;
    private boolean mIsUsbSimSecurity;
    private String mSettingFunction;
    private String mUsbStorageType;
    private final ReentrantLock mAdbUpdateLock = new ReentrantLock();
    //ALPS00428998
    private boolean mMtpAskDisconnect;
    //ALPS00428998

    //VIA-START VIA USB
    private final boolean mPCModeEnable =!SystemProperties.getBoolean("sys.usb.pcmodem.disable",false);
    private final boolean mAutoCdromEnable = mPCModeEnable && SystemProperties.getBoolean("sys.usb.autocdrom.enable",false);
    //VIA-END VIA USB
    private class AdbSettingsObserver extends ContentObserver {
        public AdbSettingsObserver() {
            super(null);
        }
        @Override
        public void onChange(boolean selfChange) {
            boolean enable = (Settings.Global.getInt(mContentResolver,
                    Settings.Global.ADB_ENABLED, 0) > 0);
            mHandler.sendMessage(MSG_ENABLE_ADB, enable);
        }
    }

    private class AcmSettingsObserver extends ContentObserver {
        public AcmSettingsObserver() {
            super(null);
        }
        @Override
        public void onChange(boolean selfChange) {
            int port_num = Settings.Global.getInt(mContentResolver,
                    Settings.Global.ACM_ENABLED, 0);
            mHandler.sendMessage(MSG_ENABLE_ACM, port_num);
        }
    }

    /*
     * Listens for uevent messages from the kernel to monitor the USB state
     */
    private final UEventObserver mUEventObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            if (DEBUG) Slog.v(TAG, "USB UEVENT: " + event.toString());

            String state = event.get("USB_STATE");
            String accessory = event.get("ACCESSORY");
            // USBIF
            final String usbifevent = event.get("USBIF_EVENT");

			// XHCI MISC UEVENT
			final String xhci_misc_uevent = event.get("XHCI_MISC_UEVENT");
				
            //fix ALPS01706482
            if(mHandler == null){
            			SystemClock.sleep(2);
            	}

			if (DEBUG) Slog.d(TAG, "mUEventObserver - onUEvent - state: " + state);

            // USBIF
            if (usbifevent != null){
                mHandler.post(new Runnable() {
                    public void run() {
                        final AlertDialog usbif_alertbox = new AlertDialog.Builder(mContext).create();
                        usbif_alertbox.setTitle("USB event");

                        if (usbifevent.equals("NO_RESP_FOR_SRP")) {
                            usbif_alertbox.setMessage("Device No Response !");

                        }else if (usbifevent.equals("NO_RESP_FOR_HNP_ENABLE")) {
                            usbif_alertbox.setMessage("Device No Response !");

                        }else if (usbifevent.equals("HUB_NOT_SUPPORTED")) {
                            usbif_alertbox.setMessage("Unsupported HUB Device.");

                        }else if (usbifevent.equals("MAX_HUB_TIER_EXCEED")) {
                            usbif_alertbox.setMessage("Max hub tier exceeded.");

                        }else if (usbifevent.equals("DEV_NOT_SUPPORTED")) {
                            usbif_alertbox.setMessage("Unsupported Device.");

                        }else if (usbifevent.equals("HNP_FAILED")) {
                            usbif_alertbox.setMessage("Device No Response for HNP!");

                        }else if (usbifevent.equals("DEV_CONN_TMOUT")) {
                            usbif_alertbox.setMessage("Device No Response !");

                        }else if (usbifevent.equals("DEV_OVER_CURRENT")) {
                            usbif_alertbox.setMessage("USB Device exceed maximum current !");

                        }else{
                            usbif_alertbox.setMessage("USBIF: unknown U3D event");
                        }
                        usbif_alertbox.setButton("Ok", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface arg0, int arg1) {
                                // do nothing now
                            }
                        });
                        usbif_alertbox.getWindow().setType((WindowManager.LayoutParams.TYPE_SYSTEM_ALERT));
                        usbif_alertbox.show();

                    }
                }) ;
            }

			if (xhci_misc_uevent != null){
                mHandler.post(new Runnable() {
                    public void run() {
                        final AlertDialog alertbox = new AlertDialog.Builder(mContext).create();
                        alertbox.setTitle("OTG Event");

                        if (xhci_misc_uevent.equals("OVER_CURRENT")) {
                            alertbox.setMessage("Warning: Over current happens! Cut the power down.");
                    	}
						else {
							alertbox.setMessage("Unknown XHCI event!");
						}

                        alertbox.setButton("Ok", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface arg0, int arg1) {
                                // do nothing now
                            }
                        });
                        alertbox.getWindow().setType((WindowManager.LayoutParams.TYPE_SYSTEM_ALERT));
                        alertbox.show();
                    }
                });
			}

            if (state != null) {
                mHandler.updateState(state);
            } else if ("START".equals(accessory)) {
                if (DEBUG) Slog.d(TAG, "got accessory start");
                startAccessoryMode();
            }
            //VIA-START VIA USB
            if(bEvdoDtViaSupport == true){
                if(mPCModeEnable){
                    String eject = event.get("VIACDROM");
                    if("EJECT".equals(eject)){
                        mHandler.sendMessage(MSG_VIA_CDROM_EDJECT,true);
                    }
                 }
            }
            //VIA-END VIA USB
        }
    };

    public UsbDeviceManager(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        PackageManager pm = mContext.getPackageManager();

        mHwDisconnected = true;
        mSettingUsbCharging = false;
        mSettingUsbBicr = false;
        mIsUsbBicrEvo = false;
        mIsPcKnowMe = true;
        mIsUserSwitch = false;
        mIsUsbSimSecurity = false;
        mBatteryChargingUnPlug = false;
        //ALPS00428998
        mMtpAskDisconnect = false;
        //ALPS00428998
        mHasUsbAccessory = pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY);
        initRndisAddress();

        String value = SystemProperties.get("persist.sys.usb.bicr_evo", "");
        if (value.equals("yes")) {
            Slog.d(TAG, "Enable BICR evolution!!");
            mIsUsbBicrEvo = true;
            mIsPcKnowMe = false;
        }

        if ("1".equals(SystemProperties.get("ro.mtk_usb_cba_support"))) {
            if ("OP01".equals(SystemProperties.get("ro.operator.optr"))) {
                Slog.d(TAG, "Have USB SIM Security!!");
                mIsUsbSimSecurity = true;
            }
        }

        readOemUsbOverrideConfig();

        mHandler = new UsbHandler(FgThread.get().getLooper());

        /* ALPS01961546, startObserving after mHandler is created */
        Slog.i(TAG, "startObserving begin");
        // Watch for USB configuration changes
        mUEventObserver.startObserving(OTG_STATE_MATCH);
        mUEventObserver.startObserving(USB_STATE_MATCH);
        mUEventObserver.startObserving(ACCESSORY_START_MATCH);
        //ALPS00428998
        mUEventObserver.startObserving(MTP_STATE_MATCH);
        //ALPS00428998

        // USBIF
        mUEventObserver.startObserving(USBIF_U3D_MATCH);
        mUEventObserver.startObserving(USBIF_U3H_MATCH);
        mUEventObserver.startObserving(USBIF_U3H_TEST_MATCH);

        // XHCI_MISC_EVENT
        mUEventObserver.startObserving(XHCI_MISC_UEVENT_MATCH);

        Slog.i(TAG, "startObserving end");

        if (nativeIsStartRequested()) {
            if (DEBUG) Slog.d(TAG, "accessory attached at boot");
            startAccessoryMode();
        }

        boolean secureAdbEnabled = SystemProperties.getBoolean("ro.adb.secure", false);
        boolean dataEncrypted = "1".equals(SystemProperties.get("vold.decrypt"));
        if (secureAdbEnabled && !dataEncrypted) {
            mDebuggingManager = new UsbDebuggingManager(context);
        }
    }

    public void setCurrentSettings(UsbSettingsManager settings) {
        synchronized (mLock) {
            mCurrentSettings = settings;
        }
    }

    private UsbSettingsManager getCurrentSettings() {
        synchronized (mLock) {
            return mCurrentSettings;
        }
    }

    public void systemReady() {
        if (DEBUG) Slog.d(TAG, "systemReady");

        mNotificationManager = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        // We do not show the USB notification if any volume supports mass storage.
        // The legacy mass storage UI will be used instead.
        String config = SystemProperties.get("persist.sys.usb.config", UsbManager.USB_FUNCTION_MTP);
        config = removeFunction(config, UsbManager.USB_FUNCTION_ADB);
        config = removeFunction(config, UsbManager.USB_FUNCTION_ACM);
        mUsbStorageType = SystemProperties.get("ro.sys.usb.storage.type", UsbManager.USB_FUNCTION_MTP);

        Slog.d(TAG, "systemReady - mUsbStorageType: " + mUsbStorageType + ", config: " + config);

        if (!containsFunction(mUsbStorageType, config)) {
            mUsbStorageType = config;
            Slog.d(TAG, "systemReady - mUsbStorageType = config");
        }
        if (mUsbStorageType.equals(UsbManager.USB_FUNCTION_MASS_STORAGE)) {

            Slog.d(TAG, "systemReady - UMS only");

            mUseUsbNotification = false;
        boolean massStorageSupported = false;
        final StorageManager storageManager = StorageManager.from(mContext);
            final StorageVolume[] volumes = storageManager.getVolumeList();

            if (volumes != null) {
                for (int i=0; i < volumes.length; i++) {
                    if (volumes[i].allowMassStorage()) {
                        Slog.d(TAG, "systemReady - massStorageSupported: " + massStorageSupported);
                        massStorageSupported = true;
                        break;
                    }
                }
            }
        mUseUsbNotification = !massStorageSupported;
        }
        else {
            Slog.d(TAG, "systemReady - MTP(+UMS)");
            mUseUsbNotification = true;
        }
        if (containsFunction(config, UsbManager.USB_FUNCTION_CHARGING_ONLY)) {
            mSettingUsbCharging = true;
        }

        // make sure the ADB_ENABLED setting value matches the current state
		if(bEvdoDtViaSupport != true){
			/* FIXME, add for L1 */
			try {
				Settings.Global.putInt(mContentResolver,
						Settings.Global.ADB_ENABLED, mAdbEnabled ? 1 : 0);
			} catch (SecurityException e) {
				// If UserManager.DISALLOW_DEBUGGING_FEATURES is on, that this setting can't be changed.
				Slog.d(TAG, "ADB_ENABLED is restricted.");
			}
		}
        mHandler.sendEmptyMessage(MSG_SYSTEM_READY);
    }

    private void startAccessoryMode() {
        if (!mHasUsbAccessory) return;

        mAccessoryStrings = nativeGetAccessoryStrings();
        boolean enableAudio = (nativeGetAudioMode() == AUDIO_MODE_SOURCE);
        // don't start accessory mode if our mandatory strings have not been set
        boolean enableAccessory = (mAccessoryStrings != null &&
                        mAccessoryStrings[UsbAccessory.MANUFACTURER_STRING] != null &&
                        mAccessoryStrings[UsbAccessory.MODEL_STRING] != null);
        String functions = null;

        if (enableAccessory && enableAudio) {
            functions = UsbManager.USB_FUNCTION_ACCESSORY + ","
                    + UsbManager.USB_FUNCTION_AUDIO_SOURCE;
        } else if (enableAccessory) {
            functions = UsbManager.USB_FUNCTION_ACCESSORY;
        } else if (enableAudio) {
            functions = UsbManager.USB_FUNCTION_AUDIO_SOURCE;
        }

        if (functions != null) {
            mAccessoryModeRequestTime = SystemClock.elapsedRealtime();
            setCurrentFunctions(functions, false);
        }
    }

    private static void initRndisAddress() {
        // configure RNDIS ethernet address based on our serial number using the same algorithm
        // we had been previously using in kernel board files
        final int ETH_ALEN = 6;
        int address[] = new int[ETH_ALEN];
        // first byte is 0x02 to signify a locally administered address
        address[0] = 0x02;

        String serial = SystemProperties.get("ro.serialno", "1234567890ABCDEF");
        int serialLength = serial.length();
        // XOR the USB serial across the remaining 5 bytes
        for (int i = 0; i < serialLength; i++) {
            address[i % (ETH_ALEN - 1) + 1] ^= (int)serial.charAt(i);
        }
        String addrString = String.format(Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
            address[0], address[1], address[2], address[3], address[4], address[5]);
        try {
            FileUtils.stringToFile(RNDIS_ETH_ADDR_PATH, addrString);
        } catch (IOException e) {
           Slog.e(TAG, "failed to write to " + RNDIS_ETH_ADDR_PATH);
        }
    }

     private static String addFunction(String functions, String function) {
         if ("none".equals(functions)) {
             return function;
         }

		if (DEBUG) Slog.d(TAG, "Add " + function + " into " + functions);

        if (!containsFunction(functions, function)) {

            if ((function.equals(UsbManager.USB_FUNCTION_ADB) || function.equals(UsbManager.USB_FUNCTION_RNDIS) || function.equals(UsbManager.USB_FUNCTION_EEM))&& containsFunction(functions,UsbManager.USB_FUNCTION_ACM)) {
    	        functions = removeFunction(functions,UsbManager.USB_FUNCTION_ACM);
            }

            if (functions.length() > 0) {
                functions += ",";
            }
            functions += function;

            if ((function.equals(UsbManager.USB_FUNCTION_ADB) || function.equals(UsbManager.USB_FUNCTION_RNDIS) || function.equals(UsbManager.USB_FUNCTION_EEM)) && containsFunction(functions,UsbManager.USB_FUNCTION_ACM)) {
    	        functions = addFunction(functions,UsbManager.USB_FUNCTION_ACM);
            }
        }
        return functions;
    }

    private static String removeFunction(String functions, String function) {
        String[] split = functions.split(",");
        for (int i = 0; i < split.length; i++) {
            if (function.equals(split[i])) {
                split[i] = null;
            }
        }
        if (split.length == 1 && split[0] == null) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
         for (int i = 0; i < split.length; i++) {
            String s = split[i];
            if (s != null) {
                if (builder.length() > 0) {
                    builder.append(",");
                }
                builder.append(s);
            }
        }
        return builder.toString();
    }

    private static boolean containsFunction(String functions, String function) {
        return Arrays.asList(functions.split(",")).contains(function);
    }

    private final class UsbHandler extends Handler {

        // current USB state
        private boolean mConnected;
        private boolean mConfigured;
        //VIA-START VIA USB
        private boolean mViaCdromEjected;
        private Bypass mBypass;
        private boolean mUsbSetBypassWithTether = false;
        //VIA-END VIA USB
        private String mCurrentFunctions;
        private String mDefaultFunctions;
        private UsbAccessory mCurrentAccessory;
        private int mUsbNotificationId;
        private boolean mAdbNotificationShown;
        private int mCurrentUser = UserHandle.USER_NULL;
        private int mPlugType;
		/* HQ_ChenWenshuai 2015-09-17 modified for HQ01352311*/
		private final IMountService mMountService = IMountService.Stub.asInterface(ServiceManager.getService("mount"));
		//chenwenshuai modify for HQ01482929
		private final KeyguardManager mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);


        private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (action != null) {
                    if(action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                if (DEBUG) Slog.d(TAG, "boot completed");
                mHandler.sendEmptyMessage(MSG_BOOT_COMPLETED);
                    } else if(action.equals(IPO_POWER_ON)) {
                        Slog.d(TAG, "onReceive - [IPO_POWER_ON] mDefaultFunctions: " + mDefaultFunctions +
                                     ", mSettingUsbCharging: " + mSettingUsbCharging + ", mSettingUsbBicr: " + mSettingUsbBicr);

                        if (mSettingUsbBicr) {
                            mSettingUsbBicr = false;
                            setCurrentFunctions(mDefaultFunctions, false);
                        }
                        if (mIsUsbBicrEvo) {
                            mIsPcKnowMe = false;
                            setCurrentFunctions(UsbManager.USB_FUNCTION_BICR, false);
                        } else {
                            mIsPcKnowMe = true;
                        }

                        // update notification
                        updateUsbNotification();
                        updateAdbNotification();

                    } else if (action.equals(IPO_POWER_OFF)) {
                        // cancel notification
                        if (mUsbNotificationId != 0) {
                            mNotificationManager.cancelAsUser(null, mUsbNotificationId,
                                                              UserHandle.ALL);
                            mUsbNotificationId = 0;
                        }
                        if (mAdbNotificationShown) {
                            mNotificationManager.cancelAsUser(null, com.android.internal.R.string.adb_active_notification_title,
                                                          UserHandle.ALL);
                            mAdbNotificationShown = false;
                        }
                    }

                    if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                        mPlugType = intent.getIntExtra("plugged", 0);
                        Slog.d(TAG, "onReceive - BATTERY_CHANGED - mPlugType: " + mPlugType +
                                     ", mSettingUsbCharging: " + mSettingUsbCharging + ", mConnected: " + mConnected +
                                     ", mSettingUsbBicr: " + mSettingUsbBicr);
                        /// M: Add for DUAL_INPUT_CHARGER_SUPPORT,
                        /// mplug is 3 when AC_online && USB_online are both true @{
                        if(SystemProperties.get("ro.mtk_diso_support").equals("true") && (mPlugType == 3)){
                            mHwDisconnected = false;
                            if (mBatteryChargingUnPlug) {
                                Slog.d(TAG, "onReceive - BATTERY_CHANGED - [IGNORE] mHwDisconnected: " + mHwDisconnected + ", mConnected: " + mConnected + ", mConfigured: " + mConfigured + ", mBatteryChargingUnPlug: " + mBatteryChargingUnPlug);
                                removeMessages(MSG_UPDATE_DISCONNECT_STATE);
                                mBatteryChargingUnPlug = false;
                            }
                            if (mSettingUsbCharging) {
                                if (mUsbNotificationId != com.mediatek.internal.R.string.usb_charging_notification_title) {
                                    Slog.d(TAG, "onReceive - BATTERY_CHANGED - [Update USB Notification] For [USB PLUGGED in USB Charging Mode] mSettingUsbCharging: " + mSettingUsbCharging + ", mPlugType: " + mPlugType);
                                    updateUsbNotification();
                                    updateAdbNotification();
                                }
                            }
                            return;
                        }
                        /// M:  @}
                        if (mPlugType != 2) {
                            mHwDisconnected = true;
                            if (mSettingUsbCharging) {
                                Slog.d(TAG, "onReceive - BATTERY_CHANGED - [Update USB Notification] For [USB UNPLUGGED in USB Charging Mode] mSettingUsbCharging: " + mSettingUsbCharging + ", mPlugType: " + mPlugType);
                                updateUsbNotification();
                                updateAdbNotification();
                            }
                            if (mSettingUsbBicr) {
                                Slog.d(TAG, "onReceive - BATTERY_CHANGED - [USB UNPLUGGED in USB BICR Mode]");
                                mSettingUsbBicr = false;
                                setCurrentFunctions(mDefaultFunctions, false);
                            }
                            if (!mSettingUsbCharging && !mSettingUsbBicr) {
                                if (mConnected) {
                                    mBatteryChargingUnPlug = true;
                                    removeMessages(MSG_UPDATE_STATE);
                                    removeMessages(MSG_UPDATE_DISCONNECT_STATE);
                                    Message msg = Message.obtain();
                                    msg.what = MSG_UPDATE_DISCONNECT_STATE;
                                    msg.arg1 = 0;
                                    msg.arg2 = 0;
                                    Slog.d(TAG, "onReceive - BATTERY_CHANGED - [HW USB Disconnected] mHwDisconnected: " + mHwDisconnected + ", mConnected: " + mConnected + ", mConfigured: " + mConfigured);
                                    sendMessageDelayed(msg, 1000);
                                }
                            }
                        } else {
                            mHwDisconnected = false;
                            if (mBatteryChargingUnPlug) {
                                Slog.d(TAG, "onReceive - BATTERY_CHANGED - [IGNORE] mHwDisconnected: " + mHwDisconnected + ", mConnected: " + mConnected + ", mConfigured: " + mConfigured + ", mBatteryChargingUnPlug: " + mBatteryChargingUnPlug);
                                removeMessages(MSG_UPDATE_DISCONNECT_STATE);
                                mBatteryChargingUnPlug = false;
                            }
                            if (mSettingUsbCharging) {
                                if (mUsbNotificationId != com.mediatek.internal.R.string.usb_charging_notification_title) {
                                    Slog.d(TAG, "onReceive - BATTERY_CHANGED - [Update USB Notification] For [USB PLUGGED in USB Charging Mode] mSettingUsbCharging: " + mSettingUsbCharging + ", mPlugType: " + mPlugType);
                                    updateUsbNotification();
                                    updateAdbNotification();
                                }
                            }
                        }
                    }
					/* HQ_ChenWenshuai 2015-11-03 modified for HQ01482929 begin */
					String state = null;
					try{
							state = FileUtils.readTextFile(new File(STATE_PATH), 0, null).trim();
						}catch(Exception e)
						{
							Slog.e(TAG,"Failed to read state of USB connection", e);
						}
					if(Intent.ACTION_USER_PRESENT.equals(intent.getAction()) && ("CONNECTED".equals(state) || "CONFIGURED".equals(state))){
						if(mCurrentFunctions.contains(UsbManager.USB_FUNCTION_MASS_STORAGE)){//chenwenshuai modify for HQ01482929
							try{
								StorageManager storageManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
			        			String storageState = storageManager.getVolumeState("/storage/sdcard1");
								if(!storageState.equals(android.os.Environment.MEDIA_UNMOUNTED)){
									mMountService.setUsbMassStorageEnabled(true);
								}
							} catch (Exception ex){
								Slog.e(TAG,"Failed to enable UMS", ex);
							}
						}
					}
					/*HQ_ChenWenshuai 2015-11-03 modified end */
                }
            }
        };

        private final BroadcastReceiver mUserSwitchedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                mHandler.obtainMessage(MSG_USER_SWITCHED, userId, 0).sendToTarget();
            }
        };
        //VIA-START VIA USB
        private final class Bypass{
            private static final String ACTION_USB_BYPASS_SETFUNCTION =
                    "com.via.bypass.action.setfunction";
            private static final String ACTION_USB_BYPASS_SETTETHERFUNCTION =
                    "com.via.bypass.action.settetherfunction";			
            private static final String VALUE_ENABLE_BYPASS =
                    "com.via.bypass.enable_bypass";
            private static final String ACTION_USB_BYPASS_SETBYPASS =
                    "com.via.bypass.action.setbypass";
            private static final String ACTION_USB_BYPASS_SETBYPASS_RESULT =
                    "com.via.bypass.action.setbypass_result";
            private static final String VALUE_ISSET_BYPASS =
                    "com.via.bypass.isset_bypass";
            private static final String ACTION_USB_BYPASS_GETBYPASS =
                    "com.via.bypass.action.getbypass";
            private static final String ACTION_USB_BYPASS_GETBYPASS_RESULT =
                    "com.via.bypass.action.getbypass_result";
            private static final String VALUE_BYPASS_CODE =
                    "com.via.bypass.bypass_code";
            private static final String ACTION_VIA_ETS_DEV_CHANGED =
                    "via.cdma.action.ets.dev.changed";
            private static final String ACTION_RADIO_AVAILABLE =
                    "android.intent.action.RADIO_AVAILABLE";

            /*VIA-START SET ETS*/
            private static final String ACTION_VIA_SET_ETS_DEV =
                    "via.cdma.action.set.ets.dev";
            private static final String EXTRAL_VIA_ETS_DEV =
                    "via.cdma.extral.ets.dev";
            /*VIA-END SET ETS*/

            private static final String USB_FUNCTION_BYPASS = "via_bypass";
            public static final String USB_FUNCTION_USERMODE = UsbManager.USB_FUNCTION_MTP;

            /*Bypass function values*/
            private File[] mBypassFiles;
            private final int[] mBypassCodes = new int[]{1,2,4,8,16};
            private final String[] mBypassName = new String[]{"gps","pcv","atc","ets","data"};
            private int mBypassAll = 0;
            private int mBypassToSet;
            private boolean mEtsDevInUse = false;

            private final BroadcastReceiver mBypassReceiver = new BroadcastReceiver()
            {
                @Override
                public void onReceive(Context context, Intent intent)
                {
                    if (DEBUG) Slog.i(TAG,"onReceive="+intent.getAction());
                    if (intent.getAction() != null) {
                        if (intent.getAction().equals(ACTION_USB_BYPASS_SETFUNCTION)) {
                            Boolean enablebypass = intent.getBooleanExtra(VALUE_ENABLE_BYPASS, false);
                            if (enablebypass) {
                                setCurrentFunctions(USB_FUNCTION_BYPASS,false);
                            } else {
                                closeBypassFunction();
                            }
                        }else if (intent.getAction().equals(ACTION_USB_BYPASS_SETTETHERFUNCTION)){
                            Boolean enablebypass = intent.getBooleanExtra(VALUE_ENABLE_BYPASS, false);
                            ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

                            if (enablebypass) {
                                Slog.i(TAG,"Enable the byass with Tethering");
                                mUsbSetBypassWithTether = true ;		
                                cm.setUsbTethering(true) ;
                            } else {
                                Slog.i(TAG,"disable the byass with Tethering");
                                updateBypassMode(0);
                                cm.setUsbTethering(false) ;
                            }
                        } else if (intent.getAction().equals(ACTION_USB_BYPASS_SETBYPASS)) {
                            int bypasscode = intent.getIntExtra(VALUE_BYPASS_CODE,-1);
                            if (bypasscode>=0 && bypasscode<=mBypassAll){
                                setBypassMode(bypasscode);
                            } else {
                                notifySetBypassResult(false,getCurrentBypassMode());
                            }
                        } else if (intent.getAction().equals(ACTION_USB_BYPASS_GETBYPASS)) {
                            Intent reintent = new Intent(ACTION_USB_BYPASS_GETBYPASS_RESULT);
                            reintent.putExtra(VALUE_BYPASS_CODE, getCurrentBypassMode());
                            mContext.sendBroadcast(reintent);
                        } else if(intent.getAction().equals(ACTION_VIA_ETS_DEV_CHANGED)) {
                            boolean result = intent.getBooleanExtra("set.ets.dev.result", false);
                            int bypass;
                            if (result) {
                                //setBypass(mBypassToSet);
                                bypass = mBypassToSet;
                            } else {
                                //setBypass(currentBypass);
                                bypass = getCurrentBypassMode();
                            }
                            Message m = Message.obtain(mHandler, MSG_SET_BYPASS);
                            m.arg1 = bypass;
                            sendMessage(m);
                        } else if(intent.getAction().equals(ACTION_RADIO_AVAILABLE)) {
                            if (mEtsDevInUse) {
                                Intent reintent = new Intent(ACTION_VIA_SET_ETS_DEV);
                                reintent.putExtra(EXTRAL_VIA_ETS_DEV, 1);
                                mContext.sendBroadcast(reintent);
                            }
                        }
                    }
                }
            };

            public Bypass() {

                mBypassFiles = new File[mBypassName.length];
                for(int i=0;i<mBypassName.length;i++){
                    final String path = "/sys/class/usb_rawbulk/"+mBypassName[i]+"/enable";
                    //if (DEBUG) Slog.d(TAG, "bypass mode file path="+path);
                    mBypassFiles[i] = new File(path);
                    mBypassAll += mBypassCodes[i];
                }
                if(bEvdoDtViaSupport == true){
                    //register bypass receiver
                    IntentFilter intent = new IntentFilter(ACTION_USB_BYPASS_SETFUNCTION);
                    intent.addAction(ACTION_USB_BYPASS_SETTETHERFUNCTION);
                    intent.addAction(ACTION_USB_BYPASS_SETBYPASS);
                    intent.addAction(ACTION_USB_BYPASS_GETBYPASS);
                    intent.addAction(ACTION_VIA_ETS_DEV_CHANGED);
                    intent.addAction(ACTION_RADIO_AVAILABLE);
                    mContext.registerReceiver(mBypassReceiver,intent);
                }
            }
            private int getCurrentBypassMode(){
                int bypassmode = 0;
                try {
                    for(int i=0;i<mBypassCodes.length;i++){
                        String code;
                        if(i==2){
                           code = SystemProperties.get("sys.cp.bypass.at","0");
                        }else{
                           code = FileUtils.readTextFile(mBypassFiles[i],0,null);
                        }
                        //if (DEBUG) Slog.d(TAG, "'"+mBypassFiles[i].getAbsolutePath()+"' value is "+code);
                        if(code!=null && code.trim().equals("1")){
                            bypassmode |= mBypassCodes[i];
                        }
                    }
                    if (DEBUG) Slog.d(TAG, "getCurrentBypassMode()="+bypassmode);
                } catch (IOException e) {
                   Slog.e(TAG, "failed to read bypass mode code!");
                }
                return bypassmode;
            }

            private void setBypass(int bypassmode) {
                Slog.d(TAG, "setBypass bypass = "+ bypassmode);
                int bypassResult = getCurrentBypassMode();
                if(bypassmode == bypassResult){
                    Slog.d(TAG, "setBypass bypass == oldbypass!!");
                    notifySetBypassResult(true,bypassResult);
                    return;
                }

                try {
                    for(int i=0;i<mBypassCodes.length;i++){
                        if((bypassmode & mBypassCodes[i]) != 0){
                            if(i==2){
                                SystemProperties.set("sys.cp.bypass.at", "1");
                            }else{
                                FileUtils.stringToFile(mBypassFiles[i].getAbsolutePath(), "1");
                            }
                            bypassResult |= mBypassCodes[i];
                        }else{
                            if(i==2){
                                SystemProperties.set("sys.cp.bypass.at", "0");
                            }else{
                                FileUtils.stringToFile(mBypassFiles[i].getAbsolutePath(), "0");
                            }
                            if((bypassResult & mBypassCodes[i]) != 0)
                                bypassResult ^= mBypassCodes[i];
                        }
                        if (DEBUG)Slog.d(TAG, "Write '"+mBypassFiles[i].getAbsolutePath()+"' successsfully!");
                    }
                    notifySetBypassResult(true,bypassResult);
                    Slog.d(TAG, "setBypass success bypassResult = " + bypassResult);
                } catch (IOException e) {
                   Slog.e(TAG, "failed to operate bypass!");
                   notifySetBypassResult(false,bypassResult);
                }
            }

            void updateBypassMode(int bypassmode){
                Slog.d(TAG, "updateBypassMode");
                //Open/Close ets port for pc
                if(!setEtsDev(bypassmode))
                {
                  //if needn't Open/Close ets port for pc set bypass code now
                  setBypass(bypassmode);
                }
                else
                {
                  Slog.d(TAG, "updateBypassMode mBypassToSet = " + mBypassToSet);
                  mBypassToSet = bypassmode;
                }
            }

            private boolean setEtsDev(int bypass){
                int oldBypass = getCurrentBypassMode();
                Slog.d(TAG, "setEtsDev bypass = " + bypass + " oldBypass = " + oldBypass);
                if((bypass&mBypassCodes[3])!=0 && (oldBypass&mBypassCodes[3])==0 ){
                    Slog.d(TAG, "setEtsDev mEtsDevInUse = true");
                    Intent reintent = new Intent(ACTION_VIA_SET_ETS_DEV);
                    reintent.putExtra(EXTRAL_VIA_ETS_DEV, 1);
                    mContext.sendBroadcast(reintent);
                    mEtsDevInUse = true;
                    return true;
                }else if((bypass&mBypassCodes[3])==0 &&
                        (oldBypass&mBypassCodes[3])!=0) {
                    Slog.d(TAG, "setEtsDev mEtsDevInUse = false");
                    Intent reintent = new Intent(ACTION_VIA_SET_ETS_DEV);
                    reintent.putExtra(EXTRAL_VIA_ETS_DEV, 0);
                    mContext.sendBroadcast(reintent);
                    mEtsDevInUse = false;
                    return true;
                }
                else {
                    return false;
                }
            }

            /*Set bypass mode*/
            private void setBypassMode(int bypassmode){
                if (DEBUG) Slog.d(TAG, "setBypassMode()="+bypassmode);
                Message m = Message.obtain(mHandler, MSG_SET_BYPASS_MODE);
                m.arg1 = bypassmode;
                sendMessage(m);
            }
            private void notifySetBypassResult(Boolean isset,int bypassCode){
                if (mBootCompleted) {
                    Intent intent = new Intent(ACTION_USB_BYPASS_SETBYPASS_RESULT);
                    intent.putExtra(VALUE_ISSET_BYPASS, isset);
                    intent.putExtra(VALUE_BYPASS_CODE, bypassCode);
                    mContext.sendBroadcast(intent);
                }
            }
            void handleCTClinet(){
                String ctclientStatus = SystemProperties.get("net.ctclientd","disable");
                if (DEBUG)Slog.d(TAG,"handleCTClinet() mConnected="+mConnected
                    +",mCurrentFunctions="+mCurrentFunctions
                    +",ctclientStatus="+ctclientStatus);
                //when usb connnected and not bypass+at
                if(mConnected){
                    if(ctclientStatus.equals("disable")){
                        if (DEBUG)Slog.d(TAG,"enable ctclientd");
                        SystemProperties.set("net.ctclientd","enable");
                    }
                } else {
                    if(ctclientStatus.equals("enable")){
                        SystemProperties.set("net.ctclientd","disable");
                        if (DEBUG)Slog.d(TAG,"disable ctclientd");
                    }
                }
            }

            void closeBypassFunction(){
                if (DEBUG) Slog.d(TAG, "closeBypassFunction() CurrentFunctions = " +
                                   mCurrentFunctions+",DefaultFunctions="+mDefaultFunctions);
                updateBypassMode(0);
                if(mCurrentFunctions.contains(USB_FUNCTION_BYPASS)){
                    setEnabledFunctions(mDefaultFunctions, false);
                }
            }
        }
        //VIA-END VIA USB

        public UsbHandler(Looper looper) {
            super(looper);
            try {
                //VIA-START VIA USB
                if(bEvdoDtViaSupport == true){
                    mBypass = new Bypass();
                }
                //VIA-END VIA USB
                // persist.sys.usb.config should never be unset.  But if it is, set it to "adb"
                // so we have a chance of debugging what happened.

                // if "persist.sys.usb.config" is empty or get property, set the default to mtp,adb
                //otherwise, the USB function switch menu would be disappear!!
                //ALPS00384287
                mDefaultFunctions = SystemProperties.get("persist.sys.usb.config", "mtp,adb");
                //ALPS00384287

                // Check if USB mode needs to be overridden depending on OEM specific bootmode.
                mDefaultFunctions = processOemUsbOverride(mDefaultFunctions);

                mAdbEnabled = containsFunction(mDefaultFunctions, UsbManager.USB_FUNCTION_ADB);
                mAcmEnabled = containsFunction(mDefaultFunctions, UsbManager.USB_FUNCTION_ACM);
                mAcmPortIdx = "";

                if (DEBUG) Slog.d(TAG, "UsbHandler - mDefaultFunctions: " + mDefaultFunctions);

                if (mIsUsbBicrEvo) {
                    mDefaultFunctions = UsbManager.USB_FUNCTION_BICR;
                    if (mAdbEnabled)
                        mDefaultFunctions = addFunction(mDefaultFunctions, UsbManager.USB_FUNCTION_ADB);
                    else
                        mDefaultFunctions = removeFunction(mDefaultFunctions, UsbManager.USB_FUNCTION_ADB);
                }

                // sanity check the sys.usb.config system property
                // this may be necessary if we crashed while switching USB configurations
                String config = SystemProperties.get("sys.usb.config", "none");
                if (!config.equals(mDefaultFunctions)) {
                    Slog.w(TAG, "resetting config to persistent property: " + mDefaultFunctions);
                    SystemProperties.set("sys.usb.config", mDefaultFunctions);
                }

				/* FIXME, chg for L1, to check, mDefault to getDefault()*/
                mCurrentFunctions = getDefaultFunctions();
                mSettingFunction = mCurrentFunctions;
                String state = FileUtils.readTextFile(new File(STATE_PATH), 0, null).trim();
                updateState(state);

                // Upgrade step for previous versions that used persist.service.adb.enable
                String value = SystemProperties.get("persist.service.adb.enable", "");
                if (DEBUG) Slog.d(TAG, "persist.service.adb.enable:" + value);
                if (value.length() > 0) {
                    char enable = value.charAt(0);
                    if (enable == '1') {
                        setAdbEnabled(true);
                    } else if (enable == '0') {
                        setAdbEnabled(false);
                    }
                    if(!mIsUsbBicrEvo) SystemProperties.set("persist.service.adb.enable", "");
                }

                value = SystemProperties.get("ro.usb.adb.auto_enable", "");
                if (value.length() > 0) {
                    char enable = value.charAt(0);
                    if (enable == '1') {
                        setAdbEnabled(true);
                    } else if (enable == '0') {
                        setAdbEnabled(false);
                    }
                }

                /* VIA move this setence from systemReady() to here*/
                // make sure the ADB_ENABLED setting value matches the current state(move from systemready())
                if(bEvdoDtViaSupport == true){
                    Settings.Secure.putInt(mContentResolver, Settings.Secure.ADB_ENABLED, mAdbEnabled ? 1 : 0);
                }

                value = SystemProperties.get("persist.service.acm.enable", "");
                if (DEBUG) Slog.d(TAG, "persist.service.acm.enable:" + value);
                if (value.length() > 0) {
                    char enable = value.charAt(0);
                    if (enable == '1') {
                        setAcmEnabled(true);
                    } else if (enable == '0') {
                        setAcmEnabled(false);
                    }
                    SystemProperties.set("persist.service.acm.enable", "");
                }


				/*	mark this, port_index for acm is not used currently
                value = SystemProperties.get("persist.sys.port_index", "");
                if (DEBUG) Slog.d(TAG, "persist.sys.port_index:" + value);
                if (value != null && !value.isEmpty() && validPortNum(value) > 0) {
                    int port_num = validPortNum(value);
                    mAcmPortIdx = value;
                    writeFile(ACM_PORT_INDEX_PATH, mAcmPortIdx);

                    // Add ACM or Dual ACM at the tail. Even with ADB.
                    mDefaultFunctions = removeFunction(mDefaultFunctions, UsbManager.USB_FUNCTION_ACM);
                    mDefaultFunctions = removeFunction(mDefaultFunctions, UsbManager.USB_FUNCTION_DUAL_ACM);

                    String tmp = ( (port_num == 2) ? UsbManager.USB_FUNCTION_DUAL_ACM : UsbManager.USB_FUNCTION_ACM);
                    mDefaultFunctions = addFunction(mDefaultFunctions, tmp);

                    SystemProperties.set("sys.usb.config", mDefaultFunctions);
                }*/

                value = SystemProperties.get("persist.radio.port_index", "");
                if (DEBUG) Slog.d(TAG, "persist.radio.port_index:" + value);
                if (value != null && !value.isEmpty() && validPortNum(value) > 0) {
                    mAcmPortIdx = value;
                    writeFile(ACM_PORT_INDEX_PATH, mAcmPortIdx);
                    SystemProperties.set("sys.usb.config", mDefaultFunctions);
                }

                // register observer to listen for settings changes
                mContentResolver.registerContentObserver(
                        Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
                                false, new AdbSettingsObserver());

                mContentResolver.registerContentObserver(
                        Settings.Global.getUriFor(Settings.Global.ACM_ENABLED),
                                false, new AcmSettingsObserver());

                IntentFilter filter = new IntentFilter();

                if (bMtkIpoSupport == true) {
                    filter.addAction(IPO_POWER_ON);
                    filter.addAction(IPO_POWER_OFF);
                }

                filter.addAction(Intent.ACTION_BOOT_COMPLETED);
                filter.addAction(Intent.ACTION_BATTERY_CHANGED);
				filter.addAction(Intent.ACTION_USER_PRESENT);//chenwenshuai add for HQ01482929
                filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

                mContext.registerReceiver(mIntentReceiver, filter);
                mContext.registerReceiver(
                        mUserSwitchedReceiver, new IntentFilter(Intent.ACTION_USER_SWITCHED));
                //VIA-START VIA USB
                if(bEvdoDtViaSupport == true
                               &&  mAutoCdromEnable){
                    sendEmptyMessage(MSG_SET_VIA_CDROM);
                }
                //VIA-END VIA USB
            } catch (Exception e) {
                Slog.e(TAG, "Error initializing UsbHandler", e);
            }
        }

        public void sendMessage(int what, boolean arg) {
            removeMessages(what);
            Message m = Message.obtain(this, what);
            m.arg1 = (arg ? 1 : 0);
            sendMessage(m);
        }

        public void sendMessage(int what, Object arg) {
            removeMessages(what);
            Message m = Message.obtain(this, what);
            m.obj = arg;
            sendMessage(m);
        }

        public void sendMessage(int what, Object arg0, boolean arg1) {
            removeMessages(what);
            Message m = Message.obtain(this, what);
            m.obj = arg0;
            m.arg1 = (arg1 ? 1 : 0);
            sendMessage(m);
        }

        public void updateState(String state) {
            int connected, configured;

            if (DEBUG) Slog.d(TAG, "updateState - " + state);
            Message msg;

            if ("HWDISCONNECTED".equals(state)) {
                connected = 0;
                configured = 0;
                mHwDisconnected = true;
                msg = Message.obtain(this, MSG_UPDATE_DISCONNECT_STATE);
            } else if ("DISCONNECTED".equals(state)) {
                connected = 0;
                configured = 0;
                mHwDisconnected = false;
                mIsBicrSet = false;
                msg = Message.obtain(this, MSG_UPDATE_DISCONNECT_STATE);
            } else if ("CONNECTED".equals(state)) {
                connected = 1;
                configured = 0;
                mHwDisconnected = false;
                mIsBicrSet = false;
                msg = Message.obtain(this, MSG_UPDATE_STATE);
            } else if ("CONFIGURED".equals(state)) {
                connected = 1;
                configured = 1;
                mHwDisconnected = false;
                mIsBicrSet = false;

				/* HQ_ChenWenshuai 2015-09-17 modified for HQ01352311 begin */
				boolean isKeyguard = mKeyguardManager.isKeyguardSecure() && mKeyguardManager.inKeyguardRestrictedInputMode();//chenwenshuai modify for HQ01482929
				if(mCurrentFunctions.contains(UsbManager.USB_FUNCTION_MASS_STORAGE) && !isKeyguard){//chenwenshuai modify for HQ01482929
					try{
						StorageManager storageManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
	        			String storageState = storageManager.getVolumeState("/storage/sdcard1");
						if(!storageState.equals(android.os.Environment.MEDIA_UNMOUNTED)){
							mMountService.setUsbMassStorageEnabled(true);
						}
					} catch (Exception ex){
						Slog.e(TAG,"Failed to enable UMS", ex);
					}
				}
				/*HQ_ChenWenshuai 2015-09-17 modified end */

                msg = Message.obtain(this, MSG_UPDATE_STATE);
            } else if ("REZEROCMD".equals(state)) {
                /*When recieve REZEROCMD, it means that PC has installed a proper driver, so can switch to other USB function(UMS)*/
                Slog.w(TAG, "PC knows me");
                mIsPcKnowMe = true;
                setCurrentFunctions(UsbManager.USB_FUNCTION_MASS_STORAGE, false);
                return;
            } else if ("SHOWCDROMCMD".equals(state)) {
                /*When recieve REZEROCMD, it means that PC has installed a proper driver, so can switch to other USB function(UMS)*/
                Slog.w(TAG, "SHOW CD-ROM CMD");
                setCurrentFunctions(UsbManager.USB_FUNCTION_BICR, false);
                return;
            //ALPS00428998
            } else if ("MTPASKDISCONNECT".equals(state)) {
                /*When recieve MTPASKDISCONNECT, it means that PC has installed a proper driver, so can switch to other USB function(UMS)*/
                Slog.w(TAG, "MTPASKDISCONNECT");
                mMtpAskDisconnect = true;
                //setCurrentFunctions(UsbManager.USB_FUNCTION_MTP, false);
                Slog.w(TAG, "mCurrentFunctions = " + mCurrentFunctions);
                setCurrentFunctions(mCurrentFunctions, false);
                return;
            //ALPS00428998
            } else {
                Slog.e(TAG, "unknown state " + state);
                return;
            }
            removeMessages(MSG_UPDATE_STATE);
            removeMessages(MSG_UPDATE_DISCONNECT_STATE);
            msg.arg1 = connected;
            msg.arg2 = configured;
            // debounce disconnects to avoid problems bringing up USB tethering
            if (mHwDisconnected || mSettingUsbCharging) {
                Slog.d(TAG, "updateState - UPDATE_DELAY  " + state + " mSettingFunction: " + mSettingFunction);
                if(mIsUsbBicrEvo && mIsPcKnowMe) {
                    sendMessageDelayed(msg, (connected == 0) ? UPDATE_DELAY_BICR : 0);
                    Slog.w(TAG, "Delay!!!!" + UPDATE_DELAY_BICR );
                } else {
                    sendMessageDelayed(msg, (connected == 0) ? UPDATE_DELAY : 0);
                }
            }
            else {
                if (mIsUsbSimSecurity) {
                    String value = SystemProperties.get("persist.sys.usb.activation", "no");
                    if (value.equals("no")) {
                        Slog.d(TAG, "Usb is non-activated!");
                        sendMessageDelayed(msg, 0);
                    } else {
                        Slog.d(TAG, "updateState - RNDIS_UPDATE_DELAY  " + state + " mSettingFunction: " + mSettingFunction);
                        sendMessageDelayed(msg, (connected == 0) ? RNDIS_UPDATE_DELAY : 0);
                    }
                } else {
                    Slog.d(TAG, "updateState - RNDIS_UPDATE_DELAY  " + state + " mSettingFunction: " + mSettingFunction);
                    sendMessageDelayed(msg, (connected == 0) ? RNDIS_UPDATE_DELAY : 0);
                }
            }
        }

        private boolean waitForState(String state) {
            // wait for the transition to complete.
            // give up after 1 second.
            for (int i = 0; i < 40; i++) {
                // State transition is done when sys.usb.state is set to the new configuration
                if (state.equals(SystemProperties.get("sys.usb.state"))) return true;
                SystemClock.sleep(50);
            }
            Slog.e(TAG, "waitForState(" + state + ") FAILED");
            return false;
        }

        private boolean setUsbConfig(String config) {
            if (DEBUG) Slog.d(TAG, "setUsbConfig(" + config + ")");
            // set the new configuration
            if (DEBUG) Slog.d(TAG, "setUsbConfig - config: " + config);
            SystemProperties.set("sys.usb.config", config);
            return waitForState(config);
        }

        private void setAdbEnabled(boolean enable) {
            if (DEBUG) Slog.d(TAG, "setAdbEnabled: " + enable);
            if (enable != mAdbEnabled) {
                mAdbEnabled = enable;
                // Due to the persist.sys.usb.config property trigger, changing adb state requires
                // switching to default function
                if (containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_BICR)) {
                    setEnabledFunctions(mCurrentFunctions, false);
                    updateAdbNotification();
                } else if (mIsUsbBicrEvo && mIsPcKnowMe) {
                    /* In BICR evo, the default function is always as bicr. So when enable or disable adb,*/
                    /* Use mCurrentFunctions to be the USB function that user wants to switch to.*/
                    if (containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_RNDIS) || containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_EEM)) {
                        setEnabledFunctions(UsbManager.USB_FUNCTION_MASS_STORAGE, false);
                    } else {
                        setEnabledFunctions(mCurrentFunctions, false);
                    }
                    updateAdbNotification();
                } else if (!mCurrentFunctions.equals(UsbManager.USB_FUNCTION_CHARGING_ONLY)) {
					if (mDefaultFunctions.equals(UsbManager.USB_FUNCTION_CHARGING_ONLY)) {
                        mSettingUsbCharging = true;
                    }
                setEnabledFunctions(mDefaultFunctions, true);
                // After persisting them use the lock-down aware function set
				/* FIXME, L1 new behavior but temporally marked */
                //setEnabledFunctions(getDefaultFunctions(), false);
                updateAdbNotification();
				} else if (mCurrentFunctions.equals(UsbManager.USB_FUNCTION_CHARGING_ONLY)) {
					if (mAdbEnabled) {
						SystemProperties.set("persist.service.adb.enable", "1");
					} else {
						SystemProperties.set("persist.service.adb.enable", "0");
					}
					return;
				}
                /* ADB setting should keep in mind when bicr evo is defined.*/
                /* Cuz after power on/off, the device should know adb is on/off.*/
                if(mIsUsbBicrEvo) {
                    if (mAdbEnabled) {
                        SystemProperties.set("persist.service.adb.enable", "1");
                    } else {
                        SystemProperties.set("persist.service.adb.enable", "0");
                    }
            }
            if (mDebuggingManager != null) {
                mDebuggingManager.setAdbEnabled(mAdbEnabled);
            }
        }
        }

        private void setAcmEnabled(boolean enable) {
            if (DEBUG) Slog.d(TAG, "setAcmEnabled: " + enable);
            if (enable != mAcmEnabled) {
                mAcmEnabled = enable;
                // Due to the persist.sys.usb.config property trigger, changing adb state requires
                // switching to default function
                setEnabledFunctions(mDefaultFunctions, true);
            }
        }

        private void writeFile(String path, String data) {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(path);
                fos.write(data.getBytes());
            } catch (IOException e) {
                Slog.w(TAG, "Unable to write " + path);
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        Slog.w(TAG, "Unable to close fos at path: " + path);
                    }
                }
            }
        }

        private void setEnabledFunctions(String functions, boolean makeDefault) {
            if (DEBUG) {
                Slog.d(TAG, "setEnabledFunctions - functions: " + functions);
                Slog.d(TAG, "setEnabledFunctions - mDefaultFunctions: " + mDefaultFunctions);
                Slog.d(TAG, "setEnabledFunctions - mCurrentFunctions: " + mCurrentFunctions);
                Slog.d(TAG, "setEnabledFunctions - mSettingFunction: " + mSettingFunction);
            }

            if (mCurrentFunctions.equals(UsbManager.USB_FUNCTION_CHARGING_ONLY)) {
                Slog.d(TAG, "setEnabledFunctions - [Disable USB Charging]");
                SystemProperties.set("sys.usb.charging","no");
            }

            if (containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_BICR)) {
                if (mIsUsbBicrEvo) {
                    Slog.w(TAG, "====mIsPcKnowMe:" + mIsPcKnowMe + ", mIsBicrSet:" + mIsBicrSet + ", mHwDisconnected:" + mHwDisconnected);
                    if (mIsBicrSet && mHwDisconnected) {
                        Slog.d(TAG, "Do not set sys.usb.bicr=no again!!!");
                    } else {
                        Slog.d(TAG, "setEnabledFunctions1 - [Disable USB BICR]");
                        SystemProperties.set("sys.usb.bicr","no");
                    }
                } else {
                    Slog.d(TAG, "setEnabledFunctions2 - [Disable USB BICR]");
                    SystemProperties.set("sys.usb.bicr","no");
                }
            }

            if (mIsUsbBicrEvo && functions != null) {
                if( containsFunction(functions, UsbManager.USB_FUNCTION_RNDIS) || containsFunction(functions, UsbManager.USB_FUNCTION_EEM) ) {
                    mDefaultFunctions = mSettingFunction;
                }
            }
            // Do not update persystent.sys.usb.config if the device is booted up
            // with OEM specific mode.
            if (functions != null && makeDefault && !needsOemUsbOverride() && !mIsUsbBicrEvo) {
                if(bEvdoDtViaSupport == true) {
                    //VIA-START VIA USB
                    if(Settings.Secure.getInt(mContentResolver,
                        Settings.Secure.ADB_ENABLED, 0) > 0){
                        mAdbEnabled = true;
                        updateAdbNotification();
                    }
                    //VIA-END VIA USB
                }

                mSettingFunction = functions;
                if (mAdbEnabled && !mSettingUsbCharging) {
                    functions = addFunction(functions, UsbManager.USB_FUNCTION_ADB);
                } else {
                    functions = removeFunction(functions, UsbManager.USB_FUNCTION_ADB);
                }

                String acm_idx = SystemProperties.get("sys.usb.acm_idx", "");
                Slog.d(TAG, "sys.usb.acm_idx=" + acm_idx + ",mAcmPortIdx=" + mAcmPortIdx);
                if ((mAcmEnabled || ((acm_idx!=null) && !acm_idx.isEmpty()) ||
                                  ((mAcmPortIdx!=null) && !mAcmPortIdx.isEmpty())) &&
                                  !mSettingUsbCharging && !mSettingUsbBicr) {
                    int port_num = 0;
                    String port_str = "";
                    if(!acm_idx.isEmpty()) {
                        port_num = validPortNum(acm_idx);
                        if(port_num > 0 ) {
                            port_str = acm_idx;
                            mAcmPortIdx = acm_idx;
                        }
                    } else if(!mAcmPortIdx.isEmpty()) {
                        port_num = validPortNum(mAcmPortIdx);
                        if(port_num > 0 )
                            port_str = mAcmPortIdx;
                    }

                    Slog.d(TAG, "port_num=" + port_num);
                    if (port_num > 0) {
                        Slog.d(TAG, "Write port_str=" + port_str);
                        writeFile(ACM_PORT_INDEX_PATH, port_str);
                    }

                    /*Add ACM or Dual ACM at the tail. Even with ADB.*/
                    functions = removeFunction(functions, UsbManager.USB_FUNCTION_ACM);
                    functions = removeFunction(functions, UsbManager.USB_FUNCTION_DUAL_ACM);

                    String tmp = ( (port_num == 2) ? UsbManager.USB_FUNCTION_DUAL_ACM : UsbManager.USB_FUNCTION_ACM);
                    functions = addFunction(functions, tmp);

                    //Slog.d(TAG, "set persist.sys.port_index=" + port_str);
                    /*Keep the port info. We need it when reboot.*/
                    //SystemProperties.set("persist.sys.port_index", port_str);

                } else {
                    functions = removeFunction(functions, UsbManager.USB_FUNCTION_ACM);
                    functions = removeFunction(functions, UsbManager.USB_FUNCTION_DUAL_ACM);

                    SystemProperties.set("persist.sys.port_index", "");
                }
                Slog.d(TAG, "setEnabledFunctions - functions: " + functions);
                if (!mDefaultFunctions.equals(functions) || containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_BICR) || containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_RNDIS)) {
                    if (!setUsbConfig("none")) {
                        Slog.e(TAG, "Failed to disable USB");
                        // revert to previous configuration if we fail
                        setUsbConfig(mCurrentFunctions);
                        return;
                    }
                    // setting this property will also change the current USB state
                    // via a property trigger
                    SystemProperties.set("persist.sys.usb.config", functions);

                    if (mSettingFunction.equals(UsbManager.USB_FUNCTION_CHARGING_ONLY)) {
                        if (mAdbEnabled) {
                            SystemProperties.set("persist.service.adb.enable", "1");
                        } else {
                            SystemProperties.set("persist.service.adb.enable", "0");
                        }
                    } else {
                        SystemProperties.set("persist.service.adb.enable", "");
                    }

                    if (waitForState(functions)) {
                        mCurrentFunctions = functions;
                        mDefaultFunctions = functions;
                    } else {
                        Slog.e(TAG, "Failed to switch persistent USB config to " + functions);
                        // revert to previous configuration if we fail
                        SystemProperties.set("persist.sys.usb.config", mDefaultFunctions);
                    }
                } else {
                    //VIA-START VIA USB
                    if(bEvdoDtViaSupport == true) {
                        if(mDefaultFunctions.equals(functions) &&
                        (mCurrentFunctions.contains(UsbManager.USB_FUNCTION_VIA_CDROM)
                        ||mCurrentFunctions.contains(Bypass.USB_FUNCTION_BYPASS))){
                            Slog.i(TAG, "reset DefaultFunctions!");
                            if (!setUsbConfig("none")) {
                                Slog.e(TAG, "Failed to disable USB");
                                // revert to previous configuration if we fail
                                setUsbConfig(mCurrentFunctions);
                                return;
                            }
                            if (setUsbConfig(functions)) {
                                mCurrentFunctions = functions;
                            } else {
                                Slog.e(TAG, "Failed to switch USB config to " + functions);
                                // revert to previous configuration if we fail
                                setUsbConfig(mCurrentFunctions);
                            }
                        }
                    }
                    //VIA-END VIA USB
                }
            } else {
                if (functions == null) {
                    functions = mDefaultFunctions;
                    if(bEvdoDtViaSupport == true){
                        mUsbSetBypassWithTether=false ;
                    }
                }
                //VIA-START VIA USB
                if(bEvdoDtViaSupport == true) {
                    //adb can't be open with bypass and via cdrom
                    if(Settings.Secure.getInt(mContentResolver,
                        Settings.Secure.ADB_ENABLED, 0) > 0){
                        mAdbEnabled = true;
                        updateAdbNotification();
                    }
                }
                //VIA-END VIA USB
                // Override with bootmode specific usb mode if needed
                functions = processOemUsbOverride(functions);
                mSettingFunction = functions;

                if(bEvdoDtViaSupport == true){
                    if ((containsFunction(functions, UsbManager.USB_FUNCTION_RNDIS) || containsFunction(functions, UsbManager.USB_FUNCTION_EEM)) && mUsbSetBypassWithTether){					
                        functions = addFunction(functions, Bypass.USB_FUNCTION_BYPASS);
                        Slog.d(TAG, "add the bypass functions to tethering : " + functions);
                    }
                }

                if (mIsUsbBicrEvo && !mIsPcKnowMe && !mSettingUsbCharging) {
                    functions = UsbManager.USB_FUNCTION_BICR;
                    mSettingFunction = functions;
                }

                if (mAdbEnabled && !mSettingUsbCharging) {
                    functions = addFunction(functions, UsbManager.USB_FUNCTION_ADB);
                } else {
                    functions = removeFunction(functions, UsbManager.USB_FUNCTION_ADB);
                }

                String acm_idx = SystemProperties.get("sys.usb.acm_idx", "");
                Slog.d(TAG, "sys.usb.acm_idx=" + acm_idx);
                if ((mAcmEnabled || ((acm_idx!=null) && !acm_idx.isEmpty()) ||
                                  ((mAcmPortIdx!=null) && !mAcmPortIdx.isEmpty())) &&
                                  !mSettingUsbCharging && !mSettingUsbBicr) {
                    int port_num = 0;
                    String port_str = "";
                    if(!acm_idx.isEmpty()) {
                        port_num = validPortNum(acm_idx);
                        if(port_num > 0 ) {
                            port_str = acm_idx;
                            mAcmPortIdx = acm_idx;
                        }

                    } else if(!mAcmPortIdx.isEmpty()) {
                        port_num = validPortNum(mAcmPortIdx);
                        if(port_num > 0 )
                            port_str = mAcmPortIdx;
                    }

                    Slog.d(TAG, "port_num=" + port_num);
                    if (port_num > 0) {
                        Slog.d(TAG, "Write port_str=" + port_str);
                        writeFile(ACM_PORT_INDEX_PATH, port_str);
                    }

                    /*Add ACM or Dual ACM at the tail. Even with ADB.*/
                    functions = removeFunction(functions, UsbManager.USB_FUNCTION_ACM);
                    functions = removeFunction(functions, UsbManager.USB_FUNCTION_DUAL_ACM);

                    String tmp = ( (port_num == 2) ? UsbManager.USB_FUNCTION_DUAL_ACM : UsbManager.USB_FUNCTION_ACM);
                    functions = addFunction(functions, tmp);
                } else {
                    functions = removeFunction(functions, UsbManager.USB_FUNCTION_ACM);
                    functions = removeFunction(functions, UsbManager.USB_FUNCTION_DUAL_ACM);
                }

                Slog.d(TAG, "else setEnabledFunctions, functions: " + functions + ", mCurrentFunctions: " + mCurrentFunctions);
                if (!mCurrentFunctions.equals(functions) || containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_BICR) || (mIsUsbBicrEvo && !mIsPcKnowMe) || mMtpAskDisconnect) {
                    if (mIsUsbBicrEvo) {
                        Slog.w(TAG, "====mIsPcKnowMe:" + mIsPcKnowMe + ", mIsBicrSet:" + mIsBicrSet + ", mHwDisconnected:" + mHwDisconnected);
                        if (mIsBicrSet && !mIsPcKnowMe && mHwDisconnected && mCurrentFunctions.equals(functions) && containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_BICR)) {
                            Slog.e(TAG, "Do not do setUsbConfig() again!!!");
                            return;
                        } else {
                            mIsBicrSet = true;
                        }
                    }
                    if (!setUsbConfig("none")) {
                        Slog.e(TAG, "Failed to disable USB");
                        // revert to previous configuration if we fail
                        setUsbConfig(mCurrentFunctions);
                        return;
                    }
                    if (setUsbConfig(functions)) {
                        mCurrentFunctions = functions;
                    } else {
                        Slog.e(TAG, "Failed to switch USB config to " + functions);
                        // revert to previous configuration if we fail
                        setUsbConfig(mCurrentFunctions);
                    }
                }
            }
			
			/* reset this value after trigger setconfig, i.e. one time JOB */	
			if(bEvdoDtViaSupport == true){
				mUsbSetBypassWithTether=false ;
			}
        }

        private void updateCurrentAccessory() {
            // We are entering accessory mode if we have received a request from the host
            // and the request has not timed out yet.
            boolean enteringAccessoryMode =
                    mAccessoryModeRequestTime > 0 &&
                        SystemClock.elapsedRealtime() <
                            mAccessoryModeRequestTime + ACCESSORY_REQUEST_TIMEOUT;

            if (mConfigured && enteringAccessoryMode) {
                // successfully entered accessory mode

                if (mAccessoryStrings != null) {
                    mCurrentAccessory = new UsbAccessory(mAccessoryStrings);
                    Slog.d(TAG, "entering USB accessory mode: " + mCurrentAccessory);
                    // defer accessoryAttached if system is not ready
                    if (mBootCompleted) {
                        getCurrentSettings().accessoryAttached(mCurrentAccessory);
                    } // else handle in mBootCompletedReceiver
                } else {
                    Slog.e(TAG, "nativeGetAccessoryStrings failed");
                }
            } else if (!enteringAccessoryMode) {
                // make sure accessory mode is off
                // and restore default functions
                Slog.d(TAG, "exited USB accessory mode");
				if (mDefaultFunctions.equals(UsbManager.USB_FUNCTION_CHARGING_ONLY)) {
					mSettingUsbCharging = true;
					updateUsbState();
				} else {
					mSettingUsbCharging = false;
				}
				/* FIXME, to check, chg for L1 */
                setEnabledFunctions(getDefaultFunctions(), false);

                if (mCurrentAccessory != null) {
                    if (mBootCompleted) {
                        getCurrentSettings().accessoryDetached(mCurrentAccessory);
                    }
                    mCurrentAccessory = null;
                    mAccessoryStrings = null;
                }
            }
        }

        private void updateUsbState() {
            // send a sticky broadcast containing current USB state
            Intent intent = new Intent(UsbManager.ACTION_USB_STATE);
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            intent.putExtra(UsbManager.USB_CONNECTED, mConnected);
            intent.putExtra(UsbManager.USB_CONFIGURED, mConfigured);
            intent.putExtra("USB_HW_DISCONNECTED", mHwDisconnected);
            intent.putExtra("USB_IS_PC_KNOW_ME", mIsPcKnowMe);
            intent.putExtra("SettingUsbCharging", mSettingUsbCharging);
            intent.putExtra("USB_VIA_CDROM_EJECTED", mViaCdromEjected);

            if (mCurrentFunctions != null) {
                String[] functions = mCurrentFunctions.split(",");
                for (int i = 0; i < functions.length; i++) {
                    intent.putExtra(functions[i], true);
                }
            }

            if (DEBUG) Slog.d(TAG, "broadcasting " + intent + " connected: " + mConnected
                                    + " configured: " + mConfigured);
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);

			//ALPS00606302 -> keep usb/mtp/ptp connection state with property and could be get by Mtpreceiver while media process recovery
			if(containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_PTP) && mConnected)
			{
				SystemProperties.set("sys.usb.mtpConnect", "ptpConnection");
			}
			else if(containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_MTP) && mConnected)
			{
				SystemProperties.set("sys.usb.mtpConnect", "mtpConnection");
			}
			else
			{
				SystemProperties.set("sys.usb.mtpConnect", "noneMTPConnection");
			}
			Slog.w(TAG, "sys.usb.mtpConnect = " + SystemProperties.get("sys.usb.mtpConnect"));
			//ALPS00606302

            String acm_idx = SystemProperties.get("sys.usb.acm_idx", "");
            Slog.d(TAG, "sys.usb.acm_idx=" + acm_idx);
            if((acm_idx!=null) && !acm_idx.isEmpty()){
                if(validPortNum(acm_idx) > 0 ) {
                    String port_str = "";
                    port_str = acm_idx;
                    Slog.d(TAG, "set persist.sys.port_index=" + port_str);
                    /*Keep the port info. We need it when reboot.*/
                    SystemProperties.set("persist.sys.port_index", port_str);
                }
            }
        }

        private void updateAudioSourceFunction() {
            boolean enabled = containsFunction(mCurrentFunctions,
                    UsbManager.USB_FUNCTION_AUDIO_SOURCE);
            if (enabled != mAudioSourceEnabled) {
                // send a sticky broadcast containing current USB state
                Intent intent = new Intent(AudioManager.ACTION_USB_AUDIO_ACCESSORY_PLUG);
                intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
                intent.putExtra("state", (enabled ? 1 : 0));
                if (enabled) {
                    Scanner scanner = null;
                    try {
                        scanner = new Scanner(new File(AUDIO_SOURCE_PCM_PATH));
                        int card = scanner.nextInt();
                        int device = scanner.nextInt();
                        intent.putExtra("card", card);
                        intent.putExtra("device", device);
                    } catch (FileNotFoundException e) {
                        Slog.e(TAG, "could not open audio source PCM file", e);
                    } finally {
                        if (scanner != null) {
                            scanner.close();
                        }
                    }
                }
                mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
                mAudioSourceEnabled = enabled;
            }
        }

        @Override
        public void handleMessage(Message msg) {

            if (DEBUG) Slog.w(TAG, "handleMessage - " + msg.what);

            switch (msg.what) {
                case MSG_UPDATE_STATE:
                case MSG_UPDATE_DISCONNECT_STATE:
                    if (mBatteryChargingUnPlug) {
						mBatteryChargingUnPlug = false;
					}

                    mConnected = (msg.arg1 == 1);
                    mConfigured = (msg.arg2 == 1);
                    updateUsbNotification();
                    updateAdbNotification();
                    if (containsFunction(mCurrentFunctions,
                            UsbManager.USB_FUNCTION_ACCESSORY)) {
                        updateCurrentAccessory();
                    }

                    if (mIsUsbBicrEvo) {
                        if (mHwDisconnected) {
                            mIsPcKnowMe = false;
                        } else if (!mIsUserSwitch && !mConnected && !mSettingUsbCharging) {
                            mIsPcKnowMe = false;
                        }
                    }

                    if (!mConnected && !mSettingUsbCharging && !mSettingUsbBicr) {
                        // restore defaults when USB is disconnected
						if (mDefaultFunctions.equals(UsbManager.USB_FUNCTION_CHARGING_ONLY)) {
							mSettingUsbCharging = true;
							updateUsbState();
						} else {
							mSettingUsbCharging = false;
						}

                        Slog.d(TAG, "handleMessage - MSG_UPDATE_STATE/MSG_UPDATE_DISCONNECT_STATE - mConnected: " + mConnected +
                                     ", mSettingUsbCharging: " + mSettingUsbCharging +
                                     ", mSettingUsbBicr: " + mSettingUsbBicr);

						/* FIXME, to check, chg for L1 */
                        setEnabledFunctions(getDefaultFunctions(), false);
                    }
                    if (mBootCompleted) {
                        updateUsbState();
                        updateAudioSourceFunction();

                        Slog.d(TAG, "handleMessage mConnected:" + mConnected + ",mConfigured:" + mConfigured +
                                   ", mHwDisconnected:" + mHwDisconnected);
                    }
                    //VIA-START VIA USB
                    if(bEvdoDtViaSupport == true) {
                        sendEmptyMessage(MSG_HANDLE_CTCLINET);
                        if (!mConnected && !mSettingUsbCharging && !mSettingUsbBicr){
                            //set bypass mode to 0
                            mBypass.updateBypassMode(0);
                            if(mAutoCdromEnable){
                                Slog.d(TAG, "handleMessage Send MSG_SET_VIA_CDROM!!!");
                                sendEmptyMessage(MSG_SET_VIA_CDROM);
                            }
                        }
                    }
                    //VIA-END VIA USB
                    break;
                case MSG_ENABLE_ADB:
                    if (mIsUsbSimSecurity) {
                        String value = SystemProperties.get("persist.sys.usb.activation", "no");
                        if (value.equals("no")) {
                            Slog.d(TAG, "Usb is non-activated!");
                        } else {
                            setAdbEnabled(msg.arg1 == 1);
                        }
                    } else {
                        setAdbEnabled(msg.arg1 == 1);
                    }
                    break;
                case MSG_ENABLE_ACM:
                    int portNum = (Integer)msg.obj;
                    if (portNum >= 1 && portNum <= 4)
                        mAcmPortIdx = String.valueOf(portNum);
                    else
                        mAcmPortIdx = "";
                    Slog.d(TAG, "mAcmPortIdx=" + mAcmPortIdx);
                    setAcmEnabled(!mAcmPortIdx.isEmpty());
                    break;
                case MSG_SET_CURRENT_FUNCTIONS:
                    String functions = (String)msg.obj;
                    boolean makeDefault = (msg.arg1 == 1);

                    mSettingUsbCharging = false;
                    mSettingUsbBicr = false;

                    /* In BICR evo, it's hard to confirm that the current disconnect is caused by switching usb function or unplugging usb cable*/
                    /* So add a flag to know it*/
                    mIsUserSwitch = true;

                    if (functions != null && functions.equals(UsbManager.USB_FUNCTION_CHARGING_ONLY)) {
                        mSettingUsbCharging = true;
                        mCurrentFunctions = removeFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_MTP);
                        mCurrentFunctions = removeFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_PTP);
                        updateUsbState();
                        Slog.d(TAG, "handleMessage - MSG_SET_CURRENT_FUNCTION - CHARGING_ONLY - makeDefault: " + makeDefault);
                    } else if (functions != null && functions.equals(UsbManager.USB_FUNCTION_BICR)) {
                        mSettingUsbBicr = true;
                        Slog.d(TAG, "handleMessage - MSG_SET_CURRENT_FUNCTION - BICR - makeDefault: " + makeDefault);
                    } else if (functions == null && mDefaultFunctions.equals(UsbManager.USB_FUNCTION_CHARGING_ONLY)) {
                        functions = mDefaultFunctions;
                        mSettingUsbCharging = true;
                        makeDefault = true;
                        updateUsbState();
                        Slog.d(TAG, "handleMessage - MSG_SET_CURRENT_FUNCTION - [Tethering Off] USB_FUNCTION_CHARGING_ONLY - makeDefault: " + makeDefault);
                    }

                    setEnabledFunctions(functions, makeDefault);

                    mIsUserSwitch = false;
                    //ALPS00428998
                    if(mMtpAskDisconnect) mMtpAskDisconnect = false;
                    //ALPS00428998

                    Slog.d(TAG, "handleMessage - MSG_SET_CURRENT_FUNCTION - functions: " + functions);
                    break;
                case MSG_SYSTEM_READY:
                    updateUsbNotification();
                    updateAdbNotification();
                    updateUsbState();
                    updateAudioSourceFunction();
                    break;
                case MSG_BOOT_COMPLETED:
                    long bootTime = SystemClock.elapsedRealtime();
                    final boolean mtpState =
                            containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_MTP)
                            || containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_PTP);

                    mBootCompleted = true;

                    /* reset MTP if take too long to boot */
                    if (mtpState && (bootTime > MTP_RESET_TIME)){
                        Slog.v(TAG, "resetting USB host stack for MTP");
                        if (mIsUsbSimSecurity) {
                            String value = SystemProperties.get("persist.sys.usb.activation", "no");
                            if (value.equals("no")) {
                                Slog.d(TAG, "Usb is non-activated!");
                            } else {
                                setUsbConfig("none");
                                setUsbConfig(mCurrentFunctions);
                            }
                        } else {
                            setUsbConfig("none");
                            setUsbConfig(mCurrentFunctions);
                        }
                    }

                    //ALPS00112030 modification
                    //update while System ready is too early that the "SystemUIService(  314): loading: class com.android.systemui.statusbar.phone.PhoneStatusBar" is not ready
                    updateUsbNotification();
                    updateAdbNotification();
                    //ALPS00112030 modification

                    if (mCurrentAccessory != null) {
                        getCurrentSettings().accessoryAttached(mCurrentAccessory);
                    }
                    if (mDebuggingManager != null) {
                        mDebuggingManager.setAdbEnabled(mAdbEnabled);
                    }
                    //ALPS00112030 modification
                    if(mBootCompleted)
                        updateUsbState();
                    //ALPS00112030 modification
                    break;
                case MSG_USER_SWITCHED: {
                    UserManager userManager =
                            (UserManager) mContext.getSystemService(Context.USER_SERVICE);
                    UserHandle userHandle = new UserHandle(msg.arg1);
                    if (userManager.hasUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER,
                            userHandle)) {
                        Slog.v(TAG, "Switched to user " + msg.arg1 +
                                " with DISALLOW_USB_FILE_TRANSFER restriction; disabling USB.");
                        setUsbConfig("none");
                        mCurrentUser = msg.arg1;
                        break;
                    }

                    final boolean mtpActive =
                            containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_MTP)
                            || containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_PTP);
                    if (mtpActive && mCurrentUser != UserHandle.USER_NULL) {
                        Slog.v(TAG, "Current user switched; resetting USB host stack for MTP");
                        setUsbConfig("none");
                        setUsbConfig(mCurrentFunctions);
                    }
                    mCurrentUser = msg.arg1;
                    break;
                }
                //VIA-START VIA USB
                case MSG_SET_BYPASS_MODE:
                    if(bEvdoDtViaSupport == true) {
                        mBypass.updateBypassMode(msg.arg1);
                    }
                    break;
                 case MSG_HANDLE_CTCLINET:
                    if(bEvdoDtViaSupport == true) {
                        mBypass.handleCTClinet();
                    }
                    break;
                 case MSG_VIA_CDROM_EDJECT:
                    if(bEvdoDtViaSupport == true) {
                        mViaCdromEjected = true;
                        setEnabledFunctions(mDefaultFunctions, msg.arg1 == 1);
                        updateUsbState();
                    }
                    break;
                case MSG_SET_VIA_CDROM:
                    if(bEvdoDtViaSupport == true) {
                       if(mPCModeEnable && !mCurrentFunctions.contains(UsbManager.USB_FUNCTION_VIA_CDROM)){
                            mViaCdromEjected = false;
            								setEnabledFunctions(UsbManager.USB_FUNCTION_VIA_CDROM,false);
            					 }
                    }
                    break;
                case MSG_SET_BYPASS:
                    if(bEvdoDtViaSupport == true) {
                        mBypass.setBypass(msg.arg1);
                    }
                    break;
                //VIA-END VIA USB
            }
        }

        public UsbAccessory getCurrentAccessory() {
            return mCurrentAccessory;
        }

        private void updateUsbNotification() {
            if(DEBUG) {
                Slog.d(TAG, "updateUsbNotification - mNotificationManager: " + mNotificationManager);
                Slog.d(TAG, "updateUsbNotification - mUseUsbNotification: " + mUseUsbNotification);
            }

            if (mNotificationManager == null || !mUseUsbNotification) return;
            int id = 0;
            Resources r = mContext.getResources();

            if(DEBUG) {
                Slog.w(TAG, "updateUsbNotification - mConnected: " + mConnected);
                Slog.w(TAG, "updateUsbNotification - mCurrentFunctions: " + mCurrentFunctions);
            }

            boolean usbChargingFlag = (mSettingUsbCharging && mPlugType == 2) ||
			      (SystemProperties.get("ro.mtk_diso_support").equals("true") && mPlugType == 3);
            if (mConnected || usbChargingFlag) {
                if (containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_MTP)) {
                    id = com.android.internal.R.string.usb_mtp_notification_title;
                    Slog.d(TAG, "updateUsbNotification - containsFunction:  USB_FUNCTION_MTP");
                } else if (containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_PTP)) {
                    id = com.android.internal.R.string.usb_ptp_notification_title;
                    Slog.d(TAG, "updateUsbNotification - containsFunction:  USB_FUNCTION_PTP");
                } else if (containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_MASS_STORAGE)) {
                    Slog.d(TAG, "updateUsbNotification - containsFunction:  USB_FUNCTION_MASS_STORAGE - mUsbStorageType: " + mUsbStorageType);
                    if (mUsbStorageType.equals(UsbManager.USB_FUNCTION_MTP)) {
                    id = com.android.internal.R.string.usb_cd_installer_notification_title;
                    } else {
                        id = com.mediatek.internal.R.string.usb_ums_notification_title;
                    }
                } else if (containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_ACCESSORY)) {
                    id = com.android.internal.R.string.usb_accessory_notification_title;
                } else if (containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_CHARGING_ONLY)) {
            		if (usbChargingFlag) {
						id = com.mediatek.internal.R.string.usb_charging_notification_title;
						Slog.d(TAG, "updateUsbNotification - containsFunction:  USB_FUNCTION_CHARGING_ONLY");
					}
                } else if (containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_BICR)) {
                    id = com.android.internal.R.string.usb_cd_installer_notification_title;
                    Slog.d(TAG, "updateUsbNotification - containsFunction:  USB_FUNCTION_BICR");
                } else {
                    // There is a different notification for USB tethering so we don't need one here
                    if (!containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_RNDIS) && !containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_EEM)) {
                        Slog.e(TAG, "No known USB function in updateUsbNotification");
                    }
                //VIA-START VIA USB
                    if(bEvdoDtViaSupport == true) {
                        if(mPCModeEnable &&
                            containsFunction(mCurrentFunctions,UsbManager.USB_FUNCTION_VIA_CDROM)){
                            id = com.mediatek.internal.R.string.usb_autorun_cdrom_notification_title;
                            Slog.d(TAG, "updateUsbNotification - containsFunction:  USB_FUNCTION_VIA_CDROM");
                        }
                    }
                //VIA-END VIA USB
                }
            }
            //ALPS00112030 modification
            //update while System ready is too early that the "SystemUIService(  314): loading: class com.android.systemui.statusbar.phone.PhoneStatusBar" is not ready
            if (id != mUsbNotificationId && mBootCompleted) {
            //ALPS00112030 modification
                // clear notification if title needs changing
                if (mUsbNotificationId != 0) {
                    mNotificationManager.cancelAsUser(null, mUsbNotificationId,
                            UserHandle.ALL);
                    mUsbNotificationId = 0;
                }
                if (id != 0) {
                    CharSequence message = r.getText(
                            com.android.internal.R.string.usb_notification_message);
                    CharSequence title = r.getText(id);

                    Notification notification = new Notification();
						notification.icon = com.android.internal.R.drawable.stat_sys_data_usb;
						Bitmap mBitmap1 = BitmapFactory.decodeResource(mContext.getResources(), com.hq.resource.internal.R.drawable.stat_sys_data_usb_large);
						if(mBitmap1 != null){
							notification.largeIcon = mBitmap1;
							Slog.v("caoxuhao1", "com.android.internal.R.emo_im_winking");
						}

                    notification.when = 0;
                    notification.flags = Notification.FLAG_ONGOING_EVENT;
                    notification.tickerText = title;
                    notification.defaults = 0; // please be quiet
                    notification.sound = null;
                    notification.vibrate = null;
                    notification.priority = Notification.PRIORITY_DEFAULT;

                    Intent intent = Intent.makeRestartActivityTask(
                            new ComponentName("com.android.settings",
                                    "com.android.settings.UsbSettings"));
                    PendingIntent pi = PendingIntent.getActivityAsUser(mContext, 0,
                            intent, 0, null, UserHandle.CURRENT);
                    notification.color = mContext.getResources().getColor(
                            com.android.internal.R.color.system_notification_accent_color);
                    notification.setLatestEventInfo(mContext, title, message, pi);
                    notification.visibility = Notification.VISIBILITY_PUBLIC;
                    mNotificationManager.notifyAsUser(null, id, notification,
                            UserHandle.ALL);
                    mUsbNotificationId = id;
                }
            }
        }

        private void updateAdbNotification() {
            if (mNotificationManager == null) return;
            final int id = com.android.internal.R.string.adb_active_notification_title;

            mAdbUpdateLock.lock();

            if (mAdbEnabled && mConnected && !mSettingUsbCharging) {
                if ("0".equals(SystemProperties.get("persist.adb.notify"))) return;

                //ALPS00112030 modification
                //update while System ready is too early that the "SystemUIService(  314): loading: class com.android.systemui.statusbar.phone.PhoneStatusBar" is not ready
                if (mBootCompleted && !mAdbNotificationShown) {
                //ALPS00112030 modification
                    Resources r = mContext.getResources();
                    CharSequence title = r.getText(id);
                    CharSequence message = r.getText(
                            com.android.internal.R.string.adb_active_notification_message);

                    Notification notification = new Notification();

                    notification.icon = com.android.internal.R.drawable.stat_sys_adb;

						Bitmap mBitmap2 = BitmapFactory.decodeResource(mContext.getResources(), com.hq.resource.internal.R.drawable.stat_sys_adb_am_large);
						if(mBitmap2 != null){
							notification.largeIcon = mBitmap2;
							Slog.v("caoxuhao1", "com.hq.resource.internal.R.stat_sys_adb_am_large");
						}

                    notification.when = 0;
                    notification.flags = Notification.FLAG_ONGOING_EVENT;
                    notification.tickerText = title;
                    notification.defaults = 0; // please be quiet
                    notification.sound = null;
                    notification.vibrate = null;
                    notification.priority = Notification.PRIORITY_LOW;

                    Intent intent = Intent.makeRestartActivityTask(
                            new ComponentName("com.android.settings",
                                    "com.android.settings.DevelopmentSettings"));
                    PendingIntent pi = PendingIntent.getActivityAsUser(mContext, 0,
                            intent, 0, null, UserHandle.CURRENT);
                    notification.color = mContext.getResources().getColor(
                            com.android.internal.R.color.system_notification_accent_color);
                    notification.setLatestEventInfo(mContext, title, message, pi);
                    notification.visibility = Notification.VISIBILITY_PUBLIC;
                    mAdbNotificationShown = true;
                    mNotificationManager.notifyAsUser(null, id, notification,
                            UserHandle.ALL);
                }
            } else if (mAdbNotificationShown) {
                mAdbNotificationShown = false;
                mNotificationManager.cancelAsUser(null, id, UserHandle.ALL);
            }
            mAdbUpdateLock.unlock();
        }

        private String getDefaultFunctions() {
			
			// FIXME, to check L1 new feature, marked first
			/*	
            UserManager userManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
            if (userManager.hasUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER,
                    new UserHandle(mCurrentUser))) {
                return "none";
            }*/
            return mDefaultFunctions;
        }

        public void dump(FileDescriptor fd, PrintWriter pw) {
            pw.println("  USB Device State:");
            pw.println("    Current Functions: " + mCurrentFunctions);
            pw.println("    Default Functions: " + mDefaultFunctions);
            pw.println("    mConnected: " + mConnected);
            pw.println("    mConfigured: " + mConfigured);
            pw.println("    mCurrentAccessory: " + mCurrentAccessory);
            try {
                pw.println("    Kernel state: "
                        + FileUtils.readTextFile(new File(STATE_PATH), 0, null).trim());
                pw.println("    Kernel function list: "
                        + FileUtils.readTextFile(new File(FUNCTIONS_PATH), 0, null).trim());
                pw.println("    Mass storage backing file: "
                        + FileUtils.readTextFile(new File(MASS_STORAGE_FILE_PATH), 0, null).trim());
            } catch (IOException e) {
                pw.println("IOException: " + e);
            }
        }

        private int validPortNum(String port) {
            String[] tmp = port.split(",");
            String port_idx_valid = "";
            int port_num = 0;
            for(int i=0; i<tmp.length; i++) {
                if(Integer.valueOf(tmp[i])>0 && Integer.valueOf(tmp[i])<5) {
                    port_num++;
                }
            }
            Slog.d(TAG, "port_num=" + port_num +",tmp.length()="+ tmp.length);
            return ((port_num == tmp.length) ? port_num : 0);
        }
    }

    /* returns the currently attached USB accessory */
    public UsbAccessory getCurrentAccessory() {
        return mHandler.getCurrentAccessory();
    }

    /* opens the currently attached USB accessory */
    public ParcelFileDescriptor openAccessory(UsbAccessory accessory) {
        UsbAccessory currentAccessory = mHandler.getCurrentAccessory();
        if (currentAccessory == null) {
            throw new IllegalArgumentException("no accessory attached");
        }
        if (!currentAccessory.equals(accessory)) {
            String error = accessory.toString()
                    + " does not match current accessory "
                    + currentAccessory;
            throw new IllegalArgumentException(error);
        }
        getCurrentSettings().checkPermission(accessory);
        return nativeOpenAccessory();
    }

    public void setCurrentFunctions(String functions, boolean makeDefault) {
        if (DEBUG) Slog.d(TAG, "setCurrentFunctions(" + functions + ") default: " + makeDefault);
        mHandler.sendMessage(MSG_SET_CURRENT_FUNCTIONS, functions, makeDefault);
    }

    public int getCurrentState() {
        int state = 0;
        if ((!mHwDisconnected) && (mHandler.mPlugType == 2))
            state = 1;
        if (DEBUG) Slog.d(TAG, "getCurrentState - " + state);
        return state;
    }

    public void setMassStorageBackingFile(String path) {
        if (path == null) path = "";
        try {
            FileUtils.stringToFile(MASS_STORAGE_FILE_PATH, path);
        } catch (IOException e) {
           Slog.e(TAG, "failed to write to " + MASS_STORAGE_FILE_PATH);
        }
    }

    private void readOemUsbOverrideConfig() {
        String[] configList = mContext.getResources().getStringArray(
            com.android.internal.R.array.config_oemUsbModeOverride);

        if (configList != null) {
            for (String config: configList) {
                String[] items = config.split(":");
                if (items.length == 3) {
                    if (mOemModeMap == null) {
                        mOemModeMap = new HashMap<String, List<Pair<String, String>>>();
                    }
                    List<Pair<String, String>> overrideList = mOemModeMap.get(items[0]);
                    if (overrideList == null) {
                        overrideList = new LinkedList<Pair<String, String>>();
                        mOemModeMap.put(items[0], overrideList);
                    }
                    overrideList.add(new Pair<String, String>(items[1], items[2]));
                }
            }
        }
    }

    private boolean needsOemUsbOverride() {
        if (mOemModeMap == null) return false;

        String bootMode = SystemProperties.get(BOOT_MODE_PROPERTY, "unknown");
        return (mOemModeMap.get(bootMode) != null) ? true : false;
    }

    private String processOemUsbOverride(String usbFunctions) {
        if ((usbFunctions == null) || (mOemModeMap == null)) return usbFunctions;

        String bootMode = SystemProperties.get(BOOT_MODE_PROPERTY, "unknown");

        List<Pair<String, String>> overrides = mOemModeMap.get(bootMode);
        if (overrides != null) {
            for (Pair<String, String> pair: overrides) {
                if (pair.first.equals(usbFunctions)) {
                    Slog.d(TAG, "OEM USB override: " + pair.first + " ==> " + pair.second);
                    return pair.second;
                }
            }
        }
        // return passed in functions as is.
        return usbFunctions;
    }

    public void allowUsbDebugging(boolean alwaysAllow, String publicKey) {
        if (mDebuggingManager != null) {
            mDebuggingManager.allowUsbDebugging(alwaysAllow, publicKey);
        }
    }

    public void denyUsbDebugging() {
        if (mDebuggingManager != null) {
            mDebuggingManager.denyUsbDebugging();
        }
    }

    public void clearUsbDebuggingKeys() {
        if (mDebuggingManager != null) {
            mDebuggingManager.clearUsbDebuggingKeys();
        } else {
            throw new RuntimeException("Cannot clear Usb Debugging keys, "
                        + "UsbDebuggingManager not enabled");
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw) {
        if (mHandler != null) {
            mHandler.dump(fd, pw);
        }
        if (mDebuggingManager != null) {
            mDebuggingManager.dump(fd, pw);
        }
    }

    private native String[] nativeGetAccessoryStrings();
    private native ParcelFileDescriptor nativeOpenAccessory();
    private native boolean nativeIsStartRequested();
    private native int nativeGetAudioMode();
}
