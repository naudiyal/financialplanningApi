package com.naudi.financialplanningapi.model;

public enum TimelineType {
    MID_TO_MID,
    START_TO_END;

    public static TimelineType fromStoredValue(String value) {
        if (value == null || value.isBlank()) {
            return MID_TO_MID;
        }

        try {
            return TimelineType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return MID_TO_MID;
        }
    }
}