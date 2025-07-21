/*
 * Copyright (C) 2025 Korea Association of AI Smart Home.
 * Copyright (C) 2025 KyungDong Navien Co, Ltd.
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

package kr.or.kashi.hde.ksx4506_ex;

import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.base.ByteArrayBuffer;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.device.AirConditioner;
import kr.or.kashi.hde.ksx4506.KSAirConditioner;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.util.Map;

/**
 * Extended implementation of [KS X 4506] the air-conditioner.
 */
public class KSAirConditioner2 extends KSAirConditioner {
    private String TAG = "KSAirConditioner2";
    private static final boolean DBG = true;

    public KSAirConditioner2(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps);
    }

    @Override
    protected void encodeCharacteristicRsp(PropertyMap props, ByteArrayBuffer data) {
        super.encodeCharacteristicRsp(props, data); // call super
                                // DATA 0 ~ DATA 7
        int data8 = 0;
        final int supportedModes = props.get(AirConditioner.PROP_SUPPORTED_MODES, Integer.class);
        if ((supportedModes & AirConditioner.OpMode.AUTO) != 0)     data8 |= (1 << 0);
        if ((supportedModes & AirConditioner.OpMode.DEHUMID) != 0)  data8 |= (1 << 1);
        if ((supportedModes & AirConditioner.OpMode.BLOWING) != 0)  data8 |= (1 << 2);
        data.append(data8);     // DATA 8
    }

    @Override
    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) {
        @ParseResult int res = super.parseCharacteristicRsp(packet, outProps);
        if (res <= PARSE_ERROR_UNKNOWN) {
            return res;
        }

        if (packet.data.length > 8) {
            final byte data8 = packet.data[8];
            final boolean supportsAuto      = ((data8 & (1 << 0)) != 0);
            final boolean supportsDehumid   = ((data8 & (1 << 1)) != 0);
            final boolean supportsBlowing   = ((data8 & (1 << 2)) != 0);

            int newSupports = outProps.get(AirConditioner.PROP_SUPPORTED_MODES, Integer.class);

            // Overwrite the supported modes based on extended prototocol.
            if (supportsAuto) newSupports |= AirConditioner.OpMode.AUTO;
            else newSupports &= ~AirConditioner.OpMode.AUTO;
            if (supportsDehumid) newSupports |= AirConditioner.OpMode.DEHUMID;
            else newSupports &= ~AirConditioner.OpMode.DEHUMID;
            if (supportsBlowing) newSupports |= AirConditioner.OpMode.BLOWING;
            else newSupports &= ~AirConditioner.OpMode.BLOWING;

            outProps.put(AirConditioner.PROP_SUPPORTED_MODES, newSupports);
        }

        return res;
    }
}
