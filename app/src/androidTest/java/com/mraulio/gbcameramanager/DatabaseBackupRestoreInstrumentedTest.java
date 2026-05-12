package com.mraulio.gbcameramanager;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

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

    private void openDatabase() {
        if (StaticValues.db != null && StaticValues.db.isOpen()) {
            StaticValues.db.close();
        }

        StaticValues.db = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "Database")
                .allowMainThreadQueries()
                .build();
    }
}
