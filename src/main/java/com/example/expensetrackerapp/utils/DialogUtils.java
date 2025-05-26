package com.example.expensetrackerapp.utils;

import static android.view.View.inflate;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.expensetrackerapp.R;
import com.example.expensetrackerapp.adapter.TransactionAdapter;
import com.example.expensetrackerapp.item.TransactionItem;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DialogUtils {

    private List<TransactionItem> transactionList;
    private TransactionAdapter transactionAdapter;
    private String currentFilterMonth = null;

    public void addTransactionToFirestore(Context context, FirebaseFirestore firestore, FirebaseUser user,
                                          double amount, String category, String type, String description) {
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
                            Toast.makeText(context, "Insufficient balance!", Toast.LENGTH_SHORT).show();
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
                                    Toast.makeText(context, "Transaction Added!", Toast.LENGTH_SHORT).show();
                                    loadTransactions(context, firestore, user);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(context, "Failed to fetch balance!", Toast.LENGTH_SHORT).show());
        } else {
            Toast.makeText(context, "User not logged in!", Toast.LENGTH_SHORT).show();
        }
    }
    public void showTransactionDialog(Context context, FirebaseFirestore firestore, FirebaseUser user) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
        View view = View.inflate(context,R.layout.transaction_form, null);

        EditText etAmount = view.findViewById(R.id.etAmount);
        AutoCompleteTextView autoCompleteCategory = view.findViewById(R.id.autoCompleteCategory);
        AutoCompleteTextView autoCompleteType = view.findViewById(R.id.autoCompleteType);
        EditText etDescription = view.findViewById(R.id.etdescription);
        Button btnSaveTransaction = view.findViewById(R.id.btnSaveTransaction);

        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.show();

        String[] categories = {"Food", "Transportation", "School", "Entertainment", "Bills", "Shopping", "Other"};
        ArrayAdapter<String> categoriesAdapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, categories);
        autoCompleteCategory.setAdapter(categoriesAdapter);
        autoCompleteCategory.setThreshold(0);
        autoCompleteCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                autoCompleteCategory.showDropDown();
            }
        });

        String[] types = {"Allowance", "Income", "Expense"};
        ArrayAdapter<String> typesAdapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, types);
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
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            double amount = Double.parseDouble(amountStr);

            addTransactionToFirestore(context, firestore, user, amount, category, type, description);
            bottomSheetDialog.dismiss();
        });
    }

    private void loadTransactions(Context context, FirebaseFirestore firestore, FirebaseUser user) {
        if (user == null) {
            Toast.makeText(context, "User not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();
        firestore.collection("users").document(userId)
                .collection("transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
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

                        transactionList.add(new TransactionItem(type, category, amount, description, month, timeInMillis));
                    }
                    transactionAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Toast.makeText(context, "Failed to load transactions", Toast.LENGTH_SHORT).show());
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


}
