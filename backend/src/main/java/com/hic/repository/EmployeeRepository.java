package com.hic.repository;

import com.hic.model.Employee;
import com.hic.model.Employee.EmploymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByDepartmentId(Long departmentId);

    List<Employee> findByEmploymentStatus(EmploymentStatus status);

    Optional<Employee> findByFinNumber(String finNumber);

    Optional<Employee> findByEmployeeId(String employeeId);

    List<Employee> findByDepartmentIdIn(Collection<Long> departmentIds);

    Page<Employee> findByDepartmentIdIn(Collection<Long> departmentIds, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE " +
           "(:status IS NULL OR e.employmentStatus = :status) AND " +
           "(:departmentId IS NULL OR e.departmentId = :departmentId)")
    Page<Employee> findByFilters(@Param("status") EmploymentStatus status,
                                  @Param("departmentId") Long departmentId,
                                  Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE " +
           "LOWER(e.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.employeeId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Employee> search(@Param("query") String query, Pageable pageable);
}
