/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.geocoding;

import android.database.Cursor;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.sql.SQLException;

import java.io.File;
import android.telephony.Rlog;

import android.location.CountryDetector;
import android.os.Build;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
//add by zhangjinqiang for GeoDescription-start
//import com.cootek.smartdialer_plugin_oem.CooTekSmartdialerOemModule;
//import com.cootek.smartdialer_plugin_oem.IServiceStateCallback;
import android.util.Log;


//add by zhangjinqiang end


/**
 * Singleton design pattern
 */
public class GeoCodingQuery /*implements IServiceStateCallback*/{
    private static final String LOG_TAG = "GeoCodingQuery";
    private static final String DBFilePath = "/system/etc/geocoding.db";
    private DBHelper mDbHelper = null;
    private SQLiteDatabase mDatabase;
    private boolean mIsDBReady = false;
    private Context mContext = null;
    private static Object mInstanceObjectSync = new Object();
    private static GeoCodingQuery myInstance = null;

    private static final String NumberHeadWithIDToByteFilePath = "/system/etc/NumberHeadWithIDToByte";
    private RandomAccessFile mRandomAccessFile = null;
    private IntBuffer mIntBuffer = null;

   //private  CooTekSmartdialerOemModule csom;
    private GeoCodingQuery(Context context) {
        mContext = context;
        openDatabase(context);
        openNumberHeadWithIDToByteFile();
		//add by zhangjinqiang for GeoDescription-start
		/*
    		if(csom==null){
			csom = new CooTekSmartdialerOemModule(mContext,this);
		}
		*/
		//add by zhangjinqiang end
    }

    /**
     * Return the GeoCodingQuery object.
     *
     * @param context the Context.
     * @return Return the GeoCodingQuery object.
     * @hide
     * @internal
     */
    public static GeoCodingQuery getInstance(Context context) {
        synchronized (mInstanceObjectSync) {
            if (myInstance == null) {
                myInstance = new GeoCodingQuery(context);
                Rlog.d(LOG_TAG, "getInstance(): myInstance is created");
            }
        }
        return myInstance;
    }

    private boolean canQuery() {
        return mIsDBReady;
    }

	//add by zhangjinqiang for GeoDescription-start
	/*
		@Override
	public void onServiceConnected() {
		//Toast.makeText(PeopleActivity.this, "号码助手Service通信成功！", Toast.LENGTH_SHORT).show();  
	}
	
	@Override
	public void onServiceDisconnected() {
		//Toast.makeText(PeopleActivity.this, "号码助手Service通信连接失败！！", Toast.LENGTH_LONG).show(); 
	}
	*/
	//add by zhangjinqiang end

    /**
     * Return the geo description.
     *
     * @param number the phone number.
     * @return Return the geo description.
     * @hide
     * @internal
     */
    public String queryByNumber(String number) {
        String returnValue = "";
        int numberValidLength = 11;
        int numberTailLength = 4;
        String countryIso = "";
		//add by zhangjinqiang for GeoDescription--start
		/*
                if(true){
	     	returnValue = csom.getPhoneAttribute(number);
			return returnValue;
		}
               */
		//add by zhangjinqiang end

        if (mDbHelper == null) {
            Rlog.d(LOG_TAG, "Database is not opened !");
            return returnValue;
        }

        if (Build.TYPE.equals("eng"))/*HQ_guomiao add for HQ01444267*/
        Rlog.d(LOG_TAG, "number = " + number);
        /* Ignore space character and get the previous 7 number */
        String queryNumber = number.replaceAll(" ", "");
        int numberTotalLength = queryNumber.length();
        if (numberTotalLength < numberValidLength) {
            Rlog.d(LOG_TAG, "The length of dial number is less than 11 !");
            return returnValue;
        }

        if (queryNumber.startsWith("+") || queryNumber.startsWith("00")) {
            // international number
            if (!queryNumber.startsWith("+86") && !queryNumber.startsWith("0086")) {
                Rlog.d(LOG_TAG, "The dial number is a international number and didn't start with +86!");
                return returnValue;
            }
        } else {
            // non-international number
            CountryDetector detector = (CountryDetector) mContext.getSystemService(Context.COUNTRY_DETECTOR);
            if ((detector != null) && (detector.detectCountry() != null))
                countryIso = detector.detectCountry().getCountryIso();
            else
                countryIso = mContext.getResources().getConfiguration().locale.getCountry();
            if (!countryIso.equalsIgnoreCase("cn")) {
                Rlog.d(LOG_TAG, "The dial number is not at CN!");
                return returnValue;
            }
        }

        int startIndex = numberTotalLength - numberValidLength;
        int endIndex = numberTotalLength - numberTailLength;
        queryNumber = queryNumber.substring(startIndex, endIndex);
        Rlog.d(LOG_TAG, "Query number = " + queryNumber);
        int queryNumberLength = 7;

        for (int i = 0; i < queryNumberLength; i++)
        {
            if ((queryNumber.charAt(i) < '0') || (queryNumber.charAt(i) > '9')) {
               return returnValue;
            }
        }

        int index = binarySearchNumberHead(Integer.parseInt(queryNumber));
        if (-1 != index) {
            String sqlCmd = "Select city_name from city where _id = " + (mIntBuffer.get(index) % 1000);
            Cursor cursor = mDatabase.rawQuery(sqlCmd, null);

            if ((cursor != null) && (cursor.getCount() > 0)) {
                cursor.moveToFirst();
                returnValue = cursor.getString(0);
            }

            if (cursor != null)
                cursor.close();
        }
        else
            Rlog.d(LOG_TAG, "The query number is not found in 'NumberHeadWithIDToByte' database!");

        return returnValue;
    }

