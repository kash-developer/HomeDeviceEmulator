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

package kr.or.kashi.hde;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.test.DeviceTestCallback;
import kr.or.kashi.hde.util.DebugLog;
import kr.or.kashi.hde.util.LocalPreferences;
import kr.or.kashi.hde.util.LocalPreferences.Pref;
import kr.or.kashi.hde.widget.DeviceTypeListAdapter;
import kr.or.kashi.hde.widget.CustomLayoutManager;
import kr.or.kashi.hde.widget.DebugLogView;
import kr.or.kashi.hde.widget.DeviceInfoView;
import kr.or.kashi.hde.widget.DeviceListAdapter;
import kr.or.kashi.hde.widget.DeviceTestPartView;
import kr.or.kashi.hde.widget.HomeDeviceView;
import kr.or.kashi.hde.widget.NullRecyclerViewAdapter;

public class MainFragment extends Fragment {
    private static final String TAG = "HomeTestFragment";
    private static final String SAVED_DEVICES_FILENAME = "saved_devices";

    private final Context mContext;
    private final HomeNetwork mNetwork;
    private HomeDevice mSelectedDevice;

    private CheckBox mEventLogCheck;
    private CheckBox mTxRxLogCheck;
    private ToggleButton mAutoScrollToggle;
    private DebugLogView mDebugLogView;
    private ToggleButton mDiscoveryToggle;
    private ListView mDeviceTypeListView;
    private CheckBox mGroupIdCheckBox;
    private SeekBar mGroupIdSeekBar;
    private TextView mGroupIdTextView;
    private ToggleButton mGroupFullToggle;
    private CheckBox mSingleIdCheckBox;
    private SeekBar mSingleIdSeekBar;
    private TextView mSingleIdTextView;
    private ToggleButton mSingleFullToggle;
    private TextView mRangeTextView;
    private Spinner mPollingIntervalsSpinner;
    private ToggleButton mAutoTestToggle;
    private TextView mDeviceCountText;
    private RecyclerView mDeviceListView;
    private ProgressBar mDiscoveryProgress;
    private ViewGroup mDeviceDetailPart;
    private DeviceInfoView mDeviceInfoView;
    private ViewGroup mDeviceControlArea;
    private DeviceTestPartView mDeviceTestPart;

    private Map<String, Integer> mDeviceToKsIdMap = new TreeMap<>();
    private Set<String> mSelectedDeviceTypes = new HashSet<>();

    private DeviceDiscovery.Callback mDiscoveryCallback = new DeviceDiscovery.Callback() {
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
                updateDeviceTypeList();
                mDiscoveryProgress.setVisibility(View.GONE);
                mDiscoveryToggle.setChecked(false);
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

    public MainFragment(Context context, HomeNetwork network) {
        mContext = context;
        mNetwork = network;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNetwork.getDeviceDiscovery().addCallback(mDiscoveryCallback);

        mDeviceToKsIdMap.put("AirConditioner", 0x02);
        mDeviceToKsIdMap.put("BatchSwitch", 0x33);
        mDeviceToKsIdMap.put("Curtain", 0x13);
        mDeviceToKsIdMap.put("DoorLock", 0x31);
        mDeviceToKsIdMap.put("GasValve", 0x12);
        mDeviceToKsIdMap.put("HouseMeter", 0x30);
        mDeviceToKsIdMap.put("Light", 0x0E);
        mDeviceToKsIdMap.put("PowerSaver", 0x39);
        mDeviceToKsIdMap.put("Thermostat", 0x36);
        mDeviceToKsIdMap.put("Ventilation", 0x32);

        mSelectedDeviceTypes = LocalPreferences.getSelectedDeviceTypes();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {
        final View v = inflater.inflate(R.layout.main, container, false);

        v.findViewById(R.id.clear_log_button).setOnClickListener(view -> {
            mDebugLogView.clear();
            mAutoScrollToggle.setChecked(true);
            mDebugLogView.setAutoScroll(true);
        });
        mEventLogCheck = (CheckBox) v.findViewById(R.id.event_log_check);
        mEventLogCheck.setChecked(LocalPreferences.getBoolean(Pref.DEBUG_LOG_EVENT_ENABLED, true));
        mEventLogCheck.setOnClickListener(view -> updateLogFilter());
        mTxRxLogCheck = (CheckBox) v.findViewById(R.id.txrx_log_check);
        mTxRxLogCheck.setChecked(LocalPreferences.getBoolean(Pref.DEBUG_LOG_TXRX_ENABLED, true));
        mTxRxLogCheck.setOnClickListener(view -> updateLogFilter());
        mAutoScrollToggle = (ToggleButton) v.findViewById(R.id.auto_scroll_toggle);;
        mAutoScrollToggle.setOnClickListener(view -> mDebugLogView.setAutoScroll(((Checkable)view).isChecked()));
        mDebugLogView = (DebugLogView) v.findViewById(R.id.debug_log_view);
        DebugLog.setLogger(mDebugLogView);
        mDebugLogView.setOnRawTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                mAutoScrollToggle.setChecked(false);
                mDebugLogView.setAutoScroll(false);
            }
            return false;
        });

