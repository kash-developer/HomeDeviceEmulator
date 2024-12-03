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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kr.or.kashi.hde.session.NetworkSession;
import kr.or.kashi.hde.session.UartSchedSession;
import kr.or.kashi.hde.session.UsbNetworkSession;
import kr.or.kashi.hde.util.LocalPreferences;
import kr.or.kashi.hde.util.LocalPreferences.Pref;
import kr.or.kashi.hde.util.Utils;
import kr.or.kashi.hde.BuildConfig;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String PORT_TYPE_INTERNAL = "INTERNAL";
    private static final String PORT_TYPE_USB = "USB";
    private static final String PROTOCOL_TYPE_KSX4506 = "KS X 4506";
    private static final String MODE_TYPE_MASTER = "MASTER";
    private static final String MODE_TYPE_SLAVE = "SLAVE";

    private static final String ACTION_USB_PERMISSION = "kr.or.kashi.hde.ACTION_USB_PERMISSION";

    private Handler mHandler;
    private InputMethodManager mInputMethodManager;
    private EmptyFragment mEmptyFragment;
    private TextView mVersionText;
    private Button mStartButton;
    private Button mStopButton;
    private Spinner mPortsSpinner;
    private Spinner mProtocalsSpinner;
    private Spinner mModesSpinner;

    private HomeNetwork mHomeNetwork;

    private BroadcastReceiver mUsbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    Log.d(TAG, "Device permission granted: " + device);
                    startEmulator();
                } else {
                    setStateText("PERMISSION DENIED");
                }
            }
        }
    };

    private BroadcastReceiver mExternalMediaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction())) {
                Uri uri = intent.getData();
                if (uri != null) {
                    checkUpdateFile(uri.getPath());
                }
            }
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(Utils.createDensityAdjustedContextByHeight(newBase, 600));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.root);

        registerReceiver(mUsbPermissionReceiver, new IntentFilter(ACTION_USB_PERMISSION));

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addDataScheme("file");
        registerReceiver(mExternalMediaReceiver, intentFilter);

        mHandler = new Handler(Looper.getMainLooper());
        mInputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        mVersionText = findViewById(R.id.version_text);
        mVersionText.setText(BuildConfig.VERSION_NAME);

        mStartButton = findViewById(R.id.start_button);
        mStartButton.setOnClickListener(v -> startEmulator());
        mStartButton.setEnabled(true);
        mStopButton = findViewById(R.id.stop_button);
        mStopButton.setOnClickListener(v -> stopEmulator());
        mStopButton.setEnabled(false);

        List<String> portTypes = new ArrayList<>();
        portTypes.add(PORT_TYPE_USB);
        portTypes.add(PORT_TYPE_INTERNAL);
        mPortsSpinner = findViewById(R.id.ports_spinner);
        mPortsSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.spinner_item, portTypes));
        mPortsSpinner.setSelection(LocalPreferences.getInt(Pref.PORT_INDEX));

        List<String> protocolTypes = new ArrayList<>();
        protocolTypes.add(PROTOCOL_TYPE_KSX4506);
        mProtocalsSpinner = findViewById(R.id.protocols_spinner);
        mProtocalsSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.spinner_item, protocolTypes));
        mProtocalsSpinner.setSelection(LocalPreferences.getInt(Pref.PROTOCOL_INDEX));

        List<String> modeTypes = new ArrayList<>();
        modeTypes.add(MODE_TYPE_MASTER);
        modeTypes.add(MODE_TYPE_SLAVE);
        mModesSpinner = findViewById(R.id.modes_spinner);
        mModesSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.spinner_item, modeTypes));
        mModesSpinner.setSelection(LocalPreferences.getInt(Pref.MODE_INDEX));

        mEmptyFragment = new EmptyFragment();
        setStateText("STOPPED");
        showFragment(mEmptyFragment);

        if (LocalPreferences.getBoolean(Pref.LAST_RUNNING)) {
            stopEmulator();
            new Handler().postDelayed(this::startEmulator, 100);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbPermissionReceiver);
        unregisterReceiver(mExternalMediaReceiver);
    }

    private void setStateText(String text) {
        Log.d(TAG, "" + text);
        if (mEmptyFragment != null) {
            mEmptyFragment.setText(text);
        }
    }

    private String findInternalSerialPort() {
        if (Build.MANUFACTURER.length() > 7 && !Build.MANUFACTURER.substring(3,5).equals("AV")) {
            Log.e(TAG, "Internal port is not supported for manufacturer:" + Build.MANUFACTURER);
            return null;
        }

        final String portPath = "/dev/ttyS0";
        if (!new File(portPath).exists()) {
            Log.e(TAG, portPath + " is does not exist");
            return null;
        }

        return portPath;
    }

    private void suppressInternalService() {
        for (com.kdiwin.wall.home.WHomeNetwork network: com.kdiwin.wall.Wall.getManager(
                this, com.kdiwin.wall.home.WHomeNetworkManager.class).
                        getInstalledNetworks()) {
            network.stop();
        }
    }

    private void startEmulator() {
        setStateText("STARTING...");

        final boolean isSlaveMode = MODE_TYPE_SLAVE.equals(mModesSpinner.getSelectedItem().toString());

        NetworkSession networkSession = null;

        switch (mPortsSpinner.getSelectedItem().toString()) {
            case PORT_TYPE_INTERNAL: {
                final String portPath = findInternalSerialPort();
                if (portPath == null) {
                    setStateText("ERROR: NO INTERNAL PORT!");
                    break;
                }
                suppressInternalService();

                try {
                    int portType = isSlaveMode ? UartSchedSession.PORT_TYPE_RS485_SLAVE : UartSchedSession.PORT_TYPE_RS485_MASTER;
                    networkSession = new UartSchedSession(this, mHandler, portType, portPath, 9600);
                } catch (RuntimeException e) {
                    setStateText("ERROR: PORT IS NOT SUPPORTED!");
                }
                Log.d(TAG, "Internal network session created! port:" + portPath);
                break;
            }

            case PORT_TYPE_USB: {
                List<UsbDevice> usbDevices = UsbNetworkSession.getAvailableDevices(this);
                if (usbDevices.isEmpty()) {
                    setStateText("ERROR: NO USB");
                    break;
                }

                Intent intent = new Intent(ACTION_USB_PERMISSION);
                intent.setPackage(getPackageName());
                if (!UsbNetworkSession.requestPermission(this, usbDevices.get(0), intent)) {
                    setStateText("WAITING USB PERMISSION");
                    break;
                }

                networkSession = new UsbNetworkSession(this);
                Log.d(TAG, "USB network session created! dev:" + usbDevices.get(0));
                break;
            }
        }

        if (networkSession == null) {
            return;
        }

        mHomeNetwork = new HomeNetwork(this, isSlaveMode);
        boolean res = mHomeNetwork.start(networkSession);
        if (!res) {
            setStateText("ERROR: CAN'T START NETWORK!");
            return;
        }

        // Save user selections before staring the emulator
        LocalPreferences.putInt(Pref.PORT_INDEX, mPortsSpinner.getSelectedItemPosition());
        LocalPreferences.putInt(Pref.PROTOCOL_INDEX, mProtocalsSpinner.getSelectedItemPosition());
        LocalPreferences.putInt(Pref.MODE_INDEX, mModesSpinner.getSelectedItemPosition());
        LocalPreferences.putBoolean(Pref.LAST_RUNNING, true);

        mPortsSpinner.setEnabled(false);
        mProtocalsSpinner.setEnabled(false);
        mModesSpinner.setEnabled(false);
        mStartButton.setEnabled(false);
        mStopButton.setEnabled(true);

        showFragment(new MainFragment(this, mHomeNetwork));
    }

    private void stopEmulator() {
        setStateText("STOPPED");

        if (mHomeNetwork != null) {
            mHomeNetwork.stop();
        }

        LocalPreferences.putBoolean(Pref.LAST_RUNNING, false);

        mPortsSpinner.setEnabled(true);
        mProtocalsSpinner.setEnabled(true);
        mModesSpinner.setEnabled(true);
        mStartButton.setEnabled(true);
        mStopButton.setEnabled(false);

        showFragment(mEmptyFragment);
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, fragment)
                .commit();
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent ev) {
        final View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            if (!(currentFocus instanceof EditText) || !isTouchInsideView(ev, currentFocus)) {
                currentFocus.clearFocus();
                mInputMethodManager.hideSoftInputFromWindow(
                        currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean isTouchInsideView(final MotionEvent ev, final View view) {
        final int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return ev.getRawX() > loc[0] && ev.getRawY() > loc[1] && ev.getRawX() < (loc[0] + view.getWidth())
            && ev.getRawY() < (loc[1] + view.getHeight());
    }

    private void checkUpdateFile(String rootPath) {
        final String apkFileName = "HomeDeviceEmulator.apk";
        final File srcApkFile = new File(rootPath, apkFileName);
        if (!srcApkFile.exists()) {
            Log.i(TAG, apkFileName + " file is not found on " + rootPath);
            return;
        }

        if (!Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            final File tmpApkFile = new File(getFilesDir(), "tmp.apk");
            if (Utils.copyFile(srcApkFile, tmpApkFile)) {
                handler.post(() -> installUpdateFile(tmpApkFile));
            } else {
                Log.e(TAG, "Can't copy file! src:" + srcApkFile + " dst:" + tmpApkFile);
            }
        });
    }

    private void installUpdateFile(File apkFile) {
        try {
            final PackageManager packageManager = getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageArchiveInfo(apkFile.getPath(), PackageManager.GET_META_DATA);
            if (packageInfo == null) {
                Log.e(TAG, "Can't get package info");
                return;
            }

            if (!getPackageName().equals(packageInfo.packageName)) {
                Log.e(TAG, "Different package name: " + packageInfo.packageName);
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        final Uri uri = FileProvider.getUriForFile(this, "kr.or.kashi.hde.installapkprovider", apkFile);
        Utils.installUnknownPackage(this, uri);
    }
}
