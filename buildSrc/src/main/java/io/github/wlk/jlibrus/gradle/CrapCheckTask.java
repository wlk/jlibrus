package io.github.wlk.jlibrus.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

/**
 * Fails the build when any method's CRAP score reaches the threshold (ub-mode: CRAP &lt; 10).
 */
public abstract class CrapCheckTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getJacocoXml();

    @Input
    public abstract Property<Double> getThreshold();

    @Input
    public abstract ListProperty<String> getExcludedClassPrefixes();

    @TaskAction
    public void check() throws IOException {
        List<MethodCrap> methods;
        try (InputStream in = Files.newInputStream(getJacocoXml().get().getAsFile().toPath())) {
            methods = CrapCalculator.fromJacocoXml(in);
        }
        List<String> excluded = getExcludedClassPrefixes().get();
        List<MethodCrap> offenders = CrapCalculator.offenders(methods, getThreshold().get()).stream()
                .filter(m -> excluded.stream().noneMatch(m.className()::startsWith))
                .toList();
        getLogger().lifecycle("CRAP check: {} methods analysed, {} at or above {}",
                methods.size(), offenders.size(), getThreshold().get());
        offenders.forEach(m -> getLogger().error("  " + m.describe()));
        if (!offenders.isEmpty()) {
            throw new GradleException("CRAP threshold exceeded by " + offenders.size() + " method(s)");
        }
    }
}
