package org.shalash.adminapi.repo;

import org.shalash.adminapi.entity.Credentials;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialRepository
        extends JpaRepository<Credentials, Long> {

    public Credentials findByAccessKey(String accessKey);

}
