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

/**
 * [KS X 4506] Utility class
 */
public class KSUtils {
    private static final float EPSILON = 0.005f;

    public static float parseTemperatureByte(byte data) {
        float temperature = (data & 0x7F) * 1.0F;
        if ((data & 0x80) != 0) {
            temperature += 0.5F;
        }
        return temperature;
    }

    public static byte makeTemperatureByte(float temp, float min, float max, boolean canHaveHalf) {
        final float resolution = canHaveHalf ? 0.5f : 1.0f;
        return makeTemperatureByte(temp, min, max, resolution);
    }

    public static byte makeTemperatureByte(float temp, float min, float max, float resolution) {
        // Round value by resolution
        temp = roundByUnit(temp, resolution);

        // Clamp value in range
        temp = Math.min(temp, max);
        temp = Math.max(temp, min);

        // Cast to floor value in type of integer (byte)
        byte tempByte = (byte)temp;

        // Set half-degree field if has
        if (!floatEquals(resolution, 1.0f)) { // TODO:
            float rest = temp - (float)tempByte;
            if (floatEquals(rest, 0.5f)) {
                tempByte |= (byte)(1 << 7);
            }
        }

        return tempByte;
    }

    public static boolean floatEquals(float a, float b) {
        return Math.abs(a - b) < EPSILON;
    }

    public static float roundByUnit(float value, float unit) {
        final float floor = (float)((int)(value / unit)) * unit;
        final float rest = value - floor;
        final float extra = (rest > (unit / 2f)) ? unit : 0f;
        final float rounded = floor + extra;
        return rounded;
    }
}
