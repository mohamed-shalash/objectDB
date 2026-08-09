package org.shalash.adminapi.repo;


import org.shalash.adminapi.entity.Autherization;
import org.shalash.adminapi.entity.UserPermissionId;
import org.shalash.adminapi.entity.UserPermissions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserPermissionsRepository
        extends JpaRepository<UserPermissions, UserPermissionId> {

    @Query("""
    SELECT up
    FROM UserPermissions up
    JOIN FETCH up.authorization a
    JOIN FETCH up.permission p
    WHERE a.user.accessKey = :accessKey
""")
    List<UserPermissions> findByAccessKey(
            @Param("accessKey") String accessKey);

    void removeByAuthorization(Autherization autherization);


    boolean existsByIdAuthorizationIdAndIdPermissionId(
            Long authorizationId,
            Long permissionId
    );
}