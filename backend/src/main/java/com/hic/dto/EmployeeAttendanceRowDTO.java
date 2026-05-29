package com.hic.dto;

import com.hic.model.DailyAttendanceSummary.AttendanceStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeeAttendanceRowDTO {
    private LocalDate date;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private Double hoursWorked;
    private AttendanceStatus status;
    private String notes;
}
