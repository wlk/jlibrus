package io.github.wlk.jlibrus.server.domain.view;

/** Librus colour ids (grade/event categories) to hex colours. */
public final class Palette {

    private static final String[] COLORS = {
            "2196F3", "F0E68C", "87CEFA", "B0C4DE", "F0F8FF", "F0FFFF", "F5F5DC", "FFEBCD", "FFF8DC", "A9A9A9",
            "BDB76B", "8FBC8F", "DCDCDC", "DAA520", "E6E6FA", "FFA07A", "32CD32", "66CDAA", "66CDAA", "C0C0C0",
            "D2B48C", "3333FF", "7B68EE", "BA55D3", "FFB6C1", "FF1493", "DC143C", "FF0000", "FF8C00", "FFD700",
            "ADFF2F", "7CFC00"};

    private Palette() {
    }

    /** {@code #RRGGBB}; unknown ids get the default blue. */
    public static String hex(Integer colorId) {
        int index = colorId == null || colorId < 1 || colorId >= COLORS.length ? 0 : colorId;
        return "#" + COLORS[index];
    }
}
