package io.github.wlk.jlibrus.client.grades;

import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a school values "+" and "-" on a 1-6 mark. Schools differ; the common default is +0.5 / -0.25.
 *
 * @param plus  added for "+", in {@code [0, 1)}
 * @param minus subtracted for "-", in {@code [0, 1)}
 */
public record GradeScale(double plus, double minus) {

    public static final GradeScale DEFAULT = new GradeScale(0.5, 0.25);
    private static final Pattern MARK = Pattern.compile("([+-]?)([1-6])([+-]?)");

    public GradeScale {
        if (plus < 0 || plus >= 1 || minus < 0 || minus >= 1) {
            throw new IllegalArgumentException("Plus/minus values must be in [0, 1)");
        }
    }

    /** Numeric value of a mark like "4+", "5-" or "-5"; empty for "np", "+", "bz" and other symbols. */
    public OptionalDouble numeric(String mark) {
        Matcher matcher = MARK.matcher(mark.replace(" ", ""));
        if (!matcher.matches()) {
            return OptionalDouble.empty();
        }
        String modifier = matcher.group(1) + matcher.group(3);
        if (modifier.length() > 1) {
            return OptionalDouble.empty();
        }
        double base = Character.digit(matcher.group(2).charAt(0), 10);
        return OptionalDouble.of(switch (modifier) {
            case "+" -> base + plus;
            case "-" -> base - minus;
            default -> base;
        });
    }
}
