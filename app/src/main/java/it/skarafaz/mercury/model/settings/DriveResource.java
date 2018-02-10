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

package it.skarafaz.mercury.model.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.android.gms.drive.DriveId;

import java.io.Serializable;

@SuppressWarnings("unused")
public class DriveResource implements Serializable {
    private static final long serialVersionUID = 4570407809866461209L;
    private String fileName;
    private String driveIdString;

    public DriveResource() {
    }

    public DriveResource(String fileName, String driveIdString) {
        this.fileName = fileName;
        this.driveIdString = driveIdString;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDriveIdString() {
        return driveIdString;
    }

    public void setDriveIdString(String driveIdString) {
        this.driveIdString = driveIdString;
    }

    @JsonIgnore
    public DriveId getDriveId() {
        return DriveId.decodeFromString(driveIdString);
    }

    @JsonIgnore
    public void setDriveId(DriveId driveId) {
        driveIdString = driveId.encodeToString();
    }

    @Override
    public String toString() {
        return fileName;
    }
}
