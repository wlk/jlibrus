package io.github.wlk.jlibrus.client.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One grade as Librus shows it.
 *
 * @param value           the mark as text: "4+", "5", "np", "+" ...
 * @param improvesGradeId id of the earlier grade this one corrects, or {@code null}
 */
public record Grade(long id, String value, Long categoryId, long subjectId, Long teacherId, int semester,
                    LocalDateTime addedAt, GradeKind kind, List<Long> commentIds, Long improvesGradeId) {

    public Grade {
        commentIds = List.copyOf(commentIds);
    }
}
