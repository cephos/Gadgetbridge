/*  Copyright (C) 2015-2021 Andreas Böhler, Andreas Shimokawa, Avamander,
    Carsten Pfeiffer, Daniel Dakhno, Daniele Gobbetti, Daniel Hauck, Dikay900,
    Frank Slezak, ivanovlev, João Paulo Barraca, José Rebelo, Julien Pivotto,
    Kasha, keeshii, mamucho, Martin, Matthieu Baerts, Nephiel, Sebastian Kranz,
    Sergey Trofimov, Steffen Liebergeld, Taavi Eomäe, Uwe Hermann

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
package nodomain.freeyourgadget.gadgetbridge.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.GBException;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.externalevents.AlarmClockReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.AlarmReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.BluetoothConnectReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.BluetoothPairingRequestReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.CMWeatherReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.CalendarReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.DeviceSettingsReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.GenericWeatherReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.LineageOsWeatherReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.MusicPlaybackReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.OmniJawsObserver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.PebbleReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.PhoneCallReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.SMSReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.TimeChangeReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.TinyWeatherForecastGermanyReceiver;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceService;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationType;
import nodomain.freeyourgadget.gadgetbridge.model.Reminder;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.model.WorldClock;
import nodomain.freeyourgadget.gadgetbridge.service.receivers.AutoConnectIntervalReceiver;
import nodomain.freeyourgadget.gadgetbridge.service.receivers.GBAutoFetchReceiver;
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper;
import nodomain.freeyourgadget.gadgetbridge.util.EmojiConverter;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;
import nodomain.freeyourgadget.gadgetbridge.util.language.LanguageUtils;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;
import nodomain.freeyourgadget.gadgetbridge.util.language.Transliterator;

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_TRANSLITERATION_LANGUAGES;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_ADD_CALENDAREVENT;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_APP_CONFIGURE;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_APP_REORDER;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_CALLSTATE;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_CONNECT;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_DELETEAPP;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_DELETE_CALENDAREVENT;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_DELETE_NOTIFICATION;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_DISCONNECT;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_ENABLE_HEARTRATE_SLEEP_SUPPORT;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_ENABLE_REALTIME_HEARTRATE_MEASUREMENT;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_ENABLE_REALTIME_STEPS;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_FETCH_RECORDED_DATA;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_FIND_DEVICE;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_HEARTRATE_TEST;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_INSTALL;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_NOTIFICATION;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_PHONE_FOUND;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_POWER_OFF;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_READ_CONFIGURATION;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_REQUEST_APPINFO;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_REQUEST_DEVICEINFO;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_REQUEST_SCREENSHOT;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_RESET;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SEND_CONFIGURATION;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SEND_WEATHER;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SETCANNEDMESSAGES;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SETMUSICINFO;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SETMUSICSTATE;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SETTIME;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SET_ALARMS;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SET_CONSTANT_VIBRATION;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SET_FM_FREQUENCY;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SET_HEARTRATE_MEASUREMENT_INTERVAL;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SET_GPS_LOCATION;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SET_LED_COLOR;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SET_PHONE_VOLUME;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SET_REMINDERS;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SET_WORLD_CLOCKS;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_START;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_STARTAPP;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_TEST_NEW_FUNCTION;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_ALARMS;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_APP_CONFIG;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_APP_CONFIG_ID;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_APP_START;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_APP_UUID;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_BOOLEAN_ENABLE;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALENDAREVENT_DESCRIPTION;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALENDAREVENT_DURATION;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALENDAREVENT_ID;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALENDAREVENT_LOCATION;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALENDAREVENT_TIMESTAMP;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALENDAREVENT_ALLDAY;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALENDAREVENT_TITLE;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALENDAREVENT_TYPE;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALENDAREVENT_CALNAME;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALENDAREVENT_COLOR;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALL_COMMAND;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALL_DISPLAYNAME;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALL_DNDSUPPRESSED;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CALL_PHONENUMBER;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CANNEDMESSAGES;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CANNEDMESSAGES_TYPE;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CONFIG;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_CONNECT_FIRST_TIME;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_FIND_START;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_FM_FREQUENCY;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_GPS_LOCATION;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_INTERVAL_SECONDS;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_LED_COLOR;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_MUSIC_ALBUM;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_MUSIC_ARTIST;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_MUSIC_DURATION;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_MUSIC_POSITION;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_MUSIC_RATE;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_MUSIC_REPEAT;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_MUSIC_SHUFFLE;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_MUSIC_STATE;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_MUSIC_TRACK;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_MUSIC_TRACKCOUNT;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_MUSIC_TRACKNR;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_ACTIONS;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_BODY;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_DNDSUPPRESSED;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_FLAGS;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_ICONID;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_ID;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_PEBBLE_COLOR;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_PHONENUMBER;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_SENDER;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_SOURCEAPPID;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_SOURCENAME;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_SUBJECT;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_TITLE;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_NOTIFICATION_TYPE;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_PHONE_VOLUME;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_RECORDED_DATA_TYPES;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_REMINDERS;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_RESET_FLAGS;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_URI;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_VIBRATION_INTENSITY;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_WEATHER;
import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_WORLD_CLOCKS;

public class DeviceCommunicationService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static class DeviceStruct{
        private GBDevice device;
        private DeviceCoordinator coordinator;
        private DeviceSupport deviceSupport;

        public GBDevice getDevice() {
            return device;
        }

        public void setDevice(GBDevice device) {
            this.device = device;
        }

        public DeviceCoordinator getCoordinator() {
            return coordinator;
        }

        public void setCoordinator(DeviceCoordinator coordinator) {
            this.coordinator = coordinator;
        }

        public DeviceSupport getDeviceSupport() {
            return deviceSupport;
        }

        public void setDeviceSupport(DeviceSupport deviceSupport) {
            this.deviceSupport = deviceSupport;
        }
    }

    private class FeatureSet{
        private boolean supportsWeather = false;
        private boolean supportsActivityDataFetching = false;
        private boolean supportsCalendarEvents = false;
        private boolean supportsMusicInfo = false;

        public boolean supportsWeather() {
            return supportsWeather;
        }

        public void setSupportsWeather(boolean supportsWeather) {
            this.supportsWeather = supportsWeather;
        }

        public boolean supportsActivityDataFetching() {
            return supportsActivityDataFetching;
        }

        public void setSupportsActivityDataFetching(boolean supportsActivityDataFetching) {
            this.supportsActivityDataFetching = supportsActivityDataFetching;
        }

        public boolean supportsCalendarEvents() {
            return supportsCalendarEvents;
        }

        public void setSupportsCalendarEvents(boolean supportsCalendarEvents) {
            this.supportsCalendarEvents = supportsCalendarEvents;
        }

        public boolean supportsMusicInfo() {
            return supportsMusicInfo;
        }

        public void setSupportsMusicInfo(boolean supportsMusicInfo) {
            this.supportsMusicInfo = supportsMusicInfo;
        }

        public void logicalOr(DeviceCoordinator operand){
            if(operand.supportsCalendarEvents()){
                setSupportsCalendarEvents(true);
            }
            if(operand.supportsWeather()){
                setSupportsWeather(true);
            }
            if(operand.supportsActivityDataFetching()){
                setSupportsActivityDataFetching(true);
            }
            if(operand.supportsMusicInfo()){
                setSupportsMusicInfo(true);
            }
        }
    }

    public static class DeviceNotFoundException extends GBException{
        private final String address;

        public DeviceNotFoundException(GBDevice device) {
            this.address = device.getAddress();
        }

        public DeviceNotFoundException(String address) {
            this.address = address;
        }

        @Nullable
        @Override
        public String getMessage() {
            return String.format("device %s not found cached", address);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(DeviceCommunicationService.class);
    @SuppressLint("StaticFieldLeak") // only used for test cases
    private static DeviceSupportFactory DEVICE_SUPPORT_FACTORY = null;

    private boolean mStarted = false;

    private DeviceSupportFactory mFactory;
    private final ArrayList<DeviceStruct> deviceStructs = new ArrayList<>(1);

    private PhoneCallReceiver mPhoneCallReceiver = null;
    private SMSReceiver mSMSReceiver = null;
    private PebbleReceiver mPebbleReceiver = null;
    private MusicPlaybackReceiver mMusicPlaybackReceiver = null;
    private TimeChangeReceiver mTimeChangeReceiver = null;
    private BluetoothConnectReceiver mBlueToothConnectReceiver = null;
    private BluetoothPairingRequestReceiver mBlueToothPairingRequestReceiver = null;
    private AlarmClockReceiver mAlarmClockReceiver = null;
    private GBAutoFetchReceiver mGBAutoFetchReceiver = null;
    private AutoConnectIntervalReceiver mAutoConnectInvervalReceiver= null;

    private AlarmReceiver mAlarmReceiver = null;
    private List<CalendarReceiver> mCalendarReceiver = new ArrayList<>();
    private CMWeatherReceiver mCMWeatherReceiver = null;
    private LineageOsWeatherReceiver mLineageOsWeatherReceiver = null;
    private TinyWeatherForecastGermanyReceiver mTinyWeatherForecastGermanyReceiver = null;
    private GenericWeatherReceiver mGenericWeatherReceiver = null;
    private OmniJawsObserver mOmniJawsObserver = null;
    private final DeviceSettingsReceiver deviceSettingsReceiver = new DeviceSettingsReceiver();

    private final String[] mMusicActions = {
            "com.android.music.metachanged",
            "com.android.music.playstatechanged",
            "com.android.music.queuechanged",
            "com.android.music.playbackcomplete",
            "net.sourceforge.subsonic.androidapp.EVENT_META_CHANGED",
            "com.maxmpz.audioplayer.TPOS_SYNC",
            "com.maxmpz.audioplayer.STATUS_CHANGED",
            "com.maxmpz.audioplayer.PLAYING_MODE_CHANGED",
            "com.spotify.music.metadatachanged",
            "com.spotify.music.playbackstatechanged"
    };

    private final String COMMAND_BLUETOOTH_CONNECT = "nodomain.freeyourgadget.gadgetbridge.BLUETOOTH_CONNECT";
    private final String ACTION_DEVICE_CONNECTED = "nodomain.freeyourgadget.gadgetbridge.BLUETOOTH_CONNECTED";
    private boolean allowBluetoothIntentApi = false;

    private void sendDeviceConnectedBroadcast(String address){
        if(!allowBluetoothIntentApi){
            GB.log("not sending API event due to settings", GB.INFO, null);
            return;
        }
        Intent intent = new Intent(ACTION_DEVICE_CONNECTED);
        intent.putExtra("EXTRA_DEVICE_ADDRESS", address);

        sendBroadcast(intent);
    }

    BroadcastReceiver bluetoothCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()){
                case COMMAND_BLUETOOTH_CONNECT:
                    if(!allowBluetoothIntentApi){
                        GB.log("Connection API not allowed in settings", GB.ERROR, null);
                        return;
                    }
                    Bundle extras = intent.getExtras();
                    if(extras == null){
                        GB.log("no extras provided in Intent", GB.ERROR, null);
                        return;
                    }
                    String address = extras.getString("EXTRA_DEVICE_ADDRESS", "");
                    if(address.isEmpty()){
                        GB.log("no bluetooth address provided in Intent", GB.ERROR, null);
                        return;
                    }
                    if(isDeviceConnected(address)){
                        GB.log(String.format("device %s already connected", address), GB.INFO, null);
                        sendDeviceConnectedBroadcast(address);
                        return;
                    }

                    List<GBDevice> devices = GBApplication.app().getDeviceManager().getDevices();
                    GBDevice targetDevice = GBApplication
                            .app()
                            .getDeviceManager()
                            .getDeviceByAddress(address);

                    if(targetDevice == null){
                        GB.log(String.format("device %s not registered", address), GB.ERROR, null);
                        return;
                    }

                    GB.log(String.format("connecting to %s", address), GB.INFO, null);

                    GBApplication
                            .deviceService(targetDevice)
                            .connect();

                    break;
            }
        }
    };

    /**
     * For testing!
     *
     * @param factory
     */
    @SuppressWarnings("JavaDoc")
    public static void setDeviceSupportFactory(DeviceSupportFactory factory) {
        DEVICE_SUPPORT_FACTORY = factory;
    }

    public DeviceCommunicationService() {

    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(GBDevice.ACTION_DEVICE_CHANGED.equals(action)){
                GBDevice device = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE);

                // create a new instance of the changed devices coordinator, in case it's capabilities changed
                DeviceStruct cachedStruct = getDeviceStructOrNull(device);
                if(cachedStruct != null) {
                    cachedStruct.setDevice(device);
                    DeviceCoordinator newCoordinator = DeviceHelper.getInstance().getCoordinator(device);
                    cachedStruct.setCoordinator(newCoordinator);
                }
                updateReceiversState();

                if(device.isInitialized()){
                    sendDeviceConnectedBroadcast(device.getAddress());
                }
            }
        }
    };

    private void updateReceiversState(){
        boolean enableReceivers = false;
        boolean anyDeviceInitialized = false;
        List <GBDevice> devicesWithCalendar = new ArrayList<>();

        FeatureSet features = new FeatureSet();

        for(DeviceStruct struct: deviceStructs){
            DeviceSupport deviceSupport = struct.getDeviceSupport();
            if((deviceSupport != null && deviceSupport.useAutoConnect()) || isDeviceInitialized(struct.getDevice())){
                enableReceivers = true;
            }
            if(isDeviceInitialized(struct.getDevice())){
                anyDeviceInitialized = true;
            }

            DeviceCoordinator coordinator = struct.getCoordinator();
            if(coordinator != null){
                features.logicalOr(coordinator);
                if (coordinator.supportsCalendarEvents()){
                    devicesWithCalendar.add(struct.getDevice());
                }
            }
        }
        setReceiversEnableState(enableReceivers, anyDeviceInitialized, features, devicesWithCalendar);
    }

    @Override
    public void onCreate() {
        LOG.debug("DeviceCommunicationService is being created");
        super.onCreate();
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, new IntentFilter(GBDevice.ACTION_DEVICE_CHANGED));
        mFactory = getDeviceSupportFactory();

        mBlueToothConnectReceiver = new BluetoothConnectReceiver(this);
        registerReceiver(mBlueToothConnectReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));

        mAutoConnectInvervalReceiver= new AutoConnectIntervalReceiver(this);
        registerReceiver(mAutoConnectInvervalReceiver, new IntentFilter("GB_RECONNECT"));

        if (hasPrefs()) {
            getPrefs().getPreferences().registerOnSharedPreferenceChangeListener(this);
            allowBluetoothIntentApi = getPrefs().getBoolean(GBPrefs.PREF_ALLOW_INTENT_API, false);
        }

        IntentFilter bluetoothCommandFilter = new IntentFilter();
        bluetoothCommandFilter.addAction(COMMAND_BLUETOOTH_CONNECT);
        registerReceiver(bluetoothCommandReceiver, bluetoothCommandFilter);

        final IntentFilter deviceSettingsIntentFilter = new IntentFilter();
        deviceSettingsIntentFilter.addAction(DeviceSettingsReceiver.COMMAND);
        registerReceiver(deviceSettingsReceiver, deviceSettingsIntentFilter);
    }

    private DeviceSupportFactory getDeviceSupportFactory() {
        if (DEVICE_SUPPORT_FACTORY != null) {
            return DEVICE_SUPPORT_FACTORY;
        }
        return new DeviceSupportFactory(this);
    }

    @Override
    public synchronized int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            LOG.info("no intent");
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        boolean firstTime = intent.getBooleanExtra(EXTRA_CONNECT_FIRST_TIME, false);

        if (action == null) {
            LOG.info("no action");
            return START_NOT_STICKY;
        }

        LOG.debug("Service startcommand: " + action);

        if (!action.equals(ACTION_START) && !action.equals(ACTION_CONNECT)) {
            if (!mStarted) {
                // using the service before issuing ACTION_START
                LOG.info("Must start service with " + ACTION_START + " or " + ACTION_CONNECT + " before using it: " + action);
                return START_NOT_STICKY;
            }

            // TODO
            /*if (mDeviceSupport == null || (!isInitialized() && !action.equals(ACTION_DISCONNECT) && (!mDeviceSupport.useAutoConnect() || isConnected()))) {
                // trying to send notification without valid Bluetooth connection
                if (mGBDevice != null) {
                    // at least send back the current device state
                    mGBDevice.sendDeviceUpdateIntent(this);
                }
                return START_STICKY;
            }*/
        }

        // when we get past this, we should have valid mDeviceSupport and mGBDevice instances

        Prefs prefs = getPrefs();
        switch (action) {
            case ACTION_START:
                start();
                break;
            case ACTION_CONNECT:
                start(); // ensure started
                GBDevice gbDevice = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE);
                String btDeviceAddress = null;
                if (gbDevice == null) {
                    if (prefs != null) { // may be null in test cases
                        btDeviceAddress = prefs.getString("last_device_address", null);
                        if (btDeviceAddress != null) {
                            gbDevice = DeviceHelper.getInstance().findAvailableDevice(btDeviceAddress, this);
                        }
                    }
                } else {
                    btDeviceAddress = gbDevice.getAddress();
                }

                if(gbDevice == null){
                    return START_NOT_STICKY;
                }

                boolean autoReconnect = GBPrefs.AUTO_RECONNECT_DEFAULT;
                if (prefs != null && prefs.getPreferences() != null) {
                    prefs.getPreferences().edit().putString("last_device_address", btDeviceAddress).apply();
                    autoReconnect = getGBPrefs().getAutoReconnect(gbDevice);
                }

                DeviceStruct registeredStruct = getDeviceStructOrNull(gbDevice);
                if(registeredStruct != null){
                    boolean deviceAlreadyConnected = isDeviceConnecting(registeredStruct.getDevice()) || isDeviceConnected(registeredStruct.getDevice());
                    if(deviceAlreadyConnected){
                        break;
                    }
                    try {
                        removeDeviceSupport(gbDevice);
                    } catch (DeviceNotFoundException e) {
                        e.printStackTrace();
                    }
                }else{
                    registeredStruct = new DeviceStruct();
                    registeredStruct.setDevice(gbDevice);
                    registeredStruct.setCoordinator(DeviceHelper.getInstance().getCoordinator(gbDevice));
                    deviceStructs.add(registeredStruct);
                }

                try {
                    DeviceSupport deviceSupport = mFactory.createDeviceSupport(gbDevice);
                    if (deviceSupport != null) {
                        setDeviceSupport(gbDevice, deviceSupport);
                        if (firstTime) {
                            deviceSupport.connectFirstTime();
                        } else {
                            deviceSupport.setAutoReconnect(autoReconnect);
                            deviceSupport.connect();
                        }
                    } else {
                        GB.toast(this, getString(R.string.cannot_connect, "Can't create device support"), Toast.LENGTH_SHORT, GB.ERROR);
                    }
                } catch (Exception e) {
                    GB.toast(this, getString(R.string.cannot_connect, e.getMessage()), Toast.LENGTH_SHORT, GB.ERROR, e);
                }

                for(DeviceStruct struct2 : deviceStructs){
                    struct2.getDevice().sendDeviceUpdateIntent(this);
                }
                break;
            default:
                GBDevice targetedDevice = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE);
                ArrayList<GBDevice> targetedDevices = new ArrayList<>();
                if(targetedDevice != null){
                    targetedDevices.add(targetedDevice);
                }else{
                    for(GBDevice device : getGBDevices()){
                        if(isDeviceInitialized(device)){
                            targetedDevices.add(device);
                        }
                    }
                }
                for (GBDevice device1 : targetedDevices) {
                    try {
                        handleAction(intent, action, device1);
                    } catch (DeviceNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                break;
        }
        return START_STICKY;
    }

    /**
     * @param text original text
     * @return 'text' or a new String without non supported chars like emoticons, etc.
     */
    private String sanitizeNotifText(String text, GBDevice device) throws DeviceNotFoundException {
        if (text == null || text.length() == 0)
            return text;

        text = getDeviceSupport(device).customStringFilter(text);

        if (!getDeviceCoordinator(device).supportsUnicodeEmojis()) {
            return EmojiConverter.convertUnicodeEmojiToAscii(text, getApplicationContext());
        }

        return text;
    }

    private DeviceCoordinator getDeviceCoordinator(GBDevice device) throws DeviceNotFoundException {
        if(device == null){
            throw new DeviceNotFoundException("null");
        }
        for(DeviceStruct struct : deviceStructs){
            if(struct.getDevice().equals(device)){
                return struct.getCoordinator();
            }
        }
        throw new DeviceNotFoundException(device);
    }

    private void handleAction(Intent intent, String action, GBDevice device) throws DeviceNotFoundException {
        DeviceSupport deviceSupport = getDeviceSupport(device);

        Prefs devicePrefs = new Prefs(GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()));

        final Transliterator transliterator = LanguageUtils.getTransliterator(device);

        if (transliterator != null) {
            for (String extra : GBDeviceService.transliterationExtras) {
                if (intent.hasExtra(extra)) {
                    intent.putExtra(extra, transliterator.transliterate(intent.getStringExtra(extra)));
                }
            }
        }

        switch (action) {
            case ACTION_REQUEST_DEVICEINFO:
                device.sendDeviceUpdateIntent(this);
                break;
            case ACTION_NOTIFICATION: {
                int desiredId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
                NotificationSpec notificationSpec = new NotificationSpec(desiredId);
                notificationSpec.phoneNumber = intent.getStringExtra(EXTRA_NOTIFICATION_PHONENUMBER);
                notificationSpec.sender = sanitizeNotifText(intent.getStringExtra(EXTRA_NOTIFICATION_SENDER), device);
                notificationSpec.subject = sanitizeNotifText(intent.getStringExtra(EXTRA_NOTIFICATION_SUBJECT), device);
                notificationSpec.title = sanitizeNotifText(intent.getStringExtra(EXTRA_NOTIFICATION_TITLE), device);
                notificationSpec.body = sanitizeNotifText(intent.getStringExtra(EXTRA_NOTIFICATION_BODY), device);
                notificationSpec.sourceName = intent.getStringExtra(EXTRA_NOTIFICATION_SOURCENAME);
                notificationSpec.type = (NotificationType) intent.getSerializableExtra(EXTRA_NOTIFICATION_TYPE);
                notificationSpec.attachedActions = (ArrayList<NotificationSpec.Action>) intent.getSerializableExtra(EXTRA_NOTIFICATION_ACTIONS);
                notificationSpec.pebbleColor = (byte) intent.getSerializableExtra(EXTRA_NOTIFICATION_PEBBLE_COLOR);
                notificationSpec.flags = intent.getIntExtra(EXTRA_NOTIFICATION_FLAGS, 0);
                notificationSpec.sourceAppId = intent.getStringExtra(EXTRA_NOTIFICATION_SOURCEAPPID);
                notificationSpec.iconId = intent.getIntExtra(EXTRA_NOTIFICATION_ICONID, 0);
                notificationSpec.dndSuppressed = intent.getIntExtra(EXTRA_NOTIFICATION_DNDSUPPRESSED, 0);

                if (notificationSpec.type == NotificationType.GENERIC_SMS && notificationSpec.phoneNumber != null) {
                    GBApplication.getIDSenderLookup().add(notificationSpec.getId(), notificationSpec.phoneNumber);
                }

                //TODO: check if at least one of the attached actions is a reply action instead?
                if ((notificationSpec.attachedActions != null && notificationSpec.attachedActions.size() > 0)
                        || (notificationSpec.type == NotificationType.GENERIC_SMS && notificationSpec.phoneNumber != null)) {
                    // NOTE: maybe not where it belongs
                    // I would rather like to save that as an array in SharedPreferences
                    // this would work but I dont know how to do the same in the Settings Activity's xml
                    ArrayList<String> replies = new ArrayList<>();
                    for (int i = 1; i <= 16; i++) {
                        String reply = devicePrefs.getString("canned_reply_" + i, null);
                        if (reply != null && !reply.equals("")) {
                            replies.add(reply);
                        }
                    }
                    notificationSpec.cannedReplies = replies.toArray(new String[0]);
                }

                deviceSupport.onNotification(notificationSpec);
                break;
            }
            case ACTION_DELETE_NOTIFICATION: {
                deviceSupport.onDeleteNotification(intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1));
                break;
            }
            case ACTION_ADD_CALENDAREVENT: {
                CalendarEventSpec calendarEventSpec = new CalendarEventSpec();
                calendarEventSpec.id = intent.getLongExtra(EXTRA_CALENDAREVENT_ID, -1);
                calendarEventSpec.type = intent.getByteExtra(EXTRA_CALENDAREVENT_TYPE, (byte) -1);
                calendarEventSpec.timestamp = intent.getIntExtra(EXTRA_CALENDAREVENT_TIMESTAMP, -1);
                calendarEventSpec.durationInSeconds = intent.getIntExtra(EXTRA_CALENDAREVENT_DURATION, -1);
                calendarEventSpec.allDay = intent.getBooleanExtra(EXTRA_CALENDAREVENT_ALLDAY, false);
                calendarEventSpec.title = sanitizeNotifText(intent.getStringExtra(EXTRA_CALENDAREVENT_TITLE), device);
                calendarEventSpec.description = sanitizeNotifText(intent.getStringExtra(EXTRA_CALENDAREVENT_DESCRIPTION), device);
                calendarEventSpec.location = sanitizeNotifText(intent.getStringExtra(EXTRA_CALENDAREVENT_LOCATION), device);
                calendarEventSpec.calName = sanitizeNotifText(intent.getStringExtra(EXTRA_CALENDAREVENT_CALNAME), device);
                calendarEventSpec.color = intent.getIntExtra(EXTRA_CALENDAREVENT_COLOR, 0);
                deviceSupport.onAddCalendarEvent(calendarEventSpec);
                break;
            }
            case ACTION_DELETE_CALENDAREVENT: {
                long id = intent.getLongExtra(EXTRA_CALENDAREVENT_ID, -1);
                byte type = intent.getByteExtra(EXTRA_CALENDAREVENT_TYPE, (byte) -1);
                deviceSupport.onDeleteCalendarEvent(type, id);
                break;
            }
            case ACTION_RESET: {
                int flags = intent.getIntExtra(EXTRA_RESET_FLAGS, 0);
                deviceSupport.onReset(flags);
                break;
            }
            case ACTION_HEARTRATE_TEST: {
                deviceSupport.onHeartRateTest();
                break;
            }
            case ACTION_FETCH_RECORDED_DATA: {
                if(!getDeviceCoordinator(device).supportsActivityDataFetching()){
                    break;
                }
                int dataTypes = intent.getIntExtra(EXTRA_RECORDED_DATA_TYPES, 0);
                deviceSupport.onFetchRecordedData(dataTypes);
                break;
            }
            case ACTION_DISCONNECT:
                try {
                    removeDeviceSupport(device);
                } catch (DeviceNotFoundException e) {
                    e.printStackTrace();
                }
                device.setState(GBDevice.State.NOT_CONNECTED);
                device.sendDeviceUpdateIntent(this);
                updateReceiversState();
                break;
            case ACTION_FIND_DEVICE: {
                boolean start = intent.getBooleanExtra(EXTRA_FIND_START, false);
                deviceSupport.onFindDevice(start);
                break;
            }
            case ACTION_PHONE_FOUND: {
                deviceSupport.onPhoneFound();
                break;
            }
            case ACTION_SET_CONSTANT_VIBRATION: {
                int intensity = intent.getIntExtra(EXTRA_VIBRATION_INTENSITY, 0);
                deviceSupport.onSetConstantVibration(intensity);
                break;
            }
            case ACTION_CALLSTATE:
                CallSpec callSpec = new CallSpec();
                callSpec.command = intent.getIntExtra(EXTRA_CALL_COMMAND, CallSpec.CALL_UNDEFINED);
                callSpec.number = intent.getStringExtra(EXTRA_CALL_PHONENUMBER);
                callSpec.name = sanitizeNotifText(intent.getStringExtra(EXTRA_CALL_DISPLAYNAME), device);
                callSpec.dndSuppressed = intent.getIntExtra(EXTRA_CALL_DNDSUPPRESSED, 0);
                deviceSupport.onSetCallState(callSpec);
                break;
            case ACTION_SETCANNEDMESSAGES:
                int type = intent.getIntExtra(EXTRA_CANNEDMESSAGES_TYPE, -1);
                String[] cannedMessages = intent.getStringArrayExtra(EXTRA_CANNEDMESSAGES);

                CannedMessagesSpec cannedMessagesSpec = new CannedMessagesSpec();
                cannedMessagesSpec.type = type;
                cannedMessagesSpec.cannedMessages = cannedMessages;
                deviceSupport.onSetCannedMessages(cannedMessagesSpec);
                break;
            case ACTION_SETTIME:
                deviceSupport.onSetTime();
                break;
            case ACTION_SETMUSICINFO:
                MusicSpec musicSpec = new MusicSpec();
                musicSpec.artist = sanitizeNotifText(intent.getStringExtra(EXTRA_MUSIC_ARTIST), device);
                musicSpec.album = sanitizeNotifText(intent.getStringExtra(EXTRA_MUSIC_ALBUM), device);
                musicSpec.track = sanitizeNotifText(intent.getStringExtra(EXTRA_MUSIC_TRACK), device);
                musicSpec.duration = intent.getIntExtra(EXTRA_MUSIC_DURATION, 0);
                musicSpec.trackCount = intent.getIntExtra(EXTRA_MUSIC_TRACKCOUNT, 0);
                musicSpec.trackNr = intent.getIntExtra(EXTRA_MUSIC_TRACKNR, 0);
                deviceSupport.onSetMusicInfo(musicSpec);
                break;
            case ACTION_SET_PHONE_VOLUME:
                float phoneVolume = intent.getFloatExtra(EXTRA_PHONE_VOLUME, 0);
                deviceSupport.onSetPhoneVolume(phoneVolume);
                break;
            case ACTION_SETMUSICSTATE:
                MusicStateSpec stateSpec = new MusicStateSpec();
                stateSpec.shuffle = intent.getByteExtra(EXTRA_MUSIC_SHUFFLE, (byte) 0);
                stateSpec.repeat = intent.getByteExtra(EXTRA_MUSIC_REPEAT, (byte) 0);
                stateSpec.position = intent.getIntExtra(EXTRA_MUSIC_POSITION, 0);
                stateSpec.playRate = intent.getIntExtra(EXTRA_MUSIC_RATE, 0);
                stateSpec.state = intent.getByteExtra(EXTRA_MUSIC_STATE, (byte) 0);
                deviceSupport.onSetMusicState(stateSpec);
                break;
            case ACTION_REQUEST_APPINFO:
                deviceSupport.onAppInfoReq();
                break;
            case ACTION_REQUEST_SCREENSHOT:
                deviceSupport.onScreenshotReq();
                break;
            case ACTION_STARTAPP: {
                UUID uuid = (UUID) intent.getSerializableExtra(EXTRA_APP_UUID);
                boolean start = intent.getBooleanExtra(EXTRA_APP_START, true);
                deviceSupport.onAppStart(uuid, start);
                break;
            }
            case ACTION_DELETEAPP: {
                UUID uuid = (UUID) intent.getSerializableExtra(EXTRA_APP_UUID);
                deviceSupport.onAppDelete(uuid);
                break;
            }
            case ACTION_APP_CONFIGURE: {
                UUID uuid = (UUID) intent.getSerializableExtra(EXTRA_APP_UUID);
                String config = intent.getStringExtra(EXTRA_APP_CONFIG);
                Integer id = null;
                if (intent.hasExtra(EXTRA_APP_CONFIG_ID)) {
                    id = intent.getIntExtra(EXTRA_APP_CONFIG_ID, 0);
                }
                deviceSupport.onAppConfiguration(uuid, config, id);
                break;
            }
            case ACTION_APP_REORDER: {
                UUID[] uuids = (UUID[]) intent.getSerializableExtra(EXTRA_APP_UUID);
                deviceSupport.onAppReorder(uuids);
                break;
            }
            case ACTION_INSTALL:
                Uri uri = intent.getParcelableExtra(EXTRA_URI);
                if (uri != null) {
                    LOG.info("will try to install app/fw");
                    deviceSupport.onInstallApp(uri);
                }
                break;
            case ACTION_SET_ALARMS:
                ArrayList<? extends Alarm> alarms = (ArrayList<? extends Alarm>) intent.getSerializableExtra(EXTRA_ALARMS);
                deviceSupport.onSetAlarms(alarms);
                break;
            case ACTION_SET_REMINDERS:
                ArrayList<? extends Reminder> reminders = (ArrayList<? extends Reminder>) intent.getSerializableExtra(EXTRA_REMINDERS);
                deviceSupport.onSetReminders(reminders);
                break;
            case ACTION_SET_WORLD_CLOCKS:
                ArrayList<? extends WorldClock> clocks = (ArrayList<? extends WorldClock>) intent.getSerializableExtra(EXTRA_WORLD_CLOCKS);
                deviceSupport.onSetWorldClocks(clocks);
                break;
            case ACTION_ENABLE_REALTIME_STEPS: {
                boolean enable = intent.getBooleanExtra(EXTRA_BOOLEAN_ENABLE, false);
                deviceSupport.onEnableRealtimeSteps(enable);
                break;
            }
            case ACTION_ENABLE_HEARTRATE_SLEEP_SUPPORT: {
                boolean enable = intent.getBooleanExtra(EXTRA_BOOLEAN_ENABLE, false);
                deviceSupport.onEnableHeartRateSleepSupport(enable);
                break;
            }
            case ACTION_SET_HEARTRATE_MEASUREMENT_INTERVAL: {
                int seconds = intent.getIntExtra(EXTRA_INTERVAL_SECONDS, 0);
                deviceSupport.onSetHeartRateMeasurementInterval(seconds);
                break;
            }
            case ACTION_ENABLE_REALTIME_HEARTRATE_MEASUREMENT: {
                boolean enable = intent.getBooleanExtra(EXTRA_BOOLEAN_ENABLE, false);
                deviceSupport.onEnableRealtimeHeartRateMeasurement(enable);
                break;
            }
            case ACTION_SEND_CONFIGURATION: {
                String config = intent.getStringExtra(EXTRA_CONFIG);
                deviceSupport.onSendConfiguration(config);
                break;
            }
            case ACTION_READ_CONFIGURATION: {
                String config = intent.getStringExtra(EXTRA_CONFIG);
                deviceSupport.onReadConfiguration(config);
                break;
            }
            case ACTION_TEST_NEW_FUNCTION: {
                deviceSupport.onTestNewFunction();
                break;
            }
            case ACTION_SEND_WEATHER: {
                WeatherSpec weatherSpec = intent.getParcelableExtra(EXTRA_WEATHER);
                if (weatherSpec != null) {
                    deviceSupport.onSendWeather(weatherSpec);
                }
                break;
            }
            case ACTION_SET_LED_COLOR:
                int color = intent.getIntExtra(EXTRA_LED_COLOR, 0);
                if (color != 0) {
                    deviceSupport.onSetLedColor(color);
                }
                break;
            case ACTION_POWER_OFF:
                deviceSupport.onPowerOff();
                break;
            case ACTION_SET_FM_FREQUENCY:
                float frequency = intent.getFloatExtra(EXTRA_FM_FREQUENCY, -1);
                if (frequency != -1) {
                    deviceSupport.onSetFmFrequency(frequency);
                }
                break;
            case ACTION_SET_GPS_LOCATION:
                final Location location = intent.getParcelableExtra(EXTRA_GPS_LOCATION);
                deviceSupport.onSetGpsLocation(location);
                break;
        }
    }

    /**
     * Disposes the current DeviceSupport instance (if any) and sets a new device support instance
     * (if not null).
     *
     * @param deviceSupport deviceSupport to reokace/add
     */
    private void setDeviceSupport(GBDevice device, DeviceSupport deviceSupport) throws DeviceNotFoundException {
       DeviceStruct deviceStruct = getDeviceStruct(device);
       DeviceSupport cachedDeviceSupport = deviceStruct.getDeviceSupport();
       if (deviceSupport != cachedDeviceSupport && cachedDeviceSupport != null) {
           cachedDeviceSupport.dispose();
       }
       deviceStruct.setDeviceSupport(deviceSupport);
    }

    private void removeDeviceSupport(GBDevice device) throws DeviceNotFoundException {
        DeviceStruct struct = getDeviceStruct(device);
        if(struct.getDeviceSupport() != null){
            struct.getDeviceSupport().dispose();
        }
        struct.setDeviceSupport(null);
    }

    private DeviceStruct getDeviceStructOrNull(GBDevice device){
        DeviceStruct deviceStruct = null;
        try {
            deviceStruct = getDeviceStruct(device);
        } catch (DeviceNotFoundException e) {
            e.printStackTrace();
        }
        return deviceStruct;
    }

    public DeviceStruct getDeviceStruct(GBDevice device) throws DeviceNotFoundException {
        if(device == null){
            throw new DeviceNotFoundException("null");
        }
        for(DeviceStruct struct : deviceStructs){
            if(struct.getDevice().equals(device)){
                return struct;
            }
        }
        throw new DeviceNotFoundException(device);
    }

    public GBDevice getDeviceByAddress(String deviceAddress) throws DeviceNotFoundException {
        if(deviceAddress == null){
            throw new DeviceNotFoundException(deviceAddress);
        }
        for(DeviceStruct struct : deviceStructs){
            if(struct.getDevice().getAddress().equals(deviceAddress)){
                return struct.getDevice();
            }
        }
        throw new DeviceNotFoundException(deviceAddress);
    }

    public GBDevice getDeviceByAddressOrNull(String deviceAddress){
        GBDevice device = null;
        try {
            device = getDeviceByAddress(deviceAddress);
        } catch (DeviceNotFoundException e) {
            e.printStackTrace();
        }
        return device;
    }

    private DeviceSupport getDeviceSupport(GBDevice device) throws DeviceNotFoundException {
        if(device == null){
            throw new DeviceNotFoundException("null");
        }
        for(DeviceStruct struct : deviceStructs){
            if(struct.getDevice().equals(device)){
                if(struct.getDeviceSupport() == null)
                    throw new DeviceNotFoundException(device);

                return struct.getDeviceSupport();
            }
        }
        throw new DeviceNotFoundException(device);
    }

    private void start() {
        if (!mStarted) {
            GB.createNotificationChannels(this);
            startForeground(GB.NOTIFICATION_ID, GB.createNotification(getString(R.string.gadgetbridge_running), this));
            mStarted = true;
        }
    }

    public boolean isStarted() {
        return mStarted;
    }

    private boolean isDeviceConnected(GBDevice device) {
        return isDeviceConnected(device.getAddress());
    }

    private boolean isDeviceConnected(String deviceAddress) {
        for(DeviceStruct struct : deviceStructs){
            if(struct.getDevice().getAddress().compareToIgnoreCase(deviceAddress) == 0){
                return struct.getDevice().isConnected();
            }
        }
        return false;
    }

    private boolean isDeviceConnecting(GBDevice device) {
        return isDeviceConnecting(device.getAddress());
    }

    private boolean isDeviceConnecting(String deviceAddress) {
        for(DeviceStruct struct : deviceStructs){
            if(struct.getDevice().getAddress().compareToIgnoreCase(deviceAddress) == 0){
                return struct.getDevice().isConnecting();
            }
        }
        return false;
    }

    private boolean isDeviceInitialized(GBDevice device) {
        return isDeviceInitialized(device.getAddress());
    }

    private boolean isDeviceInitialized(String deviceAddress) {
        for(DeviceStruct struct : deviceStructs){
            if(struct.getDevice().getAddress().compareToIgnoreCase(deviceAddress) == 0){
                return struct.getDevice().isInitialized();
            }
        }
        return false;
    }

    private boolean deviceHasCalendarReceiverRegistered(GBDevice device){
        for (CalendarReceiver receiver: mCalendarReceiver){
            if(receiver.getGBDevice().equals(device)){
                return true;
            }
        }
        return false;
    }


    private void setReceiversEnableState(boolean enable, boolean initialized, FeatureSet features, List <GBDevice> devicesWithCalendar) {
        LOG.info("Setting broadcast receivers to: " + enable);

        if(enable && features == null){
            throw new RuntimeException("features cannot be null when enabling receivers");
        }

        if (enable && initialized && features.supportsCalendarEvents()) {
            for (GBDevice deviceWithCalendar : devicesWithCalendar) {
                if (!deviceHasCalendarReceiverRegistered(deviceWithCalendar)) {
                    if (!(GBApplication.isRunningMarshmallowOrLater() && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_DENIED)) {
                        IntentFilter calendarIntentFilter = new IntentFilter();
                        calendarIntentFilter.addAction("android.intent.action.PROVIDER_CHANGED");
                        calendarIntentFilter.addDataScheme("content");
                        calendarIntentFilter.addDataAuthority("com.android.calendar", null);
                        CalendarReceiver receiver = new CalendarReceiver(deviceWithCalendar);
                        registerReceiver(receiver, calendarIntentFilter);
                        mCalendarReceiver.add(receiver);
                    }
                }
            }
            if (mAlarmReceiver == null) {
                mAlarmReceiver = new AlarmReceiver();
                registerReceiver(mAlarmReceiver, new IntentFilter("DAILY_ALARM"));
            }
        } else {
            for (CalendarReceiver registeredReceiver: mCalendarReceiver){
                unregisterReceiver(registeredReceiver);
            }
            mCalendarReceiver.clear();
            if (mAlarmReceiver != null) {
                unregisterReceiver(mAlarmReceiver);
                mAlarmReceiver = null;
            }
        }

        if (enable) {
            if (mPhoneCallReceiver == null) {
                mPhoneCallReceiver = new PhoneCallReceiver();
                IntentFilter filter = new IntentFilter();
                filter.addAction("android.intent.action.PHONE_STATE");
                filter.addAction("android.intent.action.NEW_OUTGOING_CALL");
                filter.addAction("nodomain.freeyourgadget.gadgetbridge.MUTE_CALL");
                registerReceiver(mPhoneCallReceiver, filter);
            }
            if (mSMSReceiver == null) {
                mSMSReceiver = new SMSReceiver();
                registerReceiver(mSMSReceiver, new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));
            }
            if (mPebbleReceiver == null) {
                mPebbleReceiver = new PebbleReceiver();
                registerReceiver(mPebbleReceiver, new IntentFilter("com.getpebble.action.SEND_NOTIFICATION"));
            }
            if (mMusicPlaybackReceiver == null && features.supportsMusicInfo()) {
                mMusicPlaybackReceiver = new MusicPlaybackReceiver();
                IntentFilter filter = new IntentFilter();
                for (String action : mMusicActions) {
                    filter.addAction(action);
                }
                registerReceiver(mMusicPlaybackReceiver, filter);
            }
            if (mTimeChangeReceiver == null) {
                mTimeChangeReceiver = new TimeChangeReceiver();
                IntentFilter filter = new IntentFilter();
                filter.addAction("android.intent.action.TIME_SET");
                filter.addAction("android.intent.action.TIMEZONE_CHANGED");
                registerReceiver(mTimeChangeReceiver, filter);
            }
            if (mBlueToothPairingRequestReceiver == null) {
                mBlueToothPairingRequestReceiver = new BluetoothPairingRequestReceiver(this);
                registerReceiver(mBlueToothPairingRequestReceiver, new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST));
            }
            if (mAlarmClockReceiver == null) {
                mAlarmClockReceiver = new AlarmClockReceiver();
                IntentFilter filter = new IntentFilter();
                filter.addAction(AlarmClockReceiver.ALARM_ALERT_ACTION);
                filter.addAction(AlarmClockReceiver.ALARM_DONE_ACTION);
                filter.addAction(AlarmClockReceiver.GOOGLE_CLOCK_ALARM_ALERT_ACTION);
                filter.addAction(AlarmClockReceiver.GOOGLE_CLOCK_ALARM_DONE_ACTION);
                registerReceiver(mAlarmClockReceiver, filter);
            }

            // Weather receivers
            if (features.supportsWeather()) {
                if (GBApplication.isRunningOreoOrLater()) {
                    if (mLineageOsWeatherReceiver == null) {
                        mLineageOsWeatherReceiver = new LineageOsWeatherReceiver();
                        registerReceiver(mLineageOsWeatherReceiver, new IntentFilter("GB_UPDATE_WEATHER"));
                    }
                }
                else {
                    if (mCMWeatherReceiver == null) {
                        mCMWeatherReceiver = new CMWeatherReceiver();
                        registerReceiver(mCMWeatherReceiver, new IntentFilter("GB_UPDATE_WEATHER"));
                    }
                }
                if (mTinyWeatherForecastGermanyReceiver == null) {
                    mTinyWeatherForecastGermanyReceiver = new TinyWeatherForecastGermanyReceiver();
                    registerReceiver(mTinyWeatherForecastGermanyReceiver, new IntentFilter("de.kaffeemitkoffein.broadcast.WEATHERDATA"));
                }
                if (mGenericWeatherReceiver == null) {
                    mGenericWeatherReceiver = new GenericWeatherReceiver();
                    registerReceiver(mGenericWeatherReceiver, new IntentFilter(GenericWeatherReceiver.ACTION_GENERIC_WEATHER));
                }
                if (mOmniJawsObserver == null) {
                    try {
                        mOmniJawsObserver = new OmniJawsObserver(new Handler());
                        getContentResolver().registerContentObserver(OmniJawsObserver.WEATHER_URI, true, mOmniJawsObserver);
                    } catch (PackageManager.NameNotFoundException e) {
                        //Nothing wrong, it just means we're not running on omnirom.
                    }
                }
            }

            if (GBApplication.getPrefs().getBoolean("auto_fetch_enabled", false) &&
                    features.supportsActivityDataFetching() && mGBAutoFetchReceiver == null) {
                mGBAutoFetchReceiver = new GBAutoFetchReceiver();
                registerReceiver(mGBAutoFetchReceiver, new IntentFilter("android.intent.action.USER_PRESENT"));
            }
        } else {
            if (mPhoneCallReceiver != null) {
                unregisterReceiver(mPhoneCallReceiver);
                mPhoneCallReceiver = null;
            }
            if (mSMSReceiver != null) {
                unregisterReceiver(mSMSReceiver);
                mSMSReceiver = null;
            }
            if (mPebbleReceiver != null) {
                unregisterReceiver(mPebbleReceiver);
                mPebbleReceiver = null;
            }
            if (mMusicPlaybackReceiver != null) {
                unregisterReceiver(mMusicPlaybackReceiver);
                mMusicPlaybackReceiver = null;
            }
            if (mTimeChangeReceiver != null) {
                unregisterReceiver(mTimeChangeReceiver);
                mTimeChangeReceiver = null;
            }

            if (mBlueToothPairingRequestReceiver != null) {
                unregisterReceiver(mBlueToothPairingRequestReceiver);
                mBlueToothPairingRequestReceiver = null;
            }
            if (mAlarmClockReceiver != null) {
                unregisterReceiver(mAlarmClockReceiver);
                mAlarmClockReceiver = null;
            }
            if (mCMWeatherReceiver != null) {
                unregisterReceiver(mCMWeatherReceiver);
                mCMWeatherReceiver = null;
            }
            if (mLineageOsWeatherReceiver != null) {
                unregisterReceiver(mLineageOsWeatherReceiver);
                mLineageOsWeatherReceiver = null;
            }
            if (mOmniJawsObserver != null) {
                getContentResolver().unregisterContentObserver(mOmniJawsObserver);
                mOmniJawsObserver = null;
            }
            if (mTinyWeatherForecastGermanyReceiver != null) {
                unregisterReceiver(mTinyWeatherForecastGermanyReceiver);
                mTinyWeatherForecastGermanyReceiver = null;
            }
            if (mGBAutoFetchReceiver != null) {
                unregisterReceiver(mGBAutoFetchReceiver);
                mGBAutoFetchReceiver = null;
            }
            if (mGenericWeatherReceiver != null) {
                unregisterReceiver(mGenericWeatherReceiver);
                mGenericWeatherReceiver = null;
            }
        }
    }

    @Override
    public void onDestroy() {
        if (hasPrefs()) {
            getPrefs().getPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        LOG.debug("DeviceCommunicationService is being destroyed");
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        setReceiversEnableState(false, false, null, null); // disable BroadcastReceivers

        unregisterReceiver(mBlueToothConnectReceiver);
        mBlueToothConnectReceiver = null;

        unregisterReceiver(mAutoConnectInvervalReceiver);
        mAutoConnectInvervalReceiver.destroy();
        mAutoConnectInvervalReceiver = null;

        for(GBDevice device : getGBDevices()){
            try {
                removeDeviceSupport(device);
            } catch (DeviceNotFoundException e) {
                e.printStackTrace();
            }
        }
        GB.removeNotification(GB.NOTIFICATION_ID, this); // need to do this because the updated notification won't be cancelled when service stops

        unregisterReceiver(bluetoothCommandReceiver);
        unregisterReceiver(deviceSettingsReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (GBPrefs.DEVICE_AUTO_RECONNECT.equals(key)) {
            for(DeviceStruct deviceStruct : deviceStructs){
                boolean autoReconnect = getGBPrefs().getAutoReconnect(deviceStruct.getDevice());
                deviceStruct.getDeviceSupport().setAutoReconnect(autoReconnect);
            }
        }
        if (GBPrefs.CHART_MAX_HEART_RATE.equals(key) || GBPrefs.CHART_MIN_HEART_RATE.equals(key)) {
            HeartRateUtils.getInstance().updateCachedHeartRatePreferences();
        }
        if (GBPrefs.PREF_ALLOW_INTENT_API.equals(key)){
            allowBluetoothIntentApi = sharedPreferences.getBoolean(GBPrefs.PREF_ALLOW_INTENT_API, false);
            GB.log("allowBluetoothIntentApi changed to " + allowBluetoothIntentApi, GB.INFO, null);
        }
    }

    protected boolean hasPrefs() {
        return getPrefs().getPreferences() != null;
    }

    public Prefs getPrefs() {
        return GBApplication.getPrefs();
    }

    public GBPrefs getGBPrefs() {
        return GBApplication.getGBPrefs();
    }

    public GBDevice[] getGBDevices() {
        GBDevice[] devices = new GBDevice[deviceStructs.size()];
        for(int i = 0; i < devices.length; i++){
            devices[i] = deviceStructs.get(i).getDevice();
        }
        return devices;
    }
}
