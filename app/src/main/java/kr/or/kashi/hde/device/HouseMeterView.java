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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;

import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.view.HomeDeviceView;

public class HouseMeterView extends HomeDeviceView<HouseMeter> {
    private static final String TAG = HouseMeterView.class.getSimpleName();
    private final Context mContext;

    private final String[] mMeterTypes = new String [] {
        "UNKNOWN",
        "WATER",
        "GAS",
        "ELECTRICITY",
        "HOT_WATER",
        "HEATING",
    };

    private final String[] mMeasureUnits = new String [] {
        "<?>",
        "m3",
        "W",
        "MW",
        "kWh",
    };

    private CheckBox mMeterTypeCheck;
    private TextView mMeterTypeText;
    private Spinner mMeterTypeSpinner;
    private CheckBox mMeterStateCheck;
    private RadioGroup mMeterStateGroup;
    private RadioButton mMeterStateDisabledRadio;
    private RadioButton mMeterStateEnabledRadio;
    private CheckBox mCurrentMeterCheck;
    private TextView mCurrentMeterText;
    private EditText mCurrentMeterEdit;
    private Spinner mCurrentMeterSpinner;
    private Button mCurrentMeterSetButton;
    private Button mCurrentMeterPlusButton;
    private Button mCurrentMeterMinusButton;
    private CheckBox mTotalMeterCheck;
    private TextView mTotalMeterText;
    private EditText mTotalMeterEdit;
    private Spinner mTotalMeterSpinner;
    private Button mTotalMeterSetButton;
    private Button mTotalMeterPlusButton;
    private Button mTotalMeterMinusButton;

