package com.example.mlsmartwatchproject;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.GridViewPager;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.content.Intent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.PriorityQueue;

import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.SparseInstance;
import weka.classifiers.trees.J48;
import static weka.core.SerializationHelper.read;

public class MainActivity extends Activity implements SensorEventListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
    private static final String TAG = "MainActivity";
    private SensorManager sensorManager;
    Sensor accelerometer;
    //    ArrayList<Float> x = new ArrayList<Float>();
    private final int window_size = 4;
    private float[] x = new float[window_size*300];
    private int index = 0;
    private boolean full = false;
    private String[][] data;
    private String[] features;
    private Classifier cls = null;
    private Classifier cls1 = null;
    private Classifier cls2 = null;
    private Classifier cls3 = null;
    TextView classification;
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        mApiClient = new GoogleApiClient.Builder(MainActivity.this)
//                .addApi(ActivityRecognition.API)
//                .addConnectionCallbacks(MainActivity.this)
//                .addOnConnectionFailedListener(MainActivity.this)
//                .build();
//        mApiClient.connect();
        Log.d(TAG, "onCreate: Initializing Sensor Services");
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(MainActivity.this, accelerometer, 10000);
        classification = (TextView) findViewById(R.id.classification);
        try {
            cls = (Classifier) read(getAssets().open("J48ExerciseBenchPress4.model"));
            cls1 = (Classifier) read(getAssets().open("J48ExerciseBicepCurl4.model"));
            cls2 = (Classifier) read(getAssets().open("J48ExerciseLateralRaise4.model"));
            cls3 = (Classifier) read(getAssets().open("J48ExerciseNotExercising4.model"));
        } catch (Exception e) {
            e.printStackTrace();
        }
