package io.github.wlk.jlibrus.server.security;

import io.github.wlk.jlibrus.server.domain.LoginThrottle;
import io.github.wlk.jlibrus.server.domain.SecretTokens;
import io.github.wlk.jlibrus.server.domain.SessionPrincipal;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Accepts exactly one username/password pair from configuration. There is no user store and no sign-up.
 */
@ApplicationScoped
public class EnvIdentityProvider implements IdentityProvider<UsernamePasswordAuthenticationRequest> {

    private static final Logger LOG = Logger.getLogger(EnvIdentityProvider.class);
    static final String ROLE = "parent";

    /** Key of the global counter: caps guessing even when client addresses are spoofed or rotated. */
    static final String EVERYONE = "*";
    static final int GLOBAL_FACTOR = 4;

    private final AuthConfig config;
    private final SessionEpoch epoch;
    private final LoginThrottle perClient;
    private final LoginThrottle global;

    @Inject
    public EnvIdentityProvider(AuthConfig config, SessionEpoch epoch) {
        this.config = config;
        this.epoch = epoch;
        this.perClient = new LoginThrottle(config.maxFailures(), config.blockWindow(), Instant::now);
        this.global = new LoginThrottle(config.maxFailures() * GLOBAL_FACTOR, config.blockWindow(), Instant::now);
    }

    @Override
    public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
        return UsernamePasswordAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(UsernamePasswordAuthenticationRequest request,
                                              AuthenticationRequestContext context) {
        String client = clientAddress(request);
        if (perClient.isBlocked(client) || global.isBlocked(EVERYONE)) {
            LOG.warnf("Login blocked for %s after repeated failures", client);
            return Uni.createFrom().failure(new AuthenticationFailedException("Too many failed attempts"));
        }
        String password = new String(request.getPassword().getPassword());
        boolean userOk = SecretTokens.matches(config.username(), request.getUsername());
        boolean passOk = SecretTokens.matches(config.password(), password);
        if (!(userOk && passOk)) {
            perClient.recordFailure(client);
            global.recordFailure(EVERYONE);
            LOG.infof("Failed login from %s", client);
            return Uni.createFrom().failure(new AuthenticationFailedException("Invalid credentials"));
        }
        perClient.recordSuccess(client);
        return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(new SessionPrincipal(config.username(), epoch.current()).format()))
                .addRole(ROLE)
                .build());
    }

    private static String clientAddress(UsernamePasswordAuthenticationRequest request) {
        RoutingContext routingContext = HttpSecurityUtils.getRoutingContextAttribute(request);
        if (routingContext == null || routingContext.request().remoteAddress() == null) {
            return "unknown";
        }
        return routingContext.request().remoteAddress().host();
    }
}
