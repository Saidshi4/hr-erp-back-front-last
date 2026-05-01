package com.hic.repository;

import com.hic.model.AttendanceLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    // Tenant-aware methods
    List<AttendanceLog> findByTenantIdAndCheckInTimeBetween(Long tenantId, LocalDateTime start, LocalDateTime end);
    List<AttendanceLog> findByTenantIdAndEmployeeIdAndCheckInTimeBetween(Long tenantId, Long employeeId,
                                                                          LocalDateTime start, LocalDateTime end);
    long countByTenantIdAndCheckInTimeBetween(Long tenantId, LocalDateTime start, LocalDateTime end);

    // Ordered queries for recent logs
    List<AttendanceLog> findByTenantIdOrderByCheckInTimeDesc(Long tenantId, Pageable pageable);
    List<AttendanceLog> findAllByOrderByCheckInTimeDesc(Pageable pageable);

    // Legacy methods
    List<AttendanceLog> findByEmployeeIdAndCheckInTimeBetween(Long employeeId,
                                                               LocalDateTime start,
                                                               LocalDateTime end);
    List<AttendanceLog> findByDeviceId(String deviceId);
    List<AttendanceLog> findByCheckInTimeBetween(LocalDateTime start, LocalDateTime end);
    long countByCheckInTimeBetween(LocalDateTime start, LocalDateTime end);
}
