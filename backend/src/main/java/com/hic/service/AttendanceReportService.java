package com.hic.service;

import com.hic.dto.AttendanceReportRowDTO;
import com.hic.dto.PaginatedResponse;
import com.hic.model.AttendanceLog;
import com.hic.model.Department;
import com.hic.model.Employee;
import com.hic.model.Position;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DepartmentRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.FaceDataRepository;
import com.hic.repository.PositionRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceReportService {

    private final AttendanceLogRepository attendanceLogRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final FaceDataRepository faceDataRepository;
    private final AttendanceInferenceService attendanceInferenceService;

    public PaginatedResponse<AttendanceReportRowDTO> getReport(
            LocalDate start,
            LocalDate end,
            String shiftType,
            String employeeCode,
            String name,
            String fin,
            String position,
            String department,
            String area,
            int page,
            int size
    ) {
        List<AttendanceReportRowDTO> allRows = queryRows(
                start, end, shiftType, employeeCode, name, fin, position, department, area
        );
        int fromIndex = Math.min(page * size, allRows.size());
        int toIndex = Math.min(fromIndex + size, allRows.size());
        int totalPages = size > 0 ? (int) Math.ceil((double) allRows.size() / size) : 0;
        return PaginatedResponse.of(allRows.subList(fromIndex, toIndex), allRows.size(), totalPages, page, size);
    }

    public byte[] exportExcel(
            LocalDate start,
            LocalDate end,
            String shiftType,
            String employeeCode,
            String name,
            String fin,
            String position,
            String department,
            String area
    ) {
        List<AttendanceReportRowDTO> rows = queryRows(start, end, shiftType, employeeCode, name, fin, position, department, area);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Attendance Reports");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "ID", "Name", "FIN", "Department", "Position", "Area",
                    "Date", "Check-in", "Check-out", "Worked", "Method", "Shift"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                sheet.setColumnWidth(i, 5500);
            }

            int rowNum = 1;
            for (AttendanceReportRowDTO row : rows) {
                Row excelRow = sheet.createRow(rowNum++);
                excelRow.createCell(0).setCellValue(safe(row.getEmployeeId()));
                excelRow.createCell(1).setCellValue(safe(row.getFullName()));
                excelRow.createCell(2).setCellValue(safe(row.getFin()));
                excelRow.createCell(3).setCellValue(safe(row.getDepartment()));
                excelRow.createCell(4).setCellValue(safe(row.getPosition()));
                excelRow.createCell(5).setCellValue(safe(row.getArea()));
                excelRow.createCell(6).setCellValue(row.getDate() != null ? row.getDate().toString() : "");
                excelRow.createCell(7).setCellValue(row.getCheckInTime() != null ? row.getCheckInTime().toLocalTime().toString() : "");
                excelRow.createCell(8).setCellValue(row.getCheckOutTime() != null ? row.getCheckOutTime().toLocalTime().toString() : "");
                excelRow.createCell(9).setCellValue(formatDuration(row.getWorkedMinutes()));
                excelRow.createCell(10).setCellValue(safe(row.getVerificationMethod()));
                excelRow.createCell(11).setCellValue(safe(row.getShiftType()));
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export attendance report", e);
        }
    }

    private List<AttendanceReportRowDTO> queryRows(
            LocalDate start,
            LocalDate end,
            String shiftType,
            String employeeCode,
            String name,
            String fin,
            String position,
            String department,
            String area
    ) {
        Long tenantId = TenantContext.getTenantId();

        // Include previous day so night shifts that cross midnight into `start` are counted.
        LocalDateTime startDt = start.minusDays(1).atStartOfDay();
        LocalDateTime endDt = end.atTime(LocalTime.MAX);

        List<AttendanceLog> logs = tenantId != null
                ? attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(tenantId, startDt, endDt)
                : attendanceLogRepository.findByCheckInTimeBetween(startDt, endDt);

        Set<Long> employeeIds = logs.stream()
                .map(AttendanceLog::getEmployeeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        Set<Long> departmentIds = employeeMap.values().stream()
                .map(Employee::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> positionIds = employeeMap.values().stream()
                .map(Employee::getPositionId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> departmentNames = departmentRepository.findAllById(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDepartmentName));
        Map<Long, String> positionNames = positionRepository.findAllById(positionIds).stream()
                .collect(Collectors.toMap(Position::getId, Position::getPositionName));

        Map<Long, List<AttendanceLog>> logsByEmployee = logs.stream()
                .filter(log -> log.getEmployeeId() != null && log.getCheckInTime() != null)
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));

        Predicate<AttendanceReportRowDTO> predicate = dto ->
                contains(dto.getEmployeeId(), employeeCode) &&
                        contains(dto.getFullName(), name) &&
                        contains(dto.getFin(), fin) &&
                        contains(dto.getPosition(), position) &&
                        contains(dto.getDepartment(), department) &&
                        contains(dto.getArea(), area) &&
                        matchesShiftType(dto.getShiftType(), shiftType);

        List<AttendanceReportRowDTO> rows = new ArrayList<>();
        for (Map.Entry<Long, List<AttendanceLog>> entry : logsByEmployee.entrySet()) {
            Employee employee = employeeMap.get(entry.getKey());
            if (employee == null) {
                continue;
            }

            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                final LocalDate currentDate = date;
                List<AttendanceLog> dayLogs = entry.getValue().stream()
                        .filter(log -> attendanceInferenceService.overlapsDay(log, currentDate))
                        .toList();
                if (dayLogs.isEmpty()) {
                    continue;
                }

                AttendanceInferenceService.AttendanceInference inference =
                        attendanceInferenceService.inferDay(dayLogs, currentDate);
                if (inference.firstEntry() == null && !inference.currentlyInside()) {
                    continue;
                }

                AttendanceReportRowDTO dto = new AttendanceReportRowDTO();
                dto.setEmployeePk(employee.getId());
                dto.setEmployeeId(employee.getEmployeeId());
                dto.setFullName((safe(employee.getFirstName()) + " " + safe(employee.getLastName())).trim());
                dto.setFin(employee.getFinNumber());
                dto.setDepartment(departmentNames.get(employee.getDepartmentId()));
                dto.setPosition(positionNames.get(employee.getPositionId()));
                dto.setArea(employee.getArea());
                dto.setDate(currentDate);
                dto.setCheckInTime(inference.firstEntry());
                dto.setCheckOutTime(inference.lastExit());
                dto.setWorkedMinutes(inference.workedMinutes());
                dto.setVerificationMethod(normalizeVerificationMethod(dayLogs.stream()
                        .map(AttendanceLog::getVerificationMethod)
                        .filter(method -> method != null && !method.isBlank())
                        .findFirst()
                        .orElse(null)));
                dto.setShiftType(employee.getShiftType());
                faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(employee.getId())
                        .ifPresent(face -> dto.setPhotoUrl("/api/faces/employee/" + employee.getId() + "/image"));

                if (predicate.test(dto)) {
                    rows.add(dto);
                }
            }
        }

        return rows.stream()
                .sorted(Comparator.comparing(AttendanceReportRowDTO::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AttendanceReportRowDTO::getCheckInTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private boolean matchesShiftType(String employeeShiftType, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        if (employeeShiftType == null || employeeShiftType.isBlank()) {
            return false;
        }
        return employeeShiftType.equalsIgnoreCase(filter) ||
                employeeShiftType.toUpperCase(Locale.ROOT).contains(filter.toUpperCase(Locale.ROOT));
    }

    private boolean contains(String value, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return safe(value).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String formatDuration(Integer workedMinutes) {
        if (workedMinutes == null || workedMinutes <= 0) {
            return "";
        }
        int hours = workedMinutes / 60;
        int minutes = workedMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    private String normalizeVerificationMethod(String method) {
        if (method == null || method.isBlank()) {
            return null;
        }
        String lower = method.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("face")) {
            return "face";
        }
        if (lower.contains("card") || lower.contains("mifare")) {
            return "card";
        }
        if (lower.contains("finger")) {
            return "finger";
        }
        if ("isapi_punch".equals(lower) || "door_session".equals(lower)) {
            return "device";
        }
        return lower;
    }
}
