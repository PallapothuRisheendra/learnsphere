package com.learnsphere.admin;

import com.learnsphere.auth.AuthDtos;
import com.learnsphere.course.CourseDtos;
import com.learnsphere.course.CourseService;
import com.learnsphere.enrollment.EnrollmentRepository;
import com.learnsphere.lesson.Lesson;
import com.learnsphere.lesson.LessonDtos;
import com.learnsphere.lesson.LessonRepository;
import com.learnsphere.user.User;
import com.learnsphere.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    public record UserUpdate(@NotBlank @Size(max=100) String fullName, @Email @NotBlank @Size(max=254) String email,
                             @NotBlank String role, Boolean active) {}
    public record UserAdminView(Long id, String fullName, String email, String role, boolean emailVerified,
                                boolean active, java.time.Instant createdAt) {}
    public record EnrollmentView(Long id, Long userId, String email, Long courseId, String courseName,
                                 int progress, java.time.Instant enrolledAt) {}
    private final CourseService courses;
    private final UserRepository users;
    private final LessonRepository lessons;
    private final EnrollmentRepository enrollments;

    public AdminController(CourseService courses, UserRepository users, LessonRepository lessons, EnrollmentRepository enrollments) {
        this.courses = courses; this.users = users; this.lessons = lessons; this.enrollments = enrollments;
    }

    @GetMapping("/courses")
    public List<CourseDtos.CourseView> listCourses(Authentication auth) { return courses.adminList(); }

    @PostMapping("/courses")
    public CourseDtos.CourseView createCourse(@Valid @RequestBody CourseDtos.CourseInput input) { return courses.create(input); }
    @PutMapping("/courses/{id}")
    public CourseDtos.CourseView updateCourse(@PathVariable Long id, @Valid @RequestBody CourseDtos.CourseInput input) { return courses.update(id, input); }
    @DeleteMapping("/courses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCourse(@PathVariable Long id) { courses.deactivate(id); }
    @PostMapping("/courses/{id}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publishCourse(@PathVariable Long id) { courses.publish(id); }
    @GetMapping("/courses/{courseId}/lessons")
    public List<LessonDtos.LessonView> listLessons(@PathVariable Long courseId) {
        courses.getAnyCourse(courseId);
        return lessons.findAllByCourse_IdAndActiveTrueOrderBySortOrderAsc(courseId).stream()
            .map(l -> new LessonDtos.LessonView(l.getId(), l.getTitle(), l.getDescription(), l.getContent(), l.getSortOrder(), false, 0, l.getVideoUrl())).toList();
    }
    @PostMapping("/courses/{courseId}/lessons")
    public LessonDtos.LessonView createLesson(@PathVariable Long courseId, @Valid @RequestBody LessonDtos.LessonInput input) {
        var course = courses.getAnyCourse(courseId);
        Lesson lesson = lessons.save(new Lesson(course, input.title().trim(), input.description().trim(), input.content().trim(), input.sortOrder(), input.videoUrl()));
        return view(lesson);
    }
    @PutMapping("/courses/{courseId}/lessons/{lessonId}")
    @Transactional
    public LessonDtos.LessonView updateLesson(@PathVariable Long courseId, @PathVariable Long lessonId, @Valid @RequestBody LessonDtos.LessonInput input) {
        Lesson lesson = lessons.findByIdAndCourse_IdAndActiveTrue(lessonId, courseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found."));
        lesson.update(input.title().trim(), input.description().trim(), input.content().trim(), input.sortOrder(), input.videoUrl());
        return view(lesson);
    }
    @DeleteMapping("/courses/{courseId}/lessons/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteLesson(@PathVariable Long courseId, @PathVariable Long lessonId) {
        Lesson lesson = lessons.findByIdAndCourse_IdAndActiveTrue(lessonId, courseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found."));
        lesson.deactivate();
    }
    @GetMapping("/users")
    public List<UserAdminView> listUsers() {
        return users.findAllByActiveTrueOrderByCreatedAtDesc().stream().map(this::view).toList();
    }
    @PutMapping("/users/{id}")
    @Transactional
    public UserAdminView updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdate input) {
        User user = users.findByIdAndActiveTrue(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        String email = input.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (users.existsByEmailIgnoreCaseAndIdNot(email, id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already in use.");
        String role = input.role().trim().toUpperCase(java.util.Locale.ROOT);
        if (!role.equals("LEARNER") && !role.equals("ADMIN")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be LEARNER or ADMIN.");
        user.updateProfile(input.fullName().trim(), email, role);
        if (Boolean.FALSE.equals(input.active())) user.deactivate();
        return view(user);
    }
    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteUser(@PathVariable Long id) {
        User user = users.findByIdAndActiveTrue(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        user.deactivate();
    }
    @GetMapping("/enrollments")
    @Transactional(readOnly = true)
    public List<EnrollmentView> listEnrollments() {
        return enrollments.findAll().stream().map(e -> new EnrollmentView(e.getId(), e.getUser().getId(), e.getUser().getEmail(),
            e.getCourse().getId(), e.getCourse().getName(), e.getProgressPercent(), e.getEnrolledAt())).toList();
    }
    @DeleteMapping("/enrollments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void removeEnrollment(@PathVariable Long id) {
        if (!enrollments.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found.");
        enrollments.deleteById(id);
    }
    private LessonDtos.LessonView view(Lesson lesson) {
        return new LessonDtos.LessonView(lesson.getId(), lesson.getTitle(), lesson.getDescription(), lesson.getContent(), lesson.getSortOrder(), false, 0, lesson.getVideoUrl());
    }
    private UserAdminView view(User user) { return new UserAdminView(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.isEmailVerified(), user.isActive(), user.getCreatedAt()); }
}
