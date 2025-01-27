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

package com.mediatek.ims;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.mediatek.internal.telephony.ITelephonyEx;
//import com.android.internal.telephony.gsm.GSMPhone;
import com.mediatek.xlog.Xlog;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.io.OutputStream;

public class ImsAdapter extends BroadcastReceiver {

    public static class VaEvent {
        public static final int DEFAULT_MAX_DATA_LENGTH = 40960;

        private int mPhoneId = SubscriptionManager.INVALID_PHONE_INDEX;
        private int request_id;
        private int data_len;
        private int read_offset;
        private byte data[];
        private int event_max_data_len = DEFAULT_MAX_DATA_LENGTH;

        /**
         * The VaEvent constructor with specified phone Id.
         *
         * @param phoneId the phone Id of the event
         * @param rid the request Id of the event
         */
        public VaEvent(int phoneId, int rid) {
            this(phoneId, rid, DEFAULT_MAX_DATA_LENGTH);
        }

        /**
         * The VaEvent constructor with specified phone Id.
         *
         * @param phoneId the phone Id of the event
         * @param rid the request Id of the event
         * @param length the max data length of the event
         */
        public VaEvent(int phoneId, int rid, int length) {
            mPhoneId = phoneId;
            request_id = rid;
            event_max_data_len = length;
            data = new byte[event_max_data_len];
            data_len = 0;
            read_offset = 0;
        }

        public int putInt(int value) {
            if (data_len > event_max_data_len - 4) {
                return -1;
            }

            synchronized (this) {
                for (int i = 0 ; i < 4 ; ++i) {
                    data[data_len] = (byte) ((value >> (8 * i)) & 0xFF);
                    data_len++;
                }
            }
            return 0;
        }

        public int putShort(int value) {
            if (data_len > event_max_data_len - 2) {
                return -1;
            }

            synchronized (this) {
                for (int i = 0 ; i < 2 ; ++i) {
                    data[data_len] = (byte) ((value >> (8 * i)) & 0xFF);
                    data_len++;
                }
            }

            return 0;
        }

        public int putByte(int value) {
            if (data_len > event_max_data_len - 1) {
                return -1;
            }

            synchronized (this) {
                data[data_len] = (byte) (value & 0xFF);
                data_len++;
            }

            return 0;
        }

        public int putString(String str, int len) {
            if (data_len > event_max_data_len - len) {
                return -1;
            }

            synchronized (this) {
                byte s[] = str.getBytes();
                if (len < str.length()) {
                    System.arraycopy(s, 0, data, data_len, len);
                    data_len += len;
                } else {
                    int remain = len - str.length();
                    System.arraycopy(s, 0, data, data_len, str.length());
                    data_len += str.length();
                    for (int i = 0 ; i < remain ; i++) {
                        data[data_len] = 0;
                        data_len++;
                    }
                }
            }

            return 0;
        }

        public int putBytes(byte [] value) {
            int len = value.length;

            if (len > event_max_data_len) {
                return -1;
            }

            synchronized (this) {
                System.arraycopy(value, 0, data, data_len, len);
                data_len += len;
            }

            return 0;
        }

        public byte [] getData() {
            return data;
        }

        public int getDataLen() {
            return data_len;
        }

        public int getRequestID() {
            return request_id;
        }

        public int getPhoneId() {
            return mPhoneId;
        }

        public int getInt() {
            int ret = 0;
            synchronized (this) {
                ret = ((data[read_offset + 3] & 0xff) << 24 | (data[read_offset + 2] & 0xff) << 16 | (data[read_offset + 1] & 0xff) << 8 | (data[read_offset] & 0xff));
                read_offset += 4;
            }
            return ret;
        }

        public int getShort() {
            int ret = 0;
            synchronized (this) {
                ret =  ((data[read_offset + 1] & 0xff) << 8 | (data[read_offset] & 0xff));
                read_offset += 2;
            }
            return ret;
        }

        // Notice: getByte is to get int8 type from VA, not get one byte.
        public int getByte() {
            int ret = 0;
            synchronized (this) {
                ret = (data[read_offset] & 0xff);
                read_offset += 1;
            }
            return ret;
        }

        public byte[] getBytes(int length) {
            if (length > data_len - read_offset) {
                return null;
            }

            byte[] ret = new byte[length];

            synchronized (this) {
                for (int i = 0 ; i < length ; i++) {
                    ret[i] = data[read_offset];
                    read_offset++;
                }
                return ret;
            }
        }

        public String getString(int len) {
            byte buf [] = new byte[len];

            synchronized (this) {
                System.arraycopy(data, read_offset, buf, 0, len);
                read_offset += len;
            }

            return (new String(buf)).trim();
        }
    }

