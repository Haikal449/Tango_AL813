/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.mms.ui;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.provider.BaseColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Conversations;
import android.provider.Telephony.TextBasedSmsColumns;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import com.android.mms.R;
import com.google.android.mms.MmsException;

import java.util.regex.Pattern;

/// M:
import android.content.ContentUris;
import android.net.Uri;
import android.os.Message;
import android.telephony.SmsManager;

import com.google.android.mms.pdu.PduHeaders;
import com.android.mms.util.MmsLog;
import android.provider.Telephony;
import com.mediatek.ipmsg.util.IpMessageUtils;
import com.mediatek.mms.ext.IMmsMultiDeleteAndForwardExt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/// M: add for ipmessage
import android.text.TextUtils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/*HQ_zhangjing add for al812 mms ui begin*/
import huawei.android.widget.TimeAxisWidget;
import java.util.Calendar;
import android.widget.LinearLayout;
/*HQ_zhangjing add for al812 mms ui end*/

/**
 * The back-end data adapter of a message list.
 */
public class MessageListAdapter extends MessageCursorAdapter {
    private static final String TAG = "MessageListAdapter";
    /// M: add for ipmessage
    public static final int MESSAGE_LIST_REFRASH                    = 0;
    public static final int MESSAGE_LIST_REFRASH_WITH_CLEAR_CACHE   = 1;
    private static final String IPMSG_TAG = "Mms/ipmsg/MessageListAdapter";
    private static final String TAG_DIVIDER = "Mms/divider";
    private static final boolean LOCAL_LOGV = false;
    private static final String SMS_IP_MESSAGE_ID = Telephony.Sms.IPMSG_ID;
    private static final String SMS_SPAM = "spam";
    private Set<Long> mItemDividerSet;
    private HashMap<Long, Integer> mNotifyIndStatusMap;

    /// M: add for OP09 Feature, two new columns for mms cc.
    private static final String MMS_CC          = "mms_cc";
    private static final String MMS_CC_ENCODING = "mms_cc_encoding";

    static final String[] PROJECTION = new String[] {
        // TODO: should move this symbol into com.android.mms.telephony.Telephony.
        MmsSms.TYPE_DISCRIMINATOR_COLUMN,
        BaseColumns._ID,
        Conversations.THREAD_ID,
        // For SMS
        Sms.ADDRESS,
        Sms.BODY,
        Sms.DATE,
        Sms.DATE_SENT,
        Sms.READ,
        Sms.TYPE,
        Sms.STATUS,
        Sms.LOCKED,
        Sms.ERROR_CODE,
        // For MMS
        Mms.SUBJECT,
        Mms.SUBJECT_CHARSET,
        Mms.DATE,
        Mms.DATE_SENT,
        Mms.READ,
        Mms.MESSAGE_TYPE,
        Mms.MESSAGE_BOX,
        Mms.DELIVERY_REPORT,
        Mms.READ_REPORT,
        PendingMessages.ERROR_TYPE,
        Mms.LOCKED,
        Mms.STATUS,
        /// M: @{
        Telephony.Sms.SUBSCRIPTION_ID,
        Telephony.Mms.SUBSCRIPTION_ID,
        Sms.SERVICE_CENTER,
        Telephony.Mms.SERVICE_CENTER,
        /// @}
        /// M: add for ipmessage
        SMS_IP_MESSAGE_ID,
        SMS_SPAM,
        /// M: add for OP09 Feature, mms cc.
        MMS_CC,
        MMS_CC_ENCODING,
    };

    // The indexes of the default columns which must be consistent
    // with above PROJECTION.
    static final int COLUMN_MSG_TYPE            = 0;
    static final int COLUMN_ID                  = 1;
    static final int COLUMN_THREAD_ID           = 2;
    static final int COLUMN_SMS_ADDRESS         = 3;
    static final int COLUMN_SMS_BODY            = 4;
    static final int COLUMN_SMS_DATE            = 5;
    static final int COLUMN_SMS_DATE_SENT       = 6;
    static final int COLUMN_SMS_READ            = 7;
    static final int COLUMN_SMS_TYPE            = 8;
    static final int COLUMN_SMS_STATUS          = 9;
    static final int COLUMN_SMS_LOCKED          = 10;
    static final int COLUMN_SMS_ERROR_CODE      = 11;
    static final int COLUMN_MMS_SUBJECT         = 12;
    static final int COLUMN_MMS_SUBJECT_CHARSET = 13;
    static final int COLUMN_MMS_DATE            = 14;
    static final int COLUMN_MMS_DATE_SENT       = 15;
    static final int COLUMN_MMS_READ            = 16;
    static final int COLUMN_MMS_MESSAGE_TYPE    = 17;
    static final int COLUMN_MMS_MESSAGE_BOX     = 18;
    static final int COLUMN_MMS_DELIVERY_REPORT = 19;
    static final int COLUMN_MMS_READ_REPORT     = 20;
    static final int COLUMN_MMS_ERROR_TYPE      = 21;
    static final int COLUMN_MMS_LOCKED          = 22;
    static final int COLUMN_MMS_STATUS          = 23;

    private static final int CACHE_SIZE         = 50;

    /// M:
    private static final int INCOMING_ITEM_TYPE = 0;
    private static final int OUTGOING_ITEM_TYPE = 1;
    /// M: add for ipmessage
    private static final int INCOMING_ITEM_TYPE_IPMSG = 2;
    private static final int OUTGOING_ITEM_TYPE_IPMSG = 3;
    /// M: add for ipmessage
    private int[] layoutList = { R.layout.message_list_item_recv, R.layout.message_list_item_send
            , R.layout.message_list_item_recv_ipmsg, R.layout.message_list_item_send_ipmsg
            };

    protected LayoutInflater mInflater;
    private final MessageItemCache mMessageItemCache;
    private final ColumnsMap mColumnsMap;
    private OnDataSetChangedListener mOnDataSetChangedListener;
    private Handler mMsgListItemHandler;
    private Pattern mHighlight;
    private Context mContext;
    /// M: google JB.MR1 patch, group mms
    private boolean mIsGroupConversation;

    /// M: @{
    public static final String CACHE_TAG = "Mms/MessageItemCache";

    static final int COLUMN_SMS_SUBID           = 24;
    static final int COLUMN_MMS_SUBID           = 25;
    static final int COLUMN_SMS_SERVICE_CENTER  = 26;
    static final int COLUMN_MMS_SERVICE_CENTER  = 27;
    /// M: add for ipmessage
    static final int COLUMN_SMS_IP_MESSAGE_ID   = 28;
    static final int COLUMN_SMS_SPAM            = 29;

    /// M: add for OP09 Feature, mms cc.
    static final int COLUMN_MMS_CC              = 30;
    static final int COLUMN_MMS_CC_ENCODING     = 31;

    private boolean mClearCacheFlag = true;
    static final int MSG_LIST_NEED_REFRASH   = 100;

