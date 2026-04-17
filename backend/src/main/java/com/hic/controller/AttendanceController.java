package com.hic.controller;

import com.hic.dto.AttendanceDTO;
import com.hic.dto.AttendanceLogDTO;
import com.hic.dto.DailyAttendanceSummaryDTO;
import com.hic.dto.ApiResponse;
import com.hic.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {
    private final AttendanceService attendanceService;

    @PostMapping("/log")
    public ResponseEntity<ApiResponse<AttendanceLogDTO>> logAttendance(@Valid @RequestBody AttendanceDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.logAttendance(dto)));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ApiResponse<List<AttendanceLogDTO>>> getLogsForEmployee(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getLogsForEmployee(employeeId, start, end)));
    }

    @GetMapping("/range")
    public ResponseEntity<ApiResponse<List<AttendanceLogDTO>>> getLogsByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getLogsByDateRange(start, end)));
    }

    @GetMapping("/summary/{employeeId}")
    public ResponseEntity<ApiResponse<List<DailyAttendanceSummaryDTO>>> getSummary(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getDailySummary(employeeId, start, end)));
    }

    @PostMapping("/summary/generate/{employeeId}")
    public ResponseEntity<ApiResponse<DailyAttendanceSummaryDTO>> generateSummary(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.generateDailySummary(employeeId, date)));
    }
}
