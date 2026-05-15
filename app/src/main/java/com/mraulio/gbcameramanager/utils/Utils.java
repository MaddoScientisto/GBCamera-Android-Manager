package com.mraulio.gbcameramanager.utils;

import static com.mraulio.gbcameramanager.utils.StaticValues.dateLocale;
import static com.mraulio.gbcameramanager.utils.StaticValues.hiddenTags;
import static com.mraulio.gbcameramanager.utils.StaticValues.selectedTags;
import static com.mraulio.gbcameramanager.utils.StaticValues.sharedPreferences;
import static com.mraulio.gbcameramanager.utils.DiskCache.CACHE_DIR_NAME;
import static com.mraulio.gbcameramanager.utils.StaticValues.sortPalettesByUsage;
import static com.mraulio.gbcameramanager.utils.StaticValues.timesPalettesUsed;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.gameboycameralib.codecs.Codec;
import com.mraulio.gbcameramanager.gameboycameralib.codecs.ImageCodec;
import com.mraulio.gbcameramanager.model.GbcFrame;
import com.mraulio.gbcameramanager.model.GbcImage;
import com.mraulio.gbcameramanager.model.GbcPalette;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class with puclic static variables and methods that are shared alongside the app
 */
public class Utils {
    private static final String MAIN_FOLDER_NAME = "GBCamera Manager";
    private static final String DB_BACKUP_FOLDER_NAME = "DB Backup";
    static File downloadDirectory = new File(".");
    static File picturesDirectory = new File(".");

    public static int notificationId = 0;
    public static File MAIN_FOLDER = new File(downloadDirectory, MAIN_FOLDER_NAME);
    public static File SAVE_FOLDER = new File(MAIN_FOLDER, "Save dumps");
    public static File IMAGES_FOLDER = new File(picturesDirectory, MAIN_FOLDER_NAME);
    public static File IMAGES_JSON = new File(MAIN_FOLDER, "Images json");
    public static File TXT_FOLDER = new File(MAIN_FOLDER, "Hex images");
    public static File PALETTES_FOLDER = new File(MAIN_FOLDER, "Palettes json");
    public static File FRAMES_FOLDER = new File(MAIN_FOLDER, "Frames json");
    public static File ARDUINO_HEX_FOLDER = new File(MAIN_FOLDER, "Arduino Printer Hex");
    public static File PHOTO_DUMPS_FOLDER = new File(MAIN_FOLDER, "PHOTO Rom Dumps");
    public static File DB_BACKUP_FOLDER = new File(MAIN_FOLDER, DB_BACKUP_FOLDER_NAME);
    public static final String CHANNEL_ID = "gbcam_channel";
    public static final String CHANNEL_NAME = "GBCAM Channel";

    private static final String DB_NAME = "Database";
    private static final String DB_NAME_SHM = "Database-shm";
    private static final String DB_NAME_WAL = "Database-wal";
    private static final String DB_BACKUP_TREE_URI_PREF = "db_backup_tree_uri";
    private static final String EXPORT_TREE_URI_PREF = "export_tree_uri";
    private static final int MIN_RESTORABLE_DB_VERSION = 5;

    public static LinkedHashMap<String, String> frameGroupsNames = new LinkedHashMap<>();

    public static final int[] ROTATION_VALUES = {0, 90, 180, 270};
    public static List<GbcImage> gbcImagesList = new ArrayList<>();
    public static ArrayList<GbcPalette> gbcPalettesList = new ArrayList<>();
    public static ArrayList<GbcPalette> sortedPalettes = new ArrayList<>();
    public static List<GbcFrame> framesList = new ArrayList<>();
    public static HashMap<String, Bitmap> imageBitmapCache = new HashMap<>();
    public static LinkedHashMap<String, GbcFrame> hashFrames = new LinkedHashMap<>();
    public static HashMap<String, GbcPalette> hashPalettes = new HashMap<>();

    public static LinkedHashSet<String> tagsHash = new LinkedHashSet<>();

    public static enum SAVE_TYPE_INT_JP_HK {
        INT,
        JP,
        HK
    }

    public static HashMap<String, String> saveTypeNames = new HashMap<String, String>() {{
        put("International", "INT");
        put("Japanese", "JP");
        put("Hello Kitty", "HK");
    }};

    public static final class DatabaseBackupEntry {
        private final String name;
        private final int version;
        private final File directory;
        private final String relativePath;
        private final DocumentFile documentDirectory;

        DatabaseBackupEntry(String name, int version, File directory, String relativePath, DocumentFile documentDirectory) {
            this.name = name;
            this.version = version;
            this.directory = directory;
            this.relativePath = relativePath;
            this.documentDirectory = documentDirectory;
        }

        public String getName() {
            return name;
        }

        public int getVersion() {
            return version;
        }

