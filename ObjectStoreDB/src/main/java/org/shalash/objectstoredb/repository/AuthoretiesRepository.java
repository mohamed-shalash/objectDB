package org.shalash.objectstoredb.repository;

import org.shalash.objectstoredb.dto.Authorization;
import org.shalash.objectstoredb.dto.Credential;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AuthoretiesRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthoretiesRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    public List<Authorization> getAuthoritiesByUser(String accessKey) {
        return jdbcTemplate.query(
                """
                SELECT
                    c.access_key,
                    a.pattern,
                    p.authority
                FROM authorization_permission ap
                JOIN authorization a
                    ON a.id = ap.authorization_id
                JOIN permissions p
                    ON p.id = ap.permission_id
                JOIN credentials c
                    ON c.id = a.user_id
                WHERE c.access_key = ?
                ORDER BY a.pattern
                """,
                rs -> {
                    Map<String, Authorization> map = new LinkedHashMap<>();

                    while (rs.next()) {
                        String pattern = rs.getString("pattern");

                        Authorization auth = map.computeIfAbsent(pattern, p -> {
                            Authorization a = new Authorization();
                            try {
                                a.setAccessKey(rs.getString("access_key"));
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                            a.setPattern(pattern);
                            return a;
                        });

                        auth.getAuthorities().add(rs.getString("authority"));
                    }

                    return new ArrayList<>(map.values());
                },
                accessKey
        );
    }
}
