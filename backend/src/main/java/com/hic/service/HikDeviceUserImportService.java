package com.hic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hic.dto.HikUserInfoSearchDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.DeviceConfig;
import com.hic.model.Employee;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.EncryptionUtil;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Pulls all users registered on a Hikvision device via the ISAPI
 * {@code /ISAPI/AccessControl/UserInfo/Search?format=json} endpoint,
 * handles pagination, and persists new employees to the local database.
 *
 * <p>Authentication: Hikvision devices accept HTTP Basic Auth. The
 * {@code Authorization: Basic <base64>} header is sent on every request
 * so no challenge-response round-trip is needed. The plain Spring
 * {@link RestTemplate} is used — no Apache HttpClient dependency required.
 *
 * <p>Pagination: the request body carries {@code searchResultPosition}
 * (zero-based offset) and {@code maxResults} (page size). The loop advances
 * the offset by {@code PAGE_SIZE} each iteration and stops when the device
 * returns {@code "NO MATCHES"} or an empty / null user list.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HikDeviceUserImportService {

    private static final String SEARCH_PATH       = "/ISAPI/AccessControl/UserInfo/Search?format=json";
    private static final String SEARCH_ID         = "sync_users_process";
    private static final int    PAGE_SIZE         = 100;
    private static final String STATUS_NO_MATCHES = "NO MATCHES";

    private final RestTemplate           restTemplate;
    private final DeviceConfigRepository deviceConfigRepository;
    private final EmployeeRepository     employeeRepository;
    private final EncryptionUtil         encryptionUtil;
    private final ObjectMapper           objectMapper;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Imports all users from the given {@code deviceConfigId} into the local
     * employee table. Already-existing employees (matched by {@code employeeNo})
     * are skipped so manual HR edits are never overwritten.
     *
     * @param deviceConfigId PK of the {@link DeviceConfig} row
     * @return a summary of the import operation
     */
    public HikUserInfoSearchDTO.ImportResultDTO importUsersFromDevice(Long deviceConfigId) {
        DeviceConfig device = deviceConfigRepository.findById(deviceConfigId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceConfig", deviceConfigId));

        String baseUrl  = buildBaseUrl(device);
        String username = device.getUsername();
        String password = decryptPassword(device.getPasswordEncrypted());

        log.info("Starting Hikvision user import: deviceConfigId={}, url={}{}",
                deviceConfigId, baseUrl, SEARCH_PATH);

        List<HikUserInfoSearchDTO.UserInfo> allUsers = fetchAllPages(baseUrl, username, password);

        log.info("Fetched {} user record(s) from device {}", allUsers.size(), baseUrl);

        return persistUsers(allUsers, device);
    }

    // -------------------------------------------------------------------------
    // Pagination loop
    // -------------------------------------------------------------------------

    /**
     * Pages through the device's user search endpoint until it reports no
     * more matches, collecting every {@link HikUserInfoSearchDTO.UserInfo}
     * record into a single list.
     */
    private List<HikUserInfoSearchDTO.UserInfo> fetchAllPages(
            String baseUrl, String username, String password) {

        String      url     = baseUrl + SEARCH_PATH;
        HttpHeaders headers = buildHeaders(username, password);

        List<HikUserInfoSearchDTO.UserInfo> result = new ArrayList<>();
        int offset = 0;

        while (true) {
            HikUserInfoSearchDTO.SearchRequest body = new HikUserInfoSearchDTO.SearchRequest(
                    new HikUserInfoSearchDTO.SearchCond(SEARCH_ID, PAGE_SIZE, offset)
            );

            log.debug("Requesting page: offset={}, pageSize={}", offset, PAGE_SIZE);

            HttpEntity<HikUserInfoSearchDTO.SearchRequest> entity = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<String> raw = restTemplate.exchange(
                        url, HttpMethod.POST, entity, String.class);

                if (raw.getBody() == null) {
                    log.warn("Empty response body from device at offset {}", offset);
                    break;
                }

                HikUserInfoSearchDTO.SearchResponse response =
                        objectMapper.readValue(raw.getBody(), HikUserInfoSearchDTO.SearchResponse.class);

                HikUserInfoSearchDTO.SearchResult sr = response.getUserInfoSearch();
                if (sr == null) {
                    log.warn("UserInfoSearch block is null in device response at offset {}", offset);
                    break;
                }

                String                            status = sr.getResponseStatusStrg();
                List<HikUserInfoSearchDTO.UserInfo> page = sr.getUserInfoList();

                if (STATUS_NO_MATCHES.equalsIgnoreCase(status) || page == null || page.isEmpty()) {
                    log.info("No more results from device (status='{}', offset={})", status, offset);
                    break;
                }

                result.addAll(page);
                log.info("Page fetched: {} record(s), cumulative={}", page.size(), result.size());

                // Fewer records than requested → this was the last page
                if (page.size() < PAGE_SIZE) {
                    break;
                }

                offset += PAGE_SIZE;

            } catch (Exception ex) {
                log.error("Error fetching users from device at offset {}: {}",
                        offset, ex.getMessage(), ex);
                break;
            }
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    /**
     * Saves fetched users to the {@code employees} table.
     * Employees already present (matched on {@code employeeNo} + tenant) are skipped.
     */
    private HikUserInfoSearchDTO.ImportResultDTO persistUsers(
            List<HikUserInfoSearchDTO.UserInfo> users, DeviceConfig device) {

        Long tenantId = TenantContext.getTenantId();
        // Fall back to the device's own tenant when the thread-local is absent
        // (e.g. called from a scheduled job rather than an HTTP request).
        if (tenantId == null) {
            tenantId = device.getTenantId();
        }

        int created = 0;
        int skipped = 0;
        int errors  = 0;

        for (HikUserInfoSearchDTO.UserInfo userInfo : users) {
            try {
                if (!StringUtils.hasText(userInfo.getEmployeeNo())) {
                    log.warn("Skipping device user with blank employeeNo");
                    skipped++;
                    continue;
                }

                String employeeNo = userInfo.getEmployeeNo().trim();

                // Do NOT overwrite manually-edited employee records.
                Optional<Employee> existing = tenantId != null
                        ? employeeRepository.findByTenantIdAndEmployeeIdIgnoreCase(tenantId, employeeNo)
                        : employeeRepository.findByEmployeeIdIgnoreCase(employeeNo);

                if (existing.isPresent()) {
                    log.debug("Employee {} already exists – skipping", employeeNo);
                    skipped++;
                    continue;
                }

                Employee emp = mapToEmployee(userInfo, tenantId, device.getBranchId());
                employeeRepository.save(emp);
                created++;
                log.info("Imported employee: employeeNo={}, name={}", employeeNo, userInfo.getName());

            } catch (Exception ex) {
                log.error("Failed to persist device user '{}': {}",
                        userInfo.getEmployeeNo(), ex.getMessage(), ex);
                errors++;
            }
        }

        String message = String.format(
                "Import complete: fetched=%d, created=%d, skipped=%d, errors=%d",
                users.size(), created, skipped, errors);
        log.info(message);
        return new HikUserInfoSearchDTO.ImportResultDTO(users.size(), created, skipped, errors, message);
    }

    /**
     * Maps a device {@link HikUserInfoSearchDTO.UserInfo} record to a local
     * {@link Employee} entity. Only device-supplied fields are populated;
     * HR-specific fields (salary, department, timetable, etc.) are left null
     * so an HR manager can fill them in via the UI after import.
     */
    private Employee mapToEmployee(
            HikUserInfoSearchDTO.UserInfo userInfo, Long tenantId, Long branchId) {

        Employee emp = new Employee();
        emp.setEmployeeId(userInfo.getEmployeeNo().trim());
        emp.setTenantId(tenantId);
        emp.setBranchId(branchId);
        emp.setEmploymentStatus(Employee.EmploymentStatus.ACTIVE);

        // --- Name ---
        // Device stores the full name in one field, e.g. "John Doe" or just "John".
        String fullName = StringUtils.hasText(userInfo.getName()) ? userInfo.getName().trim() : "";
        String[] parts  = fullName.split("\\s+", 2);
        emp.setFirstName(parts.length > 0 && !parts[0].isEmpty() ? parts[0] : fullName);
        emp.setLastName(parts.length > 1 ? parts[1] : "");

        // --- Gender ---
        if (StringUtils.hasText(userInfo.getGender())) {
            // Normalise device values ("male"/"female") to our DB convention (uppercase).
            emp.setGender(userInfo.getGender().toUpperCase());
        }

        // --- Hire date: derived from the validity begin time on the device ---
        if (userInfo.getValid() != null && StringUtils.hasText(userInfo.getValid().getBeginTime())) {
            emp.setHireDate(parseLocalDate(userInfo.getValid().getBeginTime()));
        }
        if (emp.getHireDate() == null) {
            emp.setHireDate(LocalDate.now());
        }

        return emp;
    }

    // -------------------------------------------------------------------------
    // HTTP / Auth helpers
    // -------------------------------------------------------------------------

    /**
     * Builds request headers with:
     * <ul>
     *   <li>{@code Content-Type: application/json}</li>
     *   <li>{@code Accept: application/json}</li>
     *   <li>{@code Authorization: Basic <base64(user:pass)>} — sent preemptively
     *       so no WWW-Authenticate challenge round-trip is required.</li>
     * </ul>
     *
     * <p>Note: pure Spring {@link RestTemplate} is used here deliberately — the
     * project carries {@code httpclient} 4.x which is incompatible with Spring
     * Boot 3.x's {@code HttpComponentsClientHttpRequestFactory} (which expects
     * httpclient 5.x / {@code org.apache.hc.client5}). Sending the Basic Auth
     * header preemptively is perfectly sufficient for Hikvision devices.
     */
    private HttpHeaders buildHeaders(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        if (StringUtils.hasText(username)) {
            String credentials = username + ":" + (password != null ? password : "");
            String encoded     = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }
        return headers;
    }

    /**
     * Constructs the device base URL from its stored IP and port.
     * Defaults to port 80 (HTTP) when no port is configured.
     */
    private String buildBaseUrl(DeviceConfig device) {
        String ip   = device.getDeviceIp().trim();
        int    port = (device.getDevicePort() != null && device.getDevicePort() > 0)
                      ? device.getDevicePort()
                      : 80;
        if (port == 80)  return "http://"  + ip;
        if (port == 443) return "https://" + ip;
        return "http://" + ip + ":" + port;
    }

    /**
     * Decrypts the stored device password using {@link EncryptionUtil}.
     * Returns the raw value unchanged when it is not encrypted
     * (i.e. does not start with {@code "ENC:"}).
     */
    private String decryptPassword(String passwordEncrypted) {
        if (!StringUtils.hasText(passwordEncrypted)) {
            return "";
        }
        try {
            return encryptionUtil.decrypt(passwordEncrypted);
        } catch (Exception ex) {
            log.warn("Could not decrypt device password – using value as-is: {}", ex.getMessage());
            return passwordEncrypted;
        }
    }

    /**
     * Parses the ISO-8601 date-time string returned by the device into a
     * {@link LocalDate}. Returns {@code null} on any parse failure so the
     * caller can substitute a sensible default.
     */
    private LocalDate parseLocalDate(String dateTimeStr) {
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                .toLocalDate();
        } catch (DateTimeParseException e1) {
            try {
                return LocalDate.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e2) {
                log.warn("Could not parse date '{}' from device – leaving null", dateTimeStr);
                return null;
            }
        }
    }
}
