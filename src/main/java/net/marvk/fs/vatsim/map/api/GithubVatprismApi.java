package net.marvk.fs.vatsim.map.api;

import com.github.zafarkhaja.semver.Version;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.log4j.Log4j2;
import net.marvk.fs.vatsim.map.data.VersionProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Checks for updates against the releases of a GitHub repository.
 */
@Log4j2
public class GithubVatprismApi implements VatprismApi {
    private static final String RELEASES_API_URL = "https://api.github.com/repos/%s/releases?per_page=30";
    private static final String GITHUB_URL = "https://github.com/";

    private final VersionProvider versionProvider;
    private final String repository;
    private final Duration timeout;

    @Inject
    public GithubVatprismApi(
            final VersionProvider versionProvider,
            @Named("githubRepository") final String repository,
            @Named("versionApiTimeout") final Duration timeout
    ) {
        this.versionProvider = versionProvider;
        this.repository = repository;
        this.timeout = timeout;
    }

    @Override
    public VersionResponse checkVersion(final UpdateChannel channel) throws VatprismApiException {
        final Optional<Release> latest;

        try {
            latest = latestRelease(requestReleases(), channel, installerExtension());
        } catch (final RuntimeException e) {
            throw new VatprismApiException("Failed to parse releases", e);
        }

        if (latest.isEmpty()) {
            return VersionResponse.of(false, versionProvider.getString(), releasesUrl(), "");
        }

        final Release release = latest.get();
        final Version currentVersion = versionProvider.getVersion();
        final boolean outdated = currentVersion != null && release.version.greaterThan(currentVersion);

        return VersionResponse.of(outdated, release.version.toString(), sanitize(release.url), release.notes);
    }

    /**
     * Theme choices were reported to the server of the original author, this distribution reports nothing.
     */
    @Override
    public void submitThemeChoice(final String themeName) {
    }

    private String requestReleases() throws VatprismApiException {
        try {
            final HttpRequest request = HttpRequest
                    .newBuilder(new URI(RELEASES_API_URL.formatted(repository)))
                    .header("Accept", "application/vnd.github+json")
                    .timeout(timeout)
                    .GET()
                    .build();

            final HttpResponse<String> response = HttpClient
                    .newBuilder()
                    .connectTimeout(timeout)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new VatprismApiException("Unexpected response %d while fetching releases".formatted(response.statusCode()));
            }

            return response.body();
        } catch (final IOException | InterruptedException | java.net.URISyntaxException e) {
            throw new VatprismApiException("Failed to fetch releases", e);
        }
    }

    /**
     * @param json               the response of the GitHub releases API
     * @param channel            prereleases are only considered for the experimental channel
     * @param installerExtension the file extension of the installer to link to, e.g. {@code .msi}, {@code null} to
     *                           link to the release page
     *
     * @return the release with the highest version
     */
    static Optional<Release> latestRelease(final String json, final UpdateChannel channel, final String installerExtension) {
        final JsonArray releases = JsonParser.parseString(json).getAsJsonArray();

        Release result = null;

        for (final JsonElement element : releases) {
            final JsonObject release = element.getAsJsonObject();

            if (isTrue(release, "draft") || (isTrue(release, "prerelease") && channel != UpdateChannel.EXPERIMENTAL)) {
                continue;
            }

            final Version version = parseVersion(string(release, "tag_name"));

            if (version == null || (result != null && !version.greaterThan(result.version))) {
                continue;
            }

            result = new Release(version, installerUrl(release, installerExtension), string(release, "body"));
        }

        return Optional.ofNullable(result);
    }

    private static String installerUrl(final JsonObject release, final String installerExtension) {
        if (installerExtension != null && release.has("assets") && release.get("assets").isJsonArray()) {
            for (final JsonElement element : release.getAsJsonArray("assets")) {
                final JsonObject asset = element.getAsJsonObject();
                final String name = string(asset, "name");

                if (name != null && name.toLowerCase(Locale.ROOT).endsWith(installerExtension)) {
                    return string(asset, "browser_download_url");
                }
            }
        }

        return string(release, "html_url");
    }

    private static Version parseVersion(final String tag) {
        if (tag == null) {
            return null;
        }

        try {
            return Version.valueOf(tag.startsWith("v") ? tag.substring(1) : tag);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static boolean isTrue(final JsonObject object, final String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
    }

    private static String string(final JsonObject object, final String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    private static String installerExtension() {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (os.startsWith("mac os x")) {
            return ".dmg";
        } else if (os.startsWith("windows")) {
            return ".msi";
        } else {
            return null;
        }
    }

    /**
     * The url is opened in the browser of the user, so it must not point anywhere unexpected.
     */
    private String sanitize(final String url) {
        return url != null && url.startsWith(GITHUB_URL + repository + "/") ? url : releasesUrl();
    }

    private String releasesUrl() {
        return GITHUB_URL + repository + "/releases";
    }

    static class Release {
        private final Version version;
        private final String url;
        private final String notes;

        Release(final Version version, final String url, final String notes) {
            this.version = version;
            this.url = url;
            this.notes = notes == null ? "" : notes;
        }

        Version getVersion() {
            return version;
        }

        String getUrl() {
            return url;
        }

        String getNotes() {
            return notes;
        }
    }
}
