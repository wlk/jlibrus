package io.github.wlk.jlibrus.server.security;

import io.github.wlk.jlibrus.server.domain.SecretTokens;
import io.github.wlk.jlibrus.server.domain.SessionPrincipal;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TrustedAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Restores the identity from the encrypted form-login cookie. The cookie can only be minted by the server after a
 * successful password check; it stays valid while the user name matches and its session epoch is current
 * (logout bumps the epoch and so revokes every earlier cookie).
 */
@ApplicationScoped
public class TrustedSessionIdentityProvider implements IdentityProvider<TrustedAuthenticationRequest> {

    private final AuthConfig config;
    private final SessionEpoch epoch;

    @Inject
    public TrustedSessionIdentityProvider(AuthConfig config, SessionEpoch epoch) {
        this.config = config;
        this.epoch = epoch;
    }

    @Override
    public Class<TrustedAuthenticationRequest> getRequestType() {
        return TrustedAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(TrustedAuthenticationRequest request,
                                              AuthenticationRequestContext context) {
        boolean valid = SessionPrincipal.parse(request.getPrincipal())
                .filter(p -> p.epoch() == epoch.current())
                .filter(p -> SecretTokens.matches(config.username(), p.username()))
                .isPresent();
        if (!valid) {
            return Uni.createFrom().failure(new AuthenticationFailedException("Session revoked or unknown user"));
        }
        return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(request.getPrincipal()))
                .addRole(EnvIdentityProvider.ROLE)
                .build());
    }
}
