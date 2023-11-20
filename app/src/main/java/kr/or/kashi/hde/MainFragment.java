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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.util.DebugLog;
import kr.or.kashi.hde.util.LocalPreferences;
import kr.or.kashi.hde.util.Utils;
import kr.or.kashi.hde.widget.CheckableListAdapter;
import kr.or.kashi.hde.widget.CustomLayoutManager;
import kr.or.kashi.hde.widget.DebugLogView;
import kr.or.kashi.hde.widget.DeviceInfoView;
import kr.or.kashi.hde.widget.DeviceListAdapter;
import kr.or.kashi.hde.widget.DeviceTestPartView;
import kr.or.kashi.hde.widget.HomeDeviceView;
import kr.or.kashi.hde.widget.NullRecyclerViewAdapter;

public class MainFragment extends Fragment {
    private static final String TAG = "HomeTestFragment";

    private final Context mContext;
    private final HomeNetwork mNetwork;
    private HomeDevice mSelectedDevice;

    private CheckBox mEventLogCheck;
    private CheckBox mTxRxLogCheck;
    private ToggleButton mAutoScrollToggle;
    private DebugLogView mDebugLogView;
    private ToggleButton mDiscoveryToggle;
    private ListView mDeviceTypeListView;
    private CheckBox mSingleIdCheckBox;
    private SeekBar mSingleIdSeekBar;
    private TextView mSingleIdTextView;
    private CheckBox mGroupIdCheckBox;
    private SeekBar mGroupIdSeekBar;
    private TextView mGroupIdTextView;
    private CheckBox mSingleAllCheckBox;
    private CheckBox mGroupAllCheckBox;
    private CheckBox mTypeAllCheckBox;
    private TextView mRangeTextView;
    private Spinner mManualIdHex1Spinner;
    private Spinner mManualIdHex2Spinner;
    private Spinner mPollingIntervalsSpinner;
    private ToggleButton mAutoTestToggle;
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

        ((Button)v.findViewById(R.id.clear_log_button)).setOnClickListener(view -> mDebugLogView.clear());
        mEventLogCheck = (CheckBox) v.findViewById(R.id.event_log_check);
        mEventLogCheck.setOnClickListener(view -> updateLogFilter());
        mTxRxLogCheck = (CheckBox) v.findViewById(R.id.txrx_log_check);
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
        ((Button)v.findViewById(R.id.reset_range_button)).setOnClickListener(view -> resetRange());

        mDiscoveryToggle = (ToggleButton) v.findViewById(R.id.discovery_toggle);
        mDiscoveryToggle.setOnClickListener(view -> setDiscoveryOn(((Checkable)view).isChecked()));

        final CheckableListAdapter<String> deviceTypeListAdapter = new CheckableListAdapter<>(
                mContext, new ArrayList(mDeviceToKsIdMap.keySet()), mSelectedDeviceTypes);
        deviceTypeListAdapter.setChangeRunnable(() -> {
            LocalPreferences.putSelectedDeviceTypes(mSelectedDeviceTypes);
        });
        mDeviceTypeListView = (ListView) v.findViewById(R.id.device_types_list);
        mDeviceTypeListView.setAdapter(deviceTypeListAdapter);

        final SeekBar.OnSeekBarChangeListener seekBarListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateRangeText(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { updateRangeText();  }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { updateRangeText(); }
        };

        mSingleIdCheckBox = (CheckBox) v.findViewById(R.id.single_id_check);
        mSingleIdCheckBox.setOnClickListener(view -> updateRangeText());
        mSingleIdSeekBar = (SeekBar) v.findViewById(R.id.single_id_seek);
        mSingleIdSeekBar.setMin(1);
        mSingleIdSeekBar.setMax(0xE);
        mSingleIdSeekBar.setOnSeekBarChangeListener(seekBarListener);
        mSingleIdTextView = (TextView) v.findViewById(R.id.single_id_text);

        mGroupIdCheckBox = (CheckBox) v.findViewById(R.id.group_id_check);
        mGroupIdCheckBox.setOnClickListener(view -> updateRangeText());
        mGroupIdSeekBar = (SeekBar) v.findViewById(R.id.group_id_seek);
        mGroupIdSeekBar.setMin(1);
        mGroupIdSeekBar.setMax(0xE);
        mGroupIdSeekBar.setOnSeekBarChangeListener(seekBarListener);
        mGroupIdTextView = (TextView) v.findViewById(R.id.group_id_text);

