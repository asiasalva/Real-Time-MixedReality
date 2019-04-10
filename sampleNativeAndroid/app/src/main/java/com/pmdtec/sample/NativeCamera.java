package com.pmdtec.sample;

/**
 * This class contains the native methods used in this sample.
 * This class is only used in a static context.
 */
public final class NativeCamera
{
    /**
     * An interface to receive amplitudes.
     */
    public interface AmplitudeListener
    {
        void onAmplitudes(int[] amplitudes);
    }

    // Used to load the 'royaleSample' library on application startup.
    static
    {
        System.loadLibrary("usb_android");
        System.loadLibrary("royale");
        System.loadLibrary("royaleSample");
    }

    private NativeCamera()
    {
    }

    public static native int[] openCameraNative(int fd, int vid, int pid);

    public static native void closeCameraNative();

    public static native void registerAmplitudeListener(AmplitudeListener amplitudeListener);
}
