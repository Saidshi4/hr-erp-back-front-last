package com.hic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

public class AttendanceLogSyncDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttendanceLogEntryDTO {
        private Long id;
        private Long deviceId;
        private String employeeNo;
        private OffsetDateTime punchTime;
        private Long rawEventId;
    }
}
