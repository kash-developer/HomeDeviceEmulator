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

package kr.or.kashi.hde.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.R;

public class DeviceInfoView extends LinearLayout {
    private static final String TAG = DeviceInfoView.class.getSimpleName();
    private final Context mContext;

    private TextView mDeviceTypeText;
    private TextView mDeviceIdText;
    private TextView mDeviceSubIdText;

    public DeviceInfoView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mDeviceTypeText = findViewById(R.id.device_type_text);
        mDeviceIdText = findViewById(R.id.device_id_text);
        mDeviceSubIdText = findViewById(R.id.device_sub_id_text);
    }

    public void setDevice(HomeDevice device) {
        if (device != null) {
            mDeviceTypeText.setText("[" + HomeDevice.typeToString(device.getType()) + "]");
            mDeviceIdText.setText(device.getAddress().substring(2, 4));
            mDeviceSubIdText.setText(device.getAddress().substring(4, 6));
            setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.INVISIBLE);
        }
    }
}