    public HouseMeterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate () {
        super.onFinishInflate();

        mMeterTypeCheck = findViewById(R.id.meter_type_check);
        mMeterTypeText = findViewById(R.id.meter_type_text);
        mMeterTypeText.setVisibility(isMaster() ? View.VISIBLE : View.GONE);
        mMeterTypeSpinner = findViewById(R.id.meter_type_spinner);
        mMeterTypeSpinner.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mMeterTypeSpinner.setEnabled(false);
        mMeterTypeSpinner.setAdapter(new ArrayAdapter<>(mContext, R.layout.spinner_item, mMeterTypes));
        mMeterTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setMeterType();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                setMeterType();
            }
        });

        mMeterStateCheck = findViewById(R.id.meter_state_check);
        mMeterStateGroup = findViewById(R.id.meter_state_group);
        mMeterStateDisabledRadio = findViewById(R.id.meter_state_disabled_radio);
        mMeterStateDisabledRadio.setOnClickListener(view -> setMeterState());
        mMeterStateEnabledRadio = findViewById(R.id.meter_state_enabled_radio);
        mMeterStateEnabledRadio.setOnClickListener(view -> setMeterState());

        mCurrentMeterCheck = findViewById(R.id.current_meter_check);
        mCurrentMeterText = findViewById(R.id.current_meter_text);
        mCurrentMeterText.setVisibility(isMaster() ? View.VISIBLE : View.GONE);
        mCurrentMeterEdit = findViewById(R.id.current_meter_edit);
        mCurrentMeterEdit.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mCurrentMeterSpinner = findViewById(R.id.current_meter_spinner);
        mCurrentMeterSpinner.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mCurrentMeterSpinner.setEnabled(false);
        mCurrentMeterSpinner.setAdapter(new ArrayAdapter<>(mContext, R.layout.spinner_item, mMeasureUnits));
        mCurrentMeterSetButton = findViewById(R.id.current_meter_set_button);
        mCurrentMeterSetButton.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mCurrentMeterSetButton.setOnClickListener(view -> setCurrentMeterValue());
        mCurrentMeterPlusButton = findViewById(R.id.current_meter_plus_button);
        mCurrentMeterPlusButton.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mCurrentMeterPlusButton.setOnClickListener(view -> incCurrentMeterValue());
        mCurrentMeterMinusButton = findViewById(R.id.current_meter_minus_button);
        mCurrentMeterMinusButton.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mCurrentMeterMinusButton.setOnClickListener(view -> decCurrentMeterValue());

        mTotalMeterCheck = findViewById(R.id.total_meter_check);
        mTotalMeterText = findViewById(R.id.total_meter_text);
        mTotalMeterText.setVisibility(isMaster() ? View.VISIBLE : View.GONE);
        mTotalMeterEdit = findViewById(R.id.total_meter_edit);
        mTotalMeterEdit.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mTotalMeterSpinner = findViewById(R.id.total_meter_spinner);
        mTotalMeterSpinner.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mTotalMeterSpinner.setEnabled(false);
        mTotalMeterSpinner.setAdapter(new ArrayAdapter<>(mContext, R.layout.spinner_item, mMeasureUnits));
        mTotalMeterSetButton = findViewById(R.id.total_meter_set_button);
        mTotalMeterSetButton.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mTotalMeterSetButton.setOnClickListener(view -> setTotalMeterValue());
        mTotalMeterPlusButton = findViewById(R.id.total_meter_plus_button);
        mTotalMeterPlusButton.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mTotalMeterPlusButton.setOnClickListener(view -> incTotalMeterValue());
        mTotalMeterMinusButton = findViewById(R.id.total_meter_minus_button);
        mTotalMeterMinusButton.setVisibility(isSlave() ? View.VISIBLE : View.GONE);
        mTotalMeterMinusButton.setOnClickListener(view -> decTotalMeterValue());
    }

    @Override
    public void onUpdateProperty(PropertyMap props, PropertyMap changed) {
        final int meterType = props.get(HouseMeter.PROP_METER_TYPE, Integer.class);
        final int currentUnit = props.get(HouseMeter.PROP_CURRENT_METER_UNIT, Integer.class);
        final int totalUnit = props.get(HouseMeter.PROP_TOTAL_METER_UNIT, Integer.class);
        mMeterTypeText.setText(mMeterTypes[meterType]);
        mMeterTypeSpinner.setSelection(meterType);
        mMeterStateDisabledRadio.setClickable(isSlave());
        mMeterStateEnabledRadio.setClickable(isSlave());
        mCurrentMeterSpinner.setSelection(currentUnit);
        mTotalMeterSpinner.setSelection(totalUnit);

        final boolean meterEnabled = props.get(HouseMeter.PROP_METER_ENABLED, Boolean.class);
        if (meterEnabled) {
            final double currentMeter = props.get(HouseMeter.PROP_CURRENT_METER_VALUE, Double.class);
            final double totalMeter = props.get(HouseMeter.PROP_TOTAL_METER_VALUE, Double.class);
            mMeterStateEnabledRadio.setChecked(true);
            mCurrentMeterText.setText(currentMeter + " " + mMeasureUnits[currentUnit]);
            mCurrentMeterEdit.setText("" + currentMeter);
            mTotalMeterText.setText(totalMeter + " " + mMeasureUnits[totalUnit]);
            mTotalMeterEdit.setText("" + totalMeter);
        } else {
            mMeterStateDisabledRadio.setChecked(true);
            mCurrentMeterText.setText("N/A");
            mCurrentMeterEdit.setText("0.0");
            mTotalMeterText.setText("N/A");
            mTotalMeterEdit.setText("0.0");
        }
    }

    private void setMeterType() {
        final int meterType = mMeterTypeSpinner.getSelectedItemPosition();
        device().setProperty(HouseMeter.PROP_METER_TYPE, Integer.class, meterType);
    }

    private void setMeterState() {
        final boolean meterEnabled = mMeterStateEnabledRadio.isChecked();
        device().setProperty(HouseMeter.PROP_METER_ENABLED, Boolean.class, meterEnabled);
    }

    private void setCurrentMeterValue() {
        final double currentMeter = Double.valueOf(mCurrentMeterEdit.getText().toString()).doubleValue();
        device().setProperty(HouseMeter.PROP_CURRENT_METER_VALUE, Double.class, currentMeter);
    }

    private void incCurrentMeterValue() {
        final double currentMeter = Double.valueOf(mCurrentMeterEdit.getText().toString()).doubleValue();
        mCurrentMeterEdit.setText("" + (currentMeter + 1.0));
        setCurrentMeterValue();
    }

    private void decCurrentMeterValue() {
        final double currentMeter = Double.valueOf(mCurrentMeterEdit.getText().toString()).doubleValue();
        if (currentMeter >= 1.0) {
            mCurrentMeterEdit.setText("" + (currentMeter - 1.0));
        }
        setCurrentMeterValue();
    }

    private void setTotalMeterValue() {
        final double totalMeter = Double.valueOf(mTotalMeterEdit.getText().toString()).doubleValue();
        device().setProperty(HouseMeter.PROP_TOTAL_METER_VALUE, Double.class, totalMeter);
    }

    private void incTotalMeterValue() {
        final double totalMeter = Double.valueOf(mTotalMeterEdit.getText().toString()).doubleValue();
        mTotalMeterEdit.setText("" + (totalMeter + 1.0));
        setTotalMeterValue();
    }

    private void decTotalMeterValue() {
        final double totalMeter = Double.valueOf(mTotalMeterEdit.getText().toString()).doubleValue();
        if (totalMeter >= 1.0) {
            mTotalMeterEdit.setText("" + (totalMeter - 1.0));
        }
        setTotalMeterValue();
    }
}
