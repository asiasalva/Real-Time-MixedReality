package com.pmdtec.sample;

//import android.graphics.Bitmap;

import android.graphics.Bitmap;
import android.util.Log;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.usb.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import android.media.MediaMetadataRetriever;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.io.FileOutputStream;

import static java.lang.Math.abs;
import static java.sql.Types.NULL;

public class FrameBuffer{

    private static final String TAG = "ApplicationLogCat";


     private ArrayList<FB> frames_buffer = new ArrayList<>();

    private FrameBuffer(){
        new FrameBuffer();

    }

    public void add(Bitmap bitmap, int timestamp){
        FB element = new FB(bitmap,timestamp);
        frames_buffer.add(element);
    }

    public static int compareFrames(ArrayList<FB> mobile_buffer, ArrayList<FB> pico_buffer){

        Log.d(TAG,"sono nel metodo compareFrames");
        Log.e(TAG,"mobile_buffer size :" +mobile_buffer.size());
        Log.e(TAG, "pico buffer size: "+pico_buffer.size());

        int ret_value;

        int link_position = 0;

        if( pico_buffer.size() <= mobile_buffer.size())
        {
            //Log.d(TAG,"il buffer di Pico e' minore");
            for(int i=0; i<pico_buffer.size(); i++)
            {
                link_position = findNearest(pico_buffer.get(i),mobile_buffer);
                //Log.d(TAG,"linked position nel for: "+link_position);
                if(! (link_position == -1)){
                    pico_buffer.get(i).addLinked(mobile_buffer.get(link_position));
                }
            }
            ret_value = 1; //pico buffer < mobile buffer
        }
        else
        {
            for(int i=0; i<mobile_buffer.size(); i++){
                link_position = findNearest(mobile_buffer.get(i),pico_buffer);
                mobile_buffer.get(i).addLinked(pico_buffer.get(link_position));
            }
            ret_value = 0;
        }

        return ret_value;
    }

    private static int findNearest(FB fb, ArrayList<FB> buffer) {

        Log.d(TAG,"Find nearest");

        int timestamp = fb.timestamp;
        Log.d(TAG,"timestamp = "+timestamp);

        int linked_position = -1;

        double delta = 0;
        double delta_succ = 0;
        int size = buffer.size();

        //Log.d(TAG,"buffer size: "+size);
        delta = buffer.get(0).timestamp - timestamp ;
        //Log.d(TAG,"delta del buffer in pos 0: " +delta);


        for(int i=1; i<=size-1; i++)
        {
            //Log.d(TAG, "delta = " + delta);
            delta_succ = buffer.get(i).timestamp - timestamp;
            if (abs(delta) <= abs(delta_succ))
            {
                Log.d(TAG,"ho trovato la mia i");
                linked_position = i-1;
                break;
            }
            else
            {
                delta = delta_succ;
            }
        }

        //Log.d(TAG,"linked position = "+linked_position);
        return linked_position;
    }

}

class FB {

    Bitmap bitmap;
    int timestamp;
    FB linked;

    FB(Bitmap bitmap, int timestamp){

        this.bitmap = bitmap;
        this.timestamp = timestamp;
    }

    public void addLinked(FB linked){
        this.linked = linked;
    }
}