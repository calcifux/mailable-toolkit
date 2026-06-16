// Example (architect) — a custom AssetResolver for an "sftp:" scheme. Lets any mailable attach files that
// live on an SFTP server: `.attach("sftp:/facturas/INV-1024.pdf")`. The toolkit downloads the bytes at
// SEND time (worker-side when queued), so only the path travels through the queue, never the megabytes.
// This is the whole point of the AssetResolver SPI: a new source is a new bean, not a fork of the renderer.
package com.example.mail;

import com.github.calcifux.mailabletoolkit.AssetResolver;
import com.github.calcifux.mailabletoolkit.ResolvedAsset;
import com.github.calcifux.mailabletoolkit.RetryableMailException;
import com.github.calcifux.mailabletoolkit.TerminalMailException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class SftpAssetResolver {

    @Bean
    AssetResolver sftpAssets(SftpClient sftp) {
        return new AssetResolver() {
            @Override
            public boolean supports(String uri) {
                return uri.startsWith("sftp:");
            }

            @Override
            public ResolvedAsset resolve(String uri) {
                String path = uri.substring("sftp:".length());
                if (!sftp.exists(path)) {
                    throw new TerminalMailException("SFTP file not found: " + path);     // permanent → dead-letter
                }
                try (InputStream in = sftp.download(path)) {
                    byte[] bytes = in.readAllBytes();
                    String filename = path.substring(path.lastIndexOf('/') + 1);
                    String type = filename.toLowerCase().endsWith(".pdf")
                            ? "application/pdf" : "application/octet-stream";
                    return new ResolvedAsset(bytes, type, filename);
                } catch (IOException e) {
                    throw new RetryableMailException("SFTP unavailable for " + path, e);  // transient → retry
                }
            }
        };
    }

    // illustrative stand-in for YOUR SFTP client
    interface SftpClient {
        boolean exists(String path);
        InputStream download(String path);
    }
}
