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
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import kr.or.kashi.hde.DeviceDiscovery;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.HomeNetwork;
import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.test.HomeDeviceTestCallback;
import kr.or.kashi.hde.util.DebugLog;
import kr.or.kashi.hde.util.LocalPreferences;
import kr.or.kashi.hde.widget.CustomLayoutManager;
import kr.or.kashi.hde.widget.NullRecyclerViewAdapter;

public class DeviceListPartView extends LinearLayout {
    private static final String TAG = DeviceListPartView.class.getSimpleName();
    private static final String SAVED_DEVICES_FILENAME = "saved_devices";

    private final Context mContext;
    private final Handler mHandler;

    private HomeNetwork mNetwork;
    private ControlsPartView mControlsPartView;
    private AutoTestPartView mAutoTestPart;
    private HomeDevice mSelectedDevice;

    private Spinner mPollingIntervalsSpinner;
    private ToggleButton mAutoTestToggle;
    private TextView mDeviceCountText;
    private RecyclerView mDeviceListView;
    private ProgressBar mDiscoveryProgress;

    private final DeviceDiscovery.Callback mDiscoveryCallback = new DeviceDiscovery.Callback() {
        List<HomeDevice> mStagedDevices = new ArrayList<>();

        @Override
        public void onDiscoveryStarted() {
            debug("onDiscoveryStarted ");
            mDiscoveryProgress.setVisibility(View.VISIBLE);
            mStagedDevices.clear();
        }

        @Override
        public void onDiscoveryFinished() {
            debug("onDiscoveryFinished ");
            if (mStagedDevices.size() > 0) {
                for (HomeDevice device: mStagedDevices) {
                    mNetwork.addDevice(device);
                }
                mStagedDevices.clear();
            }

            // Wait for a while until new devices are up to date by polling its state
            new Handler().postDelayed(() -> {
                updateDeviceList();
                mDiscoveryProgress.setVisibility(View.GONE);
            }, 1000);
        }

        @Override
        public void onDeviceDiscovered(HomeDevice device) {
            final String clsName = device.getClass().getSimpleName();
            debug("onDeviceDiscovered " + clsName + " " + device.getAddress());
            mStagedDevices.add(device);
        }
    };

    private HomeDevice.Callback mDeviceCallback = new HomeDevice.Callback() {
        public void onPropertyChanged(HomeDevice device, PropertyMap props) {
            for (String name : props.toMap().keySet()) {
                debug("onPropertyChanged - " + name + " : " + device.dc().getProperty(name).getValue());
            }
        }

        public void onErrorOccurred(HomeDevice device, int error) {
            debug("onErrorOccurred:" + error);
        }
    };

    private DeviceListAdapter mDeviceListAdapter = new DeviceListAdapter(new DeviceListAdapter.Listener() {
        @Override
        public void onItemClicked(DeviceListAdapter.Holder holder) {
            selectDevice(holder.device);
        }
        @Override
        public void onItemSwitchClicked(DeviceListAdapter.Holder holder, boolean isChecked) {
            if (holder.device != null) {
                holder.device.setOn(isChecked);
                selectDevice(holder.device);
            }
        }
        @Override
        public void onItemRemoveClicked(DeviceListAdapter.Holder holder) {
            if (holder.device != null && mNetwork != null) {
                mNetwork.removeDevice(holder.device);
                updateDeviceList();
            }
        }
    });

    public DeviceListPartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void init(HomeNetwork network, ControlsPartView deviceCtrlPartView, AutoTestPartView deviceTestPartView) {
        mNetwork = network;
        mControlsPartView = deviceCtrlPartView;
        mAutoTestPart = deviceTestPartView;

        mNetwork.addCallback(new HomeNetwork.Callback() {
            @Override public void onDeviceAdded(List<HomeDevice> devices) { updateDeviceList(); }
            @Override public void onDeviceRemoved(List<HomeDevice> devices) { updateDeviceList(); }
        });

        mNetwork.getDeviceDiscovery().addCallback(mDiscoveryCallback);

        mAutoTestPart.getTestRunner().addCallback(new HomeDeviceTestCallback() {
            @Override public void onTestRunnerFinished() {
                mAutoTestToggle.setChecked(false);
            }
        });
        // HACK: Don't access interval component in other part view
        final Button closeButton = mAutoTestPart.findViewById(R.id.close_button);
        closeButton.setOnClickListener(view -> setAutoTestOn(false));

        findViewById(R.id.remove_all_button).setOnClickListener(view -> removeAllDevices());
        findViewById(R.id.remove_all_button).setOnLongClickListener(view -> removeAllDisconnectedDevices());
        findViewById(R.id.load_button).setOnClickListener(view -> loadDeviceList());
        findViewById(R.id.save_button).setOnClickListener(view -> saveDeviceList());

        findViewById(R.id.polling_interval_group).setVisibility(mNetwork.isSlaveMode() ? View.GONE : View.VISIBLE);
        final List<String> intervalTexts = new ArrayList<>();
        intervalTexts.add("0");
        intervalTexts.add("500");
        intervalTexts.add("1000");
        intervalTexts.add("2000");
        intervalTexts.add("3000");
        mPollingIntervalsSpinner = findViewById(R.id.polling_intervals_spinner);
        mPollingIntervalsSpinner.setAdapter(new ArrayAdapter<>(mContext, R.layout.spinner_item, intervalTexts));
        mPollingIntervalsSpinner.setSelection(LocalPreferences.getInt(LocalPreferences.Pref.POLLING_INTERVAL_INDEX, 2));
        mPollingIntervalsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object selectedItem = mPollingIntervalsSpinner.getSelectedItem();
                if (selectedItem != null) {
                    long pollingIntervalMs = Long.valueOf(selectedItem.toString()).longValue();
                    mNetwork.getDeviceStatePoller().setPollIntervalMs(pollingIntervalMs);
                    LocalPreferences.putInt(LocalPreferences.Pref.POLLING_INTERVAL_INDEX, position);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {  }
        });

