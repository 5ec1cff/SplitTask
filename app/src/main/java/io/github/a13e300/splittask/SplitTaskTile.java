package io.github.a13e300.splittask;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.IBinder;
import android.service.quicksettings.TileService;
import android.util.Log;

public class SplitTaskTile extends TileService {
    private static final String TAG = "SplitTask";
    @Override
    public void onClick() {
        super.onClick();
        // Log.d(TAG, "clicked");
        sendBroadcast(new Intent(Constants.ACTION_REQUEST_MOVE_ACTIVITY));
        collapse();
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private void collapse() {
        try {
            var serviceField = TileService.class.getDeclaredField("mService");
            serviceField.setAccessible(true);
            var tokenField = TileService.class.getDeclaredField("mTileToken");
            tokenField.setAccessible(true);
            var service = serviceField.get(this);
            var token = tokenField.get(this);
            var onStartActivityMethod = service.getClass().getDeclaredMethod("onStartActivity", IBinder.class);
            onStartActivityMethod.setAccessible(true);
            onStartActivityMethod.invoke(service, token);
        } catch (Throwable t) {
            Log.e(TAG, "collapse: ", t);
        }
    }
}
