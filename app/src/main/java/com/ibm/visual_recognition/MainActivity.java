package com.ibm.visual_recognition;

import android.Manifest;
import android.app.Activity;
import android.app.DialogFragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.ibm.watson.developer_cloud.service.exception.ForbiddenException;
import com.ibm.watson.developer_cloud.visual_recognition.v3.VisualRecognition;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifyImagesOptions;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.DetectedFaces;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualClassification;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualRecognitionOptions;

import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

import com.ibm.mobilefirstplatform.clientsdk.android.core.api.BMSClient;





public class MainActivity extends AppCompatActivity {

    
    
    private static final String STATE_IMAGE = "image";
    private static final int REQUEST_CAMERA = 1;
    private static final int REQUEST_GALLERY = 2;

    // Text to Speech
    private TextToSpeech textToSpeech;
    private String TTS_username = "86661ff9-96e3-42ce-bd26-41bfdeeb49d9";
    private String TTS_password = "JMgjXoXhIKkL";
    StreamPlayer streamPlayer;

    // Visual Recognition Service has a maximum file size limit that we control by limiting the size of the image.
    private static final float MAX_IMAGE_DIMENSION = 1200;

    private VisualRecognition visualService;
    private RecognitionResultFragment resultFragment;

    private String mSelectedImageUri = null;
    private File output = null;
    private Boolean flag = false;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        textToSpeech = new TextToSpeech();
        textToSpeech.setUsernameAndPassword(TTS_username, TTS_password);

