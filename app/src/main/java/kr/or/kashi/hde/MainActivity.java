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

import static android.os.Build.VERSION.SDK_INT;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import kr.or.kashi.hde.session.NetworkSession;
import kr.or.kashi.hde.session.UartSchedSession;
import kr.or.kashi.hde.session.UsbNetworkSession;
import kr.or.kashi.hde.util.Utils;

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

    private Spinner mPortsSpinner;
    private Spinner mProtocalsSpinner;
    private Spinner mModesSpinner;

    private HomeNetwork mHomeNetwork;

    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
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

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(Utils.createDensityAdjustedContextByHeight(newBase, 600));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.root);
        registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_PERMISSION));

        mHandler = new Handler(Looper.getMainLooper());
        mInputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        findViewById(R.id.start_button).setOnClickListener(v -> startEmulator());
        findViewById(R.id.stop_button).setOnClickListener(v -> stopEmulator());

        List<String> portTypes = new ArrayList<>();
        portTypes.add(PORT_TYPE_USB);
        portTypes.add(PORT_TYPE_INTERNAL);
        mPortsSpinner = findViewById(R.id.ports_spinner);
        mPortsSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.spinner_item, portTypes));

        List<String> protocolTypes = new ArrayList<>();
        protocolTypes.add(PROTOCOL_TYPE_KSX4506);
        mProtocalsSpinner = findViewById(R.id.protocols_spinner);
        mProtocalsSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.spinner_item, protocolTypes));

        List<String> modeTypes = new ArrayList<>();
        modeTypes.add(MODE_TYPE_MASTER);
        modeTypes.add(MODE_TYPE_SLAVE);
        mModesSpinner = findViewById(R.id.modes_spinner);
        mModesSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.spinner_item, modeTypes));

        mEmptyFragment = new EmptyFragment();

        stopEmulator();
        new Handler().postDelayed(this::startEmulator, 100);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbReceiver);
    }

    private void setStateText(String text) {
        Log.d(TAG, "" + text);
        if (mEmptyFragment != null) {
            mEmptyFragment.setText(text);
        }
    }

    private void startEmulator() {
        setStateText("STARTING...");

        NetworkSession networkSession = null;

        switch (mPortsSpinner.getSelectedItem().toString()) {
            case PORT_TYPE_INTERNAL: {
                try {
                    networkSession = new UartSchedSession(this, mHandler, "/dev/ttyS4", 9600);
                } catch (RuntimeException e) {
                    setStateText("ERROR: INTERNAL PORT IS NOT SUPPORTED!");
                    break;
                }
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
                break;
            }
        }

        if (networkSession == null) {
            return;
        }

        final boolean isSlaveMode = MODE_TYPE_SLAVE.equals(mModesSpinner.getSelectedItem().toString());

        mHomeNetwork = new HomeNetwork(this, isSlaveMode);
        boolean res = mHomeNetwork.start(networkSession);
        if (!res) {
            setStateText("ERROR: CAN'T START NETWORK!");
            return;
        }

        showFragment(new MainFragment(this, mHomeNetwork));
    }

    private void stopEmulator() {
        setStateText("STOPPED");

        if (mHomeNetwork != null) {
            mHomeNetwork.stop();
        }

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
}