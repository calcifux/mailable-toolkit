package com.github.calcifux.mailabletoolkit;

/**
 * Resolves an inline image / attachment reference (a URI) to its bytes — the SPI that lets an asset come
 * from anywhere: the classpath, any folder on disk, an HTTP(S) URL (which covers pre-signed S3/GCS/Azure
 * links with no cloud SDK), or a scheme you register yourself ({@code s3://}, {@code gcs://}, …).
 *
 * <p>Resolution happens at <b>render time</b>, so for a queued mail only the small URI travels through the
 * broker (inside the serialized {@code Mailable}) and the worker fetches the bytes when it sends — large
 * files never sit in Redis. A resolver should throw {@link RetryableMailException} for a transient failure
 * (network/timeout) and {@link TerminalMailException} for a permanent one (missing file, 404).</p>
 */
public interface AssetResolver {

    /** Whether this resolver handles the given reference (typically by URI scheme). */
    boolean supports(String uri);

    /** Fetch the bytes (+ content type + filename). Only called when {@link #supports(String)} is true. */
    ResolvedAsset resolve(String uri);
}
