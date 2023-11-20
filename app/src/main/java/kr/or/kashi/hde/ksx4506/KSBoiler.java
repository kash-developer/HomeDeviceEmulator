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
import kr.or.kashi.hde.PacketSchedule;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.Thermostat;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.util.Map;

/**
 * [KS X 4506] The boiler implementation for Thermostat
 */
public class KSBoiler extends KSDeviceContextBase {
    private static final String TAG = "KSBoiler";
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

    public KSBoiler(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps, Thermostat.class);

        setPropertyTask(HomeDevice.PROP_ONOFF, this::onFunctionControlTask);
        setPropertyTask(Thermostat.PROP_FUNCTION_STATES, this::onFunctionControlTask);
        setPropertyTask(Thermostat.PROP_SETTING_TEMPERATURE, this::onTemperatureControlTask);
    }

    @Override
    public @ParseResult int parsePayload(HomePacket packet, PropertyMap outProps) {
        KSPacket ksPacket = (KSPacket) packet;

        switch (ksPacket.commandType) {
            case CMD_HEATING_STATE_RSP:
            case CMD_TEMPERATURE_RSP:
            case CMD_RESERVED_MODE_RSP:
            case CMD_OUTING_SETTING_RSP:
            case CMD_HOTWATER_ONLY_RSP: {
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

        final boolean heatingState = ((packet.data[1] & (1 << 0)) != 0);
        final boolean coolingState = false;
        final boolean outingSetting = ((packet.data[1] & (1 << 1)) != 0);
        final boolean reservedMode = ((packet.data[1] & (1 << 2)) != 0);
        final boolean hotwaterOnly = ((packet.data[1] & (1 << 3)) != 0);

        long curStates = (long) outProps.get(Thermostat.PROP_FUNCTION_STATES).getValue();
        long newStates = 0;

        if (heatingState) newStates |= Thermostat.Function.HEATING;
        if (coolingState) newStates |= Thermostat.Function.COOLING;
        if (outingSetting) newStates |= Thermostat.Function.OUTING_SETTING;
        if (reservedMode) newStates |= Thermostat.Function.RESERVED_MODE;
        if (hotwaterOnly) newStates |= Thermostat.Function.HOTWATER_ONLY;

        if (newStates != curStates) {
            outProps.put(Thermostat.PROP_FUNCTION_STATES, newStates);
            outProps.put(HomeDevice.PROP_ONOFF, outingSetting ? false : true);
            result = PARSE_OK_STATE_UPDATED;
        }

        final float settingTemp = outProps.get(Thermostat.PROP_SETTING_TEMPERATURE, Float.class);
        final float currentTemp = outProps.get(Thermostat.PROP_CURRENT_TEMPERATURE, Float.class);
        
        float newSettingTemp = parseTemperatureByte(packet.data[2]);
        float newCurrentTemp = parseTemperatureByte(packet.data[3]);

        if (newSettingTemp != settingTemp || newCurrentTemp != currentTemp) {
            outProps.put(Thermostat.PROP_SETTING_TEMPERATURE, newSettingTemp);
            outProps.put(Thermostat.PROP_CURRENT_TEMPERATURE, newCurrentTemp);
            result = PARSE_OK_STATE_UPDATED;
        }

        return result;
    }

    private float parseTemperatureByte(byte data) {
        float temperature = (data & 0x7F) * 1.0F;
        if ((data & 0x80) != 0) {
            temperature += 0.5F;
        }
        return temperature;
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

        mManufacturerId = packet.data[1] & 0xFF;
        mTemperatureDetectingType = packet.data[2] & 0xFF;
        mMaxTemperature = (float)(packet.data[3] & 0xFF);
        mMinTemperature = (float)(packet.data[4] & 0xFF);
        final int data5 = packet.data[5] & 0xFF;

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

    protected @ParseResult int parseSingleControlRsp(KSPacket packet, PropertyMap outProps) {
        // NOOP, No response packect(C1) about single control (41~) in boiler specification.
        // There are dedicated responses(C3,C4,..) for separated control commands(43,44,..) instead.
        return PARSE_OK_NONE;
    }

    protected boolean onFunctionControlTask(PropertyMap reqProps, PropertyMap outProps) {
        long curStates = (Long) getProperty(Thermostat.PROP_FUNCTION_STATES).getValue();
        long reqStates = (Long) reqProps.get(Thermostat.PROP_FUNCTION_STATES).getValue();
        long difStates = curStates ^ reqStates;

        boolean consumed = false;

        if ((difStates & Thermostat.Function.HEATING) != 0) {
            byte state = (byte) ((reqStates & Thermostat.Function.HEATING) != 0 ? 1 : 0);
            sendControlPacket3x(CMD_HEATING_STATE_REQ, state);
            consumed |= true;
        } else {
            boolean curOnOff = (Boolean) getProperty(HomeDevice.PROP_ONOFF).getValue();
            boolean reqOnOff = (Boolean) reqProps.get(HomeDevice.PROP_ONOFF).getValue();
            if (reqOnOff != curOnOff) {
                byte heating = (byte) ((reqOnOff == true) ? 1 : 0);
                sendControlPacket3x(CMD_HEATING_STATE_REQ, heating);
                consumed |= true;
            }
        }

        if ((difStates & Thermostat.Function.COOLING) != 0) {
            // Not supported
        }

        if ((difStates & Thermostat.Function.OUTING_SETTING) != 0) {
            byte state = (byte) ((reqStates & Thermostat.Function.OUTING_SETTING) != 0 ? 1 : 0);
            sendControlPacket3x(CMD_OUTING_SETTING_REQ, state);
            consumed |= true;
        }

        if ((difStates & Thermostat.Function.HOTWATER_ONLY) != 0) {
            byte state = (byte) ((reqStates & Thermostat.Function.HOTWATER_ONLY) != 0 ? 1 : 0);
            sendControlPacket3x(CMD_HOTWATER_ONLY_REQ, state);
            consumed |= true;
        }

        if ((difStates & Thermostat.Function.RESERVED_MODE) != 0) {
            byte state = (byte) ((reqStates & Thermostat.Function.RESERVED_MODE) != 0 ? 1 : 0);
            sendControlPacket3x(CMD_RESERVED_MODE_REQ, state);
            consumed |= true;
        }

        return consumed;
    }

    protected boolean onTemperatureControlTask(PropertyMap reqProps, PropertyMap outProps) {
        float reqTemp = (Float) reqProps.get(Thermostat.PROP_SETTING_TEMPERATURE).getValue();
        sendControlPacket3x(CMD_TEMPERATURE_REQ, makeTemperatureByte(reqTemp));
        return true;
    }

    private byte makeTemperatureByte(float temp) {
        final float granularity = mSupportHalfDegree ? 0.5f : 1.0f;

        // Round value by granularity
        temp = roundByUnit(temp, granularity);

        // Clamp value in range
        temp = Math.min(temp, mMaxTemperature);
        temp = Math.max(temp, mMinTemperature);

        // Cast to floor value in type of integer (byte)
        byte tempByte = (byte)temp; 

        // Set half-degree field if has
        if (mSupportHalfDegree) {
            float rest = temp - (float)tempByte;
            if (floatEquals(rest, 0.5f)) {
                tempByte |= (byte)(1 << 7);
            }
        }

        return tempByte;
    }

    private boolean floatEquals(float a, float b) {
        return Math.abs(a - b) < 0.005f /* epsilon */;
    }

    private float roundByUnit(float value, float unit) {
        final float floor = (float)((int)(value / unit)) * unit;
        final float rest = value - floor;
        final float extra = (rest > (unit / 2f)) ? unit : 0f;
        final float rounded = floor + extra;
        return rounded;
    }

    protected void sendControlPacket3x(int cmd, byte data) {
        KSPacket packet = createPacket(cmd, data);
        sendPacket(packet, 3); // WTF! repeat 3 times. (see. spec.)
    }
}
