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

package it.skarafaz.mercury.manager.config;

import android.Manifest;
import android.os.Environment;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.tasks.Tasks;
import it.skarafaz.mercury.MercuryApplication;
import it.skarafaz.mercury.jackson.ServerMapper;
import it.skarafaz.mercury.jackson.ServerValidationException;
import it.skarafaz.mercury.manager.drive.DriveResourcesManager;
import it.skarafaz.mercury.manager.drive.LoadDriveResourcesStatus;
import it.skarafaz.mercury.manager.settings.SettingsManager;
import it.skarafaz.mercury.model.config.Server;
import it.skarafaz.mercury.model.settings.DriveResource;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_DIR = "Mercury-SSH";
    private static final String JSON_EXT = "json";
    private static ConfigManager instance;
    private ServerMapper serverMapper;
    private List<Server> servers;
    private LoadConfigurationStatus status;

    private ConfigManager() {
        serverMapper = new ServerMapper();
        servers = new ArrayList<>();
        status = LoadConfigurationStatus.SUCCESS;
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public List<Server> getServers() {
        return servers;
    }

    public LoadConfigurationStatus getStatus() {
        return status;
    }

    public LoadConfigurationStatus loadConfiguration(DriveClient driveClient, DriveResourceClient driveResourceClient) {
        status = LoadConfigurationStatus.SUCCESS;

        servers.clear();

        loadConfigurationFiles();

        if (SettingsManager.getInstance().getEnableDrive() && driveClient != null && driveResourceClient != null) {
            loadDriveResources(driveClient, driveResourceClient);
        }

        Collections.sort(servers);

        return status;
    }

    private void loadConfigurationFiles() {
        File configDir = new File(Environment.getExternalStorageDirectory(), CONFIG_DIR);

        if (MercuryApplication.isExternalStorageReadable()) {
            if (MercuryApplication.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                if (configDir.exists() && configDir.isDirectory()) {
                    for (File file : FileUtils.listFiles(configDir, new String[]{JSON_EXT, JSON_EXT.toUpperCase()}, false)) {
                        try {
                            servers.add(serverMapper.parseConfigFile(file));
                        } catch (IOException | ServerValidationException e) {
                            status = LoadConfigurationStatus.ERRORS;
                            logger.error(e.getMessage().replace("\n", " "));
                        }
                    }
                } else {
                    if (!(MercuryApplication.isExternalStorageWritable() && configDir.mkdirs())) {
                        status = LoadConfigurationStatus.ERRORS;
                        logger.error("Cannot create configuration directory: {}", configDir);
                    }
                }
            } else {
                status = LoadConfigurationStatus.ERRORS;
                logger.error("Mercury-SSH requires STORAGE permission to read configuration files from external storage");
            }
        } else {
            status = LoadConfigurationStatus.ERRORS;
            logger.error("Cannot read external storage");
        }
    }

    private void loadDriveResources(DriveClient driveClient, DriveResourceClient driveResourceClient) {
        if (DriveResourcesManager.getInstance().loadResources(driveClient, driveResourceClient) == LoadDriveResourcesStatus.ERRORS) {
            status = LoadConfigurationStatus.ERRORS;
        }

        for (DriveResource resource : DriveResourcesManager.getInstance().getResources()) {
            DriveContents contents = null;
            try {
                contents = Tasks.await(driveResourceClient.openFile(resource.getDriveId().asDriveFile(), DriveFile.MODE_READ_ONLY));
                servers.add(serverMapper.parseDriveContents(contents, resource.getTitle()));
            } catch (ExecutionException e) {
                status = LoadConfigurationStatus.ERRORS;
                logger.error(e.getCause().getMessage().replace("\n", " "));
            } catch (ServerValidationException | IOException e) {
                status = LoadConfigurationStatus.ERRORS;
                logger.error(e.getMessage().replace("\n", " "));
            } catch (InterruptedException e) {
                // ignore
            } finally {
                if (contents != null) {
                    try {
                        Tasks.await(driveResourceClient.discardContents(contents));
                    } catch (ExecutionException | InterruptedException e) {
                        // ignore
                    }
                }
            }
        }
    }
}
