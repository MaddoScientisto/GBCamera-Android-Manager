package com.mraulio.gbcameramanager.ui.usbserial;

import android.content.Context;
import android.graphics.Bitmap;
import com.mraulio.gbcameramanager.utils.AppTask;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.gameboycameralib.codecs.ImageCodec;
import com.mraulio.gbcameramanager.model.GbcImage;
import com.mraulio.gbcameramanager.model.ImageData;
import com.mraulio.gbcameramanager.ui.gallery.CustomGridViewAdapterImage;
import com.mraulio.gbcameramanager.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public class PicNRecCommands {
    public static final int DEFAULT_BAUD_RATE = 1000000;
    public static final int FIRST_IMAGE_SLOT = 1;
    public static final int MAX_SUPPORTED_IMAGE_INDEX = 18720;
    private static final int BLOCK_SIZE = 64;
    private static final int IMAGE_SIZE = 0x0E00;
    private static final int IMAGE_BLOCK_COUNT = IMAGE_SIZE / BLOCK_SIZE;
    private static final int METADATA_BLOCK_COUNT = 0x0A00 / BLOCK_SIZE;
    private static final int LAST_ADDRESS_SCAN_BYTES = 2500;
    private static final int BLOCK_TIMEOUT_MS = 500;
    private static final int RETRY_COUNT = 3;
    private static final int RETRY_DELAY_MS = 200;
    private static final byte CLEAR_METADATA_ACK = 0x31;
    private static final byte[] BLACK_TILE = new byte[16];

    static {
        Arrays.fill(BLACK_TILE, (byte) 0xFF);
    }

    public static void flushInput(UsbSerialPort port) {
        byte[] buffer = new byte[BLOCK_SIZE];
        long deadline = System.currentTimeMillis() + 300;
        while (System.currentTimeMillis() < deadline) {
            try {
                int length = port.read(buffer, 40);
                if (length <= 0) {
                    return;
                }
            } catch (Exception e) {
                return;
            }
        }
    }

    private static void writeCommand(UsbSerialPort port, String command) throws Exception {
        port.write(command.getBytes(StandardCharsets.US_ASCII), BLOCK_TIMEOUT_MS);
    }

    private static void setNumber(UsbSerialPort port, int value) throws Exception {
        int safeValue = Math.max(0, value);
        writeCommand(port, "A" + Integer.toHexString(safeValue) + "\0");
    }

    private static void ensureIdle(UsbSerialPort port) {
        try {
            writeCommand(port, "0");
        } catch (Exception e) {
            e.printStackTrace();
        }
        flushInput(port);
    }

    private static byte[] readBlockSequence(UsbSerialPort port, int blockCount) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(blockCount * BLOCK_SIZE);
        for (int blockIndex = 0; blockIndex < blockCount; blockIndex++) {
            byte[] block = readExactly(port, BLOCK_SIZE, BLOCK_TIMEOUT_MS);
            outputStream.write(block);

            if (blockIndex < blockCount - 1) {
                writeCommand(port, "1");
            }
        }
        return outputStream.toByteArray();
    }

    private static byte[] readExactly(UsbSerialPort port, int length, int timeout) throws Exception {
        byte[] output = new byte[length];
        int offset = 0;
        long deadline = System.currentTimeMillis() + timeout;

        while (offset < length && System.currentTimeMillis() < deadline) {
            byte[] buffer = new byte[length - offset];
            int read = port.read(buffer, Math.max(1, (int) (deadline - System.currentTimeMillis())));
            if (read > 0) {
                System.arraycopy(buffer, 0, output, offset, read);
                offset += read;
            }
        }

        if (offset != length) {
            throw new IllegalStateException("Timed out waiting for " + length + " bytes; received " + offset);
        }

        return output;
    }

    public static int getEffectiveLastImageIndex(int reportedLastImageNumber) {
        return Math.min(Math.max(0, reportedLastImageNumber - 1), MAX_SUPPORTED_IMAGE_INDEX);
    }

    public static int readReportedLastImageNumber(UsbSerialPort port) throws Exception {
        ensureIdle(port);
        flushInput(port);
        setNumber(port, 0);
        writeCommand(port, "R");

        byte[] metadata = readBlockSequence(port, METADATA_BLOCK_COUNT);
        ensureIdle(port);

        int lastAddress = 0;
        for (int index = 0; index < Math.min(metadata.length, LAST_ADDRESS_SCAN_BYTES); index++) {
            int value = metadata[index] & 0xFF;
            if (value == 0x00) lastAddress += 8;
            else if (value == 0x01) lastAddress += 7;
            else if (value == 0x03) lastAddress += 6;
            else if (value == 0x07) lastAddress += 5;
            else if (value == 0x0F) lastAddress += 4;
            else if (value == 0x1F) lastAddress += 3;
            else if (value == 0x3F) lastAddress += 2;
            else if (value == 0x7F) lastAddress += 1;
        }
        return Math.min(Math.max(0, lastAddress), MAX_SUPPORTED_IMAGE_INDEX + 1);
    }

    public static int readLastImageNumber(UsbSerialPort port) throws Exception {
        return getEffectiveLastImageIndex(readReportedLastImageNumber(port));
    }

    public static void clearMemory(UsbSerialPort port) throws Exception {
        flushInput(port);
        writeCommand(port, "k");
        byte[] acknowledgement = readExactly(port, 1, BLOCK_TIMEOUT_MS);
        if (acknowledgement[0] != CLEAR_METADATA_ACK) {
            throw new IllegalStateException("Unexpected clear acknowledgement: 0x" + Integer.toHexString(acknowledgement[0] & 0xFF));
        }
        flushInput(port);
    }

    public static byte[] readImage(UsbSerialPort port, int imageNumber) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= RETRY_COUNT; attempt++) {
            try {
                ensureIdle(port);
                flushInput(port);
                setNumber(port, imageNumber);
                writeCommand(port, "R");
                byte[] image = readBlockSequence(port, IMAGE_BLOCK_COUNT);
                ensureIdle(port);
                return image;
            } catch (Exception e) {
                lastException = e;
                ensureIdle(port);
                if (attempt < RETRY_COUNT) {
                    Thread.sleep(RETRY_DELAY_MS);
                }
            }
        }
        throw lastException;
    }

    private static boolean isImportableImage(byte[] image) {
        for (int offset = 0; offset + 16 <= image.length; offset += 16) {
            boolean allWhite = true;
            boolean allBlack = true;
            for (int index = 0; index < 16; index++) {
                byte value = image[offset + index];
                allWhite &= value == 0x00;
                allBlack &= value == (byte) 0xFF;
            }
            if (!allWhite && !allBlack) {
                return true;
            }
        }
        return false;
    }

    private static byte[] padToFullCameraImage(byte[] picNRecImage) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(5760);
        for (int tile = 0; tile < 40; tile++) {
            outputStream.write(BLACK_TILE);
        }

        for (int row = 0; row < 14; row++) {
            outputStream.write(BLACK_TILE);
            outputStream.write(BLACK_TILE);
            outputStream.write(picNRecImage, row * 16 * 16, 16 * 16);
            outputStream.write(BLACK_TILE);
            outputStream.write(BLACK_TILE);
        }

        for (int tile = 0; tile < 40; tile++) {
            outputStream.write(BLACK_TILE);
        }
        return outputStream.toByteArray();
    }

    public static Bitmap decodePicNRecImage(Context context, byte[] imageBytes) throws Exception {
        byte[] fullImageBytes = padToFullCameraImage(imageBytes);
        ImageCodec imageCodec = new ImageCodec(160, 144);
        return imageCodec.decodeWithPalette(Utils.hashPalettes.get(new GbcImage().getPaletteId()).getPaletteColorsInt(), fullImageBytes, false);
    }

    private static void addImage(Context context, byte[] imageBytes, int imageNumber) throws Exception {
        byte[] fullImageBytes = padToFullCameraImage(imageBytes);
        GbcImage gbcImage = new GbcImage();
        gbcImage.setImageBytes(fullImageBytes);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(fullImageBytes);
        String hashHex = Utils.bytesToHex(hash);
        gbcImage.setHashCode(hashHex);
        gbcImage.setName(imageNumber + "-picnrec");

        ImageData imageData = new ImageData();
        imageData.setImageId(hashHex);
        imageData.setData(fullImageBytes);

        ImageCodec imageCodec = new ImageCodec(160, 144);
        Bitmap bitmap = imageCodec.decodeWithPalette(Utils.hashPalettes.get(gbcImage.getPaletteId()).getPaletteColorsInt(), fullImageBytes, false);
        UsbSerialFragment.finalListImages.add(gbcImage);
        UsbSerialFragment.finalListBitmaps.add(bitmap);
    }

    public interface DeviceInfoListener {
        void onDeviceInfo(int reportedLastImageNumber);
        void onDeviceInfoError(Exception exception);
    }

    public interface ClearMemoryListener {
        void onCleared();
        void onClearError(Exception exception);
    }

    public static class DetectPicNRecAsyncTask extends AppTask<Void, Void, Exception> {
        private final UsbSerialPort port;
        private final DeviceInfoListener listener;
        private int reportedLastImageNumber;

        public DetectPicNRecAsyncTask(UsbSerialPort port, DeviceInfoListener listener) {
            this.port = port;
            this.listener = listener;
        }

        @Override
        protected Exception doInBackground(Void... voids) {
            try {
                if (port == null) {
                    throw new IllegalStateException("No USB serial device found");
                }
                reportedLastImageNumber = readReportedLastImageNumber(port);
            } catch (Exception e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception exception) {
            if (exception != null) {
                listener.onDeviceInfoError(exception);
                return;
            }
            listener.onDeviceInfo(reportedLastImageNumber);
        }
    }

    public static class PreviewPicNRecAsyncTask extends AppTask<Void, Void, Exception> {
        private final UsbSerialPort port;
        private final Context context;
        private final int imageNumber;
        private final ImageView imageView;
        private final TextView statusView;
        private Bitmap bitmap;

        public PreviewPicNRecAsyncTask(UsbSerialPort port, Context context, int imageNumber, ImageView imageView, TextView statusView) {
            this.port = port;
            this.context = context;
            this.imageNumber = imageNumber;
            this.imageView = imageView;
            this.statusView = statusView;
        }

        @Override
        protected void onPreExecute() {
            statusView.setText(context.getString(R.string.picnrec_previewing) + imageNumber + "...");
        }

        @Override
        protected Exception doInBackground(Void... voids) {
            try {
                if (port == null) {
                    throw new IllegalStateException("No USB serial device found");
                }
                bitmap = decodePicNRecImage(context, readImage(port, imageNumber));
            } catch (Exception e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception exception) {
            if (exception != null) {
                statusView.setText(context.getString(R.string.picnrec_preview_failed) + exception.toString());
                return;
            }
            imageView.setImageBitmap(bitmap);
            statusView.setText(context.getString(R.string.picnrec_preview_loaded) + imageNumber + ".");
        }
    }

    public static class ClearPicNRecAsyncTask extends AppTask<Void, Void, Exception> {
        private final UsbSerialPort port;
        private final ClearMemoryListener listener;

        public ClearPicNRecAsyncTask(UsbSerialPort port, ClearMemoryListener listener) {
            this.port = port;
            this.listener = listener;
        }

        @Override
        protected Exception doInBackground(Void... voids) {
            try {
                if (port == null) {
                    throw new IllegalStateException("No USB serial device found");
                }
                clearMemory(port);
            } catch (Exception e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception exception) {
            if (exception != null) {
                listener.onClearError(exception);
                return;
            }
            listener.onCleared();
        }
    }

    public static class ReadPicNRecAsyncTask extends AppTask<Void, Integer, Exception> {
        private final UsbSerialPort port;
        private final Context context;
        private final TextView tv;
        private final int startImageNumber;
        private final int endImageNumber;
        private int lastImageNumber;

        public ReadPicNRecAsyncTask(UsbSerialPort port, Context context, TextView tv) {
            this(port, context, tv, FIRST_IMAGE_SLOT, 0);
        }

        public ReadPicNRecAsyncTask(UsbSerialPort port, Context context, TextView tv, int startImageNumber, int endImageNumber) {
            this.port = port;
            this.context = context;
            this.tv = tv;
            this.startImageNumber = startImageNumber;
            this.endImageNumber = endImageNumber;
        }

        @Override
        protected Exception doInBackground(Void... voids) {
            try {
                if (port == null) {
                    throw new IllegalStateException("No USB serial device found");
                }

                publishProgress(0, 0, 0);
                lastImageNumber = endImageNumber > 0 ? endImageNumber : readLastImageNumber(port);
                publishProgress(0, 1);

                UsbSerialFragment.finalListImages.clear();
                UsbSerialFragment.finalListBitmaps.clear();
                UsbSerialFragment.latestFile = null;

                int startImage = Math.max(FIRST_IMAGE_SLOT, startImageNumber);
                int totalImages = lastImageNumber - startImage + 1;
                for (int imageNumber = startImage; imageNumber <= lastImageNumber; imageNumber++) {
                    byte[] image = readImage(port, imageNumber);
                    if (isImportableImage(image)) {
                        addImage(context, image, imageNumber);
                    }
                    int progress = totalImages <= 0 ? 100 : (imageNumber - startImage + 1) * 100 / totalImages;
                    publishProgress(progress, 2, imageNumber);
                }
                ensureIdle(port);
            } catch (Exception e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            int progress = values[0];
            int state = values[1];
            if (state == 0) {
                tv.setText(context.getString(R.string.picnrec_detecting));
            } else if (state == 1) {
                tv.setText(context.getString(R.string.picnrec_importing_range, Math.max(FIRST_IMAGE_SLOT, startImageNumber), lastImageNumber));
            } else {
                tv.setText(context.getString(R.string.picnrec_importing_range, Math.max(FIRST_IMAGE_SLOT, startImageNumber), lastImageNumber) + "\n" + progress + "%");
            }
        }

        @Override
        protected void onPostExecute(Exception exception) {
            if (exception != null) {
                tv.setText(context.getString(R.string.picnrec_error) + exception.toString());
                return;
            }

            if (UsbSerialFragment.finalListImages.isEmpty()) {
                tv.setText(context.getString(R.string.picnrec_no_images));
                return;
            }

            tv.setText(context.getString(R.string.picnrec_import_ready) + UsbSerialFragment.finalListImages.size());
            UsbSerialFragment.btnAddImages.setVisibility(View.VISIBLE);
            UsbSerialFragment.btnDelSav.setVisibility(View.GONE);
            UsbSerialFragment.layoutCb.setVisibility(View.GONE);
            UsbSerialFragment.gridView.setAdapter(new CustomGridViewAdapterImage(context, R.layout.row_items, UsbSerialFragment.finalListImages, UsbSerialFragment.finalListBitmaps, true, true, false, null));
        }
    }
}