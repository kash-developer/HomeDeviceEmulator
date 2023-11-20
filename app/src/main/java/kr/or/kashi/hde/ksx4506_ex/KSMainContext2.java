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

package kr.or.kashi.hde.ksx4506_ex;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.*;
import kr.or.kashi.hde.ksx4506.KSMainContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Extended implementation of [KS X 4506] main context
 */
public class KSMainContext2 extends KSMainContext {
    private static final String TAG = "KSMainContext2";
    private static final boolean DBG = true;

    public KSMainContext2(Context context) {
        super(context);

        // Override super's map with extended classes.
        mAddressToClassMap.put(0x33, KSBatchSwitch2.class);
        mAddressToClassMap.put(0x12, KSGasValve2.class);
        mAddressToClassMap.put(0x30, KSHouseMeter2.class);
        mAddressToClassMap.put(0x0E, KSLight2.class);
        mAddressToClassMap.put(0x32, KSVentilation2.class);
    }
}
