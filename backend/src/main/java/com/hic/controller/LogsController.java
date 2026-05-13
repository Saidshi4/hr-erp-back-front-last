package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.AttendanceLogSyncDTO;
import com.hic.service.AttendanceLogSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogsController {

    private final AttendanceLogSyncService attendanceLogSyncService;

    @GetMapping("/attendance")
    public ResponseEntity<ApiResponse<List<AttendanceLogSyncDTO.AttendanceLogEntryDTO>>> getAttendanceLogs(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String employeeNo,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                attendanceLogSyncService.getAttendanceLogs(deviceId, employeeNo, limit)
        ));
    }
}
