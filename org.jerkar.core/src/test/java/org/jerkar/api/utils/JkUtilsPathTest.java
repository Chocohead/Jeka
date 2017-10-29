package org.jerkar.api.utils;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.junit.Test;


@SuppressWarnings("javadoc")
public class JkUtilsPathTest {

    @Test
    public void testCopyDir() throws Exception {
        final URL sampleFileUrl = JkUtilsPathTest.class
                .getResource("samplefolder/subfolder/sample.txt");
        final Path source = Paths.get(sampleFileUrl.toURI()).getParent().getParent();
        Files.createDirectories(source.resolve("emptyfolder"));   // git won't copy empty dir
        final Path target = Files.createTempDirectory("copydirtest");
        JkUtilsPath.copyDirContent(source, target, path -> true, StandardCopyOption.REPLACE_EXISTING);
        System.out.println(target);
        assertTrue(Files.exists(target.resolve("subfolder/sample.txt")));
        assertTrue(Files.exists(target.resolve("emptyfolder")));
        assertTrue(Files.isDirectory(target.resolve("emptyfolder")));

        final Path subfolder = Paths.get(sampleFileUrl.toURI()).getParent();
        final Path target2 = Files.createTempDirectory("copydirtest");
        JkUtilsPath.copyDirContent(subfolder, target2, path -> true, StandardCopyOption.REPLACE_EXISTING);
        System.out.println(target2);
        assertTrue(Files.exists(target2.resolve("sample.txt")));

    }

    @Test
    public void testZipRoot() throws IOException {
        Path zipPath = JkUtilsPath.zipRoot(Paths.get("toto.zip"));
        System.out.println(zipPath);
        Path dirWithSpaces = Files.createTempDirectory("folder name with space");
        Path otherPath = dirWithSpaces.resolve("toto.zip");
        System.out.println(otherPath.toUri());
        JkUtilsPath.zipRoot(otherPath);
    }


}
