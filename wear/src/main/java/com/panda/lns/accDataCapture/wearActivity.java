package com.panda.lns.accDataCapture;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;

import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class wearActivity extends Activity {

    private static final int PERMISSIONS_REQUEST_CODE = 100;


    private final String TAG ="WearActivity";

    private static TextView log;
    static Handler txtLogHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear);
        Log.w(TAG, "Activity onCreate()***************************");

        log=(TextView)findViewById(R.id.textView);
        log.setText("pooki!");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        txtLogHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                log.setText((String)msg.obj);
            }
        };
    }



    public boolean checkPermissions() {
        boolean recordAudioPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        if (recordAudioPermissionGranted) {
            return true;
        } else {
            ActivityCompat. requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
            return false;
        }

    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            } else {
                // Permission has been denied before. At this point we should show a dialog to
                // user and explain why this permission is needed and direct him to go to the
                // Permissions settings for the app in the System settings. For this sample, we
                // simply exit to get to the important part.
                Toast.makeText(this,"No permissions", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkPermissions();
        Log.w(TAG, "Activity onStart()***************************");

    }
    @Override
    protected void onStop() {
        super.onStop();
        Log.w(TAG, "Activity onStop()***************************");

    }



}