        ((Button)v.findViewById(R.id.select_all_button)).setOnClickListener(view -> selectAllTypes());
        ((Button)v.findViewById(R.id.deselect_all_button)).setOnClickListener(view -> deselectAllTypes());
        ((Button)v.findViewById(R.id.reset_range_button)).setOnClickListener(view -> resetRanges());

        mDiscoveryToggle = (ToggleButton) v.findViewById(R.id.discovery_toggle);
        mDiscoveryToggle.setEnabled(!mNetwork.isSlaveMode());
        mDiscoveryToggle.setOnClickListener(view -> setDiscoveryOn(((Checkable)view).isChecked()));

        final DeviceTypeListAdapter deviceTypeListAdapter = new DeviceTypeListAdapter(
                mContext, new ArrayList(mDeviceToKsIdMap.keySet()), mSelectedDeviceTypes);
        deviceTypeListAdapter.setCallback(new DeviceTypeListAdapter.Callback() {
            @Override
            public void onCheckedChanged(String item, boolean checked) {
                LocalPreferences.putSelectedDeviceTypes(mSelectedDeviceTypes);
            }
            @Override
            public void onAddButtonClicked(String item) {
                String address = deviceTypeListAdapter.getAddress(item);
                if (address != null && !address.isEmpty()) {
                    try {
                        int deviceId = Integer.parseInt(address.substring(2, 4), 16);
                        int groupId = Integer.parseInt(address.substring(4, 5), 16);
                        int singleId = Integer.parseInt(address.substring(5, 6), 16);
                        mNetwork.addDevice(createKsDevice(item, deviceId, groupId, singleId));
                        updateDeviceList();
                        updateDeviceTypeList();
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        mDeviceTypeListView = (ListView) v.findViewById(R.id.device_types_list);
        mDeviceTypeListView.setAdapter(deviceTypeListAdapter);

        final SeekBar.OnSeekBarChangeListener seekBarListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { onRangeChanged(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { onRangeChanged();  }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { onRangeChanged(true); }
        };

        mGroupIdCheckBox = (CheckBox) v.findViewById(R.id.group_id_check);
        mGroupIdCheckBox.setOnClickListener(view -> onRangeChanged(true));
        mGroupIdSeekBar = (SeekBar) v.findViewById(R.id.group_id_seek);
        mGroupIdSeekBar.setMin(0x1);
        mGroupIdSeekBar.setMax(0xE);
        mGroupIdSeekBar.setOnSeekBarChangeListener(seekBarListener);
        mGroupIdTextView = (TextView) v.findViewById(R.id.group_id_text);
        mGroupFullToggle = (ToggleButton) v.findViewById(R.id.group_full_toggle);
        mGroupFullToggle.setOnClickListener(view -> onRangeChanged(true));

        mSingleIdCheckBox = (CheckBox) v.findViewById(R.id.single_id_check);
        mSingleIdCheckBox.setOnClickListener(view -> onRangeChanged(true));
        mSingleIdSeekBar = (SeekBar) v.findViewById(R.id.single_id_seek);
        mSingleIdSeekBar.setMin(0x1);
        mSingleIdSeekBar.setMax(0xE);
        mSingleIdSeekBar.setOnSeekBarChangeListener(seekBarListener);
        mSingleIdTextView = (TextView) v.findViewById(R.id.single_id_text);
        mSingleFullToggle = (ToggleButton) v.findViewById(R.id.single_full_toggle);
        mSingleFullToggle.setOnClickListener(view -> onRangeChanged(true));
        mRangeTextView = (TextView) v.findViewById(R.id.range_text);
        ((Button)v.findViewById(R.id.add_selected_button)).setOnClickListener(view -> addSelectedDevices());
        ((Button)v.findViewById(R.id.add_range_button)).setOnClickListener(view -> addAllDevicesInRange());
        ((Button)v.findViewById(R.id.remove_all_button)).setOnClickListener(view -> removeAllDevices());
        ((Button)v.findViewById(R.id.remove_all_button)).setOnLongClickListener(view -> removeAllDisconnectedDevices());
        ((Button)v.findViewById(R.id.load_button)).setOnClickListener(view -> loadDeviceList());
        ((Button)v.findViewById(R.id.save_button)).setOnClickListener(view -> saveDeviceList());

        v.findViewById(R.id.polling_interval_group).setVisibility(mNetwork.isSlaveMode() ? View.GONE : View.VISIBLE);
        final List<String> intervalTexts = new ArrayList<>();
        intervalTexts.add("0");
        intervalTexts.add("500");
        intervalTexts.add("1000");
        intervalTexts.add("2000");
        intervalTexts.add("3000");
        mPollingIntervalsSpinner = (Spinner) v.findViewById(R.id.polling_intervals_spinner);
        mPollingIntervalsSpinner.setAdapter(new ArrayAdapter<>(mContext, R.layout.spinner_item, intervalTexts));
        mPollingIntervalsSpinner.setSelection(LocalPreferences.getInt(Pref.POLLING_INTERVAL_INDEX, 2));
        mPollingIntervalsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object selectedItem = mPollingIntervalsSpinner.getSelectedItem();
                if (selectedItem != null) {
                    long pollingIntervalMs = Long.valueOf(selectedItem.toString()).longValue();
                    mNetwork.getDeviceStatePoller().setPollIntervalMs(pollingIntervalMs);
                    LocalPreferences.putInt(Pref.POLLING_INTERVAL_INDEX, position);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {  }
        });

        mAutoTestToggle = (ToggleButton) v.findViewById(R.id.auto_test_toggle);
        mAutoTestToggle.setEnabled(!mNetwork.isSlaveMode());
        mAutoTestToggle.setOnClickListener(view -> setAutoTestOn(((Checkable)view).isChecked()));
        mAutoTestToggle.setOnCheckedChangeListener((view, isChecked) -> {
            if (isChecked) {
                mNetwork.getDeviceStatePoller().setPaused(true);
                mDeviceTestPart.startTest(getCurrentDevices());
            } else {
                mDeviceTestPart.stopTest();
                mNetwork.getDeviceStatePoller().setPaused(false);
            }
        });

        mDeviceCountText = v.findViewById(R.id.device_count_text);

        mDeviceListView = (RecyclerView) v.findViewById(R.id.device_list);
        mDeviceListView.setLayoutManager(new CustomLayoutManager(mContext));
        mDeviceListView.setAdapter(new NullRecyclerViewAdapter());

        mDiscoveryProgress = (ProgressBar) v.findViewById(R.id.discovery_progress);

        mDeviceDetailPart = (ViewGroup) v.findViewById(R.id.device_detail_part);
        mDeviceInfoView = (DeviceInfoView) v.findViewById(R.id.device_info_view);
        mDeviceControlArea = (ViewGroup) v.findViewById(R.id.device_control_area);

        mDeviceTestPart = (DeviceTestPartView) v.findViewById(R.id.device_test_part);
        mDeviceTestPart.getTestRunner().addCallback(new DeviceTestCallback() {
            @Override public void onTestRunnerFinished() {
                mAutoTestToggle.setChecked(false);
            }
        });
        final Button closeButton = mDeviceTestPart.findViewById(R.id.close_button);
        closeButton.setOnClickListener(view -> setAutoTestOn(false));

        if (needInitRange()) {
            resetRanges();
            saveRanges();
        } else {
            loadRanges();
        }

        updateLogFilter();
        updateDeviceTypeList();

        return v;
    }

    private void debug(String text) {
        Log.d(TAG, text);
        DebugLog.printEvent(text);
    }

    private void selectAllTypes() {
        mSelectedDeviceTypes.addAll(mDeviceToKsIdMap.keySet());
        final DeviceTypeListAdapter deviceTypeListAdapter = new DeviceTypeListAdapter(
                mContext, new ArrayList(mDeviceToKsIdMap.keySet()), mDeviceToKsIdMap.keySet());
        mDeviceTypeListView.setAdapter(deviceTypeListAdapter);
        LocalPreferences.putSelectedDeviceTypes(mSelectedDeviceTypes);
    }

    private void deselectAllTypes() {
        mSelectedDeviceTypes.clear();
        final DeviceTypeListAdapter deviceTypeListAdapter = new DeviceTypeListAdapter(
                mContext, new ArrayList(mDeviceToKsIdMap.keySet()), mSelectedDeviceTypes);
        mDeviceTypeListView.setAdapter(deviceTypeListAdapter);
        LocalPreferences.putSelectedDeviceTypes(mSelectedDeviceTypes);
    }

    private void updateLogFilter() {
        int logFilter = 0;
        if (mEventLogCheck.isChecked()) logFilter |= DebugLog.EVENT;
        if (mTxRxLogCheck.isChecked()) logFilter |= DebugLog.TXRX;
        mDebugLogView.setFilter(logFilter);

        LocalPreferences.putBoolean(Pref.DEBUG_LOG_EVENT_ENABLED, mEventLogCheck.isChecked());
        LocalPreferences.putBoolean(Pref.DEBUG_LOG_TXRX_ENABLED, mTxRxLogCheck.isChecked());
    }

    private void setDiscoveryOn(boolean on) {
        if (!mNetwork.isRunning()) {
            debug("network is not running");
            return;
        }

        mNetwork.getDeviceDiscovery().stopDiscovering();

        if (on == false) {
            return;
        }

        List<HomeDevice> devices = createDevicesInRange();
        if (devices.size() == 0) {
            debug("No device(s) to discover!");
            mDiscoveryToggle.setChecked(false);
            return;
        }

        if (!mNetwork.getDeviceDiscovery().discoverDevices(0, devices)) {
            debug("Can't start scanning!");
            mDiscoveryToggle.setChecked(false);
            return;
        }

        LocalPreferences.putSelectedDeviceTypes(mSelectedDeviceTypes);
    }

    private void resetRanges() {
        mGroupIdCheckBox.setChecked(true);
        mGroupIdSeekBar.setProgress(1);
        mGroupFullToggle.setChecked(false);
        mSingleIdCheckBox.setChecked(false);
        mSingleIdSeekBar.setProgress(4);
        mSingleFullToggle.setChecked(false);
        updateRangeText();
    }

    private void loadRanges() {
        mGroupIdCheckBox.setChecked(LocalPreferences.getBoolean(Pref.RANGE_GROUP_CHECKED));
        mGroupIdSeekBar.setProgress(LocalPreferences.getInt(Pref.RANGE_GROUP_LAST_ID));
        mGroupFullToggle.setChecked(LocalPreferences.getBoolean(Pref.RANGE_GROUP_FULL_CHECKED));
        mSingleIdCheckBox.setChecked(LocalPreferences.getBoolean(Pref.RANGE_SINGLE_CHECKED));
        mSingleIdSeekBar.setProgress(LocalPreferences.getInt(Pref.RANGE_SINGLE_LAST_ID));
        mSingleFullToggle.setChecked(LocalPreferences.getBoolean(Pref.RANGE_SINGLE_FULL_CHECKED));
        updateRangeText();
    }

    private void saveRanges() {
        LocalPreferences.putBoolean(Pref.RANGE_GROUP_CHECKED, mGroupIdCheckBox.isChecked());
        LocalPreferences.putInt(Pref.RANGE_GROUP_LAST_ID, mGroupIdSeekBar.getProgress());
        LocalPreferences.putBoolean(Pref.RANGE_GROUP_FULL_CHECKED, mGroupFullToggle.isChecked());
        LocalPreferences.putBoolean(Pref.RANGE_SINGLE_CHECKED, mSingleIdCheckBox.isChecked());
        LocalPreferences.putInt(Pref.RANGE_SINGLE_LAST_ID, mSingleIdSeekBar.getProgress());
        LocalPreferences.putBoolean(Pref.RANGE_SINGLE_FULL_CHECKED, mSingleFullToggle.isChecked());
    }

    private boolean needInitRange() {
        return (LocalPreferences.getInt(Pref.RANGE_GROUP_LAST_ID, 99999) == 9999);
    }

    private void onRangeChanged() {
        onRangeChanged(false);
    }

    private void onRangeChanged(boolean savePrefs) {
        updateRangeText();
        if (savePrefs) {
            saveRanges();
        }
    }

    private void updateRangeText() {
        int singles = mSingleIdSeekBar.getProgress();
        mSingleIdTextView.setText(String.format("%X", singles));

        int groups = mGroupIdSeekBar.getProgress();
        mGroupIdTextView.setText(String.format("%X", groups));

        final StringBuilder sb = new StringBuilder();

        if (mSingleIdCheckBox.isChecked() && singles > 0) {
            sb.append("  ");
            if (singles == 1) {
                sb.append("01");
            } else {
                sb.append("0[1");
                sb.append("~" + String.format("%X", singles));
                sb.append("]");
            }
        }

        if (mGroupIdCheckBox.isChecked() && groups > 0) {
            sb.append("  ");
            if (groups == 1) {
                sb.append("1");
            } else {
                sb.append("[1");
                sb.append("~" + String.format("%X", groups));
                sb.append("]");
            }
            if (singles == 1) {
                sb.append("1");
            } else {
                sb.append("[1");
                sb.append("~" + String.format("%X", singles));
                sb.append("]");
            }
        }

        if (mSingleFullToggle.isChecked() && mSingleIdCheckBox.isChecked()) {
            sb.append("  ");
            sb.append("0F");
        }

        if (mSingleFullToggle.isChecked() && mGroupIdCheckBox.isChecked()) {
            sb.append("  ");
            if (groups == 1) {
                sb.append("1");
            } else {
                sb.append("[1");
                sb.append("~" + String.format("%X", groups));
                sb.append("]");
            }
            sb.append("F");
        }

        if (mGroupFullToggle.isChecked() && mSingleFullToggle.isChecked()) {
            sb.append("  ");
            sb.append("FF");
        }

        mRangeTextView.setText(sb.toString());
    }

    private void setAutoTestOn(boolean testOn) {
        mAutoTestToggle.setChecked(testOn);
        mDeviceTestPart.setVisibility(testOn ? View.VISIBLE : View.GONE);
    }

    private void addSelectedDevices() {
        for (HomeDevice device: createSelectedDevices()) {
            mNetwork.addDevice(device);
        }
        updateDeviceList();
        updateDeviceTypeList();
    }

    private void addAllDevicesInRange() {
        for (HomeDevice device: createDevicesInRange()) {
            mNetwork.addDevice(device);
        }
        updateDeviceList();
        updateDeviceTypeList();
    }

    private List<HomeDevice> createSelectedDevices() {
        List<HomeDevice> devices = new ArrayList<>();

        for (String deviceType: mSelectedDeviceTypes) {
            if (!mDeviceToKsIdMap.containsKey(deviceType)) {
                debug("No device type: " + deviceType);
                continue;
            }

            int deviceId = mDeviceToKsIdMap.get(deviceType);
            int single = mSingleIdSeekBar.getProgress();
            int group = mGroupIdSeekBar.getProgress();

            if (mSingleIdCheckBox.isChecked()) {
                devices.add(createKsDevice(deviceType, deviceId, 0, single));
            }

            if (mSingleFullToggle.isChecked() && mSingleIdCheckBox.isChecked()) {
                devices.add(createKsDevice(deviceType, deviceId, 0, 0xF));
            }

            if (mGroupIdCheckBox.isChecked()) {
                devices.add(createKsDevice(deviceType, deviceId, group, single));

                if (mSingleFullToggle.isChecked() && mGroupIdCheckBox.isChecked()) {
                    devices.add(createKsDevice(deviceType, deviceId, group, 0xF));
                }
            }

            if (mGroupFullToggle.isChecked() && mSingleFullToggle.isChecked()) {
                devices.add(createKsDevice(deviceType, deviceId, 0xF, 0xF));
            }
        }

        return devices;
    }

    private List<HomeDevice> createDevicesInRange() {
        List<HomeDevice> devices = new ArrayList<>();

        for (String deviceType: mSelectedDeviceTypes) {
            if (!mDeviceToKsIdMap.containsKey(deviceType)) {
                debug("No device type: " + deviceType);
                continue;
            }

            int deviceId = mDeviceToKsIdMap.get(deviceType);
            int maxGroupId = mGroupIdSeekBar.getProgress();
            int maxSingleId = mSingleIdSeekBar.getProgress();

            if (mSingleIdCheckBox.isChecked()) {
                for (int si = 1; si <= maxSingleId; si++) {
                    devices.add(createKsDevice(deviceType, deviceId, 0, si));
                }
            }

            if (mSingleFullToggle.isChecked() && mSingleIdCheckBox.isChecked()) {
                devices.add(createKsDevice(deviceType, deviceId, 0, 0xF));
            }

            if (mGroupIdCheckBox.isChecked()) {
                for (int gi = 1; gi <= maxGroupId; gi++) {
                    for (int si = 1; si <= maxSingleId; si++) {
                        devices.add(createKsDevice(deviceType, deviceId, gi, si));
                    }
                    if (mSingleFullToggle.isChecked()) {
                        devices.add(createKsDevice(deviceType, deviceId, gi, 0xF));
                    }
                }
            }

            if (mGroupFullToggle.isChecked() && mSingleFullToggle.isChecked()) {
                devices.add(createKsDevice(deviceType, deviceId, 0xF, 0xF));
            }
        }

        return devices;
    }

    private void removeAllDevices() {
        if (mNetwork == null) {
            return;
        }
        mNetwork.removeAllDevices();
        updateDeviceList();
        updateDeviceTypeList();
    }

    private boolean removeAllDisconnectedDevices() {
        if (mNetwork == null) {
            return false;
        }
        for (HomeDevice device: mNetwork.getAllDevices()) {
            if (!device.isConnected()) mNetwork.removeDevice(device);
        }
        updateDeviceList();
        updateDeviceTypeList();
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
            updateDeviceTypeList();
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

    private String createKsAddress(int deviceId, int groupId, int singleId) {
        return String.format("::%02X%1X%1X", deviceId, groupId, singleId);
    }

    private HomeDevice createKsDevice(String typeName, int deviceId, int groupId, int singleId) {
        String address = createKsAddress(deviceId, groupId, singleId);

        int area = HomeDevice.Area.UNKNOWN;
        String groupStr = (groupId == 0x0F) ? "F" : Integer.valueOf(groupId).toString();
        String singleStr =  (singleId == 0x0F) ? "F" : Integer.valueOf(singleId).toString();
        String name = typeName + " (" + groupStr + "-" + singleStr + ")";

        return mNetwork.createDevice(address, area, name);
    }

    private void updateDeviceList() {
        selectDevice(null);

        RecyclerView.Adapter adapter = new DeviceListAdapter(mContext, getCurrentDevices(), new DeviceListAdapter.Listener() {
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
                    updateDeviceTypeList();
                }
            }
        });

        mDeviceCountText.setText(" " + adapter.getItemCount());
        mDeviceListView.setAdapter(adapter);
    }

    private void updateDeviceTypeList() {
        int[] ids = new int[mDeviceToKsIdMap.size()];
        Arrays.fill(ids, 0);

        List<Integer> devIdList = new ArrayList<>(mDeviceToKsIdMap.values());

        for (HomeDevice device: getCurrentDevices()) {
            int devId = Integer.parseInt(device.getAddress().substring(2, 4), 16);
            int subId = Integer.parseInt(device.getAddress().substring(4, 6), 16);

            int pos = devIdList.indexOf(devId);
            if (pos < 0 || pos >= ids.length)
                continue;

            if (ids[pos] < subId) {
                ids[pos] = subId;
            }
        }

        final DeviceTypeListAdapter deviceTypeListAdapter =
                (DeviceTypeListAdapter) mDeviceTypeListView.getAdapter();

        for (int i=0; i<ids.length; i++) {
            int devId = devIdList.get(i);
            int subId = ids[i];
            if (subId == 0) {
                subId = 0x01;
                if (devId == 0x0E || devId == 0x39 || devId == 0x36) {
                    subId += 0x10;
                }
            } else {
               subId++;
               if ((subId & 0xF) == 0xF) subId += 2;

               if (devId == 0x0E || devId == 0x39 || devId == 0x36) {
                   if (subId >= 0xFF) subId = -1;
               } else {
                   if (subId >= 0x0F) subId = -1;
               }
            }
            ids[i] = subId;
        }

        for (int i=0; i<ids.length; i++) {
            if (ids[i] > -1) {
                String address = String.format("::%02X%02X", devIdList.get(i), ids[i]);
                deviceTypeListAdapter.setAddress(i, address);
            } else {
                deviceTypeListAdapter.setAddress(i, "");
            }
        }
    }

    private List<HomeDevice> getCurrentDevices() {
        return mNetwork.getAllDevices();
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

            if (!mDeviceTestPart.getTestRunner().isRunning()) {
                setAutoTestOn(false);
            }
        }

        setDeviceControlView(mSelectedDevice);
    }

    private void setDeviceControlView(HomeDevice device) {
        mDeviceControlArea.removeAllViews();
        mDeviceInfoView.setDevice(device);

        if (device == null) {
            return;
        }

        int resId = getDeviceControlViewResId(device);
        if (resId == 0) {
            return;
        }

        HomeDeviceView deviceView = (HomeDeviceView) LayoutInflater.from(mContext)
                .inflate(resId, mDeviceControlArea, false);

        deviceView.setDevice(device);

        mDeviceControlArea.addView(deviceView);
    }

    private int getDeviceControlViewResId(HomeDevice device) {
        switch(device.getType()) {
            case HomeDevice.Type.LIGHT: return R.layout.device_detail_light;
            case HomeDevice.Type.DOOR_LOCK: return R.layout.device_detail_door_lock;
            case HomeDevice.Type.VENTILATION: return R.layout.device_detail_ventilation;
            case HomeDevice.Type.GAS_VALVE: return R.layout.device_detail_gas_valve;
            case HomeDevice.Type.HOUSE_METER: return R.layout.device_detail_house_meter;
            case HomeDevice.Type.CURTAIN: return R.layout.device_detail_curtain;
            case HomeDevice.Type.THERMOSTAT: return R.layout.device_detail_thermostat;
            case HomeDevice.Type.BATCH_SWITCH: return R.layout.device_detail_batch_switch;
            case HomeDevice.Type.SENSOR: return R.layout.device_detail_binary_sensors;
            case HomeDevice.Type.AIRCONDITIONER: return R.layout.device_detail_air_conditioner;
            case HomeDevice.Type.POWER_SAVER: return R.layout.device_detail_power_saver;
        }
        return 0;
    }
}
