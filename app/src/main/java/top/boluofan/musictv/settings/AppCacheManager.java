package top.boluofan.musictv.settings;

import java.io.File;

public final class AppCacheManager {
    private AppCacheManager() {}

    public static long measure(File... cacheRoots) {
        long bytes = 0L;
        if (cacheRoots == null) return bytes;
        for (File cacheRoot : cacheRoots) bytes += measureEntry(cacheRoot);
        return bytes;
    }

    private static long measureEntry(File cacheRoot) {
        if (cacheRoot == null || !cacheRoot.exists()) return 0L;
        if (cacheRoot.isFile()) return cacheRoot.length();
        File[] children = cacheRoot.listFiles();
        if (children == null) return 0L;
        long bytes = 0L;
        for (File child : children) bytes += measureEntry(child);
        return bytes;
    }

}
