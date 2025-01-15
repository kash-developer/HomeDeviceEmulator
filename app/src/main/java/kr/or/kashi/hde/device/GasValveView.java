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
import android.widget.RadioGroup;

import androidx.annotation.Nullable;

import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.view.HomeDeviceView;

public class GasValveView extends HomeDeviceView<GasValve> {
    private static final String TAG = GasValveView.class.getSimpleName();
    private final Context mContext;

    private CheckBox mGasValveCheck;
    private RadioGroup mGasValveGroup;
    private RadioButton mGasValveClosedRadio;
    private RadioButton mGasValveOpenedRadio;
    private CheckBox mInductionCheck;
    private RadioGroup mInductionGroup;
    private RadioButton mInductionOffRadio;
    private RadioButton mInductionOnRadio;
    private CheckBox mExtinguisherCheck;
    private RadioGroup mExtinguisherGroup;
    private RadioButton mExtinguisherNormalRadio;
    private RadioButton mExtinguisherBuzzingRadio;
    private CheckBox mGasLeakageCheck;
    private RadioGroup mGasLeakageGroup;
    private RadioButton mGasLeakageNormalRadio;
    private RadioButton mGasLeakageDetectedRadio;

    public GasValveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate () {
        super.onFinishInflate();

        mGasValveCheck = findViewById(R.id.gas_valve_check);
        mGasValveCheck.setEnabled(isSlave());
        mGasValveCheck.setOnClickListener(v -> setSupports());
        mGasValveGroup = findViewById(R.id.gas_valve_group);
        mGasValveClosedRadio = findViewById(R.id.gas_valve_closed_radio);
        mGasValveClosedRadio.setOnClickListener(v -> setStates());
        mGasValveOpenedRadio = findViewById(R.id.gas_valve_opened_radio);
        mGasValveOpenedRadio.setOnClickListener(v -> setStates());
        mInductionCheck = findViewById(R.id.induction_check);
        mInductionCheck.setEnabled(isSlave());
        mInductionCheck.setOnClickListener(v -> setSupports());
        mInductionGroup = findViewById(R.id.induction_group);
        mInductionOffRadio = findViewById(R.id.induction_off_radio);
        mInductionOffRadio.setOnClickListener(v -> setStates());
        mInductionOnRadio = findViewById(R.id.induction_on_radio);
        mInductionOnRadio.setOnClickListener(v -> setStates());
        mExtinguisherCheck = findViewById(R.id.extinguisher_check);
        mExtinguisherCheck.setEnabled(isSlave());
        mExtinguisherCheck.setOnClickListener(v -> setSupports());
        mExtinguisherGroup = findViewById(R.id.extinguisher_group);
        mExtinguisherNormalRadio = findViewById(R.id.extinguisher_normal_radio);
        mExtinguisherNormalRadio.setOnClickListener(v -> setAlarms());
        mExtinguisherBuzzingRadio = findViewById(R.id.extinguisher_buzzing_radio);
        mExtinguisherBuzzingRadio.setOnClickListener(v -> setAlarms());
        mGasLeakageCheck = findViewById(R.id.gas_leakage_check);
        mGasLeakageCheck.setEnabled(isSlave());
        mGasLeakageCheck.setOnClickListener(v -> setSupports());
        mGasLeakageGroup = findViewById(R.id.gas_leakage_group);
        mGasLeakageNormalRadio = findViewById(R.id.gas_leakage_normal_radio);
        mGasLeakageNormalRadio.setOnClickListener(v -> setAlarms());
        mGasLeakageDetectedRadio = findViewById(R.id.gas_leakage_detected_radio);
        mGasLeakageDetectedRadio.setOnClickListener(v -> setAlarms());
    }

