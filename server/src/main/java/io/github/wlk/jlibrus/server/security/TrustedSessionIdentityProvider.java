package io.github.wlk.jlibrus.server.security;

import io.github.wlk.jlibrus.server.domain.SecretTokens;
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
 * Restores the identity from the encrypted form-login cookie. The cookie can only be minted by the server
 * after a successful password check, so it is trusted as long as the user name still matches the configuration.
 */
@ApplicationScoped
public class TrustedSessionIdentityProvider implements IdentityProvider<TrustedAuthenticationRequest> {

    private final AuthConfig config;

    @Inject
    public TrustedSessionIdentityProvider(AuthConfig config) {
        this.config = config;
    }

    @Override
    public Class<TrustedAuthenticationRequest> getRequestType() {
        return TrustedAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(TrustedAuthenticationRequest request,
                                              AuthenticationRequestContext context) {
        if (!SecretTokens.matches(config.username(), request.getPrincipal())) {
            return Uni.createFrom().failure(new AuthenticationFailedException("Unknown session user"));
        }
        return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(config.username()))
                .addRole(EnvIdentityProvider.ROLE)
                .build());
    }
}
