package com.hic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Setup-team flow: import employees from all (or selected) devices of a branch.
 * Match key is device {@code employeeNo} (person ID) — more reliable than HikCentral's name match.
 */
public class DeviceEmployeeImportDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportRequest {
        /** Required: import scans devices belonging to this branch. */
        private Long branchId;

        /**
         * Optional subset of {@code device_configs.id}.
         * When null/empty, all devices of the branch are scanned.
         */
        private List<Long> deviceConfigIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportResult {
        private Long branchId;
        private String branchName;
        private String branchPrefix;
        private int devicesScanned;
        private int devicesFailed;
        private int totalFetched;
        private int uniquePersons;
        private int created;
        private int skippedExisting;
        private int skippedConflict;
        private int crossBranchLinked;
        private int accessLinked;
        private int errors;
        private String message;
        private List<DeviceScanStatus> deviceStatuses = new ArrayList<>();
        private List<SkippedPerson> conflicts = new ArrayList<>();
        private List<SkippedPerson> errorsDetail = new ArrayList<>();
        private List<CreatedPerson> createdPersons = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceScanStatus {
        private Long deviceConfigId;
        private String deviceName;
        private String deviceIp;
        private boolean success;
        private int userCount;
        private String error;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkippedPerson {
        private String employeeNo;
        private String name;
        private String reason;
        private List<Long> deviceConfigIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatedPerson {
        private Long employeePk;
        private String employeeNo;
        /** Prefixed HR code stored as employeeId, e.g. BAK-1001. */
        private String employeeId;
        /** Raw device person ID. */
        private String deviceEmployeeNo;
        private Long homeBranchId;
        private String firstName;
        private String lastName;
        private List<Long> deviceConfigIds;
    }
}
