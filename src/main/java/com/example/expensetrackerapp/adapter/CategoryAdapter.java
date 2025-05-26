package com.example.expensetrackerapp.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetrackerapp.item.CategoryItem;
import com.example.expensetrackerapp.R;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private final Context context;
    private final List<CategoryItem> categoryList;
    private final boolean isExpense;
    private double maxAmount;

    public CategoryAdapter(Context context, List<CategoryItem> categoryList, boolean isExpense, double maxAmount) {
        this.context = context;
        this.categoryList = categoryList;
        this.isExpense = isExpense;
        this.maxAmount = maxAmount;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.category_item, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryItem category = categoryList.get(position);

        holder.tvCategoryName.setText(category.getName());
        holder.tvAmount.setText("₱" + category.getAmount());

        if (maxAmount > 0) {
            int progress = (int) ((category.getAmount() / maxAmount) * 100);
            holder.progressBar.setProgress(progress);
        } else {
            holder.progressBar.setProgress(0);
        }

        holder.tvAmount.setTextColor(isExpense ? Color.RED : Color.GREEN);
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategoryName, tvAmount;
        ProgressBar progressBar;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            progressBar = itemView.findViewById(R.id.progressBar);
        }
    }
}
