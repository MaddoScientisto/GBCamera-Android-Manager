package com.mraulio.gbcameramanager.ui.gbstorage;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.utils.LoadingDialog;
import com.mraulio.gbcameramanager.utils.StaticValues;
import com.mraulio.gbcameramanager.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class GbStorageFragment extends Fragment {
    private final SharedPreferences.Editor editor = StaticValues.sharedPreferences.edit();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        StaticValues.currentFragment = StaticValues.CURRENT_FRAGMENT.GBSTORAGE;
        View view = inflater.inflate(R.layout.fragment_gbstorage, container, false);

        EditText etServerAddress = view.findViewById(R.id.etGbStorageServerAddress);
        EditText etApiKey = view.findViewById(R.id.etGbStorageApiKey);
        Spinner spinnerTimestampSource = view.findViewById(R.id.spGbStorageTimestampSource);
        Button btnSave = view.findViewById(R.id.btnGbStorageSave);
        Button btnTest = view.findViewById(R.id.btnGbStorageTestConnection);

        etServerAddress.setText(StaticValues.gbStorageServerAddress);
        etApiKey.setText(StaticValues.gbStorageApiKey);

        List<String> timestampOptions = new ArrayList<>();
        timestampOptions.add(getString(R.string.gbstorage_creation_date));
        timestampOptions.add(getString(R.string.gbstorage_current_time));

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, timestampOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTimestampSource.setAdapter(spinnerAdapter);
        spinnerTimestampSource.setSelection("current_time".equals(StaticValues.gbStorageTimestampSource) ? 1 : 0);

        btnSave.setOnClickListener(v -> saveSettings(etServerAddress.getText().toString(), etApiKey.getText().toString(), spinnerTimestampSource.getSelectedItemPosition()));
        btnTest.setOnClickListener(v -> {
            saveSettings(etServerAddress.getText().toString(), etApiKey.getText().toString(), spinnerTimestampSource.getSelectedItemPosition());
            LoadingDialog loadingDialog = new LoadingDialog(requireContext(), getString(R.string.loading));
            loadingDialog.showDialog();
            GbStorageSyncManager.testConnection(requireContext(), StaticValues.gbStorageServerAddress, StaticValues.gbStorageApiKey, result -> {
                loadingDialog.dismissDialog();
                AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                builder.setTitle(R.string.gbstorage_test_dialog_title);
                builder.setMessage(result.getDetails());
                builder.setPositiveButton(getString(R.string.dialog_close_button), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                builder.show();
            });
        });

        return view;
    }

    private void saveSettings(String serverAddress, String apiKey, int timestampSelection) {
        String normalizedServerAddress = serverAddress == null ? "" : serverAddress.trim();
        String normalizedApiKey = apiKey == null ? "" : apiKey.trim();

        StaticValues.gbStorageServerAddress = normalizedServerAddress;
        StaticValues.gbStorageApiKey = normalizedApiKey;
        StaticValues.gbStorageTimestampSource = timestampSelection == 1 ? "current_time" : "creation_date";

        editor.putString("gbstorage_server_address", normalizedServerAddress);
        editor.putString("gbstorage_api_key", normalizedApiKey);
        editor.putString("gbstorage_timestamp_source", StaticValues.gbStorageTimestampSource);
        editor.apply();

        Utils.toast(requireContext(), getString(R.string.gbstorage_save_settings));
    }
}