        // Set and create temp storage for camera to utilize when taking a picture
        if (savedInstanceState == null) {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DCIM);
            dir.mkdirs();
            output = new File(dir, "mCameraContent.jpeg");
        } else {
            output = (File)savedInstanceState.getSerializable("com.ibm.visual_recognition.EXTRA_FILENAME");
        }
        
        // Using a retained fragment to hold our result from the Recognition Service, create if it doesn't exist.
        resultFragment = (RecognitionResultFragment)getSupportFragmentManager().findFragmentByTag("result");
        if (resultFragment == null) {
            resultFragment = new RecognitionResultFragment();
            resultFragment.setRetainInstance(true);
            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, resultFragment, "result").commit();
        }

        // While the fragment retains the result from Recognition, we need to handle the selected image ourselves.
        if (savedInstanceState != null) {
            mSelectedImageUri = savedInstanceState.getString(STATE_IMAGE);

            // Re-fetch the selected Bitmap from its Uri, or if null, restore the default image.
            if (mSelectedImageUri != null) {
                Uri imageUri = Uri.parse(mSelectedImageUri);
                Bitmap selectedImage = fetchBitmapFromUri(imageUri);

                ImageView selectedImageView = (ImageView) findViewById(R.id.selectedImageView);
                selectedImageView.setImageBitmap(selectedImage);
            } else {
                ImageView selectedImageView = (ImageView) findViewById(R.id.selectedImageView);
                selectedImageView.setImageDrawable(ContextCompat.getDrawable(this, R.mipmap.bend));
            }
        }

        textToSpeechAudio("¡Hola! Bienvenido al Sistema Inteligente Electoral. Por favor retírate lentes y objetos que puedan cubrir tu rostro, y presiona el botón de la cámara para tomar la foto.");

        ImageButton cameraButton = (ImageButton) findViewById(R.id.cameraButton);
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE_SECURE);
                if(output == null){
                    output=(File)savedInstanceState.getSerializable("com.ibm.visual_recognition.EXTRA_FILENAME");
                }
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(output));
                startActivityForResult(cameraIntent, REQUEST_CAMERA);
            }
        });

        ImageButton galleryButton = (ImageButton) findViewById(R.id.galleryButton);
        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(galleryIntent, REQUEST_GALLERY);
            }
        });

        // Core SDK must be initialized to interact with Bluemix Mobile services.
        BMSClient.getInstance().initialize(getApplicationContext(), BMSClient.REGION_US_SOUTH);

        Button continuarB = (Button) findViewById(R.id.continuar);
        continuarB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setContentView(R.layout.activity_finger_print);
                textToSpeechAudio("Coloca tu dedo pulgar derecho en el lector.");
                Button buttonFinger = (Button) findViewById(R.id.buttonFinger);
                buttonFinger.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setContentView(R.layout.activity_votacion);
                        textToSpeechAudio("Presiona sobre la foto de algún candidato para votar por él.");
                        Button buttonV1 = (Button) findViewById(R.id.buttonV1);
                        buttonV1.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                textToSpeechAudio("Tu voto por el Sistema Inteligente Electoral se ha registrado.");
                                insertar("1");
                            }
                        });
                        Button buttonV2 = (Button) findViewById(R.id.buttonV2);
                        buttonV2.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                textToSpeechAudio("Tu voto por Chefsito se ha registrado.");
                                insertar("2");
                            }
                        });
                        Button buttonV3 = (Button) findViewById(R.id.buttonV3);
                        buttonV3.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                textToSpeechAudio("Tu voto por EDetect se ha registrado.");
                                insertar("3");
                            }
                        });
                        Button buttonV4 = (Button) findViewById(R.id.buttonV4);
                        buttonV4.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                textToSpeechAudio("Tu voto por E M D se ha registrado.");
                                insertar("4");
                            }
                        });
                        Button buttonV5 = (Button) findViewById(R.id.buttonV5);
                        buttonV5.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                textToSpeechAudio("Tu voto por EMIN se ha registrado.");
                                insertar("5");
                            }
                        });
                        Button buttonV6 = (Button) findViewById(R.id.buttonV6);
                        buttonV6.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                textToSpeechAudio("Tu voto por Cuic med se ha registrado.");
                                insertar("6");
                            }
                        });
                        Button buttonV7 = (Button) findViewById(R.id.buttonV7);
                        buttonV7.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                textToSpeechAudio("Tu voto por T W T se ha registrado.");
                                insertar("7");
                            }
                        });
                    }
                });
            }
        });

        

        

        

        

        

        
        visualService = new VisualRecognition(VisualRecognition.VERSION_DATE_2016_05_20,
                getString(R.string.visualrecognitionApi_key));

        // Immediately on start attempt to validate the user's credentials from credentials.xml.
        ValidateCredentialsTask vct = new ValidateCredentialsTask();
        vct.execute();
    }

    public class SQLConnect extends AsyncTask<String,Void,String> {
        @Override
        protected String doInBackground(final String... params) {
            Thread thread = new Thread(new Runnable() {
                public void run() {
                    try {
                        String url = "jdbc:mysql://us-mm-auto-sl-dfw-01-a.cleardb.net:3306/ibmx_cec0e20ede2b77b";
                        String user = "b2bca383df7d29";
                        String password = "cf38232b";

                        try {
                            Class.forName("com.mysql.jdbc.Driver").newInstance();
                            Connection con = DriverManager.getConnection(url, user, password);
                            Statement st = con.createStatement();
                            Random rn = new Random();
                            int randomNum = rn.nextInt((10000 - 10) + 1) + 10;
                            st.executeUpdate("INSERT INTO voto (no_ine, id_candidato) VALUES (" + randomNum + ", " + params[0] + ")");
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        } catch (InstantiationException e) {
                            e.printStackTrace();
                        } catch (SQLException e) {
                            e.printStackTrace();
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            thread.start();
            return "";
        }
    }
    public void insertar(String s)
    {
            SQLConnect con = new SQLConnect();
            con.doInBackground(s);
    }
    public void textToSpeechAudio(final String message){
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    streamPlayer = new StreamPlayer();
                    streamPlayer.playStream(textToSpeech.synthesize(message, Voice.LA_SOFIA).execute());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }
    @Override
    public void onResume() {
        super.onResume();
        
        
        
    }

    @Override
    public void onPause() {
        super.onPause();
        
    }

    @Override
    public void onDestroy() {
        // Have the fragment save its state for recreation on orientation changes.
        resultFragment.saveData();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the URI of the currently selected image for recreation.
        savedInstanceState.putString(STATE_IMAGE, mSelectedImageUri);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            if (requestCode == REQUEST_GALLERY || requestCode == REQUEST_CAMERA) {
                Uri uri = data.getData();

                // Create uri from temp storage if returned intent could not gather Uri
                if (uri == null) {
                    uri = Uri.fromFile(output);
                }
                
                mSelectedImageUri = uri.toString();

                // Fetch the Bitmap from the Uri.
                Bitmap selectedImage = fetchBitmapFromUri(uri);

                // Set the UI's Bitmap with the full-sized, rotated Bitmap.
                ImageView resultImage = (ImageView) findViewById(R.id.selectedImageView);
                resultImage.setImageBitmap(selectedImage);

                // Resize the Bitmap to constrain within Watson Image Recognition's Size Limit.
                selectedImage = resizeBitmapForWatson(selectedImage, MAX_IMAGE_DIMENSION);

                // Send the resized, rotated, bitmap to the Classify Task for Classification.
                ClassifyTask ct = new ClassifyTask();
                ct.execute(selectedImage);
            }
        }
    }

    /**
     * Displays an AlertDialogFragment with the given parameters.
     * @param errorTitle Error Title from values/strings.xml.
     * @param errorMessage Error Message either from values/strings.xml or response from server.
     * @param canContinue Whether the application can continue without needing to be rebuilt.
     */
    private void showDialog(int errorTitle, String errorMessage, boolean canContinue) {
        DialogFragment newFragment = AlertDialogFragment.newInstance(errorTitle, errorMessage, canContinue);
        newFragment.show(getFragmentManager(), "dialog");
    }

    /**
     * Asynchronously contacts the Visual Recognition Service to see if provided Credentials are valid.
     */
    private class ValidateCredentialsTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            // Check to see if the user's credentials are valid or not along with other errors.
            try {
                visualService.getClassifiers().execute();
            } catch (Exception ex) {
                if (ex.getClass().equals(ForbiddenException.class) ||
                        ex.getClass().equals(IllegalArgumentException.class)) {
                    showDialog(R.string.error_title_invalid_credentials,
                            getString(R.string.error_message_invalid_credentials), false);
                }
                else if (ex.getCause() != null && ex.getCause().getClass().equals(UnknownHostException.class)) {
                    showDialog(R.string.error_title_bluemix_connection,
                            getString(R.string.error_message_bluemix_connection), true);
                }
                else {
                    showDialog(R.string.error_title_default, ex.getMessage(), true);
                    ex.printStackTrace();
                }
            }
            return null;
        }
    }

    /**
     * Asynchronously sends the selected image to Visual Recognition for Classification then passes the
     * result to our RecognitionResultBuilder to display to the user.
     */
    private class ClassifyTask extends AsyncTask<Bitmap, Void, ClassifyTaskResult> {

        @Override
        protected void onPreExecute() {
            ProgressBar progressSpinner = (ProgressBar)findViewById(R.id.loadingSpinner);
            progressSpinner.setVisibility(View.VISIBLE);

            // Clear the current image tags from our result layout.
            LinearLayout resultLayout = (LinearLayout) findViewById(R.id.recognitionResultLayout);
            resultLayout.removeAllViews();
        }

        @Override
        protected ClassifyTaskResult doInBackground(Bitmap... params) {
            Bitmap createdPhoto = params[0];

            // Reformat Bitmap into a .jpg and save as file to input to Watson.
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            createdPhoto.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

            try {
                File tempPhoto = File.createTempFile("photo", ".jpg", getCacheDir());
                FileOutputStream out = new FileOutputStream(tempPhoto);
                out.write(bytes.toByteArray());
                out.close();

                // Two different service calls for objects and for faces.
                ClassifyImagesOptions classifyImagesOptions = new ClassifyImagesOptions.Builder().images(tempPhoto).build();
                VisualRecognitionOptions recognitionOptions = new VisualRecognitionOptions.Builder().images(tempPhoto).build();

                VisualClassification classification = visualService.classify(classifyImagesOptions).execute();
                DetectedFaces faces = visualService.detectFaces(recognitionOptions).execute();

                ClassifyTaskResult result = new ClassifyTaskResult(classification, faces);

                tempPhoto.delete();

                return result;
            } catch (Exception ex) {
                if (ex.getCause() != null && ex.getCause().getClass().equals(UnknownHostException.class)) {
                    showDialog(R.string.error_title_bluemix_connection,
                            getString(R.string.error_message_bluemix_connection), true);
                } else {
                    showDialog(R.string.error_title_default, ex.getMessage(), true);
                    ex.printStackTrace();
                }
                return null;
            }
        }

        @Override
        protected void onPostExecute(ClassifyTaskResult result) {
            ProgressBar progressSpinner = (ProgressBar)findViewById(R.id.loadingSpinner);
            progressSpinner.setVisibility(View.GONE);

            if (result != null) {
                // If not null send the full result from ToneAnalyzer to our UI Builder class.
                RecognitionResultBuilder resultBuilder = new RecognitionResultBuilder(MainActivity.this);
                LinearLayout resultLayout = (LinearLayout) findViewById(R.id.recognitionResultLayout);

                if(resultLayout != null){
                    resultLayout.removeAllViews();
                }
                LinearLayout recognitionView = resultBuilder.buildRecognitionResultView(result.getVisualClassification(), result.getDetectedFaces());

                resultLayout.addView(recognitionView);
            }
        }
    }

    /**
     * Holds our output data from the Visual Recognition Service Calls to be passed to onPostExecute.
     */
    private class ClassifyTaskResult {
        private final VisualClassification visualClassification;
        private final DetectedFaces detectedFaces;

        ClassifyTaskResult (VisualClassification vcIn, DetectedFaces dfIn) {
            visualClassification = vcIn;
            detectedFaces = dfIn;
        }

        VisualClassification getVisualClassification() { return visualClassification;}
        DetectedFaces getDetectedFaces() {return detectedFaces;}

    }

    /**
     * Fetches a bitmap image from the device given the image's uri.
     * @param imageUri Uri of the image on the device (either in the gallery or from the camera).
     * @return A Bitmap representation of the image on the device, correctly orientated.
     */
    private Bitmap fetchBitmapFromUri(Uri imageUri) {
        try {
            // Fetch the Bitmap from the Uri.
            Bitmap selectedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

            // Fetch the orientation of the Bitmap in storage.
            String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
            Cursor cursor = getContentResolver().query(imageUri, orientationColumn, null, null, null);
            int orientation = 0;
            if (cursor != null && cursor.moveToFirst()) {
                orientation = cursor.getInt(cursor.getColumnIndex(orientationColumn[0]));
            }
            if(cursor != null) {
                cursor.close();
            }

            // Rotate the bitmap with the found orientation.
            Matrix matrix = new Matrix();
            matrix.setRotate(orientation);
            selectedImage = Bitmap.createBitmap(selectedImage, 0, 0, selectedImage.getWidth(), selectedImage.getHeight(), matrix, true);

            return selectedImage;

        } catch (IOException e) {
            showDialog(R.string.error_title_default, e.getMessage(), true);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Scales the given image to an image that fits within the size constraints placed by Visual Recognition.
     * @param originalImage Full-sized Bitmap to be scaled.
     * @param maxSize The maximum allowed dimension of the image.
     * @return The original image rescaled so that it's largest dimension is equal to maxSize
     */
    private Bitmap resizeBitmapForWatson(Bitmap originalImage, float maxSize) {

        int originalHeight = originalImage.getHeight();
        int originalWidth = originalImage.getWidth();

        int boundingDimension = (originalHeight > originalWidth) ? originalHeight : originalWidth;

        float scale = maxSize / boundingDimension;

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        originalImage = Bitmap.createBitmap(originalImage, 0, 0, originalWidth, originalHeight, matrix, true);

        return originalImage;
    }
}
