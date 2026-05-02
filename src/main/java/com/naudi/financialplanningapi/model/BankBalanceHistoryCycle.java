package com.naudi.financialplanningapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BankBalanceHistoryCycle(
    CyclePeriod cycle,
    List<BankBalanceHistoryPoint> banks,
    String encryptedHistoryData,
    String encryptionIv
) {
}