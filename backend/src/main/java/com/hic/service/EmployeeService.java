package com.hic.service;

import com.hic.dto.EmployeeDTO;
import com.hic.dto.EmployeeResponseDTO;
import com.hic.dto.PaginatedResponse;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Department;
import com.hic.model.Employee;
import com.hic.model.Employee.EmploymentStatus;
import com.hic.model.Position;
import com.hic.repository.DepartmentRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.PositionRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final IsapiEmployeeUserSyncService isapiEmployeeUserSyncService;

    public PaginatedResponse<EmployeeResponseDTO> getAll(int page, int size, String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy != null ? sortBy : "id"));
        Long tenantId = TenantContext.getTenantId();
        Page<Employee> employeePage = tenantId != null
                ? employeeRepository.findByTenantId(tenantId, pageable)
                : employeeRepository.findAll(pageable);
        return buildPaginatedResponse(employeePage);
    }

    public EmployeeResponseDTO getById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
        return toResponseDTO(employee);
    }

    public PaginatedResponse<EmployeeResponseDTO> getByBranch(Long branchId, int page, int size) {
        List<Department> departments = departmentRepository.findByBranchId(branchId);
        List<Long> deptIds = departments.stream().map(Department::getId).collect(Collectors.toList());
        Pageable pageable = PageRequest.of(page, size);
        Long tenantId = TenantContext.getTenantId();
        Page<Employee> employeePage = tenantId != null
                ? employeeRepository.findByTenantIdAndDepartmentIdIn(tenantId, deptIds, pageable)
                : employeeRepository.findByDepartmentIdIn(deptIds, pageable);
        return buildPaginatedResponse(employeePage);
    }

    public List<EmployeeResponseDTO> getByDepartment(Long departmentId) {
        Long tenantId = TenantContext.getTenantId();
        List<Employee> employees = tenantId != null
                ? employeeRepository.findByTenantIdAndDepartmentId(tenantId, departmentId)
                : employeeRepository.findByDepartmentId(departmentId);
        return mapEmployeeListToDTOs(employees);
    }

    public List<EmployeeResponseDTO> getByStatus(EmploymentStatus status) {
        Long tenantId = TenantContext.getTenantId();
        List<Employee> employees = tenantId != null
                ? employeeRepository.findByTenantIdAndEmploymentStatus(tenantId, status)
                : employeeRepository.findByEmploymentStatus(status);
        return mapEmployeeListToDTOs(employees);
    }

    public PaginatedResponse<EmployeeResponseDTO> search(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Long tenantId = TenantContext.getTenantId();
        Page<Employee> employeePage = tenantId != null
                ? employeeRepository.searchByTenant(tenantId, query, pageable)
                : employeeRepository.search(query, pageable);
        return buildPaginatedResponse(employeePage);
    }

    @Transactional
    public EmployeeResponseDTO create(EmployeeDTO dto) {
        validateDepartmentExists(dto.getDepartmentId());

        Long tenantId = TenantContext.getTenantId();

        if (dto.getFinNumber() != null && !dto.getFinNumber().isBlank()) {
            if (tenantId != null) {
                employeeRepository.findByTenantIdAndFinNumber(tenantId, dto.getFinNumber()).ifPresent(e -> {
                    throw new BadRequestException("Employee with FIN number already exists: " + dto.getFinNumber());
                });
            } else {
                employeeRepository.findByFinNumber(dto.getFinNumber()).ifPresent(e -> {
                    throw new BadRequestException("Employee with FIN number already exists: " + dto.getFinNumber());
                });
            }
        }

        Employee employee = new Employee();
        mapDtoToEmployee(dto, employee);
        if (tenantId != null) {
            employee.setTenantId(tenantId);
        }
        employee.setEmployeeId(generateEmployeeId(tenantId));
        if (employee.getEmploymentStatus() == null) {
            employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        }

        Employee saved = employeeRepository.save(employee);
        isapiEmployeeUserSyncService.syncEmployee(saved);
        return toResponseDTO(saved);
    }

    @Transactional
    public EmployeeResponseDTO update(Long id, EmployeeDTO dto) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));

        validateDepartmentExists(dto.getDepartmentId());
        mapDtoToEmployee(dto, employee);

        Employee saved = employeeRepository.save(employee);
        return toResponseDTO(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Employee", id);
        }
        employeeRepository.deleteById(id);
    }

    private void mapDtoToEmployee(EmployeeDTO dto, Employee employee) {
        employee.setFirstName(dto.getFirstName());
        employee.setLastName(dto.getLastName());
        employee.setBirthDate(dto.getBirthDate());
        employee.setGender(dto.getGender());
        employee.setMobilePhone(dto.getMobilePhone());
        employee.setEmail(dto.getEmail());
        employee.setFinNumber(dto.getFinNumber());
        employee.setFaceId(dto.getFaceId());
        employee.setCardId(dto.getCardId());
        employee.setDepartmentId(dto.getDepartmentId());
        employee.setPositionId(dto.getPositionId());
        employee.setHireDate(dto.getHireDate() != null ? dto.getHireDate() : LocalDate.now());
        if (dto.getEmploymentStatus() != null) {
            employee.setEmploymentStatus(dto.getEmploymentStatus());
        }
        if (dto.getFatherName() != null) employee.setFatherName(dto.getFatherName());
        if (dto.getArea() != null) employee.setArea(dto.getArea());
        if (dto.getShiftType() != null) employee.setShiftType(dto.getShiftType());
    }

    private EmployeeResponseDTO toResponseDTO(Employee employee) {
        return toResponseDTO(employee, null, null);
    }

    private EmployeeResponseDTO toResponseDTO(Employee employee,
                                               Map<Long, String> departmentNames,
                                               Map<Long, String> positionNames) {
        EmployeeResponseDTO dto = new EmployeeResponseDTO();
        dto.setId(employee.getId());
        dto.setEmployeeId(employee.getEmployeeId());
        dto.setFirstName(employee.getFirstName());
        dto.setLastName(employee.getLastName());
        dto.setBirthDate(employee.getBirthDate());
        dto.setGender(employee.getGender());
        dto.setMobilePhone(employee.getMobilePhone());
        dto.setEmail(employee.getEmail());
        dto.setFinNumber(employee.getFinNumber());
        dto.setFaceId(employee.getFaceId());
        dto.setCardId(employee.getCardId());
        dto.setDepartmentId(employee.getDepartmentId());
        dto.setPositionId(employee.getPositionId());
        dto.setHireDate(employee.getHireDate());
        dto.setFatherName(employee.getFatherName());
        dto.setArea(employee.getArea());
        dto.setShiftType(employee.getShiftType());
        dto.setEmploymentStatus(employee.getEmploymentStatus());
        dto.setCreatedAt(employee.getCreatedAt());
        dto.setUpdatedAt(employee.getUpdatedAt());

        if (employee.getDepartmentId() != null) {
            if (departmentNames != null) {
                dto.setDepartmentName(departmentNames.get(employee.getDepartmentId()));
            } else {
                departmentRepository.findById(employee.getDepartmentId())
                        .ifPresent(d -> dto.setDepartmentName(d.getDepartmentName()));
            }
        }
        if (employee.getPositionId() != null) {
            if (positionNames != null) {
                dto.setPositionName(positionNames.get(employee.getPositionId()));
            } else {
                positionRepository.findById(employee.getPositionId())
                        .ifPresent(p -> dto.setPositionName(p.getPositionName()));
            }
        }

        return dto;
    }

    private List<EmployeeResponseDTO> mapEmployeeListToDTOs(List<Employee> employees) {
        if (employees.isEmpty()) return List.of();
        Set<Long> deptIds = employees.stream()
                .filter(e -> e.getDepartmentId() != null)
                .map(Employee::getDepartmentId)
                .collect(Collectors.toSet());
        Set<Long> posIds = employees.stream()
                .filter(e -> e.getPositionId() != null)
                .map(Employee::getPositionId)
                .collect(Collectors.toSet());
        Map<Long, String> deptNames = departmentRepository.findAllById(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDepartmentName));
        Map<Long, String> posNames = positionRepository.findAllById(posIds).stream()
                .collect(Collectors.toMap(Position::getId, Position::getPositionName));
        return employees.stream()
                .map(e -> toResponseDTO(e, deptNames, posNames))
                .collect(Collectors.toList());
    }

    private PaginatedResponse<EmployeeResponseDTO> buildPaginatedResponse(Page<Employee> page) {
        List<EmployeeResponseDTO> content = mapEmployeeListToDTOs(page.getContent());
        return PaginatedResponse.of(content, page.getTotalElements(),
                page.getTotalPages(), page.getNumber(), page.getSize());
    }

    private void validateDepartmentExists(Long departmentId) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new ResourceNotFoundException("Department", departmentId);
        }
    }

    private String generateEmployeeId(Long tenantId) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long count = tenantId != null ? employeeRepository.countByTenantId(tenantId) + 1 : employeeRepository.count() + 1;
        return String.format("EMP%s%04d", datePart, count);
    }
}