    @Override
    public void onUpdateProperty(PropertyMap props, PropertyMap changed) {
        final long supportedStates = props.get(GasValve.PROP_SUPPORTED_STATES, Long.class);
        mGasValveCheck.setChecked((supportedStates & GasValve.State.GAS_VALVE) != 0);
        mGasValveClosedRadio.setEnabled((supportedStates & GasValve.State.GAS_VALVE) != 0);
        mGasValveOpenedRadio.setEnabled((supportedStates & GasValve.State.GAS_VALVE) != 0);
        mInductionCheck.setChecked((supportedStates & GasValve.State.INDUCTION) != 0);
        mInductionOffRadio.setEnabled((supportedStates & GasValve.State.INDUCTION) != 0);
        mInductionOnRadio.setEnabled((supportedStates & GasValve.State.INDUCTION) != 0);

        final long supportedAlarms = props.get(GasValve.PROP_SUPPORTED_ALARMS, Long.class);
        mExtinguisherCheck.setChecked((supportedAlarms & GasValve.Alarm.EXTINGUISHER_BUZZING) != 0);
        mExtinguisherNormalRadio.setEnabled((supportedAlarms & GasValve.Alarm.EXTINGUISHER_BUZZING) != 0);
        mExtinguisherBuzzingRadio.setEnabled((supportedAlarms & GasValve.Alarm.EXTINGUISHER_BUZZING) != 0);
        mGasLeakageCheck.setChecked((supportedAlarms & GasValve.Alarm.GAS_LEAKAGE_DETECTED) != 0);
        mGasLeakageNormalRadio.setEnabled((supportedAlarms & GasValve.Alarm.GAS_LEAKAGE_DETECTED) != 0);
        mGasLeakageDetectedRadio.setEnabled((supportedAlarms & GasValve.Alarm.GAS_LEAKAGE_DETECTED) != 0);

        final long currentStates = props.get(GasValve.PROP_CURRENT_STATES, Long.class);
        mGasValveClosedRadio.setChecked((currentStates & GasValve.State.GAS_VALVE) == 0);
        mGasValveOpenedRadio.setChecked((currentStates & GasValve.State.GAS_VALVE) != 0);
        mInductionOffRadio.setChecked((currentStates & GasValve.State.INDUCTION) == 0);
        mInductionOnRadio.setChecked((currentStates & GasValve.State.INDUCTION) != 0);

        final long currentAlarms = props.get(GasValve.PROP_CURRENT_ALARMS, Long.class);
        mExtinguisherNormalRadio.setChecked((currentAlarms & GasValve.Alarm.EXTINGUISHER_BUZZING) == 0);
        mExtinguisherBuzzingRadio.setChecked((currentAlarms & GasValve.Alarm.EXTINGUISHER_BUZZING) != 0);
        mGasLeakageNormalRadio.setChecked((currentAlarms & GasValve.Alarm.GAS_LEAKAGE_DETECTED) == 0);
        mGasLeakageDetectedRadio.setChecked((currentAlarms & GasValve.Alarm.GAS_LEAKAGE_DETECTED) != 0);
        mGasLeakageNormalRadio.setClickable(isSlave());
        mGasLeakageDetectedRadio.setClickable(isSlave());
    }

    private void setSupports() {
        long supportedStates = 0;
        if (mGasValveCheck.isChecked()) supportedStates |= GasValve.State.GAS_VALVE;
        if (mInductionCheck.isChecked()) supportedStates |= GasValve.State.INDUCTION;
        device().setProperty(GasValve.PROP_SUPPORTED_STATES, Long.class, supportedStates);

        long supportedAlarms = 0;
        if (mExtinguisherCheck.isChecked()) supportedAlarms |= GasValve.Alarm.EXTINGUISHER_BUZZING;
        if (mGasLeakageCheck.isChecked()) supportedAlarms |= GasValve.Alarm.GAS_LEAKAGE_DETECTED;
        device().setProperty(GasValve.PROP_SUPPORTED_ALARMS, Long.class, supportedAlarms);
    }

    private void setStates() {
        long states = 0;
        if (mGasValveOpenedRadio.isChecked()) states |= GasValve.State.GAS_VALVE;
        if (mInductionOnRadio.isChecked()) states |= GasValve.State.INDUCTION;
        device().setProperty(GasValve.PROP_CURRENT_STATES, Long.class, states);
    }

    private void setAlarms() {
        long alarms = 0;
        if (mExtinguisherBuzzingRadio.isChecked()) alarms |= GasValve.Alarm.EXTINGUISHER_BUZZING;
        if (mGasLeakageDetectedRadio.isChecked()) alarms |= GasValve.Alarm.GAS_LEAKAGE_DETECTED;

        device().setProperty(GasValve.PROP_CURRENT_ALARMS, Long.class, alarms);
    }
}
