package com.example.indonav;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NavigationActivity extends AppCompatActivity implements StepListner, SensorEventListener {

    TextView sourcetv;
    CardView cv3;
    CardView cv4;
    RelativeLayout overlayScreen;
    ArFragment arFragment;

    Context context;
    private String imagePath = "";
    private String destination = "";
    private String source = "";
    private List<Path> instructionList;
    private boolean isTracking;
    private boolean isHiting;

    private float[] orientation = new float[3];
    private float[] rMat = new float[9];
    private int absoluteDir = 0;
    private float[] lastAccelerometer = new float[3];
    private float[] lastMagnetometer = new float[3];
    private boolean lastAccelerometerSet = false;
    private boolean lLastMagnetometerSet = false;
    private int rotation = 0;
    private int stepCount = 0;
    private int cross = 0;
    private int instructionIndex = 0;
    private int instructionCount = 0;

    private String[] landmarks;
    private SensorManager sensorManager;
    private Sensor acceleroMeter, magnetoMeter;
    private StepDetector stepDetector;
    private PointerDrawable pointer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        Intent intent = getIntent();

        context = this;
        instructionList = new ArrayList<>();
        pointer = new PointerDrawable();
        landmarks = getResources().getStringArray(R.array.landmarks);
        sourcetv = findViewById(R.id.source_tv);
        cv3 = findViewById(R.id.cv3);
        cv4 = findViewById(R.id.cv4);

        overlayScreen = findViewById(R.id.overlay_screen);
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.cam_fragment);
        arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            arFragment.onUpdate(frameTime);
            // onUpdate();
        });

        imagePath = intent.getStringExtra("image_path");
        rotation = intent.getIntExtra("image_rotation", 0);
        destination = intent.getStringExtra("dst");
        detectText();
    }

    private void setupNavigation() {
        Toast.makeText(NavigationActivity.this, "point your phone towards surface", Toast.LENGTH_SHORT).show();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        acceleroMeter = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetoMeter = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        stepDetector = new StepDetector();
        stepDetector.registerListner(this);

        stepCount = 0;
        sensorManager.registerListener(NavigationActivity.this, acceleroMeter, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(NavigationActivity.this, magnetoMeter, SensorManager.SENSOR_DELAY_UI);
        // setup instruction based on source and destination
        instructionList.add(new Path(1, 10));
        // 1 - north, 2 - right, 3 - south, 4 - left
        instructionCount = instructionList.size();
    }

    private void detectText() {
        Log.d("text_rec", "inside detect text");
        new Thread(() -> {
            Log.d("text_rec", "inside thread");
            File image = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), imagePath);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            InputImage inputImage = null;
            try {
                inputImage = InputImage.fromFilePath(context, Uri.fromFile(image));
            } catch (IOException e) {
                e.printStackTrace();
            }

            Task<Text> results = recognizer.process(inputImage)
                    .addOnSuccessListener(text -> {
                        Log.d("text_rec", text.getText());
                        overlayScreen.setVisibility(View.INVISIBLE);

                        boolean found = false;
                        for (Text.TextBlock block : text.getTextBlocks()) {
                            for (Text.Line line : block.getLines()) {
                                for (Text.Element element : line.getElements()) {
                                    String elementText = element.getText();
                                    String res = detectSource(elementText);
                                    if (res.length() > 0) {
                                        sourcetv.setText(res);
                                        source = res;
                                        cv3.setVisibility(View.VISIBLE);
                                        slide(cv3, 0, 0, -cv3.getHeight(), 0);
                                        found = true;
                                        break;
                                    }
                                }
                                if (found) break;
                            }
                            if (found) break;
                        }

                        if (!found) {
                            Log.d("text_rec", "text not detected");
                            Intent intent = new Intent(context, MainActivity.class);
                            intent.putExtra("try_again", true);
                            startActivity(intent);
                        } else {
                            setupNavigation();
                            slide(cv3, 0, 0, 0, -cv3.getHeight(), 2000);
                            cv3.setVisibility(View.GONE);

                            cv4.setVisibility(View.VISIBLE);
                            slide(cv4, 0, 0, cv4.getHeight(), 0, 800);
                        }
                    })
                    .addOnFailureListener(e -> {
                        // text not detected
                        Log.d("text_rec", e.getMessage());
                        Intent intent = new Intent(context, MainActivity.class);
                        intent.putExtra("try_again", true);
                        startActivity(intent);
                    });
        }).start();
    }

    private String detectSource(String text) {
        for (String tag : landmarks) {
            String[] pair = tag.split(":");
            if (pair[0].toLowerCase().contains(text.toLowerCase()) ||
                    text.toLowerCase().contains(pair[0].toLowerCase(Locale.ROOT))) {
                return pair[1];
            }
        }
        return "";
    }

    private void slide(View view, int x1, int x2, int y1, int y2) {
        TranslateAnimation animate = new TranslateAnimation(x1, x2, y1, y2);
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
    }

    private void slide(View view, int x1, int x2, int y1, int y2, long millis) {
        TranslateAnimation animate = new TranslateAnimation(x1, x2, y1, y2);
        animate.setDuration(millis);
        animate.setFillAfter(true);
        view.startAnimation(animate);
    }

    private void onUpdate() {
        updateTracking();
        if(isTracking) {
            boolean flag = updateHitTest();
            if(flag) {

            }
        }
    }

    private boolean updateHitTest() {
        Frame frame = arFragment.getArSceneView().getArFrame();
        Point pt = getScreenCenter();
        List<HitResult> hits;
        boolean wasHiting = isHiting;
        isHiting = false;
        if (frame != null) {
            hits = frame.hitTest((float) pt.x, (float) pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    isHiting = true;
                    break;
                }
            }
        }
        return wasHiting != isHiting;
    }

    private Point getScreenCenter() {
        View view = findViewById(R.id.root_view);
        return new Point(view.getWidth() / 2, view.getHeight() / 2);
    }

    private boolean updateTracking() {
        Frame frame = arFragment.getArSceneView().getArFrame();
        boolean wasTracking = isTracking;
        isTracking = frame.getCamera().getTrackingState() == TrackingState.TRACKING;
        return isTracking != wasTracking;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            stepDetector.updateAccelerometer(sensorEvent.timestamp, sensorEvent.values[0], sensorEvent.values[1], sensorEvent.values[2]);
            System.arraycopy(sensorEvent.values, 0, lastAccelerometer, 0, sensorEvent.values.length);
            lastAccelerometerSet = true;
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(sensorEvent.values, 0, lastMagnetometer, 0, sensorEvent.values.length);
            lLastMagnetometerSet = true;
        }

        if (lastAccelerometerSet && lLastMagnetometerSet) {
            SensorManager.getRotationMatrix(rMat, null, lastAccelerometer, lastMagnetometer);
            SensorManager.getOrientation(rMat, orientation);
            absoluteDir = (int) (Math.toDegrees(SensorManager.getOrientation(rMat, orientation)[0]) + 360) % 360;
            absoluteDir = Math.round(absoluteDir);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private int getRange(int degree) {
        Log.d("rotaion", String.valueOf(degree));
        int mRangeVal = 0;
        if (degree > 335 || degree < 25)
            mRangeVal = 1;    //N
        else if (degree > 65 && degree < 115)
            mRangeVal = 2;    //E
        else if (degree > 155 && degree < 205)
            mRangeVal = 3;    //S
        else if (degree > 245 && degree < 295)
            mRangeVal = 4;
        return mRangeVal;
    }

    @Override
    public void step() {
        // addObject(Uri.parse("Arrow_straight_Zneg.sfb"));

//        int dir = 99, dirAll = 99;
//
//        if(instructionIndex == 0 && instructionIndex < instructionCount) {
//            dir = instructionList.get(0).getDir();
//        }
//        if(instructionIndex < instructionCount) {
//            dirAll = instructionList.get(instructionIndex).getDir();
//        }
//
        int dir = 99;
        if(instructionIndex < instructionCount) {
            dir = instructionList.get(instructionIndex).getDir();
        }

        if(instructionIndex < instructionCount && stepCount >= instructionList.get(instructionIndex).getSteps()) {
            instructionIndex++;
            stepCount = 0;
            if(instructionIndex == instructionCount) {
                sensorManager.unregisterListener(NavigationActivity.this);
                Log.d("direction", "reached destination");
                finish();
                // next activity
                // reached detination
            }
        }
        if(stepCount == 0) {
            if(dir == 1 && getRange(absoluteDir) == 1 && cross != 1) {
                Log.d("direction", "go straight");
                addObject(Uri.parse("Arrow_straight_Zneg.sfb"));
            } else if(dir == 3 && getRange(absoluteDir) == 3 && cross != 1) {
                Log.d("direction", "go straight");
                addObject(Uri.parse("Arrow_straight_Zneg.sfb"));
            } else if(dir == 1 && getRange(absoluteDir) == 3 && cross != 1) {
                Log.d("direction", "go reverse");
                addObject(Uri.parse("Arrow_straight_Zpos.sfb"));
            } else if(dir == 3 && getRange(absoluteDir) == 1 && cross != 1) {
                Log.d("direction", "go reverse");
                addObject(Uri.parse("Arrow_straight_Zpos.sfb"));
            } else if(dir == 1 || dir == 3) {
                Log.d("direction", "go straight");
                addObject(Uri.parse("Arrow_straight_Zneg.sfb"));
            }
        }
        stepCount++;
        Log.d("step", "step count " + stepCount);

        TextView tv = findViewById(R.id.step_count_tv);
        if (tv != null) {
            tv.setText(String.valueOf(stepCount));
        }
    }

    private void addObject(Uri modelUri) {
        Frame frame = arFragment.getArSceneView().getArFrame();
        Point point = getScreenCenter();
        if (frame != null) {
            List<HitResult> hits = frame.hitTest((float) point.x, (float) point.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    placeObject(hit.createAnchor(), modelUri);
                    break;
                }
            }
        }
    }

    private void placeObject(Anchor anchor, Uri modelUri) {
        ModelRenderable.builder()
                .setSource(arFragment.getContext(), modelUri)
                .build()
                .thenAccept(renderable -> addNodeToScene(anchor, renderable))
                .exceptionally(throwable -> {
                    Toast.makeText(NavigationActivity.this, "Error", Toast.LENGTH_SHORT).show();
                    return null;
                });
    }

    private void addNodeToScene(Anchor anchor, Renderable renderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);

        TransformableNode transformableNode = new TransformableNode(arFragment.getTransformationSystem());
        // transformableNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 1f, 0), 90f));
        transformableNode.setRenderable(renderable);
        transformableNode.setParent(anchorNode);
        arFragment.getArSceneView().getScene().addChild(anchorNode);
        transformableNode.select();
    }
}