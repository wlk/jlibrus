package io.github.wlk.jlibrus.client.model;

import java.util.Objects;

/**
 * Who is logged in ({@code Me}). For a parent login the account owner is the parent and the student is the child.
 *
 * @param studentUserId the student's {@code Users} id, or {@code null} when Librus does not say
 */
public record AccountInfo(long accountId, String login, String ownerFirstName, String ownerLastName,
                          boolean parentAccount, String studentFirstName, String studentLastName,
                          Long studentUserId) {

    public AccountInfo {
        Objects.requireNonNull(login, "login");
    }

    public String studentName() {
        return (studentFirstName + " " + studentLastName).trim();
    }
}
