package com.pmdtec.sample;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SizeF;

import android.util.Log;
import android.util.Rational;
import android.hardware.camera2.CameraCharacteristics.Key;

import java.util.List;

import com.pmdtec.sample.NativeCamera;

import java.util.List;



import static android.content.Context.CAMERA_SERVICE;
import static android.hardware.camera2.CaptureRequest.LENS_FOCAL_LENGTH;

public class SAlignment{

    public void prova(){
    }

    //double thetaV = Math.toRadians(p.getVerticalViewAngle());
    //double thetaH = Math.toRadians(p.getHorizontalViewAngle());

    Key<float[]> keyCar = CameraCharacteristics.LENS_INTRINSIC_CALIBRATION;

    String fx = keyCar.toString();


    float pico_focal_length_x = NativeCamera.getXFocalLength();
    float pico_focal_length_y = NativeCamera.getYFocalLength();
    float pico_focal_center_x = NativeCamera.getXFocalCenter();
    float pico_focal_center_y = NativeCamera.getYFocalCenter();
    double focal_lentgh_mobile = 3.95;

}


