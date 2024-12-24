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
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.base.PropertyValue;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.Holder> {
    private static final String TAG = "DeviceListAdapter";

    private final Context mContext;
    private final List<HomeDevice> mDeviceList;
    private final Listener mListener;
    private Holder mSelectedHolder = null;
    private HomeDevice mSelectedDevice = null;

    public DeviceListAdapter(Context context, List<HomeDevice> deviceList, Listener listener) {
        mContext = context;
        mDeviceList = new ArrayList<>(deviceList);
        mListener = listener;

        Collections.sort(mDeviceList, (Object o1, Object o2) -> {
            HomeDevice d1 = (HomeDevice) o1;
            HomeDevice d2 = (HomeDevice) o2;
            return d1.getAddress().toString().compareTo(d2.getAddress().toString());
        });
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(mContext).inflate(R.layout.device_list_item, parent, false);
        return new Holder(itemView);
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        final HomeDevice device = mDeviceList.get(position);
        holder.bind(device);
        holder.setSelected((device == mSelectedDevice));
    }

    @Override
    public int getItemCount() {
        return mDeviceList.size();
    }

    private void setSelectedItem(Holder holder) {
        if (mSelectedHolder != null) {
            mSelectedHolder.setSelected(false);
        }
        if (holder != null) {
            holder.setSelected(true);
        }
        mSelectedHolder = holder;
        mSelectedDevice = holder.device;
    }

    public interface Listener {
        void onItemClicked(Holder holder);
        void onItemSwitchClicked(Holder holder, boolean isChecked);
        void onItemRemoveClicked(Holder holder);
    }

    public class Holder extends RecyclerView.ViewHolder
                        implements View.OnClickListener, HomeDevice.Callback {
        public final View rootView;
        public final TextView deviceText;
        public final TextView elapsedText;
        public final Switch onoffSwitch;
        public final Button removeButton;
        public HomeDevice device;
        private long mOnOffReqTime = -1;

        public Holder(View itemView) {
            super(itemView);
            rootView = itemView;
            rootView.setOnClickListener(this);
            deviceText = itemView.findViewById(R.id.device_text);
            elapsedText = itemView.findViewById(R.id.elapsed_text);
            onoffSwitch = itemView.findViewById(R.id.onoff_switch);
            onoffSwitch.setOnClickListener(this);
            removeButton = itemView.findViewById(R.id.remove_button);
            removeButton.setOnClickListener(this);
        }

        public void bind(HomeDevice newDevice) {
            if (this.device != null) {
                this.device.removeCallback(this);
            }

            this.device = newDevice;

            if (this.device != null) {
                this.device.addCallback(this);
            }

            updateStates();

            mOnOffReqTime = -1;
            this.elapsedText.setText("");
        }

        public void setSelected(boolean selected) {
            String selectedColor = selected ? "#FFAAAAAA" : "#00000000";
            this.rootView.setBackgroundColor(Color.parseColor(selectedColor));
            if (!selected) {
                mOnOffReqTime = -1;
                this.elapsedText.setText("");
            }
        }

        public void setConnected(boolean conntected) {
            int connectedColor = conntected ? 0xFF000000 : 0xFF888888;
            this.deviceText.setTextColor(connectedColor);
        }

        public void updateStates() {
            final String text =
                    this.device.getAddress() + ", " +
                    this.device.getName();
            this.deviceText.setText(text);

            this.onoffSwitch.setChecked(this.device.isOn());

//            this.elapsedText.setText("");

            setConnected(this.device.isConnected());
        }

        @Override
        public void onClick(View v) {
            setSelectedItem(this);
            if (v == rootView) {
                mListener.onItemClicked(this);
            } else if (v == onoffSwitch) {
                mListener.onItemSwitchClicked(this, onoffSwitch.isChecked());
                mOnOffReqTime = SystemClock.uptimeMillis();
            } else if (v == removeButton) {
                mListener.onItemRemoveClicked(this);
            }
        }

        @Override
        public void onPropertyChanged(HomeDevice device, PropertyMap props) {
            updateStates();

            if (mOnOffReqTime != -1 && props.get(HomeDevice.PROP_ONOFF) != null) {
                long elapsedTime = SystemClock.uptimeMillis() - mOnOffReqTime;
                if (elapsedTime < (2L * 1000L)) {
                    this.elapsedText.setText(elapsedTime + "ms");
                }
            }
        }

        @Override
        public void onErrorOccurred(HomeDevice device, int error) {
        }
    }
}