        mSingleAllCheckBox = (CheckBox) v.findViewById(R.id.single_all_check);
        mSingleAllCheckBox.setOnClickListener(view -> updateRangeText());
        mGroupAllCheckBox = (CheckBox) v.findViewById(R.id.group_all_check);
        mGroupAllCheckBox.setOnClickListener(view -> updateRangeText());
        mTypeAllCheckBox = (CheckBox) v.findViewById(R.id.type_all_check);
        mTypeAllCheckBox.setOnClickListener(view -> updateRangeText());

        mRangeTextView = (TextView) v.findViewById(R.id.range_text);

        final List<String> hexDigits = new ArrayList<>();
        for (int i=0; i<=0xF; i++) {
            hexDigits.add(String.format("%X", i));
        }
        mManualIdHex1Spinner = (Spinner) v.findViewById(R.id.manual_id_hex1_spinner);
        mManualIdHex1Spinner.setAdapter(new ArrayAdapter<>(mContext, R.layout.spinner_item, hexDigits));
        mManualIdHex2Spinner = (Spinner) v.findViewById(R.id.manual_id_hex2_spinner);
        mManualIdHex2Spinner.setAdapter(new ArrayAdapter<>(mContext, R.layout.spinner_item, hexDigits));
        ((Button)v.findViewById(R.id.add_device_button)).setOnClickListener(view -> addSingleDevices());

        ((Button)v.findViewById(R.id.remove_all_button)).setOnClickListener(view -> removeAllDevices());

