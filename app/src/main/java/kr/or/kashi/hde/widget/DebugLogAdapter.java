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
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;

import kr.or.kashi.hde.R;

public class DebugLogAdapter extends RecyclerView.Adapter<DebugLogAdapter.Holder> {
    private static final String TAG = "DebugLogAdapter";
    private static final int MAX_ITEM_COUNT = 512;

    private final Context mContext;

    private LogItem[] mLogItems;
    private int mLogIndexStart = 0;
    private int mLogIndexEnd = 0;

    private LogItem mSelectedItem = null;

    public DebugLogAdapter(Context context) {
        mContext = context;
        mLogItems = new LogItem[MAX_ITEM_COUNT];
        for (int i=0; i<MAX_ITEM_COUNT; i++) {
            mLogItems[i] = new LogItem();
        }
    }

    public void addLog(String text) {
        LogItem item = mLogItems[mLogIndexEnd];
        item.timeStamp = SystemClock.uptimeMillis();
        item.logText = text;

        mLogIndexEnd++;
        if (mLogIndexEnd >= MAX_ITEM_COUNT) {
            mLogIndexEnd = 0;
        }

        if (mLogIndexStart == mLogIndexEnd) {
            int reduce = (MAX_ITEM_COUNT / 4);
            mLogIndexStart = mLogIndexStart + reduce;
            if (mLogIndexStart >= MAX_ITEM_COUNT) {
                mLogIndexStart = mLogIndexStart - MAX_ITEM_COUNT;
            }
            notifyDataSetChanged();
        } else {
            notifyItemInserted(getLogCount() - 1);
        }
    }

    public int getLogCount() {
        if (mLogIndexStart == mLogIndexEnd) {
            return 0;
        } else if (mLogIndexStart < mLogIndexEnd) {
            return mLogIndexEnd - mLogIndexStart;
        } else {
            return (MAX_ITEM_COUNT - mLogIndexStart) + mLogIndexEnd;
        }
    }

    public void clearAll() {
        mLogIndexStart = 0;
        mLogIndexEnd = 0;
        notifyDataSetChanged();
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(mContext).inflate(R.layout.debug_log_item, parent, false);
        return new Holder(itemView);
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        int index = mLogIndexStart + position;
        if (index >= MAX_ITEM_COUNT) {
            index -= MAX_ITEM_COUNT;
        }
        final LogItem item = mLogItems[index];
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return getLogCount();
    }

    private class LogItem {
        long timeStamp;
        String logText;
    }

    public class Holder extends RecyclerView.ViewHolder {
        public final View rootView;
        public final TextView timeText;
        public final TextView logText;
        public LogItem item;

        public Holder(View itemView) {
            super(itemView);
            rootView = itemView;
            timeText = itemView.findViewById(R.id.time_text);
            logText = itemView.findViewById(R.id.log_text);
        }

        public void bind(LogItem item) {
            this.item = item;
            updateStates();
        }

        public void updateStates() {
            Date date = new Date(this.item.timeStamp);
            String dateString = new SimpleDateFormat("HH:mm:ss").format(date);
            timeText.setText(dateString);
            logText.setText(this.item.logText);
        }
    }
}
