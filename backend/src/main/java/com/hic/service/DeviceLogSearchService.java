package com.hic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hic.dto.DeviceLogSearchDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.DeviceConfig;
import com.hic.repository.DeviceConfigRepository;
import com.hic.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Proxies Hikvision ISAPI {@code /ISAPI/AccessControl/AcsEvent} search requests
 * directly to the physical device.  Nothing is written to the database.
 *
 * <h3>Authentication</h3>
 * Uses Spring {@link RestTemplate} with Preemptive Basic Authentication.
 * This ensures compatibility with devices that may fail Digest challenge sequences
 * for specific ISAPI paths, mirroring the approach in HikDeviceUserImportService.
 *
 * <h3>Event filter</h3>
 * The ISAPI body always sends {@code major=5, minor=75}, which is the
 * "card/face verification success" event — confirmed via live Postman tests against
 * the real device.  Only these events carry {@code name}, {@code employeeNoString},
 * {@code cardNo}, and {@code pictureURL} fields.
 *
 * <h3>Picture URLs</h3>
 * The {@code pictureURL} field includes a trailing device token
 * (e.g. {@code …0.jpeg@WEB000000001062}) that must be preserved exactly as returned
 * by the device.  It is passed through unchanged to the photo-proxy endpoint.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceLogSearchService {

    private static final String ACS_EVENT_PATH = "/ISAPI/AccessControl/AcsEvent?format=json";

    /**
     * major=5  → Access Control category.
     * minor=75 → Card/face verification success (confirmed on real device).
     */
    private static final int MAJOR = 5;
    private static final int MINOR = 75;

    /** 10-second timeout for all device calls. */
    private static final int TIMEOUT_MS = 10_000;

    private final DeviceConfigRepository deviceConfigRepository;
    private final EncryptionUtil         encryptionUtil;
    private final ObjectMapper           objectMapper;
    private final RestTemplate           restTemplate;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Searches for Access Control events on the specified Hikvision device.
     *
     * @param deviceConfigId PK of the {@link DeviceConfig} row
     * @param employeeId     optional employee number to filter (sent to device as employeeNoString)
     * @param name           optional name substring to filter client-side (ISAPI does not support name filter)
     * @param cardNo         optional card number to filter (sent to device)
     * @param startTime      ISO-8601 start time string (e.g. {@code 2026-07-08T00:00:00+04:00})
     * @param endTime        ISO-8601 end time string
     * @param page           zero-based page number
     * @param pageSize       number of results per page (maps to ISAPI {@code maxResults})
     * @return paged search result with matched events and total count
     */
    public DeviceLogSearchDTO.SearchResultDTO search(
            Long   deviceConfigId,
            String employeeId,
            String name,
            String cardNo,
            String startTime,
            String endTime,
            int    page,
            int    pageSize
    ) {
        DeviceConfig device = deviceConfigRepository.findById(deviceConfigId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceConfig", deviceConfigId));

        String baseUrl  = buildBaseUrl(device);
        String username = device.getUsername();
        String password = decryptPassword(device.getPasswordEncrypted());

        int searchResultPosition = page * pageSize;

        DeviceLogSearchDTO.AcsEventCond cond = DeviceLogSearchDTO.AcsEventCond.builder()
                .searchID(UUID.randomUUID().toString())
                .searchResultPosition(searchResultPosition)
                .maxResults(pageSize)
                .major(MAJOR)
                .minor(MINOR)
                .startTime(startTime  != null ? startTime  : "")
                .endTime(endTime      != null ? endTime    : "")
                .employeeNoString(StringUtils.hasText(employeeId) ? employeeId.trim() : "")
                .cardNo(StringUtils.hasText(cardNo) ? cardNo.trim() : "")
                .picEnable(true)
                .build();

        DeviceLogSearchDTO.SearchRequest requestBody =
                DeviceLogSearchDTO.SearchRequest.builder().acsEventCond(cond).build();

        String targetUrl = baseUrl + ACS_EVENT_PATH;
        log.info("Device log search: deviceConfigId={}, url={}, page={}, pageSize={}",
                deviceConfigId, targetUrl, page, pageSize);

        try {
            String rawResponse = postWithBasicAuth(
                    targetUrl, username, password,
                    objectMapper.writeValueAsString(requestBody)
            );

            DeviceLogSearchDTO.DeviceResponse deviceResponse =
                    objectMapper.readValue(rawResponse, DeviceLogSearchDTO.DeviceResponse.class);

            DeviceLogSearchDTO.AcsEvent acsEvent = deviceResponse.getAcsEvent();
            if (acsEvent == null) {
                log.warn("Device response contained no AcsEvent block for deviceConfigId={}", deviceConfigId);
                return emptyResult(page, pageSize);
            }

            List<DeviceLogSearchDTO.EventInfo> infoList =
                    acsEvent.getInfoList() != null ? acsEvent.getInfoList() : Collections.emptyList();

            log.info("Device returned {} matches (total={}) for deviceConfigId={}",
                    acsEvent.getNumOfMatches(), acsEvent.getTotalMatches(), deviceConfigId);

            // Apply client-side name filter — ISAPI does not support name-based filtering
            List<DeviceLogSearchDTO.EventEntryDTO> items = infoList.stream()
                    .filter(info -> !StringUtils.hasText(name) ||
                            (info.getName() != null &&
                             info.getName().toLowerCase().contains(name.trim().toLowerCase())))
                    .map(this::toDTO)
                    .collect(Collectors.toList());

            return DeviceLogSearchDTO.SearchResultDTO.builder()
                    .items(items)
                    .totalMatches(acsEvent.getTotalMatches())
                    .numOfMatches(acsEvent.getNumOfMatches())
                    .page(page)
                    .pageSize(pageSize)
                    .responseStatus(acsEvent.getResponseStatusStrg())
                    .build();

        } catch (IOException e) {
            log.error("Network or JSON error communicating with device {}: {}", targetUrl, e.getMessage());
            throw new RuntimeException("Could not process device response. Check that it is online and reachable.", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during device log search for deviceConfigId={}: {}", deviceConfigId, e.getMessage(), e);
            throw new RuntimeException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Downloads an image from the Hikvision device using Digest Authentication and
     * returns the raw bytes.  The full {@code pictureURL} (including the trailing
     * {@code @WEB…} device token) must be passed unmodified.
     *
     * @param pictureUrl     full picture URL as returned by the device
     * @param deviceConfigId PK of the {@link DeviceConfig} row (for credentials)
     * @return JPEG image bytes
     */
    public byte[] fetchPicture(String pictureUrl, Long deviceConfigId) {
        DeviceConfig device = deviceConfigRepository.findById(deviceConfigId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceConfig", deviceConfigId));

        String username = device.getUsername();
        String password = decryptPassword(device.getPasswordEncrypted());

        log.info("Fetching picture from device: url={}, deviceConfigId={}", pictureUrl, deviceConfigId);
        try {
            return getWithBasicAuth(pictureUrl, username, password);
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("Network error fetching picture from {}: {}", pictureUrl, e.getMessage());
            throw new RuntimeException("Could not connect to the device to fetch the picture.", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP / Auth helpers
    // ─────────────────────────────────────────────────────────────────────────

    private HttpHeaders buildHeaders(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        if (StringUtils.hasText(username)) {
            String credentials = username + ":" + (password != null ? password : "");
            String encoded     = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }
        return headers;
    }

    private String postWithBasicAuth(String url, String username, String password, String body) {
        HttpHeaders headers = buildHeaders(username, password);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return response.getBody();
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new RuntimeException("Authentication failed (401): wrong username or password for device.");
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Device returned HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        }
    }

    private byte[] getWithBasicAuth(String url, String username, String password) {
        HttpHeaders headers = buildHeaders(username, password);
        // Overwrite Accept header for images
        headers.setAccept(List.of(MediaType.parseMediaType("image/jpeg"), MediaType.parseMediaType("image/*"), MediaType.ALL));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            return response.getBody();
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new RuntimeException("Authentication failed (401) when fetching picture.");
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Device returned HTTP " + e.getStatusCode() + " when fetching picture.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapping helpers
    // ─────────────────────────────────────────────────────────────────────────

    private DeviceLogSearchDTO.EventEntryDTO toDTO(DeviceLogSearchDTO.EventInfo info) {
        return DeviceLogSearchDTO.EventEntryDTO.builder()
                .employeeId(info.getEmployeeNoString())
                .name(info.getName())
                .cardNo(info.getCardNo())
                .eventDescription(resolveEventDescription(info.getMinor()))
                .time(info.getTime())
                .hasPicture(info.getPicturesNumber() != null && info.getPicturesNumber() > 0)
                .pictureURL(info.getPictureURL())
                .verifyMode(info.getCurrentVerifyMode())
                .build();
    }

    /**
     * Translates a Hikvision minor event code to a human-readable description.
     * minor=75 is the only value expected when the device is filtered with major=5/minor=75,
     * but additional codes are listed defensively.
     */
    private String resolveEventDescription(int minor) {
        return switch (minor) {
            case 75 -> "Card / Face Verification";
            case 22 -> "Door Open";
            case 21 -> "Door Closed";
            case 32 -> "Card Reader Offline";
            default -> "Access Control Event (" + minor + ")";
        };
    }

    private DeviceLogSearchDTO.SearchResultDTO emptyResult(int page, int pageSize) {
        return DeviceLogSearchDTO.SearchResultDTO.builder()
                .items(Collections.emptyList())
                .totalMatches(0)
                .numOfMatches(0)
                .page(page)
                .pageSize(pageSize)
                .responseStatus("NO MATCHES")
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Device URL / credential helpers (mirrors HikDeviceUserImportService pattern)
    // ─────────────────────────────────────────────────────────────────────────

    private String buildBaseUrl(DeviceConfig device) {
        String ip   = device.getDeviceIp().trim();
        int    port = (device.getDevicePort() != null && device.getDevicePort() > 0)
                      ? device.getDevicePort() : 80;
        if (port == 80)  return "http://"  + ip;
        if (port == 443) return "https://" + ip;
        return "http://" + ip + ":" + port;
    }

    private String decryptPassword(String passwordEncrypted) {
        if (!StringUtils.hasText(passwordEncrypted)) {
            return "";
        }
        try {
            return encryptionUtil.decrypt(passwordEncrypted);
        } catch (Exception ex) {
            log.warn("Could not decrypt device password — using value as-is: {}", ex.getMessage());
            return passwordEncrypted;
        }
    }
}
