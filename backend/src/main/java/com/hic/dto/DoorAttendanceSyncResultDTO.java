package com.hic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DoorAttendanceSyncResultDTO {
    private int totalPunches;
    private int matchedSessions;
    private int createdLogs;
    private int skippedEmployees;
    private int recalculatedDays;
}
