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
import kr.or.kashi.hde.HomePacket;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.DeviceContextBase;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.base.ReadOnlyPropertyMap;
import kr.or.kashi.hde.device.AirConditioner;
import kr.or.kashi.hde.device.Light;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;
import kr.or.kashi.hde.ksx4506.KSUtils;

import java.util.Map;

/**
 * [KS X 4506] The air-conditioner implementation for AirConditioner
 */
public class KSAirConditioner extends KSDeviceContextBase {
    private static final String TAG = "KSAirConditioner";
    private static final boolean DBG = true;
    private static final boolean USE_OPSTATE_FOR_ONOFF = true;

    public static final int CMD_SINGLE_ONOFF_REQ = CMD_SINGLE_CONTROL_REQ;
    public static final int CMD_SINGLE_ONOFF_RSP = CMD_SINGLE_CONTROL_RSP;
    public static final int CMD_TOTAL_ONOFF_REQ = CMD_GROUP_CONTROL_REQ;
    public static final int CMD_OPERATION_STATE_REQ = 0x43; // stopped or runnning
    public static final int CMD_OPERATION_STATE_RSP = 0xC3;
    public static final int CMD_TEMPERATURE_SETTING_REQ = 0x44;
    public static final int CMD_TEMPERATURE_SETTING_RSP = 0xC4;
    public static final int CMD_OPERATION_MODE_REQ = 0x45;
    public static final int CMD_OPERATION_MODE_RSP = 0xC5;
    public static final int CMD_FLOW_DIRECTION_REQ = 0x46;
    public static final int CMD_FLOW_DIRECTION_RSP = 0xC6;
    public static final int CMD_FAN_SPEED_REQ = 0x47;
    public static final int CMD_FAN_SPEED_RSP = 0xC7;

    public static final int DEVICE_STATE_BYTES = 5;

    private boolean mSupportsNaturalWind = false;
    private boolean mSupportsCooling = false;
    private boolean mSupportsHeating = false;
    private boolean mSupportsReservedMode = false;
    private boolean mSupportsHalfDegree = false;
    private boolean mSupportsFlowDirControl = false;
    private boolean mSupportsReservedBit6 = false;
    private boolean mSupportsReservedBit7 = false;

    private float mMaxCoolingTemp = 0.0f;
    private float mMinCoolingTemp = 0.0f;
    private float mMaxHeatingTemp = 0.0f;
    private float mMinHeatingTemp = 0.0f;
    private int mMaxFanSpeed = 5;
    private int mNumberOfDevices = 0;

    public KSAirConditioner(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps, AirConditioner.class);

