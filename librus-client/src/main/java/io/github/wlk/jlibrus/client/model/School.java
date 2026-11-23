package io.github.wlk.jlibrus.client.model;

import java.util.List;
import java.util.Optional;

/** The school and its bell schedule. */
public record School(long id, String name, String town, List<LessonSlot> lessonSlots) {

    public School {
        lessonSlots = List.copyOf(lessonSlots);
    }

    public Optional<LessonSlot> slot(int lessonNo) {
        return lessonSlots.stream().filter(s -> s.number() == lessonNo).findFirst();
    }
}
