package io.github.wlk.jlibrus.server.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kinds of things that happen in Librus. Users pick from these for e-mail summaries and calendar feeds.
 */
public enum EntryType {
    GRADE("Grades", "Oceny", false),
    BEHAVIOUR_GRADE("Behaviour grades", "Oceny z zachowania", false),
    ATTENDANCE("Absences and lateness", "Nieobecności i spóźnienia", false),
    NOTE("Behaviour notes", "Uwagi", false),
    EVENT("Tests and calendar events", "Sprawdziany i wydarzenia", true),
    HOMEWORK("Homework", "Zadania domowe", true),
    ANNOUNCEMENT("Announcements", "Ogłoszenia", false),
    MESSAGE("Messages", "Wiadomości", false),
    LUCKY_NUMBER("Lucky number", "Szczęśliwy numerek", false),
    FREE_DAY("School free days", "Dni wolne", true),
    TEACHER_ABSENCE("Teacher absences", "Nieobecności nauczycieli", true),
    PARENT_MEETING("Parent-teacher meetings", "Zebrania z rodzicami", true),
    TIMETABLE_CHANGE("Substitutions and cancellations", "Zastępstwa i odwołane lekcje", true),
    LESSON("Lessons (timetable)", "Lekcje (plan)", true);

    private final String labelEn;
    private final String labelPl;
    private final boolean calendar;

    EntryType(String labelEn, String labelPl, boolean calendar) {
        this.labelEn = labelEn;
        this.labelPl = labelPl;
        this.calendar = calendar;
    }

    public String label(Locale locale) {
        return "pl".equals(locale.getLanguage()) ? labelPl : labelEn;
    }

    /** Whether entries of this type have a date worth exporting to a calendar. */
    public boolean isCalendar() {
        return calendar;
    }

    public static Set<EntryType> calendarTypes() {
        return Arrays.stream(values()).filter(EntryType::isCalendar)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Parses a comma-separated list; blank means "all types". Unknown names are rejected. */
    public static Set<EntryType> parseSet(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.copyOf(EnumSet.allOf(EntryType.class));
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> valueOf(s.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());
    }
}
