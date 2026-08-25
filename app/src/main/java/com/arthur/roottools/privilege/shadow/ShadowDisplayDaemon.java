package com.arthur.roottools.privilege.shadow;

import android.annotation.SuppressLint;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Root-only app_process helper that owns one trusted VirtualDisplay.
 *
 * The class deliberately exposes no network/Binder/shell command surface. Root Tools controls it
 * through a fixed state directory and typed PrivilegeRouter actions. Process death releases the
 * VirtualDisplay automatically, so Stop is also the rollback mechanism.
 */
public final class ShadowDisplayDaemon {
    private static final File STATE_DIR = new File("/data/local/tmp/roottools-shadow");
    private static final File STATUS_FILE = new File(STATE_DIR, "status.properties");
    private static final File STOP_REQUEST = new File(STATE_DIR, "stop.request");
    private static final File CAPTURE_REQUEST = new File(STATE_DIR, "capture.request");
    private static final File PREVIEW_FILE = new File(STATE_DIR, "preview.jpg");
    private static final File PREVIEW_TMP_FILE = new File(STATE_DIR, "preview.jpg.tmp");

    private static final int FLAG_PUBLIC = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private static final int FLAG_PRESENTATION = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
    private static final int FLAG_OWN_CONTENT_ONLY = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
    private static final int FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int FLAG_ROTATES_WITH_CONTENT = 1 << 7;
    private static final int FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int FLAG_TRUSTED = 1 << 10;
    private static final int FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int FLAG_ALWAYS_UNLOCKED = 1 << 12;
    private static final int FLAG_TOUCH_FEEDBACK_DISABLED = 1 << 13;
    private static final int FLAG_OWN_FOCUS = 1 << 14;
    private static final int FLAG_DEVICE_DISPLAY_GROUP = 1 << 15;

    private ShadowDisplayDaemon() {}

