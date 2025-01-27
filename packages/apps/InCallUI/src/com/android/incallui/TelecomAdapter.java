/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.incallui;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Looper;
import android.telecom.InCallAdapter;
import android.telecom.Phone;
import android.telecom.PhoneAccountHandle;

import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.android.internal.os.SomeArgs;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SmsApplication;

import android.provider.ContactsContract;
import com.google.common.base.Preconditions;

import java.util.List;

/** Wrapper around {@link InCallAdapter} that only forwards calls to the adapter when it's valid. */
public final class TelecomAdapter implements InCallPhoneListener {
    private static final String ADD_CALL_MODE_KEY = "add_call_mode";

    private static TelecomAdapter sInstance;
    private Context mContext;
    private Phone mPhone;

    public static TelecomAdapter getInstance() {
        Preconditions.checkState(Looper.getMainLooper().getThread() == Thread.currentThread());
        if (sInstance == null) {
            sInstance = new TelecomAdapter();
        }
        return sInstance;
    }

    private TelecomAdapter() {
    }

    void setContext(Context context) {
        mContext = context;
    }

    @Override
    public void setPhone(Phone phone) {
        mPhone = phone;
    }

    @Override
    public void clearPhone() {
        mPhone = null;
    }

    private android.telecom.Call getTelecommCallById(String callId) {
        final Call call = CallList.getInstance().getCallById(callId);
        return call == null ? null : call.getTelecommCall();
    }

    void answerCall(String callId, int videoState) {
        if (mPhone != null) {
            final android.telecom.Call call = getTelecommCallById(callId);
            if (call != null) {
                call.answer(videoState);
            } else {
                Log.e(this, "error answerCall, call not in call list: " + callId);
            }
        } else {
            Log.e(this, "error answerCall, mPhone is null");
        }
    }

    void rejectCall(String callId, boolean rejectWithMessage, String message) {
        if (mPhone != null) {
            final android.telecom.Call call = getTelecommCallById(callId);
            if (call != null) {
                call.reject(rejectWithMessage, message);
            } else {
                Log.e(this, "error rejectCall, call not in call list: " + callId);
            }
        } else {
            Log.e(this, "error rejectCall, mPhone is null");
        }
    }

    void disconnectCall(String callId) {
        if (mPhone != null) {
            getTelecommCallById(callId).disconnect();
        } else {
            Log.e(this, "error disconnectCall, mPhone is null");
        }
    }

    void holdCall(String callId) {
        if (mPhone != null) {
            getTelecommCallById(callId).hold();
        } else {
            Log.e(this, "error holdCall, mPhone is null");
        }
    }

    void unholdCall(String callId) {
        if (mPhone != null) {
            getTelecommCallById(callId).unhold();
        } else {
            Log.e(this, "error unholdCall, mPhone is null");
        }
    }

    void mute(boolean shouldMute) {
        if (mPhone != null) {
            mPhone.setMuted(shouldMute);
        } else {
            Log.e(this, "error mute, mPhone is null");
        }
    }

    void setAudioRoute(int route) {
        if (mPhone != null) {
            mPhone.setAudioRoute(route);
        } else {
            Log.e(this, "error setAudioRoute, mPhone is null");
        }
    }

    void turnOnProximitySensor() {
        if (mPhone != null) {
            mPhone.setProximitySensorOn();
        } else {
            Log.e(this, "error setProximitySensorOn, mPhone is null");
        }
    }

    void turnOffProximitySensor(boolean screenOnImmediately) {
        if (mPhone != null) {
            mPhone.setProximitySensorOff(screenOnImmediately);
        } else {
            Log.e(this, "error setProximitySensorOff, mPhone is null");
        }
    }

    void separateCall(String callId) {
        if (mPhone != null) {
            getTelecommCallById(callId).splitFromConference();
        } else {
            Log.e(this, "error separateCall, mPhone is null.");
        }
    }