        if (isMaster()) {
            // Register the tasks to be performed when specific property changes.
            setPropertyTask(HomeDevice.PROP_ONOFF, this::onPowerControlTask);
            setPropertyTask(AirConditioner.PROP_OPERATION_MODE, this::onOperationControlTask);
            setPropertyTask(AirConditioner.PROP_REQ_TEMPERATURE, this::onTemperatureControlTask);
            setPropertyTask(AirConditioner.PROP_FLOW_DIRECTION, this::onFlowDirectionControlTask);
            setPropertyTask(AirConditioner.PROP_FAN_MODE, this::onFanSpeedControlTask);
            setPropertyTask(AirConditioner.PROP_CUR_FAN_SPEED, this::onFanSpeedControlTask);
        }
    }

    @Override
    public @ParseResult int parsePayload(HomePacket packet, PropertyMap outProps) {
        KSPacket ksPacket = (KSPacket) packet;

        switch (ksPacket.commandType) {
            case CMD_OPERATION_STATE_REQ: return parseOperationStateReq(ksPacket, outProps);
            case CMD_TEMPERATURE_SETTING_REQ: return parseTemperatureSettingReq(ksPacket, outProps);
            case CMD_OPERATION_MODE_REQ: return parseOperationModeReq(ksPacket, outProps);
            case CMD_FLOW_DIRECTION_REQ: return parseFlowDirectionReq(ksPacket, outProps);
            case CMD_FAN_SPEED_REQ: return parseFanSpeedReq(ksPacket, outProps);

            case CMD_OPERATION_STATE_RSP:
            case CMD_TEMPERATURE_SETTING_RSP:
            case CMD_OPERATION_MODE_RSP:
            case CMD_FLOW_DIRECTION_RSP:
            case CMD_FAN_SPEED_RSP: {
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

    @Override
    protected @ParseResult int parseStatusReq(KSPacket packet, PropertyMap outProps) {
        sendStatusOrControlRsp(CMD_STATUS_RSP);
        return PARSE_OK_STATE_UPDATED;
    }

    private void sendStatusOrControlRsp(int rspCmd) {
        ByteArrayBuffer data = new ByteArrayBuffer();
        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();
        if (isSingleDevice()) {
            makeDeviceStateBytes(getReadPropertyMap(), data);
        } else {
            for (DeviceContextBase child: getChildren()) {
                makeDeviceStateBytes(child.getReadPropertyMap(), data);
            }
        }
        sendPacket(createPacket(rspCmd, data.toArray()));
    }

    @Override
    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 5) {
            if (DBG) Log.w(TAG, "parse-status-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();
        if (!thisSubId.isSingle() && !thisSubId.isSingleOfGroup()) {
            // Device object that represents as parent of single devices doesn't
            // need to parse any state data since it's depending on child object
            // and stateless.
            return PARSE_OK_NONE;
        }

        final KSAddress.DeviceSubId pktSubId = KSAddress.toDeviceSubId(packet.deviceSubId);
        if (pktSubId.isSingle() || pktSubId.isSingleOfGroup()) {
            // Parse just single data since this is single device.
            return parseDeviceStateBytes(packet.data, 0, outProps);
        } else if (pktSubId.isFullOfGroup()) {
            // From group data, parse only one channel associated to this single device.
            final int thisSingleId = thisSubId.value() & 0x0F;
            if (thisSingleId > 0x0 && thisSingleId < 0xF) {
                final int thisSingleIndex = thisSingleId - 1;
                final int dataOffset = thisSingleIndex * DEVICE_STATE_BYTES;
                return parseDeviceStateBytes(packet.data, dataOffset, outProps);
            } else {
                Log.w(TAG, "parse-status-rsp: out of id range: " + thisSingleId);
            }
        } else {
            Log.w(TAG, "parse-status-rsp: not implemented case, should never reach this");
        }

        return PARSE_OK_NONE;
    }

    private void makeDeviceStateBytes(PropertyMap props, ByteArrayBuffer outData) {
        outData.append(0); // error code

        int outOpData = 0;
        if (props.get(HomeDevice.PROP_ONOFF, Boolean.class)) {
            outOpData |= (1 << 4);
        }

        switch (props.get(AirConditioner.PROP_OPERATION_MODE, Integer.class)) {
            case AirConditioner.OpMode.AUTO:     outOpData |= 0; break;
            case AirConditioner.OpMode.COOLING:  outOpData |= 1; break;
            case AirConditioner.OpMode.DEHUMID:  outOpData |= 2; break;
            case AirConditioner.OpMode.BLOWING:  outOpData |= 3; break;
            case AirConditioner.OpMode.HEATING:  outOpData |= 4; break;
            case AirConditioner.OpMode.RESERVED: outOpData |= 5; break;
        }

        outData.append(outOpData);

        int outFanSpeed = 0;
        int curFanMode = props.get(AirConditioner.PROP_FAN_MODE, Integer.class);
        if (curFanMode == AirConditioner.FanMode.AUTO) outFanSpeed = 0x0;
        else if (curFanMode == AirConditioner.FanMode.NATURAL) outFanSpeed = 0xF;
        else outFanSpeed = props.get(AirConditioner.PROP_CUR_FAN_SPEED, Integer.class);

        int outFlowDir = 0;
        int curFlowDir = props.get(AirConditioner.PROP_FLOW_DIRECTION, Integer.class);
        if (curFlowDir == AirConditioner.FlowDir.MANUAL) outFlowDir = 0x0;
        if (curFlowDir == AirConditioner.FlowDir.AUTO) outFlowDir = 0x1;

        outData.append(((outFlowDir << 4) & 0xF0) | (outFanSpeed & 0x0F));

        final float tempRes = props.get(AirConditioner.PROP_TEMP_RESOLUTION, Float.class);
        final float minTemp = props.get(AirConditioner.PROP_MIN_TEMPERATURE, Float.class);
        final float maxTemp = props.get(AirConditioner.PROP_MAX_TEMPERATURE, Float.class);
        final float reqTemp = props.get(AirConditioner.PROP_REQ_TEMPERATURE, Float.class);
        final float curTemp = props.get(AirConditioner.PROP_CUR_TEMPERATURE, Float.class);

        outData.append(KSUtils.makeTemperatureByte(reqTemp, minTemp, maxTemp, tempRes));
        outData.append(KSUtils.makeTemperatureByte(curTemp, minTemp, maxTemp, tempRes));
    }

    private @ParseResult int parseDeviceStateBytes(byte[] data, int offset, PropertyMap outProps) {
        int stateSize = Math.min(DEVICE_STATE_BYTES, data.length-offset);
        if (stateSize < DEVICE_STATE_BYTES) {
            if (DBG) Log.w(TAG, "parse-status-rsp: wrong size of state:" + stateSize);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = data[offset + 0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-status-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        final byte data1 = data[offset + 1];

        final boolean running = ((data1 >> 4) & 0x01) != 0;
        outProps.put(HomeDevice.PROP_ONOFF, running);

        final int modeData = data1 & 0x0F;
        int opMode = AirConditioner.OpMode.AUTO;
             if (modeData == 0) opMode = AirConditioner.OpMode.AUTO;
        else if (modeData == 1) opMode = AirConditioner.OpMode.COOLING;
        else if (modeData == 2) opMode = AirConditioner.OpMode.DEHUMID;
        else if (modeData == 3) opMode = AirConditioner.OpMode.BLOWING;
        else if (modeData == 4) opMode = AirConditioner.OpMode.HEATING;
        else if (modeData == 5) opMode = AirConditioner.OpMode.RESERVED;
        outProps.put(AirConditioner.PROP_OPERATION_MODE, opMode);

        final byte data2 = data[offset + 2];
        final int fanSpeed = data2 & 0x0F;

        int newFanSpeed = outProps.get(AirConditioner.PROP_CUR_FAN_SPEED, Integer.class);
        int newFanMode = AirConditioner.FanMode.MANUAL;
        if (fanSpeed == 0x0) newFanMode = AirConditioner.FanMode.AUTO;
        else if (fanSpeed == 0xF) newFanMode = AirConditioner.FanMode.NATURAL;
        else newFanSpeed = fanSpeed;
        outProps.put(AirConditioner.PROP_FAN_MODE, newFanMode);
        outProps.put(AirConditioner.PROP_CUR_FAN_SPEED, newFanSpeed);

        final int flowDir = (data2 >> 4) & 0x0F;
        int newFlowDir = AirConditioner.FlowDir.MANUAL;
        if (flowDir == 0) newFlowDir = AirConditioner.FlowDir.MANUAL;
        if (flowDir == 1) newFlowDir = AirConditioner.FlowDir.AUTO;
        outProps.put(AirConditioner.PROP_FLOW_DIRECTION, newFlowDir);

        final float settingTemp = outProps.get(AirConditioner.PROP_REQ_TEMPERATURE, Float.class);
        final float currentTemp = outProps.get(AirConditioner.PROP_CUR_TEMPERATURE, Float.class);
        float newSettingTemp = KSUtils.parseTemperatureByte(data[offset + 3]);
        float newCurrentTemp = KSUtils.parseTemperatureByte(data[offset + 4]);
        if (!KSUtils.floatEquals(newSettingTemp, settingTemp) || !KSUtils.floatEquals(newCurrentTemp, currentTemp)) {
            outProps.put(AirConditioner.PROP_REQ_TEMPERATURE, newSettingTemp);
            outProps.put(AirConditioner.PROP_CUR_TEMPERATURE, newCurrentTemp);
        }

        if (opMode == AirConditioner.OpMode.HEATING) {
            outProps.put(AirConditioner.PROP_MIN_TEMPERATURE, mMinHeatingTemp);
            outProps.put(AirConditioner.PROP_MAX_TEMPERATURE, mMaxHeatingTemp);
        } else {
            outProps.put(AirConditioner.PROP_MIN_TEMPERATURE, mMinCoolingTemp);
            outProps.put(AirConditioner.PROP_MAX_TEMPERATURE, mMaxCoolingTemp);
        }

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseCharacteristicReq(KSPacket packet, PropertyMap outProps) {
        // No data to parse from request packet.

        final PropertyMap props = getReadPropertyMap();
        final ByteArrayBuffer data = new ByteArrayBuffer();

        data.append(0); // no error

        final int supportedModes = props.get(AirConditioner.PROP_SUPPORTED_MODES, Integer.class);
        mSupportsCooling = (supportedModes | AirConditioner.OpMode.COOLING) != 0;
        mSupportsHeating = (supportedModes | AirConditioner.OpMode.HEATING) != 0;
        mSupportsReservedMode = (supportedModes | AirConditioner.OpMode.RESERVED) != 0;

        int data1 = 0;
        if (mSupportsNaturalWind)   data1 |= (1 << 0);
        if (mSupportsCooling)       data1 |= (1 << 1);
        if (mSupportsHeating)       data1 |= (1 << 2);
        if (mSupportsReservedMode)  data1 |= (1 << 3);
        if (mSupportsHalfDegree)    data1 |= (1 << 4);
        if (mSupportsFlowDirControl)data1 |= (1 << 5);
        if (mSupportsReservedBit6)  data1 |= (1 << 6);
        if (mSupportsReservedBit7)  data1 |= (1 << 7);

        data.append(data1);

        final float maxTemp = props.get(AirConditioner.PROP_MAX_TEMPERATURE, Float.class);
        final float minTemp = props.get(AirConditioner.PROP_MIN_TEMPERATURE, Float.class);
        final float tempRes = props.get(AirConditioner.PROP_TEMP_RESOLUTION, Float.class);
        final byte maxTempByte = KSUtils.makeTemperatureByte(maxTemp, maxTemp, maxTemp, tempRes);
        final byte minTempByte = KSUtils.makeTemperatureByte(minTemp, minTemp, minTemp, tempRes);
        mMaxCoolingTemp = mMaxHeatingTemp = maxTemp; // TODO:
        mMinCoolingTemp = mMinHeatingTemp = minTemp; // TODO:
        mMaxFanSpeed = props.get(AirConditioner.PROP_MAX_FAN_SPEED, Integer.class);
        mNumberOfDevices = (hasChild()) ? (getChildCount()) : (1);

        data.append(maxTempByte);
        data.append(minTempByte);
        data.append(maxTempByte);
        data.append(minTempByte);
        data.append(mMaxFanSpeed);
        data.append(mNumberOfDevices);

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

        final byte data1 = packet.data[1];
        mSupportsNaturalWind    = ((data1 & (1 << 0)) != 0);
        mSupportsCooling        = ((data1 & (1 << 1)) != 0);
        mSupportsHeating        = ((data1 & (1 << 2)) != 0);
        mSupportsReservedMode   = ((data1 & (1 << 3)) != 0);
        mSupportsHalfDegree     = ((data1 & (1 << 4)) != 0);
        mSupportsFlowDirControl = ((data1 & (1 << 5)) != 0);
        mSupportsReservedBit6   = ((data1 & (1 << 6)) != 0);
        mSupportsReservedBit7   = ((data1 & (1 << 7)) != 0);

        int newSupports = 0;
        newSupports |= AirConditioner.OpMode.AUTO;
        newSupports |= AirConditioner.OpMode.BLOWING;
        newSupports |= AirConditioner.OpMode.DEHUMID;
        if (mSupportsCooling) newSupports |= AirConditioner.OpMode.COOLING;
        if (mSupportsHeating) newSupports |= AirConditioner.OpMode.HEATING;
        if (mSupportsReservedMode) newSupports |= AirConditioner.OpMode.RESERVED;
        outProps.put(AirConditioner.PROP_SUPPORTED_MODES, newSupports);

        mMaxCoolingTemp = KSUtils.parseTemperatureByte(packet.data[2]);
        mMinCoolingTemp = KSUtils.parseTemperatureByte(packet.data[3]);
        mMaxHeatingTemp = KSUtils.parseTemperatureByte(packet.data[4]);
        mMinHeatingTemp = KSUtils.parseTemperatureByte(packet.data[5]);
        mMaxFanSpeed = clamp("maxFanSpeed", packet.data[6] & 0xFF, 1, 5);
        mNumberOfDevices = packet.data[7] & 0xFF;

        final float minTemp = (mSupportsHeating) ? (mMinHeatingTemp) : (mMinCoolingTemp);
        final float maxTemp = (mSupportsHeating) ? (mMaxHeatingTemp) : (mMaxCoolingTemp);
        final float tempResolution = (mSupportsHalfDegree) ? (0.5f) : (1.0f);
        outProps.put(AirConditioner.PROP_MIN_TEMPERATURE, minTemp);
        outProps.put(AirConditioner.PROP_MAX_TEMPERATURE, maxTemp);
        outProps.put(AirConditioner.PROP_TEMP_RESOLUTION, tempResolution);

        final int minFanSpeed = 1;
        final int maxFanSpeed = mMaxFanSpeed;
        outProps.put(AirConditioner.PROP_MIN_FAN_SPEED, minFanSpeed);
        outProps.put(AirConditioner.PROP_MAX_FAN_SPEED, maxFanSpeed);

        final KSAddress.DeviceSubId ctxSubId = getDeviceSubId();
        if (ctxSubId.isSingle() || ctxSubId.isSingleOfGroup()) {
            int singleIndex = ctxSubId.singleId() - 1;
            if (singleIndex >= 0 && singleIndex < mNumberOfDevices) {
                return PARSE_OK_PEER_DETECTED;
            }
        }

        return PARSE_OK_NONE;
    }

    protected @ParseResult int parseOperationStateReq(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 1) {
            if (DBG) Log.w(TAG, "parse-op-state-req: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        int opState = packet.data[0] & 0xFF;
        outProps.put(Light.PROP_ONOFF, (opState != 0));
        // TODO: Change also children states if this is parent of.

        sendStatusOrControlRsp(CMD_OPERATION_STATE_RSP);

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseTemperatureSettingReq(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 1) {
            if (DBG) Log.w(TAG, "parse-temp-set-req: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        float curTemp = KSUtils.parseTemperatureByte(packet.data[0]);
        outProps.put(AirConditioner.PROP_REQ_TEMPERATURE, curTemp);
        outProps.put(AirConditioner.PROP_CUR_TEMPERATURE, curTemp);
        // TODO: Change also children states if this is parent of.

        sendStatusOrControlRsp(CMD_TEMPERATURE_SETTING_RSP);

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseOperationModeReq(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 1) {
            if (DBG) Log.w(TAG, "parse-op-mode-req: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        int opMode = AirConditioner.OpMode.AUTO;
        switch (packet.data[0] & 0xFF) {
            case 0x00: opMode = AirConditioner.OpMode.AUTO; break;
            case 0x01: opMode = AirConditioner.OpMode.COOLING; break;
            case 0x04: opMode = AirConditioner.OpMode.HEATING; break;
            case 0x03: opMode = AirConditioner.OpMode.BLOWING; break;
            case 0x02: opMode = AirConditioner.OpMode.DEHUMID; break;
            case 0x05: opMode = AirConditioner.OpMode.RESERVED; break;
        }
        outProps.put(AirConditioner.PROP_OPERATION_MODE, opMode);
        // TODO: Change also children states if this is parent of.

        sendStatusOrControlRsp(CMD_OPERATION_MODE_RSP);

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseFlowDirectionReq(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 1) {
            if (DBG) Log.w(TAG, "parse-flow-dir-req: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        int flowDir = AirConditioner.FlowDir.MANUAL;
        final int dirByte = packet.data[0] & 0xFF;
        if (dirByte == 0x00) flowDir = AirConditioner.FlowDir.MANUAL;
        else if (dirByte == 0x01) flowDir = AirConditioner.FlowDir.AUTO;
        outProps.put(AirConditioner.PROP_FLOW_DIRECTION, flowDir);
        // TODO: Change also children states if this is parent of.

        sendStatusOrControlRsp(CMD_FLOW_DIRECTION_RSP);

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseFanSpeedReq(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 1) {
            if (DBG) Log.w(TAG, "parse-fan-speed-req: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        int fanMode = AirConditioner.FanMode.MANUAL;
        int fanSpeed = outProps.get(AirConditioner.PROP_CUR_FAN_SPEED, Integer.class);
        int fanSpeedByte = packet.data[0] & 0xFF;
        if (fanSpeedByte == 0x00) {
            fanMode = AirConditioner.FanMode.AUTO;
        } else if (fanSpeedByte == 0x0F) {
            fanMode = AirConditioner.FanMode.NATURAL;
        } else {
            fanSpeed = fanSpeedByte;
        }
        outProps.put(AirConditioner.PROP_FAN_MODE, fanMode);
        outProps.put(AirConditioner.PROP_CUR_FAN_SPEED, fanSpeed);
        // TODO: Change also children states if this is parent of.

        sendStatusOrControlRsp(CMD_FAN_SPEED_RSP);

        return PARSE_OK_STATE_UPDATED;
    }

    protected boolean onPowerControlTask(PropertyMap reqProps, PropertyMap outProps) {
        final boolean curState = (Boolean) getProperty(HomeDevice.PROP_ONOFF).getValue();
        final boolean reqState = (Boolean) reqProps.get(HomeDevice.PROP_ONOFF).getValue();
        if (reqState == curState) {
            return false;
        }

        final byte data = (byte) ((reqState == true) ? 0x01 : 0x00);

        if (USE_OPSTATE_FOR_ONOFF) {
            sendControlData(CMD_OPERATION_STATE_REQ, data);
        } else {
            sendControlData(isSingleDevice() ? CMD_SINGLE_ONOFF_REQ : CMD_TOTAL_ONOFF_REQ, data);
        }

        return true;
    }

    protected boolean onOperationControlTask(PropertyMap reqProps, PropertyMap outProps) {
        final int curOpMode = (Integer) getProperty(AirConditioner.PROP_OPERATION_MODE).getValue();
        final int reqOpMode = (Integer) reqProps.get(AirConditioner.PROP_OPERATION_MODE).getValue();
        if (reqOpMode == curOpMode) {
            return false;
        }

        byte modeData = 0x00;

        switch (reqOpMode) {
            case AirConditioner.OpMode.AUTO:       modeData = 0x00; break;
            case AirConditioner.OpMode.COOLING:    modeData = 0x01; break;
            case AirConditioner.OpMode.HEATING:    modeData = 0x04; break;
            case AirConditioner.OpMode.BLOWING:    modeData = 0x03; break;
            case AirConditioner.OpMode.DEHUMID:    modeData = 0x02; break;
            case AirConditioner.OpMode.RESERVED:   modeData = 0x05; break;
        }

        sendControlData(CMD_OPERATION_MODE_REQ, modeData);

        return true;
    }

    protected boolean onTemperatureControlTask(PropertyMap reqProps, PropertyMap outProps) {
        final float curTemp = (Float) getProperty(AirConditioner.PROP_REQ_TEMPERATURE).getValue();
        final float reqTemp = (Float) reqProps.get(AirConditioner.PROP_REQ_TEMPERATURE).getValue();
        if (reqTemp == curTemp) {
            return false;
        }

        final int reqOpMode = (Integer) reqProps.get(AirConditioner.PROP_OPERATION_MODE).getValue();
        final float tempMin = (reqOpMode == AirConditioner.OpMode.HEATING) ? mMinHeatingTemp : mMinCoolingTemp;
        final float tempMax = (reqOpMode == AirConditioner.OpMode.HEATING) ? mMaxHeatingTemp : mMaxCoolingTemp;
        final byte tempByte = KSUtils.makeTemperatureByte(reqTemp, tempMin, tempMax, mSupportsHalfDegree);
        sendControlData(CMD_TEMPERATURE_SETTING_REQ, tempByte);

        return true;
    }

    protected boolean onFlowDirectionControlTask(PropertyMap reqProps, PropertyMap outProps) {
        final int curFlowDir = (Integer) getProperty(AirConditioner.PROP_FLOW_DIRECTION).getValue();
        final int reqFlowDir = (Integer) reqProps.get(AirConditioner.PROP_FLOW_DIRECTION).getValue();
        if (reqFlowDir == curFlowDir) {
            return false;
        }

        byte dirByte = 0x00;
        if (reqFlowDir == AirConditioner.FlowDir.MANUAL) dirByte = 0x00;
        else if (reqFlowDir == AirConditioner.FlowDir.AUTO) dirByte = 0x01;
        sendControlData(CMD_FLOW_DIRECTION_REQ, dirByte);

        return true;
    }

    protected boolean onFanSpeedControlTask(PropertyMap reqProps, PropertyMap outProps) {
        final int curFanSpeed = (Integer) getProperty(AirConditioner.PROP_CUR_FAN_SPEED).getValue();
        final int reqFanSpeed = (Integer) reqProps.get(AirConditioner.PROP_CUR_FAN_SPEED).getValue();
        final int curFanMode = (Integer) getProperty(AirConditioner.PROP_FAN_MODE).getValue();
        final int reqFanMode = (Integer) reqProps.get(AirConditioner.PROP_FAN_MODE).getValue();
        if (reqFanSpeed == curFanSpeed && reqFanMode == curFanMode) {
            return false;
        }

        byte fanSpeedByte = 0x00;
        if (reqFanMode == AirConditioner.FanMode.AUTO) fanSpeedByte = 0x00;
        else if (reqFanMode == AirConditioner.FanMode.NATURAL) fanSpeedByte = 0x0F;
        else fanSpeedByte = (byte) reqFanSpeed;
        sendControlData(CMD_FAN_SPEED_REQ, fanSpeedByte);

        return true;
    }

    protected boolean sendControlData(int cmd, byte data) {
        final KSPacket packet = createPacket(cmd, new byte[] { data });
        final int repeatCount = isSingleDevice() ? 1 : 3;
        // WTF!!! Should send same packet 3 times if target is group (see. spec.)
        sendPacket(packet, repeatCount);
        return true;
    }
}

