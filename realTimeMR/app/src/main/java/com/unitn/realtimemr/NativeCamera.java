package com.unitn.realtimemr;

/**
 * This class contains the native methods used in this realtimemr.
 * This class is only used in a static context.
 */
public final class NativeCamera {
    /**
     * An interface to receive amplitudes.
     */
    public interface AmplitudeListener {
        void onAmplitudes(int[] amplitudes);
    }

    // Used to load the 'royaleSample' library on application startup.
    static {
        System.loadLibrary("usb_android");
        System.loadLibrary("royale");
        System.loadLibrary("royaleSample");
    }

    private NativeCamera() {
    }

    public static native int[] openCameraNative(int fd, int vid, int pid);

    public static native void closeCameraNative();

    public static native void registerAmplitudeListener(AmplitudeListener amplitudeListener);

    public static native int recordRRF(int argc, String file, int[] array);

    public static native int stopRegistration();

    public static native void semaphoreNotify(boolean notify);

    public static native int convertToPLY(String file_name);

    public static native float getXFocalLength();

    public static native float getYFocalLength();

    public static native float getXFocalCenter();

    public static native float getYFocalCenter();
}
