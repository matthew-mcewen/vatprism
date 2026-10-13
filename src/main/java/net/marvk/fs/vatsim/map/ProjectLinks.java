package net.marvk.fs.vatsim.map;

/**
 * Where this distribution of VATprism is hosted. Releases, issues and documentation are linked relative to it.
 */
public final class ProjectLinks {
    public static final String GITHUB_REPOSITORY = "matthew-mcewen/vatprism";

    public static final String REPOSITORY = "https://github.com/" + GITHUB_REPOSITORY;
    public static final String ISSUES = REPOSITORY + "/issues";
    public static final String RELEASES = REPOSITORY + "/releases";

    private ProjectLinks() {
        throw new AssertionError("No instances of utility class " + ProjectLinks.class);
    }
}
