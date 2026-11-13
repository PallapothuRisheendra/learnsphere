package com.learnsphere.lesson;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findAllByCourse_IdAndActiveTrueOrderBySortOrderAsc(Long courseId);
    Optional<Lesson> findByIdAndCourse_IdAndActiveTrue(Long id, Long courseId);
    long countByCourse_IdAndActiveTrue(Long courseId);
}
