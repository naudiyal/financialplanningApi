package com.naudi.financialplanningapi.model;

import java.time.LocalDate;

public record CyclePeriod(
    LocalDate startDate,
    LocalDate endDate
) {
}