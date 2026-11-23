package io.github.wlk.jlibrus.server.librus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One configured Librus account (one child), read from numbered settings such as
 * {@code LIBRUS_ACCOUNT_1_LOGIN}, {@code LIBRUS_ACCOUNT_1_PASSWORD} and optional {@code LIBRUS_ACCOUNT_1_NAME}.
 *
 * @param label optional display name overriding the student's name from Librus
 */
public record AccountSettings(int index, String login, String password, String label) {

    private static final Pattern KEY = Pattern.compile("LIBRUS_ACCOUNT_(\\d{1,3})_(LOGIN|PASSWORD|NAME)");

    public AccountSettings {
        Objects.requireNonNull(login, "login");
        Objects.requireNonNull(password, "password");
    }

    @Override
    public String toString() {
        return "AccountSettings[" + index + ", login=" + login + ", password=***]";
    }

    /**
     * Collects accounts from any key/value source. Keys are normalised (upper case, dots and dashes become
     * underscores) so {@code librus.account.1.login} in a properties file works too. Incomplete entries are
     * reported, not silently dropped.
     */
    public static List<AccountSettings> parse(Map<String, String> source) {
        Map<Integer, Map<String, String>> byIndex = new TreeMap<>();
        source.forEach((rawKey, value) -> {
            Matcher matcher = KEY.matcher(normalise(rawKey));
            if (matcher.matches() && value != null && !value.isBlank()) {
                byIndex.computeIfAbsent(Integer.parseInt(matcher.group(1)), i -> new TreeMap<>())
                        .put(matcher.group(2), value.trim());
            }
        });
        List<AccountSettings> result = new ArrayList<>();
        byIndex.forEach((index, fields) -> result.add(toSettings(index, fields)));
        return List.copyOf(result);
    }

    private static AccountSettings toSettings(int index, Map<String, String> fields) {
        if (!fields.containsKey("LOGIN") || !fields.containsKey("PASSWORD")) {
            throw new IllegalArgumentException("LIBRUS_ACCOUNT_" + index + " needs both _LOGIN and _PASSWORD");
        }
        return new AccountSettings(index, fields.get("LOGIN"), fields.get("PASSWORD"), fields.get("NAME"));
    }

    static String normalise(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }
}
