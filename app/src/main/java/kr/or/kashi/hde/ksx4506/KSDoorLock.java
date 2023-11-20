/*
 * Copyright (C) 2023 Korea Association of AI Smart Home.
 * Copyright (C) 2023 KyungDong Navien Co, Ltd.
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

package kr.or.kashi.hde.ksx4506;

import android.util.Log;

import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.DoorLock;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.util.Map;

/**
 * [KS X 4506] The door lock implementation for DoorLock
 */
public class KSDoorLock extends KSDeviceContextBase {
    private static final String TAG = "KSDoorLock";
    private static final boolean DBG = true;

    private static boolean mForceReleaseSupported = false;

    public KSDoorLock(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps, DoorLock.class);

        // Register the tasks to be performed when specific property changes.
        setPropertyTask(DoorLock.PROP_CURRENT_STATES, mSingleControlTask);
        setPropertyTask(HomeDevice.PROP_ONOFF, mSingleControlTask);
    }

    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 2) {
            if (DBG) Log.w(TAG, "parse-status-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-status-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        parseDoorLockStateByte(packet.data[1], outProps);

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 2) {
            if (DBG) Log.w(TAG, "parse-chr-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-chr-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        mForceReleaseSupported = ((packet.data[1] & (1 << 0)) != 0); // Not used

        // Set just as all supported
        long supportedStates = 0;
        supportedStates |= (DoorLock.State.DOOR_OPENED);
        supportedStates |= (DoorLock.State.EMERGENCY_ALARMED);
        outProps.put(DoorLock.PROP_SUPPORTED_STATES, supportedStates);

        return PARSE_OK_PEER_DETECTED;
    }

    protected @ParseResult int parseSingleControlRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 2) {
            if (DBG) Log.w(TAG, "parse-status-ctrl-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-single-ctrl-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        parseDoorLockStateByte(packet.data[1], outProps);

        return PARSE_OK_ACTION_PERFORMED;
    }

    private void parseDoorLockStateByte(byte stateByte, PropertyMap outProps) {
        long curStates = (long) outProps.get(DoorLock.PROP_CURRENT_STATES).getValue();
        long newStates = 0;
        if ((stateByte & (1 << 0)) != 0) newStates |= DoorLock.State.DOOR_OPENED;
        if ((stateByte & (1 << 1)) != 0) newStates |= DoorLock.State.EMERGENCY_ALARMED;
        outProps.put(DoorLock.PROP_CURRENT_STATES, newStates);

        final boolean isOn = ((newStates & DoorLock.State.DOOR_OPENED) != 0);
        outProps.put(HomeDevice.PROP_ONOFF, isOn);
    }

    @Override
    protected KSPacket makeControlReq(PropertyMap props) {
        long curStates = (Long) getProperty(DoorLock.PROP_CURRENT_STATES).getValue();
        long reqStates = props.get(DoorLock.PROP_CURRENT_STATES, Long.class);
        long difStates = curStates ^ reqStates;

        if ((difStates & DoorLock.State.DOOR_OPENED) == 0) {
            // Check also changes of on/off state, and overide switch state if so.
            final boolean curOnOff = (Boolean) getProperty(HomeDevice.PROP_ONOFF).getValue();
            final boolean reqOnOff = (Boolean) props.get(HomeDevice.PROP_ONOFF).getValue();
            if (reqOnOff != curOnOff) {
                if (reqOnOff == false) {
                    reqStates |= DoorLock.State.DOOR_OPENED;
                } else {
                    reqStates &= ~DoorLock.State.DOOR_OPENED;
                }
            }
        }

        byte controlData = 0;
        if ((reqStates & DoorLock.State.DOOR_OPENED) != 0L) {
            controlData |= (byte)(1 << 0); // 0:open 1:close
        }

        return createPacket(CMD_SINGLE_CONTROL_REQ, new byte[] { controlData });
    }
}
