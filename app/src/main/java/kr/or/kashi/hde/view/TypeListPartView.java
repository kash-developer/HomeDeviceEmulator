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
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import kr.or.kashi.hde.DeviceDiscovery;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.HomeNetwork;
import kr.or.kashi.hde.R;
import kr.or.kashi.hde.base.BasicPropertyMap;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.device.AirConditioner;
import kr.or.kashi.hde.device.BatchSwitch;
import kr.or.kashi.hde.device.Curtain;
import kr.or.kashi.hde.device.DoorLock;
import kr.or.kashi.hde.device.GasValve;
import kr.or.kashi.hde.device.HouseMeter;
import kr.or.kashi.hde.device.Light;
import kr.or.kashi.hde.device.PowerSaver;
import kr.or.kashi.hde.device.Thermostat;
import kr.or.kashi.hde.device.Ventilation;
import kr.or.kashi.hde.util.DebugLog;
import kr.or.kashi.hde.util.LocalPreferences;

public class TypeListPartView extends LinearLayout {
    private static final String TAG = TypeListPartView.class.getSimpleName();

    private final Context mContext;
    private final Handler mHandler;
    private HomeNetwork mNetwork;

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

    private Map<String, Integer> mDeviceToKsIdMap = new TreeMap<>();
    private Map<Integer, int[]> mMaxSingleIdsMap = new ArrayMap<>();
    private Set<String> mSelectedDeviceTypes = new HashSet<>();

