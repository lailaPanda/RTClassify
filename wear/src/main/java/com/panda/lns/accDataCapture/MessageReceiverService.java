package com.panda.lns.accDataCapture;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.panda.lns.accDataCapture.shared.ClientPaths;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.File;

public class MessageReceiverService extends WearableListenerService {
    private static final String TAG = "SensorDashboard/MessageReceiverService";
    private SensorService sensorService;
    private DeviceClient deviceClient;

    protected Integer NOTIFICATION_ID = 23213124; // Some random integer
    private MessageReceiverService.LoadNotification loadNotification;



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        deviceClient.sendString(" MRSonStart()!!!!!!!!!!!!!");
        Log.w("MessageReceiverService", "***********in onstartCommand()");
        loadNotification = new LoadNotification("someTitle", "someMessage");
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

            // builder.setSmallIcon(R.drawable.some_icon_id);  // icon id of the image

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
        deviceClient = DeviceClient.getInstance(this);
        sensorService = new SensorService();
        Log.d("MesageReceiverService", "Haha*************%%%%%%");
        //deviceClient.sendString(" MRSonCreate()!!!!!!!!!!!!!");

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //deviceClient.sendString("msgRecvr onDestroy()");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);

        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = dataEvent.getDataItem();
                Uri uri = dataItem.getUri();
                String path = uri.getPath();

                if (path.startsWith("/filter")) {
                    DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                    // int filterById = dataMap.getInt(DataMapKeys.FILTER);
                    // deviceClient.setSensorFilter(filterById);
                }
            }
        }
    }


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d("sensorService", "Received message: " + messageEvent.getPath());

        if (messageEvent.getPath().equals(ClientPaths.START_MEASUREMENT)) {
            SensorService.dataList.clear();
            SensorService.run=true;
            startService(new Intent(this, SensorService.class));
            deviceClient.sendString("Sensors Running");
            deviceClient.sendString("Storing data");
        }

        if (messageEvent.getPath().equals(ClientPaths.STOP_MEASUREMENT)) {
            SensorService.run=false;
            double time =  Double.valueOf(SensorService.getDuration())/1000000000.0;
            double f= Double.valueOf(SensorService.count)/time;
            String freq = String.format("%.2f", f);

            deviceClient.sendString("Time " + time  + " S" + " Avg s rate = " + freq  + " Hz");
            stopService(new Intent(this, SensorService.class));
        }

        if (messageEvent.getPath().equals(ClientPaths.CALIBRATE)) {
            deviceClient.sendString("Calibrating....");
            stopService(new Intent(this, SensorService.class));
            startService(new Intent(this, SensorService.class));
            SensorService.run=true;
            sensorService.calibrate();
            SensorService.run=false;
            //stopService(new Intent(this, SensorService.class));
        }
        if (messageEvent.getPath().equals(ClientPaths.SEND_FILE)) {
                SensorService.writeFile();
        }
    }
}
