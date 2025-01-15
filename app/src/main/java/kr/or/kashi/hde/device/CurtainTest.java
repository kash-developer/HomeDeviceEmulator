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

import static kr.or.kashi.hde.device.Curtain.PROP_CUR_OPEN_ANGLE;
import static kr.or.kashi.hde.device.Curtain.PROP_CUR_OPEN_LEVEL;
import static kr.or.kashi.hde.device.Curtain.PROP_MAX_OPEN_ANGLE;
import static kr.or.kashi.hde.device.Curtain.PROP_MAX_OPEN_LEVEL;
import static kr.or.kashi.hde.device.Curtain.PROP_MIN_OPEN_ANGLE;
import static kr.or.kashi.hde.device.Curtain.PROP_MIN_OPEN_LEVEL;
import static kr.or.kashi.hde.device.Curtain.PROP_OPERATION;
import static kr.or.kashi.hde.device.Curtain.PROP_STATE;
import static kr.or.kashi.hde.device.Curtain.PROP_SUPPORTS;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.test.HomeDeviceTestCase;

public class CurtainTest extends HomeDeviceTestCase {
    public void test_OnOff() throws Exception {
        assertPropertyChanaged(HomeDevice.PROP_ONOFF, Boolean.class, false, true);
    }

    public void test_StateCheck() throws Exception {
        assertSupported(PROP_SUPPORTS, Curtain.Support.STATE);

        device().setProperty(PROP_OPERATION, Integer.class, Curtain.Operation.STOP);
        waitFor(200);

        device().setProperty(PROP_OPERATION, Integer.class, Curtain.Operation.CLOSE);
        waitForProperty(PROP_STATE, Integer.class, Curtain.OpState.CLOSED);
        assertEquals(PROP_STATE, Integer.class, Curtain.OpState.CLOSED);

        device().setProperty(PROP_OPERATION, Integer.class, Curtain.Operation.OPEN);
        waitForProperty(PROP_STATE, Integer.class, Curtain.OpState.OPENED);
        assertEquals(PROP_STATE, Integer.class, Curtain.OpState.OPENED);
    }

    public void test_OpenLevel() throws Exception {
        assertSupported(PROP_SUPPORTS, Curtain.Support.OPEN_LEVEL);

        int minOpenLevel = device().getProperty(PROP_MIN_OPEN_LEVEL, Integer.class);
        int maxOpenLevel = device().getProperty(PROP_MAX_OPEN_LEVEL, Integer.class);
        assertTrue(minOpenLevel < maxOpenLevel);
        int expOpenLevel = minOpenLevel + 1;

        device().setProperty(PROP_OPERATION, Integer.class, Curtain.Operation.STOP);
        device().setProperty(PROP_CUR_OPEN_LEVEL, Integer.class, minOpenLevel);
        waitFor(200);

        device().setProperty(PROP_CUR_OPEN_LEVEL, Integer.class, expOpenLevel);
        device().setProperty(PROP_OPERATION, Integer.class, Curtain.Operation.OPEN);
        waitForProperty(PROP_CUR_OPEN_LEVEL, Integer.class, expOpenLevel);
        assertEquals(PROP_CUR_OPEN_LEVEL, Integer.class, expOpenLevel);
    }

    public void test_OpenAngle() throws Exception {
        assertSupported(PROP_SUPPORTS, Curtain.Support.OPEN_ANGLE);

        int minOpenAngle = device().getProperty(PROP_MIN_OPEN_ANGLE, Integer.class);
        int maxOpenAngle = device().getProperty(PROP_MAX_OPEN_ANGLE, Integer.class);
        assertTrue(minOpenAngle < maxOpenAngle);
        int expOpenAngle = minOpenAngle + 1;

        device().setProperty(PROP_OPERATION, Integer.class, Curtain.Operation.STOP);
        device().setProperty(PROP_CUR_OPEN_ANGLE, Integer.class, minOpenAngle);
        waitFor(200);

        device().setProperty(PROP_CUR_OPEN_ANGLE, Integer.class, expOpenAngle);
        device().setProperty(PROP_OPERATION, Integer.class, Curtain.Operation.OPEN);
        waitForProperty(PROP_CUR_OPEN_ANGLE, Integer.class, expOpenAngle);
        assertEquals(PROP_CUR_OPEN_ANGLE, Integer.class, expOpenAngle);
    }
}
