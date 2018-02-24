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

import it.skarafaz.mercury.R;

public enum LoadDriveResourcesStatus {
    SUCCESS(null),
    ERRORS(R.string.update_drive_resource_failure),
    INTERRUPTED(null);

    private Integer message;

    LoadDriveResourcesStatus(Integer message) {
        this.message = message;
    }

    public Integer message() {
        return message;
    }
}
