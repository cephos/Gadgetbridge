/*  Copyright (C) 2016-2021 Andreas Shimokawa, Carsten Pfeiffer, Daniele
    Gobbetti, Dmitry Markin, José Rebelo

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.huami.miband2;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.InstallHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;

public class MiBand2Coordinator extends HuamiCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(MiBand2Coordinator.class);

    @Override
    public DeviceType getDeviceType() {
        return DeviceType.MIBAND2;
    }

    @NonNull
    @Override
    public DeviceType getSupportedType(GBDeviceCandidate candidate) {
        try {
            BluetoothDevice device = candidate.getDevice();
            String name = device.getName();
            if (name != null && name.equalsIgnoreCase(HuamiConst.MI_BAND2_NAME)) {
                return DeviceType.MIBAND2;
            }
        } catch (Exception ex) {
            LOG.error("unable to check device support", ex);
        }
        return DeviceType.UNKNOWN;

    }

    @Override
    public InstallHandler findInstallHandler(Uri uri, Context context) {
        MiBand2FWInstallHandler handler = new MiBand2FWInstallHandler(uri, context);
        return handler.isValid() ? handler : null;
    }

    @Override
    public boolean supportsHeartRateMeasurement(GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsWeather() {
        return false;
    }

    @Override
    public int getReminderSlotCount() {
        return 0;
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(GBDevice device) {
        return new int[]{
                R.xml.devicesettings_miband2,
                R.xml.devicesettings_wearlocation,
                R.xml.devicesettings_heartrate_sleep,
                R.xml.devicesettings_goal_notification,
                R.xml.devicesettings_timeformat,
                R.xml.devicesettings_donotdisturb_withauto,
                R.xml.devicesettings_liftwrist_display,
                R.xml.devicesettings_inactivity_dnd,
                R.xml.devicesettings_rotatewrist_cycleinfo,
                R.xml.devicesettings_buttonactions,
                R.xml.devicesettings_reserve_alarms_calendar,
                R.xml.devicesettings_bt_connected_advertisement,
                R.xml.devicesettings_overwrite_settings_on_connection,
                R.xml.devicesettings_transliteration
        };
    }
}
