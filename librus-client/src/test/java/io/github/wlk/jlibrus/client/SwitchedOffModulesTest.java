package io.github.wlk.jlibrus.client;

import io.github.wlk.jlibrus.client.ScriptedServer.Reply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Modules a school may switch off read as empty; core resources still fail loudly. */
class SwitchedOffModulesTest {

    private final ScriptedServer server = new ScriptedServer();

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void switchedOffModulesReadAsEmptyButCoreResourcesFail() {
        // every gateway resource without a route answers 404
        server.happyLogin("DZIENNIKSID=d; Path=/", "oauth_token=t; Path=/")
                .on("/gateway/api/2.0/Notes", Reply.json(403, "{}"))
                .on("/gateway/api/2.0/LuckyNumbers", Reply.json(401, "{\"Message\":\"Insufficient scopes\"}"));
        LibrusAccount account = LibrusAccount.builder(new Credentials("1u", "p"))
                .endpoints(LibrusEndpoints.singleHost(server.base())).build();

        assertThat(account.classrooms()).isEmpty();
        assertThat(account.gradeComments()).isEmpty();
        assertThat(account.eventCategories()).isEmpty();
        assertThat(account.noteCategories()).isEmpty();
        assertThat(account.behaviour()).isEmpty();
        assertThat(account.notes()).isEmpty();
        assertThat(account.announcements()).isEmpty();
        assertThat(account.luckyNumber()).isEmpty();
        assertThat(account.timetable(LocalDate.of(2026, 11, 25))).isEmpty();
        assertThat(account.events()).isEmpty();
        assertThat(account.homework()).isEmpty();
        assertThat(account.parentMeetings()).isEmpty();
        assertThat(account.schoolFreeDays()).isEmpty();
        assertThat(account.teacherAbsences()).isEmpty();
        assertThat(account.justifications()).isEmpty();
        assertThat(account.schoolTrips()).isEmpty();
        assertThat(account.descriptiveGrades()).isEmpty();
        assertThatThrownBy(account::grades).isInstanceOf(LibrusException.ModuleUnavailable.class);
    }
}
