package io.github.wlk.jlibrus.server.domain;

import java.util.Locale;
import java.util.Map;

/** Server-generated wording (calendar titles, e-mails) in Polish (default) or English. */
public final class Texts {

    private static final Map<String, String[]> TEXTS = Map.ofEntries(
            entry("homework", "Zadanie domowe", "Homework due"),
            entry("cancelled", "Odwołana lekcja", "Cancelled lesson"),
            entry("substitution", "Zastępstwo", "Substitution"),
            entry("movedIn", "Przeniesiona lekcja", "Moved lesson"),
            entry("movedOut", "Lekcja przeniesiona na inny termin", "Lesson moved elsewhere"),
            entry("teacherAbsent", "Nieobecność nauczyciela", "Teacher absent"),
            entry("lesson", "lekcja", "lesson"),
            entry("luckyNumber", "Szczęśliwy numerek", "Lucky number"),
            entry("grade", "Ocena", "Grade"),
            entry("note.POSITIVE", "Pochwała", "Praise"),
            entry("note.NEGATIVE", "Uwaga negatywna", "Negative note"),
            entry("note.NEUTRAL", "Uwaga", "Note"),
            entry("attendance.ABSENT", "Nieobecność", "Absence"),
            entry("attendance.EXCUSED", "Nieobecność usprawiedliwiona", "Excused absence"),
            entry("attendance.LATE", "Spóźnienie", "Late"),
            entry("attendance.RELEASED", "Zwolnienie", "Released"),
            entry("attendance.PRESENT", "Obecność", "Present"),
            entry("summary.DAILY", "Dzienne podsumowanie", "Daily summary"),
            entry("summary.WEEKLY", "Tygodniowe podsumowanie", "Weekly summary"),
            entry("summary.MONTHLY", "Miesięczne podsumowanie", "Monthly summary"),
            entry("summary.new", "Nowe wpisy", "What's new"),
            entry("summary.upcoming", "Nadchodzące", "Coming up"),
            entry("summary.nothing", "Brak nowych wpisów.", "Nothing new."),
            entry("summary.nothingUpcoming", "Brak zaplanowanych wydarzeń.", "Nothing scheduled."),
            entry("summary.footer", "Wiadomość wysłana automatycznie przez jlibrus.",
                    "Sent automatically by jlibrus."),
            entry("allDay", "cały dzień", "all day"));

    private Texts() {
    }

    private static Map.Entry<String, String[]> entry(String key, String pl, String en) {
        return Map.entry(key, new String[]{pl, en});
    }

    public static String get(String key, Locale locale) {
        String[] values = TEXTS.get(key);
        if (values == null) {
            return key;
        }
        return "en".equals(locale.getLanguage()) ? values[1] : values[0];
    }
}
