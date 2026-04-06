package com.naudi.financialplanningapi.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CycleSlot {
    CURRENT("current"),
    PREVIOUS("previous");

    private final String wireValue;

    CycleSlot(String wireValue) {
        this.wireValue = wireValue;
    }

    public static CycleSlot fromParameter(String value) {
        if (value == null || value.isBlank()) {
            return CURRENT;
        }

        for (CycleSlot cycleSlot : values()) {
            if (cycleSlot.wireValue.equalsIgnoreCase(value)) {
                return cycleSlot;
            }
        }

        throw new IllegalArgumentException("Unsupported cycle: " + value);
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}