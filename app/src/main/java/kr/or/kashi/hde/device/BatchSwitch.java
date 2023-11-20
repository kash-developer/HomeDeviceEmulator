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

package kr.or.kashi.hde.device;

import kr.or.kashi.hde.base.PropertyValue;
import kr.or.kashi.hde.base.PropertyDef;
import kr.or.kashi.hde.DeviceContextBase;
import kr.or.kashi.hde.HomeDevice;

/**
 * A device class for batch-controllable switchs
 */
public class BatchSwitch extends HomeDevice {
    private static final String PROP_PREFIX = "bs.";

    /**
     * Switch types
     */
    public @interface Switch {
        long GAS_LOCKING        = 1L << 0;
        long OUTING_SETTING     = 1L << 1;
        long BATCH_LIGHT_OFF    = 1L << 2;
        long POWER_SAVING       = 1L << 3;
        long ELEVATOR_UP_CALL   = 1L << 4;
        long ELEVATOR_DOWN_CALL = 1L << 5;
        long THREEWAY_LIGHT     = 1L << 6;
        long COOKTOP_OFF        = 1L << 7;
        long HEATER_SAVING      = 1L << 8;
    }

    /**
     * Displayable informations
     */
    public @interface Display {
        long ELEVATOR_FLOOR     = 1L << 0;
        long ELEVATOR_ARRIVAL   = 1L << 1;
        long LIFE_INFORMATION   = 1L << 2;
        long PARKING_LOCATION   = 1L << 3;
    }

    /** Property of bit flags that represents each switch is supported or not */
    @PropertyDef(valueClass=Switch.class, formatHint="bits")
    public static final String PROP_SUPPORTED_SWITCHES  = PROP_PREFIX + "supported_switches";

    /** Property of bit flags that represents each displayable information is supported or not */
    @PropertyDef(valueClass=Display.class, formatHint="bits")
    public static final String PROP_SUPPORTED_DISPLAYS  = PROP_PREFIX + "supported_displays";

    /** Property of bit flags that represents each switch's on/off state */
    @PropertyDef(valueClass=Switch.class, formatHint="bits")
    public static final String PROP_SWITCH_STATES       = PROP_PREFIX + "switch_states";

    /** Property of bit flags that represents each display is on or not */
    @PropertyDef(valueClass=Display.class, formatHint="bits")
    public static final String PROP_DISPLAY_STATES      = PROP_PREFIX + "display_states";

    /**
     * Construct new instance. Don't call this directly.
     */
    public BatchSwitch(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /** @hide */
    @Override
    public @Type int getType() {
        return HomeDevice.Type.BATCH_SWITCH;
    }

    /**
     * Whether if a switch is equipped in the device.
     * @return {@code true} if device has the switch.
     */
    public boolean hasSwitch(@Switch long switch_) {
        return (getPropertyL(PROP_SUPPORTED_SWITCHES) & switch_) != 0L;
    }

    /**
     * Get current on or off state of switch
     * @return {@code true} if switch is on.
     */
    public boolean getSwitchState(@Switch long switch_) {
        return (getSwitchStates() & switch_) != 0L;
    }

    /**
     * Get all states of switches.
     * @return {@code long} value interpereted as {@link Switch} bits.
     */
    public @Switch long getSwitchStates() {
        return getPropertyL(PROP_SWITCH_STATES);
    }

    /**
     * Set bits of on/off states for multiple switches.
     * @param states {@code long} value interpereted as {@link Switch} bits.
     * @param on {@code true} if switches of {@param states} should be on.
     */
    public void setSwitchStates(@Switch long states, boolean on) {
        final long curStates = (Long) getStagedProperty(PROP_SWITCH_STATES, Long.class);
        final long newStates = (on) ? (curStates | states) : (curStates & ~states);
        setProperty(PROP_SWITCH_STATES, Long.class, newStates);
    }

    /**
     * Whether if a life information is displayabble in the device.
     * @return {@code true} if device can display the information.
     */
    public boolean hasDisplay(@Display long display) {
        return (getPropertyL(PROP_SUPPORTED_DISPLAYS) & display) != 0L;
    }

    /**
     * Gets if the information is displaying on device
     * @param display The type of diaplayable information.
     * @return {@code true} if the display is on.
     */
    public boolean getDisplayState(@Display long display) {
        return (getDisplayStates() & display) != 0L;
    }

    /**
     * Get all on/off states of displays.
     * @return {@code long} value interpereted as {@link Display} bits.
     */
    public @Display long getDisplayStates() {
        return getPropertyL(PROP_DISPLAY_STATES);
    }

    /**
     * Set bits of on/off states for multiple displays.
     * @param states {@code long} value interpereted as {@link Display} bits.
     * @param on {@code true} if displays of {@param states} should be on.
     */
    public void setDisplayStates(@Display long states, boolean on) {
        final long curStates = (Long) getStagedProperty(PROP_DISPLAY_STATES, Long.class);
        final long newStates = (on) ? (curStates | states) : (curStates & ~states);
        setProperty(PROP_DISPLAY_STATES, Long.class, newStates);
    }

    /**
     * Send vendor-specific displayable data to the device.
     * @param display The display id as {@link Display}.
     * @param data Vendor-specific displayable data.
     */
    public void setDisplayData(@Display long display, byte[] data) {
        flushProperties(); // Ensure there's no staged changes and ...
        // Set extra data for only single display.
        setPropertyNow(new PropertyValue<Long>(PROP_DISPLAY_STATES, display, data));
    }
}
