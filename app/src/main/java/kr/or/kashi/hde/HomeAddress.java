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

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents the address of a home device.
 * @hide
 */
public class HomeAddress implements Parcelable {
    private final String mDeviceAddress;

    /**
     * Creates an instance of HomeAddress.
     *
     * @param name      Property name
     * @param value     Value of Property
     * @hide
     */
    public HomeAddress(String deviceAddress) {
        mDeviceAddress = (deviceAddress != null) ? deviceAddress.toUpperCase() : deviceAddress;
    }

    /**
     * Creates an instance of HomeAddress.
     *
     * @param in Parcel to read
     * @hide
     */
    @SuppressWarnings("unchecked")
    public HomeAddress(Parcel in) {
        String deviceAddress = in.readString();
        mDeviceAddress = (deviceAddress != null) ? deviceAddress.toUpperCase() : deviceAddress;
    }

    public static final Creator<HomeAddress> CREATOR = new Creator<HomeAddress>() {
        @Override
        public HomeAddress createFromParcel(Parcel in) {
            return new HomeAddress(in);
        }

        @Override
        public HomeAddress[] newArray(int size) {
            return new HomeAddress[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDeviceAddress);
    }

    public String getDeviceAddress() {
        return mDeviceAddress;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof HomeAddress)) return false;
        HomeAddress other = (HomeAddress)obj;
        if (!mDeviceAddress.equals(other.mDeviceAddress)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return mDeviceAddress.hashCode();
    }

    /** @hide */
    @Override
    public String toString() {
        return  "HomeAddress {" +
                    "mDeviceAddress=" + mDeviceAddress +
                "}";
    }
}
