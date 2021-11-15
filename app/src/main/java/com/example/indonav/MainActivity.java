package com.example.indonav;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.animation.Animator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.INTERNET", "android.permission.ACTIVITY_RECOGNITION"};
    private Executor executor = Executors.newSingleThreadExecutor();
    private boolean card1Visible = true, card2Visible = false;
    private String destination;
    private String filePath = "";
    private int rotation;
    PreviewView previewView;
    ImageView captureImage;
    CardView cv1, cv2;
    Button startButton;
    Button captureButton;
    TextView destinationTv;

    Context context;
    String[] destinations;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;

        Intent temp = getIntent();
        boolean tryAgainFlag = temp.getBooleanExtra("try_again", false);
        if(tryAgainFlag) {
            Toast.makeText(context, "Text not detected! Try again", Toast.LENGTH_SHORT).show();
        }

        previewView = findViewById(R.id.camera_view);
        cv1 = findViewById(R.id.cv1);
        cv2 = findViewById(R.id.cv2);
        startButton = findViewById(R.id.start_btn);
        destinationTv = findViewById(R.id.selected_destination);
        captureButton = findViewById(R.id.capture_btn);
        destinations = getResources().getStringArray(R.array.destinations);
        setupSpinner();
        destination = destinations[0];

        boolean flag = checkPermissions();
        if(!flag) askForPermissions();
        startCamera();

        startButton.setOnClickListener(view -> {
            if(card1Visible) {
                slideUp(cv1, 0, 0, 0, -cv1.getHeight());
                cv1.setVisibility(View.GONE);
            } else {
                cv1.setVisibility(View.VISIBLE);
                slideDown(cv1, 0, 0, 0, cv1.getHeight());
            }
            card1Visible = !card1Visible;

            if(card2Visible) {
                slideDown(cv2, 0, 0, -cv2.getHeight(), 0);
                cv2.setVisibility(View.GONE);
            } else {
                cv2.setVisibility(View.VISIBLE);
                slideUp(cv2, 0, 0, cv2.getHeight(), 0);
            }
            card2Visible = !card2Visible;
            Toast.makeText(context, "Capture the image of nearest landmark", Toast.LENGTH_LONG).show();
        });
    }

    private void slideDown(View view, int x1, int x2, int y1, int y2) {
        TranslateAnimation animate = new TranslateAnimation(x1, x2, y1, y2);
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
    }
    private void slideUp(View view, int x1, int x2, int y1, int y2) {
        TranslateAnimation animate = new TranslateAnimation(x1, x2, y1, y2);
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderListenableFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderListenableFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderListenableFuture.get();
                    bindPreview(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    // errors
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();

        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().build();
        ImageCapture.Builder builder = new ImageCapture.Builder();

        // HdrImageCamptureExtender hdrImageCamptureExtender = HdrImageCamptureExtender.create(builder);
        rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        final ImageCapture imageCapture = builder.setTargetRotation(rotation).build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis, imageCapture);

        captureButton.setOnClickListener(view -> {
            if(card2Visible) {
                slideDown(cv2, 0, 0, 0, cv2.getHeight());
                cv2.setVisibility(View.GONE);
            } else {
                cv2.setVisibility(View.VISIBLE);
                slideUp(cv2, 0, 0, cv2.getHeight(), 0);
            }
            card2Visible = !card2Visible;

            String date = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date());

            File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), date + ".jpg");
            filePath = date + ".jpg";
            Log.i("image path", file.getPath());
            ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();

            imageCapture.takePicture(outputFileOptions, executor, new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                     ContextCompat.getMainExecutor(context).execute(() -> {
                         Intent intent = new Intent(MainActivity.this, NavigationActivity.class);
                         intent.putExtra("image_path", filePath);
                         intent.putExtra("image_rotation", rotation);
                         intent.putExtra("dst", destination);
                         startActivity(intent);
                     });
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    exception.printStackTrace();
                }
            });
        });
    }

    private void setupSpinner() {
        Spinner spinner = findViewById(R.id.destination_dropdown);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                destination = destinations[i];
                destinationTv.setText(destination);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        ArrayAdapter ad = new ArrayAdapter(this, R.layout.spinner_item, destinations);
        ad.setDropDownViewResource(R.layout.spinner_dropdown_item);

        spinner.setAdapter(ad);
    }

    private boolean checkPermissions() {
        for (String p : REQUIRED_PERMISSIONS) {
            if(ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void askForPermissions() {
        ActivityCompat.requestPermissions((Activity) context, REQUIRED_PERMISSIONS, 1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            for (String p : REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    finish();
                }
            }
        }
    }
}