package net.xinhaoyuan.glasscontroller;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class GlassControllerService extends Service {
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	Intent i = new Intent(this, EntryActivity.class);
    	i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	startActivity(i);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }    
}
