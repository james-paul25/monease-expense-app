package com.example.expensetrackerapp.item;

public class CategoryItem {
    private String name;
    private double amount;
    private double budget;

    public CategoryItem(String name, double amount) {
        this.name = name;
        this.amount = amount;
        //this.budget = budget;
    }

    public String getName() {
        return name;
    }

    public double getAmount() {
        return amount;
    }

    public double getBudget() {
        return budget;
    }
    public void addAmount(double amount) {
        this.amount += amount;
    }
}