    private final DeviceDiscovery.Callback mDiscoveryCallback = new DeviceDiscovery.Callback() {
        final List<HomeDevice> mStagedDevices = new ArrayList<>();

        @Override
        public void onDiscoveryStarted() {
            debug("onDiscoveryStarted ");
            mStagedDevices.clear();
        }

        @Override
        public void onDiscoveryFinished() {
            debug("onDiscoveryFinished ");
            if (mStagedDevices.size() > 0) {
                mNetwork.addDevice(mStagedDevices);
                mStagedDevices.clear();
            }

            // Wait for a while until new devices are up to date by polling its state
            new Handler().postDelayed(() -> {
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

    private TypeListAdapter mDeviceTypeListAdapter;

    public TypeListPartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void init(HomeNetwork network) {
        mNetwork = network;
        mNetwork.getDeviceDiscovery().addCallback(mDiscoveryCallback);
        mNetwork.addCallback(new HomeNetwork.Callback() {
            @Override public void onDeviceAdded(List<HomeDevice> devices) {
                updateMaxSingleIdsMap();
                updateCandidateAddresses();
            }
            @Override public void onDeviceRemoved(List<HomeDevice> devices) {
                updateMaxSingleIdsMap();
                updateCandidateAddresses();
            }
        });

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

        findViewById(R.id.select_all_button).setOnClickListener(view -> selectAllTypes());
        findViewById(R.id.deselect_all_button).setOnClickListener(view -> deselectAllTypes());
        findViewById(R.id.reset_range_button).setOnClickListener(view -> resetRanges());

        mDiscoveryToggle = findViewById(R.id.discovery_toggle);
        mDiscoveryToggle.setEnabled(!mNetwork.isSlaveMode());
        mDiscoveryToggle.setOnClickListener(view -> setDiscoveryOn(((Checkable)view).isChecked()));

        mDeviceTypeListAdapter = new TypeListAdapter(
                mContext, new ArrayList(mDeviceToKsIdMap.keySet()), mSelectedDeviceTypes);
        mDeviceTypeListAdapter.setCallback(new TypeListAdapter.Callback() {
            @Override
            public void onCheckedChanged(String item, boolean checked) {
                LocalPreferences.putSelectedDeviceTypes(mSelectedDeviceTypes);
            }
            @Override
            public void onPrevButtonClicked(String item) {
                updateGroupShiftedInAddress(item, true);
            }
            @Override
            public void onNextButtonClicked(String item) {
                updateGroupShiftedInAddress(item, false);
            }
            @Override
            public void onAddButtonClicked(String item, boolean longClick) {
                if (longClick) {
                    addFullDeviceIf(item);
                } else {
                    addSingleDevice(item);
                }
            }
        });

        mDeviceTypeListView = findViewById(R.id.device_types_list);
        mDeviceTypeListView.setAdapter(mDeviceTypeListAdapter);

        final SeekBar.OnSeekBarChangeListener seekBarListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { onRangeChanged(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { onRangeChanged();  }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { onRangeChanged(true); }
        };

        mGroupIdCheckBox = findViewById(R.id.group_id_check);
        mGroupIdCheckBox.setOnClickListener(view -> onRangeChanged(true));
        mGroupIdSeekBar = findViewById(R.id.group_id_seek);
        mGroupIdSeekBar.setMin(0x1);
        mGroupIdSeekBar.setMax(0xE);
        mGroupIdSeekBar.setOnSeekBarChangeListener(seekBarListener);
        mGroupIdTextView = findViewById(R.id.group_id_text);
        mGroupFullToggle = findViewById(R.id.group_full_toggle);
        mGroupFullToggle.setOnClickListener(view -> onRangeChanged(true));

        mSingleIdCheckBox = findViewById(R.id.single_id_check);
        mSingleIdCheckBox.setOnClickListener(view -> onRangeChanged(true));
        mSingleIdSeekBar = findViewById(R.id.single_id_seek);
        mSingleIdSeekBar.setMin(0x1);
        mSingleIdSeekBar.setMax(0xE);
        mSingleIdSeekBar.setOnSeekBarChangeListener(seekBarListener);
        mSingleIdTextView = findViewById(R.id.single_id_text);
        mSingleFullToggle = findViewById(R.id.single_full_toggle);
        mSingleFullToggle.setOnClickListener(view -> onRangeChanged(true));
        mRangeTextView = findViewById(R.id.range_text);
        findViewById(R.id.add_selected_button).setOnClickListener(view -> addSelectedDevices());
        findViewById(R.id.add_range_button).setOnClickListener(view -> addAllDevicesInRange());

        if (needInitRange()) {
            resetRanges();
            saveRanges();
        } else {
            loadRanges();
        }

        updateMaxSingleIdsMap();
        updateCandidateAddresses();
    }

    private void debug(String text) {
        Log.d(TAG, text);
        DebugLog.printEvent(text);
    }

    private void selectAllTypes() {
        mSelectedDeviceTypes.addAll(mDeviceToKsIdMap.keySet());
        mDeviceTypeListAdapter.setSelctedItemsRef(mSelectedDeviceTypes);
        LocalPreferences.putSelectedDeviceTypes(mSelectedDeviceTypes);
    }

    private void deselectAllTypes() {
        mSelectedDeviceTypes.clear();
        mDeviceTypeListAdapter.setSelctedItemsRef(mSelectedDeviceTypes);
        LocalPreferences.putSelectedDeviceTypes(mSelectedDeviceTypes);
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
        mGroupIdCheckBox.setChecked(LocalPreferences.getBoolean(LocalPreferences.Pref.RANGE_GROUP_CHECKED));
        mGroupIdSeekBar.setProgress(LocalPreferences.getInt(LocalPreferences.Pref.RANGE_GROUP_LAST_ID));
        mGroupFullToggle.setChecked(LocalPreferences.getBoolean(LocalPreferences.Pref.RANGE_GROUP_FULL_CHECKED));
        mSingleIdCheckBox.setChecked(LocalPreferences.getBoolean(LocalPreferences.Pref.RANGE_SINGLE_CHECKED));
        mSingleIdSeekBar.setProgress(LocalPreferences.getInt(LocalPreferences.Pref.RANGE_SINGLE_LAST_ID));
        mSingleFullToggle.setChecked(LocalPreferences.getBoolean(LocalPreferences.Pref.RANGE_SINGLE_FULL_CHECKED));
        updateRangeText();
    }

    private void saveRanges() {
        LocalPreferences.putBoolean(LocalPreferences.Pref.RANGE_GROUP_CHECKED, mGroupIdCheckBox.isChecked());
        LocalPreferences.putInt(LocalPreferences.Pref.RANGE_GROUP_LAST_ID, mGroupIdSeekBar.getProgress());
        LocalPreferences.putBoolean(LocalPreferences.Pref.RANGE_GROUP_FULL_CHECKED, mGroupFullToggle.isChecked());
        LocalPreferences.putBoolean(LocalPreferences.Pref.RANGE_SINGLE_CHECKED, mSingleIdCheckBox.isChecked());
        LocalPreferences.putInt(LocalPreferences.Pref.RANGE_SINGLE_LAST_ID, mSingleIdSeekBar.getProgress());
        LocalPreferences.putBoolean(LocalPreferences.Pref.RANGE_SINGLE_FULL_CHECKED, mSingleFullToggle.isChecked());
    }

    private boolean needInitRange() {
        return (LocalPreferences.getInt(LocalPreferences.Pref.RANGE_GROUP_LAST_ID, 99999) == 9999);
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


    private void addSingleDevice(String type) {
        HomeDevice device = createSingleDeviceOfType(type);
        if (device != null) {
            mNetwork.addDevice(device);
        }
    }

    private void addFullDeviceIf(String type) {
        String address = mDeviceTypeListAdapter.getAddress(type);
        if (address.isEmpty()) return;

        final int devId = Integer.parseInt(address.substring(2, 4), 16);
        final int grpId = Integer.parseInt(address.substring(4, 5), 16);

        if (mNetwork.getDevice(createKsAddress(devId, grpId, 0xF)) == null) {
            mNetwork.addDevice(createKsDevice(type, devId, grpId, 0xF));
        } else if (mNetwork.getDevice(createKsAddress(devId, 0xFF)) == null) {
            mNetwork.addDevice(createKsDevice(type, devId, 0xF, 0xF));
        }
    }

    private void addSelectedDevices() {
        mNetwork.addDevice(createSelectedDevices());
    }

    private void addAllDevicesInRange() {
        mNetwork.addDevice(createDevicesInRange());
    }

    private HomeDevice createSingleDeviceOfType(String type) {
        String address = mDeviceTypeListAdapter.getAddress(type);
        if (address != null && !address.isEmpty()) {
            try {
                int deviceId = Integer.parseInt(address.substring(2, 4), 16);
                int groupId = Integer.parseInt(address.substring(4, 5), 16);
                int singleId = Integer.parseInt(address.substring(5, 6), 16);
                return createKsDevice(type, deviceId, groupId, singleId);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    private List<HomeDevice> createSelectedDevices() {
        List<HomeDevice> devices = new ArrayList<>();

        for (String type: mSelectedDeviceTypes) {
            if (!mDeviceToKsIdMap.containsKey(type)) {
                debug("No device type: " + type);
                continue;
            }

            final HomeDevice device = createSingleDeviceOfType(type);
            if (device != null) {
                devices.add(device);
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

    private static String createKsAddress(int devId, int subId) {
        return String.format("::%02X%02X", devId, subId);
    }

    private static String createKsAddress(int devId, int grpId, int sglId) {
        return String.format("::%02X%1X%1X", devId, grpId, sglId);
    }

    private HomeDevice createKsDevice(String typeName, int deviceId, int groupId, int singleId) {
        String address = createKsAddress(deviceId, groupId, singleId);

        int area = HomeDevice.Area.UNKNOWN;
        String groupStr = (groupId == 0x0F) ? "F" : Integer.valueOf(groupId).toString();
        String singleStr =  (singleId == 0x0F) ? "F" : Integer.valueOf(singleId).toString();
        String name = typeName + " (" + groupStr + "-" + singleStr + ")";

        final PropertyMap defaultProps = new BasicPropertyMap();
        defaultProps.put(HomeDevice.PROP_ADDR, address.toUpperCase());
        defaultProps.put(HomeDevice.PROP_AREA, area);
        defaultProps.put(HomeDevice.PROP_NAME, name);

        if (mNetwork.isSlaveMode()) {
            switch (typeName) {
                case "AirConditioner": {
                    long supportedModes = 0;
                    supportedModes |= AirConditioner.OpMode.AUTO;
                    supportedModes |= AirConditioner.OpMode.COOLING;
                    supportedModes |= AirConditioner.OpMode.HEATING;
                    supportedModes |= AirConditioner.OpMode.BLOWING;
                    supportedModes |= AirConditioner.OpMode.DEHUMID;
                    supportedModes |= AirConditioner.OpMode.RESERVED;
                    defaultProps.put(AirConditioner.PROP_SUPPORTED_MODES, supportedModes);
                    defaultProps.put(HomeDevice.PROP_ONOFF, true);
                    break;
                }
                case "BatchSwitch": {
                    long supportedSwitches = 0;
                    supportedSwitches |= BatchSwitch.Switch.GAS_LOCKING;
                    supportedSwitches |= BatchSwitch.Switch.OUTING_SETTING;
                    supportedSwitches |= BatchSwitch.Switch.BATCH_LIGHT_OFF;
                    supportedSwitches |= BatchSwitch.Switch.POWER_SAVING;
                    supportedSwitches |= BatchSwitch.Switch.ELEVATOR_UP_CALL;
                    supportedSwitches |= BatchSwitch.Switch.ELEVATOR_DOWN_CALL;
                    supportedSwitches |= BatchSwitch.Switch.THREEWAY_LIGHT;
                    supportedSwitches |= BatchSwitch.Switch.COOKTOP_OFF;
                    supportedSwitches |= BatchSwitch.Switch.HEATER_SAVING;
                    defaultProps.put(BatchSwitch.PROP_SUPPORTED_SWITCHES, supportedSwitches);
                    break;
                }
                case "Curtain": {
                    int supportedFunctions = 0;
                    supportedFunctions |= Curtain.Support.STATE;
                    supportedFunctions |= Curtain.Support.OPEN_LEVEL;
                    supportedFunctions |= Curtain.Support.OPEN_ANGLE;
                    defaultProps.put(Curtain.PROP_SUPPORTS, supportedFunctions);
                    break;
                }
                case "DoorLock": {
                    defaultProps.put(DoorLock.PROP_SUPPORTED_STATES, DoorLock.State.DOOR_OPENED | DoorLock.State.EMERGENCY_ALARMED);
                    break;
                }
                case "GasValve": {
                    defaultProps.put(GasValve.PROP_SUPPORTED_STATES, GasValve.State.GAS_VALVE | GasValve.State.INDUCTION);
                    defaultProps.put(GasValve.PROP_SUPPORTED_ALARMS, GasValve.Alarm.EXTINGUISHER_BUZZING | GasValve.Alarm.GAS_LEAKAGE_DETECTED);
                    break;
                }
                case "HouseMeter": {
                    defaultProps.put(HouseMeter.PROP_METER_ENABLED, true);
                    defaultProps.put(HomeDevice.PROP_ONOFF, true);
                    break;
                }
                case "Light": {
                    defaultProps.put(Light.PROP_DIM_SUPPORTED, true);
                    defaultProps.put(Light.PROP_TONE_SUPPORTED, true);
                    break;
                }
                case "PowerSaver": {
                    long supportedState = 0;
                    supportedState |= PowerSaver.State.OVERLOAD_DETECTED;
                    supportedState |= PowerSaver.State.STANDBY_DETECTED;
                    defaultProps.put(PowerSaver.PROP_SUPPORTED_STATES, supportedState);
                    defaultProps.put(PowerSaver.PROP_SUPPORTED_SETTINGS, PowerSaver.Setting.STANDBY_BLOCKING_ON);
                    break;
                }
                case "Thermostat": {
                    long supportedFunctions = 0;
                    supportedFunctions |= Thermostat.Function.HEATING;
                    supportedFunctions |= Thermostat.Function.OUTING_SETTING;
                    supportedFunctions |= Thermostat.Function.HOTWATER_ONLY;
                    supportedFunctions |= Thermostat.Function.RESERVED_MODE;
                    defaultProps.put(Thermostat.PROP_SUPPORTED_FUNCTIONS, supportedFunctions);
                    defaultProps.put(Thermostat.PROP_MIN_TEMPERATURE, 0.0f);
                    defaultProps.put(Thermostat.PROP_MAX_TEMPERATURE, 40.0f);
                    defaultProps.put(Thermostat.PROP_TEMP_RESOLUTION, 0.5f);
                    defaultProps.put(Thermostat.PROP_SETTING_TEMPERATURE, 10.0f);
                    defaultProps.put(Thermostat.PROP_CURRENT_TEMPERATURE, 10.0f);
                    break;
                }
                case "Ventilation": {
                    long supportedModes = 0;
                    supportedModes |= Ventilation.Mode.NORMAL;
                    supportedModes |= Ventilation.Mode.SLEEP;
                    supportedModes |= Ventilation.Mode.RECYCLE;
                    supportedModes |= Ventilation.Mode.AUTO;
                    supportedModes |= Ventilation.Mode.SAVING;
                    supportedModes |= Ventilation.Mode.CLEANAIR;
                    supportedModes |= Ventilation.Mode.INTERNAL;
                    defaultProps.put(Ventilation.PROP_SUPPORTED_MODES, supportedModes);

                    long supportedSensors = 0;
                    supportedSensors |= Ventilation.Sensor.CO2;
                    defaultProps.put(Ventilation.PROP_SUPPORTED_SENSORS, supportedSensors);

                    defaultProps.put(Ventilation.PROP_OPERATION_MODE, Ventilation.Mode.NORMAL);
                    defaultProps.put(Ventilation.PROP_OPERATION_ALARM, 0);
                    defaultProps.put(Ventilation.PROP_CUR_FAN_SPEED, 0);
                    defaultProps.put(Ventilation.PROP_MIN_FAN_SPEED, 0);
                    defaultProps.put(Ventilation.PROP_MAX_FAN_SPEED, 5);
                    break;
                }
            }
        }

        return mNetwork.createDevice(defaultProps);
    }

    private void updateMaxSingleIdsMap() {
        mMaxSingleIdsMap.clear();

        for (HomeDevice device: mNetwork.getAllDevices()) {
            int devId = Integer.parseInt(device.getAddress().substring(2, 4), 16);

            final int[] ids = getMaxSingleIdsRef(devId);
            int subId = Integer.parseInt(device.getAddress().substring(4, 6), 16);
            int grpId = (subId & 0xF0) >> 4;
            int sglId = (subId & 0x0F);

            if (sglId != 0xF && ids[grpId] < sglId) {
                ids[grpId] = sglId;
            }
        }
    }

    private int[] getMaxSingleIdsRef(int devId) {
        int[] ids = mMaxSingleIdsMap.get(devId);
        if (ids == null) {
            ids = new int[0xF];
            mMaxSingleIdsMap.put(devId, ids);
            Arrays.fill(ids, 0);
        }
        return ids;
    }

    private void updateCandidateAddresses() {
        final List<Integer> devIdList = new ArrayList<>(mDeviceToKsIdMap.values());
        final String[] addresses = new String[devIdList.size()];

        for (int i=0; i<devIdList.size(); i++) {
            String addr = mDeviceTypeListAdapter.getAddress(i);
            if (addr.length() < 6) addr = getInitialAddress(devIdList.get(i));

            final int devId = Integer.parseInt(addr.substring(2, 4), 16);
            final int subId = Integer.parseInt(addr.substring(4, 6), 16);

            final int[] ids = getMaxSingleIdsRef(devId);

            final int grpId = (subId & 0xF0) >> 4;
            final int sglId = ids[grpId] + 1;

            if (sglId < 0xF) {
                addresses[i] = createKsAddress(devId, grpId, sglId);
            } else {
                addresses[i] = getNextAddress(devId, grpId, sglId);
            }
        }

        mDeviceTypeListAdapter.setAddresses(addresses);
    }

    private String getNextAddress(String address) {
        if (address == null || address.length() < 6) return "";
        final int devId = Integer.parseInt(address.substring(2, 4), 16);
        final int subId = Integer.parseInt(address.substring(4, 6), 16);
        final int grpId = (subId & 0xF0) >> 4;
        final int sglId = (subId & 0x0F);
        return getNextAddress(devId, grpId, sglId);
    }

    private String getNextAddress(int devId, int grpId, int sglId) {
        int[] ids = getMaxSingleIdsRef(devId);
        if (ids == null) {
            return getInitialAddress(devId);
        }

        int newGrpId = grpId;
        int newSglId = sglId + 1;

        if (newSglId < 0xF) {
            return createKsAddress(devId, newGrpId, newSglId);
        }

        for (int i=0; i<0xF; i++) {
            if (++newGrpId >= 0xF) newGrpId = 0;
            newSglId = ids[newGrpId] + 1;
            if (newSglId < 0xF) {
                return createKsAddress(devId, newGrpId, newSglId);
            }
        }

        return "";
    }

    private void updateGroupShiftedInAddress(String type, boolean reversed) {
        final List<String> keyList = new ArrayList<>(mDeviceToKsIdMap.keySet());
        final int index = keyList.indexOf(type);
        if (index < 0) return;

        final String address = mDeviceTypeListAdapter.getAddress(type);
        if (address == null || address.length() < 6) return;

        final int devId = Integer.parseInt(address.substring(2, 4), 16);
        final int subId = Integer.parseInt(address.substring(4, 6), 16);
        final int grpId = (subId & 0xF0) >> 4;
        final int sglId = (subId & 0x0F);

        int newGrpId = grpId;
        int newSglId = 0;

        int[] ids = mMaxSingleIdsMap.get(devId);
        if (ids == null) return;

        for (int i=0; i<0xF; i++) {
            if (reversed) {
                if (--newGrpId < 0) newGrpId = 0xE;
            } else {
                if (++newGrpId > 0xE) newGrpId = 0;
            }

            int nxtSglId = ids[newGrpId] + 1;
            if (nxtSglId < 0xF) {
                newSglId = nxtSglId;
                break;
            }
        }

        if (newSglId > 0) {
            final String newAddr = String.format("::%02X%01X%01X", devId, newGrpId, newSglId);
            mDeviceTypeListAdapter.setAddress(index, newAddr);
        }
    }

    private String getInitialAddress(int devId) {
        int subId = 0x01;
        if (devId == 0x0E || devId == 0x39 || devId == 0x36) {
            subId += 0x10;
        }
        return createKsAddress(devId, subId);
    }
}
