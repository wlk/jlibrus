package io.github.wlk.jlibrus.client.model;

/** Whether a grade is a regular one or a semester/final (proposal) summary grade. */
public enum GradeKind {
    REGULAR,
    SEMESTER_PROPOSAL,
    SEMESTER,
    FINAL_PROPOSAL,
    FINAL;

    /** Summary grades summarise regular ones and must not be averaged with them. */
    public boolean isSummary() {
        return this != REGULAR;
    }
}
