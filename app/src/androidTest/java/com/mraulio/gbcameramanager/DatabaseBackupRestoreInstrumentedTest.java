package com.mraulio.gbcameramanager;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.mraulio.gbcameramanager.db.AppDatabase;
import com.mraulio.gbcameramanager.model.GbcPalette;
import com.mraulio.gbcameramanager.utils.StaticValues;
import com.mraulio.gbcameramanager.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DatabaseBackupRestoreInstrumentedTest {
    private Context context;
    private Utils.DatabaseBackupEntry originalBackup;
    private Utils.DatabaseBackupEntry markerBackup;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        openDatabase();
        originalBackup = Utils.createDatabaseBackup(context);
    }

    @After
    public void tearDown() throws Exception {
        if (originalBackup != null) {
            Utils.restoreDatabaseFiles(context, originalBackup);
        }
        openDatabase();
    }

    @Test
    public void databaseBackupCanBeRestoredAfterDatabaseChanges() throws Exception {
        String paletteId = "restore_probe_" + System.currentTimeMillis();
        GbcPalette originalPalette = new GbcPalette("-1,-2,-3,-4,", paletteId);
        originalPalette.setPaletteName("Original restore probe");
        StaticValues.db.paletteDao().insert(originalPalette);

        markerBackup = Utils.createDatabaseBackup(context);
        List<Utils.DatabaseBackupEntry> visibleBackups = Utils.listDatabaseBackups(context, 7);
        boolean backupIsVisible = false;
        for (Utils.DatabaseBackupEntry backupEntry : visibleBackups) {
            if (backupEntry.getName().equals(markerBackup.getName())) {
                backupIsVisible = true;
                break;
            }
        }
        org.junit.Assert.assertTrue("Expected backup to appear in restore list", backupIsVisible);

        GbcPalette modifiedPalette = StaticValues.db.paletteDao().findByName(paletteId);
        assertNotNull(modifiedPalette);
        modifiedPalette.setPaletteName("Modified restore probe");
        StaticValues.db.paletteDao().update(modifiedPalette);

        Utils.restoreDatabaseFiles(context, markerBackup);
        openDatabase();

        GbcPalette restoredPalette = StaticValues.db.paletteDao().findByName(paletteId);
        assertNotNull(restoredPalette);
        org.junit.Assert.assertEquals("Original restore probe", restoredPalette.getPaletteName());

        Utils.restoreDatabaseFiles(context, originalBackup);
        openDatabase();
        assertNull(StaticValues.db.paletteDao().findByName(paletteId));
    }

    @Test
    public void databaseBackupIsWrittenToSharedDownloadsAndCanBeRestored() throws Exception {
        String paletteId = "downloads_restore_probe_" + System.currentTimeMillis();
        GbcPalette originalPalette = new GbcPalette("-1,-2,-3,-4,", paletteId);
        originalPalette.setPaletteName("Original downloads restore probe");
        StaticValues.db.paletteDao().insert(originalPalette);

        Utils.DatabaseBackupEntry downloadsBackup = Utils.createDatabaseBackup(context);
        markerBackup = downloadsBackup;

        org.junit.Assert.assertNull("Android 10+ backups should not use app-private backup directories", downloadsBackup.getDirectory());
        org.junit.Assert.assertNotNull("Backup should expose a shared Downloads relative path", downloadsBackup.getRelativePath());
        org.junit.Assert.assertTrue(
                "Backup should be under shared Downloads",
                downloadsBackup.getRelativePath().startsWith(Environment.DIRECTORY_DOWNLOADS + "/GBCamera Manager/DB Backup/")
        );
        assertSharedDownloadFileExists(downloadsBackup.getRelativePath(), "Database");

        GbcPalette modifiedPalette = StaticValues.db.paletteDao().findByName(paletteId);
        assertNotNull(modifiedPalette);
        modifiedPalette.setPaletteName("Modified downloads restore probe");
        StaticValues.db.paletteDao().update(modifiedPalette);

        Utils.restoreDatabaseFiles(context, downloadsBackup);
        openDatabase();

        GbcPalette restoredPalette = StaticValues.db.paletteDao().findByName(paletteId);
        assertNotNull(restoredPalette);
        org.junit.Assert.assertEquals("Original downloads restore probe", restoredPalette.getPaletteName());
    }

    @Test
    public void defaultExportsAreWrittenToSharedDownloads() throws Exception {
        StaticValues.sharedPreferences.edit().remove("export_tree_uri").apply();

        Utils.SavedExportEntry imageExport = createProbeExport("storage_probe_image_", ".png", "image/png", false);
        assertSharedDownloadFile(imageExport, Environment.DIRECTORY_DOWNLOADS + "/GBCamera Manager/Images/", "image/png");

        Utils.SavedExportEntry textExport = createProbeExport("storage_probe_text_", ".txt", "text/plain", true);
        assertSharedDownloadFile(textExport, Environment.DIRECTORY_DOWNLOADS + "/GBCamera Manager/Hex images/", "text/plain");
    }

    private void openDatabase() {
        if (StaticValues.db != null && StaticValues.db.isOpen()) {
            StaticValues.db.close();
        }

        StaticValues.sharedPreferences = context.getSharedPreferences("Preferences", Context.MODE_PRIVATE);
        Utils.configureStorage(context);

        StaticValues.db = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "Database")
                .allowMainThreadQueries()
                .build();
    }

    private Utils.SavedExportEntry createProbeExport(String prefix, String extension, String mimeType, boolean textExport) throws Exception {
        File sourceFile = File.createTempFile(prefix, extension, context.getCacheDir());
        try (OutputStream outputStream = new FileOutputStream(sourceFile)) {
            outputStream.write(("probe-" + System.currentTimeMillis()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        String displayName = prefix + System.currentTimeMillis() + extension;
        try {
            return Utils.saveExportToConfiguredLocation(context, sourceFile, displayName, mimeType, textExport);
        } finally {
            sourceFile.delete();
        }
    }

    private void assertSharedDownloadFile(Utils.SavedExportEntry exportEntry, String expectedRelativePath, String expectedMimeType) throws Exception {
        assertNotNull(exportEntry);
        org.junit.Assert.assertEquals(expectedMimeType, exportEntry.getMimeType());
        assertSharedDownloadFileExists(expectedRelativePath, exportEntry.getDisplayName());

        try (InputStream inputStream = context.getContentResolver().openInputStream(exportEntry.getUri())) {
            assertNotNull(inputStream);
            org.junit.Assert.assertTrue("Exported file should contain probe bytes", inputStream.read() != -1);
        }
    }

    private void assertSharedDownloadFileExists(String relativePath, String displayName) {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String[] projection = new String[]{
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MIME_TYPE
        };
        String selection = MediaStore.Files.FileColumns.RELATIVE_PATH + "=? AND "
                + MediaStore.Files.FileColumns.DISPLAY_NAME + "=?";
        String[] selectionArgs = new String[]{relativePath, displayName};

        try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, null)) {
            assertNotNull(cursor);
            org.junit.Assert.assertTrue("Expected shared Downloads file: " + relativePath + displayName, cursor.moveToFirst());
        }
    }
}
