package net.marvk.fs.vatsim.map.configuration;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import net.marvk.fs.vatsim.map.ProjectLinks;
import net.marvk.fs.vatsim.map.api.GithubVatprismApi;
import net.marvk.fs.vatsim.map.api.VatprismApi;

import java.time.Duration;

public class ApiModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(VatprismApi.class).to(GithubVatprismApi.class).in(Singleton.class);
        bind(String.class).annotatedWith(Names.named("githubRepository")).toInstance(ProjectLinks.GITHUB_REPOSITORY);
        bind(Duration.class).annotatedWith(Names.named("versionApiTimeout")).toInstance(Duration.ofMillis(2500));
    }
}
