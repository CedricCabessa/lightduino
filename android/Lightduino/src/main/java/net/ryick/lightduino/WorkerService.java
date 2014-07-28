package net.ryick.lightduino;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

public class WorkerService extends Service {
    private final static String TAG = "LightduinoWifiWorkerService";
    private final static String DEVICE = "20:13:12:05:03:09";
    private final static String BASE_UUID = "00001101-0000-1000-8000-00805F9B34FB";
    private final IBinder mBinder = new WorkerBinder();
    private boolean mThreadRun = false;
    private BluetoothSocket mBtSocket = null;
    private String mCmd;
    private enum BtStatus {
        UNKNOWN,
        OFF,
        DISCONNECTED,
        CONNECTED
    };
    private enum LightStatus {
        UNKNOWN,
        ON,
        OFF
    }
    private LightStatus mLightStatus = LightStatus.UNKNOWN;

    private static final String MSG_ON = "relay on";
    private static final String MSG_OFF = "relay off";

    public WorkerService() {
        super();
        Log.d(TAG, "WorkerService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(btReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(btReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        mCmd = MSG_ON;
        if (intent.getBooleanExtra("disconnected", false)) {
            mCmd = MSG_OFF;
            mThreadRun = false;
            configureAlarmAndClear(false);
        } else {
            synchronized (this) {
                if (mThreadRun == false) {
                    mThreadRun = true;
                    new Thread(mSenderTask).start();
                    new Thread(mReceiverTask).start();
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void switchOff() {
        Log.d(TAG, "switchOff");
        mCmd = MSG_OFF;
        if (mThreadRun == false) {
            mThreadRun = true;
            new Thread(mSenderTask).start();
            new Thread(mReceiverTask).start();
        }
    }

    public void switchOn() {
        Log.d(TAG, "switchOn");
        mCmd = MSG_ON;
        if (mThreadRun == false) {
            mThreadRun = true;
            new Thread(mSenderTask).start();
            new Thread(mReceiverTask).start();
        }
    }

    public BtStatus getBtStatus() {
        if (BluetoothAdapter.getDefaultAdapter().isEnabled() == false) {
            return BtStatus.OFF;
        } else if (BluetoothAdapter.getDefaultAdapter().isEnabled() &&
                mBtSocket != null &&
                mBtSocket.isConnected()) {
            return BtStatus.CONNECTED;
        } else {
            return BtStatus.DISCONNECTED;
        }
    }

    public LightStatus getLightStatus() {
        return mLightStatus;
    }

    // I hope that the last message is ok
    private void setLightSatus() {
        if (mCmd.equals(MSG_OFF)) {
            mLightStatus = LightStatus.OFF;
        } else if (mCmd.equals(MSG_ON)) {
            mLightStatus = LightStatus.ON;
        } else {
            mLightStatus = LightStatus.UNKNOWN;
        }
    }


    public class WorkerBinder extends Binder {
        WorkerService getService() {
            return WorkerService.this;
        }
    }

    private Runnable mSenderTask = new Runnable() {
        @Override
        public void run() {
            if (mCmd == null)
                return;

            long sleep = 8*1000L;
            while (WorkerService.this.mThreadRun) {
                Log.d(TAG, "doing stuff");
                try {
                    BtStatus status = getBtStatus();
                    if (status == BtStatus.CONNECTED) {
                        mBtSocket.getOutputStream().write(mCmd.getBytes());
                        sleep = 5 * 1000L;
                    } else {
                        if (status == BtStatus.DISCONNECTED) {
                            sleep = 2 * 1000L;
                        } else if (status == BtStatus.OFF) {
                            sleep = 15 * 1000L;
                        }
                        connectDevice();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    btCleanup();
                }
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                }
            }
            configureAlarmAndClear(getLightStatus() == LightStatus.ON);
            Log.d(TAG, "stop doing stuff");
        }
    };

    private void configureAlarmAndClear(boolean alarm) {
        AlarmManager am = (AlarmManager)WorkerService.this.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent("net.ryick.lightduino.START");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(WorkerService.this,
                0, intent, 0);
        if (alarm) {
            Log.d(TAG, "set alarm");
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000L * 60 * 20, pendingIntent);
        } else {
            Log.d(TAG, "remove alarm");
            am.cancel(pendingIntent);
        }
        try {
            if (mBtSocket != null)
                mBtSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private Runnable mReceiverTask = new Runnable() {
        @Override
        public void run() {
            while (WorkerService.this.mThreadRun) {
                Log.d(TAG, "reading stuff");
                BtStatus status = getBtStatus();
                if (status == BtStatus.CONNECTED) {
                    try {
                        Log.d(TAG, "try to read");
                        StringBuilder buf = new StringBuilder();
                        while (mBtSocket.getInputStream().available() > 0) {
                            int c = mBtSocket.getInputStream().read();
                            buf.append((char)c);
                        }
                        if (buf.toString().contains("ack")) {
                            setLightSatus();
                            mBtSocket.getOutputStream().write("acked".getBytes());
                            Log.d(TAG, "ack, bye");
                            WorkerService.this.mThreadRun = false;
                            break;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    Thread.sleep(5 * 1000L);
                } catch (Exception e) {
                }
            }
        }
    };

    private void btCleanup() {
        try {
            mBtSocket.close();
        } catch (IOException e) {}
        mBtSocket = null;
    }

    private boolean connectDevice() {
        Log.d(TAG, "connecting");
        BluetoothAdapter myAdapter = BluetoothAdapter.getDefaultAdapter();
        if(myAdapter.isEnabled() == false) {
            myAdapter.enable();
            return false;
        }
        BluetoothDevice remoteDevice = myAdapter.getRemoteDevice(DEVICE);
        try {
            mBtSocket = remoteDevice.createRfcommSocketToServiceRecord(UUID.fromString(BASE_UUID));
            mBtSocket.connect();
        } catch (IOException e) {
            e.printStackTrace();
            btCleanup();
            return false;
        }
        return true;
    }

    private BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Bluetoothchanged");
            if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF) ==
                BluetoothAdapter.STATE_ON) {
                connectDevice();
            }
        }
    };
}
