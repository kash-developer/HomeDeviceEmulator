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

}
