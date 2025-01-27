package com.mediatek.wfc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.ims.ImsConfig;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;

import com.mediatek.ims.WfcReasonInfo;

import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;


public class WfcSummary {

    private static final boolean DBG = true;
    private static final String TAG = "WfcSummaryState";
    private static final String KEY_ISIM_PRESENT = "persist.sys.wfc_isimAppPresent";
    // TODO: need to read the value configured via engineering mode
    private static final int WIFI_SIGNAL_STRENGTH_THRESHOLD = -75;
    public static final String ACTION_WFC_SUMMARY_CHANGE = "action_wfc_summary_change";
    public static final String EXTRA_SUMMARY = "summary_string";

     private enum WfcSummaryState {
        SUMMARY_NON_WFC_STATE,
        SUMMARY_ENABLING,
        SUMMARY_WFC_ON,
        SUMMARY_READY_TO_CALL,
        SUMMARY_ERROR,
        SUMMARY_DISABLING,
        SUMMARY_WFC_OFF,
        SUMMARY_UNKNOWN_STATE
    };

    private Context mContext;
    private WfcSummaryState mWfcSummaryState = WfcSummaryState.SUMMARY_NON_WFC_STATE;
    private ImsManager mImsManager;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            if (DBG) Log.d(TAG, "Intent action:" + intent.getAction());
            int wfcState = WfcReasonInfo.CODE_WFC_DEFAULT;
            if (ImsManager.ACTION_IMS_STATE_CHANGED.equals(intent.getAction())) {
                wfcState = intent.getIntExtra(ImsManager.EXTRA_IMS_REG_STATE_KEY,
                                            ServiceState.STATE_OUT_OF_SERVICE);
                if (DBG) Log.d(TAG, "wfcState:" + wfcState);
                if (wfcState == ServiceState.STATE_OUT_OF_SERVICE) {
                    int errorCode = intent.getIntExtra(ImsManager.EXTRA_IMS_REG_ERROR_KEY,
                                                WfcReasonInfo.CODE_WFC_DEFAULT);
                    if (DBG) Log.d(TAG, "error:" + errorCode);
                    // If error is RNS error, no action
                    if (!showErrorToUser(errorCode)) {
                        if (DBG) Log.d(TAG, "invalid error code, return");
                        return;
                    }
                    
                    wfcState = errorCode;
                    sendWfcSummaryChangeIntent(context, wfcState);
                }
            } else if (ImsManager.ACTION_IMS_CAPABILITIES_CHANGED.equals(intent.getAction())) {
                wfcState = handleImsCapabilityChange(context, intent);
                sendWfcSummaryChangeIntent(context, wfcState);
            } else if (ImsManager.ACTION_IMS_SERVICE_DOWN.equals(intent.getAction())) {
                wfcState = WfcReasonInfo.CODE_WFC_DEFAULT;
                sendWfcSummaryChangeIntent(context, wfcState);
            } else if (ImsManager.ACTION_IMS_SERVICE_UP.equals(intent.getAction())
                    || Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())
                    || WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())
                    || (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())
                        && IccCardConstants.INTENT_VALUE_ICC_LOADED
                            .equals(intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE)))
                    || WifiManager.RSSI_CHANGED_ACTION.equals(intent.getAction())
                    || WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                wfcState = mImsManager.getWfcStatusCode();
                if (DBG) Log.d(TAG, "Wfc state:" + wfcState);
                sendWfcSummaryChangeIntent(context, wfcState);
            }
        }
    };

    public WfcSummary(Context context) {
        mContext = context;
        mImsManager = ImsManager.getInstance(context, SubscriptionManager.getDefaultVoiceSubId());
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void registerWfcSummary() {
        IntentFilter filter = new IntentFilter(ImsManager.ACTION_IMS_STATE_CHANGED);
        filter.addAction(ImsManager.ACTION_IMS_CAPABILITIES_CHANGED);
        filter.addAction(ImsManager.ACTION_IMS_SERVICE_DOWN);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        //filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);        
        mContext.registerReceiver(mReceiver, filter);
    }

    public void unRegisterWfcSummary() {
        if (DBG) Log.d(TAG, "in unregister receiver");
        mContext.unregisterReceiver(mReceiver);
    }

    public String getWfcSummaryText(int wfcState) {
        if (!isWfcSwitchOn(mContext)) {
            if (DBG) Log.d(TAG, "WFC OFF");
            return mContext.getResources().getString(R.string.Wfc_off_summary);
        }  else if (wfcState == WfcReasonInfo.CODE_WFC_SUCCESS) {
            if (DBG) Log.d(TAG, "WFC registered");
            return mContext.getResources().getString(R.string.wfc_ready_for_calls_summary);
        } else if (isAirplaneModeOn(mContext) && !isWifiEnabled()) {
            // Show airplane mode only if wifi is off .i.e., if user has enabled wifi after turning Airplane mode ON
            //show summary as per wifi & ims/wfc conditions
            if (DBG) Log.d(TAG, "Airplane mode ON");
            return mContext.getResources().getString(R.string.airplane_mode_on);
        } else if (isWifiEnabled() && !isIsimAppPresent()) {
            // Show ISIM not present error only, when wifi is enabled
            if (DBG) Log.d(TAG, "ISIM not present");
            return mContext.getResources().getString(WfcReasonInfo.getImsStatusCodeString(wfcState));
        } else if (getWfcPreferenceFromDb(mContext) == TelephonyManager.WifiCallingPreferences.CELLULAR_PREFERRED
                && wfcState  == WfcReasonInfo.CODE_WFC_DEFAULT) {
            if (DBG) Log.d(TAG, "pref cellular preferred");
            return mContext.getResources().getString(R.string.cellular_prefered_summary);
        } else if (!isWifiEnabled()) {
            if (DBG) Log.d(TAG, "wifi OFF");
            return mContext.getResources().getString(R.string.wifi_off_summary);
        } else if (!isWifiConnected()) {
            if (DBG) Log.d(TAG, "wifi not  connected");
            return mContext.getResources().getString(R.string.not_connected_to_wifi_summary);
        } else if (mWifiManager.getConnectionInfo().getRssi() < WIFI_SIGNAL_STRENGTH_THRESHOLD) {
            if (DBG) Log.d(TAG, "poor signal strenth" + mWifiManager.getConnectionInfo().getRssi());
            return mContext.getResources().getString(R.string.poor_wifi_signal_summary);
        } else {
            return getWfcSummary(mContext, wfcState);
        }
    }

    private String getWfcSummary(Context context, int wfcState) {

        setWfcSummaryState(context, wfcState);

        switch(mWfcSummaryState) {
            case SUMMARY_ENABLING:
                return context.getResources().getString(R.string.wfc_enabling_summary);

            case SUMMARY_WFC_ON:
                return context.getResources().getString(R.string.wfc_on_summary);

            case SUMMARY_READY_TO_CALL:
                return context.getResources().getString(R.string.wfc_ready_for_calls_summary);

            case SUMMARY_ERROR:
                if (WfcReasonInfo.getImsStatusCodeString(wfcState) != 0) {
                    return context.getResources().getString(WfcReasonInfo
                            .getImsStatusCodeString(wfcState));
                } else {
                    if (DBG) Log.d(TAG, "in error but invalid code:" + wfcState);
                    return context.getResources().getString(R.string.wfc_unknown_error_summary);
                }

            case SUMMARY_DISABLING:
                return context.getResources().getString(R.string.wfc_disabling_summary);

            case SUMMARY_WFC_OFF:
                return context.getResources().getString(R.string.Wfc_off_summary);                

            case SUMMARY_UNKNOWN_STATE:
            default:
                return context.getResources().getString(R.string.wfc_unknown_error_summary);

        }
    }

    private void setWfcSummaryState(Context context, int wfcState) {

        int imsState;
        try {
            imsState = mImsManager.getImsState();
        } catch (ImsException e) {
            if (DBG) Log.d(TAG, "ImsException:" + e);
            imsState = PhoneConstants.IMS_STATE_DISABLED;    
        }
        if (DBG) Log.d(TAG, "ims state:" + imsState);
        if (DBG) Log.d(TAG, "wfc state:" + wfcState);
        if (DBG) Log.d(TAG, "wfc switch ON:" + isWfcSwitchOn(mContext));

        if (isWfcSwitchOn(mContext) && imsState == PhoneConstants.IMS_STATE_ENABLING) {
            mWfcSummaryState = WfcSummaryState.SUMMARY_ENABLING;
        } else if (isWfcSwitchOn(mContext) && wfcState == WfcReasonInfo.CODE_WFC_DEFAULT) {
                    mWfcSummaryState = WfcSummaryState.SUMMARY_WFC_ON;
        }  else if (isWfcSwitchOn(mContext) && wfcState == WfcReasonInfo.CODE_WFC_SUCCESS) {
                    mWfcSummaryState = WfcSummaryState.SUMMARY_READY_TO_CALL;
        } else if (isWfcSwitchOn(mContext) && wfcState > WfcReasonInfo.CODE_WFC_DEFAULT) {
                    mWfcSummaryState = WfcSummaryState.SUMMARY_ERROR;
        } else if (imsState == PhoneConstants.IMS_STATE_DISABLING
                && (wfcState > WfcReasonInfo.CODE_WFC_DEFAULT
                        || wfcState == WfcReasonInfo.CODE_WFC_SUCCESS)) {
                    mWfcSummaryState = WfcSummaryState.SUMMARY_DISABLING;
        } else if (!isWfcSwitchOn(mContext)) {
            mWfcSummaryState = WfcSummaryState.SUMMARY_WFC_OFF;
        } else {
            mWfcSummaryState = WfcSummaryState.SUMMARY_UNKNOWN_STATE;
        }
        if (DBG) Log.d(TAG, "summary state:" + mWfcSummaryState);
    }

    private int handleImsCapabilityChange(Context context, Intent intent) {
        int[] enabledFeatures = intent.getIntArrayExtra(ImsManager.EXTRA_IMS_ENABLE_CAP_KEY);
        if (DBG) Log.d(TAG, "in intent, capability:" + enabledFeatures[ImsConfig.FeatureConstants
                .FEATURE_TYPE_VOICE_OVER_WIFI]);
        if (enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI] ==
                ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI) {
            return WfcReasonInfo.CODE_WFC_SUCCESS;
        }
        else return WfcReasonInfo.CODE_WFC_DEFAULT;
    }

    private void sendWfcSummaryChangeIntent(Context context, int wfcState) {
        Intent intent = new Intent(ACTION_WFC_SUMMARY_CHANGE);
        String summary = getWfcSummaryText(wfcState);
        intent.putExtra(EXTRA_SUMMARY, summary);
        if (DBG) Log.d(TAG, "Sending broadcast with summary:" + summary);
        context.sendBroadcast(intent);
    }

    /* Whether a error code is error condition for WFC notification or not
     * RNS errors are not considered as error conditions by WFC notification
     * so blocking them
     */
    private boolean showErrorToUser(int errorCode) {
        switch(errorCode) {
            case WfcReasonInfo.CODE_WFC_WIFI_SIGNAL_LOST:
            case WfcReasonInfo.CODE_WFC_UNABLE_TO_COMPLETE_CALL:
            case WfcReasonInfo.CODE_WFC_NO_AVAILABLE_QUALIFIED_MOBILE_NETWORK:
            case WfcReasonInfo.CODE_WFC_UNABLE_TO_COMPLETE_CALL_CD:
            case WfcReasonInfo.CODE_WFC_RNS_ALLOWED_RADIO_DENY:
            case WfcReasonInfo.CODE_WFC_RNS_ALLOWED_RADIO_NONE:
                return false;
            default:
                return true;
        }
    }

     private boolean isWfcSwitchOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.WHEN_TO_MAKE_WIFI_CALLS,
                TelephonyManager.WifiCallingChoices.ALWAYS_USE)
                == TelephonyManager.WifiCallingChoices.ALWAYS_USE ? true : false;
    }

    private int getWfcPreferenceFromDb(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.SELECTED_WFC_PREFERRENCE,
                TelephonyManager.WifiCallingPreferences.WIFI_PREFERRED);
    }

    private boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
    }

    private boolean isWifiEnabled() {
        int wifiState = mWifiManager.getWifiState();
        if (DBG) Log.d(TAG, "wifi state:" + wifiState);
        return (wifiState != WifiManager.WIFI_STATE_DISABLED);
    }

    private boolean isWifiConnected() {
        NetworkInfo networkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (DBG) Log.d(TAG, "networkInfo:" + networkInfo);
        if (networkInfo != null) {
            if (DBG) Log.d(TAG, "networkInfo:" + networkInfo.isConnected());
            if (DBG) Log.d(TAG, "networkInfo:" + networkInfo.getDetailedState());
        }
        return (networkInfo != null && (networkInfo.isConnected()
                    || networkInfo.getDetailedState() == DetailedState.CAPTIVE_PORTAL_CHECK));
    }

    private boolean isIsimAppPresent() {
        if (DBG) Log.d(TAG, "isimApp present:" + SystemProperties.get(KEY_ISIM_PRESENT));
        if ("1".equals(SystemProperties.get(KEY_ISIM_PRESENT))) return true;
        else return false;
    }

}
