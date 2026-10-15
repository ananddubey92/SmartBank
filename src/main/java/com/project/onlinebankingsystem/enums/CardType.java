package com.project.onlinebankingsystem.enums;

public enum CardType {
    RUPAY_PLATINUM("RuPay Platinum", "RUPAY"),
    VISA_PLATINUM("Visa Platinum", "VISA"),
    MASTERCARD_WORLD("Mastercard World", "MASTERCARD"),
    RUPAY_CLASSIC("RuPay Classic", "RUPAY");

    private final String displayName;
    private final String network;

    CardType(String displayName, String network) {
        this.displayName = displayName;
        this.network = network;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNetwork() {
        return network;
    }
}
