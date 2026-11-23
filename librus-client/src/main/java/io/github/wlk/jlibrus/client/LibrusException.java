package io.github.wlk.jlibrus.client;

/** Base of every failure reported by the client. Unchecked; subclasses say what the caller can do. */
public class LibrusException extends RuntimeException {

    public LibrusException(String message) {
        super(message);
    }

    public LibrusException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Librus rejected the login or password. Retrying with the same credentials will not help. */
    public static final class InvalidCredentials extends LibrusException {
        public InvalidCredentials(String message) {
            super(message);
        }
    }

    /** Librus wants a captcha / device confirmation; log in once in a browser and retry later. */
    public static final class CaptchaRequired extends LibrusException {
        public CaptchaRequired(String message) {
            super(message);
        }
    }

    /** Maintenance, outage, or an unexpected non-JSON page. Retry later. */
    public static final class ServiceUnavailable extends LibrusException {
        public ServiceUnavailable(String message) {
            super(message);
        }

        public ServiceUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** This account or school has no access to the requested module (403 / insufficient scopes). */
    public static final class ModuleUnavailable extends LibrusException {
        public ModuleUnavailable(String message) {
            super(message);
        }
    }

    /** The session could not be renewed even with a fresh password login. */
    public static final class SessionExpired extends LibrusException {
        public SessionExpired(String message) {
            super(message);
        }
    }

    /** Librus answered with something the client does not understand. */
    public static final class ProtocolError extends LibrusException {
        public ProtocolError(String message) {
            super(message);
        }

        public ProtocolError(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
