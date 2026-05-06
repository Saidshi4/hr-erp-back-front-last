package com.hic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTOs used when proxying ISAPI requests from the backend.
 * Response payloads are returned as raw {@code Map<String, Object>} to mirror
 * the ISAPI response format without tight coupling; only request bodies need
 * strong types here.
 */
public class IsapiDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceEnabledRequest {
        private boolean enabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceUserCreateRequest {
        private String employeeNo;
        private String name;
        private String userType;
        private String gender;
        private String beginTime;
        private String endTime;
        private String faceDataUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceUserUpdateRequest {
        private String name;
        private String userType;
        private String gender;
        private String beginTime;
        private String endTime;
        private String faceDataUrl;
    }
}
