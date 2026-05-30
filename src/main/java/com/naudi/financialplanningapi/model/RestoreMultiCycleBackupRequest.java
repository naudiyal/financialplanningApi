package com.naudi.financialplanningapi.model;

import java.util.List;

public record RestoreMultiCycleBackupRequest(
    TimelineType timelineType,
    List<CycleBackupEntry> cycles
) {
}
