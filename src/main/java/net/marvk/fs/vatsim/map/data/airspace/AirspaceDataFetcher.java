package net.marvk.fs.vatsim.map.data.airspace;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Downloads third party airspace data into the user cache directory. Files are revalidated with their ETag, so an
 * unchanged file costs a single conditional request and no download.
 */
@Log4j2
@Singleton
public class AirspaceDataFetcher {
    private static final String ETAG_SUFFIX = ".etag";
    private static final String TEMP_SUFFIX = ".tmp";

    private static final RequestConfig REQUEST_CONFIG = RequestConfig
            .custom()
            .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
            .setConnectTimeout(10_000)
            .setSocketTimeout(30_000)
            .build();

    private final CloseableHttpClient httpClient;
    private final Path cachePath;

    @Inject
    public AirspaceDataFetcher(final CloseableHttpClient httpClient, @Named("userCacheDir") final Path cachePath) {
        this.httpClient = httpClient;
        this.cachePath = cachePath;
    }

    /**
     * @param fileName         name of the file in the cache directory
     * @param url              url to download the file from
     * @param minCheckInterval the remote is not contacted if the cached file was validated more recently than this
     *
     * @return the path to the most recent available version of the file, empty if there is neither a cached file nor a
     * successful download
     */
    public Optional<Path> fetch(final String fileName, final String url, final Duration minCheckInterval) {
        final Path file = cachePath.resolve(fileName);
        final Path etagFile = cachePath.resolve(fileName + ETAG_SUFFIX);

        try {
            Files.createDirectories(cachePath);

            if (isFresh(file, etagFile, minCheckInterval)) {
                log.info("Using cached %s, last checked less than %s ago".formatted(fileName, minCheckInterval));
                return Optional.of(file);
            }

            download(fileName, url, file, etagFile);
        } catch (final IOException | RuntimeException e) {
            log.warn("Failed to update %s from %s".formatted(fileName, url), e);
        }

        return Files.exists(file) ? Optional.of(file) : Optional.empty();
    }

    private static boolean isFresh(final Path file, final Path etagFile, final Duration minCheckInterval) throws IOException {
        if (!Files.exists(file) || !Files.exists(etagFile)) {
            return false;
        }

        final Instant lastChecked = Files.getLastModifiedTime(etagFile).toInstant();

        return lastChecked.plus(minCheckInterval).isAfter(Instant.now());
    }

    private void download(final String fileName, final String url, final Path file, final Path etagFile) throws IOException {
        final HttpGet request = new HttpGet(url);
        request.setConfig(REQUEST_CONFIG);

        if (Files.exists(file) && Files.exists(etagFile)) {
            final String etag = Files.readString(etagFile).strip();

            // Not every server sends an ETag
            if (!etag.isEmpty()) {
                request.setHeader(HttpHeaders.IF_NONE_MATCH, etag);
            }
        }

        try (final CloseableHttpResponse response = httpClient.execute(request)) {
            final int status = response.getStatusLine().getStatusCode();

            if (status == HttpStatus.SC_NOT_MODIFIED) {
                log.info("Cached %s is up to date".formatted(fileName));
                Files.setLastModifiedTime(etagFile, FileTime.from(Instant.now()));
                return;
            }

            final HttpEntity entity = response.getEntity();

            if (status != HttpStatus.SC_OK || entity == null) {
                throw new IOException("Unexpected response %s".formatted(response.getStatusLine()));
            }

            final Path temp = cachePath.resolve(fileName + TEMP_SUFFIX);

            try (final InputStream in = entity.getContent()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);

            final String etag = response.containsHeader(HttpHeaders.ETAG)
                    ? response.getFirstHeader(HttpHeaders.ETAG).getValue()
                    : "";
            Files.writeString(etagFile, etag, StandardCharsets.UTF_8);

            log.info("Downloaded %s (%d bytes)".formatted(fileName, Files.size(file)));
        }
    }
}
