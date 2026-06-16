package com.github.calcifux.mailabletoolkit;

import java.io.Serializable;

/**
 * The bytes of a resolved inline image / attachment, plus the content type and a filename derived from
 * its source. Produced by an {@link AssetResolver} at render time (worker-side when queued).
 */
public record ResolvedAsset(byte[] content, String contentType, String filename) implements Serializable {
}
