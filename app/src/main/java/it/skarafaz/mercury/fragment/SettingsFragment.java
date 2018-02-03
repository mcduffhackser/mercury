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

package it.skarafaz.mercury.fragment;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.annotation.Nullable;
import android.widget.Toast;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.drive.Drive;
import it.skarafaz.mercury.MercuryApplication;
import it.skarafaz.mercury.R;

public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int DRIVE_SIGN_IN_REQUEST_CODE = 101;
    private static final String LOAD_FROM_DRIVE_KEY = "settings_load_from_drive";
    private SwitchPreference loadFromDrivePreference;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);
        loadFromDrivePreference = (SwitchPreference) findPreference(LOAD_FROM_DRIVE_KEY);
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case DRIVE_SIGN_IN_REQUEST_CODE:
                boolean failed = true;

                if (resultCode == Activity.RESULT_OK && GoogleSignIn.getSignedInAccountFromIntent(data).isSuccessful()) {
                    failed = false;
                }

                if (failed) {
                    Toast.makeText(getActivity(), getString(R.string.drive_signin_failure), Toast.LENGTH_LONG).show();
                    loadFromDrivePreference.setChecked(false);
                }

                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case LOAD_FROM_DRIVE_KEY:
                if (sharedPreferences.getBoolean(key, false)) {
                    signInToDrive();
                }
                break;
        }
    }

    protected void signInToDrive() {
        GoogleSignInAccount signInAccount = GoogleSignIn.getLastSignedInAccount(MercuryApplication.getContext());

        if (signInAccount == null || !signInAccount.getGrantedScopes().contains(Drive.SCOPE_FILE)) {
            GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestScopes(Drive.SCOPE_FILE).build();
            startActivityForResult(GoogleSignIn.getClient(getActivity(), signInOptions).getSignInIntent(), DRIVE_SIGN_IN_REQUEST_CODE);
        }
    }
}