    public class VaSocketIO extends Thread {
        private byte buf[];

        private int mTyp = -1;
        private int mId  = -1;
        private String mSocketName = null;
        private LocalSocket mSocket = null;
        private OutputStream mOut = null;
        private DataInputStream mDin = null;
        private boolean IS_USER_BUILD = "user".equals(Build.TYPE);
        private boolean IS_USERDEBUG_BUILD = "userdebug".equals(Build.TYPE);
        private boolean IS_ENG_BUILD = "eng".equals(Build.TYPE);

        private int mPhoneId = SubscriptionManager.INVALID_PHONE_INDEX;

        Object VaSocketIOThreadLock = new Object();

        public VaSocketIO(String socket_name) {
            mSocketName = socket_name;
            // TODO: buffer size confirm
            buf = new byte[8];
            Xlog.d(TAG, "VaSocketIO(): Enter");
        }

        public void run() {
            Xlog.d(TAG, "VaSocketIO(): Run");
            while (true) {
                if (misImsAdapterEnabled) {
                    boolean doTrm = false;
                    try {
                        if (mDin != null) {
                            // read the Event from mIO
                            VaEvent event = readEvent();

                            // TODO: need to confirm if event is null or not
                            if (event != null) {
                                Message msg = new Message();
                                msg.obj = event;
                                mImsEventDispatcher.sendMessage(msg);
                            }
                        }
                    } catch (InterruptedIOException e) {
                        disconnectSocket();
                        if (misImsAdapterEnabled && (IS_USER_BUILD || IS_USERDEBUG_BUILD)) {
                            doTrm = true;
                        }
                        e.printStackTrace();
                        Xlog.d(TAG, "VaSocketIO(): InterruptedIOException (" + (doTrm == true ? "1" : "0" ) + ")");
                    } catch (Exception e) {
                        disconnectSocket();
                        e.printStackTrace();
                        if (misImsAdapterEnabled && (IS_USER_BUILD || IS_USERDEBUG_BUILD)) {
                            doTrm = true;
                        }
                        Xlog.d(TAG, "VaSocketIO(): Exception (" + (doTrm == true ? "1" : "0" ) + ")");
                    }
                    
                    if (doTrm == true) {
                        int trmPhoneId = Util.getDefaultVoltePhoneId();
                        Xlog.d(TAG, "VaSocketIO(): recover Phone (trmPhoneId="
                                + trmPhoneId + ")");
                        try {
                            getITelephonyEx().setTrmForPhone(trmPhoneId, 2);
                        } catch (RemoteException re) {
                            Xlog.d(TAG, "VaSocketIO: phone trm exception (re: "+re.getMessage()+")");
                        } catch (NullPointerException npex) {
                            // This could happen before phone restarts due to crashing
                            Xlog.d(TAG, "VaSocketIO: phone trm exception (npex: "+npex.getMessage()+")");
                        }

                    }
                } else {
                    synchronized (VaSocketIOThreadLock) {
                        try {
                            Xlog.d(TAG, "VaSocketIO(): thread \""+Thread.currentThread().getId()+"\" enter wait state");
                            VaSocketIOThreadLock.wait();
                            Xlog.d(TAG, "VaSocketIO(): thread \""+Thread.currentThread().getId()+"\" leave wait state");
                        } catch (InterruptedException ie) {
                            Xlog.d(TAG, "VaSocketIO(): waiting thread \""+Thread.currentThread().getId()+"\" interrupted ("+ie.getMessage()+")");
                        }
                    }
                }
            }
        }

        public boolean connectSocket() {
            Xlog.d(TAG, "connectSocket() Enter");

            if (mSocket != null) {
                mSocket = null; // reset to null, create the new one
            }

            try {
                mSocket = new LocalSocket();
                LocalSocketAddress addr = new LocalSocketAddress(mSocketName, LocalSocketAddress.Namespace.RESERVED);

                mSocket.connect(addr);

                mOut = new BufferedOutputStream(mSocket.getOutputStream(), 4096);
                mDin = new DataInputStream(mSocket.getInputStream());

                int sendBufferSize = 0;
                sendBufferSize = mSocket.getSendBufferSize();
                mSocket.setSendBufferSize(512);
                sendBufferSize = mSocket.getSendBufferSize();

                mPhoneId = Util.getDefaultVoltePhoneId();
                Xlog.d(TAG, "connectSocket() update socket phone Id: " + mPhoneId);

            } catch (IOException e) {
                e.printStackTrace();
                disconnectSocket();
                return false;
            }
            return true;
        }

