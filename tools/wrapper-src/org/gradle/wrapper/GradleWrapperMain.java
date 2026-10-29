package org.gradle.wrapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal self-contained Gradle distribution bootstrap for this generated project.
 * Included because the standard Gradle wrapper binary cannot be fetched in the
 * artifact-creation environment. It supports the normal gradlew/gradlew.bat commands.
 * Not affiliated with or supplied by the Gradle project.
 */
public final class GradleWrapperMain {
    private GradleWrapperMain() { }

    public static void main(String[] args) throws Exception {
        Path currentJar = Paths.get(GradleWrapperMain.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path root = currentJar.getParent().getParent().getParent();
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(
                root.resolve("gradle/wrapper/gradle-wrapper.properties"))) {
            properties.load(input);
        }
        String address = properties.getProperty("distributionUrl", "");
        if (!address.startsWith("https://services.gradle.org/distributions/gradle-")) {
            throw new IOException("Unsupported Gradle distribution: " + address);
        }
        String filename = address.substring(address.lastIndexOf('/') + 1);
        String version = filename.replace("-bin.zip", "").replace("-all.zip", "");
        Path home = Paths.get(System.getProperty("user.home"), ".gradle", "glowdeck-bootstrap", version);
        Path install = home.resolve(version);
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path executable = install.resolve("bin").resolve(windows ? "gradle.bat" : "gradle");
        if (!Files.isRegularFile(executable)) {
            Files.createDirectories(home);
            Path zip = home.resolve(filename);
            if (!Files.isRegularFile(zip) || Files.size(zip) < 100_000) {
                System.out.println("Downloading " + filename + " (first build only)…");
                Path temp = home.resolve(filename + ".part");
                HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(30_000);
                connection.setReadTimeout(120_000);
                connection.setRequestProperty("User-Agent", "GlowDeckTV-GradleBootstrap/1.0");
                try (InputStream stream = connection.getInputStream(); OutputStream out = Files.newOutputStream(temp)) {
                    byte[] buf = new byte[64 * 1024];
                    int size;
                    while ((size = stream.read(buf)) >= 0) out.write(buf, 0, size);
                } finally { connection.disconnect(); }
                Files.move(temp, zip, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("Extracting Gradle distribution…");
            try (ZipInputStream stream = new ZipInputStream(Files.newInputStream(zip))) {
                ZipEntry entry;
                byte[] buf = new byte[64 * 1024];
                while ((entry = stream.getNextEntry()) != null) {
                    Path destination = home.resolve(entry.getName()).normalize();
                    if (!destination.startsWith(home)) throw new IOException("Unsafe ZIP entry");
                    if (entry.isDirectory()) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        try (OutputStream out = Files.newOutputStream(destination)) {
                            int size;
                            while ((size = stream.read(buf)) >= 0) out.write(buf, 0, size);
                        }
                    }
                    stream.closeEntry();
                }
            }
        }
        if (!Files.isRegularFile(executable)) throw new IOException("Gradle not extracted: " + executable);
        if (!windows) executable.toFile().setExecutable(true);
        List<String> command = new ArrayList<>();
        if (windows) { command.add("cmd.exe"); command.add("/c"); }
        command.add(executable.toString());
        for (String arg : args) command.add(arg);
        Process process = new ProcessBuilder(command).directory(root.toFile()).inheritIO().start();
        System.exit(process.waitFor());
    }
}
