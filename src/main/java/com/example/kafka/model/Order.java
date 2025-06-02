package com.example.kafka.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Order {
    public String id;
    public double amount;
    public String status;

    @JsonCreator
    public Order(
        @JsonProperty("id") String id,
        @JsonProperty("amount") double amount,
        @JsonProperty("status") String status
    ) {
        this.id = id;
        this.amount = amount;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Order{id='%s', amount=%.2f, status='%s'}".formatted(id, amount, status);
    }
}
