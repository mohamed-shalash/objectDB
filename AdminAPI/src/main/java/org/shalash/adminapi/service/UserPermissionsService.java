package org.shalash.adminapi.service;

import lombok.RequiredArgsConstructor;
import org.shalash.adminapi.dto.request.PermissionRequest;
import org.shalash.adminapi.entity.*;
import org.shalash.adminapi.exception.PermissionNotFoundException;
import org.shalash.adminapi.exception.UserNotFoundException;
import org.shalash.adminapi.repo.AuthorizationRepository;
import org.shalash.adminapi.repo.CredentialRepository;
import org.shalash.adminapi.repo.PermissionsRepository;
import org.shalash.adminapi.repo.UserPermissionsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserPermissionsService {

    private final PermissionsRepository permissionsRepository;
    private final UserPermissionsRepository userPermissionsRepository;
    private final AuthorizationRepository authorizationRepository;
    private final CredentialRepository credentialRepository;

    public ResponseEntity<String> addPermissions(PermissionRequest request) {
        Credentials credentials = credentialRepository.findByAccessKey(request.getAccessKey());
        if(credentials == null) {
            throw new UserNotFoundException("user not found");
        }
        Optional<Autherization>  autherization = authorizationRepository.findByUserIdAndPattern(credentials.getId(), request.getPattern());
        if(autherization.isEmpty()) {
            throw new UserNotFoundException("autherization for user not found");
        }

        Permissions permissions =permissionsRepository.findByAuthority(request.getAuthority());
        if(permissions == null) {
            throw new PermissionNotFoundException("permission not found");
        }
        UserPermissionId userPermissionId =
                UserPermissionId.builder()
                        .authorizationId(autherization.get().getId())
                        .permissionId(permissions.getId())
                        .build();
        userPermissionsRepository.save(new UserPermissions(userPermissionId, autherization.get(), permissions));

        return ResponseEntity.status(HttpStatus.CREATED).body("created");
    }


    public ResponseEntity<String> revokePermissions(String accessKey, String pattern,String permission) {
        Credentials credentials = credentialRepository.findByAccessKey(accessKey);
        if(credentials == null) {
            throw new UserNotFoundException("user not found");
        }
        Optional<Autherization>  autherization = authorizationRepository.findByUserIdAndPattern(credentials.getId(), pattern);
        if(autherization.isEmpty()) {
            throw new UserNotFoundException("autherization for user not found");
        }
        Permissions permissions =permissionsRepository.findByAuthority(permission);
        if(permissions == null) {
            throw new PermissionNotFoundException("permission not found");
        }

        UserPermissionId userPermissionId =
                UserPermissionId.builder()
                        .authorizationId(autherization.get().getId())
                        .permissionId(permissions.getId())
                        .build();

        userPermissionsRepository.delete(new UserPermissions(userPermissionId, autherization.get(), permissions));
        return ResponseEntity.status(HttpStatus.CREATED).body("created");
    }
}
