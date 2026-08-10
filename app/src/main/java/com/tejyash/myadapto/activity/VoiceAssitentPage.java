package com.tejyash.myadapto.activity;

import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

import com.tejyash.myadapto.R;

public class    VoiceAssitentPage extends AppCompatActivity {

    TextView txtResult;
    TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.voice_assitent_page);

        txtResult = findViewById(R.id.textView23);

        ImageView imgMic = findViewById(R.id.imgMic);

        imgMic.setOnClickListener(v -> {

            Intent intent = new Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

            startActivityForResult(intent, 1);
        });

    }   // <-- onCreate ends here


    // PASTE THE CODE BELOW HERE

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    Intent data) {

        super.onActivityResult(requestCode,
                resultCode,
                data);

        if(requestCode == 1 &&
                resultCode == RESULT_OK &&
                data != null) {

            ArrayList<String> result =
                    data.getStringArrayListExtra(
                            RecognizerIntent.EXTRA_RESULTS);

            String command = result.get(0);

            txtResult.append(command + "\n\n");
            command = command.toLowerCase();

            if(command.contains("phone") || command.contains("dial")){

                Intent intent = new Intent(Intent.ACTION_DIAL);
                startActivity(intent);

            }
            else if(command.contains("camera")){

                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivity(intent);

            }
            else if(command.contains("gallery") || command.contains("photos")){

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setType("image/*");
                startActivity(intent);

            }
            else if(command.contains("setting")){

                Intent intent =
                        new Intent(android.provider.Settings.ACTION_SETTINGS);

                startActivity(intent);

            }
            else if(command.contains("call mummy")){

                Intent intent = new Intent(
                        Intent.ACTION_DIAL,
                        android.net.Uri.parse("tel:9757042063"));

                startActivity(intent);
            }
            else if(command.contains("browser")
                    || command.contains("chrome")){

                Intent intent = new Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.google.com"));

                startActivity(intent);

            }
            else if(command.contains("youtube")){

                Intent intent = new Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.youtube.com"));

                startActivity(intent);

            }
            else if(command.contains("time")){

                java.util.Calendar calendar =
                        java.util.Calendar.getInstance();

                String time =
                        calendar.get(java.util.Calendar.HOUR_OF_DAY)
                                + ":" +
                                calendar.get(java.util.Calendar.MINUTE);

                txtResult.append("Time: " + time + "\n\n");

            }
            else if(command.contains("date")){

                java.text.SimpleDateFormat sdf =
                        new java.text.SimpleDateFormat("dd/MM/yyyy");

                String date =
                        sdf.format(new java.util.Date());

                txtResult.append("Date: " + date + "\n\n");

            }
            else if(command.contains("read text")
                    || command.contains("read aloud")
                    || command.contains("speak text")){

                String text =
                        txtResult.getText().toString();

                tts.speak(
                        text,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null);

            }
            Button btnClear = findViewById(R.id.btnClear);

            btnClear.setOnClickListener(v -> {
                txtResult.setText("");
            });
            txtResult = findViewById(R.id.textView23);
            tts = new TextToSpeech(this, status -> {

                if(status == TextToSpeech.SUCCESS){

                    tts.setLanguage(Locale.US);

                }


            });


        }

    }
    @Override
    protected void onDestroy() {

        if(tts != null){

            tts.stop();
            tts.shutdown();

        }

        super.onDestroy();
    }


} // <-- class ends here