package com.mraulio.gbcameramanager.ui.gbstorage;

import android.graphics.Bitmap;

import com.mraulio.gbcameramanager.model.GbcImage;

public class RemoteGbStorageImage {
    public final String id;
    public final GbcImage image;
    public Bitmap previewBitmap;

    public RemoteGbStorageImage(String id, GbcImage image) {
        this.id = id;
        this.image = image;
    }
}
