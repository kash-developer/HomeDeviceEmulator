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

package kr.or.kashi.hde.view;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import kr.or.kashi.hde.R;

public class TypeListAdapter extends BaseAdapter {
    private Context mContext;
    private Set<String> mSelectedTypes;
    private List<String> mAllTypes;
    private List<String> mAddresses;
    private Callback mCallback;

    public interface Callback {
        default void onCheckedChanged(String item, boolean checked) {}
        default void onAddressClicked(String item) {}
        default void onPrevButtonClicked(String item) {}
        default void onNextButtonClicked(String item) {}
        default void onAddButtonClicked(String item, boolean longClick) {}
    }

    public TypeListAdapter(Context context, List<String> allItems, Set<String> selectedItemsRef) {
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

    public void setAddresses(String[] addresses) {
        final int count = Math.min(addresses.length, mAddresses.size());
        for (int i=0; i<count; i++) {
            mAddresses.set(i, addresses[i]);
        }
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
            convertView = layoutInflator.inflate(R.layout.type_list_item, parent, false);

            holder = new ViewHolder();
            holder.mTextView = convertView.findViewById(R.id.text);
            holder.mCheckBox = convertView.findViewById(R.id.checkbox);
            holder.mAddrTextView = convertView.findViewById(R.id.address_text);
            holder.mPrevButton = convertView.findViewById(R.id.prev_button);
            holder.mNextButton = convertView.findViewById(R.id.next_button);
            holder.mAddButton = convertView.findViewById(R.id.add_button);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.mTextView.setText(mAllTypes.get(position).toString());

        final String address = mAddresses.get(position).toString();
        if (address.length() == 6) {
            SpannableString spanAddrStr = new SpannableString(address.substring(2));
            spanAddrStr.setSpan(new ForegroundColorSpan(Color.BLACK), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spanAddrStr.setSpan(new ForegroundColorSpan(Color.BLACK), 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spanAddrStr.setSpan(new ForegroundColorSpan(Color.BLACK), 3, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            holder.mAddrTextView.setText(spanAddrStr);
        } else {
            holder.mAddrTextView.setText(address);
        }

        final String item = mAllTypes.get(position);
        boolean isSel = mSelectedTypes.contains(item);

        holder.mCheckBox.setOnCheckedChangeListener(null);
        holder.mCheckBox.setChecked(isSel);

        holder.mCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mSelectedTypes.add(item);
            } else {
                mSelectedTypes.remove(item);
            }

            if (mCallback != null) {
                mCallback.onCheckedChanged(item, isChecked);
            }
        });

        holder.mTextView.setOnClickListener(view -> {
            holder.mCheckBox.toggle();
        });

        holder.mPrevButton.setOnClickListener(view -> {
            if (mCallback != null) {
                mCallback.onPrevButtonClicked(item);
            }
        });

        holder.mNextButton.setOnClickListener(view -> {
            if (mCallback != null) {
                mCallback.onNextButtonClicked(item);
            }
        });

        holder.mAddrTextView.setOnClickListener(view -> {
            if (mCallback != null) {
                mCallback.onAddressClicked(item);
            }
        });

        holder.mAddButton.setOnClickListener(view -> {
            if (mCallback != null) {
                mCallback.onAddButtonClicked(item, false);
            }
        });

        holder.mAddButton.setOnLongClickListener(view -> {
            if (mCallback != null) {
                mCallback.onAddButtonClicked(item, true);
                return true;
            }
            return false;
        });

        return convertView;
    }

    private class ViewHolder {
        private CheckBox mCheckBox;
        private TextView mTextView;
        private TextView mAddrTextView;
        private Button mPrevButton;
        private Button mNextButton;
        private Button mAddButton;
    }
}
