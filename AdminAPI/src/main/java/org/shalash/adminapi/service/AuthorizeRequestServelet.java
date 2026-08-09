package org.shalash.adminapi.service;

import lombok.RequiredArgsConstructor;
import org.shalash.adminapi.dto.AutherizationResponse;
import org.shalash.adminapi.dto.request.UserDto;
import org.shalash.adminapi.entity.Autherization;
import org.shalash.adminapi.entity.Credentials;
import org.shalash.adminapi.entity.UserPermissions;
import org.shalash.adminapi.exception.UserNotFoundException;
import org.shalash.adminapi.repo.AuthorizationRepository;
import org.shalash.adminapi.repo.CredentialRepository;
import org.shalash.adminapi.repo.PermissionsRepository;
import org.shalash.adminapi.repo.UserPermissionsRepository;
import org.shalash.adminapi.util.Utils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;


@Service
@RequiredArgsConstructor
public class AuthorizeRequestServelet {

    private final CredentialRepository credentialRepository;
    private final AdminUserService adminUserService;
    private final UserPermissionsRepository userPermissionsRepository;

    private static final long TIMESTAMP_VALIDITY_SECONDS = 5 * 60;

    //public boolean isAuthorized(String uri, String accessKey, String hash) {
    public boolean isAuthorized(
            String method,
            String accessKey,
            long timestamp,
            String hmac
    ) {

        long currentTimestamp = System.currentTimeMillis() / 1000;

        if (Math.abs(currentTimestamp - timestamp) > TIMESTAMP_VALIDITY_SECONDS) {
            return false;
        }

        Credentials credentials = credentialRepository.findByAccessKey(accessKey);
        if (credentials == null) {
            return false;
        }
        if (!credentials.isActive()) {
            return false;
        }

        String generatedHmac =
                Utils.generateHmac(credentials.getSecretKey(), method, timestamp);

        if (!MessageDigest.isEqual(
                generatedHmac.getBytes(StandardCharsets.UTF_8),
                hmac.getBytes(StandardCharsets.UTF_8)
        )) {
            return false;
        }

        UserDto userDto;
        try {
             userDto = adminUserService.getUserDto(accessKey);
        }catch (UserNotFoundException e) {
             return false;
        }

        if (userDto == null || userDto.getAuthorities() == null) {
            return false;
        }

        for (AutherizationResponse authorization : userDto.getAuthorities()) {

            String pattern = authorization.getPattern();

            if ("*".equals(pattern)) {

                if (authorization.getAuthorities().contains("admin")) {
                    return true;
                }
            }

        }
        return false;
    }


}
