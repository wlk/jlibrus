package io.github.wlk.jlibrus.client.model;

/** A teacher or other school employee ({@code Users}). */
public record Person(long id, String firstName, String lastName) {

    public String fullName() {
        return (firstName + " " + lastName).trim();
    }
}
