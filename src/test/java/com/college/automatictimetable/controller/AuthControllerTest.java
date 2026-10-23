package com.college.automatictimetable.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:timetable-auth-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthControllerTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void testValidAdminLogin() {
        ResponseEntity<Map> res = http.postForEntity("/api/auth/login",
                Map.of("role", "admin", "username", "admin", "password", "admin123"),
                Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("admin", res.getBody().get("role"));
        assertEquals("Administrator", res.getBody().get("name"));
        assertEquals("/index.html", res.getBody().get("redirect"));

        List<String> cookies = res.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies);
        assertFalse(cookies.isEmpty());
    }

    @Test
    void testValidStaffLogin() {
        ResponseEntity<Map> res = http.postForEntity("/api/auth/login",
                Map.of("role", "staff", "username", "staff", "password", "staff123"),
                Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("staff", res.getBody().get("role"));
        assertEquals("Staff User", res.getBody().get("name"));
        assertEquals("/user.html", res.getBody().get("redirect"));
    }

    @Test
    void testIncorrectPasswordForAdmin() {
        ResponseEntity<Map> res = http.postForEntity("/api/auth/login",
                Map.of("role", "admin", "username", "admin", "password", "wrongpass"),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertTrue(res.getBody().get("message").toString().contains("Invalid Admin"));
    }

    @Test
    void testIncorrectPasswordForStaff() {
        ResponseEntity<Map> res = http.postForEntity("/api/auth/login",
                Map.of("role", "staff", "username", "staff", "password", "wrongpass"),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertTrue(res.getBody().get("message").toString().contains("Invalid Staff"));
    }

    @Test
    void testCrossRoleAuthenticationBlocked() {
        // Staff credentials on Admin form -> fails
        ResponseEntity<Map> adminWithStaffCreds = http.postForEntity("/api/auth/login",
                Map.of("role", "admin", "username", "staff", "password", "staff123"),
                Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, adminWithStaffCreds.getStatusCode());

        // Admin credentials on Staff form -> fails
        ResponseEntity<Map> staffWithAdminCreds = http.postForEntity("/api/auth/login",
                Map.of("role", "staff", "username", "admin", "password", "admin123"),
                Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, staffWithAdminCreds.getStatusCode());
    }

    @Test
    void testMissingCredentialsValidation() {
        // Missing username
        ResponseEntity<Map> noUser = http.postForEntity("/api/auth/login",
                Map.of("role", "admin", "password", "admin123"),
                Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, noUser.getStatusCode());

        // Missing password
        ResponseEntity<Map> noPass = http.postForEntity("/api/auth/login",
                Map.of("role", "admin", "username", "admin"),
                Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, noPass.getStatusCode());

        // Missing role
        ResponseEntity<Map> noRole = http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "admin123"),
                Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, noRole.getStatusCode());
    }

    @Test
    void testStaffCannotAccessAdminApisOrGenerateTimetable() {
        // Login as staff
        ResponseEntity<Map> loginRes = http.postForEntity("/api/auth/login",
                Map.of("role", "staff", "username", "staff", "password", "staff123"),
                Map.class);
        String cookie = loginRes.getHeaders().get(HttpHeaders.SET_COOKIE).getFirst().split(";")[0];
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);

        // Staff can read timetable
        ResponseEntity<String> ttRes = http.exchange("/api/timetable", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, ttRes.getStatusCode());

        // Staff CANNOT generate timetable (403 Forbidden)
        ResponseEntity<Map> genRes = http.exchange("/api/timetable/generate", HttpMethod.POST,
                new HttpEntity<>(Map.of("classId", 1), headers), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, genRes.getStatusCode());

        // Staff CANNOT create department (403 Forbidden)
        ResponseEntity<Map> createDept = http.exchange("/api/departments", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Test"), headers), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, createDept.getStatusCode());
    }

    @Test
    void testLogoutClearsSession() {
        // Login as admin
        ResponseEntity<Map> loginRes = http.postForEntity("/api/auth/login",
                Map.of("role", "admin", "username", "admin", "password", "admin123"),
                Map.class);
        String cookie = loginRes.getHeaders().get(HttpHeaders.SET_COOKIE).getFirst().split(";")[0];
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);

        // /api/auth/me reports authenticated
        ResponseEntity<Map> meBefore = http.exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, meBefore.getStatusCode());
        assertEquals("admin", meBefore.getBody().get("role"));

        // Logout
        ResponseEntity<Void> logoutRes = http.exchange("/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(headers), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, logoutRes.getStatusCode());

        // /api/auth/me reports 401 after logout
        ResponseEntity<Map> meAfter = http.exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, meAfter.getStatusCode());
    }
}
