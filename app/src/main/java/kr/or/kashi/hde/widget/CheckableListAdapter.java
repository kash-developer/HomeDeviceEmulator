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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.List;
import java.util.Set;

import kr.or.kashi.hde.R;

public class CheckableListAdapter<T> extends BaseAdapter {
    private Context mContext;
    private Set<T> mSelectedItems;
    private List<T> mAllItems;
    private Runnable mChangeRunnable;

    public CheckableListAdapter(Context context, List<T> allItems, Set<T> selectedItems) {
        mContext = context;
        mAllItems = allItems;
        mSelectedItems = selectedItems;
    }

    public void setChangeRunnable(Runnable runnable) {
        mChangeRunnable = runnable;
    }

    @Override
    public int getCount() {
        return mAllItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mAllItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;
        if (convertView == null ) {
            LayoutInflater layoutInflator = LayoutInflater.from(mContext);
            convertView = layoutInflator.inflate(R.layout.checkable_list_item, parent, false);

            holder = new ViewHolder();
            holder.mTextView = convertView.findViewById(R.id.text);
            holder.mCheckBox = convertView.findViewById(R.id.checkbox);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final int listPos = position;
        holder.mTextView.setText(mAllItems.get(listPos).toString());

        final T item = mAllItems.get(listPos);
        boolean isSel = mSelectedItems.contains(item);

        holder.mCheckBox.setOnCheckedChangeListener(null);
        holder.mCheckBox.setChecked(isSel);

        holder.mCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mSelectedItems.add(item);
                } else {
                    mSelectedItems.remove(item);
                }

                if (mChangeRunnable != null) {
                    mChangeRunnable.run();
                }
            }
        });

        holder.mTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                holder.mCheckBox.toggle();
            }
        });

        return convertView;
    }

    private class ViewHolder {
        private TextView mTextView;
        private CheckBox mCheckBox;
    }
}
