package io.github.wlk.jlibrus.server.it;

import io.github.wlk.jlibrus.server.domain.student.Directory;
import io.github.wlk.jlibrus.server.librus.Children;
import io.github.wlk.jlibrus.server.librus.LibrusData;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reference data stays cached until it is forgotten for one child; the other children keep their copy. */
@QuarkusTest
@QuarkusTestResource(MockLibrusResource.class)
class LibrusDataCacheIT {

    @Inject
    LibrusData data;

    @Inject
    Children children;

    @AfterEach
    void clear() {
        data.forgetEverything();
    }

    @Test
    void forgettingTheDirectoryReloadsOnlyThatChild() {
        List<String> ids = children.ids();
        assertThat(ids).hasSizeGreaterThanOrEqualTo(2);
        String first = ids.get(0);
        String second = ids.get(1);
        Directory firstBefore = data.directory(first);
        Directory secondBefore = data.directory(second);
        assertThat(data.directory(first)).isSameAs(firstBefore);

        data.forgetDirectory(first);

        assertThat(data.directory(first)).isNotSameAs(firstBefore);
        assertThat(data.directory(second)).isSameAs(secondBefore);
    }
}
