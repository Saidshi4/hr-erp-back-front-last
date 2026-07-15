package com.hic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hic.dto.DeviceEmployeeImportDTO;
import com.hic.model.Branch;
import com.hic.model.DeviceConfig;
import com.hic.model.Employee;
import com.hic.repository.BranchRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeDeviceAccessRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.TenantRepository;
import com.hic.util.EncryptionUtil;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HikDeviceUserImportServiceTest {

    @Mock private RestTemplate restTemplate;
    @Mock private DeviceConfigRepository deviceConfigRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeDeviceAccessRepository employeeDeviceAccessRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private EncryptionUtil encryptionUtil;

    private HikDeviceUserImportService service;

    @BeforeEach
    void setUp() {
        service = new HikDeviceUserImportService(
                restTemplate,
                deviceConfigRepository,
                employeeRepository,
                employeeDeviceAccessRepository,
                branchRepository,
                tenantRepository,
                encryptionUtil,
                new ObjectMapper());
        ReflectionTestUtils.setField(service, "isapiBaseUrl", "http://isapi:8081");
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void importUsersFromBranch_createsPrefixedEmployee_mergesDevices_skipsConflict() {
        Branch branch = branch(10L, "Baku", "BAK");
        when(branchRepository.findById(10L)).thenReturn(Optional.of(branch));

        DeviceConfig entry = device(1L, "101", "Entry", "10.0.0.1", 10L);
        DeviceConfig exit = device(2L, "102", "Exit", "10.0.0.2", 10L);
        when(deviceConfigRepository.findByBranchId(10L)).thenReturn(List.of(entry, exit));

        when(restTemplate.exchange(eq("http://isapi:8081/api/devices/101/users/from-device"),
                eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        [{"employeeNo":"1001","name":"Ali Valiyev","gender":"male","beginTime":"2024-01-01T00:00:00"},
                         {"employeeNo":"1002","name":"A","gender":"male"}]
                        """));
        when(restTemplate.exchange(eq("http://isapi:8081/api/devices/102/users/from-device"),
                eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        [{"employeeNo":"1001","name":"Ali Valiyev","gender":"male"},
                         {"employeeNo":"1002","name":"B","gender":"male"}]
                        """));

        when(employeeRepository.findByTenantIdAndEmployeeIdIgnoreCase(eq(1L), anyString()))
                .thenReturn(Optional.empty());
        when(employeeRepository.findByTenantIdAndBranchIdAndDeviceEmployeeNoIgnoreCase(eq(1L), eq(10L), anyString()))
                .thenReturn(List.of());
        when(employeeRepository.findByTenantIdAndDeviceEmployeeNoIgnoreCase(eq(1L), anyString()))
                .thenReturn(List.of());
        when(employeeRepository.findByTenantIdAndEmployeeIdIn(eq(1L), any()))
                .thenReturn(List.of());
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(50L);
            return e;
        });
        when(employeeDeviceAccessRepository.existsByEmployeeIdAndDeviceConfigId(anyLong(), anyLong()))
                .thenReturn(false);

        DeviceEmployeeImportDTO.ImportResult result = service.importUsersFromBranch(
                new DeviceEmployeeImportDTO.ImportRequest(10L, null));

        assertThat(result.getBranchPrefix()).isEqualTo("BAK");
        assertThat(result.getCreated()).isEqualTo(1);
        assertThat(result.getSkippedConflict()).isEqualTo(1);
        assertThat(result.getCreatedPersons().get(0).getEmployeeId()).isEqualTo("BAK-1001");
        assertThat(result.getCreatedPersons().get(0).getDeviceEmployeeNo()).isEqualTo("1001");
        assertThat(result.getCreatedPersons().get(0).getHomeBranchId()).isEqualTo(10L);

        ArgumentCaptor<Employee> empCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository, times(1)).save(empCaptor.capture());
        assertThat(empCaptor.getValue().getEmployeeId()).isEqualTo("BAK-1001");
        assertThat(empCaptor.getValue().getDeviceEmployeeNo()).isEqualTo("1001");
        assertThat(empCaptor.getValue().getBranchId()).isEqualTo(10L);
        verify(employeeDeviceAccessRepository, times(2)).save(any());
    }

    @Test
    void importUsersFromBranch_crossBranchSamePerson_linksAccessOnly() {
        Branch branch = branch(20L, "Ganja", "GAN");
        when(branchRepository.findById(20L)).thenReturn(Optional.of(branch));
        DeviceConfig entry = device(5L, "201", "Entry", "10.0.1.1", 20L);
        when(deviceConfigRepository.findByBranchId(20L)).thenReturn(List.of(entry));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        [{"employeeNo":"1001","name":"Ali Valiyev"}]
                        """));

        when(employeeRepository.findByTenantIdAndEmployeeIdIgnoreCase(1L, "GAN-1001"))
                .thenReturn(Optional.empty());
        when(employeeRepository.findByTenantIdAndEmployeeIdIgnoreCase(1L, "1001"))
                .thenReturn(Optional.empty());
        when(employeeRepository.findByTenantIdAndBranchIdAndDeviceEmployeeNoIgnoreCase(1L, 20L, "1001"))
                .thenReturn(List.of());

        Employee existingHome = new Employee();
        existingHome.setId(9L);
        existingHome.setTenantId(1L);
        existingHome.setEmployeeId("BAK-1001");
        existingHome.setDeviceEmployeeNo("1001");
        existingHome.setBranchId(10L);
        existingHome.setFirstName("Ali");
        existingHome.setLastName("Valiyev");
        when(employeeRepository.findByTenantIdAndDeviceEmployeeNoIgnoreCase(1L, "1001"))
                .thenReturn(List.of(existingHome));
        when(employeeDeviceAccessRepository.existsByEmployeeIdAndDeviceConfigId(9L, 5L))
                .thenReturn(false);

        DeviceEmployeeImportDTO.ImportResult result = service.importUsersFromBranch(
                new DeviceEmployeeImportDTO.ImportRequest(20L, null));

        assertThat(result.getCreated()).isZero();
        assertThat(result.getCrossBranchLinked()).isEqualTo(1);
        assertThat(result.getAccessLinked()).isEqualTo(1);
        verify(employeeRepository, never()).save(any());
        verify(employeeDeviceAccessRepository).save(any());
    }

    @Test
    void importUsersFromBranch_reportsDeviceFailure_continues() {
        when(branchRepository.findById(10L)).thenReturn(Optional.of(branch(10L, "Baku", "BAK")));
        DeviceConfig a = device(1L, "101", "A", "10.0.0.1", 10L);
        DeviceConfig b = device(2L, "102", "B", "10.0.0.2", 10L);
        when(deviceConfigRepository.findByBranchId(10L)).thenReturn(List.of(a, b));

        when(restTemplate.exchange(eq("http://isapi:8081/api/devices/101/users/from-device"),
                eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenThrow(new RuntimeException("down"));
        when(restTemplate.exchange(eq("http://isapi:8081/api/devices/102/users/from-device"),
                eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
        when(encryptionUtil.decrypt(any())).thenReturn("pass");
        when(restTemplate.exchange(
                eq("http://10.0.0.1/ISAPI/AccessControl/UserInfo/Search?format=json"),
                eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new RuntimeException("basic also failed"));

        DeviceEmployeeImportDTO.ImportResult result = service.importUsersFromBranch(
                new DeviceEmployeeImportDTO.ImportRequest(10L, null));

        assertThat(result.getDevicesFailed()).isEqualTo(1);
        assertThat(result.getDevicesScanned()).isEqualTo(2);
    }

    @Test
    void resolveBranchPrefix_usesCodeOrName() {
        assertThat(HikDeviceUserImportService.resolveBranchPrefix(branch(1L, "Baku Office", "bak-01")))
                .isEqualTo("BAK01");
        assertThat(HikDeviceUserImportService.resolveBranchPrefix(branch(1L, "Gəncə", null)))
                .isEqualTo("GNC");
        assertThat(HikDeviceUserImportService.buildPrefixedEmployeeId("BAK", "1001"))
                .isEqualTo("BAK-1001");
    }

    private static Branch branch(Long id, String name, String code) {
        Branch b = new Branch();
        b.setId(id);
        b.setName(name);
        b.setCode(code);
        b.setTenantId(1L);
        return b;
    }

    private static DeviceConfig device(Long id, String isapiId, String name, String ip, Long branchId) {
        DeviceConfig d = new DeviceConfig();
        d.setId(id);
        d.setDeviceId(isapiId);
        d.setDeviceName(name);
        d.setDeviceIp(ip);
        d.setDevicePort(80);
        d.setUsername("admin");
        d.setPasswordEncrypted("ENC:x");
        d.setBranchId(branchId);
        d.setTenantId(1L);
        return d;
    }
}
