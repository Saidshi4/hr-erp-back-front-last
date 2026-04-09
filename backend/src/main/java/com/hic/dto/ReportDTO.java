package com.hic.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReportDTO {
    private String reportType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long branchId;
    private Long departmentId;
}
