package com.example.expensetrackerapp.item;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TransactionItem {
    private String type;
    private String category;
    private double amount;
    private String description;
    private String month;
    private long timestamp;

    public TransactionItem(String type, String category, double amount, String description, String month, long timestamp) {
        this.type = type;
        this.category = category;
        this.amount = amount;
        this.description = description;
        this.month = month;
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public String getCategory() {
        return category;
    }

    public double getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getMonth() {
        return month;
    }
    public long getTimestamp(){
        return timestamp;
    }
    public String getFormattedTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
