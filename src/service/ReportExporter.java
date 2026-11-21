package service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

public class ReportExporter {
    private final Path folder;

    public ReportExporter(String folderName) {
        this.folder = Paths.get(folderName);
    }

    // Writes the report to <folder>/report-<date>.txt and returns the full path of the file
    public Path export(String text, LocalDate date) throws IOException {
        Files.createDirectories(folder);
        Path file = folder.resolve("report-" + date + ".txt");

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(text);
        }   // closed automatically, even if an error happens

        return file.toAbsolutePath();
    }
}