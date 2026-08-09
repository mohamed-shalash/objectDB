package org.shalash.adminapi.repo;

import org.shalash.adminapi.entity.Permissions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionsRepository extends JpaRepository<Permissions, Long> {
    Permissions findByAuthority(String authority);
}
