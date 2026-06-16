package com.github.calcifux.mailabletoolkit;

import java.io.Serializable;

/**
 * An attachment carried as raw BYTES (no disk) — the {@code attachData(...)} path. Preferred over
 * file-path attachments for generated content (a PDF built in memory) and queue-safe: when a mailable
 * is sent async, the bytes are produced WORKER-side (build() runs there), so they never bloat the broker.
 *
 * @param filename    the name the recipient sees
 * @param content     the raw bytes
 * @param contentType MIME type, e.g. {@code application/pdf} (null → application/octet-stream)
 */
public record DataAttachment(String filename, byte[] content, String contentType) implements Serializable {
}
