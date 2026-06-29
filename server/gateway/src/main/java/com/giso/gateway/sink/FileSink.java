package com.giso.gateway.sink;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;

/** 本地 JSONL 出口：按天滚动（events_raw-2026-06-10.jsonl），适合本地开发与小流量兜底。 */
public final class FileSink implements EventSink {
    private final Path dir;
    private LocalDate curDate;
    private BufferedWriter raw;
    private BufferedWriter quarantine;

    public FileSink(Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir);
        roll();
    }

    @Override
    public synchronized void accept(ObjectNode event, boolean q) {
        try {
            if (!LocalDate.now().equals(curDate)) roll();
            BufferedWriter w = q ? quarantine : raw;
            w.write(event.toString());
            w.newLine();
            w.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void roll() throws IOException {
        if (raw != null) raw.close();
        if (quarantine != null) quarantine.close();
        curDate = LocalDate.now();
        raw = open("events_raw-" + curDate + ".jsonl");
        quarantine = open("events_quarantine-" + curDate + ".jsonl");
    }

    private BufferedWriter open(String name) throws IOException {
        return Files.newBufferedWriter(dir.resolve(name), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Override
    public String name() { return "file(" + dir + ")"; }

    @Override
    public synchronized void close() {
        try {
            if (raw != null) raw.close();
            if (quarantine != null) quarantine.close();
        } catch (IOException ignored) { }
    }
}
