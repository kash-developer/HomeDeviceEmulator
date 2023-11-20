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

import android.os.Handler;

import kr.or.kashi.hde.base.PropertyDef;
import kr.or.kashi.hde.DeviceContextBase;
import kr.or.kashi.hde.HomeDevice;

/**
 * A device class of air-conditioner
 */
public class AirConditioner extends HomeDevice {
    private static final String PROP_PREFIX = "ac.";

    /**
     * Modes
     */
    public @interface OpMode {
        int AUTO        = 1 << 1;
        int COOLING     = 1 << 2;
        int HEATING     = 1 << 3;
        int BLOWING     = 1 << 4;  // Fan only
        int DEHUMID     = 1 << 5;  // Dehumidifying
        int RESERVED    = 1 << 6;
    }

    public @interface FlowDir {
        int MANUAL      = 0;        // Depending on a user setting or fixed
        int AUTO        = 1;
    }

    public @interface FanMode {
        int MANUAL      = 0;        // Depending on fan speed
        int AUTO        = 1;
        int NATURAL     = 2;
    }

    public static class SpeedRange {
        public int min;
        public int max;
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
         * Minimum temperature, lowest value for {@link #requestTemperature(float)}
         */
        public float min;

        /**
         * Maximum temperature, highest value for {@link #requestTemperature(float)}
         */
        public float max;
    }

    /** Property: Bit flags of supported operation of {@link OpMode} */
    @PropertyDef(valueClass=OpMode.class, formatHint="bits")
    public static final String PROP_SUPPORTED_MODES = PROP_PREFIX + "operation_mode.supports";

    /** Property: Current operation mode of {@link OpMode} */
    @PropertyDef(valueClass=OpMode.class)
    public static final String PROP_OPERATION_MODE  = PROP_PREFIX + "operation_mode.current";

    /** Property: Direction {@link FlowDir} that air blows to */
    @PropertyDef(valueClass=FlowDir.class)
    public static final String PROP_FLOW_DIRECTION  = PROP_PREFIX + "flow_direction";

    /** Property: One of {@link FanMode} is set to this */
    @PropertyDef(valueClass=FanMode.class)
    public static final String PROP_FAN_MODE        = PROP_PREFIX + "fan_mode";

    /** Property: Minimum level of fan speed */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_MIN_FAN_SPEED   = PROP_PREFIX + "fan_speed.minimum";

    /** Property: Maximum level of fan speed */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_MAX_FAN_SPEED   = PROP_PREFIX + "fan_speed.maximum";

    /** Property: Current level of fan speed */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_CUR_FAN_SPEED   = PROP_PREFIX + "fan_speed.current";

    /** Property: Resolution of temperature (defaut: 1.0) */
    @PropertyDef(valueClass=Float.class, defValueF=1.0f)
    public static final String PROP_TEMP_RESOLUTION = PROP_PREFIX + "temperature.resolution";

    /** Property: Lowest temperature */
    @PropertyDef(valueClass=Float.class)
    public static final String PROP_MIN_TEMPERATURE = PROP_PREFIX + "temperature.minimum";

    /** Property: Highest temperature */
    @PropertyDef(valueClass=Float.class)
    public static final String PROP_MAX_TEMPERATURE = PROP_PREFIX + "temperature.maximum";

    /** Property: Last temperature that is requested */
    @PropertyDef(valueClass=Float.class)
    public static final String PROP_REQ_TEMPERATURE = PROP_PREFIX + "temperature.request";

    /** Property: Current temperature that has been retrieved from device */
    @PropertyDef(valueClass=Float.class)
    public static final String PROP_CUR_TEMPERATURE = PROP_PREFIX + "temperature.current";

    /**
     * Construct new instance. Don't call this directly.
     */
    public AirConditioner(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /**
     * Returns the support state for a operation mode.
     * @return {@code true} if given mode is supported.
     */
    public boolean isSupportedOperation(@OpMode int mode) {
        return (getProperty(PROP_SUPPORTED_MODES, Integer.class) & mode) != 0L;
    }

    /**
     * Gets current operation mode.
     * @return One of value in {@link OpMode}.
     */
    public @OpMode int getOperationMode() {
        return getProperty(PROP_OPERATION_MODE, Integer.class);
    }

    /**
     * Sets new operation mode.
     * @param mode One of value in {@link OpMode}.
     */
    public void setOperationMode(@OpMode int mode) {
        setProperty(PROP_OPERATION_MODE, Integer.class, mode);
    }

    /**
     * Retrives the {@link FlowDir} that is currently set in device.
     * @return The air-flow direction.
     */
    public @FlowDir int getFlowDirection() {
        return getProperty(PROP_FLOW_DIRECTION, Integer.class);
    }

    /**
     * Sets value to control the direction of air-flow.
     * @param dir One of {@link FlowDir}.
     */
    public void setFlowDirection(@FlowDir int dir) {
        setProperty(PROP_FLOW_DIRECTION, Integer.class, dir);
    }

    /**
     * Retrieve current fan mode.
     * @return One of {@link FanMode}.
     */
    public @FanMode int getFanMode() {
        return getProperty(PROP_FAN_MODE, Integer.class);
    }

    /**
     * Change the fan mode.
     * @param mode One of {@link FanMode}
     */
    public void setFanMode(@FanMode int mode) {
        setProperty(PROP_FAN_MODE, Integer.class, mode);
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

    /**
     * Returns a object that has the range of temperature
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
     * Gets current temperature that recently updated from device.
     * @return Current temperature as floating value.
     */
    public float getCurrentTemperature() {
        return getProperty(PROP_CUR_TEMPERATURE, Float.class);
    }

    /**
     * Gets last temperature that user requested.
     * @return Requested temperature as floating value.
     */
    public float getRequestedTemperature() {
        return getProperty(PROP_REQ_TEMPERATURE, Float.class);
    }

    /**
     * Request setting temperature to device. the value should be within the
     * {@link TempRange} that can get by calling {@link #getTemperatureRange},
     * otherwise it will be adjested to lowest or highest within the range
     * @param temp Temperature value
     * @see #getTemperatureRange()
     */
    public void requestTemperature(float temp) {
        setProperty(PROP_REQ_TEMPERATURE, Float.class, temp);
    }
}
