package com.nector.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Secondary DataSource configuration for PostgreSQL.
 * Manages the {@code ScoreAudit} entity.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages               = "com.nector.repository.postgres",
        entityManagerFactoryRef    = "postgresEntityManagerFactory",
        transactionManagerRef      = "postgresTransactionManager"
)
public class PostgresDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.postgres")
    public DataSourceProperties postgresDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.postgres.hikari")
    public HikariDataSource postgresDataSource(
            @Qualifier("postgresDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder()
                    .type(HikariDataSource.class)
                    .build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory(
            @Qualifier("postgresDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.nector.model.postgres");
        em.setPersistenceUnitName("postgres");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setGenerateDdl(true);
        em.setJpaVendorAdapter(adapter);

        Map<String, Object> props2 = new HashMap<>();
        props2.put("hibernate.hbm2ddl.auto",      "update");
        props2.put("hibernate.dialect",            "org.hibernate.dialect.PostgreSQLDialect");
        props2.put("hibernate.show_sql",          "true");
        props2.put("hibernate.format_sql",        "true");
        props2.put("hibernate.jdbc.time_zone",    "UTC");
        em.setJpaPropertyMap(props2);

        return em;
    }

    @Bean
    public PlatformTransactionManager postgresTransactionManager(
            @Qualifier("postgresEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
        JpaTransactionManager tm = new JpaTransactionManager();
        tm.setEntityManagerFactory(emf.getObject());
        return tm;
    }
}
