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

package it.skarafaz.mercury.manager.drive;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.drive.*;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import it.skarafaz.mercury.MercuryApplication;
import it.skarafaz.mercury.misc.ExponentialBackoff;
import it.skarafaz.mercury.model.settings.DriveResource;
import it.skarafaz.mercury.model.settings.DriveResourceBundle;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

public class DriveResourcesManager {
    private static final Logger logger = LoggerFactory.getLogger(DriveResourcesManager.class);
    private static final String DRIVE_RESOURCES_KEY = "settings_drive_resources";
    private static final String LAST_DRIVE_SYNC_REQUEST_KEY = "settings_last_drive_sync_request";
    private static final int SYNC_INTERVAL = 20000;
    private static final int SYNC_ATTEMPTS = 15;

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

    public LoadDriveResourcesStatus loadResources(DriveClient driveCLient, DriveResourceClient driveResourceClient, boolean requestSync) {
        if (requestSync) {
            requestSync(driveCLient);
        }

        LoadDriveResourcesStatus status = LoadDriveResourcesStatus.SUCCESS;

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
                    status = LoadDriveResourcesStatus.ERRORS;

                    String message = e.getCause().getMessage().replace("\n", " ");
                    logger.error("Cannot sync drive resource {}: {}", resource, message);
                }
            } catch (InterruptedException e) {
                status = LoadDriveResourcesStatus.INTERRUPTED;
            }
        }

        Collections.sort(resources);
        writeResources();

        return status;
    }

    public AddDriveResourceStatus addResource(DriveResourceClient driveResourceClient, Task<DriveId> pickFileTask) {
        AddDriveResourceStatus status = AddDriveResourceStatus.SUCCESS;

        try {
            DriveId driveId = Tasks.await(pickFileTask);
            Metadata metadata = Tasks.await(driveResourceClient.getMetadata(driveId.asDriveFile()));

            DriveResource resource = new DriveResource(metadata);

            int i = resources.indexOf(resource);

            if (i >= 0) {
                resources.get(i).update(metadata);
            } else {
                resources.add(resource);
            }

            Collections.sort(resources);
            writeResources();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AddDriveResCancelException) {
                status = AddDriveResourceStatus.CANCELED;
            } else {
                status = AddDriveResourceStatus.ERROR;
                logger.error(e.getMessage().replace("\n", " "));
            }
        } catch (InterruptedException e) {
            status = AddDriveResourceStatus.INTERRUPTED;
        }

        return status;
    }

    public void removeResources(Collection<DriveResource> toRemove) {
        resources.removeAll(toRemove);
        writeResources();
    }

    private void requestSync(final DriveClient driveClient) {
        Long lastSyncRequest = readLastSyncRequest();

        if (lastSyncRequest == null || System.currentTimeMillis() - lastSyncRequest > SYNC_INTERVAL) {
            writeLastSyncRequest();

            new ExponentialBackoff<Void, Void>() {

                @Override
                protected Void doWork(int attempt, Void... voids) throws ExecutionException, InterruptedException {
                    Tasks.await(driveClient.requestSync());
                    logger.debug("request sync: attempt {} success", attempt);

                    return null;
                }

                @Override
                protected boolean handleAttemptFailure(int attempt, Exception exception, Void... voids) {
                    logger.debug("request sync: attempt {} failure", attempt);

                    boolean keepOn = true;

                    if (exception instanceof ExecutionException) {
                        Integer statusCode = extractStatusCode((ExecutionException) exception);

                        if (statusCode == null || statusCode != DriveStatusCodes.DRIVE_RATE_LIMIT_EXCEEDED) {
                            keepOn = false;
                        }
                    } else {
                        keepOn = false;
                    }

                    return keepOn;
                }
            }.execute(SYNC_ATTEMPTS);
        }
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

    private Long readLastSyncRequest() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MercuryApplication.getContext());
        long value = prefs.getLong(LAST_DRIVE_SYNC_REQUEST_KEY, -1);
        return value >= 0 ? value : null;
    }

    private void writeLastSyncRequest() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MercuryApplication.getContext());
        prefs.edit().putLong(LAST_DRIVE_SYNC_REQUEST_KEY, System.currentTimeMillis()).apply();
    }

    private String readDriveResource(DriveResourceClient driveResourceClient, DriveContents contents) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(contents.getInputStream()));
        StringBuilder text = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            text.append(line).append("\n");
        }
        IOUtils.closeQuietly(reader);

        try {
            Tasks.await(driveResourceClient.discardContents(contents));
        } catch (ExecutionException | InterruptedException e) {
            // ignore
        }

        return text.toString();
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