//        Classifier cls = null;
//        try {
//            cls = (Classifier) read(getAssets().open("J48ExerciseRecognition.model"));
//            ArrayList<Double> xAcceleration = new ArrayList<>();
//            ArrayList<Double> yAcceleration = new ArrayList<>();
//            ArrayList<Double> zAcceleration = new ArrayList<>();
//        }catch(Exception e){
//            System.out.println(e);
//            Toast.makeText()
//        }
        Log.d(TAG, "onCreate: Registered accelerometer listener");

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Intent intent = new Intent(MainActivity.this, ActivityRecognizedService.class);
        PendingIntent pendingIntent = PendingIntent.getService(MainActivity.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        //ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mApiClient, 2000, pendingIntent);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }



    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        //Log.d(TAG, "onSensorChanged: X: " + sensorEvent.values[0] + " Y: " + sensorEvent.values[1] + " Z: " + sensorEvent.values[2]);
//        x.add(sensorEvent.values[0]);
        if(index == window_size*100-1)
            full = true;
        x[index*3] = sensorEvent.values[0];
        x[index*3+1] = sensorEvent.values[1];
        x[index*3+2] = sensorEvent.values[2];
        index = (index+1)%(window_size*100);
        //Log.d(TAG, "output" + index);
        if(full&&index%100 == 0) {
            float mean = 0;
            float mean2 = 0;
            float rms1 = 0;
            float rms = 0;
            float std = 0;
            for(int i = 0; i < window_size*100; i++) {
                mean += x[i*3];
                mean2 += x[i*3+2];
                rms += x[i*3]*x[i*3];
                rms1 += x[i*3+1]*x[i*3+1];
            }
            mean /= (100*window_size);
            mean2 /= (100*window_size);
            rms /= (100*window_size);
            rms = (float) Math.sqrt(rms);
            rms1 /= (100*window_size);
            rms1 = (float) Math.sqrt(rms1);
            for(int i = 0; i < window_size*100; i++) {
                std += (x[i*3]-mean)*(x[i*3]-mean);
            }
            std /= (100*window_size);
            std = (float) Math.sqrt(std);
//        	float m0 = findMedian(0);
            float m1 = findMedian(1);
            float m2 = findMedian(2);
            double max_probs = 0.0;
            String label = "";

            // model 2
            data = new String[2][3+1];
            features = new String[]{"mean_x", "rms_y","rms_x","label"};
            data[0] = features;
            data[1][0] = String.valueOf(mean);  //mean_x
            data[1][1] = String.valueOf(rms1);
            data[1][2] = String.valueOf(rms);
            data[1][3] = "?";
            String arffData = null;
            try {
                arffData = MyWekaUtils.csvToArff(data, new int[]{0,1,2});
            } catch (Exception e) {
                e.printStackTrace();
            }
            StringReader strReader = new StringReader(arffData);
            Instances unlabeled = null;
            try {
                unlabeled = new Instances(strReader);
            } catch (IOException e) {
                e.printStackTrace();
            }
            unlabeled.setClassIndex(unlabeled.numAttributes() - 1);
            double probs = 0.0;

            try {
//                double[] p = cls2.distributionForInstance(unlabeled.instance(0));
//                for(double d:p){
//                    Log.d(TAG, String.valueOf(d));
//                }

                probs = cls2.distributionForInstance(unlabeled.instance(0))[1];
                Log.d(TAG, "lat" + String.valueOf(probs));
            } catch (Exception e) {
                e.printStackTrace();
            }
            if(probs > max_probs) {
                max_probs = probs;
                label = "Lateral_Raise";
            }

            // model 1
            data = new String[2][3+1];
            features = new String[]{"rms_y","std_x", "mean_x","label"};
            data[0] = features;
            data[1][0] = String.valueOf(rms1);
            data[1][1] = String.valueOf(std);
            data[1][2] = String.valueOf(mean);
            data[1][3] = "?";
            arffData = null;
            try {
                arffData = MyWekaUtils.csvToArff(data, new int[]{0,1,2});
            } catch (Exception e) {
                e.printStackTrace();
            }
            strReader = new StringReader(arffData);
            unlabeled = null;
            try {
                unlabeled = new Instances(strReader);
            } catch (IOException e) {
                e.printStackTrace();
            }
            unlabeled.setClassIndex(unlabeled.numAttributes() - 1);
            try {
//                double[] p1 = cls1.distributionForInstance(unlabeled.instance(0));
//                for(double d:p1){
//                    Log.d(TAG, String.valueOf(d));
//                }

                probs = cls1.distributionForInstance(unlabeled.instance(0))[1];
                Log.d(TAG, "Bicep" + String.valueOf(probs));
            } catch (Exception e) {
                e.printStackTrace();
            }
            if(probs > max_probs) {
                max_probs = probs;
                label = "Bicep_Curl";
            }

            // model 0
            data = new String[2][2+1];
            features = new String[]{"median_y", "mean_z","label"};
            data[0] = features;
            data[1][0] = String.valueOf(m1);
            data[1][1] = String.valueOf(mean2);
            data[1][2] = "?";
            arffData = null;
            try {
                arffData = MyWekaUtils.csvToArff(data, new int[]{0,1});
            } catch (Exception e) {
                e.printStackTrace();
            }
            strReader = new StringReader(arffData);
            unlabeled = null;
            try {
                unlabeled = new Instances(strReader);
            } catch (IOException e) {
                e.printStackTrace();
            }
            unlabeled.setClassIndex(unlabeled.numAttributes() - 1);
            try {
//                double[] p3 = cls.distributionForInstance(unlabeled.instance(0));
//                for(double d:p3){
//                    Log.d(TAG, String.valueOf(d));
//                }

                probs = cls.distributionForInstance(unlabeled.instance(0))[0];
                Log.d(TAG, "BenchPress" + String.valueOf(probs));
            } catch (Exception e) {
                e.printStackTrace();
            }
            if(probs > max_probs) {
                max_probs = probs;
                label = "Bench_Press";
            }

            // model 3
            data = new String[2][3+1];
            features = new String[]{"mean_x", "median_z","rms_y", "label"};
            data[0] = features;
            data[1][0] = String.valueOf(mean);
            data[1][1] = String.valueOf(m2);
            data[1][2] = String.valueOf(rms1);
            data[1][3] = "?";
            arffData = null;
            try {
                arffData = MyWekaUtils.csvToArff(data, new int[]{0,1,2});
            } catch (Exception e) {
                e.printStackTrace();
            }
            strReader = new StringReader(arffData);
            unlabeled = null;
            try {
                unlabeled = new Instances(strReader);
            } catch (IOException e) {
                e.printStackTrace();
            }
            unlabeled.setClassIndex(unlabeled.numAttributes() - 1);
            try {
//                double[] p3 = cls.distributionForInstance(unlabeled.instance(0));
//                for(double d:p3){
//                    Log.d(TAG, String.valueOf(d));
//                }

                probs = cls.distributionForInstance(unlabeled.instance(0))[1];
                Log.d(TAG, "not_exercising" + String.valueOf(probs));
            } catch (Exception e) {
                e.printStackTrace();
            }
            if(probs > max_probs) {
                max_probs = probs;
                label = "not_exercising";
            }
            Log.d(TAG, "Classification: " + label);

            classification.setText("You are:" + label);

        }
    }

    private float findMedian(int i) {
        PriorityQueue<Float> p = new PriorityQueue<>();
        for(int j = 0; j < 100*window_size; j++) {
            if(p.size()==50*window_size+1) {
                p.poll();
            }
            p.add(x[i+j*3]);
        }
        float a = p.poll();
        float b = p.poll();
        return (a+b)/2;
    }

}