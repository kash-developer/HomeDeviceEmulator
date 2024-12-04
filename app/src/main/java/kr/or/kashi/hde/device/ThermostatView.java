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
import kr.or.kashi.hde.util.Utils;
import kr.or.kashi.hde.widget.HomeDeviceView;

public class ThermostatView extends HomeDeviceView<Thermostat> {
    private static final String TAG = ThermostatView.class.getSimpleName();
    private final Context mContext;

    private CheckBox mFunctionCheck;
    private CheckBox mHeatingCheck;
    private CheckBox mCoolingCheck;
    private CheckBox mOutingSettingCheck;
    private CheckBox mHotwaterOnlyCheck;
    private CheckBox mReservedModeCheck;
    private CheckBox mTempRangeCheck;
    private TextView mTempRangeText;
    private CheckBox mCurrentTempCheck;
    private TextView mCurrentTempText;
    private CheckBox mSettingTempCheck;
    private TextView mSettingTempText;
    private EditText mSettingTempEdit;

    private float mMinTemp = 0.0f;
    private float mMaxTemp = 0.0f;
    private float mTempRes = 0.0f;

    public ThermostatView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate () {
        super.onFinishInflate();

        mFunctionCheck = findViewById(R.id.function_check);
        mHeatingCheck = findViewById(R.id.function_heating_check);
        mHeatingCheck.setOnClickListener(v -> setFunctions());
        mCoolingCheck = findViewById(R.id.function_cooling_check);
        mCoolingCheck.setOnClickListener(v -> setFunctions());
        mOutingSettingCheck = findViewById(R.id.function_outing_setting_check);
        mOutingSettingCheck.setOnClickListener(v -> setFunctions());
        mHotwaterOnlyCheck = findViewById(R.id.function_hotwater_only_check);
        mHotwaterOnlyCheck.setOnClickListener(v -> setFunctions());
        mReservedModeCheck = findViewById(R.id.function_reserved_mode_check);
        mReservedModeCheck.setOnClickListener(v -> setFunctions());
        mTempRangeCheck = findViewById(R.id.temp_range_check);
        mTempRangeText = findViewById(R.id.temp_range_text);
        mCurrentTempCheck = findViewById(R.id.current_temp_check);
        mCurrentTempText = findViewById(R.id.current_temp_text);
        mSettingTempCheck = findViewById(R.id.setting_temp_check);
        mSettingTempText = findViewById(R.id.setting_temp_text);
        mSettingTempEdit = findViewById(R.id.setting_temp_edit);
        findViewById(R.id.temp_setting_button).setOnClickListener(v -> setTemperature());
        findViewById(R.id.temp_plus_button).setOnClickListener(v -> incTemperature());
        findViewById(R.id.temp_minus_button).setOnClickListener(v -> decTemperature());
    }

    @Override
    public void onUpdateProperty(PropertyMap props, PropertyMap changed) {
        final long supportedFunctions = props.get(Thermostat.PROP_SUPPORTED_FUNCTIONS, Long.class);
        mHeatingCheck.setEnabled((supportedFunctions & Thermostat.Function.HEATING) != 0);
        mCoolingCheck.setEnabled((supportedFunctions & Thermostat.Function.COOLING) != 0);
        mOutingSettingCheck.setEnabled((supportedFunctions & Thermostat.Function.OUTING_SETTING) != 0);
        mHotwaterOnlyCheck.setEnabled((supportedFunctions & Thermostat.Function.HOTWATER_ONLY) != 0);
        mReservedModeCheck.setEnabled((supportedFunctions & Thermostat.Function.RESERVED_MODE) != 0);

        final long functionStates = props.get(Thermostat.PROP_FUNCTION_STATES, Long.class);
        mHeatingCheck.setChecked((functionStates & Thermostat.Function.HEATING) != 0);
        mCoolingCheck.setChecked((functionStates & Thermostat.Function.COOLING) != 0);
        mOutingSettingCheck.setChecked((functionStates & Thermostat.Function.OUTING_SETTING) != 0);
        mHotwaterOnlyCheck.setChecked((functionStates & Thermostat.Function.HOTWATER_ONLY) != 0);
        mReservedModeCheck.setChecked((functionStates & Thermostat.Function.RESERVED_MODE) != 0);

        mMinTemp = props.get(Thermostat.PROP_MIN_TEMPERATURE, Float.class);
        mMaxTemp = props.get(Thermostat.PROP_MAX_TEMPERATURE, Float.class);
        mTempRes = props.get(Thermostat.PROP_TEMP_RESOLUTION, Float.class);
        mTempRangeText.setText("min:" + mMinTemp + ", max:" + mMaxTemp + ", res:" + mTempRes);

        final float curTemp = props.get(Thermostat.PROP_CURRENT_TEMPERATURE, Float.class);
        mCurrentTempText.setText("" + curTemp);

        final float setTemp = props.get(Thermostat.PROP_SETTING_TEMPERATURE, Float.class);
        mSettingTempText.setText("" + setTemp);
        if (!mSettingTempEdit.hasFocus()) mSettingTempEdit.setText("" + setTemp);
    }

    private void setFunctions() {
        long functions = 0;
        if (mHeatingCheck.isChecked()) functions |= Thermostat.Function.HEATING;
        if (mCoolingCheck.isChecked()) functions |= Thermostat.Function.COOLING;
        if (mOutingSettingCheck.isChecked()) functions |= Thermostat.Function.OUTING_SETTING;
        if (mHotwaterOnlyCheck.isChecked()) functions |= Thermostat.Function.HOTWATER_ONLY;
        if (mReservedModeCheck.isChecked()) functions |= Thermostat.Function.RESERVED_MODE;
        device().setProperty(Thermostat.PROP_FUNCTION_STATES, Long.class,  functions);
    }

    private void setTemperature() {
        final String editStr = mSettingTempEdit.getText().toString();
        float reqTemp = PropertyValue.newValueObject(Float.class, editStr);
        device().setProperty(Thermostat.PROP_SETTING_TEMPERATURE, Float.class, reqTemp);
    }

    private void incTemperature() {
        final String editStr = mSettingTempEdit.getText().toString();
        float newTemp = PropertyValue.newValueObject(Float.class, editStr);
        newTemp = roundTemp(newTemp + mTempRes);
        mSettingTempEdit.setText("" + newTemp);
        setTemperature();
    }

    private void decTemperature() {
        final String editStr = mSettingTempEdit.getText().toString();
        float newTemp = PropertyValue.newValueObject(Float.class, editStr);
        newTemp = roundTemp(newTemp - mTempRes);
        mSettingTempEdit.setText("" + newTemp);
        setTemperature();
    }

    public float roundTemp(float value) {
        value = Utils.roundToNearest(value, mTempRes);
        value = Math.max(value, mMinTemp);
        value = Math.min(value, mMaxTemp);
        return value;
    }
}