    @SuppressLint("WrongConstant")
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Expected width height densityDpi");
            return;
        }
        final int width = parsePositive(args[0]);
        final int height = parsePositive(args[1]);
        final int densityDpi = parsePositive(args[2]);
        if (!validConfig(width, height, densityDpi)) {
            System.err.println("Invalid display configuration");
            return;
        }

        STATE_DIR.mkdirs();
        STOP_REQUEST.delete();
        CAPTURE_REQUEST.delete();
        PREVIEW_FILE.delete();
        PREVIEW_TMP_FILE.delete();
        final long startedAtMs = System.currentTimeMillis();
        final int pid = Process.myPid();
        writeStatus("starting", pid, -1, width, height, densityDpi, startedAtMs, "");

        HandlerThread frameThread = null;
        ImageReader imageReader = null;
        Surface surface = null;
        VirtualDisplay virtualDisplay = null;
        final Object latestFrameLock = new Object();
        final Image[] latestFrame = new Image[1];
        int displayId = -1;
        try {
            ensureMainLooper();
            Context context = createShellContext();

            frameThread = new HandlerThread("RootTools-shadow-frames");
            frameThread.start();
            Handler frameHandler = new Handler(frameThread.getLooper());
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null) return;
                    synchronized (latestFrameLock) {
                        Image previous = latestFrame[0];
                        latestFrame[0] = image;
                        image = null;
                        if (previous != null) previous.close();
                    }
                } catch (Throwable error) {
                    System.err.println("Shadow frame update failed: " + oneLine(error));
                } finally {
                    if (image != null) image.close();
                }
            }, frameHandler);
            surface = imageReader.getSurface();

            DisplayManager displayManager = createDisplayManager(context);
            int flags = FLAG_PUBLIC
                    | FLAG_PRESENTATION
                    | FLAG_OWN_CONTENT_ONLY
                    | FLAG_SUPPORTS_TOUCH
                    | FLAG_ROTATES_WITH_CONTENT
                    | FLAG_DESTROY_CONTENT_ON_REMOVAL;
            if (Build.VERSION.SDK_INT >= 33) {
                flags |= FLAG_TRUSTED
                        | FLAG_OWN_DISPLAY_GROUP
                        | FLAG_ALWAYS_UNLOCKED
                        | FLAG_TOUCH_FEEDBACK_DISABLED;
            }
            if (Build.VERSION.SDK_INT >= 34) {
                flags |= FLAG_OWN_FOCUS | FLAG_DEVICE_DISPLAY_GROUP;
            }

            virtualDisplay = displayManager.createVirtualDisplay(
                    "RootTools Shadow Display",
                    width,
                    height,
                    densityDpi,
                    surface,
                    flags
            );
            if (virtualDisplay == null || virtualDisplay.getDisplay() == null) {
                throw new IllegalStateException("DisplayManager returned no VirtualDisplay");
            }
            displayId = virtualDisplay.getDisplay().getDisplayId();
            writeStatus("running", pid, displayId, width, height, densityDpi, startedAtMs, "");
            System.out.println("shadow-display-ready id=" + displayId);

            while (!STOP_REQUEST.isFile()) {
                if (CAPTURE_REQUEST.isFile()) {
                    try {
                        synchronized (latestFrameLock) {
                            if (latestFrame[0] != null) {
                                writePreview(latestFrame[0], width, height);
                                CAPTURE_REQUEST.delete();
                            }
                        }
                    } catch (Throwable captureError) {
                        System.err.println("Preview capture failed: " + oneLine(captureError));
                        CAPTURE_REQUEST.delete();
                    }
                }
                Thread.sleep(200L);
            }
        } catch (Throwable error) {
            writeStatus("error", pid, displayId, width, height, densityDpi, startedAtMs, oneLine(error));
            error.printStackTrace(System.err);
        } finally {
            if (virtualDisplay != null) {
                try {
                    virtualDisplay.release();
                } catch (Throwable ignored) {}
            }
            if (imageReader != null) {
                try {
                    imageReader.close();
                } catch (Throwable ignored) {}
            }
            synchronized (latestFrameLock) {
                if (latestFrame[0] != null) {
                    try {
                        latestFrame[0].close();
                    } catch (Throwable ignored) {}
                    latestFrame[0] = null;
                }
            }
            if (surface != null) {
                try {
                    surface.release();
                } catch (Throwable ignored) {}
            }
            if (frameThread != null) {
                frameThread.quitSafely();
            }
            STOP_REQUEST.delete();
            CAPTURE_REQUEST.delete();
            writeStatus("stopped", pid, displayId, width, height, densityDpi, startedAtMs, "");
        }
    }

    private static DisplayManager createDisplayManager(Context context) throws Exception {
        Constructor<DisplayManager> constructor = DisplayManager.class.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        return constructor.newInstance(context);
    }

    private static Context createShellContext() throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = null;
        try {
            Method currentMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentMethod.setAccessible(true);
            activityThread = currentMethod.invoke(null);
        } catch (Throwable ignored) {}

        if (activityThread == null) {
            Constructor<?> constructor = activityThreadClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            activityThread = constructor.newInstance();
            try {
                Field currentField = activityThreadClass.getDeclaredField("sCurrentActivityThread");
                currentField.setAccessible(true);
                currentField.set(null, activityThread);
            } catch (Throwable ignored) {}
            fillConfigurationController(activityThreadClass, activityThread);
        }

        Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
        getSystemContext.setAccessible(true);
        Context systemContext = (Context) getSystemContext.invoke(activityThread);
        if (systemContext == null) {
            throw new IllegalStateException("Unable to obtain Android system context");
        }
        return new ShellContext(systemContext);
    }

    private static void fillConfigurationController(Class<?> activityThreadClass, Object activityThread) {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            Class<?> controllerClass = Class.forName("android.app.ConfigurationController");
            Class<?> activityThreadInternalClass = Class.forName("android.app.ActivityThreadInternal");
            Constructor<?> constructor = controllerClass.getDeclaredConstructor(activityThreadInternalClass);
            constructor.setAccessible(true);
            Object controller = constructor.newInstance(activityThread);
            Field field = activityThreadClass.getDeclaredField("mConfigurationController");
            field.setAccessible(true);
            field.set(activityThread, controller);
        } catch (Throwable ignored) {
            // Best-effort OEM workaround; most devices do not require this field.
        }
    }

    @SuppressWarnings("deprecation")
    private static void ensureMainLooper() {
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
        }
    }

    private static void writePreview(Image image, int width, int height) throws IOException {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        int paddedWidth = width + Math.max(0, rowPadding / Math.max(1, pixelStride));
        Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        Bitmap cropped = paddedWidth == width ? padded : Bitmap.createBitmap(padded, 0, 0, width, height);
        int previewWidth = Math.min(width, 420);
        int previewHeight = Math.max(1, Math.round(height * (previewWidth / (float) width)));
        Bitmap preview = (previewWidth == width)
                ? cropped
                : Bitmap.createScaledBitmap(cropped, previewWidth, previewHeight, true);
        try (FileOutputStream stream = new FileOutputStream(PREVIEW_TMP_FILE, false)) {
            if (!preview.compress(Bitmap.CompressFormat.JPEG, 78, stream)) {
                throw new IOException("Bitmap compression failed");
            }
            stream.flush();
        }
        if (!PREVIEW_TMP_FILE.renameTo(PREVIEW_FILE)) {
            throw new IOException("Unable to publish preview image");
        }
        if (preview != cropped) preview.recycle();
        if (cropped != padded) cropped.recycle();
        padded.recycle();
    }

    private static void writeStatus(
            String state,
            int pid,
            int displayId,
            int width,
            int height,
            int densityDpi,
            long startedAtMs,
            String error
    ) {
        STATE_DIR.mkdirs();
        File tmp = new File(STATE_DIR, "status.properties.tmp");
        try (FileWriter writer = new FileWriter(tmp, false)) {
            writer.write("state=" + state + "\n");
            writer.write("pid=" + pid + "\n");
            writer.write("displayId=" + displayId + "\n");
            writer.write("width=" + width + "\n");
            writer.write("height=" + height + "\n");
            writer.write("densityDpi=" + densityDpi + "\n");
            writer.write("startedAtMs=" + startedAtMs + "\n");
            writer.write("error=" + sanitize(error) + "\n");
            writer.flush();
            if (!tmp.renameTo(STATUS_FILE)) {
                throw new IOException("Unable to publish status file");
            }
        } catch (Throwable statusError) {
            System.err.println("Unable to write shadow status: " + oneLine(statusError));
            tmp.delete();
        }
    }

    private static int parsePositive(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean validConfig(int width, int height, int dpi) {
        return width >= 360 && width <= 2560
                && height >= 640 && height <= 3200
                && dpi >= 120 && dpi <= 640;
    }

    private static String oneLine(Throwable error) {
        String message = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        return sanitize(message);
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').trim();
    }

    private static final class ShellContext extends ContextWrapper {
        ShellContext(Context base) {
            super(base);
        }

        @Override
        public String getPackageName() {
            return "com.android.shell";
        }

        @Override
        public String getOpPackageName() {
            return "com.android.shell";
        }

        @Override
        public AttributionSource getAttributionSource() {
            if (Build.VERSION.SDK_INT >= 31) {
                return new AttributionSource.Builder(2000)
                        .setPackageName("com.android.shell")
                        .build();
            }
            return super.getAttributionSource();
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }
    }
}
