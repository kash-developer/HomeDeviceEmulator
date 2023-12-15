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

import static kr.or.kashi.hde.device.DoorLock.PROP_CURRENT_STATES;

import kr.or.kashi.hde.test.DeviceTestCase;

public class DoorLockTest extends DeviceTestCase {
    public void test_DoorOpen() throws Exception {
        final long state = device().getProperty(PROP_CURRENT_STATES, Long.class);
        final long orgState = (state | DoorLock.State.DOOR_OPENED);
        final long expState = (state & ~DoorLock.State.DOOR_OPENED);

        device().setProperty(PROP_CURRENT_STATES, Long.class, orgState);
        waitFor(500);
        device().setProperty(PROP_CURRENT_STATES, Long.class, expState);
        waitForPropertyChanged();
        assertEquals(PROP_CURRENT_STATES, Long.class, expState);
    }
}
