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

import kr.or.kashi.hde.test.DeviceTestCase;

public class GasValveTest extends DeviceTestCase {
    public void test_GasValve() throws Exception {
        assertSupported(GasValve.PROP_SUPPORTED_STATES, GasValve.State.GAS_VALVE);

        // NOTE: Only one-way setting is available.
        final long state = device().getProperty(GasValve.PROP_CURRENT_STATES, Long.class);
        final long expected = (state | ~GasValve.State.GAS_VALVE);
        device().setProperty(GasValve.PROP_CURRENT_STATES, Long.class, expected);
        waitFor(1000);
        assertEquals(GasValve.PROP_CURRENT_STATES, Long.class, expected);
    }

    public void test_Induction() throws Exception {
        assertSupported(GasValve.PROP_SUPPORTED_STATES, GasValve.State.INDUCTION);

        // NOTE: Only one-way setting is available.
        final long state = device().getProperty(GasValve.PROP_CURRENT_STATES, Long.class);
        final long expected = (state | ~GasValve.State.INDUCTION);
        device().setProperty(GasValve.PROP_CURRENT_STATES, Long.class, expected);
        waitFor(1000);
        assertEquals(GasValve.PROP_CURRENT_STATES, Long.class, expected);
    }

    public void test_Extinguisher() throws Exception {
        assertSupported(GasValve.PROP_SUPPORTED_ALARMS, GasValve.Alarm.EXTINGUISHER_BUZZING);

        // NOTE: Only one-way setting is available.
        final long state = device().getProperty(GasValve.PROP_CURRENT_ALARMS, Long.class);
        final long expected = (state | ~GasValve.Alarm.EXTINGUISHER_BUZZING);
        device().setProperty(GasValve.PROP_CURRENT_ALARMS, Long.class, expected);
        waitFor(1000);
        assertEquals(GasValve.PROP_CURRENT_ALARMS, Long.class, expected);
    }

    public void test_GasLeakage() throws Exception {
        assertSupported(GasValve.PROP_SUPPORTED_ALARMS, GasValve.Alarm.GAS_LEAKAGE_DETECTED);
        assertTrue(true); // Passed since alarm is not settable.
    }
}
