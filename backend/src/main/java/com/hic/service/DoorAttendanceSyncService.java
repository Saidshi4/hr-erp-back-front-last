package com.hic.service;

import com.hic.dto.AttendanceLogSyncDTO;
import com.hic.dto.DoorAttendanceSyncResultDTO;
import com.hic.exception.BadRequestException;
import com.hic.model.AttendanceLog;
import com.hic.model.DeviceConfig;
import com.hic.model.Employee;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoorAttendanceSyncService {

    private static final int DEFAULT_LIMIT = 500;

    private final AttendanceLogSyncService attendanceLogSyncService;
    private final AttendanceLogRepository attendanceLogRepository;
    private final EmployeeRepository employeeRepository;
    private final DeviceConfigRepository deviceConfigRepository;
    private final AttendanceCalculationService attendanceCalculationService;
    private final AttendanceService attendanceService;



    @Transactional
    public DoorAttendanceSyncResultDTO syncAllDevices(
            LocalDateTime start,
            LocalDateTime end,
            Integer limit
    ) {
        int effectiveLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        Long tenantId = TenantContext.getTenantId();

        List<DeviceConfig> devices = tenantId != null
                ? deviceConfigRepository.findByTenantId(tenantId)
                : deviceConfigRepository.findAll();

        List<DeviceConfig> activeDevicesWithRoles = devices.stream()
                .filter(d -> "ACTIVE".equalsIgnoreCase(d.getStatus()))
                .filter(d -> "ENTRY".equalsIgnoreCase(d.getDoorRole()) || "EXIT".equalsIgnoreCase(d.getDoorRole()))
                .toList();

        List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> allPunches = new ArrayList<>();
        for (DeviceConfig device : activeDevicesWithRoles) {
            allPunches.addAll(fetchPunchesInRange(device.getId(), start, end, effectiveLimit)
                    .stream()
                    .filter(p -> p.getPunchTime() != null && p.getEmployeeNo() != null)
                    .toList());
        }

        Map<Long, String> deviceRoles = activeDevicesWithRoles.stream()
                .collect(Collectors.toMap(DeviceConfig::getId, DeviceConfig::getDoorRole));

        Set<String> unresolvedEmployeeNos = new HashSet<>();
        Map<Long, Set<LocalDate>> recalcDatesByEmployee = new HashMap<>();

        // Resolve each punch to an employee using device + device person ID
        // so the same raw employeeNo at two branches can map to different people.
        Map<Long, List<AttendanceLogSyncDTO.AttendanceLogEntryDTO>> punchesByEmployeeId = new HashMap<>();

        int matchedSessions = 0;
        int createdLogs = 0;
        int skippedEmployees = 0;
        int skippedPunches = 0;

        for (AttendanceLogSyncDTO.AttendanceLogEntryDTO punch : allPunches) {
            String code = normalizeEmployeeCode(punch.getEmployeeNo());
            if (code == null) {
                skippedPunches++;
                continue;
            }
            Employee employee = resolveEmployee(tenantId, punch.getDeviceId(), punch.getEmployeeNo());
            if (employee == null) {
                skippedPunches++;
                unresolvedEmployeeNos.add(code);
                continue;
            }
            punchesByEmployeeId.computeIfAbsent(employee.getId(), ignored -> new ArrayList<>()).add(punch);
        }

        skippedEmployees = unresolvedEmployeeNos.size();

        for (Map.Entry<Long, List<AttendanceLogSyncDTO.AttendanceLogEntryDTO>> entry : punchesByEmployeeId.entrySet()) {
            Employee employee = tenantId != null
                    ? employeeRepository.findByTenantIdAndId(tenantId, entry.getKey()).orElse(null)
                    : employeeRepository.findById(entry.getKey()).orElse(null);
            if (employee == null) {
                continue;
            }

            List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> employeePunches = entry.getValue().stream()
                    .sorted(Comparator.comparing(p -> toLocalDateTime(p.getPunchTime())))
                    .toList();

            LocalDateTime currentEntryTime = null;
            Long currentEntryDeviceId = null;

            for (int i = 0; i < employeePunches.size(); i++) {
                AttendanceLogSyncDTO.AttendanceLogEntryDTO punch = employeePunches.get(i);
                LocalDateTime punchTime = toLocalDateTime(punch.getPunchTime());
                String role = deviceRoles.get(punch.getDeviceId());

                if ("ENTRY".equals(role)) {
                    if (currentEntryTime == null) {
                        currentEntryTime = punchTime;
                        currentEntryDeviceId = punch.getDeviceId();
                    }
                } else if ("EXIT".equals(role)) {
                    if (currentEntryTime != null) {
                        if (punchTime.isAfter(currentEntryTime)) {
                            LocalDateTime entryTime = currentEntryTime;
                            LocalDateTime exitTime = punchTime;
                            Long entryDevId = currentEntryDeviceId;
                            Long exitDevId = punch.getDeviceId();

                            String doorId = entryDevId + ":" + exitDevId;

                            addRecalcDate(recalcDatesByEmployee, employee.getId(), entryTime.toLocalDate());
                            addRecalcDate(recalcDatesByEmployee, employee.getId(), exitTime.toLocalDate());

                            Optional<AttendanceLog> existingLogOpt = tenantId != null
                                    ? attendanceLogRepository.findByTenantIdAndEmployeeIdAndCheckInTime(tenantId, employee.getId(), entryTime)
                                    : attendanceLogRepository.findByEmployeeIdAndCheckInTime(employee.getId(), entryTime);

                            if (existingLogOpt.isPresent()) {
                                AttendanceLog existingLog = existingLogOpt.get();
                                if (existingLog.getCheckOutTime() == null) {
                                    existingLog.setCheckOutTime(exitTime);
                                    existingLog.setDoorId(doorId);
                                    attendanceLogRepository.save(existingLog);
                                    createdLogs++;
                                    matchedSessions++;
                                }
                            } else {
                                AttendanceLog logEntry = new AttendanceLog();
                                logEntry.setTenantId(tenantId != null ? tenantId : employee.getTenantId());
                                logEntry.setEmployeeId(employee.getId());
                                logEntry.setCheckInTime(entryTime);
                                logEntry.setCheckOutTime(exitTime);
                                logEntry.setDoorId(doorId);
                                logEntry.setDeviceId(String.valueOf(entryDevId));
                                logEntry.setEventType("DOOR_SESSION");
                                logEntry.setVerificationMethod("ISAPI_PUNCH");
                                logEntry.setStatus("ACTIVE");
                                attendanceLogRepository.save(logEntry);
                                createdLogs++;
                                matchedSessions++;
                            }

                            currentEntryTime = null;
                            currentEntryDeviceId = null;
                        }
                    }
                }
            }

            if (currentEntryTime != null) {
                LocalDateTime entryTime = currentEntryTime;
                Long entryDevId = currentEntryDeviceId;
                String doorId = entryDevId + ":null";

                addRecalcDate(recalcDatesByEmployee, employee.getId(), entryTime.toLocalDate());

                Optional<AttendanceLog> existingLogOpt = tenantId != null
                        ? attendanceLogRepository.findByTenantIdAndEmployeeIdAndCheckInTime(tenantId, employee.getId(), entryTime)
                        : attendanceLogRepository.findByEmployeeIdAndCheckInTime(employee.getId(), entryTime);

                if (existingLogOpt.isEmpty()) {
                    AttendanceLog logEntry = new AttendanceLog();
                    logEntry.setTenantId(tenantId != null ? tenantId : employee.getTenantId());
                    logEntry.setEmployeeId(employee.getId());
                    logEntry.setCheckInTime(entryTime);
                    logEntry.setCheckOutTime(null);
                    logEntry.setDoorId(doorId);
                    logEntry.setDeviceId(String.valueOf(entryDevId));
                    logEntry.setEventType("DOOR_SESSION");
                    logEntry.setVerificationMethod("ISAPI_PUNCH");
                    logEntry.setStatus("ACTIVE");
                    attendanceLogRepository.save(logEntry);
                    createdLogs++;
                }
            }
        }

        int recalculatedDays = 0;
        for (Map.Entry<Long, Set<LocalDate>> entry : recalcDatesByEmployee.entrySet()) {
            List<LocalDate> dates = new ArrayList<>(entry.getValue());
            dates.sort(Comparator.naturalOrder());
            for (LocalDate date : dates) {
                attendanceCalculationService.calculateForDay(entry.getKey(), date);
                attendanceService.generateDailySummary(entry.getKey(), date);
                recalculatedDays++;
            }
        }

        if (!unresolvedEmployeeNos.isEmpty()) {
            log.warn("Auto attendance sync unresolved employeeNo values: {} ({} punches skipped)",
                    unresolvedEmployeeNos, skippedPunches);
        }

        return new DoorAttendanceSyncResultDTO(
                allPunches.size(),
                matchedSessions,
                createdLogs,
                skippedEmployees,
                skippedPunches,
                recalculatedDays,
                unresolvedEmployeeNos.stream().sorted().toList()
        );
    }



    private List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> fetchPunchesInRange(
            Long deviceId,
            LocalDateTime start,
            LocalDateTime end,
            int pageSize
    ) {
        List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> punches = new ArrayList<>();
        OffsetDateTime rangeStart = start == null
                ? OffsetDateTime.now(ZoneId.systemDefault()).minusDays(7)
                : start.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime rangeEnd = end == null
                ? OffsetDateTime.now(ZoneId.systemDefault())
                : end.atZone(ZoneId.systemDefault()).toOffsetDateTime();

        int page = 0;
        while (true) {
            List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> batch = attendanceLogSyncService.getAttendanceLogs(
                    deviceId,
                    null,
                    rangeStart,
                    rangeEnd,
                    page,
                    pageSize
            );
            if (batch.isEmpty()) {
                break;
            }
            punches.addAll(batch);
            if (batch.size() < pageSize) {
                break;
            }
            page++;
        }
        return punches;
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime time) {
        return time.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private Employee resolveEmployee(Long tenantId, Long deviceConfigId, String employeeCode) {
        String normalizedCode = employeeCode == null ? null : employeeCode.trim();
        if (normalizedCode == null || normalizedCode.isEmpty()) {
            return null;
        }

        if (deviceConfigId != null) {
            List<Employee> byAccess = employeeRepository.findByDeviceAccessAndDeviceEmployeeNo(
                    deviceConfigId, normalizedCode);
            if (byAccess.size() == 1) {
                return byAccess.get(0);
            }
            if (byAccess.size() > 1) {
                log.warn("Multiple employees match deviceConfigId={} deviceEmployeeNo={}", deviceConfigId, normalizedCode);
                return byAccess.get(0);
            }

            DeviceConfig device = deviceConfigRepository.findById(deviceConfigId).orElse(null);
            if (device != null && device.getBranchId() != null && tenantId != null) {
                List<Employee> byHomeBranch = employeeRepository.findByTenantIdAndBranchIdAndDeviceEmployeeNoIgnoreCase(
                        tenantId, device.getBranchId(), normalizedCode);
                if (byHomeBranch.size() == 1) {
                    return byHomeBranch.get(0);
                }
                if (byHomeBranch.size() > 1) {
                    return byHomeBranch.get(0);
                }
            }
        }

        // Legacy / prefixed employeeId exact match (BAK-1001 or raw 1001)
        Employee byEmployeeCode = tenantId != null
                ? employeeRepository.findByTenantIdAndEmployeeId(tenantId, normalizedCode)
                .or(() -> employeeRepository.findByTenantIdAndEmployeeIdIgnoreCase(tenantId, normalizedCode))
                .orElse(null)
                : employeeRepository.findByEmployeeId(normalizedCode)
                .or(() -> employeeRepository.findByEmployeeIdIgnoreCase(normalizedCode))
                .orElse(null);
        if (byEmployeeCode != null) {
            return byEmployeeCode;
        }

        List<Employee> byDeviceNo = tenantId != null
                ? employeeRepository.findByTenantIdAndDeviceEmployeeNoIgnoreCase(tenantId, normalizedCode)
                : employeeRepository.findByDeviceEmployeeNoIgnoreCase(normalizedCode);
        if (byDeviceNo.size() == 1) {
            return byDeviceNo.get(0);
        }

        if (normalizedCode.chars().allMatch(Character::isDigit)) {
            try {
                Long employeePk = Long.parseLong(normalizedCode);
                return tenantId != null
                        ? employeeRepository.findByTenantIdAndId(tenantId, employeePk).orElse(null)
                        : employeeRepository.findById(employeePk).orElse(null);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int countPunchesForEmployeeCode(List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> punches, String employeeCode) {
        return (int) punches.stream()
                .filter(p -> employeeCode.equals(normalizeEmployeeCode(p.getEmployeeNo())))
                .count();
    }

    private int countPunchesForEmployeeCode(
            List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> entryPunches,
            List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> exitPunches,
            String employeeCode
    ) {
        return countPunchesForEmployeeCode(entryPunches, employeeCode)
                + countPunchesForEmployeeCode(exitPunches, employeeCode);
    }

    private String normalizeEmployeeCode(String employeeCode) {
        return employeeCode == null ? null : employeeCode.trim().toUpperCase();
    }

    private void addRecalcDate(Map<Long, Set<LocalDate>> map, Long employeeId, LocalDate date) {
        if (employeeId == null || date == null) {
            return;
        }
        map.computeIfAbsent(employeeId, ignored -> new HashSet<>()).add(date);
    }
}
