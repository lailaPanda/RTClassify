package com.panda.lns.accDataCapture;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.File;
import java.io.IOException;


public class MainActivity extends AppCompatActivity{
    private RemoteSensorManager remoteSensorManager;
    final private int REQUEST_CODE_ASK_PERMISSIONS = 124;

    static File file=null;
    static String fileName="poo.txt";
    final static String directory = Environment.getExternalStorageDirectory().getAbsolutePath().toString() + "/AccDataCapure/";




    Toolbar mToolbar;
    public static TextView txtLog = null;
    public static TextView txtMessages = null;
    public static TextView txtTime = null;
    public static ImageView image=null;

    public static EditText classNameTxt=null;
    static boolean initOk=false;
    private static Handler mHandler = new Handler();
    static Handler txtLogHandler;
    static Handler txtMessagesHandler;
    static Handler txtTimeHandler;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkWritePermission();


        txtLog=(TextView)findViewById(R.id.txtLog);
        txtMessages=(TextView)findViewById(R.id.txtMessages);

        classNameTxt = (EditText)findViewById(R.id.classNameTxt);
        remoteSensorManager = RemoteSensorManager.getInstance(this);

        final ToggleButton trackBtn = (ToggleButton)findViewById(R.id.toggleButton);
        trackBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    SensorReceiverService.clearList(classNameTxt.getText().toString());
                    remoteSensorManager.startMeasurement();
                } else {
                    remoteSensorManager.stopMeasurement();
                }
            }
        });

        Button btnCalib = (Button) findViewById(R.id.btnCalib);
        btnCalib.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        if (trackBtn.isChecked()) {
                            trackBtn.setChecked(false);
                            remoteSensorManager.stopMeasurement();
                        }
                        remoteSensorManager.calibrate();
                    }

                }
        );

        Button btnFile = (Button) findViewById(R.id.btnFile);
        btnFile.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        String s = classNameTxt.getText().toString();
                        if(s.length() > 0){
                            SensorReceiverService.fileName=s;
                        }
                        if (trackBtn.isChecked()) {
                            trackBtn.setChecked(false);
                            remoteSensorManager.stopMeasurement();
                        }
                        remoteSensorManager.getFile();
                    }

                }
        );

        txtLogHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                txtLog.setText((String)msg.obj);
            }
        };
        txtMessagesHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                String s = (String)msg.obj;
                txtMessages.setText(s);
            }
        };
    }

    private void checkWritePermission() {
        int hasWriteExternalStoragePermission = ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (hasWriteExternalStoragePermission != PackageManager.PERMISSION_GRANTED) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                showMessageOKCancel("You need to allow access to Storage",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        REQUEST_CODE_ASK_PERMISSIONS);
                            }
                        });
                return;
            }
          /*  ActivityCompat.requestPermissions(MainActivity.this,
                    new String[] {Manifest.permission.WRITE_CONTACTS},
                    REQUEST_CODE_ASK_PERMISSIONS);
            Log.w("MainActivity", "Permission requested!");

            return;*/
        }else{
            Log.w("MainActivity", "Permission already granted");

        }
    }
    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    public static void displayData(String s){
        final String str = s;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                // This gets executed on the UI thread so it can safely modify Views
                txtLog.setText(str);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    protected void onDestroy() {
        initOk=false;
        super.onDestroy();
    }
}
