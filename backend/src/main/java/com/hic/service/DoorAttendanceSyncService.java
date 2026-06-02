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
    public DoorAttendanceSyncResultDTO syncDoorAttendance(
            Long entryDeviceId,
            Long exitDeviceId,
            LocalDateTime start,
            LocalDateTime end,
            Integer limit
    ) {
        int effectiveLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;

        List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> allPunches = new ArrayList<>();
        allPunches.addAll(fetchPunchesInRange(entryDeviceId, start, end, effectiveLimit)
                .stream()
                .filter(p -> p.getPunchTime() != null && p.getEmployeeNo() != null)
                .toList());
        allPunches.addAll(fetchPunchesInRange(exitDeviceId, start, end, effectiveLimit)
                .stream()
                .filter(p -> p.getPunchTime() != null && p.getEmployeeNo() != null)
                .toList());

        Set<String> employeeCodes = allPunches.stream()
                .map(p -> normalizeEmployeeCode(p.getEmployeeNo()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Long tenantId = TenantContext.getTenantId();
        String doorId = entryDeviceId + ":" + exitDeviceId;
        int matchedSessions = 0;
        int createdLogs = 0;
        int skippedEmployees = 0;
        int skippedPunches = 0;
        Set<String> unresolvedEmployeeNos = new HashSet<>();
        Map<Long, Set<LocalDate>> recalcDatesByEmployee = new HashMap<>();

        for (String employeeCode : employeeCodes) {
            Employee employee = resolveEmployee(tenantId, employeeCode);

            if (employee == null) {
                skippedEmployees++;
                skippedPunches += countPunchesForEmployeeCode(allPunches, employeeCode);
                unresolvedEmployeeNos.add(employeeCode);
                continue;
            }

            List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> employeePunches = allPunches.stream()
                    .filter(p -> employeeCode.equals(normalizeEmployeeCode(p.getEmployeeNo())))
                    .sorted(Comparator.comparing(p -> toLocalDateTime(p.getPunchTime())))
                    .toList();

            for (int i = 0; i < employeePunches.size(); i += 2) {
                AttendanceLogSyncDTO.AttendanceLogEntryDTO entryPunch = employeePunches.get(i);
                AttendanceLogSyncDTO.AttendanceLogEntryDTO exitPunch = (i + 1 < employeePunches.size())
                        ? employeePunches.get(i + 1)
                        : null;

                LocalDateTime entryTime = toLocalDateTime(entryPunch.getPunchTime());
                LocalDateTime exitTime = exitPunch != null ? toLocalDateTime(exitPunch.getPunchTime()) : null;

                if (exitTime != null && !exitTime.isAfter(entryTime)) {
                    exitTime = null;
                    exitPunch = null;
                }

                addRecalcDate(recalcDatesByEmployee, employee.getId(), entryTime.toLocalDate());
                if (exitTime != null) {
                    addRecalcDate(recalcDatesByEmployee, employee.getId(), exitTime.toLocalDate());
                }

                boolean alreadyExists = tenantId != null
                        ? attendanceLogRepository.existsByTenantIdAndEmployeeIdAndCheckInTimeAndCheckOutTimeAndDoorId(
                        tenantId,
                        employee.getId(),
                        entryTime,
                        exitTime,
                        doorId
                )
                        : attendanceLogRepository.existsByEmployeeIdAndCheckInTimeAndCheckOutTimeAndDoorId(
                        employee.getId(),
                        entryTime,
                        exitTime,
                        doorId
                );

                if (alreadyExists) {
                    continue;
                }

                AttendanceLog log = new AttendanceLog();
                log.setTenantId(tenantId != null ? tenantId : employee.getTenantId());
                log.setEmployeeId(employee.getId());
                log.setCheckInTime(entryTime);
                log.setCheckOutTime(exitTime);
                log.setDoorId(doorId);
                log.setDeviceId(String.valueOf(entryPunch.getDeviceId()));
                log.setEventType("DOOR_SESSION");
                log.setVerificationMethod("ISAPI_PUNCH");
                log.setStatus("ACTIVE");
                attendanceLogRepository.save(log);
                createdLogs++;
                if (exitTime != null) {
                    matchedSessions++;
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
            log.warn("Door attendance sync unresolved employeeNo values: {} ({} punches skipped)",
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

    @Transactional
    public DoorAttendanceSyncResultDTO syncDoorAttendanceByDoorId(
            Long doorId,
            LocalDateTime start,
            LocalDateTime end,
            Integer limit
    ) {
        DeviceConfig entryDevice = deviceConfigRepository.findByDoorIdAndDoorRole(doorId, "ENTRY")
                .orElseThrow(() -> new BadRequestException("Door has no ENTRY device assigned"));
        DeviceConfig exitDevice = deviceConfigRepository.findByDoorIdAndDoorRole(doorId, "EXIT")
                .orElseThrow(() -> new BadRequestException("Door has no EXIT device assigned"));
        return syncDoorAttendance(entryDevice.getId(), exitDevice.getId(), start, end, limit);
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

    private Employee resolveEmployee(Long tenantId, String employeeCode) {
        String normalizedCode = employeeCode == null ? null : employeeCode.trim();
        if (normalizedCode == null || normalizedCode.isEmpty()) {
            return null;
        }

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
