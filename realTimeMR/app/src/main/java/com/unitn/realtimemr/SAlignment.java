package com.unitn.realtimemr;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;

import android.hardware.camera2.CameraManager;
import android.util.Log;
import android.util.SizeF;
import java.util.ArrayList;

/**
 * TODO
 * Utility class used to take intrinsics data from both camera device and pico flexx, like focal lenght, optical centers and so on, in order to build the rototranslation matrix
 * to perform spatial alignment.
 */
public class SAlignment {

    private static final String TAG = "ApplicationLogCat";

    float pico_focal_length_x = NativeCamera.getXFocalLength();
    float pico_focal_length_y = NativeCamera.getYFocalLength();
    float pico_focal_center_x = NativeCamera.getXFocalCenter();
    float pico_focal_center_y = NativeCamera.getYFocalCenter();


    private void getMobileChar() {
        ArrayList params = new ArrayList();
        Activity activity = AutomaticActivity.camera2video.getAct();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        String cameraId = null;
        CameraCharacteristics chars = null;
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            SizeF sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            float fl = 0;
            if (focalLengths != null && focalLengths.length > 0) {
                fl = focalLengths[0];
                params.add(fl);
                float pxWidth = (AutomaticActivity.bitmapWidth * fl) / sensorSize.getWidth();
                float pxHeight = (AutomaticActivity.bitmapHeight * fl) / sensorSize.getHeight();
                float fx = fl / pxWidth;
                params.add(fx);
                float fy = fl / pxHeight;
                params.add(fy);
                Log.d(TAG, "fl: " + fl + " px: " + pxWidth + " py: " + pxHeight + " fx: " + fx + " fy: " + fy);
            }
        } catch (CameraAccessException ex) {
            Log.e(TAG, "Error: " + ex);
        }
    }
}


