package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;
import java.util.List;

/** Homework assignment ({@code HomeWorkAssignments}). Librus gives no subject, only the teacher. */
public record Homework(long id, String topic, String text, Long teacherId, LocalDate assignedOn,
                       LocalDate dueOn, List<String> attachmentNames) {

    public Homework {
        attachmentNames = List.copyOf(attachmentNames);
    }
}
