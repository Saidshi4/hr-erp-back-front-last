package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.PermissionDTO;
import com.hic.dto.PermissionTypeDTO;
import com.hic.service.PermissionService;
import com.hic.service.PermissionTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {
    private final PermissionService permissionService;
    private final PermissionTypeService permissionTypeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PermissionDTO>> create(@RequestBody PermissionDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.create(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionDTO>> update(@PathVariable Long id, @RequestBody PermissionDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<PermissionTypeDTO>>> getTypes() {
        return ResponseEntity.ok(ApiResponse.success(permissionTypeService.getAll()));
    }

    @PostMapping("/types")
    public ResponseEntity<ApiResponse<PermissionTypeDTO>> createType(@RequestBody PermissionTypeDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(permissionTypeService.create(dto)));
    }

    @DeleteMapping("/types/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteType(@PathVariable Long id) {
        permissionTypeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
