package com.hic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSyncDTO {
    private String deviceId;
    private String syncResult;
    private Integer recordsSynced;
    private String errorMessage;
}
