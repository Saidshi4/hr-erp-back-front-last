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
import java.util.*;
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
            String[] headers = {"ID", "Name", "FIN", "Department", "Position", "Area", "Date", "Check-in", "Shift"};
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
                excelRow.createCell(8).setCellValue(safe(row.getShiftType()));
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

        // Query attendance_logs for the date range (using checkInTime)
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atTime(LocalTime.MAX);

        List<AttendanceLog> logs = tenantId != null
                ? attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(tenantId, startDt, endDt)
                : attendanceLogRepository.findByCheckInTimeBetween(startDt, endDt);

        // Build employee lookup
        Set<Long> employeeIds = logs.stream()
                .map(AttendanceLog::getEmployeeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        // Build department & position lookups
        Set<Long> departmentIds = employeeMap.values().stream()
                .map(Employee::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> positionIds = employeeMap.values().stream()
                .map(Employee::getPositionId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> departmentNames = departmentRepository.findAllById(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDepartmentName));
        Map<Long, String> positionNames = positionRepository.findAllById(positionIds).stream()
                .collect(Collectors.toMap(Position::getId, Position::getPositionName));

        // Group logs by (employeeId, date) and keep the earliest check-in per day
        // so each employee appears once per working day
        Map<String, AttendanceLog> earliest = new LinkedHashMap<>();
        for (AttendanceLog log : logs) {
            if (log.getEmployeeId() == null || log.getCheckInTime() == null) continue;
            LocalDate logDate = log.getCheckInTime().toLocalDate();
            String key = log.getEmployeeId() + "_" + logDate;
            earliest.merge(key, log, (existing, candidate) ->
                    candidate.getCheckInTime().isBefore(existing.getCheckInTime()) ? candidate : existing);
        }

        Predicate<AttendanceReportRowDTO> predicate = dto ->
                contains(dto.getEmployeeId(), employeeCode) &&
                        contains(dto.getFullName(), name) &&
                        contains(dto.getFin(), fin) &&
                        contains(dto.getPosition(), position) &&
                        contains(dto.getDepartment(), department) &&
                        contains(dto.getArea(), area) &&
                        matchesShiftType(dto.getShiftType(), shiftType);

        List<AttendanceReportRowDTO> rows = new ArrayList<>();
        for (AttendanceLog log : earliest.values()) {
            Employee employee = employeeMap.get(log.getEmployeeId());
            if (employee == null) continue;

            AttendanceReportRowDTO dto = new AttendanceReportRowDTO();
            dto.setEmployeePk(employee.getId());
            dto.setEmployeeId(employee.getEmployeeId());
            dto.setFullName((safe(employee.getFirstName()) + " " + safe(employee.getLastName())).trim());
            dto.setFin(employee.getFinNumber());
            dto.setDepartment(departmentNames.get(employee.getDepartmentId()));
            dto.setPosition(positionNames.get(employee.getPositionId()));
            dto.setArea(employee.getArea());
            dto.setDate(log.getCheckInTime().toLocalDate());
            dto.setCheckInTime(log.getCheckInTime());
            // Use the employee's assigned shift type
            dto.setShiftType(employee.getShiftType());
            faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(employee.getId())
                    .ifPresent(face -> dto.setPhotoUrl("/api/faces/employee/" + employee.getId() + "/image"));

            if (predicate.test(dto)) {
                rows.add(dto);
            }
        }

        return rows.stream()
                .sorted(Comparator.comparing(AttendanceReportRowDTO::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AttendanceReportRowDTO::getCheckInTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * If no shiftType filter is provided, all records match.
     * If a filter is given, match employees whose shiftType contains the filter value (case-insensitive).
     */
    private boolean matchesShiftType(String employeeShiftType, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        if (employeeShiftType == null || employeeShiftType.isBlank()) {
            // Employee has no shift type set — include them when no specific filter is chosen,
            // but exclude when a specific shift is selected.
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
}
