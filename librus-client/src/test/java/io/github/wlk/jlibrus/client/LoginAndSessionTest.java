package io.github.wlk.jlibrus.client;

import io.github.wlk.jlibrus.mock.MockAccount;
import io.github.wlk.jlibrus.mock.MockLibrus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAndSessionTest {

    private MockLibrus mock;

    @BeforeEach
    void start() {
        mock = MockedLibrus.start();
    }

    @AfterEach
    void stop() {
        mock.close();
    }

    @Test
    void logsInThroughTheRedirectChainAndReadsTheAccount() {
        LibrusAccount account = MockedLibrus.account(mock, MockAccount.ola());

        var me = account.me();

        assertThat(me.studentName()).isEqualTo("Ola Kowalska");
        assertThat(me.login()).isEqualTo("1234567u");
        assertThat(me.parentAccount()).isTrue();
        assertThat(mock.state().passwordLogins()).isEqualTo(1);
    }

    @Test
    void wrongPasswordIsReportedAndNotRetriedDuringBackoff() {
        LibrusAccount account = MockedLibrus.account(mock, "1234567u", "wrong");

        assertThatThrownBy(account::me)
                .isInstanceOf(LibrusException.InvalidCredentials.class)
                .hasMessageContaining("Nieprawidłowy login lub hasło");
        assertThatThrownBy(account::grades).isInstanceOf(LibrusException.InvalidCredentials.class);
        assertThat(mock.state().passwordLogins()).isZero();
    }

    @Test
    void captchaPageIsRecognised() {
        LibrusAccount account = MockedLibrus.account(mock, "captcha", "x");

        assertThatThrownBy(account::connect).isInstanceOf(LibrusException.CaptchaRequired.class);
    }

    @Test
    void expiredTokenIsRenewedWithoutPasswordLogin() {
        LibrusAccount account = MockedLibrus.account(mock, MockAccount.ola());
        account.connect();
        mock.state().expireTokens();

        assertThat(account.subjects()).isNotEmpty();
        assertThat(mock.state().passwordLogins()).isEqualTo(1);
    }

    @Test
    void lostSynergiaSessionTriggersPasswordLogin() {
        LibrusAccount account = MockedLibrus.account(mock, MockAccount.ola());
        account.connect();
        mock.state().expireSynergiaSessions();

        assertThat(account.users()).isNotEmpty();
        assertThat(mock.state().passwordLogins()).isEqualTo(2);
    }

    @Test
    void childrenHaveSeparateSessions() {
        LibrusAccount ola = MockedLibrus.account(mock, MockAccount.ola());
        LibrusAccount jan = MockedLibrus.account(mock, MockAccount.jan());

        assertThat(ola.me().studentName()).isEqualTo("Ola Kowalska");
        assertThat(jan.me().studentName()).isEqualTo("Jan Kowalski");
        assertThat(ola.me().studentName()).isEqualTo("Ola Kowalska");
    }

    @Test
    void unreachableHostIsServiceUnavailable() {
        LibrusAccount account = LibrusAccount.builder(new Credentials("x", "y"))
                .endpoints(LibrusEndpoints.singleHost(java.net.URI.create("http://127.0.0.1:1")))
                .build();

        assertThatThrownBy(account::connect).isInstanceOf(LibrusException.ServiceUnavailable.class);
    }
}