        public File getDirectory() {
            return directory;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public DocumentFile getDocumentDirectory() {
            return documentDirectory;
        }
    }

    public static final class SavedExportEntry {
        private final String displayName;
        private final Uri uri;
        private final String mimeType;

        SavedExportEntry(String displayName, Uri uri, String mimeType) {
            this.displayName = displayName;
            this.uri = uri;
            this.mimeType = mimeType;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Uri getUri() {
            return uri;
        }

        public String getMimeType() {
            return mimeType;
        }
    }

    public static void configureStorage(Context context) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        File downloads = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File pictures = appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        downloadDirectory = downloads != null ? downloads : appContext.getFilesDir();
        picturesDirectory = pictures != null ? pictures : appContext.getFilesDir();

        MAIN_FOLDER = new File(downloadDirectory, MAIN_FOLDER_NAME);
        SAVE_FOLDER = new File(MAIN_FOLDER, "Save dumps");
        IMAGES_FOLDER = new File(picturesDirectory, MAIN_FOLDER_NAME);
        IMAGES_JSON = new File(MAIN_FOLDER, "Images json");
        TXT_FOLDER = new File(MAIN_FOLDER, "Hex images");
        PALETTES_FOLDER = new File(MAIN_FOLDER, "Palettes json");
        FRAMES_FOLDER = new File(MAIN_FOLDER, "Frames json");
        ARDUINO_HEX_FOLDER = new File(MAIN_FOLDER, "Arduino Printer Hex");
        PHOTO_DUMPS_FOLDER = new File(MAIN_FOLDER, "PHOTO Rom Dumps");
        DB_BACKUP_FOLDER = new File(MAIN_FOLDER, DB_BACKUP_FOLDER_NAME);
    }

    //Auxiliary method to convert byte[] to hexadecimal String
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] encodeImage(Bitmap bitmap, String paletteId) throws IOException {
        Codec decoder = new ImageCodec(160, bitmap.getHeight());
        return decoder.encodeInternal(bitmap, paletteId);
    }

    public static byte[] convertToByteArray(String data) {
        String[] byteStrings = data.split(" ");
        byte[] bytes = new byte[byteStrings.length];
        for (int i = 0; i < byteStrings.length; i++) {
            bytes[i] = (byte) ((Character.digit(byteStrings[i].charAt(0), 16) << 4)
                    + Character.digit(byteStrings[i].charAt(1), 16));
        }
        return bytes;
    }

    public static void makeDirs() {
        List<File> listFiles = new ArrayList<>();
        listFiles.add(MAIN_FOLDER);
        listFiles.add(SAVE_FOLDER);
        listFiles.add(IMAGES_FOLDER);
        listFiles.add(IMAGES_JSON);
        listFiles.add(TXT_FOLDER);
        listFiles.add(PALETTES_FOLDER);
        listFiles.add(FRAMES_FOLDER);
        listFiles.add(ARDUINO_HEX_FOLDER);
        listFiles.add(PHOTO_DUMPS_FOLDER);

        for (File file : listFiles) {
            try {
                file.mkdirs();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static LinkedHashSet<String> retrieveTags(List<GbcImage> gbcImages) {
        tagsHash.clear();
        for (GbcImage gbcImage : gbcImages) {
            for (String tag : gbcImage.getTags()) {
                tagsHash.add(tag);
            }
        }
        return tagsHash;
    }

    public static void saveTagsSet(HashSet<String> tagsList, boolean saveAsHiddenTag) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(tagsList);
        if (!saveAsHiddenTag) {
            editor.putString("selected_tags", json);
        } else {
            editor.putString("hidden_tags", json);
        }
        editor.apply();
    }

    public static HashSet<String> getSelectedTags() {
        try {
            HashSet<String> tagList;

            if (!selectedTags.isEmpty()) {
                tagList = new Gson().fromJson(selectedTags, new TypeToken<HashSet<String>>() {
                }.getType());
            } else {
                tagList = new HashSet<>();
            }
            return tagList;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashSet<>();
        }
    }

    public static HashSet<String> getHiddenTags() {
        try {
            HashSet<String> hiddenTagList;

            if (!hiddenTags.isEmpty()) {
                hiddenTagList = new Gson().fromJson(hiddenTags, new TypeToken<HashSet<String>>() {
                }.getType());
            } else {
                hiddenTagList = new HashSet<>();
            }
            return hiddenTagList;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashSet<>();
        }
    }

    public static void toast(Context context, String message) {
        Context toastContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        Runnable showToast = () -> Toast.makeText(toastContext, message, Toast.LENGTH_SHORT).show();
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showToast.run();
        } else {
            new Handler(Looper.getMainLooper()).post(showToast);
        }
    }

    public static Bitmap rotateBitmap(Bitmap originalBitmap, GbcImage gbcImage) {
        Matrix matrix = new Matrix();
        matrix.postRotate(ROTATION_VALUES[gbcImage.getRotation()]);
        Bitmap rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, false);
        return rotatedBitmap;
    }

