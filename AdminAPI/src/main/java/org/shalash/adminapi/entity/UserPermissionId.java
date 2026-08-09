package org.shalash.adminapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPermissionId {


    @Column(name = "authorization_id")
    private Long authorizationId;

    @Column(name = "permission_id")
    private Long permissionId;
}