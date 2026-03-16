package org.school.personalLoad.config;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import java.sql.SQLException;

public class DatabaseAuthFailureAnalyzer extends AbstractFailureAnalyzer<BeanCreationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, BeanCreationException cause) {
        SQLException sqlException = findSqlException(cause);
        if (sqlException == null || !"28P01".equals(sqlException.getSQLState())) {
            return null;
        }

        String description = "Не удалось подключиться к PostgreSQL: ошибка аутентификации (SQLState 28P01).\n"
                + "Пользователь/пароль в приложении не совпадают с учетными данными в самой БД.";

        String action = "Проверьте и синхронизируйте значения в .env:\n"
                + "- POSTGRES_USER = DB_USERNAME\n"
                + "- POSTGRES_PASSWORD = DB_PASSWORD\n"
                + "Если база уже была инициализирована с другим паролем, выполните одно из действий:\n"
                + "1) пересоздайте volume: docker compose down -v && docker compose up -d --build\n"
                + "2) или смените пароль пользователя в текущей БД: ALTER USER <user> WITH PASSWORD '<new_password>';";

        return new FailureAnalysis(description, action, cause);
    }

    private SQLException findSqlException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException) {
                return (SQLException) current;
            }
            current = current.getCause();
        }
        return null;
    }
}
