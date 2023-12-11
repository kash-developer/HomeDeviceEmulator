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

package kr.or.kashi.hde.widget;

import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class FlowLayout extends ViewGroup {
    private final Delegate mDelegate = new Delegate(this);
    private final Point mMeasuredSize = new Point();

    public FlowLayout(Context context) {
        super(context);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Point measuredSize = mDelegate.measure(widthMeasureSpec, heightMeasureSpec, mMeasuredSize);
        setMeasuredDimension(measuredSize.x, measuredSize.y);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        if (p instanceof LayoutParams) {
            return true;
        }
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mDelegate.layout(changed, l, t, r, b);
    }

    public static class Delegate {
        private final ViewGroup mViewGroup;
        private int mLineHeight;

        public Delegate(ViewGroup viewGroup) {
            mViewGroup = viewGroup;
        }

        public Point measure(int widthMeasureSpec, int heightMeasureSpec, Point outMeasuredSize) {
            final int count = mViewGroup.getChildCount();

            final int paddingTop = mViewGroup.getPaddingTop();
            final int paddingBottom = mViewGroup.getPaddingBottom();
            final int paddingLeft = mViewGroup.getPaddingLeft();
            final int paddingRight = mViewGroup.getPaddingRight();

            final int width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight;
            int height = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom;

            int x = paddingLeft;
            int y = paddingTop;
            int lineHeight = 0;

            int childHeightMeasureSpec;
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
                childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
            } else {
                childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            }

            for (int i = 0; i < count; i++) {
                final View child = mViewGroup.getChildAt(i);
                if (child.getVisibility() != GONE) {
                    child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                            childHeightMeasureSpec);
                    final int childWidth = child.getMeasuredWidth();
                    lineHeight = Math.max(lineHeight, child.getMeasuredHeight());

                    if (x + childWidth > width) {
                        x = paddingLeft;
                        y += lineHeight;
                    }

                    x += childWidth;
                }
            }
            mLineHeight = lineHeight;

            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                height = y + lineHeight;
            } else if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
                if (y + lineHeight < height) {
                    height = y + lineHeight;
                }
            }

            outMeasuredSize.x = width;
            outMeasuredSize.y = height;

            return outMeasuredSize;
        }

        public void layout(boolean changed, int l, int t, int r, int b) {
            final int count = mViewGroup.getChildCount();
            final int width = r - l;
            int x = mViewGroup.getPaddingLeft();
            int y = mViewGroup.getPaddingTop();

            for (int i = 0; i < count; i++) {
                final View child = mViewGroup.getChildAt(i);
                if (child.getVisibility() != GONE) {
                    final int childWidth = child.getMeasuredWidth();
                    final int childHeight = child.getMeasuredHeight();
                    if (x + childWidth > width) {
                        x = mViewGroup.getPaddingLeft();
                        y += mLineHeight;
                    }
                    child.layout(x, y, x + childWidth, y + childHeight);
                    x += childWidth;
                }
            }
        }
    }
}
