package net.ryick.lightduino;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends Activity implements ILightListener {
    private final static String TAG = "LightduinoWifiMainActivity";
    private boolean mBound = false;
    private WorkerService mService;

    private final static int LIGHT_OFF = 0;
    private final static int LIGHT_ON = 1;

    public Handler mHandler = new Handler( ) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LIGHT_OFF: {
                    displayLightOff();
                    break;
                }
                case LIGHT_ON: {
                    displayLightOn();
                    break;
                }
                default:
                    Log.e(TAG, "default case?!");
            }
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "service connected");
            WorkerService.WorkerBinder binder = (WorkerService.WorkerBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.addLightListener(MainActivity.this);
            if (mService.getLightStatus() == WorkerService.LightStatus.ON) {
                displayLightOn();
            } else if (mService.getLightStatus() == WorkerService.LightStatus.OFF) {
                displayLightOff();
            } else {
                //FIXME: user do not want to see this...
                TextView tv = (TextView) findViewById(R.id.lightStatus);
                tv.setText("???");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "service disconnected");
            mBound = false;
            mService.removeLightListener(MainActivity.this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button sleep_button = (Button)findViewById(R.id.btn_sleep);
        sleep_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBound) {
                    mService.switchOff();
                } else {
                    Log.d(TAG, "service is not bound");
                }
            }
        });
        Button light_button = (Button)findViewById(R.id.btn_light);
        light_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBound) {
                    mService.switchOn();
                } else {
                    Log.d(TAG, "service is not bound");
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        Intent intent = new Intent(this, WorkerService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
            Log.d(TAG, "onStop, unbind");
        }

    }

    private void displayLightOn() {
        TextView tv = (TextView) findViewById(R.id.lightStatus);
        tv.setText("ON");
    }

    private void displayLightOff() {
        TextView tv = (TextView) findViewById(R.id.lightStatus);
        tv.setText("OFF");
    }

    @Override
    public void lightOn() {
        mHandler.sendEmptyMessage(LIGHT_ON);
    }

    @Override
    public void lightOff() {
        mHandler.sendEmptyMessage(LIGHT_OFF);
    }
}
