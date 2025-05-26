package com.example.expensetrackerapp.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetrackerapp.R;
import com.example.expensetrackerapp.activity.DashboardActivity;
import com.example.expensetrackerapp.item.DebtItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DebtHistoryAdapter extends RecyclerView.Adapter<DebtHistoryAdapter.ViewHolder> {

    private List<DebtItem> debtList;

    private Context context;
    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private FirebaseFirestore firestore;

    public DebtHistoryAdapter(Context context, List<DebtItem> debtList) {
        this.context = context;
        this.debtList = debtList;
        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();
        firestore = FirebaseFirestore.getInstance();
    }


    @NonNull
    @Override
    public DebtHistoryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.debt_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DebtHistoryAdapter.ViewHolder holder, int position) {
        DebtItem item = debtList.get(position);

        holder.debtorName.setText(item.getDebtorName());
        holder.debtAmount.setText("₱" + item.getAmount());
        holder.debtDescription.setText(item.getDescription());
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat outputFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
        try {
            Date date = inputFormat.parse(item.getDueDate());
            String formattedDate = outputFormat.format(date);
            holder.dueDate.setText(formattedDate);
        } catch (ParseException e) {
            holder.dueDate.setText(item.getDueDate());
        }

        holder.debtStatus.setText(item.getStatus());
        holder.debtStatus.setTextColor(Color.parseColor("#F44336"));

        holder.markAsPaidButton.setOnClickListener(v -> {
            debtToTransaction(item, holder);
        });
    }

    private void debtToTransaction(DebtItem item, @NonNull DebtHistoryAdapter.ViewHolder holder) {
        String docId = item.getId();
        String userId = user.getUid();
        String type = "Expense";
        double amount = Double.parseDouble(item.getAmount());
        String debtorName = item.getDebtorName();

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
                    transaction.put("category", "Other");
                    transaction.put("type", "Expense");
                    transaction.put("description", "Pay the debt from " + debtorName);
                    transaction.put("timestamp", FieldValue.serverTimestamp());

                    firestore.collection("users").document(userId)
                            .collection("transactions").add(transaction)
                            .addOnSuccessListener(documentReference -> {
                                firestore.collection("users").document(userId)
                                        .collection("debt").document(docId)
                                        .update("debtStatus", "Paid")
                                        .addOnSuccessListener(aVoid -> {
                                            holder.debtStatus.setText("Paid");
                                            holder.debtStatus.setTextColor(Color.parseColor("#008000"));
                                            holder.markAsPaidButton.setVisibility(View.GONE);
                                            Toast.makeText(context, "Marked as Paid", Toast.LENGTH_SHORT).show();
                                            Toast.makeText(context, "Transaction added", Toast.LENGTH_SHORT).show();

                                            if (context instanceof DashboardActivity) {
                                                ((DashboardActivity) context).loadTransactions();
                                                ((DashboardActivity) context).loadHistoryTransactions(null);
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(context, "Failed to update debt status", Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(context, "Failed to add transaction", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Failed to load transactions", Toast.LENGTH_SHORT).show();
                });
    }


    @Override
    public int getItemCount() {
        return debtList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView debtorName, debtAmount, debtDescription, dueDate, debtStatus;
        Button markAsPaidButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            debtorName = itemView.findViewById(R.id.deptorName);
            debtAmount = itemView.findViewById(R.id.debtAmount);
            debtDescription = itemView.findViewById(R.id.debtDescription);
            dueDate = itemView.findViewById(R.id.dueDate);
            debtStatus = itemView.findViewById(R.id.debtStatus);
            markAsPaidButton = itemView.findViewById(R.id.btnMarkAsPaid);
        }
    }

}
