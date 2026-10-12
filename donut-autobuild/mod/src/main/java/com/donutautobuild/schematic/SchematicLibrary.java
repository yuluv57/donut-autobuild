package com.donutautobuild.schematic;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Stores references only; a schematic is always read directly from its original path. */
public final class SchematicLibrary {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path configFile;
    private final Path defaultDirectory;
    private Path directory;
    private final LinkedHashSet<Path> favorites = new LinkedHashSet<>();
    private final List<Path> recents = new ArrayList<>();
    private boolean colorRules;
    private String error = "";

    public SchematicLibrary(Path profile) {
        profile = normalize(profile);
        configFile = profile.resolve("config/donutautobuild-schematics.json");
        defaultDirectory = profile.resolve("schematics");
        directory = defaultDirectory;
        if (Files.isRegularFile(configFile)) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(configFile, StandardCharsets.UTF_8)).getAsJsonObject();
                if (json.has("directory")) directory = normalize(Path.of(json.get("directory").getAsString()));
                if (json.has("favorites")) readPaths(json.getAsJsonArray("favorites"), favorites);
                if (json.has("recents")) readPaths(json.getAsJsonArray("recents"), recents);
                if (json.has("colorRules")) colorRules = json.get("colorRules").getAsBoolean();
                if (recents.size() > 16) recents.subList(16, recents.size()).clear();
            } catch (Exception ex) {
                error = "Không đọc được thư viện sche; giữ nguyên file cấu hình hiện có.";
            }
        }
    }
    public static Path normalize(Path path) { return path.toAbsolutePath().normalize(); }
    private static void readPaths(JsonArray array, Collection<Path> output) {
        for (JsonElement entry : array) {
            try { Path path = normalize(Path.of(entry.getAsString())); if (!output.contains(path)) output.add(path); }
            catch (RuntimeException ignored) { }
        }
    }
    public static boolean supported(Path path) {
        if (path == null || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".litematic") || name.endsWith(".schematic") || name.endsWith(".schem") || name.endsWith(".nbt");
    }
    public Path directory() { return directory; }
    public Path defaultDirectory() { return defaultDirectory; }
    public String error() { return error; }
    public List<Path> favorites() { return List.copyOf(favorites); }
    public List<Path> recents() { return List.copyOf(recents); }
    public boolean isFavorite(Path path) { return path != null && favorites.contains(normalize(path)); }
    public boolean colorRules() { return colorRules; }
    public void colorRules(boolean enabled) throws IOException { colorRules = enabled; save(); }
    public void directory(Path path) throws IOException {
        path = normalize(path);
        if (!Files.isDirectory(path)) throw new IOException("Thư mục không tồn tại: " + path);
        directory = path; save();
    }
    public List<Path> list(String filter) throws IOException {
        if (directory.equals(defaultDirectory) && !Files.exists(directory)) Files.createDirectories(directory);
        if (!Files.isDirectory(directory)) throw new IOException("Thư mục sche không còn tồn tại: " + directory);
        String query = filter == null ? "" : filter.strip().toLowerCase(Locale.ROOT);
        try (Stream<Path> walk = Files.walk(directory, 4)) {
            return walk.filter(Files::isRegularFile).filter(SchematicLibrary::supported)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains(query))
                .limit(5000).map(SchematicLibrary::normalize)
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                    .thenComparing(Path::toString)).toList();
        }
    }
    public boolean toggleFavorite(Path path) throws IOException {
        path = normalize(path);
        if (!supported(path)) throw new IOException("Chọn file .litematic, .schematic, .schem hoặc .nbt.");
        boolean added = favorites.add(path);
        if (!added) favorites.remove(path);
        save(); return added;
    }
    public void used(Path path) throws IOException {
        path = normalize(path); recents.remove(path); recents.addFirst(path);
        while (recents.size() > 16) recents.removeLast();
        save();
    }
    private JsonArray paths(Collection<Path> paths) {
        JsonArray array = new JsonArray(); for (Path path : paths) array.add(path.toString()); return array;
    }
    private void save() throws IOException {
        if (!error.isEmpty()) throw new IOException(error);
        JsonObject json = new JsonObject(); json.addProperty("version", 1); json.addProperty("directory", directory.toString());
        json.addProperty("colorRules", colorRules); json.add("favorites", paths(favorites)); json.add("recents", paths(recents));
        Files.createDirectories(configFile.getParent());
        Path temporary = Files.createTempFile(configFile.getParent(), "donutautobuild-schematics-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(json), StandardCharsets.UTF_8);
            try { Files.move(temporary, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
