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

import kr.or.kashi.hde.base.BasicPropertyMap;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.base.PropertyValue;
import kr.or.kashi.hde.DeviceContextBase;
import kr.or.kashi.hde.DeviceContextBase.ParseResult;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * [KS X 4506] The implementation of main context
 */
public class KSMainContext extends MainContext {
    private static final String TAG = "KSMainContext";
    private static final boolean DBG = true;

    protected final Map<Integer, Class<?>> mAddressToClassMap = new HashMap<>();
    private DeviceDiscovery mDiscovery = null;
    private final Map<String, HomeDevice> mVirtualDeviceMap = new ConcurrentHashMap<>();

    public static int getDeviceIdFromProps(Map props) {
        PropertyValue propAddr = (PropertyValue) props.get(HomeDevice.PROP_ADDR);
        if (propAddr == null) return 0;
        KSAddress ksAddr = new KSAddress((String)propAddr.getValue());
        return ksAddr.getDeviceId();
    }

    public KSMainContext(Context context, boolean isSlaveMode) {
        super(context, isSlaveMode);
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
        final String address = device.getAddress();

        if (mVirtualDeviceMap.containsKey(address)) {
            // Remove virtual device if it's real.
            mVirtualDeviceMap.remove(address);
        }

        boolean added = super.addDevice(device); // Call super
        if (!added) {
            return false;
        }

        final KSDeviceContextBase dc = ((KSDeviceContextBase)device.dc());
        final int devId = dc.getDeviceId();
        final int subId = dc.getDeviceSubId().value();
        final int subIdUpper = (subId & 0xF0);
        final int subIdLower = (subId & 0x0F);
        if (subIdLower == 0x0F) {
            for (int i = 1; i <= 0xE; i++) {
                String childAddr = new KSAddress(devId, (subIdUpper | i)).toAddressString();
                HomeDevice child = getDevice(childAddr);
                if (child != null) device.dc().addChild(child.dc());
            }
        } else {
            String parentAddr = new KSAddress(devId, (subIdUpper | 0x0F)).toAddressString();
            HomeDevice parent = getDevice(parentAddr);
            if (parent == null) {
                parent = mVirtualDeviceMap.get(parentAddr);
                if (parent == null) {
                    PropertyMap props = new BasicPropertyMap();
                    props.put(HomeDevice.PROP_ADDR, parentAddr);
                    props.put(HomeDevice.PROP_AREA, HomeDevice.Area.UNKNOWN);
                    props.put(HomeDevice.PROP_NAME, "Virtual " + parentAddr);
                    props.put(HomeDevice.PROP_IS_SLAVE, device.getProperty(HomeDevice.PROP_IS_SLAVE, Boolean.class));

                    // Create new virutal device as parent.
                    parent = createDevice(props.toMap());
                    mVirtualDeviceMap.put(parentAddr, parent);
                }
            }
            parent.dc().addChild(device.dc());
        }

        return added;
    }

    @Override
    public void removeDevice(HomeDevice device) {
        final String address = device.getAddress();
        if (mVirtualDeviceMap.containsKey(address)) {
            mVirtualDeviceMap.remove(address);
        }

        final KSDeviceContextBase dc = ((KSDeviceContextBase)device.dc());
        if (dc.getDeviceSubId().hasFull()) { // all devices in group(?F) or all device (FF)
            dc.removeAllChildren();
        } else {
            final DeviceContextBase parent = dc.getParent();
            if (parent != null) {
                parent.removeChild(dc);

                final String parentAddress = parent.getAddress().getDeviceAddress();
                if (!parent.hasChild() && mVirtualDeviceMap.containsKey(parentAddress)) {
                    mVirtualDeviceMap.remove(parentAddress);
                }
            }
        }

        super.removeDevice(device); // Call super
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
                    int thisDevId = address.getDeviceId();
                    int thisGroup = address.getDeviceSubId().value() & 0xF0;

                    int lastDevId = 0;
                    int lastGroup = 0;
                    if (mLastPollAddress != null) {
                        lastDevId = ((KSAddress)mLastPollAddress).getDeviceId();
                        lastGroup = ((KSAddress)mLastPollAddress).getDeviceSubId().value() & 0xF0;
                    }

                    // Ping only if device is single or not pinged as group.
                    final boolean doPing =
                            (thisDevId != lastDevId) ||
                            (!address.getDeviceSubId().hasGroup()) ||
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

        // Parse packet for discovery if running.
        parsePacketInDiscovery(packet);

        // Parse packet in device contexts.
        parsePacketInDeviceContexts(packet);

        return true;
    }

    private void parsePacketInDiscovery(KSPacket packet) {
        if (mDiscovery == null || !mDiscovery.isRunning()) {
            return;
        }

        if (packet.commandType != KSDeviceContextBase.CMD_CHARACTERISTIC_RSP) {
            return;
        }

        // Parse in the exact context of the id of device.
        mDiscovery.onParsePacket(new KSAddress(packet.deviceId, packet.deviceSubId), packet);

        // Parse in each single contexts if this packet is for all single devices.
        if (packet.deviceSubId == 0x0F) {
            for (int i = 1; i < 0x0F; i++) {
                mDiscovery.onParsePacket(new KSAddress(packet.deviceId, i), packet);
            }
        }

        // Parse in the contexts of same group of device too if it is of group.
        if ((packet.deviceSubId & 0x0F) == 0x0F) {
            int groupId = (packet.deviceSubId & 0xF0);
            for (int i = 1; i < 0x0F; i++) {
                mDiscovery.onParsePacket(new KSAddress(packet.deviceId, (groupId | i)), packet);
            }
        }
    }

    private void parsePacketInDeviceContexts(KSPacket packet) {
        final KSAddress address = new KSAddress(packet.deviceId, packet.deviceSubId);
        final String devAddr = address.getDeviceAddress();
        HomeDevice device = getDevice(devAddr);

        @DeviceContextBase.ParseResult int res = DeviceContextBase.PARSE_OK_NONE;

        if (device == null) {
            // No device found, try to get from virtual.
            device = mVirtualDeviceMap.get(devAddr);
        }
        if (device != null) {
            res = device.dc().parsePacket(packet);

            if (device.dc().isMaster()) {
                // TODO: Consider if doing by parent is more efficient.
                for (DeviceContextBase child: device.dc().getChildren()) {
                    child.parsePacket(packet);
                }
            }
        }

        // HACK: In slave mode, if any response packet was sent, let's send
        // null packet to avoid timeout and make the port be ready in receive
        // mode as soon as possible.
        if (mIsSlaveMode && res == DeviceContextBase.PARSE_OK_NONE) {
            sendPacket(null, new HomePacket.Null());
        }
    }
}