    void merge(String callId) {
        if (mPhone != null) {
            android.telecom.Call call = getTelecommCallById(callId);
            List<android.telecom.Call> conferenceable = call.getConferenceableCalls();
            if (!conferenceable.isEmpty()) {
                call.conference(conferenceable.get(0));
            } else {
                if (call.getDetails().can(
                        android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                    call.mergeConference();
                }
            }
        } else {
            Log.e(this, "error merge, mPhone is null.");
        }
    }

    void swap(String callId) {
        if (mPhone != null) {
            android.telecom.Call call = getTelecommCallById(callId);
            if (call.getDetails().can(
                    android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE)) {
                call.swapConference();
            }
        } else {
            Log.e(this, "Error swap, mPhone is null.");
        }
    }

	/*add by zhangjinqiang for al812,begin*/
	public void addContacts(){
		Log.d(this, "add a contact");
		if (mContext != null) {
			Intent intent = new Intent(ContactsContract.Intents.UI.LIST_ALL_CONTACTS_ACTION);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

			try {
				Log.d(this, "Sending the add contact intent");
				mContext.startActivity(intent);
			} catch (ActivityNotFoundException e) {
				// This is rather rare but possible.
				// Note: this method is used even when the phone is encrypted. At that moment
				// the system may not find any Activity which can accept this Intent.
				Log.e(this, "Activity for adding contact isn't found.", e);
			}
		}
	}
	/*add by zhangjinqiang for al812,end*/


    void addCall() {
        if (mContext != null) {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // when we request the dialer come up, we also want to inform
            // it that we're going through the "add call" option from the
            // InCallScreen / PhoneUtils.
            intent.putExtra(ADD_CALL_MODE_KEY, true);
            try {
                Log.d(this, "Sending the add Call intent");
                mContext.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                // This is rather rare but possible.
                // Note: this method is used even when the phone is encrypted. At that moment
                // the system may not find any Activity which can accept this Intent.
                Log.e(this, "Activity for adding calls isn't found.", e);
            }
        }
    }

    void playDtmfTone(String callId, char digit) {
        if (mPhone != null) {
            getTelecommCallById(callId).playDtmfTone(digit);
        } else {
            Log.e(this, "error playDtmfTone, mPhone is null");
        }
    }

    void stopDtmfTone(String callId) {
        if (mPhone != null) {
            getTelecommCallById(callId).stopDtmfTone();
        } else {
            Log.e(this, "error stopDtmfTone, mPhone is null");
        }
    }

    void postDialContinue(String callId, boolean proceed) {
        if (mPhone != null && getTelecommCallById(callId) != null) {
            getTelecommCallById(callId).postDialContinue(proceed);
        } else {
            Log.e(this, "error postDialContinue, mPhone or call is null");
        }
    }

    void phoneAccountSelected(String callId, PhoneAccountHandle accountHandle, boolean setDefault) {
        if (mPhone != null) {
            getTelecommCallById(callId).phoneAccountSelected(accountHandle, setDefault);
        }  else {
            Log.e(this, "error phoneAccountSelected, mAdapter is null");
        }

        if (accountHandle == null) {
            Log.e(this, "error phoneAccountSelected, accountHandle is null");
        }
    }

    boolean canAddCall() {
        // Default to true if we are not connected to telecom.
        return mPhone == null ? true : mPhone.canAddCall();
    }

    // ---------------------------------MTK--------------------------------------

    /**
     * Start voice recording
     */
    void startVoiceRecording() {
        if (mPhone != null) {
            mPhone.startVoiceRecording();
        } else {
            Log.e(this, "error startVoiceRecording, mPhone is null");
        }
    }

    /**
     * Stop voice recording
     */
    void stopVoiceRecording() {
        if (mPhone != null) {
            mPhone.stopVoiceRecording();
        } else {
            Log.e(this, "error stopVoiceRecording, mPhone is null");
        }
    }

    /**
     * M: The all background calls will be sorted according to the time
     * the call be held, e.g. the first hold call will be first item in
     * the list.
     */
    void setSortedBackgroudCallList(List<String> backgroundCallList) {
        if (mPhone != null) {
            mPhone.setSortedBackgroudCallList(backgroundCallList);
        } else {
            Log.e(this, "error setSortedBackgroudCallList, mPhone is null");
        }
    }

    /**
     * M: The all incoming calls will be sorted according to user's action,
     * since there are more than 1 incoming call exist user may touch to switch
     * any incoming call to the primary screen, the sequence of the incoming call
     * will be changed.
     */
    void setSortedIncomingCallList(List<String> incomingCallList) {
        if (mPhone != null) {
            mPhone.setSortedIncomingCallList(incomingCallList);
        } else {
            Log.e(this, "error setSortedIncomingCallList, mPhone is null");
        }
    }

    /**
     * M: Handle ECT.
     */
    void explicitCallTransfer(String callId) {
        if (mPhone != null) {
            mPhone.explicitCallTransfer(callId);
        } else {
            Log.e(this, "error explicitCallTransfer, mPhone is null");
        }
    }

    /**
     * Instructs Telecom to disconnect all the calls.
     */
    void hangupAll() {
        if (mPhone != null) {
            mPhone.hangupAll();
        } else {
            Log.e(this, "error hangupAll, mPhone is null");
        }
    }

    /**
     * Instructs Telecom to disconnect all the HOLDING calls.
     */
    void hangupAllHoldCalls() {
        if (mPhone != null) {
            mPhone.hangupAllHoldCalls();
        } else {
            Log.e(this, "error hangupAllHoldCalls, mPhone is null");
        }
    }

    /**
     * Instructs Telecom to disconnect active call and answer waiting call.
     */
    void hangupActiveAndAnswerWaiting() {
        if (mPhone != null) {
            mPhone.hangupActiveAndAnswerWaiting();
        } else {
            Log.e(this, "error hangupActiveAndAnswerWaiting, mPhone is null");
        }
    }

    /*
     * M: ALPS01766524. If Call ended, then send sms.
     */
    void sendMessageIfCallEnded(Context context, String callId, String phoneNumber,
            String textMessage) {
        if (mPhone != null && getTelecommCallById(callId) != null) {
            return;
        }

        if (textMessage != null) {
            final ComponentName component =
                    SmsApplication.getDefaultRespondViaMessageApplication(context,
                            false /*updateIfNeeded*/);
            if (component != null) {
                // Build and send the intent
                final Uri uri = Uri.fromParts("smsto", phoneNumber, null);
                final Intent intent = new Intent(TelephonyManager.ACTION_RESPOND_VIA_MESSAGE, uri);
                intent.putExtra(Intent.EXTRA_TEXT, textMessage);
                final Call call = CallList.getInstance().getCallById(callId);
                intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, call.getSubId());

                showMessageSentToast(phoneNumber, context);
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = phoneNumber;
                args.arg2 = context;
                intent.setComponent(component);
                context.startService(intent);
                Log.d(this, "sendMessageIfCallEnded message: " + textMessage);
            }
        }
    }

