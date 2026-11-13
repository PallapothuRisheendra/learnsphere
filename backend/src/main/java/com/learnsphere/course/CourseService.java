package com.learnsphere.course;

import com.learnsphere.enrollment.Enrollment;
import com.learnsphere.enrollment.EnrollmentRepository;
import com.learnsphere.user.User;
import com.learnsphere.user.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseService {
    private static final Set<String> SORT_FIELDS = Set.of("name", "category", "level", "duration", "priceMinor");
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;

    public CourseService(CourseRepository courses, EnrollmentRepository enrollments, UserRepository users) {
        this.courses = courses; this.enrollments = enrollments; this.users = users;
    }

    @Transactional(readOnly = true)
    public CourseDtos.CoursePage list(String email, String search, String category, String level,
                                      boolean enrolledOnly, int page, int size, String sort, String direction) {
        if (page < 0 || size < 1 || size > 50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be zero or greater and size must be between 1 and 50.");
        String sortField = SORT_FIELDS.contains(sort) ? sort : "name";
        Sort.Direction sortDirection;
        try { sortDirection = Sort.Direction.fromString(direction); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sort direction must be asc or desc."); }
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortField).and(Sort.by("id")));
        Page<Course> found = courses.findCatalog(email, normalize(search), normalize(category), normalize(level), enrolledOnly, pageable);
        List<Long> ids = found.getContent().stream().map(Course::getId).toList();
        Map<Long, Enrollment> enrolled = ids.isEmpty() ? Map.of() : enrollments.findAllByUser_EmailIgnoreCaseAndCourse_IdIn(email, ids).stream()
            .collect(Collectors.toMap(e -> e.getCourse().getId(), Function.identity()));
        return new CourseDtos.CoursePage(found.getContent().stream().map(course -> toView(course, enrolled.get(course.getId()))).toList(),
            found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages(), sortDirection.name().toLowerCase(Locale.ROOT) + "," + sortField);
    }

    @Transactional(readOnly = true)
    public List<CourseDtos.CourseView> adminList() { return courses.findAllByOrderByNameAsc().stream().map(course -> toView(course, null)).toList(); }
    @Transactional
    public void publish(Long courseId) {
        Course course = courses.findById(courseId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));
        course.publish();
    }
    @Transactional(readOnly = true)
    public Course getAnyCourse(Long id) { return courses.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found.")); }

    @Transactional
    public CourseDtos.CourseView enroll(String email, Long courseId) {
        Course course = findCourse(courseId);
        if (course.getPriceMinor() > 0)
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Complete checkout before enrolling in this course.");
        Enrollment existing = enrollments.findByUser_EmailIgnoreCaseAndCourse_Id(email, courseId).orElse(null);
        if (existing != null) return toView(course, existing);
        User user = users.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists."));
        return toView(course, enrollments.save(new Enrollment(user, course)));
    }

    @Transactional
    public CourseDtos.CourseView create(CourseDtos.CourseInput input) {
        validatePriceAndCurrency(input);
        Course course = new Course(input.name().trim(), input.category().trim(), input.level().trim(), input.duration().trim(),
            input.description().trim(), input.icon(), input.color(), input.priceMinor(), input.currency(), input.thumbnailUrl());
        return toView(courses.save(course), null);
    }

    @Transactional
    public CourseDtos.CourseView update(Long courseId, CourseDtos.CourseInput input) {
        validatePriceAndCurrency(input);
        Course course = courses.findById(courseId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));
        course.update(input.name().trim(), input.category().trim(), input.level().trim(), input.duration().trim(),
            input.description().trim(), input.icon(), input.color(), input.priceMinor(), input.currency(), input.thumbnailUrl());
        return toView(course, null);
    }

    @Transactional
    public void deactivate(Long courseId) {
        Course course = courses.findById(courseId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));
        course.deactivate();
    }

    @Transactional(readOnly = true)
    public Course getActiveCourse(Long courseId) { return findCourse(courseId); }

    private void validatePriceAndCurrency(CourseDtos.CourseInput input) {
        if (input.priceMinor() > 0 && (input.currency() == null || input.currency().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency is required for paid courses.");
    }

    private Course findCourse(Long id) {
        return courses.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));
    }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private CourseDtos.CourseView toView(Course course, Enrollment enrollment) {
        return new CourseDtos.CourseView(course.getId(), course.getName(), course.getCategory(), course.getLevel(),
            course.getDuration(), course.getDescription(), course.getIcon(), course.getColor(),
            enrollment == null ? 0 : enrollment.getProgressPercent(), enrollment != null, course.getPriceMinor(), course.getCurrency(), course.getThumbnailUrl(), course.isActive());
    }
}
