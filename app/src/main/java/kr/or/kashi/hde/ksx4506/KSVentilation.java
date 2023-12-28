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
import kr.or.kashi.hde.device.Ventilation;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.util.Map;

/**
 * [KS X 4506] The ventilation implementation for Ventilation
 */
public class KSVentilation extends KSDeviceContextBase {
    private static final String TAG = "KSVentilation";
    private static final boolean DBG = true;

    public static final int CMD_POWER_CONTROL_REQ = CMD_SINGLE_CONTROL_REQ;
    public static final int CMD_POWER_CONTROL_RSP = CMD_SINGLE_CONTROL_RSP;
    public static final int CMD_FAN_SPEED_CONTROL_REQ = 0x42;
    public static final int CMD_FAN_SPEED_CONTROL_RSP = 0xC2;
    public static final int CMD_MODE_CONTROL_REQ = 0x43;
    public static final int CMD_MODE_CONTROL_RSP = 0xC3;

    private int mMinFanSpeedLevel = 0;
    private int mMaxFanSpeedLevel = 0;

    public KSVentilation(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps, Ventilation.class);

        // Register the tasks to be performed when specific property changes.
        setPropertyTask(HomeDevice.PROP_ONOFF, this::onPowerControlTask);
        setPropertyTask(Ventilation.PROP_CUR_FAN_SPEED, this::onFanSpeedControlTask);
        setPropertyTask(Ventilation.PROP_OPERATION_MODE, this::onModeControlTask);
        setPropertyTask(Ventilation.PROP_OPERATION_ALARM, this::onAlarmControlTask);
    }

    @Override
    public @ParseResult int parsePayload(KSPacket packet, PropertyMap outProps) {
        // TODO: Use some new interface of parsing action.
        switch (packet.commandType) {
            case CMD_POWER_CONTROL_RSP:
            case CMD_FAN_SPEED_CONTROL_RSP:
            case CMD_MODE_CONTROL_RSP:
                return parseSingleControlRsp(packet, outProps);
        }
        return super.parsePayload(packet, outProps);
    }

    @Override
    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 5) { // At least, 5 (error, running, speed, mode, alarm)
            if (DBG) Log.w(TAG, "parse-status-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-status-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
        }

        parsePowerStateByte(packet.data[1], outProps);
        parseFanSpeedByte(packet.data[2], outProps);
        parseModeStateByte(packet.data[3], outProps);
        parseAlarmStateByte(packet.data[4], outProps);

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 2) { // At least, 2 (speed levels, mode supports)
            if (DBG) Log.w(TAG, "parse-chr-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int data0 = packet.data[0] & 0xFF;
        mMinFanSpeedLevel = 0; // HACK: Some device has zero speed that isn't be specified in specification.
        mMaxFanSpeedLevel = data0;
        outProps.put(Ventilation.PROP_MIN_FAN_SPEED, mMinFanSpeedLevel);
        outProps.put(Ventilation.PROP_MAX_FAN_SPEED, mMaxFanSpeedLevel);
        outProps.put(Ventilation.PROP_CUR_FAN_SPEED, 0); // Set 0 as the meaning of off

        final int data1 = packet.data[1] & 0xFF;
        long supportedModes = 0;
        if ((data1 & (1 << 0)) != 0) supportedModes |= Ventilation.Mode.NORMAL;
        if ((data1 & (1 << 1)) != 0) supportedModes |= Ventilation.Mode.SLEEP;
        if ((data1 & (1 << 2)) != 0) supportedModes |= Ventilation.Mode.RECYCLE;
        if ((data1 & (1 << 3)) != 0) supportedModes |= Ventilation.Mode.AUTO;
        if ((data1 & (1 << 4)) != 0) supportedModes |= Ventilation.Mode.SAVING;
        outProps.put(Ventilation.PROP_SUPPORTED_MODES, supportedModes);

        long supportedSensors = 0;
        if ((packet.data[1] & (1 << 5)) != 0) supportedSensors |= Ventilation.Sensor.CO2;
        outProps.put(Ventilation.PROP_SUPPORTED_SENSORS, supportedSensors);

        return PARSE_OK_PEER_DETECTED;
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
        }

        parsePowerStateByte(packet.data[1], outProps);
        // Ignore DATA 2 (CO2 overflow alarm) because it will be retreived from next alarm byte.
        parseModeStateByte(packet.data[3], outProps);
        parseAlarmStateByte(packet.data[4], outProps);

        return PARSE_OK_ACTION_PERFORMED;
    }

    private void parsePowerStateByte(byte powerByte, PropertyMap outProps) {
        final int running = powerByte & 0xFF;
        outProps.put(HomeDevice.PROP_ONOFF, (running == 1));
    }

    private void parseFanSpeedByte(byte fanSpeedByte, PropertyMap outProps) {
        int fanSpeed = fanSpeedByte & 0xFF;
        if (fanSpeed != 0) // HACK: recognize 0 as off, otherwise clamp value within range.
            fanSpeed = clamp("fanSpeed", fanSpeed, mMinFanSpeedLevel, mMaxFanSpeedLevel);
        outProps.put(Ventilation.PROP_CUR_FAN_SPEED, fanSpeed);
    }

    protected void parseModeStateByte(byte modeByte, PropertyMap outProps) {
        outProps.put(Ventilation.PROP_OPERATION_MODE, byteToMode(modeByte));
    }

    private void parseAlarmStateByte(byte alarmByte, PropertyMap outProps) {
        long newAlarms = 0;
        if ((alarmByte & (1 << 0)) != 0) newAlarms |= Ventilation.Alarm.FAN_OVERHEATING;
        if ((alarmByte & (1 << 1)) != 0) newAlarms |= Ventilation.Alarm.RECYCLER_CHANGE;
        if ((alarmByte & (1 << 2)) != 0) newAlarms |= Ventilation.Alarm.FILTER_CHANGE;
        if ((alarmByte & (1 << 3)) != 0) newAlarms |= Ventilation.Alarm.SMOKE_REMOVING;
        if ((alarmByte & (1 << 4)) != 0) newAlarms |= Ventilation.Alarm.HIGH_CO2_LEVEL;
        if ((alarmByte & (1 << 5)) != 0) newAlarms |= Ventilation.Alarm.HEATER_RUNNING;
        outProps.put(Ventilation.PROP_OPERATION_ALARM, newAlarms);
    }

    protected boolean onPowerControlTask(PropertyMap reqProps, PropertyMap outProps) {
        final boolean isOn = reqProps.get(HomeDevice.PROP_ONOFF, Boolean.class);
        final byte data = (byte) ((isOn == true) ? 0x01 : 0x00);
        sendPacket(createPacket(CMD_POWER_CONTROL_REQ, data));
        return true;
    }

    protected boolean onFanSpeedControlTask(PropertyMap reqProps, PropertyMap outProps) {
        final int fanSpeed = reqProps.get(Ventilation.PROP_CUR_FAN_SPEED, Integer.class);
        sendPacket(createPacket(CMD_FAN_SPEED_CONTROL_REQ, (byte)fanSpeed));
        return true;
    }

    protected boolean onModeControlTask(PropertyMap reqProps, PropertyMap outProps) {
        final long mode = reqProps.get(Ventilation.PROP_OPERATION_MODE, Long.class);
        sendPacket(createPacket(CMD_MODE_CONTROL_REQ, modeToByte(mode)));
        return true;
    }

    protected boolean onAlarmControlTask(PropertyMap reqProps, PropertyMap outProps) {
        // Normally alarms are read-only.
        return true;
    }

    protected long byteToMode(byte modeByte) {
        if (modeByte == 0x01) return Ventilation.Mode.NORMAL;
        else if (modeByte == 0x02) return Ventilation.Mode.SLEEP;
        else if (modeByte == 0x03) return Ventilation.Mode.RECYCLE;
        else if (modeByte == 0x04) return Ventilation.Mode.AUTO;
        else if (modeByte == 0x05) return Ventilation.Mode.SAVING;
        else Log.w(TAG, "byte-to-mode: invalid mode byte:" + modeByte);
        return Ventilation.Mode.NORMAL;
    }

    protected byte modeToByte(long mode) {
        if (mode == Ventilation.Mode.NORMAL) return 0x01;
        else if (mode == Ventilation.Mode.SLEEP) return 0x02;
        else if (mode == Ventilation.Mode.RECYCLE) return  0x03;
        else if (mode == Ventilation.Mode.AUTO) return 0x04;
        else if (mode == Ventilation.Mode.SAVING) return 0x05;
        else Log.w(TAG, "mode-to-byte: invalid mode:" + mode);
        return 0x00;
    }
}