    /*
     * M: ALPS01784391. Show toast for sms.
     */
    private void showMessageSentToast(final String phoneNumber, final Context context) {
        // ...and show a brief confirmation to the user (since
        // otherwise it's hard to be sure that anything actually
        // happened.)
        final Resources res = context.getResources();
        final String formatString = res.getString(
                R.string.respond_via_sms_confirmation_format);
        final String confirmationMsg = String.format(formatString, phoneNumber);
        Toast.makeText(context, confirmationMsg,
                Toast.LENGTH_LONG).show();
    }

    public void updatePowerForSmartBook(boolean onOff) {
        if (mPhone != null) {
            mPhone.updatePowerForSmartBook(onOff);
        } else {
            Log.e(this, "error updatePowerForSmartBook, mPhone is null");
        }
    }

    /// M: For VoLTE @{
    public void inviteConferenceParticipants(String conferenceCallId, List<String> numbers) {
        if (mPhone != null) {
            getTelecommCallById(conferenceCallId).inviteConferenceParticipants(numbers);
        } else {
            Log.e(this, "inviteConferenceParticipants()... mPhone is null");
        }
    }
    /// @}
    
	// Add For synchronize ringer and UI
	void playIncomingCallRingtone(android.telecom.Call call) {
		if (mPhone != null) {
			Log.d(this, "playIncomingCallRingtone() Call:" + call);
			if (call != null) {
				call.playIncomingCallRingtone();
			} else {
				Log.e(this, "error playIncomingCallRingtone, call is null");
			}
		} else {
			Log.e(this, "error playIncomingCallRingtone, mPhone is null");
		}
	}
}
