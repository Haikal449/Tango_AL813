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
 * MediaTek Inc. (C) 2014. All rights reserved.
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
package com.android.camera;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

public class CameraActivityBridgeFactory {
    private static final String TAG =  CameraActivityBridgeFactory.class.getSimpleName();
    private static Map<CameraActivity, Object> mCameraActivityBridgeMap = new HashMap<CameraActivity, Object>();

    public static synchronized ICameraActivityBridge getCameraActivityBridge(CameraActivity activity) {
        Log.i(TAG, "[getCameraActivityBridge]+ activity = " + activity);
        if (mCameraActivityBridgeMap.get(activity) != null) {
            return (ICameraActivityBridge)mCameraActivityBridgeMap.get(activity);
        }
        try {
            Class<?> cameraActivityBridgeClass = Class.forName("com.android.camera.v2.CameraActivityBridge");
            Class<?>[] parameterTypes = {CameraActivity.class};
            
            Constructor<?> constructor = cameraActivityBridgeClass.getConstructor(parameterTypes);
            
            Object[] parameters = {activity};
            Object cameraActivityBridgeObj = constructor.newInstance(parameters);
            mCameraActivityBridgeMap.put(activity, cameraActivityBridgeObj);
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "[getCameraActivityBridge]- return null");
            return null;
        }
        ICameraActivityBridge cameraActivityBridge = (ICameraActivityBridge)mCameraActivityBridgeMap.get(activity);
        Log.i(TAG, "[getCameraActivityBridge]- return " + cameraActivityBridge);
        return cameraActivityBridge;
    }

    public static synchronized void destroyCameraActivityBridge(CameraActivity activity) {
        Log.i(TAG, "destroyCameraActivityBridge map size: " + mCameraActivityBridgeMap.size());
        if (activity != null) {
            mCameraActivityBridgeMap.remove(activity);
        }
    }
}
