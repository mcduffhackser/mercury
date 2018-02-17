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

import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.*;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveStatusCodes;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.OpenFileActivityOptions;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.SearchableField;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import it.skarafaz.mercury.MercuryApplication;
import it.skarafaz.mercury.R;
import it.skarafaz.mercury.activity.MercuryActivity;
import it.skarafaz.mercury.model.event.DriveReady;
import it.skarafaz.mercury.model.settings.DriveResource;
import it.skarafaz.mercury.model.settings.DriveResourceBundle;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class DriveResourcesFragment extends ListFragment implements AbsListView.MultiChoiceModeListener {
    private static final Logger logger = LoggerFactory.getLogger(DriveResourcesFragment.class);
    private static final String DRIVE_RESOURCES_KEY = "settings_drive_resources";
    private static final int RC_PICK_TEXT_FILE = 101;
    private static final int DRC_LOAD_RESOURCES = 301;
    private static final int DRC_ADD_RESOURCE = 302;

    @BindView(R.id.progress)
    protected ProgressBar progressBar;
    @BindView(R.id.add)
    protected FloatingActionButton addButton;

    private ArrayAdapter<DriveResource> listAdapter;

    private ObjectMapper objectMapper = new ObjectMapper();
    private DriveResourceBundle resources = new DriveResourceBundle(0);
    private TaskCompletionSource<DriveId> openItemTask;
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
                getMecuryActivity().refreshDriveSignin(DRC_ADD_RESOURCE);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        EventBus.getDefault().register(this);

        getMecuryActivity().refreshDriveSignin(DRC_LOAD_RESOURCES);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);

        super.onStop();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RC_PICK_TEXT_FILE:
                if (resultCode == AppCompatActivity.RESULT_OK) {
                    openItemTask.setResult((DriveId) data.getParcelableExtra(OpenFileActivityOptions.EXTRA_RESPONSE_DRIVE_ID));
                } else {
                    openItemTask.setException(new RuntimeException("No file selected"));
                }
                break;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_drive_resources, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_reload:
                getMecuryActivity().refreshDriveSignin(DRC_LOAD_RESOURCES);
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

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onDriveReady(DriveReady event) {
        switch (event.getRequestCode()) {
            case DRC_LOAD_RESOURCES:
                if (event.getSuccess()) {
                    loadResources();
                }
                break;
            case DRC_ADD_RESOURCE:
                if (event.getSuccess()) {
                    addResource();
                }
                break;
        }
        EventBus.getDefault().removeStickyEvent(event);
    }

    private void loadResources() {
        if (!busy) {
            new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected void onPreExecute() {
                    busy = true;
                    progressBar.setVisibility(View.VISIBLE);
                    getListView().setVisibility(View.INVISIBLE);
                    getListView().getEmptyView().setVisibility(View.INVISIBLE);
                    addButton.hide();
                }

                @Override
                protected Boolean doInBackground(Void... voids) {
                    boolean success = true;

                    DriveResourceBundle savedResources = readResourcesFromPrefs();
                    DriveResourceBundle updatedResources = new DriveResourceBundle(0);

                    for (DriveResource res : savedResources) {
                        try {
                            Metadata metadata = Tasks.await(getMecuryActivity().getDriveResourceClient().getMetadata(res.getDriveId().asDriveFile()));

                            if (!metadata.isExplicitlyTrashed() && !metadata.isTrashed()) {
                                res.update(metadata);
                                updatedResources.add(res);
                            }
                        } catch (ExecutionException e) {
                            if (!(e.getCause() instanceof ApiException) || ((ApiException) e.getCause()).getStatusCode() != DriveStatusCodes.DRIVE_RESOURCE_NOT_AVAILABLE) {
                                success = false;
                                logger.error(e.getMessage().replace("\n", " "));
                            }
                        } catch (InterruptedException e) {
                            success = false;
                            logger.error(e.getMessage().replace("\n", " "));
                        }
                    }

                    resources.clear();
                    resources.addAll(updatedResources);

                    writeResourcesToPrefs();

                    return success;
                }

                @Override
                protected void onPostExecute(Boolean success) {
                    // TODO message on empty view

                    listAdapter.notifyDataSetChanged();

                    if (!success) {
                        Toast.makeText(getActivity(), getString(R.string.update_drive_resource_failure), Toast.LENGTH_LONG).show();
                    }

                    progressBar.setVisibility(View.INVISIBLE);
                    addButton.show();
                    busy = false;
                }
            }.execute();
        }
    }

    private void addResource() {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                MercuryApplication.showProgressDialog(getFragmentManager(), getString(R.string.wait));
            }

            @Override
            protected Boolean doInBackground(Void... voids) {
                boolean success = true;

                try {
                    DriveId driveId = Tasks.await(pickTextFile());
                    Metadata metadata = Tasks.await(getMecuryActivity().getDriveResourceClient().getMetadata(driveId.asDriveFile()));

                    resources.add(new DriveResource(metadata)); // TODO check if already added

                    writeResourcesToPrefs();
                } catch (ExecutionException e) {
                    success = false;
                } catch (InterruptedException e) {
                    success = false;
                }

                return success;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                MercuryApplication.dismissProgressDialog(getFragmentManager());

                if (success) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        }.execute();
    }

    private Task<DriveId> pickTextFile() {
        OpenFileActivityOptions openOptions = new OpenFileActivityOptions.Builder()
                .setSelectionFilter(Filters.eq(SearchableField.MIME_TYPE, "text/plain"))
                .setActivityTitle(getString(R.string.add_drive_resource))
                .build();

        openItemTask = new TaskCompletionSource<>();

        getMecuryActivity().getDriveClient()
                .newOpenFileActivityIntentSender(openOptions)
                .continueWith(new Continuation<IntentSender, Void>() {
                    @Override
                    public Void then(@NonNull Task<IntentSender> task) throws Exception {
                        startIntentSenderForResult(task.getResult(), RC_PICK_TEXT_FILE, null, 0, 0, 0, null);
                        return null;
                    }
                });

        return openItemTask.getTask();
    }

    private void removeSelectedResources() {
        SparseBooleanArray positions = getListView().getCheckedItemPositions();

        for (int i = positions.size() - 1; i >= 0; i--) {
            if (positions.valueAt(i)) {
                listAdapter.remove(listAdapter.getItem(positions.keyAt(i)));
            }
        }

        writeResourcesToPrefs(); // TODO async ?
    }

    private DriveResourceBundle readResourcesFromPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        return deserializeResources(prefs.getString(DRIVE_RESOURCES_KEY, null));
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

    private void writeResourcesToPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.edit().putString(DRIVE_RESOURCES_KEY, serializeResources(resources)).apply();
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

    private MercuryActivity getMecuryActivity() {
        return (MercuryActivity) getActivity();
    }
}
