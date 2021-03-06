package dev.jeka.core.api.tooling.eclipse;

import dev.jeka.core.api.depmanagement.JkJavaDepScopes;
import dev.jeka.core.api.depmanagement.JkScope;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.file.JkPathTreeSet;
import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsString;
import dev.jeka.core.api.utils.JkUtilsXml;
import dev.jeka.core.tool.JkOptions;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

final class DotClasspathModel {

    private static final String OPTION_VAR_PREFIX = "eclipse.var.";

    static final String CLASSPATHENTRY = "classpathentry";

    static final String JEKA_HOME = "JEKA_HOME";

    static final String JEKA_USER_HOME= "JEKA_USER_HOME";

    private final List<ClasspathEntry> classpathentries = new LinkedList<>();

    private DotClasspathModel(List<ClasspathEntry> classpathentries) {
        this.classpathentries.addAll(classpathentries);
    }

    static DotClasspathModel from(Path dotClasspathFile) {
        final Document document = JkUtilsXml.documentFrom(dotClasspathFile.toFile());
        return from(document);
    }

    static DotClasspathModel from(Document document) {
        final NodeList nodeList = document.getElementsByTagName(CLASSPATHENTRY);
        final List<ClasspathEntry> classpathEntries = new LinkedList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            final Node node = nodeList.item(i);
            final Element element = (Element) node;
            classpathEntries.add(ClasspathEntry.from(element));
        }
        return new DotClasspathModel(classpathEntries);
    }

    public Sources sourceDirs(Path baseDir, Sources.TestSegregator segregator) {
        final List<JkPathTree> prods = new LinkedList<>();
        final List<JkPathTree> tests = new LinkedList<>();
        for (final ClasspathEntry classpathEntry : classpathentries) {
            if (classpathEntry.kind.equals(ClasspathEntry.Kind.SRC) && !classpathEntry.isOptional()) {
                if (segregator.isTest(classpathEntry.path)) {
                    tests.add(classpathEntry.srcAsJkDir(baseDir));
                } else {
                    prods.add(classpathEntry.srcAsJkDir(baseDir));
                }
            }
        }
        return new Sources(JkPathTreeSet.of(prods), JkPathTreeSet.of(tests));
    }

    public List<Lib> libs(Path baseDir, ScopeResolver scopeResolver) {
        final List<Lib> result = new LinkedList<>();
        final Map<String, Path> projects = JkEclipseProjectGenerator.findProjectPath(baseDir.getParent());
        for (final ClasspathEntry classpathEntry : classpathentries) {

            if (classpathEntry.kind.equals(ClasspathEntry.Kind.CON)) {
                final JkScope scope = scopeResolver.scopeOfCon(classpathEntry.path);
                if (classpathEntry.path.startsWith(ClasspathEntry.JRE_CONTAINER_PREFIX)) {
                    continue;
                }
                for (final Path file : classpathEntry.conAsFiles()) {
                    result.add(Lib.file(file, scope, classpathEntry.exported));
                }

            } else if (classpathEntry.kind.equals(ClasspathEntry.Kind.LIB)) {
                final JkScope scope = scopeResolver.scopeOfLib(ClasspathEntry.Kind.LIB,
                        classpathEntry.path);
                result.add(Lib.file(classpathEntry.libAsFile(baseDir, projects), scope,
                        classpathEntry.exported));

            } else if (classpathEntry.kind.equals(ClasspathEntry.Kind.VAR)) {
                final String var = JkUtilsString.substringBeforeFirst(classpathEntry.path, "/");
                final String varFile;
                if (JEKA_HOME.equals(var)) {
                    varFile = JkLocator.getJekaHomeDir().toAbsolutePath().normalize().toString();
                } else if (JEKA_USER_HOME.equals(var)) {
                    varFile = JkLocator.getJekaUserHomeDir().normalize().toString();
                } else {
                    final String optionName = OPTION_VAR_PREFIX + var;
                    varFile = JkOptions.get(optionName);
                    if (varFile == null) {
                        throw new JkException(
                                "No option found with name "
                                        + optionName
                                        + ". It is needed in order to build this project as it is mentionned andAccept Eclipse .classpath."
                                        + " Please set this option either in command line as -"
                                        + optionName
                                        + "=/absolute/path/for/this/var or in [jeka_home]/options.properties");
                    }
                }

                final Path file = Paths.get(varFile).resolve(JkUtilsString.substringAfterFirst(
                        classpathEntry.path, "/"));
                if (!Files.exists(file)) {
                    JkLog.warn("Can't find Eclipse classpath entry : " + file.toAbsolutePath());
                }
                final JkScope scope = scopeResolver.scopeOfLib(ClasspathEntry.Kind.VAR, classpathEntry.path);
                result.add(Lib.file(file, scope, classpathEntry.exported));

            } else if (classpathEntry.kind.equals(ClasspathEntry.Kind.SRC)) {
                if (classpathEntry.isProjectSrc()) {
                    final String projectPath = classpathEntry.projectRelativePath(baseDir, projects);
                    result.add(Lib.project(projectPath, JkJavaDepScopes.COMPILE,
                            classpathEntry.exported));
                }

            }
        }
        return result;
    }

    static class ClasspathEntry {

        final static String JRE_CONTAINER_PREFIX = "org.eclipse.jdt.launching.JRE_CONTAINER";

        enum Kind {
            SRC, CON, LIB, VAR, OUTPUT, UNKNOWN
        }

        private final Kind kind;

        private final boolean exported;

        private final String path;

        private final String excluding;

        private final String including;

        private final Map<String, String> attributes = new HashMap<>();

        ClasspathEntry(Kind kind, String path, String excluding, String including,
                boolean exported) {
            super();
            this.kind = kind;
            this.path = path;
            this.excluding = excluding;
            this.including = including;
            this.exported = exported;
        }

        static ClasspathEntry of(Kind kind, String path) {
            return new ClasspathEntry(kind, path, null, null, false);
        }

        static ClasspathEntry from(Element classpathEntryEl) {
            final String kindString = classpathEntryEl.getAttribute("kind");
            final String path = classpathEntryEl.getAttribute("path");
            final String including = classpathEntryEl.getAttribute("including");
            final String excluding = classpathEntryEl.getAttribute("excluding");
            final Kind kind;
            if ("lib".equals(kindString)) {
                kind = Kind.LIB;
            } else if ("con".equals(kindString)) {
                kind = Kind.CON;
            } else if ("src".equals(kindString)) {
                kind = Kind.SRC;
            } else if ("var".equals(kindString)) {
                kind = Kind.VAR;
            } else if ("output".equals(kindString)) {
                kind = Kind.OUTPUT;
            } else {
                kind = Kind.UNKNOWN;
            }
            final String exportedString = classpathEntryEl.getAttribute("exported");
            final boolean export = "true".equals(exportedString);
            final ClasspathEntry result = new ClasspathEntry(kind, path, excluding, including,
                    export);
            final NodeList nodeList = classpathEntryEl.getElementsByTagName("attributes");
            for (int i = 0; i < nodeList.getLength(); i++) {
                final Element attributeEl = (Element) nodeList.item(i);
                final String name = attributeEl.getAttribute("name");
                final String value = attributeEl.getAttribute("value");
                result.attributes.put(name, value);
            }
            return result;
        }

        JkPathTree srcAsJkDir(Path baseDir) {
            if (!this.kind.equals(Kind.SRC)) {
                throw new IllegalStateException(
                        "Can only get source dir to classpath entry of kind 'src'.");
            }
            final Path dir = baseDir.resolve(path);
            JkPathTree jkFileTree = JkPathTree.of(dir);
            if (!excluding.isEmpty()) {
                final String[] patterns = excluding.split("\\|");
                jkFileTree = jkFileTree.andMatching(false, patterns);
            }
            if (!including.isEmpty()) {
                final String[] patterns = including.split("\\|");
                jkFileTree = jkFileTree.andMatching(true, patterns);
            }
            return jkFileTree;
        }

        boolean isOptional() {
            return "true".equals(this.attributes.get("optional"));
        }

        public boolean sameTypeAndPath(ClasspathEntry other) {
            if (!this.kind.equals(other.kind)) {
                return false;
            }
            return this.path.equals(other.path);
        }

        List<Path> conAsFiles() {
            if (!this.kind.equals(Kind.CON)) {
                throw new IllegalStateException(
                        "Can only get files to classpath entry of kind 'con'.");
            }
            if (!Files.exists(Lib.CONTAINER_DIR) && !Files.exists(Lib.CONTAINER_USER_DIR)) {
                JkLog.warn("Eclipse containers directory " + Lib.CONTAINER_USER_DIR
                        + " or  " + Lib.CONTAINER_DIR + " does not exists... Ignore");
                return Collections.emptyList();
            }
            final String folderName = path.replace('/', '_').replace('\\', '_');
            Path conFolder = Lib.CONTAINER_USER_DIR.resolve(folderName);
            if (!Files.exists(conFolder)) {
                conFolder = Lib.CONTAINER_DIR.resolve(folderName);
                if (!Files.exists(conFolder)) {
                    JkLog.warn("Eclipse containers directory " + conFolder + " or "
                            + Lib.CONTAINER_USER_DIR.resolve(folderName)
                            + "  do not exists... ignogre.");
                    return Collections.emptyList();
                }
            }
            final JkPathTree dirView = JkPathTree.of(conFolder).andMatching(true, "**.jar");
            final List<Path> result = new LinkedList<>();
            result.addAll(dirView.getFiles());
            return result;
        }

        Path libAsFile(Path baseDir, Map<String, Path> projectLocationMap) {
            final String pathInProject;
            final Path pathAsFile = Paths.get(path);
            if (pathAsFile.isAbsolute() && Files.exists(pathAsFile)) {
                return pathAsFile;
            }
            if (path.startsWith("/")) {
                final int secondSlashIndex = path.indexOf("/", 1);
                pathInProject = path.substring(secondSlashIndex + 1);
                final Path otherProjectDir = projectLocation(baseDir.getParent(),
                        projectLocationMap);
                return otherProjectDir.resolve(pathInProject);
            }
            return baseDir.resolve(path);
        }

        boolean isProjectSrc() {
            return path.startsWith("/");
        }

        String projectRelativePath(Path baseDir, Map<String, Path> projectLocationMap) {
            final Path projectDir = projectLocation(baseDir.getParent(), projectLocationMap);
            return baseDir.relativize(projectDir).toString();
        }

        private Path projectLocation(Path parent, Map<String, Path> projectLocationMap) {
            final int secondSlashIndex = path.indexOf("/", 1);
            final String projectName;
            if (secondSlashIndex == -1) {
                projectName = path.substring(1);
            } else {
                projectName = path.substring(1, secondSlashIndex);
            }
            final Path otherProjectDir = projectLocationMap.get(projectName);
            if (otherProjectDir == null) {
                throw new IllegalStateException(parent + File.separator + projectName + " is not an Eclipse project (.classpath is missing).");
            }
            return otherProjectDir;
        }

    }

}