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
import kr.or.kashi.hde.HomePacket;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.BinarySensors;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.util.Map;

/**
 * [KS X 4506] The security-expansion implementation for BinarySensors
 */
public class KSSecurityExpansion extends KSDeviceContextBase {
    private static final String TAG = "KSSecurityExpansion";
    private static final boolean DBG = true;

    public static final int CMD_SENSOR_SETTING_REQ = 0x43;
    public static final int CMD_SENSOR_SETTING_RSP = 0xC3;

    public static final int TYPE_NO  = 0; // 00
    public static final int TYPE_NC  = 1; // 01
    public static final int TYPE_ETC = 2; // 11

    public static final int STATE_NORMAL = 0; // 00
    public static final int STATE_OPENED = 1; // 01
    public static final int STATE_CLOSED = 2; // 10


    public KSSecurityExpansion(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps, BinarySensors.class);

        // Register the task to be performed when specific property changes.
        setPropertyTask(HomeDevice.PROP_ONOFF, this::onSensorEnableTask);
        setPropertyTask(BinarySensors.PROP_ENABLED_SENSORS, this::onSensorEnableTask);
    }

    @Override
    public @ParseResult int parsePayload(HomePacket packet, PropertyMap outProps) {
        KSPacket ksPacket = (KSPacket) packet;

        switch (ksPacket.commandType) {
            case CMD_SENSOR_SETTING_RSP: {
                // All the responses about control requests is same with the response of status.
                int res = parseStatusRsp(ksPacket, outProps);
                if (res < PARSE_OK_NONE && res == PARSE_OK_ERROR_RECEIVED) {
                    return res;
                }
                return PARSE_OK_ACTION_PERFORMED;
            }
        }

        return super.parsePayload(packet, outProps);
    }

    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 5) {
            if (DBG) Log.w(TAG, "parse-status-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-status-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();
        if (!thisSubId.isSingle() && !thisSubId.isSingleOfGroup()) {
            // Device object that represents as parent of single devices doesn't
            // need to parse any state data since it's depending on child object
            return PARSE_OK_NONE;
        }

        final KSAddress.DeviceSubId pktSubId = KSAddress.toDeviceSubId(packet.deviceSubId);
        if (!pktSubId.isSingle() && !pktSubId.isSingleOfGroup()) {
            // It's allowed to parse only the packet form of single device.
            return PARSE_OK_NONE;
        }

        @ParseResult int result = PARSE_OK_NONE;

        long curEnableds = (long) outProps.get(BinarySensors.PROP_ENABLED_SENSORS).getValue();
        long newEnableds = 0;

        if ((packet.data[1] & (1 << 0)) != 0) newEnableds |= BinarySensors.Sensor.SENSOR_1;
        if ((packet.data[1] & (1 << 1)) != 0) newEnableds |= BinarySensors.Sensor.SENSOR_2;
        if ((packet.data[1] & (1 << 2)) != 0) newEnableds |= BinarySensors.Sensor.SENSOR_3;
        if ((packet.data[1] & (1 << 3)) != 0) newEnableds |= BinarySensors.Sensor.SENSOR_4;
        if ((packet.data[1] & (1 << 4)) != 0) newEnableds |= BinarySensors.Sensor.SENSOR_5;
        if ((packet.data[1] & (1 << 5)) != 0) newEnableds |= BinarySensors.Sensor.SENSOR_6;
        if ((packet.data[1] & (1 << 6)) != 0) newEnableds |= BinarySensors.Sensor.SENSOR_7;
        if ((packet.data[1] & (1 << 7)) != 0) newEnableds |= BinarySensors.Sensor.SENSOR_8;

        if (newEnableds != curEnableds) {
            outProps.put(BinarySensors.PROP_ENABLED_SENSORS, newEnableds);
            outProps.put(HomeDevice.PROP_ONOFF, newEnableds == 0 ? false : true);
            result = PARSE_OK_STATE_UPDATED;
        }

        long curStates = (long) outProps.get(BinarySensors.PROP_SENSOR_STATES).getValue();
        long newStates = 0;

        final int type1  = (packet.data[3] >> 0) & 0x3;
        final int type2  = (packet.data[3] >> 2) & 0x3;
        final int type3  = (packet.data[3] >> 4) & 0x3;
        final int type4  = (packet.data[3] >> 6) & 0x3;
        final int type5  = (packet.data[2] >> 0) & 0x3;
        final int type6  = (packet.data[2] >> 2) & 0x3;
        final int type7  = (packet.data[2] >> 4) & 0x3;
        final int type8  = (packet.data[2] >> 6) & 0x3;

        final int state1 = (packet.data[5] >> 0) & 0x3;
        final int state2 = (packet.data[5] >> 2) & 0x3;
        final int state3 = (packet.data[5] >> 4) & 0x3;
        final int state4 = (packet.data[5] >> 6) & 0x3;
        final int state5 = (packet.data[4] >> 0) & 0x3;
        final int state6 = (packet.data[4] >> 2) & 0x3;
        final int state7 = (packet.data[4] >> 4) & 0x3;
        final int state8 = (packet.data[4] >> 6) & 0x3;

        if (isDetecting(type1, state1)) newStates |= BinarySensors.Sensor.SENSOR_1;
        if (isDetecting(type2, state2)) newStates |= BinarySensors.Sensor.SENSOR_2;
        if (isDetecting(type3, state3)) newStates |= BinarySensors.Sensor.SENSOR_3;
        if (isDetecting(type4, state4)) newStates |= BinarySensors.Sensor.SENSOR_4;
        if (isDetecting(type5, state5)) newStates |= BinarySensors.Sensor.SENSOR_5;
        if (isDetecting(type6, state6)) newStates |= BinarySensors.Sensor.SENSOR_6;
        if (isDetecting(type7, state7)) newStates |= BinarySensors.Sensor.SENSOR_7;
        if (isDetecting(type8, state8)) newStates |= BinarySensors.Sensor.SENSOR_8;

        if (newStates != curStates) {
            outProps.put(BinarySensors.PROP_SENSOR_STATES, newStates);
            result = PARSE_OK_STATE_UPDATED;
        }

        return result;
    }

    private boolean isDetecting(int type, int state) {
        return (type == TYPE_NO && state == STATE_OPENED) || (type == TYPE_NC && state == STATE_CLOSED);
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

        final boolean type1Settable = (packet.data[1] & (1 >> 0)) != 0;
        final boolean type2Settable = (packet.data[1] & (1 >> 1)) != 0;
        final boolean type3Settable = (packet.data[1] & (1 >> 2)) != 0;
        final boolean type4Settable = (packet.data[1] & (1 >> 3)) != 0;
        final boolean type5Settable = (packet.data[1] & (1 >> 4)) != 0;
        final boolean type6Settable = (packet.data[1] & (1 >> 5)) != 0;
        final boolean type7Settable = (packet.data[1] & (1 >> 6)) != 0;
        final boolean type8Settable = (packet.data[1] & (1 >> 7)) != 0;

        // No operation with the characteristics that shows specific type is settable or not

        return PARSE_OK_PEER_DETECTED;
    }

    protected boolean onSensorEnableTask(PropertyMap reqProps, PropertyMap outProps) {
        long curStates = (Long) getProperty(BinarySensors.PROP_ENABLED_SENSORS).getValue();
        long reqStates = (Long) reqProps.get(BinarySensors.PROP_ENABLED_SENSORS).getValue();
        long difStates = curStates ^ reqStates;

        boolean consumed = false;

        if (difStates != 0) {
            byte settingByte = 0;
            if ((reqStates & BinarySensors.Sensor.SENSOR_1) != 0) settingByte |= (1 << 0);
            if ((reqStates & BinarySensors.Sensor.SENSOR_2) != 0) settingByte |= (1 << 1);
            if ((reqStates & BinarySensors.Sensor.SENSOR_3) != 0) settingByte |= (1 << 2);
            if ((reqStates & BinarySensors.Sensor.SENSOR_4) != 0) settingByte |= (1 << 3);
            if ((reqStates & BinarySensors.Sensor.SENSOR_5) != 0) settingByte |= (1 << 4);
            if ((reqStates & BinarySensors.Sensor.SENSOR_6) != 0) settingByte |= (1 << 5);
            if ((reqStates & BinarySensors.Sensor.SENSOR_7) != 0) settingByte |= (1 << 6);
            if ((reqStates & BinarySensors.Sensor.SENSOR_8) != 0) settingByte |= (1 << 7);
            sendPacket(createPacket(CMD_SENSOR_SETTING_REQ, new byte[] { settingByte }));
            consumed |= true;
        } else {
            boolean curOnOff = (Boolean) getProperty(HomeDevice.PROP_ONOFF).getValue();
            boolean reqOnOff = (Boolean) reqProps.get(HomeDevice.PROP_ONOFF).getValue();
            if (reqOnOff != curOnOff) {
                byte settingByte = (byte) ((reqOnOff == true) ? 0xFF : 0x00);
                sendPacket(createPacket(CMD_SENSOR_SETTING_REQ, new byte[] { settingByte }));
                consumed |= true;
            }
        }

        return consumed;
    }
}
