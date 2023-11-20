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

import android.content.Context;
import android.util.Log;

import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.HouseMeter;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.util.List;
import java.util.Map;

/**
 * [KS X 4506] The house-meter implementation for HouseMeter
 */
public class KSHouseMeter extends KSDeviceContextBase {
    private static final String TAG = "KSHouseMeter";
    private static final boolean DBG = true;

    public static final int CURRENT_METER_BYTES = 3; // BCD 6 digits
    public static final int TOTAL_METER_BYTES = 3;   // BCD 6 digits
    public static final int METER_DATA_BYTES = CURRENT_METER_BYTES + TOTAL_METER_BYTES;

    protected int mCharData1ReservedBit5 = 0;
    protected int mCharData1ReservedBit6 = 0;
    protected int mCharData1ReservedBit7 = 0;

    public KSHouseMeter(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps, HouseMeter.class);
    }

    @Override
    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 6) { // At least, 7 = current meter(3) + total meter(3)
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
            // Only the response of single request has the error field.
            final int error = packet.data[0] & 0xFF;
            if (error != 0) {
                if (DBG) Log.d(TAG, "parse-status-rsp: error occurred! " + error);
                onErrorOccurred(HomeDevice.Error.UNKNOWN);
                return PARSE_OK_ERROR_RECEIVED;
            }

            // Parse just single data since this is single device.
            return parseMeterDataBytes(packet.data, 1, 0, outProps);
        } else if (pktSubId.isFull()) {
            // Be careful of missing error byte in the response of group request.

            // Parse only one part of this single device.
            final int thisSingleId = thisSubId.value() & 0x0F;
            if (thisSingleId > 0x0 && thisSingleId < 0xF) {
                final int meterIndex = thisSingleId - 1;
                return parseMeterDataBytes(packet.data, 0, meterIndex, outProps);
            } else {
                Log.w(TAG, "parse-status-rsp: out of id range: " + thisSingleId);
            }
        } else {
            Log.w(TAG, "parse-status-rsp: not implemented case, should never reach this");
        }

        return PARSE_OK_NONE;
    }

    protected @ParseResult int parseMeterDataBytes(byte[] data, int dataOffset, int meterIndex, PropertyMap outProps) {
        final int meterOffset = dataOffset + (meterIndex * METER_DATA_BYTES);

        int dataSize = Math.min(METER_DATA_BYTES, data.length-meterOffset);
        if (dataSize < METER_DATA_BYTES) {
            if (DBG) Log.w(TAG, "parse-status-rsp: wrong size of data " + dataSize);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int type = (Integer) getProperty(HouseMeter.PROP_METER_TYPE).getValue();

        final int cd1 = ((data[meterOffset + 0] & 0xF0) >> 4);
        final int cd2 = ((data[meterOffset + 0] & 0x0F));
        final int cd3 = ((data[meterOffset + 1] & 0xF0) >> 4);
        final int cd4 = ((data[meterOffset + 1] & 0x0F));
        final int cd5 = ((data[meterOffset + 2] & 0xF0) >> 4);
        final int cd6 = ((data[meterOffset + 2] & 0x0F));

        final int td1 = ((data[meterOffset + 3] & 0xF0) >> 4);
        final int td2 = ((data[meterOffset + 3] & 0x0F));
        final int td3 = ((data[meterOffset + 4] & 0xF0) >> 4);
        final int td4 = ((data[meterOffset + 4] & 0x0F));
        final int td5 = ((data[meterOffset + 5] & 0xF0) >> 4);
        final int td6 = ((data[meterOffset + 5] & 0x0F));

        double currentMeter = 0.0;
        if (type == HouseMeter.MeterType.ELECTRICITY) {
            currentMeter += ((double)cd1 * 100000.0);
            currentMeter += ((double)cd2 * 10000.0);
            currentMeter += ((double)cd3 * 1000.0);
            currentMeter += ((double)cd4 * 100.0);
            currentMeter += ((double)cd5 * 10.0);
            currentMeter += ((double)cd6 * 1.0);
        } else {
            currentMeter += ((double)cd1 * 100.0);
            currentMeter += ((double)cd2 * 10.0);
            currentMeter += ((double)cd3 * 1.0);
            currentMeter += ((double)cd4 * 0.1);
            currentMeter += ((double)cd5 * 0.01);
            currentMeter += ((double)cd6 * 0.001);
            currentMeter = Math.round(currentMeter * 1000.0) / 1000.0;
        }
        outProps.put(HouseMeter.PROP_CURRENT_METER_VALUE, currentMeter);

        double totalMeter = 0.0;
        if (type == HouseMeter.MeterType.HEATING) {
            totalMeter += ((double)td1 * 1000.0);
            totalMeter += ((double)td2 * 100.0);
            totalMeter += ((double)td3 * 10.0);
            totalMeter += ((double)td4 * 1.0);
            totalMeter += ((double)td5 * 0.1);
            totalMeter += ((double)td6 * 0.01);
            totalMeter = Math.round(totalMeter * 100.0) / 100.0;
        } else{
            totalMeter += ((double)td1 * 10000.0);
            totalMeter += ((double)td2 * 1000.0);
            totalMeter += ((double)td3 * 100.0);
            totalMeter += ((double)td4 * 10.0);
            totalMeter += ((double)td5 * 1.0);
            totalMeter += ((double)td6 * 0.1);
            totalMeter = Math.round(totalMeter * 10.0) / 10.0;
        }
        outProps.put(HouseMeter.PROP_TOTAL_METER_VALUE, totalMeter);

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 2) { // At least, 2 = error(1) + charicteristics(1)
            if (DBG) Log.w(TAG, "parse-chr-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-chr-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();
        if (!thisSubId.isSingle() && !thisSubId.isSingleOfGroup()) {
            // Device object that represents as parent of single devices doesn't
            // need to parse any state data since it's depending on child object
            // and stateless.
            return PARSE_OK_NONE;
        }

        final KSAddress.DeviceSubId pktSubId = KSAddress.toDeviceSubId(packet.deviceSubId);
        if (pktSubId.isFull() || pktSubId.isFullOfGroup() || pktSubId.isAll()) {
            // Only full type of packets should be comming.
            final int thisSingleId = thisSubId.value() & 0x0F;
            final int meterIndex = thisSingleId - 1;
            parseMeterCharByte(packet.data[1], meterIndex, outProps);
        } else {
            Log.w(TAG, "parse-chr-rsp: should never reach this");
        }

        return PARSE_OK_PEER_DETECTED;
    }

    protected void parseMeterCharByte(byte charByte, int meterIndex, PropertyMap outProps) {
        int mtrType = HouseMeter.MeterType.UNKNOWN;
        int curUnit = HouseMeter.MeasureUnit.UNKNOWN;
        int totUnit = HouseMeter.MeasureUnit.UNKNOWN;

        switch(meterIndex) {
            case 0:
                mtrType = HouseMeter.MeterType.WATER;
                curUnit = HouseMeter.MeasureUnit.m3;
                totUnit = HouseMeter.MeasureUnit.m3;
                break;
            case 1:
                mtrType = HouseMeter.MeterType.GAS;
                curUnit = HouseMeter.MeasureUnit.m3;
                totUnit = HouseMeter.MeasureUnit.m3;
                break;
            case 2:
                mtrType = HouseMeter.MeterType.ELECTRICITY;
                curUnit = HouseMeter.MeasureUnit.W;
                totUnit = HouseMeter.MeasureUnit.kWh;
                break;
            case 3:
                mtrType = HouseMeter.MeterType.HOT_WATER;
                curUnit = HouseMeter.MeasureUnit.m3;
                totUnit = HouseMeter.MeasureUnit.m3;
                break;
            case 4:
                mtrType = HouseMeter.MeterType.HEATING;
                curUnit = HouseMeter.MeasureUnit.MW;
                totUnit = HouseMeter.MeasureUnit.MW;
                break;
            default:
                Log.e(TAG, "parse-chr-rsp: no type assigned for meter index:" + meterIndex);
                break;
        }

        // Put out the type and units of meter
        outProps.put(HouseMeter.PROP_METER_TYPE, mtrType);
        outProps.put(HouseMeter.PROP_CURRENT_METER_UNIT, curUnit);
        outProps.put(HouseMeter.PROP_TOTAL_METER_UNIT, totUnit);

        // Put out the enabled state of meter
        boolean enabled = (charByte & (1 << meterIndex)) != 0;
        outProps.put(HouseMeter.PROP_METER_ENABLED, enabled);

        // Paser reserved bits internally
        mCharData1ReservedBit5 = (charByte >> 5) & 0x01;
        mCharData1ReservedBit6 = (charByte >> 6) & 0x01;
        mCharData1ReservedBit7 = (charByte >> 7) & 0x01;
    }
}
