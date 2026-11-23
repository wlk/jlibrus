package io.github.wlk.jlibrus.mock;

/**
 * A fake Synergia account served by the mock.
 *
 * @param messagesEnabled whether the messages module answers (false simulates "Brak dostępu")
 */
public record MockAccount(String login, String password, String studentFirstName, String studentLastName,
                          int classNumber, String classSymbol, boolean messagesEnabled) {

    public static MockAccount ola() {
        return new MockAccount("1234567u", "demo", "Ola", "Kowalska", 4, "b", true);
    }

    public static MockAccount jan() {
        return new MockAccount("7654321u", "demo", "Jan", "Kowalski", 7, "d", true);
    }

    /** Stable small number derived from the login, so each child gets different but repeatable data. */
    public int seed() {
        return Math.abs(login.hashCode() % 97);
    }
}
