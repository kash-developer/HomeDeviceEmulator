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

import androidx.annotation.Nullable;
import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.util.Utils;
import kr.or.kashi.hde.view.HomeDeviceView;

public class LightView extends HomeDeviceView<Light> implements SeekBar.OnSeekBarChangeListener {
    private static final String TAG = LightView.class.getSimpleName();
    private final Context mContext;

    private CheckBox mLightStateCheck;
    private RadioButton mLightOffRadio;
    private RadioButton mLightOnRadio;
    private CheckBox mLightDimmingCheck;
    private TextView mLightCurDimmingText;
    private SeekBar mLightCurDimmingSeek;
    private EditText mLightMaxDimmingEdit;
    private CheckBox mLightColorCheck;
    private TextView mLightCurColorText;
    private SeekBar mLightCurColorSeek;
    private EditText mLightMaxColorEdit;

    public LightView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mLightStateCheck = findViewById(R.id.light_state_check);
        mLightStateCheck.setChecked(true);
        mLightStateCheck.setEnabled(false);
        mLightOffRadio = findViewById(R.id.light_off_radio);
        mLightOffRadio.setOnClickListener(v -> device().setOn(false));
        mLightOnRadio = findViewById(R.id.light_on_radio);
        mLightOnRadio.setOnClickListener(v -> device().setOn(true));

        mLightDimmingCheck = findViewById(R.id.light_dimming_check);
        mLightDimmingCheck.setEnabled(isSlave());
        mLightDimmingCheck.setOnClickListener(v -> device().setProperty(Light.PROP_DIM_SUPPORTED, Boolean.class, mLightDimmingCheck.isChecked()));
        mLightCurDimmingText = findViewById(R.id.light_cur_dimming_text);
        mLightCurDimmingSeek = findViewById(R.id.light_cur_dimming_seekbar);
        mLightCurDimmingSeek.setOnSeekBarChangeListener(this);
        mLightMaxDimmingEdit = findViewById(R.id.light_max_dimming_edit);
        mLightMaxDimmingEdit.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onUpdateMaxDimEdit((EditText)view);
            return false;
        });
        mLightMaxDimmingEdit.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) onUpdateMaxDimEdit((EditText)view);
        });

        mLightColorCheck = findViewById(R.id.light_color_check);
        mLightColorCheck.setEnabled(isSlave());
        mLightColorCheck.setOnClickListener(v -> device().setProperty(Light.PROP_TONE_SUPPORTED, Boolean.class, mLightColorCheck.isChecked()));
        mLightCurColorText = findViewById(R.id.light_cur_color_text);
        mLightCurColorSeek = findViewById(R.id.light_cur_color_seekbar);
        mLightCurColorSeek.setOnSeekBarChangeListener(this);
        mLightMaxColorEdit = findViewById(R.id.light_max_color_edit);
        mLightMaxColorEdit.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onUpdateMaxToneEdit((EditText)view);
            return false;
        });
        mLightMaxColorEdit.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) onUpdateMaxToneEdit((EditText)view);
        });
    }

    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
    @Override public void onStartTrackingTouch(SeekBar seekBar) { }
    @Override public void onStopTrackingTouch(SeekBar seekBar) {
        if (seekBar == mLightCurDimmingSeek) {
            device().setCurDimLevel(mLightCurDimmingSeek.getProgress());
        } else if (seekBar == mLightCurColorSeek) {
            device().setCurToneLevel(mLightCurColorSeek.getProgress());
        }
    }

    @Override
    public void onUpdateProperty(PropertyMap props, PropertyMap changed) {
        final boolean onoff = props.get(HomeDevice.PROP_ONOFF, Boolean.class);
        mLightOffRadio.setChecked(!onoff);
        mLightOnRadio.setChecked(onoff);

        final boolean dimSupported = props.get(Light.PROP_DIM_SUPPORTED, Boolean.class);
        mLightDimmingCheck.setChecked(dimSupported);
        mLightCurDimmingSeek.setEnabled(dimSupported && onoff);
        mLightMaxDimmingEdit.setEnabled(dimSupported && isSlave());
        if (dimSupported) {
            final int min = props.get(Light.PROP_MIN_DIM_LEVEL, Integer.class);
            final int max = props.get(Light.PROP_MAX_DIM_LEVEL, Integer.class);
            final int cur = device().getCurDimLevel();
            mLightCurDimmingSeek.setMin(min);
            mLightCurDimmingSeek.setMax(max);
            mLightCurDimmingSeek.setProgress(cur, true);
            mLightCurDimmingText.setText("" + cur);
            mLightMaxDimmingEdit.setText("" + max);
        } else {
            mLightCurDimmingText.setText("");
        }

        final boolean toneSupported = props.get(Light.PROP_TONE_SUPPORTED, Boolean.class);
        mLightColorCheck.setChecked(toneSupported);
        mLightCurColorSeek.setEnabled(toneSupported && onoff);
        mLightMaxColorEdit.setEnabled(dimSupported && isSlave());
        if (toneSupported) {
            final int min = props.get(Light.PROP_MIN_TONE_LEVEL, Integer.class);
            final int max = props.get(Light.PROP_MAX_TONE_LEVEL, Integer.class);
            final int cur = device().getCurToneLevel();
            mLightCurColorSeek.setMin(min);
            mLightCurColorSeek.setMax(max);
            mLightCurColorSeek.setProgress(cur, true);
            mLightCurColorText.setText("" + cur);
            mLightMaxColorEdit.setText("" + max);
        } else {
            mLightCurColorText.setText("");
        }
    }

    private void onUpdateMaxDimEdit(EditText editText) {
        Utils.hideKeyboard(mContext, editText);
        int max = setMaxDimLevel(Integer.parseInt(editText.getText().toString()));
        editText.setText("" + max);
    }

    private void onUpdateMaxToneEdit(EditText editText) {
        Utils.hideKeyboard(mContext, editText);
        int max = setMaxToneLevel(Integer.parseInt(editText.getText().toString()));
        editText.setText("" + max);
    }

    private int setMaxDimLevel(int value) {
        int min = device().getProperty(Light.PROP_MIN_DIM_LEVEL, Integer.class);
        int max = Math.max(min, Math.min(value, 15));
        int cur = device().getCurDimLevel();

        cur = Math.min(cur, max);
        cur = Math.max(cur, min);

        device().setProperty(Light.PROP_MIN_DIM_LEVEL, Integer.class, min);
        device().setProperty(Light.PROP_MAX_DIM_LEVEL, Integer.class, max);
        device().setCurDimLevel(cur);

        return max;
    }

    private int setMaxToneLevel(int value) {
        int min = device().getProperty(Light.PROP_MIN_TONE_LEVEL, Integer.class);
        int max = Math.max(min, Math.min(value, 15));
        int cur = device().getCurToneLevel();

        cur = Math.min(cur, max);
        cur = Math.max(cur, min);

        device().setProperty(Light.PROP_MIN_TONE_LEVEL, Integer.class, min);
        device().setProperty(Light.PROP_MAX_TONE_LEVEL, Integer.class, max);
        device().setCurToneLevel(cur);

        return max;
    }
}
