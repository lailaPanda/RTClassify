package com.panda.lns.accDataCapture;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.SparseLongArray;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class SensorService extends Service implements SensorEventListener {
    public static boolean run=false;

    public static boolean serviceRunning=false;

    private static final String TAG = "SensorService";
    private final static int SENS_LINEAR_ACCELERATION = Sensor.TYPE_ACCELEROMETER;



    //recording stuff
    public static final String DATA_FILE_NAME = "data.txt";


    SensorManager mSensorManager;
    Sensor linearAccelerationSensor;

    private static DeviceClient client;

    private static AsyncTask<Void, Void, Void> storeDataTask;

    static Context context;
    private SparseLongArray lastSensorData;
    boolean isFirst=true;

    private static double calibX=0.0f;
    private static double calibY=0.0f;
    private static double calibZ=0.0f;
    private static double lastX=0.0f;
    private static double lastY=0.0f;
    private static double lastZ=0.0f;


    static ArrayList<String> dataList = new ArrayList<String>();

    protected Integer NOTIFICATION_ID = 23213123; // Some random integer

    private LoadNotification loadNotification;
    private static PowerManager.WakeLock wakeLock;



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
       // client.sendString("onStart()!!!!!!!!!!!!!");
        Log.w(TAG, "***********in onstartCommand()");
        loadNotification = new LoadNotification("AccDataCapture", "Logging Data...");
        loadNotification.notifyMessage();

        return START_STICKY;
    }

    class LoadNotification {

        private String titleMessage;
        private String textMessage;


        public LoadNotification(String titleMessage, String textMessage) {
            this.titleMessage = titleMessage;
            this.textMessage = textMessage;
        }

        public void notifyMessage() {
            NotificationCompat.Builder builder = getNotificationBuilder(MessageReceiverService.class);
            startForeground(NOTIFICATION_ID, builder.build());

        }

        protected NotificationCompat.Builder getNotificationBuilder(Class clazz) {
            final NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());

            builder.setSmallIcon(R.mipmap.ic_launcher);  // icon id of the image

            builder.setContentTitle(this.titleMessage)
                    .setContentText(this.textMessage)
                    .setContentInfo("JukeSpot");

            Intent foregroundIntent = new Intent(getApplicationContext(), clazz);

            foregroundIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, foregroundIntent, 0);

            builder.setContentIntent(contentIntent);
            return builder;
        }

    }



    @Override
    public void onCreate() {
        super.onCreate();
        count =0;
        duration=0;
        client = DeviceClient.getInstance(this);
        Log.w(TAG, "in on create");
        lastSensorData = new SparseLongArray();
        startMeasurement();
        client.sendString("Sensors running");
        context=this;
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyWakelockTag");
        wakeLock.acquire();

    }

    ArrayList<String> l = new ArrayList<String>();

    @Override
    public void onDestroy() {
        super.onDestroy();
        double time =  Double.valueOf(SensorService.getDuration())/1000000000.0;
        double f= Double.valueOf(SensorService.count)/time;
        String freq = String.format("%.2f", f);
        ///client.sendString("OnDestroy()!!!!!!!!!!!!!!!!!" + "Time " + time  + " S" + " Avg s rate = " + freq  + " Hz");
        Log.w(TAG, "Stopping measurement");
        mSensorManager.unregisterListener(this, linearAccelerationSensor);
        wakeLock.release();
        stopForeground(true);
    }



    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected static void writeFile(){
        storeDataTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {

            }

            @Override
            protected Void doInBackground(Void... params) {
                Log.w(TAG, "in write file!");
                writeFile(dataList);
                return null;
            }


            void writeFile(ArrayList<String> list){
                File file = context.getFileStreamPath(DATA_FILE_NAME);
                Log.w(TAG, "length deleted   = " + file.length());
                OutputStream os;
                try {
                    if(file.exists()){
                        file.delete();
                    }
                    file.createNewFile();
                    OutputStreamWriter opWriter =  new OutputStreamWriter(context.openFileOutput(DATA_FILE_NAME, Context.MODE_PRIVATE));

                    for(int i=0;i<list.size(); i++) {
                        opWriter.write(list.get(i)+"\n");
                        opWriter.flush();
                    }
                    opWriter.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Log.w("witeFile", "file written. Size = " + Long.toString(file.length()));
                client.sendString("Data saved. File size = " + Long.toString(file.length()));

                if(file!=null){
                    Log.d("MessageReceiverService",Long.toString(file.length()));
                    client.sendFile(file);
                    client.sendString("File sent. Size = " + file.length() + " list length = " + Integer.toString(dataList.size()));
                }else{
                    client.sendString("File null");
                }


            }

            @Override
            protected void onPostExecute(Void aVoid) {
                Log.w("witeFile", "post ececute");
                dataList.clear();
                storeDataTask = null;
            }

            @Override
            protected void onCancelled() {
                storeDataTask = null;
            }


        };

        storeDataTask.execute();
    }

    static long getDuration(){
        /*long duration=0;
        if(dataList.size() > 1){
            duration = Long.parseLong(dataList.get(dataList.size()-1).split(" ")[0]) - Long.parseLong(dataList.get(0).split(" ")[0]);
        }*/
        return duration;

    }

    protected void startMeasurement() {
        isFirst = true;
        Log.w("haha", "started!");
        //use only gyro for trigger detection
        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));
        linearAccelerationSensor = mSensorManager.getDefaultSensor(SENS_LINEAR_ACCELERATION);
        // Register the listener
        if (mSensorManager != null) {
            if (linearAccelerationSensor != null) {
                mSensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_FASTEST);
            } else {
                Log.d(TAG, "No Linear Acceleration Sensor found");
            }
        }
        dataList.clear();
    }


    public static void calibrate(){
        calibX= lastX;
        calibY= lastY;
        calibZ= lastZ;
        client.sendString("Calibrated : " + calibX +" " + calibY + " " + calibZ );

    }

    protected void stopMeasurement() {
        Log.d(TAG, "in stopMeasurement()");
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
            client.sendString("Measurement stopped");
        }
    }

    void showMessage(String s){
        Message msg = Message.obtain();
        msg.obj = new String(s);
        wearActivity.txtLogHandler.sendMessage(msg) ;
    }


    static int count=0;
    static long duration=0;
    @Override
    public void onSensorChanged(SensorEvent event) {
            long lastTimestamp = lastSensorData.get(event.sensor.getType());
            long timeAgo = event.timestamp - lastTimestamp; // in nano seconds
            if(count > 0){
                duration+=timeAgo;
            }
            lastSensorData.put(event.sensor.getType(), event.timestamp);


        if (lastTimestamp != 0) {
                if (event.sensor.getType()!=1) { // if not accelerometer
                    return;
                }
            }

            double x=0;
            double y=0;
            double z=0;

            double filtX=0;

            if((event.sensor.getType()==1)){
                x=event.values[0];
                y=event.values[1];
                z=event.values[2];
                lastX=x;

                //Log.d(TAG, "runninng....");
                filtX = x + (x-lastX)*0.2;
                //Log.d(TAG, Double.toString(filtX));

                double ang = Math.acos(filtX/9.81) * 57.2958;
                //showMessage(Double.toString(ang));


                if(run){
                    count++;
                    dataList.add(Long.toString(event.timestamp) + " " + Double.toString(x) + " " + Double.toString(y) + " " + Double.toString(z));
                   // Log.d(TAG, Long.toString(duration));
                }

            }
    }



    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

class AccData{
    double x,y,z;
    long ts;
    AccData(double x, double y, double z, long ts){
        this.x=x;
        this.y=y;
        this.z=z;
        this.ts=ts;
    }
}








