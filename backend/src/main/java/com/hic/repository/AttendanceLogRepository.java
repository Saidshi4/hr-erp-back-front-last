package com.hic.repository;

import com.hic.model.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {
    List<AttendanceLog> findByEmployeeIdAndCheckInTimeBetween(Long employeeId,
                                                               LocalDateTime start,
                                                               LocalDateTime end);
    List<AttendanceLog> findByDeviceId(String deviceId);
    List<AttendanceLog> findByCheckInTimeBetween(LocalDateTime start, LocalDateTime end);
}
