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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ListFragment;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.skarafaz.mercury.R;
import it.skarafaz.mercury.model.settings.DriveResource;
import it.skarafaz.mercury.model.settings.DriveResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DriveResourcesFragment extends ListFragment implements AbsListView.MultiChoiceModeListener {
    private static final Logger logger = LoggerFactory.getLogger(DriveResourcesFragment.class);
    public static final String DRIVE_RESOURCES_KEY = "settings_drive_resources";
    private ObjectMapper objectMapper = new ObjectMapper();
    private DriveResourceBundle resources;

    @BindView(R.id.add)
    protected FloatingActionButton addButton;

    private ArrayAdapter<DriveResource> listAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        readResources();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_drive_resources, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        listAdapter = new ArrayAdapter<>(getActivity(), R.layout.drive_resource_list_item, resources);
        setListAdapter(listAdapter);

        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        getListView().setMultiChoiceModeListener(this);

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO

                listAdapter.add(new DriveResource("file_" + (listAdapter.getCount() + 1), "XYZ12345"));
                writeResources();
            }
        });
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.menu_context_drive_resources, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        // Nothing to do
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        // Nothing to do
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_remove:
                removeSelectedResources();
                mode.finish();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        mode.setTitle(getListView().getCheckedItemCount() + "");
    }

    private void removeSelectedResources() {
        SparseBooleanArray positions = getListView().getCheckedItemPositions();

        for (int i = positions.size() - 1; i >=0; i--) {
            if (positions.valueAt(i)) {
                listAdapter.remove(listAdapter.getItem(positions.keyAt(i)));
            }
        }

        writeResources();
    }

    private void readResources() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        resources = deserializeResources(prefs.getString(DRIVE_RESOURCES_KEY, null));
    }

    private DriveResourceBundle deserializeResources(String str) {
        DriveResourceBundle resources = new DriveResourceBundle(0);

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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
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
}
