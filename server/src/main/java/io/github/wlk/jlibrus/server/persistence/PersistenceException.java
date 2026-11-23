package io.github.wlk.jlibrus.server.persistence;

/** Unchecked wrapper for database failures. */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
