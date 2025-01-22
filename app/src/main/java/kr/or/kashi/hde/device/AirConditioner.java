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

    /** Property: Bit flags of supported operation of {@link OpMode} */
    @PropertyDef(valueClass=OpMode.class, formatHint="bits")
    public static final String PROP_SUPPORTED_MODES = PROP_PREFIX + "operation_mode.supports";

    /** Property: Current operation mode of {@link OpMode} */
    @PropertyDef(valueClass=OpMode.class, defValueI=OpMode.COOLING)
    public static final String PROP_OPERATION_MODE  = PROP_PREFIX + "operation_mode.current";

    /** Property: Direction {@link FlowDir} that air blows to */
    @PropertyDef(valueClass=FlowDir.class, defValueI=FlowDir.MANUAL)
    public static final String PROP_FLOW_DIRECTION  = PROP_PREFIX + "flow_direction";

    /** Property: One of {@link FanMode} is set to this */
    @PropertyDef(valueClass=FanMode.class, defValueI=FanMode.MANUAL)
    public static final String PROP_FAN_MODE        = PROP_PREFIX + "fan_mode";

    /** Property: Minimum level of fan speed */
    @PropertyDef(valueClass=Integer.class, defValueI=1)
    public static final String PROP_MIN_FAN_SPEED   = PROP_PREFIX + "fan_speed.minimum";

    /** Property: Maximum level of fan speed */
    @PropertyDef(valueClass=Integer.class, defValueI=5)
    public static final String PROP_MAX_FAN_SPEED   = PROP_PREFIX + "fan_speed.maximum";

    /** Property: Current level of fan speed */
    @PropertyDef(valueClass=Integer.class, defValueI=1)
    public static final String PROP_CUR_FAN_SPEED   = PROP_PREFIX + "fan_speed.current";

    /** Property: Resolution of temperature (defaut: 1.0) */
    @PropertyDef(valueClass=Float.class, defValueF=0.5f)
    public static final String PROP_TEMP_RESOLUTION = PROP_PREFIX + "temperature.resolution";

    /** Property: Lowest temperature */
    @PropertyDef(valueClass=Float.class, defValueF=5.0f)
    public static final String PROP_MIN_TEMPERATURE = PROP_PREFIX + "temperature.minimum";

    /** Property: Highest temperature */
    @PropertyDef(valueClass=Float.class, defValueF=30.0f)
    public static final String PROP_MAX_TEMPERATURE = PROP_PREFIX + "temperature.maximum";

    /** Property: Last temperature that is requested */
    @PropertyDef(valueClass=Float.class, defValueF=18.5f)
    public static final String PROP_REQ_TEMPERATURE = PROP_PREFIX + "temperature.request";

    /** Property: Current temperature that has been retrieved from device */
    @PropertyDef(valueClass=Float.class, defValueF=18.5f)
    public static final String PROP_CUR_TEMPERATURE = PROP_PREFIX + "temperature.current";

    /**
     * Construct new instance. Don't call this directly.
     */
    public AirConditioner(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /** @hide */
    public @Type int getType() {
        return Type.AIRCONDITIONER;
    }

}
