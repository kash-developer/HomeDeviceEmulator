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

package kr.or.kashi.hde.ksx4506;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import kr.or.kashi.hde.base.PropertyValue;
import kr.or.kashi.hde.DeviceContextBase;
import kr.or.kashi.hde.DeviceDiscovery;
import kr.or.kashi.hde.HomePacket;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.*;
import kr.or.kashi.hde.util.DebugLog;
import kr.or.kashi.hde.util.Utils;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * [KS X 4506] The implementation of main context
 */
public class KSMainContext extends MainContext {
    private static final String TAG = "KSMainContext";
    private static final boolean DBG = true;

    protected final Map<Integer, Class<?>> mAddressToClassMap = new HashMap<>();
    private DeviceDiscovery mDiscovery = null;

    public static int getDeviceIdFromProps(Map props) {
        PropertyValue propAddr = (PropertyValue) props.get(HomeDevice.PROP_ADDR);
        if (propAddr == null) return 0;
        KSAddress ksAddr = new KSAddress((String)propAddr.getValue());
        return ksAddr.getDeviceId();
    }

    public KSMainContext(Context context) {
        super(context);
        mAddressToClassMap.put(0x02, KSAirConditioner.class);
        mAddressToClassMap.put(0x33, KSBatchSwitch.class);
        mAddressToClassMap.put(0x35, KSBoiler.class);
        mAddressToClassMap.put(0x13, KSCurtain.class);
        mAddressToClassMap.put(0x31, KSDoorLock.class);
        mAddressToClassMap.put(0x12, KSGasValve.class);
        mAddressToClassMap.put(0x30, KSHouseMeter.class);
        mAddressToClassMap.put(0x0E, KSLight.class);
        mAddressToClassMap.put(0x39, KSPowerSaver.class);
        mAddressToClassMap.put(0x34, KSSecurityExpansion.class);
        mAddressToClassMap.put(0x36, KSThermostat.class);
        mAddressToClassMap.put(0x32, KSVentilation.class);
    }

    @Override
    public boolean addDevice(HomeDevice device) {
        boolean added = super.addDevice(device);
        if (added) {
            final KSDeviceContextBase dc = ((KSDeviceContextBase)device.dc());
            final int devId = dc.getDeviceId();
            final int subId = dc.getDeviceSubId().value();
            final int subIdUpper = (subId & 0xF0);
            final int subIdLower = (subId & 0x0F);
            if (subIdLower == 0x0F) {
                for (int i = 1; i <= 0xE; i++) {
                    HomeDevice child = getDevice(new KSAddress(devId, (subIdUpper | i)).toAddressString());
                    if (child != null) device.dc().addChild(child.dc());
                }
            } else {
                HomeDevice parent = getDevice(new KSAddress(devId, (subIdUpper | 0x0F)).toAddressString());
                if (parent != null) {
                    parent.dc().addChild(device.dc());
                }
            }
        }
        return added;
    }

    @Override
    public void removeDevice(HomeDevice device) {
        if (getDevice(device.getAddress()) != null) {
            final KSDeviceContextBase dc = ((KSDeviceContextBase)device.dc());
            final int devId = dc.getDeviceId();
            final int subId = dc.getDeviceSubId().value();
            if ((subId & 0x0F) == 0x0F) {
                device.dc().removeAllChildren();
            } else {
                HomeDevice parent = getDevice(new KSAddress(devId, (subId | 0x0F)).toAddressString());
                if (parent != null) {
                    parent.dc().removeChild(device.dc());
                }
            }
            super.removeDevice(device);
        }
    }

    @Override
    public void clearAllDevices() {
        for (HomeDevice device : getAllDevices()) {
            removeDevice(device);
        }
    }

    @Override
    public Class<?> getContextClass(Map defaultProps) {
        Class<?> contextClass = mAddressToClassMap.get(getDeviceIdFromProps(defaultProps));
        return (contextClass != null) ? contextClass : KSUnknown.class;
    }

    @Override
    public DeviceDiscovery getDeviceDiscovery() {
        if (mDiscovery == null) {
            mDiscovery = new DeviceDiscovery() {
                @Override
                public boolean isRunning() {
                    if (mStreamProcessor == null || !mStreamProcessor.isRunning()) {
                        Log.e(TAG,  "Can't start scanning since this network is not " +
                                    "installed or stream seesion is not established!");
                        return false;
                    }
                    return super.isRunning();
                }

                protected void pingDevice(HomeDevice device) {
                    final KSAddress address = (KSAddress) device.dc().getAddress();
                    int thisGroup = address.getDeviceSubId().value() & 0xF0;
                    int lastGroup = 0;
                    if (mLastPollAddress != null) {
                        lastGroup = ((KSAddress)mLastPollAddress).getDeviceSubId().value() & 0xF0;
                    }

                    // Ping only if device is single or not pinged as group.
                    final boolean doPing = (!address.getDeviceSubId().hasGroup()) ||
                            (address.getDeviceSubId().hasGroup() && thisGroup != lastGroup);
                    if (doPing) {
                        super.pingDevice(device);
                    }
                }
            };
        }
        return mDiscovery;
    }

    @Override
    public HomePacket createPacket() {
        return new KSPacket(); // TODO: reuse it as static or from pool
    }

    @Override
    public boolean parsePacket(ByteBuffer buffer) throws BufferUnderflowException {
        while ((buffer.get(buffer.position()) & 0xFF) != KSPacket.STX) {
            buffer.get(); // No header found yet, move next.
            if (!buffer.hasRemaining()) {
                return true;
            }
        }

        if (!KSPacket.ensure(buffer)) {
            // Not enough size of packet, return and wait for complete packget.
            throw new BufferUnderflowException();
        }

        KSPacket packet = (KSPacket) createPacket();
        if (!packet.parse(buffer)) {
            // Invalid packet. let's find next header.
            // Otherwise, if buffer is too short, this loop exits by the exception.
            buffer.get(); // move to next
            return false;
        }

        // TODO: Should be get more efficient instead of converting to byte buffer.
        ByteBuffer byteBuffer = ByteBuffer.allocate(64);
        packet.toBuffer(byteBuffer);
        DebugLog.printTxRx("RX: " + Utils.toHexString(byteBuffer));

        // Parse in the exact context of the id of device.
        parsePacketIfMatched(packet, packet.deviceId, packet.deviceSubId);

        // Parse in each single contexts if this packet is for all single devices.
        if (packet.deviceSubId == 0x0F) {
            for (int i = 1; i < 0x0F; i++) {
                parsePacketIfMatched(packet, packet.deviceId, i);
            }
        }

        // Parse in the contexts of same group of device too if it is of group.
        if ((packet.deviceSubId & 0x0F) == 0x0F) {
            int groupId = (packet.deviceSubId & 0xF0);
            for (int i = 1; i < 0x0F; i++) {
                parsePacketIfMatched(packet, packet.deviceId, (groupId | i));
            }
        }

        return true;
    }

    private void parsePacketIfMatched(KSPacket packet, int deviceId, int deviceSubId) {
        KSAddress address = new KSAddress(deviceId, deviceSubId);
        if (mDiscovery != null && mDiscovery.isRunning()) {
            if (packet.commandType == KSDeviceContextBase.CMD_CHARACTERISTIC_RSP) {
                mDiscovery.onParsePacket(address, packet);
            }
        } else {
            HomeDevice device = getDevice(address.getDeviceAddress());
            if (device != null) {
                device.dc().parsePacket(packet);
            }
        }
    }
}
