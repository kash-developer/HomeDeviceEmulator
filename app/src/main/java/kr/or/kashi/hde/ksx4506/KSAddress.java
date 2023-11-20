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

import java.net.InetAddress;
import java.net.UnknownHostException;

import kr.or.kashi.hde.HomeAddress;

/**
 * [KS X 4506] Address class
 */
public class KSAddress extends HomeAddress {
    private final int mDeviceId;
    private final DeviceSubId mDeviceSubId;

    public KSAddress(HomeAddress other) {
        this(other.getDeviceAddress());
    }

    public KSAddress(String address) {
        super(address);

        int devId = 0;
        int devSubId = 0;

        try {
            final byte[] addrBytes = InetAddress.getByName(address).getAddress();
            devId = addrBytes[addrBytes.length-2] & 0xFF;
            devSubId = addrBytes[addrBytes.length-1] & 0xFF;
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        mDeviceId = devId;
        mDeviceSubId = toDeviceSubId(devSubId);
    }

    public KSAddress(int deviceId, int deviceSubId) {
        super(toDeviceAddress(deviceId, deviceSubId));

        mDeviceId = deviceId;
        mDeviceSubId = toDeviceSubId(deviceSubId);
    }

    public int getDeviceId() { 
        return mDeviceId;
    }

    public DeviceSubId getDeviceSubId() { 
        return mDeviceSubId;
    }

    public String toAddressString() {
        return toDeviceAddress(mDeviceId, mDeviceSubId.value());
    }

    public static DeviceSubId toDeviceSubId(int deviceSubId) {
        return new DeviceSubId(deviceSubId);
    }

    private static String toDeviceAddress(int deviceId, int deviceSubId) {
        // Abbreviated IPv6 form, https://datatracker.ietf.org/doc/html/rfc5156
        return String.format("::%02X%02X", deviceId, deviceSubId);
    }

    public static class DeviceSubId {
        private int mId;

        DeviceSubId(int deviceSubId) {
            mId = deviceSubId;
        }

        public int value() {
            return mId;
        }

        public boolean hasSingle() {
            return (mId & 0x0F) != 0 && !hasFull();
        }
    
        public boolean hasGroup() {
            return (mId & 0xF0) != 0 && mId != 0xFF;
        }
    
        public boolean hasFull() {
            return (mId & 0x0F) == 0x0F;
        }
    
        public boolean isSingle() {
            return hasSingle() && !hasGroup();
        }

        public boolean isFull() {
            return hasFull() && !hasGroup();
        }

        public boolean isSingleOfGroup() {
            return hasSingle() && hasGroup();
        }
    
        public boolean isFullOfGroup() {
            return hasFull() && hasGroup();
        }
    
        public boolean isAll() {
            return mId == 0xFF;
        }
    }

    @Override
    public String toString() {
        return  KSAddress.class.getSimpleName() + " {" +
                    "mDeviceAddress={" +
                            String.format("%02x", getDeviceId()) + ", " +
                            String.format("%02x", getDeviceSubId().value()) +
                "}}";
    }
}
