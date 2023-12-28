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
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.widget.HomeDeviceView;

public class VentilationView extends HomeDeviceView<Ventilation> {
    private static final String TAG = VentilationView.class.getSimpleName();
    private final Context mContext;

    private CheckBox mModesCheck;
    private RadioButton mModeNormalRadio;
    private RadioButton mModeSleepRadio;
    private RadioButton mModeRecycleRadio;
    private RadioButton mModeAutoRadio;
    private RadioButton mModeSavingRadio;
    private RadioButton mModeCleanAirRadio;
    private RadioButton mModeInternalRadio;
    private CheckBox mSensorsCheck;
    private CheckBox mSensorCo2Check;
    private CheckBox mAlarmsCheck;
    private CheckBox mAlarmFanOverHeatingCheck;
    private CheckBox mAlarmRecyclerChangeCheck;
    private CheckBox mAlarmFilterChangeCheck;
    private CheckBox mAlarmSmokeRemovingCheck;
    private CheckBox mAlarmHighCo2LevelCheck;
    private CheckBox mAlarmHeaterRunningCheck;
    private CheckBox mFanSpeedCheck;
    private TextView mFanSpeedText;
    private SeekBar mFanSpeedSeek;

    public VentilationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

    }

    @Override
    protected void onFinishInflate () {
        super.onFinishInflate();

        mModesCheck = findViewById(R.id.modes_check);
        mModeNormalRadio = findViewById(R.id.mode_normal_radio);
        mModeNormalRadio.setOnClickListener(v -> setOperationMode());
        mModeSleepRadio = findViewById(R.id.mode_sleep_radio);
        mModeSleepRadio.setOnClickListener(v -> setOperationMode());
        mModeRecycleRadio = findViewById(R.id.mode_recycle_radio);
        mModeRecycleRadio.setOnClickListener(v -> setOperationMode());
        mModeAutoRadio = findViewById(R.id.mode_auto_radio);
        mModeAutoRadio.setOnClickListener(v -> setOperationMode());
        mModeSavingRadio = findViewById(R.id.mode_saving_radio);
        mModeSavingRadio.setOnClickListener(v -> setOperationMode());
        mModeCleanAirRadio = findViewById(R.id.mode_cleanair_radio);
        mModeCleanAirRadio.setOnClickListener(v -> setOperationMode());
        mModeInternalRadio = findViewById(R.id.mode_internal_radio);
        mModeInternalRadio.setOnClickListener(v -> setOperationMode());
        mSensorsCheck = findViewById(R.id.sensors_check);
        mSensorCo2Check = findViewById(R.id.sensor_co2_check);
        mSensorCo2Check.setOnClickListener(v -> setSupportedSensors());
        mSensorCo2Check.setClickable(isSlave());
        mAlarmsCheck = findViewById(R.id.alarms_check);
        mAlarmFanOverHeatingCheck = findViewById(R.id.alarm_fan_overheating_check);
        mAlarmFanOverHeatingCheck.setOnClickListener(v -> setOperationAlarms());
        mAlarmFanOverHeatingCheck.setClickable(isSlave());
        mAlarmRecyclerChangeCheck = findViewById(R.id.alarm_recycler_change_check);
        mAlarmRecyclerChangeCheck.setOnClickListener(v -> setOperationAlarms());
        mAlarmRecyclerChangeCheck.setClickable(isSlave());
        mAlarmFilterChangeCheck = findViewById(R.id.alarm_filter_change_check);
        mAlarmFilterChangeCheck.setOnClickListener(v -> setOperationAlarms());
        mAlarmFilterChangeCheck.setClickable(isSlave());
        mAlarmSmokeRemovingCheck = findViewById(R.id.alarm_smoke_removing_check);
        mAlarmSmokeRemovingCheck.setOnClickListener(v -> setOperationAlarms());
        mAlarmSmokeRemovingCheck.setClickable(isSlave());
        mAlarmHighCo2LevelCheck = findViewById(R.id.alarm_high_co2_level_check);
        mAlarmHighCo2LevelCheck.setOnClickListener(v -> setOperationAlarms());
        mAlarmHighCo2LevelCheck.setClickable(isSlave());
        mAlarmHeaterRunningCheck = findViewById(R.id.alarm_heater_running_check);
        mAlarmHeaterRunningCheck.setOnClickListener(v -> setOperationAlarms());
        mAlarmHeaterRunningCheck.setClickable(isSlave());
        mFanSpeedCheck = findViewById(R.id.fan_speed_check);
        mFanSpeedText = findViewById(R.id.fan_speed_text);
        mFanSpeedSeek = findViewById(R.id.fan_speed_seek);
        mFanSpeedSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                setFanSpeed();
            }
        });
    }

    @Override
    public void onUpdateProperty(PropertyMap props, PropertyMap changed) {
        final long supportedModes = props.get(Ventilation.PROP_SUPPORTED_MODES, Long.class);
        mModeNormalRadio.setEnabled((supportedModes & Ventilation.Mode.NORMAL) != 0);
        mModeSleepRadio.setEnabled((supportedModes & Ventilation.Mode.SLEEP) != 0);
        mModeRecycleRadio.setEnabled((supportedModes & Ventilation.Mode.RECYCLE) != 0);
        mModeAutoRadio.setEnabled((supportedModes & Ventilation.Mode.AUTO) != 0);
        mModeSavingRadio.setEnabled((supportedModes & Ventilation.Mode.SAVING) != 0);
        mModeCleanAirRadio.setEnabled((supportedModes & Ventilation.Mode.CLEANAIR) != 0);
        mModeInternalRadio.setEnabled((supportedModes & Ventilation.Mode.INTERNAL) != 0);

        final long operationMode = props.get(Ventilation.PROP_OPERATION_MODE, Long.class);
        mModeNormalRadio.setChecked((operationMode & Ventilation.Mode.NORMAL) != 0);
        mModeSleepRadio.setChecked((operationMode & Ventilation.Mode.SLEEP) != 0);
        mModeRecycleRadio.setChecked((operationMode & Ventilation.Mode.RECYCLE) != 0);
        mModeAutoRadio.setChecked((operationMode & Ventilation.Mode.AUTO) != 0);
        mModeSavingRadio.setChecked((operationMode & Ventilation.Mode.SAVING) != 0);
        mModeCleanAirRadio.setChecked((operationMode & Ventilation.Mode.CLEANAIR) != 0);
        mModeInternalRadio.setChecked((operationMode & Ventilation.Mode.INTERNAL) != 0);

        final long supportedSendors = props.get(Ventilation.PROP_SUPPORTED_SENSORS, Long.class);
        mSensorCo2Check.setChecked((supportedSendors & Ventilation.Sensor.CO2) != 0);

        final long operationAlarms = props.get(Ventilation.PROP_OPERATION_ALARM, Long.class);
        mAlarmFanOverHeatingCheck.setChecked((operationAlarms & Ventilation.Alarm.FAN_OVERHEATING) != 0);
        mAlarmRecyclerChangeCheck.setChecked((operationAlarms & Ventilation.Alarm.RECYCLER_CHANGE) != 0);
        mAlarmFilterChangeCheck.setChecked((operationAlarms & Ventilation.Alarm.FILTER_CHANGE) != 0);
        mAlarmSmokeRemovingCheck.setChecked((operationAlarms & Ventilation.Alarm.SMOKE_REMOVING) != 0);
        mAlarmHighCo2LevelCheck.setChecked((operationAlarms & Ventilation.Alarm.HIGH_CO2_LEVEL) != 0);
        mAlarmHeaterRunningCheck.setChecked((operationAlarms & Ventilation.Alarm.HEATER_RUNNING) != 0);

        final int curSpeed = props.get(Ventilation.PROP_CUR_FAN_SPEED, Integer.class);
        final int minSpeed = props.get(Ventilation.PROP_MIN_FAN_SPEED, Integer.class);
        final int maxSpeed = props.get(Ventilation.PROP_MAX_FAN_SPEED, Integer.class);
        final String speedText = "min:" + minSpeed + " max:" + maxSpeed + " cur:" + curSpeed;
        mFanSpeedText.setText(speedText);
        mFanSpeedSeek.setMin(minSpeed);
        mFanSpeedSeek.setMax(maxSpeed);
        mFanSpeedSeek.setProgress(curSpeed);
    }

    private void setOperationMode() {
        long operationMode = 0;
        if (mModeNormalRadio.isChecked()) operationMode |= Ventilation.Mode.NORMAL;
        if (mModeSleepRadio.isChecked()) operationMode |= Ventilation.Mode.SLEEP;
        if (mModeRecycleRadio.isChecked()) operationMode |= Ventilation.Mode.RECYCLE;
        if (mModeAutoRadio.isChecked()) operationMode |= Ventilation.Mode.AUTO;
        if (mModeSavingRadio.isChecked()) operationMode |= Ventilation.Mode.SAVING;
        if (mModeCleanAirRadio.isChecked()) operationMode |= Ventilation.Mode.CLEANAIR;
        if (mModeInternalRadio.isChecked()) operationMode |= Ventilation.Mode.INTERNAL;
        device().setProperty(Ventilation.PROP_OPERATION_MODE, Long.class, operationMode);
    }

    private void setSupportedSensors() {
        long supportedSendors = 0;
        if (mSensorCo2Check.isChecked()) supportedSendors |= Ventilation.Sensor.CO2;
        device().setProperty(Ventilation.PROP_SUPPORTED_SENSORS, Long.class, supportedSendors);
    }

    private void setOperationAlarms() {
        long operationAlarms = 0;
        if (mAlarmFanOverHeatingCheck.isChecked()) operationAlarms |= Ventilation.Alarm.FAN_OVERHEATING;
        if (mAlarmRecyclerChangeCheck.isChecked()) operationAlarms |= Ventilation.Alarm.RECYCLER_CHANGE;
        if (mAlarmFilterChangeCheck.isChecked()) operationAlarms |= Ventilation.Alarm.FILTER_CHANGE;
        if (mAlarmSmokeRemovingCheck.isChecked()) operationAlarms |= Ventilation.Alarm.SMOKE_REMOVING;
        if (mAlarmHighCo2LevelCheck.isChecked()) operationAlarms |= Ventilation.Alarm.HIGH_CO2_LEVEL;
        if (mAlarmHeaterRunningCheck.isChecked()) operationAlarms |= Ventilation.Alarm.HEATER_RUNNING;
        device().setProperty(Ventilation.PROP_OPERATION_ALARM, Long.class, operationAlarms);
    }

    private void setFanSpeed() {
        device().setProperty(Ventilation.PROP_CUR_FAN_SPEED, Integer.class, mFanSpeedSeek.getProgress());
    }
}
