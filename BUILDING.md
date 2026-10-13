# Building, running and releasing

This is a fork of [VATprism](https://github.com/marvk/vatprism) which is distributed as **MattPrism**. It installs
next to the original VATprism and shares its settings.

## Requirements

| Tool                                                   | Version | Needed for                        |
|:-------------------------------------------------------|:--------|:----------------------------------|
| [JDK](https://adoptium.net/temurin/releases/?version=17) | 17      | Everything                        |
| [Maven](https://maven.apache.org/download.cgi)         | 3.9+    | Everything                        |
| [WiX Toolset](https://github.com/wixtoolset/wix3/releases) | 3.11+ (not 4 or later) | Building the Windows installer |

`java`, `mvn` and, for the installer, the WiX `candle` and `light` have to be on the `PATH`. All libraries are
downloaded by Maven, nothing has to be installed by hand.

## Run from source

```bash
mvn compile exec:java
```

## Run the tests

```bash
mvn test
```

## Build the installer

```bash
mvn clean verify
```

This runs the tests and writes the installer for the system it runs on to `target-installer`: an `.msi` on Windows, a
`.dmg` on macOS. Building for another system is not possible, use the release workflow for that. The runnable jar
which the installer is built from is written to `target-fat-jar`.

Without the WiX Toolset the last step fails on Windows. To build only the jar and run it with your own Java:

```bash
mvn clean package -Dexec.skip
```

```bash
java -jar target-fat-jar/vatsim-map-0.4.0-fat.jar
```

## Publish a release

Releases are built by GitHub Actions when a version tag is pushed.

1. Move the entries under `Unreleased` in `CHANGELOG.md` to a heading for the new version and commit.
2. Tag the commit and push the tag:

   ```bash
   git tag v0.4.0
   ```

   ```bash
   git push origin master v0.4.0
   ```

3. The [Release workflow](.github/workflows/release.yml) builds the installers and publishes them on the
   [releases page](https://github.com/matthew-mcewen/vatprism/releases), which takes about ten minutes.

The version of the application is taken from the tag. A tag with a suffix, such as `v0.5.0-beta1`, is published as a
prerelease, which is only offered to users who enabled *Prerelease Updates* in the settings. The macOS build is best
effort: if it fails, the release is published with the Windows installer only.

Every push to `master` is also built and tested by the [CI workflow](.github/workflows/ci.yml). Its installer can be
downloaded from the workflow run, which is useful for testing before tagging. Add `#no-build` to a commit message to
skip it.

The running application looks for newer releases of this repository at startup and offers to download them.

## Installing

Download the installer from the [releases page](https://github.com/matthew-mcewen/vatprism/releases/latest).

**Windows**: run the `.msi`. The installer is not signed, so Windows SmartScreen shows a warning; choose *More info*,
then *Run anyway*. A newer installer replaces an older MattPrism.

**macOS**: open the `.dmg` and drag the application to *Applications*. The application is not signed. If macOS
reports that it is damaged or can not be opened, run:

```bash
xattr -cr "/Applications/MattPrism.app"
```

MattPrism can be installed next to the original VATprism. Both read their settings from the same directory, see
the [usage guide](docs/usage-guide.md#configuration-directories), so settings carry over in both directions.

## Changing the identity of the application

The name, vendor and identifiers of the installed application are properties at the top of `pom.xml`. Do not change
`winUpgradeUuid` once a release is out: Windows uses it to recognise an installer as an update of an installed
version. The repository which is checked for updates and linked from the application is set in
`src/main/java/net/marvk/fs/vatsim/map/ProjectLinks.java`.

## Licenses

VATprism is licensed under the [GNU AGPLv3](LICENSE). Anyone you distribute it to is entitled to the source of that
exact version, which is covered by publishing releases from a public repository.

The airspace data is not part of the application or its installer, it is downloaded by the application:

| Data                                                                                | License         |
|:------------------------------------------------------------------------------------|:----------------|
| [VATGlasses](https://github.com/lennycolton/vatglasses-data)                        | CC BY-NC-SA 4.0 |
| [SimAware TRACON Project](https://github.com/vatsimnetwork/simaware-tracon-project) | CC BY-SA 4.0    |
