package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.model.Holiday;
import com.hic.service.HolidayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
public class HolidayController {
    private final HolidayService holidayService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Holiday>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(holidayService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Holiday>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(holidayService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Holiday>> create(@RequestBody Holiday holiday) {
        return ResponseEntity.ok(ApiResponse.success(holidayService.create(holiday)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Holiday>> update(@PathVariable Long id, @RequestBody Holiday holiday) {
        return ResponseEntity.ok(ApiResponse.success(holidayService.update(id, holiday)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        holidayService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
