package com.unitn.realtimemr;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

/**
 * Starting class of the project.
 * It allows user to choose between manual and automatic mode.
 */
public class StartingActivity extends Activity {

    private static final String TAG = "ApplicationLogCat";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "StartingActivity.onCreate savedInstanceState = [" + savedInstanceState + "]");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.starting_layout);

        //Initialize buffers variables in case of coming back here to choose again the modality
        if( AutomaticActivity.frames_buffer_pico.size() !=0){
            for(int i=0; i<AutomaticActivity.frames_buffer_pico.size(); i++){
                AutomaticActivity.frames_buffer_pico.remove(i);
            }
        }
        if( AutomaticActivity.frames_buffer.size() !=0){
            for(int i=0; i<AutomaticActivity.frames_buffer.size(); i++){
                AutomaticActivity.frames_buffer.remove(i);
            }
        }

        Button autoBtn = findViewById(R.id.autoButton);
        Button manualBtn = findViewById(R.id.manualButton);

        autoBtn.setOnClickListener(v -> {
            Log.i(TAG, "automaticMode clicked");
            startActivity(new Intent(StartingActivity.this, AutomaticActivity.class));
        });

        manualBtn.setOnClickListener(v -> {
            Log.i(TAG, "manualMode clicked");
            startActivity(new Intent(StartingActivity.this, SampleActivity.class));
        });
    }
}
