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
    private Set<String> mSelectedDeviceTypes = new HashSet<>();

    private DeviceDiscovery.Callback mDiscoveryCallback = new DeviceDiscovery.Callback() {
        @Override
        public void onDiscoveryFinished() {
            // Wait for a while until new devices are up to date by polling its state
            new Handler().postDelayed(() -> {
                mDiscoveryToggle.setChecked(false);
            }, 1000);
        }
    };

    public TypeListPartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void init(HomeNetwork network) {
        mNetwork = network;
        mNetwork.getDeviceDiscovery().addCallback(mDiscoveryCallback);
        mNetwork.addCallback(new HomeNetwork.Callback() {
            @Override public void onDeviceAdded(List<HomeDevice> devices) { updateDeviceTypeList(); }
            @Override public void onDeviceRemoved(List<HomeDevice> devices) { updateDeviceTypeList(); }
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

        final TypeListAdapter deviceTypeListAdapter = new TypeListAdapter(
                mContext, new ArrayList(mDeviceToKsIdMap.keySet()), mSelectedDeviceTypes);
        deviceTypeListAdapter.setCallback(new TypeListAdapter.Callback() {
            @Override
            public void onCheckedChanged(String item, boolean checked) {
                LocalPreferences.putSelectedDeviceTypes(mSelectedDeviceTypes);
            }
            @Override
            public void onAddButtonClicked(String item) {
                addSingleDevice(item);
            }
        });

        mDeviceTypeListView = findViewById(R.id.device_types_list);
        mDeviceTypeListView.setAdapter(deviceTypeListAdapter);

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

        updateDeviceTypeList();
    }

    private void debug(String text) {
        Log.d(TAG, text);
        DebugLog.printEvent(text);
    }

    private void selectAllTypes() {
        mSelectedDeviceTypes.addAll(mDeviceToKsIdMap.keySet());
        final TypeListAdapter deviceTypeListAdapter =
                (TypeListAdapter) mDeviceTypeListView.getAdapter();
        deviceTypeListAdapter.setSelctedItemsRef(mSelectedDeviceTypes);
        LocalPreferences.putSelectedDeviceTypes(mSelectedDeviceTypes);
    }

    private void deselectAllTypes() {
        mSelectedDeviceTypes.clear();
        final TypeListAdapter deviceTypeListAdapter =
                (TypeListAdapter) mDeviceTypeListView.getAdapter();
        deviceTypeListAdapter.setSelctedItemsRef(mSelectedDeviceTypes);
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
            updateDeviceTypeList();
        }
    }

    private void addSelectedDevices() {
        mNetwork.addDevice(createSelectedDevices());
        updateDeviceTypeList();
    }

    private void addAllDevicesInRange() {
        mNetwork.addDevice(createDevicesInRange());
        updateDeviceTypeList();
    }

    private HomeDevice createSingleDeviceOfType(String type) {
        final TypeListAdapter deviceTypeListAdapter =
                (TypeListAdapter) mDeviceTypeListView.getAdapter();

        String address = deviceTypeListAdapter.getAddress(type);
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

    private void updateDeviceTypeList() {
        int[] ids = new int[mDeviceToKsIdMap.size()];
        Arrays.fill(ids, 0);

        List<Integer> devIdList = new ArrayList<>(mDeviceToKsIdMap.values());

        for (HomeDevice device: mNetwork.getAllDevices()) {
            int devId = Integer.parseInt(device.getAddress().substring(2, 4), 16);
            int subId = Integer.parseInt(device.getAddress().substring(4, 6), 16);

            int pos = devIdList.indexOf(devId);
            if (pos < 0 || pos >= ids.length)
                continue;

            if (ids[pos] < subId) {
                ids[pos] = subId;
            }
        }

        final TypeListAdapter deviceTypeListAdapter =
                (TypeListAdapter) mDeviceTypeListView.getAdapter();

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

        final String[] addresses = new String[ids.length];
        for (int i=0; i<ids.length; i++) {
            if (ids[i] > -1) {
                addresses[i] = String.format("::%02X%02X", devIdList.get(i), ids[i]);
            } else {
                addresses[i] = "";
            }
        }

        deviceTypeListAdapter.setAddresses(addresses);
    }
}
