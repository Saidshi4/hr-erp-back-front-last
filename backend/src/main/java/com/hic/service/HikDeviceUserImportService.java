package com.hic.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hic.dto.DeviceEmployeeImportDTO;
import com.hic.dto.HikUserInfoSearchDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Branch;
import com.hic.model.DeviceConfig;
import com.hic.model.Employee;
import com.hic.model.EmployeeDeviceAccess;
import com.hic.repository.BranchRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeDeviceAccessRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.TenantRepository;
import com.hic.util.EncryptionUtil;
import com.hic.util.TenantContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Setup-team import: pull persons from Hikvision devices into local employees.
 *
 * <p>Match key is device {@code employeeNo} (person ID). HikCentral Professional
 * documents name-based matching for "Import from Device"; we use employeeNo because
 * it is the stable ISAPI identity and what attendance already resolves against.
 *
 * <p>Across devices of a branch: same employeeNo + consistent name → one employee;
 * same employeeNo + conflicting names → skip and flag; already in DB → skip employee
 * fields but link missing device access.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HikDeviceUserImportService {

    private static final String SEARCH_PATH = "/ISAPI/AccessControl/UserInfo/Search?format=json";
    private static final int PAGE_SIZE = 30;

    private final RestTemplate restTemplate;
    private final DeviceConfigRepository deviceConfigRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeDeviceAccessRepository employeeDeviceAccessRepository;
    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final EncryptionUtil encryptionUtil;
    private final ObjectMapper objectMapper;

    @Value("${isapi.base-url:}")
    private String isapiBaseUrl;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public HikUserInfoSearchDTO.ImportResultDTO importUsersFromDevice(Long deviceConfigId) {
        DeviceConfig device = deviceConfigRepository.findById(deviceConfigId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceConfig", deviceConfigId));
        if (device.getBranchId() == null) {
            throw new BadRequestException("Device is not assigned to a branch");
        }
        DeviceEmployeeImportDTO.ImportRequest request = new DeviceEmployeeImportDTO.ImportRequest(
                device.getBranchId(), List.of(deviceConfigId));
        DeviceEmployeeImportDTO.ImportResult result = importUsersFromBranch(request);
        return new HikUserInfoSearchDTO.ImportResultDTO(
                result.getTotalFetched(),
                result.getCreated(),
                result.getSkippedExisting() + result.getSkippedConflict(),
                result.getErrors(),
                result.getMessage());
    }

    @Transactional
    public DeviceEmployeeImportDTO.ImportResult importUsersFromBranch(DeviceEmployeeImportDTO.ImportRequest request) {
        if (request == null || request.getBranchId() == null) {
            throw new BadRequestException("branchId is required");
        }
        Long branchId = request.getBranchId();
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));

        List<DeviceConfig> branchDevices = deviceConfigRepository.findByBranchId(branchId);
        if (branchDevices.isEmpty()) {
            throw new BadRequestException("No devices found for branch " + branchId);
        }

        List<DeviceConfig> devices = selectDevices(branchDevices, request.getDeviceConfigIds(), branchId);
        Long tenantId = resolveTenantId(devices);
        String branchPrefix = resolveBranchPrefix(branch);

        DeviceEmployeeImportDTO.ImportResult result = new DeviceEmployeeImportDTO.ImportResult();
        result.setBranchId(branchId);
        result.setBranchName(branch.getName());
        result.setBranchPrefix(branchPrefix);

        Map<String, AggregatedPerson> byEmployeeNo = new LinkedHashMap<>();
        int totalFetched = 0;

        for (DeviceConfig device : devices) {
            DeviceEmployeeImportDTO.DeviceScanStatus status = new DeviceEmployeeImportDTO.DeviceScanStatus();
            status.setDeviceConfigId(device.getId());
            status.setDeviceName(device.getDeviceName());
            status.setDeviceIp(device.getDeviceIp());
            try {
                List<FetchedPerson> users = fetchUsersFromDevice(device);
                status.setSuccess(true);
                status.setUserCount(users.size());
                totalFetched += users.size();
                for (FetchedPerson person : users) {
                    mergePerson(byEmployeeNo, person, device.getId());
                }
            } catch (Exception ex) {
                log.error("Failed to fetch users from deviceConfigId={}: {}", device.getId(), ex.getMessage(), ex);
                status.setSuccess(false);
                status.setUserCount(0);
                status.setError(ex.getMessage());
                result.setDevicesFailed(result.getDevicesFailed() + 1);
            }
            result.getDeviceStatuses().add(status);
        }

        result.setDevicesScanned(devices.size());
        result.setTotalFetched(totalFetched);
        result.setUniquePersons(byEmployeeNo.size());

        for (AggregatedPerson person : byEmployeeNo.values()) {
            try {
                persistAggregated(person, tenantId, branchId, branchPrefix, result);
            } catch (Exception ex) {
                log.error("Failed to persist imported person {}: {}", person.employeeNo, ex.getMessage(), ex);
                result.setErrors(result.getErrors() + 1);
                result.getErrorsDetail().add(new DeviceEmployeeImportDTO.SkippedPerson(
                        person.employeeNo, person.name, ex.getMessage(),
                        new ArrayList<>(person.deviceConfigIds)));
            }
        }

        result.setMessage(String.format(
                "Import complete: branch=%s prefix=%s devices=%d failedDevices=%d fetched=%d unique=%d created=%d skippedExisting=%d crossBranchLinked=%d skippedConflict=%d accessLinked=%d errors=%d",
                branch.getName(), branchPrefix, result.getDevicesScanned(), result.getDevicesFailed(),
                result.getTotalFetched(), result.getUniquePersons(), result.getCreated(),
                result.getSkippedExisting(), result.getCrossBranchLinked(), result.getSkippedConflict(),
                result.getAccessLinked(), result.getErrors()));
        log.info(result.getMessage());
        return result;
    }

    // -------------------------------------------------------------------------
    // Device selection / fetch
    // -------------------------------------------------------------------------

    private List<DeviceConfig> selectDevices(
            List<DeviceConfig> branchDevices, List<Long> requestedIds, Long branchId) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return branchDevices;
        }
        Set<Long> allowed = new LinkedHashSet<>();
        for (DeviceConfig d : branchDevices) {
            allowed.add(d.getId());
        }
        List<DeviceConfig> selected = new ArrayList<>();
        for (Long id : requestedIds) {
            if (id == null) continue;
            if (!allowed.contains(id)) {
                throw new BadRequestException("Device " + id + " does not belong to branch " + branchId);
            }
            branchDevices.stream()
                    .filter(d -> d.getId().equals(id))
                    .findFirst()
                    .ifPresent(selected::add);
        }
        if (selected.isEmpty()) {
            throw new BadRequestException("No valid devices selected for import");
        }
        return selected;
    }

    private List<FetchedPerson> fetchUsersFromDevice(DeviceConfig device) {
        Long isapiId = resolveIsapiDeviceId(device);
        if (isapiId != null && StringUtils.hasText(isapiBaseUrl)) {
            try {
                return fetchViaIsapiService(isapiId);
            } catch (Exception ex) {
                log.warn("ISAPI Digest pull failed for deviceConfigId={}, falling back to direct Basic: {}",
                        device.getId(), ex.getMessage());
            }
        }
        return fetchViaDirectBasic(device);
    }

    private List<FetchedPerson> fetchViaIsapiService(Long isapiDeviceId) throws Exception {
        String url = trimTrailingSlash(isapiBaseUrl) + "/api/devices/" + isapiDeviceId + "/users/from-device";
        ResponseEntity<String> raw = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        if (raw.getBody() == null) {
            return List.of();
        }
        List<IsapiPersonDto> list = objectMapper.readValue(raw.getBody(), new TypeReference<>() {});
        List<FetchedPerson> result = new ArrayList<>();
        for (IsapiPersonDto dto : list) {
            if (dto == null || !StringUtils.hasText(dto.getEmployeeNo())) continue;
            result.add(new FetchedPerson(
                    dto.getEmployeeNo().trim(),
                    dto.getName(),
                    dto.getGender(),
                    dto.getBeginTime()));
        }
        return result;
    }

    private List<FetchedPerson> fetchViaDirectBasic(DeviceConfig device) {
        String baseUrl = buildBaseUrl(device);
        String username = device.getUsername();
        String password = decryptPassword(device.getPasswordEncrypted());
        String url = baseUrl + SEARCH_PATH;
        HttpHeaders headers = buildHeaders(username, password);

        List<FetchedPerson> result = new ArrayList<>();
        String searchId = "sync_users_" + device.getId();
        int offset = 0;

        while (true) {
            HikUserInfoSearchDTO.SearchRequest body = new HikUserInfoSearchDTO.SearchRequest(
                    new HikUserInfoSearchDTO.SearchCond(searchId, PAGE_SIZE, offset));
            HttpEntity<HikUserInfoSearchDTO.SearchRequest> entity = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<String> raw = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                if (raw.getBody() == null) break;

                HikUserInfoSearchDTO.SearchResponse response =
                        objectMapper.readValue(raw.getBody(), HikUserInfoSearchDTO.SearchResponse.class);
                HikUserInfoSearchDTO.SearchResult sr = response.getUserInfoSearch();
                if (sr == null) break;

                String status = sr.getResponseStatusStrg();
                List<HikUserInfoSearchDTO.UserInfo> page = sr.getUserInfoList();
                if (page == null || page.isEmpty()
                        || (status != null && status.toUpperCase(Locale.ROOT).contains("NO"))) {
                    break;
                }

                int got = 0;
                for (HikUserInfoSearchDTO.UserInfo u : page) {
                    if (u == null || !StringUtils.hasText(u.getEmployeeNo())) continue;
                    String begin = u.getValid() != null ? u.getValid().getBeginTime() : null;
                    result.add(new FetchedPerson(u.getEmployeeNo().trim(), u.getName(), u.getGender(), begin));
                    got++;
                }

                int numOfMatches = sr.getNumOfMatches() > 0 ? sr.getNumOfMatches() : got;
                if ("OK".equalsIgnoreCase(status) || numOfMatches < PAGE_SIZE || got == 0) {
                    break;
                }
                offset += numOfMatches;
                if (offset > 100_000) break;
            } catch (Exception ex) {
                throw new IllegalStateException(
                        "Direct device UserInfo/Search failed for " + device.getDeviceIp() + ": " + ex.getMessage(),
                        ex);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Merge + persist
    // -------------------------------------------------------------------------

    private void mergePerson(Map<String, AggregatedPerson> byEmployeeNo, FetchedPerson person, Long deviceConfigId) {
        String key = person.employeeNo().toLowerCase(Locale.ROOT);
        AggregatedPerson agg = byEmployeeNo.get(key);
        if (agg == null) {
            agg = new AggregatedPerson();
            agg.employeeNo = person.employeeNo();
            agg.name = person.name();
            agg.gender = person.gender();
            agg.beginTime = person.beginTime();
            agg.deviceConfigIds.add(deviceConfigId);
            byEmployeeNo.put(key, agg);
            return;
        }
        agg.deviceConfigIds.add(deviceConfigId);
        if (agg.conflict) {
            return;
        }
        String existingNorm = normalizeName(agg.name);
        String incomingNorm = normalizeName(person.name());
        if (!existingNorm.isEmpty() && !incomingNorm.isEmpty() && !existingNorm.equals(incomingNorm)) {
            agg.conflict = true;
            agg.conflictReason = "Name mismatch across devices: \"" + agg.name + "\" vs \"" + person.name() + "\"";
            return;
        }
        if (!StringUtils.hasText(agg.name) && StringUtils.hasText(person.name())) {
            agg.name = person.name();
        }
        if (!StringUtils.hasText(agg.gender) && StringUtils.hasText(person.gender())) {
            agg.gender = person.gender();
        }
        if (!StringUtils.hasText(agg.beginTime) && StringUtils.hasText(person.beginTime())) {
            agg.beginTime = person.beginTime();
        }
    }

    private void persistAggregated(
            AggregatedPerson person,
            Long tenantId,
            Long branchId,
            String branchPrefix,
            DeviceEmployeeImportDTO.ImportResult result) {

        if (person.conflict) {
            result.setSkippedConflict(result.getSkippedConflict() + 1);
            result.getConflicts().add(new DeviceEmployeeImportDTO.SkippedPerson(
                    person.employeeNo, person.name, person.conflictReason,
                    new ArrayList<>(person.deviceConfigIds)));
            return;
        }

        String prefixedId = buildPrefixedEmployeeId(branchPrefix, person.employeeNo);

        // Same person already imported for this branch (prefixed id or home-branch + device no).
        Optional<Employee> sameBranchExisting = findExistingForBranch(tenantId, branchId, prefixedId, person.employeeNo);
        if (sameBranchExisting.isPresent()) {
            result.setSkippedExisting(result.getSkippedExisting() + 1);
            Employee existing = sameBranchExisting.get();
            ensureDeviceEmployeeNo(existing, person.employeeNo);
            int linked = linkMissingAccess(existing, person.deviceConfigIds);
            result.setAccessLinked(result.getAccessLinked() + linked);
            return;
        }

        // Same device person + name already exists under another home branch → cross-branch access only.
        Optional<Employee> crossBranchSamePerson = findCrossBranchSamePerson(tenantId, person.employeeNo, person.name);
        if (crossBranchSamePerson.isPresent()) {
            Employee existing = crossBranchSamePerson.get();
            ensureDeviceEmployeeNo(existing, person.employeeNo);
            int linked = linkMissingAccess(existing, person.deviceConfigIds);
            result.setAccessLinked(result.getAccessLinked() + linked);
            result.setCrossBranchLinked(result.getCrossBranchLinked() + 1);
            result.setSkippedExisting(result.getSkippedExisting() + 1);
            return;
        }

        Employee emp = mapToEmployee(person, tenantId, branchId, prefixedId);
        enforceEmployeeQuota(tenantId);
        Employee saved = employeeRepository.save(emp);
        int linked = linkMissingAccess(saved, person.deviceConfigIds);
        result.setAccessLinked(result.getAccessLinked() + linked);
        result.setCreated(result.getCreated() + 1);
        result.getCreatedPersons().add(new DeviceEmployeeImportDTO.CreatedPerson(
                saved.getId(),
                person.employeeNo,
                saved.getEmployeeId(),
                saved.getDeviceEmployeeNo(),
                saved.getBranchId(),
                saved.getFirstName(),
                saved.getLastName(),
                new ArrayList<>(person.deviceConfigIds)));
    }

    private Optional<Employee> findExistingForBranch(
            Long tenantId, Long branchId, String prefixedId, String deviceEmployeeNo) {
        Optional<Employee> byPrefixed = tenantId != null
                ? employeeRepository.findByTenantIdAndEmployeeIdIgnoreCase(tenantId, prefixedId)
                : employeeRepository.findByEmployeeIdIgnoreCase(prefixedId);
        if (byPrefixed.isPresent()) {
            return byPrefixed;
        }

        // Legacy imports stored raw device no as employeeId.
        Optional<Employee> byRawId = tenantId != null
                ? employeeRepository.findByTenantIdAndEmployeeIdIgnoreCase(tenantId, deviceEmployeeNo)
                : employeeRepository.findByEmployeeIdIgnoreCase(deviceEmployeeNo);
        if (byRawId.isPresent() && branchId.equals(byRawId.get().getBranchId())) {
            return byRawId;
        }

        List<Employee> byDeviceNo = tenantId != null
                ? employeeRepository.findByTenantIdAndBranchIdAndDeviceEmployeeNoIgnoreCase(
                        tenantId, branchId, deviceEmployeeNo)
                : List.of();
        return byDeviceNo.stream().findFirst();
    }

    private Optional<Employee> findCrossBranchSamePerson(Long tenantId, String deviceEmployeeNo, String name) {
        String nameNorm = normalizeName(name);
        if (nameNorm.isEmpty()) {
            return Optional.empty();
        }
        List<Employee> candidates = tenantId != null
                ? employeeRepository.findByTenantIdAndDeviceEmployeeNoIgnoreCase(tenantId, deviceEmployeeNo)
                : employeeRepository.findByDeviceEmployeeNoIgnoreCase(deviceEmployeeNo);
        for (Employee candidate : candidates) {
            String candidateName = ((candidate.getFirstName() != null ? candidate.getFirstName() : "")
                    + " " + (candidate.getLastName() != null ? candidate.getLastName() : "")).trim();
            if ("-".equals(candidate.getLastName())) {
                candidateName = candidate.getFirstName() != null ? candidate.getFirstName() : "";
            }
            if (nameNorm.equals(normalizeName(candidateName))) {
                return Optional.of(candidate);
            }
        }
        // Also match legacy rows where employeeId == device no and name matches.
        List<Employee> legacy = tenantId != null
                ? employeeRepository.findByTenantIdAndEmployeeIdIn(tenantId, List.of(deviceEmployeeNo))
                : employeeRepository.findByEmployeeIdIn(List.of(deviceEmployeeNo));
        for (Employee candidate : legacy) {
            if (StringUtils.hasText(candidate.getDeviceEmployeeNo())) {
                continue;
            }
            String candidateName = ((candidate.getFirstName() != null ? candidate.getFirstName() : "")
                    + " " + (candidate.getLastName() != null ? candidate.getLastName() : "")).trim();
            if (nameNorm.equals(normalizeName(candidateName))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private void ensureDeviceEmployeeNo(Employee employee, String deviceEmployeeNo) {
        if (!StringUtils.hasText(employee.getDeviceEmployeeNo()) && StringUtils.hasText(deviceEmployeeNo)) {
            employee.setDeviceEmployeeNo(deviceEmployeeNo.trim());
            employeeRepository.save(employee);
        }
    }

    private int linkMissingAccess(Employee employee, Set<Long> deviceConfigIds) {
        int linked = 0;
        Long tenantId = employee.getTenantId();
        for (Long deviceConfigId : deviceConfigIds) {
            if (deviceConfigId == null) continue;
            if (employeeDeviceAccessRepository.existsByEmployeeIdAndDeviceConfigId(employee.getId(), deviceConfigId)) {
                continue;
            }
            EmployeeDeviceAccess access = new EmployeeDeviceAccess();
            access.setTenantId(tenantId);
            access.setEmployeeId(employee.getId());
            access.setDeviceConfigId(deviceConfigId);
            employeeDeviceAccessRepository.save(access);
            linked++;
        }
        return linked;
    }

    private Employee mapToEmployee(AggregatedPerson person, Long tenantId, Long branchId, String prefixedId) {
        Employee emp = new Employee();
        emp.setEmployeeId(prefixedId);
        emp.setDeviceEmployeeNo(person.employeeNo.trim());
        emp.setTenantId(tenantId);
        emp.setBranchId(branchId);
        emp.setEmploymentStatus(Employee.EmploymentStatus.ACTIVE);

        String fullName = StringUtils.hasText(person.name) ? person.name.trim() : person.employeeNo;
        String[] parts = fullName.split("\\s+", 2);
        emp.setFirstName(parts.length > 0 && !parts[0].isEmpty() ? parts[0] : fullName);
        emp.setLastName(parts.length > 1 ? parts[1] : "-");

        if (StringUtils.hasText(person.gender)) {
            emp.setGender(person.gender.toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(person.beginTime)) {
            emp.setHireDate(parseLocalDate(person.beginTime));
        }
        if (emp.getHireDate() == null) {
            emp.setHireDate(LocalDate.now());
        }
        return emp;
    }

    static String resolveBranchPrefix(Branch branch) {
        if (branch == null) {
            return "BR";
        }
        if (StringUtils.hasText(branch.getCode())) {
            return sanitizePrefix(branch.getCode());
        }
        return sanitizePrefix(branch.getName());
    }

    private void enforceEmployeeQuota(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            Integer max = tenant.getMaxEmployees();
            if (max == null || max <= 0) {
                return;
            }
            long current = employeeRepository.countByTenantId(tenantId);
            if (current >= max) {
                throw new BadRequestException(
                        "Employee limit reached for this tenant (" + max + "). Upgrade the subscription to add more.");
            }
        });
    }

    static String sanitizePrefix(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "BR";
        }
        String cleaned = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "");
        if (cleaned.isEmpty()) {
            return "BR";
        }
        return cleaned.length() > 8 ? cleaned.substring(0, 8) : cleaned;
    }

    static String buildPrefixedEmployeeId(String prefix, String deviceEmployeeNo) {
        String p = sanitizePrefix(prefix);
        String no = deviceEmployeeNo == null ? "" : deviceEmployeeNo.trim();
        return p + "-" + no;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Long resolveTenantId(List<DeviceConfig> devices) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) return tenantId;
        return devices.stream()
                .map(DeviceConfig::getTenantId)
                .filter(id -> id != null)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("tenantId could not be resolved for import"));
    }

    private Long resolveIsapiDeviceId(DeviceConfig device) {
        if (!StringUtils.hasText(device.getDeviceId())) return null;
        try {
            return Long.valueOf(device.getDeviceId().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeName(String name) {
        if (!StringUtils.hasText(name)) return "";
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private HttpHeaders buildHeaders(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(username)) {
            String credentials = username + ":" + (password != null ? password : "");
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }
        return headers;
    }

    private String buildBaseUrl(DeviceConfig device) {
        String ip = device.getDeviceIp().trim();
        int port = (device.getDevicePort() != null && device.getDevicePort() > 0)
                ? device.getDevicePort() : 80;
        if (port == 80) return "http://" + ip;
        if (port == 443) return "https://" + ip;
        return "http://" + ip + ":" + port;
    }

    private String decryptPassword(String passwordEncrypted) {
        if (!StringUtils.hasText(passwordEncrypted)) return "";
        try {
            return encryptionUtil.decrypt(passwordEncrypted);
        } catch (Exception ex) {
            log.warn("Could not decrypt device password – using value as-is: {}", ex.getMessage());
            return passwordEncrypted;
        }
    }

    private LocalDate parseLocalDate(String dateTimeStr) {
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException e1) {
            try {
                return LocalDate.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e2) {
                log.warn("Could not parse date '{}' from device – leaving null", dateTimeStr);
                return null;
            }
        }
    }

    private static final class AggregatedPerson {
        private String employeeNo;
        private String name;
        private String gender;
        private String beginTime;
        private boolean conflict;
        private String conflictReason;
        private final Set<Long> deviceConfigIds = new LinkedHashSet<>();
    }

    private record FetchedPerson(String employeeNo, String name, String gender, String beginTime) {}

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class IsapiPersonDto {
        private String employeeNo;
        private String name;
        private String userType;
        private String gender;
        private String beginTime;
        private String endTime;
    }
}
