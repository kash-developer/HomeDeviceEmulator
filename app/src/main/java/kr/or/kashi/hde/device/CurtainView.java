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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.view.HomeDeviceView;

public class CurtainView extends HomeDeviceView<Curtain>
        implements  View.OnClickListener,
                    RadioGroup.OnCheckedChangeListener,
                    SeekBar.OnSeekBarChangeListener {

    private static final String TAG = CurtainView.class.getSimpleName();
    private final Context mContext;
    private ViewGroup mOperationGroup;
    private CheckBox mOperationCheck;
    private Button mOpStopButton;
    private Button mOpOpenButton;
    private Button mOpCloseButton;
    private CheckBox mStateCheck;
    private RadioGroup mStateGroup;
    private RadioButton mStateOpenedRadio;
    private RadioButton mStateClosedRadio;
    private RadioButton mStateOpeningRadio;
    private RadioButton mStateClosingRadio;
    private CheckBox mOpenLevelCheck;
    private SeekBar mOpenLevelSeek;
    private TextView mOpenLevelText;
    private CheckBox mOpenAngleCheck;
    private SeekBar mOpenAngleSeek;
    private TextView mOpenAngleText;

    private int mMinOpenLevel = 0;
    private int mMaxOpenLevel = 0;
    private int mMinOpenAngle = 0;
    private int mMaxOpenAngle = 0;

    public CurtainView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

    }

    @Override
    protected void onFinishInflate () {
        super.onFinishInflate();

        mOperationGroup = findViewById(R.id.operation_group);
        mOperationGroup.setVisibility(isMaster() ? View.VISIBLE : View.GONE);
        mOperationCheck = findViewById(R.id.operation_check);
        mOperationCheck.setEnabled(false);
        mOpStopButton = findViewById(R.id.op_stop_button);
        mOpStopButton.setEnabled(isMaster());
        mOpStopButton.setOnClickListener(this);
        mOpOpenButton = findViewById(R.id.op_open_button);
        mOpOpenButton.setEnabled(isMaster());
        mOpOpenButton.setOnClickListener(this);
        mOpCloseButton = findViewById(R.id.op_close_button);
        mOpCloseButton.setEnabled(isMaster());
        mOpCloseButton.setOnClickListener(this);

        mStateCheck = findViewById(R.id.state_check);
        mStateCheck.setEnabled(isSlave());
        mStateCheck.setOnClickListener(this);
        mStateGroup = findViewById(R.id.state_group);
        mStateGroup.setEnabled(isSlave());
        mStateOpenedRadio = findViewById(R.id.state_opened_radio);
        mStateOpenedRadio.setClickable(isSlave());
        mStateClosedRadio = findViewById(R.id.state_closed_radio);
        mStateClosedRadio.setClickable(isSlave());
        mStateOpeningRadio = findViewById(R.id.state_opening_radio);
        mStateOpeningRadio.setClickable(isSlave());
        mStateClosingRadio = findViewById(R.id.state_closing_radio);
        mStateClosingRadio.setClickable(isSlave());

        mOpenLevelCheck = findViewById(R.id.open_level_check);
        mOpenLevelCheck.setEnabled(isSlave());
        mOpenLevelCheck.setOnClickListener(this);
        mOpenLevelSeek = findViewById(R.id.open_level_seek);
        mOpenLevelSeek.setOnSeekBarChangeListener(this);
        mOpenLevelText = findViewById(R.id.open_level_text);

        mOpenAngleCheck = findViewById(R.id.open_angle_check);
        mOpenAngleCheck.setEnabled(isSlave());
        mOpenAngleCheck.setOnClickListener(this);
        mOpenAngleSeek = findViewById(R.id.open_angle_seek);
        mOpenAngleSeek.setOnSeekBarChangeListener(this);
        mOpenAngleText = findViewById(R.id.open_angle_text);
    }

    @Override
    public void onUpdateProperty(PropertyMap props, PropertyMap changed) {
        int stateResId = R.id.op_mode_auto_radio;
        int state = props.get(Curtain.PROP_STATE, Integer.class);
             if (state == Curtain.OpState.OPENED)  stateResId = R.id.state_opened_radio;
        else if (state == Curtain.OpState.CLOSED)  stateResId = R.id.state_closed_radio;
        else if (state == Curtain.OpState.OPENING) stateResId = R.id.state_opening_radio;
        else if (state == Curtain.OpState.CLOSING) stateResId = R.id.state_closing_radio;
        mStateGroup.setOnCheckedChangeListener(null);
        mStateGroup.check(stateResId);
        mStateGroup.setOnCheckedChangeListener(this);

        int supports = props.get(Curtain.PROP_SUPPORTS, Integer.class);
        mStateCheck.setChecked((supports & Curtain.Support.STATE) != 0);
        mOpenLevelCheck.setChecked((supports & Curtain.Support.OPEN_LEVEL) != 0);
        mOpenAngleCheck.setChecked((supports & Curtain.Support.OPEN_ANGLE) != 0);

        mStateGroup.setEnabled(mStateCheck.isChecked());
        mStateOpenedRadio.setEnabled(mStateCheck.isChecked());
        mStateClosedRadio.setEnabled(mStateCheck.isChecked());
        mStateOpeningRadio.setEnabled(mStateCheck.isChecked());
        mStateClosingRadio.setEnabled(mStateCheck.isChecked());

        mMinOpenLevel = props.get(Curtain.PROP_MIN_OPEN_LEVEL, Integer.class);
        mMaxOpenLevel = props.get(Curtain.PROP_MAX_OPEN_LEVEL, Integer.class);
        mOpenLevelSeek.setEnabled(mOpenLevelCheck.isChecked());
        mOpenLevelSeek.setMin(mMinOpenLevel);
        mOpenLevelSeek.setMax(mMaxOpenLevel);
        mOpenLevelSeek.setProgress(props.get(Curtain.PROP_CUR_OPEN_LEVEL, Integer.class));
        mOpenLevelText.setText(formatProgressText(mOpenLevelSeek));

        mMinOpenAngle = props.get(Curtain.PROP_MIN_OPEN_ANGLE, Integer.class);
        mMaxOpenAngle = props.get(Curtain.PROP_MAX_OPEN_ANGLE, Integer.class);
        mOpenAngleSeek.setEnabled(mOpenAngleCheck.isChecked());
        mOpenAngleSeek.setMin(mMinOpenAngle);
        mOpenAngleSeek.setMax(mMaxOpenAngle);
        mOpenAngleSeek.setProgress(props.get(Curtain.PROP_CUR_OPEN_ANGLE, Integer.class));
        mOpenAngleText.setText(formatProgressText(mOpenAngleSeek));
    }

    @Override
    public void onClick(View v) {
        if (v == mOpStopButton) {
            setOperation(Curtain.Operation.STOP);
        } else if (v == mOpOpenButton) {
            setOperation(Curtain.Operation.OPEN);
        } else if (v == mOpCloseButton) {
            setOperation(Curtain.Operation.CLOSE);
        } else if (v == mStateCheck) {
            setSupports();
        } else if (v == mOpenLevelCheck) {
            setSupports();
        } else if (v == mOpenAngleCheck) {
            setSupports();
        }
    }

    private void setOperation(int operation) {
        device().setProperty(Curtain.PROP_OPERATION, Integer.class, operation);
        device().setProperty(Curtain.PROP_CUR_OPEN_LEVEL, Integer.class, mOpenLevelSeek.getProgress());
        device().setProperty(Curtain.PROP_CUR_OPEN_ANGLE, Integer.class, mOpenAngleSeek.getProgress());
    }

    private void setSupports() {
        int supports = 0;
        if (mStateCheck.isChecked()) supports |= Curtain.Support.STATE;
        if (mOpenLevelCheck.isChecked()) supports |= Curtain.Support.OPEN_LEVEL;
        if (mOpenAngleCheck.isChecked()) supports |= Curtain.Support.OPEN_ANGLE;
        device().setProperty(Curtain.PROP_SUPPORTS, Integer.class, supports);
    }

    @Override
    public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
        if (group == mStateGroup) {
            int state = Curtain.OpState.CLOSED;
                 if (checkedId == R.id.state_opened_radio)  state = Curtain.OpState.OPENED;
            else if (checkedId == R.id.state_closed_radio)  state = Curtain.OpState.CLOSED;
            else if (checkedId == R.id.state_opening_radio) state = Curtain.OpState.OPENING;
            else if (checkedId == R.id.state_closing_radio) state = Curtain.OpState.CLOSING;
            device().setProperty(Curtain.PROP_STATE, Integer.class, state);
        }
    }

    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar == mOpenLevelSeek) {
            mOpenLevelText.setText(formatProgressText(mOpenLevelSeek));
        } else if (seekBar == mOpenAngleSeek) {
            mOpenAngleText.setText(formatProgressText(mOpenAngleSeek));
        }
    }
    @Override public void onStartTrackingTouch(SeekBar seekBar) { }
    @Override public void onStopTrackingTouch(SeekBar seekBar) {
        if (seekBar == mOpenLevelSeek) {
            device().setProperty(Curtain.PROP_CUR_OPEN_LEVEL, Integer.class, seekBar.getProgress());
            if (isMaster()) setOperation(Curtain.Operation.OPEN);
        } else if (seekBar == mOpenAngleSeek) {
            device().setProperty(Curtain.PROP_CUR_OPEN_ANGLE, Integer.class, seekBar.getProgress());
            if (isMaster()) setOperation(Curtain.Operation.OPEN);
        }
    }

    private String formatProgressText(SeekBar seekBar) {
        return seekBar.getProgress() + " [" + seekBar.getMin() + "~" + seekBar.getMax() + "]";
    }
}
