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

import android.support.annotation.NonNull;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;

@SuppressWarnings("unused")
public class DriveResource implements Serializable, Comparable<DriveResource> {
    private static final long serialVersionUID = 4570407809866461209L;
    private String title;
    private String driveIdString;

    public DriveResource() {
    }

    public DriveResource(Metadata metadata) {
        update(metadata);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public void update(Metadata metadata) {
        this.title = metadata.getTitle();
        this.driveIdString = metadata.getDriveId().encodeToString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof DriveResource)) {
            return false;
        }

        DriveResource other = (DriveResource) o;

        return new EqualsBuilder().append(getDriveId(), other.getDriveId()).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(getDriveId()).toHashCode();
    }

    @Override
    public int compareTo(@NonNull DriveResource o) {
        return title.compareTo(o.getTitle());
    }

    @Override
    public String toString() {
        return title;
    }
}
