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
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.annotation.Nullable;

import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.widget.HomeDeviceView;

public class BatchSwitchView extends HomeDeviceView<BatchSwitch> {
    private static final String TAG = BatchSwitchView.class.getSimpleName();
    private final Context mContext;

    private Button mApplySwitchesButton;
    private CheckBox mGasLockingCheck;
    private CheckBox mOutingSettingCheck;
    private CheckBox mBatchLightOffCheck;
    private CheckBox mPowerSavingCheck;
    private CheckBox mElevatorUpCallCheck;
    private CheckBox mElevatorDownCallCheck;

    private boolean mGasLockingReqTriggered = false;
    private boolean mOutingSettingReqTriggered = false;
    private boolean mElevatorUpCallReqTriggered = false;
    private boolean mElevatorDownCallReqTriggered = false;

    public BatchSwitchView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate () {
        super.onFinishInflate();
        mApplySwitchesButton = findViewById(R.id.apply_switches_button);
        mApplySwitchesButton.setOnClickListener(v -> applySwitches());
        mGasLockingCheck = findViewById(R.id.gas_locking_check);
        mOutingSettingCheck = findViewById(R.id.outing_setting_check);
        mBatchLightOffCheck = findViewById(R.id.batch_light_off_check);
        mPowerSavingCheck = findViewById(R.id.power_saving_check);
        mElevatorUpCallCheck = findViewById(R.id.elevator_up_call_check);
        mElevatorDownCallCheck = findViewById(R.id.elevator_down_call_check);
    }

    @Override
    public void onUpdateProperty(PropertyMap props, PropertyMap changed) {
        final long supports = props.get(BatchSwitch.PROP_SUPPORTED_SWITCHES, Long.class);
        mGasLockingCheck.setEnabled((supports & BatchSwitch.Switch.GAS_LOCKING) != 0);
        mOutingSettingCheck.setEnabled((supports & BatchSwitch.Switch.OUTING_SETTING) != 0);
        mBatchLightOffCheck.setEnabled((supports & BatchSwitch.Switch.BATCH_LIGHT_OFF) != 0);
        mPowerSavingCheck.setEnabled((supports & BatchSwitch.Switch.POWER_SAVING) != 0);
        mElevatorUpCallCheck.setEnabled((supports & BatchSwitch.Switch.ELEVATOR_UP_CALL) != 0);
        mElevatorDownCallCheck.setEnabled((supports & BatchSwitch.Switch.ELEVATOR_DOWN_CALL) != 0);

        final long curStates = props.get(BatchSwitch.PROP_SWITCH_STATES, Long.class);
        mGasLockingCheck.setChecked((curStates & BatchSwitch.Switch.GAS_LOCKING) != 0);
        mOutingSettingCheck.setChecked((curStates & BatchSwitch.Switch.OUTING_SETTING) != 0);
        mBatchLightOffCheck.setChecked((curStates & BatchSwitch.Switch.BATCH_LIGHT_OFF) != 0);
        mPowerSavingCheck.setChecked((curStates & BatchSwitch.Switch.POWER_SAVING) != 0);
        mElevatorUpCallCheck.setChecked((curStates & BatchSwitch.Switch.ELEVATOR_UP_CALL) != 0);
        mElevatorDownCallCheck.setChecked((curStates & BatchSwitch.Switch.ELEVATOR_DOWN_CALL) != 0);
    }

    private void applySwitches() {
        long states = 0;
        if (mGasLockingCheck.isChecked()) states |= BatchSwitch.Switch.GAS_LOCKING;
        if (mOutingSettingCheck.isChecked()) states |= BatchSwitch.Switch.OUTING_SETTING;
        if (mBatchLightOffCheck.isChecked()) states |= BatchSwitch.Switch.BATCH_LIGHT_OFF;
        if (mPowerSavingCheck.isChecked()) states |= BatchSwitch.Switch.POWER_SAVING;
        if (mElevatorUpCallCheck.isChecked()) states |= BatchSwitch.Switch.ELEVATOR_UP_CALL;
        if (mElevatorDownCallCheck.isChecked()) states |= BatchSwitch.Switch.ELEVATOR_DOWN_CALL;
        device().setProperty(BatchSwitch.PROP_SWITCH_STATES, Long.class,  states);
    }
}
