package com.github.calcifux.mailabletoolkit;

import java.io.Serializable;

/**
 * An inline image referenced from the HTML by {@code <img src="cid:<cid>">}. A mail carries 0..N of
 * these (an empty list = none, no special case). The source is EITHER a resource ref
 * ({@code classpath:/...} or a filesystem path) OR raw bytes — use the factory methods.
 *
 * @param cid         the Content-ID used in the HTML (e.g. {@code "logo"} → {@code cid:logo})
 * @param resource    {@code classpath:/...} or a file path (null when using bytes)
 * @param content     raw bytes (null when using a resource ref)
 * @param contentType MIME type for the bytes path (e.g. {@code image/png}); null → guessed
 */
public record InlineAsset(String cid, String resource, byte[] content, String contentType) implements Serializable {

    /** Inline image from a {@code classpath:/...} ref or a file path. */
    public static InlineAsset ofResource(String cid, String resource) {
        return new InlineAsset(cid, resource, null, null);
    }

    /** Inline image from raw bytes (e.g. a generated chart). */
    public static InlineAsset ofData(String cid, byte[] content, String contentType) {
        return new InlineAsset(cid, null, content, contentType);
    }

    public boolean hasBytes() {
        return content != null;
    }
}
