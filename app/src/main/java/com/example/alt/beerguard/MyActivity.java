package com.example.alt.beerguard;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;


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
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Logging;
import com.mbientlab.metawear.module.Temperature;




/*

I messed up the second milestone. Got swamped with other projects, did not anticipate the issues I came across for this project in time.

Basically this:

* has basic accelerometer functionality, basically lifted from the FreeFall demo app
* basic temperature reading, done once every minute via ScheduledExecutorService
* an attempt was made to get vibration working once the temperature get's cold enough, but that does not work.
* has no real functionality on the phone, only console logs


I'll make sure it's good to go for milestone 3.

 */

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import bolts.Continuation;
import bolts.Task;
public class MyActivity extends AppCompatActivity implements ServiceConnection {

    private BtleService.LocalBinder serviceBinder;
    private MetaWearBoard board;
    private Accelerometer accelerometer;
    private Runnable run_temp = new Runnable() {
        @Override
        public void run() {
            readTemp();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        // Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);

        findViewById(R.id.start).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Log.i("beerguard", "start");
                // start accelerometer
                accelerometer.acceleration().start();
                accelerometer.start();
                //start scheduled (1 min) temp reading
                ScheduledExecutorService read_temp = Executors.newScheduledThreadPool(1);
                read_temp.scheduleAtFixedRate(run_temp, 0, 1, TimeUnit.MINUTES);
            }
        });
        findViewById(R.id.stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("beerguard", "stop");
                accelerometer.stop();
                accelerometer.acceleration().stop();
            }
        });
        findViewById(R.id.reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                board.tearDown();
            }
        });
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        // Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        serviceBinder = (BtleService.LocalBinder) service;
        Log.i("beerguard", "Service Connected");
        retrieveBoard("F1:51:4D:F3:B9:AC");


    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    //retrieving board/accelerometer. Taken from the FreeFall demo app at https://mbientlab.com/tutorials/SDKs.html#freefall-app
    private void retrieveBoard(String mac_addr) {
        final BluetoothManager btManager=
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice=
                btManager.getAdapter().getRemoteDevice(mac_addr);

        // Create a MetaWear board object for the Bluetooth Device
        board= serviceBinder.getMetaWearBoard(remoteDevice);
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
                                Log.i("beerguard", "theft");
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

    // temperature reading/vibration
    private void readTemp()
    {
        final Temperature temperature = board.getModule(Temperature.class);
        final Temperature.Sensor temp_sensor = temperature.findSensors(Temperature.SensorType.PRESET_THERMISTOR)[0];
        final Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        temp_sensor.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        Log.i("beerguard", "Temperature (C) = " +  data.value(Float.class));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        {
                            if(data.value(Float.class) < 25.0)
                            {
                                vibe.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                            }
                        }

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

}