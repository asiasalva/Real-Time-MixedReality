package com.pmdtec.sample;

import android.graphics.Bitmap;
import android.util.Log;
import java.util.ArrayList;
import static java.lang.Math.abs;

public class FrameBuffer {

    private static final String TAG = "ApplicationLogCat";
    private ArrayList<FB> frames_buffer = new ArrayList<>();

    private FrameBuffer() {
        new FrameBuffer();
    }

    public void add(Bitmap bitmap, int timestamp) {
        FB element = new FB(bitmap, timestamp);
        frames_buffer.add(element);
    }

    /**
     * First, choose the shortest buffer: this is the one we need to find the corresponding frame for each bitmap.
     * @param mobile_buffer : buffer of the mobile camera data
     * @param pico_buffer : buffer of the pico camera data
     * @return :  which is the shortest buffer
     */
    public static int compareFrames(ArrayList<FB> mobile_buffer, ArrayList<FB> pico_buffer) {

        Log.d(TAG, "FrameBuffer.compareFrames");
        //Log.e(TAG, "mobile_buffer size :" + mobile_buffer.size());
        //Log.e(TAG, "pico buffer size: " + pico_buffer.size());

        int ret_value;
        int link_position;

        if (pico_buffer.size() <= mobile_buffer.size()) {
            for (int i = 0; i < pico_buffer.size(); i++) {
                link_position = findNearest(pico_buffer.get(i), mobile_buffer);
                if (!(link_position == -1)) {
                    pico_buffer.get(i).addLinked(mobile_buffer.get(link_position));
                }
            }
            ret_value = 1; //pico buffer < mobile buffer
        } else {
            for (int i = 0; i < mobile_buffer.size(); i++) {
                link_position = findNearest(mobile_buffer.get(i), pico_buffer);
                mobile_buffer.get(i).addLinked(pico_buffer.get(link_position));
            }
            ret_value = 0;
        }
        return ret_value;
    }

    /**
     * Given a buffer element it will look for the corresponding element from the other buffer.
     * @param fb : buffer element to which find the corresponding.
     * @param buffer : buffer in which perform the search.
     * @return
     */
    private static int findNearest(FB fb, ArrayList<FB> buffer) {

        Log.d(TAG, "FramesBuffer.findNearest");
        int timestamp = fb.timestamp;

        int linked_position = -1;
        double delta;
        double delta_succ;
        int size = buffer.size();

        delta = buffer.get(0).timestamp - timestamp;

        for (int i = 1; i <= size - 1; i++) {
            delta_succ = buffer.get(i).timestamp - timestamp;
            if (abs(delta) <= abs(delta_succ)) {
                linked_position = i - 1;
                break;
            } else {
                delta = delta_succ;
            }
        }
        return linked_position;
    }
}

/**
 * Class representing a frameBuffer element:
 * each FB element has a bitmap and a timestamp.
 * The "linked" FB element is the corresponding frame I find with findNearest function.
 * Bu default, linked does not exist: it will add only when will be found.
 */
class FB {

    Bitmap bitmap;
    int timestamp;
    FB linked;

    FB(Bitmap bitmap, int timestamp) {

        this.bitmap = bitmap;
        this.timestamp = timestamp;
        this.linked = null;
    }

    public void addLinked(FB linked) {
        this.linked = linked;
    }
}