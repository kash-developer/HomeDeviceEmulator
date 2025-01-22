package kr.or.kashi.hde.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import kr.or.kashi.hde.R;
import kr.or.kashi.hde.util.Utils;

public class FloatRangeView extends LinearLayout {

    public interface OnValueEditedListener {
        void onRangeValueEdited(FloatRangeView view, float cur, float min, float max, float res);
    }

    private final Context mContext;
    private final Handler mCallckHandler;
    private OnValueEditedListener mOnValueEditedListener;

    private ViewGroup mCurGroup;
    private TextView mCurText;
    private EditText mCurEdit;
    private TextView mMinText;
    private EditText mMinEdit;
    private TextView mMaxText;
    private EditText mMaxEdit;
    private ViewGroup mResGroup;
    private TextView mResText;
    private EditText mResEdit;

    private float mCur = 0.0f;
    private float mMin = 0.0f;
    private float mMax = 0.0f;
    private float mRes = 1.0f;

    public FloatRangeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mCallckHandler = new Handler(Looper.getMainLooper());

        View.inflate(context, R.layout.float_range_view, this);

        mCurGroup = findViewById(R.id.cur_group);
        mCurText = findViewById(R.id.cur_text);
        mCurEdit = findViewById(R.id.cur_edit);
        mCurEdit.setOnEditorActionListener((view, actionId, event) -> {
            return onUpdateEdit((EditText)view, actionId, mCur);
        });
        mCurEdit.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) onUpdateEdit((EditText)view, EditorInfo.IME_ACTION_DONE, mCur);
        });

        mMinText = findViewById(R.id.min_text);
        mMinEdit = findViewById(R.id.min_edit);
        mMinEdit.setOnEditorActionListener((view, actionId, event) -> {
            return onUpdateEdit((EditText)view, actionId, mMin);
        });
        mMinEdit.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) onUpdateEdit((EditText)view, EditorInfo.IME_ACTION_DONE, mMin);
        });

        mMaxText = findViewById(R.id.max_text);
        mMaxEdit = findViewById(R.id.max_edit);
        mMaxEdit.setOnEditorActionListener((view, actionId, event) -> {
            return onUpdateEdit((EditText)view, actionId, mMax);
        });
        mMaxEdit.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) onUpdateEdit((EditText)view, EditorInfo.IME_ACTION_DONE, mMax);
        });

        mResGroup = findViewById(R.id.res_group);
        mResText = findViewById(R.id.res_text);
        mResEdit = findViewById(R.id.res_edit);
        mResEdit.setOnEditorActionListener((view, actionId, event) -> {
            return onUpdateEdit((EditText)view, actionId, mRes);
        });
        mResEdit.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) onUpdateEdit((EditText)view, EditorInfo.IME_ACTION_DONE, mRes);
        });

        setEditable(false, false, false, false);
        refreshTextFormat();
    }

    public void setOnValueEditedListener(OnValueEditedListener l) {
        mOnValueEditedListener = l;
    }

    protected boolean onUpdateEdit(EditText editText, int actionId, float oldValue) {
        if (actionId != EditorInfo.IME_ACTION_DONE) {
            return false;
        }

        Utils.hideKeyboard(mContext, editText);

        float value = oldValue;
        try {
            value = Float.parseFloat(editText.getText().toString());
        } catch (Exception e) {
        }

        boolean changed = false;
             if (editText == mCurEdit) changed = setCurInternal(value, true);
        else if (editText == mMinEdit) changed = setMinInternal(value, true);
        else if (editText == mMaxEdit) changed = setMaxInternal(value, true);
        else if (editText == mResEdit) changed = setResInternal(value, true);

        if (changed) {
            callbackRangeValueEditedIf();
        }

        return true;
    }

    private void callbackRangeValueEditedIf() {
        if (mOnValueEditedListener == null) return;
        mCallckHandler.removeCallbacksAndMessages(null);
        mCallckHandler.post(() -> {
            if (mOnValueEditedListener != null) {
                mOnValueEditedListener.onRangeValueEdited(this, mCur, mMin, mMax, mRes);
            }
        });
    }

    private void refreshTextFormat() {
        setNumberText(mCurText, mCur);
        setNumberText(mCurEdit, mCur);
        setNumberText(mMinText, mMin);
        setNumberText(mMinEdit, mMin);
        setNumberText(mMaxText, mMax);
        setNumberText(mMaxEdit, mMax);
        setNumberText(mResText, mRes);
        setNumberText(mResEdit, mRes);
    }

    public void setVisible(boolean curVisible, boolean resVisible) {
        mCurGroup.setVisibility(curVisible ? View.VISIBLE : View.GONE);
        mResGroup.setVisibility(resVisible ? View.VISIBLE : View.GONE);
    }

    public void setEditable(boolean editable) {
        setEditable(editable, editable, editable, editable);
    }

    public void setEditable(boolean curEditable, boolean minEditable, boolean maxEditable, boolean resEditable) {
        mCurText.setVisibility(curEditable ? View.GONE : View.VISIBLE);
        mCurEdit.setVisibility(curEditable ? View.VISIBLE : View.GONE);
        mMinText.setVisibility(minEditable ? View.GONE : View.VISIBLE);
        mMinEdit.setVisibility(minEditable ? View.VISIBLE : View.GONE);
        mMaxText.setVisibility(maxEditable ? View.GONE : View.VISIBLE);
        mMaxEdit.setVisibility(maxEditable ? View.VISIBLE : View.GONE);
        mResText.setVisibility(resEditable ? View.GONE : View.VISIBLE);
        mResEdit.setVisibility(resEditable ? View.VISIBLE : View.GONE);
    }

    private float round(float num) {
        return Math.round(num / mRes) * mRes;
    }

    public static boolean floatEquals(float a, float b) {
        return Math.abs(a - b) < 0.005f;
    }

    private void setNumberText(TextView view, float number) {
        boolean isDecimal = (mRes == (int)mRes);
        if (isDecimal) {
            view.setText("" + ((int)number));
        } else {
            view.setText("" + number);
        }
    }

    public float getRes() {
        return mRes;
    }

    public void setRes(float res) {
        if (setResInternal(res, true)) {
            callbackRangeValueEditedIf();
        }
    }

    public boolean setResInternal(float res, boolean ensureInRange) {
        boolean changed = false;

        if (res != mRes) {
            mRes = res;
            changed = true;

            if (ensureInRange) {
                changed |= setMinInternal(mMin, true);
                changed |= setMaxInternal(mMax, true);
                changed |= setCurInternal(mCur, true);
            }

            refreshTextFormat();
        }

        return changed;
    }

    public float getCur() {
        return mCur;
    }

    public void setCur(float cur) {
        setCurInternal(cur, false);
    }

    public boolean setCurInternal(float cur, boolean ensureInRange) {
        cur = round(cur);

        if (ensureInRange) {
            if (cur < mMin) {
                cur = mMin;
            }
            if (cur > mMax) {
                cur = mMax;
            }
        }

        boolean changed = false;
        if (!floatEquals(cur, mCur)) {
            mCur = cur;
            setNumberText(mCurText, cur);
            setNumberText(mCurEdit, cur);
            changed = true;
        }

        return changed;
    }

    public float getMin() {
        return mMin;
    }

    public void setMin(float min) {
        setMinInternal(min, false);
    }

    public boolean setMinInternal(float min, boolean ensureInRange) {
        min = round(min);

        if (ensureInRange) {
            if (min > mMax) {
                min = mMax;
            }
        }

        boolean changed = false;
        if (!floatEquals(min, mMin)) {
            mMin = min;
            setNumberText(mMinText, min);
            setNumberText(mMinEdit, min);
            changed = true;
        }

        if (ensureInRange) {
            changed |= setCurInternal(mCur, ensureInRange);
        }

        return changed;
    }

    public float getMax() {
        return mMax;
    }

    public void setMax(float max) {
        setMaxInternal(max, false);
    }

    public boolean setMaxInternal(float max, boolean ensureInRange) {
        max = round(max);

        if (ensureInRange) {
            if (max < mMin) {
                max = mMin;
            }
        }

        boolean changed = false;
        if (!floatEquals(max, mMax)) {
            mMax = max;
            setNumberText(mMaxText, max);
            setNumberText(mMaxEdit, max);
            changed = true;
        }

        if (ensureInRange) {
            changed |= setCurInternal(mCur, ensureInRange);
        }

        return changed;
    }
}
