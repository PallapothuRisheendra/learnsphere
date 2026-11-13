package com.learnsphere.course;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {
    java.util.List<Course> findAllByOrderByNameAsc();
    Optional<Course> findByIdAndActiveTrue(Long id);
    @Query(value = """
        select c from Course c where c.active = true
          and (:search is null or lower(c.name) like lower(concat('%', :search, '%'))
            or lower(c.description) like lower(concat('%', :search, '%'))
            or lower(c.category) like lower(concat('%', :search, '%')))
          and (:category is null or lower(c.category) = lower(:category))
          and (:level is null or lower(c.level) = lower(:level))
          and (:enrolledOnly = false or exists (select e.id from Enrollment e where e.course = c and e.user.email = :email))
        """,
        countQuery = """
        select count(c) from Course c where c.active = true
          and (:search is null or lower(c.name) like lower(concat('%', :search, '%'))
            or lower(c.description) like lower(concat('%', :search, '%'))
            or lower(c.category) like lower(concat('%', :search, '%')))
          and (:category is null or lower(c.category) = lower(:category))
          and (:level is null or lower(c.level) = lower(:level))
          and (:enrolledOnly = false or exists (select e.id from Enrollment e where e.course = c and e.user.email = :email))
        """)
    Page<Course> findCatalog(@Param("email") String email, @Param("search") String search,
                             @Param("category") String category, @Param("level") String level,
                             @Param("enrolledOnly") boolean enrolledOnly, Pageable pageable);
}
