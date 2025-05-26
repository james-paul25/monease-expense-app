package com.example.expensetrackerapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetrackerapp.R;
import com.example.expensetrackerapp.item.TransactionItem;

import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private List<TransactionItem> transactionList;

    public TransactionAdapter(List<TransactionItem> transactionList) {
        this.transactionList = transactionList;
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.transaction_item, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        TransactionItem transactionItem = transactionList.get(position);
        String type = transactionItem.getType();
        double amount = transactionItem.getAmount();

        if (type.equalsIgnoreCase("Expense")) {
            holder.tvAmount.setText("- ₱" + amount);
            holder.tvAmount.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
        } else {
            holder.tvAmount.setText("+ ₱" + amount);
            holder.tvAmount.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
        }

        holder.tvType.setText(transactionItem.getType() + " - " + transactionItem.getCategory());
        holder.tvTimestamp.setText(transactionItem.getFormattedTimestamp());
        holder.tvDescription.setText(transactionItem.getDescription());
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    public static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView tvType, tvTimestamp, tvAmount, tvDescription;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.transactionType);
            tvTimestamp = itemView.findViewById(R.id.transactionTimestamp);
            tvAmount = itemView.findViewById(R.id.transactionAmount);
            tvDescription = itemView.findViewById(R.id.transactionDescription);
        }
    }
}
