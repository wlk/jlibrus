package io.github.wlk.jlibrus.client.messages;

import java.util.Set;

/** Recipient groups that are not school staff (parent councils, own contact groups, Librus support). */
final class RecipientGroups {

    static final Set<String> SKIPPED = Set.of("parentsCouncil", "schoolParentsCouncil", "customGroups",
            "contactsGroups", "sadmin");

    private RecipientGroups() {
    }
}
