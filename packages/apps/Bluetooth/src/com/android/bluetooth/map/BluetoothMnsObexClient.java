/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
* Copyright (C) 2014 Samsung System LSI
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
package com.android.bluetooth.map;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseBooleanArray;

import java.io.IOException;
import java.io.OutputStream;

import javax.obex.ClientOperation;
import javax.obex.ClientSession;
import javax.obex.HeaderSet;
import javax.obex.ObexTransport;
import javax.obex.ResponseCodes;

/**
 * The Message Notification Service class runs its own message handler thread,
 * to avoid executing long operations on the MAP service Thread.
 * This handler context is passed to the content observers,
 * hence all call-backs (and thereby transmission of data) is executed
 * from this thread.
 */
public class BluetoothMnsObexClient {

    private static final String TAG = "[MAP]BluetoothMnsObexClient";
    private static final boolean D = true;
    private static final boolean V = true;

    private ObexTransport mTransport;
    public Handler mHandler = null;
    private volatile boolean mWaitingForRemote;
    private static final String TYPE_EVENT = "x-bt/MAP-event-report";
    private ClientSession mClientSession;
    private boolean mConnected = false;
    BluetoothDevice mRemoteDevice;
    private SparseBooleanArray mRegisteredMasIds = new SparseBooleanArray(1);

    private HeaderSet mHsConnect = null;
    private Handler mCallback = null;

    // Used by the MAS to forward notification registrations
    public static final int MSG_MNS_NOTIFICATION_REGISTRATION = 1;
    public static final int MSG_MNS_SEND_EVENT = 2;


    public static final ParcelUuid BLUETOOTH_UUID_OBEX_MNS =
            ParcelUuid.fromString("00001133-0000-1000-8000-00805F9B34FB");


    public BluetoothMnsObexClient(BluetoothDevice remoteDevice, Handler callback) {
        if (remoteDevice == null) {
            throw new NullPointerException("Obex transport is null");
        }
        mRemoteDevice = remoteDevice;
        HandlerThread thread = new HandlerThread("BluetoothMnsObexClient");
        thread.start();
        /* This will block until the looper have started, hence it will be safe to use it,
           when the constructor completes */
        Looper looper = thread.getLooper();
        mHandler = new MnsObexClientHandler(looper);
        mCallback = callback;
    }

    public Handler getMessageHandler() {
        return mHandler;
    }

