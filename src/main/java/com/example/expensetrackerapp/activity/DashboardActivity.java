package com.example.expensetrackerapp.activity;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.example.expensetrackerapp.R;
import com.example.expensetrackerapp.adapter.TransactionAdapter;
import com.example.expensetrackerapp.item.DebtItem;
import com.example.expensetrackerapp.item.TransactionItem;
import com.example.expensetrackerapp.utils.DebtReminderWorker;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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

public class DashboardActivity extends AppCompatActivity {

    TransactionAdapter transactionAdapter;
    List<TransactionItem> transactionList;

    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private FirebaseFirestore firestore;

    private TextView tvBalanceAmount, tvIncomeAmount, tvExpenseAmount, tvWelcome, tvSeeAll;
    private ShapeableImageView imageView;
    private RecyclerView rvTransactions;
    private BottomNavigationView bottomNavigationView;
    private static final String CHANNEL_ID = "debt_channel_id";

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        user = mAuth.getCurrentUser();

        scheduleDebtReminderWorker();

        tvWelcome = findViewById(R.id.tv_welcome);
        imageView = findViewById(R.id.iv_profile);
        tvBalanceAmount = findViewById(R.id.tv_balance_amount);
        tvIncomeAmount = findViewById(R.id.tv_income_amount);
        tvExpenseAmount = findViewById(R.id.tv_expense_amount);
        rvTransactions = findViewById(R.id.rv_transactions);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        tvSeeAll = findViewById(R.id.tv_see_all);

        if (user != null) {
            tvWelcome.setText("Hello, " + user.getDisplayName() + "!");
            if (user.getPhotoUrl() != null) {
                Glide.with(this)
                        .load(user.getPhotoUrl().toString())
                        .placeholder(R.drawable.profile_placeholder)
                        .into(imageView);
            }
            loadTransactions();
        }

        fetchDebtHistory();

        transactionList = new ArrayList<>();
        transactionAdapter = new TransactionAdapter(transactionList);
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvTransactions.setHasFixedSize(true);
        rvTransactions.setAdapter(transactionAdapter);
        loadHistoryTransactions(null);

        tvSeeAll.setOnClickListener(v -> {
            user = mAuth.getCurrentUser();
            Intent intent = new Intent(DashboardActivity.this, TransactionHistoryActivity.class);
            startActivity(intent);
        });

