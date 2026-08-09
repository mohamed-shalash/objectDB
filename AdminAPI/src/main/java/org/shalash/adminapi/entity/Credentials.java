package org.shalash.adminapi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "credentials")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Credentials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true,name = "access_key")
    private String accessKey;

    @Column(nullable = false, unique = true,name = "secret_key")
    private String secretKey;

    @Column(nullable = false)
    private boolean active = true;
}
