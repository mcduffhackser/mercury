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

package it.skarafaz.mercury.manager;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.DriveStatusCodes;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import it.skarafaz.mercury.MercuryApplication;
import it.skarafaz.mercury.model.settings.DriveResource;
import it.skarafaz.mercury.model.settings.DriveResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

public class DriveResourcesManager {
    private static final Logger logger = LoggerFactory.getLogger(DriveResourcesManager.class);
    private static final String DRIVE_RESOURCES_KEY = "settings_drive_resources";

    private static DriveResourcesManager instance;

    private DriveResourceBundle resources;
    private ObjectMapper objectMapper;

    private DriveResourcesManager() {
        resources = new DriveResourceBundle(0);
        objectMapper = new ObjectMapper();
    }

    public static synchronized DriveResourcesManager getInstance() {
        if (instance == null) {
            instance = new DriveResourcesManager();
        }
        return instance;
    }

    public DriveResourceBundle getResources() {
        return resources;
    }

    public boolean loadResources(DriveResourceClient driveResourceClient) {
        boolean success = true;

        resources.clear();

        for (DriveResource resource : readResources()) {
            try {
                Metadata metadata = Tasks.await(driveResourceClient.getMetadata(resource.getDriveId().asDriveFile()));

                if (!metadata.isExplicitlyTrashed() && !metadata.isTrashed()) {
                    resource.update(metadata);
                    resources.add(resource);
                }
            } catch (ExecutionException e) {
                Integer statusCode = extractStatusCode(e);

                if (statusCode == null || statusCode != DriveStatusCodes.DRIVE_RESOURCE_NOT_AVAILABLE) {
                    String message = e.getCause().getMessage().replace("\n", " ");
                    logger.error("Cannot sync drive resource {}: {}", resource, message);
                    success = false;
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }

        Collections.sort(resources);

        writeResources();

        return success;
    }

    public boolean addResource(DriveResourceClient driveResourceClient, Task<DriveId> pickFileTask) {
        boolean success = false;

        try {
            DriveId driveId = Tasks.await(pickFileTask);
            Metadata metadata = Tasks.await(driveResourceClient.getMetadata(driveId.asDriveFile()));

            success = true;

            DriveResource resource = new DriveResource(metadata);

            int i = resources.indexOf(resource);

            if (i > 0) {
                resources.get(i).update(metadata);
            } else {
                resources.add(resource);
            }

            Collections.sort(resources);
            writeResources();
        } catch (ExecutionException e) {
            // ignore
        } catch (InterruptedException e) {
            // ignore
        }

        return success;
    }

    public void removeResources(Collection<DriveResource> toRemove) {
        resources.removeAll(toRemove);
        writeResources();
    }

    private DriveResourceBundle readResources() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MercuryApplication.getContext());
        return deserializeResources(prefs.getString(DRIVE_RESOURCES_KEY, null));
    }

    private DriveResourceBundle deserializeResources(String str) {
        DriveResourceBundle resources = new DriveResourceBundle();

        if (str != null) {
            try {
                resources = objectMapper.readValue(str, DriveResourceBundle.class);
            } catch (IOException e) {
                logger.error(e.getMessage().replace("\n", " "));
            }
        }

        return resources;
    }

    private void writeResources() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MercuryApplication.getContext());
        prefs.edit().putString(DRIVE_RESOURCES_KEY, serializeResources(resources)).apply();
    }

    private String serializeResources(DriveResourceBundle resources) {
        String str = null;

        if (resources != null) {
            try {
                str = objectMapper.writeValueAsString(resources);
            } catch (JsonProcessingException e) {
                logger.error(e.getMessage().replace("\n", " "));
            }
        }

        return str;
    }

    private Integer extractStatusCode(ExecutionException e) {
        Integer status = null;

        Throwable cause = e.getCause();
        if (cause instanceof ApiException) {
            status = ((ApiException) cause).getStatusCode();
        }

        return status;
    }
}
