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

package kr.or.kashi.hde.ksx4506_kd;

import android.content.Context;
import android.util.Log;

import kr.or.kashi.hde.device.*;
import kr.or.kashi.hde.ksx4506.KDThermostat;
import kr.or.kashi.hde.ksx4506_ex.KSMainContext2;

/**
 * Extended implementation of [KS X 4506] main context
 */
public class KDMainContext extends KSMainContext2 {
    private static final String TAG = "KDMainContext";
    private static final boolean DBG = true;

    public KDMainContext(Context context, boolean isSlaveMode) {
        super(context, isSlaveMode);

        // Override super's map with extended classes.
        mAddressToClassMap.put(0x36, KDThermostat.class);
    }
}
