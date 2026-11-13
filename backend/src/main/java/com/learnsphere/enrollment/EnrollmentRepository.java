package com.learnsphere.enrollment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findAllByUser_EmailIgnoreCase(String email);
    Optional<Enrollment> findByUser_EmailIgnoreCaseAndCourse_Id(String email, Long courseId);
    boolean existsByUser_EmailIgnoreCaseAndCourse_Id(String email, Long courseId);
    List<Enrollment> findAllByUser_EmailIgnoreCaseAndCourse_IdIn(String email, Collection<Long> courseIds);
}
