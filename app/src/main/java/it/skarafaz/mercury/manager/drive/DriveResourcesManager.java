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

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.drive.*;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import it.skarafaz.mercury.manager.settings.SettingsManager;
import it.skarafaz.mercury.misc.ExponentialBackoff;
import it.skarafaz.mercury.model.settings.DriveResource;
import it.skarafaz.mercury.model.settings.DriveResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

public class DriveResourcesManager {
    private static final Logger logger = LoggerFactory.getLogger(DriveResourcesManager.class);
    private static final int SYNC_INTERVAL = 20000;
    private static final int SYNC_ATTEMPTS = 15;

    private static DriveResourcesManager instance;

    private DriveResourceBundle resources;

    private DriveResourcesManager() {
        resources = new DriveResourceBundle(0);
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

        for (DriveResource resource : SettingsManager.getInstance().getDriveResources()) {
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
        SettingsManager.getInstance().saveDriveResources(resources);

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
            SettingsManager.getInstance().saveDriveResources(resources);
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
        SettingsManager.getInstance().saveDriveResources(resources);
    }

    private void requestSync(final DriveClient driveClient) {
        Long lastSyncRequest = SettingsManager.getInstance().getLastDriveSyncRequest();

        if (lastSyncRequest == null || System.currentTimeMillis() - lastSyncRequest > SYNC_INTERVAL) {
            SettingsManager.getInstance().saveLastDriveSyncRequest();

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

    private Integer extractStatusCode(ExecutionException e) {
        Integer status = null;

        Throwable cause = e.getCause();
        if (cause instanceof ApiException) {
            status = ((ApiException) cause).getStatusCode();
        }

        return status;
    }
}
