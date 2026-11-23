package io.github.wlk.jlibrus.server.persistence;

import java.util.Optional;

record TestDatabaseConfig(String url, String user, String pass) implements DatabaseConfig {

    @Override
    public Optional<String> username() {
        return Optional.ofNullable(user);
    }

    @Override
    public Optional<String> password() {
        return Optional.ofNullable(pass);
    }

    @Override
    public int poolSize() {
        return 2;
    }
}
