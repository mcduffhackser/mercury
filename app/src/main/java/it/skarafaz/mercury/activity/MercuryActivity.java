/*
 * Mercury-SSH
 * Copyright (C) 2017 Skarafaz
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

package it.skarafaz.mercury.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.tasks.Task;
import it.skarafaz.mercury.R;
import it.skarafaz.mercury.model.event.DriveReady;
import org.greenrobot.eventbus.EventBus;

public abstract class MercuryActivity extends AppCompatActivity {
    private static final int DRIVE_SIGN_IN_REQUEST_CODE = 901;
    private DriveClient driveClient;
    private DriveResourceClient driveResourceClient;
    private Integer lastDriveRequestCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setElevation(0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case DRIVE_SIGN_IN_REQUEST_CODE:
                Task<GoogleSignInAccount> gatAccountTask = GoogleSignIn.getSignedInAccountFromIntent(data);

                if (resultCode == Activity.RESULT_OK && gatAccountTask.isSuccessful()) {
                    initDrive(gatAccountTask.getResult());
                } else {
                    Toast.makeText(this, getString(R.string.drive_signin_failure), Toast.LENGTH_LONG).show();
                }

                break;
        }
    }

    public void refreshDriveSignin(int requestCode) {
        lastDriveRequestCode = requestCode;

        GoogleSignInAccount signInAccount = GoogleSignIn.getLastSignedInAccount(this);

        if (signInAccount != null && signInAccount.getGrantedScopes().contains(Drive.SCOPE_FILE)) {
            initDrive(signInAccount);
        } else {
            GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestScopes(Drive.SCOPE_FILE).build();
            startActivityForResult(GoogleSignIn.getClient(this, signInOptions).getSignInIntent(), DRIVE_SIGN_IN_REQUEST_CODE);
        }
    }

    private void initDrive(GoogleSignInAccount signInAccount) {
        driveClient = Drive.getDriveClient(this, signInAccount);
        driveResourceClient = Drive.getDriveResourceClient(this, signInAccount);
        EventBus.getDefault().postSticky(new DriveReady(lastDriveRequestCode, true));
    }

    public DriveClient getDriveClient() {
        return driveClient;
    }

    public DriveResourceClient getDriveResourceClient() {
        return driveResourceClient;
    }
}