        public void disconnectSocket() {
            Xlog.d(TAG, "disconnectSocket() Enter, mOut=" + mOut);
            try {
                if (mSocket != null) {
                    mSocket.close();
                }
                if (mOut != null) {
                    mOut.close();
                }
                if (mDin != null) {
                    mDin.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mSocket = null;
                mOut = null;
                mDin = null;
                mPhoneId = SubscriptionManager.INVALID_PHONE_INDEX;
                Xlog.d(TAG, "disconnectSocket() reset socket phone Id");
            }
        }

        private void writeBytes(byte [] value, int len) throws IOException {
            mOut.write(value, 0, len);
        }

        private void writeInt(int value) throws IOException {
            for (int i = 0 ; i < 4 ; ++i) {
                mOut.write((value >> (8 * i)) & 0xff);
            }
        }

        public int writeEvent(VaEvent event) {
            Xlog.d(TAG, "writeEvent Enter");
            int ret = -1;
            try {
                synchronized (this) {
                    if (mOut != null) {
                        if (event.getPhoneId() == SubscriptionManager.INVALID_PHONE_INDEX
                                || event.getPhoneId() != mPhoneId) {
                            Xlog.d(TAG,
                                    "writeEvent event phoneId mismatch, event skipped. (event requestId="
                                            + event.getRequestID()
                                            + ", phoneId=" + event.getPhoneId()
                                            + ", socket phoneId=" + mPhoneId
                                            + ")");
                        } else {
                            dumpEvent(event);

                            writeInt(event.getRequestID());
                            writeInt(event.getDataLen());
                            writeBytes(event.getData(), event.getDataLen());
                            mOut.flush();
                            ret = 0;
                        }
                    } else {
                        Xlog.d(TAG, "mOut is null, socket is not setup");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            }

            return ret;
        }

        private int readInt() throws IOException {
            mDin.readFully(buf, 0, 4);
            return ((buf[3]) << 24 | (buf[2] & 0xff) << 16 | (buf[1] & 0xff) << 8 | (buf[0] & 0xff));
        }

        private void readFully(byte b[], int off, int len) throws IOException {
            mDin.readFully(b, off, len);
        }

        private VaEvent readEvent() throws IOException {
            Xlog.d(TAG, "readEvent Enter");
            int request_id;
            int data_len;
            byte buf [];
            VaEvent event;

            request_id = readInt();
            data_len = readInt();
            buf = new byte[data_len];
            readFully(buf, 0, data_len);

            int phoneId = Util.getDefaultVoltePhoneId();
            event = new VaEvent(phoneId, request_id);
            event.putBytes(buf);

            dumpEvent(event);
            return event;
        }

        private void dumpEvent(VaEvent event) {
            Xlog.d(TAG, "dumpEvent: phone_id:" + event.getPhoneId()
                    + ",reqiest_id:" + event.getRequestID()
                    + ",data_len:" + event.getDataLen()
                    + ",event:" + event.getData());
        }

    }

    /* ImsAdapter class */
    private static final String SOCKET_NAME1 = "volte_imsa1";
    private static final String SOCKET_NAME2 = "volte_imsa2";

    private static final String TAG = "ImsAdapter";
    private Context mContext;
    //private GSMPhone mPhone;
    private VaSocketIO mIO;
    private static ImsEventDispatcher mImsEventDispatcher;

    private static ImsAdapter mInstance;
    private static boolean misImsAdapterEnabled = false;
    private static boolean misImsAdapterInit = false;
    private static boolean mImsServiceUp = false;
    private static int sRatType = 0;

    public ImsAdapter(Context context) {

        mContext = context;

        if (mInstance == null) {
            mInstance = this;
        }

        Xlog.d(TAG, "ImsAdapter(): ImsAdapter Enter");
        // new the mIO object to communicate with the va
        mIO = new VaSocketIO(SOCKET_NAME1);
        mImsEventDispatcher = new ImsEventDispatcher(mContext, mIO);

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_CLEAR_DATA_BEARER_FINISHED);

        mContext.registerReceiver(this, filter);

        mIO.start();
    }

    public static ImsAdapter getInstance() {
        return mInstance;
    }

    public void enableImsAdapter() {
        Xlog.d(TAG, "enableImsAdapter: misImsAdapterEnabled="
                + misImsAdapterEnabled + ", misImsAdapterInit="
                + misImsAdapterInit);

        if (!misImsAdapterEnabled && !misImsAdapterInit) {
            misImsAdapterInit = true;
            // disconnect Socket first
            mIO.disconnectSocket();

            // stop Va process first, to ensure it correct.
            stopVaProcess();
            Intent intent = new Intent(TelephonyIntents.ACTION_CLEAR_DATA_BEARER_NOTIFY);
            intent.putExtra(PhoneConstants.PHONE_KEY, Util.getDefaultVoltePhoneId());
            mContext.sendBroadcast(intent);
        }
    }

    public boolean getImsAdapterEnable() {
        return misImsAdapterEnabled;
    }

    private Handler mHandler = new Handler();
    private EnableImsRunnable mEnableImsRunnable;

    private class EnableImsRunnable implements Runnable {

        public void run() {

            if (misImsAdapterInit) {
                if (mIO.connectSocket() == true) {
                    Xlog.d(TAG, "EnableImsRunnable(): connectSocket success");

                    // start domain event dispatcher to recieve broadcast
                    mImsEventDispatcher.enableRequest();

                    misImsAdapterEnabled = true;
                    synchronized (mIO.VaSocketIOThreadLock) {
                        mIO.VaSocketIOThreadLock.notify();
                    }
                    misImsAdapterInit = false;
                } else {
                    Xlog.d(TAG, "EnableImsRunnable(): connectSocket error");
                    // restart Va process, and reconnect again
                    stopVaProcess();
                    enableImsAdapter2ndStage();
                }
            }
        }
    };

    private void enableImsAdapter2ndStage() {
        Xlog.d(TAG, "enableImsAdapter2ndStage()Enter");

        SystemProperties.set("ril.volte.stack", "1");
        SystemProperties.set("ril.volte.ua", "1");
        SystemProperties.set("ril.volte.imcb", "1");
        if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
            SystemProperties.set("ril.volte.wfca", "1");
        }
        Xlog.d(TAG, "enableImsAdapter2ndStage(): Va process started!");

        if (mEnableImsRunnable != null) {
            mHandler.removeCallbacks(mEnableImsRunnable);
            mEnableImsRunnable = null;
        }

        mEnableImsRunnable = new EnableImsRunnable();
        mHandler.postDelayed(mEnableImsRunnable, 3000);
    }

