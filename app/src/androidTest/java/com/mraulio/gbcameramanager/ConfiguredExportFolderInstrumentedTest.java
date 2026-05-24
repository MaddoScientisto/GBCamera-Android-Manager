package com.mraulio.gbcameramanager;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.mraulio.gbcameramanager.utils.StaticValues;
import com.mraulio.gbcameramanager.utils.Utils;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

@RunWith(AndroidJUnit4.class)
public class ConfiguredExportFolderInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        StaticValues.sharedPreferences = context.getSharedPreferences("Preferences", Context.MODE_PRIVATE);
        Utils.configureStorage(context);
    }

    @Test
    public void configuredExportFolderReceivesExport() throws Exception {
        Uri exportTreeUri = Utils.getStoredExportTreeUri();
        Assume.assumeNotNull("Select a custom export folder in Settings before running this test", exportTreeUri);

        DocumentFile exportDirectory = DocumentFile.fromTreeUri(context, exportTreeUri);
        assertNotNull(exportDirectory);

        File sourceFile = File.createTempFile("configured_export_probe_", ".txt", context.getCacheDir());
        try (OutputStream outputStream = new FileOutputStream(sourceFile)) {
            outputStream.write("configured-export-probe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        String displayName = "configured_export_probe_" + System.currentTimeMillis() + ".txt";
        Utils.SavedExportEntry exportEntry;
        try {
            exportEntry = Utils.saveExportToConfiguredLocation(context, sourceFile, displayName, "text/plain", true);
        } finally {
            sourceFile.delete();
        }

        assertNotNull(exportEntry);
        DocumentFile exportedFile = exportDirectory.findFile(displayName);
        assertNotNull("Expected export file in selected folder", exportedFile);

        try (InputStream inputStream = context.getContentResolver().openInputStream(exportedFile.getUri())) {
            assertNotNull(inputStream);
            org.junit.Assert.assertTrue("Configured-folder export should contain probe bytes", inputStream.read() != -1);
        }
    }
}
