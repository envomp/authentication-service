package ee.taltech.arete_admin_panel.repository;

import ee.taltech.arete_admin_panel.domain.Course;
import ee.taltech.arete_admin_panel.domain.CourseStudent;
import ee.taltech.arete_admin_panel.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseStudentRepository extends JpaRepository<CourseStudent, Long> {

    Optional<CourseStudent> findByStudentAndCourse(@Param("student") Student student, @Param("course") Course course);

    List<CourseStudent> findTop500ByOrderByIdDesc();

}