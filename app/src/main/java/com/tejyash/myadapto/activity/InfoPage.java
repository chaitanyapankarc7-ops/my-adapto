package com.tejyash.myadapto.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.view.View;
import android.widget.Toast;

import com.tejyash.myadapto.R;

public class InfoPage extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.infopage);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.button5), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        CardView card1;
        card1 = findViewById(R.id.card1);
        card1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(InfoPage.this, "Card click", Toast.LENGTH_SHORT).show();
                Intent i = new Intent(InfoPage.this, SizeEditingPage.class);
                startActivity(i);

            }
        });
        CardView card2;
        card2 = findViewById(R.id.card2);
        card2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(InfoPage.this, "Card Click", Toast.LENGTH_SHORT).show();
                Intent d = new Intent(InfoPage.this, SizeEditingPage.class);
                startActivity(d);
            }
        });
        CardView card3;
        card3 = findViewById(R.id.card3);
        card3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(InfoPage.this, "Card Click", Toast.LENGTH_SHORT).show();
                Intent i = new Intent(InfoPage.this, AssitentInfoage.class);
                startActivity(i);
            }
        });
        CardView card4;
        card4= findViewById(R.id.card4);
        card4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(InfoPage.this, "Card Click", Toast.LENGTH_SHORT).show();
                Intent i = new Intent(InfoPage.this, SizeEditingPage.class);
                startActivity(i);
            }
        });
    }
}
