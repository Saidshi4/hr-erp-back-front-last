package com.hic.service;

import com.hic.dto.AttendanceDTO;
import com.hic.dto.EmployeeAttendanceRowDTO;
import com.hic.dto.EmployeeAttendanceSummaryDTO;
import com.hic.dto.AttendanceLogDTO;
import com.hic.dto.DailyAttendanceSummaryDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.AttendanceLog;
import com.hic.model.DailyAttendanceSummary;
import com.hic.model.DailyAttendanceSummary.AttendanceStatus;
import com.hic.model.Employee;
import com.hic.model.EmployeePermission;
import com.hic.model.LeaveRequest;
import com.hic.model.Timetable;
import com.hic.model.WorkSchedule;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DailyAttendanceSummaryRepository;
import com.hic.repository.EmployeePermissionRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.repository.TimetableRepository;
import com.hic.repository.WorkScheduleRepository;
import com.hic.util.ShiftTypes;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceLogRepository attendanceLogRepository;
    private final DailyAttendanceSummaryRepository summaryRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeePermissionRepository employeePermissionRepository;
    private final TimetableRepository timetableRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final UserScopeService userScopeService;
    private final AttendanceInferenceService attendanceInferenceService;

    private static final LocalTime DEFAULT_SHIFT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_SHIFT_END = LocalTime.of(17, 0);
    private static final int DEFAULT_ALLOWED_LATE_MINUTES = 5;

    @Transactional
    public AttendanceLogDTO logAttendance(AttendanceDTO dto) {
        AttendanceLog log = new AttendanceLog();
        log.setEmployeeId(dto.getEmployeeId());
        log.setCheckInTime(dto.getCheckInTime());
        log.setCheckOutTime(dto.getCheckOutTime());
        log.setDeviceId(dto.getDeviceId());
        log.setDoorId(dto.getDoorId());
        log.setEventType(dto.getEventType());
        log.setVerificationMethod(dto.getVerificationMethod());
        log.setStatus("ACTIVE");
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            log.setTenantId(tenantId);
        }
        AttendanceLog saved = attendanceLogRepository.save(log);
        if (dto.getCheckInTime() != null) {
            generateDailySummary(dto.getEmployeeId(), dto.getCheckInTime().toLocalDate());
        }
        return toLogDTO(saved);
    }

    public List<AttendanceLogDTO> getLogsForEmployee(Long employeeId, LocalDateTime start, LocalDateTime end) {
        Long tenantId = TenantContext.getTenantId();
        List<AttendanceLog> logs = tenantId != null
                ? attendanceLogRepository.findByTenantIdAndEmployeeIdAndCheckInTimeBetween(tenantId, employeeId, start, end)
                : attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(employeeId, start, end);
        return logs.stream().map(this::toLogDTO).collect(Collectors.toList());
    }

    public List<AttendanceLogDTO> getLogsByDateRange(LocalDateTime start, LocalDateTime end) {
        Long tenantId = TenantContext.getTenantId();
        List<AttendanceLog> logs = tenantId != null
                ? attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(tenantId, start, end)
                : attendanceLogRepository.findByCheckInTimeBetween(start, end);
        return logs.stream().map(this::toLogDTO).collect(Collectors.toList());
    }

    @Transactional
    public DailyAttendanceSummaryDTO generateDailySummary(Long employeeId, LocalDate date) {
        List<AttendanceLog> logs = findDayLogs(employeeId, date);
        AttendanceInferenceService.AttendanceInference inference = attendanceInferenceService.inferDay(logs, date);
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        String shiftType = resolveShiftType(employee);
        ScheduleSettings scheduleSettings = resolveScheduleSettings(employee, date);

        Optional<DailyAttendanceSummary> existing = summaryRepository.findByEmployeeIdAndAttendanceDate(employeeId, date);
        DailyAttendanceSummary summary = existing.orElse(new DailyAttendanceSummary());
        summary.setTenantId(resolveAttendanceTenantId(employeeId));
        summary.setEmployeeId(employeeId);
        summary.setAttendanceDate(date);
        summary.setIsHoliday(false);
        summary.setIsLeave(false);
        summary.setIsStandardDay(true);
        summary.setIsAdditionalDay(false);
        summary.setIsExtraDay(false);

        if (inference.firstEntry() == null && !inference.currentlyInside()) {
            summary.setCheckInTime(null);
            summary.setCheckOutTime(null);
            summary.setAttendanceStatus(AttendanceStatus.ABSENT);
            summary.setHoursWorked(0.0);
        } else {
            summary.setCheckInTime(inference.firstEntry());
            summary.setCheckOutTime(inference.lastExit());
            summary.setHoursWorked(inference.workedHoursForShift(shiftType));
            summary.setAttendanceStatus(determineStatus(date, inference, scheduleSettings, shiftType));
        }

        return toSummaryDTO(summaryRepository.save(summary));
    }

    public List<DailyAttendanceSummaryDTO> getDailySummary(Long employeeId, LocalDate start, LocalDate end) {
        return summaryRepository.findByEmployeeIdAndAttendanceDateBetween(employeeId, start, end)
                .stream().map(this::toSummaryDTO).collect(Collectors.toList());
    }

    public List<EmployeeAttendanceRowDTO> getEmployeeAttendance(Long employeeId, LocalDate start, LocalDate end) {
        Employee employee = getAccessibleEmployee(employeeId);
        AttendanceContext context = loadAttendanceContext(employee, employeeId, start, end);

        List<EmployeeAttendanceRowDTO> rows = new ArrayList<>();
        List<AttendanceLog> sortedLogs = context.logs().stream()
                .filter(log -> log.getCheckInTime() != null)
                .filter(log -> overlapsAnyDayInRange(log, start, end))
                .sorted(Comparator.comparing(AttendanceLog::getCheckInTime))
                .toList();

        for (AttendanceLog log : sortedLogs) {
            LocalDate displayDate = log.getCheckInTime().toLocalDate();
            DayAttendanceContext day = buildDayContext(context, displayDate);

            EmployeeAttendanceRowDTO row = new EmployeeAttendanceRowDTO();
            row.setDate(displayDate);
            row.setCheckInTime(toOffsetDateTime(log.getCheckInTime()));
            row.setCheckOutTime(toOffsetDateTime(log.getCheckOutTime()));
            if (log.getCheckOutTime() != null && log.getCheckOutTime().isAfter(log.getCheckInTime())) {
                row.setHoursWorked(Duration.between(log.getCheckInTime(), log.getCheckOutTime()).toMinutes() / 60.0);
            }
            row.setStatus(day.status());
            row.setNotes(day.notes());
            row.setShiftType(day.shiftType());
            rows.add(row);
        }

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            final LocalDate currentDate = date;
            boolean hasLog = sortedLogs.stream()
                    .anyMatch(log -> attendanceInferenceService.overlapsDay(log, currentDate));
            if (hasLog) {
                continue;
            }

            DayAttendanceContext day = buildDayContext(context, currentDate);
            if (day.status() == AttendanceStatus.ON_LEAVE || day.status() == AttendanceStatus.ABSENT) {
                EmployeeAttendanceRowDTO row = new EmployeeAttendanceRowDTO();
                row.setDate(currentDate);
                row.setStatus(day.status());
                row.setNotes(day.notes());
                row.setShiftType(day.shiftType());
                rows.add(row);
            }
        }

        rows.sort(Comparator
                .comparing(EmployeeAttendanceRowDTO::getDate)
                .thenComparing(row -> row.getCheckInTime(), Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    public EmployeeAttendanceSummaryDTO getEmployeeAttendanceSummary(Long employeeId, LocalDate start, LocalDate end) {
        List<EmployeeAttendanceRowDTO> rows = buildDailyAttendanceRows(employeeId, start, end);
        EmployeeAttendanceSummaryDTO summary = new EmployeeAttendanceSummaryDTO();
        summary.setTotalDays(rows.size());
        summary.setWorkingDays(rows.stream()
                .filter(row -> row.getStatus() == AttendanceStatus.PRESENT
                        || row.getStatus() == AttendanceStatus.LATE
                        || row.getStatus() == AttendanceStatus.WORKDAY_COMPLETE)
                .count());
        summary.setTotalHours(rows.stream()
                .map(EmployeeAttendanceRowDTO::getHoursWorked)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum());
        summary.setAbsentDays(rows.stream().filter(row -> row.getStatus() == AttendanceStatus.ABSENT).count());
        summary.setLateDays(rows.stream().filter(row -> row.getStatus() == AttendanceStatus.LATE).count());
        summary.setLeaveDays(rows.stream().filter(row -> row.getStatus() == AttendanceStatus.ON_LEAVE).count());
        return summary;
    }

    /** One summarized row per calendar day — used for summary cards on the Attendance page. */
    private List<EmployeeAttendanceRowDTO> buildDailyAttendanceRows(Long employeeId, LocalDate start, LocalDate end) {
        Employee employee = getAccessibleEmployee(employeeId);
        AttendanceContext context = loadAttendanceContext(employee, employeeId, start, end);

        List<EmployeeAttendanceRowDTO> rows = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            final LocalDate currentDate = date;
            List<AttendanceLog> dayLogs = context.logs().stream()
                    .filter(log -> attendanceInferenceService.overlapsDay(log, currentDate))
                    .toList();
            AttendanceInferenceService.AttendanceInference inference =
                    attendanceInferenceService.inferDay(dayLogs, currentDate);
            DayAttendanceContext day = buildDayContext(context, currentDate);

            DailyAttendanceSummary summary = context.summariesByDate().get(currentDate);
            LocalDateTime firstCheckIn = inference.firstEntry();
            LocalDateTime lastCheckOut = inference.lastExit();
            double hoursWorked = inference.workedHoursForShift(day.shiftType());

            if (summary != null && summary.getHoursWorked() != null && dayLogs.isEmpty()) {
                hoursWorked = summary.getHoursWorked();
                firstCheckIn = summary.getCheckInTime();
                lastCheckOut = summary.getCheckOutTime();
            }

            EmployeeAttendanceRowDTO row = new EmployeeAttendanceRowDTO();
            row.setDate(currentDate);
            row.setCheckInTime(toOffsetDateTime(firstCheckIn));
            row.setCheckOutTime(toOffsetDateTime(lastCheckOut));
            row.setHoursWorked(hoursWorked);
            row.setStatus(day.status());
            row.setNotes(day.notes());
            row.setShiftType(day.shiftType());
            rows.add(row);
        }

        return rows;
    }

    private AttendanceContext loadAttendanceContext(Employee employee, Long employeeId, LocalDate start, LocalDate end) {
        Long tenantId = TenantContext.getTenantId();

        LocalDateTime rangeStart = start.minusDays(1).atStartOfDay();
        LocalDateTime rangeEnd = end.atTime(23, 59, 59);
        List<AttendanceLog> logs = tenantId != null
                ? attendanceLogRepository.findByTenantIdAndEmployeeIdAndCheckInTimeBetween(tenantId, employeeId, rangeStart, rangeEnd)
                : attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(employeeId, rangeStart, rangeEnd);

        Map<LocalDate, DailyAttendanceSummary> summariesByDate = summaryRepository
                .findByEmployeeIdAndAttendanceDateBetween(employeeId, start, end)
                .stream()
                .collect(Collectors.toMap(DailyAttendanceSummary::getAttendanceDate, summary -> summary, (left, right) -> right, LinkedHashMap::new));

        List<LeaveRequest> approvedLeaves = tenantId != null
                ? leaveRequestRepository.findApprovedByTenantAndEmployeeIdAndDateRange(tenantId, employeeId, start, end)
                : leaveRequestRepository.findApprovedByEmployeeIdAndDateRange(employeeId, start, end);
        List<EmployeePermission> approvedPermissions = tenantId != null
                ? employeePermissionRepository.findByTenantIdAndEmployeeIdAndDateRange(tenantId, employeeId, start, end)
                : employeePermissionRepository.findByEmployeeIdAndDateRange(employeeId, start, end);
        Optional<Timetable> timetable = getEmployeeTimetable(employee, tenantId);
        String shiftType = resolveShiftType(employee, timetable.orElse(null));

        return new AttendanceContext(logs, summariesByDate, approvedLeaves, approvedPermissions, timetable, shiftType);
    }

    private DayAttendanceContext buildDayContext(AttendanceContext context, LocalDate date) {
        List<AttendanceLog> dayLogs = context.logs().stream()
                .filter(log -> attendanceInferenceService.overlapsDay(log, date))
                .toList();
        AttendanceInferenceService.AttendanceInference inference = attendanceInferenceService.inferDay(dayLogs, date);
        boolean onLeave = overlapsLeave(context.approvedLeaves(), date)
                || overlapsPermission(context.approvedPermissions(), date);
        DailyAttendanceSummary summary = context.summariesByDate().get(date);
        AttendanceStatus status = determineDailyStatus(
                summary,
                inference,
                onLeave,
                date,
                context.timetable().orElse(null),
                context.shiftType()
        );
        String notes = buildNotes(context.approvedLeaves(), context.approvedPermissions(), date);
        return new DayAttendanceContext(status, notes, context.shiftType());
    }

    private boolean overlapsAnyDayInRange(AttendanceLog log, LocalDate start, LocalDate end) {
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (attendanceInferenceService.overlapsDay(log, date)) {
                return true;
            }
        }
        return false;
    }

    private record AttendanceContext(
            List<AttendanceLog> logs,
            Map<LocalDate, DailyAttendanceSummary> summariesByDate,
            List<LeaveRequest> approvedLeaves,
            List<EmployeePermission> approvedPermissions,
            Optional<Timetable> timetable,
            String shiftType
    ) {
    }

    private record DayAttendanceContext(AttendanceStatus status, String notes, String shiftType) {
    }

    private java.time.LocalTime getScheduleStart(WorkSchedule s, java.time.DayOfWeek day) {
        return switch (day) {
            case MONDAY -> s.getMondayStart();
            case TUESDAY -> s.getTuesdayStart();
            case WEDNESDAY -> s.getWednesdayStart();
            case THURSDAY -> s.getThursdayStart();
            case FRIDAY -> s.getFridayStart();
            case SATURDAY -> s.getSaturdayStart();
            case SUNDAY -> s.getSundayStart();
        };
    }

    private java.time.LocalTime getScheduleEnd(WorkSchedule s, java.time.DayOfWeek day) {
        return switch (day) {
            case MONDAY -> s.getMondayEnd();
            case TUESDAY -> s.getTuesdayEnd();
            case WEDNESDAY -> s.getWednesdayEnd();
            case THURSDAY -> s.getThursdayEnd();
            case FRIDAY -> s.getFridayEnd();
            case SATURDAY -> s.getSaturdayEnd();
            case SUNDAY -> s.getSundayEnd();
        };
    }

    private AttendanceLogDTO toLogDTO(AttendanceLog log) {
        AttendanceLogDTO dto = new AttendanceLogDTO();
        dto.setId(log.getId());
        dto.setEmployeeId(log.getEmployeeId());
        dto.setCheckInTime(toOffsetDateTime(log.getCheckInTime()));
        dto.setCheckOutTime(toOffsetDateTime(log.getCheckOutTime()));
        dto.setDeviceId(log.getDeviceId());
        dto.setDoorId(log.getDoorId());
        dto.setEventType(log.getEventType());
        dto.setVerificationMethod(log.getVerificationMethod());
        dto.setStatus(log.getStatus());
        dto.setCreatedAt(toOffsetDateTime(log.getCreatedAt()));
        return dto;
    }

    private DailyAttendanceSummaryDTO toSummaryDTO(DailyAttendanceSummary s) {
        DailyAttendanceSummaryDTO dto = new DailyAttendanceSummaryDTO();
        dto.setId(s.getId());
        dto.setEmployeeId(s.getEmployeeId());
        dto.setAttendanceDate(s.getAttendanceDate());
        dto.setCheckInTime(toOffsetDateTime(s.getCheckInTime()));
        dto.setCheckOutTime(toOffsetDateTime(s.getCheckOutTime()));
        dto.setHoursWorked(s.getHoursWorked());
        dto.setIsStandardDay(s.getIsStandardDay());
        dto.setIsAdditionalDay(s.getIsAdditionalDay());
        dto.setIsExtraDay(s.getIsExtraDay());
        dto.setIsHoliday(s.getIsHoliday());
        dto.setIsLeave(s.getIsLeave());
        dto.setAttendanceStatus(s.getAttendanceStatus());
        return dto;
    }

    private Employee getAccessibleEmployee(Long employeeId) {
        Long tenantId = TenantContext.getTenantId();
        Employee employee = tenantId != null
                ? employeeRepository.findByTenantIdAndId(tenantId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId))
                : employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        Long branchScope = userScopeService.resolveBranchScope(null);
        if (branchScope != null && (employee.getBranchId() == null || !branchScope.equals(employee.getBranchId()))) {
            throw new ResourceNotFoundException("Employee", employeeId);
        }
        return employee;
    }

    private Long resolveAttendanceTenantId(Long employeeId) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return employeeRepository.findById(employeeId)
                .map(Employee::getTenantId)
                .orElse(null);
    }

    private Optional<Timetable> getEmployeeTimetable(Employee employee, Long tenantId) {
        if (employee.getTimetableId() == null) {
            return Optional.empty();
        }
        return tenantId != null
                ? timetableRepository.findByTenantIdAndId(tenantId, employee.getTimetableId())
                : timetableRepository.findById(employee.getTimetableId());
    }

    private boolean overlapsLeave(List<LeaveRequest> leaves, LocalDate date) {
        return leaves.stream().anyMatch(leave ->
                !leave.getStartDate().isAfter(date) && !leave.getEndDate().isBefore(date));
    }

    private boolean overlapsPermission(List<EmployeePermission> permissions, LocalDate date) {
        return permissions.stream().anyMatch(permission ->
                !permission.getStartDate().isAfter(date) && !permission.getEndDate().isBefore(date));
    }

    private AttendanceStatus determineDailyStatus(DailyAttendanceSummary summary,
                                                  AttendanceInferenceService.AttendanceInference inference,
                                                  boolean onLeave,
                                                  LocalDate date,
                                                  Timetable timetable,
                                                  String shiftType) {
        if (onLeave) {
            return AttendanceStatus.ON_LEAVE;
        }
        if (summary != null && summary.getAttendanceStatus() == AttendanceStatus.ON_LEAVE) {
            return AttendanceStatus.ON_LEAVE;
        }
        if (summary != null && summary.getAttendanceStatus() != null) {
            return summary.getAttendanceStatus();
        }

        ScheduleSettings scheduleSettings = timetable != null
                ? new ScheduleSettings(
                timetable.getStartTime() != null ? timetable.getStartTime() : DEFAULT_SHIFT_START,
                timetable.getEndTime() != null ? timetable.getEndTime() : DEFAULT_SHIFT_END,
                timetable.getAllowedLateMinutes() != null ? timetable.getAllowedLateMinutes() : DEFAULT_ALLOWED_LATE_MINUTES
        )
                : resolveScheduleSettings(null, date);
        return determineStatus(date, inference, scheduleSettings, shiftType);
    }

    private String resolveShiftType(Employee employee) {
        return resolveShiftType(employee, null);
    }

    private String resolveShiftType(Employee employee, Timetable timetable) {
        if (employee != null && employee.getShiftType() != null && !employee.getShiftType().isBlank()) {
            return employee.getShiftType();
        }
        if (timetable != null && timetable.getShiftType() != null && !timetable.getShiftType().isBlank()) {
            return timetable.getShiftType();
        }
        return employee != null ? employee.getShiftType() : null;
    }

    private String buildNotes(List<LeaveRequest> leaves, List<EmployeePermission> permissions, LocalDate date) {
        Optional<EmployeePermission> permission = permissions.stream()
                .filter(item -> !item.getStartDate().isAfter(date) && !item.getEndDate().isBefore(date))
                .findFirst();
        if (permission.isPresent()) {
            String reason = permission.get().getReason();
            return reason != null && !reason.isBlank() ? reason : "Approved permission";
        }

        boolean onLeave = leaves.stream()
                .anyMatch(item -> !item.getStartDate().isAfter(date) && !item.getEndDate().isBefore(date));
        return onLeave ? "Approved leave" : null;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private List<AttendanceLog> findDayLogs(Long employeeId, LocalDate date) {
        // Fetch previous day too — night shifts that start yesterday may continue past midnight.
        List<AttendanceLog> candidates = attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(
                employeeId,
                date.minusDays(1).atStartOfDay(),
                date.plusDays(1).atStartOfDay().minusNanos(1)
        );
        return candidates.stream()
                .filter(log -> attendanceInferenceService.overlapsDay(log, date))
                .toList();
    }

    private ScheduleSettings resolveScheduleSettings(Employee employee, LocalDate date) {
        if (employee != null && employee.getTimetableId() != null) {
            Long tenantId = employee.getTenantId() != null ? employee.getTenantId() : TenantContext.getTenantId();
            Optional<Timetable> timetable = tenantId != null
                    ? timetableRepository.findByTenantIdAndId(tenantId, employee.getTimetableId())
                    : timetableRepository.findById(employee.getTimetableId());
            if (timetable.isPresent()) {
                Timetable value = timetable.get();
                return new ScheduleSettings(
                        value.getStartTime() != null ? value.getStartTime() : DEFAULT_SHIFT_START,
                        value.getEndTime() != null ? value.getEndTime() : DEFAULT_SHIFT_END,
                        value.getAllowedLateMinutes() != null ? value.getAllowedLateMinutes() : DEFAULT_ALLOWED_LATE_MINUTES
                );
            }
        }

        if (employee != null) {
            Optional<WorkSchedule> schedule = workScheduleRepository
                    .findTopByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(employee.getId(), date)
                    .filter(value -> value.getEndDate() == null || !value.getEndDate().isBefore(date));
            if (schedule.isPresent()) {
                WorkSchedule value = schedule.get();
                return new ScheduleSettings(
                        Optional.ofNullable(getScheduleStart(value, date.getDayOfWeek())).orElse(DEFAULT_SHIFT_START),
                        Optional.ofNullable(getScheduleEnd(value, date.getDayOfWeek())).orElse(DEFAULT_SHIFT_END),
                        value.getGracePeriodMinutes() != null ? value.getGracePeriodMinutes() : DEFAULT_ALLOWED_LATE_MINUTES
                );
            }
        }

        return new ScheduleSettings(DEFAULT_SHIFT_START, DEFAULT_SHIFT_END, DEFAULT_ALLOWED_LATE_MINUTES);
    }

    private AttendanceStatus determineStatus(LocalDate date,
                                             AttendanceInferenceService.AttendanceInference inference,
                                             ScheduleSettings scheduleSettings,
                                             String shiftType) {
        if (inference.firstEntry() == null) {
            return AttendanceStatus.ABSENT;
        }

        boolean flexible = ShiftTypes.isFlexible(shiftType);
        boolean late = !flexible
                && Duration.between(scheduleSettings.startTime(), inference.firstEntry().toLocalTime()).toMinutes()
                > scheduleSettings.allowedLateMinutes();

        if (!date.equals(LocalDate.now())) {
            return late ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
        }

        if (inference.currentlyInside()) {
            return late ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
        }

        if (flexible) {
            return inference.lastExit() != null ? AttendanceStatus.WORKDAY_COMPLETE : AttendanceStatus.PRESENT;
        }

        LocalDateTime scheduledEnd = date.atTime(scheduleSettings.endTime());
        if (inference.lastExit() != null && !inference.lastExit().isBefore(scheduledEnd)) {
            return AttendanceStatus.WORKDAY_COMPLETE;
        }

        return AttendanceStatus.ABSENT;
    }

    private record ScheduleSettings(LocalTime startTime, LocalTime endTime, int allowedLateMinutes) {
    }
}
