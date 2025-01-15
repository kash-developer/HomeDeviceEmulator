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

package kr.or.kashi.hde.view;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.R;
import kr.or.kashi.hde.test.HomeDeviceTestCallback;

public class ControlsPartView extends LinearLayout implements HomeDeviceTestCallback {
    private static final String TAG = ControlsPartView.class.getSimpleName();
    private final Context mContext;
    private final Handler mHandler;

    private DeviceInfoView mDeviceInfoView;
    private ViewGroup mDeviceControlArea;

    public ControlsPartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());

    }

    public void init() {
        mDeviceInfoView = findViewById(R.id.device_info_view);
        mDeviceControlArea = findViewById(R.id.device_control_area);
    }

    public void setDevice(HomeDevice device) {
        mDeviceControlArea.removeAllViews();
        mDeviceInfoView.setDevice(device);

        if (device == null) {
            return;
        }

        int resId = getDeviceControlViewResId(device);
        if (resId == 0) {
            return;
        }

        HomeDeviceView deviceView = (HomeDeviceView) LayoutInflater.from(mContext)
                .inflate(resId, mDeviceControlArea, false);

        deviceView.setDevice(device);

        mDeviceControlArea.addView(deviceView);
    }

    private int getDeviceControlViewResId(HomeDevice device) {
        switch(device.getType()) {
            case HomeDevice.Type.LIGHT: return R.layout.controls_light;
            case HomeDevice.Type.DOOR_LOCK: return R.layout.controls_door_lock;
            case HomeDevice.Type.VENTILATION: return R.layout.controls_ventilation;
            case HomeDevice.Type.GAS_VALVE: return R.layout.controls_gas_valve;
            case HomeDevice.Type.HOUSE_METER: return R.layout.controls_house_meter;
            case HomeDevice.Type.CURTAIN: return R.layout.controls_curtain;
            case HomeDevice.Type.THERMOSTAT: return R.layout.controls_thermostat;
            case HomeDevice.Type.BATCH_SWITCH: return R.layout.controls_batch_switch;
            case HomeDevice.Type.SENSOR: return R.layout.controls_binary_sensors;
            case HomeDevice.Type.AIRCONDITIONER: return R.layout.controls_air_conditioner;
            case HomeDevice.Type.POWER_SAVER: return R.layout.controls_power_saver;
        }
        return 0;
    }
}
