package org.example.moono_backend.batch.billing.dto;

import java.util.List;

public record BillingDetailsJson(
    List<Item> discounts,
    List<Item> overages,
    int baseFee
) {
    public record Item(String name, int amount) {}
}
