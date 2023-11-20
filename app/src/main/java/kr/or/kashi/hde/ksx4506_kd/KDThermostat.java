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
import kr.or.kashi.hde.device.Thermostat;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;
import kr.or.kashi.hde.ksx4506.KSUtils;

import java.util.Map;

/**
  * Non-standard implementation of [KS X 4506] the thermostat
 */
public class KDThermostat extends KSThermostat {
    private static final String TAG = "KDThermostat";
    private static final boolean DBG = true;

    public static final int CMD_POWER_OFF_REQ = 0x50;

    public KDThermostat(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps);
    }

    @Override
    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) {
        @ParseResult int result = super.parseStatusRsp(packet, outProps); // call super
        if (result == PARSE_OK_STATE_UPDATED) {
            // HACK: No field to get the state of power off, so let's guess it from
            // each state of functions.
            final long states = outProps.get(Thermostat.PROP_FUNCTION_STATES, Long.class);
            outProps.put(HomeDevice.PROP_ONOFF, (states != 0L));
        }
        return result;
    }

    @Override
    protected boolean onPowerControlTask(PropertyMap reqProps, PropertyMap outProps) {
        final boolean isOn = (Boolean) reqProps.get(HomeDevice.PROP_ONOFF).getValue();
        final KSAddress devAddress = (KSAddress)getAddress();
        final long repeatCount = devAddress.getDeviceSubId().hasFull() ? 3L : 0L;
        sendPacket(createPacket(CMD_POWER_OFF_REQ, (byte)(isOn ? 0x00 : 0x01)), repeatCount);
        return true;
    }
}
