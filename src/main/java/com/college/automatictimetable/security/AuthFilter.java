package com.college.automatictimetable.security;

import com.college.automatictimetable.controller.AuthController;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Enforces role-based access control and session verification across pages and APIs.
 *
 * <p>Rules enforced:
 * <ul>
 *   <li>Static assets (CSS, JS, fonts, images) and /login.html are public.</li>
 *   <li>/api/auth/** endpoints (login, logout, me) are public.</li>
 *   <li>/index.html (Admin dashboard) requires an authenticated 'admin' role. Unauthenticated requests
 *       are redirected to /login.html; Staff accounts are redirected to /user.html.</li>
 *   <li>/user.html (Staff dashboard) requires an authenticated role ('admin' or 'staff'). Unauthenticated
 *       requests are redirected to /login.html.</li>
 *   <li>/api/** endpoints require authentication (401 Unauthorized for unauthenticated requests).</li>
 *   <li>Staff accounts are strictly restricted to read-only (GET) operations on timetable and catalog
 *       resources, and are denied (403 Forbidden) from accessing modification APIs (POST, PUT, DELETE)
 *       or generating timetables.</li>
 * </ul>
 * </p>
 */
@Component
@Order(1)
public class AuthFilter implements Filter {

    @Value("${app.security.enabled:true}")
    private boolean securityEnabled;

    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".woff", ".woff2", ".ttf", ".map"
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        if (!securityEnabled) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String uri = request.getRequestURI();
        String method = request.getMethod().toUpperCase();

        // Allow static resource files
        for (String ext : STATIC_EXTENSIONS) {
            if (uri.endsWith(ext)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // Allow login page, auth endpoints, and error handling
        if (uri.equals("/login.html") || uri.startsWith("/api/auth/") || uri.equals("/error")) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        String role = (session != null) ? (String) session.getAttribute(AuthController.SESSION_ROLE) : null;

        // ── Protect HTML Pages ────────────────────────────────────────────────
        if (uri.equals("/") || uri.equals("/index.html")) {
            if (role == null) {
                response.sendRedirect("/login.html");
                return;
            }
            if ("staff".equalsIgnoreCase(role) || "user".equalsIgnoreCase(role)) {
                // Staff cannot access Admin portal
                response.sendRedirect("/user.html");
                return;
            }
            if ("admin".equalsIgnoreCase(role)) {
                chain.doFilter(request, response);
                return;
            }
            response.sendRedirect("/login.html");
            return;
        }

        if (uri.equals("/user.html")) {
            if (role == null) {
                response.sendRedirect("/login.html");
                return;
            }
            // Both admin and staff can access user/staff portal
            chain.doFilter(request, response);
            return;
        }

        // ── Protect API Endpoints (/api/**) ───────────────────────────────────
        if (uri.startsWith("/api/")) {
            if (role == null) {
                sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required. Please sign in.");
                return;
            }

            if ("staff".equalsIgnoreCase(role) || "user".equalsIgnoreCase(role)) {
                // Staff accounts cannot perform write actions or generate timetables
                if (!"GET".equals(method)) {
                    sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied. Admin privileges required.");
                    return;
                }
                if (uri.contains("/timetable/generate")) {
                    sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied. Timetable generation is restricted to Admin.");
                    return;
                }
                // Allow read-only GET requests for staff
                chain.doFilter(request, response);
                return;
            }

            if ("admin".equalsIgnoreCase(role)) {
                chain.doFilter(request, response);
                return;
            }

            sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied. Invalid session role.");
            return;
        }

        chain.doFilter(request, response);
    }

    private void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
