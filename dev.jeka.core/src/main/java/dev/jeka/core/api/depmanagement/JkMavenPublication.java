package dev.jeka.core.api.depmanagement;

import dev.jeka.core.api.utils.JkUtilsAssert;
import dev.jeka.core.api.utils.JkUtilsIterable;
import dev.jeka.core.api.utils.JkUtilsPath;
import dev.jeka.core.api.utils.JkUtilsString;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Publication specific information to include in POM file in order to be published of a Maven repository.
 * These information contains : <ul>
 *   <li>The artifacts to be published (main artifact and artifacts with classifiers)</li>
 *   <li>Information about describing the project as some public repositories require</li>
 * </ul>
 */
public final class JkMavenPublication {

    private final List<JkClassifiedFileArtifact> classifiedArtifacts;

    private final List<File> mainArtifacts; // can't have 2 artifacts with same getExtension

    private final JkMavenPublicationInfo extraInfo;

    private JkMavenPublication(List<File> mainArtifacts, List<JkClassifiedFileArtifact> classified,
            JkMavenPublicationInfo extraInfo) {
        super();
        this.mainArtifacts = mainArtifacts;
        this.classifiedArtifacts = classified;
        this.extraInfo = extraInfo;
    }

    /**
     * Creates a Maven publication specifying the file to publish as main artifact.
     */
    public static JkMavenPublication of(Path file) {
        return new JkMavenPublication(JkUtilsIterable.listOf(file.toFile()),
                Collections.emptyList(), null);
    }

    /**
     * Creates a Maven publication to publish all artifacts referenced in the specified artifact locator.
     */
    public static JkMavenPublication of(JkArtifactLocator artifactLocator, Set<JkArtifactId> excludedArtifacts) {
        JkMavenPublication result = JkMavenPublication.of(artifactLocator.getArtifactPath(artifactLocator.getMainArtifactId()));
        for (final JkArtifactId artifactFileId : artifactLocator.getArtifactIds()) {
            if (excludedArtifacts.contains(artifactFileId) || artifactFileId.getClassifier() == null) {
                continue;
            }
            final Path file = artifactLocator.getArtifactPath(artifactFileId);
            result = result.andOptional(file, artifactFileId.getClassifier());
        }
        return result;
    }

    /**
     * Returns a {@link JkMavenPublication} identical to this one but adding a classified artifact.
     */
    public JkMavenPublication and(Path file, String classifier) {
        JkUtilsAssert.isTrue(!JkUtilsString.isBlank(classifier), "classifier can not be empty. Use JkMavenPublication#of " +
                "for creating a publication with the main artifact.");
        final String fileExt = JkUtilsString.substringAfterLast(file.getFileName().toString(), ".");
        if (JkUtilsString.isBlank(fileExt)) {
            throw new IllegalArgumentException("File " + file
                    + " must have an getExtension (as .jar, .zip, ...");
        }
        if (contains(fileExt, classifier)) {
            throw new IllegalArgumentException(
                    "Can't add artifact with getExtension/classifier equals to [" + fileExt + "/"
                            + classifier
                            + "] as this combination is yet present in this publication " + this);
        }
        final JkClassifiedFileArtifact artifact = new JkClassifiedFileArtifact(classifier, file);
        final List<JkClassifiedFileArtifact> list = new LinkedList<>(
                this.classifiedArtifacts);
        list.add(artifact);
        return new JkMavenPublication(this.mainArtifacts, list, this.extraInfo);
    }


    private boolean contains(String ext, String classifier) {
        for (final JkClassifiedFileArtifact classifiedArtifact : this.classifiedArtifacts) {
            if (classifier.equals(classifiedArtifact.classifier) && classifiedArtifact.getExtension().equals(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a new publication based on this one but with the specified
     * publication extra infoString required to publish on Maven central repository.
     */
    public JkMavenPublication with(JkMavenPublicationInfo extraInfo) {
        return new JkMavenPublication(this.mainArtifacts, this.classifiedArtifacts, extraInfo);
    }

    /**
     * Returns a new publication based on this one but with the specified signer to sign published artifacts.
     */
    public JkMavenPublication withSigner(UnaryOperator<Path> signer) {
        return new JkMavenPublication(this.mainArtifacts, this.classifiedArtifacts, this.extraInfo);
    }

    /**
     * Returns a new publication based on this one but with the specified signer to sign published artifacts.
     */
    public JkMavenPublication withChecksums(Set<String> checksumAlgos) {
        return new JkMavenPublication(this.mainArtifacts, this.classifiedArtifacts, this.extraInfo);
    }

    /**
     * @see #withChecksums(Set)
     */
    public JkMavenPublication withChecksums(String ... algos) {
        return new JkMavenPublication(this.mainArtifacts, this.classifiedArtifacts, this.extraInfo);
    }

    /**
     * Same as {@link #and(Path, String)} but effective only if the specified file exists.
     * If not the case, this method returns this object.
     */
    public JkMavenPublication andOptional(Path file, String classifier) {
        if (Files.exists(file)) {
            return and(file, classifier);
        }
        return this;
    }

    /**
     * Same as {@link #andOptional(Path, String)}  but effective only if the specified condition is <code>true</code>
     */
    public JkMavenPublication andOptionalIf(boolean conditional, Path file, String classifier) {
        if (conditional) {
            return andOptional(file, classifier);
        }
        return this;
    }

    /** Files constituting main artifact */
    public List<Path> getMainArtifactFiles() {
        return JkUtilsPath.toPaths(this.mainArtifacts);
    }

    /** Files constituting classified artifacts */
    public List<JkClassifiedFileArtifact> getClassifiedArtifacts() {
        return Collections.unmodifiableList(classifiedArtifacts);
    }

    /**  */
    public JkMavenPublicationInfo getExtraInfo() {
        return this.extraInfo;
    }

    @Override
    public String toString() {
        return mainArtifacts.toString() + " / " + classifiedArtifacts.toString();
    }

    List<Path> missingFiles() {
        final List<Path> result = new LinkedList<>();
        for (final File file : this.mainArtifacts) {
            if (!file.exists()) {
                result.add(file.toPath());
            }
        }
        for (final JkClassifiedFileArtifact classifiedFileArtifact : this.classifiedArtifacts) {
            if (!Files.exists(classifiedFileArtifact.file)) {
                result.add(classifiedFileArtifact.file);
            }
        }
        return result;
    }

    /**
     * An artifact with a classifier for Maven repository.
     */
    public static class JkClassifiedFileArtifact {

        private final String classifier;
        private final Path file;

        JkClassifiedFileArtifact(String classifier, Path file) {
            super();
            this.classifier = classifier;
            this.file = file;
        }

        /** Classifier string for this classified artifact */
        public String getClassifier() {
            return classifier;
        }

        /** File for this classified artifact */
        public Path getFile() {
            return file;
        }

        /** File getExtension */
        public String getExtension() {
            return JkUtilsString.substringAfterLast(file.getFileName().toString(), ".");
        }

    }





}
