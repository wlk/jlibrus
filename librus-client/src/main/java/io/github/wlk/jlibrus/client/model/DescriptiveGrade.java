package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Descriptive grade (grades 1-3 and some subjects): a value on the school's scale for one skill, usually with a
 * teacher's comment.
 *
 * @param id        unique within the account ({@code p}-prefixed for the newer grading system)
 * @param skill     what was assessed, e.g. "Ekspresja muzyczna. Śpiew"
 * @param teacherId {@code Users} id; {@code null} for the newer system, which uses other identifiers
 * @param system    which Librus grading system the grade comes from
 */
public record DescriptiveGrade(String id, Long subjectId, String value, String skill, Long teacherId, LocalDate date,
                               LocalDateTime addedAt, Integer semester, List<String> comments,
                               List<String> requirements, DescriptiveSystem system) {

    public DescriptiveGrade {
        comments = List.copyOf(comments);
        requirements = List.copyOf(requirements);
    }

    /** Librus has two descriptive grading systems. */
    public enum DescriptiveSystem {
        /** {@code DescriptiveGrades}: skills with a value on the school scale. */
        CLASSIC,
        /** Newer "partial grades" of the descriptive grading system (grade 1 from 2026). */
        PARTIAL
    }
}
