package com.example.weather;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {
    public static final String PREF_NAME = "weather_settings";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_TEMPERATURE_UNIT = "temperature_unit";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(PREF_NAME);
        setPreferencesFromResource(R.xml.preferences, rootKey);

        ListPreference languagePreference = findPreference(KEY_LANGUAGE);
        if (languagePreference != null) {
            languagePreference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            languagePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(String.valueOf(newValue))
                );
                return true;
            });
        }

        ListPreference temperatureUnitPreference = findPreference(KEY_TEMPERATURE_UNIT);
        if (temperatureUnitPreference != null) {
            temperatureUnitPreference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        }
    }
}
