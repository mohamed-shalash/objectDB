package org.shalash.objectstoredb.service;

import jakarta.servlet.http.HttpServletRequest;
import org.shalash.objectstoredb.dto.Credential;
import org.shalash.objectstoredb.repository.CredentialRepository;
import org.shalash.objectstoredb.utils.SignatureUtils;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final CredentialRepository repository;
    private final SignatureUtils signatureUtils;

    public AuthenticationService(
            CredentialRepository repository,
            SignatureUtils signatureUtils) {

        this.repository = repository;
        this.signatureUtils = signatureUtils;
    }

    public boolean authenticate(
            HttpServletRequest request,
            String accessKey,
            String signature,
            String signedHeaders,
            String dateStamp,
            String region) {

        Credential credential =
                repository.findByAccessKey(accessKey);

        if (credential == null) {
            return false;
        }

        return signatureUtils.verify(
                request,
                credential.getSecretKey(),
                dateStamp,
                region,
                signedHeaders,
                signature);
    }

}
