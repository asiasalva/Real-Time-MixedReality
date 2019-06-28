package com.pmdtec.sample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

public class StartingActivity extends Activity {

    private static final String TAG = "ApplicationLogCat";
    public static boolean flag_mode = true; //true if manual, false if automatic

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "StartingActivity.onCreate savedInstanceState = [" + savedInstanceState + "]");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.starting_layout);

        //Inizializzo le variabili pubbliche dei buffers
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

        manualBtn.setEnabled(false);

        autoBtn.setOnClickListener(v -> {
            Log.d(TAG, "automaticMode clicked");
            flag_mode = false;
            startActivity(new Intent(StartingActivity.this, AutomaticActivity.class));
        });

        manualBtn.setOnClickListener(v -> {
            Log.d(TAG, "manualMode clicked");
            startActivity(new Intent(StartingActivity.this, SampleActivity.class));
        });
    }
}
