package org.school.personalLoad.config;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Environment;
import org.school.personalLoad.model.NamingMesh;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.TarifficationChangesMesh;
import org.school.personalLoad.model.TarifficationPerson;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class HibernateConfig {

    private static SessionFactory sessionFactory;

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            synchronized (HibernateConfig.class) {
                if (sessionFactory == null) {
                    sessionFactory = buildSessionFactory();
                }
            }
        }
        return sessionFactory;
    }

    private static SessionFactory buildSessionFactory() {
        try {
            StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder();

            Map<String, String> settings = new HashMap<>();
            settings.put(Environment.DRIVER, "org.postgresql.Driver");
            settings.put(Environment.URL, env("DB_URL", "jdbc:postgresql://localhost:5432/tariffication_db"));
            settings.put(Environment.USER, env("DB_USERNAME", "tarif_user"));
            settings.put(Environment.PASS, requiredEnv("DB_PASSWORD"));
            settings.put(Environment.DIALECT, "org.hibernate.dialect.PostgreSQLDialect");
            settings.put(Environment.HBM2DDL_AUTO, env("HIBERNATE_DDL_AUTO", "update"));
            settings.put(Environment.SHOW_SQL, env("HIBERNATE_SHOW_SQL", "false"));

            registryBuilder.applySettings(settings);

            StandardServiceRegistry registry = registryBuilder.build();
            MetadataSources sources = new MetadataSources(registry)
                    .addAnnotatedClass(TarifficationPerson.class)
                    .addAnnotatedClass(TarifficationChanges.class)
                    .addAnnotatedClass(NamingMesh.class)
                    .addAnnotatedClass(TarifficationChangesMesh.class);

            Metadata metadata = sources.getMetadataBuilder().build();
            return metadata.getSessionFactoryBuilder().build();

        } catch (Exception e) {
            log.error("Failed to create Hibernate session factory", e);
            throw new RuntimeException("Failed to create Hibernate session factory", e);
        }
    }

    private static String requiredEnv(String key) {
        String value = getPropertyOrEnv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable is required: " + key);
        }
        return value;
    }

    private static String getPropertyOrEnv(String key) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String propValue = System.getProperty(key);
        if (propValue != null && !propValue.isBlank()) {
            return propValue;
        }
        return null;
    }

    private static String env(String key, String defaultValue) {
        String value = getPropertyOrEnv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}
