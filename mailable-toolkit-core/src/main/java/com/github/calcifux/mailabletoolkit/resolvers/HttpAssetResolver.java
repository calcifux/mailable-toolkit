package com.github.calcifux.mailabletoolkit.resolvers;

import com.github.calcifux.mailabletoolkit.AssetResolver;
import com.github.calcifux.mailabletoolkit.ResolvedAsset;
import com.github.calcifux.mailabletoolkit.RetryableMailException;
import com.github.calcifux.mailabletoolkit.TerminalMailException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Resolves {@code http(s):} assets. Covers the common "from the cloud" case without any SDK: a pre-signed
 * S3 / GCS / Azure Blob URL is just an HTTPS link. A {@code 5xx} or network error is retryable (the queue
 * will retry); a {@code 4xx} (404 / expired link) is terminal (straight to the dead-letter).
 */
public class HttpAssetResolver implements AssetResolver {

    private final Duration timeout;

    public HttpAssetResolver() {
        this(Duration.ofSeconds(10));
    }

    public HttpAssetResolver(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public boolean supports(String uri) {
        return uri.startsWith("http://") || uri.startsWith("https://");
    }

    @Override
    public ResolvedAsset resolve(String uri) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri)).timeout(timeout).GET().build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            int status = response.statusCode();
            if (status / 100 != 2) {
                String message = "HTTP " + status + " fetching asset " + uri;
                throw status >= 500 ? new RetryableMailException(message) : new TerminalMailException(message);
            }

            String filename = ResolverSupport.filename(uri);
            String header = response.headers().firstValue("Content-Type").orElse(null);
            if (header != null) {
                int semicolon = header.indexOf(';');   // drop "; charset=..."
                if (semicolon >= 0) {
                    header = header.substring(0, semicolon).trim();
                }
            }
            return new ResolvedAsset(response.body(), ResolverSupport.contentType(filename, header), filename);
        } catch (RetryableMailException | TerminalMailException e) {
            throw e;
        } catch (IOException e) {
            throw new RetryableMailException("Network error fetching asset " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryableMailException("Interrupted fetching asset " + uri, e);
        }
    }
}
