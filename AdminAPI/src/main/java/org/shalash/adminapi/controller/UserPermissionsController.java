package org.shalash.adminapi.controller;

import lombok.RequiredArgsConstructor;
import org.shalash.adminapi.dto.request.PermissionRequest;
import org.shalash.adminapi.service.UserPermissionsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class UserPermissionsController {

    private final UserPermissionsService userPermissionsService;

    @PostMapping
    public ResponseEntity<String> addPermissions(@RequestBody PermissionRequest request) {
        return userPermissionsService.addPermissions(request);
    }

    @DeleteMapping()
    public ResponseEntity<String> revokePermissions(@RequestParam String accessKey, @RequestParam String pattern,@RequestParam String permission) {
        return userPermissionsService.revokePermissions(accessKey,pattern,permission);
    }
}
