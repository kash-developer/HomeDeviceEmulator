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
 * A device class for controlling the gas valve.
 */
public class GasValve extends HomeDevice {
    private static final String PROP_PREFIX = "gv.";

    /**
     * States
     */
    public @interface State {
        long GAS_VALVE      = 1L << 0;
        long INDUCTION      = 1L << 1;
    }

    /**
     * Alarms
     */
    public @interface Alarm {
        long EXTINGUISHER_BUZZING   = 1L << 1;
        long GAS_LEAKAGE_DETECTED   = 1L << 2;
    }

    /** Property of bits of supported {@link State}s. */
    @PropertyDef(valueClass=State.class, formatHint="bits")
    public static final String PROP_SUPPORTED_STATES    = PROP_PREFIX + "supported_states";

    /** Property of bits that is indicating which states are activated or not */
    @PropertyDef(valueClass=State.class, formatHint="bits")
    public static final String PROP_CURRENT_STATES      = PROP_PREFIX + "current_states";

    /** Property of bits of supported {@link Alarm}s. */
    @PropertyDef(valueClass=Alarm.class, formatHint="bits")
    public static final String PROP_SUPPORTED_ALARMS    = PROP_PREFIX + "supported_alarms";

    /** Property of bits that is indicating which alarms are triggered or not */
    @PropertyDef(valueClass=State.class, formatHint="bits")
    public static final String PROP_CURRENT_ALARMS      = PROP_PREFIX + "current_alarms";

    /**
     * Construct new instance. Don't call this directly.
     */
    public GasValve(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /** @hide */
    public @Type int getType() {
        return HomeDevice.Type.GAS_VALVE;
    }

}
