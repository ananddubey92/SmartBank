package com.project.onlinebankingsystem.enums;

public enum ExpenseCategory {
    FOOD("Food", "fa-utensils", "#10b981"),
    SHOPPING("Shopping", "fa-bag-shopping", "#ec4899"),
    BILLS("Bills", "fa-file-invoice-dollar", "#f59e0b"),
    TRAVEL("Travel", "fa-plane-departure", "#06b6d4"),
    EDUCATION("Education", "fa-graduation-cap", "#8b5cf6"),
    ENTERTAINMENT("Entertainment", "fa-film", "#f43f5e"),
    HEALTHCARE("Healthcare", "fa-heart-pulse", "#14b8a6"),
    OTHER("Other", "fa-receipt", "#64748b");

    private final String displayName;
    private final String icon;
    private final String color;

    ExpenseCategory(String displayName, String icon, String color) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }
}
