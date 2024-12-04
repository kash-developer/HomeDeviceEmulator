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
import kr.or.kashi.hde.HomeAddress;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.AirConditioner;
import kr.or.kashi.hde.device.HouseMeter;
import kr.or.kashi.hde.device.Thermostat;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;
import kr.or.kashi.hde.ksx4506.KSUtils;

import java.util.Map;

/**
 * [KS X 4506] The thermostat implementation for Thermostat
 */
public class KSThermostat extends KSDeviceContextBase {
    private static final String TAG = "KSThermostat";
    private static final boolean DBG = true;

    public static final int CMD_HEATING_STATE_REQ = 0x43;
    public static final int CMD_HEATING_STATE_RSP = 0xC3;
    public static final int CMD_TEMPERATURE_REQ = 0x44;
    public static final int CMD_TEMPERATURE_RSP = 0xC4;
    public static final int CMD_OUTING_SETTING_REQ = 0x45;
    public static final int CMD_OUTING_SETTING_RSP = 0xC5;
    public static final int CMD_RESERVED_MODE_REQ = 0x46;
    public static final int CMD_RESERVED_MODE_RSP = 0xC6;
    public static final int CMD_HOTWATER_ONLY_REQ = 0x47;
    public static final int CMD_HOTWATER_ONLY_RSP = 0xC7;

    private int mManufacturerId = 0;
    private int mTemperatureDetectingType = 0; // 1:by air, 2:by returning-water
    private float mMinTemperature = 0.0f;
    private float mMaxTemperature = 40.0f;
    private boolean mSupportHalfDegree = false;
    private int mControllerCountInGroup = 0;

    public KSThermostat(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps, Thermostat.class);