    private final class MnsObexClientHandler extends Handler {
        private MnsObexClientHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_MNS_NOTIFICATION_REGISTRATION:
                handleRegistration(msg.arg1 /*masId*/, msg.arg2 /*status*/);
                break;
            case MSG_MNS_SEND_EVENT:
                sendEventHandler((byte[])msg.obj/*byte[]*/, msg.arg1 /*masId*/);
                break;
            default:
                break;
            }
        }
    }

    public boolean isConnected() {
        return mConnected;
    }

    /**
     * Disconnect the connection to MNS server.
     * Call this when the MAS client requests a de-registration on events.
     */
    public synchronized void disconnect() {
        try {
            if (mClientSession != null) {
                mClientSession.disconnect(null);
                if (D) Log.d(TAG, "[disconnect] OBEX session disconnected");
            }
        } catch (IOException e) {
            Log.w(TAG, "[disconnect] OBEX session disconnect error " + e.getMessage());
        }
        try {
            if (mClientSession != null) {
                if (D) Log.d(TAG, "[disconnect] OBEX session close mClientSession");
                mClientSession.close();
                mClientSession = null;
                if (D) Log.d(TAG, "[disconnect] OBEX session closed");
            }
        } catch (IOException e) {
            Log.w(TAG, "[disconnect] OBEX session close error:" + e.getMessage());
        }
        if (mTransport != null) {
            try {
                if (D) Log.d(TAG, "[disconnect] Close Obex Transport");
                mTransport.close();
                mTransport = null;
                mConnected = false;
                if (D) Log.d(TAG, "[disconnect] Obex Transport Closed");
            } catch (IOException e) {
                Log.e(TAG, "[disconnect] mTransport.close error: " + e.getMessage());
            }
        }
    }

    /**
     * Shutdown the MNS.
     */
    public void shutdown() {
        if (D) Log.d(TAG, "[shutdown] begin");
        /* should shutdown handler thread first to make sure
         * handleRegistration won't be called when disconnect
         */
        if (mHandler != null) {
            // Shut down the thread
            mHandler.removeCallbacksAndMessages(null);
            Looper looper = mHandler.getLooper();
            if (looper != null) {
                looper.quit();
            }
            mHandler = null;
        }

        /* Disconnect if connected */
        disconnect();

        mRegisteredMasIds.clear();
    }

    /**
     * We store a list of registered MasIds only to control connect/disconnect
     * @param masId
     * @param notificationStatus
     */
    public void handleRegistration(int masId, int notificationStatus){
        if(D) Log.d(TAG, "handleRegistration( " + masId + ", " + notificationStatus + ")");

        if(notificationStatus == BluetoothMapAppParams.NOTIFICATION_STATUS_NO) {
            mRegisteredMasIds.delete(masId);
        } else if(notificationStatus == BluetoothMapAppParams.NOTIFICATION_STATUS_YES) {
            /* Connect if we do not have a connection, and start the content observers providing
             * this thread as Handler.
             */
            if(isConnected() == false) {
                if(D) Log.d(TAG, "handleRegistration: connect");
                connect();
            }
            mRegisteredMasIds.put(masId, true); // We don't use the value for anything
        }
        if(mRegisteredMasIds.size() == 0) {
            // No more registrations - disconnect
            if(D) Log.d(TAG, "handleRegistration: disconnect");
            disconnect();
        }
    }

    public void connect() {
        Log.d(TAG, "[connect] begin");

        mConnected = true;

        BluetoothSocket btSocket = null;
        try {
            // TODO: Why insecure? - is it because the link is already encrypted?
            btSocket = mRemoteDevice.createInsecureRfcommSocketToServiceRecord(
                    BLUETOOTH_UUID_OBEX_MNS.getUuid());
            Log.d(TAG, "[connect] run");
            btSocket.connect();
        } catch (IOException e) {
            Log.e(TAG, "[connect] BtSocket Connect error " + e.getMessage(), e);
            // TODO: do we need to report error somewhere?
            mConnected = false;
            return;
        }

        mTransport = new BluetoothMnsRfcommTransport(btSocket);

        try {
            mClientSession = new ClientSession(mTransport);
        } catch (IOException e1) {
            Log.e(TAG, "OBEX session create error " + e1.getMessage());
            mConnected = false;
        }
        if (mConnected && mClientSession != null) {
            boolean connected = false;
            HeaderSet hs = new HeaderSet();
            // bb582b41-420c-11db-b0de-0800200c9a66
            byte[] mnsTarget = { (byte) 0xbb, (byte) 0x58, (byte) 0x2b, (byte) 0x41,
                                 (byte) 0x42, (byte) 0x0c, (byte) 0x11, (byte) 0xdb,
                                 (byte) 0xb0, (byte) 0xde, (byte) 0x08, (byte) 0x00,
                                 (byte) 0x20, (byte) 0x0c, (byte) 0x9a, (byte) 0x66 };
            hs.setHeader(HeaderSet.TARGET, mnsTarget);

            synchronized (this) {
                mWaitingForRemote = true;
            }
            try {
                mHsConnect = mClientSession.connect(hs);
                if (D) Log.d(TAG, "[connect] OBEX session created");
                connected = true;
            } catch (IOException e) {
                Log.e(TAG, "[connect] OBEX session connect error " + e.getMessage());
            }
            mConnected = connected;
        }
            synchronized (this) {
                mWaitingForRemote = false;
        }
    }

    /**
     * Call this method to queue an event report to be send to the MNS server.
     * @param eventBytes the encoded event data.
     * @param masInstanceId the MasId of the instance sending the event.
     */
    public void sendEvent(byte[] eventBytes, int masInstanceId) {
        // We need to check for null, to handle shutdown.
        if(mHandler != null) {
            Message msg = mHandler.obtainMessage(MSG_MNS_SEND_EVENT, masInstanceId, 0, eventBytes);
            if(msg != null) {
                msg.sendToTarget();
            }
        }
        notifyUpdateWakeLock();
    }

    private int sendEventHandler(byte[] eventBytes, int masInstanceId) {

        boolean error = false;
        int responseCode = -1;
        HeaderSet request;
        int maxChunkSize, bytesToWrite, bytesWritten = 0;
        ClientSession clientSession = mClientSession;

        if ((!mConnected) || (clientSession == null)) {
            Log.w(TAG, "sendEvent after disconnect:" + mConnected);
            return responseCode;
        }

        request = new HeaderSet();
        BluetoothMapAppParams appParams = new BluetoothMapAppParams();
        appParams.setMasInstanceId(masInstanceId);

        ClientOperation putOperation = null;
        OutputStream outputStream = null;

        try {
            request.setHeader(HeaderSet.TYPE, TYPE_EVENT);
            request.setHeader(HeaderSet.APPLICATION_PARAMETER, appParams.EncodeParams());

            if (mHsConnect.mConnectionID != null) {
                request.mConnectionID = new byte[4];
                System.arraycopy(mHsConnect.mConnectionID, 0, request.mConnectionID, 0, 4);
            } else {
                Log.w(TAG, "sendEvent: no connection ID");
            }

            synchronized (this) {
                mWaitingForRemote = true;
            }
            // Send the header first and then the body
            try {
                if (V) Log.v(TAG, "[sendEvent] Send headerset Event ");
                putOperation = (ClientOperation)clientSession.put(request);
                // TODO - Should this be kept or Removed

            } catch (IOException e) {
                Log.e(TAG, "[sendEvent] Error when put HeaderSet " + e.getMessage());
                error = true;
            }
            synchronized (this) {
                mWaitingForRemote = false;
            }
            if (!error) {
                try {
                    if (V) Log.v(TAG, "[sendEvent] Send headerset Event ");
                    outputStream = putOperation.openOutputStream();
                } catch (IOException e) {
                    Log.e(TAG, "[sendEvent] Error when opening OutputStream " + e.getMessage());
                    error = true;
                }
            }

            if (!error) {

                maxChunkSize = putOperation.getMaxPacketSize();

                while (bytesWritten < eventBytes.length) {
                    bytesToWrite = Math.min(maxChunkSize, eventBytes.length - bytesWritten);
                    outputStream.write(eventBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }

                if (bytesWritten == eventBytes.length) {
                    Log.i(TAG, "SendEvent finished send length" + eventBytes.length);
                } else {
                    error = true;
                    putOperation.abort();
                    Log.i(TAG, "[SendEvent] interrupted");
                }
            }
        } catch (IOException e) {
            handleSendException(e.toString());
            error = true;
        } catch (IndexOutOfBoundsException e) {
            handleSendException(e.toString());
            error = true;
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error when closing stream after send " + e.getMessage());
            }
            try {
                if ((!error) && (putOperation != null)) {
                    responseCode = putOperation.getResponseCode();
                    if (responseCode != -1) {
                        if (V) Log.v(TAG, "[sendEvent] Put response code " + responseCode);
                        if (responseCode != ResponseCodes.OBEX_HTTP_OK) {
                            Log.i(TAG, "[sendEvent] Response error code is " + responseCode);
                        }
                    }
                }
                if (putOperation != null) {
                    putOperation.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "[sendEvent] Error when closing stream after send " + e.getMessage());
            }
        }

        return responseCode;
    }

    private void handleSendException(String exception) {
        Log.e(TAG, "[handleSendException] Error when sending event: " + exception);
    }

    private void notifyUpdateWakeLock() {
        if(mCallback != null) {
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothMapService.MSG_ACQUIRE_WAKE_LOCK;
            msg.sendToTarget();
        }
    }
}
