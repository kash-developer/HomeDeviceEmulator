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

import kr.or.kashi.hde.base.ByteArrayBuffer;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.base.PropertyTask;
import kr.or.kashi.hde.device.Curtain;
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
        final PropertyTask controlTask = this::onDoorLockControlTask;
        setPropertyTask(DoorLock.PROP_CURRENT_STATES, controlTask);
        setPropertyTask(HomeDevice.PROP_ONOFF, controlTask);
    }

    protected int getCapabilities() {
        return CAP_STATUS_SINGLE | CAP_CHARAC_SINGLE;
    }

    @Override
    protected @ParseResult int parseStatusReq(KSPacket packet, PropertyMap outProps) {
        // No data to parse from request packet.

        // Send response packet.
        final PropertyMap props = getReadPropertyMap();
        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // no error
        makeDoorLockStateByte(props, data);
        sendPacket(createPacket(CMD_STATUS_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
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

    @Override
    protected @ParseResult int parseCharacteristicReq(KSPacket packet, PropertyMap outProps) {
        // No data to parse from request packet.

        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // no error
        data.append(mForceReleaseSupported ? 1 : 0);    // TODO:

        // Send response packet.
        sendPacket(createPacket(CMD_CHARACTERISTIC_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
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

    @Override
    protected @ParseResult int parseSingleControlReq(KSPacket packet, PropertyMap outProps) {
        final int control = packet.data[0] & 0xFF;
        outProps.putBit(DoorLock.PROP_CURRENT_STATES, DoorLock.State.DOOR_OPENED, (control == 1));
        outProps.put(HomeDevice.PROP_ONOFF, (control == 1));

        // Send response packet.
        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // no error
        makeDoorLockStateByte(outProps, data);
        sendPacket(createPacket(CMD_SINGLE_CONTROL_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
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

    private void makeDoorLockStateByte(PropertyMap props, ByteArrayBuffer outData) {
        final long curStates = props.get(DoorLock.PROP_CURRENT_STATES, Long.class);

        int stateData = 0;
        if ((curStates & DoorLock.State.DOOR_OPENED) != 0) stateData |= (1 << 0);
        if ((curStates & DoorLock.State.EMERGENCY_ALARMED) != 0) stateData |= (1 << 1);

        outData.append(stateData);
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

    protected boolean onDoorLockControlTask(PropertyMap reqProps, PropertyMap outProps) {
        boolean curOnOff = (Boolean) getProperty(HomeDevice.PROP_ONOFF).getValue();
        boolean newOnOff = (Boolean) reqProps.get(HomeDevice.PROP_ONOFF).getValue();
        long curStates = (Long) getProperty(DoorLock.PROP_CURRENT_STATES).getValue();
        long newStates = reqProps.get(DoorLock.PROP_CURRENT_STATES, Long.class);
        boolean curDoorOpened = (curStates & DoorLock.State.DOOR_OPENED) != 0L;
        boolean newDoorOpened = (newStates & DoorLock.State.DOOR_OPENED) != 0L;
        if (newDoorOpened == curDoorOpened && newOnOff != curOnOff) {
            if (newOnOff) newStates &= ~DoorLock.State.DOOR_OPENED;
            else newStates |= DoorLock.State.DOOR_OPENED;
        }

        if (isMaster()) {
            byte data = 0;
            if ((newStates & DoorLock.State.DOOR_OPENED) != 0L) data |= (byte)(1 << 0);
            sendPacket(createPacket(CMD_SINGLE_CONTROL_REQ, data));
        } else {
            outProps.put(DoorLock.PROP_CURRENT_STATES, newStates);
            outProps.put(HomeDevice.PROP_ONOFF, (newStates & DoorLock.State.DOOR_OPENED) == 0L);
        }

        return true;
    }
}
