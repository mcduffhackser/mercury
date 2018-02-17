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
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ListFragment;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
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
    private static final String DRIVE_RESOURCES_KEY = "settings_drive_resources";

    @BindView(R.id.progress)
    protected ProgressBar progressBar;
    @BindView(R.id.add)
    protected FloatingActionButton addButton;

    private ArrayAdapter<DriveResource> listAdapter;

    private ObjectMapper objectMapper = new ObjectMapper();
    private DriveResourceBundle resources = new DriveResourceBundle(0);
    private boolean busy = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
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

        readResources();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_drive_resources, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_reload:
                readResources();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.menu_context_drive_resources, menu);
        addButton.hide();
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        // Nothing to do
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        addButton.show();
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

        for (int i = positions.size() - 1; i >= 0; i--) {
            if (positions.valueAt(i)) {
                listAdapter.remove(listAdapter.getItem(positions.keyAt(i)));
            }
        }

        writeResources();
    }

    private void readResources() {
        if (!busy) {
            new AsyncTask<Void, Void, DriveResourceBundle>() {
                @Override
                protected void onPreExecute() {
                    busy = true;
                    progressBar.setVisibility(View.VISIBLE);
                    getListView().setVisibility(View.INVISIBLE);
                    getListView().getEmptyView().setVisibility(View.INVISIBLE);
                    addButton.hide();
                }

                @Override
                protected DriveResourceBundle doInBackground(Void... voids) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    DriveResourceBundle resources = deserializeResources(prefs.getString(DRIVE_RESOURCES_KEY, null));

                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // TODO update metadata

                    return resources;
                }

                @Override
                protected void onPostExecute(DriveResourceBundle resources) {
                    listAdapter.clear();
                    listAdapter.addAll(resources);

                    progressBar.setVisibility(View.INVISIBLE);
                    addButton.show();
                    busy = false;
                }
            }.execute();
        }
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
