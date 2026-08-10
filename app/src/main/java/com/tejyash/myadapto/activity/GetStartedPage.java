package com.tejyash.myadapto.activity;


import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.launcher.HomeActivity;
import com.tejyash.myadapto.utils.Constants;

public class GetStartedPage extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_ONBOARDING, MODE_PRIVATE);
        boolean setupComplete   = prefs.getBoolean(Constants.KEY_SETUP_COMPLETE, false);

        if (setupComplete) {
            // User already did setup — go straight to the launcher home
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.getstartedpage);
        Animation press = AnimationUtils.loadAnimation(this, R.anim.button_press);
        Animation release = AnimationUtils.loadAnimation(this, R.anim.button_relese);
        Button button =findViewById(R.id.button);
        button.setOnTouchListener((v, event) -> {

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    button.startAnimation(press);
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    button.startAnimation(release);
                    break;
            }

            return false; // Important: lets the click listener still work
        });

        button.setOnClickListener(v ->
                startActivity(new Intent(GetStartedPage.this, InfoPage.class)));
    }
}