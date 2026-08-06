package org.shalash.objectstoredb.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class MetadataRepository {
    private final JdbcTemplate jdbcTemplate;

    public MetadataRepository(JdbcTemplate jdbc) {
        this.jdbcTemplate = jdbc;
    }

    public void addMetadata(String bucket, String key, String versionId, long size, String contentType) {
        jdbcTemplate.update("""
        INSERT INTO objects(bucket, object_key, version_id, size, content_type)
        VALUES (?, ?, ?, ?, ?)
    """, bucket, key, versionId, size, contentType);
    }

    public Map<String, Object> getMetadata(String bucket, String key, String versionId) {
        return jdbcTemplate.queryForMap("""
        SELECT * FROM objects
        WHERE bucket=? AND object_key=? AND version_id=?
    """, bucket, key, versionId);
    }
}
