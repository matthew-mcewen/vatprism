package net.marvk.fs.vatsim.map.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class GithubVatprismApiTest {
    private static final String RELEASES = """
            [
              {
                "tag_name": "v0.4.1", "draft": false, "prerelease": false, "body": "Fixes",
                "html_url": "https://github.com/owner/repo/releases/tag/v0.4.1",
                "assets": [
                  {"name": "LICENSE", "browser_download_url": "https://github.com/owner/repo/releases/download/v0.4.1/LICENSE"},
                  {"name": "App-0.4.1-windows.msi", "browser_download_url": "https://github.com/owner/repo/releases/download/v0.4.1/App-0.4.1-windows.msi"}
                ]
              },
              {
                "tag_name": "v0.5.0-beta1", "draft": false, "prerelease": true, "body": null,
                "html_url": "https://github.com/owner/repo/releases/tag/v0.5.0-beta1", "assets": []
              },
              {
                "tag_name": "v0.10.0", "draft": false, "prerelease": false, "body": "Big",
                "html_url": "https://github.com/owner/repo/releases/tag/v0.10.0", "assets": []
              },
              {
                "tag_name": "v9.0.0", "draft": true, "prerelease": false, "body": "Draft",
                "html_url": "https://github.com/owner/repo/releases/tag/v9.0.0", "assets": []
              },
              {
                "tag_name": "nightly", "draft": false, "prerelease": false, "body": "Not a version",
                "html_url": "https://github.com/owner/repo/releases/tag/nightly", "assets": []
              }
            ]
            """;

    @Test
    void picksTheHighestStableVersion() {
        final GithubVatprismApi.Release release = GithubVatprismApi.latestRelease(RELEASES, UpdateChannel.STABLE, ".msi").orElseThrow();

        Assertions.assertEquals("0.10.0", release.getVersion().toString());
        Assertions.assertEquals("Big", release.getNotes());
    }

    @Test
    void ignoresDraftsAndTagsWhichAreNotVersions() {
        final String onlyUnusable = """
                [
                  {"tag_name": "v9.0.0", "draft": true, "prerelease": false, "html_url": "https://github.com/owner/repo"},
                  {"tag_name": "nightly", "draft": false, "prerelease": false, "html_url": "https://github.com/owner/repo"}
                ]
                """;

        Assertions.assertEquals(Optional.empty(), GithubVatprismApi.latestRelease(onlyUnusable, UpdateChannel.EXPERIMENTAL, ".msi"));
    }

    @Test
    void considersPrereleasesOnlyOnTheExperimentalChannel() {
        final String releases = """
                [
                  {"tag_name": "v0.4.0", "draft": false, "prerelease": false, "html_url": "https://github.com/owner/repo/a"},
                  {"tag_name": "v0.5.0-beta1", "draft": false, "prerelease": true, "html_url": "https://github.com/owner/repo/b"}
                ]
                """;

        Assertions.assertEquals("0.4.0", GithubVatprismApi.latestRelease(releases, UpdateChannel.STABLE, null).orElseThrow().getVersion().toString());
        Assertions.assertEquals("0.5.0-beta1", GithubVatprismApi.latestRelease(releases, UpdateChannel.EXPERIMENTAL, null).orElseThrow().getVersion().toString());
    }

    @Test
    void linksToTheInstallerOfThePlatformIfThereIsOne() {
        final String single = """
                [{
                  "tag_name": "v0.4.1", "draft": false, "prerelease": false,
                  "html_url": "https://github.com/owner/repo/releases/tag/v0.4.1",
                  "assets": [{"name": "App-0.4.1-windows.MSI", "browser_download_url": "https://github.com/owner/repo/releases/download/v0.4.1/App.msi"}]
                }]
                """;

        Assertions.assertEquals(
                "https://github.com/owner/repo/releases/download/v0.4.1/App.msi",
                GithubVatprismApi.latestRelease(single, UpdateChannel.STABLE, ".msi").orElseThrow().getUrl()
        );
        Assertions.assertEquals(
                "https://github.com/owner/repo/releases/tag/v0.4.1",
                GithubVatprismApi.latestRelease(single, UpdateChannel.STABLE, ".dmg").orElseThrow().getUrl()
        );
        Assertions.assertEquals(
                "https://github.com/owner/repo/releases/tag/v0.4.1",
                GithubVatprismApi.latestRelease(single, UpdateChannel.STABLE, null).orElseThrow().getUrl()
        );
    }

    @Test
    void noReleasesMeansNoUpdate() {
        Assertions.assertEquals(Optional.empty(), GithubVatprismApi.latestRelease("[]", UpdateChannel.STABLE, ".msi"));
    }
}
