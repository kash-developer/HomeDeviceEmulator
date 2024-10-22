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

import static kr.or.kashi.hde.device.AirConditioner.*;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.ksx4506.KSUtils;
import kr.or.kashi.hde.test.DeviceTestCase;

public class AirConditionerTest extends DeviceTestCase {
    private static final float EPSILON = 0.005f;

    public void test_OnOff() throws Exception {
        assertPropertyChanaged(HomeDevice.PROP_ONOFF, Boolean.class, false, true);
    }

    public void test_OperationMode() throws Exception {
        final int supportedModes = device().getProperty(PROP_SUPPORTED_MODES, Integer.class);
        assertTrue((supportedModes | AirConditioner.OpMode.AUTO) != 0);
        assertTrue((supportedModes | AirConditioner.OpMode.COOLING) != 0);

        assertPropertyChanaged(PROP_OPERATION_MODE, Integer.class, AirConditioner.OpMode.AUTO, AirConditioner.OpMode.COOLING);
    }

    public void test_FlowDirection() throws Exception {
        assertPropertyChanaged(PROP_FLOW_DIRECTION, Integer.class, AirConditioner.FlowDir.MANUAL, AirConditioner.FlowDir.AUTO);
    }

    public void test_FanMode() throws Exception {
        assertPropertyChanaged(PROP_FAN_MODE, Integer.class, AirConditioner.FanMode.MANUAL, AirConditioner.FanMode.AUTO);
    }

    public void test_FanSpeed() throws Exception {
        final int minFanSpeed = device().getProperty(PROP_MIN_FAN_SPEED, Integer.class);
        final int maxFanSpeed = device().getProperty(PROP_MAX_FAN_SPEED, Integer.class);
        assertTrue(minFanSpeed < maxFanSpeed);

        device().setProperty(PROP_FAN_MODE, Integer.class, FanMode.MANUAL);
        waitForProperty(PROP_FAN_MODE, Integer.class, FanMode.MANUAL);
        assertPropertyChanaged(PROP_CUR_FAN_SPEED, Integer.class, minFanSpeed, minFanSpeed+1);
    }

    public void test_Temperature() throws Exception {
        final float minTemp = device().getProperty(PROP_MIN_TEMPERATURE, Float.class);
        final float maxTemp = device().getProperty(PROP_MAX_TEMPERATURE, Float.class);
        final float reqTemp = minTemp + 1.0f;

        assertTrue(reqTemp < maxTemp);

        device().setProperty(PROP_REQ_TEMPERATURE, Float.class, minTemp);
        waitForProperty(PROP_REQ_TEMPERATURE, Float.class, minTemp);
        device().setProperty(PROP_REQ_TEMPERATURE, Float.class, reqTemp);
        waitForProperty(PROP_REQ_TEMPERATURE, Float.class, reqTemp);
        assertEquals("", reqTemp, device().getProperty(PROP_REQ_TEMPERATURE, Float.class), EPSILON);
        // NOTE: Checks only request temperature because the actual temperature changes slowly depending on the target device
    }
}