    public void disableImsAdapter(boolean isNormalDisable) {

        Xlog.d(TAG, "disableImsAdapter(): misImsAdapterEnabled="
                + misImsAdapterEnabled + ", misImsAdapterInit="
                + misImsAdapterInit + ", isNormalDisable=" + isNormalDisable);
        
        if(misImsAdapterEnabled || misImsAdapterInit) {
            
            if (mEnableImsRunnable != null) {
                mHandler.removeCallbacks(mEnableImsRunnable);
                mEnableImsRunnable = null;
            }

            mIO.disconnectSocket();

            if (misImsAdapterEnabled) {
                mImsEventDispatcher.disableRequest();
                misImsAdapterEnabled = false;
            }
            misImsAdapterInit = false;

            stopVaProcess();
        }
    }

    private void stopVaProcess() {
        Xlog.d(TAG, "stopVaProcess");
        SystemProperties.set("ril.volte.stack", "0");
        SystemProperties.set("ril.volte.ua", "0");
        SystemProperties.set("ril.volte.imcb", "0");
        if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
            SystemProperties.set("ril.volte.wfca", "0");
        }
    }

    // for AP side UT, set event and call ImsAdapter.sendTestEvent(event)
    public void sendTestEvent(VaEvent event) {
        // Sample Code:
        // new the event object for Test Event
        // VaEvent event = new VaEvent(MSG_ID_IMSA_IMCB_TEST_A);
        // event.putInt(2);
        // event.putInt(3);
        mImsEventDispatcher.dispatchCallback(event);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Xlog.d(TAG, "onReceive, intent action is " + action);

        if (TelephonyIntents.ACTION_CLEAR_DATA_BEARER_FINISHED.equals(action)) {
            enableImsAdapter2ndStage();
        }
    }

    public void ImsServiceUp() {
        mImsServiceUp = true;
        Xlog.d(TAG, "ImsServiceUp, start to ACTION_IMS_SERVICE_UP intent");
/*
        Intent intent = new Intent(ImsManager.ACTION_IMS_SERVICE_UP);
        mContext.sendBroadcast(intent);
*/
    }

    public boolean getImsServiceUp() {
        return mImsServiceUp;
    }

    public static int getRatType() {
        return sRatType;
    }

    public static void setRatType(int rat_type) {
        sRatType = rat_type;
    }
    /**
     * This is a utility class for ImsAdapter related work.
     */
    public static class Util {

        /**
         * To get current the default Volte Phone Id.
         * Only for single 4G DSDS project, and it should always align to the 4G phone Id.
         *
         * @return current default Volte Phone Id. (align to 4G phone Id)
         */
        public static int getDefaultVoltePhoneId() {
            int phoneId = SystemProperties.getInt(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, 1) - 1;
            if (phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) {
                phoneId = SubscriptionManager.INVALID_PHONE_INDEX;
            }
            return phoneId;
        }

    }
    
    private ITelephonyEx getITelephonyEx() {
        return ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
    }
}

