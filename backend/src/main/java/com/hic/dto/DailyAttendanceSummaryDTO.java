package com.hic.dto;

import com.hic.model.DailyAttendanceSummary.AttendanceStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DailyAttendanceSummaryDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private LocalDate attendanceDate;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private Double hoursWorked;
    private Boolean isStandardDay;
    private Boolean isAdditionalDay;
    private Boolean isExtraDay;
    private Boolean isHoliday;
    private Boolean isLeave;
    private AttendanceStatus attendanceStatus;
}
