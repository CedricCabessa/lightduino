package net.ryick.lightduino;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiStateReceiver extends BroadcastReceiver {
    private static final String TAG = "LightduinoWifiReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == "net.ryick.lightduino.START") {
            Log.d(TAG, "alarm wakeup");
            Intent srvIntent = new Intent(context, WorkerService.class);
            context.startService(srvIntent);
            return;
        }

        NetworkInfo ni = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        if (ni != null &&
            ni.getType() == ConnectivityManager.TYPE_WIFI) {

            SharedPreferences sp = context.getSharedPreferences("lightduino", Context.MODE_PRIVATE);

            if (ni.getState() == NetworkInfo.State.DISCONNECTED) {
                Log.d(TAG, "DISCONNECTED");
                SharedPreferences.Editor editor = sp.edit();
                editor.putBoolean("connected", false);
                editor.commit();

                Log.d(TAG, "kill service...");
                Intent srvIntent = new Intent(context, WorkerService.class);
                srvIntent.putExtra("disconnected", true);
                context.startService(srvIntent);

            } else if (ni.isConnectedOrConnecting()) {
                if (sp.getBoolean("connected", false)) {
                    Log.d(TAG, "flapping...");
                    return;
                }
                SharedPreferences.Editor editor = sp.edit();
                editor.putBoolean("connected", true);
                editor.commit();

                WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                Log.d(TAG, "wifi is " + wm.getConnectionInfo());
                if ("\"cedNetwork\"".equals(wm.getConnectionInfo().getSSID())) {
                    Log.d(TAG, "starting...");
                    Intent srvIntent = new Intent(context, WorkerService.class);
                    context.startService(srvIntent);
                }
            }
        }
    }
}
