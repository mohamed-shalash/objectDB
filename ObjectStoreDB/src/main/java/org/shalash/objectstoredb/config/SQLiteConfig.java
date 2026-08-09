package org.shalash.objectstoredb.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class SQLiteConfig {

    @Bean
    public DataSource dataSource() {
        return new DriverManagerDataSource(
                "jdbc:sqlite:../data.db"
        );
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource ds) {
        return new JdbcTemplate(ds);
    }

    // مثلاً في FileOperationController أو Application class

}