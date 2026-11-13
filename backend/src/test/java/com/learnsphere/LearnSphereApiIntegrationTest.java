package com.learnsphere;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin.email=qa-admin@example.com", "app.admin.password=correct-horse-battery"})
@ActiveProfiles("test")
class LearnSphereApiIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private String csrf;
    private String csrfCookie;
    private String authToken;
    @LocalServerPort private int port;

    @BeforeEach void loadCsrf() throws Exception {
        HttpResponse<String> response = get("/api/auth/csrf", null);
        csrf = (String) object(response).get("token");
        csrfCookie = response.headers().allValues("set-cookie").stream().filter(value -> value.startsWith("XSRF-TOKEN="))
            .map(value -> value.substring("XSRF-TOKEN=".length(), value.indexOf(';'))).findFirst().orElse(null);
        authToken = null;
    }

    @Test
    void registrationLoginAndCourseProgressWorkEndToEnd() throws Exception {
        String email = "learner-" + UUID.randomUUID() + "@example.com";
        register(email);
        String accessToken = authToken;

        HttpResponse<String> meResponse = get("/api/auth/me", accessToken);
        assertThat(meResponse.statusCode()).isEqualTo(200);
        Map<String, Object> me = object(meResponse);
        assertThat(me).containsEntry("email", email);

        HttpResponse<String> loginResponse = post("/api/auth/login", Map.of("email", email, "password", "correct-horse-battery"), null);
        Map<String, Object> login = object(loginResponse);
        assertThat(login.get("user")).isNotNull();
        accessToken = cookieToken(loginResponse);
        assertThat(post("/api/auth/forgot-password", Map.of("email", email), accessToken).statusCode()).isEqualTo(HttpStatus.ACCEPTED.value());
        assertThat(post("/api/auth/reset-password", Map.of("token", "invalid", "password", "correct-horse-battery"), accessToken).statusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(post("/api/auth/verify-email", Map.of("token", "invalid"), accessToken).statusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(post("/api/payments/courses/1/order", null, accessToken).statusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());

        Map<String, Object> catalogPage = object(get("/api/courses", accessToken));
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) catalogPage.get("content");
        assertThat(catalog).hasSizeGreaterThanOrEqualTo(5);
        Map<String, Object> firstPage = object(get("/api/courses?page=0&size=2&sort=priceMinor&direction=desc", accessToken));
        assertThat((List<?>) firstPage.get("content")).hasSize(2);
        assertThat(number(firstPage.get("totalElements"))).isEqualTo(number(catalogPage.get("totalElements")));
        assertThat(number(firstPage.get("totalPages"))).isEqualTo((number(catalogPage.get("totalElements")) + 1) / 2);
        assertThat(((List<?>) object(get("/api/courses?search=java", accessToken)).get("content"))).hasSize(1);
        assertThat(((List<?>) object(get("/api/courses?category=Programming", accessToken)).get("content"))).hasSizeGreaterThanOrEqualTo(2);
        Map<String, Object> javaCourse = catalog.stream()
            .filter(course -> "Java Foundations".equals(course.get("name"))).findFirst().orElseThrow();
        long courseId = number(javaCourse.get("id"));
        assertThat(javaCourse).containsEntry("enrolled", false).containsEntry("progress", 0);
        assertThat(get("/api/courses/" + courseId + "/lessons", accessToken).statusCode())
            .isEqualTo(HttpStatus.FORBIDDEN.value());

        Map<String, Object> enrolled = object(post("/api/courses/" + courseId + "/enroll", null, accessToken));
        assertThat(enrolled).containsEntry("enrolled", true).containsEntry("progress", 0);

        List<Map<String, Object>> lessons = objects(get("/api/courses/" + courseId + "/lessons", accessToken));
        assertThat(lessons).hasSize(3);
        for (Map<String, Object> lesson : lessons) {
            Map<String, Object> completed = object(post("/api/courses/" + courseId + "/lessons/" + number(lesson.get("id")) + "/complete", null, accessToken));
            assertThat(completed).containsEntry("completed", true);
        }
        Map<String, Object> finished = object(post("/api/courses/" + courseId + "/lessons/" + number(lessons.get(0).get("id")) + "/complete", null, accessToken));
        assertThat(finished).containsEntry("courseProgress", 100);

        Map<String, Object> refreshed = ((List<Map<String, Object>>) object(get("/api/courses", accessToken)).get("content")).stream()
            .filter(course -> courseId == number(course.get("id"))).findFirst().orElseThrow();
        assertThat(refreshed).containsEntry("progress", 100).containsEntry("enrolled", true);
        assertThat(get("/api/admin/courses", accessToken).statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        HttpResponse<String> logout = post("/api/auth/logout", null, accessToken);
        assertThat(logout.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(get("/api/auth/me", accessToken).statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void duplicateRegistrationAndInvalidLoginReturnUsefulStatuses() throws Exception {
        String email = "learner-" + UUID.randomUUID() + "@example.com";
        register(email);
        HttpResponse<String> duplicate = post("/api/auth/register", Map.of(
            "fullName", "Test Learner", "email", email.toUpperCase(), "password", "correct-horse-battery"), null);
        assertThat(duplicate.statusCode()).isEqualTo(HttpStatus.CONFLICT.value());

        HttpResponse<String> invalid = post("/api/auth/login", Map.of(
            "email", email, "password", "incorrect-password"), null);
        assertThat(invalid.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());

        HttpResponse<String> oversizedUtf8Password = post("/api/auth/register", Map.of(
            "fullName", "Test Learner", "email", "long-password-" + UUID.randomUUID() + "@example.com",
            "password", "é".repeat(40)), null);
        assertThat(oversizedUtf8Password.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());

        HttpResponse<String> invalidEmail = post("/api/auth/register", Map.of(
            "fullName", "Test Learner", "email", "not-an-email", "password", "correct-horse-battery"), null);
        assertThat(invalidEmail.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void protectedCourseRoutesRejectMissingBearerToken() throws Exception {
        HttpResponse<String> response = get("/api/courses", null);
        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(get("/actuator/health", null).statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void adminCanManageCoursesLessonsUsersAndEnrollments() throws Exception {
        String email = "qa-admin@example.com";
        HttpResponse<String> login = post("/api/auth/login", Map.of("email", email, "password", "correct-horse-battery"), null);
        String adminToken = cookieToken(login);
        assertThat(login.statusCode()).isEqualTo(HttpStatus.OK.value());
        Map<String, Object> input = Map.of("name", "QA Course " + UUID.randomUUID(), "category", "Programming", "level", "Beginner",
            "duration", "2 weeks", "description", "Course created in integration coverage.", "icon", "QA", "color", "violet",
            "priceMinor", 0, "currency", "INR", "thumbnailUrl", "");
        HttpResponse<String> created = post("/api/admin/courses", input, adminToken);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(HttpStatus.OK.value());
        long courseId = number(object(created).get("id"));
        assertThat(get("/api/admin/users", adminToken).statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(get("/api/admin/enrollments", adminToken).statusCode()).isEqualTo(HttpStatus.OK.value());
        HttpResponse<String> lesson = post("/api/admin/courses/" + courseId + "/lessons", Map.of("title", "First lesson",
            "description", "A short lesson", "content", "Lesson content", "sortOrder", 1, "videoUrl", "https://video.example.com/lesson"), adminToken);
        assertThat(lesson.statusCode()).isEqualTo(HttpStatus.OK.value());
        long lessonId = number(object(lesson).get("id"));
        assertThat(get("/api/admin/courses/" + courseId + "/lessons", adminToken).statusCode()).isEqualTo(HttpStatus.OK.value());
        HttpResponse<String> lessonUpdate = send("PUT", "/api/admin/courses/" + courseId + "/lessons/" + lessonId,
            Map.of("title", "Updated lesson", "description", "Updated summary", "content", "Updated content", "sortOrder", 2, "videoUrl", ""), adminToken);
        assertThat(lessonUpdate.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(send("DELETE", "/api/admin/courses/" + courseId + "/lessons/" + lessonId, null, adminToken).statusCode())
            .isEqualTo(HttpStatus.NO_CONTENT.value());
        assertThat(send("DELETE", "/api/admin/courses/" + courseId, null, adminToken).statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
        assertThat(post("/api/admin/courses/" + courseId + "/publish", null, adminToken).statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    private Map<String, Object> register(String email) throws Exception {
        HttpResponse<String> response = post("/api/auth/register", Map.of(
            "fullName", "Test Learner", "email", email, "password", "correct-horse-battery"), null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(HttpStatus.OK.value());
        authToken = cookieToken(response);
        assertThat(authToken).isNotBlank();
        return object(response);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send("GET", path, null, token);
    }

    private HttpResponse<String> post(String path, Object body, String token) throws Exception {
        return send("POST", path, body, token);
    }

    private HttpResponse<String> send(String method, String path, Object body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Accept", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (!List.of("GET", "HEAD", "OPTIONS").contains(method) && !path.equals("/api/payments/webhook")) {
            request.header("X-XSRF-TOKEN", csrf);
            if (csrfCookie != null) request.header("Cookie", "XSRF-TOKEN=" + csrfCookie);
        }
        if (body != null) request.header("Content-Type", "application/json");
        HttpRequest.BodyPublisher publisher = body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
        return http.send(request.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(HttpResponse<String> response) throws Exception {
        return (Map<String, Object>) JSON.readValue(response.body(), Object.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> objects(HttpResponse<String> response) throws Exception {
        return (List<Map<String, Object>>) JSON.readValue(response.body(), Object.class);
    }

    private long number(Object value) { return ((Number) value).longValue(); }
    private String cookieToken(HttpResponse<String> response) {
        return response.headers().allValues("set-cookie").stream().filter(value -> value.startsWith("LEARN_SPHERE_TOKEN="))
            .map(value -> value.substring("LEARN_SPHERE_TOKEN=".length(), value.indexOf(';'))).findFirst().orElse(null);
    }
}
