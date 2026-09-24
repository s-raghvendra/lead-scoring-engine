package com.nector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * Nector Foods – Backend Lead Scoring Service.
 * <p>
 * Auto-configuration for the default DataSource is disabled here because we
 * configure <em>two</em> separate DataSources (MySQL + PostgreSQL) manually
 * in {@code config/MysqlDataSourceConfig} and {@code config/PostgresDataSourceConfig}.
 */
import java.util.TimeZone;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class
})
public class BackendServiceApplication {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(BackendServiceApplication.class, args);
    }
}
