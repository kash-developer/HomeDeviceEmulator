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

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import kr.or.kashi.hde.widget.HomeDeviceView;

public class GasValveView extends HomeDeviceView<GasValve> {
    private static final String TAG = GasValveView.class.getSimpleName();
    private final Context mContext;

    public GasValveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

    }

    @Override
    protected void onFinishInflate () {
        super.onFinishInflate();

    }

}
