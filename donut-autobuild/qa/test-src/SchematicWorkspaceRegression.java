package com.donutautobuild;

import com.donutautobuild.build.*;
import com.donutautobuild.schematic.*;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherSchematic;
import fi.dy.masa.litematica.render.schematic.IChunkRendererFactory;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;
import fi.dy.masa.litematica.util.SchematicWorldRefresher;
import fi.dy.masa.litematica.world.ChunkManagerSchematic;
import fi.dy.masa.litematica.world.ChunkSchematic;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.util.LayerMode;
import fi.dy.masa.malilib.util.PositionUtils.CoordinateType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** File references plus real Litematica placement transforms, locks and visibility. */
public final class SchematicWorkspaceRegression {
    private static final class TestFont extends net.minecraft.client.font.TextRenderer {
        TestFont() { super(id -> null, false); }
        @Override public int getWidth(String value) { return value.length() * 6; }
        @Override public int getWidth(net.minecraft.text.StringVisitable value) { return getWidth(value.getString()); }
        @Override public int getWidth(net.minecraft.text.OrderedText value) { return 80; }
    }
    static int checks;
    static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    public static int run() throws Exception {
        checks = 0;
        Path profile = Files.createTempDirectory("autobuild-schematic-library-");
        library(profile); geometry(); workspace(profile);
        System.out.println("PASS: " + checks + " schematic library, native display, commit and real placement assertions.");
        return checks;
    }
    static void library(Path profile) throws Exception {
        var library = new SchematicLibrary(profile);
        check(library.directory().equals(profile.resolve("schematics")), "default library points directly to the active Modrinth profile schematics folder");
        check(library.list("").isEmpty() && Files.isDirectory(library.directory()), "first use creates the schematics library folder");
        Path first = library.directory().resolve("Beta.litematic"), second = library.directory().resolve("alpha.SCHEM");
        Path nested = library.directory().resolve("nested/inside.schematic"), vanilla = library.directory().resolve("structure.nbt");
        Files.createDirectories(nested.getParent());
        for (Path file : List.of(first, second, nested, vanilla, library.directory().resolve("ignore.txt"))) Files.writeString(file, "reference-data");
        check(library.list("").size() == 4, "library lists all four formats actually supported by installed Litematica");
        check(library.list("ALPHA").equals(List.of(second)), "file filter is case insensitive");
        check(library.list("").getFirst().equals(second), "file browser sorts by name including nested files");
        byte[] original = Files.readAllBytes(first);
        Path outside = Files.createTempDirectory("autobuild-external-sche-").resolve("Other.litematic"); Files.writeString(outside, "external-data");
        check(library.toggleFavorite(outside), "favorites accepts a direct reference outside the current directory");
        library.used(outside); library.used(first); library.used(outside); library.colorRules(true);
        var reloaded = new SchematicLibrary(profile);
        check(reloaded.favorites().equals(List.of(outside)), "favorite absolute paths persist across a restart");
        check(reloaded.recents().equals(List.of(outside, first)), "recent files are unique and the most recent appears first");
        check(reloaded.colorRules(), "own GUI color rules persist without editing AutoBuild build settings");
        check(!Files.exists(profile.resolve("schematics/Other.litematic")), "reference library never copies external files");
        check(Arrays.equals(original, Files.readAllBytes(first)) && Files.readString(outside).equals("external-data"), "library actions leave source schematics byte identical");
        check(!reloaded.toggleFavorite(outside) && Files.isRegularFile(outside), "removing a favorite never deletes its source schematic");
        reloaded.directory(outside.getParent());
        check(new SchematicLibrary(profile).directory().equals(outside.getParent()), "custom library directory persists");
        Files.delete(outside);
        check(new SchematicLibrary(profile).recents().contains(outside), "missing references remain visible for correction instead of silently disappearing");
        for (int i = 0; i < 20; i++) reloaded.used(outside.getParent().resolve("recent" + i + ".litematic"));
        check(reloaded.recents().size() == 16, "recent history is bounded");
        Path brokenProfile = Files.createTempDirectory("autobuild-broken-library-");
        Files.createDirectories(brokenProfile.resolve("config")); Path malformed = brokenProfile.resolve("config/donutautobuild-schematics.json");
        Files.writeString(malformed, "broken-existing-config"); var broken = new SchematicLibrary(brokenProfile);
        check(!broken.error().isEmpty() && broken.directory().equals(brokenProfile.resolve("schematics")), "malformed library config falls back to the profile path");
        try { broken.toggleFavorite(first); throw new AssertionError("must retain malformed config"); } catch (java.io.IOException expected) { }
        check(Files.readString(malformed).equals("broken-existing-config"), "failed JSON parsing never overwrites the user's existing config");
    }
    static void geometry() {
        for (Direction heading : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            BlockPos ahead = SchematicWorkspace.relativeOffset(heading, "FORWARD", 5);
            BlockPos back = SchematicWorkspace.relativeOffset(heading, "BACK", 5);
            BlockPos left = SchematicWorkspace.relativeOffset(heading, "LEFT", 1);
            BlockPos right = SchematicWorkspace.relativeOffset(heading, "RIGHT", 1);
            check(ahead.add(back).equals(BlockPos.ORIGIN), "forward/back cancel for every player heading");
            check(left.add(right).equals(BlockPos.ORIGIN), "left/right cancel for every player heading");
            check(left.equals(BlockPos.ORIGIN.offset(heading.rotateYCounterclockwise())), "left uses correct player relative side");
            check(right.equals(BlockPos.ORIGIN.offset(heading.rotateYClockwise())), "right uses correct player relative side");
            check(SchematicWorkspace.relativeOffset(heading, "UP", 10).equals(new BlockPos(0,10,0)), "up always moves vertically");
            check(SchematicWorkspace.relativeOffset(heading, "DOWN", 10).equals(new BlockPos(0,-10,0)), "down always moves vertically");
        }
        for (BlockRotation initial : BlockRotation.values()) {
            BlockRotation actual = initial;
            for (int n = 0; n < 4; n++) actual = SchematicWorkspace.rotationAfter(actual, 1);
            check(actual == initial, "four 90 degree turns restore the original orientation");
            check(SchematicWorkspace.rotationAfter(SchematicWorkspace.rotationAfter(initial, 1), -1) == initial, "left and right rotation are inverses");
        }
    }
    static void workspace(Path profile) throws Exception {
        var session = new RecoveryRegression.Session(Items.NETHERITE_PICKAXE, 1, 1);
        Object previousClient = RecoveryRegression.field(MinecraftClient.class, "instance").get(null);
        RecoveryRegression.field(MinecraftClient.class, "instance").set(null, session.mc);
        RecoveryRegression.object(session.mc, MinecraftClient.class, "runDirectory", profile.toFile());
        RecoveryRegression.object(session.mc, MinecraftClient.class, "textRenderer", new TestFont());
        WaterlogAimRegression.position(session.player, new Vec3d(12.4, 64, -8.2));
        BuildConfig previousConfig = AutoBuildMod.config; BuildController previousController = AutoBuildMod.controller;
        AutoBuildMod.config = new BuildConfig(); AutoBuildMod.config.syncLitematicaLayer = false; AutoBuildMod.controller = null;
        var manager = DataManager.getSchematicPlacementManager(); List<SchematicPlacement> originalPlacements = List.copyOf(manager.getAllSchematicsPlacements());
        SchematicPlacement originalSelected = manager.getSelectedSchematicPlacement();
        var layers = DataManager.getRenderLayerRange();
        Object previousRefresher = RecoveryRegression.field(fi.dy.masa.malilib.util.LayerRange.class, "refresher").get(layers);
        layers.setRefresher(new fi.dy.masa.malilib.interfaces.IRangeChangeListener() {
            public void updateAll() { } public void updateBetweenX(int a, int b) { }
            public void updateBetweenY(int a, int b) { } public void updateBetweenZ(int a, int b) { }
        }); // Headless fixture still uses the real range, file loader and placement manager.
        try {
            AreaSelection area = new AreaSelection(); area.setExplicitOrigin(BlockPos.ORIGIN);
            area.addSubRegionBox(new fi.dy.masa.litematica.selection.Box(BlockPos.ORIGIN, new BlockPos(2,1,4), "body"), false);
            var schematic = LitematicaSchematic.createEmptySchematic(area, "test");
            schematic.getSubRegionContainer("body").set(0, 0, 0, net.minecraft.block.Blocks.COBBLESTONE.getDefaultState());
            schematic.getSubRegionContainer("body").set(2, 1, 4, net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
            var placement = SchematicPlacement.createFor(schematic, new BlockPos(100, 64, 100), "existing", true, true);
            var other = SchematicPlacement.createFor(schematic, new BlockPos(-100, 64, -100), "other", true, true);
            manager.addSchematicPlacement(placement, false); manager.addSchematicPlacement(other, false); manager.setSelectedSchematicPlacement(placement);
            RecoveryRegression.field(SchematicWorkspace.class, "workflowTracked").setBoolean(null, false);
            check(SchematicWorkspace.permitStart(), "legacy K and reconnect remain available before the new edit workflow is opened");
            Configs.Colors.SCHEMATIC_OVERLAY_COLOR_MISSING.setIntegerValue(0x7044AADD);
            Configs.Colors.SCHEMATIC_OVERLAY_COLOR_WRONG_STATE.setIntegerValue(0x70FFD54F);
            Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.setBooleanValue(true);
            SchematicWorkspace.enablePreview();
            check(SchematicWorkspace.selected() == placement && SchematicWorkspace.placements().contains(other), "opening old GUI attaches to selected placement and preserves other placements");
            check(Configs.Colors.SCHEMATIC_OVERLAY_COLOR_MISSING.getIntegerValue() == Configs.Colors.SCHEMATIC_OVERLAY_COLOR_MISSING.getDefaultIntegerValue()
                && Configs.Colors.SCHEMATIC_OVERLAY_COLOR_WRONG_STATE.getIntegerValue() == Configs.Colors.SCHEMATIC_OVERLAY_COLOR_WRONG_STATE.getDefaultIntegerValue()
                && Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.getBooleanValue() == Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.getDefaultBooleanValue(),
                "119 forced tint colors and translucency migrate to native 109 display defaults");
            check(!new SchematicLibrary(profile).colorRules() && !SchematicWorkspace.colorRulesEnabled(), "legacy custom rendering marker is disabled after one migration");
            check(SchematicWorkspace.editing() && !SchematicWorkspace.isCommitted() && !SchematicWorkspace.permitStart(), "preview editing requires Nạp before K or Start can build");
            SchematicWorkspace.nudge(1,5,-2); check(placement.getOrigin().equals(new BlockPos(101,69,98)), "XYZ nudges mutate actual Litematica origin");
            check(other.getOrigin().equals(new BlockPos(-100,64,-100)), "nudge leaves other placement origins unchanged");
            SchematicWorkspace.moveToPlayer(); check(placement.getOrigin().equals(new BlockPos(12,64,-9)), "move to player uses actual block position with negative floor rounding");
            SchematicWorkspace.rotate(1); check(placement.getRotation() == BlockRotation.CLOCKWISE_90, "rotation uses native placement transform");
            SchematicWorkspace.mirror(true); check(placement.getMirror() == BlockMirror.FRONT_BACK, "mirror X reverses local X");
            SchematicWorkspace.mirror(false); check(placement.getMirror() == BlockMirror.NONE && placement.getRotation() == BlockRotation.COUNTERCLOCKWISE_90,
                "applying mirror Z after X retains both reflections by composing them into a half turn");
            SchematicWorkspace.mirror(false); check(placement.getMirror() == BlockMirror.LEFT_RIGHT, "repeating Z after both reflections restores geometric X");
            SchematicWorkspace.toggleLock(); check(placement.isLocked(), "lock button explicitly enables native placement lock");
            BlockPos lockedOrigin = placement.getOrigin(); BlockRotation lockedRotation = placement.getRotation(); BlockMirror lockedMirror = placement.getMirror();
            check(SchematicWorkspace.nudge(5,5,5).contains("khóa"), "locked transform reports why it did not move");
            SchematicWorkspace.rotate(1); SchematicWorkspace.mirror(true); SchematicWorkspace.moveToPlayer();
            check(placement.getOrigin().equals(lockedOrigin) && placement.getRotation() == lockedRotation && placement.getMirror() == lockedMirror, "locked placement is never silently unlocked or mutated");
            SchematicWorkspace.toggleLock(); placement.setCoordinateLocked(CoordinateType.X, true);
            String result = SchematicWorkspace.setPosition(30,70,40);
            check(placement.getOrigin().equals(new BlockPos(12,70,40)) && result.contains("trục đang khóa"), "native per-axis coordinate locks remain respected and reported");
            placement.setCoordinateLocked(CoordinateType.X, false);
            SchematicWorkspace.showAll();
            check(SchematicWorkspace.visible() && "ALL".equals(SchematicWorkspace.viewMode()), "All enables native schematic contents and all layers");
            SchematicWorkspace.showLayer(65);
            check(SchematicWorkspace.visible() && layers.getLayerMode() == LayerMode.SINGLE_LAYER && layers.getLayerSingle() == 65, "single-layer view applies exactly the selected Y");
            SchematicWorkspace.enablePreview();
            check(layers.getLayerMode() == LayerMode.SINGLE_LAYER && layers.getLayerSingle() == 65, "reopening old GUI retains chosen layer instead of forcing All");
            SchematicWorkspace.stepLayer(1); check(layers.getLayerSingle() == 66, "up-layer control moves one Y when viewing a layer");
            SchematicWorkspace.stepLayer(-1); check(layers.getLayerSingle() == 65, "down-layer control restores previous Y");
            SchematicWorkspace.hideContents();
            check(!Configs.Visuals.ENABLE_SCHEMATIC_BLOCKS.getBooleanValue() && !Configs.Visuals.ENABLE_SCHEMATIC_FLUIDS.getBooleanValue()
                && !Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.getBooleanValue() && !Configs.Visuals.RENDER_SCHEMATIC_ENTITIES.getBooleanValue()
                && !Configs.Visuals.RENDER_SCHEMATIC_TILE_ENTITIES.getBooleanValue(), "Hide disables all contents including water, entities and overlay");
            check(Configs.Visuals.ENABLE_PLACEMENT_BOXES_RENDERING.getBooleanValue() && placement.shouldRenderEnclosingBox(), "Hide leaves only placement bounds");
            SchematicWorkspace.enablePreview(); check(!SchematicWorkspace.visible(), "reopening GUI retains explicit hidden contents");
            SchematicWorkspace.syncLayer(80);
            check(!SchematicWorkspace.visible() && layers.getLayerMode() == LayerMode.ALL, "native build layer updates cannot override Hide");
            SchematicWorkspace.showAll(); SchematicWorkspace.syncLayer(80);
            check(layers.getLayerMode() == LayerMode.ALL, "native build layer updates cannot override explicit All");
            SchematicWorkspace.showLayer(65); SchematicWorkspace.syncLayer(66);
            check(layers.getLayerSingle() == 66, "single-layer view follows actual next building layer");
            Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.setBooleanValue(true);
            Configs.Visuals.SCHEMATIC_OVERLAY_ENABLE_SIDES.setBooleanValue(false);
            SchematicWorkspace.enablePreview();
            check(Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.getBooleanValue() && !Configs.Visuals.SCHEMATIC_OVERLAY_ENABLE_SIDES.getBooleanValue(),
                "after migration, reopening never overwrites user's native rendering preferences");
            SchematicWorkspace.showAll();
            check(SchematicWorkspace.applyPath("\"" + profile.resolve("schematics") + "\"").startsWith("Thư viện"), "pasted quoted Windows directory paths are accepted");
            check(SchematicWorkspace.start().contains("Vào thế giới"), "cannot start build before entering a world");
            session.mc.world = RecoveryRegression.allocate(RecoveryRegression.World.class);
            RecoveryRegression.object(session.mc.world, net.minecraft.world.World.class, "registryKey", net.minecraft.world.World.OVERWORLD);
            RecoveryRegression.object(session.player, net.minecraft.entity.Entity.class, "world", session.mc.world);
            fi.dy.masa.litematica.world.SchematicWorldHandler.INSTANCE.setDynamicRegistryManager(
                net.minecraft.registry.DynamicRegistryManager.of(net.minecraft.registry.Registries.REGISTRIES));
            check(SchematicWorkspace.paste(profile.resolve("missing.litematic")).contains("không tồn tại"), "missing source file gives actionable error without deleting existing placements");
            Path source = profile.resolve("schematics/Native.litematic");
            check(schematic.writeToFile(source.getParent().toFile(), "Native", false), "test saves a real supported native schematic");
            byte[] sourceBytes = Files.readAllBytes(source);
            String pasted = SchematicWorkspace.paste(source);
            check(pasted.startsWith("Đã đặt sche xem trước") && SchematicWorkspace.selected() != placement && SchematicWorkspace.selected().getOrigin().equals(session.player.getBlockPos()),
                "Paste loads a real Litematica file as a preview placement at the player: " + pasted);
            check(manager.getAllSchematicsPlacements().contains(placement) && manager.getAllSchematicsPlacements().contains(other), "paste preserves both previously loaded placements");
            SchematicWorkspace.paste(source); check(SchematicWorkspace.selected().getName().equals("Native (2)"), "pasting the same schematic adds a distinct preview without overwriting existing placements");
            check(Arrays.equals(sourceBytes, Files.readAllBytes(source)), "paste and transformations leave schematic source bytes intact");
            check(SchematicWorkspace.recents().getFirst().equals(source), "successful paste records the original file reference");
            check(DataManager.getRenderLayerRange().getLayerMode() == LayerMode.ALL && SchematicWorkspace.visible(), "paste restores full visible preview even after a hidden or limited previous preview");
            rendererVisibility(session);
            SchematicWorkspace.enablePreview();
            AutoBuildMod.controller = new BuildController(AutoBuildMod.config);
            check(!SchematicWorkspace.isCommitted(), "newly pasted schematic is editable and uncommitted");
            AutoBuildMod.controller.userStart();
            check(!AutoBuildMod.controller.wanted() && !AutoBuildMod.controller.active() && !AutoBuildMod.controller.hasPlan(), "K/userStart cannot silently load and build an uncommitted preview");
            AutoBuildMod.controller.start();
            check(!AutoBuildMod.controller.wanted() && !AutoBuildMod.controller.active() && !AutoBuildMod.controller.hasPlan(), "direct native start is also guarded against uncommitted positions");
            var pastedPlacement = SchematicWorkspace.selected();
            String prepared = SchematicWorkspace.prepare();
            check(prepared.startsWith("Đã chốt") && SchematicWorkspace.isCommitted() && SchematicWorkspace.permitStart(), "Nạp validates and commits the selected native placement: " + prepared);
            check(!pastedPlacement.isLocked(), "Nạp wrapper commit does not add a permanent native Litematica lock");
            var nativePreparedPlan = (SchematicPlan)RecoveryRegression.field(BuildController.class, "plan").get(AutoBuildMod.controller);
            for (int loadTick = 0; loadTick < 100 && !nativePreparedPlan.loaded; loadTick++) nativePreparedPlan.loadTick();
            check(nativePreparedPlan.targets.size() == 2 && AutoBuildMod.controller.qualitySchematicReady(), "Nạp uses the real native plan loader with both original block materials");
            RecoveryRegression.field(SchematicWorkspace.class, "workflowTracked").setBoolean(null, false);
            RecoveryRegression.field(SchematicWorkspace.class, "committedPlan").set(null, null);
            SchematicWorkspace.enablePreview();
            check(SchematicWorkspace.isCommitted(), "opening old GUI reuses an already loaded, unchanged legacy plan without forcing another Nạp");
            BlockPos committedOrigin = pastedPlacement.getOrigin();
            BlockRotation committedRotation = pastedPlacement.getRotation();
            BlockMirror committedMirror = pastedPlacement.getMirror();
            check(SchematicWorkspace.nudge(1,0,0).contains("Sửa vị trí"), "committed position requires explicit Edit button before moving");
            SchematicWorkspace.rotate(1); SchematicWorkspace.mirror(true); SchematicWorkspace.moveToPlayer();
            check(pastedPlacement.getOrigin().equals(committedOrigin) && pastedPlacement.getRotation() == committedRotation && pastedPlacement.getMirror() == committedMirror,
                "committed placement rejects all movement, rotation and mirror controls");
            SchematicWorkspace.showAll(); SchematicWorkspace.enablePreview();
            check(SchematicWorkspace.isCommitted() && "ALL".equals(SchematicWorkspace.viewMode()), "opening menu again preserves commit and display selection");
            Object savedPlan = RecoveryRegression.field(BuildController.class, "plan").get(AutoBuildMod.controller);
            RecoveryRegression.field(BuildController.class, "running").setBoolean(AutoBuildMod.controller, true);
            RecoveryRegression.field(BuildController.class, "wanted").setBoolean(AutoBuildMod.controller, true);
            RecoveryRegression.field(BuildController.class, "matched").setInt(AutoBuildMod.controller, 17);
            @SuppressWarnings("unchecked") Set<BlockPos> completed = (Set<BlockPos>)RecoveryRegression.field(BuildController.class, "placedDone").get(AutoBuildMod.controller);
            completed.add(new BlockPos(99,63,98)); session.mc.options.forwardKey.setPressed(true);
            SchematicWorkspace.editPosition();
            check(SchematicWorkspace.editing() && !SchematicWorkspace.isCommitted() && !AutoBuildMod.controller.active() && !AutoBuildMod.controller.wanted()
                && !session.mc.options.forwardKey.isPressed(), "Sửa vị trí stops movement and automatic resume, then reopens editable preview");
            SchematicWorkspace.nudge(1,0,0);
            check(pastedPlacement.getOrigin().equals(committedOrigin.add(1,0,0)), "position can move only after Sửa vị trí");
            check(RecoveryRegression.field(BuildController.class, "plan").get(AutoBuildMod.controller) == savedPlan
                && completed.contains(new BlockPos(99,63,98)) && AutoBuildMod.controller.completed() == 17,
                "editing preserves completed physical blocks and existing progress until next Nạp");
            check(!SchematicWorkspace.permitStart(), "edited position cannot resume old plan before Nạp");
            SchematicWorkspace.prepare(); pastedPlacement.setOrigin(pastedPlacement.getOrigin().add(1,0,0), message -> {});
            check(!SchematicWorkspace.isCommitted() && !SchematicWorkspace.permitStart(), "external native Litematica move invalidates the committed transform snapshot too");
            SchematicWorkspace.restorePreviewState(); check(SchematicWorkspace.editing(), "tick housekeeping invalidates external edits without changing render buffers");
            pastedPlacement.toggleLocked();
            check(pastedPlacement.isLocked(), "fixture enables a preexisting user native lock");
            SchematicWorkspace.prepare(); SchematicWorkspace.editPosition();
            check(pastedPlacement.isLocked() && SchematicWorkspace.nudge(1,0,0).contains("Litematica"), "Sửa vị trí never removes preexisting native user locks");
            pastedPlacement.toggleLocked(); SchematicWorkspace.prepare();
            var beforeWorld = session.mc.world;
            session.mc.world = null; SchematicWorkspace.restorePreviewState();
            check(!SchematicWorkspace.isCommitted() && SchematicWorkspace.permitStart(), "disconnect clears only the edit guard, preserving old reconnect and resume policy");
            session.mc.world = beforeWorld;
            SchematicWorkspace.selectPlacement(other); check(SchematicWorkspace.selected() == other && SchematicWorkspace.editing(), "changing selected placement requires a fresh Nạp");
            manager.removeSchematicPlacement(other, false);
            check(!SchematicWorkspace.isCommitted(), "removing current placement cannot retain a stale commit");
            check(manager.getAllSchematicsPlacements().contains(placement), "workflow preserves existing first placement");
        } finally {
            for (var placement : List.copyOf(manager.getAllSchematicsPlacements())) if (!originalPlacements.contains(placement)) manager.removeSchematicPlacement(placement, false);
            manager.setSelectedSchematicPlacement(originalSelected); AutoBuildMod.config = previousConfig; AutoBuildMod.controller = previousController;
            layers.setRefresher((fi.dy.masa.malilib.interfaces.IRangeChangeListener)previousRefresher);
            RecoveryRegression.field(MinecraftClient.class, "instance").set(null, previousClient);
        }
    }

    /** Exercise native loaded-chunk scheduling, rather than testing only the toggle's booleans. */
    private static void rendererVisibility(RecoveryRegression.Session session) throws Exception {
        var clientWorld = session.mc.world;
        var rendererOwner = LitematicaRenderer.getInstance();
        var rendererField = RecoveryRegression.field(LitematicaRenderer.class, "worldRenderer");
        Object previousRenderer = rendererField.get(rendererOwner);
        var handlerField = RecoveryRegression.field(SchematicWorldHandler.class, "world");
        Object previousSchematicWorld = handlerField.get(SchematicWorldHandler.INSTANCE);
        var refresherClient = RecoveryRegression.field(SchematicWorldRefresher.class, "mc");
        Object previousRefresherClient = refresherClient.get(SchematicWorldRefresher.INSTANCE);
        try {
            var visibleClient = RecoveryRegression.allocate(VisibleClientWorld.class);
            var clientChunks = RecoveryRegression.allocate(VisibleClientChunks.class);
            visibleClient.chunks = clientChunks;
            session.mc.world = visibleClient;

            var schematicWorld = RecoveryRegression.allocate(VisibleSchematicWorld.class);
            var schematicChunks = RecoveryRegression.allocate(ChunkManagerSchematic.class);
            var loaded = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ChunkSchematic>();
            ChunkPos chunkPos = new ChunkPos(1, 1);
            var chunk = RecoveryRegression.allocate(ChunkSchematic.class);
            RecoveryRegression.object(chunk, net.minecraft.world.chunk.Chunk.class, "pos", chunkPos);
            loaded.put(chunkPos.toLong(), chunk);
            RecoveryRegression.object(schematicChunks, ChunkManagerSchematic.class, "loadedChunks", loaded);
            schematicWorld.chunks = schematicChunks;
            clientChunks.chunk = chunk;

            var renderer = RecoveryRegression.allocate(WorldRendererSchematic.class);
            RecoveryRegression.object(renderer, WorldRendererSchematic.class, "mc", session.mc);
            RecoveryRegression.object(renderer, WorldRendererSchematic.class, "profiler", net.minecraft.util.profiler.DummyProfiler.INSTANCE);
            RecoveryRegression.object(renderer, WorldRendererSchematic.class, "world", schematicWorld);
            RecoveryRegression.object(schematicWorld, WorldSchematic.class, "worldRenderer", renderer);
            var chunkConstructor = ChunkRendererSchematicVbo.class.getDeclaredConstructor(WorldSchematic.class, WorldRendererSchematic.class);
            chunkConstructor.setAccessible(true);
            IChunkRendererFactory factory = (world, owner) -> {
                try { return chunkConstructor.newInstance(world, owner); }
                catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
            };
            var constructor = ChunkRenderDispatcherSchematic.class.getDeclaredConstructor(WorldSchematic.class, int.class,
                WorldRendererSchematic.class, IChunkRendererFactory.class);
            constructor.setAccessible(true);
            var dispatcher = constructor.newInstance(schematicWorld, 10, renderer, factory);
            RecoveryRegression.object(renderer, WorldRendererSchematic.class, "chunkRendererDispatcher", dispatcher);
            rendererField.set(rendererOwner, renderer);
            handlerField.set(SchematicWorldHandler.INSTANCE, schematicWorld);
            RecoveryRegression.object(SchematicWorldRefresher.INSTANCE, SchematicWorldRefresher.class, "mc", session.mc);
            var getChunk = ChunkRenderDispatcherSchematic.class.getDeclaredMethod("getChunkRenderer", int.class, int.class);
            getChunk.setAccessible(true);
            var renderChunk = (ChunkRendererSchematicVbo)getChunk.invoke(dispatcher, chunkPos.x, chunkPos.z);
            var dirty = RecoveryRegression.field(ChunkRendererSchematicVbo.class, "needsUpdate");
            check(!dirty.getBoolean(renderChunk), "native new chunk renderers start unscheduled; deleting the dispatcher alone cannot restore a stationary preview");
            var listDirty = RecoveryRegression.field(WorldRendererSchematic.class, "displayListEntitiesDirty");
            SchematicWorkspace.showAll();
            for (int cycle = 0; cycle < 20; cycle++) {
                dirty.setBoolean(renderChunk, false); listDirty.setBoolean(renderer, false);
                SchematicWorkspace.hideContents();
                check(!SchematicWorkspace.visible() && dirty.getBoolean(renderChunk), "Hide schedules native loaded chunks instead of leaving stale contents: " + cycle);
                check(renderer.hasWorld() && listDirty.getBoolean(renderer), "Hide refreshes the native visible list without losing the schematic world: " + cycle);
                dirty.setBoolean(renderChunk, false); listDirty.setBoolean(renderer, false);
                DataManager.getRenderLayerRange().setLayerMode(LayerMode.SINGLE_LAYER, false);
                SchematicWorkspace.showAll();
                check(SchematicWorkspace.visible() && dirty.getBoolean(renderChunk), "second Show and subsequent Shows schedule actual chunk rebuilding even while the player stays still: " + cycle);
                check(DataManager.getRenderLayerRange().getLayerMode() == LayerMode.ALL && listDirty.getBoolean(renderer),
                    "Show restores full preview layers and marks native renderInfos dirty: " + cycle);
                check(RecoveryRegression.field(WorldRendererSchematic.class, "chunkRendererDispatcher").get(renderer) == dispatcher
                    && getChunk.invoke(dispatcher, chunkPos.x, chunkPos.z) == renderChunk,
                    "repeated toggles preserve existing renderers and their buffers rather than deleting them: " + cycle);
            }
            dirty.setBoolean(renderChunk, false); listDirty.setBoolean(renderer, false);
            for (int tick = 0; tick < 40; tick++) SchematicWorkspace.restorePreviewState();
            check(!dirty.getBoolean(renderChunk) && !listDirty.getBoolean(renderer), "idle tick housekeeping never schedules schematic rebuilds that cause flashing");
            check(RecoveryRegression.field(WorldRendererSchematic.class, "chunkRendererDispatcher").get(renderer) == dispatcher,
                "idle ticks preserve all native renderer buffers");

        } finally {
            session.mc.world = clientWorld;
            rendererField.set(rendererOwner, previousRenderer);
            handlerField.set(SchematicWorldHandler.INSTANCE, previousSchematicWorld);
            RecoveryRegression.object(SchematicWorldRefresher.INSTANCE, SchematicWorldRefresher.class, "mc", previousRefresherClient);
        }
    }

    private static final class VisibleClientWorld extends net.minecraft.client.world.ClientWorld {
        VisibleClientChunks chunks;
        private VisibleClientWorld() { super(null,null,null,null,0,0,null,false,0L,0); }
        @Override public net.minecraft.client.world.ClientChunkManager getChunkManager() { return chunks; }
    }
    private static final class VisibleClientChunks extends net.minecraft.client.world.ClientChunkManager {
        net.minecraft.world.chunk.WorldChunk chunk;
        private VisibleClientChunks() { super(null, 0); }
        @Override public net.minecraft.world.chunk.WorldChunk getChunk(int x, int z, net.minecraft.world.chunk.ChunkStatus status, boolean create) { return chunk; }
    }
    private static final class VisibleSchematicWorld extends WorldSchematic {
        ChunkManagerSchematic chunks;
        private VisibleSchematicWorld() { super(null,null,null,null); }
        @Override public ChunkManagerSchematic getChunkProvider() { return chunks; }
        @Override public ChunkManagerSchematic getChunkManager() { return chunks; }
        @Override public int getBottomY() { return 0; }
        @Override public int getTopYInclusive() { return 255; }
    }
}
