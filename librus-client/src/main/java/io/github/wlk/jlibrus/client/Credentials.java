package io.github.wlk.jlibrus.client;

/**
 * Synergia login and password of one child (or the parent account linked to that child).
 * {@link #toString()} never reveals the password.
 */
public record Credentials(String login, String password) {

    public Credentials {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Librus login must not be blank");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Librus password must not be empty");
        }
        login = login.trim();
    }

    @Override
    public String toString() {
        return "Credentials[login=" + login + ", password=***]";
    }
}