    /// M: add for multi-delete
    public boolean mIsDeleteMode = false;
    Map<Long, Boolean> mListItem;
    private Map<String, Boolean> mSimMsgListItem;

    ///M: add for adjust text size
    private float mTextSize = 0;
    /// @}
    /// M: Operator Plugin
    private IMmsMultiDeleteAndForwardExt mDeleteAndForwardPlugin  = null;
    /// M: For OP09; If mms notification has been delete and there is an exception occurred when load it.We should hide
    /// the view.
    private boolean mNeedHideView = false;

    public MessageListAdapter(
            Context context, Cursor c, ListView listView,
            boolean useDefaultColumnsMap, Pattern highlight) {
        super(context, c, FLAG_REGISTER_CONTENT_OBSERVER);
        mContext = context;
        mHighlight = highlight;

        mInflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        mMessageItemCache = new MessageItemCache(CACHE_SIZE);

        /// M: @{
        mListItem = new LinkedHashMap<Long, Boolean>();
        mSimMsgListItem = new HashMap<String, Boolean>();
        mItemDividerSet = new HashSet<Long>();
        mNotifyIndStatusMap = new HashMap<Long, Integer>();
        /// @}

        if (useDefaultColumnsMap) {
            mColumnsMap = new ColumnsMap();
        } else {
            mColumnsMap = new ColumnsMap(c);
        }

        listView.setRecyclerListener(new AbsListView.RecyclerListener() {
            @Override
			/*HQ_zhangjing add for al812 mms ui begin*/
			//public void onMovedToScrapHeap(View view) {
            public void onMovedToScrapHeap(View itemView) {
				TimeAxisWidget taw = (TimeAxisWidget)itemView.findViewById(R.id.item_date);
				View view = taw.getContent();
			/*HQ_zhangjing add for al812 mms ui end*/
                if (view instanceof MessageListItem) {
                    MessageListItem mli = (MessageListItem) view;
                    // Clear references to resources
                    mli.unbind();
                }
            }
        });
    }

    public MessageListAdapter(
            Context context, Cursor c, ListView listView,
            boolean useDefaultColumnsMap, Pattern highlight, IMmsMultiDeleteAndForwardExt DeleteAndForwardPlugin) {
        this(context, c, listView, useDefaultColumnsMap, highlight);
        Log.d(TAG, "MessageListAdapter 1");
        mDeleteAndForwardPlugin = DeleteAndForwardPlugin;
        mDeleteAndForwardPlugin.initBodyandAddress();
    }

