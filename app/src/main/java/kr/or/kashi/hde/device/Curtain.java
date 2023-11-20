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
 * Curtain device class.
 */
public class Curtain extends HomeDevice {
    private static final String PROP_PREFIX = "ct.";

    /**
     * Supported functions.
     */
    public @interface Support {
        int STATE       = 1 << 0;
        int OPEN_LEVEL  = 1 << 1;
        int OPEN_ANGLE  = 1 << 2;
    }

    /**
     * Operating states.
     */
    public @interface OpState {
        int OPENED      = 0;
        int CLOSED      = 1;
        int OPENING     = 2;
        int CLOSING     = 3;
    }

    /**
     * Operations to controll.
     */
    public @interface Operation {
        int STOP        = 0;
        int OPEN        = 1;
        int CLOSE       = 2;
    }

    /**
     * Range of Openning
     */
    public static class OpenRange {
        /**
         * Minimum step of openning, lowest value that can be set by {@link #setOpenLevel(int)}
         * @see #getOpenRange()
         */
        public int minLevel;

        /**
         * Maximum step of openning, highest value that can be set by {@link #setOpenLevel(int)}
         * @see #getOpenRange()
         */
        public int maxLevel;

        /**
         * Minimum angle of openning, lowest value that can be set by {@link #setOpenAngle(int)}
         * @see #getOpenRange()
         */
        public int minAngle;

        /**
         * Maximum angle of openning, highest value that can be set by {@link #setOpenAngle(int)}
         * @see #getOpenRange()
         */
        public int maxAngle;
    }

    /** Property: Bits for supported funtions of device */
    @PropertyDef(valueClass=Support.class)
    public static final String PROP_SUPPORTS        = PROP_PREFIX + "supports";

    /** Property: Current {@link OpState} of device */
    @PropertyDef(valueClass=OpState.class)
    public static final String PROP_STATE           = PROP_PREFIX + "operation.state";

    /** Property: The {@link Operation} to request to device */
    @PropertyDef(valueClass=Operation.class)
    public static final String PROP_OPERATION       = PROP_PREFIX + "requested_operation";

    /** Property for the minimum level of opening */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_MIN_OPEN_LEVEL  = PROP_PREFIX + "open_level.min";

    /** Property for the maximum level of opening */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_MAX_OPEN_LEVEL  = PROP_PREFIX + "open_level.max";

    /** Property for current level of opening */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_CUR_OPEN_LEVEL  = PROP_PREFIX + "open_level.cur";

    /** Property for the minimum angle of opening */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_MIN_OPEN_ANGLE  = PROP_PREFIX + "open_angle.min";

    /** Property for the maximum angle of opening */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_MAX_OPEN_ANGLE  = PROP_PREFIX + "open_angle.max";

    /** Property for current angle of opening */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_CUR_OPEN_ANGLE  = PROP_PREFIX + "open_angle.cur";

    /**
     * Construct new instance. Don't call this directly.
     */
    public Curtain(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /** @hide */
    public @Type int getType() {
        return HomeDevice.Type.CURTAIN;
    }

    /**
     * Gets current state of device operation.
     * @return One of {@link OpState} as integer.
     */
    public @OpState int getOpState() {
        return getProperty(PROP_STATE, Integer.class);
    }

    /**
     * Request operation to device. After that, current state of device can
     * be retrieved by {@link #getOpState}.
     * @param op The id of {@link Operation}.
     * @see #getOpState()
     */
    public void setOperation(@Operation int op) {
        setProperty(PROP_OPERATION, Integer.class, op);
    }

    /**
     * Get the range of device movement. {@link OpenRange} contains lowest and
     * highest value for each changable factors like the opening level or the
     * opening slopes, but some values can be zero according to whether that
     * function is supported or not.
     * @return The {@link OpenRange} object that contains range factors.
     */
    public OpenRange getOpenRange() {
        OpenRange range = new OpenRange();
        range.minLevel = getProperty(PROP_MIN_OPEN_LEVEL, Integer.class);
        range.maxLevel = getProperty(PROP_MAX_OPEN_LEVEL, Integer.class);
        range.minAngle = getProperty(PROP_MIN_OPEN_ANGLE, Integer.class);
        range.maxAngle = getProperty(PROP_MAX_OPEN_ANGLE, Integer.class);
        return range;
    }

    /**
     * Gets the current level of opening.
     * @return The level of opening
     */
    public int getOpenLevel() {
        return getProperty(PROP_CUR_OPEN_LEVEL, Integer.class);
    }

    /**
     * Request that the device is opened to specific level.
     * @param level The level of opening.
     */
    public void setOpenLevel(int level) {
        setProperty(PROP_CUR_OPEN_LEVEL, Integer.class, level);
    }

    /**
     * Gets the current angle of opening.
     * @return The angle of opening
     */
    public int getOpenAngle() {
        return getProperty(PROP_CUR_OPEN_ANGLE, Integer.class);
    }


    /**
     * Request that the device is opened to specific step of angle.
     * @param angle The level of opening.
     */
    public void setOpenAngle(int angle) {
        setProperty(PROP_CUR_OPEN_ANGLE, Integer.class, angle);
    }
}
