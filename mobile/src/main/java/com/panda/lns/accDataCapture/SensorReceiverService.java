package com.panda.lns.accDataCapture;

import android.net.Uri;
import android.os.Environment;
import android.os.Message;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.panda.lns.accDataCapture.shared.DataMapKeys;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class SensorReceiverService extends WearableListenerService {
    private static final int CLIENT_CONNECTION_TIMEOUT = 15000;
    static ArrayList<String> dataList = new ArrayList<String>();
    private static final String TAG = "SRService";

    //File stuff
    private GoogleApiClient googleApiClient;
    final static String directory = Environment.getExternalStorageDirectory().getAbsolutePath().toString() + "/AccDataCapture/";
    static File file=null;
    static String fileName="poo";


    @Override
    public void onCreate() {
        super.onCreate();
        googleApiClient = new GoogleApiClient.Builder(this).addApi(Wearable.API).build();
        googleApiClient.connect();
    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);

        Log.i(TAG, "Connected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        super.onPeerDisconnected(peer);

        Log.i(TAG, "Disconnected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
    }

    static int count =0;


    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = dataEvent.getDataItem();
                Uri uri = dataItem.getUri();
                String path = uri.getPath();
                if (path.startsWith("/message")) {
                    count++;
                    Log.d(TAG, "data Changed!***********************  count = " + count);
                     DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                    Message msg = Message.obtain();
                    msg.what=1;
                    msg.obj = dataMap.getString(DataMapKeys.MESSAGE);
                    //MainActivity.txtMessagesHandler.removeMessages(1);
                    MainActivity.txtMessagesHandler.sendMessage(msg) ;
                }
            }
        }

    }


    private boolean validateConnection() {
        if (googleApiClient.isConnected()) {
            return true;
        }

        ConnectionResult result = googleApiClient.blockingConnect(CLIENT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);

        return result.isSuccess();
    }

    @Override
    public void onChannelOpened(Channel channel) {
        Log.d(TAG, "channel opened!");

        if(validateConnection()){
            if (channel.getPath().equals("/mypath")) {
                try {
                    File dirFile = new File(directory);
                    if(!dirFile.exists()){
                        dirFile.mkdir();
                    }
                    file = new File(directory + fileName + ".txt");
                    if(file.exists()){
                        file.delete();
                    }
                    file.createNewFile();
                } catch (IOException e) {
                    Log.d("Main",e.toString());
                }
                Log.d("Main", Long.toString(file.length()));
                Uri uri = Uri.fromFile(file);
                channel.receiveFile(googleApiClient, uri, false);
            }
        }else{
            Message msg = Message.obtain();
            msg.obj = new String("Not ready yet. Try later");
            MainActivity.txtLogHandler.sendMessage(msg);
        }
    }

    //when file is ready
    @Override
    public void onInputClosed(Channel channel, int i, int i1) {
        int count=0;
        Log.d(TAG, "Got File size = " + file.length());
        Message msg = Message.obtain();
        try{
            FileInputStream in = new FileInputStream(file);
            InputStreamReader inputStreamReader = new InputStreamReader(in);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String receiveString = "";
            while ( (receiveString = bufferedReader.readLine()) != null ) {
                count++;
            }


        }catch(Exception e){
            Log.d(TAG, "Exception reciving file");
        }
        msg.obj = "File received. Size = " + Long.toString(file.length()) + " lines = " + Integer.toString(count);


        MainActivity.txtLogHandler.sendMessage(msg);
    }

    public static void clearList(String className){
        dataList.clear();
        if(className.length()!=0){
            dataList.add(className);
        }
    }
}