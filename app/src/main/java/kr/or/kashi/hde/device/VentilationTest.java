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
import kr.or.kashi.hde.test.DeviceTestCase;

public class VentilationTest extends DeviceTestCase {
    public void test_OnOff() throws Exception {
        assertPropertyChanaged(HomeDevice.PROP_ONOFF, Boolean.class, false, true);
    }

    public void test_NormalMode() throws Exception {
        doOperationModeTest(Ventilation.Mode.NORMAL);
    }

    public void test_SleepMode() throws Exception {
        doOperationModeTest(Ventilation.Mode.SLEEP);
    }

    public void test_RecycleMode() throws Exception {
        doOperationModeTest(Ventilation.Mode.RECYCLE);
    }

    public void test_AutoMode() throws Exception {
        doOperationModeTest(Ventilation.Mode.AUTO);
    }

    public void test_SavingMode() throws Exception {
        doOperationModeTest(Ventilation.Mode.SAVING);
    }

    private void doOperationModeTest(long opMode) throws Exception {
        assertSupported(Ventilation.PROP_SUPPORTED_MODES, opMode);
        device().setProperty(Ventilation.PROP_OPERATION_MODE, Long.class, opMode);
        waitFor(2000);
        long modes = device().getProperty(Ventilation.PROP_OPERATION_MODE, Long.class);
        assertTrue((modes & opMode) != 0);
    }

    public void test_FanSpeed() throws Exception {
        int minSpeed = device().getProperty(Ventilation.PROP_MIN_FAN_SPEED, Integer.class);
        int maxSpeed = device().getProperty(Ventilation.PROP_MAX_FAN_SPEED, Integer.class);
        int curSpeed = device().getProperty(Ventilation.PROP_CUR_FAN_SPEED, Integer.class);
        assertTrue(minSpeed < maxSpeed);
        assertTrue(curSpeed >= minSpeed && curSpeed <= maxSpeed);

        int expSpeed = minSpeed + 1;
        device().setProperty(Ventilation.PROP_CUR_FAN_SPEED, Integer.class, expSpeed);
        waitFor(2000);
        assertEquals((int)device().getProperty(Ventilation.PROP_CUR_FAN_SPEED, Integer.class), expSpeed);
    }
}
