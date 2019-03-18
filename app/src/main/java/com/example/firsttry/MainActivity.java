package com.example.firsttry;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.example.firsttry.R.layout.activity_main;

public class MainActivity extends AppCompatActivity {

    //Activity request code
    private static final int CAMERA_CAPTURE_IMAGE_REQUEST_CODE = 100;
    private static final int CAMERA_CAPTURE_VIDEO_REQUEST_CODE = 200;
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    //Directory used to save captured images and videos
    public static final String IMAGE_DIRECTORY_NAME = "Hello Camera";

    //fileURL to store images/videos
    private Uri fileUri;

    private ImageView imgPreview;
    private VideoView videoPreview;
    private Button btnCapturePicture, btnRecordVideo;


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(activity_main);

        imgPreview = findViewById(R.id.imgPreview);
        videoPreview = findViewById(R.id.videoPreview);
        btnCapturePicture = findViewById(R.id.btnCapturePicture);
        btnRecordVideo = findViewById(R.id.btnRecordVideo);

        /* Capture image button click event */
        btnCapturePicture.setOnClickListener((v) -> {
            captureImage();
        });

        //Recrord a video button click event

        btnRecordVideo.setOnClickListener((v) -> {
            recordVideo();
        });

        //Check camera availability
        if (!isDeviceSupportCamera()) {
            Toast.makeText(getApplicationContext(),
                    "Your device does not support camera",
                    Toast.LENGTH_LONG).show();
            //close the app if the device does not have camera
            finish();
        }
    }

    /**
     * Checking if the device has camera hardware or not
     **/

    private boolean isDeviceSupportCamera() {
        if (getApplicationContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA))
        { //On the device there is the camera
            return true;
        }
        else
        { //No camera on device
            return false;
        }
    }

    /**
     * Capturing camera image
     * Launch camera app request
     * Image capture
    **/
    private void captureImage(){
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        fileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE);

        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);

        //Start image capture Intent
        startActivityForResult(intent,CAMERA_CAPTURE_IMAGE_REQUEST_CODE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        //save file url in bundle
        outState.putParcelable("file_uri",fileUri);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState){
        super.onRestoreInstanceState(savedInstanceState);
        //get the file url
        fileUri = savedInstanceState.getParcelable("file_uri");
    }

    /**
     * Recording video
     */
    private void recordVideo(){
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);

        fileUri = getOutputMediaFileUri(MEDIA_TYPE_VIDEO);

        //Set video quality
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY,1);
        //Set image file name
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);

        //Start video recording Intent
        startActivityForResult(intent,CAMERA_CAPTURE_VIDEO_REQUEST_CODE);
    }

    /**
     *
     * @param requestCode
     * @param resultCode
     * @param data
     *
     * It will be called after closing the camera
     *
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        //Result is capturing Image
        if(requestCode == CAMERA_CAPTURE_IMAGE_REQUEST_CODE)
        {
            if (resultCode == RESULT_OK)
            {
                //Successfully captured the image
                //Now need to display it in image view
                previewCapturedImage();
            }
            else if(resultCode == RESULT_CANCELED)
            {
                //User cancelled image capture
                Toast.makeText(getApplicationContext(),
                        "User cancelled image capture.", Toast.LENGTH_SHORT)
                        .show();
            }
            else
            {
                //Failed to capture image
                Toast.makeText(getApplicationContext(),
                        "Failed to capture image.", Toast.LENGTH_SHORT)
                        .show();
            }
        }
        else if(requestCode == CAMERA_CAPTURE_VIDEO_REQUEST_CODE)
        {
            if (resultCode == RESULT_OK)
            {
                //Successfully recorded video
                //Now need to preview the recorded video
                previewVideo();
            }
            else if(resultCode == RESULT_CANCELED)
            {
                //User cancelled recording
                Toast.makeText(getApplicationContext(),
                        "User cancelled video recording.", Toast.LENGTH_SHORT)
                        .show();
            }
            else
            {
                //Failed to record video
                Toast.makeText(getApplicationContext(),
                        "Failed to record video.", Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    /**
     * Display image from a path to ImageView
     */
    private void previewCapturedImage() {
        try {
            //hide video preview
            videoPreview.setVisibility(View.GONE);
            imgPreview.setVisibility(View.VISIBLE);

            //Bitmap factory
            BitmapFactory.Options options = new BitmapFactory.Options();

            //downsizing image as it throws OutOfMemory Exception for larger images
            options.inSampleSize = 8;

            final Bitmap bitmap = BitmapFactory.decodeFile(fileUri.getPath(), options);

            imgPreview.setImageBitmap(bitmap);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    /**
     * Display the recorded video
     */
    private void previewVideo() {
        try {
            //hide img preview
            imgPreview.setVisibility(View.GONE);
            videoPreview.setVisibility(View.VISIBLE);
            videoPreview.setVideoPath(fileUri.getPath());

            //start playing
            videoPreview.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *  HELPER METHODS
     */

    /**
     * Create file uri to store images and videos
     */
    public Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /**
     * Return image or video
     */
    private static File getOutputMediaFile(int type){
        //External sd card location
        File mediaStorageDir = new File(
                Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                IMAGE_DIRECTORY_NAME);

        //Create the storage directory if it does not exists
        if(!mediaStorageDir.exists())
        {
            if(!mediaStorageDir.mkdirs())
            {
                Log.d(IMAGE_DIRECTORY_NAME,"Failed to create."
                        + IMAGE_DIRECTORY_NAME + "directory");
                return null;
            }
        }

        //Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd HHmmss",
                Locale.getDefault()).format(new Date());
        File mediaFile;
        if(type == MEDIA_TYPE_IMAGE)
        {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + "IMG_" + timeStamp + ".jpg");
        }
        else if (type == MEDIA_TYPE_VIDEO)
        {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + "VID_" + timeStamp + ".mp4");
        }
        else
        {
            return null;
        }
        return mediaFile;
    }
}