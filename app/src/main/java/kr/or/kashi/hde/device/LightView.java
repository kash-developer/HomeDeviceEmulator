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
import android.widget.RadioButton;
import android.widget.SeekBar;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.widget.HomeDeviceView;

public class LightView extends HomeDeviceView<Light> implements SeekBar.OnSeekBarChangeListener {
    private static final String TAG = LightView.class.getSimpleName();
    private final Context mContext;

    private RadioButton mLightOffRadio;
    private RadioButton mLightOnRadio;
    private SeekBar mLightDimmingSeekBar;
    private SeekBar mLightColorSeekBar;

    public LightView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mLightOffRadio = findViewById(R.id.light_off_radio);
        mLightOffRadio.setOnClickListener(v -> device().setOn(false));

        mLightOnRadio = findViewById(R.id.light_on_radio);
        mLightOnRadio.setOnClickListener(v -> device().setOn(true));

        mLightDimmingSeekBar = findViewById(R.id.light_dimming_seekbar);
        mLightDimmingSeekBar.setOnSeekBarChangeListener(this);

        mLightColorSeekBar = findViewById(R.id.light_color_seekbar);
        mLightColorSeekBar.setOnSeekBarChangeListener(this);
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
    public void onUpdateProperty(PropertyMap props) {
        final boolean onoff = device().getProperty(HomeDevice.PROP_ONOFF, Boolean.class);
        mLightOffRadio.setChecked(!onoff);
        mLightOnRadio.setChecked(onoff);

        final boolean dimSupported = device().getProperty(Light.PROP_DIM_SUPPORTED, Boolean.class);
        mLightDimmingSeekBar.setEnabled(dimSupported);
        if (dimSupported) {
            mLightDimmingSeekBar.setMin(device().getProperty(Light.PROP_MIN_DIM_LEVEL, Integer.class));
            mLightDimmingSeekBar.setMax(device().getProperty(Light.PROP_MAX_DIM_LEVEL, Integer.class));
            mLightDimmingSeekBar.setProgress(device().getCurDimLevel(), true);
        }

        final boolean toneSupported = device().getProperty(Light.PROP_TONE_SUPPORTED, Boolean.class);
        mLightColorSeekBar.setEnabled(toneSupported);
        if (toneSupported) {
            mLightColorSeekBar.setMin(device().getProperty(Light.PROP_MIN_TONE_LEVEL, Integer.class));
            mLightColorSeekBar.setMax(device().getProperty(Light.PROP_MAX_TONE_LEVEL, Integer.class));
            mLightColorSeekBar.setProgress(device().getCurToneLevel(), true);
        }
    }
}
