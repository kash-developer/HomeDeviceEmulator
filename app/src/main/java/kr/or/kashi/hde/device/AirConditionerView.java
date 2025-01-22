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
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.util.Utils;
import kr.or.kashi.hde.view.HomeDeviceView;
import kr.or.kashi.hde.widget.FloatRangeView;
import kr.or.kashi.hde.widget.FloatSeekBar;

public class AirConditionerView extends HomeDeviceView<AirConditioner>
        implements  RadioGroup.OnCheckedChangeListener,
                    FloatSeekBar.OnSeekBarChangeListener,
                    FloatRangeView.OnValueEditedListener {

    private static final String TAG = AirConditionerView.class.getSimpleName();
    private final Context mContext;

    private CheckBox mOpStateCheck;
    private RadioGroup mOpStateGroup;
    private CheckBox mOpModeCheck;
    private RadioGroup mOpModeGroup;
    private CheckBox mFlowDirCheck;
    private RadioGroup mFlowDirGroup;
    private CheckBox mFlowModeCheck;
    private RadioGroup mFlowModeGroup;
    private CheckBox mFanSpeedCheck;
    private FloatRangeView mFanSpeedText;
    private FloatSeekBar mFanSpeedSeek;
    private CheckBox mTempResCheck;
    private TextView mTempResText;
    private RadioGroup mTempResGroup;
    private CheckBox mReqTempCheck;
    private FloatRangeView mReqTempText;
    private FloatSeekBar mReqTempSeek;
    private CheckBox mCurTempCheck;
    private FloatRangeView mCurTempText;
    private FloatSeekBar mCurTempSeek;


    public AirConditionerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mOpStateCheck = findViewById(R.id.op_state_check);
        mOpStateCheck.setChecked(true);
        mOpStateGroup = findViewById(R.id.op_state_group);

        mOpModeCheck = findViewById(R.id.op_mode_check);
        mOpModeGroup = findViewById(R.id.op_mode_group);

        mFlowDirCheck = findViewById(R.id.flow_dir_check);
        mFlowDirGroup = findViewById(R.id.flow_dir_group);

        mFlowModeCheck = findViewById(R.id.fan_mode_check);
        mFlowModeGroup = findViewById(R.id.fan_mode_group);

        mFanSpeedCheck = findViewById(R.id.fan_speed_check);
        mFanSpeedSeek = findViewById(R.id.fan_speed_seek);
        mFanSpeedSeek.setOnFloatSeekBarChangeListener(this);
        mFanSpeedText = findViewById(R.id.fan_speed_text);
        mFanSpeedText.setOnValueEditedListener(this);
        mFanSpeedText.setEditable(isSlave());

        mTempResCheck = findViewById(R.id.temp_res_check);
        mTempResText = findViewById(R.id.temp_res_text);
        mTempResText.setVisibility(isSlave() ? View.GONE : View.VISIBLE);
        mTempResGroup = findViewById(R.id.temp_res_group);
        mTempResGroup.setVisibility(isSlave() ? View.VISIBLE : View.GONE);

        mReqTempCheck = findViewById(R.id.req_temp_check);
        mReqTempSeek = findViewById(R.id.req_temp_seek);
        mReqTempSeek.setOnFloatSeekBarChangeListener(this);
        mReqTempSeek.setEnabled(!isSlave());
        mReqTempText = findViewById(R.id.req_temp_text);
        mReqTempText.setOnValueEditedListener(this);
        mReqTempText.setEditable(!isSlave());

        mCurTempCheck = findViewById(R.id.cur_temp_check);
        mCurTempSeek = findViewById(R.id.cur_temp_seek);
        mCurTempSeek.setOnFloatSeekBarChangeListener(this);
        mCurTempSeek.setEnabled(isSlave());
        mCurTempText = findViewById(R.id.cur_temp_text);
        mCurTempText.setOnValueEditedListener(this);
        mCurTempText.setEditable(isSlave());
    }

    @Override
    public void onUpdateProperty(PropertyMap props, PropertyMap changed) {
        final boolean onoff = props.get(HomeDevice.PROP_ONOFF, Boolean.class);
        mOpStateGroup.setOnCheckedChangeListener(null);
        mOpStateGroup.check((onoff) ? R.id.op_state_on_radio : R.id.op_state_off_radio);
        mOpStateGroup.setOnCheckedChangeListener(this);

        int opModeId = R.id.op_mode_auto_radio;
        int opMode = props.get(AirConditioner.PROP_OPERATION_MODE, Integer.class);
        if (opMode == AirConditioner.OpMode.AUTO) opModeId = R.id.op_mode_auto_radio;
        else if (opMode == AirConditioner.OpMode.COOLING) opModeId = R.id.op_mode_cooling_radio;
        else if (opMode == AirConditioner.OpMode.HEATING) opModeId = R.id.op_mode_heating_radio;
        else if (opMode == AirConditioner.OpMode.BLOWING) opModeId = R.id.op_mode_blowing_radio;
        else if (opMode == AirConditioner.OpMode.DEHUMID) opModeId = R.id.op_mode_dehumid_radio;
        else if (opMode == AirConditioner.OpMode.RESERVED) opModeId = R.id.op_mode_reserved_radio;
        mOpModeGroup.setOnCheckedChangeListener(null);
        mOpModeGroup.check(opModeId);
        mOpModeGroup.setOnCheckedChangeListener(this);

        int flowDirId = R.id.flow_dir_manual_radio;
        int flowDir = props.get(AirConditioner.PROP_FLOW_DIRECTION, Integer.class);
        if (flowDir == AirConditioner.FlowDir.MANUAL) flowDirId = R.id.flow_dir_manual_radio;
        else if (flowDir == AirConditioner.FlowDir.AUTO) flowDirId = R.id.flow_dir_auto_radio;
        mFlowDirGroup.setOnCheckedChangeListener(null);
        mFlowDirGroup.check(flowDirId);
        mFlowDirGroup.setOnCheckedChangeListener(this);

        int fanModeId = R.id.fan_mode_manual_radio;
        int fanMode = props.get(AirConditioner.PROP_FAN_MODE, Integer.class);
        if (fanMode == AirConditioner.FanMode.MANUAL) fanModeId = R.id.fan_mode_manual_radio;
        else if (fanMode == AirConditioner.FanMode.AUTO) fanModeId = R.id.fan_mode_auto_radio ;
        else if (fanMode == AirConditioner.FanMode.NATURAL) fanModeId = R.id.fan_mode_natural_radio;
        mFlowModeGroup.setOnCheckedChangeListener(null);
        mFlowModeGroup.check(fanModeId);
        mFlowModeGroup.setOnCheckedChangeListener(this);

        mFanSpeedSeek.setResolution(1.0f);
        mFanSpeedSeek.setMinF((float)props.get(AirConditioner.PROP_MIN_FAN_SPEED, Integer.class));
        mFanSpeedSeek.setMaxF((float)props.get(AirConditioner.PROP_MAX_FAN_SPEED, Integer.class));
        mFanSpeedSeek.setProgressF((float)props.get(AirConditioner.PROP_CUR_FAN_SPEED, Integer.class));
        mFanSpeedText.setRes(1.0f);
        mFanSpeedText.setMin((float)props.get(AirConditioner.PROP_MIN_FAN_SPEED, Integer.class));
        mFanSpeedText.setMax((float)props.get(AirConditioner.PROP_MAX_FAN_SPEED, Integer.class));
        mFanSpeedText.setCur((float)props.get(AirConditioner.PROP_CUR_FAN_SPEED, Integer.class));

        final float tempRes = props.get(AirConditioner.PROP_TEMP_RESOLUTION, Float.class);
        mTempResText.setText("" + tempRes);
        final int tempResId = Utils.floatEquals(tempRes, 0.5f) ? R.id.temp_res_0d5_radio : R.id.temp_res_1d0_radio;
        mTempResGroup.setOnCheckedChangeListener(null);
        mTempResGroup.check(tempResId);
        mTempResGroup.setOnCheckedChangeListener(this);

        mReqTempSeek.setResolution(tempRes);
        mReqTempSeek.setMinF(props.get(AirConditioner.PROP_MIN_TEMPERATURE, Float.class));
        mReqTempSeek.setMaxF(props.get(AirConditioner.PROP_MAX_TEMPERATURE, Float.class));
        mReqTempSeek.setProgressF(props.get(AirConditioner.PROP_REQ_TEMPERATURE, Float.class));
        mReqTempText.setRes(tempRes);
        mReqTempText.setMin(props.get(AirConditioner.PROP_MIN_TEMPERATURE, Float.class));
        mReqTempText.setMax(props.get(AirConditioner.PROP_MAX_TEMPERATURE, Float.class));
        mReqTempText.setCur(props.get(AirConditioner.PROP_REQ_TEMPERATURE, Float.class));

        mCurTempSeek.setResolution(tempRes);
        mCurTempSeek.setMinF(props.get(AirConditioner.PROP_MIN_TEMPERATURE, Float.class));
        mCurTempSeek.setMaxF(props.get(AirConditioner.PROP_MAX_TEMPERATURE, Float.class));
        mCurTempSeek.setProgressF(props.get(AirConditioner.PROP_CUR_TEMPERATURE, Float.class));
        mCurTempText.setRes(tempRes);
        mCurTempText.setMin(props.get(AirConditioner.PROP_MIN_TEMPERATURE, Float.class));
        mCurTempText.setMax(props.get(AirConditioner.PROP_MAX_TEMPERATURE, Float.class));
        mCurTempText.setCur(props.get(AirConditioner.PROP_CUR_TEMPERATURE, Float.class));
    }

    @Override
    public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
        if (group == mOpStateGroup) {
            boolean onoff = (checkedId == R.id.op_state_on_radio);
            device().setProperty(HomeDevice.PROP_ONOFF, Boolean.class, onoff);
        } else if (group == mOpModeGroup) {
            int opMode = AirConditioner.OpMode.AUTO;
            if (checkedId == R.id.op_mode_auto_radio) opMode = AirConditioner.OpMode.AUTO;
            else if (checkedId == R.id.op_mode_cooling_radio) opMode = AirConditioner.OpMode.COOLING;
            else if (checkedId == R.id.op_mode_heating_radio) opMode = AirConditioner.OpMode.HEATING;
            else if (checkedId == R.id.op_mode_blowing_radio) opMode = AirConditioner.OpMode.BLOWING;
            else if (checkedId == R.id.op_mode_dehumid_radio) opMode = AirConditioner.OpMode.DEHUMID;
            else if (checkedId == R.id.op_mode_reserved_radio) opMode = AirConditioner.OpMode.RESERVED;
            device().setProperty(AirConditioner.PROP_OPERATION_MODE, Integer.class, opMode);
        } else if (group == mFlowDirGroup) {
            int flowDir = AirConditioner.FlowDir.MANUAL;
            if (checkedId == R.id.flow_dir_manual_radio) flowDir = AirConditioner.FlowDir.MANUAL;
            else if (checkedId == R.id.flow_dir_auto_radio) flowDir = AirConditioner.FlowDir.AUTO;
            device().setProperty(AirConditioner.PROP_FLOW_DIRECTION, Integer.class, flowDir);
        } else if (group == mFlowModeGroup) {
            int fanMode = AirConditioner.FanMode.MANUAL;
            if (checkedId == R.id.fan_mode_manual_radio) fanMode = AirConditioner.FanMode.MANUAL;
            else if (checkedId == R.id.fan_mode_auto_radio) fanMode = AirConditioner.FanMode.AUTO;
            else if (checkedId == R.id.fan_mode_natural_radio) fanMode = AirConditioner.FanMode.NATURAL;
            device().setProperty(AirConditioner.PROP_FAN_MODE, Integer.class, fanMode);
        } else if (group == mTempResGroup) {
            float tempRes = 1.0f;
            if (checkedId == R.id.temp_res_0d5_radio) tempRes = 0.5f;
            else if (checkedId == R.id.temp_res_1d0_radio) tempRes = 1.0f;
            device().setProperty(AirConditioner.PROP_TEMP_RESOLUTION, Float.class, tempRes);
        }
    }

    @Override
    public void onProgressChanged(FloatSeekBar seekBar, float progress, boolean fromUser) {
        if (seekBar == mFanSpeedSeek) {
            mFanSpeedText.setCur(progress);
        } else if (seekBar == mReqTempSeek) {
            mReqTempText.setCur(progress);
        } else if (seekBar == mCurTempSeek) {
            mCurTempText.setCur(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(FloatSeekBar seekBar) { }

    @Override
    public void onStopTrackingTouch(FloatSeekBar seekBar) {
        if (seekBar == mFanSpeedSeek) {
            device().setProperty(AirConditioner.PROP_FAN_MODE, Integer.class, AirConditioner.FanMode.MANUAL);
            device().setProperty(AirConditioner.PROP_CUR_FAN_SPEED, Integer.class, (int) seekBar.getProgressF());
        } else if (seekBar == mReqTempSeek) {
            device().setProperty(AirConditioner.PROP_REQ_TEMPERATURE, Float.class, seekBar.getProgressF());
        } else if (seekBar == mCurTempSeek) {
            device().setProperty(AirConditioner.PROP_CUR_TEMPERATURE, Float.class, seekBar.getProgressF());
        }
    }

    @Override
    public void onRangeValueEdited(FloatRangeView view, float cur, float min, float max, float res) {
        if (view == mFanSpeedText) {
            device().setProperty(AirConditioner.PROP_CUR_FAN_SPEED, Integer.class, (int)cur);
            device().setProperty(AirConditioner.PROP_MIN_FAN_SPEED, Integer.class, (int)min);
            device().setProperty(AirConditioner.PROP_MAX_FAN_SPEED, Integer.class, (int)max);
        } else if (view == mReqTempText) {
            device().setProperty(AirConditioner.PROP_REQ_TEMPERATURE, Float.class, cur);
            device().setProperty(AirConditioner.PROP_MIN_TEMPERATURE, Float.class, min);
            device().setProperty(AirConditioner.PROP_MAX_TEMPERATURE, Float.class, max);
        } else if (view == mCurTempText) {
            device().setProperty(AirConditioner.PROP_CUR_TEMPERATURE, Float.class, cur);
            device().setProperty(AirConditioner.PROP_MIN_TEMPERATURE, Float.class, min);
            device().setProperty(AirConditioner.PROP_MAX_TEMPERATURE, Float.class, max);
        }
    }
}
