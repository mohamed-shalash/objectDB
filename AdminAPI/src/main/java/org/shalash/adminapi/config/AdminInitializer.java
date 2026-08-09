package org.shalash.adminapi.config;


import lombok.RequiredArgsConstructor;
import org.shalash.adminapi.entity.*;
import org.shalash.adminapi.repo.AuthorizationRepository;
import org.shalash.adminapi.repo.CredentialRepository;
import org.shalash.adminapi.repo.PermissionsRepository;
import org.shalash.adminapi.repo.UserPermissionsRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final CredentialRepository credentialRepository;
    private final PermissionsRepository permissionsRepository;
    private final AuthorizationRepository authorizationRepository;
    private final UserPermissionsRepository userPermissionsRepository;

    @Override
    public void run(String... args) {

        //  Create permissions if they don't exist
        Permissions read = createPermissionIfNotExists("read");
        Permissions write = createPermissionIfNotExists("write");
        Permissions delete = createPermissionIfNotExists("delete");
        Permissions admin = createPermissionIfNotExists("admin");

        // Check admin
        Credentials credentials =
                credentialRepository.findByAccessKey("admin");

        if (credentials == null) {

            credentials = Credentials.builder()
                    .accessKey("admin")
                    .secretKey("admin-secret")
                    .active(true)
                    .build();

            credentials = credentialRepository.save(credentials);

            System.out.println("Admin user created");
        }


        //  Check authorization
        Autherization authorization =
                authorizationRepository
                        .findByUser_IdAndPattern(
                                credentials.getId(),
                                "*"
                        )
                        .orElse(null);

        if (authorization == null) {

            authorization = new Autherization();
            authorization.setUser(credentials);
            authorization.setPattern("*");

            authorization = authorizationRepository.save(authorization);
        }

        // Ensure admin has all permissions
        addPermissionIfMissing(authorization, read);
        addPermissionIfMissing(authorization, write);
        addPermissionIfMissing(authorization, delete);
        addPermissionIfMissing(authorization, admin);

    }

    private Permissions createPermissionIfNotExists(String authority) {
        Permissions permissions=permissionsRepository
                .findByAuthority(authority);
        if(permissions==null){
            Permissions permission = new Permissions();
            permission.setAuthority(authority);
            return permissionsRepository.save(permission);
        }
        return permissions;
    }

    private void addPermissionIfMissing(
            Autherization authorization,
            Permissions permission) {

        boolean exists =
                userPermissionsRepository
                        .existsByIdAuthorizationIdAndIdPermissionId(
                                authorization.getId(),
                                permission.getId()
                        );

        if (!exists) {

            UserPermissionId id =
                    new UserPermissionId(
                            authorization.getId(),
                            permission.getId()
                    );

            UserPermissions userPermissions =
                    new UserPermissions();

            userPermissions.setId(id);
            userPermissions.setAuthorization(authorization);
            userPermissions.setPermission(permission);

            userPermissionsRepository.save(userPermissions);
        }
    }
}
