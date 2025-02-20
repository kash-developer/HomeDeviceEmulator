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
 * A device class for probing meters in the house.
 */
public class HouseMeter extends HomeDevice {
    private static final String PROP_PREFIX = "hm.";

    /**
     * Types of meter
     */
    public @interface MeterType {
        int UNKNOWN     = 0;
        int WATER       = 1;
        int GAS         = 2;
        int ELECTRICITY = 3;
        int HOT_WATER   = 4;
        int HEATING     = 5;
    }

    /**
     * Units of measurement
     */
    public @interface MeasureUnit {
        int UNKNOWN     = 0;
        int m3          = 1;
        int W           = 2;
        int MW          = 3;
        int kWh         = 4;
    }

    /** Property: Indicating what type of device */
    @PropertyDef(valueClass=MeterType.class)
    public static final String PROP_METER_TYPE          = PROP_PREFIX + "meter_type";

    /** Property: The enabled state of meter */
    @PropertyDef(valueClass=Boolean.class)
    public static final String PROP_METER_ENABLED       = PROP_PREFIX + "meter_enabled";

    /** Property: Unit for current measurement */
    @PropertyDef(valueClass=MeasureUnit.class)
    public static final String PROP_CURRENT_METER_UNIT  = PROP_PREFIX + "current_meter.unit";

    /** Property: Current measurement value*/
    @PropertyDef(valueClass=Double.class)
    public static final String PROP_CURRENT_METER_VALUE = PROP_PREFIX + "current_meter.value";

    /** Property: Unit for total measurement */
    @PropertyDef(valueClass=MeasureUnit.class)
    public static final String PROP_TOTAL_METER_UNIT    = PROP_PREFIX + "total_meter.unit";

    /** Property: Total measurement value */
    @PropertyDef(valueClass=Double.class)
    public static final String PROP_TOTAL_METER_VALUE   = PROP_PREFIX + "total_meter.value";

    /**
     * Construct new instance. Don't call this directly.
     */
    public HouseMeter(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /** @hide */
    public @Type int getType() {
        return HomeDevice.Type.HOUSE_METER;
    }

}
