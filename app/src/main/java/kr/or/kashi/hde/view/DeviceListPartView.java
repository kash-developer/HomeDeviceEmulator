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
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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

    private final Runnable mBackDevicesRunnable = () -> {
        backupCurrentDevices(0L);
    };

    private final DeviceDiscovery.Callback mDiscoveryCallback = new DeviceDiscovery.Callback() {
        @Override
        public void onDiscoveryStarted() {
            mDiscoveryProgress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onDiscoveryFinished() {
            debug("onDiscoveryFinished ");
            // Wait for a while until new devices are up to date by polling its state
            new Handler().postDelayed(() -> {
                mDiscoveryProgress.setVisibility(View.GONE);
            }, 1000);
        }
    };

    private HomeDevice.Callback mDeviceCallback = new HomeDevice.Callback() {
        public void onPropertyChanged(HomeDevice device, PropertyMap props) {
            for (String name : props.toMap().keySet()) {
                debug("onPropertyChanged - " + name + " : " + device.dc().getProperty(name).getValue());
            }
            backupCurrentDevices(1000L);
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
            @Override public void onDeviceAdded(List<HomeDevice> devices) {
                updateDeviceList();
                if (devices.size() == 1) {
                    scrollToDevice(devices.get(0));
                }
                backupCurrentDevices(1000L);
            }
            @Override public void onDeviceRemoved(List<HomeDevice> devices) {
                updateDeviceList();
                backupCurrentDevices(1000L);
            }
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

        final ViewGroup presetGroup = findViewById(R.id.preset_group);
        for (int i=0; i<presetGroup.getChildCount(); i++) {
            final Button button = (Button) presetGroup.getChildAt(i);
            final String presetName = button.getText().toString();
            button.setOnClickListener(view -> loadPresetDevices(presetName));
            button.setOnLongClickListener(view -> { savePresetDevices(presetName); return true; });
        }
        updateAllPresetStates();

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
        mDeviceListView.setLayoutManager(new LinearLayoutManager(mContext));
        mDeviceListView.setAdapter(mDeviceListAdapter);

        mDiscoveryProgress = findViewById(R.id.discovery_progress);

        restoreLastDevices();
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
    }

    private boolean removeAllDisconnectedDevices() {
        if (mNetwork == null) {
            return false;
        }
        for (HomeDevice device: mNetwork.getAllDevices()) {
            if (!device.isConnected()) mNetwork.removeDevice(device);
        }
        return true;
    }

    private void restoreLastDevices() {
        loadDeviceList(SAVED_DEVICES_FILENAME);
    }

    private void backupCurrentDevices(long delay) {
        if (delay == 0L) {
            mHandler.removeCallbacks(mBackDevicesRunnable);
            if (!saveDeviceList(SAVED_DEVICES_FILENAME)) {
                debug("Can't backup current devices");
            }
        } else {
            if (!mHandler.hasCallbacks(mBackDevicesRunnable)) {
                mHandler.postDelayed(mBackDevicesRunnable, delay);
            }
        }
    }

    private void loadPresetDevices(String presetName) {
        if (loadDeviceList(SAVED_DEVICES_FILENAME + "_" + presetName)) {
            debug("Device list has been successfully loaded from the preset " + presetName);
        } else {
            debug("Can't load the saved device list from the preset " + presetName);
        }
        updateAllPresetStates();
    }

    private void savePresetDevices(String presetName) {
        if (saveDeviceList(SAVED_DEVICES_FILENAME + "_" + presetName)) {
            debug("Device list has been successfully saved to the preset " + presetName);
        } else {
            debug("Can't save current devices to the preset " + presetName);
        }
        updateAllPresetStates();
    }

    private boolean loadDeviceList(String fileName) {
        FileInputStream fis = null;
        boolean result = false;
        try {
            fis = mContext.openFileInput(fileName);
            result = mNetwork.loadDevicesFrom(fis);
        } catch (FileNotFoundException e) {
            debug("No saved devices in the file:" + fileName);
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (IOException e) { }
            }
        }
        return result;
    }

    private boolean saveDeviceList(String fileName) {
        FileOutputStream fos = null;
        boolean result = false;
        try {
            fos = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
            result = mNetwork.saveDevicesTo(fos);
        } catch (FileNotFoundException e) {
            debug("Can't save device list to the file " + fileName);
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException e) { }
            }
        }
        return result;
    }

    private void updateAllPresetStates() {
        final ViewGroup presetGroup = findViewById(R.id.preset_group);
        for (int i=0; i<presetGroup.getChildCount(); i++) {
            final Button button = (Button) presetGroup.getChildAt(i);
            final String presetName = button.getText().toString();

            final String fileName = SAVED_DEVICES_FILENAME + "_" + presetName;
            final File file = mContext.getFileStreamPath(fileName);
            if (file != null && file.length() > 0) {
                button.setTextColor(0xFF006666);
                button.setTypeface(null, Typeface.BOLD);
            } else {
                button.setTextColor(0xFF000000);
                button.setTypeface(null, Typeface.NORMAL);
            }
        }
    }

    private void updateDeviceList() {
        selectDevice(null);
        mDeviceListAdapter.update(getCurrentDevices());
        mDeviceCountText.setText("" + mDeviceListAdapter.getItemCount());
    }

    private void scrollToDevice(HomeDevice device) {
        final int index = mDeviceListAdapter.indexOf(device);
        if (index != -1) mDeviceListView.smoothScrollToPosition(index);
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
