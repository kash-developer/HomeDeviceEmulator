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

import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.Curtain;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.util.List;
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

        // Register the tasks to be performed when specific property changes.
        setPropertyTask(HomeDevice.PROP_ONOFF, this::onCurtainControlTask);
        setPropertyTask(Curtain.PROP_OPERATION, this::onCurtainControlTask);
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
        if ((data1 & (1 << 0)) != 0) supports |= (1 << Curtain.Support.OPEN_LEVEL);
        if ((data1 & (1 << 1)) != 0) supports |= (1 << Curtain.Support.OPEN_ANGLE);
        if ((data1 & (1 << 2)) != 0) supports |= (1 << Curtain.Support.STATE);
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

    private void parseExtendedCurtainStateByte(byte data, PropertyMap outProps) {
        final int openLevel = clamp("openLevel", (data >> 0) & 0x0F, mMinOpenLevel, mMaxOpenLevel);
        final int openAngle = clamp("openAngle", (data >> 4) & 0x0F, mMinOpenAngle, mMaxOpenAngle);
        outProps.put(Curtain.PROP_CUR_OPEN_LEVEL, openLevel);
        outProps.put(Curtain.PROP_CUR_OPEN_ANGLE, openAngle);
    }

    protected boolean onCurtainControlTask(PropertyMap reqProps, PropertyMap outProps) {
        final int curOperation = (Integer) getProperty(Curtain.PROP_OPERATION).getValue();
        final int reqOperation = reqProps.get(Curtain.PROP_OPERATION, Integer.class);
        if (reqOperation != curOperation) {
            sendPacket(makeCurtainControlReq(reqOperation, reqProps));
            return true;
        }

        final boolean curOnOff = (Boolean) getProperty(HomeDevice.PROP_ONOFF).getValue();
        final boolean reqOnOff = (Boolean) reqProps.get(HomeDevice.PROP_ONOFF).getValue();
        if (reqOnOff != curOnOff) {
            int operation = Curtain.Operation.CLOSE;
            if (reqOnOff == true) {
                operation = Curtain.Operation.OPEN;
            }
            sendPacket(makeCurtainControlReq(operation, reqProps));
            outProps.put(Curtain.PROP_OPERATION, operation);
            return true;
        }

        return false;
    }

    private KSPacket makeCurtainControlReq(int opertation, PropertyMap props) {
        byte data[] = new byte[2];

        data[0] = 0;
        switch (opertation) {
            case Curtain.Operation.STOP:  data[0] = 0; break;
            case Curtain.Operation.OPEN:  data[0] = 1; break;
            case Curtain.Operation.CLOSE: data[0] = 2; break;
            default:
                Log.d(TAG, "make-control-req: unknown operation:" + opertation);
                break;
        }

        final int openLevel = props.get(Curtain.PROP_CUR_OPEN_LEVEL, Integer.class);
        final int openAngle = props.get(Curtain.PROP_CUR_OPEN_ANGLE, Integer.class);
        data[1] = (byte) ((openLevel & 0x0F) | ((openAngle << 4) & 0xF0));

        return createPacket(CMD_SINGLE_CONTROL_REQ, data);
    }
}
