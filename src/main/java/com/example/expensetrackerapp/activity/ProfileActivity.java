package com.example.expensetrackerapp.activity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.expensetrackerapp.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {
    DashboardActivity dashboardActivity;
    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private FirebaseFirestore firestore;
    private GoogleSignInClient mGoogleSignInClient;
    private LinearLayout accountButton, settingsButton,
            exportDataButton, logoutButton,
            deptHistory;
    private ShapeableImageView imageView;
    private EditText etDebtorName, etDebtAmount,etDebtDueDate, etDebtDescription;
    private Button saveDebtButton;
    private Button btnBack;
    private TextView username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        dashboardActivity = new DashboardActivity();

        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();
        firestore = FirebaseFirestore.getInstance();

        username = findViewById(R.id.username_text);
        imageView = findViewById(R.id.iv_profile);

        btnBack = findViewById(R.id.button_back);

        if (user != null) {
            username.setText(user.getDisplayName());
            if (user.getPhotoUrl() != null) {
                Glide.with(this)
                        .load(user.getPhotoUrl().toString())
                        .placeholder(R.drawable.profile_placeholder)
                        .into(imageView);
            }
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        exportDataButton = findViewById(R.id.export_data_button);
        logoutButton = findViewById(R.id.logout_button);
        deptHistory = findViewById(R.id.debt_history);


        exportDataButton.setOnClickListener(v -> exportData());
        deptHistory.setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, DebtHistoryActivity.class));
            finish();
        });
        logoutButton.setOnClickListener(v -> logoutUser());

        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, DashboardActivity.class));
            finish();
        });
    }
    private void exportData() {
        Toast.makeText(this, "Exporting data...", Toast.LENGTH_SHORT).show();
        if (user == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();
        firestore.collection("users").document(userId)
                .collection("transactions")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "No transactions to export!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    JSONArray jsonArray = new JSONArray();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {

                        JSONObject transaction = new JSONObject();
                        try {
                            transaction.put("amount", doc.getDouble("amount"));
                            transaction.put("category", doc.getString("category"));
                            transaction.put("type", doc.getString("type"));
                            transaction.put("description", doc.getString("description"));
                            transaction.put("timestamp", doc.getDate("timestamp").toString());

                            jsonArray.put(transaction);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    try {
                        saveToFile(jsonArray.toString(4));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to export data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void logoutUser() {
        // Handle logout logic (e.g., Firebase sign-out or clearing shared preferences)
        Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show();
        mAuth.signOut(); // Sign out from Firebase Auth

        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Toast.makeText(ProfileActivity.this, "Logged out successfully!", Toast.LENGTH_SHORT).show();

            // Redirect to login screen
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        finish(); // Close activity
    }
    private void shareFile(File file) {
        Uri fileUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent, "Share JSON file"));
    }
    private void saveToFile(String jsonData) {
        try {
            File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MoneaseExpenseTracker");
            if (!folder.exists()) {
                folder.mkdirs();
            }

            String fileName = "transaction" + System.currentTimeMillis() + ".doc";
            File file = new File(folder, fileName);
            FileWriter writer = new FileWriter(file);
            writer.write("\t\t\t\t\t\t\tFinancial Report\n\n" + jsonData);
            writer.flush();
            writer.close();

            Toast.makeText(this, "Data exported to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();

            shareFile(file);
        } catch (Exception e) {
            Toast.makeText(this, "Error saving file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private void clearFields() {
        etDebtorName.setText("");
        etDebtAmount.setText("");
        etDebtDueDate.setText("");
        etDebtDescription.setText("");
    }

}
