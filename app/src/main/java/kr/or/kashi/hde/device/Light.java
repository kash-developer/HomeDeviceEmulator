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

import kr.or.kashi.hde.base.PropertyDef;
import kr.or.kashi.hde.DeviceContextBase;
import kr.or.kashi.hde.HomeDevice;

/**
 * A device class for controlling the switchs or device of light.
 */
public class Light extends HomeDevice {
    private static final String PROP_PREFIX = "ld.";

    /**
     * Range of levels
     */
    public static class LevelRanges {
        /**
         * Minimum level of dimming
         */
        public int minDim;

        /**
         * Maximum level of dimming
         */
        public int maxDim;

        /**
         * Minimum level of color-tone (temperature)
         */
        public int minTone;

        /**
         * Maximum level of color-tone (temperature)
         */
        public int maxTone;
    }

    /** Property: Whether to support dimming */
    @PropertyDef(valueClass=Boolean.class, defValueB=false)
    public static final String PROP_DIM_SUPPORTED   = PROP_PREFIX + "dim_supported";

    /** Property: Minimum level of dimming */
    @PropertyDef(valueClass=Integer.class, defValueI=0x1)
    public static final String PROP_MIN_DIM_LEVEL   = PROP_PREFIX + "min_dim_level";

    /** Property: Maximum level of dimming */
    @PropertyDef(valueClass=Integer.class, defValueI=0xf)
    public static final String PROP_MAX_DIM_LEVEL   = PROP_PREFIX + "max_dim_level";

    /** Property: Current level of dimming */
    @PropertyDef(valueClass=Integer.class, defValueI=0x0)
    public static final String PROP_CUR_DIM_LEVEL   = PROP_PREFIX + "cur_dim_level";

    /** Property: Whether to support color temperature (color tone) */
    @PropertyDef(valueClass=Boolean.class, defValueB=false)
    public static final String PROP_TONE_SUPPORTED  = PROP_PREFIX + "tone_supported";

    /** Property: Minimum level of color tone */
    @PropertyDef(valueClass=Integer.class, defValueI=0x1)
    public static final String PROP_MIN_TONE_LEVEL  = PROP_PREFIX + "min_tone_level";

    /** Property: Maximum level of color tone */
    @PropertyDef(valueClass=Integer.class, defValueI=0xf)
    public static final String PROP_MAX_TONE_LEVEL  = PROP_PREFIX + "max_tone_level";

    /** Property: Current level of color tone */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_CUR_TONE_LEVEL  = PROP_PREFIX + "cur_tone_level";

    /** Property: Whether if the type of switch is 3-way */
    @PropertyDef(valueClass=Boolean.class)
    public static final String PROP_IS_3WAY_SWITCH  = PROP_PREFIX + "is_3way_switch";

    /** Property: Enter to or exit from the batch-light-off state */
    @PropertyDef(valueClass=Boolean.class)
    public static final String PROP_BATCH_LIGHT_OFF = PROP_PREFIX + "batch_light_off";

    /**
     * Construct new instance. Don't call this directly.
     */
    public Light(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /** @hide */
    public @Type int getType() {
        return HomeDevice.Type.LIGHT;
    }

    /**
     * Whether if dimming of light is supported.
     * @return true if dimming is supported.
     */
    public boolean isDimSupported() {
        return getProperty(PROP_DIM_SUPPORTED, Boolean.class);
    }

    /**
     * Whether if changing of color tone is supported.
     * @return true if color tone is changable.
     */
    public boolean isToneSupported() {
        return getProperty(PROP_TONE_SUPPORTED, Boolean.class);
    }

    /**
     * Returns {@link LevelRanges} that contains ranges of such as dimming or
     * changing of color tone. Some range factors can be zero if the function
     * is not supported.
     * @see #isDimSupported()
     * @see #isToneSupported()
     * @return {@link LevelRanges} object
     */
    public LevelRanges getLevelRanges() {
        LevelRanges ranges = new LevelRanges();
        ranges.minDim  = getProperty(PROP_MIN_DIM_LEVEL, Integer.class);
        ranges.maxDim  = getProperty(PROP_MAX_DIM_LEVEL, Integer.class);
        ranges.minTone = getProperty(PROP_MIN_TONE_LEVEL, Integer.class);
        ranges.maxTone = getProperty(PROP_MAX_TONE_LEVEL, Integer.class);
        return ranges;
    }

    /**
     * Gets current level of dimming.
     * @return {@code int} value of dimming level
     */
    public int getCurDimLevel() {
        return getProperty(PROP_CUR_DIM_LEVEL, Integer.class);
    }

    /**
     * Request new level of dimming.
     * @param level {@code int} value of dimming level
     */
    public void setCurDimLevel(int level) {
        setProperty(PROP_CUR_DIM_LEVEL, Integer.class, level);
    }

    /**
     * Gets current tone of color.
     * @return {@code int} value of color tone
     */
    public int getCurToneLevel() {
        return getProperty(PROP_CUR_TONE_LEVEL, Integer.class);
    }

    /**
     * Request new tone of color.
     * @param level {@code int} value of color tone
     */
    public void setCurToneLevel(int level) {
        setProperty(PROP_CUR_TONE_LEVEL, Integer.class, level);
    }
}
