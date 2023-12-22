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
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.GasValve;
import kr.or.kashi.hde.ksx4506.KSGasValve;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.util.Map;

/**
 * Extended implementation of [KS X 4506] the gas-valve
 */
public class KSGasValve2 extends KSGasValve {
    private static final String TAG = "KSGasValve2";
    private static final boolean DBG = true;

    public KSGasValve2(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps);
    }

    @Override
    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) {
        @ParseResult int res = super.parseCharacteristicRsp(packet, outProps); // call super
        if (res <= PARSE_ERROR_UNKNOWN) {
            return res;
        }

        final int data1 = packet.data[1] & 0xFF;

        long supportedStates = outProps.get(GasValve.PROP_SUPPORTED_STATES, Long.class);
        long supportedAlarms = outProps.get(GasValve.PROP_SUPPORTED_ALARMS, Long.class);

        if ((data1 & (1L << 2)) != 0L) supportedStates |= (GasValve.State.INDUCTION);
        if ((data1 & (1L << 3)) != 0L) {    // Induction only
            supportedStates |= GasValve.State.INDUCTION;

            // Clear the gas-related bits
            supportedStates &= ~GasValve.State.GAS_VALVE;
            supportedAlarms &= ~GasValve.Alarm.GAS_LEAKAGE_DETECTED;
        }

        outProps.put(GasValve.PROP_SUPPORTED_STATES, supportedStates);
        outProps.put(GasValve.PROP_SUPPORTED_ALARMS, supportedAlarms);

        return PARSE_OK_PEER_DETECTED;
    }

    private boolean supportsInductionOnly(PropertyMap props) {
        final long supportedStates = props.get(GasValve.PROP_SUPPORTED_STATES, Long.class);
        return  ((supportedStates & GasValve.State.GAS_VALVE) == 0) &&
                ((supportedStates & GasValve.State.INDUCTION) != 0);
    }

    @Override
    protected void parseGasValveStateByte(byte stateByte, PropertyMap outProps) {
        super.parseGasValveStateByte(stateByte, outProps); // call super

        long newStates = outProps.get(GasValve.PROP_CURRENT_STATES, Long.class);
        if ((stateByte & (1L << 5)) != 0L) newStates |= GasValve.State.INDUCTION;
        outProps.put(GasValve.PROP_CURRENT_STATES, newStates);

        if (supportsInductionOnly(outProps)) {
            boolean isInductionOn = ((newStates & GasValve.State.INDUCTION) != 0L);
            outProps.put(HomeDevice.PROP_ONOFF, isInductionOn);
        }
    }

    @Override
    protected byte makeControlReqData(long states, long alarms) {
        byte controlData = super.makeControlReqData(states, alarms);

        // OVERRIDE the bit of gas valve since it's not compatible with standard.
        // WTF!!! HACK: In extended specification, the value of gas closed state is inverted
        // compared to original specification (see '7.6 gas valve action control request')
        if ((states & GasValve.State.GAS_VALVE) != 0L) {
            controlData |= (byte)(1 << 0);      // 1:open valve
        } else {
            controlData &= ~((byte)(1 << 0));   // 0:close value
        }

        if ((states & GasValve.State.INDUCTION) != 0L) {
            controlData &= ~((byte)(1 << 2));   // 0:on
        } else {
            controlData |= (byte)(1 << 2);      // 1:off
        }

        return controlData;
    }

    @Override
    protected void parseControlReqData(int controlData, PropertyMap outProps) {
        super.parseControlReqData(controlData, outProps);

        final boolean openValve = ((controlData & (1 << 0)) != 0);
        outProps.putBit(GasValve.PROP_CURRENT_STATES, GasValve.State.GAS_VALVE, openValve);
        outProps.put(HomeDevice.PROP_ONOFF, openValve);

        final boolean inductionOn = ((controlData & (1 << 2)) != 0);
        outProps.putBit(GasValve.PROP_CURRENT_STATES, GasValve.State.INDUCTION, inductionOn);
        if (supportsInductionOnly(outProps)) {
            outProps.put(HomeDevice.PROP_ONOFF, inductionOn);
        }
    }
}
