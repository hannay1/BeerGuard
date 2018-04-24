package com.example.alt.beerguard;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.view.View;

/**
 * Created by alt on 4/23/18.
 */

public class NotificationView extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notification);
    }

    public void beer_note(String dialog)
    {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "1")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("BeerGuard")
                .setContentText(dialog)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat n_manager = NotificationManagerCompat.from(this);
        n_manager.notify(1, mBuilder.build());
    }

    public void vibe()
    {
        Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        vib.vibrate(5000);
    }


}
