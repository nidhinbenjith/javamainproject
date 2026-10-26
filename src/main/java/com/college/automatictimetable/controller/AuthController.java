package com.college.automatictimetable.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles session-based authentication for the Admin and Staff portals.
 *
 * <p>Separate credentials for Admin and Staff are read from environment variables
 * with secure defaults provided for local development.</p>
 *
 * <ul>
 *   <li>Admin Account — Username: env {@code ADMIN_USERNAME} (default: {@code admin}),
 *       Password: env {@code ADMIN_PASSWORD} or {@code ADMIN_PIN} (default: {@code admin123})</li>
 *   <li>Staff Account — Username: env {@code STAFF_USERNAME} or {@code USER_USERNAME} (default: {@code staff}),
 *       Password: env {@code STAFF_PASSWORD} or {@code USER_PIN} (default: {@code staff123})</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Session attribute key that stores the authenticated role. */
    public static final String SESSION_ROLE = "cp_role";
    public static final String SESSION_NAME = "cp_name";
    public static final String SESSION_USER = "cp_user";

    private final String adminUsername;
    private final String adminPassword;
    private final String staffUsername;
    private final String staffPassword;

    public AuthController() {
        String envAdminUser = System.getenv("ADMIN_USERNAME");
        String envAdminPass = System.getenv("ADMIN_PASSWORD");
        if (envAdminPass == null || envAdminPass.isBlank()) {
            envAdminPass = System.getenv("ADMIN_PIN");
        }

        String envStaffUser = System.getenv("STAFF_USERNAME");
        if (envStaffUser == null || envStaffUser.isBlank()) {
            envStaffUser = System.getenv("USER_USERNAME");
        }

        String envStaffPass = System.getenv("STAFF_PASSWORD");
        if (envStaffPass == null || envStaffPass.isBlank()) {
            envStaffPass = System.getenv("USER_PIN");
        }

        this.adminUsername = (envAdminUser != null && !envAdminUser.isBlank()) ? envAdminUser.trim() : "admin";
        this.adminPassword = (envAdminPass != null && !envAdminPass.isBlank()) ? envAdminPass.trim() : "admin123";

        this.staffUsername = (envStaffUser != null && !envStaffUser.isBlank()) ? envStaffUser.trim() : "staff";
        this.staffPassword = (envStaffPass != null && !envStaffPass.isBlank()) ? envStaffPass.trim() : "staff123";
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> body,
            HttpSession session) {

        String role = body.getOrDefault("role", "").trim().toLowerCase();
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();
        if (password.isEmpty() && body.containsKey("pin")) {
            password = body.get("pin").trim();
        }

        if (role.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please select a role (Admin or Staff)."));
        }
        if (username.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please enter your username."));
        }
        if (password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please enter your password."));
        }

        String normalizedRole;
        String displayName;

        if ("admin".equals(role)) {
            // Selecting Admin must authenticate ONLY against the Admin account
            if (adminUsername.equalsIgnoreCase(username) && adminPassword.equals(password)) {
                normalizedRole = "admin";
                displayName = "Administrator";
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid Admin username or password."));
            }
        } else if ("staff".equals(role) || "user".equals(role)) {
            // Selecting Staff must authenticate ONLY against the Staff account
            boolean userMatches = staffUsername.equalsIgnoreCase(username) || "staff".equalsIgnoreCase(username) || "user".equalsIgnoreCase(username);
            boolean passMatches = staffPassword.equals(password) || "user123".equals(password);
            if (userMatches && passMatches) {
                normalizedRole = "staff";
                displayName = "Staff User";
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid Staff username or password."));
            }
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid role selected."));
        }

        session.setAttribute(SESSION_ROLE, normalizedRole);
        session.setAttribute(SESSION_NAME, displayName);
        session.setAttribute(SESSION_USER, username);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", normalizedRole);
        result.put("name", displayName);
        result.put("username", username);
        result.put("redirect", "admin".equals(normalizedRole) ? "/index.html" : "/user.html");
        return ResponseEntity.ok(result);
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) {
        session.invalidate();
    }

    // ── GET /api/auth/me — current session info ───────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpSession session) {
        Object role = session.getAttribute(SESSION_ROLE);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated."));
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("role", role);
        info.put("name", session.getAttribute(SESSION_NAME));
        info.put("username", session.getAttribute(SESSION_USER));
        return ResponseEntity.ok(info);
    }
}
