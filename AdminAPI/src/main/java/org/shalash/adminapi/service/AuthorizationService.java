package org.shalash.adminapi.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.shalash.adminapi.dto.request.AddAutherizationRequest;
import org.shalash.adminapi.entity.Autherization;
import org.shalash.adminapi.entity.Credentials;
import org.shalash.adminapi.exception.UserNotFoundException;
import org.shalash.adminapi.repo.AuthorizationRepository;
import org.shalash.adminapi.repo.CredentialRepository;
import org.shalash.adminapi.repo.UserPermissionsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final AuthorizationRepository authorizationRepository;
    private final CredentialRepository credentialRepository;
    private final UserPermissionsRepository userPermissionsRepository;

    public ResponseEntity<String> addAutherization(AddAutherizationRequest request) {
        Autherization autherization = new Autherization();
        autherization.setPattern(request.getPattern());
        Credentials credentials = credentialRepository.findByAccessKey(request.getAccessKey());
        if (credentials == null) {
            throw new UserNotFoundException("user not found");
        }
        autherization.setUser(credentials);
        authorizationRepository.save(autherization);
        return ResponseEntity.status(HttpStatus.CREATED).body("created");
    }

    @Transactional
    public ResponseEntity<String> removeAutherization(String accessKey, String pattern) {
        Credentials credentials = credentialRepository.findByAccessKey(accessKey);
        if (credentials == null) {
            throw new UserNotFoundException("user not found");
        }

        Autherization autherization = authorizationRepository.findByUserIdAndPattern(credentials.getId(), pattern).orElseThrow(() -> new UserNotFoundException("autherization for user not found"));

        userPermissionsRepository.removeByAuthorization(autherization);
        authorizationRepository.delete(autherization);
        return ResponseEntity.status(HttpStatus.CREATED).body("deleted");
    }
}
