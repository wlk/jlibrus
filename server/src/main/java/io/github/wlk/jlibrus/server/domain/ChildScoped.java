package io.github.wlk.jlibrus.server.domain;

import java.util.Set;

/** Something the user set up for some children. Choosing none means every child, including ones added later. */
// A trait that records implement through their childIds accessor, never a lambda target.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface ChildScoped {

    /** Children chosen; empty means all children. */
    Set<String> childIds();

    default boolean includesChild(String childId) {
        Set<String> chosen = childIds();
        return chosen.isEmpty() || chosen.contains(childId);
    }
}
