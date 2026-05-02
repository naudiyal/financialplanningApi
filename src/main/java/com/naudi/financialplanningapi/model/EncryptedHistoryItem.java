package com.naudi.financialplanningapi.model;

public record EncryptedHistoryItem(
    String timelineType,
    String cycleStartDate,
    String cycleEndDate,
    String encryptedHistoryData,
    String encryptionIv
) {
}
