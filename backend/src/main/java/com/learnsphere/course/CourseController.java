package com.learnsphere.course;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseService courseService;
    public CourseController(CourseService courseService) { this.courseService = courseService; }

    @GetMapping
    public CourseDtos.CoursePage list(Authentication auth,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String level,
        @RequestParam(defaultValue = "false") boolean enrolledOnly,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "9") int size,
        @RequestParam(defaultValue = "name") String sort,
        @RequestParam(defaultValue = "asc") String direction) {
        return courseService.list(auth.getName(), search, category, level, enrolledOnly, page, size, sort, direction);
    }
    @PostMapping("/{courseId}/enroll")
    public CourseDtos.CourseView enroll(Authentication auth, @PathVariable Long courseId) {
        return courseService.enroll(auth.getName(), courseId);
    }
}
