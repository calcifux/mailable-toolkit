// Example (architect) — resolve SMTP servers from a DB table at runtime, and teach the toolkit a new
// asset scheme (s3://). Both are just beans the autoconfig picks up.
package com.example.mail;

import com.github.calcifux.mailabletoolkit.AssetResolver;
import com.github.calcifux.mailabletoolkit.MailerProvider;
import com.github.calcifux.mailabletoolkit.ResolvedAsset;
import com.github.calcifux.mailabletoolkit.RetryableMailException;
import com.github.calcifux.mailabletoolkit.TerminalMailException;
import com.github.calcifux.mailabletoolkit.spring.SmtpMailTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DynamicMailersAndAssets {

    /**
     * A mailer resolved by name from your own table of SMTP servers. The registry tries the static
     * (yml) mailers first, then a short-TTL cache, then this provider. Return null for names you
     * don't own. Usage: Mail.mailer("tenant-42").send(...).
     */
    @Bean
    MailerProvider databaseMailers(SmtpServerRepository repository) {
        return name -> {
            SmtpServerRow row = repository.findByName(name);
            return row == null ? null
                    : SmtpMailTransport.smtp(name, row.host(), row.port(), row.username(), row.password(),
                                             row.encryption(), row.fromEmail(), row.fromName());
        };
    }

    /**
     * Teach the toolkit an s3:// scheme for inline images / attachments. Consulted before the built-in
     * classpath/file/http resolvers. (For pre-signed S3 URLs you don't even need this — the built-in
     * http(s) resolver already handles them.)
     */
    @Bean
    AssetResolver s3AssetResolver(S3Client s3) {
        return new AssetResolver() {
            @Override
            public boolean supports(String uri) {
                return uri.startsWith("s3://");
            }

            @Override
            public ResolvedAsset resolve(String uri) {
                try {
                    S3Object object = s3.get(uri);   // your client
                    return new ResolvedAsset(object.bytes(), object.contentType(), object.filename());
                } catch (TransientS3Exception e) {
                    throw new RetryableMailException("S3 temporarily unavailable for " + uri, e);
                } catch (MissingS3Exception e) {
                    throw new TerminalMailException("S3 object not found: " + uri, e);
                }
            }
        };
    }

    // --- the bits below are just illustrative stand-ins for YOUR types ---
    interface SmtpServerRepository { SmtpServerRow findByName(String name); }
    record SmtpServerRow(String host, int port, String username, String password,
                         String encryption, String fromEmail, String fromName) {}
    interface S3Client { S3Object get(String uri); }
    interface S3Object { byte[] bytes(); String contentType(); String filename(); }
    class TransientS3Exception extends RuntimeException {}
    class MissingS3Exception extends RuntimeException {}
}
