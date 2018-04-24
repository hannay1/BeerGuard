package com.example.alt.beerguard;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.TextView;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.builder.filter.Comparison;
import com.mbientlab.metawear.builder.filter.ThresholdOutput;
import com.mbientlab.metawear.builder.function.Function1;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Temperature;

import bolts.Continuation;
import bolts.Task;

/**
 * Created by alt on 4/23/18.
 */

public class BeerService extends Service  implements ServiceConnection {


    public TextView celc;
    public BtleService.LocalBinder serviceBinder;
    public MetaWearBoard board;
    public final String mac_addr = "F1:51:4D:F3:B9:AC";
    public NotificationView my_note;
    public Accelerometer accelerometer;
    public Runnable run_temp;
    public String saved_temp;
    public Handler hand;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onDestroy()
    {

        super.onDestroy();
        Log.i("beerservice", "service destroyed");
        board.disconnectAsync().continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) throws Exception {
                Log.i("beerguard", "Service Disconnected");
                return null;
            }
        });
        getApplicationContext().unbindService(this);
    }

    public void start_accel()
    {
        // start accelerometer
        accelerometer.acceleration().start();
        accelerometer.start();

        int delay = 1000;
        hand.postDelayed(new Runnable() {
            @Override
            public void run() {
                readTemp();
                celc.setText(saved_temp);
                hand.postDelayed(run_temp, delay);

            }
        }, delay);
    }



    //retrieving board/accelerometer. Taken from the FreeFall demo app at https://mbientlab.com/tutorials/SDKs.html#freefall-app


    public void readTemp()
    {
        my_note = new NotificationView();
        final Temperature temperature = board.getModule(Temperature.class);
        final Temperature.Sensor temp_sensor = temperature.findSensors(Temperature.SensorType.PRESET_THERMISTOR)[0];
        temp_sensor.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        Log.i("beerguard", "Temperature (C) = " +  data.value(Float.class).toString());

                        if(data.value(Float.class).floatValue() < 20.0) {
                            Log.i("beerguard", "[!] BEER IS READY");
                            accelerometer.stop();
                            accelerometer.acceleration().stop();
                            my_note.beer_note("[*] BEER IS READY :)");
                        }
                        BeerService.this.saved_temp = data.value(Float.class).toString();
                    }
                });
            }
        }).continueWith(new Continuation<Route, Void>() {
            @Override
            public Void then(Task<Route> task) throws Exception {
                temp_sensor.read();
                return null;
            }
        });


    }

    public void retrieveBoard(final String mac_addr) {

        final BluetoothManager btManager= (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice= btManager.getAdapter().getRemoteDevice(mac_addr);

        MediaPlayer mp = MediaPlayer.create(this, R.raw.alarm);
        my_note = new NotificationView();
        // Create a MetaWear board object for the Bluetooth Device
        board = serviceBinder.getMetaWearBoard(remoteDevice);
        board.connectAsync().onSuccessTask(new Continuation<Void, Task<Route>>() {
            @Override
            public Task<Route> then(Task<Void> task) throws Exception {
                Log.i("beerguard", "Connected to board with " + mac_addr);

                accelerometer = board.getModule(Accelerometer.class);
                accelerometer.configure()
                        .odr(60f)       // Set sampling frequency to 25Hz, or closest valid ODR
                        .commit();
                return accelerometer.acceleration().addRouteAsync(new RouteBuilder() {
                    @Override
                    public void configure(RouteComponent source) {
                        source.map(Function1.RSS).average((byte) 4).filter(ThresholdOutput.BINARY, 0.80f).multicast().to().filter(Comparison.EQ, -1).stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                Log.i("beerguard", "theft!!!!!");
                                mp.start();
                                my_note.vibe();
                                my_note.beer_note("[!] POTENTIAL BEER THEFT");
                            }
                        }).to().filter(Comparison.EQ, 1).stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                Log.i("beerguard", "no theft");
                            }
                        }).end();
                    }
                });
            }
        }).continueWith(new Continuation<Route, Void>() {
            @Override
            public Void then(Task<Route> task) throws Exception {
                if (task.isFaulted()){
                    Log.w("beerguard", "Failed to configure app, " + task.getError());
                }else
                {
                    Log.i("beerguard", "App successfully configured");
                }
                return null;
            }
        });
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.i("beerservice", "service started");
        serviceBinder = (BtleService.LocalBinder) service;
        this.retrieveBoard(this.mac_addr);
        this.start_accel();

    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
}
