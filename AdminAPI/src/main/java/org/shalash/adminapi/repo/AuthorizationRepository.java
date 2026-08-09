package org.shalash.adminapi.repo;

import org.shalash.adminapi.entity.Autherization;
import org.shalash.adminapi.entity.Credentials;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorizationRepository extends JpaRepository<Autherization, Long> {

    Optional<Autherization> findByUserIdAndPattern(
            Long userId,
            String pattern
    );

    void removeByUser(Credentials user);

    Optional<Autherization> findByUserId(Long userId);

    Optional<Autherization> findByUser_IdAndPattern(
            Long userId,
            String pattern
    );
}
