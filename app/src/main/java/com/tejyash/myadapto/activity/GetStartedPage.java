package com.tejyash.myadapto.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.launcher.HomeActivity;

public class GetStartedPage extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("AdaptoPrefs", MODE_PRIVATE);
        boolean setupComplete   = prefs.getBoolean("setupComplete", false);

        if (setupComplete) {
            // User already did setup — go straight to the launcher home
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.getstartedpage);

        Button button = findViewById(R.id.button);
        button.setOnClickListener(v ->
                startActivity(new Intent(GetStartedPage.this, InfoPage.class)));
    }
}
