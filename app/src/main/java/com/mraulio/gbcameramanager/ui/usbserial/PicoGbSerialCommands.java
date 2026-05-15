package com.mraulio.gbcameramanager.ui.usbserial;

import android.content.Context;
import android.graphics.Bitmap;
import com.mraulio.gbcameramanager.utils.AppTask;
import android.view.View;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.gameboycameralib.codecs.ImageCodec;
import com.mraulio.gbcameramanager.model.GbcImage;
import com.mraulio.gbcameramanager.ui.gallery.CustomGridViewAdapterImage;
import com.mraulio.gbcameramanager.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PicoGbSerialCommands {
    public static final int DEFAULT_BAUD_RATE = 115200;
    public static final int PICO_VENDOR_ID = 0xCAFE;
    public static final int PICO_PRODUCT_ID = 0x4001;

    private static final byte FRAME_VERSION = 1;
    private static final byte FRAME_HELLO = 0x01;
    private static final byte FRAME_STATUS = 0x02;
    private static final byte FRAME_JOB = 0x10;
    private static final byte FRAME_ACK = 0x20;
    private static final byte FRAME_ERROR = 0x7F;

    private static final byte COMMAND_INIT = 0x01;
    private static final byte COMMAND_PRINT = 0x02;
    private static final byte COMMAND_DATA = 0x04;
    private static final byte COMMAND_TRANSFER = 0x10;

    private static final int FRAME_HEADER_LENGTH = 12;
    private static final int FRAME_CRC_LENGTH = 4;
    private static final int COMMAND_TIMEOUT_MS = 3000;
    private static final int CAPTURE_IDLE_TIMEOUT_MS = 250;
    private static final int TILE_BYTE_COUNT = 16;
    private static final int PRINTER_WIDTH_TILES = 20;
    private static final int PRINTER_HEIGHT_TILES = 18;
    private static final int CAMERA_WIDTH_TILES = 16;
    private static final byte[] MAGIC = new byte[]{'P', 'G', 'B', 'S'};
    private static final byte[] WHITE_TILE = new byte[TILE_BYTE_COUNT];
    private static final byte[] BLACK_TILE = new byte[TILE_BYTE_COUNT];

    static {
        Arrays.fill(BLACK_TILE, (byte) 0xFF);
    }

    public static void flushInput(UsbSerialPort port) {
        byte[] buffer = new byte[256];
        long deadline = System.currentTimeMillis() + 300;
        while (System.currentTimeMillis() < deadline) {
            try {
                int length = port.read(buffer, 40);
                if (length <= 0) return;
            } catch (Exception e) {
                return;
            }
        }
    }

    public static DeviceStatus readStatus(UsbSerialPort port) throws Exception {
        Frame frame = sendCommandForFrame(port, "STATUS", FRAME_STATUS);
        if (frame.payload.length < 16) throw new IllegalStateException("Status frame is too short");
        return new DeviceStatus(
                readUInt32(frame.payload, 0),
                readUInt32(frame.payload, 4),
                readUInt32(frame.payload, 8),
                frame.payload[12] != 0,
                frame.payload[13] != 0,
                frame.payload[14] != 0);
    }

    private static void clearDevice(UsbSerialPort port) throws Exception {
        sendCommandForFrame(port, "CLEAR", FRAME_ACK);
    }

    private static Frame getNextFrame(UsbSerialPort port) throws Exception {
        port.write("GET_NEXT\n".getBytes(StandardCharsets.US_ASCII), COMMAND_TIMEOUT_MS);
        while (true) {
            Frame frame = readFrame(port, COMMAND_TIMEOUT_MS);
            throwIfError(frame);
            if (frame.type == FRAME_HELLO) continue;
            return frame;
        }
    }

    private static Frame sendCommandForFrame(UsbSerialPort port, String command, byte expectedType) throws Exception {
        port.write((command + "\n").getBytes(StandardCharsets.US_ASCII), COMMAND_TIMEOUT_MS);
        while (true) {
            Frame frame = readFrame(port, COMMAND_TIMEOUT_MS);
            throwIfError(frame);
            if (frame.type == FRAME_HELLO) continue;
            if (frame.type == expectedType) return frame;
        }
    }

    private static Frame tryReadFrame(UsbSerialPort port, int timeoutMs) throws Exception {
        if (!tryReadMagic(port, timeoutMs)) return null;
        return readFrameAfterMagic(port, timeoutMs);
    }

    private static Frame readFrame(UsbSerialPort port, int timeoutMs) throws Exception {
        if (!tryReadMagic(port, timeoutMs)) throw new IllegalStateException("Timed out waiting for Pico GB Serial frame");
        return readFrameAfterMagic(port, timeoutMs);
    }

    private static Frame readFrameAfterMagic(UsbSerialPort port, int timeoutMs) throws Exception {
        byte[] headerRest = readExactly(port, FRAME_HEADER_LENGTH - MAGIC.length, timeoutMs);
        if (headerRest[0] != FRAME_VERSION) {
            throw new IllegalStateException("Unsupported Pico GB Serial frame version " + headerRest[0]);
        }
        byte type = headerRest[1];
        int flags = readUInt16(headerRest, 2);
        long length = readUInt32(headerRest, 4);
        if (length > Integer.MAX_VALUE) throw new IllegalStateException("Frame payload is too large: " + length);
        byte[] payload = length == 0 ? new byte[0] : readExactly(port, (int) length, timeoutMs);
        byte[] crcBytes = readExactly(port, FRAME_CRC_LENGTH, timeoutMs);
        long expectedCrc = readUInt32(crcBytes, 0);
        long actualCrc = crc32(payload);
        if (expectedCrc != actualCrc) {
            throw new IllegalStateException("Frame CRC mismatch. Expected 0x" + Long.toHexString(expectedCrc) + ", got 0x" + Long.toHexString(actualCrc));
        }
        return new Frame(type, flags, payload);
    }

    private static boolean tryReadMagic(UsbSerialPort port, int timeoutMs) throws Exception {
        int matched = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (matched < MAGIC.length) {
            if (System.currentTimeMillis() >= deadline) return false;
            byte[] data = new byte[1];
            int read = port.read(data, Math.max(1, (int) (deadline - System.currentTimeMillis())));
            if (read <= 0) return false;
            if (data[0] == MAGIC[matched]) matched++;
            else matched = data[0] == MAGIC[0] ? 1 : 0;
        }
        return true;
    }

    private static byte[] readExactly(UsbSerialPort port, int length, int timeoutMs) throws Exception {
        byte[] output = new byte[length];
        int offset = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (offset < length && System.currentTimeMillis() < deadline) {
            byte[] buffer = new byte[length - offset];
            int read = port.read(buffer, Math.max(1, (int) (deadline - System.currentTimeMillis())));
            if (read > 0) {
                System.arraycopy(buffer, 0, output, offset, read);
                offset += read;
            }
        }
        if (offset != length) throw new IllegalStateException("Timed out waiting for " + length + " bytes; received " + offset);
        return output;
    }

    private static void throwIfError(Frame frame) {
        if (frame.type == FRAME_ERROR) {
            throw new IllegalStateException("Pico GB Serial error: " + new String(frame.payload, StandardCharsets.US_ASCII));
        }
    }

    public static DecodedCapture decodeCapture(Context context, byte[] payload, int captureNumber) throws Exception {
        List<GbcImage> images = new ArrayList<>();
        List<Bitmap> bitmaps = new ArrayList<>();
        byte[] tileBuffer = new byte[1024 * 1024];
        int stripStart = 0;
        int pointer = 0;
        int index = 0;

        while (index < payload.length) {
            byte command = payload[index++];
            switch (command) {
                case COMMAND_INIT:
                    break;
                case COMMAND_PRINT: {
                    if (index + 2 > payload.length) {
                        index = payload.length;
                        break;
                    }
                    int length = (payload[index++] & 0xFF) | ((payload[index++] & 0xFF) << 8);
                    if (length != 4 || index + 4 > payload.length) {
                        index = payload.length;
                        break;
                    }
                    index += 4;
                    addTileImage(context, images, bitmaps, Arrays.copyOfRange(tileBuffer, stripStart, pointer), PRINTER_WIDTH_TILES, captureNumber);
                    stripStart = pointer;
                    break;
                }
                case COMMAND_TRANSFER: {
                    if (index + 2 > payload.length) {
                        index = payload.length;
                        break;
                    }
                    int length = (payload[index++] & 0xFF) | ((payload[index++] & 0xFF) << 8);
                    int available = Math.min(length, payload.length - index);
                    int before = pointer;
                    pointer = copyUncompressed(payload, index, available, tileBuffer, pointer);
                    index += available;
                    addTileImage(context, images, bitmaps, Arrays.copyOfRange(tileBuffer, before, pointer), CAMERA_WIDTH_TILES, captureNumber);
                    stripStart = pointer;
                    if (available < length) index = payload.length;
                    break;
                }
                case COMMAND_DATA: {
                    if (index + 3 > payload.length) {
                        index = payload.length;
                        break;
                    }
                    boolean compressed = payload[index++] != 0;
                    int length = (payload[index++] & 0xFF) | ((payload[index++] & 0xFF) << 8);
                    int available = Math.min(length, payload.length - index);
                    pointer = compressed
                            ? decodeCompressed(payload, index, available, tileBuffer, pointer)
                            : copyUncompressed(payload, index, available, tileBuffer, pointer);
                    index += available;
                    if (available < length) index = payload.length;
                    break;
                }
                default:
                    index = payload.length;
                    break;
            }
        }
        if (pointer > stripStart) {
            addTileImage(context, images, bitmaps, Arrays.copyOfRange(tileBuffer, stripStart, pointer), PRINTER_WIDTH_TILES, captureNumber);
        }
        return new DecodedCapture(images, bitmaps);
    }

    private static int copyUncompressed(byte[] source, int sourceIndex, int length, byte[] destination, int destinationIndex) {
        int take = Math.min(length, Math.max(0, destination.length - destinationIndex));
        if (take > 0) System.arraycopy(source, sourceIndex, destination, destinationIndex, take);
        return destinationIndex + take;
    }

    private static int decodeCompressed(byte[] source, int sourceIndex, int length, byte[] destination, int destinationIndex) {
        int stop = sourceIndex + length;
        while (sourceIndex < stop) {
            int tag = source[sourceIndex++] & 0xFF;
            if ((tag & 0x80) != 0) {
                if (sourceIndex >= stop) break;
                byte value = source[sourceIndex++];
                int count = (tag & 0x7F) + 2;
                int take = Math.min(count, Math.max(0, destination.length - destinationIndex));
                if (take > 0) {
                    Arrays.fill(destination, destinationIndex, destinationIndex + take, value);
                    destinationIndex += take;
                }
                if (take < count) break;
            } else {
                int count = tag + 1;
                int available = Math.min(count, stop - sourceIndex);
                int take = Math.min(available, Math.max(0, destination.length - destinationIndex));
                if (take > 0) {
                    System.arraycopy(source, sourceIndex, destination, destinationIndex, take);
                    destinationIndex += take;
                }
                sourceIndex += available;
                if (take < count) break;
            }
        }
        return destinationIndex;
    }

    private static void addTileImage(Context context, List<GbcImage> images, List<Bitmap> bitmaps, byte[] tiles, int widthTiles, int captureNumber) throws Exception {
        int tileCount = tiles.length / TILE_BYTE_COUNT;
        if (tileCount == 0) return;
        int heightTiles = (tileCount + widthTiles - 1) / widthTiles;
        boolean transferCapture = widthTiles == CAMERA_WIDTH_TILES;
        byte[] imageBytes = transferCapture ? padTransferTilesToPrinterFrame(tiles, heightTiles) : padTilesToAppWidth(tiles, widthTiles, heightTiles);
        int height = (transferCapture ? PRINTER_HEIGHT_TILES : heightTiles) * 8;
        GbcImage gbcImage = new GbcImage();
        gbcImage.setImageBytes(imageBytes);
        String hashHex = Utils.bytesToHex(MessageDigest.getInstance("SHA-256").digest(imageBytes));
        gbcImage.setHashCode(hashHex);
        gbcImage.setName("pico-gb-serial-" + captureNumber + "-" + (images.size() + 1));
        ImageCodec imageCodec = new ImageCodec(160, height);
        Bitmap bitmap = imageCodec.decodeWithPalette(Utils.hashPalettes.get(gbcImage.getPaletteId()).getPaletteColorsInt(), imageBytes, false);
        images.add(gbcImage);
        bitmaps.add(bitmap);
    }

    private static byte[] padTransferTilesToPrinterFrame(byte[] tiles, int heightTiles) {
        byte[] imageBytes = new byte[PRINTER_WIDTH_TILES * PRINTER_HEIGHT_TILES * TILE_BYTE_COUNT];
        for (int tileIndex = 0; tileIndex < PRINTER_WIDTH_TILES * PRINTER_HEIGHT_TILES; tileIndex++) {
            System.arraycopy(BLACK_TILE, 0, imageBytes, tileIndex * TILE_BYTE_COUNT, TILE_BYTE_COUNT);
        }
        int copiedHeightTiles = Math.min(heightTiles, PRINTER_HEIGHT_TILES - 4);
        for (int row = 0; row < copiedHeightTiles; row++) {
            for (int column = 0; column < CAMERA_WIDTH_TILES; column++) {
                int sourceOffset = (row * CAMERA_WIDTH_TILES + column) * TILE_BYTE_COUNT;
                int destinationOffset = ((row + 2) * PRINTER_WIDTH_TILES + column + 2) * TILE_BYTE_COUNT;
                if (sourceOffset < tiles.length) {
                    System.arraycopy(tiles, sourceOffset, imageBytes, destinationOffset, Math.min(TILE_BYTE_COUNT, tiles.length - sourceOffset));
                }
            }
        }
        drawBlackFrame(imageBytes, 15, 15, 145, 129);
        return imageBytes;
    }

    private static void drawBlackFrame(byte[] imageBytes, int left, int top, int right, int bottom) {
        for (int x = left; x <= right; x++) {
            setBlackPixel(imageBytes, x, top);
            setBlackPixel(imageBytes, x, bottom);
        }
        for (int y = top; y <= bottom; y++) {
            setBlackPixel(imageBytes, left, y);
            setBlackPixel(imageBytes, right, y);
        }
    }

    private static void setBlackPixel(byte[] imageBytes, int x, int y) {
        if (x < 0 || x >= PRINTER_WIDTH_TILES * 8 || y < 0 || y >= PRINTER_HEIGHT_TILES * 8) return;
        int tileColumn = x / 8;
        int tileRow = y / 8;
        int pixelRow = y % 8;
        int bit = 7 - (x % 8);
        int offset = (tileRow * PRINTER_WIDTH_TILES + tileColumn) * TILE_BYTE_COUNT + pixelRow * 2;
        imageBytes[offset] |= (byte) (1 << bit);
        imageBytes[offset + 1] |= (byte) (1 << bit);
    }

    private static byte[] padTilesToAppWidth(byte[] tiles, int widthTiles, int heightTiles) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(PRINTER_WIDTH_TILES * heightTiles * TILE_BYTE_COUNT);
        int tileIndex = 0;
        for (int row = 0; row < heightTiles; row++) {
            if (widthTiles == CAMERA_WIDTH_TILES) {
                outputStream.write(WHITE_TILE);
                outputStream.write(WHITE_TILE);
            }
            for (int column = 0; column < widthTiles; column++) {
                int offset = tileIndex * TILE_BYTE_COUNT;
                if (offset < tiles.length) {
                    int remaining = tiles.length - offset;
                    outputStream.write(tiles, offset, Math.min(TILE_BYTE_COUNT, remaining));
                    if (remaining < TILE_BYTE_COUNT) outputStream.write(new byte[TILE_BYTE_COUNT - remaining]);
                } else {
                    outputStream.write(WHITE_TILE);
                }
                tileIndex++;
            }
            if (widthTiles == CAMERA_WIDTH_TILES) {
                outputStream.write(WHITE_TILE);
                outputStream.write(WHITE_TILE);
            }
        }
        return outputStream.toByteArray();
    }

    private static int readUInt16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long readUInt32(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF)
                | (((long) data[offset + 1] & 0xFF) << 8)
                | (((long) data[offset + 2] & 0xFF) << 16)
                | (((long) data[offset + 3] & 0xFF) << 24);
    }

    private static long crc32(byte[] data) {
        long crc = 0xFFFFFFFFL;
        for (byte datum : data) {
            crc ^= datum & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                long mask = -(crc & 1L);
                crc = (crc >>> 1) ^ (0xEDB88320L & mask);
            }
        }
        return (~crc) & 0xFFFFFFFFL;
    }

    private static class Frame {
        final byte type;
        final int flags;
        final byte[] payload;

        Frame(byte type, int flags, byte[] payload) {
            this.type = type;
            this.flags = flags;
            this.payload = payload;
        }
    }

    public static class StreamParser {
        private byte[] buffer = new byte[0];

        public List<byte[]> feed(byte[] data) {
            byte[] combined = new byte[buffer.length + data.length];
            System.arraycopy(buffer, 0, combined, 0, buffer.length);
            System.arraycopy(data, 0, combined, buffer.length, data.length);
            buffer = combined;

            List<byte[]> jobs = new ArrayList<>();
            while (true) {
                int magicOffset = findMagic(buffer);
                if (magicOffset < 0) {
                    keepPossibleMagicPrefix();
                    return jobs;
                }
                if (magicOffset > 0) {
                    buffer = Arrays.copyOfRange(buffer, magicOffset, buffer.length);
                }
                if (buffer.length < FRAME_HEADER_LENGTH) {
                    return jobs;
                }
                if (buffer[4] != FRAME_VERSION) {
                    buffer = Arrays.copyOfRange(buffer, 1, buffer.length);
                    continue;
                }
                byte type = buffer[5];
                long length = readUInt32(buffer, 8);
                if (length > Integer.MAX_VALUE) {
                    buffer = Arrays.copyOfRange(buffer, 1, buffer.length);
                    continue;
                }
                int frameLength = FRAME_HEADER_LENGTH + (int) length + FRAME_CRC_LENGTH;
                if (buffer.length < frameLength) {
                    return jobs;
                }
                byte[] payload = Arrays.copyOfRange(buffer, FRAME_HEADER_LENGTH, FRAME_HEADER_LENGTH + (int) length);
                long expectedCrc = readUInt32(buffer, FRAME_HEADER_LENGTH + (int) length);
                long actualCrc = crc32(payload);
                if (expectedCrc != actualCrc) {
                    buffer = Arrays.copyOfRange(buffer, 1, buffer.length);
                    continue;
                }
                if (type == FRAME_JOB) {
                    jobs.add(payload);
                } else if (type == FRAME_ERROR) {
                    throw new IllegalStateException("Pico GB Serial error: " + new String(payload, StandardCharsets.US_ASCII));
                }
                buffer = Arrays.copyOfRange(buffer, frameLength, buffer.length);
            }
        }

        private int findMagic(byte[] data) {
            for (int offset = 0; offset <= data.length - MAGIC.length; offset++) {
                boolean matches = true;
                for (int index = 0; index < MAGIC.length; index++) {
                    if (data[offset + index] != MAGIC[index]) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return offset;
                }
            }
            return -1;
        }

        private void keepPossibleMagicPrefix() {
            int keep = Math.min(buffer.length, MAGIC.length - 1);
            for (int length = keep; length > 0; length--) {
                boolean matches = true;
                for (int index = 0; index < length; index++) {
                    if (buffer[buffer.length - length + index] != MAGIC[index]) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    buffer = Arrays.copyOfRange(buffer, buffer.length - length, buffer.length);
                    return;
                }
            }
            buffer = new byte[0];
        }
    }

    public static class DeviceStatus {
        public final long queuedCaptures;
        public final long lastCaptureSize;
        public final long replayCaptureSize;
        public final boolean isPrinting;
        public final boolean hasReplayCapture;
        public final boolean debugEnabled;

        DeviceStatus(long queuedCaptures, long lastCaptureSize, long replayCaptureSize, boolean isPrinting, boolean hasReplayCapture, boolean debugEnabled) {
            this.queuedCaptures = queuedCaptures;
            this.lastCaptureSize = lastCaptureSize;
            this.replayCaptureSize = replayCaptureSize;
            this.isPrinting = isPrinting;
            this.hasReplayCapture = hasReplayCapture;
            this.debugEnabled = debugEnabled;
        }
    }

    public static class DecodedCapture {
        public final List<GbcImage> images;
        public final List<Bitmap> bitmaps;

        DecodedCapture(List<GbcImage> images, List<Bitmap> bitmaps) {
            this.images = images;
            this.bitmaps = bitmaps;
        }
    }

    public interface ReceiveListener {
        void onConnected(DeviceStatus status);
        void onCapture(DecodedCapture capture, int captureCount);
        void onStatus(String message);
        void onError(Exception exception);
    }

    public static class ReceivePicoGbSerialAsyncTask extends AppTask<Void, Object, Exception> {
        private final UsbSerialPort port;
        private final Context context;
        private final ReceiveListener listener;
        private int captureCount = 0;

        public ReceivePicoGbSerialAsyncTask(UsbSerialPort port, Context context, ReceiveListener listener) {
            this.port = port;
            this.context = context;
            this.listener = listener;
        }

        @Override
        protected Exception doInBackground(Void... voids) {
            try {
                publishProgress("receiving");
                int idleCaptureCount = 0;
                long nextPollAt = System.currentTimeMillis() + 1000;
                while (!isCancelled()) {
                    Frame frame = tryReadFrame(port, CAPTURE_IDLE_TIMEOUT_MS);
                    if (frame == null) {
                        if (idleCaptureCount > 0) {
                            try {
                                clearDevice(port);
                            } catch (Exception clearException) {
                                publishProgress("status", context.getString(R.string.pico_gb_serial_clear_failed) + clearException.toString());
                            }
                            idleCaptureCount = 0;
                            publishProgress("status", context.getString(R.string.pico_gb_serial_cleared));
                        }
                        if (System.currentTimeMillis() >= nextPollAt) {
                            nextPollAt = System.currentTimeMillis() + 1000;
                            try {
                                frame = getNextFrame(port);
                            } catch (Exception pollException) {
                                publishProgress("status", context.getString(R.string.pico_gb_serial_poll_failed) + pollException.toString());
                                nextPollAt = System.currentTimeMillis() + 3000;
                                continue;
                            }
                        } else {
                            continue;
                        }
                    }
                    if (frame.type == FRAME_HELLO || frame.type == FRAME_STATUS) continue;
                    throwIfError(frame);
                    if (frame.type != FRAME_JOB) continue;
                    captureCount++;
                    idleCaptureCount++;
                    publishProgress("capture", decodeCapture(context, frame.payload, captureCount), captureCount);
                }
            } catch (Exception e) {
                if (!isCancelled()) return e;
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            String event = (String) values[0];
            if ("connected".equals(event)) listener.onConnected((DeviceStatus) values[1]);
            else if ("capture".equals(event)) listener.onCapture((DecodedCapture) values[1], (Integer) values[2]);
            else if ("status".equals(event)) listener.onStatus((String) values[1]);
            else if ("receiving".equals(event)) listener.onStatus(context.getString(R.string.pico_gb_serial_receiving_no_status));
        }

        @Override
        protected void onPostExecute(Exception exception) {
            if (exception != null) listener.onError(exception);
        }
    }

    public static void showIncomingBuffer(Context context) {
        UsbSerialFragment.gridView.setAdapter(new CustomGridViewAdapterImage(context, R.layout.row_items, UsbSerialFragment.finalListImages, UsbSerialFragment.finalListBitmaps, true, true, false, null));
        UsbSerialFragment.btnAddImages.setVisibility(View.GONE);
    }
}