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

import android.util.Log;

import kr.or.kashi.hde.base.ByteArrayBuffer;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.base.PropertyTask;
import kr.or.kashi.hde.device.Curtain;

import java.util.Map;

/**
 * [KS X 4506] The curtain implementation for Curtain
 */
public class KSCurtain extends KSDeviceContextBase {
    private static final String TAG = "KSCurtain";
    private static final boolean DBG = true;

    private int mMinOpenLevel = 0;
    private int mMaxOpenLevel = 0;
    private int mCurOpenLevel = 0;
    private int mMinOpenAngle = 0;
    private int mMaxOpenAngle = 0;
    private int mCurOpenAngle = 0;

    public KSCurtain(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps, Curtain.class);

        if (isMaster()) {
            // Register the tasks to be performed when specific property changes.
            final PropertyTask propTask = this::onCurtainControlTask;
            setPropertyTask(HomeDevice.PROP_ONOFF, propTask);
            setPropertyTask(Curtain.PROP_OPERATION, propTask);
        } else {
            final PropertyTask propTask = this::onCurtainStateUpdateTaskForSlave;
            setPropertyTask(HomeDevice.PROP_ONOFF, propTask);
            setPropertyTask(Curtain.PROP_STATE, propTask);

            // Initialize some properties as specific values in slave mode.
            int supportedFunctions = 0;
            supportedFunctions |= Curtain.Support.STATE;
            supportedFunctions |= Curtain.Support.OPEN_LEVEL;
            supportedFunctions |= Curtain.Support.OPEN_ANGLE;
            mRxPropertyMap.put(Curtain.PROP_SUPPORTS, supportedFunctions);
            mRxPropertyMap.commit();
        }
    }

    protected int getCapabilities() {
        return CAP_STATUS_SINGLE | CAP_CHARAC_SINGLE;
    }

    public @ParseResult int parsePayload(KSPacket packet, PropertyMap outProps) {
        switch (packet.commandType) {
            case CMD_GROUP_CONTROL_REQ:
                return parseGroupControlReq(packet, outProps);
        }
        return super.parsePayload(packet, outProps);
    }

    @Override
    protected @ParseResult int parseStatusReq(KSPacket packet, PropertyMap outProps) {
        // No data to parse from request packet.

        // Send response packet.
        final PropertyMap props = getReadPropertyMap();
        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // no error
        makeBasicCurtainStateByte(props, data);
        makeExtendedCurtainStateByte(props, data);
        sendPacket(createPacket(CMD_STATUS_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 3) {
            if (DBG) Log.w(TAG, "parse-status-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-status-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        parseBasicCurtainStateByte(packet.data[1], outProps);
        parseExtendedCurtainStateByte(packet.data[2], outProps);

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseCharacteristicReq(KSPacket packet, PropertyMap outProps) {
        // No data to parse from request packet.

        final PropertyMap props = getReadPropertyMap();
        final ByteArrayBuffer data = new ByteArrayBuffer();

        data.append(0); // no error

        int data1 = 0;
        int supports = props.get(Curtain.PROP_SUPPORTS, Integer.class);
        if ((supports & Curtain.Support.OPEN_LEVEL) != 0) data1 |= (1 << 0);
        if ((supports & Curtain.Support.OPEN_ANGLE) != 0) data1 |= (1 << 1);
        if ((supports & Curtain.Support.STATE) == 0)      data1 |= (1 << 2);
        data.append(data1);

        // Send response packet.
        sendPacket(createPacket(CMD_CHARACTERISTIC_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 2) {
            if (DBG) Log.w(TAG, "parse-chr-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-chr-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        final int data1 = packet.data[1] & 0xFF;
        int supports = 0;
        if ((data1 & (1 << 0)) != 0) supports |= Curtain.Support.OPEN_LEVEL;
        if ((data1 & (1 << 1)) != 0) supports |= Curtain.Support.OPEN_ANGLE;
        if ((data1 & (1 << 2)) == 0) supports |= Curtain.Support.STATE;
        outProps.put(Curtain.PROP_SUPPORTS, supports);

        if ((supports & Curtain.Support.OPEN_LEVEL) != 0) {
            mMinOpenLevel = 0;
            mMaxOpenLevel = 10;
            mCurOpenLevel = mMinOpenLevel;
            outProps.put(Curtain.PROP_MIN_OPEN_LEVEL, mMinOpenLevel);
            outProps.put(Curtain.PROP_MAX_OPEN_LEVEL, mMaxOpenLevel);
            outProps.put(Curtain.PROP_CUR_OPEN_LEVEL, mCurOpenLevel);
        }

        if ((supports & Curtain.Support.OPEN_ANGLE) != 0) {
            mMinOpenAngle = 0;
            mMaxOpenAngle = 10;
            mCurOpenAngle = mMinOpenAngle;
            outProps.put(Curtain.PROP_MIN_OPEN_ANGLE, mMinOpenAngle);
            outProps.put(Curtain.PROP_MAX_OPEN_ANGLE, mMaxOpenAngle);
            outProps.put(Curtain.PROP_CUR_OPEN_ANGLE, mCurOpenAngle);
        }

        return PARSE_OK_PEER_DETECTED;
    }

    @Override
    protected @ParseResult int parseSingleControlReq(KSPacket packet, PropertyMap outProps) {
        // Parse request packet.
        parseCurtainControlReq(packet.data, outProps);

        // Send response packet.
        final PropertyMap props = getReadPropertyMap();
        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // no error
        makeBasicCurtainStateByte(props, data);
        makeExtendedCurtainStateByte(props, data);
        sendPacket(createPacket(CMD_SINGLE_CONTROL_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseSingleControlRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 3) {
            if (DBG) Log.w(TAG, "parse-control-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-control-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        parseBasicCurtainStateByte(packet.data[1], outProps);
        parseExtendedCurtainStateByte(packet.data[2], outProps);

        return PARSE_OK_ACTION_PERFORMED;
    }

    protected @ParseResult int parseGroupControlReq(KSPacket packet, PropertyMap outProps) {
        // Parse request packet.
        parseCurtainControlReq(packet.data, outProps);

        for (KSCurtain child: getChildren(KSCurtain.class)) {
            child.parseGroupControlReq(packet, child.mRxPropertyMap);
            child.commitPropertyChanges(child.mRxPropertyMap);
        }

        return PARSE_OK_STATE_UPDATED;
    }

    private void makeBasicCurtainStateByte(PropertyMap props, ByteArrayBuffer outData) {
        int state = props.get(Curtain.PROP_STATE, Integer.class);
        int data = 0;
        if (state == Curtain.OpState.OPENED)  data |= (1 << 0);
        if (state == Curtain.OpState.CLOSED)  data |= (1 << 1);
        if (state == Curtain.OpState.CLOSING) data |= (1 << 2);
        if (state == Curtain.OpState.OPENING) data |= (1 << 3);
        outData.append(data);
    }

    private void parseBasicCurtainStateByte(byte data, PropertyMap outProps) {
        int state = Curtain.OpState.OPENED;
        if ((data & (1 << 0)) != 0)      state = Curtain.OpState.OPENED;
        else if ((data & (1 << 1)) != 0) state = Curtain.OpState.CLOSED;
        else if ((data & (1 << 2)) != 0) state = Curtain.OpState.CLOSING;
        else if ((data & (1 << 3)) != 0) state = Curtain.OpState.OPENING;
        outProps.put(Curtain.PROP_STATE, state);

        boolean onoff = true;
        switch(state) {
            case Curtain.OpState.OPENED:  onoff = true;  break;
            case Curtain.OpState.OPENING: onoff = true;  break;
            case Curtain.OpState.CLOSED:  onoff = false; break;
            case Curtain.OpState.CLOSING: onoff = false; break;
        }
        outProps.put(HomeDevice.PROP_ONOFF, onoff);
    }

    private void makeExtendedCurtainStateByte(PropertyMap props, ByteArrayBuffer outData) {
        int openLevel = props.get(Curtain.PROP_CUR_OPEN_LEVEL, Integer.class);
        int openAngle = props.get(Curtain.PROP_CUR_OPEN_ANGLE, Integer.class);
        int data = (openLevel & 0x0F) | ((openAngle & 0x0F) << 4);
        outData.append(data);
    }

    private void parseExtendedCurtainStateByte(byte data, PropertyMap outProps) {
        final int openLevel = clamp("openLevel", (data >> 0) & 0x0F, mMinOpenLevel, mMaxOpenLevel);
        final int openAngle = clamp("openAngle", (data >> 4) & 0x0F, mMinOpenAngle, mMaxOpenAngle);
        outProps.put(Curtain.PROP_CUR_OPEN_LEVEL, openLevel);
        outProps.put(Curtain.PROP_CUR_OPEN_ANGLE, openAngle);
    }

    protected boolean onCurtainControlTask(PropertyMap reqProps, PropertyMap outProps) {
        boolean curOnOff = (Boolean) getProperty(HomeDevice.PROP_ONOFF).getValue();
        boolean newOnOff = (Boolean) reqProps.get(HomeDevice.PROP_ONOFF).getValue();
        int curOperation = (Integer) getProperty(Curtain.PROP_OPERATION).getValue();
        int newOperation = reqProps.get(Curtain.PROP_OPERATION, Integer.class);
        if (newOperation == curOperation && newOnOff != curOnOff) {
            newOperation = (newOnOff) ? Curtain.Operation.OPEN : Curtain.Operation.CLOSE;
        }

        // Send control packet
        sendPacket(makeCurtainControlReq(newOperation, reqProps));

        outProps.put(Curtain.PROP_OPERATION, newOperation);
        outProps.put(HomeDevice.PROP_ONOFF, (newOperation == Curtain.Operation.OPEN));
        return false;
    }

    protected boolean onCurtainStateUpdateTaskForSlave(PropertyMap reqProps, PropertyMap outProps) {
        boolean curOnOff = (Boolean) getProperty(HomeDevice.PROP_ONOFF).getValue();
        boolean newOnOff = (Boolean) reqProps.get(HomeDevice.PROP_ONOFF).getValue();
        int curState = (Integer) getProperty(Curtain.PROP_STATE).getValue();
        int newState = reqProps.get(Curtain.PROP_STATE, Integer.class);
        if (newState == curState && newOnOff != curOnOff) {
            newState = (newOnOff) ? Curtain.OpState.OPENED : Curtain.OpState.CLOSED;
        }
        outProps.put(Curtain.PROP_STATE, newState);
        outProps.put(HomeDevice.PROP_ONOFF, (newState == Curtain.OpState.OPENED) || (newState == Curtain.OpState.OPENING));
        return false;
    }

    private KSPacket makeCurtainControlReq(int operation, PropertyMap props) {
        final ByteArrayBuffer data = new ByteArrayBuffer();

        int data0 = 0;
        switch (operation) {
            case Curtain.Operation.STOP:  data0 = 2; break;
            case Curtain.Operation.OPEN:  data0 = 1; break;
            case Curtain.Operation.CLOSE: data0 = 0; break;
            default:
                Log.d(TAG, "make-control-req: unknown operation:" + operation);
                break;
        }
        data.append(data0);

        final int openLevel = props.get(Curtain.PROP_CUR_OPEN_LEVEL, Integer.class);
        final int openAngle = props.get(Curtain.PROP_CUR_OPEN_ANGLE, Integer.class);
        final int data1 = ((openLevel & 0x0F) | ((openAngle << 4) & 0xF0));
        data.append(data1);

        return createPacket(CMD_SINGLE_CONTROL_REQ, data.toArray());
    }

    private void parseCurtainControlReq(byte[] data, PropertyMap outProps) {
        int opData = data[0] & 0xFF;

        int operation = Curtain.Operation.STOP;
        if (opData == 0) operation = Curtain.Operation.CLOSE;
        else if (opData == 1) operation = Curtain.Operation.OPEN;
        else if (opData == 2) operation = Curtain.Operation.STOP;

        // Just changed current state by requested operation.
        // TODO: PROP_STATE should be changed by emulation layer.
        int opState = outProps.get(Curtain.PROP_STATE, Integer.class);
        if (operation == Curtain.Operation.OPEN) {
            opState = Curtain.OpState.OPENED;
        } else if (operation == Curtain.Operation.CLOSE) {
            opState = Curtain.OpState.CLOSED;
        }

        outProps.put(Curtain.PROP_OPERATION, operation);
        outProps.put(Curtain.PROP_STATE, opState);
        outProps.put(HomeDevice.PROP_ONOFF, (operation == Curtain.Operation.OPEN));

        int openLevel = data[1] & 0x0F;
        int openAngle = (data[1] >> 4) & 0x0F;

        outProps.put(Curtain.PROP_CUR_OPEN_LEVEL, openLevel);
        outProps.put(Curtain.PROP_CUR_OPEN_ANGLE, openAngle);
    }
}
