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
 * A device class for blocking standby or overload power.
 */
public class PowerSaver extends HomeDevice {
    private static final String PROP_PREFIX = "ps.";

    /**
     * States
     */
    public @interface State {
        long OVERLOAD_DETECTED = 1L << 0;
        long STANDBY_DETECTED = 1L << 1;
    }

    /**
     * Settings
     */
    public @interface Setting {
        long STANDBY_BLOCKING_ON = 1L << 0;
    }

    /** Property: Bit fields that is indicating wheter each {@link State} is supproted */
    @PropertyDef(valueClass=State.class, formatHint="bits")
    public static final String PROP_SUPPORTED_STATES    = PROP_PREFIX + "states.support";

    /** Property: Bit fields that is indicating each state is detected or not */
    @PropertyDef(valueClass=State.class, formatHint="bits")
    public static final String PROP_CURRENT_STATES      = PROP_PREFIX + "states.current";

    /** Property: Bit fields that is indicating wheter each {@link Setting} is supproted */
    @PropertyDef(valueClass=Setting.class, formatHint="bits")
    public static final String PROP_SUPPORTED_SETTINGS  = PROP_PREFIX + "settings.support";

    /** Property: Bit fields that is indicating each setting is set or not */
    @PropertyDef(valueClass=Setting.class, formatHint="bits")
    public static final String PROP_CURRENT_SETTINGS    = PROP_PREFIX + "settings.current";

    /** Property: The level (watt) of standby power consumption that has been set to device */
    @PropertyDef(valueClass=Float.class)
    public static final String PROP_STANDBY_CONSUMPTION = PROP_PREFIX + "consumption.standby";

    /** Property: The level (watt) of current power consumption that is retrieved from device */
    @PropertyDef(valueClass=Float.class)
    public static final String PROP_CURRENT_CONSUMPTION = PROP_PREFIX + "consumption.current";

    /**
     * Construct new instance. Don't call this directly.
     */
    public PowerSaver(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /** @hide */
    public @Type int getType() {
        return HomeDevice.Type.POWER_SAVER;
    }

    /**
     * Retrieves all the detecting states.
     * @return Bits of {@link State}.
     */
    public @State long getStates() {
        return getProperty(PROP_CURRENT_STATES, Long.class);
    }

}
