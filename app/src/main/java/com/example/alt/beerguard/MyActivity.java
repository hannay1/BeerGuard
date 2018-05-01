package com.example.alt.beerguard;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.RequiresApi;
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



import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import bolts.Continuation;
import bolts.Task;
public class MyActivity extends AppCompatActivity implements ServiceConnection {


    private TextView celc;
    private BtleService.LocalBinder serviceBinder;
    private MetaWearBoard board;
    private final String mac_addr = "F1:51:4D:F3:B9:AC";
    private Accelerometer accelerometer;
    private Runnable run_temp;
    public String saved_temp;
    private Handler hand;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        celc = findViewById(R.id.temperature_print);
        hand = new Handler();
        this.getParent();
        findViewById(R.id.start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("beerguard", "start");
                // start accelerometer

                try {
                    accelerometer.acceleration().start();
                    accelerometer.start();
                    int delay = 1000;
                    hand.postDelayed(run_temp = new Runnable() {
                        @Override
                        public void run() {
                            readTemp();
                            celc.setText(saved_temp);
                            hand.postDelayed(run_temp, delay);

                        }
                    }, delay);
                    // }else
                    //{
                    // bluetooth_alert("Bluetooth error", "Please check if bluetooth is enabled/the board is connected");

                    //}
                }catch(java.lang.NullPointerException npe)
                {

                    bluetooth_alert("Bluetooth error", "Please check if bluetooth is enabled/the board is connected");

                }



            }
        });
        findViewById(R.id.stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("beerguard", "stop");
                try {
                    accelerometer.stop();
                    accelerometer.acceleration().stop();
                }catch(java.lang.NullPointerException npe){
                    Log.i("beerguard", "Perhaps bluetooth is off? ");
                    bluetooth_alert("Bluetooth error", "Please check if bluetooth is enabled/the board is connected");


                }
            }
        });
        findViewById(R.id.exit).setOnClickListener(new View.OnClickListener() {
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
        this.board.disconnectAsync().continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) throws Exception {
                Log.i("beerguard", "Service Disconnected");
                return null;
            }
        });

    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        serviceBinder = (BtleService.LocalBinder) service;
        Log.i("beerguard", "Service Connected");
        try {
            this.retrieveBoard(this.mac_addr);
        } catch (TimeoutException e) {
            Log.i("beerguard", "Board messed up");
            finish();
            System.exit(0);
            Intent restart_intent = getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(getBaseContext().getPackageName());
            restart_intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(restart_intent);


        }


    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        accelerometer.stop();
        accelerometer.acceleration().stop();


    }

    private void vibe()
    {
        Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        vib.vibrate(1000);
    }





    //retrieving board/accelerometer. Taken from the FreeFall demo app at https://mbientlab.com/tutorials/SDKs.html#freefall-app
    private void retrieveBoard(final String mac_addr) throws java.util.concurrent.TimeoutException {
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

                                vibe();
                                mp.start();
                                beer_note("[!] POTENTIAL BEER THEFT");

                                bluetooth_alert("Beerguard: ALERT", "[!] YOUR BEER IS UNDER SIEGE");





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
                if (task.isFaulted()) {
                    Log.w("beerguard", "Failed to configure app, " + task.getError());

                    /**
                     *
                     * problem: java.lang.IllegalStateException is sometimes called. need to catch this exception
                     *
                     */




                } else {
                    Log.i("beerguard", "App successfully configured");


                }
                return null;
            }
        });

    }


    private void beer_note(String dialog)
    {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "1")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("BeerGuard")
                .setContentText(dialog)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat n_manager = NotificationManagerCompat.from(this);
        n_manager.notify(1, mBuilder.build());
    }

    private void alarm_sound()
    {

        MediaPlayer mp = MediaPlayer.create(this, R.raw.alarm);
        CountDownTimer ctd = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                mp.start();
            }

            @Override
            public void onFinish() {
                mp.stop();

            }
        };
        ctd.start();


    }





    // temperature reading/vibration
    public void readTemp()
    {
        final Temperature temperature = board.getModule(Temperature.class);
        final Temperature.Sensor temp_sensor = temperature.findSensors(Temperature.SensorType.PRESET_THERMISTOR)[0];
        temp_sensor.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {


                    @Override
                    public void apply(Data data, Object... env) {
                        Log.i("beerguard", "Temperature (C) = " +  data.value(Float.class).toString());


                        if(data.value(Float.class).floatValue() < 10.0) {
                            Log.i("beerguard", "[!] BEER IS READY");
                            accelerometer.stop();
                            accelerometer.acceleration().stop();
                            beer_note("[*] BEER IS READY :)");
                            bluetooth_alert("Beerguard", "[*] Your beer is ready");
                        }
                        MyActivity.this.saved_temp = data.value(Float.class).toString();


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