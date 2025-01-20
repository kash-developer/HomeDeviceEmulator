/*
 * Copyright (C) 2025 Korea Association of AI Smart Home.
 * Copyright (C) 2025 KyungDong Navien Co, Ltd.
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

package kr.or.kashi.hde.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.SeekBar;

public class FloatSeekBar extends SeekBar {
    public interface OnSeekBarChangeListener {
        default void onProgressChanged(FloatSeekBar seekBar, float progress, boolean fromUser) {}
        default void onStartTrackingTouch(FloatSeekBar seekBar) {}
        default void onStopTrackingTouch(FloatSeekBar seekBar) {}
    }

    private float mResolution = 1.0f;

    public FloatSeekBar(Context context) {
        super(context);
    }

    public FloatSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FloatSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public float getResolution() {
        return mResolution;
    }

    public void setResolution(float res) {
        mResolution = (float) (Math.floor(res * 10.0) / 10.0);
    }

    private int steps() {
        return steps(1.0f);
    }

    private int steps(float value) {
        return (int) (value / mResolution);
    }

    public float getProgressF() {
        return (float) (super.getProgress() * mResolution);
    }

    public void setProgressF(float progress) {
        super.setProgress(steps(progress));
    }

    public float getMinF() {
        return (float) (super.getMin() / steps());
    }

    public void setMinF(float min) {
        super.setMin(steps(min));
    }

    public float getMaxF() {
        return (float) (super.getMax() / steps());
    }

    public void setMaxF(float max) {
        super.setMax(steps(max));
    }

    @Override
    public void setOnSeekBarChangeListener(SeekBar.OnSeekBarChangeListener l) {
        if (l == null) {
            super.setOnSeekBarChangeListener(null);
            return;
        }

        super.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                l.onProgressChanged(seekBar, (int) (progress / steps()), fromUser);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                l.onStartTrackingTouch(seekBar);
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                l.onStopTrackingTouch(seekBar);
            }
        });
    }

    public void setOnFloatSeekBarChangeListener(FloatSeekBar.OnSeekBarChangeListener l) {
        super.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                l.onProgressChanged(FloatSeekBar.this, (float) (progress * mResolution), fromUser);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                l.onStartTrackingTouch(FloatSeekBar.this);
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                l.onStopTrackingTouch(FloatSeekBar.this);
            }
        });
    }
}
