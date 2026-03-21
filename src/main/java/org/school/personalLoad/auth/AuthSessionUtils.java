package org.school.personalLoad.auth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class AuthSessionUtils {
    private AuthSessionUtils() {
    }

    public static SessionUser requiredUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new AuthExceptions.UnauthorizedException("Требуется вход в систему");
        }
        Object value = session.getAttribute(SessionUser.SESSION_KEY);
        if (!(value instanceof SessionUser user)) {
            throw new AuthExceptions.UnauthorizedException("Требуется вход в систему");
        }
        return user;
    }
}
