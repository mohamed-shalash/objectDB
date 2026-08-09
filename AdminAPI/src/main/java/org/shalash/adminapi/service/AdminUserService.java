package org.shalash.adminapi.service;


import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.shalash.adminapi.dto.AutherizationResponse;
import org.shalash.adminapi.dto.request.GenerateHmacRequest;
import org.shalash.adminapi.dto.request.UserDto;
import org.shalash.adminapi.dto.request.UserRequest;
import org.shalash.adminapi.dto.response.GenerateHmacResponse;
import org.shalash.adminapi.dto.response.UserResponse;
import org.shalash.adminapi.entity.Autherization;
import org.shalash.adminapi.entity.Credentials;
import org.shalash.adminapi.entity.UserPermissions;
import org.shalash.adminapi.exception.UserNotFoundException;
import org.shalash.adminapi.repo.AuthorizationRepository;
import org.shalash.adminapi.repo.CredentialRepository;
import org.shalash.adminapi.repo.UserPermissionsRepository;
import org.shalash.adminapi.util.Utils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final CredentialRepository credentialRepository;
    private final UserPermissionsRepository userPermissionsRepository;
    private final AuthorizationRepository authorizationRepository;

    public ResponseEntity<UserDto> getByAccessKey(String accessKey) {
        UserDto userDto = getUserDto(accessKey);
        return  ResponseEntity.status(HttpStatus.OK).body(userDto);
    }

    public UserDto getUserDto(String accessKey) {
        Credentials credentials = credentialRepository.findByAccessKey(accessKey);
        if (credentials == null) {
            throw new UserNotFoundException("user not found");
        }

        List<UserPermissions> list = userPermissionsRepository.findByAccessKey(accessKey);

        List<AutherizationResponse> authorities =
                groppingAutherizationByBacket(list);
        UserDto userDto = UserDto.builder()
                .accessKey(credentials.getAccessKey())
                .active(credentials.isActive())
                .authorities(authorities)
                .build();
        return userDto;
    }


    public ResponseEntity<UserResponse> addNewUser(UserRequest request) {
        Credentials credentials = Credentials.builder()
                .accessKey(request.getAccessKey())
                .secretKey(UUID.randomUUID().toString())
                .active(true)
                .build();
        credentials = credentialRepository.save(credentials);
        return  ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.builder()
                .accessKey(credentials.getAccessKey())
                .secretKey(credentials.getSecretKey())
                .build());
    }

    public ResponseEntity<UserResponse> updateUser(UserRequest request) {
        Credentials credentials = credentialRepository.findByAccessKey(request.getAccessKey());
        if (credentials == null) {
            throw new RuntimeException("user not found");
        }
        credentials.setSecretKey(UUID.randomUUID().toString());

        credentials = credentialRepository.save(credentials);

        return  ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.builder()
                .accessKey(credentials.getAccessKey())
                .secretKey(credentials.getSecretKey())
                .build());
    }

    @Transactional
    public ResponseEntity<String> removeUser(String accessKey) {

        Credentials credentials = credentialRepository.findByAccessKey(accessKey);

        if (credentials == null) {
            throw new UserNotFoundException("user not found");
        }


        Optional<Autherization>  autherization = authorizationRepository.findByUserId(credentials.getId());

        if(autherization.isPresent()) {
            userPermissionsRepository.removeByAuthorization(autherization.get());
        }

        authorizationRepository.removeByUser(credentials);

        credentialRepository.delete(credentials);
        return ResponseEntity.status(HttpStatus.CREATED).body("deleted");
    }

    private static List<AutherizationResponse> groppingAutherizationByBacket(List<UserPermissions> list) {
        Map<String, List<String>> grouped = new HashMap<>();

        for (UserPermissions up : list) {

            String pattern = up.getAuthorization().getPattern();
            String authority = up.getPermission().getAuthority();

            grouped.computeIfAbsent(pattern, k -> new ArrayList<>())
                    .add(authority);
        }

        List<AutherizationResponse> authorities = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {

            authorities.add(
                    AutherizationResponse.builder()
                            .pattern(entry.getKey())
                            .authorities(entry.getValue())
                            .build()
            );
        }
        return authorities;
    }



    public ResponseEntity<GenerateHmacResponse> generateHmac(GenerateHmacRequest request) {
        Credentials credentials = credentialRepository.findByAccessKey(request.getAccessKey());
        if (credentials == null) {
            throw new UserNotFoundException("user not found");
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String hmac = Utils.generateHmac(credentials.getSecretKey(), request.getMethod(), Long.parseLong(timestamp));
        return ResponseEntity.status(HttpStatus.OK).body(GenerateHmacResponse.builder().timestamp(timestamp).hmac(hmac).build());
    }
}