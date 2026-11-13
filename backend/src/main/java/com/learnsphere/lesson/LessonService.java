package com.learnsphere.lesson;

import com.learnsphere.course.CourseRepository;
import com.learnsphere.enrollment.Enrollment;
import com.learnsphere.enrollment.EnrollmentRepository;
import com.learnsphere.user.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LessonService {
    private final LessonRepository lessons;
    private final LessonCompletionRepository completions;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final CourseRepository courses;
    public LessonService(LessonRepository lessons, LessonCompletionRepository completions,
                         EnrollmentRepository enrollments, UserRepository users, CourseRepository courses) {
        this.lessons = lessons; this.completions = completions; this.enrollments = enrollments;
        this.users = users; this.courses = courses;
    }

    @Transactional(readOnly = true)
    public List<LessonDtos.LessonView> list(String email, Long courseId) {
        Enrollment enrollment = requireEnrollment(email, courseId);
        Set<Long> done = completions.findAllByUser_EmailIgnoreCaseAndLesson_Course_Id(email, courseId).stream()
            .map(completion -> completion.getLesson().getId()).collect(Collectors.toSet());
        int progress = enrollment.getProgressPercent();
        return lessons.findAllByCourse_IdAndActiveTrueOrderBySortOrderAsc(courseId).stream()
            .map(lesson -> toView(lesson, done.contains(lesson.getId()), progress)).toList();
    }

    @Transactional
    public LessonDtos.LessonView complete(String email, Long courseId, Long lessonId) {
        Enrollment enrollment = requireEnrollment(email, courseId);
        Lesson lesson = lessons.findByIdAndCourse_IdAndActiveTrue(lessonId, courseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found."));
        if (completions.findByUser_EmailIgnoreCaseAndLesson_Id(email, lessonId).isEmpty()) {
            var user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists."));
            completions.save(new LessonCompletion(user, lesson));
            long total = lessons.countByCourse_IdAndActiveTrue(courseId);
            long complete = completions.countByUser_EmailIgnoreCaseAndLesson_Course_Id(email, courseId);
            int percent = total == 0 ? 0 : (int) Math.round((100.0 * complete) / total);
            enrollment.updateProgress(percent);
        }
        return toView(lesson, true, enrollment.getProgressPercent());
    }

    private Enrollment requireEnrollment(String email, Long courseId) {
        if (!courses.findByIdAndActiveTrue(courseId).isPresent())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found.");
        return enrollments.findByUser_EmailIgnoreCaseAndCourse_Id(email, courseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Enroll in this course to access its lessons."));
    }
    private LessonDtos.LessonView toView(Lesson lesson, boolean completed, int progress) {
        return new LessonDtos.LessonView(lesson.getId(), lesson.getTitle(), lesson.getDescription(), lesson.getContent(),
            lesson.getSortOrder(), completed, progress, lesson.getVideoUrl());
    }
}
