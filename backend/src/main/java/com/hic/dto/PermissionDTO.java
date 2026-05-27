package com.hic.dto;

import lombok.Data;

@Data
public class PermissionDTO {
    private Long id;
    private Long tenantId;
    private String name;
    private String description;
    private String leaveType;
    private String applyType;
    private Long targetId;
    private String startDate;
    private String endDate;
    private String status;
}
