package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.IsapiDTO;
import com.hic.service.DeviceSyncService;
import com.hic.service.IsapiClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Proxies device-user management operations to the ISAPI microservice.
 *
 * <p>All paths use the <em>backend</em> device ID ({@code deviceId}) which is
 * transparently mapped to the corresponding ISAPI device ID via the device IP.
 * The device must have been synced at least once so that a matching record
 * exists in ISAPI.
 */
@RestController
@RequestMapping("/api/devices/{deviceId}/users")
@RequiredArgsConstructor
public class DeviceUserProxyController {

    private final DeviceSyncService deviceSyncService;
    private final IsapiClientService isapiClientService;

    /**
     * Lists all users registered on the ISAPI device that corresponds to the
     * given backend {@code deviceId}.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listUsers(
            @PathVariable Long deviceId) {
        Long isapiId = resolveIsapiId(deviceId);
        List<Map<String, Object>> users = isapiClientService.listIsapiDeviceUsers(isapiId);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /**
     * Returns a single device user by its ISAPI-assigned {@code userId}.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUser(
            @PathVariable Long deviceId,
            @PathVariable Long userId) {
        Long isapiId = resolveIsapiId(deviceId);
        Map<String, Object> user = isapiClientService.getIsapiDeviceUser(isapiId, userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device user not found");
        }
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * Creates a new device user in ISAPI and syncs it to the physical device.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createUser(
            @PathVariable Long deviceId,
            @RequestBody IsapiDTO.DeviceUserCreateRequest request) {
        Long isapiId = resolveIsapiId(deviceId);
        // employeeNo and name are required; optional fields are only sent when non-null
        Map<String, Object> body = buildNonNullMap(
                "employeeNo", request.getEmployeeNo(),
                "name", request.getName(),
                "userType", request.getUserType(),
                "gender", request.getGender(),
                "beginTime", request.getBeginTime(),
                "endTime", request.getEndTime(),
                "faceDataUrl", request.getFaceDataUrl()
        );
        Map<String, Object> created = isapiClientService.createIsapiDeviceUser(isapiId, body);
        if (created == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ISAPI returned no response when creating user");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    /**
     * Updates an existing device user in ISAPI.
     */
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUser(
            @PathVariable Long deviceId,
            @PathVariable Long userId,
            @RequestBody IsapiDTO.DeviceUserUpdateRequest request) {
        Long isapiId = resolveIsapiId(deviceId);
        Map<String, Object> body = buildNonNullMap(
                "name", request.getName(),
                "userType", request.getUserType(),
                "gender", request.getGender(),
                "beginTime", request.getBeginTime(),
                "endTime", request.getEndTime(),
                "faceDataUrl", request.getFaceDataUrl()
        );
        Map<String, Object> updated = isapiClientService.updateIsapiDeviceUser(isapiId, userId, body);
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ISAPI returned no response when updating user");
        }
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    /**
     * Deletes a device user from ISAPI.
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable Long deviceId,
            @PathVariable Long userId) {
        Long isapiId = resolveIsapiId(deviceId);
        isapiClientService.deleteIsapiDeviceUser(isapiId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Triggers an immediate sync of the device user to the physical device.
     */
    @PostMapping("/{userId}/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncUser(
            @PathVariable Long deviceId,
            @PathVariable Long userId) {
        Long isapiId = resolveIsapiId(deviceId);
        Map<String, Object> result = isapiClientService.syncIsapiDeviceUser(isapiId, userId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ISAPI returned no response during user sync");
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Uploads a face image for the device user to ISAPI.
     */
    @PostMapping(value = "/{userId}/face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadFace(
            @PathVariable Long deviceId,
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file) {
        Long isapiId = resolveIsapiId(deviceId);
        Map<String, Object> result = isapiClientService.uploadFaceToIsapiDeviceUser(isapiId, userId, file);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ISAPI returned no response during face upload");
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Long resolveIsapiId(Long backendDeviceId) {
        String ip = deviceSyncService.getDeviceIp(backendDeviceId);
        Long isapiId = isapiClientService.findIsapiDeviceIdByIp(ip);
        if (isapiId == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Device is not registered in ISAPI yet. Please run sync first.");
        }
        return isapiId;
    }

    /** Builds a map including only keys whose values are non-null. */
    private Map<String, Object> buildNonNullMap(Object... pairs) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                map.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return map;
    }
}
