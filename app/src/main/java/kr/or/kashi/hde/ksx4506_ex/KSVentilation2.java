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

package kr.or.kashi.hde.ksx4506_ex;

import android.util.Log;

import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.HomePacket;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.Ventilation;
import kr.or.kashi.hde.ksx4506.KSPacket;
import kr.or.kashi.hde.ksx4506.KSVentilation;

import java.util.Map;

/**
 * Extended implementation of [KS X 4506] the ventilation
 */
public class KSVentilation2 extends KSVentilation {
    private static final String TAG = "KSVentilation2";
    private static final boolean DBG = true;

    public static final int CMD_FILTER_CHANGE_ALARM_OFF_REQ    = 0x51;
    public static final int CMD_FILTER_CHANGE_ALARM_OFF_RSP    = 0xD1;

    private int mModeVersion = 0;
    private int mVendorCode = 0;

    public KSVentilation2(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps);
    }

    public @ParseResult int parsePayload(HomePacket packet, PropertyMap outProps) {
        KSPacket ksPacket = (KSPacket) packet;

        switch (ksPacket.commandType) {
            case CMD_FILTER_CHANGE_ALARM_OFF_RSP:
                return parseFilterChangeAlarmOffControlRsp(ksPacket, outProps);
        }

        return super.parsePayload(packet, outProps);
    }

    @Override
    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) {
        @ParseResult int res = super.parseCharacteristicRsp(packet, outProps); // call super
        if (res <= PARSE_ERROR_UNKNOWN) {
            return res;
        }

        // If has version byte for mode variation
        if (packet.data.length >= 3) {
            mModeVersion = packet.data[3] & 0xFF;

            // HACK: Expand or replace mode for some manufacturer.
            if (mModeVersion == 0x01) {
                long supportedModes = outProps.get(Ventilation.PROP_SUPPORTED_MODES, Long.class);
                supportedModes &= ~Ventilation.Mode.SAVING;    // Clear the saving mode from default bits.
                supportedModes |= Ventilation.Mode.CLEANAIR;   // And set the clean-air mode as supported instead.
            } else if (mModeVersion == 0x02) {
                long supportedModes = outProps.get(Ventilation.PROP_SUPPORTED_MODES, Long.class);
                supportedModes |= Ventilation.Mode.INTERNAL;   // Set the internal circulation mode as supported additionally.
                outProps.put(Ventilation.PROP_SUPPORTED_MODES, supportedModes);
            }
        }

        // If has vendor information
        if (packet.data.length >= 4) {
            mVendorCode = packet.data[4] & 0xFF;
            // HACK: Vendor code is not common but only for ventilation.
            outProps.put(HomeDevice.PROP_VENDOR_CODE, mVendorCode);
        }

        return PARSE_OK_PEER_DETECTED;
    }

    protected long byteToMode(byte modeByte) {
        if (mModeVersion == 0x01) {
            if (modeByte == 0x01) return Ventilation.Mode.NORMAL;
            else if (modeByte == 0x02) return Ventilation.Mode.SLEEP;
            else if (modeByte == 0x03) return Ventilation.Mode.RECYCLE;
            else if (modeByte == 0x04) return Ventilation.Mode.AUTO;
            else if (modeByte == 0x05) return Ventilation.Mode.CLEANAIR;
        } else if (mModeVersion == 0x02) {
            if (modeByte == 0x01) return Ventilation.Mode.NORMAL;
            else if (modeByte == 0x02) return Ventilation.Mode.SLEEP;
            else if (modeByte == 0x03) return Ventilation.Mode.RECYCLE;
            else if (modeByte == 0x04) return Ventilation.Mode.AUTO;
            else if (modeByte == 0x05) return Ventilation.Mode.SAVING;
            else if (modeByte == 0x06) return Ventilation.Mode.INTERNAL;
        } else {
            return super.byteToMode(modeByte); // call super
        }

        Log.w(TAG, "byte-to-mode: invalid mode byte:" + modeByte + "(ver:" + mModeVersion + ")");
        return Ventilation.Mode.NORMAL;
    }

    protected byte modeToByte(long mode) {
        if (mModeVersion == 0x01) {
            if (mode == Ventilation.Mode.NORMAL) return 0x01;
            else if (mode == Ventilation.Mode.SLEEP) return 0x02;
            else if (mode == Ventilation.Mode.RECYCLE) return  0x03;
            else if (mode == Ventilation.Mode.AUTO) return 0x04;
            else if (mode == Ventilation.Mode.CLEANAIR) return 0x05;
        } else if (mModeVersion == 0x02) {
            if (mode == Ventilation.Mode.NORMAL) return 0x01;
            else if (mode == Ventilation.Mode.SLEEP) return 0x02;
            else if (mode == Ventilation.Mode.RECYCLE) return  0x03;
            else if (mode == Ventilation.Mode.AUTO) return 0x04;
            else if (mode == Ventilation.Mode.SAVING) return 0x05;
            else if (mode == Ventilation.Mode.INTERNAL) return 0x06;
        } else {
            return super.modeToByte(mode); // call super
        }

        Log.w(TAG, "mode-to-byte: invalid mode:" + mode + "(ver:" + mModeVersion + ")");
        return 0x00;
    }

    @Override
    protected boolean onAlarmControlTask(PropertyMap reqProps, PropertyMap outProps) {
        super.onAlarmControlTask(reqProps, outProps); // call super

        long curAlarms = (Long) getProperty(Ventilation.PROP_OPERATION_ALARM).getValue();
        long reqAlarms = (Long) reqProps.get(Ventilation.PROP_OPERATION_ALARM).getValue();
        long difAlarms = curAlarms ^ reqAlarms;

        if ((difAlarms & Ventilation.Alarm.FILTER_CHANGE) != 0L) {
            // Send command if user wants the filter change alarm to be off.
            if ((reqAlarms & Ventilation.Alarm.FILTER_CHANGE) == 0L) {
                sendPacket(createPacket(CMD_FILTER_CHANGE_ALARM_OFF_REQ));
            }
        }

        return true;
    }

    private @ParseResult int parseFilterChangeAlarmOffControlRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length != 1) {
            if (DBG) Log.w(TAG, "parse-filterchangealarmoff-ctrl-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-filterchangealarmoff-ctrl-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.CANT_CONTROL);
            return PARSE_OK_ERROR_RECEIVED;
        }

        // No data to parse.

        return PARSE_OK_STATE_UPDATED;
    }
}
