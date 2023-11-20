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

import java.util.ArrayList;
import java.util.List;

/**
 * A device class for the ventilation.
 */
public class Ventilation extends HomeDevice {
    private static final String PROP_PREFIX = "vt.";

    /**
     * Operation modes
     */
    public @interface Mode {
        long NORMAL     = 1L << 1;
        long SLEEP      = 1L << 2;
        long RECYCLE    = 1L << 3;
        long AUTO       = 1L << 4;
        long SAVING     = 1L << 5;
        long CLEANAIR   = 1L << 6;
        long INTERNAL   = 1L << 7;
    }

    /**
     * Sensors that are installed in device
     */
    public @interface Sensor {
        long CO2 = 1 << 0;
    }

    /**
     * Alarms that the device can notify
     */
    public @interface Alarm {
        long FAN_OVERHEATING    = 1L << 0; // Fan is overheated
        long RECYCLER_CHANGE    = 1L << 1; // Needs change of recycler
        long FILTER_CHANGE      = 1L << 2; // Needs change of filter
        long SMOKE_REMOVING     = 1L << 3; // Removing smoke is in progress
        long HIGH_CO2_LEVEL     = 1L << 4; // CO2 level is high
        long HEATER_RUNNING     = 1L << 5; // Heater is running
    }

    /**
     * Range of Fan Speed
     */
    public static class SpeedRange {
        /**
         * Lowest level of fan speed.
         * @see #getFanSpeedRange()
         * @see #setFanSpeed(int)
         */
        public int min;

        /**
         * Highest level of fan speed.
         * @see #getFanSpeedRange()
         * @see #setFanSpeed(int)
         */
        public int max;
    }

    /** Property: Bits of supported {@link Mode}. */
    @PropertyDef(valueClass=Mode.class, formatHint="bits")
    public static final String PROP_SUPPORTED_MODES     = PROP_PREFIX + "supported_modes";

    /** Property: Bits of supported {@link Sensor}. */
    @PropertyDef(valueClass=Sensor.class, formatHint="bits")
    public static final String PROP_SUPPORTED_SENSORS   = PROP_PREFIX + "supported_sensors";

    /** Property: Current operation mode. */
    @PropertyDef(valueClass=Mode.class)
    public static final String PROP_OPERATION_MODE      = PROP_PREFIX + "operation.mode";

    /** Property: Bits of {@link Alarm} that is indicating of each alarmed state. */
    @PropertyDef(valueClass=Alarm.class, formatHint="bits")
    public static final String PROP_OPERATION_ALARM     = PROP_PREFIX + "operation.alarm";

    /** Property: Current speed of fan */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_CUR_FAN_SPEED       = PROP_PREFIX + "fan_speed.current";

    /** Property: Minimum range of fan speed */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_MIN_FAN_SPEED       = PROP_PREFIX + "fan_speed.minimum";

    /** Property: Maximum range of fan speed */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_MAX_FAN_SPEED       = PROP_PREFIX + "fan_speed.maximum";

    /**
     * Construct new instance. Don't call this directly.
     */
    public Ventilation(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /** @hide */
    public @Type int getType() {
        return HomeDevice.Type.VENTILATION;
    }

    /**
     * Returns the support state for a operation mode.
     * @return {@code true} if given mode is supported.
     */
    public boolean isSupportedMode(@Mode long mode) {
        final long supportedModes = getProperty(PROP_SUPPORTED_MODES, Long.class);
        return (supportedModes & mode) != 0L;
    }

    /**
     * Get the list of supported operation modes.
     * @return {@code List<Long>} consist of {@link Mode}.
     */
    public List<Long> getSupportedModes() {
        final long supportedModes = getProperty(PROP_SUPPORTED_MODES, Long.class);

        List<Long> outModeList = new ArrayList<>();
        for (int i=0; i<Long.SIZE; i++) {
            long mode = (1 << i);
            if ((supportedModes & mode) != 0L) {
                outModeList.add(mode);
            }
        }

        return outModeList;
    }

    /**
     * Gets current operation mode.
     * @return One of {@link Mode}
     */
    public @Mode long getMode() {
        return getProperty(PROP_OPERATION_MODE, Long.class);
    }

    /**
     * Request new mode for operation to device. If the new mode is not
     * supported by device, it can be ignored or transited to other mode.
     * @see #getMode()
     * @param mode One of {@link Mode}
     */
    public void setMode(@Mode long mode) {
        setProperty(PROP_OPERATION_MODE, Long.class, mode);
    }

    /**
     * Check if given {@link Alarm} is detected or not.
     * @param alarm One of {@link Alarm}
     * @return {@code true} if the alarm is detected.
     */
    public boolean isAlarmed(@Alarm long alarm) {
        return (getProperty(PROP_OPERATION_ALARM, Long.class) & alarm) != 0L;
    }

    /**
     * Clear detected alarms. Only the bits of alarm that can be cleared may change.
     * @param alarms {@code long} value interpereted as {@link Alarm} bits.
     */
    public void clearAlarms(@Alarm long alarms) {
        final long curAlarms = (Long) getStagedProperty(PROP_OPERATION_ALARM, Long.class);
        final long newAlarms = (curAlarms & ~alarms); // Clear bits
        setProperty(PROP_OPERATION_ALARM, Long.class, newAlarms);
    }

    /**
     * Retrieves the range of fan speed.
     * @return {@link SpeedRange} object that contains lowest and hightest speed.
     */
    public SpeedRange getFanSpeedRange() {
        SpeedRange range = new SpeedRange();
        range.min = getProperty(PROP_MIN_FAN_SPEED, Integer.class);
        range.max = getProperty(PROP_MAX_FAN_SPEED, Integer.class);
        return range;
    }

    /**
     * Gets current fan speed.
     * @return The level of fan speed.
     */
    public int getFanSpeed() {
        return getProperty(PROP_CUR_FAN_SPEED, Integer.class);
    }

    /**
     * Sets new speed of fan that should be with in the range.
     * @param speed The level of fan speed.
     * @see #getFanSpeedRange()
     */
    public void setFanSpeed(int speed) {
        setProperty(PROP_CUR_FAN_SPEED, Integer.class, speed);
    }
}