    private void openDatabase(Context context) {
        try {
            Rlog.d(LOG_TAG, "Open GeoCoding database.");
            if (new File(DBFilePath).exists()) {
                mDbHelper = new DBHelper(context);
                mDatabase = mDbHelper.openDatabase();
                mIsDBReady = true;
            }
            else {
                closeDatabase();
            }
        } catch (SQLException e) {
            Rlog.d(LOG_TAG, "Failed to open GeoCoding database!");
            closeDatabase();
        }
    }

    private void closeDatabase() {
        try {
            if (mDbHelper != null) {
                mDbHelper.close();
            }
        } catch (IllegalStateException e) {
            Rlog.d(LOG_TAG, "Failed to close GeoCoding database!");
            e.printStackTrace();
        }
        mDbHelper = null;
        mIsDBReady = false;
    }

    private void openNumberHeadWithIDToByteFile() {
        try {
            mRandomAccessFile = new RandomAccessFile(NumberHeadWithIDToByteFilePath, "r");
            long nCount = mRandomAccessFile.length();
            MappedByteBuffer mappedByteBuffer = mRandomAccessFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, nCount);
            mIntBuffer = mappedByteBuffer.asIntBuffer();
        } catch (IOException e) {
            Rlog.d(LOG_TAG, "Failed to open NumberHead file!");
            e.printStackTrace();
            try {
                if (mRandomAccessFile != null)
                    mRandomAccessFile.close();
            } catch (IOException e1) {
                Rlog.d(LOG_TAG, "Failed to close NumberHead file!");
                e1.printStackTrace();
            }
        }
    }

    private int binarySearchNumberHead(int target) {
        int low = 0;
        int upper = mIntBuffer.limit() - 1;

        while (low <= upper) {
            int mid = (low + upper) / 2;
            int nNumberHead = mIntBuffer.get(mid) / 1000;
            if (nNumberHead < target)
                low = mid + 1;
            else if (nNumberHead > target)
                upper = mid - 1;
            else
                return mid;
        }

        return -1;
    }

    public class DBHelper extends SQLiteOpenHelper {
       private static final String DATABASE_NAME = DBFilePath;
       private static final int DATABASE_VERSION = 4;
       private SQLiteDatabase mDatabase;

       public DBHelper(Context context) {
         super(context, DATABASE_NAME, null, DATABASE_VERSION);
       }

       @Override
       public void onCreate(SQLiteDatabase db) {
       }

       @Override
       public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
       }

       public SQLiteDatabase openDatabase() throws SQLException {
          mDatabase = SQLiteDatabase.openDatabase(DATABASE_NAME, null, SQLiteDatabase.OPEN_READONLY);
          return mDatabase;
       }
    } /* End of DBHelper class */
} /* End of GeoCodingQuery class */
