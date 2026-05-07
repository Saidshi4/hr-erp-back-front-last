package com.hic.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Client service that delegates device operations to the ISAPI microservice
 * via its REST API instead of calling device endpoints directly.
 *
 * <p>ISAPI base URL is configured via {@code isapi.base-url} in application.yml
 * (default: {@code http://localhost:8081}).
 *
 * <p>The {@code enabled} flag sent during device registration is controlled by
 * {@code isapi.device-enabled-default} (default: {@code false}).  Keeping it
 * {@code false} prevents ISAPI from starting the alert-stream immediately on
 * registration; use {@link #startDevice(String)} to activate a device explicitly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IsapiClientService {

    private final RestTemplate restTemplate;

    @Value("${isapi.base-url:http://localhost:8081}")
    private String isapiBaseUrl;

    @Value("${isapi.device-enabled-default:false}")
    private boolean deviceEnabledDefault;

    // -----------------------------------------------------------------------
    // Device status check
    // -----------------------------------------------------------------------

    /**
     * Checks device connectivity by delegating to the ISAPI service.
     *
     * <p>The method first looks up the ISAPI-side device record for the given
     * {@code ip}. If no record exists yet it registers the device in ISAPI and
     * then queries its status. Returns {@code true} only when ISAPI reports the
     * device as online.
     *
     * @param ip       device IP address
     * @param port     device port (used only when creating a new ISAPI record)
     * @param username device username
     * @param password plain-text device password
     * @return {@code true} if the device is reachable/online according to ISAPI
     */
    public boolean checkDeviceConnectivity(String ip, int port, String username, String password) {
        try {
            Long isapiDeviceId = findIsapiDeviceIdByIp(ip);
            if (isapiDeviceId == null) {
                isapiDeviceId = registerDeviceInIsapi(ip, port, username, password);
            }
            if (isapiDeviceId == null) {
                log.warn("IsapiClientService: could not resolve ISAPI device id for ip={}", ip);
                return false;
            }
            return isDeviceOnline(isapiDeviceId);
        } catch (RestClientException e) {
            log.warn("IsapiClientService: REST call failed while checking device ip={}: {}", ip, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("IsapiClientService: unexpected error checking device ip={}: {}", ip, e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Device lifecycle (start / stop / status / enabled)
    // -----------------------------------------------------------------------

    /**
     * Asks ISAPI to start the alert-stream worker for the device with the given IP.
     */
    public void startDevice(String ip) {
        try {
            Long id = findIsapiDeviceIdByIp(ip);
            if (id == null) {
                log.warn("IsapiClientService: cannot start – no ISAPI device found for ip={}", ip);
                return;
            }
            restTemplate.postForObject(isapiBaseUrl + "/api/devices/" + id + "/start",
                    new HttpEntity<>(jsonHeaders()), Object.class);
            log.info("IsapiClientService: started device id={} ip={}", id, ip);
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to start device ip={}: {}", ip, e.getMessage());
        }
    }

    /**
     * Asks ISAPI to stop the alert-stream worker for the device with the given IP.
     */
    public void stopDevice(String ip) {
        try {
            Long id = findIsapiDeviceIdByIp(ip);
            if (id == null) {
                log.warn("IsapiClientService: cannot stop – no ISAPI device found for ip={}", ip);
                return;
            }
            restTemplate.postForObject(isapiBaseUrl + "/api/devices/" + id + "/stop",
                    new HttpEntity<>(jsonHeaders()), Object.class);
            log.info("IsapiClientService: stopped device id={} ip={}", id, ip);
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to stop device ip={}: {}", ip, e.getMessage());
        }
    }

    /**
     * Starts the alert-stream for the ISAPI device identified by {@code isapiDeviceId}
     * and returns the runtime response from ISAPI.
     */
    public Map<String, Object> startIsapiDevice(Long isapiDeviceId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/start",
                    new HttpEntity<>(jsonHeaders()), Map.class);
            return result;
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to start ISAPI device id={}: {}", isapiDeviceId, e.getMessage());
            return null;
        }
    }

    /**
     * Stops the alert-stream for the ISAPI device identified by {@code isapiDeviceId}
     * and returns the runtime response from ISAPI.
     */
    public Map<String, Object> stopIsapiDevice(Long isapiDeviceId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/stop",
                    new HttpEntity<>(jsonHeaders()), Map.class);
            return result;
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to stop ISAPI device id={}: {}", isapiDeviceId, e.getMessage());
            return null;
        }
    }

    /**
     * Returns the online status for the ISAPI device identified by {@code isapiDeviceId}.
     */
    public Map<String, Object> getIsapiDeviceStatus(Long isapiDeviceId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> status = restTemplate.getForObject(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/status",
                    Map.class);
            return status;
        } catch (RestClientException e) {
            log.warn("IsapiClientService: status check failed for ISAPI device id={}: {}", isapiDeviceId, e.getMessage());
            return null;
        }
    }

    /**
     * Patches the {@code enabled} flag on the ISAPI device and returns the response.
     */
    public Map<String, Object> patchIsapiDeviceEnabled(Long isapiDeviceId, boolean enabled) {
        try {
            Map<String, Object> body = Map.of("enabled", enabled);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, jsonHeaders());
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/enabled",
                    HttpMethod.PATCH, entity,
                    new ParameterizedTypeReference<>() {});
            return response.getBody();
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to patch enabled for ISAPI device id={}: {}", isapiDeviceId, e.getMessage());
            return null;
        }
    }

    /**
     * Resets the ISAPI event cursor ({@code lastSerialNo} and {@code lastEventTime})
     * for the given ISAPI device ID.
     */
    public Map<String, Object> resetIsapiDeviceCursor(Long isapiDeviceId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/cursor/reset",
                    new HttpEntity<>(jsonHeaders()), Map.class);
            return result;
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to reset cursor for ISAPI device id={}: {}", isapiDeviceId, e.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Device user proxy
    // -----------------------------------------------------------------------

    /**
     * Lists all users registered on the ISAPI device identified by {@code isapiDeviceId}.
     */
    public List<Map<String, Object>> listIsapiDeviceUsers(Long isapiDeviceId) {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/users",
                    HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
                    new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to list users for ISAPI device id={}: {}", isapiDeviceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Returns a single device user from ISAPI.
     */
    public Map<String, Object> getIsapiDeviceUser(Long isapiDeviceId, Long userId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.getForObject(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/users/" + userId,
                    Map.class);
            return result;
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to get user id={} for ISAPI device id={}: {}",
                    userId, isapiDeviceId, e.getMessage());
            return null;
        }
    }

    /**
     * Creates a device user in ISAPI.
     *
     * @param isapiDeviceId ISAPI device ID
     * @param body          request body fields (employeeNo, name, userType, gender, beginTime, endTime, faceDataUrl)
     * @return the created user response from ISAPI
     */
    public Map<String, Object> createIsapiDeviceUser(Long isapiDeviceId, Map<String, Object> body) {
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, jsonHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/users",
                    entity, Map.class);
            return result;
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to create user for ISAPI device id={}: {}", isapiDeviceId, e.getMessage());
            return null;
        }
    }

    /**
     * Updates a device user in ISAPI.
     */
    public Map<String, Object> updateIsapiDeviceUser(Long isapiDeviceId, Long userId, Map<String, Object> body) {
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, jsonHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/users/" + userId,
                    HttpMethod.PUT, entity,
                    new ParameterizedTypeReference<>() {});
            return response.getBody();
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to update user id={} for ISAPI device id={}: {}",
                    userId, isapiDeviceId, e.getMessage());
            return null;
        }
    }

    /**
     * Deletes a device user from ISAPI.
     */
    public void deleteIsapiDeviceUser(Long isapiDeviceId, Long userId) {
        try {
            restTemplate.exchange(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/users/" + userId,
                    HttpMethod.DELETE, new HttpEntity<>(jsonHeaders()), Void.class);
            log.info("IsapiClientService: deleted user id={} from ISAPI device id={}", userId, isapiDeviceId);
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to delete user id={} for ISAPI device id={}: {}",
                    userId, isapiDeviceId, e.getMessage());
        }
    }

    /**
     * Triggers sync of a device user to the physical device via ISAPI.
     */
    public Map<String, Object> syncIsapiDeviceUser(Long isapiDeviceId, Long userId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/users/" + userId + "/sync",
                    new HttpEntity<>(jsonHeaders()), Map.class);
            return result;
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to sync user id={} for ISAPI device id={}: {}",
                    userId, isapiDeviceId, e.getMessage());
            return null;
        }
    }

    /**
     * Uploads a face image for a device user via ISAPI (multipart relay).
     *
     * @param isapiDeviceId ISAPI device ID
     * @param userId        ISAPI user ID
     * @param file          face image multipart file
     * @return the ISAPI response body, or {@code null} on failure
     */
    public Map<String, Object> uploadFaceToIsapiDeviceUser(Long isapiDeviceId, Long userId, MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "face.jpg";

            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
            formData.add("file", resource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(formData, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/users/" + userId + "/face",
                    entity, Map.class);
            return result;
        } catch (IOException e) {
            log.warn("IsapiClientService: failed to read face file for ISAPI device id={} user id={}: {}",
                    isapiDeviceId, userId, e.getMessage());
            return null;
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to upload face for ISAPI device id={} user id={}: {}",
                    isapiDeviceId, userId, e.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Event / punch proxy
    // -----------------------------------------------------------------------

    /**
     * Returns attendance punch records from ISAPI.
     *
     * @param deviceId    optional ISAPI device ID filter
     * @param employeeNo  optional employee number filter
     * @param limit       max results (ISAPI default 50)
     */
    public List<Map<String, Object>> getIsapiPunches(Long deviceId, String employeeNo, Integer limit) {
        try {
            String url = buildUrl("/api/punches",
                    "deviceId", deviceId,
                    "employeeNo", employeeNo,
                    "limit", limit);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
                    new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to get punches: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Returns raw ACS event records from ISAPI.
     */
    public List<Map<String, Object>> getIsapiRawEvents(Long deviceId, Integer major, Integer minor,
                                                         Boolean includeRawJson, Integer limit) {
        try {
            String url = buildUrl("/api/raw-events",
                    "deviceId", deviceId,
                    "major", major,
                    "minor", minor,
                    "includeRawJson", includeRawJson,
                    "limit", limit);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
                    new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to get raw events: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Returns failed access attempt records from ISAPI.
     */
    public List<Map<String, Object>> getIsapiFailedAttempts(Long deviceId, Integer limit) {
        try {
            String url = buildUrl("/api/failed-attempts",
                    "deviceId", deviceId,
                    "limit", limit);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
                    new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to get failed attempts: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the ISAPI-internal device ID for the given IP, or {@code null} if
     * no matching device exists in ISAPI.
     */
    public Long findIsapiDeviceIdByIp(String ip) {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    isapiBaseUrl + "/api/devices",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {});

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return null;
            }

            return response.getBody().stream()
                    .filter(d -> ip.equals(d.get("ip")))
                    .map(d -> toLong(d.get("id")))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to list ISAPI devices: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Registers a new device in ISAPI and returns its assigned ID.
     * Returns {@code null} if the registration fails.
     */
    private Long registerDeviceInIsapi(String ip, int port, String username, String password) {
        try {
            Map<String, Object> body = Map.of(
                    "ip", ip,
                    "port", port,
                    "username", username,
                    "password", password,
                    "enabled", deviceEnabledDefault);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, jsonHeaders());

            @SuppressWarnings("unchecked")
            Map<String, Object> created = restTemplate.postForObject(
                    isapiBaseUrl + "/api/devices", entity, Map.class);

            if (created == null) {
                log.warn("IsapiClientService: ISAPI returned null body when registering ip={}", ip);
                return null;
            }
            Long id = toLong(created.get("id"));
            log.info("IsapiClientService: registered device in ISAPI id={} ip={}", id, ip);
            return id;
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to register device ip={} in ISAPI: {}", ip, e.getMessage());
            return null;
        }
    }

    /**
     * Queries ISAPI for the online status of the device identified by {@code isapiDeviceId}.
     */
    private boolean isDeviceOnline(Long isapiDeviceId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> status = restTemplate.getForObject(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/status",
                    Map.class);

            if (status == null) {
                return false;
            }
            Object online = status.get("online");
            return Boolean.TRUE.equals(online);
        } catch (RestClientException e) {
            log.warn("IsapiClientService: status check failed for ISAPI device id={}: {}", isapiDeviceId, e.getMessage());
            return false;
        }
    }

    /** Returns HTTP headers with {@code Content-Type: application/json} and {@code Accept: application/json}. */
    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    /**
     * Builds a properly encoded URL with optional query parameters.
     * Null values are omitted. String values are percent-encoded by
     * {@link UriComponentsBuilder} to prevent query-parameter injection.
     */
    private String buildUrl(String path, Object... pairs) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(isapiBaseUrl + path);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object val = pairs[i + 1];
            if (val != null) {
                builder.queryParam((String) pairs[i], val);
            }
        }
        return builder.toUriString();
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
