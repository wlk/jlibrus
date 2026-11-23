package io.github.wlk.jlibrus.client.model;

import java.time.LocalDateTime;

/** Behaviour entry: points, a monthly behaviour grade, or a short mark ({@code BehaviourGrades/Points}). */
public record BehaviourPoint(long id, String display, String text, Long categoryId, Long teacherId,
                             Integer semester, LocalDateTime addedAt) {
}