        if (isMaster()) {
            // Register the tasks to be performed when specific property changes.
            setPropertyTask(HomeDevice.PROP_ONOFF, this::onPowerControlTask);
            setPropertyTask(Thermostat.PROP_FUNCTION_STATES, this::onFunctionControlTask);
            setPropertyTask(Thermostat.PROP_SETTING_TEMPERATURE, this::onTemperatureControlTask);
        } else {
            // TEMP: Initialize some properties as specific values in slave mode.
            long supportedFunctions = 0;
            supportedFunctions |= Thermostat.Function.HEATING;
            supportedFunctions |= Thermostat.Function.OUTING_SETTING;
            supportedFunctions |= Thermostat.Function.HOTWATER_ONLY;
            supportedFunctions |= Thermostat.Function.RESERVED_MODE;
            mRxPropertyMap.put(Thermostat.PROP_SUPPORTED_FUNCTIONS, supportedFunctions);
            mRxPropertyMap.put(Thermostat.PROP_MIN_TEMPERATURE, mMinTemperature);
            mRxPropertyMap.put(Thermostat.PROP_MAX_TEMPERATURE, mMaxTemperature);
            mRxPropertyMap.put(Thermostat.PROP_TEMP_RESOLUTION, mSupportHalfDegree ? 0.5f : 1.0f);
            mRxPropertyMap.put(Thermostat.PROP_SETTING_TEMPERATURE, 10.0f);
            mRxPropertyMap.put(Thermostat.PROP_CURRENT_TEMPERATURE, 10.0f);
            mRxPropertyMap.commit();
        }
    }

    @Override
    protected int getCapabilities() {
        return CAP_STATUS_MULTI | CAP_CHARAC_MULTI;
    }

    @Override
    public @ParseResult int parsePayload(KSPacket packet, PropertyMap outProps) {
        switch (packet.commandType) {
            // Request commands
            case CMD_HEATING_STATE_REQ: return parseHeatingStateReq(packet, outProps);
            case CMD_TEMPERATURE_REQ: return parseTemperatureReq(packet, outProps);
            case CMD_RESERVED_MODE_REQ: return parseReservedModeReq(packet, outProps);
            case CMD_OUTING_SETTING_REQ: return parseOutingSettingReq(packet, outProps);
            case CMD_HOTWATER_ONLY_REQ: return parseHotwaterOnlyReq(packet, outProps);
            // Response commands
            case CMD_HEATING_STATE_RSP:
            case CMD_TEMPERATURE_RSP:
            case CMD_RESERVED_MODE_RSP:
            case CMD_OUTING_SETTING_RSP:
            case CMD_HOTWATER_ONLY_RSP: {
                // All the responses about control requests is same with the response of status.
                int res = parseStatusRsp(packet, outProps);
                if (res < PARSE_OK_NONE && res == PARSE_OK_ERROR_RECEIVED) {
                    return res;
                }
                return PARSE_OK_ACTION_PERFORMED;
            }
        }
        // Call super as fallback
        return super.parsePayload(packet, outProps);
    }

    @Override
    protected @ParseResult int parseStatusReq(KSPacket packet, PropertyMap outProps) {
        // No data to parse from request packet.

        final KSAddress devAddress = (KSAddress)getAddress();
        if (!devAddress.getDeviceSubId().isFullOfGroup()) {
            // Only full type of group device can parse the packet.
            return PARSE_OK_NONE;
        }

        final ByteArrayBuffer data = new ByteArrayBuffer();
        makeStatusRspData(getReadPropertyMap(), data);

        // Send response packet
        sendPacket(createPacket(CMD_STATUS_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    protected void makeStatusRspData(PropertyMap props, ByteArrayBuffer outData) {
        outData.append(0); // no error

        int heatingState  = 0;
        int coolingState  = 0;  // not used
        int outingSetting = 0;
        int reservedMode  = 0;
        int hotwaterOnly  = 0;
        int devIndex = 0;

        for (KSThermostat child: getChildren(KSThermostat.class)) {
            final PropertyMap childProps = child.getReadPropertyMap();
            final long curStates = childProps.get(Thermostat.PROP_FUNCTION_STATES, Long.class);
            if ((curStates & Thermostat.Function.HEATING) != 0) heatingState |= (1 << devIndex);
            if ((curStates & Thermostat.Function.OUTING_SETTING) != 0) outingSetting |= (1 << devIndex);
            if ((curStates & Thermostat.Function.HOTWATER_ONLY) != 0) hotwaterOnly |= (1 << devIndex);
            if ((curStates & Thermostat.Function.RESERVED_MODE) != 0) reservedMode |= (1 << devIndex);
            devIndex++;
        }

        outData.append(heatingState);
        outData.append(outingSetting);
        outData.append(reservedMode);
        outData.append(hotwaterOnly);

        for (KSThermostat child: getChildren(KSThermostat.class)) {
            final PropertyMap childProps = child.getReadPropertyMap();
            final float tempRes = childProps.get(Thermostat.PROP_TEMP_RESOLUTION, Float.class);
            final float minTemp = childProps.get(Thermostat.PROP_MIN_TEMPERATURE, Float.class);
            final float maxTemp = childProps.get(Thermostat.PROP_MAX_TEMPERATURE, Float.class);
            final float setTemp = childProps.get(Thermostat.PROP_SETTING_TEMPERATURE, Float.class);
            final float curTemp = childProps.get(Thermostat.PROP_CURRENT_TEMPERATURE, Float.class);
            outData.append(KSUtils.makeTemperatureByte(setTemp, minTemp, maxTemp, tempRes));
            outData.append(KSUtils.makeTemperatureByte(curTemp, minTemp, maxTemp, tempRes));
        }
    }

    @Override
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

        int controllerCount = (int)((packet.data.length - 5) / 2);
        if (controllerCount <= 0) {
            if (DBG) Log.w(TAG, "parse-status-rsp: wrong or none of controllers:" + controllerCount);
            controllerCount = 0;
        }
        if (controllerCount > 8) {
            if (DBG) Log.w(TAG, "parse-status-rsp: exceeding count of controllers:" + controllerCount);
            controllerCount = 8;
        }

        @ParseResult int result = PARSE_OK_NONE;

        final KSAddress devAddress = (KSAddress)getAddress();
        if (!devAddress.getDeviceSubId().isSingle() && !devAddress.getDeviceSubId().isSingleOfGroup()) {
            // Group context doesn't need to parse the status of device,
            // each single devices may parse only its part.
            return result;
        }

        final int devIndex = (devAddress.getDeviceSubId().value() & 0x0F) - 1;
        if (devIndex < 0) {
            if (DBG) Log.e(TAG, "parse-status-rsp: wrong device index:" + devIndex);
            return result;
        }
        if (devIndex > controllerCount - 1) {
            if (DBG) Log.e(TAG, "parse-status-rsp: dev. index(" + devIndex + ") is exceeding the limit");
            return result;
        }

        final boolean heatingState = ((packet.data[1] & (1 << devIndex)) != 0);
        final boolean coolingState = false;
        final boolean outingSetting = ((packet.data[2] & (1 << devIndex)) != 0);
        final boolean reservedMode = ((packet.data[3] & (1 << devIndex)) != 0);
        final boolean hotwaterOnly = ((packet.data[4] & (1 << devIndex)) != 0);

        long curStates = (long) outProps.get(Thermostat.PROP_FUNCTION_STATES).getValue();
        long newStates = 0;
        if (heatingState) newStates |= Thermostat.Function.HEATING;
        if (coolingState) newStates |= Thermostat.Function.COOLING;
        if (outingSetting) newStates |= Thermostat.Function.OUTING_SETTING;
        if (reservedMode) newStates |= Thermostat.Function.RESERVED_MODE;
        if (hotwaterOnly) newStates |= Thermostat.Function.HOTWATER_ONLY;

        if (newStates != curStates) {
            outProps.put(Thermostat.PROP_FUNCTION_STATES, newStates);
            result = PARSE_OK_STATE_UPDATED;
        }

        final float settingTemp = outProps.get(Thermostat.PROP_SETTING_TEMPERATURE, Float.class);
        final float currentTemp = outProps.get(Thermostat.PROP_CURRENT_TEMPERATURE, Float.class);
        int temperatureDataOffset = 5 + (devIndex * 2);
        float newSettingTemp = KSUtils.parseTemperatureByte(packet.data[temperatureDataOffset]);
        float newCurrentTemp = KSUtils.parseTemperatureByte(packet.data[temperatureDataOffset + 1]);

        if (!KSUtils.floatEquals(newSettingTemp, settingTemp) || !KSUtils.floatEquals(newCurrentTemp, currentTemp)) {
            outProps.put(Thermostat.PROP_SETTING_TEMPERATURE, newSettingTemp);
            outProps.put(Thermostat.PROP_CURRENT_TEMPERATURE, newCurrentTemp);
            result = PARSE_OK_STATE_UPDATED;
        }

        return result;
    }

    @Override
    protected @ParseResult int parseCharacteristicReq(KSPacket packet, PropertyMap outProps) {
        // No data to parse from request packet.

        final KSAddress devAddress = (KSAddress)getAddress();
        if (!devAddress.getDeviceSubId().isFullOfGroup()) {
            // Only full type of group device can parse the packet.
            return PARSE_OK_NONE;
        }

        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // no error
        data.append(mManufacturerId);
        data.append(mTemperatureDetectingType);

        final PropertyMap props = getReadPropertyMap();
        final float maxTemp = props.get(Thermostat.PROP_MAX_TEMPERATURE, Float.class);
        final float minTemp = props.get(Thermostat.PROP_MIN_TEMPERATURE, Float.class);
        data.append((int)maxTemp);
        data.append((int)minTemp);

        int data5 = 0;
        final long supportedFunctions = props.get(Thermostat.PROP_SUPPORTED_FUNCTIONS, Long.class);
        if ((supportedFunctions & Thermostat.Function.OUTING_SETTING) != 0L) data5 |= (1 << 1);
        if ((supportedFunctions & Thermostat.Function.HOTWATER_ONLY) != 0L)  data5 |= (1 << 2);
        if ((supportedFunctions & Thermostat.Function.RESERVED_MODE) != 0L)  data5 |= (1 << 3);
        final float tempRes = props.get(Thermostat.PROP_TEMP_RESOLUTION, Float.class);
        if (KSUtils.floatEquals(tempRes, 0.5f))                              data5 |= (1 << 4);
        data.append(data5);
        data.append(getChildCount()); // count of thermostats

        // Send response packet
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

        mManufacturerId = packet.data[1] & 0xFF;
        mTemperatureDetectingType = packet.data[2] & 0xFF;
        mMaxTemperature = (float)(packet.data[3] & 0xFF);
        mMinTemperature = (float)(packet.data[4] & 0xFF);
        final int data5 = packet.data[5] & 0xFF;
        mControllerCountInGroup = packet.data[6] & 0xFF;

        KSAddress address = (KSAddress)getAddress();
        if (!address.getDeviceSubId().hasFull()) {
            int index = (address.getDeviceSubId().value() & 0x0F) - 1;
            if (index >= mControllerCountInGroup) {
                // Exit since the device index is out of count.
                return PARSE_OK_NONE;
            }
        }

        long supportedFunctions = (Thermostat.Function.HEATING);
        if ((data5 & (1 << 1)) != 0) supportedFunctions |= (Thermostat.Function.OUTING_SETTING);
        if ((data5 & (1 << 2)) != 0) supportedFunctions |= (Thermostat.Function.HOTWATER_ONLY);
        if ((data5 & (1 << 3)) != 0) supportedFunctions |= (Thermostat.Function.RESERVED_MODE);
        if ((data5 & (1 << 4)) != 0) mSupportHalfDegree = true;

        outProps.put(Thermostat.PROP_SUPPORTED_FUNCTIONS, supportedFunctions);
        outProps.put(Thermostat.PROP_MIN_TEMPERATURE, mMinTemperature);
        outProps.put(Thermostat.PROP_MAX_TEMPERATURE, mMaxTemperature);
        outProps.put(Thermostat.PROP_TEMP_RESOLUTION, mSupportHalfDegree ? 0.5f : 1.0f);

        return PARSE_OK_PEER_DETECTED;
    }

    protected @ParseResult int parseHeatingStateReq(KSPacket packet, PropertyMap outProps) {
        // Parse request packet
        final boolean heatingOn = ((packet.data[0] & 0xFF) == 0x01);
        outProps.putBit(Thermostat.PROP_FUNCTION_STATES, Thermostat.Function.HEATING, heatingOn);

        final ByteArrayBuffer data = new ByteArrayBuffer();
        makeStatusRspData(outProps, data); // Encode response packet same with status.

        // Send response packet
        sendPacket(createPacket(CMD_HEATING_STATE_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseTemperatureReq(KSPacket packet, PropertyMap outProps) {
        // Parse request packet
        final float temp = KSUtils.parseTemperatureByte(packet.data[0]);
        outProps.put(Thermostat.PROP_SETTING_TEMPERATURE, temp);
        outProps.put(Thermostat.PROP_CURRENT_TEMPERATURE, temp); // TODO: This should be changed by some simulated logic.

        final ByteArrayBuffer data = new ByteArrayBuffer();
        makeStatusRspData(outProps, data); // Encode response packet same with status.

        // Send response packet
        sendPacket(createPacket(CMD_TEMPERATURE_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseReservedModeReq(KSPacket packet, PropertyMap outProps) {
        // Parse request packet
        final boolean reservedMode = ((packet.data[0] & 0xFF) == 0x01);
        outProps.putBit(Thermostat.PROP_FUNCTION_STATES, Thermostat.Function.RESERVED_MODE, reservedMode);

        final ByteArrayBuffer data = new ByteArrayBuffer();
        makeStatusRspData(outProps, data); // Encode response packet same with status.

        // Send response packet
        sendPacket(createPacket(CMD_RESERVED_MODE_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseOutingSettingReq(KSPacket packet, PropertyMap outProps) {
        // Parse request packet
        final boolean outinggOn = ((packet.data[0] & 0xFF) == 0x01);
        outProps.putBit(Thermostat.PROP_FUNCTION_STATES, Thermostat.Function.OUTING_SETTING, outinggOn);

        final ByteArrayBuffer data = new ByteArrayBuffer();
        makeStatusRspData(outProps, data); // Encode response packet same with status.

        // Send response packet
        sendPacket(createPacket(CMD_OUTING_SETTING_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseHotwaterOnlyReq(KSPacket packet, PropertyMap outProps) {
        // Parse request packet
        final boolean hotwaterOnly = ((packet.data[0] & 0xFF) == 0x01);
        outProps.putBit(Thermostat.PROP_FUNCTION_STATES, Thermostat.Function.HOTWATER_ONLY, hotwaterOnly);

        final ByteArrayBuffer data = new ByteArrayBuffer();
        makeStatusRspData(outProps, data); // Encode response packet same with status.

        // Send response packet
        sendPacket(createPacket(CMD_HOTWATER_ONLY_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseSingleControlRsp(KSPacket packet, PropertyMap outProps) {
        // NOOP, No response packect(C1) about single control (41~) in thermostat specification.
        // There are dedicated responses(C3,C4,..) for separated control commands(43,44,..) instead.
        return PARSE_OK_NONE;
    }

    protected boolean onPowerControlTask(PropertyMap reqProps, PropertyMap outProps) {
        // NOOP: There no command to control the thermostat's power in standard.
        return false;
    }

    protected boolean onFunctionControlTask(PropertyMap reqProps, PropertyMap outProps) {
        long curStates = (Long) getProperty(Thermostat.PROP_FUNCTION_STATES).getValue();
        long reqStates = (Long) reqProps.get(Thermostat.PROP_FUNCTION_STATES).getValue();
        long difStates = curStates ^ reqStates;

        boolean consumed = false;

        if ((difStates & Thermostat.Function.HEATING) != 0) {
            byte state = (byte) ((reqStates & Thermostat.Function.HEATING) != 0 ? 1 : 0);
            sendControlPacket(CMD_HEATING_STATE_REQ, state);
            consumed |= true;
        }

        if ((difStates & Thermostat.Function.COOLING) != 0) {
            // Not supported
        }

        if ((difStates & Thermostat.Function.OUTING_SETTING) != 0) {
            byte state = (byte) ((reqStates & Thermostat.Function.OUTING_SETTING) != 0 ? 1 : 0);
            sendControlPacket(CMD_OUTING_SETTING_REQ, state);
            consumed |= true;
        }

        if ((difStates & Thermostat.Function.HOTWATER_ONLY) != 0) {
            byte state = (byte) ((reqStates & Thermostat.Function.HOTWATER_ONLY) != 0 ? 1 : 0);
            sendControlPacket(CMD_HOTWATER_ONLY_REQ, state);
            consumed |= true;
        }

        if ((difStates & Thermostat.Function.RESERVED_MODE) != 0) {
            byte state = (byte) ((reqStates & Thermostat.Function.RESERVED_MODE) != 0 ? 1 : 0);
            sendControlPacket(CMD_RESERVED_MODE_REQ, state);
            consumed |= true;
        }

        return consumed;
    }

    protected boolean onTemperatureControlTask(PropertyMap reqProps, PropertyMap outProps) {
        float reqTemp = (Float) reqProps.get(Thermostat.PROP_SETTING_TEMPERATURE).getValue();
        byte tempByte = KSUtils.makeTemperatureByte(reqTemp, mMinTemperature, mMaxTemperature, mSupportHalfDegree);
        sendControlPacket(CMD_TEMPERATURE_REQ, tempByte);
        return true;
    }

    protected void sendControlPacket(int cmd, byte data) {
        KSPacket packet = createPacket(cmd, data);
        long repeatCount = getDeviceSubId().hasFull() ? 3L : 0L;
        sendPacket(packet, repeatCount);
    }
}
