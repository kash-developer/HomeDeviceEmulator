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

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.test.HomeDeviceTestCase;

public class ThermostatTest extends HomeDeviceTestCase {
    public void test_OnOff() throws Exception {
        assertPropertyChanaged(HomeDevice.PROP_ONOFF, Boolean.class, false, true);
    }

    public void test_Heating() throws Exception {
        doFunctionTest(Thermostat.Function.HEATING);
    }

    public void test_OutingSetting() throws Exception {
        doFunctionTest(Thermostat.Function.OUTING_SETTING);
    }

    public void test_ReservedMode() throws Exception {
        doFunctionTest(Thermostat.Function.RESERVED_MODE);
    }

    private void doFunctionTest(long testFunction) throws Exception {
        assertSupported(Thermostat.PROP_SUPPORTED_FUNCTIONS, testFunction);

        long functionStates = device().getProperty(Thermostat.PROP_FUNCTION_STATES, Long.class);
        device().setProperty(Thermostat.PROP_FUNCTION_STATES, Long.class, (functionStates | testFunction));
        waitFor(2000);
        functionStates = device().getProperty(Thermostat.PROP_FUNCTION_STATES, Long.class);
        assertTrue((functionStates & testFunction) != 0);
    }

    public void test_TemperatureSetting() throws Exception {
        final float minTemp = device().getProperty(Thermostat.PROP_MIN_TEMPERATURE, Float.class);
        final float maxTemp = device().getProperty(Thermostat.PROP_MAX_TEMPERATURE, Float.class);
        final float tempRes = device().getProperty(Thermostat.PROP_TEMP_RESOLUTION, Float.class);
        final float reqTemp = minTemp + tempRes;

        assertTrue(reqTemp < maxTemp);

        device().setProperty(Thermostat.PROP_SETTING_TEMPERATURE, Float.class, minTemp);
        waitFor(2000);
        device().setProperty(Thermostat.PROP_SETTING_TEMPERATURE, Float.class, reqTemp);
        waitFor(2000);
        assertEquals("", reqTemp, device().getProperty(Thermostat.PROP_SETTING_TEMPERATURE, Float.class), 0.005f /* epsilon */);
        // NOTE: Checks only request temperature because the actual temperature changes slowly depending on the target device
    }
}
