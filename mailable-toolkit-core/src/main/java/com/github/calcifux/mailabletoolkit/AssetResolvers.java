package com.github.calcifux.mailabletoolkit;

import com.github.calcifux.mailabletoolkit.resolvers.ClasspathAssetResolver;
import com.github.calcifux.mailabletoolkit.resolvers.FileAssetResolver;
import com.github.calcifux.mailabletoolkit.resolvers.HttpAssetResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered chain of {@link AssetResolver}s: the first one that {@link AssetResolver#supports(String)} a
 * reference resolves it. {@link #defaults()} covers {@code classpath:}, {@code http(s):} and {@code file:}
 * (plus bare filesystem paths). Register extra schemes ({@code s3://}, etc.) by putting your resolver
 * first with {@link #withFirst(AssetResolver)}.
 */
public class AssetResolvers {

    private final List<AssetResolver> resolvers;

    public AssetResolvers(List<AssetResolver> resolvers) {
        this.resolvers = List.copyOf(resolvers);
    }

    /** classpath → http(s) → file (file is the catch-all for bare paths, so it goes last). */
    public static AssetResolvers defaults() {
        return new AssetResolvers(List.of(
                new ClasspathAssetResolver(),
                new HttpAssetResolver(),
                new FileAssetResolver()));
    }

    /** A copy with {@code extra} consulted before all the existing resolvers (e.g. an {@code s3://} one). */
    public AssetResolvers withFirst(AssetResolver extra) {
        List<AssetResolver> chain = new ArrayList<>();
        chain.add(extra);
        chain.addAll(resolvers);
        return new AssetResolvers(chain);
    }

    public ResolvedAsset resolve(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new TerminalMailException("Empty asset reference");
        }
        for (AssetResolver resolver : resolvers) {
            if (resolver.supports(uri)) {
                return resolver.resolve(uri);
            }
        }
        throw new TerminalMailException("No AssetResolver handles '" + uri
                + "' — register one for its scheme (e.g. an s3:// resolver)");
    }
}
