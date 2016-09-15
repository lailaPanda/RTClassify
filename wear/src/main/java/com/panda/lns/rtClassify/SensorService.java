package com.panda.lns.rtClassify;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.util.SparseLongArray;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;

public class SensorService extends Service implements SensorEventListener {
    public static boolean storeData=false;
    public static boolean run=false;
    public static boolean checkTrigger=false;


    private static final String TAG = "SensorService";
    private final static int SENS_GYROSCOPE = Sensor.TYPE_GYROSCOPE;
    private final static int SENS_LINEAR_ACCELERATION = Sensor.TYPE_LINEAR_ACCELERATION;



    //recording stuff
    public static final String DATA_FILE_NAME = "data.txt";
    private static final int RECORDING_RATE = 8000; // 8 kHz
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static int BUFFER_SIZE = AudioRecord.getMinBufferSize(RECORDING_RATE, CHANNEL_IN, FORMAT)*16; //2 seconds

    private State mState = State.IDLE;

    private AsyncTask<Void, Void, Void> mRecordingAsyncTask;

    enum State {
        IDLE, RECORDING
    }



    SensorManager mSensorManager;
    private static DeviceClient client;
    private ScheduledExecutorService mScheduler;

    private AsyncTask<Void, Void, Void> sensorFileWriteTask;
    private AsyncTask<Void, Void, Void> triggerTask;

    Context context;
    private SparseLongArray lastSensorData;

    private static float calibX=0.0f;
    private static float calibY=0.0f;
    private static float calibZ=0.0f;
    private  static float lastX=0.0f;
    private static float lastY=0.0f;
    private static float lastZ=0.0f;


    private static boolean record=false;

    @Override
    public void onCreate() {
        super.onCreate();
        client = DeviceClient.getInstance(this);
        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle("Sensor Dashboard");
        builder.setContentText("Collecting sensor data..");
        dataList.clear();
        startForeground(1, builder.build());
        clearVariables();
        run=true;
        storeDataList.clear();
        storeDataList.clear();
        startMeasurement();
        context=this;
        lastSensorData = new SparseLongArray();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        run=false;
        storeDataList.clear();
        dataList.clear();
        stopMeasurement();
        Log.w(TAG, "Stopping measurement");

    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static final int soundBufferSize=16;

    protected void startMeasurement() {
        isFirstTime=true;
        strokes=0;
        Log.w("haha", "started!");
        int count=0;

        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));
        Sensor linearAccelerationSensor = mSensorManager.getDefaultSensor(SENS_LINEAR_ACCELERATION);
        Sensor gyroscopeSensor = mSensorManager.getDefaultSensor(SENS_GYROSCOPE);
        // Register the listener
        if (mSensorManager != null) {
            if (linearAccelerationSensor != null) {
                mSensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.d(TAG, "No Linear Acceleration Sensor found");
            }
            if (gyroscopeSensor != null) {
                mSensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.w(TAG, "No Gyroscope Sensor found");
            }
        }

        sensorFileWriteTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                initClassifiers();
            }

            LimitedClassList classList = new LimitedClassList(10);

            @Override
            protected Void doInBackground(Void... params) {
                StrokeData sd = null;
                long time1=0;
                long startTime=System.currentTimeMillis();
                long runningTime=0;
                //showMessage("startMeasurement!");
                int count=0;
                while(run){
                    //classification Thread
                    runningTime=System.currentTimeMillis()-startTime;
                    /*if(runningTime > 10000){
                        showMessage("done!");
                        ArrayList<StrokeTimes> finalList = getClasses(classList);
                        TimeStroke timeStroke  = getResult(finalList, 5);
                        ArrayList<String> l = new ArrayList<String>();
                        for(int i=0;i<timeStroke.times.length;i++){
                            l.add(Integer.toString(i+1) + " " + timeStroke.times[i] + " " + timeStroke.strokes[i]);
                        }

                        writeFile(l);
                        l.clear();
                        classList.clear();
                        dataList.clear();
                        run=false;
                        checkTrigger=true;
                        checkTrigger();
                    }*/
                    //**********************************************************

                    if(dataList.size() > 0){
                        Log.w(TAG, "in!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

                        count++;
                        sd = dataList.remove(0);
                        long ts=sd.firstTs;
                        int a = classifyL1(sd);
                        if(a==0){
                            a = classifyL2_h01h05(sd);
                            if(a==0){
                                a=classifyL3_h01h04(sd);
                                if(a==0){
                                    //client.sendString("H01");
                                    classList.add(new StrokeClass(0, sd.firstTs, sd.lastTs));
                                }else if(a==1){
                                    //client.sendString("H04");
                                    classList.add(new StrokeClass(1, sd.firstTs, sd.lastTs));
                                }
                            }else if(a==1){
                                //client.sendString("H05");
                                classList.add(new StrokeClass(2, sd.firstTs, sd.lastTs));
                            }
                        }else if(a==1){
                            //client.sendString("H06");
                            classList.add(new StrokeClass(3, sd.firstTs, sd.lastTs));
                        }
                            long time = (classList.get(classList.size()-1).getEndTs() - classList.get(0).getStartTs())/100000;
                            if((time > 94000) || (time < 11000)){    //if time taken is too long or too short ignore the data

                            }else{
                                if(classList.size() == 10 ){
                                    getClasses(classList);
                                }
                                startTime=System.currentTimeMillis();
                            }

                            count=0;
                    }

                }

                return null;
            }

            class TimeStroke{
                long[] times=null;
                int[] strokes=null;
                TimeStroke(long[] times, int[] strokes){
                    this.times=times;
                    this.strokes=strokes;
                }

            }

            void getClasses(LimitedClassList classList){
                final double[] p = new double[]{0.60,0.5,0.5,0.7};
                int[] classNum = new int[4];
                for(int k=0;k<classNum.length;k++){
                    classNum[k]=0;
                }
                for(int i=0;i<classList.size();i++){
                    classNum[classList.get(i).getClassification()]++;
                }
                int maxIndex=-1;
                int max=0;
                for(int i=0;i<classNum.length;i++){
                    if(classNum[i] > max){
                        max=classNum[i];
                        maxIndex=i;
                    }
                }
                if((maxIndex >=0)&&((double)max >= (10.0*(double)p[maxIndex]))){
                    client.sendString(Integer.toString(maxIndex));
                }else{
                    client.sendString("No class detected");
                }

            }
            DiscNB nb1=null;
            DiscNB nb2=null;
            DiscNB nb3=null;
            DiscNB nb4=null;
            DiscNB nb5=null;



            int classifyL1(StrokeData sd){
                double[] values = new double[5];
                values[0] =sd.ywx;
                values[1] =sd.ywz;
                values[2] =sd.wxwy;
                values[3] =sd.wx;
                values[4] =sd.wz;
                return (nb1.getPrediction(values));
            }
            int classifyL2_h01h05(StrokeData sd){
                double[] values = new double[7];
                values[0] =sd.xy;
                values[1] =sd.xwx;
                values[2] =sd.ywx;
                values[3] =sd.ywz;
                values[4] =sd.zwx;
                values[5] =sd.y;
                values[6] =sd.wx;
                return (nb2.getPrediction(values));
            }
            int classifyL3_h01h04(StrokeData sd){
                double[] values = new double[2];
                values[0] =sd.xwy;
                values[1] =sd.wywz;
                return (nb3.getPrediction(values));
            }
            int classifyL2_h06h07(StrokeData sd){
                double[] values = new double[5];
                values[0] =sd.xy;
                values[1] =sd.x;
                values[2] =sd.z;
                values[3] =sd.wy;
                values[4] =sd.wz;
                return (nb5.getPrediction(values));
            }
            void initClassifiers(){
                //level 1 classification
                DiscNBPredictor[] l1predictorh01 = new DiscNBPredictor[]{new DiscNBPredictor(10, new double[]{0.233645,0.394105,0.54907,0.82134,1.123035,6.958335,10.934415,26.806965,46.388645}, new double[]{0.027,0.072,0.083,0.155,0.146,0.467,0.027,0.017,0.003,0.002}) , new DiscNBPredictor(7, new double[]{0.567135,1.08258,1.83854,3.71757,69.67076,6065.443115}, new double[]{0.12,0.249,0.237,0.224,0.165,0.003,0.001}), new DiscNBPredictor(8, new double[]{0.927885,1.993655,2.325405,3.1638,4.67117,9.99271,15.02089}, new double[]{0.285,0.376,0.057,0.098,0.079,0.084,0.014,0.006}), new DiscNBPredictor(10, new double[]{0.16324,0.376015,1.004825,4.5091,6.98962,8.309225,9.663405,11.608685,19.215405}, new double[]{0.005,0.011,0.086,0.696,0.119,0.027,0.016,0.012,0.019,0.009}) , new DiscNBPredictor(5, new double[]{0.0014,0.230935,3.758245,4.738855}, new double[]{0.001,0.01,0.81,0.079,0.101})};
                DiscNBPredictor[] l1predictorh06 = new DiscNBPredictor[]{new DiscNBPredictor(10, new double[]{0.233645,0.394105,0.54907,0.82134,1.123035,6.958335,10.934415,26.806965,46.388645}, new double[]{0.215,0.248,0.182,0.124,0.052,0.079,0.016,0.025,0.013,0.046}) , new DiscNBPredictor(7, new double[]{0.567135,1.08258,1.83854,3.71757,69.67076,6065.443115}, new double[]{0.019,0.084,0.158,0.263,0.425,0.051,0}), new DiscNBPredictor(8, new double[]{0.927885,1.993655,2.325405,3.1638,4.67117,9.99271,15.02089}, new double[]{0.02,0.065,0.024,0.092,0.126,0.279,0.173,0.221}), new DiscNBPredictor(10, new double[]{0.16324,0.376015,1.004825,4.5091,6.98962,8.309225,9.663405,11.608685,19.215405}, new double[]{0.063,0.03,0.029,0.062,0.046,0.027,0.045,0.097,0.287,0.314}) , new DiscNBPredictor(5, new double[]{0.0014,0.230935,3.758245,4.738855}, new double[]{0,0.099,0.842,0.039,0.02})};
                DiscNBClass l1h01 = new DiscNBClass("h01" ,0.772,l1predictorh01);
                DiscNBClass l1h06 = new DiscNBClass("h06" ,0.228,l1predictorh06);
                DiscNBClass[] classes = new DiscNBClass[]{l1h01,l1h06};
                nb1= new DiscNB(classes);

                //level2 classification for h01 and h05
                DiscNBPredictor[] l2predictorh01 = new DiscNBPredictor[]{new DiscNBPredictor(4, new double[]{0.41586,1.04455,2.52894}, new double[]{0.16,0.252,0.304,0.284}) , new DiscNBPredictor(5, new double[]{0.4128,0.817845,1.243695,4.02984}, new double[]{0.094,0.137,0.141,0.466,0.161}), new DiscNBPredictor(2, new double[]{0.36003}, new double[]{0.058,0.942}), new DiscNBPredictor(2, new double[]{6.090}, new double[]{0.942,0.058}) , new DiscNBPredictor(5, new double[]{0.3264,0.8029,1.42597,2.596}, new double[]{0.042,0.127,0.163,0.236,0.432}) , new DiscNBPredictor(4, new double[]{3.754,7.642,15.923}, new double[]{0.676,0.236,0.071,0.017}) , new DiscNBPredictor(4, new double[]{2.682,3.805,5.294}, new double[]{0.628,0.191,0.102,0.079})};
                DiscNBPredictor[] l2predictorh05 = new DiscNBPredictor[]{new DiscNBPredictor(4, new double[]{0.41586,1.04455,2.52894}, new double[]{0.385,0.392,0.161,0.062}) , new DiscNBPredictor(5, new double[]{0.4128,0.817845,1.243695,4.02984}, new double[]{0.39,0.3,0.133,0.167,0.009}), new DiscNBPredictor(2, new double[]{0.36003}, new double[]{0.193,0.807}), new DiscNBPredictor(2, new double[]{6.090}, new double[]{0.792,0.208}) , new DiscNBPredictor(5, new double[]{0.3264,0.8029,1.42597,2.596}, new double[]{0.212,0.36,0.241,0.117,0.071}) , new DiscNBPredictor(4, new double[]{3.754,7.642,15.923}, new double[]{0.375,0.299,0.187,0.139}) , new DiscNBPredictor(4, new double[]{2.682,3.805,5.294}, new double[]{0.109,0.112,0.222,0.557})};
                DiscNBClass l2h01 = new DiscNBClass("h01" ,0.857,l2predictorh01);
                DiscNBClass l2h05 = new DiscNBClass("h05" ,0.143,l2predictorh05);
                classes = new DiscNBClass[]{l2h01,l2h05};
                nb2= new DiscNB(classes);

                //level2 classification for h06 and h07
                DiscNBPredictor[] l2predictorh06 = new DiscNBPredictor[]{new DiscNBPredictor(2, new double[]{0.632785}, new double[]{0.517,0.483}) , new DiscNBPredictor(3, new double[]{0.568495,2.48}, new double[]{0.066,0.345,0.588}), new DiscNBPredictor(3, new double[]{8.68,66.36}, new double[]{0.434,0.562,0.004}), new DiscNBPredictor(4, new double[]{0.0324,0.669,2.824}, new double[]{0.03,0.218,0.447,0.305}) , new DiscNBPredictor(5, new double[]{0.0084,0.042995,0.91226}, new double[]{0.008,0.037,0.349,0.606})};
                DiscNBPredictor[] l2predictorh07 = new DiscNBPredictor[]{new DiscNBPredictor(2, new double[]{0.632785}, new double[]{0.713,0.287}) , new DiscNBPredictor(3, new double[]{0.568495,2.48}, new double[]{0.036,0.607,0.357}), new DiscNBPredictor(3, new double[]{8.68,66.36}, new double[]{0.698,0.285,0.016}), new DiscNBPredictor(4, new double[]{0.0324,0.669,2.824}, new double[]{0.028,0.06,0.791,0.121}) , new DiscNBPredictor(5, new double[]{0.0084,0.042995,0.91226}, new double[]{0,0.037,0.069,0.893})};
                DiscNBClass l2h06 = new DiscNBClass("h06" ,0.5,l2predictorh06);
                DiscNBClass l2h07 = new DiscNBClass("h07" ,0.5,l2predictorh07);
                classes = new DiscNBClass[]{l2h06,l2h07};
                nb5= new DiscNB(classes);

                //level3 classification for h01 and h04
                DiscNBPredictor[] l3predictorh01 = new DiscNBPredictor[]{new DiscNBPredictor(5, new double[]{0.461,0.9901,1.33005,3.454465}, new double[]{0.041,0.118,0.079,0.341,0.421}) , new DiscNBPredictor(5, new double[]{0.9367,1.545735,3.35396,6.251135}, new double[]{0.717,0.174,0.09,0.015,0.003})};
                DiscNBPredictor[] l3predictorh04 = new DiscNBPredictor[]{new DiscNBPredictor(5, new double[]{0.461,0.9901,1.33005,3.454465}, new double[]{0.286,0.349,0.129,0.21,0.027}) , new DiscNBPredictor(5, new double[]{0.9367,1.545735,3.35396,6.251135}, new double[]{0.038,0.138,0.389,0.244,0.192})};
                DiscNBClass l3h01 = new DiscNBClass("h01" ,0.833,l3predictorh01);
                DiscNBClass l3h04 = new DiscNBClass("h05" ,0.167,l3predictorh04);
                classes = new DiscNBClass[]{l3h01,l3h04};
                nb3= new DiscNB(classes);

                //level2 classification for h01 and h05
                Predictor[] predictor_l2_h01 = new Predictor[]{new Predictor(3.2526,5.8964),new Predictor(2.1028,2.8323), new Predictor(3.8924, 8.2771),new Predictor(2.5062,5.8054),new Predictor(4.3881,8.7717),new Predictor(1.6,2.035), new Predictor(3.86, 3.93),new Predictor(2.6571,1.9774)};
                Predictor[] predictor_l2_h04 = new Predictor[]{new Predictor(0.8526,0.7927),new Predictor(1.732,2.073), new Predictor(6.2884,8.5315),new Predictor(4.5117,7.4634),new Predictor(1.011,0.9322), new Predictor(2.6309, 2.8492),new Predictor(8.7257,9.6235),new Predictor(6.4383,3.9062)};
                Class l2_h01 = new Class("l2h01" , 0.71,predictor_l2_h01);
                Class l2_h04 = new Class("l2h04" , 0.14,predictor_l2_h04);
                //classes = new Class[]{l2_h01,l2_h04};
                //nb3=new DiscNB(classes);

                //level3 classification for h01 and h04
                Predictor[] predictor_l3_h01 = new Predictor[]{new Predictor(2.0465,5.1648),new Predictor(6.9559,12.5512), new Predictor(4.5267, 18.7112),new Predictor(1.0384,0.7473),new Predictor(0.7509,0.895),new Predictor(1.6858,1.4136), new Predictor(2.9712, 2.2503)};
                Predictor[] predictor_l3_h04 = new Predictor[]{new Predictor(0.3451,0.3699),new Predictor(1.0932,1.1781), new Predictor(19.4489,21.4995),new Predictor(4.4094,3.5483),new Predictor(5.0384,4.5241), new Predictor(3.6837, 1.4882),new Predictor(1.0434,0.644)};
                Class l3_h01 = new Class("l2h01" , 0.83,predictor_l3_h01);
                Class l3_h04 = new Class("l2h04" , 0.17,predictor_l3_h04);
                //classes = new Class[]{l3_h01,l3_h04};
                //nb4=new DiscNB(classes);

            }

            void showMessage(String s){
                Message msg = Message.obtain();
                msg.obj = new String(s);
                wearActivity.txtLogHandler.sendMessage(msg) ;
            }



            @Override
            protected void onPostExecute(Void aVoid) {
                sensorFileWriteTask = null;
            }

            @Override
            protected void onCancelled() {
                sensorFileWriteTask = null;
            }


        };

        sensorFileWriteTask.execute();

    }

    public static void calibrate(){
        calibX= lastX;
        calibY= lastY;
        calibZ= lastZ;
        client.sendString("Calibrated : " + calibX +" " + calibY + " " + calibZ );

    }

    private void stopMeasurement() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
        checkTrigger=false;
        run=false;
    }

    static boolean isFirstTime=true;
    long ts;
    long firstTs=0;
    long lastTs=0;
    static int accCount=0;
    float xSum=0;
    float ySum=0;
    float zSum=0;
    float xv=0;
    float yv=0;
    float zv=0;
    float xDist=0;
    float yDist=0;
    float zDist=0;
    float xwDist=0;
    float ywDist=0;
    float zwDist=0;
    int n=0;
    float avgAccXY=0;
    float lastV=0;
    float gyrSum=0;
    float lastGyrSum=0;
    float dist=0;
    int triggerGyrCount=0;
    int strokes=0;
    long[] last2ts = new long[2];
    boolean measureAcc=false;
    int gyrCount=0;

    ArrayList<Long> zCrossingTs = new ArrayList<Long>();
    LimitedArrayList vList = new LimitedArrayList(300);
    LimitedArrayList gyrList = new LimitedArrayList(300);//contains last 300 Xgyr data points
    ArrayList<StrokeData> dataList = new ArrayList<StrokeData>();
    ArrayList<String> storeDataList = new ArrayList<String>();

    void showMessage(String s){
        Message msg = Message.obtain();
        msg.obj = new String(s);
        wearActivity.txtLogHandler.sendMessage(msg) ;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        long lastTimestamp = lastSensorData.get(event.sensor.getType());
        long timeAgo = event.timestamp - lastTimestamp; // in nano seconds

        if (lastTimestamp != 0) {
            if (timeAgo < 100) { //1 ms
                return;
            }
            if (!((event.sensor.getType()==10) || (event.sensor.getType() == 4))) {
                return;
            }
        }
        lastSensorData.put(event.sensor.getType(), event.timestamp);

        float x=0;
        float y=0;
        float z=0;
        float xGyr,yGyr,zGyr;

        if((event.sensor.getType()==10)){
                accCount++;
                x=event.values[0]-calibX;
                y=event.values[1]-calibY;
                z=event.values[2]-calibZ;
                lastX=x;
                lastY=y;
                lastZ=z;
                xSum+=x;
                ySum+=y;
                zSum+=z;
                if(accCount==2){
                    ts = event.timestamp;
                }
                if(accCount >= 4){
                    xSum/=accCount;
                    ySum/=accCount;
                    zSum/=accCount;
                    xv+=xSum;
                    yv+=ySum;
                    zv+=zSum;
                    avgAccXY=Math.abs(xSum) + Math.abs(ySum);
                    if(xv*yv<0){
                        yv*=(-1);
                    }
                    lastV = (xv + yv);
                    dist+=Math.abs(xv+yv+zv);
                    xDist+=Math.abs(xv);
                    yDist+=Math.abs(yv);
                    zDist+=Math.abs(zv);
                    if(n==0){
                        firstTs=ts;
                    }
                    lastTs=ts;
                    n++;
                    accCount=0;
            }

        }else {  // if its gyro

            gyrCount++;
                xGyr = event.values[0];
                yGyr = event.values[1];
                zGyr = event.values[2];

                if (xGyr * yGyr < 0) yGyr *= (-1);
                if (xGyr * zGyr < 0) zGyr *= (-1);
                gyrSum += xGyr + yGyr + zGyr;

                if (gyrCount == 2) {
                    //ts=event.timestamp;
                }
                xwDist += Math.abs(xGyr);
                ywDist += Math.abs(yGyr);
                zwDist += Math.abs(zGyr);
                if (gyrCount >= 4) {

                    if (Math.abs(gyrSum) < 2.3) {   //zero velocity update
                        //Log.w(TAG, "zero!!!!");
                        if ((dist > 5) || ((xwDist+ywDist+zwDist) > 100)) {
                            //showMessage("dist  = "  + dist + "gyrThing = " + (xwDist+ywDist+zwDist));
                            last2ts[0] = last2ts[1];
                            last2ts[1] = event.timestamp;
                            strokes++;
                            //client.sendString(Integer.toString(strokes));
                            showMessage(Integer.toString(strokes));
                            dataList.add(new StrokeData(xDist,yDist,zDist,xwDist,ywDist,zwDist,firstTs,lastTs,(xwDist+ywDist+zwDist)));
                            //storeDataList.add(new String(Double.toString(xDist/yDist) + "," + Double.toString(xDist / zDist)+" " + Double.toString(xDist / xwDist) +" " + Double.toString(xDist/ywDist)+" " + Double.toString(xDist/zwDist)+" " + Double.toString(yDist/zDist)+" " + Double.toString(yDist/xwDist)+" " + Double.toString(yDist/ywDist)+" " + Double.toString(yDist/zwDist)+" " + Double.toString(zDist/xwDist) +" " + Double.toString(zDist/ywDist)+" " + Double.toString(zDist/zwDist)+" " + Double.toString(xwDist/ywDist)+" " + Double.toString(xwDist/zwDist)+" " + Double.toString(ywDist/zwDist)+" " + Double.toString(xDist/n)+" " + Double.toString(yDist/n)+" " + Double.toString(zDist/n)+" " + Double.toString(xwDist/n)+" " + Double.toString(ywDist/n)+" " + Double.toString(zwDist/n)));
                        }
                        //showMessage("dist = " + Double.toString(dist));
                        xv = 0;
                        yv = 0;
                        zv = 0;
                        xDist = 0;
                        yDist = 0;
                        zDist = 0;
                        xwDist = 0;
                        ywDist = 0;
                        zwDist = 0;
                        dist = 0;
                        n = 0;
                    }
                    lastGyrSum=gyrSum;
                    gyrSum = 0;
                    gyrCount = 0;
                }
        }
    }


    public void clearVariables(){
        isFirstTime=true;
        ts=0;
        firstTs=0;
        lastTs=0;
        accCount=0;
        xSum=0;
        ySum=0;
        zSum=0;
        xv=0;
        yv=0;
        zv=0;
        xDist=0;
        yDist=0;
        zDist=0;
        xwDist=0;
        ywDist=0;
        zwDist=0;
        n=0;
        avgAccXY=0;
        lastV=0;
        gyrSum=0;
        lastGyrSum=0;
        dist=0;
        gyrCount=0;
        triggerGyrCount=0;
        strokes=0;
        last2ts = new long[2];
        zCrossingTs.clear();
        vList.clear();
        dataList.clear();
        gyrList.clear();

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

class StrokeData{
    double xy,xz,xwx, xwy, xwz, yz,ywx, ywy, ywz, zwx, zwy, zwz, wxwy, wxwz, wywz,x,y,z,wx,wy,wz,wdist;
    long firstTs,lastTs;
    String data="";
    StrokeData(double x, double y, double z, double wx, double wy, double wz, long firstTs,long lastTs,double wdist){
        this.xy = x/y;
        this.xz = x/z;
        this.xwx = x/wx;
        this.xwy = x/wy;
        this.xwz = x/wz;
        this.yz = y/z;
        this.ywx = y/wx;
        this.ywy = y/wy;
        this.ywz = y/wz;
        this.zwx = z/wx;
        this.zwy = z/wy;
        this.zwz = z/wz;
        this.wxwy = wx/wy;
        this.wxwz = wx/wz;
        this.wywz = wy/wz;
        this.x=x;
        this.y=y;
        this.z=z;
        this.wx=wx;
        this.wy=wy;
        this.wz=wz;
        data = Double.toString(xy) + " " + Double.toString(xz) + " " + Double.toString(xwx) + " " + Double.toString(xwy) + " " + Double.toString(xwz) + " " + Double.toString(yz) + " " + Double.toString(ywx) + " " + Double.toString(ywy) + " " + Double.toString(ywz) + " " +Double.toString(zwx) + " " +Double.toString(zwy) + " " +Double.toString(zwz) + " " +Double.toString(wxwy) + " " +Double.toString(wxwz) + " " +Double.toString(wywz) + " " +Double.toString(x) + " " +Double.toString(y) +" " +Double.toString(z)  + " " +Double.toString(wx) + " " +Double.toString(wy) + " " +Double.toString(wz);

        this.firstTs=firstTs;
        this.lastTs=lastTs;
        this.wdist=wdist;
    }
}

class DataPoint{
    long ts;
    float v;

    DataPoint(long ts, float v){
        this.ts=ts;
        this.v=v;
    }

}


class LimitedArrayList extends ArrayList<DataPoint>{
    private int limit=0;
    LimitedArrayList(int limit){
        this.limit=limit;
    }
    public boolean add(DataPoint sp){
        if(this.size() < limit){
            return super.add(sp);
        }else{
            super.remove(0);
            return super.add(sp);
        }
    }
}
class LimitedClassList extends ArrayList<StrokeClass>{
    private int limit=0;
    LimitedClassList(int limit){
        this.limit=limit;
    }
    public boolean add(StrokeClass sc){
        if(this.size() < limit){
            return super.add(sc);
        }else{
            super.remove(0);
            return super.add(sc);
        }
    }
}

class StrokeTimes{
    private int classification=-1;
    private long time=0;
    StrokeTimes(int classification, long time){
        this.classification=classification;
        this.time=time;
    }
    int getClassification(){
        return classification;
    }
    long getTime(){
        return time;
    }
}

class StrokeClass{
    private int classification=-1;
    private long startTs=0;
    private long endTs=0;

    StrokeClass(int classification, long startTs,long endTs){
        this.classification=classification;
        this.startTs=startTs;
        this.endTs=endTs;
    }

    int getClassification(){
        return classification;
    }

    long getStartTs(){
        return startTs;
    }
    long getEndTs(){
        return endTs;
    }
    long getTime(){
        return (endTs-startTs);
    }

}







