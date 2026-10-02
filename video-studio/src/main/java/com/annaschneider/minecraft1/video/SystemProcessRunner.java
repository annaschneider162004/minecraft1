package com.annaschneider.minecraft1.video;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** {@link ProcessRunner} using {@link ProcessBuilder}: arguments are passed as a list, never through a shell. */
public final class SystemProcessRunner implements ProcessRunner {
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    @Override
    public Result run(List<String> command, String stdin, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        Process process = builder.start();
        TailBuffer output = new TailBuffer();
        Thread reader = new Thread(() -> copy(process.getInputStream(), output), "process-output");
        reader.setDaemon(true);
        reader.start();
        try (OutputStream in = process.getOutputStream()) {
            if (stdin != null) {
                in.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // the program may exit without reading its input; its output explains why
        }
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw new IOException(command.get(0) + " did not finish within " + timeout.toSeconds() + " s");
        }
        reader.join(2_000);
        return new Result(process.exitValue(), output.text());
    }

    private static void copy(InputStream stream, TailBuffer output) {
        byte[] buffer = new byte[8192];
        try (stream) {
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                output.write(buffer, read);
            }
        } catch (IOException ignored) {
            // process ended
        }
    }

    /** Keeps only the last {@link #MAX_OUTPUT_BYTES} bytes so chatty tools cannot exhaust memory. */
    private static final class TailBuffer {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        synchronized void write(byte[] data, int length) {
            bytes.write(data, 0, length);
            if (bytes.size() > MAX_OUTPUT_BYTES * 2) {
                byte[] all = bytes.toByteArray();
                bytes.reset();
                bytes.write(all, all.length - MAX_OUTPUT_BYTES, MAX_OUTPUT_BYTES);
            }
        }

        synchronized String text() {
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
