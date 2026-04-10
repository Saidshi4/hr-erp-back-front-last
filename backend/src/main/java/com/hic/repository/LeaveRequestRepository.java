package com.hic.repository;

import com.hic.model.LeaveRequest;
import com.hic.model.LeaveRequest.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeId(Long employeeId);
    List<LeaveRequest> findByStatus(LeaveStatus status);
    List<LeaveRequest> findByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employeeId IN :employeeIds " +
           "AND lr.startDate <= :end AND lr.endDate >= :start")
    List<LeaveRequest> findByEmployeeIdsAndDateRange(
            @Param("employeeIds") List<Long> employeeIds,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
