package org.shalash.objectstoredb.repository;

import org.shalash.objectstoredb.dto.Credential;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CredentialRepository {

    private final JdbcTemplate jdbcTemplate;

    public CredentialRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String getSecretKey(String accessKey) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT secret_key FROM credentials WHERE access_key=?",
                    String.class,
                    accessKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    public Credential findByAccessKey(String accessKey) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT access_key, secret_key FROM credentials WHERE access_key=?",
                    (rs, rowNum) -> {
                        Credential c = new Credential();
                        c.setAccessKey(rs.getString("access_key"));
                        c.setSecretKey(rs.getString("secret_key"));
                        return c;
                    },
                    accessKey
            );
        } catch (Exception e) {
            return null;
        }
    }
}
