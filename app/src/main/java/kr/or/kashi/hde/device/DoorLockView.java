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
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.Nullable;

import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.view.HomeDeviceView;

public class DoorLockView extends HomeDeviceView<DoorLock> implements View.OnClickListener {
    private static final String TAG = DoorLockView.class.getSimpleName();
    private final Context mContext;

    private CheckBox mDoorCheck;
    private RadioGroup mDoorGroup;
    private RadioButton mDoorOpenedRadio;
    private RadioButton mDoorClosedRadio;
    private CheckBox mEmergencyCheck;
    private RadioGroup mEmergencyGroup;
    private RadioButton mEmergencyAlarmedRadio;
    private RadioButton mEmergencyNormalRadio;

    public DoorLockView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate () {
        super.onFinishInflate();

        mDoorCheck = findViewById(R.id.door_check);
        mDoorCheck.setEnabled(false); // supported in forced (see. spec)
        mDoorCheck.setOnClickListener(this);
        mDoorGroup = findViewById(R.id.door_group);
        mDoorOpenedRadio = findViewById(R.id.door_opened_radio);
        mDoorOpenedRadio.setOnClickListener(this);
        mDoorClosedRadio = findViewById(R.id.door_closed_radio);
        mDoorClosedRadio.setOnClickListener(this);

        mEmergencyCheck = findViewById(R.id.emergency_check);
        mEmergencyCheck.setEnabled(false); // supported in forced (see. spec)
        mEmergencyCheck.setOnClickListener(this);
        mEmergencyGroup = findViewById(R.id.emergency_group);
        mEmergencyAlarmedRadio = findViewById(R.id.emergency_alarmed_radio);
        mEmergencyAlarmedRadio.setClickable(isSlave());
        if (isSlave()) mEmergencyAlarmedRadio.setOnClickListener(this);
        mEmergencyNormalRadio = findViewById(R.id.emergency_normal_radio);
        mEmergencyNormalRadio.setClickable(isSlave());
        if (isSlave()) mEmergencyNormalRadio.setOnClickListener(this);
    }

    @Override
    public void onUpdateProperty(PropertyMap props, PropertyMap changed) {
        final long supports = props.get(DoorLock.PROP_SUPPORTED_STATES, Long.class);
        mDoorCheck.setChecked((supports & DoorLock.State.DOOR_OPENED) != 0);
        mEmergencyCheck.setChecked((supports & DoorLock.State.EMERGENCY_ALARMED) != 0);
        mDoorOpenedRadio.setEnabled(mDoorCheck.isChecked());
        mDoorClosedRadio.setEnabled(mDoorCheck.isChecked());
        mEmergencyAlarmedRadio.setEnabled(mEmergencyCheck.isChecked());
        mEmergencyNormalRadio.setEnabled(mEmergencyCheck.isChecked());

        final long states = props.get(DoorLock.PROP_CURRENT_STATES, Long.class);
        final boolean opened = (states & DoorLock.State.DOOR_OPENED) != 0;
        final boolean alarmed = (states & DoorLock.State.EMERGENCY_ALARMED) != 0;
        mDoorGroup.check(opened ? R.id.door_opened_radio : R.id.door_closed_radio);
        mEmergencyGroup.check(alarmed ? R.id.emergency_alarmed_radio : R.id.emergency_normal_radio);
    }

    @Override
    public void onClick(View v) {
        if (v == mDoorCheck) {
            setSupports();
        } else if (v == mEmergencyCheck) {
            setSupports();
        } else if (v == mDoorOpenedRadio) {
            setStates();
        } else if (v == mDoorClosedRadio) {
            setStates();
        } else if (v == mEmergencyAlarmedRadio) {
            setStates();
        } else if (v == mEmergencyNormalRadio) {
            setStates();
        }
    }

    private void setSupports() {
        long supports = 0;
        if (mDoorCheck.isChecked()) supports |= DoorLock.State.DOOR_OPENED;
        if (mEmergencyCheck.isChecked()) supports |= DoorLock.State.EMERGENCY_ALARMED;
        device().setProperty(DoorLock.PROP_SUPPORTED_STATES, Long.class, supports);
    }

    private void setStates() {
        long states = 0;
        if (mDoorOpenedRadio.isChecked()) states |= DoorLock.State.DOOR_OPENED;
        if (mEmergencyAlarmedRadio.isChecked()) states |= DoorLock.State.EMERGENCY_ALARMED;
        device().setProperty(DoorLock.PROP_CURRENT_STATES, Long.class, states);
    }
}
