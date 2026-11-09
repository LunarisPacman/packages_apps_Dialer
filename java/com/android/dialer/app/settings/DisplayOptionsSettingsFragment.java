/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.dialer.app.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceManager;

import com.android.dialer.R;
import com.android.dialer.common.LogUtil;
import com.android.dialer.expressive.CallerScreenStylePreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DisplayOptionsSettingsFragment extends com.android.dialer.expressive.ExpressivePreferenceFragment
    implements Preference.OnPreferenceChangeListener {

  private SharedPreferences mPrefs;
  private ListPreference mCallerScreenStyle;
  private MultiSelectListPreference mHiddenNavItems;
  private ActivityResultLauncher<String> mBackgroundPickerLauncher;
  private Preference mCustomBackgroundPref;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBackgroundPickerLauncher = registerForActivityResult(
        new ActivityResultContracts.GetContent(), this::onCustomBackgroundPicked);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
    mPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    String callerStyle = CallerScreenStylePreferences.readAndMigrate(mPrefs);
    addPreferencesFromResource(R.xml.display_options_settings);

    findPreference(getString(R.string.display_options_sort_list_by_key)).setVisible(
        getResources().getBoolean(R.bool.config_sort_order_user_changeable));
    findPreference(getString(R.string.display_options_view_names_as_key)).setVisible(
        getResources().getBoolean(R.bool.config_display_order_user_changeable));

    Preference callerPreference = findPreference(CallerScreenStylePreferences.KEY);
    if (callerPreference instanceof ListPreference) {
      mCallerScreenStyle = (ListPreference) callerPreference;
      mCallerScreenStyle.setValue(callerStyle);
      mCallerScreenStyle.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
      mCallerScreenStyle.setOnPreferenceChangeListener(this);
    }

    mCustomBackgroundPref = findPreference("custom_call_background");
    if (mCustomBackgroundPref != null) {
      updateCustomBackgroundSummary();
      mCustomBackgroundPref.setOnPreferenceClickListener(pref -> {
        boolean isSet = mPrefs.getBoolean("custom_call_background_set", false);
        if (isSet) {
          new AlertDialog.Builder(requireContext())
              .setTitle(R.string.custom_call_background_title)
              .setItems(new CharSequence[] {
                  getString(R.string.custom_call_background_choose_new),
                  getString(R.string.custom_call_background_remove)
              }, (dialog, which) -> {
                if (which == 0) {
                  mBackgroundPickerLauncher.launch("image/*");
                } else if (which == 1) {
                  removeCustomBackground();
                }
              })
              .setNegativeButton(android.R.string.cancel, null)
              .show();
        } else {
          mBackgroundPickerLauncher.launch("image/*");
        }
        return true;
      });
    }

    Preference hiddenNavPref = findPreference("hidden_nav_items");
    if (hiddenNavPref instanceof MultiSelectListPreference) {
      mHiddenNavItems = (MultiSelectListPreference) hiddenNavPref;
      updateHiddenNavItemsSummary(mHiddenNavItems.getValues());
      mHiddenNavItems.setOnPreferenceChangeListener(this);
    }
  }

  private void onCustomBackgroundPicked(@Nullable Uri uri) {
    if (uri == null) {
      return;
    }
    Context context = getContext();
    if (context == null) {
      return;
    }
    try (InputStream in = context.getContentResolver().openInputStream(uri)) {
      if (in == null) {
        return;
      }
      File target = new File(context.getFilesDir(), "custom_call_background");
      try (FileOutputStream out = new FileOutputStream(target)) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
        }
        out.flush();
      }
      mPrefs.edit()
          .putBoolean("custom_call_background_set", true)
          .putLong("custom_call_background_timestamp", System.currentTimeMillis())
          .apply();
      updateCustomBackgroundSummary();
      Toast.makeText(context, R.string.custom_call_background_set_success, Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      LogUtil.e("DisplayOptionsSettings", "Failed to save custom call background", e);
      Toast.makeText(context, R.string.custom_call_background_set_failed, Toast.LENGTH_SHORT).show();
    }
  }

  private void removeCustomBackground() {
    Context context = getContext();
    if (context == null) {
      return;
    }
    File target = new File(context.getFilesDir(), "custom_call_background");
    if (target.exists()) {
      target.delete();
    }
    mPrefs.edit()
        .putBoolean("custom_call_background_set", false)
        .putLong("custom_call_background_timestamp", System.currentTimeMillis())
        .apply();
    updateCustomBackgroundSummary();
    Toast.makeText(context, R.string.custom_call_background_removed, Toast.LENGTH_SHORT).show();
  }

  private void updateCustomBackgroundSummary() {
    if (mCustomBackgroundPref == null) {
      return;
    }
    boolean isSet = mPrefs.getBoolean("custom_call_background_set", false);
    if (isSet) {
      mCustomBackgroundPref.setSummary(R.string.custom_call_background_active_summary);
    } else {
      mCustomBackgroundPref.setSummary(R.string.custom_call_background_summary);
    }
  }

  private void updateHiddenNavItemsSummary(Set<String> values) {
    if (mHiddenNavItems == null) {
      return;
    }
    if (values == null || values.isEmpty()) {
      mHiddenNavItems.setSummary(R.string.hidden_nav_items_summary);
      return;
    }
    List<String> labels = new ArrayList<>();
    CharSequence[] entries = mHiddenNavItems.getEntries();
    CharSequence[] entryValues = mHiddenNavItems.getEntryValues();
    if (entries != null && entryValues != null) {
      for (int i = 0; i < entryValues.length; i++) {
        if (values.contains(entryValues[i].toString())) {
          labels.add(entries[i].toString());
        }
      }
    }
    if (labels.isEmpty()) {
      mHiddenNavItems.setSummary(R.string.hidden_nav_items_summary);
    } else {
      mHiddenNavItems.setSummary(String.join(", ", labels));
    }
  }

  @Override
  public boolean onPreferenceChange(Preference preference, Object objValue) {
    if (preference == mCallerScreenStyle && objValue instanceof String) {
      return mCallerScreenStyle.findIndexOfValue((String) objValue) >= 0;
    }
    if (preference == mHiddenNavItems && objValue instanceof Set) {
      @SuppressWarnings("unchecked")
      Set<String> selected = (Set<String>) objValue;
      if (selected.size() > 2) {
        Toast.makeText(requireContext(), R.string.hidden_nav_items_max_limit_toast, Toast.LENGTH_SHORT).show();
        return false;
      }
      updateHiddenNavItemsSummary(selected);
      return true;
    }
    return false;
  }
}
