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
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.Nullable;

import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.base.PropertyValue;
import kr.or.kashi.hde.view.HomeDeviceView;

public class PowerSaverView extends HomeDeviceView<PowerSaver> implements View.OnClickListener {
    private static final String TAG = PowerSaverView.class.getSimpleName();
    private final Context mContext;

    private CheckBox mStateCheck;
    private CheckBox mOverloadDetectedCheck;
    private CheckBox mStandbyDetectedCheck;
    private CheckBox mSettingCheck;
    private RadioButton mStandbyBlockingOffRadio;
    private RadioButton mStandbyBlockingOnRadio;
    private CheckBox mCurrentPowerCheck;
    private TextView mCurrentPowerText;
    private EditText mCurrentPowerEdit;
    private Button mCurrentPowerSetButton;
    private Button mCurrentPowerPlusButton;
    private Button mCurrentPowerMinusButton;
    private CheckBox mStandbyPowerCheck;
    private TextView mStandbyPowerText;
    private EditText mStandbyPowerEdit;
    private Button mStandbyPowerSetButton;
    private Button mStandbyPowerPlusButton;
    private Button mStandbyPowerMinusButton;

    public PowerSaverView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate () {
        super.onFinishInflate();

        mStateCheck = findViewById(R.id.state_check);
        mStateCheck.setEnabled(true);
        mStateCheck.setClickable(false);
        mOverloadDetectedCheck = findViewById(R.id.overload_detected_check);
        if (isMaster()) mOverloadDetectedCheck.setClickable(false);
        if (isSlave()) mOverloadDetectedCheck.setOnClickListener(this);
        mStandbyDetectedCheck = findViewById(R.id.standby_detected_check);
        if (isMaster()) mStandbyDetectedCheck.setClickable(false);
        if (isSlave()) mStandbyDetectedCheck.setOnClickListener(this);
        mSettingCheck = findViewById(R.id.setting_check);
        mStandbyBlockingOffRadio = findViewById(R.id.standby_blocking_off_radio);
        mStandbyBlockingOffRadio.setOnClickListener(this);
        mStandbyBlockingOnRadio = findViewById(R.id.standby_blocking_on_radio);
        mStandbyBlockingOnRadio.setOnClickListener(this);
        mCurrentPowerCheck = findViewById(R.id.current_power_check);
        mCurrentPowerText = findViewById(R.id.current_power_text);
        mCurrentPowerEdit = findViewById(R.id.current_power_edit);
        mCurrentPowerEdit.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mCurrentPowerSetButton = findViewById(R.id.current_power_set_button);
        mCurrentPowerSetButton.setOnClickListener(this);
        mCurrentPowerSetButton.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mCurrentPowerPlusButton = findViewById(R.id.current_power_plus_button);
        mCurrentPowerPlusButton.setOnClickListener(this);
        mCurrentPowerPlusButton.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mCurrentPowerMinusButton = findViewById(R.id.current_power_minus_button);
        mCurrentPowerMinusButton.setOnClickListener(this);
        mCurrentPowerMinusButton.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mStandbyPowerCheck = findViewById(R.id.standby_power_check);
        mStandbyPowerText = findViewById(R.id.standby_power_text);
        mStandbyPowerEdit = findViewById(R.id.standby_power_edit);
        mStandbyPowerEdit.setVisibility(isMaster() ? View.VISIBLE : View.GONE);
        mStandbyPowerSetButton = findViewById(R.id.standby_power_set_button);
        mStandbyPowerSetButton.setOnClickListener(this);
        mStandbyPowerSetButton.setVisibility(isMaster() ? View.VISIBLE : View.GONE);
        mStandbyPowerPlusButton = findViewById(R.id.standby_power_plus_button);
        mStandbyPowerPlusButton.setOnClickListener(this);
        mStandbyPowerPlusButton.setVisibility(isMaster() ? View.VISIBLE : View.GONE);
        mStandbyPowerMinusButton = findViewById(R.id.standby_power_minus_button);
        mStandbyPowerMinusButton.setOnClickListener(this);
        mStandbyPowerMinusButton.setVisibility(isMaster() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onUpdateProperty(PropertyMap props, PropertyMap changed) {
        final long supportedStates = props.get(PowerSaver.PROP_SUPPORTED_STATES, Long.class);
        mOverloadDetectedCheck.setEnabled((supportedStates & PowerSaver.State.OVERLOAD_DETECTED) != 0);
        mStandbyDetectedCheck.setEnabled((supportedStates & PowerSaver.State.STANDBY_DETECTED) != 0);

        final long currentStates = props.get(PowerSaver.PROP_CURRENT_STATES, Long.class);
        mOverloadDetectedCheck.setChecked((currentStates & PowerSaver.State.OVERLOAD_DETECTED) != 0);
        mStandbyDetectedCheck.setChecked((currentStates & PowerSaver.State.STANDBY_DETECTED) != 0);

        final long supportedSettings = props.get(PowerSaver.PROP_SUPPORTED_SETTINGS, Long.class);
        mStandbyBlockingOffRadio.setEnabled((supportedSettings & PowerSaver.Setting.STANDBY_BLOCKING_ON) != 0);
        mStandbyBlockingOnRadio.setEnabled((supportedSettings & PowerSaver.Setting.STANDBY_BLOCKING_ON) != 0);

        final long currentSettings = props.get(PowerSaver.PROP_CURRENT_SETTINGS, Long.class);
        mStandbyBlockingOffRadio.setChecked((currentSettings & PowerSaver.Setting.STANDBY_BLOCKING_ON) == 0);
        mStandbyBlockingOnRadio.setChecked((currentSettings & PowerSaver.Setting.STANDBY_BLOCKING_ON) != 0);

        final float currentConsumption = props.get(PowerSaver.PROP_CURRENT_CONSUMPTION, Float.class);
        mCurrentPowerText.setText("" + currentConsumption);
        if (!mCurrentPowerEdit.hasFocus()) mCurrentPowerEdit.setText("" + currentConsumption);

        final float standbyConsumption = props.get(PowerSaver.PROP_STANDBY_CONSUMPTION, Float.class);
        mStandbyPowerText.setText("" + standbyConsumption);
        if (!mStandbyPowerEdit.hasFocus()) mStandbyPowerEdit.setText("" + standbyConsumption);
    }

    @Override
    public void onClick(View v) {
        if (v == mOverloadDetectedCheck) {
            long states = device().getProperty(PowerSaver.PROP_CURRENT_STATES, Long.class);
            if (mOverloadDetectedCheck.isChecked()) states |= PowerSaver.State.OVERLOAD_DETECTED;
            else states &= ~PowerSaver.State.OVERLOAD_DETECTED;
            device().setProperty(PowerSaver.PROP_CURRENT_STATES, Long.class, states);
        } else if (v == mStandbyDetectedCheck) {
            long states = device().getProperty(PowerSaver.PROP_CURRENT_STATES, Long.class);
            if (mStandbyDetectedCheck.isChecked()) states |= PowerSaver.State.STANDBY_DETECTED;
            else states &= ~PowerSaver.State.STANDBY_DETECTED;
            device().setProperty(PowerSaver.PROP_CURRENT_STATES, Long.class, states);
        }

        if (v == mStandbyBlockingOffRadio) {
            final long currentSettings = device().getProperty(PowerSaver.PROP_CURRENT_SETTINGS, Long.class);
            device().setProperty(PowerSaver.PROP_CURRENT_SETTINGS, Long.class, currentSettings & ~PowerSaver.Setting.STANDBY_BLOCKING_ON);
        } else if (v == mStandbyBlockingOnRadio) {
            final long currentSettings = device().getProperty(PowerSaver.PROP_CURRENT_SETTINGS, Long.class);
            device().setProperty(PowerSaver.PROP_CURRENT_SETTINGS, Long.class, currentSettings | PowerSaver.Setting.STANDBY_BLOCKING_ON);
        } else if (v == mCurrentPowerSetButton) {
            final String editStr = mCurrentPowerEdit.getText().toString();
            float currentConsumption = PropertyValue.newValueObject(Float.class, editStr);
            device().setProperty(PowerSaver.PROP_CURRENT_CONSUMPTION, Float.class, currentConsumption);
        } else if (v == mCurrentPowerPlusButton) {
            final String editStr = mCurrentPowerEdit.getText().toString();
            float currentConsumption = PropertyValue.newValueObject(Float.class, editStr);
            currentConsumption += 1.0f;
            mCurrentPowerEdit.setText("" + currentConsumption);
            device().setProperty(PowerSaver.PROP_CURRENT_CONSUMPTION, Float.class, currentConsumption);
        } else if (v == mCurrentPowerMinusButton) {
            final String editStr = mCurrentPowerEdit.getText().toString();
            float currentConsumption = PropertyValue.newValueObject(Float.class, editStr);
            if (currentConsumption >= 1.0f) currentConsumption -= 1.0f;
            mCurrentPowerEdit.setText("" + currentConsumption);
            device().setProperty(PowerSaver.PROP_CURRENT_CONSUMPTION, Float.class, currentConsumption);
        } else if (v == mStandbyPowerSetButton) {
            final String editStr = mStandbyPowerEdit.getText().toString();
            float standbyConsumption = PropertyValue.newValueObject(Float.class, editStr);
            device().setProperty(PowerSaver.PROP_STANDBY_CONSUMPTION, Float.class, standbyConsumption);
        } else if (v == mStandbyPowerPlusButton) {
            final String editStr = mStandbyPowerEdit.getText().toString();
            float standbyConsumption = PropertyValue.newValueObject(Float.class, editStr);
            standbyConsumption += 1.0f;
            mStandbyPowerEdit.setText("" + standbyConsumption);
            device().setProperty(PowerSaver.PROP_STANDBY_CONSUMPTION, Float.class, standbyConsumption);
        } else if (v == mStandbyPowerMinusButton) {
            final String editStr = mStandbyPowerEdit.getText().toString();
            float standbyConsumption = PropertyValue.newValueObject(Float.class, editStr);
            if (standbyConsumption >= 1.0f) standbyConsumption -= 1.0f;
            mStandbyPowerEdit.setText("" + standbyConsumption);
            device().setProperty(PowerSaver.PROP_STANDBY_CONSUMPTION, Float.class, standbyConsumption);
        }
    }
}
