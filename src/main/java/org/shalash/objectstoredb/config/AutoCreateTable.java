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

            jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS credentials (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                access_key TEXT NOT NULL UNIQUE,
                secret_key TEXT NOT NULL,
                active INTEGER DEFAULT 1
            );
        """);
            log.info(" Table 'credentials' created successfully");

            jdbcTemplate.execute("""
            INSERT OR IGNORE INTO credentials(id,access_key, secret_key) VALUES (1,'test', 'test');
        """);

            jdbcTemplate.execute("PRAGMA foreign_keys = ON");

            jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS permissions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                authority TEXT NOT NULL UNIQUE
            );
        """);
            log.info(" Table 'permissions' created successfully");

            jdbcTemplate.execute("""
            INSERT OR IGNORE INTO permissions(id,authority) VALUES (1,'read');
        """);

            jdbcTemplate.execute("""
            INSERT OR IGNORE INTO permissions(id, authority) VALUES (2,'write');
        """);

            jdbcTemplate.execute("""
            INSERT OR IGNORE INTO permissions(id, authority) VALUES (3,'delete');
        """);

            jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS authorization (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL REFERENCES credentials(id),
                pattern TEXT NOT NULL
            );
        """);
            log.info(" Table 'authorization' created successfully");

            jdbcTemplate.execute("""
            INSERT OR IGNORE INTO authorization(id,user_id, pattern) VALUES (1,1 ,'*');
        """);


            jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS authorization_permission (
                  authorization_id INTEGER NOT NULL REFERENCES authorization(id),
                  permission_id INTEGER NOT NULL REFERENCES permissions(id),
                  PRIMARY KEY (authorization_id, permission_id)
              );
        """);
            log.info(" Table 'authorization_permission' created successfully");

            jdbcTemplate.execute("INSERT OR IGNORE INTO authorization_permission VALUES (1,1)");
            jdbcTemplate.execute("INSERT OR IGNORE INTO authorization_permission VALUES (1,2)");
            jdbcTemplate.execute("INSERT OR IGNORE INTO authorization_permission VALUES (1,3)");

        } catch (Exception e) {
            log.warn(" Failed to create versions or objects tables: " + e.getMessage());
        }
    }
}
