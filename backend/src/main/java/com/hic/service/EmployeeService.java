package com.hic.service;

import com.hic.dto.EmployeeDTO;
import com.hic.dto.EmployeeSearchResultDTO;
import com.hic.dto.EmployeeResponseDTO;
import com.hic.dto.PaginatedResponse;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Department;
import com.hic.model.DeviceConfig;
import com.hic.model.Door;
import com.hic.model.Employee;
import com.hic.model.Employee.EmploymentStatus;
import com.hic.model.EmployeeDeviceAccess;
import com.hic.model.Position;
import com.hic.repository.DepartmentRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.DoorRepository;
import com.hic.repository.EmployeeDeviceAccessRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.FaceDataRepository;
import com.hic.repository.PositionRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final FaceDataRepository faceDataRepository;
    private final DeviceConfigRepository deviceConfigRepository;
    private final DoorRepository doorRepository;
    private final EmployeeDeviceAccessRepository employeeDeviceAccessRepository;
    private final IsapiEmployeeUserSyncService isapiEmployeeUserSyncService;
    private final UserScopeService userScopeService;

    public PaginatedResponse<EmployeeResponseDTO> getAll(int page, int size, String sortBy) {
        return getAll(page, size, sortBy, null);
    }

    public PaginatedResponse<EmployeeResponseDTO> getAll(int page, int size, String sortBy, Long requestedBranchId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy != null ? sortBy : "id"));
        Long tenantId = TenantContext.getTenantId();
        Long effectiveBranchId = userScopeService.resolveBranchScope(requestedBranchId);
        Page<Employee> employeePage;
        if (tenantId != null && effectiveBranchId != null) {
            employeePage = employeeRepository.findByTenantIdAndBranchId(tenantId, effectiveBranchId, pageable);
        } else if (tenantId != null) {
            employeePage = employeeRepository.findByTenantId(tenantId, pageable);
        } else {
            employeePage = employeeRepository.findAll(pageable);
        }
        return buildPaginatedResponse(employeePage);
    }

    public EmployeeResponseDTO getById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
        return toResponseDTO(employee);
    }

    public PaginatedResponse<EmployeeResponseDTO> getByBranch(Long branchId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Long tenantId = TenantContext.getTenantId();
        Page<Employee> employeePage = tenantId != null
                ? employeeRepository.findByTenantIdAndBranchId(tenantId, branchId, pageable)
                : employeeRepository.findByDepartmentIdIn(
                        departmentRepository.findByBranchId(branchId).stream()
                                .map(Department::getId)
                                .toList(),
                        pageable
                );
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

    public List<EmployeeSearchResultDTO> searchEmployees(String query) {
        String normalizedQuery = query != null ? query.trim() : "";
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }

        Pageable pageable = PageRequest.of(0, 20, Sort.by("firstName").ascending().and(Sort.by("lastName").ascending()));
        Long tenantId = TenantContext.getTenantId();
        Long branchId = userScopeService.resolveBranchScope(null);
        List<Employee> employees = tenantId != null
                ? employeeRepository.searchMinimalByTenant(tenantId, branchId, normalizedQuery, pageable)
                : employeeRepository.searchMinimal(branchId, normalizedQuery, pageable);

        Map<Long, String> departmentNames = departmentRepository.findAllById(
                        employees.stream()
                                .map(Employee::getDepartmentId)
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Department::getId, Department::getDepartmentName));

        return employees.stream()
                .map(employee -> {
                    EmployeeSearchResultDTO dto = new EmployeeSearchResultDTO();
                    dto.setEmployeePk(employee.getId());
                    dto.setEmployeeId(employee.getEmployeeId());
                    dto.setFirstName(employee.getFirstName());
                    dto.setLastName(employee.getLastName());
                    dto.setFinNumber(employee.getFinNumber());
                    dto.setDepartmentId(employee.getDepartmentId());
                    dto.setDepartmentName(employee.getDepartmentId() != null
                            ? departmentNames.get(employee.getDepartmentId())
                            : null);
                    dto.setBranchId(employee.getBranchId());
                    return dto;
                })
                .toList();
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
        List<Long> deviceIdsToAssign = resolveDeviceIdsByBranch(saved, tenantId);
        List<Long> assignedDeviceIds = replaceEmployeeDeviceAccess(saved, deviceIdsToAssign, tenantId);
        syncEmployeeToDevicesSafely(saved, assignedDeviceIds);
        return toResponseDTO(saved);
    }

    @Transactional
    public EmployeeResponseDTO update(Long id, EmployeeDTO dto) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));

        validateDepartmentExists(dto.getDepartmentId());
        Long tenantId = employee.getTenantId() != null ? employee.getTenantId() : TenantContext.getTenantId();
        mapDtoToEmployee(dto, employee);

        Employee saved = employeeRepository.save(employee);
        List<Long> deviceIdsToAssign = resolveDeviceIdsByBranch(saved, tenantId);
        List<Long> assignedDeviceIds = replaceEmployeeDeviceAccess(saved, deviceIdsToAssign, tenantId);
        syncEmployeeToDevicesSafely(saved, assignedDeviceIds);
        return toResponseDTO(saved);
    }

    public List<String> getEmployeeDoorAccess(Long employeeId) {
        if (employeeId == null) {
            return Collections.emptyList();
        }
        List<Long> deviceIds = getEmployeeDeviceIds(employeeId);
        if (deviceIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeviceConfig> devices = deviceConfigRepository.findAllById(deviceIds);
        List<Long> doorIds = devices.stream()
                .map(DeviceConfig::getDoorId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (doorIds.isEmpty()) {
            return Collections.emptyList();
        }
        return doorRepository.findAllById(doorIds).stream()
                .map(d -> d.getName() + " (" + d.getStatus() + ")")
                .sorted()
                .toList();
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
        employee.setSerialNumber(dto.getSerialNumber());
        employee.setContractNumber(dto.getContractNumber());
        employee.setBranchId(dto.getBranchId());
        employee.setDepartmentId(dto.getDepartmentId());
        employee.setPositionId(dto.getPositionId());
        employee.setHireDate(dto.getHireDate() != null ? dto.getHireDate() : LocalDate.now());
        employee.setContractEndDate(dto.getContractEndDate());
        employee.setAnnualLeaveDuration(dto.getAnnualLeaveDuration());
        employee.setAnnualLeaveBalance(dto.getAnnualLeaveBalance());
        employee.setGroupName(dto.getGroupName());
        employee.setSalary(dto.getSalary());
        employee.setHourlyRate(dto.getHourlyRate());
        employee.setAllowance(dto.getAllowance());
        employee.setEmergencyContact(dto.getEmergencyContact());
        employee.setAddress(dto.getAddress());
        employee.setNotes(dto.getNotes());
        if (dto.getEmploymentStatus() != null) {
            employee.setEmploymentStatus(dto.getEmploymentStatus());
        }
        if (dto.getFatherName() != null) employee.setFatherName(dto.getFatherName());
        if (dto.getArea() != null) employee.setArea(dto.getArea());
        if (dto.getShiftType() != null) employee.setShiftType(dto.getShiftType());
        employee.setTimetableId(dto.getTimetableId());
    }

    private EmployeeResponseDTO toResponseDTO(Employee employee) {
        return toResponseDTO(employee, null, null, null);
    }

    private EmployeeResponseDTO toResponseDTO(Employee employee,
                                               Map<Long, String> departmentNames,
                                               Map<Long, String> positionNames,
                                               Map<Long, List<Long>> employeeDeviceIds) {
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
        faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(employee.getId())
                .ifPresent(faceData -> dto.setFaceImageUrl("/api/faces/employee/" + employee.getId() + "/image"));
        dto.setCardId(employee.getCardId());
        dto.setSerialNumber(employee.getSerialNumber());
        dto.setContractNumber(employee.getContractNumber());
        dto.setBranchId(employee.getBranchId());
        dto.setDepartmentId(employee.getDepartmentId());
        dto.setPositionId(employee.getPositionId());
        dto.setHireDate(employee.getHireDate());
        dto.setContractEndDate(employee.getContractEndDate());
        dto.setAnnualLeaveDuration(employee.getAnnualLeaveDuration());
        dto.setAnnualLeaveBalance(employee.getAnnualLeaveBalance());
        dto.setFatherName(employee.getFatherName());
        dto.setGroupName(employee.getGroupName());
        dto.setSalary(employee.getSalary());
        dto.setHourlyRate(employee.getHourlyRate());
        dto.setAllowance(employee.getAllowance());
        dto.setEmergencyContact(employee.getEmergencyContact());
        dto.setAddress(employee.getAddress());
        dto.setNotes(employee.getNotes());
        dto.setArea(employee.getArea());
        dto.setShiftType(employee.getShiftType());
        dto.setTimetableId(employee.getTimetableId());
        dto.setDeviceIds(employeeDeviceIds != null
                ? employeeDeviceIds.getOrDefault(employee.getId(), List.of())
                : getEmployeeDeviceIds(employee.getId()));
        dto.setDoorAccess(getEmployeeDoorAccess(employee.getId()));
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
        List<Long> employeeIds = employees.stream().map(Employee::getId).toList();
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
        Map<Long, List<Long>> employeeDeviceIds = employeeDeviceAccessRepository.findByEmployeeIdIn(employeeIds).stream()
                .collect(Collectors.groupingBy(
                        EmployeeDeviceAccess::getEmployeeId,
                        Collectors.mapping(EmployeeDeviceAccess::getDeviceConfigId,
                                Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                        .distinct()
                                        .sorted(Comparator.naturalOrder())
                                        .toList()))
                ));
        return employees.stream()
                .map(e -> toResponseDTO(e, deptNames, posNames, employeeDeviceIds))
                .collect(Collectors.toList());
    }

    private List<Long> replaceEmployeeDeviceAccess(Employee employee, List<Long> requestedDeviceIds, Long tenantId) {
        List<Long> validDeviceIds = validateDeviceIds(requestedDeviceIds, tenantId);
        validateDeviceIdsBelongToBranch(validDeviceIds, employee.getBranchId());
        employeeDeviceAccessRepository.deleteByEmployeeId(employee.getId());
        employeeDeviceAccessRepository.flush();
        if (validDeviceIds.isEmpty()) {
            return List.of();
        }

        List<EmployeeDeviceAccess> accessRows = validDeviceIds.stream()
                .map(deviceId -> {
                    EmployeeDeviceAccess access = new EmployeeDeviceAccess();
                    access.setTenantId(employee.getTenantId());
                    access.setEmployeeId(employee.getId());
                    access.setDeviceConfigId(deviceId);
                    return access;
                })
                .toList();
        employeeDeviceAccessRepository.saveAll(accessRows);
        employeeDeviceAccessRepository.flush();
        return validDeviceIds;
    }

    private List<Long> validateDeviceIds(List<Long> requestedDeviceIds, Long tenantId) {
        if (requestedDeviceIds == null || requestedDeviceIds.isEmpty()) {
            return List.of();
        }

        List<Long> normalized = requestedDeviceIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), List::copyOf));
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<DeviceConfig> deviceConfigs = deviceConfigRepository.findAllById(normalized);
        Set<Long> allowedIds = deviceConfigs.stream()
                .filter(device -> tenantId == null || device.getTenantId() == null || tenantId.equals(device.getTenantId()))
                .map(DeviceConfig::getId)
                .collect(Collectors.toSet());

        List<Long> invalidIds = normalized.stream()
                .filter(id -> !allowedIds.contains(id))
                .toList();
        if (!invalidIds.isEmpty()) {
            throw new BadRequestException("Invalid or unauthorized device ids: " + invalidIds);
        }
        return normalized;
    }

    private void validateDeviceIdsBelongToBranch(List<Long> deviceIds, Long branchId) {
        if (deviceIds == null || deviceIds.isEmpty() || branchId == null) {
            return;
        }
        List<DeviceConfig> devices = deviceConfigRepository.findAllById(deviceIds);
        List<Long> wrongBranchDeviceIds = devices.stream()
                .filter(d -> !branchId.equals(d.getBranchId()))
                .map(DeviceConfig::getId)
                .toList();
        if (!wrongBranchDeviceIds.isEmpty()) {
            throw new BadRequestException("Device ids do not belong to employee branch: " + wrongBranchDeviceIds);
        }
    }

    private List<Long> resolveDeviceIdsByBranch(Employee employee, Long tenantId) {
        Long branchId = employee.getBranchId();
        if (branchId == null) {
            return List.of();
        }
        List<Door> doors = tenantId != null
                ? doorRepository.findByTenantIdAndBranchId(tenantId, branchId)
                : doorRepository.findByBranchId(branchId);
        Set<Long> doorIdSet = doors.stream().map(Door::getId).collect(Collectors.toSet());

        List<DeviceConfig> allDevices = deviceConfigRepository.findAll();
        return allDevices.stream()
                .filter(d -> tenantId == null || d.getTenantId() == null || tenantId.equals(d.getTenantId()))
                .filter(d -> branchId.equals(d.getBranchId()) || (d.getDoorId() != null && doorIdSet.contains(d.getDoorId())))
                .map(DeviceConfig::getId)
                .distinct()
                .sorted()
                .toList();
    }

    private List<Long> getEmployeeDeviceIds(Long employeeId) {
        if (employeeId == null) {
            return Collections.emptyList();
        }
        return employeeDeviceAccessRepository.findByEmployeeId(employeeId).stream()
                .map(EmployeeDeviceAccess::getDeviceConfigId)
                .distinct()
                .sorted()
                .toList();
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

    private void syncEmployeeToDevicesSafely(Employee employee, List<Long> assignedDeviceIds) {
        try {
            if (assignedDeviceIds == null || assignedDeviceIds.isEmpty()) {
                isapiEmployeeUserSyncService.syncEmployee(employee, List.of());
                return;
            }
            List<DeviceConfig> devices = deviceConfigRepository.findAllById(assignedDeviceIds);
            List<Long> isapiDeviceIds = devices.stream()
                    .map(DeviceConfig::getDeviceId)
                    .filter(id -> id != null && !id.isBlank())
                    .map(Long::valueOf)
                    .distinct()
                    .toList();
            isapiEmployeeUserSyncService.syncEmployee(employee, isapiDeviceIds);
        } catch (RuntimeException ex) {
            log.warn("Employee {} was saved but device sync failed: {}", employee.getEmployeeId(), ex.getMessage());
        }
    }

    private String generateEmployeeId(Long tenantId) {
        long count = tenantId != null ? employeeRepository.countByTenantId(tenantId) + 1 : employeeRepository.count() + 1;
        return String.format("EMP%04d", count);
    }

    public List<String> getDistinctAreas() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return employeeRepository.findDistinctAreasByTenantId(-1L);
        }
        return employeeRepository.findDistinctAreasByTenantId(tenantId);
    }
}
