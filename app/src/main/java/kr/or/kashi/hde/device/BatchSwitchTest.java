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

package kr.or.kashi.hde.device;

import static kr.or.kashi.hde.device.BatchSwitch.PROP_SUPPORTED_SWITCHES;
import static kr.or.kashi.hde.device.BatchSwitch.PROP_SWITCH_STATES;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.test.DeviceTestCase;

public class BatchSwitchTest extends DeviceTestCase {
    public void test_OnOff() throws Exception {
        assertPropertyChanaged(HomeDevice.PROP_ONOFF, Boolean.class, false, true);
    }

    public void test_BatchLightOff() throws Exception {
        final long supportedSwitches = device().getProperty(PROP_SUPPORTED_SWITCHES, Long.class);
        assertTrue((supportedSwitches & BatchSwitch.Switch.BATCH_LIGHT_OFF) != 0);

        long curState = (device().getProperty(PROP_SWITCH_STATES, Long.class) & ~BatchSwitch.Switch.BATCH_LIGHT_OFF);
        long expState = (curState | BatchSwitch.Switch.BATCH_LIGHT_OFF);
        assertPropertyChanaged(PROP_SWITCH_STATES, Long.class, curState, expState);
    }

    public void test_PowerSaving() throws Exception {
        final long supportedSwitches = device().getProperty(PROP_SUPPORTED_SWITCHES, Long.class);
        assertTrue((supportedSwitches & BatchSwitch.Switch.POWER_SAVING) != 0);

        long curState = (device().getProperty(PROP_SWITCH_STATES, Long.class) & ~BatchSwitch.Switch.POWER_SAVING);
        long expState = curState | BatchSwitch.Switch.POWER_SAVING;
        assertPropertyChanaged(PROP_SWITCH_STATES, Long.class, curState, expState);
    }
}
