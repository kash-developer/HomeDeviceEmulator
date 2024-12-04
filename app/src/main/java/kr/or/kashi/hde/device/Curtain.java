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
    @PropertyDef(valueClass=Integer.class, defValueI=10)
    public static final String PROP_MAX_OPEN_LEVEL  = PROP_PREFIX + "open_level.max";

    /** Property for current level of opening */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_CUR_OPEN_LEVEL  = PROP_PREFIX + "open_level.cur";

    /** Property for the minimum angle of opening */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_MIN_OPEN_ANGLE  = PROP_PREFIX + "open_angle.min";

    /** Property for the maximum angle of opening */
    @PropertyDef(valueClass=Integer.class, defValueI=10)
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

}
