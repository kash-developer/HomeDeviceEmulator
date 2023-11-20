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
import kr.or.kashi.hde.base.PropertyTask;
import kr.or.kashi.hde.HomePacket;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.Light;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSLight;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.util.List;
import java.util.Map;

/**
 * Extended implementation of [KS X 4506] the light
 */
public class KSLight2 extends KSLight {
    private static final String TAG = "KSLight2";
    private static final boolean DBG = true;

    public static final int CMD_SINGLE_TONE_CONTROL_REQ = 0x54;
    public static final int CMD_SINGLE_TONE_CONTROL_RSP = 0xD4;

    public KSLight2(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps);

        setPropertyTask(Light.PROP_CUR_TONE_LEVEL, mSingleToneControlTask);
    }

    protected PropertyTask mSingleToneControlTask = new PropertyTask() {
        @Override
        public boolean execTask(PropertyMap newProps, PropertyMap outProps) {
            sendPacket(makeToneControlReq(newProps));
            sendPacket(makeStatusReq(newProps));
            return true;
        }
    };

    public @ParseResult int parsePayload(HomePacket packet, PropertyMap outProps) {
        KSPacket ksPacket = (KSPacket) packet;

        switch (ksPacket.commandType) {
            case CMD_SINGLE_TONE_CONTROL_RSP:
                return parseSingleToneControlRsp(ksPacket, outProps);
        }

        return super.parsePayload(packet, outProps);
    }

    @Override
    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) {
        @ParseResult int res = super.parseStatusRsp(packet, outProps);
        if (res <= PARSE_ERROR_UNKNOWN) {
            return res;
        }

        int toneStateOffset = 1 + mTotalCountInGroup;
        int toneStateCount = packet.data.length - toneStateOffset;

        if (toneStateCount > 0) {
            if (KSAddress.toDeviceSubId(packet.deviceSubId).isSingle()) {
                final int state = packet.data[toneStateOffset] & 0xFF;
                final int subId = state & 0x0F; // TODO: Need to check?
                final int toneValue = (state >> 4) & 0x0F;
                outProps.put(Light.PROP_CUR_TONE_LEVEL, toneValue);
            } else {
                KSAddress address = (KSAddress)getAddress();
                int thisSubId = address.getDeviceSubId().value() & 0x0F;
                for (int i=0; i<toneStateCount; i++) {
                    final int state = packet.data[toneStateOffset + i] & 0xFF;
                    final int subId = state & 0x0F;
                    final int toneValue = (state >> 4) & 0x0F;
                    if (subId == thisSubId) {
                        outProps.put(Light.PROP_CUR_TONE_LEVEL, toneValue);
                    }
                }
            }
        }

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) {
        @ParseResult int res = super.parseCharacteristicRsp(packet, outProps);
        if (res <= PARSE_ERROR_UNKNOWN) {
            return res;
        }

        int thisSubId = ((KSAddress)getAddress()).getDeviceSubId().value() & 0x0F;
        if (thisSubId > mTotalCountInGroup) {
            // Exit since the index is out of count.
            return PARSE_OK_NONE;
        }

        // If has the 3-way switch related data
        if (packet.data.length >= 6) {
            int threeWaySwitchId = packet.data[5] & 0xFF; // 0:none, 1~14:sub-id
            threeWaySwitchId = clamp("threeWaySwitchId", threeWaySwitchId, 0, 14);

            if (!KSAddress.toDeviceSubId(packet.deviceSubId).isSingle()) {
                if (threeWaySwitchId > 0 && threeWaySwitchId == thisSubId) {
                    outProps.put(Light.PROP_IS_3WAY_SWITCH, true);
                }
            }
        }

        // If has the maximum step of dimming
        if (packet.data.length >= 7) {
            final int maxDimStep = packet.data[6] & 0xFF;
            if (maxDimStep > 0) {
                final int minDimLevel = 1;
                final int maxDimLevel = clamp("maxDimStep", maxDimStep, minDimLevel, 0xf);
                outProps.put(Light.PROP_MIN_DIM_LEVEL, minDimLevel);
                outProps.put(Light.PROP_MAX_DIM_LEVEL, maxDimLevel);
            }
        }

        // If has the color temperature related data
        if (packet.data.length >= 10) {
            boolean toneSupported = false;

            final int maxToneStep = packet.data[7] & 0xFF;
            if (maxToneStep > 0) {
                final int minToneLevel = 1;
                final int maxToneLevel = clamp("maxToneStep", maxToneStep, minToneLevel, 0xf);
                outProps.put(Light.PROP_MIN_TONE_LEVEL, minToneLevel);
                outProps.put(Light.PROP_MAX_TONE_LEVEL, maxToneLevel);
                // HACK: Assumed tone-supported with steps existence at first.
                toneSupported = true;
            }

            final int toneFlag1 = packet.data[8] & 0xFF;
            final int toneFlag2 = packet.data[9] & 0xFF;
            final int toneFlags = (toneFlag2 << 8) | toneFlag1;

            if (!KSAddress.toDeviceSubId(packet.deviceSubId).isSingle()) {
                KSAddress address = (KSAddress)getAddress();
                int index = address.getDeviceSubId().value() & 0x0F;
                if (index >= 1 && index <= 0xE) {
                    toneSupported = ((toneFlags >> (index-1)) & 0x01) != 0;
                }
            }

            outProps.put(Light.PROP_TONE_SUPPORTED, toneSupported);
        }

        return PARSE_OK_PEER_DETECTED;
    }

    private KSPacket makeToneControlReq(PropertyMap props) {
        final int toneLevel = (int) props.get(Light.PROP_CUR_TONE_LEVEL).getValue();

        KSPacket packet = createPacket(CMD_SINGLE_TONE_CONTROL_REQ);
        packet.data = new byte[1];
        packet.data[0] = (byte)(toneLevel & 0x0F);

        return packet;
    }

    private @ParseResult int parseSingleToneControlRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length != 2) {
            if (DBG) Log.w(TAG, "parse-tone-ctrl-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-tone-ctrl-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.CANT_CONTROL);
            return PARSE_OK_ERROR_RECEIVED;
        }

        final int toneLevel = packet.data[1] & 0xFF;
        outProps.put(Light.PROP_CUR_TONE_LEVEL, toneLevel);

        return PARSE_OK_STATE_UPDATED;
    }
}
