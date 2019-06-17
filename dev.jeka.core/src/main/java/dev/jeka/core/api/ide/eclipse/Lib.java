package dev.jeka.core.api.ide.eclipse;

import java.nio.file.Path;

import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkScope;
import dev.jeka.core.api.java.project.JkJavaProject;
import dev.jeka.core.api.system.JkLocator;


class Lib {

    private static final String CONTAINERS_PATH = "eclipse/containers";

    static final Path CONTAINER_DIR = JkLocator.getJekaHomeDir().resolve(CONTAINERS_PATH);

    static final Path CONTAINER_USER_DIR = JkLocator.getJekaUserHomeDir().resolve(CONTAINERS_PATH);

    public static Lib file(Path file, JkScope scope, boolean exported) {
        return new Lib(file, null, scope, exported);
    }

    public static Lib project(String project, JkScope scope, boolean exported) {
        return new Lib(null, project, scope, exported);
    }

    public final Path file;

    public final String projectRelativePath;

    public final JkScope scope;

    public final boolean exported;

    private Lib(Path file, String projectRelativePath, JkScope scope, boolean exported) {
        super();
        this.file = file;
        this.scope = scope;
        this.projectRelativePath = projectRelativePath;
        this.exported = exported;
    }

    @Override
    public String toString() {
        return scope + ":" + file == null ? projectRelativePath : file.toString();
    }

    public static JkDependencySet toDependencies(Path parentDir, Iterable<Lib> libs, JkEclipseClasspathApplier applier) {
        JkDependencySet result = JkDependencySet.of();
        for (final Lib lib : libs) {
            if (lib.projectRelativePath == null) {
                result = result.andFile(lib.file, lib.scope);

            } else { // This is a dependency on an eclipse project
                final Path projectDir = parentDir.resolve(lib.projectRelativePath);
                final JkJavaProject project = JkJavaProject.ofMavenLayout(projectDir);
                applier.apply(project);
                result = result.and(project.getMaker(), lib.scope);
            }
        }
        return result;
    }

}