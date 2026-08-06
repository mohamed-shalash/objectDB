package org.shalash.objectstoredb.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Slf4j
public class AutoCreateTable {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @PostConstruct
    public void createVersionsTable() {
        try {
//            jdbcTemplate.execute("""
//            DROP TABLE IF EXISTS versions;
//            );
//        """);

            jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS versions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                bucket TEXT NOT NULL,
                object_key TEXT NOT NULL,
                version_id TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                deleted INTEGER DEFAULT 0,
                UNIQUE(bucket, object_key, version_id)
            );
        """);
            log.info(" Table 'versions' created successfully");

            jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS objects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                bucket TEXT NOT NULL,
                object_key TEXT NOT NULL,
                version_id TEXT NOT NULL,
                size INTEGER,
                content_type TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """);
            log.info(" Table 'objects' created successfully");

        } catch (Exception e) {
            log.warn(" Failed to create versions or objects tables: " + e.getMessage());
        }
    }
}
