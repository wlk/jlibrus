package io.github.wlk.jlibrus.server.domain.student;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Detects records that reference ids the cached {@link Directory} does not know (a new teacher, subject or
 * category appeared after it was cached). Such a directory must be refreshed before anything is stored,
 * because timeline entries are written once and never corrected.
 */
public final class DirectoryGaps {

    private DirectoryGaps() {
    }

    public static boolean stale(Directory d, StudentRecords r) {
        Stream<Boolean> gaps = Stream.of(
                r.grades().stream().anyMatch(g -> unknown(d.subjects(), g.subjectId())
                        || unknown(d.gradeCategories(), g.categoryId()) || unknown(d.people(), g.teacherId())),
                r.attendances().stream().anyMatch(a -> unknown(d.lessons(), a.lessonId())
                        || unknown(d.attendanceTypes(), a.typeId())),
                r.events().stream().anyMatch(e -> unknown(d.eventCategories(), e.categoryId())
                        || unknown(d.subjects(), e.subjectId())),
                r.notes().stream().anyMatch(n -> unknown(d.noteCategories(), n.categoryId())));
        return gaps.anyMatch(Boolean::booleanValue);
    }

    private static boolean unknown(Map<Long, ?> known, Long id) {
        return id != null && !known.containsKey(id);
    }
}
