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
import android.util.Log;

import kr.or.kashi.hde.base.ByteArrayBuffer;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.Light;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [KS X 4506] The light implementation for Light
 */
public class KSLight extends KSDeviceContextBase {
    private static final String TAG = "KSLight";
    private static final boolean DBG = true;

    public static final int CMD_LIGHT_ALL_CONTROL_REQ = 0x43;

    protected int mTotalCountInGroup = 0;

    public KSLight(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps, Light.class);

        if (!isSlave()) { // TODO:
            // Register the tasks to be performed when specific property changes.
            setPropertyTask(HomeDevice.PROP_ONOFF, mSingleControlTask);
            setPropertyTask(Light.PROP_CUR_DIM_LEVEL, mSingleControlTask);
        }
    }

    @Override
    protected @ParseResult int parseStatusReq(KSPacket packet, PropertyMap outProps) {
        ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // error code

        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();
        if (thisSubId.isSingle() || thisSubId.isSingleOfGroup()) {
            data.append(makeSingleLightStateByte(outProps));
        } else if (thisSubId.isFull() || thisSubId.isFullOfGroup()) {
            int childCount = getChildCount();
            for (int i=0; i<childCount; i++) {
                KSLight child = (KSLight) getChildAt(i);
                if (child != null) {
                    data.append(makeSingleLightStateByte(child.getReadPropertyMap()));
                } else {
                    data.append(0);
                }
            }
        } else {
            Log.w(TAG, "parse-status-req: should never reach this");
        }

        sendPacket(createPacket(CMD_STATUS_RSP, data.toArray()));

        return PARSE_OK_NONE;
    }

    @Override
    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 2) {
            if (DBG) Log.w(TAG, "parse-status-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-status-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        if (KSAddress.toDeviceSubId(packet.deviceSubId).isSingle()) {
            final int state = packet.data[1] & 0xFF;
            parseSingleLightStateByte(state, outProps);
        } else {
            KSAddress address = (KSAddress)getAddress();
            int index = address.getDeviceSubId().value() & 0x0F;
            if (index != 0x00 && index != 0x0F) {
                if (index < packet.data.length) {
                    // Pick only one state related with this device
                    final int state = packet.data[index] & 0xFF;
                    parseSingleLightStateByte(state, outProps);
                }
            }
        }

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseCharacteristicReq(KSPacket packet, PropertyMap outProps) {
        final PropertyMap props = getReadPropertyMap();
        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // error code

        int normalCount = 0;
        int dimmableCount = 0;
        int dimmableFlags = 0;

        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();
        if (thisSubId.isSingle() || thisSubId.isSingleOfGroup()) {
            boolean dimSupported = props.get(Light.PROP_DIM_SUPPORTED, Boolean.class);
            normalCount = dimSupported ? 0 : 1;
            dimmableCount = dimSupported ? 1 : 0;
        } else if (thisSubId.isFull() || thisSubId.isFullOfGroup()) {
            int childCount = getChildCount();
            for (int i=0; i<childCount; i++) {
                KSLight child = (KSLight) getChildAt(i);
                if (child != null && child.getReadPropertyMap().get(Light.PROP_DIM_SUPPORTED, Boolean.class)) {
                    dimmableCount++;
                    dimmableFlags |= (1 << i);
                } else {
                    normalCount++;
                }
            }
        }

        data.append(normalCount);
        data.append(dimmableCount);
        data.append((dimmableFlags >> 0) & 0xFF);
        data.append((dimmableFlags >> 8) & 0xFF);

        sendPacket(createPacket(CMD_CHARACTERISTIC_RSP, data.toArray()));

        return PARSE_OK_NONE;
    }

    @Override
    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 3) {
            if (DBG) Log.w(TAG, "parse-chr-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-chr-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        final int normalCount = packet.data[1] & 0xFF;
        final int dimmableCount = packet.data[2] & 0xFF;
        final int totalCount = normalCount + dimmableCount;

        if (KSAddress.toDeviceSubId(packet.deviceSubId).isSingle()) {
            outProps.put(Light.PROP_DIM_SUPPORTED, (dimmableCount > 0));
            mTotalCountInGroup = 1;
            return PARSE_OK_PEER_DETECTED;
        } else {
            mTotalCountInGroup = totalCount;
        }

        KSAddress address = (KSAddress)getAddress();
        int index = address.getDeviceSubId().value() & 0x0F;
        if (index > totalCount) {
            // Exit since the index is out of count.
            return PARSE_OK_NONE;
        }

        boolean dimSupported = false;

        if (packet.data.length < 5) {
            // HACK: Just guess characteristic if the data is shorter than normal.
            dimSupported = (dimmableCount == totalCount);
            outProps.put(Light.PROP_DIM_SUPPORTED, dimSupported);
            return PARSE_OK_PEER_DETECTED;
        }

        final int dimFlag1 = packet.data[3] & 0xFF;
        final int dimFlag2 = packet.data[4] & 0xFF;

        if (index >= 1 && index <= 8) {
            dimSupported = ((dimFlag1 >> (index-1)) & 0x01) != 0;
        } else if (index >= 9 && index <= 0xE) {
            dimSupported = ((dimFlag2 >> (index-9)) & 0x01) != 0;
        }

        outProps.put(Light.PROP_DIM_SUPPORTED, dimSupported);

        return PARSE_OK_PEER_DETECTED;
    }

    @Override
    protected @ParseResult int parseSingleControlRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length != 2) {
            if (DBG) Log.w(TAG, "parse-ctrl-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-ctrl-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.CANT_CONTROL);
            return PARSE_OK_ERROR_RECEIVED;
        }

        final int state = packet.data[1] & 0xFF;
        parseSingleLightStateByte(state, outProps);

        return PARSE_OK_ACTION_PERFORMED;
    }

    private int makeSingleLightStateByte(PropertyMap props) {
        final boolean isOn = props.get(HomeDevice.PROP_ONOFF, Boolean.class);
        final boolean dimSupported = props.get(Light.PROP_DIM_SUPPORTED, Boolean.class);
        final int dimLevel = props.get(Light.PROP_CUR_DIM_LEVEL, Integer.class);

        int state = 0;
        if (isOn) state |= (1 << 0);
        if (dimSupported) state |= (1 << 1);
        state |= (dimLevel & 0x0F) << 4;

        return state;
    }

    private void parseSingleLightStateByte(int state, PropertyMap outProps) {
        final boolean isOn = (state & 0x01) != 0;
        final boolean dimSupported = (state & 0x02) != 0;
        final int dimLevel = (state >> 4) & 0x0F;

        outProps.put(HomeDevice.PROP_ONOFF, isOn);
        outProps.put(Light.PROP_DIM_SUPPORTED, dimSupported);
        outProps.put(Light.PROP_CUR_DIM_LEVEL, dimLevel);
    }

    @Override
    protected KSPacket makeControlReq(PropertyMap props) {
        KSAddress address = (KSAddress)getAddress();
        if (address.getDeviceSubId().isAll())
            return makeAllControlReq(props);
        else if (address.getDeviceSubId().isFullOfGroup())
            return makeGroupControlReq(props);
        return makeSingleControlReq(props);
    }

    private KSPacket makeAllControlReq(PropertyMap props) {
        final boolean isOn = (Boolean) props.get(HomeDevice.PROP_ONOFF).getValue();
        return createPacket(CMD_LIGHT_ALL_CONTROL_REQ, (byte)(isOn ? 0x01 : 0x00));
    }

    private KSPacket makeGroupControlReq(PropertyMap props) {
        final boolean isOn = (Boolean) props.get(HomeDevice.PROP_ONOFF).getValue();
        return createPacket(CMD_GROUP_CONTROL_REQ, (byte)(isOn ? 0x01 : 0x00));
    }

    private KSPacket makeSingleControlReq(PropertyMap props) {
        final boolean isOn = (Boolean) props.get(HomeDevice.PROP_ONOFF).getValue();
        final int dimLevel = (int) props.get(Light.PROP_CUR_DIM_LEVEL).getValue();
        // TODO: check if the dimLevel is between min and max
        // TODO: assert if dimLevel is within 0x0 ~ 0xF
        return createPacket(CMD_SINGLE_CONTROL_REQ, makeLightControlData(isOn, dimLevel));
    }

    private byte[] makeLightControlData(boolean isOn, int dimLevel) {
        byte[] data = new byte[1];
        data[0] = (byte)(((isOn) ? 1 : 0) | ((dimLevel & 0x0F) << 4));
        return data;
    }
}
