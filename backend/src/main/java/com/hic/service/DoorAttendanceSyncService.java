package com.hic.service;

import com.hic.dto.AttendanceLogSyncDTO;
import com.hic.dto.DoorAttendanceSyncResultDTO;
import com.hic.model.AttendanceLog;
import com.hic.model.Employee;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class DoorAttendanceSyncService {

    private static final int DEFAULT_LIMIT = 500;

    private final AttendanceLogSyncService attendanceLogSyncService;
    private final AttendanceLogRepository attendanceLogRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceCalculationService attendanceCalculationService;

    @Transactional
    public DoorAttendanceSyncResultDTO syncDoorAttendance(
            Long entryDeviceId,
            Long exitDeviceId,
            LocalDateTime start,
            LocalDateTime end,
            Integer limit
    ) {
        int effectiveLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;

        List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> entryPunches = attendanceLogSyncService
                .getAttendanceLogs(entryDeviceId, null, effectiveLimit)
                .stream()
                .filter(p -> p.getPunchTime() != null && p.getEmployeeNo() != null)
                .filter(p -> isWithinRange(toLocalDateTime(p.getPunchTime()), start, end))
                .toList();

        List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> exitPunches = attendanceLogSyncService
                .getAttendanceLogs(exitDeviceId, null, effectiveLimit)
                .stream()
                .filter(p -> p.getPunchTime() != null && p.getEmployeeNo() != null)
                .filter(p -> isWithinRange(toLocalDateTime(p.getPunchTime()), start, end))
                .toList();

        Set<String> employeeCodes = new HashSet<>();
        entryPunches.forEach(p -> employeeCodes.add(p.getEmployeeNo().trim()));
        exitPunches.forEach(p -> employeeCodes.add(p.getEmployeeNo().trim()));

        Long tenantId = TenantContext.getTenantId();
        String doorId = entryDeviceId + ":" + exitDeviceId;
        int matchedSessions = 0;
        int createdLogs = 0;
        int skippedEmployees = 0;
        Map<Long, Set<LocalDate>> recalcDatesByEmployee = new HashMap<>();

        for (String employeeCode : employeeCodes) {
            Employee employee = tenantId != null
                    ? employeeRepository.findByTenantIdAndEmployeeId(tenantId, employeeCode).orElse(null)
                    : employeeRepository.findByEmployeeId(employeeCode).orElse(null);

            if (employee == null) {
                skippedEmployees++;
                continue;
            }

            List<LocalDateTime> employeeEntries = entryPunches.stream()
                    .filter(p -> employeeCode.equals(p.getEmployeeNo().trim()))
                    .map(p -> toLocalDateTime(p.getPunchTime()))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            List<LocalDateTime> employeeExits = exitPunches.stream()
                    .filter(p -> employeeCode.equals(p.getEmployeeNo().trim()))
                    .map(p -> toLocalDateTime(p.getPunchTime()))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            int exitIndex = 0;
            for (LocalDateTime entryTime : employeeEntries) {
                while (exitIndex < employeeExits.size() && employeeExits.get(exitIndex).isBefore(entryTime)) {
                    exitIndex++;
                }

                LocalDateTime exitTime = null;
                if (exitIndex < employeeExits.size()) {
                    exitTime = employeeExits.get(exitIndex);
                    exitIndex++;
                    matchedSessions++;
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
                log.setDeviceId(String.valueOf(entryDeviceId));
                log.setEventType("DOOR_SESSION");
                log.setVerificationMethod("ISAPI_PUNCH");
                log.setStatus("ACTIVE");
                attendanceLogRepository.save(log);
                createdLogs++;

                addRecalcDate(recalcDatesByEmployee, employee.getId(), entryTime.toLocalDate());
                if (exitTime != null) {
                    addRecalcDate(recalcDatesByEmployee, employee.getId(), exitTime.toLocalDate());
                }
            }
        }

        int recalculatedDays = 0;
        for (Map.Entry<Long, Set<LocalDate>> entry : recalcDatesByEmployee.entrySet()) {
            List<LocalDate> dates = new ArrayList<>(entry.getValue());
            dates.sort(Comparator.naturalOrder());
            for (LocalDate date : dates) {
                attendanceCalculationService.calculateForDay(entry.getKey(), date);
                recalculatedDays++;
            }
        }

        return new DoorAttendanceSyncResultDTO(
                entryPunches.size() + exitPunches.size(),
                matchedSessions,
                createdLogs,
                skippedEmployees,
                recalculatedDays
        );
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime time) {
        return time.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private boolean isWithinRange(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return value != null
                && (start == null || !value.isBefore(start))
                && (end == null || !value.isAfter(end));
    }

    private void addRecalcDate(Map<Long, Set<LocalDate>> map, Long employeeId, LocalDate date) {
        if (employeeId == null || date == null) {
            return;
        }
        map.computeIfAbsent(employeeId, ignored -> new HashSet<>()).add(date);
    }
}
