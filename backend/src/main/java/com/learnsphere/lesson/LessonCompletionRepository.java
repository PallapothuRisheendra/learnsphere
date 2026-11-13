package com.learnsphere.lesson;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonCompletionRepository extends JpaRepository<LessonCompletion, Long> {
    List<LessonCompletion> findAllByUser_EmailIgnoreCaseAndLesson_Course_Id(String email, Long courseId);
    Optional<LessonCompletion> findByUser_EmailIgnoreCaseAndLesson_Id(String email, Long lessonId);
    long countByUser_EmailIgnoreCaseAndLesson_Course_Id(String email, Long courseId);
}