        final List<String> intervalTexts = new ArrayList<>();
        intervalTexts.add("0");
        intervalTexts.add("500");
        intervalTexts.add("1000");
        intervalTexts.add("2000");
        intervalTexts.add("5000");
        mPollingIntervalsSpinner = (Spinner) v.findViewById(R.id.polling_intervals_spinner);
        mPollingIntervalsSpinner.setAdapter(new ArrayAdapter<>(mContext, R.layout.spinner_item, intervalTexts));
        mPollingIntervalsSpinner.setSelection(3);
        mPollingIntervalsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object selectedItem = mPollingIntervalsSpinner.getSelectedItem();
                if (selectedItem != null) {
                    setPollingIntervalMs(Long.valueOf(selectedItem.toString()).longValue());
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {  }
        });

        mAutoTestToggle = (ToggleButton) v.findViewById(R.id.auto_test_toggle);
        mAutoTestToggle.setOnClickListener(view -> setAutoTestOn(((Checkable)view).isChecked()));

        mDeviceListView = (RecyclerView) v.findViewById(R.id.device_list);
        mDeviceListView.setLayoutManager(new CustomLayoutManager(mContext));
        mDeviceListView.setAdapter(new NullRecyclerViewAdapter());

        mDiscoveryProgress = (ProgressBar) v.findViewById(R.id.discovery_progress);

        mDeviceDetailPart = (ViewGroup) v.findViewById(R.id.device_detail_part);
        mDeviceInfoView = (DeviceInfoView) v.findViewById(R.id.device_info_view);
        mDeviceControlArea = (ViewGroup) v.findViewById(R.id.device_control_area);

        mDeviceTestPart = (DeviceTestPartView) v.findViewById(R.id.device_test_part);

        resetRange();
        updateRangeText();
        updateLogFilter();

        return v;
    }

    private void debug(String text) {
        Log.d(TAG, text);
        DebugLog.printEvent(text);
    }

    private void selectAllTypes() {
        mSelectedDeviceTypes.addAll(mDeviceToKsIdMap.keySet());
        final CheckableListAdapter<String> deviceTypeListAdapter = new CheckableListAdapter<>(
                mContext, new ArrayList(mDeviceToKsIdMap.keySet()), mDeviceToKsIdMap.keySet());
        mDeviceTypeListView.setAdapter(deviceTypeListAdapter);
        LocalPreferences.putSelectedDeviceTypes(mSelectedDeviceTypes);
    }

    private void deselectAllTypes() {
        mSelectedDeviceTypes.clear();
        final CheckableListAdapter<String> deviceTypeListAdapter = new CheckableListAdapter<>(
                mContext, new ArrayList(mDeviceToKsIdMap.keySet()), mSelectedDeviceTypes);
        mDeviceTypeListView.setAdapter(deviceTypeListAdapter);
        LocalPreferences.putSelectedDeviceTypes(mSelectedDeviceTypes);
    }

    private void updateLogFilter() {
        int logFilter = 0;

        if (mEventLogCheck.isChecked()) {
            logFilter |= DebugLog.EVENT;
        }

        if (mTxRxLogCheck.isChecked()) {
            logFilter |= DebugLog.TXRX;
        }

        mDebugLogView.setFilter(logFilter);
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

        List<HomeDevice> devices = new ArrayList<>();
        for (String deviceType: mSelectedDeviceTypes) {
            buildDevicesToDiscover(deviceType, devices);
        }

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

    private void resetRange() {
        mSingleIdCheckBox.setChecked(true);
        mSingleIdSeekBar.setProgress(4);
        mGroupIdCheckBox.setChecked(true);
        mGroupIdSeekBar.setProgress(2);
        mSingleAllCheckBox.setChecked(false);
        mGroupAllCheckBox.setChecked(false);
        mTypeAllCheckBox.setChecked(false);

        updateRangeText();
    }

    private void updateRangeText() {
        int singles = 0;
        int groups = 0;

        if (mSingleIdCheckBox.isChecked()) {
            singles = mSingleIdSeekBar.getProgress();
            mSingleIdTextView.setText(String.format("%X", singles));
        } else {

        }

        if (mGroupIdCheckBox.isChecked()) {
            groups = mGroupIdSeekBar.getProgress();
            mGroupIdTextView.setText(String.format("%X", groups));
        } else {

        }

        final StringBuilder sb = new StringBuilder();

        if (singles > 0) {
            sb.append("~" + String.format("?%X", singles));
        }

        if (mSingleAllCheckBox.isChecked()) {
            sb.append(", ");
            sb.append("0F");
        }

        if (groups > 0) {
            sb.append(", ");
            sb.append("~" + String.format("%X?", groups));
        }

        if (mGroupAllCheckBox.isChecked()) {
            sb.append(", ");
            sb.append("?F");
        }

        if (mTypeAllCheckBox.isChecked()) {
            sb.append(", ");
            sb.append("FF");
        }

        mRangeTextView.setText(sb.toString());
    }

    private void setPollingIntervalMs(long intervalMs) {
        mNetwork.getDeviceStatePoller().setPollIntervalMs(intervalMs);
    }

    private void setAutoTestOn(boolean testOn) {
        if (testOn) {
            mDeviceDetailPart.setVisibility(View.GONE);
            mDeviceTestPart.setVisibility(View.VISIBLE);
            mDeviceTestPart.startTest(getCurrentDevices());
        } else {
            mDeviceDetailPart.setVisibility(View.VISIBLE);
            mDeviceTestPart.setVisibility(View.GONE);
            mDeviceTestPart.stopTest();
        }
    }

    private void buildDevicesToDiscover(String deviceType, List<HomeDevice> outList) {
        if (!mDeviceToKsIdMap.containsKey(deviceType)) {
            debug("No device type for discovery: " + deviceType);
            return;
        }

        int deviceId = mDeviceToKsIdMap.get(deviceType);

        int maxSingleId = 0;
        if (mSingleIdCheckBox.isChecked()) {
            maxSingleId = mSingleIdSeekBar.getProgress();
        }

        int maxGroupId = 0;
        if (mGroupIdCheckBox.isChecked()) {
            maxGroupId = mGroupIdSeekBar.getProgress();
        }

        for (int i = 1; i <= maxSingleId; i++) {
            outList.add(createKsDevice(deviceType, deviceId, 0, i));
        }

        if (mSingleAllCheckBox.isChecked()) {
            outList.add(createKsDevice(deviceType, deviceId, 0, 0xF));
        }

        for (int gi = 1; gi <= maxGroupId; gi++) {
            for (int si = 1; si <= maxSingleId; si++) {
                outList.add(createKsDevice(deviceType, deviceId, gi, si));
            }

            if (mGroupAllCheckBox.isChecked()) {
                outList.add(createKsDevice(deviceType, deviceId, gi, 0xF));
            }
        }

        if (mTypeAllCheckBox.isChecked()) {
            outList.add(createKsDevice(deviceType, deviceId, 0xF, 0xF));
        }
    }

    private void addSingleDevices() {
        for (String deviceType: mSelectedDeviceTypes) {
            if (!mDeviceToKsIdMap.containsKey(deviceType)) {
                debug("No device type for discovery: " + deviceType);
                continue;
            }

            int deviceId = mDeviceToKsIdMap.get(deviceType);
            int groupId = Integer.valueOf(mManualIdHex1Spinner.getSelectedItem().toString(), 16).intValue();
            int singleId = Integer.valueOf(mManualIdHex2Spinner.getSelectedItem().toString(), 16).intValue();

            mNetwork.addDevice(createKsDevice(deviceType, deviceId, groupId, singleId));
        }

        updateDeviceList();
    }

    private void removeAllDevices() {
        if (mNetwork == null) {
            return;
        }

        for (HomeDevice device: mNetwork.getAllDevices()) {
            mNetwork.removeDevice(device);
        }

        updateDeviceList();
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
                }
            }
        });

        mDeviceListView.setAdapter(adapter);
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
