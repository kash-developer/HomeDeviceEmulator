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
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.logging.Handler;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.util.Utils;
import kr.or.kashi.hde.widget.HomeDeviceView;

public class LightView extends HomeDeviceView<Light> implements SeekBar.OnSeekBarChangeListener {
    private static final String TAG = LightView.class.getSimpleName();
    private final Context mContext;

    private CheckBox mLightStateCheck;
    private RadioButton mLightOffRadio;
    private RadioButton mLightOnRadio;
    private CheckBox mLightDimmingCheck;
    private TextView mLightDimmingText;
    private SeekBar mLightDimmingSeekBar;
    private EditText mLightDimmingEdit;
    private CheckBox mLightColorCheck;
    private TextView mLightColorText;
    private SeekBar mLightColorSeekBar;
    private EditText mLightColorEdit;

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
        mLightDimmingText = findViewById(R.id.light_dimming_text);
        mLightDimmingSeekBar = findViewById(R.id.light_dimming_seekbar);
        mLightDimmingSeekBar.setOnSeekBarChangeListener(this);
        mLightDimmingEdit = findViewById(R.id.light_dimming_edit);
        mLightDimmingEdit.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP && keyCode == 5482) {
                setMaxDimLevel(Integer.parseInt(mLightDimmingEdit.getText().toString(), 10));
                Utils.hideKeyboard(mContext, view);
                return true;
            }
            return false;
        });
        mLightDimmingEdit.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                setMaxDimLevel(Integer.parseInt(view.getText().toString(), 10));
                Utils.hideKeyboard(mContext, view);
                return true;
            }
            return false;
        });

        mLightColorCheck = findViewById(R.id.light_color_check);
        mLightColorCheck.setEnabled(isSlave());
        mLightColorCheck.setOnClickListener(v -> device().setProperty(Light.PROP_TONE_SUPPORTED, Boolean.class, mLightColorCheck.isChecked()));
        mLightColorText = findViewById(R.id.light_color_text);
        mLightColorSeekBar = findViewById(R.id.light_color_seekbar);
        mLightColorSeekBar.setOnSeekBarChangeListener(this);
        mLightColorEdit = findViewById(R.id.light_color_edit);
        mLightColorEdit.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP && keyCode == 5482) {
                setMaxToneLevel(Integer.parseInt(mLightColorEdit.getText().toString(), 10));
                Utils.hideKeyboard(mContext, view);
                return true;
            }
            return false;
        });
        mLightColorEdit.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                setMaxToneLevel(Integer.parseInt(view.getText().toString(), 10));
                Utils.hideKeyboard(mContext, view);
                return true;
            }
            return false;
        });
    }

    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
    @Override public void onStartTrackingTouch(SeekBar seekBar) { }
    @Override public void onStopTrackingTouch(SeekBar seekBar) {
        if (seekBar == mLightDimmingSeekBar) {
            device().setCurDimLevel(mLightDimmingSeekBar.getProgress());
        } else if (seekBar == mLightColorSeekBar) {
            device().setCurToneLevel(mLightColorSeekBar.getProgress());
        }
    }

    @Override
    public void onUpdateProperty(PropertyMap props, PropertyMap changed) {
        final boolean onoff = props.get(HomeDevice.PROP_ONOFF, Boolean.class);
        mLightOffRadio.setChecked(!onoff);
        mLightOnRadio.setChecked(onoff);

        final boolean dimSupported = props.get(Light.PROP_DIM_SUPPORTED, Boolean.class);
        mLightDimmingCheck.setChecked(dimSupported);
        mLightDimmingSeekBar.setEnabled(dimSupported);
        if (dimSupported) {
            final int min = props.get(Light.PROP_MIN_DIM_LEVEL, Integer.class);
            final int max = props.get(Light.PROP_MAX_DIM_LEVEL, Integer.class);
            final int cur = device().getCurDimLevel();
            mLightDimmingSeekBar.setMin(min);
            mLightDimmingSeekBar.setMax(max);
            mLightDimmingSeekBar.setProgress(cur, true);
            mLightDimmingText.setText("(" + min + "~" + max + ", " + cur + ")");
        } else {
            mLightDimmingText.setText("");
        }

        final boolean toneSupported = props.get(Light.PROP_TONE_SUPPORTED, Boolean.class);
        mLightColorCheck.setChecked(toneSupported);
        mLightColorSeekBar.setEnabled(toneSupported);
        if (toneSupported) {
            final int min = props.get(Light.PROP_MIN_TONE_LEVEL, Integer.class);
            final int max = props.get(Light.PROP_MAX_TONE_LEVEL, Integer.class);
            final int cur = device().getCurToneLevel();
            mLightColorSeekBar.setMin(min);
            mLightColorSeekBar.setMax(max);
            mLightColorSeekBar.setProgress(cur, true);
            mLightColorText.setText("(" + min + "~" + max + ", " + cur + ")");
        } else {
            mLightColorText.setText("");
        }
    }

    private void setMaxDimLevel(int value) {
        int min = device().getProperty(Light.PROP_MIN_DIM_LEVEL, Integer.class);
        int max = Math.max(min, Math.min(value, 15));
        int cur = device().getCurDimLevel();

        cur = Math.min(cur, max);
        cur = Math.max(cur, min);

        device().setProperty(Light.PROP_MIN_DIM_LEVEL, Integer.class, min);
        device().setProperty(Light.PROP_MAX_DIM_LEVEL, Integer.class, max);
        device().setCurDimLevel(cur);
    }

    private void setMaxToneLevel(int value) {
        int min = device().getProperty(Light.PROP_MIN_TONE_LEVEL, Integer.class);
        int max = Math.max(min, Math.min(value, 15));
        int cur = device().getCurToneLevel();

        cur = Math.min(cur, max);
        cur = Math.max(cur, min);

        device().setProperty(Light.PROP_MIN_TONE_LEVEL, Integer.class, min);
        device().setProperty(Light.PROP_MAX_TONE_LEVEL, Integer.class, max);
        device().setCurToneLevel(cur);
    }
}
