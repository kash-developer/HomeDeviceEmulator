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
 * A device class for controlling the level of temperature in the house.
 */
public class Thermostat extends HomeDevice {
    private static final String PROP_PREFIX = "ts.";

    /**
     * Functions
     */
    public @interface Function {
        long HEATING = 1L << 0;
        long COOLING = 1L << 1;
        long OUTING_SETTING = 1L << 2;
        long HOTWATER_ONLY = 1L << 3;
        long RESERVED_MODE = 1L << 4;
    }

    /**
     * Range of Temperature
     */
    public static class TempRange {
        /**
         * Resolution of temperatures, all the value of temperature is rounded by this.
         */
        public float res;

        /**
         * Minimum temperature, lowest value in setting a temperature
         */
        public float min;

        /**
         * Maximum temperature, highest value in setting a temperature
         */
        public float max;
    }

    /** Property: Bits for indicating each {@link Function} is supported or not */
    @PropertyDef(valueClass=Function.class, formatHint="bits")
    public static final String PROP_SUPPORTED_FUNCTIONS     = PROP_PREFIX + "function.supports";

    /** Property: Bits for indicating each {@link Function} is activated or not */
    @PropertyDef(valueClass=Function.class, formatHint="bits")
    public static final String PROP_FUNCTION_STATES         = PROP_PREFIX + "function.states";

    /** Property: Minimum level of temperature */
    @PropertyDef(valueClass=Float.class)
    public static final String PROP_MIN_TEMPERATURE         = PROP_PREFIX + "temperature.min";

    /** Property: Maximum level of temperature */
    @PropertyDef(valueClass=Float.class)
    public static final String PROP_MAX_TEMPERATURE         = PROP_PREFIX + "temperature.max";

    /** Property: Resolution value of temperature (1.0, 0.5, 0.1 or ...) */
    @PropertyDef(valueClass=Float.class, defValueF=1.0f)
    public static final String PROP_TEMP_RESOLUTION         = PROP_PREFIX + "temperature.resolution";

    /** Property: Temperature that has been set to device */
    @PropertyDef(valueClass=Float.class)
    public static final String PROP_SETTING_TEMPERATURE     = PROP_PREFIX + "temperature.setting";

    /** Property: Current temperature */
    @PropertyDef(valueClass=Float.class)
    public static final String PROP_CURRENT_TEMPERATURE     = PROP_PREFIX + "temperature.current";

    /**
     * Construct new instance. Don't call this directly.
     */
    public Thermostat(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /** @hide */
    public @Type int getType() {
        return HomeDevice.Type.THERMOSTAT;
    }

    /**
     * Whether if given function is supported.
     * @param function One of {@link Function}
     * @return {@code true} if the function is supported.
     */
    public boolean hasFunction(long function) {
        return (getProperty(PROP_SUPPORTED_FUNCTIONS, Long.class) & function) != 0L;
    }

    /**
     * Gets the working state of a function.
     * @param function One of {@link Function}
     * @return Whether if the function is working.
     */
    public boolean getFunctionState(@Function long function) {
        return (getFunctionStates() & function) != 0L;
    }

    /**
     * Retrieves all the states of functions.
     * @return Bits of {@link Function} that is indicating each working or not.
     */
    public long getFunctionStates() {
        return getProperty(PROP_FUNCTION_STATES, Long.class);
    }

    /**
     * Sets working states for multiple functions.
     * @param funcs Bits of {@link Function} that should be changed
     * @param set {@code true} to set.
     */
    public void setFunctionStates(@Function long funcs, boolean set) {
        final long curFuncs = (Long) getStagedProperty(PROP_FUNCTION_STATES, Long.class);
        final long newFuncs = (set) ? (curFuncs | funcs) : (curFuncs & ~funcs);
        setProperty(PROP_FUNCTION_STATES, Long.class, newFuncs);
    }

    /**
     * Returns structure object that contains range factors of temperature.
     * @return {@link TempRange} object.
     */
    public TempRange getTemperatureRange() {
        TempRange range = new TempRange();
        range.res = getProperty(PROP_TEMP_RESOLUTION, Float.class);
        range.min = getProperty(PROP_MIN_TEMPERATURE, Float.class);
        range.max = getProperty(PROP_MAX_TEMPERATURE, Float.class);
        return range;
    }

    /**
     * Get current temperature that is measured from device.
     * @see #getTemperatureRange()
     * @return {@code float} value as temperature degree.
     */
    public float getCurrentTemperature() {
        return getProperty(PROP_CURRENT_TEMPERATURE, Float.class);
    }

    /**
     * Get the temperature that has been requested to set on device.
     * @see #getTemperatureRange()
     * @return {@code float} value as temperature degree.
     */
    public float getSettingTemperature() {
        return getProperty(PROP_SETTING_TEMPERATURE, Float.class);
    }

    /**
     * Request new temperature to be set to the device.
     * @param degree {@code float} value as temperature degree.
     */
    public void setTemperature(float degree) {
        setProperty(PROP_SETTING_TEMPERATURE, Float.class, degree);
    }
}
