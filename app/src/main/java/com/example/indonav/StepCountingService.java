package com.example.indonav;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;

public class StepCountingService extends Service implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor stepCounterSensor;
    private Sensor stepDetectorSensor;

    private int currentStepDetected;
    private int stepCounter;
    private int newStepCounter;

    boolean serviceStopped;

    NotificationManager notificationManager;
    private final Handler handler = new Handler();
    private int counter = 0;

    Intent intent;

    private final Runnable updateBrodcastData = new Runnable() {
        @Override
        public void run() {
            if(!serviceStopped) {
                brodcastSensorData();
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        intent = new Intent("com.example.indonav.stepcount");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        sensorManager.registerListener(this, stepCounterSensor, 0);
        sensorManager.registerListener(this, stepDetectorSensor, 0);

        currentStepDetected = 0; stepCounter = 0; newStepCounter = 0;
        serviceStopped = false;

        handler.removeCallbacks(updateBrodcastData);
        handler.post(updateBrodcastData);

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        serviceStopped = false;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(sensorEvent.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            int countSteps = (int) sensorEvent.values[0];

            if(stepCounter == 0) {
                stepCounter = (int) sensorEvent.values[0];
            }
            newStepCounter = countSteps - stepCounter;
        }

        if(sensorEvent.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            int detectSteps = (int) sensorEvent.values[0];
            currentStepDetected += detectSteps;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void brodcastSensorData() {
        intent.putExtra("counted_step", String.valueOf(newStepCounter));
        intent.putExtra("detected_step", String.valueOf(currentStepDetected));

        sendBroadcast(intent);
    }
}