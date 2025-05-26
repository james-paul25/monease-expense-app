package com.example.expensetrackerapp.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.expensetrackerapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AccountActivity extends AppCompatActivity{

    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private FirebaseFirestore firestore;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_layout);

        btnBack = findViewById(R.id.btnBack);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        user = mAuth.getCurrentUser();

        if(user == null) return;

        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(AccountActivity.this, ProfileActivity.class));
            finish();
        });
    }
}
