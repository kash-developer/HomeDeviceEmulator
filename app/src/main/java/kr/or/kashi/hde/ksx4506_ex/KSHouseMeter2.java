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

import android.content.Context;
import android.util.Log;

import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.HouseMeter;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSHouseMeter;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.util.List;
import java.util.Map;

/**
 * Extended implementation of [KS X 4506] the house-meter
 */
public class KSHouseMeter2 extends KSHouseMeter {
    private static final String TAG = "KSHouseMeter2";
    private static final boolean DBG = true;

    // HACK: Some device of manufacturer sends different size
    // of meter value in total measure.
    public static final int EXTENDED_CURRENT_METER_BYTES
            = KSHouseMeter.CURRENT_METER_BYTES;
    public static final int EXTENDED_TOTAL_METER_BYTES
            = KSHouseMeter.TOTAL_METER_BYTES + 1;
    public static final int EXTENDED_METER_DATA_BYTES
            = EXTENDED_CURRENT_METER_BYTES + EXTENDED_TOTAL_METER_BYTES;

    private boolean mExtendedMeterDigits = false;

    public KSHouseMeter2(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps);
    }

    @Override
    protected @ParseResult int parseMeterDataBytes(byte[] data, int dataOffset, int meterIndex, PropertyMap outProps) {
        if (mExtendedMeterDigits == false) {
            // Not extended, call super to parse in standard way.
            return super.parseMeterDataBytes(data, dataOffset, meterIndex, outProps);
        }

        // Parse meter data as extended format.

        final int meterOffset = dataOffset + (meterIndex * EXTENDED_METER_DATA_BYTES);

        int dataSize = Math.min(EXTENDED_METER_DATA_BYTES, data.length-meterOffset);
        if (dataSize < EXTENDED_METER_DATA_BYTES) {
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
        final int td7 = ((data[meterOffset + 6] & 0xF0) >> 4);
        final int td8 = ((data[meterOffset + 6] & 0x0F));

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
        if (type == HouseMeter.MeterType.ELECTRICITY) {
            totalMeter += ((double)td1 * 1000000.0);
            totalMeter += ((double)td2 * 100000.0);
            totalMeter += ((double)td3 * 10000.0);
            totalMeter += ((double)td4 * 1000.0);
            totalMeter += ((double)td5 * 100.0);
            totalMeter += ((double)td6 * 10.0);
            totalMeter += ((double)td7 * 1.0);
            totalMeter += ((double)td8 * 0.1);
            totalMeter = Math.round(totalMeter * 10.0) / 10.0;
        } else{
            totalMeter += ((double)td1 * 100000.0);
            totalMeter += ((double)td2 * 10000.0);
            totalMeter += ((double)td3 * 1000.0);
            totalMeter += ((double)td4 * 100.0);
            totalMeter += ((double)td5 * 10.0);
            totalMeter += ((double)td6 * 1.0);
            totalMeter += ((double)td7 * 0.1);
            totalMeter += ((double)td8 * 0.01);
            totalMeter = Math.round(totalMeter * 100.0) / 100.0;
        }
        outProps.put(HouseMeter.PROP_TOTAL_METER_VALUE, totalMeter);

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected void parseMeterCharByte(int charByte, int meterIndex, PropertyMap outProps) {
        super.parseMeterCharByte(charByte, meterIndex, outProps); // call super

        // HACK: Some device of manufacturer set fifth bit of data byte in
        // chracteristics response to inform that each value of total meters extended to
        // have 1 more byte (2 digits as bcd).
        mExtendedMeterDigits = (mCharData1ReservedBit5 == 1);
    }
}
