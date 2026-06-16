package com.github.calcifux.mailabletoolkit.resolvers;

import java.net.URLConnection;

/** Shared bits for the built-in resolvers: filename + content-type derivation. */
final class ResolverSupport {

    private ResolverSupport() {
    }

    /** Last path segment of a reference (drops any query string); a safe default if empty. */
    static String filename(String reference) {
        String value = reference;
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        return value.isBlank() ? "attachment" : value;
    }

    /** Prefer an explicit (e.g. HTTP-header) type; else guess from the filename; else octet-stream. */
    static String contentType(String filename, String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        String guessed = URLConnection.guessContentTypeFromName(filename);
        return guessed != null ? guessed : "application/octet-stream";
    }
}
