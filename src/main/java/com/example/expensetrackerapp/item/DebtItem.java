package com.example.expensetrackerapp.item;

import java.util.Date;

public class DebtItem {
    private String debtorName;
    private String debtAmount;
    private String description;
    private String dueDate;
    private String status;
    private String id;

    public DebtItem(String debtorName, String debtAmount, String description, String dueDate, String status) {
        this.debtorName = debtorName;
        this.debtAmount = debtAmount;
        this.description = description;
        this.dueDate = dueDate;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDebtorName() {
        return debtorName;
    }

    public String getAmount() {
        return debtAmount;
    }

    public String getDescription() {
        return description;
    }

    public String getDueDate() {
        return dueDate;
    }

    public String getStatus() {
        return status;
    }
}
