package com.learnsphere.lesson;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/lessons")
public class LessonController {
    private final LessonService lessonService;
    public LessonController(LessonService lessonService) { this.lessonService = lessonService; }

    @GetMapping
    public List<LessonDtos.LessonView> list(Authentication auth, @PathVariable Long courseId) {
        return lessonService.list(auth.getName(), courseId);
    }
    @PostMapping("/{lessonId}/complete")
    public LessonDtos.LessonView complete(Authentication auth, @PathVariable Long courseId, @PathVariable Long lessonId) {
        return lessonService.complete(auth.getName(), courseId, lessonId);
    }
}
