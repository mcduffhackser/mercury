/*
 * Mercury-SSH
 * Copyright (C) 2018 Skarafaz
 *
 * This file is part of Mercury-SSH.
 *
 * Mercury-SSH is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Mercury-SSH is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mercury-SSH.  If not, see <http://www.gnu.org/licenses/>.
 */

package it.skarafaz.mercury.manager.settings;


import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.skarafaz.mercury.MercuryApplication;
import it.skarafaz.mercury.model.settings.DriveResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SettingsManager {
    private static final Logger logger = LoggerFactory.getLogger(SettingsManager.class);
    public static final String ENABLE_DRIVE_KEY = "settings_enable_drive";
    public static final String LAST_DRIVE_SYNC_REQUEST_KEY = "settings_last_drive_sync_request";
    public static final String DRIVE_RESOURCES_KEY = "settings_drive_resources";
    private static SettingsManager instance;
    private ObjectMapper objectMapper;

    private SettingsManager() {
        objectMapper = new ObjectMapper();
    }

    public static synchronized SettingsManager getInstance() {
        if (instance == null) {
            instance = new SettingsManager();
        }
        return instance;
    }

    private SharedPreferences getSharedPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(MercuryApplication.getContext());
    }

    public Boolean getEnableDrive() {
        return getSharedPrefs().getBoolean(ENABLE_DRIVE_KEY, false);
    }

    public void saveEnableDrive(boolean value) {
        getSharedPrefs().edit().putBoolean(ENABLE_DRIVE_KEY, value).apply();
    }

    public Long getLastDriveSyncRequest() {
        long value = getSharedPrefs().getLong(LAST_DRIVE_SYNC_REQUEST_KEY, -1);
        return value >= 0 ? value : null;
    }

    public void saveLastDriveSyncRequest() {
        getSharedPrefs().edit().putLong(LAST_DRIVE_SYNC_REQUEST_KEY, System.currentTimeMillis()).apply();
    }

    public DriveResourceBundle getDriveResources() {
        DriveResourceBundle resources = new DriveResourceBundle(0);

        String str = getSharedPrefs().getString(DRIVE_RESOURCES_KEY, null);

        if (str != null) {
            try {
                resources = objectMapper.readValue(str, DriveResourceBundle.class);
            } catch (IOException e) {
                logger.error(e.getMessage().replace("\n", " "));
            }
        }

        return resources;
    }

    public void saveDriveResources(DriveResourceBundle resources) {
        if (resources == null) {
            resources = new DriveResourceBundle(0);
        }

        String str = null;

        try {
            str = objectMapper.writeValueAsString(resources);
        } catch (JsonProcessingException e) {
            logger.error(e.getMessage().replace("\n", " "));
        }

        getSharedPrefs().edit().putString(DRIVE_RESOURCES_KEY, str).apply();
    }
}
