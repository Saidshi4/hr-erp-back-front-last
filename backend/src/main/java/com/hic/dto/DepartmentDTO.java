package com.hic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DepartmentDTO {

    @NotBlank(message = "Department name is required")
    private String departmentName;

    @NotNull(message = "Branch ID is required")
    private Long branchId;
}
