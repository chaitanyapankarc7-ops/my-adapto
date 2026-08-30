package com.tejyash.myadapto.activity;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.telephony.SmsManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.tejyash.myadapto.BuildConfig;
import com.tejyash.myadapto.R;
import com.tejyash.myadapto.accessibility.ScreenContentAccessibilityService;
import com.tejyash.myadapto.notifications.AdaptoNotificationListenerService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VoiceAssitentPage extends AppCompatActivity {

    private static final int REQ_SPEECH = 1;
    private static final int REQ_CONTACTS_CALL = 101;
    private static final int REQ_CONTACTS_SMS = 102;

    private TextView txtResult;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    private String screenSnapshot = "";
    private String lastSpokenResponse = "";
    private OkHttpClient httpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.voice_assitent_page);

        // Retrieve screen snapshot from extra or fallback to accessibility service cache
        screenSnapshot = getIntent().getStringExtra("screen_snapshot");
        if (screenSnapshot == null || screenSnapshot.isEmpty()) {
            screenSnapshot = ScreenContentAccessibilityService.getLastScreenText();
        }

        txtResult = findViewById(R.id.textView23);
        ImageView imgMic = findViewById(R.id.imgMic);
        Button btnClear = findViewById(R.id.btnClear);

        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                if (txtResult != null) {
                    txtResult.setText("");
                }
            });
        }

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();

        // Initialize TextToSpeech once during onCreate
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsReady = true;
                }
            }
        });

        imgMic.setOnClickListener(v -> startVoiceRecognition());

        // If launched with a screen snapshot or auto-listen intent, automatically trigger recognition
        boolean autoListen = getIntent().getBooleanExtra("auto_listen", false) || getIntent().hasExtra("screen_snapshot");
        if (autoListen) {
            new Handler(Looper.getMainLooper()).postDelayed(this::startVoiceRecognition, 300);
        }
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command or ask a question...");
        try {
            startActivityForResult(intent, REQ_SPEECH);
        } catch (Exception e) {
            if (txtResult != null) {
                txtResult.append("Speech recognition not available on this device.\n\n");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result == null || result.isEmpty()) {
                return;
            }

            String command = result.get(0);
            if (txtResult != null) {
                txtResult.append("You: " + command + "\n\n");
            }
            processCommand(command);
        }
    }

    private void processCommand(String rawCommand) {
        if (rawCommand == null) return;
        String lower = rawCommand.toLowerCase().trim();

        // 1. Stop command
        if (lower.equals("stop") || lower.contains("stop listening")) {
            if (tts != null && tts.isSpeaking()) {
                tts.stop();
            }
            finish();
            return;
        }

        // 2. Repeat command
        if (lower.equals("repeat") || lower.contains("say again")) {
            if (lastSpokenResponse != null && !lastSpokenResponse.isEmpty()) {
                speakText(lastSpokenResponse);
            } else {
                speakText("I don't have anything to repeat yet.");
            }
            return;
        }

        // 3. Screen reading
        if (lower.contains("read screen") || lower.contains("what's on screen") || lower.contains("what is on screen")) {
            String textToRead = screenSnapshot;
            if (textToRead == null || textToRead.trim().isEmpty()) {
                textToRead = ScreenContentAccessibilityService.getLastScreenText();
            }
            if (textToRead == null || textToRead.trim().isEmpty()) {
                speakAndAppend("I couldn't read anything from the screen. Please ensure the MyAdapto Screen Reader accessibility service is enabled.");
            } else {
                speakAndAppend("Screen content: " + textToRead);
            }
            return;
        }

        // 4. Notification reading
        if (lower.contains("read notification") || lower.contains("read message") ||
            lower.contains("what's my notification") || lower.contains("check notification")) {
            String notif = AdaptoNotificationListenerService.getLatestNotificationSummary();
            if (notif == null) {
                speakAndAppend("You don't have any recent notifications, or notification access is not enabled yet.");
            } else {
                speakAndAppend("Latest notification: " + notif);
            }
            return;
        }

        // 5. Dial / Phone
        if (lower.contains("call mummy")) {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            startActivity(intent);
            closeShortly();
            return;
        }
        if (lower.startsWith("call ")) {
            String contactName = rawCommand.substring(5).trim();
            callContactByName(contactName);
            return;
        }
        if (lower.equals("phone") || lower.equals("dial") || lower.equals("call") || lower.contains("dialer")) {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            startActivity(intent);
            closeShortly();
            return;
        }

        // 6. Text / SMS
        if (lower.startsWith("text ") || lower.startsWith("message ")) {
            String remainder = rawCommand.substring(rawCommand.indexOf(" ") + 1).trim();
            int firstSpace = remainder.indexOf(" ");
            if (firstSpace == -1) {
                speakAndAppend("Please say the contact name and your message, for example: text John I am on my way.");
            } else {
                String contactName = remainder.substring(0, firstSpace);
                String message = remainder.substring(firstSpace + 1);
                sendSmsToContact(contactName, message);
            }
            return;
        }

        // 7. App launcher by name
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            String appName = rawCommand.substring(rawCommand.indexOf(" ") + 1).trim();
            openAppByName(appName);
            return;
        }

        // 8. Device Hardware: Camera, Gallery, Settings, Browser, YouTube
        if (lower.contains("camera")) {
            startActivity(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
            closeShortly();
            return;
        }
        if (lower.contains("gallery") || lower.contains("photos")) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setType("image/*");
            startActivity(intent);
            closeShortly();
            return;
        }
        if (lower.equals("settings") || lower.equals("setting") || lower.contains("open settings")) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
            closeShortly();
            return;
        }
        if (lower.contains("browser") || lower.contains("chrome") || lower.contains("google")) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
            closeShortly();
            return;
        }
        if (lower.contains("youtube")) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")));
            closeShortly();
            return;
        }

        // 9. Time, Date, Day
        if (lower.contains("time")) {
            Calendar calendar = Calendar.getInstance();
            String time = String.format(Locale.getDefault(), "%02d:%02d",
                    calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
            speakAndAppend("The time is " + time);
            return;
        }
        if (lower.contains("date")) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
            String date = sdf.format(new Date());
            speakAndAppend("Today is " + date);
            return;
        }
        if (lower.contains("day")) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE", Locale.US);
            String day = sdf.format(new Date());
            speakAndAppend("Today is " + day);
            return;
        }

        // 10. Greetings
        if (lower.equals("hello") || lower.equals("hi") || lower.startsWith("hello ") || lower.startsWith("hi ")) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            String greeting;
            if (hour < 12) greeting = "Good morning! How can I help you today?";
            else if (hour < 17) greeting = "Good afternoon! How can I help you today?";
            else greeting = "Good evening! How can I help you today?";
            speakAndAppend(greeting);
            return;
        }

        // 11. Battery status
        if (lower.contains("battery")) {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (bm != null) {
                int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                boolean charging = bm.isCharging();
                speakAndAppend("Battery is at " + level + " percent" + (charging ? ", and currently charging." : "."));
            } else {
                speakAndAppend("Could not determine battery level.");
            }
            return;
        }

        // 12. Volume control
        if (lower.contains("increase volume") || lower.contains("volume up")) {
            adjustVolume(AudioManager.ADJUST_RAISE, "Volume increased");
            return;
        }
        if (lower.contains("decrease volume") || lower.contains("volume down")) {
            adjustVolume(AudioManager.ADJUST_LOWER, "Volume decreased");
            return;
        }
        if (lower.contains("mute")) {
            adjustVolume(AudioManager.ADJUST_MUTE, "Muted");
            return;
        }

        // 13. Flashlight toggle
        if (lower.contains("flashlight on") || lower.contains("torch on")) {
            toggleFlashlight(true);
            return;
        }
        if (lower.contains("flashlight off") || lower.contains("torch off")) {
            toggleFlashlight(false);
            return;
        }

        // 14. Alarms & Timers
        if (lower.contains("set alarm") || lower.contains("alarm")) {
            startActivity(new Intent(AlarmClock.ACTION_SET_ALARM));
            closeShortly();
            return;
        }
        if (lower.contains("set timer") || lower.contains("timer")) {
            startActivity(new Intent(AlarmClock.ACTION_SET_TIMER));
            closeShortly();
            return;
        }

        // 15. Read text aloud
        if (lower.contains("read text") || lower.contains("read aloud") || lower.contains("speak text")) {
            if (txtResult != null) {
                String text = txtResult.getText().toString().trim();
                if (!text.isEmpty()) {
                    speakText(text);
                } else {
                    speakText("There is no text on screen to read.");
                }
            }
            return;
        }

        // 16. Fallback to Groq AI Assistant
        askGroq(rawCommand);
    }

    private void speakAndAppend(String text) {
        lastSpokenResponse = text;
        if (txtResult != null) {
            txtResult.append("Assistant: " + text + "\n\n");
        }
        speakText(text);
    }

    private void speakText(String text) {
        lastSpokenResponse = text;
        if (ttsReady && tts != null && text != null && !text.isEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_" + System.currentTimeMillis());
        }
    }

    private void closeShortly() {
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1000);
    }

    private void adjustVolume(int direction, String message) {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
                speakAndAppend(message);
            }
        } catch (Exception e) {
            speakAndAppend("Could not adjust volume: " + e.getMessage());
        }
    }

    private void toggleFlashlight(boolean turnOn) {
        try {
            CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (cameraManager == null) {
                speakAndAppend("Flashlight not available.");
                return;
            }

            String flashCameraId = null;
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (hasFlash != null && hasFlash) {
                    flashCameraId = id;
                    break;
                }
            }

            if (flashCameraId == null) {
                speakAndAppend("No flashlight found on this device.");
                return;
            }

            cameraManager.setTorchMode(flashCameraId, turnOn);
            speakAndAppend(turnOn ? "Flashlight turned on." : "Flashlight turned off.");
        } catch (Exception e) {
            speakAndAppend("Couldn't toggle flashlight: " + e.getMessage());
        }
    }

    private void openAppByName(String appName) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        String target = appName.toLowerCase().replace(" ", "");

        for (ApplicationInfo app : apps) {
            String label = pm.getApplicationLabel(app).toString().toLowerCase().replace(" ", "");
            if (label.contains(target)) {
                Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                    speakAndAppend("Opening " + pm.getApplicationLabel(app));
                    closeShortly();
                    return;
                }
            }
        }
        speakAndAppend("Sorry, I couldn't find an app called " + appName);
    }

    private void callContactByName(String contactName) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS_CALL);
            speakAndAppend("Contacts permission is needed to call contacts. Please allow and try again.");
            return;
        }

        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                new String[]{"%" + contactName + "%"},
                null);

        if (cursor != null && cursor.moveToFirst()) {
            String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            String number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
            cursor.close();

            speakAndAppend("Calling " + name);
            Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
            startActivity(callIntent);
            closeShortly();
        } else {
            if (cursor != null) cursor.close();
            speakAndAppend("Sorry, I couldn't find a contact named " + contactName);
        }
    }

    private void sendSmsToContact(String contactName, String message) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS}, REQ_CONTACTS_SMS);
            speakAndAppend("SMS and Contacts permissions are needed to send messages. Please allow and try again.");
            return;
        }

        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                new String[]{"%" + contactName + "%"},
                null);

        if (cursor != null && cursor.moveToFirst()) {
            String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            String number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
            cursor.close();

            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(number, null, message, null, null);
                speakAndAppend("Message sent to " + name);
            } catch (Exception e) {
                speakAndAppend("Failed to send message: " + e.getMessage());
            }
        } else {
            if (cursor != null) cursor.close();
            speakAndAppend("Sorry, I couldn't find a contact named " + contactName);
        }
    }

    private void askGroq(String userQuery) {
        String apiKey = BuildConfig.GROQ_API_KEY;
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("null")) {
            speakAndAppend("AI key is not configured on this device.");
            return;
        }

        if (txtResult != null) {
            txtResult.append("Assistant: Thinking...\n\n");
        }

        try {
            JSONObject body = new JSONObject();
            body.put("model", "llama-3.3-70b-versatile");
            body.put("max_tokens", 300);

            JSONArray messages = new JSONArray();

            // System prompt
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            StringBuilder systemContent = new StringBuilder("You are a helpful, concise voice assistant in an accessibility Android app. Keep responses brief, friendly, direct, and easily spoken aloud without markdown symbols.");
            if (screenSnapshot != null && !screenSnapshot.trim().isEmpty()) {
                String snippet = screenSnapshot.length() > 600 ? screenSnapshot.substring(0, 600) + "..." : screenSnapshot;
                systemContent.append("\n[Current Screen Content Context: ").append(snippet).append("]");
            }
            systemMsg.put("content", systemContent.toString());
            messages.put(systemMsg);

            // User prompt
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userQuery);
            messages.put(userMsg);

            body.put("messages", messages);

            RequestBody requestBody = RequestBody.create(
                    body.toString(), MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        speakAndAppend("Could not connect to AI assistant. Please check your internet connection.");
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        runOnUiThread(() -> {
                            if (code == 401) {
                                speakAndAppend("AI key is invalid or unauthorized.");
                            } else if (code == 429) {
                                speakAndAppend("AI rate limit reached. Please try again in a moment.");
                            } else {
                                speakAndAppend("AI service returned an error (" + code + "). Please try again.");
                            }
                        });
                        return;
                    }

                    if (response.body() == null) {
                        runOnUiThread(() -> speakAndAppend("Empty response received from AI server."));
                        return;
                    }

                    String responseStr = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseStr);
                        if (json.has("choices")) {
                            JSONArray choices = json.getJSONArray("choices");
                            if (choices.length() > 0) {
                                String reply = choices.getJSONObject(0)
                                        .getJSONObject("message")
                                        .getString("content");
                                runOnUiThread(() -> speakAndAppend(reply.trim()));
                                return;
                            }
                        }
                        runOnUiThread(() -> speakAndAppend("Could not parse AI response."));
                    } catch (Exception e) {
                        runOnUiThread(() -> speakAndAppend("Error reading AI response."));
                    }
                }
            });

        } catch (Exception e) {
            speakAndAppend("Failed to create AI request.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if ((requestCode == REQ_CONTACTS_CALL || requestCode == REQ_CONTACTS_SMS) && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission granted. Please repeat your command.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }
}