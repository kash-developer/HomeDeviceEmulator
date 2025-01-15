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

package kr.or.kashi.hde;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import kr.or.kashi.hde.view.DebugLogPartView;
import kr.or.kashi.hde.view.ControlsPartView;
import kr.or.kashi.hde.view.DeviceListPartView;
import kr.or.kashi.hde.view.AutoTestPartView;
import kr.or.kashi.hde.view.TypeListPartView;

public class MainFragment extends Fragment {
    private static final String TAG = "HomeTestFragment";

    private final Context mContext;
    private final HomeNetwork mNetwork;

    private DebugLogPartView mDebugLogPart;
    private TypeListPartView mTypeListPart;
    private DeviceListPartView mDeviceListPart;
    private ControlsPartView mControlsPart;
    private AutoTestPartView mAutoTestPart;

    public MainFragment(Context context, HomeNetwork network) {
        mContext = context;
        mNetwork = network;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {
        final View v = inflater.inflate(R.layout.main, container, false);

        mDebugLogPart = v.findViewById(R.id.device_log_part);
        mTypeListPart = v.findViewById(R.id.type_list_part);
        mDeviceListPart = v.findViewById(R.id.device_list_part);
        mControlsPart = v.findViewById(R.id.device_detail_part);
        mAutoTestPart = v.findViewById(R.id.device_test_part);

        mDebugLogPart.init();
        mTypeListPart.init(mNetwork);
        mAutoTestPart.init();
        mControlsPart.init();
        mDeviceListPart.init(mNetwork, mControlsPart, mAutoTestPart);

        return v;
    }
}
