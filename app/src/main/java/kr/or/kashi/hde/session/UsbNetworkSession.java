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

package kr.or.kashi.hde.session;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

// https://github.com/mik3y/usb-serial-for-android
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UsbNetworkSession extends NetworkSessionAdapter
            implements SerialInputOutputManager.Listener {
    private static final String TAG = "UsbNetworkSession";
    private static final boolean DBG = true;
    private static final String INTENT_ACTION_GRANT_USB = TAG + ".GRANT_USB";

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final UsbManager mUsbManager;
    private UsbSerialPort mUsbSerialPort;
    private SerialInputOutputManager mUsbIoManager;

    private byte[] testPacketBytes = new byte[] {
        (byte)0xF1, (byte)0xF2, (byte)0xF3, (byte)0xF4, (byte)0xF5, (byte)0xF6, (byte)0xF7, (byte)0xF8,
        (byte)0xF9, (byte)0xFA, (byte)0xFB, (byte)0xFC,
    };

    public static List<UsbDevice> getAvailableDevices(Context context) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        List<UsbDevice> usbDevices = new ArrayList<>();
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        for (UsbSerialDriver usb: availableDrivers) {
            usbDevices.add(usb.getDevice());
        }
        return usbDevices;
    }

    public static boolean requestPermission(Context context, UsbDevice device, Intent intent) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (!usbManager.hasPermission(device)) {
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags);
            usbManager.requestPermission(device, usbPermissionIntent);
            return false;
        }
        return true;
    }

    public UsbNetworkSession(Context context) {
        mContext = context;
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    @Override
    public boolean onOpen() {
        // Find all available drivers from attached devices.
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "no available driver!");
            return false;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = mUsbManager.openDevice(driver.getDevice());
        if (connection == null && !mUsbManager.hasPermission(driver.getDevice())) {
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(INTENT_ACTION_GRANT_USB), flags);
            mUsbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            // TODO: This method should be called after receiving of pending intent.
            return false;
        }

        if (connection == null) {
            if (!mUsbManager.hasPermission(driver.getDevice())) {
                Log.d(TAG, "permission denied");
            } else {
                Log.d(TAG, "open failed");
            }
            return false;
        }

        mUsbSerialPort = driver.getPorts().get(0); // Most devices have just one port (port 0)
        try {
            mUsbSerialPort.open(connection);
            mUsbSerialPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            mUsbIoManager = new SerialInputOutputManager(mUsbSerialPort, this);
            mUsbIoManager.start();
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "can't open!");
            return false;
        }

        Log.d(TAG, "opened!");

        return true;
    }

    @Override
    public void onClose() {
        if (mUsbIoManager != null) {
            mUsbIoManager.setListener(null);
            mUsbIoManager.stop();
            mUsbIoManager = null;
        }

        try {
            if (mUsbSerialPort != null) {
                mUsbSerialPort.close();
                mUsbSerialPort = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "closed!");
    }

    @Override
    public void onWrite(byte[] b) {
        try {
            Thread.sleep(10); // HACK: Put some delay to avoid overwritten or splitted packet.
            mUsbSerialPort.write(b, 100 /* timeout */);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onNewData(byte[] data) {
        putData(data);
    }

    @Override
    public void onRunError(Exception e) {
        Log.d(TAG, "error!");
    }
}
