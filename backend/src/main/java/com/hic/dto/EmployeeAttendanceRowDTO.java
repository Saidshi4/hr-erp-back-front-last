package com.hic.dto;

import com.hic.model.DailyAttendanceSummary.AttendanceStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
public class EmployeeAttendanceRowDTO {
    private LocalDate date;
    private OffsetDateTime checkInTime;
    private OffsetDateTime checkOutTime;
    private Double hoursWorked;
    private AttendanceStatus status;
    private String notes;
}