        setBottomNavigationView();
    }

    public void addTransactionToFirestore(double amount, String category, String type, String description) {
        if (user != null) {
            String userId = user.getUid();

            firestore.collection("users").document(userId)
                    .collection("transactions")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        double totalIncome = 0.0;
                        double totalExpense = 0.0;

                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Double docAmount = doc.getDouble("amount");
                            String docType = doc.getString("type");

                            if (docAmount == null) docAmount = 0.0;
                            if (docType != null && (docType.equalsIgnoreCase("Allowance") || docType.equalsIgnoreCase("Income"))) {
                                totalIncome += docAmount;
                            } else if (docType != null && docType.equalsIgnoreCase("Expense")) {
                                totalExpense += docAmount;
                            }
                        }

                        double currentBalance = totalIncome - totalExpense;

                        if (type.equalsIgnoreCase("Expense") && amount > currentBalance) {
                            Toast.makeText(this, "Insufficient balance!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Map<String, Object> transaction = new HashMap<>();
                        transaction.put("amount", amount);
                        transaction.put("category", category);
                        transaction.put("type", type);
                        transaction.put("description", description);
                        transaction.put("timestamp", FieldValue.serverTimestamp());

                        firestore.collection("users").document(userId)
                                .collection("transactions").add(transaction)
                                .addOnSuccessListener(documentReference -> {
                                    Toast.makeText(this, "Transaction Added!", Toast.LENGTH_SHORT).show();
                                    loadTransactions();
                                    loadHistoryTransactions(null);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to fetch balance!", Toast.LENGTH_SHORT).show());
        } else {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
        }
    }


    public void showTransactionDialog(Context context) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
        View view = getLayoutInflater().inflate(R.layout.transaction_form, null);

        EditText etAmount = view.findViewById(R.id.etAmount);
        AutoCompleteTextView autoCompleteCategory = view.findViewById(R.id.autoCompleteCategory);
        AutoCompleteTextView autoCompleteType = view.findViewById(R.id.autoCompleteType);
        EditText etDescription = view.findViewById(R.id.etdescription);
        Button btnSaveTransaction = view.findViewById(R.id.btnSaveTransaction);

        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.show();

        String[] categories = {"Food", "Transportation", "School", "Entertainment", "Bills", "Shopping", "Other"};
        ArrayAdapter<String> categoriesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, categories);
        autoCompleteCategory.setAdapter(categoriesAdapter);
        autoCompleteCategory.setThreshold(0);
        autoCompleteCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                autoCompleteCategory.showDropDown();
            }
        });

        String[] types = {"Allowance", "Income", "Expense"};
        ArrayAdapter<String> typesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, types);
        autoCompleteType.setAdapter(typesAdapter);
        autoCompleteType.setThreshold(0);
        autoCompleteType.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                autoCompleteType.showDropDown();
            }
        });


        btnSaveTransaction.setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString();
            String category = autoCompleteCategory.getText().toString();
            String type = autoCompleteType.getText().toString();
            String description = etDescription.getText().toString();

            if (amountStr.isEmpty() || category.isEmpty() || type.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            double amount = Double.parseDouble(amountStr);

            addTransactionToFirestore(amount, category, type, description);
            bottomSheetDialog.dismiss();
        });
    }

    public void loadTransactions() {
        if (user == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();
        firestore.collection("users").document(userId)
                .collection("transactions")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    double totalIncome = 0.0;
                    double totalExpense = 0.0;

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Double amount = doc.getDouble("amount");
                        String type = doc.getString("type");

                        if (amount == null) amount = 0.0;
                        if (type != null && (type.equalsIgnoreCase("Allowance") || type.equalsIgnoreCase("Income"))) {
                            totalIncome += amount;
                        } else if (type != null && type.equalsIgnoreCase("Expense")) {
                            totalExpense += amount;
                        }
                    }

                    double balance = totalIncome - totalExpense;
                    tvIncomeAmount.setText(String.format("₱ %.2f", totalIncome));
                    tvExpenseAmount.setText(String.format("₱ %.2f", totalExpense));
                    tvBalanceAmount.setText(String.format("₱ %.2f", balance));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load transactions", Toast.LENGTH_SHORT).show());
    }


    public void loadHistoryTransactions(String filterMonth) {
        if (user == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();
        firestore.collection("users").document(userId)
                .collection("transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(3)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    transactionList.clear();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String category = doc.getString("category");
                        String type = doc.getString("type");
                        String description = doc.getString("description");
                        double amount = doc.getDouble("amount");
                        Timestamp timestamp = doc.getTimestamp("timestamp");

                        long timeInMillis = timestamp.toDate().getTime();
                        String month = getMonthFromTimestamp(timeInMillis);

                        if (filterMonth == null || month.equalsIgnoreCase(filterMonth)) {
                            transactionList.add(new TransactionItem(type, category, amount, description, month, timeInMillis));
                        }
                    }
                    transactionAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load transactions", Toast.LENGTH_SHORT).show());
    }

    private String getMonthFromTimestamp(Object timestamp) {
        try {
            if (timestamp instanceof com.google.firebase.Timestamp) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(((com.google.firebase.Timestamp) timestamp).toDate().getTime());
                return new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
            } else if (timestamp instanceof Long) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis((Long) timestamp);
                return new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
            }
        } catch (Exception e) {
            Log.e("TransactionHistory", "Unknown timestamp format: " + timestamp, e);
        }
        return "";
    }

    private void scheduleDebtReminderWorker() {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                DebtReminderWorker.class,
                1, TimeUnit.DAYS
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "DebtReminderWork",
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
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

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String debtorName = doc.getString("debtorName") != null ? doc.getString("debtorName") : "Unknown";
                        String dueDate = doc.getString("dueDate");

                        String debtAmount = doc.getString("debtAmount");
                        String debtStatus = doc.getString("debtStatus");
                        String lastNotified = doc.getString("lastNotified");

                        String now = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

                        if(Objects.equals(debtStatus, "NOT PAID")){
                            try {
                                Date duesDate = sdf.parse(dueDate);
                                if (duesDate != null) {

                                    if (lastNotified == null || !lastNotified.equals(now)) {
                                        sendDebtDueNotification(debtorName, debtAmount, dueDate);

                                        doc.getReference().update("lastNotified", now);
                                    }
                                }
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load transactions", Toast.LENGTH_SHORT).show());
    }

    private void sendDebtDueNotification(String debtorName, String debtAmount, String dueDate) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String formattedDueDate = dueDate;
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
            Date date = inputFormat.parse(dueDate);
            if (date != null) {
                formattedDueDate = outputFormat.format(date);
                Log.d("Format", formattedDueDate);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle("Debt Due Soon")
                    .setContentText("Debt of ₱" + debtAmount + " from " + debtorName + " is due on " + formattedDueDate)
                    .setAutoCancel(true);
        } else {
            builder = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle("Debt Due Soon")
                    .setContentText("Debt of ₱" + debtAmount + " from " + debtorName + " is due on " + formattedDueDate)
                    .setAutoCancel(true);
        }

        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, builder.build());
    }
    private void setBottomNavigationView(){
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                Toast.makeText(this, "Home Clicked", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.nav_transactions) {
                user = mAuth.getCurrentUser();
                Intent intent = new Intent(DashboardActivity.this, TransactionHistoryActivity.class);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.nav_statistics) {
                user = mAuth.getCurrentUser();
                Intent intent = new Intent(DashboardActivity.this, FinancialReportActivity.class);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.nav_profile) {
                user = mAuth.getCurrentUser();
                Intent intent = new Intent(DashboardActivity.this, ProfileActivity.class);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.nav_add_transaction) {
                showTransactionDialog(this);
                return true;
            }
            return false;
        });
    }

}
