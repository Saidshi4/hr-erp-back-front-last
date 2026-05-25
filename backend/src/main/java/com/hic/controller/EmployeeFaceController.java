package com.hic.controller;

import com.hic.service.EmployeeFaceImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faces")
@RequiredArgsConstructor
public class EmployeeFaceController {

    private final EmployeeFaceImageService employeeFaceImageService;

    @GetMapping("/employee/{employeeId}/image")
    public ResponseEntity<FileSystemResource> getEmployeeFaceImage(@PathVariable Long employeeId) {
        return employeeFaceImageService.getLatestFaceImage(employeeId)
                .map(faceImage -> ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                        .contentType(MediaType.parseMediaType(faceImage.contentType()))
                        .body(new FileSystemResource(faceImage.path())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
