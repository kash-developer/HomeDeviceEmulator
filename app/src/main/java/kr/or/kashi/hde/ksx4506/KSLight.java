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

import kr.or.kashi.hde.DeviceContextBase;
import kr.or.kashi.hde.base.ByteArrayBuffer;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.base.PropertyTask;
import kr.or.kashi.hde.base.PropertyValue;
import kr.or.kashi.hde.device.GasValve;
import kr.or.kashi.hde.device.Light;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [KS X 4506] The light implementation for Light
 */
public class KSLight extends KSDeviceContextBase {
    private static final String TAG = "KSLight";
    private static final boolean DBG = true;

    public static final int CMD_BATCH_LIGHT_OFF_REQ = 0x43;

    protected int mTotalCountInGroup = 0;

    public KSLight(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps, Light.class);

        if (isMaster()) {
            // Register the tasks to be performed when specific property changes.
            final PropertyTask onLightControlTask = this::onLightControlTask;
            setPropertyTask(HomeDevice.PROP_ONOFF, onLightControlTask);
            setPropertyTask(Light.PROP_CUR_DIM_LEVEL, onLightControlTask);
            setPropertyTask(Light.PROP_BATCH_LIGHT_OFF, this::onBatchLightOffTask);
        } else {
            setPropertyTask(HomeDevice.PROP_ONOFF, this::onLightOnOffTaskForSlave);
        }
    }

    public @ParseResult int parsePayload(KSPacket packet, PropertyMap outProps) {
        switch (packet.commandType) {
            case CMD_GROUP_CONTROL_REQ: return parseGroupControlReq(packet, outProps);
        }
        return super.parsePayload(packet, outProps);
    }

    @Override
    protected @ParseResult int parseStatusReq(KSPacket packet, PropertyMap outProps) {
        ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // error code

        @ParseResult int res = makeStatusRsp(packet, outProps, data);
        if (res < PARSE_OK_NONE) return res;

        sendPacket(createPacket(CMD_STATUS_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    protected int makeStatusRsp(KSPacket reqPacket, PropertyMap outProps, ByteArrayBuffer outData) {
        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();
        if (thisSubId.isSingle() || thisSubId.isSingleOfGroup()) {
            outData.append(makeSingleLightStateByte(outProps));
        } else if (thisSubId.isFull() || thisSubId.isFullOfGroup()) {
            for (KSLight child: getChildren(KSLight.class)) {
                outData.append(makeSingleLightStateByte(child.getReadPropertyMap()));
            }
        } else {
            Log.w(TAG, "parse-status-req: should never reach this");
        }
        return PARSE_OK_STATE_UPDATED;
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
            if (index == 0xF) {
                boolean isOn = false;
                for (int i=0; i<mTotalCountInGroup; i++) {
                    if ((1+i) < packet.data.length) {
                        final int state = packet.data[1 + i] & 0xFF;
                        isOn |= ((state & 0x01) != 0);
                    }
                }
                outProps.put(HomeDevice.PROP_ONOFF, isOn);
            } else if (index != 0x00 && index < packet.data.length) {
                // Pick only one state related with this device
                final int state = packet.data[index] & 0xFF;
                parseSingleLightStateByte(state, outProps);
            }
        }

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseCharacteristicReq(KSPacket packet, PropertyMap outProps) {
        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // error code

        @ParseResult int res = makeCharacteristicRsp(packet, outProps, data);
        if (res < PARSE_OK_NONE) return res;

        sendPacket(createPacket(CMD_CHARACTERISTIC_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    protected int makeCharacteristicRsp(KSPacket reqPacket, PropertyMap outProps, ByteArrayBuffer outData) {
        final PropertyMap props = getReadPropertyMap();

        int normalCount = 0;
        int dimmableCount = 0;
        int dimmableFlags = 0;

        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();
        if (thisSubId.isSingle() || thisSubId.isSingleOfGroup()) {
            boolean dimSupported = props.get(Light.PROP_DIM_SUPPORTED, Boolean.class);
            normalCount = dimSupported ? 0 : 1;
            dimmableCount = dimSupported ? 1 : 0;
        } else if (thisSubId.isFull() || thisSubId.isFullOfGroup()) {
            for (KSLight child: getChildren(KSLight.class)) {
                int index = normalCount + dimmableCount;
                if (child.getReadPropertyMap().get(Light.PROP_DIM_SUPPORTED, Boolean.class)) {
                    dimmableFlags |= (1 << index);
                    dimmableCount++;
                } else {
                    normalCount++;
                }
            }
        }

        outData.append(normalCount);
        outData.append(dimmableCount);
        outData.append((dimmableFlags >> 0) & 0xFF);
        outData.append((dimmableFlags >> 8) & 0xFF);

        return PARSE_OK_STATE_UPDATED;
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

    protected @ParseResult int parseSingleControlReq(KSPacket packet, PropertyMap outProps) {
        parseLightControlData(packet.data, outProps);

        // NOTE: Just use the output props for reading because uncommitted changes that is produced
        // by previous parsing could be staging in the output props and that should be reflect by
        // consecutive response packet to the peer.
        final PropertyMap props = outProps;

        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // error code
        data.append(makeSingleLightStateByte(props));

        sendPacket(createPacket(CMD_SINGLE_CONTROL_RSP, data.toArray()));

        return PARSE_OK_ACTION_PERFORMED;
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

    protected @ParseResult int parseGroupControlReq(KSPacket packet, PropertyMap outProps) {
        final boolean isOn = ((packet.data[0] & 0xFF) == 0x01);
        outProps.put(HomeDevice.PROP_ONOFF, isOn);

        for (KSLight child: getChildren(KSLight.class)) {
            child.parseGroupControlReq(packet, child.mRxPropertyMap);
            child.commitPropertyChanges(child.mRxPropertyMap);
        }

        return PARSE_OK_STATE_UPDATED;
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

    private boolean onLightControlTask(PropertyMap reqProps, PropertyMap outProps) {
        final KSAddress.DeviceSubId subId = getDeviceSubId();
        if (subId.isFullOfGroup() || subId.isAll()) {
            sendPacket(makeGroupOrAllControlReq(reqProps), 3);
        } else {
            sendPacket(makeSingleControlReq(reqProps));
        }
        return true;
    }

    private boolean onLightOnOffTaskForSlave(PropertyMap reqProps, PropertyMap outProps) {
        final PropertyValue reqOnOff = reqProps.get(HomeDevice.PROP_ONOFF);
        final KSAddress.DeviceSubId subId = getDeviceSubId();
        if (subId.isFullOfGroup() || subId.isAll()) {
            updateChildrenPropertyRecursively(reqOnOff);
        }
        outProps.put(reqOnOff);
        return true;
    }

    protected void updateChildrenPropertyRecursively(PropertyValue propValue) {
        for (KSLight child: getChildren(KSLight.class)) {
            child.mRxPropertyMap.put(propValue);
            child.commitPropertyChanges(child.mRxPropertyMap);
            child.updateChildrenPropertyRecursively(propValue);
        }
    }

    private boolean onBatchLightOffTask(PropertyMap reqProps, PropertyMap outProps) {
        final boolean batchOff = reqProps.get(Light.PROP_BATCH_LIGHT_OFF, Boolean.class);
        KSPacket packet = createPacket(CMD_BATCH_LIGHT_OFF_REQ, (byte)(batchOff ? 0x00 : 0x01));
        sendPacket(packet, 3);
        return true;
    }

    private KSPacket makeGroupOrAllControlReq(PropertyMap props) {
        final boolean isOn = (Boolean) props.get(HomeDevice.PROP_ONOFF).getValue();
        return createPacket(CMD_GROUP_CONTROL_REQ, (byte)(isOn ? 0x01 : 0x00));
    }

    private KSPacket makeSingleControlReq(PropertyMap reqProps) {
        final boolean reqOnOff = (Boolean) reqProps.get(HomeDevice.PROP_ONOFF).getValue();
        final int curDimLevel = (Integer) getProperty(Light.PROP_CUR_DIM_LEVEL).getValue();
        final int reqDimLevel = (Integer) reqProps.get(Light.PROP_CUR_DIM_LEVEL).getValue();
        final int dimLevel = (curDimLevel != reqDimLevel) ? reqDimLevel : 0;
        return createPacket(CMD_SINGLE_CONTROL_REQ, makeLightControlData(reqOnOff, dimLevel));
    }

    private byte[] makeLightControlData(boolean isOn, int dimLevel) {
        byte[] data = new byte[1];
        data[0] = (byte)(((isOn) ? 1 : 0) | ((dimLevel & 0x0F) << 4));
        return data;
    }

    private void parseLightControlData(byte[] data, PropertyMap outProps) {
        final boolean isOn = ((data[0] & 0x01) == 1);
        final int dimLevel = ((data[0] >> 4) & 0x0F);
        outProps.put(HomeDevice.PROP_ONOFF, isOn);
        if (isOn) {
            outProps.put(Light.PROP_CUR_DIM_LEVEL, dimLevel);
        }
    }
}
