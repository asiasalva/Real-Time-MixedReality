package com.pmdtec.sample;

//import android.graphics.Bitmap;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;

import static java.lang.Math.abs;
import static java.sql.Types.NULL;

public class FrameBuffer{

    private static final String TAG = "ApplicationLogCat";

    ArrayList<FB> frames_buffer = new ArrayList<>();

    FrameBuffer(){
        new FrameBuffer();
    }

    public void add(Bitmap bitmap, int timestamp){
        FB element = new FB(bitmap,timestamp);
        frames_buffer.add(element);
    }

    public static int compareFrames(ArrayList<FB> mobile_buffer, ArrayList<FB> pico_buffer){

        Log.d(TAG,"sono nella funzione in timing");
        Log.e(TAG,"mobile_buffer size :" +mobile_buffer.size());
        Log.e(TAG, "pico buffer size: "+pico_buffer.size());

        int ret_value;

        int link_position;
        if( pico_buffer.size() <= mobile_buffer.size())
        {
            for(int i=0; i<pico_buffer.size(); i++){
                link_position = findNearest(pico_buffer.get(i),mobile_buffer);
                pico_buffer.get(i).linked = mobile_buffer.get(link_position);
            }
            ret_value = 1; //pico buffer < mobile buffer
        }
        else
        {
            for(int i=0; i<mobile_buffer.size(); i++){
                link_position = findNearest(mobile_buffer.get(i),pico_buffer);
                mobile_buffer.get(i).linked = pico_buffer.get(link_position);
            }
            ret_value = 0;
        }
        return ret_value;
    }

    private static int findNearest(FB fb, ArrayList<FB> mobile_buffer) {
        int timestamp = fb.timestamp;
        int linked_position = NULL;
        double delta;
        double delta_succ;
        for(int i=0; i<mobile_buffer.size(); i++){
            delta = abs(mobile_buffer.get(i).timestamp) - abs(timestamp);
            delta_succ = abs(mobile_buffer.get(i+1).timestamp) - abs(timestamp);
            if( abs(delta) <= abs(delta_succ))
            {
                 linked_position=i;
            }
        }
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
}