        mAutoTestToggle = findViewById(R.id.auto_test_toggle);
        mAutoTestToggle.setEnabled(!mNetwork.isSlaveMode());
        mAutoTestToggle.setOnClickListener(view -> setAutoTestOn(((Checkable)view).isChecked()));
        mAutoTestToggle.setOnCheckedChangeListener((view, isChecked) -> {
            if (isChecked) {
                mNetwork.getDeviceStatePoller().setPaused(true);
                mAutoTestPart.startTest(getCurrentDevices());
            } else {
                mAutoTestPart.stopTest();
                mNetwork.getDeviceStatePoller().setPaused(false);
            }
        });

        mDeviceCountText = findViewById(R.id.device_count_text);

        mDeviceListView = findViewById(R.id.device_list);
        mDeviceListView.setLayoutManager(new CustomLayoutManager(mContext));
        mDeviceListView.setAdapter(mDeviceListAdapter);

        mDiscoveryProgress = findViewById(R.id.discovery_progress);
    }

    private void debug(String text) {
        Log.d(TAG, text);
        DebugLog.printEvent(text);
    }

    private List<HomeDevice> getCurrentDevices() {
        return mNetwork.getAllDevices();
    }

    private void removeAllDevices() {
        if (mNetwork == null) {
            return;
        }
        mNetwork.removeAllDevices();
        updateDeviceList();
    }

    private boolean removeAllDisconnectedDevices() {
        if (mNetwork == null) {
            return false;
        }
        for (HomeDevice device: mNetwork.getAllDevices()) {
            if (!device.isConnected()) mNetwork.removeDevice(device);
        }
        updateDeviceList();
        return true;
    }

    private void loadDeviceList() {
        FileInputStream fis = null;
        try {
            fis = mContext.openFileInput(SAVED_DEVICES_FILENAME);
        } catch (FileNotFoundException e) {
            debug("No saved list");
            return;
        }

        if (mNetwork.loadDevicesFrom(fis)) {
            debug("Device list has been loaded successfully");
            updateDeviceList();
        } else {
            debug("Can't load the saved device list");
        }
    }

    private void saveDeviceList() {
        FileOutputStream fos = null;
        try {
            fos = mContext.openFileOutput(SAVED_DEVICES_FILENAME, Context.MODE_PRIVATE);
        } catch (FileNotFoundException e) {
            debug("Can't open file to save");
            return;
        }

        if (mNetwork.saveDevicesTo(fos)) {
            debug("Device list has benn saved successfully");
        } else {
            debug("Can't save the device list");
        }
    }

    private void updateDeviceList() {
        selectDevice(null);
        mDeviceListAdapter.update(getCurrentDevices());
        mDeviceCountText.setText("" + mDeviceListAdapter.getItemCount());
    }

    private void selectDevice(HomeDevice device) {
        if (device == mSelectedDevice) {
            return;
        }

        if (mSelectedDevice != null) {
            mSelectedDevice.removeCallback(mDeviceCallback);
        }

        mSelectedDevice = device;

        if (mSelectedDevice != null) {
            mSelectedDevice.addCallback(mDeviceCallback);

            if (!mAutoTestPart.getTestRunner().isRunning()) {
                setAutoTestOn(false);
            }
        }

        mControlsPartView.setDevice(mSelectedDevice);
    }

    private void setAutoTestOn(boolean testOn) {
        mAutoTestToggle.setChecked(testOn);
        mAutoTestPart.setVisibility(testOn ? View.VISIBLE : View.GONE);
    }
}