    public static Bitmap transparentBitmap(Bitmap bitmap, GbcFrame gbcFrame) {
        HashSet<int[]> transparencyHS = null;
        if (gbcFrame.getTransparentPixelPositions().size() == 0) {
            transparencyHS = transparencyHashSet(gbcFrame.getFrameBitmap());
            if (transparencyHS.size() == 0) {
                transparencyHS = generateDefaultTransparentPixelPositions(gbcFrame.getFrameBitmap(), gbcFrame.getImageMargin());
            }
            gbcFrame.setTransparentPixelPositions(transparencyHS);
        } else {
            transparencyHS = gbcFrame.getTransparentPixelPositions();
        }

        int transparentPixel = Color.argb(0, 0, 0, 0);
        for (int[] position : transparencyHS) {
            bitmap.setPixel(position[0], position[1], transparentPixel);
        }
        return bitmap;
    }

    public static HashSet<int[]> transparencyHashSet(Bitmap bitmap) {
        HashSet<int[]> transparentPixelPositions = new HashSet<>();
        // Iterate through the bitmap pixels
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.alpha(pixel) == 0) {
                    int[] pos = {x, y};
                    transparentPixelPositions.add(pos);
                }
            }
        }
        return transparentPixelPositions;
    }

    public static HashSet<int[]> generateDefaultTransparentPixelPositions(Bitmap bitmap, int imageMargin) {
        System.out.println(imageMargin+" ///////*****");
        HashSet<int[]> transparentPixelPositions = new HashSet<>();

        int bitmapHeight = bitmap.getHeight();
        int innerBitmapWidth = 128;
        int innerBitmapHeight = 112;
        int startX = 16;
//        int startY = 16;
//        if (bitmapHeight == 224) startY = 40;

        for (int y = imageMargin; y < imageMargin + innerBitmapHeight; y++) {
            for (int x = startX; x < startX + innerBitmapWidth; x++) {
                int[] pos = {x, y};
                transparentPixelPositions.add(pos);
            }
        }
        return transparentPixelPositions;
    }

    public static String generateHashFromBytes(byte[] bytes) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        String hashHex = Utils.bytesToHex(hash);
        return hashHex;
    }

    public static String removeNumbersFromEnd(String input) {
        return input.replaceAll("\\d+$", "");
    }

    public static void backupDatabase(Context context) {
        try {
            DatabaseBackupEntry backupEntry = createDatabaseBackup(context);
            toast(context, context.getString(R.string.toast_backup_db) + "\nVersion: " + backupEntry.getVersion());
        } catch (IOException e) {
            e.printStackTrace();
            toast(context, "Error creating DB backup");
        }
    }

    public static DatabaseBackupEntry createDatabaseBackup(Context context) throws IOException {
        int databaseVersion = StaticValues.db.getOpenHelper().getReadableDatabase().getVersion();
        checkpointDatabase();

        SimpleDateFormat sdf = new SimpleDateFormat(getBackupDatePattern() + "_HH-mm-ss-SSS", Locale.getDefault());
        Date date = new Date();
        String backupName = sdf.format(date) + "_v" + databaseVersion;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            DatabaseBackupEntry backupEntry = new DatabaseBackupEntry(
                    backupName,
                    databaseVersion,
                    null,
                    getSharedBackupRelativePath(backupName),
                    null
            );
            writeDatabaseBackupToSharedDownloads(context, backupEntry, getDatabaseFiles(context));
            return backupEntry;
        }

        File backupDir = new File(getLegacySharedBackupRoot(), backupName);
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new IOException("Unable to create backup directory: " + backupDir);
        }

        copyDatabaseFamily(getDatabaseFiles(context), getBackupFiles(backupDir), false);
        return new DatabaseBackupEntry(backupName, databaseVersion, backupDir, null, null);
    }

    private static String getBackupDatePattern() {
        return dateLocale == null || dateLocale.isEmpty() ? "yyyy-MM-dd" : dateLocale;
    }

    private static void checkpointDatabase() {
        if (StaticValues.db == null || !StaticValues.db.isOpen()) {
            return;
        }

        SupportSQLiteDatabase database = StaticValues.db.getOpenHelper().getWritableDatabase();
        Cursor cursor = database.query("PRAGMA wal_checkpoint(FULL)");
        cursor.close();
    }

    public static boolean showDbBackups(Context context, Activity activity) {
        if (!ensureAutomaticDownloadsBackupAccess(context)) {
            return true;
        }

        // Show backups that the current Room schema can migrate from.
        int databaseVersion = StaticValues.db.getOpenHelper().getReadableDatabase().getVersion();

        List<DatabaseBackupEntry> availableDirectories = listDatabaseBackups(context, databaseVersion);
        if (availableDirectories.isEmpty()) {
            Uri persistedTreeUri = getStoredBackupTreeUri();
            if (persistedTreeUri != null) {
                availableDirectories = listDocumentTreeBackups(context, persistedTreeUri, databaseVersion);
            }
        }

        if (availableDirectories.isEmpty()) {
            return false;
        }

        final List<DatabaseBackupEntry> directories = availableDirectories;
        final List<String> directoriesNames = new ArrayList<>();
        for (DatabaseBackupEntry entry : directories) {
            directoriesNames.add(entry.getName());
        }

        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.restore_db_dialog);

        final DatabaseBackupEntry[] selectedDirectory = {null};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, directoriesNames);
        ListView listView = dialog.findViewById(R.id.listViewRestoreDb);
        listView.setAdapter(adapter);
        Button btnCancelRestoreDb = dialog.findViewById(R.id.btnCancelRestoreDb);
        btnCancelRestoreDb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        Button btnOkRestoreDb = dialog.findViewById(R.id.btnOkRestoreDb);
        btnOkRestoreDb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (selectedDirectory[0] == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setMessage(context.getString(R.string.dialog_confirm_restore_db))
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                restoreDatabase(context, selectedDirectory[0], activity);
                            }
                        })
                        .setNegativeButton(context.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .show();
            }
        });

        dialog.show();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedDirectory[0] = directories.get(position);
            }
        });

        return true;
    }

    public static void persistBackupTreeUri(Context context, Uri treeUri) {
        if (treeUri == null) {
            return;
        }

        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        context.getContentResolver().takePersistableUriPermission(treeUri, flags);
        sharedPreferences.edit().putString(DB_BACKUP_TREE_URI_PREF, treeUri.toString()).apply();
    }

    public static void persistExportTreeUri(Context context, Uri treeUri) {
        if (treeUri == null) {
            return;
        }

        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        context.getContentResolver().takePersistableUriPermission(treeUri, flags);
        sharedPreferences.edit().putString(EXPORT_TREE_URI_PREF, treeUri.toString()).apply();
    }

    public static Uri getStoredBackupTreeUri() {
        String treeUri = sharedPreferences.getString(DB_BACKUP_TREE_URI_PREF, "");
        if (treeUri == null || treeUri.isEmpty()) {
            return null;
        }
        return Uri.parse(treeUri);
    }

    public static Uri getStoredExportTreeUri() {
        String treeUri = sharedPreferences.getString(EXPORT_TREE_URI_PREF, "");
        if (treeUri == null || treeUri.isEmpty()) {
            return null;
        }
        return Uri.parse(treeUri);
    }

    public static void clearExportTreeUri(Context context) {
        Uri treeUri = getStoredExportTreeUri();
        if (treeUri != null) {
            try {
                int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                context.getContentResolver().releasePersistableUriPermission(treeUri, flags);
            } catch (SecurityException ignored) {
            }
        }
        sharedPreferences.edit().remove(EXPORT_TREE_URI_PREF).apply();
    }

    public static String getExportLocationSummary(Context context) {
        Uri exportTreeUri = getStoredExportTreeUri();
        if (exportTreeUri == null) {
            return context.getString(R.string.export_location_default_value);
        }

        DocumentFile documentFile = DocumentFile.fromTreeUri(context, exportTreeUri);
        if (documentFile != null && documentFile.getName() != null && !documentFile.getName().isEmpty()) {
            return context.getString(R.string.export_location_custom_value, documentFile.getName());
        }

        return context.getString(R.string.export_location_custom_value, exportTreeUri.toString());
    }

    private static int getBackupDatabaseVersion(File backupDirectory) {
        return getBackupDatabaseVersion(backupDirectory.getName());
    }

    private static int getBackupDatabaseVersion(String backupName) {
        Pattern pattern = Pattern.compile("_v(\\d+)$");
        Matcher matcher = pattern.matcher(backupName);
        if (!matcher.find()) {
            return -1;
        }

        return Integer.parseInt(matcher.group(1));
    }

    private static boolean isBackupVersionRestorable(int backupVersion, int currentDatabaseVersion) {
        return backupVersion >= MIN_RESTORABLE_DB_VERSION && backupVersion <= currentDatabaseVersion;
    }

    public static List<DatabaseBackupEntry> listDatabaseBackups(Context context, int currentDatabaseVersion) {
        List<DatabaseBackupEntry> entries = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? listSharedDownloadBackups(context)
                : listLegacySharedBackups();
        List<DatabaseBackupEntry> filtered = new ArrayList<>();
        for (DatabaseBackupEntry entry : entries) {
            if (isBackupVersionRestorable(entry.getVersion(), currentDatabaseVersion)) {
                filtered.add(entry);
            }
        }
        Collections.sort(filtered, new Comparator<DatabaseBackupEntry>() {
            @Override
            public int compare(DatabaseBackupEntry left, DatabaseBackupEntry right) {
                return right.getName().compareTo(left.getName());
            }
        });
        return filtered;
    }

    private static boolean ensureAutomaticDownloadsBackupAccess(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            return true;
        }

        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        context.startActivity(intent);
        toast(context, context.getString(R.string.toast_manage_storage_db));
        return false;
    }

    private static List<DatabaseBackupEntry> listLegacySharedBackups() {
        List<DatabaseBackupEntry> entries = new ArrayList<>();
        File[] files = getLegacySharedBackupRoot().listFiles();
        if (files == null) {
            return entries;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                entries.add(new DatabaseBackupEntry(file.getName(), getBackupDatabaseVersion(file), file, null, null));
            }
        }
        return entries;
    }

    private static List<DatabaseBackupEntry> listSharedDownloadBackups(Context context) {
        LinkedHashMap<String, DatabaseBackupEntry> entries = new LinkedHashMap<>();
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String[] projection = new String[]{
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.RELATIVE_PATH
        };
        String selection = MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ? AND "
                + MediaStore.Files.FileColumns.DISPLAY_NAME + " IN (?, ?, ?)";
        String[] selectionArgs = new String[]{getSharedBackupRelativeRoot() + "%", DB_NAME, DB_NAME_SHM, DB_NAME_WAL};

        try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, null)) {
            if (cursor == null) {
                return new ArrayList<>();
            }

            int displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
            int relativePathIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH);
            while (cursor.moveToNext()) {
                cursor.getString(displayNameIndex);
                String relativePath = cursor.getString(relativePathIndex);
                if (relativePath == null || !relativePath.startsWith(getSharedBackupRelativeRoot())) {
                    continue;
                }

                String folderName = getBackupFolderNameFromRelativePath(relativePath);
                if (folderName == null) {
                    continue;
                }

                if (!entries.containsKey(folderName)) {
                    entries.put(folderName, new DatabaseBackupEntry(
                            folderName,
                            getBackupDatabaseVersion(folderName),
                            null,
                            getSharedBackupRelativePath(folderName),
                            null
                    ));
                }
            }
        }

        return new ArrayList<>(entries.values());
    }

    private static List<DatabaseBackupEntry> listDocumentTreeBackups(Context context, Uri treeUri, int currentDatabaseVersion) {
        List<DatabaseBackupEntry> entries = new ArrayList<>();
        DocumentFile rootDirectory = resolveBackupTreeDirectory(context, treeUri);
        if (rootDirectory == null || !rootDirectory.isDirectory()) {
            return entries;
        }

        for (DocumentFile documentFile : rootDirectory.listFiles()) {
            if (!documentFile.isDirectory()) {
                continue;
            }

            int backupVersion = getBackupDatabaseVersion(documentFile.getName());
            if (isBackupVersionRestorable(backupVersion, currentDatabaseVersion)) {
                entries.add(new DatabaseBackupEntry(documentFile.getName(), backupVersion, null, null, documentFile));
            }
        }

        Collections.sort(entries, new Comparator<DatabaseBackupEntry>() {
            @Override
            public int compare(DatabaseBackupEntry left, DatabaseBackupEntry right) {
                return right.getName().compareTo(left.getName());
            }
        });
        return entries;
    }

    private static DocumentFile resolveBackupTreeDirectory(Context context, Uri treeUri) {
        DocumentFile rootDirectory = DocumentFile.fromTreeUri(context, treeUri);
        if (rootDirectory == null) {
            return null;
        }

        if (containsBackupDirectories(rootDirectory)) {
            return rootDirectory;
        }

        DocumentFile nestedBackupDirectory = rootDirectory.findFile(DB_BACKUP_FOLDER_NAME);
        if (nestedBackupDirectory != null && nestedBackupDirectory.isDirectory()) {
            return nestedBackupDirectory;
        }

        return rootDirectory;
    }

    private static boolean containsBackupDirectories(DocumentFile rootDirectory) {
        for (DocumentFile child : rootDirectory.listFiles()) {
            if (child.isDirectory() && getBackupDatabaseVersion(child.getName()) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static String getBackupFolderNameFromRelativePath(String relativePath) {
        String remainder = relativePath.substring(getSharedBackupRelativeRoot().length());
        int slashIndex = remainder.indexOf('/');
        if (slashIndex <= 0) {
            return null;
        }
        return remainder.substring(0, slashIndex);
    }

    public static void restoreDatabase(Context context, DatabaseBackupEntry backupEntry, Activity activity) {
        try {
            restoreDatabaseFiles(context, backupEntry);

            //Clear shared preferences and cache
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.clear();
            editor.apply();
            deleteImageCache(context);
            toast(context, context.getString(R.string.toast_restore_db));
            restartApplication(context);
        } catch (IOException e) {
            e.printStackTrace();
            toast(context, "Error restoring DB backup");
        }
    }

    public static void restoreDatabaseFiles(Context context, DatabaseBackupEntry backupEntry) throws IOException {
        if (StaticValues.db != null && StaticValues.db.isOpen()) {
            checkpointDatabase();
            StaticValues.db.close();
        }

        if (backupEntry.getDirectory() != null) {
            File backupDB = new File(backupEntry.getDirectory(), DB_NAME);
            if (!backupDB.isFile()) {
                throw new IOException("Backup is missing database file: " + backupDB);
            }
            copyDatabaseFamily(getBackupFiles(backupEntry.getDirectory()), getDatabaseFiles(context), true);
            return;
        }

        if (backupEntry.getDocumentDirectory() != null) {
            copyDatabaseFamilyFromDocumentTree(context, backupEntry, getDatabaseFiles(context), true);
            return;
        }

        copyDatabaseFamilyFromSharedDownloads(context, backupEntry, getDatabaseFiles(context), true);
    }

    private static File[] getDatabaseFiles(Context context) {
        File currentDB = context.getDatabasePath(DB_NAME);
        return new File[]{
                currentDB,
                new File(currentDB.getParentFile(), DB_NAME_SHM),
                new File(currentDB.getParentFile(), DB_NAME_WAL)
        };
    }

    private static File[] getBackupFiles(File backupDir) {
        return new File[]{
                new File(backupDir, DB_NAME),
                new File(backupDir, DB_NAME_SHM),
                new File(backupDir, DB_NAME_WAL)
        };
    }

    private static File getLegacySharedBackupRoot() {
        File downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadsRoot, MAIN_FOLDER_NAME + File.separator + DB_BACKUP_FOLDER_NAME);
    }

    private static String getSharedBackupRelativeRoot() {
        return Environment.DIRECTORY_DOWNLOADS + "/" + MAIN_FOLDER_NAME + "/" + DB_BACKUP_FOLDER_NAME + "/";
    }

    private static String getSharedImageExportRelativePath() {
        return Environment.DIRECTORY_DOWNLOADS + "/" + MAIN_FOLDER_NAME + "/Images/";
    }

    private static String getSharedTextExportRelativePath() {
        return Environment.DIRECTORY_DOWNLOADS + "/" + MAIN_FOLDER_NAME + "/Hex images/";
    }

    private static String getSharedBackupRelativePath(String backupName) {
        return getSharedBackupRelativeRoot() + backupName + "/";
    }

    private static void writeDatabaseBackupToSharedDownloads(Context context, DatabaseBackupEntry backupEntry, File[] sourceFiles) throws IOException {
        for (int i = 0; i < sourceFiles.length; i++) {
            File sourceFile = sourceFiles[i];
            if (!sourceFile.exists()) {
                continue;
            }
            writeFileToSharedDownloads(context, sourceFile, backupEntry.getRelativePath(), sourceFile.getName(), "application/octet-stream");
        }
    }

    private static SavedExportEntry writeFileToSharedDownloads(Context context, File sourceFile, String relativePath, String displayName, String mimeType) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        ContentValues values = new ContentValues();
        values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath);
        values.put(MediaStore.Files.FileColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(collection, values);
        if (uri == null) {
            throw new IOException("Unable to create backup file in shared downloads");
        }

        try (InputStream inputStream = new FileInputStream(sourceFile);
             OutputStream outputStream = resolver.openOutputStream(uri, "w")) {
            if (outputStream == null) {
                throw new IOException("Unable to open backup file for writing: " + uri);
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        } finally {
            ContentValues publishValues = new ContentValues();
            publishValues.put(MediaStore.Files.FileColumns.IS_PENDING, 0);
            resolver.update(uri, publishValues, null, null);
        }

        return new SavedExportEntry(displayName, uri, mimeType);
    }

    public static SavedExportEntry saveExportToConfiguredLocation(Context context, File sourceFile, String displayName, String mimeType, boolean textExport) throws IOException {
        Uri exportTreeUri = getStoredExportTreeUri();
        if (exportTreeUri != null) {
            return writeFileToDocumentTree(context, exportTreeUri, sourceFile, displayName, mimeType);
        }

        String relativePath = textExport ? getSharedTextExportRelativePath() : getSharedImageExportRelativePath();
        return writeFileToSharedDownloads(context, sourceFile, relativePath, displayName, mimeType);
    }

    private static SavedExportEntry writeFileToDocumentTree(Context context, Uri treeUri, File sourceFile, String displayName, String mimeType) throws IOException {
        DocumentFile rootDirectory = DocumentFile.fromTreeUri(context, treeUri);
        if (rootDirectory == null || !rootDirectory.isDirectory()) {
            throw new IOException("Unable to access export directory");
        }

        DocumentFile existingFile = rootDirectory.findFile(displayName);
        if (existingFile != null && !existingFile.delete()) {
            throw new IOException("Unable to replace existing export file");
        }

        DocumentFile documentFile = rootDirectory.createFile(mimeType, displayName);
        if (documentFile == null) {
            throw new IOException("Unable to create export file in selected folder");
        }

        try (InputStream inputStream = new FileInputStream(sourceFile);
             OutputStream outputStream = context.getContentResolver().openOutputStream(documentFile.getUri(), "w")) {
            if (outputStream == null) {
                throw new IOException("Unable to open export file for writing");
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }

        return new SavedExportEntry(displayName, documentFile.getUri(), mimeType);
    }

    private static void copyDatabaseFamilyFromSharedDownloads(Context context, DatabaseBackupEntry backupEntry, File[] destinationFiles, boolean deleteMissingSideFiles) throws IOException {
        for (int i = 0; i < destinationFiles.length; i++) {
            Uri sourceUri = findSharedBackupFileUri(context, backupEntry.getRelativePath(), destinationFiles[i].getName());
            if (sourceUri == null) {
                if (i == 0) {
                    throw new IOException("Backup is missing database file: " + destinationFiles[i].getName());
                }
                if (deleteMissingSideFiles && destinationFiles[i].exists() && !destinationFiles[i].delete()) {
                    throw new IOException("Unable to delete stale database side file: " + destinationFiles[i]);
                }
                continue;
            }
            copyUriToFile(context, sourceUri, destinationFiles[i]);
        }
    }

    private static void copyDatabaseFamilyFromDocumentTree(Context context, DatabaseBackupEntry backupEntry, File[] destinationFiles, boolean deleteMissingSideFiles) throws IOException {
        for (int i = 0; i < destinationFiles.length; i++) {
            DocumentFile sourceFile = backupEntry.getDocumentDirectory().findFile(destinationFiles[i].getName());
            if (sourceFile == null || !sourceFile.isFile()) {
                if (i == 0) {
                    throw new IOException("Backup is missing database file: " + destinationFiles[i].getName());
                }
                if (deleteMissingSideFiles && destinationFiles[i].exists() && !destinationFiles[i].delete()) {
                    throw new IOException("Unable to delete stale database side file: " + destinationFiles[i]);
                }
                continue;
            }
            copyUriToFile(context, sourceFile.getUri(), destinationFiles[i]);
        }
    }

    private static Uri findSharedBackupFileUri(Context context, String relativePath, String displayName) {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String[] projection = new String[]{MediaStore.Files.FileColumns._ID};
        String selection = MediaStore.Files.FileColumns.RELATIVE_PATH + "=? AND "
                + MediaStore.Files.FileColumns.DISPLAY_NAME + "=?";
        String[] selectionArgs = new String[]{relativePath, displayName};

        try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                return ContentUris.withAppendedId(collection, id);
            }
        }

        return null;
    }

    private static void copyUriToFile(Context context, Uri sourceUri, File destinationFile) throws IOException {
        File parentFile = destinationFile.getParentFile();
        if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs()) {
            throw new IOException("Unable to create directory: " + parentFile);
        }

        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
             OutputStream outputStream = new FileOutputStream(destinationFile)) {
            if (inputStream == null) {
                throw new IOException("Unable to open backup file for reading: " + sourceUri);
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
    }

    private static void copyDatabaseFamily(File[] sourceFiles, File[] destinationFiles, boolean deleteMissingSideFiles) throws IOException {
        for (int i = 0; i < sourceFiles.length; i++) {
            File sourceFile = sourceFiles[i];
            File destinationFile = destinationFiles[i];

            if (!sourceFile.exists()) {
                if (i > 0 && deleteMissingSideFiles && destinationFile.exists() && !destinationFile.delete()) {
                    throw new IOException("Unable to delete stale database side file: " + destinationFile);
                }
                continue;
            }

            copyFile(sourceFile, destinationFile);
        }
    }

    private static void copyFile(File sourceFile, File destinationFile) throws IOException {
        File parentFile = destinationFile.getParentFile();
        if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs()) {
            throw new IOException("Unable to create directory: " + parentFile);
        }

        try (InputStream inputStream = new FileInputStream(sourceFile);
             OutputStream outputStream = new FileOutputStream(destinationFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
    }

    public static void deleteImageCache(Context context) {
        //Deleting cache for the next version only
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);
        // Delete all files within the cache directory
        if (cacheDir != null && cacheDir.isDirectory()) {
            File[] cacheFiles = cacheDir.listFiles();
            if (cacheFiles != null) {
                for (File cacheFile : cacheFiles) {
                    cacheFile.delete();
                }
            }
        }
    }

    public static void restartApplication(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        // Required for API 34 and later
        // Ref: https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents
        mainIntent.setPackage(context.getPackageName());
        context.startActivity(mainIntent);
        Runtime.getRuntime().exit(0);
    }

    public static void showNotification(Context context, File downloadedFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", downloadedFile);
        intent.setDataAndType(fileUri, context.getContentResolver().getType(fileUri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(context.getResources().getString(R.string.notification_download_complete))
                .setContentText(downloadedFile.getName())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(notificationId++, builder.build());
        }
    }

    public static void showNotification(Context context, SavedExportEntry downloadedFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(downloadedFile.getUri(), downloadedFile.getMimeType());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(context.getResources().getString(R.string.notification_download_complete))
                .setContentText(downloadedFile.getDisplayName())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(notificationId++, builder.build());
        }
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static void frameGroupSorting() {
        List<GbcFrame> sortedFrameList = new ArrayList<>();

        List<GbcFrame> sortedFrameGroup = new ArrayList<>();

        // Sort each frame group by id and add it to the final list
        for (String key : frameGroupsNames.keySet()) {
            sortedFrameGroup.clear();
            for (GbcFrame gbcFrame : Utils.framesList) {
                String gbcFrameGroup = gbcFrame.getFrameId().replaceAll("^(\\D+).*", "$1");//To remove the numbers at the end
                if (gbcFrameGroup.equals(key)) {
                    sortedFrameGroup.add(gbcFrame);
                }
            }
            //Now sort the group by id
            Comparator<GbcFrame> comparator = new Comparator<GbcFrame>() {
                @Override
                public int compare(GbcFrame frame1, GbcFrame frame2) {
                    int titleComparison = frame1.getFrameId().compareTo(frame2.getFrameId());

                    return titleComparison;
                }
            };
            Collections.sort(sortedFrameGroup, comparator);
            sortedFrameList.addAll(sortedFrameGroup);
        }
        Utils.framesList = sortedFrameList;
    }

    public static void sortPalettes() {
        StaticValues.timesPalettesUsed = timesPaletteUsed();
        sortedPalettes.clear();
        List<GbcPalette> sortedFavs = new ArrayList<>();
        //First add the favorites on top
        for (GbcPalette palette : gbcPalettesList) {
            if (palette.isFavorite()) {
                sortedFavs.add(palette);
            }
        }
        if (sortPalettesByUsage) {
            Collections.sort(sortedFavs, new Comparator<GbcPalette>() {
                @Override
                public int compare(GbcPalette o1, GbcPalette o2) {
                    Integer timesUsed1 = timesPalettesUsed.get(o1.getPaletteId());
                    Integer timesUsed2 = timesPalettesUsed.get(o2.getPaletteId());

                    if (timesUsed1 == null) timesUsed1 = 0;
                    if (timesUsed2 == null) timesUsed2 = 0;

                    return timesUsed2.compareTo(timesUsed1);
                }
            });
        }
        sortedPalettes.addAll(sortedFavs);
        //Then add the rest of the palettes
        List<GbcPalette> sortedNoFavs = new ArrayList<>();

        for (GbcPalette palette : gbcPalettesList) {
            if (!palette.isFavorite()) {
                sortedNoFavs.add(palette);
            }
        }
        if (sortPalettesByUsage) {
            Collections.sort(sortedNoFavs, new Comparator<GbcPalette>() {
                @Override
                public int compare(GbcPalette o1, GbcPalette o2) {
                    Integer timesUsed1 = timesPalettesUsed.get(o1.getPaletteId());
                    Integer timesUsed2 = timesPalettesUsed.get(o2.getPaletteId());

                    if (timesUsed1 == null) timesUsed1 = 0;
                    if (timesUsed2 == null) timesUsed2 = 0;

                    return timesUsed2.compareTo(timesUsed1);
                }
            });
        }

        sortedPalettes.addAll(sortedNoFavs);
    }

    public static HashMap<String, Integer> timesPaletteUsed() {

        HashMap<String, Integer> timesPaletteUsed = new HashMap<>();
        for (GbcImage gbcImage : gbcImagesList) {
            String paletteName = gbcImage.getPaletteId();
            if (timesPaletteUsed.containsKey(paletteName)) {
                timesPaletteUsed.put(paletteName, timesPaletteUsed.get(paletteName) + 1);
            } else {
                timesPaletteUsed.put(paletteName, 1);
            }
            if (gbcImage.isLockFrame()) { //Only add the frame palette to the used counter if it's locked
                paletteName = gbcImage.getFramePaletteId();
                if (timesPaletteUsed.containsKey(paletteName)) {
                    timesPaletteUsed.put(paletteName, timesPaletteUsed.get(paletteName) + 1);
                } else {
                    timesPaletteUsed.put(paletteName, 1);
                }
            }
        }

        return timesPaletteUsed;
    }
}

