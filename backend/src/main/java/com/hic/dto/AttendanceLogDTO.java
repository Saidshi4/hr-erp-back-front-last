package com.hic.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttendanceLogDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private String deviceId;
    private String doorId;
    private String eventType;
    private String verificationMethod;
    private String status;
    private LocalDateTime createdAt;
}
