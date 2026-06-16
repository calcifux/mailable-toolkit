package com.github.calcifux.mailabletoolkit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetResolversTest {

    private final AssetResolvers resolvers = AssetResolvers.defaults();

    @Test
    void resolves_classpath_with_guessed_type_and_filename() {
        ResolvedAsset asset = resolvers.resolve("classpath:assets/sample.txt");

        assertThat(new String(asset.content())).contains("calcifux");
        assertThat(asset.filename()).isEqualTo("sample.txt");
        assertThat(asset.contentType()).isEqualTo("text/plain");
    }

    @Test
    void resolves_a_bare_file_path() throws Exception {
        Path tmp = Files.createTempFile("mt-asset", ".csv");
        Files.writeString(tmp, "a,b,c");
        try {
            ResolvedAsset asset = resolvers.resolve(tmp.toString());
            assertThat(new String(asset.content())).isEqualTo("a,b,c");
            assertThat(asset.filename()).endsWith(".csv");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void unknown_scheme_is_terminal() {
        assertThatThrownBy(() -> resolvers.resolve("s3://bucket/key.png"))
                .isInstanceOf(TerminalMailException.class)
                .hasMessageContaining("No AssetResolver");
    }

    @Test
    void a_custom_resolver_takes_precedence_over_the_built_ins() {
        AssetResolver s3 = new AssetResolver() {
            @Override public boolean supports(String uri) { return uri.startsWith("s3://"); }
            @Override public ResolvedAsset resolve(String uri) { return new ResolvedAsset("X".getBytes(), "image/png", "x.png"); }
        };

        ResolvedAsset asset = resolvers.withFirst(s3).resolve("s3://bucket/x.png");

        assertThat(asset.filename()).isEqualTo("x.png");
        assertThat(asset.contentType()).isEqualTo("image/png");
    }
}
