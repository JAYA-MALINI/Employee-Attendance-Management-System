package com.attendance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.attendance.model.Employee;

@Configuration
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        
        // Let pre-flight CORS requests pass
        if (request.getMethod().equals("OPTIONS")) return true;

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("401 Unauthorized: Session Expired or Not Logged In");
            return false;
        }

        Employee user = (Employee) session.getAttribute("user");
        
        // Implement simple RBAC for Admin routes
        if (uri.startsWith("/api/admin/")) {
            if (!"ADMIN".equals(user.getRole())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("403 Forbidden: Admin Access Required");
                return false;
            }
        }
        
        return true;
    }
}
