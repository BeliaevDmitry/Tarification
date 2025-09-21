package org.school.personalLoad.config;

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

public class HibernateConfig {

    private static SessionFactory sessionFactory;

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            try {
                StandardServiceRegistryBuilder registryBuilder =
                        new StandardServiceRegistryBuilder();

                Map<String, String> settings = new HashMap<>();
                settings.put(Environment.DRIVER, "org.postgresql.Driver");
                settings.put(Environment.URL, "jdbc:postgresql://localhost:5432/tariffication_db");
                settings.put(Environment.USER, "tarif_user");
                settings.put(Environment.PASS, "tarif_password");
                settings.put(Environment.DIALECT, "org.hibernate.dialect.PostgreSQLDialect");
                settings.put(Environment.HBM2DDL_AUTO, "update");
                settings.put(Environment.SHOW_SQL, "false");


                registryBuilder.applySettings(settings);

                StandardServiceRegistry registry = registryBuilder.build();
                MetadataSources sources = new MetadataSources(registry)
                        .addAnnotatedClass(TarifficationPerson.class)
                        .addAnnotatedClass(TarifficationChanges.class)
                        .addAnnotatedClass(NamingMesh.class) // Добавьте эту строку
                        .addAnnotatedClass(TarifficationChangesMesh.class);

                Metadata metadata = sources.getMetadataBuilder().build();
                sessionFactory = metadata.getSessionFactoryBuilder().build();

            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Failed to create Hibernate session factory", e);
            }
        }
        return sessionFactory;
    }

    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}