package com.nector.config;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
 * Primary DataSource configuration for MySQL.
 * Manages the {@code Lead} and {@code ScoringConfig} entities.
 * Flyway migrations are applied programmatically on startup.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages               = "com.nector.repository.mysql",
        entityManagerFactoryRef    = "mysqlEntityManagerFactory",
        transactionManagerRef      = "mysqlTransactionManager"
)
public class MysqlDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.mysql")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.mysql.hikari")
    public HikariDataSource mysqlDataSource(
            @Qualifier("mysqlDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder()
                    .type(HikariDataSource.class)
                    .build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean mysqlEntityManagerFactory(
            @Qualifier("mysqlDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.nector.model.mysql");
        em.setPersistenceUnitName("mysql");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setGenerateDdl(false); // DDL handled by Flyway
        em.setJpaVendorAdapter(adapter);

        Map<String, Object> props2 = new HashMap<>();
        props2.put("hibernate.hbm2ddl.auto",              "update");
        // No hibernate.dialect: Hibernate 6 auto-detects MySQL dialect (avoids HHH90000025)
        props2.put("hibernate.show_sql",                  "true");
        props2.put("hibernate.format_sql",                "true");
        props2.put("hibernate.jdbc.time_zone",            "UTC");
        em.setJpaPropertyMap(props2);

        return em;
    }

    @Bean
    @Primary
    public PlatformTransactionManager mysqlTransactionManager(
            @Qualifier("mysqlEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
        JpaTransactionManager tm = new JpaTransactionManager();
        tm.setEntityManagerFactory(emf.getObject());
        return tm;
    }

    /**
     * Runs Flyway migrations against MySQL at startup before the EMF validates the schema.
     * Declared as a bean so Spring Boot lifecycle ordering is respected.
     */
    @Bean(initMethod = "migrate")
    public Flyway mysqlFlyway(@Qualifier("mysqlDataSource") DataSource dataSource) {
        return Flyway.configure()
                     .dataSource(dataSource)
                     .locations("classpath:db/migration")
                     .baselineOnMigrate(true)
                     .load();
    }
}
