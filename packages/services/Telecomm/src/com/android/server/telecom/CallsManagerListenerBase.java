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
 * limitations under the License.
 */

package com.android.server.telecom;

import java.util.List;

import android.telecom.AudioState;

/**
 * Provides a default implementation for listeners of CallsManager.
 */
class CallsManagerListenerBase implements CallsManager.CallsManagerListener {
    @Override
    public void onCallAdded(Call call) {
    }

    @Override
    public void onCallRemoved(Call call) {
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
    }

    @Override
    public void onConnectionServiceChanged(
            Call call,
            ConnectionServiceWrapper oldService,
            ConnectionServiceWrapper newService) {
    }

    @Override
    public void onIncomingCallAnswered(Call call) {
    }

    @Override
    public void onIncomingCallRejected(Call call, boolean rejectWithMessage, String textMessage) {
    }

    @Override
    public void onForegroundCallChanged(Call oldForegroundCall, Call newForegroundCall) {
    }

    @Override
    public void onAudioStateChanged(AudioState oldAudioState, AudioState newAudioState) {
    }

    @Override
    public void onRingbackRequested(Call call, boolean ringback) {
    }

    @Override
    public void onIsConferencedChanged(Call call) {
    }

    @Override
    public void onIsVoipAudioModeChanged(Call call) {
    }

    @Override
    public void onVideoStateChanged(Call call) {
    }

    @Override
    public void onCanAddCallChanged(boolean canAddCall) {
    }
    /* M: CC part start */
    @Override
    public void onConnectionLost(Call call) {
    }

    @Override
    public void onCdmaCallAccepted(Call call) {
    }
    /* M: CC part end */

    @Override
    public void onBackgroundCallListChanged(List<Call> newList) {
    }

    @Override
    public void onInComingCallListChanged(List<Call> newList) {
    }
}
