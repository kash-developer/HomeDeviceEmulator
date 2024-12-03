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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.R;

public class DeviceTypeListAdapter extends BaseAdapter {
    private Context mContext;
    private Set<String> mSelectedTypes;
    private List<String> mAllTypes;
    private List<String> mAddresses;
    private Callback mCallback;

    public interface Callback {
        default void onCheckedChanged(String item, boolean checked) {}
        default void onAddButtonClicked(String item) {}
    }

    public DeviceTypeListAdapter(Context context, List<String> allItems, Set<String> selectedItemsRef) {
        mContext = context;
        mAllTypes = allItems;
        mAddresses = new ArrayList<>(allItems.size());
        mSelectedTypes = selectedItemsRef;

        for (int i=0; i<allItems.size(); i++) {
            mAddresses.add("");
        }
    }

    public void setSelctedItemsRef(Set<String> selectedItemsRef) {
        mSelectedTypes = selectedItemsRef;
        notifyDataSetChanged();
    }

    public String getAddress(int position) {
        return mAddresses.get(position);
    }

    public String getAddress(String item) {
        int pos = mAllTypes.indexOf(item);
        if (pos < 0) return "";
        return mAddresses.get(pos);
    }

    public void setAddress(int position, String addr) {
        mAddresses.set(position, addr);
        notifyDataSetChanged();
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public int getCount() {
        return mAllTypes.size();
    }

    @Override
    public Object getItem(int position) {
        return mAllTypes.get(position);
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
            convertView = layoutInflator.inflate(R.layout.device_type_list_item, parent, false);

            holder = new ViewHolder();
            holder.mTextView = convertView.findViewById(R.id.text);
            holder.mCheckBox = convertView.findViewById(R.id.checkbox);
            holder.mAddrTextView = convertView.findViewById(R.id.address_text);
            holder.mAddButton = convertView.findViewById(R.id.add_button);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final int pos = position;
        holder.mTextView.setText(mAllTypes.get(pos).toString());
        holder.mAddrTextView.setText(mAddresses.get(pos).toString());

        final String item = mAllTypes.get(pos);
        boolean isSel = mSelectedTypes.contains(item);

        holder.mCheckBox.setOnCheckedChangeListener(null);
        holder.mCheckBox.setChecked(isSel);

        holder.mCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mSelectedTypes.add(item);
                } else {
                    mSelectedTypes.remove(item);
                }

                if (mCallback != null) {
                    mCallback.onCheckedChanged(item, isChecked);
                }
            }
        });

        holder.mTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                holder.mCheckBox.toggle();
            }
        });

        holder.mAddButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCallback != null) {
                    mCallback.onAddButtonClicked(item);
                }
            }
        });

        return convertView;
    }

    private class ViewHolder {
        private CheckBox mCheckBox;
        private TextView mTextView;
        private TextView mAddrTextView;
        private Button mAddButton;
    }
}
