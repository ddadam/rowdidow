/*
 * Copyright 2015 Sven Meier
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package svenmeier.coxswain.view;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import svenmeier.coxswain.Coxswain;
import svenmeier.coxswain.R;
import svenmeier.coxswain.util.PermissionBlock;
import svenmeier.coxswain.view.preference.ResultPreference;

public class SettingsFragment extends PreferenceFragmentCompat {

    private ResultPreference pendingResult;

    private ActivityResultLauncher<Intent> resultLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        resultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != 0 && result.getData() != null && pendingResult != null) {
                        pendingResult.onResult(result.getData());
                    }
                    pendingResult = null;
                });
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        Preference bindings = findPreference(getString(R.string.preference_workout_bindings_reset));
        bindings.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                propoid.util.content.Preference.getEnum(getActivity(), ValueBinding.class, R.string.preference_workout_binding).setList(new ArrayList<ValueBinding>());
                propoid.util.content.Preference.getEnum(getActivity(), ValueBinding.class, R.string.preference_workout_binding_pace).setList(new ArrayList<ValueBinding>());

                return true;
            }
        });

        final CheckBoxPreference external = (CheckBoxPreference) findPreference(getString(R.string.preference_data_external));
        external.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                if (Boolean.TRUE.equals(o)) {
                    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                        new PermissionBlock(getActivity()) {
                            @Override
                            protected void onPermissionsApproved() {
                                external.setChecked(true);
                            }
                        }.acquirePermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);

                        return false;
                    } else {
                        // scoped storage: app-specific directory needs no permission
                        return true;
                    }
                }

                return true;
            }
        });

        final CheckBoxPreference trace = (CheckBoxPreference) findPreference(getString(R.string.preference_hardware_trace));
        trace.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                if (Boolean.TRUE.equals(o)) {
                    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                        new PermissionBlock(getActivity()) {
                            @Override
                            protected void onPermissionsApproved() {
                                trace.setChecked(true);
                            }
                        }.acquirePermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);

                        return false;
                    } else {
                        return true;
                    }
                }

                return true;
            }
        });

        Preference log = findPreference(getString(R.string.preference_hardware_log));
        log.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                    new PermissionBlock(getActivity()) {
                        @Override
                        protected void onPermissionsApproved() {
                            exportLog();
                        }
                    }.acquirePermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                } else {
                    exportLog();
                }
                return true;
            }
        });

        Preference devices = findPreference(getString(R.string.preference_devices));
        devices.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
                transaction.replace(R.id.settings_fragment, new DevicesFragment());
                transaction.addToBackStack(null);
                transaction.commit();
                return true;
            }
        });
    }

    public static final String LOG_FILE = "coxswain.log";

    private void exportLog() {
        int toast;

        try {
            File dir = Coxswain.getExternalFilesDir(getContext());
            dir.mkdirs();

            File file = new File(dir, LOG_FILE);

            Runtime.getRuntime().exec(new String[]{"logcat", "-f", file.getAbsolutePath()});

            toast = R.string.preference_hardware_log_finished;
        } catch (IOException e) {
            Log.e(Coxswain.TAG, "expor log failed", e);
            toast = R.string.preference_hardware_log_failed;
        }

        Toast.makeText(getContext(), toast, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference instanceof ResultPreference) {
            pendingResult = (ResultPreference) preference;

            resultLauncher.launch(pendingResult.getRequest());

            return true;
        }

        return super.onPreferenceTreeClick(preference);
    }
}