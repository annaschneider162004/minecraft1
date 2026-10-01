package com.annaschneider.minecraft1.link;

/**
 * A message from the mod. Optional parts are {@code null} and omitted on the wire.
 *
 * @param id      id of the answered request ({@code kind=response} only)
 * @param ok      whether the request succeeded ({@code kind=response} only)
 * @param message user-facing text (result, error or log line)
 * @param server  capabilities ({@code hello}, {@code status})
 * @param job     current or last job of the player ({@code status}, {@code build}, {@code pause}, ..., progress events)
 * @param plan    preview ({@code plan}, {@code preview})
 * @param source  {@code uploads/...} reference of a stored image ({@code upload_image})
 */
public record LinkMessage(
    int v,
    MessageKind kind,
    String id,
    Boolean ok,
    String message,
    ServerInfo server,
    JobStatus job,
    PlanSummary plan,
    String source,
    RecordingStatus recording
) {
    public LinkMessage(int v, MessageKind kind, String id, Boolean ok, String message, ServerInfo server, JobStatus job, PlanSummary plan, String source) {
        this(v, kind, id, ok, message, server, job, plan, source, null);
    }

    public static LinkMessage ok(String id, String message) {
        return new LinkMessage(LinkProtocol.VERSION, MessageKind.RESPONSE, id, true, message, null, null, null, null, null);
    }

    public static LinkMessage error(String id, String message) {
        return new LinkMessage(LinkProtocol.VERSION, MessageKind.RESPONSE, id, false, message, null, null, null, null, null);
    }

    public static LinkMessage progress(JobStatus job) {
        return new LinkMessage(LinkProtocol.VERSION, MessageKind.PROGRESS, null, null, job.describe(), null, job, null, null, null);
    }

    public static LinkMessage log(String message) {
        return new LinkMessage(LinkProtocol.VERSION, MessageKind.LOG, null, null, message, null, null, null, null, null);
    }

    public boolean isOk() {
        return Boolean.TRUE.equals(ok);
    }

    public LinkMessage withServer(ServerInfo value) {
        return new LinkMessage(v, kind, id, ok, message, value, job, plan, source, recording);
    }

    public LinkMessage withJob(JobStatus value) {
        return new LinkMessage(v, kind, id, ok, message, server, value, plan, source, recording);
    }

    public LinkMessage withPlan(PlanSummary value) {
        return new LinkMessage(v, kind, id, ok, message, server, job, value, source, recording);
    }

    public LinkMessage withSource(String value) {
        return new LinkMessage(v, kind, id, ok, message, server, job, plan, value, recording);
    }

    public LinkMessage withRecording(RecordingStatus value) {
        return new LinkMessage(v, kind, id, ok, message, server, job, plan, source, value);
    }
}
