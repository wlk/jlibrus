package io.github.wlk.jlibrus.client.model;

/** A room. */
public record Classroom(long id, String name, String symbol) {

    /** "symbol name", or just one of them when one already contains the other. */
    public String displayName() {
        String safeName = name == null ? "" : name.strip();
        String safeSymbol = symbol == null ? "" : symbol.strip();
        if (safeName.contains(safeSymbol)) {
            return safeName;
        }
        if (safeSymbol.contains(safeName)) {
            return safeSymbol;
        }
        return safeSymbol + " " + safeName;
    }
}
