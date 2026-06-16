package com.github.calcifux.mailabletoolkit.resolvers;

import com.github.calcifux.mailabletoolkit.AssetResolver;
import com.github.calcifux.mailabletoolkit.ResolvedAsset;
import com.github.calcifux.mailabletoolkit.TerminalMailException;

import java.io.IOException;
import java.io.InputStream;

/** Resolves {@code classpath:mail/logo.png} from the application's resources. */
public class ClasspathAssetResolver implements AssetResolver {

    private static final String SCHEME = "classpath:";

    @Override
    public boolean supports(String uri) {
        return uri.startsWith(SCHEME);
    }

    @Override
    public ResolvedAsset resolve(String uri) {
        String path = uri.substring(SCHEME.length());
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = getClass().getClassLoader();
        }
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                throw new TerminalMailException("Classpath asset not found: " + uri);
            }
            byte[] bytes = in.readAllBytes();
            String filename = ResolverSupport.filename(path);
            return new ResolvedAsset(bytes, ResolverSupport.contentType(filename, null), filename);
        } catch (IOException e) {
            throw new TerminalMailException("Failed reading classpath asset " + uri, e);
        }
    }
}
