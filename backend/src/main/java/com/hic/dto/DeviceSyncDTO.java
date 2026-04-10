package com.hic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSyncDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceConfigDTO {
        private Long id;
        private String deviceId;
        private String deviceName;
        private String deviceIp;
        private Integer devicePort;
        private String username;
        private String password;
        private Long branchId;
        private String status;
        private LocalDateTime lastSyncTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncResultDTO {
        private boolean success;
        private String message;
        private Integer recordsSynced;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncHistoryDTO {
        private Long id;
        private String deviceId;
        private LocalDateTime syncStartTime;
        private LocalDateTime syncEndTime;
        private Integer recordsSynced;
        private String syncStatus;
        private String errorMessage;
    }
}
