package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    List<StudentProfile> findAllByNormalizedRecordNumber(String normalizedRecordNumber);

    List<StudentProfile> findAllByNormalizedFullNameAndBirthDate(String normalizedFullName, LocalDate birthDate);

    List<StudentProfile> findAllByNormalizedFullName(String normalizedFullName);

    List<StudentProfile> findAllByNormalizedFullNameIn(Collection<String> normalizedFullNames);
}
