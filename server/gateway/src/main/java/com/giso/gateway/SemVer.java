package com.giso.gateway;

/** 简单语义版本比较（major.minor.patch，非数字后缀忽略）。 */
public final class SemVer {
    private SemVer() { }

    /** a &gt;= b → 非负数；a &lt; b → 负数。 */
    public static int compare(String a, String b) {
        int[] av = parse(a);
        int[] bv = parse(b);
        for (int i = 0; i < 3; i++) {
            int d = av[i] - bv[i];
            if (d != 0) return d;
        }
        return 0;
    }

    private static int[] parse(String v) {
        int[] out = new int[3];
        if (v == null || v.isBlank()) return out;
        String core = v.trim().split("-")[0];
        String[] parts = core.split("\\.");
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9].*", ""));
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }
}
