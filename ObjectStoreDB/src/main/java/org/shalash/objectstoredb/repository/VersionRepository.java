package org.shalash.objectstoredb.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class VersionRepository  {

    private final JdbcTemplate jdbc;

    public VersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }


    public void addVersion(String bucket, String key, String versionId) {
        jdbc.update(
                "INSERT INTO versions(bucket, object_key, version_id, created_at) VALUES (?, ?, ?, ?)",
                bucket, key, versionId, System.currentTimeMillis()
        );
    }

    public String getLatest(String bucket, String key) {
        try {
            return jdbc.queryForObject(
                    "SELECT version_id FROM versions " +
                            "WHERE bucket=? AND object_key=? AND deleted=0 " +
                            "ORDER BY created_at DESC LIMIT 1",
                    String.class,
                    bucket, key
            );
        } catch (Exception e) {
            System.out.println("No latest version found for " + bucket + "/" + key);
            return null;
        }
    }


    public List<String> listVersions(String bucket, String key) {
        return jdbc.query(
                "SELECT version_id FROM versions " +
                        "WHERE bucket=? AND object_key=? AND deleted=0 " +
                        "ORDER BY created_at DESC",
                (rs, rowNum) -> rs.getString("version_id"),
                bucket, key
        );
    }
    public void deleteVersion(String versionId) {
        jdbc.update(
                "UPDATE versions SET deleted=1 WHERE version_id=?",
                versionId
        );
    }
}
