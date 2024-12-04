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
 * Door-lock device class.
 */
public class DoorLock extends HomeDevice {
    private static final String PROP_PREFIX = "dl.";

    /**
     * States of the door lock
     */
    public @interface State {
        long DOOR_OPENED = 1L << 0;
        long EMERGENCY_ALARMED = 1L << 1;
    }

    /** Property: Bits of {@link State} to indicate whether a state is supported */
    @PropertyDef(valueClass=State.class, formatHint="bits")
    public static final String PROP_SUPPORTED_STATES    = PROP_PREFIX + "supported_states";

    /** Property: Bits of {@link State} to indicate whether a state is activated or not */
    @PropertyDef(valueClass=State.class, formatHint="bits")
    public static final String PROP_CURRENT_STATES      = PROP_PREFIX + "current_states";

    /**
     * Construct new instance. Don't call this directly.
     */
    public DoorLock(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /** @hide */
    public @Type int getType() {
        return HomeDevice.Type.DOOR_LOCK;
    }

    /**
     * Returns all states of activation.
     * @return Bits of {@link State} as {@code long}.
     */
    public @State long getStates() {
        return getPropertyL(PROP_CURRENT_STATES);
    }

}
