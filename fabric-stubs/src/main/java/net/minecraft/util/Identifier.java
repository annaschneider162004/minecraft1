package net.minecraft.util;

import java.util.Objects;

public final class Identifier implements Comparable<Identifier> {
    public static final char NAMESPACE_SEPARATOR = ':';
    public static final String DEFAULT_NAMESPACE = "minecraft";

    private final String namespace;
    private final String path;

    public Identifier(String namespace, String path) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.path = Objects.requireNonNull(path, "path");
    }

    public Identifier(String id) {
        int colon = id.indexOf(NAMESPACE_SEPARATOR);
        if (colon >= 0) {
            this.namespace = id.substring(0, colon);
            this.path = id.substring(colon + 1);
        } else {
            this.namespace = DEFAULT_NAMESPACE;
            this.path = id;
        }
    }

    public static Identifier of(String namespace, String path) {
        return new Identifier(namespace, path);
    }

    public static Identifier tryParse(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        int colon = id.indexOf(NAMESPACE_SEPARATOR);
        String ns = colon >= 0 ? id.substring(0, colon) : DEFAULT_NAMESPACE;
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        if (!isValidNamespace(ns) || !isValidPath(path)) {
            return null;
        }
        return new Identifier(ns, path);
    }

    public static boolean isValidNamespace(String namespace) {
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.')) {
                return false;
            }
        }
        return !namespace.isEmpty();
    }

    public static boolean isValidPath(String path) {
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/')) {
                return false;
            }
        }
        return !path.isEmpty();
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return namespace + NAMESPACE_SEPARATOR + path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Identifier that)) return false;
        return namespace.equals(that.namespace) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }

    @Override
    public int compareTo(Identifier o) {
        int c = this.namespace.compareTo(o.namespace);
        return c != 0 ? c : this.path.compareTo(o.path);
    }
}
