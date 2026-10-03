package com.annaschneider.minecraft1.video.voice;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * A verified voice model from the bundled catalog that can be installed on request. Sizes and checksums come from the
 * published model metadata and are checked after downloading.
 *
 * @param modelId  model id = file name stem (e.g. {@code en_US-arctic-medium})
 * @param name     dataset name (e.g. {@code arctic})
 * @param language language code such as {@code en_US} or {@code vi_VN}
 * @param engine   engine that speaks the model ({@code piper})
 * @param quality  quality tier ({@code x_low}, {@code low}, {@code medium}, {@code high}) or empty
 * @param files    model ({@code .onnx}) and config ({@code .onnx.json}) files
 * @param speakers speakers in index order; empty for a single-speaker model
 * @param baseUrl  HTTPS folder the file paths are relative to
 */
public record DownloadableVoice(String modelId, String name, String language, String engine, String quality,
                                List<CatalogFile> files, List<VoiceSpeaker> speakers, URI baseUrl) {
    public DownloadableVoice {
        Objects.requireNonNull(modelId, "modelId");
        files = List.copyOf(files);
        speakers = List.copyOf(speakers);
        quality = quality == null ? "" : quality;
    }

    /** One file of a model with its published size and MD5 digest. */
    public record CatalogFile(String path, long size, String md5) {
        public String fileName() {
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    public long totalBytes() {
        return files.stream().mapToLong(CatalogFile::size).sum();
    }

    public URI url(CatalogFile file) {
        return URI.create(baseUrl.toString() + file.path());
    }

    /** Display name such as {@code Arctic medium}. */
    public String displayName() {
        String base = name.isEmpty() ? modelId : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return quality.isEmpty() ? base : base + " " + quality;
    }
}
