package com.example.expensetrackerapp.activity;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetrackerapp.R;
import com.example.expensetrackerapp.adapter.DebtHistoryAdapter;
import com.example.expensetrackerapp.adapter.TransactionAdapter;
import com.example.expensetrackerapp.item.DebtItem;
import com.example.expensetrackerapp.item.TransactionItem;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class DebtHistoryActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private FirebaseFirestore firestore;
    private Button btnBack, btnReload, saveDebtButton;
    private RecyclerView rvDebtHistory;
    private List<DebtItem> debtItemList;
    private DebtHistoryAdapter debtHistoryAdapter;
    private FloatingActionButton fab;
    private EditText etDebtorName, etDebtAmount,etDebtDueDate, etDebtDescription;
    private static final String CHANNEL_ID = "debt_channel_id";

    @Override
    protected void onCreate(Bundle saveInstanceState){
        super.onCreate(saveInstanceState);
        setContentView(R.layout.debt_history);

        btnBack = findViewById(R.id.btn_back);
        rvDebtHistory = findViewById(R.id.rv_dept_history);
        btnReload = findViewById(R.id.btn_reload);
        fab = findViewById(R.id.fab_add_debt);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        user = mAuth.getCurrentUser();

        createNotificationChannel();

        debtItemList = new ArrayList<>();
        debtHistoryAdapter = new DebtHistoryAdapter(this,debtItemList);
        rvDebtHistory.setLayoutManager(new LinearLayoutManager(this));
        rvDebtHistory.setAdapter(debtHistoryAdapter);

        if(user == null){
            Toast.makeText(this, "User not login", Toast.LENGTH_SHORT).show();
        }

        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
            finish();
        });

        fab.setOnClickListener(v -> showAddDebtForm());
        btnReload.setOnClickListener(v -> fetchDebtHistory());


        fetchDebtHistory();
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            CharSequence name = "Debt Notifications";
            String description = "Notifications for debt reminders";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void sendDebtAddedNotification(String debtorName, String debtAmount) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Notification.Builder builder = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle("New Debt Added")
                    .setContentText("You added a debt of ₱" + debtAmount + " from " + debtorName)
                    .setAutoCancel(true);
        }

        notificationManager.notify(1, builder.build());
    }

    private void showAddDebtForm(){
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.add_debt_form, null);

        etDebtorName = view.findViewById(R.id.etDebtorName);
        etDebtAmount = view.findViewById(R.id.etDebtAmount);
        etDebtDueDate = view.findViewById(R.id.etDebtDueDate);
        etDebtDescription = view.findViewById(R.id.etDebtDescription);
        saveDebtButton = view.findViewById(R.id.btnSaveDebt);

        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.show();

        etDebtDueDate.setOnClickListener(v -> showDatePicker());

        saveDebtButton.setOnClickListener(v -> {
            addDebtToFirestore();
            bottomSheetDialog.dismiss();
        });

    }
    private void addDebtToFirestore() {
        String debtorName = etDebtorName.getText().toString().trim();
        String debtAmount = etDebtAmount.getText().toString().trim();
        String dueDate = etDebtDueDate.getText().toString().trim();
        String debtDescription = etDebtDescription.getText().toString().trim();

        if (debtorName.isEmpty() || debtAmount.isEmpty() || dueDate.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (user != null) {
            String userId = user.getUid();

            firestore.collection("users").document(userId)
                    .collection("debt")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {

                        Map<String, Object> transaction = new HashMap<>();
                        transaction.put("debtorName", debtorName);
                        transaction.put("debtAmount", debtAmount);
                        transaction.put("dueDate", dueDate);
                        transaction.put("debtDescription", debtDescription);
                        transaction.put("debtStatus", "NOT PAID");
                        transaction.put("lastNotified", "");

                        firestore.collection("users").document(userId)
                                .collection("debt").add(transaction)
                                .addOnSuccessListener(documentReference -> {
                                    Toast.makeText(this, "Debt Added Successfully!", Toast.LENGTH_SHORT).show();
                                    fetchDebtHistory();
                                    clearFields();

                                    sendDebtAddedNotification(debtorName, debtAmount);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    });

        } else {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
        }
    }
    private void showDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                DebtHistoryActivity.this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int selectedYear, int selectedMonth, int selectedDay) {
                        String selectedDate = selectedYear + "-" +
                                String.format("%02d", selectedMonth + 1) + "-" +
                                String.format("%02d", selectedDay);
                        etDebtDueDate.setText(selectedDate);
                    }
                },
                year, month, day
        );

        datePickerDialog.show();
    }
    @SuppressLint("NotifyDataSetChanged")
    private void fetchDebtHistory(){
        if(user == null){
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
        }

        String userId = user.getUid();
        firestore.collection("users").document(userId)
                .collection("debt")
                .orderBy("dueDate", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    debtItemList.clear();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String debtorName = doc.getString("debtorName") != null ? doc.getString("debtorName") : "Unknown";
                        String dueDate = doc.getString("dueDate");

                        String debtDescription = doc.getString("debtDescription");
                        String debtAmount = doc.getString("debtAmount");
                        String debtStatus = doc.getString("debtStatus");

                        if(Objects.equals(debtStatus, "NOT PAID")){
                            DebtItem item = new DebtItem(debtorName, debtAmount, debtDescription, dueDate, debtStatus);
                            item.setId(doc.getId());
                            debtItemList.add(item);
                        }
                    }
                    debtHistoryAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load transactions", Toast.LENGTH_SHORT).show());
    }
    private void clearFields() {
        etDebtorName.setText("");
        etDebtAmount.setText("");
        etDebtDueDate.setText("");
        etDebtDescription.setText("");
    }

}
