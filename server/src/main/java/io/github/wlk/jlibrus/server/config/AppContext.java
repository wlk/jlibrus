package io.github.wlk.jlibrus.server.config;

import io.github.wlk.jlibrus.client.grades.GradeScale;
import io.github.wlk.jlibrus.server.librus.LibrusSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/** Clock, school time zone, language and grading scale - one place for cross-cutting settings. */
@ApplicationScoped
public class AppContext {

    private final Clock clock;
    private final ZoneId zone;
    private final Locale locale;
    private final GradeScale scale;

    @Inject
    public AppContext(LibrusSettings settings, @ConfigProperty(name = "app.language", defaultValue = "pl") String language) {
        this(Clock.systemUTC(), ZoneId.of(settings.timezone()), Locale.forLanguageTag(language),
                new GradeScale(settings.gradePlus(), settings.gradeMinus()));
    }

    public AppContext(Clock clock, ZoneId zone, Locale locale, GradeScale scale) {
        this.clock = clock;
        this.zone = zone;
        this.locale = locale;
        this.scale = scale;
    }

    public Clock clock() {
        return clock;
    }

    public ZoneId zone() {
        return zone;
    }

    public Locale locale() {
        return locale;
    }

    public GradeScale scale() {
        return scale;
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(clock.withZone(zone));
    }

    public LocalDate today() {
        return now().toLocalDate();
    }
}
