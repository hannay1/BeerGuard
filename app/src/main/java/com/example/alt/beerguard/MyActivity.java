package com.example.alt.beerguard;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
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

public class MyActivity extends AppCompatActivity implements ServiceConnection {


    //Global things that need to be global
    private TextView celc;
    private BtleService.LocalBinder serviceBinder;
    private MetaWearBoard board;
    private final String mac_addr = "F1:51:4D:F3:B9:AC";
    private Accelerometer accelerometer;
    private Runnable run_temp;
    public String saved_temp;
    private Handler hand;
    private boolean already_started;
    private boolean temp_done;
    private boolean is_there_a_current_THEFT_alert;
    private boolean is_there_a_current_TEMP_alert;

    @Override
    public void onCreate(Bundle savedInstanceState){
        /*
            handles on-click events, and calls to the accel./temp streaming again functionalities
         */
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        celc = findViewById(R.id.temperature_print);
        findViewById(R.id.start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("beerguard", "start");
                    /*
                    if already started, do not start accelerometer/temp streaming
                    catches bluetooth errors
                    */
                if(!MyActivity.this.already_started)
                {
                    try {
                        accelerometer.acceleration().start();
                        accelerometer.start();
                        readTemp();
                    }catch(java.lang.NullPointerException npe)
                    {
                        bluetooth_alert("Bluetooth error", "Please check if bluetooth is enabled/the board is connected");
                    }
                    MyActivity.this.already_started = true;
                    MyActivity.this.temp_done = false;
                    Log.i("beerguard", "already_started: true ");
                }
            }
        });
        findViewById(R.id.stop).setOnClickListener(new View.OnClickListener() {
            /*
            stops accelerometer and sets booleans to prevent futher stopping.
            catches null pointer exceptions in case bluetooth is not working
             */
            @Override
            public void onClick(View v) {
                Log.i("beerguard", "stop");
                try {
                    accelerometer.stop();
                    accelerometer.acceleration().stop();
                    MyActivity.this.already_started = false;
                    MyActivity.this.temp_done = true;
                }catch(java.lang.NullPointerException npe){
                    Log.i("beerguard", "Perhaps bluetooth is off? ");
                    bluetooth_alert("Bluetooth error", "Please check if bluetooth is enabled/the board is connected");
                }
            }
        });
        findViewById(R.id.exit).setOnClickListener(new View.OnClickListener() {
            /*
                board teardown, system exit on exit
             */
            @Override
            public void onClick(View v) {
                board.tearDown();
                finish();
                System.exit(0);
            }
        });

        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);
    }

    public void bluetooth_alert(String title_alert, String message)
    {
        /*
            generic alert for bluetooth/other errors
         */
        new Thread(){
            public void run(){
                MyActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            builder = new AlertDialog.Builder(MyActivity.this, android.R.style.Theme_Material_Dialog_Alert);
                        } else {
                            builder = new AlertDialog.Builder(MyActivity.this);
                        }
                        builder.setTitle(title_alert)
                                .setMessage(message)
                                .setPositiveButton("k", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                })
                                .setIcon(R.drawable.ic_launcher_background)
                                .show();
                    }
                });
            }
        }.start();
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
        this.retrieveBoard(this.mac_addr);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        accelerometer.stop();
        accelerometer.acceleration().stop();
        this.board.disconnectAsync().continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) throws Exception {
                Log.i("beerguard", "Service Disconnected");
                return null;
            }
        });
    }

    private void vibe()
    {
        Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        vib.vibrate(1000);
    }

    //retrieving board/accelerometer. Modified from the FreeFall demo app at https://mbientlab.com/tutorials/SDKs.html#freefall-app
    private void retrieveBoard(final String mac_addr) {
        final BluetoothManager btManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice =
                btManager.getAdapter().getRemoteDevice(mac_addr);
        MediaPlayer mp = MediaPlayer.create(this, R.raw.alarm);
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
                        source.map(Function1.RSS).average((byte) 4).filter(ThresholdOutput.BINARY, 0.01f).multicast().to().filter(Comparison.EQ, -1).stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                Log.i("beerguard", "theft!!!!!");
                                vibe(); //vibration
                                mp.start(); //starts media player object to play alarm sound
                                beer_note("[!] POTENTIAL BEER THEFT");
                                if (!MyActivity.this.is_there_a_current_THEFT_alert) {
                                    MyActivity.this.is_there_a_current_THEFT_alert = true;
                                    /*
                                        new thread for negative alert

                                        boolean logic is done once dismissing the alert
                                        so the temp/accel. functionalities don't conflict
                                     */
                                    new Thread() {
                                        public void run() {
                                            MyActivity.this.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    AlertDialog.Builder builder;
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                                        builder = new AlertDialog.Builder(MyActivity.this, android.R.style.Theme_Material_Dialog_Alert);
                                                    } else {
                                                        builder = new AlertDialog.Builder(MyActivity.this);
                                                    }
                                                    builder.setTitle("Beerguard: ALERT")
                                                            .setMessage("[!] YOUR BEER IS UNDER SIEGE")
                                                            .setPositiveButton("k", new DialogInterface.OnClickListener() {
                                                                @Override
                                                                public void onClick(DialogInterface dialog, int which) {
                                                                    mp.pause();
                                                                    MyActivity.this.is_there_a_current_THEFT_alert = false;
                                                                }
                                                            })
                                                            .setIcon(R.drawable.ic_launcher_background)
                                                            .show();
                                                }
                                            });
                                        }
                                    }.start();
                                }
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
            /*
                   This is the "fix" for the
                   Timeout error that happens at random

                   Fix is physically removing the battery from the board, and then re-insert
                   the battery.

                   using board.tearDown() on exit does not seem to fix this either for some reason
             */
            @Override
            public Void then(Task<Route> task) throws Exception {
                if (task.isFaulted()) {
                    Log.w("beerguard", "Failed to configure app, " + task.getError());
                    bluetooth_alert("Beerguard: ALERT", "Ooops! The app failed to configure! Possible reasons:\n*the board is too far away\n*something is wrong with your bluetooth. reset your bluetooth" +
                            "\nOR\n Remove and re-insert the battery on the board");
                } else {
                    Log.i("beerguard", "App successfully configured");
                }
                return null;
            }
        });
    }

    private void beer_note(String dialog)
    {
        /*
            generic notification invocation method, used for both positive and negative responses
         */
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "1")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("BeerGuard")
                .setContentText(dialog)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        NotificationManagerCompat n_manager = NotificationManagerCompat.from(this);
        n_manager.notify(1, mBuilder.build());
    }


    public void readTemp()
    {
        // temperature reading/vibration
        // also deals with positive alerts (when the correct temp is reached
        final Temperature temperature = board.getModule(Temperature.class);
        final Temperature.Sensor temp_sensor = temperature.findSensors(Temperature.SensorType.PRESET_THERMISTOR)[0];
        MediaPlayer mp = MediaPlayer.create(this, R.raw.ready);
        temp_sensor.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        Log.i("beerguard", "Temperature (C) = " +  data.value(Float.class).toString());
                        if(data.value(Float.class).floatValue() < 10.0) { //HARD CODED TEMP VALUE.
                            Log.i("beerguard", "[!] BEER IS READY");
                            accelerometer.stop();
                            accelerometer.acceleration().stop();
                            beer_note("[*] BEER IS READY :)");
                            mp.start(); //positive alarm sound
                            //CHECK IF CURRENT ALERT
                            /*
                                only runs if there is no current temperature alert
                                uses a new thread for the positive alarm, same as accelerometer data
                             */
                            if(!MyActivity.this.is_there_a_current_TEMP_alert) {
                                MyActivity.this.is_there_a_current_TEMP_alert = true;
                                new Thread(){
                                    public void run(){
                                        MyActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                AlertDialog.Builder builder;
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                                    builder = new AlertDialog.Builder(MyActivity.this, android.R.style.Theme_Material_Dialog_Alert);
                                                } else {
                                                    builder = new AlertDialog.Builder(MyActivity.this);
                                                }
                                                builder.setTitle("Beerguard: GOOD NEWS!")
                                                        .setMessage("[*] YOUR BEER IS READY")
                                                        .setPositiveButton("k", new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface dialog, int which) {
                                                                MyActivity.this.is_there_a_current_TEMP_alert = false;
                                                                mp.pause();
                                                            }
                                                        })
                                                        .setIcon(R.drawable.ic_launcher_background)
                                                        .show();
                                            }
                                        });
                                    }
                                }.start();
                            }
                        }
                        MyActivity.this.saved_temp = data.value(Float.class).toString();
                    }
                });
            }
        }).continueWith(new Continuation<Route, Void>() {
            @Override
            public Void then(Task<Route> task) throws Exception {
                /*
                * handler for temperature polling.
                * only runs if the cooling period is over (beer is cold enough)
                * and if there is no current positive alert (alert for beer is cold enough)
                * */
                hand = new Handler();
                int delay = 10000;
                run_temp = new Runnable() {
                    @Override
                    public void run() {
                        if(!MyActivity.this.is_there_a_current_TEMP_alert)
                        {
                            if(!MyActivity.this.temp_done)
                            {
                                temp_sensor.read();
                                celc.setText(saved_temp);
                                hand.postDelayed(run_temp, delay);
                            }else
                            {
                                hand.removeCallbacks(run_temp);
                            }
                        }
                    }
                };
                hand.post(run_temp);
                return null;
            }
        });
    }
}