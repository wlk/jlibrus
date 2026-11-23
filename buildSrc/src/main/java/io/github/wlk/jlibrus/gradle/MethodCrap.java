package io.github.wlk.jlibrus.gradle;

import java.util.Locale;

/**
 * CRAP (Change Risk Anti-Patterns) score of one method: {@code comp^2 * (1 - cov)^3 + comp}.
 */
public record MethodCrap(String className, String method, int line, int complexity, double coverage) {

    public double crap() {
        double uncovered = 1.0 - coverage;
        return complexity * complexity * uncovered * uncovered * uncovered + complexity;
    }

    public String describe() {
        return String.format(Locale.ROOT, "%s#%s (line %d) CRAP=%.1f complexity=%d coverage=%d%%",
                className, method, line, crap(), complexity, Math.round(coverage * 100));
    }
}
