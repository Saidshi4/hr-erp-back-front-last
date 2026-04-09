package com.hic.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BranchDTO {

    @NotBlank(message = "Branch name is required")
    private String branchName;

    @NotBlank(message = "Branch code is required")
    private String branchCode;

    private String location;
    private Boolean isHeadOffice;
}
