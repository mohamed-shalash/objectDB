package org.shalash.adminapi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "authorization_permission")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissions {

    @EmbeddedId
    private UserPermissionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "authorization_id",
            insertable = false,
            updatable = false
    )
    private Autherization authorization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "permission_id",
            insertable = false,
            updatable = false
    )
    private Permissions permission;
}
