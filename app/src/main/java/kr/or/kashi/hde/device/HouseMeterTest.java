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

import kr.or.kashi.hde.test.HomeDeviceTestCase;

public class HouseMeterTest extends HomeDeviceTestCase {
    public void test_OnOff() throws Exception {
        throw new UnsupportedOperationException();
    }

    public void test_CheckKnownTypes() throws Exception {
        assertTrue(device().getProperty(HouseMeter.PROP_METER_TYPE, Integer.class) != HouseMeter.MeterType.UNKNOWN);
        assertTrue(device().getProperty(HouseMeter.PROP_CURRENT_METER_UNIT, Integer.class) != HouseMeter.MeterType.UNKNOWN);
        assertTrue(device().getProperty(HouseMeter.PROP_TOTAL_METER_UNIT, Integer.class) != HouseMeter.MeterType.UNKNOWN);
    }

    public void test_CheckEnabled() throws Exception {
        assertTrue(device().getProperty(HouseMeter.PROP_METER_ENABLED, Boolean.class));
    }
}