    @Override
    public void bindView(View itemView, Context context, Cursor cursor) {
        MmsLog.d(CACHE_TAG, "bindView() start.");
		/*HQ_zhangjing add for al812 mms ui begin*/
		TimeAxisWidget taw = (TimeAxisWidget) itemView.findViewById(R.id.item_date);
		View view = taw.getContent();
		/*HQ_zhangjing add for al812 mms ui end*/
        MessageItem msgItem = null;
        if (view instanceof MessageListItem) {
            view.setVisibility(View.VISIBLE);
            String type = cursor.getString(mColumnsMap.mColumnMsgType);
            long msgId = cursor.getLong(mColumnsMap.mColumnMsgId);
            mNeedHideView = false;
            msgItem = getCachedMessageItem(type, msgId, cursor);
            if (!mIsScrolling || mIsDeleteMode) {
                // M:just for UT performance,2--state:SENT
                long smsState = cursor.getLong(mColumnsMap.mColumnSmsType);
                long mmsState = cursor.getLong(mColumnsMap.mColumnMmsMessageBox);
                if (smsState == 2 || mmsState == 2) {
                    MmsLog.d(CACHE_TAG, "bindView():UT/performance:message sent success");
                }
                MmsLog.d(CACHE_TAG, "bindView():type=" + type + ", msgId=" + msgId);

                MmsLog.d(CACHE_TAG, "OpenOneThread Check Point");
                if (msgItem != null) {
                    MessageListItem mli = (MessageListItem) view;
                    /// M: @{
                    /// M: for multi-delete
                    if (mIsDeleteMode) {
                        /// M: fix bug ALPS00432495, avoid IllegalArgumentException @{
                        if (msgItem.isSubMsg() && cursor.getColumnIndex("index_on_icc") != -1) {
                            String msgIndex = cursor.getString(cursor.getColumnIndexOrThrow("index_on_icc"));
                            MmsLog.d(CACHE_TAG, "bindView(): type=" + type + ",simMsg msgIndex=" + msgIndex);
                            String[] index = msgIndex.split(";");
//                            for (int n = 0; n < index.length; n++) {
                                if (mSimMsgListItem.get(index[0]) == null) {
                                    mSimMsgListItem.put(index[0], false);
                                } else {
                                    msgItem.setSelectedState(mSimMsgListItem.get(index[0]));
                                }
//                            }
                        } else {
                            msgId = getKey(type, msgId);
                            if (mListItem.get(msgId) == null) {
                                mListItem.put(msgId, false);
                            } else {
                                msgItem.setSelectedState(mListItem.get(msgId));
                            }
                        }
                    }
                    /// @}
                    int position = cursor.getPosition();
                    /// M: google JB.MR1 patch, group mms
                    mli.bind(msgItem, mIsGroupConversation, position, mIsDeleteMode);
                    mli.setMsgListItemHandler(mMsgListItemHandler);
                    mli.setMessageListItemAdapter(this);
                } else {
                    MessageListItem mli = (MessageListItem) view;
                    if (mNeedHideView) {
                        mNeedHideView = false;
                        mli.hideAllView();
                        view.setVisibility(View.GONE);
                        return;
                    }
                    mli.bindDefault(null, cursor.getPosition() == cursor.getCount() - 1);
                }
            } else {
             // M:for ALPS01065027,just for compose sms messagelist in scrolling
                try {
                    MessageListItem mli = (MessageListItem) view;
                    if (msgItem == null) {
                        msgItem = new MessageItem(mContext, mColumnsMap, cursor);
                    }
                    mli.bindDefault(msgItem, cursor.getPosition() == cursor.getCount() - 1);
                } catch (MmsException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            /// M: add for text zoom
            if (mTextSize != 0) {
                MessageListItem mli = (MessageListItem) view;
                mli.setBodyTextSize(mTextSize);
            }
        }
		/*HQ_zhangjing add for al812 mms ui begin*/
		if(msgItem != null){
			Log.d(TAG,"zhangjing,msgItem not null");
			Calendar cal = Calendar.getInstance();
        	cal.setTimeInMillis(msgItem.getMessageDate());
        	taw.setCalendar(cal);
		}
		/*HQ_zhangjing add for al812 mms ui end*/
    }

    public interface OnDataSetChangedListener {
        void onDataSetChanged(MessageListAdapter adapter);
        void onContentChanged(MessageListAdapter adapter);
    }

    public void setOnDataSetChangedListener(OnDataSetChangedListener l) {
        mOnDataSetChangedListener = l;
    }

    public void setMsgListItemHandler(Handler handler) {
        mMsgListItemHandler = handler;
    }

    /// M: google JB.MR1 patch, group mms
    public void setIsGroupConversation(boolean isGroup) {
        mIsGroupConversation = isGroup;
    }

    /// M: fix bug ALPS00488976, group mms
    public boolean isGroupConversation() {
        return mIsGroupConversation;
    }
    /// @}

    public void cancelBackgroundLoading() {
        mMessageItemCache.evictAll();   // causes entryRemoved to be called for each MessageItem
                                        // in the cache which causes us to cancel loading of
                                        // background pdu's and images.
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        if (LOCAL_LOGV) {
            Log.v(TAG, "MessageListAdapter.notifyDataSetChanged().");
        }

        if (mClearCacheFlag) {
            mMessageItemCache.evictAll();
        }
        mClearCacheFlag = true;

        if (mOnDataSetChangedListener != null) {
            mOnDataSetChangedListener.onDataSetChanged(this);
        }
    }

    @Override
    protected void onContentChanged() {
        if (getCursor() != null && !getCursor().isClosed()) {
            if (mOnDataSetChangedListener != null) {
                mOnDataSetChangedListener.onContentChanged(this);
            }
        }
    }
/*HQ_zhangjing add for al812 mms ui begin*/
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
		View v = mInflater.inflate(R.layout.list_item_timeaxis, parent, false);
		TimeAxisWidget taw = (TimeAxisWidget) v.findViewById(R.id.item_date);
		View listContent;
        /// M: add for ipmessage
        switch ( getItemViewType(cursor) ) {
        case INCOMING_ITEM_TYPE:
            listContent =  mInflater.inflate(R.layout.message_list_item_recv, parent, false);
			break;
        case OUTGOING_ITEM_TYPE:
            listContent =  mInflater.inflate(R.layout.message_list_item_send, parent, false);
			break;
        case INCOMING_ITEM_TYPE_IPMSG:
            listContent =  mInflater.inflate(R.layout.message_list_item_recv_ipmsg, parent, false);
			break;
        case OUTGOING_ITEM_TYPE_IPMSG:
            listContent =  mInflater.inflate(R.layout.message_list_item_send_ipmsg, parent, false);
			break;
        default:
            listContent =  mInflater.inflate(R.layout.message_list_item_send, parent, false);
			break;
        }
		taw.setContent(listContent);
		/*HQ_zhangjing 2015-10-26 modified for CQ  HQ01456345 begin*/
		LinearLayout viewTime = (LinearLayout)(listContent.getParent());
		if( viewTime != null ){
			viewTime.setPadding(0,0,0,0);
		}
		/*HQ_zhangjing 2015-10-26 modified for CQ  HQ01456345 end*/
		return	v;
    }
/*HQ_zhangjing add for al812 mms ui end*/


    public MessageItem getCachedMessageItem(String type, long msgId, Cursor c) {
        final long key = getKey(type, msgId);
        MessageItem item = mMessageItemCache.get(key);
        MmsLog.d(CACHE_TAG, "getCachedMessageItem(): key=" + key + ", item is in cache?=" + (item != null));
        if (item == null && c != null && isCursorValid(c)) {
            /// M: add for ipmessage, add isDrawTimeDivider status to MessageItem.
            final boolean isDrawTimeDivider = isMessageItemShowTimeDivider(c);
            final boolean isDrawUnreadDivider = isMessageItemShowUnreadDivider(c);
            final int unreadCount = isDrawUnreadDivider ? mUnreadCount : 0;
            MmsLog.d(CACHE_TAG, "getCachedMessageItem(): isDrawTimeDivider="
                    + isDrawTimeDivider + ", isDrawUnreadDivider=" + isDrawUnreadDivider
                    + ", unreadCount=" + unreadCount);
            if (type.equals("mms")) {
                MmsLog.d(CACHE_TAG, "getCachedMessageItem(): no cache, create one MessageItem on background.");
                final int boxId = c.getInt(mColumnsMap.mColumnMmsMessageBox);
                final int messageType = c.getInt(mColumnsMap.mColumnMmsMessageType);
                final int simId = c.getInt(mColumnsMap.mColumnSmsSubId);
                final int errorType = c.getInt(mColumnsMap.mColumnMmsErrorType);
                final int locked = c.getInt(mColumnsMap.mColumnMmsLocked);
                final int charset = c.getInt(mColumnsMap.mColumnMmsSubjectCharset);
                final long mMsgId = msgId;
                final String mmsType = type;
                final String subject = c.getString(mColumnsMap.mColumnMmsSubject);
                final String serviceCenter = c.getString(mColumnsMap.mColumnSmsServiceCenter);
                final String deliveryReport = c.getString(mColumnsMap.mColumnMmsDeliveryReport);
                final String readReport = c.getString(mColumnsMap.mColumnMmsReadReport);
                final Pattern highlight = mHighlight;
                final long indDate = c.getLong(mColumnsMap.mColumnMmsDate);
                /// M: fix bug ALPS00406912
                final int mmsStatus;
                if (getNotifIndStatus(msgId) != -1) {
                    mmsStatus = getNotifIndStatus(msgId);
                    MmsLog.d(CACHE_TAG, "getCachedMessageItem(): mmsStatus from Map = " + mmsStatus);
                } else {
                    mmsStatus = c.getInt(mColumnsMap.mColumnMmsStatus);
                }
                MmsLog.d(CACHE_TAG, "getCachedMessageItem(): mmsStatus = " + mmsStatus);
                /// M: add for OP09 Feature, mms cc
                final String mmsCc = c.getString(mColumnsMap.mColumnMmsCc);
                final String mmsCcEncoding = c.getString(mColumnsMap.mColumnMmsCcEncoding);
				MmsLog.d(CACHE_TAG, "getCachedMessageItem(): mmsCc = " + mmsCc);
				MmsLog.d(CACHE_TAG, "getCachedMessageItem(): mmsCcEncoding = " + mmsCcEncoding);

                final Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        MmsLog.d(CACHE_TAG, "getCachedMessageItem(): call UI thread notify data set change.");
                        final Message msg = Message.obtain(mMsgListItemHandler, MSG_LIST_NEED_REFRASH,
                            MESSAGE_LIST_REFRASH, 0);
                        msg.sendToTarget();
                    }
                };
                final Object object = new Object();
                pushTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            MessageItem backgroundItem = mMessageItemCache.get(key);
                            if (backgroundItem == null) {
                                backgroundItem = new MessageItem(mContext, boxId, messageType, simId, errorType,
                                        locked, charset, mMsgId, mmsType, subject, serviceCenter, deliveryReport,
                                        readReport, highlight, isDrawTimeDivider, isDrawUnreadDivider, unreadCount,
                                        indDate, mmsStatus, mmsCc, mmsCcEncoding);
                                MmsLog.d(CACHE_TAG,
                                        "getCachedMessageItem(): put new MessageItem into cache, messageId = -"
                                        + backgroundItem.mMsgId);
                                mMessageItemCache.put(key, backgroundItem);
                                mMsgListItemHandler.postDelayed(r, 200);
                            }
                            synchronized (object) {
                                object.notifyAll();
                            }
                        } catch (MmsException e) {
                            Log.e(TAG, "getCachedMessageItem: ", e);
                            if (messageType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
                                mNeedHideView = true;
                            }
                            synchronized (object) {
                                object.notifyAll();
                            }
                        }
                    }
                });

                synchronized (object) {
                    try {
                        /// M: Fix ALPS00391886, avoid waiting too long time when many uncached messages
                        int waitTime = 600;
                        /// @}
                        object.wait(waitTime);
                    } catch (InterruptedException ex) {
                        MmsLog.e(TAG, "wait has been intrrupted", ex);
                    }
                }
                item = mMessageItemCache.get(key);
                if (item != null) {
                    MmsLog.d(CACHE_TAG, "getCachedMessageItem(): get item during wait.");
                    MmsLog.d(CACHE_TAG, "getCachedMessageItem(): cancel UI thread notify data set change.");
                    mMsgListItemHandler.removeCallbacks(r);
                }
            } else {
                try {
                    item = new MessageItem(mContext, type, c, mColumnsMap, mHighlight, isDrawTimeDivider,
                            isDrawUnreadDivider, unreadCount, 0L, mIsGroupConversation);
                    mMessageItemCache.put(key, item);
                } catch (MmsException e) {
                    Log.e(TAG, "getCachedMessageItem: ", e);
                }
            }
        }
        if (null != item) {
            item.mIsDrawOnlineDivider = !TextUtils.isEmpty(mOnlineDividerString) && isMessageItemShowOnlineDivider(c);
            item.mOnlineString = item.mIsDrawOnlineDivider ? mOnlineDividerString : "";
        }
        return item;
    }

    private boolean isCursorValid(Cursor cursor) {
        // Check whether the cursor is valid or not.
        if (cursor == null || cursor.isClosed() || cursor.isBeforeFirst() || cursor.isAfterLast()) {
            return false;
        }
        return true;
    }

    public static long getKey(String type, long id) {
        if (type.equals("mms")) {
            return -id;
        } else {
            return id;
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public void changeCursor(Cursor cursor) {
        clearNotifIndStatus();
        super.changeCursor(cursor);
    }

    /* MessageListAdapter says that it contains four types of views. Really, it just contains
     * a single type, a MessageListItem. Depending upon whether the message is an incoming or
     * outgoing message, the avatar and text and other items are laid out either left or right
     * justified. That works fine for everything but the message text. When views are recycled,
     * there's a greater than zero chance that the right-justified text on outgoing messages
     * will remain left-justified. The best solution at this point is to tell the adapter we've
     * got two different types of views. That way we won't recycle views between the two types.
     * @see android.widget.BaseAdapter#getViewTypeCount()
     */
    @Override
    public int getViewTypeCount() {
        /// M: add for ipmessage
        return layoutList.length; //3;   // Incoming, outgoing messages and dividers
    }

    @Override
    public int getItemViewType(int position) {
        /// M: add for ipmessage
        Cursor cursor = (Cursor) getItem(position);
        switch (getItemViewType(cursor)) {
        case INCOMING_ITEM_TYPE:
            MmsLog.d(IPMSG_TAG, "getItemViewType(): item type = INCOMING_ITEM_TYPE");
            return INCOMING_ITEM_TYPE;
        case OUTGOING_ITEM_TYPE:
            MmsLog.d(IPMSG_TAG, "getItemViewType(): item type = OUTGOING_ITEM_TYPE");
            return OUTGOING_ITEM_TYPE;
        case INCOMING_ITEM_TYPE_IPMSG:
            MmsLog.d(IPMSG_TAG, "getItemViewType(): item type = INCOMING_ITEM_TYPE_IPMSG");
            return INCOMING_ITEM_TYPE_IPMSG;
        case OUTGOING_ITEM_TYPE_IPMSG:
            MmsLog.d(IPMSG_TAG, "getItemViewType(): item type = OUTGOING_ITEM_TYPE_IPMSG");
            return OUTGOING_ITEM_TYPE_IPMSG;
        default:
            MmsLog.d(IPMSG_TAG, "getItemViewType(): item type = INCOMING_ITEM_TYPE (default)");
            return INCOMING_ITEM_TYPE;
        }
    }

    private int getItemViewType(Cursor cursor) {
        String type = cursor.getString(mColumnsMap.mColumnMsgType);
        MmsLog.d(IPMSG_TAG, "getItemViewType(): message type = " + type);

        int boxId;
        if ("sms".equals(type)) {
            /// M: check sim sms and set box id
            long status = cursor.getLong(mColumnsMap.mColumnSmsStatus);
            boolean isSimMsg = false;
            if (status == SmsManager.STATUS_ON_ICC_SENT
                    || status == SmsManager.STATUS_ON_ICC_UNSENT) {
                isSimMsg = true;
                boxId = Sms.MESSAGE_TYPE_SENT;
            } else if (status == SmsManager.STATUS_ON_ICC_READ
                    || status == SmsManager.STATUS_ON_ICC_UNREAD) {
                isSimMsg = true;
                boxId = Sms.MESSAGE_TYPE_INBOX;
            } else {
                boxId = cursor.getInt(mColumnsMap.mColumnSmsType);
            }
            //boxId = cursor.getInt(mColumnsMap.mColumnSmsType);
            /// M: add for ipmessage
            long ipMessageId = cursor.getLong(mColumnsMap.mColumnSmsIpMessageId);
            MmsLog.d(IPMSG_TAG, "getItemViewType(): ipMessageId = " + ipMessageId + " boxId = " + boxId
				+ " isSimMsg = " + isSimMsg);
            if (ipMessageId > 0 && !isSimMsg) {
                return boxId == Mms.MESSAGE_BOX_INBOX ? INCOMING_ITEM_TYPE_IPMSG : OUTGOING_ITEM_TYPE_IPMSG;
            }
        } else {
            boxId = cursor.getInt(mColumnsMap.mColumnMmsMessageBox);
        }
        return boxId == Mms.MESSAGE_BOX_INBOX ? INCOMING_ITEM_TYPE : OUTGOING_ITEM_TYPE;
    }

    public Cursor getCursorForItem(MessageItem item) {
        Cursor cursor = getCursor();
        if (isCursorValid(cursor)) {
            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(mRowIDColumn);
                    if (id == item.mMsgId) {
                        return cursor;
                    }
                } while (cursor.moveToNext());
            }
        }
        return null;
    }

    public static class ColumnsMap {
        public int mColumnMsgType;
        public int mColumnMsgId;
        public int mColumnSmsAddress;
        public int mColumnSmsBody;
        public int mColumnSmsDate;
        public int mColumnSmsDateSent;
        public int mColumnSmsRead;
        public int mColumnSmsType;
        public int mColumnSmsStatus;
        public int mColumnSmsLocked;
        public int mColumnSmsErrorCode;
        public int mColumnMmsSubject;
        public int mColumnMmsSubjectCharset;
        public int mColumnMmsDate;
        public int mColumnMmsDateSent;
        public int mColumnMmsRead;
        public int mColumnMmsMessageType;
        public int mColumnMmsMessageBox;
        public int mColumnMmsDeliveryReport;
        public int mColumnMmsReadReport;
        public int mColumnMmsErrorType;
        public int mColumnMmsLocked;
        public int mColumnMmsStatus;
        /// M: @{
        public int mColumnSmsSubId;
        public int mColumnMmsSubId;
        public int mColumnSmsServiceCenter;
        public int mColumnMmsServiceCenter;
        /// @}
        /// M: add for ipmessage
        public int mColumnSmsIpMessageId;
        public int mColumnSmsSpam;
        /// M: add for OP09 Feature, mms cc
        public int mColumnMmsCc;
        public int mColumnMmsCcEncoding;

        public ColumnsMap() {
            mColumnMsgType            = COLUMN_MSG_TYPE;
            mColumnMsgId              = COLUMN_ID;
            mColumnSmsAddress         = COLUMN_SMS_ADDRESS;
            mColumnSmsBody            = COLUMN_SMS_BODY;
            mColumnSmsDate            = COLUMN_SMS_DATE;
            mColumnSmsDateSent        = COLUMN_SMS_DATE_SENT;
            /// M: add for ipmessage, unread divider
            mColumnSmsRead            = COLUMN_SMS_READ;

            mColumnSmsType            = COLUMN_SMS_TYPE;
            mColumnSmsStatus          = COLUMN_SMS_STATUS;
            mColumnSmsLocked          = COLUMN_SMS_LOCKED;
            mColumnSmsErrorCode       = COLUMN_SMS_ERROR_CODE;
            mColumnMmsSubject         = COLUMN_MMS_SUBJECT;
            mColumnMmsSubjectCharset  = COLUMN_MMS_SUBJECT_CHARSET;
            /// M: add for ipmessage, unread divider
            mColumnMmsDate            = COLUMN_MMS_DATE;
            mColumnMmsRead            = COLUMN_MMS_READ;

            mColumnMmsMessageType     = COLUMN_MMS_MESSAGE_TYPE;
            mColumnMmsMessageBox      = COLUMN_MMS_MESSAGE_BOX;
            mColumnMmsDeliveryReport  = COLUMN_MMS_DELIVERY_REPORT;
            mColumnMmsReadReport      = COLUMN_MMS_READ_REPORT;
            mColumnMmsErrorType       = COLUMN_MMS_ERROR_TYPE;
            mColumnMmsLocked          = COLUMN_MMS_LOCKED;
            mColumnMmsStatus          = COLUMN_MMS_STATUS;
            /// M: @{
            mColumnSmsSubId           = COLUMN_SMS_SUBID;
            mColumnMmsSubId           = COLUMN_MMS_SUBID;
            mColumnSmsServiceCenter   = COLUMN_SMS_SERVICE_CENTER;
            mColumnMmsServiceCenter   = COLUMN_MMS_SERVICE_CENTER;
            /// @}
            /// M: add for ipmessage
            mColumnSmsIpMessageId     = COLUMN_SMS_IP_MESSAGE_ID;
            mColumnSmsSpam            = COLUMN_SMS_SPAM;
            /// M: add for OP09 Feature, mms cc
            mColumnMmsCc              = COLUMN_MMS_CC;
            mColumnMmsCcEncoding      = COLUMN_MMS_CC_ENCODING;
        }

        public ColumnsMap(Cursor cursor) {
            // Ignore all 'not found' exceptions since the custom columns
            // may be just a subset of the default columns.
            try {
                mColumnMsgType = cursor.getColumnIndexOrThrow(
                        MmsSms.TYPE_DISCRIMINATOR_COLUMN);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMsgId = cursor.getColumnIndexOrThrow(BaseColumns._ID);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsAddress = cursor.getColumnIndexOrThrow(Sms.ADDRESS);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsBody = cursor.getColumnIndexOrThrow(Sms.BODY);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsDate = cursor.getColumnIndexOrThrow(Sms.DATE);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsDateSent = cursor.getColumnIndexOrThrow(Sms.DATE_SENT);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsRead = cursor.getColumnIndexOrThrow(Sms.READ);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsType = cursor.getColumnIndexOrThrow(Sms.TYPE);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsStatus = cursor.getColumnIndexOrThrow(Sms.STATUS);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsLocked = cursor.getColumnIndexOrThrow(Sms.LOCKED);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsErrorCode = cursor.getColumnIndexOrThrow(Sms.ERROR_CODE);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsSubject = cursor.getColumnIndexOrThrow(Mms.SUBJECT);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsSubjectCharset = cursor.getColumnIndexOrThrow(Mms.SUBJECT_CHARSET);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsRead = cursor.getColumnIndexOrThrow(Mms.READ);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsMessageType = cursor.getColumnIndexOrThrow(Mms.MESSAGE_TYPE);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsMessageBox = cursor.getColumnIndexOrThrow(Mms.MESSAGE_BOX);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsDeliveryReport = cursor.getColumnIndexOrThrow(Mms.DELIVERY_REPORT);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsReadReport = cursor.getColumnIndexOrThrow(Mms.READ_REPORT);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsErrorType = cursor.getColumnIndexOrThrow(PendingMessages.ERROR_TYPE);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsLocked = cursor.getColumnIndexOrThrow(Mms.LOCKED);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsStatus = cursor.getColumnIndexOrThrow(Mms.STATUS);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }
            try {
                mColumnSmsSubId = cursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID);
            } catch (IllegalArgumentException e) {
                MmsLog.w(TAG, e.getMessage());
            }

            try {
                mColumnMmsSubId = cursor.getColumnIndexOrThrow(Telephony.Mms.SUBSCRIPTION_ID);
            } catch (IllegalArgumentException e) {
                MmsLog.w(TAG, e.getMessage());
            }

            try {
                mColumnSmsServiceCenter = cursor.getColumnIndexOrThrow(Sms.SERVICE_CENTER);
            } catch (IllegalArgumentException e) {
                MmsLog.w(TAG, e.getMessage());
            }

            try {
                mColumnMmsServiceCenter = cursor.getColumnIndexOrThrow(Telephony.Mms.SERVICE_CENTER);
            } catch (IllegalArgumentException e) {
                MmsLog.w(TAG, e.getMessage());
            }

            /// M: add for ipmessage {@
            try {
                mColumnSmsIpMessageId = cursor.getColumnIndexOrThrow(SMS_IP_MESSAGE_ID);
            } catch (IllegalArgumentException e) {
                MmsLog.w(TAG, e.getMessage());
            }

            try {
                mColumnSmsSpam = cursor.getColumnIndexOrThrow(SMS_SPAM);
            } catch (IllegalArgumentException e) {
                MmsLog.w(TAG, e.getMessage());
            }
            // @}

            /// M: add for OP09 Feature, mms cc.
            try {
                mColumnMmsCc = cursor.getColumnIndexOrThrow(MMS_CC);
            } catch (IllegalArgumentException e) {
                MmsLog.w(TAG, e.getMessage());
            }

            try {
                mColumnMmsCcEncoding = cursor.getColumnIndexOrThrow(MMS_CC_ENCODING);
            } catch (IllegalArgumentException e) {
                MmsLog.w(TAG, e.getMessage());
            }
        }
    }

    private static class MessageItemCache extends LruCache<Long, MessageItem> {
        public MessageItemCache(int maxSize) {
            super(maxSize);
        }

        @Override
        protected void entryRemoved(boolean evicted, Long key,
                MessageItem oldValue, MessageItem newValue) {
            oldValue.cancelPduLoading();
        }
    }

    public void saveNotifIndStatus(long index, int Status) {
        mNotifyIndStatusMap.put(index, Status);
    }

    public int getNotifIndStatus(long index) {
        Integer result = mNotifyIndStatusMap.get(index);
        if (result == null) {
            return -1;
        } else {
            return mNotifyIndStatusMap.get(index);
        }
    }

    public void clearNotifIndStatus() {
        mNotifyIndStatusMap.clear();
    }

    /// M: @{
    /// M: add for multi-delete
    public void changeSelectedState(long listId) {

        MmsLog.e(TAG, "listId =" + listId);
        if (mListItem == null) {
            MmsLog.e(TAG, "mListItem is null");
            return;
        }
        mListItem.put(listId, !mListItem.get(listId));

    }

    public void changeSelectedState(String listId) {
        mSimMsgListItem.put(listId, !mSimMsgListItem.get(listId));

    }

    public  Map<Long, Boolean> getItemList() {
        return mListItem;

    }

    public  Map<String, Boolean> getSimMsgItemList() {
        return mSimMsgListItem;

    }

    public Uri getMessageUri(long messageId) {
        Uri messageUri = null;
        if (messageId > 0) {
            messageUri = ContentUris.withAppendedId(Sms.CONTENT_URI, messageId);
        } else {
            messageUri = ContentUris.withAppendedId(Mms.CONTENT_URI, -messageId);
        }
        return messageUri;
    }

    public void initListMap(Cursor cursor) {
        if (cursor != null) {
            long itemId = 0;
            String type;
            long msgId = 0L;
            long status = 0L;
            boolean isSimMsg = false;
            removeUselessItem(cursor);
            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                status = cursor.getLong(mColumnsMap.mColumnSmsStatus);
                if (status == SmsManager.STATUS_ON_ICC_READ
                        || status == SmsManager.STATUS_ON_ICC_UNREAD
                        || status == SmsManager.STATUS_ON_ICC_SENT
                        || status == SmsManager.STATUS_ON_ICC_UNSENT) {
                    isSimMsg = true;
                }
                /// M: fix bug ALPS00432495, avoid IllegalArgumentException @{
                if (isSimMsg && cursor.getColumnIndex("index_on_icc") != -1) {
                    String msgIndex = cursor.getString(cursor.getColumnIndexOrThrow("index_on_icc"));
                    String[] index = msgIndex.split(";");
//                    for (int n = 0; n < index.length; n++) {
                        if (mSimMsgListItem.get(index[0]) == null) {
                            mSimMsgListItem.put(index[0], false);
                        }
//                    }
                } else {
                    type = cursor.getString(mColumnsMap.mColumnMsgType);
                    msgId = cursor.getLong(mColumnsMap.mColumnMsgId);
                    itemId = getKey(type, msgId);

                    if (mListItem.get(itemId) == null) {
                        mListItem.put(itemId, false);
                    }

                    IMmsMultiDeleteAndForwardExt.BodyandAddressInfo info = new IMmsMultiDeleteAndForwardExt.BodyandAddressInfo(cursor, mColumnsMap.mColumnSmsAddress,
                        mColumnsMap.mColumnSmsBody, mColumnsMap.mColumnSmsType, type, msgId);
                        mDeleteAndForwardPlugin.setBodyandAddress(info);
                }
            }
        }
    }

    /// M: remove items that are not in the database. @{
    public void removeUselessItem(Cursor cursor) {
        if (cursor != null) {
            String type;
            long msgId = 0L;
            long status = 0L;
            boolean isSimMsg = false;
            HashSet<Long> msgIdsOnDisk = new HashSet<Long>();
            HashSet<String> simMsgIdsOnDisk = new HashSet<String>();
            if (cursor.moveToFirst()) {
                do {
                    status = cursor.getLong(mColumnsMap.mColumnSmsStatus);
                    if (status == SmsManager.STATUS_ON_ICC_READ
                            || status == SmsManager.STATUS_ON_ICC_UNREAD
                            || status == SmsManager.STATUS_ON_ICC_SENT
                            || status == SmsManager.STATUS_ON_ICC_UNSENT) {
                        isSimMsg = true;
                    }
                    if (isSimMsg && cursor.getColumnIndex("index_on_icc") != -1) {
                        String msgIndex = cursor.getString(cursor.getColumnIndexOrThrow("index_on_icc"));
                        String[] index = msgIndex.split(";");
                        simMsgIdsOnDisk.add(index[0]);
                    } else {
                        type = cursor.getString(mColumnsMap.mColumnMsgType);
                        msgId = cursor.getLong(mColumnsMap.mColumnMsgId);
                        msgId = getKey(type, msgId);
                        msgIdsOnDisk.add(msgId);
                    }
                } while (cursor.moveToNext());
            }
            Iterator iter = mListItem.keySet().iterator();
            Long itemKey = null;
            while (iter.hasNext()) {
                itemKey = (Long) iter.next();
                if (!msgIdsOnDisk.contains(itemKey)) {
                    iter.remove();
                }
            }
            if (mMsgListItemHandler != null) {
                Message msg = mMsgListItemHandler.obtainMessage(MultiDeleteActivity.UPDATE_SELECTED_COUNT);
                mMsgListItemHandler.sendMessage(msg);
            }
            /// check remain sim messages and remove useless item. @{
            Iterator simMsgIter = mSimMsgListItem.keySet().iterator();
            String simItemKey = null;
            while (simMsgIter.hasNext()) {
                simItemKey = (String) simMsgIter.next();
                if (!simMsgIdsOnDisk.contains(simItemKey)) {
                    simMsgIter.remove();
                }
            }
            /// @}
        }
    }
    /// @}

    public void setItemsValue(boolean value, long[] keyArray) {
        Iterator iter = mListItem.entrySet().iterator();
        /// M: keyArray = null means set the all item
        if (keyArray == null) {
            while (iter.hasNext()) {
                @SuppressWarnings("unchecked")
                Map.Entry<Long, Boolean> entry = (Entry<Long, Boolean>) iter.next();
                entry.setValue(value);
            }
        } else {
            for (int i = 0; i < keyArray.length; i++) {
                mListItem.put(keyArray[i], value);
            }
        }
    }

    public void setSimItemsValue(boolean value, long[] keyArray) {
        Iterator iter = mSimMsgListItem.entrySet().iterator();
        /// M: keyArray = null means set the all item
        if (keyArray == null) {
            while (iter.hasNext()) {
                @SuppressWarnings("unchecked")
                Map.Entry<String, Boolean> entry = (Entry<String, Boolean>) iter.next();
                entry.setValue(value);
            }
        }
    }

    public void clearList() {
        if (mListItem != null) {
            mListItem.clear();
        }
        if (mSimMsgListItem != null) {
            mSimMsgListItem.clear();
        }
        /// M: Operator Plugin
        mDeleteAndForwardPlugin.clearBodyandAddressList();
    }

    public int getSelectedNumber() {
        int number = 0;
        if (mListItem != null) {
            Iterator iter = mListItem.entrySet().iterator();
            while (iter.hasNext()) {
                @SuppressWarnings("unchecked")
                Map.Entry<Long, Boolean> entry = (Entry<Long, Boolean>) iter.next();
                if (entry.getValue()) {
                    number++;
                }
            }
        }
        if (mSimMsgListItem != null) {
            Iterator simMsgIter = mSimMsgListItem.entrySet().iterator();
            while (simMsgIter.hasNext()) {
                @SuppressWarnings("unchecked")
                Map.Entry<String, Boolean> entry = (Entry<String, Boolean>) simMsgIter.next();
                if (entry.getValue()) {
                    number++;
                }
            }
        }
        return number;
    }

    ///M: add for adjust text size
    public void setTextSize(float size) {
        mTextSize = size;
    }

    private static class TaskStack {
        boolean mThreadOver = false;
        Thread mWorkerThread;
        private final ArrayList<Runnable> mThingsToLoad;

        public TaskStack() {
            mThingsToLoad = new ArrayList<Runnable>();
            mWorkerThread = new Thread(new Runnable() {
                public void run() {
                    while (!mThreadOver) {
                        Runnable r = null;
                        synchronized (mThingsToLoad) {
                            if (mThingsToLoad.size() == 0) {
                                try {
                                    mThingsToLoad.wait();
                                } catch (InterruptedException ex) {
                                    MmsLog.w(TAG, ex.getMessage());
                                }
                            }
                            if (mThingsToLoad.size() > 0) {
                                r = mThingsToLoad.remove(0);
                            }
                        }
                        if (r != null) {
                            r.run();
                        }
                    }
                }
            });
            mWorkerThread.start();
        }

        public void push(Runnable r) {
            synchronized (mThingsToLoad) {
                mThingsToLoad.add(r);
                mThingsToLoad.notify();
            }
        }

        public void destroy() {
            synchronized (mThingsToLoad) {
                mThreadOver = true;
                mThingsToLoad.clear();
                mThingsToLoad.notify();
            }
        }
    }

    private final TaskStack mTaskQueue = new TaskStack();
    public void pushTask(Runnable r) {
        mTaskQueue.push(r);
    }

    public void destroyTaskStack() {
        if (mTaskQueue != null) {
            mTaskQueue.destroy();
        }
    }

    public void setClearCacheFlag(boolean clearCacheFlag) {
        mClearCacheFlag = clearCacheFlag;
    }

    /// M: add for ipmessage, message time divider {@
    private boolean isMessageItemShowTimeDivider(Cursor cursor) {
        MmsLog.w(IPMSG_TAG, "isMessageItemShowDivider(): cursor = " + cursor);
        if (null == cursor) {
            MmsLog.w(IPMSG_TAG, "isMessageItemShowDivider(): cursor is null!");
            return false;
        }
        if (cursor.isBeforeFirst() || cursor.isAfterLast()) {
            MmsLog.w(IPMSG_TAG, "isMessageItemShowDivider(): cursor is at before first or after last!");
            return false;
        }
        if (cursor.isFirst() || cursor.getCount() == 1) {
            MmsLog.w(IPMSG_TAG, "isMessageItemShowDivider(): cursor is the first or cursor count is 1! Is first?="
                + cursor.isFirst() + ", count =" + cursor.getCount());
            return true;
        }
        long currentMessageTime = getMessageTimeByCursor(cursor);
        cursor.moveToPrevious();
        long previousMessageTime = getMessageTimeByCursor(cursor);
        cursor.moveToNext();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        MmsLog.w(IPMSG_TAG, "isMessageItemShowDivider(): currentMessageTime = " + sdf.format(new Date(currentMessageTime))
                + ", previousMessageTime = " + sdf.format(new Date(previousMessageTime)));
        return IpMessageUtils.shouldShowTimeDivider(previousMessageTime, currentMessageTime);
    }

    private long getMessageTimeByCursor(Cursor cursor) {
        MmsLog.w(IPMSG_TAG, "getMessageTimeByCursor()");
        String type = cursor.getString(mColumnsMap.mColumnMsgType);
        if (type.equals("mms")) {
            return cursor.getLong(mColumnsMap.mColumnMmsDate) * 1000L;
        } else {
            return cursor.getLong(mColumnsMap.mColumnSmsDate) * 1L;
        }
    }

    private boolean getNextMessageUnreadStatusByCursor(Cursor cursor) {
        MmsLog.w(IPMSG_TAG, "getNextMessageUnreadStatusByCursor()");
        if (null == cursor) {
            MmsLog.w(IPMSG_TAG, "getNextMessageUnreadStatusByCursor(): cursor is null!");
            return false;
        }
        if (cursor.isBeforeFirst() || cursor.isAfterLast()) {
            MmsLog.w(IPMSG_TAG, "getNextMessageUnreadStatusByCursor(): cursor is at before first or after last!");
            return false;
        }
//        if (cursor.isLast() || cursor.getCount() == 1) {
//            MmsLog.w(IPMSG_TAG, "getNextMessageUnreadStatusByCursor(): cursor is the first or cursor count is 1!"
//                + " Is last?=" + cursor.isLast() + ", count =" + cursor.getCount());
//            return true;
//        }
        if (cursor.moveToNext()) {
            String type = cursor.getString(mColumnsMap.mColumnMsgType);
            boolean isUnread = false;
            if (type.equals("mms")) {
                isUnread = cursor.getInt(mColumnsMap.mColumnMmsRead) == 0;
            } else {
                isUnread = cursor.getLong(mColumnsMap.mColumnSmsRead) == 0;
            }
            cursor.moveToPrevious();
            return isUnread;
        }
        return false;
    }
    /// @}

    /// M: add for ipmessage, message unread divider {@
    private boolean isMessageItemShowUnreadDivider(Cursor cursor) {
        MmsLog.w(IPMSG_TAG, "isMessageItemShowUnreadDivider(): cursor = " + cursor);
        MmsLog.w(TAG_DIVIDER, "adapter.isMessageItemShowUnreadDivider(): cursor = " + cursor);
        if (null == cursor) {
            MmsLog.w(IPMSG_TAG, "isMessageItemShowUnreadDivider(): cursor is null!");
            MmsLog.w(TAG_DIVIDER, "adapter.isMessageItemShowUnreadDivider(): cursor is null!");
            return false;
        }
        if (cursor.isBeforeFirst() || cursor.isAfterLast()) {
            MmsLog.w(IPMSG_TAG, "isMessageItemShowUnreadDivider(): cursor is at before first or after last!");
            MmsLog.w(TAG_DIVIDER, "adapter.isMessageItemShowUnreadDivider(): cursor is at before first or after last!");
            return false;
        }
        if (cursor.isFirst() || cursor.getCount() == 1) {
            MmsLog.w(IPMSG_TAG, "isMessageItemShowUnreadDivider(): cursor is the first or cursor count is 1! Is first?="
                    + cursor.isFirst() + ", count =" + cursor.getCount() + ", cursor position = " + cursor.getPosition());
            MmsLog.w(TAG_DIVIDER, "adapter.isMessageItemShowUnreadDivider(): cursor is the first or cursor count is 1! Is first?="
                    + cursor.isFirst() + ", count =" + cursor.getCount() + ", cursor position = " + cursor.getPosition());
            return isMessageUnreadByCursor(cursor);
        }
        boolean currentMessageUnreadStatus = isMessageUnreadByCursor(cursor);
        cursor.moveToPrevious();
        boolean previousMessageUnreadStatus = isMessageUnreadByCursor(cursor);
        cursor.moveToNext();
        MmsLog.w(IPMSG_TAG, "isMessageItemShowUnreadDivider(): currentMessageUnreadStatus = " + currentMessageUnreadStatus
                + ", previousMessageUnreadStatus = " + previousMessageUnreadStatus);
        MmsLog.w(TAG_DIVIDER, "adapter.isMessageItemShowUnreadDivider(): currentMessageUnreadStatus = " + currentMessageUnreadStatus
                + ", previousMessageUnreadStatus = " + previousMessageUnreadStatus);
        return currentMessageUnreadStatus && !previousMessageUnreadStatus;
    }

    private boolean isMessageUnreadByCursor(Cursor cursor) {
        MmsLog.w(IPMSG_TAG, "isMessageUnreadByCursor(): msgId = " + cursor.getInt(mColumnsMap.mColumnMsgId));
        MmsLog.w(TAG_DIVIDER, "adapter.isMessageUnreadByCursor(): msgId = " + cursor.getInt(mColumnsMap.mColumnMsgId));
        String type = cursor.getString(mColumnsMap.mColumnMsgType);
        if (type.equals("mms")) {
            MmsLog.w(IPMSG_TAG, "isMessageUnreadByCursor(): read = " + cursor.getInt(mColumnsMap.mColumnMmsRead));
            MmsLog.w(TAG_DIVIDER, "adapter.isMessageUnreadByCursor(): read = " + cursor.getInt(mColumnsMap.mColumnMmsRead));
            return cursor.getInt(mColumnsMap.mColumnMmsRead) == 0;
        } else {
            MmsLog.w(IPMSG_TAG, "isMessageUnreadByCursor(): read = " + cursor.getInt(mColumnsMap.mColumnSmsRead));
            MmsLog.w(TAG_DIVIDER, "adapter.isMessageUnreadByCursor(): read = " + cursor.getInt(mColumnsMap.mColumnSmsRead));
            return cursor.getInt(mColumnsMap.mColumnSmsRead) == 0;
        }
    }

    private boolean isMessageItemShowOnlineDivider(Cursor cursor) {
        MmsLog.w(IPMSG_TAG, "isMessageItemShowOnlineDivider(): cursor = " + cursor);
        MmsLog.w(TAG_DIVIDER, "adapter.isMessageItemShowOnlineDivider(): cursor = " + cursor);
        if (null == cursor) {
            MmsLog.w(IPMSG_TAG, "isMessageItemShowOnlineDivider(): cursor is null!");
            MmsLog.w(TAG_DIVIDER, "adapter.isMessageItemShowOnlineDivider(): cursor is null!");
            return false;
        }
        if (cursor.isBeforeFirst() || cursor.isAfterLast()) {
            MmsLog.w(IPMSG_TAG, "isMessageItemShowOnlineDivider(): cursor is at before first or after last!");
            MmsLog.w(TAG_DIVIDER, "adapter.isMessageItemShowOnlineDivider(): cursor is at before first or after last!");
            return false;
        }
        if (cursor.getCount() == 1) {
            return true;
        }
        long currentTime = getMessageTimeByCursor(cursor);
        if (cursor.isLast()) {
            return currentTime < mOnlineDividerTime;
        }
        cursor.moveToNext();
        long nextMessageTime = getMessageTimeByCursor(cursor);
        cursor.moveToPrevious();
        return (currentTime < mOnlineDividerTime) && (nextMessageTime > mOnlineDividerTime);
    }

    private boolean getMessageLockedStateByCursor(Cursor cursor) {
        String type = cursor.getString(mColumnsMap.mColumnMsgType);
        if (type.equals("mms")) {
            return cursor.getInt(mColumnsMap.mColumnMmsLocked) != 0;
        } else {
            return cursor.getInt(mColumnsMap.mColumnSmsLocked) != 0;
        }
    }

    private int mUnreadCount = 0;
    private long mOnlineDividerTime = System.currentTimeMillis();
    private String mOnlineDividerString = "";

    public int getUnreadCount() {
        return this.mUnreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.mUnreadCount = unreadCount;
    }

    public void updateOnlineDividerTime() {
        mOnlineDividerTime = System.currentTimeMillis();
    }

    public void setOnlineDividerString(String onlineDividerString) {
        this.mOnlineDividerString = onlineDividerString;
    }
    /// @}

    /// M: For ComposeMessageActivity to check listener@{
    public OnDataSetChangedListener getOnDataSetChangedListener() {
        return mOnDataSetChangedListener;
    }
    /// @}

    ///M: add for CMCC, when send msg, get the last msg simId
    public int getSubIdFromCursor(Cursor c) {
        int simId = -1;
        try {
            String type = c.getString(mColumnsMap.mColumnMsgType);
            if (type.equals("mms")) {
                simId = c.getInt(mColumnsMap.mColumnMmsSubId);
            } else if (type.equals("sms")) {
                simId = c.getInt(mColumnsMap.mColumnSmsSubId);
            }
        } catch (Exception e) {
            MmsLog.e(TAG, "getSimId error happens, please check!");
        } finally {
            MmsLog.d(TAG, "getSimId id = " + simId);
            return simId;
        }
    }
}
