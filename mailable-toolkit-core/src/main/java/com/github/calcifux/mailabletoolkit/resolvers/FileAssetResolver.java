package com.github.calcifux.mailabletoolkit.resolvers;

import com.github.calcifux.mailabletoolkit.AssetResolver;
import com.github.calcifux.mailabletoolkit.ResolvedAsset;
import com.github.calcifux.mailabletoolkit.TerminalMailException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves a filesystem asset — either a {@code file:} URI or a bare path (any folder), so
 * {@code .attach("/var/app/other-folder/anexo.pdf")} and {@code .attach("file:/var/app/anexo.pdf")} both
 * work. Goes last in the chain (it's the catch-all for references without a recognized scheme).
 */
public class FileAssetResolver implements AssetResolver {

    @Override
    public boolean supports(String uri) {
        // file: scheme, or anything that is not a "scheme://" URI (a plain path).
        return uri.startsWith("file:") || !uri.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");
    }

    @Override
    public ResolvedAsset resolve(String uri) {
        Path path = uri.startsWith("file:") ? Path.of(URI.create(uri)) : Path.of(uri);
        try {
            byte[] bytes = Files.readAllBytes(path);
            String filename = path.getFileName() == null ? "attachment" : path.getFileName().toString();
            String probed = Files.probeContentType(path);
            return new ResolvedAsset(bytes, ResolverSupport.contentType(filename, probed), filename);
        } catch (IOException e) {
            throw new TerminalMailException("Failed reading file asset " + uri, e);
        }
    }
}
