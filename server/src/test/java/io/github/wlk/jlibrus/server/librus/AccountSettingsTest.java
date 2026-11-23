package io.github.wlk.jlibrus.server.librus;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountSettingsTest {

    @Test
    void parsesNumberedAccountsInIndexOrder() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("LIBRUS_ACCOUNT_2_LOGIN", "7654321u");
        env.put("LIBRUS_ACCOUNT_2_PASSWORD", "p2");
        env.put("librus.account.1.login", " 1234567u ");
        env.put("librus-account-1-password", "p1");
        env.put("LIBRUS_ACCOUNT_1_NAME", "Ola");
        env.put("PATH", "/usr/bin");
        env.put("LIBRUS_ACCOUNT_3_NAME", "");

        var accounts = AccountSettings.parse(env);

        assertThat(accounts).extracting(AccountSettings::login).containsExactly("1234567u", "7654321u");
        assertThat(accounts.getFirst().label()).isEqualTo("Ola");
        assertThat(accounts.get(1).label()).isNull();
        assertThat(accounts.getFirst().index()).isEqualTo(1);
    }

    @Test
    void incompleteAccountIsAnError() {
        assertThatThrownBy(() -> AccountSettings.parse(Map.of("LIBRUS_ACCOUNT_1_LOGIN", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LIBRUS_ACCOUNT_1");
    }

    @Test
    void passwordNeverPrinted() {
        assertThat(new AccountSettings(1, "l", "secret", null).toString()).doesNotContain("secret");
    }

    @Test
    void noAccountsIsAnEmptyList() {
        assertThat(AccountSettings.parse(Map.of())).isEmpty();
    }
}
