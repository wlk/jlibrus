package io.github.wlk.jlibrus.server.domain.summary;

import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;

import java.util.List;
import java.util.Locale;

/** Content of one summary e-mail: per child, what is new and what is coming up. */
public record Summary(Frequency frequency, SummaryWindow window, Locale locale, List<ChildSection> children) {

    /** One child's part of the summary. */
    public record ChildSection(String childId, String childName, List<TimelineEntry> news, List<AgendaItem> upcoming) {
        public ChildSection {
            news = List.copyOf(news);
            upcoming = List.copyOf(upcoming);
        }

        public boolean isEmpty() {
            return news.isEmpty() && upcoming.isEmpty();
        }
    }

    public Summary {
        children = List.copyOf(children);
    }

    public boolean isEmpty() {
        return children.stream().allMatch(ChildSection::isEmpty);
    }
}
