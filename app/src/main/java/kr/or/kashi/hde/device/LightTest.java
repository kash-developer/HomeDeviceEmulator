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

public class LightTest extends DeviceTestCase {
    public void test_Dimming() throws Exception {
        final Light light = (Light) device();
        if (!light.isDimSupported()) {
            throw new UnsupportedOperationException();
        }
        final Light.LevelRanges ranges = light.getLevelRanges();
        assertTrue(ranges.minDim < ranges.maxDim);
        assertPropertyChanaged(Light.PROP_CUR_DIM_LEVEL, Integer.class, ranges.minDim, ranges.minDim+1);
    }

    public void test_ColorTemperature() throws Exception {
        final Light light = (Light) device();
        if (!light.isToneSupported()) {
            throw new UnsupportedOperationException();
        }
        final Light.LevelRanges ranges = light.getLevelRanges();
        assertTrue(ranges.minTone < ranges.maxTone);
        assertPropertyChanaged(Light.PROP_CUR_TONE_LEVEL, Integer.class, ranges.minTone, ranges.minTone+1);
    }
}
