package com.donutautobuild.build;

import com.donutautobuild.build.SchematicPlan.Target;
import com.donutautobuild.build.PlacementSolver.Aim;
import net.minecraft.block.FluidFillable;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BucketItem;
import com.donutautobuild.AutoBuildMod;
import com.donutautobuild.BuildConfig;
import com.donutautobuild.logic.LayerPlanner;
import com.donutautobuild.logic.MotionMath;
import com.donutautobuild.logic.PathSearch;
import com.donutautobuild.logic.RootSearch;
import com.donutautobuild.logic.RowPlanner;
import com.donutautobuild.logic.ScaffoldSearch;
import com.donutautobuild.order.OrderCollector;
import com.donutautobuild.order.ShopBuyer;
import java.util.ArrayDeque;
import net.minecraft.fluid.FluidState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.ObserverBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.AxisDirection;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class BuildController {
   // ===== 3.0.117 (bản vá ngoài): yêu cầu dùng xô nước đã gửi =====
   private BlockPos qualityWaterRequestPos;
   private int qualityWaterRequestAt;

   // ===== 3.0.110–3.0.116 (bản vá ngoài, ghép vào nguồn ở 3.0.117) =====
   private PlacementRecoveryBudget recoveryBudget;
   private SurvivalSupplies survivalSupplies;
   private int qualityIndex;
   private SchematicPlan qualityPlan;
   private HeldPlacementGuard heldPlacementGuard;
   private Map<BlockPos, Integer> qualityRetryAt;
   private int qualityLastNear;
   private BlockPos qualityAttackPos;
   private BlockPos qualityNavTarget;
   private int qualityReplans;
   private int qualityAttackTick = -1000;
   private int qualityUnsafeSince = -1;
   private int qualityReplanAt = -1000;
   private int qualityCautiousUntil = -1000;

   private final MinecraftClient mc = MinecraftClient.getInstance();
   public final BuildConfig config;
   private final OrderCollector orders;
   private final com.donutautobuild.order.HomeManager homes;
   private final com.donutautobuild.order.ShopBrowser browser;
   private boolean browsing;
   private int orderNeed = 1;
   private final Random random = new Random();
   private boolean running;
   private boolean preparing;
   private BuildController.Stage stage = BuildController.Stage.IDLE;
   private SchematicPlan plan;
   private ScaffoldJournal journal;
   private String worldKey = "";
   private String status = "Sẵn sàng. Chọn placement trong Litematica rồi nhấn K.";
   private String waitingReason = "";
   private int ticks;
   private int stageTicks;
   private int cooldown;
   private int verifiedPasses;
   private int inventoryAttempts;
   private int aimAttempt;
   private int aimInvalidTicks;
   private SchematicPlan.Target target;
   private SchematicPlan.Target temporary;
   private SchematicPlan.Target pending;
   private PlacementSolver.Aim aim;
   private boolean temporaryPending;
   private boolean pillar;
   private boolean cleanupMode;
   private boolean approachOnly;
   private int settledTicks;
   private final List<SchematicPlan.Target> queue = new ArrayList<>();
   private final ArrayDeque<BuildController.Placed> verify = new ArrayDeque<>();
   private final Map<BlockPos, Integer> failures = new HashMap<>();
   private int placedCount;
   private int clickTick = -1000;
   private boolean sneakHeld;
   private SchematicPlan.Target supportTarget;
   private final Map<BlockPos, Integer> navFailures = new HashMap<>();
   private final Set<BlockPos> placedDone = new HashSet<>();
   private final float[] yawHistory = new float[3];
   private int targetTicks;
   private int stepTicks;
   private final Map<BlockPos, Integer> configureClicks = new HashMap<>();
   private final Map<BlockPos, Integer> repairs = new HashMap<>();
   private final Map<BlockPos, Integer> attempts = new HashMap<>();
   private final Set<BlockPos> skipped = new HashSet<>();
   private final Map<Integer, Integer> fluidRetries = new HashMap<>();
   private final Set<Integer> permRetried = new HashSet<>();
   private final Set<BlockPos> spoofPlaced = new HashSet<>();
   // 3.0.99: cấm góc giả CÓ THỜI HẠN (ô → tick hết hạn). Trước đây cấm vĩnh viễn: 14 piston facing=down ở Y=-37 biến mất
   // 2 lần lúc server lag (cobblestone cũng mất cùng lúc) → bị cấm góc giả mãi, không còn cách đặt → "Bỏ hẳn khối kẹt".
   private final Map<BlockPos, Integer> noSpoof = new HashMap<>();
   private int spoofOkAt = -100000;
   private int spoofOkCount;
   private int plainVanishAt = -100000;
   private final Set<BlockPos> leanFailed = new HashSet<>();
   private final Map<BlockPos, Set<PathSearch.Cell>> badStands = new HashMap<>();

   private boolean standBanned(BlockPos var1) {
      return this.mc.player != null
         && (
            this.badStands.getOrDefault(var1, Set.of()).contains(Navigation.cell(this.mc.player.getBlockPos()))
               || Navigation.GHOSTS.contains(this.mc.player.getBlockPos())
               || Navigation.GHOSTS.contains(this.mc.player.getBlockPos().up())
         );
   }

   private void markLeanFailed(SchematicPlan.Target var1) {
      if (var1 != null && this.leanFailed.add(var1.pos().toImmutable())) {
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] {} @ {}: ngồi chìa mép không với tới → bỏ cách chìa mép, tìm chỗ đứng sát bên / đứng trên khối cạnh để ngắm thật",
               var1.state().getBlock().getName().getString(),
               var1.pos().toShortString()
            );
      }
   }
   private final Map<BlockPos, Integer> stuckSkips = new HashMap<>();
   private final Map<Integer, Integer> layerFinishTries = new HashMap<>();
   private int selectVerifyWait;
   private final Map<BlockPos, Integer> waterTries = new HashMap<>();
   private int waterTryTick = -100000;
   private int sweepSkipAt;
   private int cleanupPasses;
   private final Map<BlockPos, Integer> depWaits = new HashMap<>();
   private final Map<Item, Integer> depMissing = new LinkedHashMap<>();
   private final Map<BlockPos, Item> depMissingFor = new HashMap<>();
   private final Map<Item, Integer> depSaid = new HashMap<>();
   private final Map<Item, Integer> depOrdered = new HashMap<>();
   private int depStallSince = -1;
   private int scaffoldWarnAt = -100000;
   private int scaffoldWantAt = -100000;
   private int scaffoldUsedAt = -100000;
   private int scaffoldOrderAt = -100000;
   private int strictLayerWarnAt = -100000;
   private boolean massWrongConfirmed;
   private boolean massWrongPaused;
   private final Map<BlockPos, String> skipReason = new HashMap<>();
   private final Set<Block> lookUpWarned = new HashSet<>();
   private final Set<Integer> retriedLayers = new HashSet<>();
   private final Map<BlockPos, Integer> bridgeDefers = new HashMap<>();
   private static final int MAX_ATTEMPTS = 6;
   private boolean repairing;
   private final Map<BlockPos, BlockState> repairWrong = new HashMap<>();
   private int repairStepTicks;
   private final Map<BlockPos, Integer> repairTries = new HashMap<>();
   private BlockPos repairGoal;
   private boolean sweeping;
   private final ArrayDeque<BlockPos> supportQueue = new ArrayDeque<>();
   private final Set<BlockPos> sweepNow = new HashSet<>();
   private final Set<BlockPos> sweepSkip = new HashSet<>();
   /** 3.0.105: khối kê đã 2 lần bước ra vẫn không đập được → CHỈ dọn ở lượt thu dọn cuối (không để lượt quét "khối thừa" đẩy lại) */
   private final Set<BlockPos> sweepLate = new HashSet<>();
   private int bridgeTicks;
   private int replans;
   private boolean escapeTried;
   private BlockPos lastPlaced;
   private BlockPos priorityPos;
   private final Map<BlockPos, Integer> deferred = new HashMap<>();
   private final Map<Item, Integer> missing = new HashMap<>();
   private final Set<Item> announcedMissing = new HashSet<>();
   private final Set<Item> fullWarned = new HashSet<>();
   private final Set<BlockPos> cleanupDeferred = new HashSet<>();
   private List<PathSearch.Cell> goalCandidates = List.of();
   private List<PathSearch.Cell> path = List.of();
   private final List<PathSearch.Cell> goals = new ArrayList<>();
   private int goalIndex;
   private int pathIndex;
   private int stuckTicks;
   private int moveDepth;
   private int goalRetries;
   private float walkPitch = 12.0F;
   private Vec3d lastPosition;
   private Navigation terrain;
   private PathSearch search;
   private Item orderItem;
   private BuildController.Stage afterOrder = BuildController.Stage.SELECT;
   private BlockPos mining;
   private int miningTicks;
   private BuildController.Scan scan;
   private int total;
   private int matched;
   private int blockedCount;
   private int unloaded;
   private int unsupported;
   private int currentLayerTotal;
   private int currentLayerDone;
   private Integer currentLayer;
   private List<BuildController.Blocked> blockedList = List.of();
   private List<BuildController.MaterialRow> layerMaterials = List.of();
   private List<BuildController.MaterialRow> totalMaterials = List.of();
   private int lastIdleScan = -1000;
   private final ShopBuyer shop;
   private boolean shopping;
   private boolean dropBuckets;
   private long tickDeadline;
   private String heavyLabel = "";
   private int slowWarnTick = -1000;
   private static final long TICK_BUDGET_NANOS = 8000000L;
   private PlacementSolver.Aim anglesFor;
   private int poseTicks;
   private float aimYawC;
   private float aimPitchC;
   private final Map<String, Integer> gates = new LinkedHashMap<>();
   private int aimLogTick = -1000;
   private Direction rowDir;
   private final Map<BlockPos, BlockPos> coverFor = new HashMap<>();
   private final Set<BlockPos> restore = new HashSet<>();
   private final Set<BlockPos> coverRestored = new HashSet<>();
   private final Map<BlockPos, Integer> restoreTries = new HashMap<>();
   private BlockPos restoringPos;
   private final Map<BlockPos, Integer> deferCount = new HashMap<>();
   private final Map<BlockPos, Integer> deferLogged = new HashMap<>();
   private Set<PathSearch.Cell> reachable;
   private int reachableTick;
   private boolean scaffoldOk;
   private int rootDepth;
   private List<SchematicPlan.Target> floorOrder = List.of();
   private Integer floorLayer;
   private int floorCursor;
   private boolean planeMove;
   private boolean climbMove;
   private boolean climbSearch;
   private int climbFailUntil;
   private int planeStuck;
   private final Map<BlockPos, Integer> floorIndex = new HashMap<>();
   private int cursorJump;
   private static final int[][] STEPS8 = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
   private final ArrayDeque<BlockPos> chain = new ArrayDeque<>();
   private Integer chainLayer;
   private SchematicPlan.Target byTarget;
   private PlacementSolver.Aim byAim;
   private int byTicks;
   private int byCount;
   private List<RowPlanner.Row> rows;
   private Integer rowsLayer;
   private RowPlanner.Row job;
   private final Map<PathSearch.Cell, Integer> rowDeferred = new HashMap<>();
   private final List<BlockPos> jobTemps = new ArrayList<>();
   private BlockPos jobTempFor;
   private boolean jobSupport;
   private boolean rowSupport;
   private final Set<BlockPos> rowTemps = new HashSet<>();
   private static final Direction[] DIR_ORDER = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP};
   private int nextRetries;
   private boolean rootTimedOut;
   private final Map<BlockPos, BuildController.PlaceKey> placeCache = new HashMap<>();
   private final Map<BlockPos, Boolean> nearCache = new HashMap<>();
   private BlockPos pillarFor;
   private int pillarTicks;
   private int pillarJumps;
   private float pillarYaw;
   private float pillarPitch;
   private final ArrayDeque<BuildController.ExtraCheck> extraChecks = new ArrayDeque<>();
   private int lastProgress;
   private int stallSaid;
   private int gStand;
   private int gNoAim;
   private int gNotRobust;
   private int gNoEdge;
   private int gFar;
   private int gAngle;
   private int spoofHintAt = -100000;
   private final Set<Integer> skipWarned = new HashSet<>();
   private final Map<Integer, Integer> layerWaitSince = new HashMap<>();
   private static final int LAYER_WAIT_TICKS = 18000;
   private int beatAt;
   private int wrongScanAt;
   private List<BuildController.Candidate> ranked = List.of();
   private boolean scaffoldPass;
   private boolean floatingGoal;
   private boolean spoofGoals;
   private int walkGoalCount;
   private BuildController.PitPlan pit;
   private int pitStep;
   private int pitTicks;
   private int pitStepTicks;
   private int pitRetries;
   private boolean pitFailed;
   private static final float ROT_EPS = 0.01F;
   private float sentYaw;
   private float sentPitch;
   private boolean ackPending;
   private int pendingSince = -1;
   private int unacked;
   private final Map<BlockPos, Integer> recentPlaced = new HashMap<>();
   private boolean resumeAfterSweep;
   private PlacementSolver.SpoofAim spoof;
   private int spoofHold;
   private BlockHitResult spoofHit;
   private static final int SPOOF_HOLD_TICKS = 5;
   private final Map<BlockPos, Integer> lookUpCache = new HashMap<>();
   private int brokeSeenAt = -1;
   private BlockPos brokeSeenPos;
   private Vec3d minePoint;
   private BlockPos minePointPos;
   private boolean manualBreaking;
   private int crosshairMiss;
   private int loopProgressTick;
   private Map<BlockPos, Integer> trailDeferred;
   private Set<BlockPos> trailTemps;
   private int loopNextAction;
   private int loopRecoveries;
   private int pillarLaunchTick;
   private boolean pillarJumpIssued;
   private boolean pillarWasAirborne;
   private boolean pillarVertical;
   private Vec3d bucketStandPos;
   private BlockPos bucketBuyPos;
   private BlockPos bucketWalkFor;
   private BlockPos bucketEdgeFor;
   private boolean bucketRayWarned;
   private boolean lastPose;
   private int poseChangedAt = -1000;
   private int screenAt = -1000;
   private int bucketGateSaidAt = -1000;
   private int bucketEdgeTicks;
   private BlockPos fluidHoldSaid;
   private int buildFluidPhase;
   private int fluidHoldAt = -100000;
   private BlockPos fluidHoldFor;
   private int fluidHoldSince;
   private ApproachBudget approachBudget;
   private ClientWorld reachableWorld;
   private SchematicPlan reachablePlan;
   private Integer reachableLayer;
   private int reachablePlaced;
   private int reachablePhase;

   public BuildController(BuildConfig var1) {
      this.config = var1;
      this.orders = new OrderCollector(var1);
      this.homes = new com.donutautobuild.order.HomeManager(var1);
      this.browser = new com.donutautobuild.order.ShopBrowser(var1);
      this.seller = new com.donutautobuild.order.SellJob(var1);
      this.shop = new ShopBuyer(var1);
   }

   public boolean active() {
      return this.running;
   }

   /** 3.0.98: tin "You bought …" lúc này có phải do mod mua để xây không (đang chạy, hoặc đang tự mua ở /shop) */
   public boolean countsMoney() {
      return this.running || this.shopping || this.browsing || this.shop.active() || this.browser.active();
   }

   public boolean preparing() {
      return this.preparing;
   }

   public String status() {
      return this.status;
   }

   public BuildController.Stage stage() {
      return this.stage;
   }

   public String stageName() {
      if (this.preparing) {
         return "LOADING";
      } else if (!this.running) {
         return this.plan == null ? "IDLE" : "PAUSED";
      } else {
         return switch (this.stage) {
            case IDLE -> "IDLE";
            case LOAD -> "LOADING";
            case SELECT -> "VERIFYING";
            case GOALS, SEARCH, NEXT -> "FINDING_POSITION";
            case MOVE -> this.approachOnly ? "RETURNING" : (this.temporary == null && this.supportTarget == null ? "MOVING" : "SUPPORT");
            case EQUIP, AIM -> this.temporary == null && this.supportTarget == null ? "PLACING" : "SUPPORT";
            case WAIT_BLOCK -> "CONFIRMING";
            case ORDER -> "ORDER";
            case HOME -> "HOME";
            case CRAFTER -> "CRAFTER";
            case FILL -> "CONTAINER";
            case STASH -> "ENDER_CHEST";
            case SELL -> "SELL";
            case WAIT_MATERIAL -> "WAITING_FOR_MATERIAL";
            case CLEANUP, MINE -> "CLEANUP";
            case PIT -> "DIGGING";
         };
      }
   }

   public String placementName() {
      return this.plan == null ? "Chưa nạp placement" : this.plan.placement.getName();
   }

   public boolean hasPlan() {
      return this.plan != null && this.plan.loaded && !this.plan.targets.isEmpty();
   }

   public int total() {
      return this.total;
   }

   public int completed() {
      return this.matched;
   }

   // ===== 3.0.99: tab Home trong GUI =====

   /** các dòng mô tả home của tầng đang xây (cho GUI) */
   public List<String> homeLines() {
      ArrayList<String> var1 = new ArrayList<>();
      Integer var2 = this.currentLayer;
      var1.add(var2 == null ? "Tầng đang xây: — (nạp placement / bấm Bắt đầu trước)" : "Tầng đang xây: Y=" + var2 + " (tầng " + this.layerIndex(var2) + ")");
      boolean var3 = this.mc.world != null && ScaffoldJournal.worldKey(this.mc).equals(this.config.ownHomeWorld);
      if (this.config.ownHomeNumber <= 0) {
         var1.add("§eChưa có home nào cho mod dùng.");
      } else {
         var1.add(
            "Home đang dùng: §a" + this.config.ownHomeNumber + "§r · "
               + (this.config.ownHomeManual ? "§bcó sẵn (bạn chọn, mod không xóa)" : "§7mod tự đặt (lên tầng mới sẽ xóa rồi đặt lại)")
         );
         var1.add(
            "Đặt ở tầng "
               + (this.config.ownHomeLayer == Integer.MIN_VALUE ? "?" : "Y=" + this.config.ownHomeLayer)
               + " · tại "
               + String.format(Locale.ROOT, "%.1f %.1f %.1f", this.config.ownHomeX, this.config.ownHomeY, this.config.ownHomeZ)
               + (var3 ? "" : " · §cthế giới khác")
         );
      }

      String var4;
      if (!this.config.useHome) {
         var4 = "§cĐang TẮT dùng /home (lỡ ngã sẽ tự tìm đường / bắc cầu lên)";
      } else if (this.ownHomeUsable()) {
         var4 = "§aTầng này CÓ home dùng được → lỡ ngã sẽ /" + this.config.homeCommand + " " + this.config.ownHomeNumber + " về ngay";
      } else if (var2 == null) {
         var4 = "§7Chưa có tầng đang xây";
      } else if (this.config.ownHomeNumber <= 0 || this.config.ownHomeLayer == Integer.MIN_VALUE || !var3) {
         var4 = "§eTầng này CHƯA có home" + (this.config.autoSetHome ? " → mod sẽ tự /sethome khi đứng lên mặt tầng" : " (tự đặt home đang TẮT)");
      } else {
         var4 = "§eHome " + this.config.ownHomeNumber + " ở tầng Y=" + this.config.ownHomeLayer + " quá xa tầng đang xây (chỉ dùng được khi cách ≤ 3 tầng)"
            + (this.config.autoSetHome ? " → mod sẽ đặt home mới" : "");
      }

      var1.add(var4);
      return var1;
   }

   /** người dùng báo: home N đã có sẵn ngay chỗ đang đứng → dùng cho tầng đang xây, mod không xóa */
   public String useExistingHome(int var1) {
      if (this.mc.player == null || this.mc.world == null) {
         return "Chưa vào thế giới.";
      } else if (var1 <= 0) {
         return "Số home không hợp lệ.";
      } else {
         int var2 = this.mc.player.getBlockY() - 1;
         this.config.ownHomeNumber = var1;
         this.config.ownHomeManual = true;
         this.config.ownHomeWorld = ScaffoldJournal.worldKey(this.mc);
         this.config.ownHomeX = this.mc.player.getX();
         this.config.ownHomeY = this.mc.player.getY();
         this.config.ownHomeZ = this.mc.player.getZ();
         this.config.ownHomeLayer = this.currentLayer != null ? Math.min(this.currentLayer, var2) : var2;
         this.homeTpFailUntil = 0;
         this.config.saveQuietly();
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] Người dùng chọn home {} (có sẵn) tại {} cho tầng Y={} → mod dùng, không xóa",
               new Object[]{var1, this.mc.player.getBlockPos().toShortString(), this.config.ownHomeLayer}
            );
         return "Đã ghi: home " + var1 + " ở chỗ đang đứng dùng cho tầng Y=" + this.config.ownHomeLayer + ".";
      }
   }

   /** quên home hiện tại (mod không /home về đó nữa; bật tự đặt thì lên mặt tầng sẽ /sethome cái mới) */
   public String forgetHome() {
      this.config.ownHomeLayer = Integer.MIN_VALUE;
      if (this.config.ownHomeManual) {
         this.config.ownHomeNumber = 0;
         this.config.ownHomeManual = false;
      }

      this.homeFailUntil = 0;
      this.config.saveQuietly();
      return "Đã bỏ home cho tầng này" + (this.config.autoSetHome ? " → mod sẽ tự /sethome khi đứng lên mặt tầng." : ".");
   }

   public Integer currentLayer() {
      return this.currentLayer;
   }

   public int layerTotal() {
      return this.currentLayerTotal;
   }

   public int layerDone() {
      return this.currentLayerDone;
   }

   public int temporaryCount() {
      return this.journal == null ? 0 : this.journal.placed.size();
   }

   public List<BuildController.Blocked> blocked() {
      return this.blockedList;
   }

   public int placedCount() {
      return this.placedCount;
   }

   public int queueSize() {
      return this.queue.size();
   }

   public String modeLabel() {
      return this.config.fastMode() ? "NHANH" : "ĐÚNG";
   }

   public List<BuildController.MaterialRow> layerMaterials() {
      return this.layerMaterials;
   }

   public List<BuildController.MaterialRow> totalMaterials() {
      return this.totalMaterials;
   }

   public int minY() {
      return this.plan == null ? 0 : this.plan.minY;
   }

   public int maxY() {
      return this.plan == null ? 0 : this.plan.maxY;
   }

   public SchematicPlan.Target currentTarget() {
      return this.current();
   }

   public String targetLabel() {
      return this.current() == null ? "—" : this.current().state().getBlock().getName().getString() + " @ " + this.current().pos().toShortString();
   }

   public String progressDetail() {
      return "Bị chặn: " + this.blockedCount + " | Chưa tải: " + this.unloaded + " | Không hỗ trợ: " + this.unsupported;
   }

   public String originLabel() {
      return this.plan == null ? "" : this.plan.placement.getOrigin().toShortString();
   }

   private SchematicPlan.Target current() {
      return this.temporary != null ? this.temporary : (this.supportTarget != null ? this.supportTarget : this.target);
   }

   private double reach() {
      return Math.min(this.config.reach, this.mc.player.getBlockInteractionRange() - 0.1);
   }

   private int menuDoneAt = -100000;

   private void setStage(BuildController.Stage var1) {
      if (this.menuStage()
         && var1 != BuildController.Stage.ORDER
         && var1 != BuildController.Stage.HOME
         && var1 != BuildController.Stage.CRAFTER
         && var1 != BuildController.Stage.FILL
         && var1 != BuildController.Stage.STASH
         && var1 != BuildController.Stage.SELL) {
         // 3.0.90: vừa xong Order/Home/Crafter → vài giây sau menu server còn bật lại thì tự đóng, không dừng
         this.menuDoneAt = this.ticks;
      }

      this.stage = var1;
      this.stageTicks = 0;
      this.aimInvalidTicks = 0;
   }

   /**
    * 3.0.108: K bật/tắt "đang chạy" theo ý người dùng (wanted). Mod tự tạm dừng vì lý do gì (menu, máu, vào lại server, lỗi…)
    * thì vẫn giữ wanted = true và tự chạy lại; chỉ K (hoặc /autobuild stop) mới dừng hẳn.
    */
   public void toggle() {
      if (this.running || this.wanted) {
         this.userStop("Đã dừng (K). Nhấn K để chạy lại.");
      } else {
         this.userStart();
      }
   }

   public void userStart() {
      if (!com.donutautobuild.schematic.SchematicWorkspace.permitStart()) {
         return;
      }

      this.wanted = true;
      this.finishedAt = -100000;
      this.placementChangedAt = -100000;
      this.autoTries = 0;
      this.autoNextAt = this.ticks + 60;
      this.noPauseOnLostFocus();
      this.start();
   }

   public void userStop(String var1) {
      this.resetPlacementRecovery();
      this.wanted = false;
      this.pause(var1);
   }

   public boolean wanted() {
      return this.wanted;
   }

   public void prepare() {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.running) {
            this.pause("Nạp lại placement.");
         }

         try {
            this.plan = new SchematicPlan();
            if (this.plan.autoNote != null) {
               this.say(this.plan.autoNote + ".");
            }

            this.planWorld = this.mc.world;
            this.journal = new ScaffoldJournal(this.mc);
            this.worldKey = ScaffoldJournal.worldKey(this.mc);
            this.currentLayer = null;
            this.buildFluidPhase = 0;
            LayerView.restore();
            this.preparing = true;
            this.scan = null;
            this.total = 0;
            this.matched = 0;
            this.currentLayer = null;
            this.say("Đang nạp placement: " + this.plan.placement.getName());
         } catch (Exception var2) {
            this.plan = null;
            this.preparing = false;
            this.say(var2.getMessage());
         }
      } else {
         this.say("Vào thế giới Minecraft trước.");
      }
   }

   public boolean isRunning() {
      return this.running;
   }

   public void start() {
      if (!com.donutautobuild.schematic.SchematicWorkspace.permitStart()) {
         return;
      }

      if (this.mc.player == null || this.mc.world == null || this.mc.interactionManager == null) {
         this.say("Vào thế giới Minecraft trước.");
      } else if (this.mc.currentScreen != null) {
         this.say("Đóng menu trước khi bắt đầu.");
      } else if (!this.mc.player.isCreative() && !this.mc.player.isSpectator()) {
         try {
            boolean var1 = this.plan != null
               && this.plan.loaded
               && !this.preparing
               && this.plan.unchanged()
               && this.worldKey.equals(ScaffoldJournal.worldKey(this.mc));
            if (!var1 && this.keepPlanAfterRejoin()) {
               // 3.0.98: vừa vào lại server → giữ bản vẽ đang xây (không nạp lại từ placement Litematica vừa nạp lại)
               var1 = true;
            }

            if (!var1 && this.keepPlanSelectionLost()) {
               var1 = true;
            }

            if (!var1) {
               this.placementHint();
            }

            if (!var1) {
               this.plan = new SchematicPlan();
               if (this.plan.autoNote != null) {
                  this.say(this.plan.autoNote + ".");
               }

               this.worldKey = ScaffoldJournal.worldKey(this.mc);
               this.currentLayer = null;
               this.buildFluidPhase = 0;
               LayerView.restore();
               this.placedDone.clear();
               this.massWrongConfirmed = false;

               try {
                  this.loadDone();
               } catch (Throwable var9) {
               }
            }

            if (this.massWrongPaused) {
               this.massWrongPaused = false;
               this.massWrongConfirmed = true;
            }

            this.journal = new ScaffoldJournal(this.mc);
            int var2 = this.journal.prune(this.mc);
            if (var2 > 0) {
               AutoBuildMod.LOGGER.info("[AutoBuild] Bỏ {} khối kê cũ không còn trong thế giới khỏi sổ", var2);
            }

            this.preparing = false;
            this.target = null;
            this.temporary = null;
            this.pending = null;
            this.mining = null;
            this.aim = null;
            this.scan = null;
            this.cleanupMode = false;
            this.pillar = false;
            this.lastPlaced = null;
            this.priorityPos = null;
            this.lastProgress = this.ticks;
            this.confirmedAt = this.ticks;
            this.bestMatched = -1;
            this.placeFails = 0;
            this.failCountedAt.clear();
            this.healthWaitSince = -1;
            this.stallSaid = this.ticks;
            this.skipWarned.clear();
            this.layerWaitSince.clear();
            this.deferCount.clear();
            this.deferLogged.clear();
            this.coverFor.clear();
            this.restore.clear();
            this.coverRestored.clear();
            this.restoreTries.clear();
            this.restoringPos = null;
            this.navFailures.clear();
            this.queue.clear();
            this.verify.clear();
            this.configureClicks.clear();
            this.pit = null;
            this.repairWrong.clear();
            this.repairTries.clear();
            this.obstacles.clear();
            if (!var1) {
               // 3.0.107: chỉ quên "tầng để đào sau" khi đọc bản vẽ MỚI. Trước đây mỗi lần nhấn K đều xoá → mod lại đi xuống tầng -58
               // (log 21:12:45 "Chặn chuyển tầng -50 → -58 vì tầng -58 còn 586 khối") chui vào vùng đá, 4 lượt quét sau mới chịu lên lại
               this.digStuckLayers.clear();
               this.digStuckLayers.addAll(this.loadedDigStuck);
            }

            this.loadedDigStuck.clear();
            this.holdPos = null;
            this.closerFor.clear();
            this.cautiousUntil = -1000;
            this.postponedLayers.clear();
            this.layerRounds.clear();
            this.layerRoundPlaced.clear();
            this.escUntil = 0;
            this.escGoal = null;
            this.deadStart = null;
            this.wsPos = null;
            this.rowDeferred.clear();
            this.attempts.clear();
            this.skipped.clear();
            this.ghosts.clear();
            Navigation.GHOSTS.clear();
            this.leanFailed.clear();
            this.liftTried.clear();
            this.badStands.clear();
            this.fluidRetries.clear();
            this.permRetried.clear();
            this.stuckSkips.clear();
            this.layerFinishTries.clear();
            this.waterTries.clear();
            this.skipReason.clear();
            this.lookUpWarned.clear();
            this.depWaits.clear();
            this.retriedLayers.clear();
            this.bridgeDefers.clear();
            this.sweepSkip.clear();
            this.sweepLate.clear();
            this.sweepFailUntil.clear();
            this.sweepFails.clear();
            this.crafterRetryAt.clear();
            this.autoWater.clear();
            this.massWrongCheckedAt = -100000;
            this.realignOffset = null;
            this.sweeping = false;
            this.repairing = false;
            this.targetTicks = 0;
            this.failures.clear();
            this.deferred.clear();
            this.missing.clear();
            this.announcedMissing.clear();
            this.fullWarned.clear();
            this.lookUpCache.clear();
            this.cleanupDeferred.clear();
            this.cooldown = 0;
            this.verifiedPasses = 0;
            this.placedCount = 0;
            this.triggerCache.clear();
            this.tntHoldCache.clear();
            this.tntDefers.clear();
            this.triggersReleased = false;
            this.triggerLayers.clear();
            this.running = true;
            this.planWorld = this.mc.world;
            this.setStage(var1 ? BuildController.Stage.SELECT : BuildController.Stage.LOAD);
            if (var1) {
               this.beginScan();
            }

            this.say("Bắt đầu xây: " + this.plan.placement.getName() + this.rangeLabel());
         } catch (Exception var3) {
            this.pause(var3.getMessage());
         }
      } else {
         this.say("Mod này chạy trong Survival.");
      }
   }

   // 3.0.98: thế giới (đối tượng ClientWorld) lúc nạp/bắt đầu bản vẽ — khác đi nghĩa là vừa vào lại server
   private Object planWorld;

   private static final fi.dy.masa.malilib.gui.interfaces.IMessageConsumer QUIET = new fi.dy.masa.malilib.gui.interfaces.IMessageConsumer() {
      @Override
      public void addMessage(fi.dy.masa.malilib.gui.Message.MessageType var1, String var2, Object... var3) {
         AutoBuildMod.LOGGER.info("[AutoBuild] Litematica: {}", var2);
      }

      @Override
      public void addMessage(fi.dy.masa.malilib.gui.Message.MessageType var1, int var2, String var3, Object... var4) {
         AutoBuildMod.LOGGER.info("[AutoBuild] Litematica: {}", var3);
      }
   };

   /**
    * 3.0.98: thoát ra vào lại (tự động khi lag hoặc người chơi vào lại) → Litematica xóa hết placement rồi nạp lại từ file,
    * có lúc chọn nhầm placement khác / lệch vị trí / mất hẳn → bản cũ nạp lại bản vẽ theo placement mới → "64 khối SAI", dừng,
    * xây sai chỗ (log 20:47, 21:00, 21:22, 22:24, 23:02, 23:29, 00:39, 00:45 — lần nào vào lại cũng vậy).
    * Giờ: giữ nguyên bản vẽ đang xây trong bộ nhớ, tìm lại đúng placement đó trong Litematica (hoặc đặt lại / thêm lại) và chọn nó.
    * true = dùng tiếp bản vẽ cũ.
    */
   private boolean keepPlanAfterRejoin() {
      if (this.plan == null
         || !this.plan.loaded
         || this.preparing
         || this.mc.world == null
         || this.planWorld == null
         || this.planWorld == this.mc.world
         || this.worldKey == null
         || !this.worldKey.equals(ScaffoldJournal.worldKey(this.mc))) {
         return false;
      } else {
         try {
            fi.dy.masa.litematica.schematic.placement.SchematicPlacement var1 = this.plan.placement;
            String var2 = SchematicPlan.layoutKey(var1);
            fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager var3 = fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager();
            fi.dy.masa.litematica.schematic.placement.SchematicPlacement var4 = var3.getSelectedSchematicPlacement();
            String var5;
            if (var4 == var1) {
               var5 = null;
            } else if (var4 != null && var2.equals(SchematicPlan.layoutKey(var4))) {
               var5 = null;
            } else {
               fi.dy.masa.litematica.schematic.placement.SchematicPlacement var6 = null;
               fi.dy.masa.litematica.schematic.placement.SchematicPlacement var7 = null;
               double var8 = Double.MAX_VALUE;
               java.io.File var10 = var1.getSchematicFile();

               for (fi.dy.masa.litematica.schematic.placement.SchematicPlacement var12 : var3.getAllSchematicsPlacements()) {
                  if (var12 == var1 || var2.equals(SchematicPlan.layoutKey(var12))) {
                     var6 = var12;
                     break;
                  }

                  if (var10 != null && var10.equals(var12.getSchematicFile())) {
                     BlockPos var40 = var12.getOrigin();
                     BlockPos var41 = var1.getOrigin();
                     double var13 = (double)(var40.getX() - var41.getX()) * (var40.getX() - var41.getX())
                        + (double)(var40.getY() - var41.getY()) * (var40.getY() - var41.getY())
                        + (double)(var40.getZ() - var41.getZ()) * (var40.getZ() - var41.getZ());
                     if (var13 < var8) {
                        var8 = var13;
                        var7 = var12;
                     }
                  }
               }

               if (var6 != null) {
                  var4 = var6;
                  var5 = "Litematica chọn nhầm placement khác sau khi vào lại → chọn lại đúng placement đang xây";
               } else if (var7 != null && var8 <= 256.0 && !var7.isLocked()) {
                  // cùng file, lệch ít (≤ 16 ô) → kéo về đúng chỗ đang xây (không đụng các bản sao ở xa)
                  BlockPos var14 = var7.getOrigin();
                  var7.setOrigin(var1.getOrigin(), var0 -> AutoBuildMod.LOGGER.info("[AutoBuild] Litematica: {}", var0));
                  if (var7.getRotation() != var1.getRotation()) {
                     var7.setRotation(var1.getRotation(), QUIET);
                  }

                  if (var7.getMirror() != var1.getMirror()) {
                     var7.setMirror(var1.getMirror(), QUIET);
                  }

                  var4 = var7;
                  var5 = "Litematica nạp placement lệch (" + var14.toShortString() + " → đúng chỗ đang xây " + var1.getOrigin().toShortString() + ") → đã đặt lại";
               } else {
                  fi.dy.masa.litematica.schematic.placement.SchematicPlacement var15 = null;

                  try {
                     com.google.gson.JsonObject var16 = var1.toJson();
                     var15 = var16 == null ? null : fi.dy.masa.litematica.schematic.placement.SchematicPlacement.fromJson(var16);
                  } catch (Throwable var17) {
                     var15 = null;
                  }

                  if (var15 == null) {
                     var15 = var1;
                  }

                  var3.addSchematicPlacement(var15, false);
                  var4 = var15;
                  var5 = "Litematica mất placement đang xây sau khi vào lại → đã tự thêm lại đúng chỗ " + var1.getOrigin().toShortString();
               }
            }

            if (var4 != var3.getSelectedSchematicPlacement()) {
               var3.setSelectedSchematicPlacement(var4);
            }

            if (!var4.isEnabled()) {
               var4.setEnabled(true);
            }

            if (!var2.equals(SchematicPlan.layoutKey(var4))) {
               AutoBuildMod.LOGGER.warn("[AutoBuild] Placement sau khi vào lại vẫn khác bản vẽ đang xây ({} ≠ {}) → vẫn xây theo bản vẽ cũ", SchematicPlan.layoutKey(var4), var2);
            }

            this.plan.rebind(var4);
            if (var5 != null) {
               this.say(var5 + ".");
            } else {
               AutoBuildMod.LOGGER.info("[AutoBuild] Vào lại server: Litematica nạp lại đúng placement {} → giữ nguyên bản vẽ đang xây", var1.getName());
            }

            return true;
         } catch (Throwable var18) {
            AutoBuildMod.LOGGER.warn("[AutoBuild] Không giữ được bản vẽ sau khi vào lại ({}) → nạp lại từ Litematica", var18.toString());
            return false;
         }
      }
   }

   public String rangeLabel() {
      return this.config.yStart == null && this.config.yEnd == null
         ? ""
         : " (Y " + (this.config.yStart == null ? "…" : this.config.yStart) + " → " + (this.config.yEnd == null ? "…" : this.config.yEnd) + ")";
   }

   public void cleanup() {
      if (this.mc.player != null && this.mc.world != null && this.mc.currentScreen == null) {
         if (this.running) {
            this.pause("Chuyển sang thu dọn khối kê.");
         }

         // 3.0.108: thu dọn là chế độ riêng do người dùng bấm → xong thì thôi, không tự quay lại xây
         this.wanted = false;

         this.journal = new ScaffoldJournal(this.mc);
         this.worldKey = ScaffoldJournal.worldKey(this.mc);
         this.cleanupDeferred.clear();
         this.sweepSkip.clear();
         this.cleanupPasses = 0;
         this.reachable = null;
         this.cleanupMode = true;
         this.temporary = null;
         this.pending = null;
         this.running = true;
         this.setStage(BuildController.Stage.CLEANUP);
      } else {
         this.say("Vào game và đóng menu trước.");
      }
   }

   public void pause(String var1) {
      this.cancelSurvivalSupplies();
      // 3.0.122: cú chuột phải chưa được game xử lý thì bỏ (không để người chơi bấm ra cú đặt của mod)
      Input.cancelUse(this.mc);
      this.qualityAttackPos = null;
      if (this.running) {
         this.saveDone(true);
      }

      if (this.eating) {
         Input.key(this.mc.options.useKey, false);
         this.eating = false;
      }

      this.running = false;
      this.pillar = false;
      this.repairing = false;
      this.sweeping = false;
      this.pit = null;
      this.status = var1 == null ? "Đã dừng." : var1;
      if (this.wanted && (this.status.contains("nhấn K") || this.status.contains("Nhấn K"))) {
         this.status = this.status + " (mod vẫn bật: hết lý do dừng là tự chạy lại; K để dừng hẳn)";
      }

      if (this.wanted) {
         // 3.0.108: tự chạy lại sớm nhất sau 3 giây (không bật lại ngay trong tick kế tiếp)
         this.autoNextAt = Math.max(this.autoNextAt, this.ticks + 60);
      }

      this.release();
      Input.forceStand(this.mc);
      this.orders.cancel(this.mc);
      this.shop.cancel(this.mc);
      this.homes.cancel(this.mc);
      this.browser.cancel(this.mc);
      this.seller.cancel(this.mc);
      if (this.stashStep >= 0 && this.stashPlaced && this.stashPos != null) {
         // dừng giữa lúc dùng ender chest → nhớ chỗ để lần sau đập thu lại
         this.stashLeft = this.stashPos;
      }

      this.stashStep = -1;
      this.forceTool = null;
      this.browsing = false;
      this.shopping = false;
      Input.restoreBreaking(this.mc);
      if (this.mc.interactionManager != null) {
         this.mc.interactionManager.cancelBlockBreaking();
      }

      LayerView.hold(this.config.syncLitematicaLayer && this.config.layerMode && !this.cleanupMode ? this.currentLayer : null);
      this.say(this.status);
   }

   public void pauseAndSave() {
      if (this.plan == null) {
         this.say("Chưa có placement để lưu.");
      } else {
         this.userStop("Đã dừng và lưu tiến độ.");
         SessionStore.save(
            this.worldKey, this.plan.placement.getName(), this.plan.placement.getOrigin(), this.config.yStart, this.config.yEnd, this.currentLayer
         );
      }
   }

   public void restore() {
      if (this.mc.player != null && this.mc.world != null) {
         SessionStore.Session var1 = SessionStore.load();
         if (var1 == null) {
            this.say("Chưa có bản lưu.");
         } else if (!var1.world().equals(ScaffoldJournal.worldKey(this.mc))) {
            this.say("Bản lưu thuộc server/thế giới khác: " + var1.world());
         } else {
            try {
               SchematicPlan var2 = new SchematicPlan();
               if (!var2.placement.getName().equals(var1.placement()) || !var2.placement.getOrigin().equals(var1.origin())) {
                  this.say("Hãy chọn placement \"" + var1.placement() + "\" tại " + var1.origin().toShortString() + " trong Litematica rồi thử lại.");
                  return;
               }
            } catch (Exception var3) {
               this.say(var3.getMessage());
               return;
            }

            this.config.yStart = var1.yStart();
            this.config.yEnd = var1.yEnd();
            this.config.saveQuietly();
            this.say("Đã khôi phục bản lưu" + (var1.layer() == null ? "" : ", tầng Y=" + var1.layer()) + ".");
            this.plan = null;
            this.mc.send(this::userStart);
         }
      } else {
         this.say("Vào thế giới Minecraft trước.");
      }
   }

   private void say(String var1) {
      this.status = var1;
      AutoBuildMod.LOGGER.info("[AutoBuild] {}", var1);
      if (this.mc.player != null) {
         this.mc.player.sendMessage(Text.literal("§b[AutoBuild] §f" + var1), false);
      }
   }

   private void release() {
      Input.key(this.mc.options.forwardKey, false);
      Input.key(this.mc.options.backKey, false);
      Input.key(this.mc.options.leftKey, false);
      Input.key(this.mc.options.rightKey, false);
      Input.key(this.mc.options.jumpKey, false);
      Input.sprint(this.mc, false, this.ticks);
      Input.key(this.mc.options.attackKey, false);
      Input.sneak(this.mc, this.running && this.overhanging(), this.ticks);
   }

   private boolean overhanging() {
      if (this.mc.player != null && this.mc.world != null && this.mc.player.isOnGround() && this.mc.player.isSneaking()) {
         BlockPos var1 = BlockPos.ofFloored(this.mc.player.getX(), this.mc.player.getY() - 0.2, this.mc.player.getZ());
         return this.mc.world.getBlockState(var1).getCollisionShape(this.mc.world, var1).isEmpty();
      } else {
         return false;
      }
   }

   private boolean overBudget() {
      return System.nanoTime() > this.tickDeadline;
   }

   public void tick() {
      if (Freecam.active()) {
         return;
      }

      LayerView.clientTick(this.mc);
      long var1 = System.nanoTime();
      // 3.0.122 (người dùng: "tìm đường, xử lý đang rất chậm"): 10 ms → 20 ms tính toán mỗi tick (1 tick = 50 ms)
      this.tickDeadline = var1 + 20000000L;
      this.heavyLabel = "";
      BuildController.Stage var3 = this.stage;
      Input.restoreBreaking(this.mc);
      this.manualBreaking = false;

      try {
         this.tickInner();
      } finally {
         if (this.manualBreaking && this.running) {
            Input.hideBreaking(this.mc);
         }

         long var7 = (System.nanoTime() - var1) / 1000000L;
         if (var7 >= 30L && this.ticks - this.slowWarnTick > 40) {
            this.slowWarnTick = this.ticks;
            AutoBuildMod.LOGGER
               .warn(
                  "[AutoBuild] Tick chậm {} ms (giai đoạn {} → {}{})",
                  new Object[]{var7, var3, this.stage, this.heavyLabel.isEmpty() ? "" : ", " + this.heavyLabel}
               );
         }
      }
   }

   private void qualityTickInnerOriginal() {
      this.ticks++;
      if (this.survivalSupplyTick()) {
         return;
      }

      if (this.mc.player != null) {
         this.sentYaw = this.mc.player.getYaw();
         this.sentPitch = this.mc.player.getPitch();
      }

      this.unacked = this.mc.world == null ? 0 : ServerAck.pendingCount(this.mc);
      long var91 = System.currentTimeMillis();
      if (this.unacked > 0 && this.rttStart < 0L) {
         this.rttStart = var91;
      } else if (this.unacked == 0 && this.rttStart >= 0L) {
         long var93 = var91 - this.rttStart;
         this.rttStart = -1L;
         if (var93 > 0L && var93 < 3000L) {
            this.rttMs = this.rttMs <= 0 ? (int)var93 : (int)(this.rttMs * 0.7 + var93 * 0.3);
         }
      }

      // 3.0.96: chỉ chờ sau khi CHUYỂN đồ từ túi lên thanh nóng (SWAP); đổi ô thanh nóng thì không chờ (xem InventoryAccess.equip)
      // 3.0.121: chờ trọn 1 lượt ping + 100 ms (trước: tối đa 300 ms trong khi ping 300–500 ms → bản sửa của server về SAU lúc đã đặt)
      InventoryAccess.settleMs = Math.max(250L, Math.min(1200L, (long)this.pingMs() + 100L));
      InventoryAccess.rttMs = Math.max(100L, Math.min(2000L, (long)this.pingMs()));
      this.moveWatch();
      this.lowerLayerWatch();
      this.tntScanTick();
      // 3.0.121: cả lúc chỉ tạm dừng (wanted) cũng được xin đồng bộ túi đồ — vd. vừa mua đồ ăn lúc máu thấp rồi cần cầm lên ăn
      InventoryAccess.setMenuBusy(this.menuBusyNow());
      InventoryAccess.menuTick(this.mc, this.running || this.wanted);
      this.stuckWatch();
      this.doneSaveTick();
      if (this.unacked > 0) {
         if (this.pendingSince < 0) {
            this.pendingSince = this.ticks;
         }

         if (this.ticks - this.pendingSince > 60) {
            if (this.ticks - this.pendingSince == 61) {
               AutoBuildMod.LOGGER.warn("[AutoBuild] {} ô chờ server xác nhận quá 3 giây → bỏ qua chờ", this.unacked);
            }

            this.unacked = 0;
         }
      } else {
         this.pendingSince = -1;
      }

      this.ackPending = this.unacked > 0;
      if (!this.running) {
         this.autoResumeTick();
         if (!this.running) {
            this.idleTick();
         }
      } else {
         this.stageTicks++;
         this.ghostTick();
         if (this.mc.player != null) {
            boolean var90 = this.mc.player.isInSneakingPose();
            if (var90 != this.lastPose) {
               this.lastPose = var90;
               this.poseChangedAt = this.ticks;
            }

            if (this.mc.currentScreen != null) {
               this.screenAt = this.ticks;
            }
         }
         if (this.cooldown > 0) {
            this.cooldown--;
         }

         if (this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
            if (!this.worldKey.equals(ScaffoldJournal.worldKey(this.mc))) {
               this.pause("Đã đổi thế giới/server; chọn lại placement.");
            } else if (!this.mc.player.isAlive()
               || this.mc.player.getHealth() <= (float)this.config.minHealth && !this.inSnowNow() && this.ticks >= this.healthWaiveUntil
               || this.mc.player.isInLava()
               || this.mc.player.hasVehicle()) {
               // 3.0.108: tạm dừng rồi autoResumeTick() tự xử lý (hồi sinh / chờ hồi máu / xuống xe / nhảy khỏi lava) và tự chạy lại
               this.pause(
                  !this.mc.player.isAlive()
                     ? "Nhân vật chết → tự hồi sinh rồi xây tiếp."
                     : (this.mc.player.isInLava() ? "Rơi vào lava → thoát ra rồi xây tiếp." : (this.mc.player.hasVehicle() ? "Đang ngồi trên xe/thuyền → xuống rồi xây tiếp." : "Máu thấp → chờ hồi máu rồi tự xây tiếp."))
               );
            } else if (this.mc.player.isCreative() || this.mc.player.isSpectator()) {
               this.pause("Đã rời chế độ Survival.");
            } else if (this.plan != null && this.stage != BuildController.Stage.LOAD && this.ticks % 5 == 0 && !this.plan.unchanged()) {
               this.currentLayer = null;
               this.placementChangedAt = this.ticks;
               this.pause("Placement đã thay đổi → chờ 30 giây không đổi nữa rồi tự đọc lại tọa độ/hướng.");
            } else if (this.mc.currentScreen instanceof AbstractSignEditScreen && !this.browsing && !this.browser.active()) {
               // 3.0.99: màn hình sửa chữ của BẢNG (bật ra sau khi đặt bảng) → đóng NGAY ở mọi bước, kể cả khi server lag làm nó
               // bật ra muộn (> 3 giây sau cú click) hoặc lúc đang /home, mua đồ… Trước đây chỉ đóng trong 3 giây sau click → đứng chờ mãi.
               // 3.0.109: TRỪ lúc đang tìm hàng ở /shop — biển "Search Order Items" / "Amount" là của shop, ShopBrowser tự ghi chữ vào.
               // Lỗi 3.0.99–3.0.108 (log 29/09 22:27:34): bấm Search → biển hiện → nhánh này đóng ngay → "bấm mà không hiện biển nhập chữ"
               // → không bao giờ tìm được Obsidian trong /shop.
               this.mc.currentScreen.close();
               if (this.mc.currentScreen instanceof AbstractSignEditScreen) {
                  this.mc.setScreen(null);
               }

               if (this.ticks - this.signCloseSaidAt > 100) {
                  this.signCloseSaidAt = this.ticks;
                  AutoBuildMod.LOGGER.info("[AutoBuild] Tự đóng bảng sửa chữ (sign) sau khi đặt bảng ({} tick sau lần click) → đặt tiếp", this.ticks - this.clickTick);
               }
            } else if (!this.menuStage()
               && (this.mc.currentScreen instanceof ChatScreen || this.mc.currentScreen instanceof GameMenuScreen || this.mc.currentScreen instanceof InventoryScreen)) {
               // 3.0.108: người chơi mở chat / ESC / túi đồ → chỉ đứng chờ (không dừng), đóng là xây tiếp ngay
               this.release();
               this.status = "Đang mở chat/menu — đóng là mod xây tiếp (K để dừng hẳn)";
            } else if (!this.menuStage() && this.mc.currentScreen != null) {
               if ((this.ticks - this.clickTick <= 60 || this.ticks - this.menuDoneAt <= 100) && this.closeAccidentalScreen()) {
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Tự đóng màn hình bật ra sau khi click: {}",
                        this.mc.currentScreen == null ? "-" : this.mc.currentScreen.getClass().getName()
                     );
               } else {
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Dừng vì màn hình đang mở: {} ({} tick sau lần click cuối)",
                        this.mc.currentScreen.getClass().getName(),
                        this.ticks - this.clickTick
                     );
                  this.pause("Bạn đang mở menu; đóng menu rồi nhấn K để tiếp tục.");
               }
            } else if (this.menuStage()
               || this.mc.currentScreen != null
               || this.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
               this.qualityReleaseForTick();
               if (this.ticks % 40 == 7 && !this.dropBuckets && this.bucketStacks() >= 2) {
                  this.dropBuckets = true;
               }

               if (!this.dropBuckets
                  || this.menuStage()
                  || this.mc.currentScreen != null
                  || this.ticks - this.clickTick < 3
                  || this.ticks % 2 != 0
                  || this.mc.player.getVelocity().horizontalLengthSquared() > 0.01
                  || !this.stackBuckets()) {
                  this.verifyPlacements();
                  if (!this.running) {
                     // 3.0.108: vừa đủ số lần đặt hỏng liên tiếp → đã xin thoát ra vào lại, không làm gì thêm trong tick này
                     return;
                  }

                  this.scanWrongNear();
                  this.heartbeat();
                  this.stallCheck();
                  if (this.mc.player.isSubmergedInWater()) {
                     // 3.0.81: lỡ rơi xuống nước → bơi lên, không dừng
                     Input.key(this.mc.options.jumpKey, true);
                     this.status = "Rơi xuống nước → bơi lên";
                  }

                  if (this.homes.active() && this.stage != BuildController.Stage.HOME) {
                     // việc /home bị ngắt giữa chừng (đổi giai đoạn) → hủy, đóng menu home, không để menu treo
                     this.homes.cancel(this.mc);
                  }

                  if (!this.realignStep() && !this.scoopTick() && !this.snowEscape() && !this.autoEat() && !this.ghostEscape() && !this.stuckEscapeTick() && !this.breakLoopIfStalled()) {
                     try {
                        switch (this.stage) {
                           case LOAD:
                              this.status = "Đang đọc schematic: " + this.plan.scanned + "/" + this.plan.volume + " ô";
                              if (this.plan.loadTick()) {
                                 if (this.plan.targets.isEmpty()) {
                                    this.finishedAt = this.ticks;
                                    this.pause("Placement không có block để xây.");
                                 } else {
                                    this.beginScan();
                                 }
                              }
                              break;
                           case SELECT:
                              this.select();
                              break;
                           case GOALS:
                              this.collectGoals();
                              break;
                           case SEARCH:
                              this.search();
                              break;
                           case MOVE:
                              this.move();
                              break;
                           case EQUIP:
                              this.equip();
                              break;
                           case AIM:
                              this.aim();
                              break;
                           case WAIT_BLOCK:
                              this.waitBlock();
                              break;
                           case ORDER:
                              this.orderTick();
                              break;
                           case HOME:
                              this.homeTick();
                              break;
                           case CRAFTER:
                              this.crafterTick();
                              break;
                           case FILL:
                              this.fillTick();
                              break;
                           case STASH:
                              this.stashTick();
                              break;
                           case SELL:
                              this.sellTick();
                              break;
                           case WAIT_MATERIAL:
                              if (this.stageTicks >= 10) {
                                 this.beginScan();
                              }
                              break;
                           case CLEANUP:
                              this.cleanupTick();
                              break;
                           case MINE:
                              this.mineTick();
                              break;
                           case PIT:
                              this.pitTick();
                              break;
                           case NEXT:
                              this.heavyLabel = "chọn khối";
                              this.nextTarget(true);
                        }

                        if (this.running && this.mc.player != null) {
                           this.yawHistory[2] = this.yawHistory[1];
                           this.yawHistory[1] = this.yawHistory[0];
                           this.yawHistory[0] = this.mc.player.getYaw();
                           boolean var1 = this.stage == BuildController.Stage.GOALS
                              || this.stage == BuildController.Stage.SEARCH
                              || this.stage == BuildController.Stage.EQUIP
                              || this.stage == BuildController.Stage.AIM
                              || this.stage == BuildController.Stage.WAIT_BLOCK
                              || this.stage == BuildController.Stage.MOVE && !this.approachOnly;
                           int var30 = this.stage != BuildController.Stage.MOVE
                                 && this.stage != BuildController.Stage.SEARCH
                                 && this.stage != BuildController.Stage.GOALS
                                 && this.temporary == null
                                 && this.supportTarget == null
                              ? 100
                              : 400;
                           if (this.target != null
                              && this.mc.world != null
                              && this.mc.world.isChunkLoaded(this.target.pos())
                              && BucketRules.placeableBucket(this.mc.world.getBlockState(this.target.pos()), this.target.state())) {
                              var30 = 600;
                           }

                           if (!this.cleanupMode && !this.repairing && !this.sweeping && this.target != null && var1 && ++this.targetTicks > var30) {
                              this.giveUpTarget();
                           }
                        }
                     } catch (Exception var2) {
                        AutoBuildMod.LOGGER.error("Builder error", var2);
                        if (this.ticks - this.crashWindowAt > 1200) {
                           this.crashWindowAt = this.ticks;
                           this.crashCount = 0;
                        }

                        if (++this.crashCount > 12) {
                           this.pause("Lỗi lặp lại liên tục: " + var2.getClass().getSimpleName() + " — " + var2.getMessage());
                        } else {
                           // 3.0.81: không dừng vì 1 lỗi — bỏ nhánh đang làm, quét lại rồi xây tiếp
                           AutoBuildMod.LOGGER.info("[AutoBuild] Lỗi {} → bỏ nhánh hiện tại, quét lại và xây tiếp (không dừng)", var2.getClass().getSimpleName());
                           this.release();
                           this.mining = null;
                           this.repairing = false;
                           this.sweeping = false;
                           this.pit = null;
                           this.search = null;
                           this.beginScan();
                        }
                     }
                  }
               }
            } else if (!this.putCursorAway() && !this.dropCursor()) {
               this.pause("Túi đầy và đang cầm đồ trên con trỏ — dọn túi rồi nhấn K.");
            }
         } else if (Reconnector.requestLost(this.mc, "Mất kết nối với server")) {
            this.pause("Mất kết nối → tự vào lại server, vào xong tự xây tiếp.");
         } else {
            this.pause(this.wanted ? "Mất kết nối → vào lại server là mod tự xây tiếp." : "Mất kết nối; nhấn K sau khi vào lại.");
         }
      }
   }

   private boolean settleOnArrival() {
      PathSearch.Cell var1 = this.path != null && !this.path.isEmpty() ? this.path.getLast() : null;
      if (var1 != null && this.mc.player.isOnGround()) {
         Vec3d var2 = Navigation.feet(var1);
         double var3 = var2.x - this.mc.player.getX();
         double var5 = var2.z - this.mc.player.getZ();
         boolean var7 = this.mc.player.getVelocity().horizontalLengthSquared() > 1.0E-4;
         double var8 = var3 * var3 + var5 * var5;
         if (var8 > 1.0 || Math.abs(this.mc.player.getY() - (double)var1.y()) > 0.6) {
            return false;
         } else if (var8 > 0.0144) {
            this.creepToward(var2);
            this.status = "Vào giữa chỗ đứng";
            return true;
         } else if (var7) {
            this.status = "Đợi đứng yên";
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void gate(String var1) {
      if (this.gates.size() < 12 || this.gates.containsKey(var1)) {
         this.gates.merge(var1, 1, Integer::sum);
      }
   }

   private String describeCrosshair() {
      HitResult var1 = this.mc.crosshairTarget;
      if (var1 == null) {
         return "không có";
      } else {
         if (var1 instanceof BlockHitResult var2 && var1.getType() == Type.BLOCK) {
            return var2.getBlockPos().toShortString() + " " + var2.getSide().asString();
         }

         return var1.getType().name();
      }
   }

   private void logAimFailure(SchematicPlan.Target var1, boolean var2) {
      if (this.ticks - this.aimLogTick >= 20) {
         this.aimLogTick = this.ticks;
         StringBuilder var3 = new StringBuilder();
         int var4 = PlacementSolver.supportMask(this.mc, var1);

         for (Direction var8 : Direction.values()) {
            if ((var4 & 1 << var8.ordinal()) != 0) {
               BlockPos var9 = var1.pos().offset(var8);
               var3.append(var8.asString()).append('=').append(this.mc.world.getBlockState(var9).getBlock().getName().getString()).append(' ');
            }
         }

         Vec3d var10 = this.mc.player.getEyePos();
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] Chưa đặt được {} @ {}: có góc={}, chặn: {} | mắt {} {} {} (ô {}), ngồi={}, dưới chân={}, chìa mép={}, mép={}, ping={}ms, bám đúng hướng: {}",
               new Object[]{
                  var1.state().getBlock().getName().getString(),
                  var1.pos().toShortString(),
                  this.aim != null,
                  this.gates.isEmpty() ? "-" : this.gates.toString(),
                  String.format("%.2f", var10.x),
                  String.format("%.2f", var10.y),
                  String.format("%.2f", var10.z),
                  this.mc.player.getBlockPos().toShortString(),
                  this.mc.player.isSneaking(),
                  var2,
                  this.overhanging(),
                  this.edgeSpot(var1.pos()),
                  this.pingMs(),
                  var3.toString().trim()
               }
            );
         this.gates.clear();
      }
   }

   private void giveUpTarget() {
      SchematicPlan.Target var1 = this.target;
      if (this.stage == BuildController.Stage.AIM && var1 != null) {
         this.logAimFailure(var1, var1.pos().getY() < this.mc.player.getBlockY());
      }

      AutoBuildMod.LOGGER
         .info(
            "[AutoBuild] Quá lâu chưa đặt được {} @ {} ({}), để lại sau",
            new Object[]{var1.state().getBlock().getName().getString(), var1.pos().toShortString(), this.stage}
         );
      this.deferred.put(var1.pos(), this.ticks + 300);
      this.attempt(var1);
      this.release();
      this.nextTarget(true);
   }

   private void idleTick() {
      if (this.mc.player != null && this.mc.world != null && this.plan != null) {
         if (this.preparing) {
            this.status = "Đang đọc schematic: " + this.plan.scanned + "/" + this.plan.volume + " ô";

            try {
               if (this.plan.loadTick()) {
                  this.preparing = false;
                  this.scan = new BuildController.Scan(false);
                  this.say("Đã nạp " + this.plan.targets.size() + " block, Y " + this.plan.minY + " → " + this.plan.maxY + ".");
               }
            } catch (Exception var2) {
               this.preparing = false;
               this.plan = null;
               this.say(var2.getMessage());
            }
         } else if (this.worldKey.equals(ScaffoldJournal.worldKey(this.mc))) {
            if (this.ticks % 20 == 0 && !this.plan.unchanged()) {
               if (this.currentLayer != null || LayerView.viewing() != null && LayerView.manual() == null) {
                  this.currentLayer = null;
                  LayerView.restore();
               }

               this.status = "Placement đã thay đổi — bấm \"Nạp placement đã chọn\".";
            } else {
               if (this.scan == null && this.ticks - this.lastIdleScan >= 40) {
                  this.scan = new BuildController.Scan(false);
                  this.lastIdleScan = this.ticks;
               }

               if (this.scan != null && this.scan.advance()) {
                  this.publish(this.scan);
                  this.scan = null;
               }
            }
         }
      }
   }

   private int signCloseSaidAt = -1000;

   private boolean closeAccidentalScreen() {
      Screen var1 = this.mc.currentScreen;
      if (var1 instanceof AbstractSignEditScreen) {
         var1.close();
         return true;
      } else if (var1 instanceof HandledScreen && !(var1 instanceof InventoryScreen)) {
         this.mc.player.closeHandledScreen();
         return true;
      } else {
         return false;
      }
   }

   private boolean substitutable(BlockState var1) {
      return this.config.fastMode()
         && (Materials.decorative(var1) || Materials.family(var1.getBlock()) != null)
         && !Materials.bucket(Blocks.AIR.getDefaultState(), var1);
   }

    private boolean done(BlockState actual, BlockState wanted) {
        return BuildQuality.done(actual, wanted, config.fastMode());
    }



   private Block familyMember(BlockState var1) {
      String var2 = Materials.family(var1.getBlock());
      if (var2 == null) {
         return null;
      } else {
         Block var3 = null;
         int var4 = 0;

         for (Block var6 : Materials.members(var2)) {
            int var7 = InventoryAccess.count(this.mc, var6.asItem());
            if (var7 > var4) {
               var4 = var7;
               var3 = var6;
            }
         }

         return var3;
      }
   }

   private BlockState fillerState(BlockState var1) {
      BlockState var2 = null;

      for (String var4 : this.config.fillerItems) {
         Identifier var5 = Identifier.tryParse(var4);
         if (var5 != null) {
            Block var6 = (Block)Registries.BLOCK.get(var5);
            if (var6 != Blocks.AIR && Materials.swappable(var1, var6.getDefaultState()) && InventoryAccess.count(this.mc, var6.asItem()) > 0) {
               return var6.getDefaultState();
            }
         }
      }

      int var8 = 0;

      for (int var9 = 0; var9 < 36; var9++) {
         ItemStack var10 = this.mc.player.getInventory().getStack(var9);
         Item var7 = var10.getItem();
         if (var7 instanceof BlockItem) {
            BlockItem var11 = (BlockItem)var7;
            if (Materials.swappable(var1, var11.getBlock().getDefaultState())) {
               int var12 = InventoryAccess.count(this.mc, var11);
               if (var12 > var8) {
                  var8 = var12;
                  var2 = var11.getBlock().getDefaultState();
               }
            }
         }
      }

      return var2;
   }

   private Item materialFor(SchematicPlan.Target var1, BlockState var2) {
      Item var3 = Materials.item(var2, var1.state());
      if (!this.config.fastMode() || InventoryAccess.count(this.mc, var3) > 0 || Materials.bucket(var2, var1.state())) {
         return var3;
      } else if (!Materials.decorative(var1.state())) {
         Block var7 = this.familyMember(var1.state());
         return var7 == null ? var3 : var7.asItem();
      } else {
         BlockState var4 = this.fillerState(var1.state());
         if (var4 != null) {
            return var4.getBlock().asItem();
         } else {
            Identifier var5 = Identifier.tryParse(this.config.fillerOrderItem);
            Block var6 = var5 == null ? Blocks.AIR : (Block)Registries.BLOCK.get(var5);
            return var6 != Blocks.AIR && Materials.swappable(var1.state(), var6.getDefaultState()) ? var6.asItem() : var3;
         }
      }
   }

   private SchematicPlan.Target resolve(SchematicPlan.Target var1) {
      if (!this.substitutable(var1.state())) {
         return var1;
      } else if (InventoryAccess.count(this.mc, var1.state().getBlock().asItem()) > 0) {
         return var1;
      } else if (Materials.decorative(var1.state())) {
         BlockState var3 = this.fillerState(var1.state());
         return var3 == null ? var1 : new SchematicPlan.Target(var1.pos(), var3);
      } else {
         Block var2 = this.familyMember(var1.state());
         return var2 != null && var2 != var1.state().getBlock() ? new SchematicPlan.Target(var1.pos(), Materials.copyProperties(var1.state(), var2)) : var1;
      }
   }

   private BlockState stateAt(BlockPos var1) {
      return this.mc.world.isChunkLoaded(var1) ? this.mc.world.getBlockState(var1) : Blocks.AIR.getDefaultState();
   }

   private int unsupSaidAt = -100000;

   /** 3.0.89: phần chưa khớp chỉ còn ô mod không tự đặt (nước/lava chảy, khối không có vật phẩm) — không còn ô nào xây/đào/đợi được */
   private boolean onlyUnsupportedLeft(BuildController.Scan var1) {
      if (var1.unsupported > 0 && var1.blocked == 0 && var1.unloaded == 0 && var1.triggerLeft == 0 && this.postponedLayers.isEmpty()) {
         for (LayerPlanner.Stat var3 : var1.layers.values()) {
            if (var3.remaining > 0) {
               return false;
            }
         }

         for (String var5 : var1.unsupportedKinds.keySet()) {
            if (var5.contains("kích hoạt piston")) {
               return false;
            }
         }

         return var1.matched + var1.unsupported >= var1.total;
      } else {
         return false;
      }
   }

   private String unsupportedLabel(BuildController.Scan var1) {
      StringBuilder var2 = new StringBuilder();
      int var3 = 0;

      for (Entry<String, Integer> var5 : var1.unsupportedKinds.entrySet()) {
         if (var3++ >= 4) {
            var2.append(", …");
            break;
         }

         if (var2.length() > 0) {
            var2.append(", ");
         }

         BlockPos var6 = var1.unsupportedAt.get(var5.getKey());
         var2.append(var5.getKey()).append(" ×").append(var5.getValue()).append(var6 == null ? "" : " vd. " + var6.toShortString());
      }

      return var2.length() == 0 ? "-" : var2.toString();
   }

   private void publish(BuildController.Scan var1) {
      this.total = var1.total;
      this.matched = var1.matched;
      this.blockedCount = var1.blocked;
      this.unloaded = var1.unloaded;
      this.unsupported = var1.unsupported;
      this.blockedList = List.copyOf(var1.blockedList);
      LayerPlanner.Choice var2 = LayerPlanner.choose(var1.layers, this.config.skipLayerWhenMissing);
      Integer var3 = var2.layer();
      Integer var4 = this.currentLayer;
      if (this.config.layerMode && var3 != null && var4 != null && var3 > var4 && this.journal != null && !this.cleanupMode && this.mc.world != null) {
         int var5 = 0;

         for (BlockPos var7 : new ArrayList<>(this.journal.placed.keySet())) {
            if (var7.getY() <= var4 && (this.plan == null || this.plan.at(var7) == null) && !this.sweepLate.contains(var7)) {
               this.sweepSkip.remove(var7);
               this.cleanupDeferred.remove(var7);
               if (this.sweepNow.add(var7.toImmutable())) {
                  var5++;
               }
            }
         }

         if (var5 > 0) {
            AutoBuildMod.LOGGER.info("[AutoBuild] Lên tầng Y={} → dọn nốt {} khối kê còn sót ở tầng dưới", var3, var5);
            this.say("Lên tầng Y=" + var3 + ": dọn nốt " + var5 + " khối kê tầng dưới trước.");
         }
      }

      if (this.config.layerMode) {
         this.currentLayer = var3;
         if (var3 != null) {
            Map<Item, Integer> var8 = var1.layerAll.getOrDefault(var3, Map.of());
            this.currentLayerTotal = var8.values().stream().mapToInt(Integer::intValue).sum();
            this.currentLayerDone = Math.max(0, this.currentLayerTotal - var1.layers.get(var3).remaining);
         } else {
            this.currentLayerTotal = 0;
            this.currentLayerDone = 0;
         }
      } else {
         this.currentLayer = null;
         this.currentLayerTotal = this.total;
         this.currentLayerDone = this.matched;
      }

      if (this.running && this.config.layerMode && this.config.syncLitematicaLayer && var3 != null && !this.cleanupMode) {
         com.donutautobuild.schematic.SchematicWorkspace.syncLayer(var3);
      }

      if (this.config.fixWrong && !var1.wrongFound.isEmpty()) {
         int var9 = 0;

         for (Entry var11 : var1.wrongFound.entrySet()) {
            if (!this.repairWrong.containsKey(var11.getKey())
               && !this.skipped.contains(var11.getKey())
               && this.repairTries.getOrDefault(var11.getKey(), 0) <= 4) {
               this.noteWrong((BlockPos)var11.getKey());
               this.placedDone.remove(var11.getKey());
               this.repairWrong.put((BlockPos)var11.getKey(), (BlockState)var11.getValue());
               var9++;
            }
         }

         if (var9 > 0) {
            int var12 = 0;

            for (BlockPos var13 : this.repairWrong.keySet()) {
               if (this.obstacles.contains(var13)) {
                  var12++;
               }
            }

            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Quét thấy {} khối SAI trong công trình ({} khối đá/đất có sẵn chắn chỗ → xây chỗ trống trước, đào sau; còn lại sửa ngay)",
                  var9,
                  var12
               );
         }
      }

      this.layerMaterials = this.rows(var3 == null ? Map.of() : var1.layerNeed.getOrDefault(var3, Map.of()), var1);
      this.totalMaterials = this.rows(var1.totalNeed, var1);
      this.enforceStrictLayer(var1);
   }

   private List<BuildController.MaterialRow> rows(Map<Item, Integer> var1, BuildController.Scan var2) {
      ArrayList var3 = new ArrayList();
      var1.forEach((var2x, var3x) -> var3.add(new BuildController.MaterialRow(var2x, var3x, var2.have(var2x))));
      var3.sort(
         Comparator.<BuildController.MaterialRow>comparingInt(var0 -> -(var0.needed() - Math.min(var0.have(), var0.needed())))
            .thenComparing(var0 -> var0.item().getName().getString())
      );
      return List.copyOf(var3);
   }

   private void beginScan() {
      this.target = null;
      this.temporary = null;
      this.aim = null;
      this.pending = null;
      this.pillar = false;
      this.scan = new BuildController.Scan(true);
      this.setStage(BuildController.Stage.SELECT);
   }

   private void select() {
      if (!this.verify.isEmpty() && this.selectVerifyWait++ < 40) {
         this.status = "Chờ server xác nhận khối vừa đặt";
      } else {
         this.selectVerifyWait = 0;
         if (this.scan == null || !this.scan.choose) {
            this.scan = new BuildController.Scan(true);
         }

         if (!this.scan.advance()) {
            this.status = "Kiểm tra công trình: " + this.scan.index + "/" + this.plan.targets.size();
         } else {
            BuildController.Scan var1 = this.scan;
            this.scan = null;
            this.publish(var1);
            if (var1.matched > this.bestMatched && this.verify.isEmpty() && this.pending == null && this.ticks - this.clickTick > 40 + this.latencyTicks() * 3) {
               // 3.0.105: số khối đúng bản vẽ tăng (kể cả đổ nước vào lá, chỉnh repeater…) = có tiến triển
               if (this.bestMatched >= 0) {
                  this.confirmed();
               }

               this.bestMatched = var1.matched;
            }

            if (this.config.layerMode && !this.triggersReleased && !var1.triggerByLayer.isEmpty()) {
               int var60 = var1.triggerByLayer.firstKey();
               if ((var1.gate == null || var60 < var1.gate) && this.triggerLayers.add(var60)) {
                  // 3.0.90: tầng này chỉ còn observer kích hoạt piston → đặt luôn trước khi lên tầng trên (bản cũ để tới cuối công trình
                  // → "chưa xây xong tầng 2 đã lên tầng 3, thiếu 2 observer"). Observer nhìn vào ô còn phải xây thì vẫn chờ ô đó xong.
                  AutoBuildMod.LOGGER
                     .info("[AutoBuild] Tầng Y={} đã xây xong phần khác → đặt {} khối để cuối (observer kích hoạt piston / TNT cạnh mạch đỏ) của tầng này rồi mới lên tầng trên", var60, var1.triggerByLayer.get(var60));
                  this.beginScan();
                  return;
               }
            }

            if (!this.triggersReleased && var1.triggerLeft > 0 && var1.gate == null) {
               this.triggersReleased = true;
               AutoBuildMod.LOGGER.info("[AutoBuild] Đã xây xong mọi khối khác → giờ mới đặt {} khối để cuối (observer kích hoạt piston / TNT cạnh mạch đỏ)", var1.triggerLeft);
               this.say("Xây xong phần chính → đặt nốt " + var1.triggerLeft + " khối để cuối (observer kích hoạt piston / TNT).");
               this.beginScan();
               return;
            }

            if (var1.gate == null && !this.postponedLayers.isEmpty() && var1.matched < var1.total) {
               AutoBuildMod.LOGGER.info("[AutoBuild] Đã xây hết phần khác → quay lại thử tiếp các tầng còn khối để cuối: {}", this.postponedLayers);
               this.postponedLayers.clear();
               this.layerRounds.clear();
               this.layerRoundPlaced.clear();
               this.beginScan();
               return;
            }

            if (this.finishFluidPhase(var1)) {
               return;
            }

            if (this.tryCollectLayerBucket(var1)) {
               return;
            }

            if (var1.total == 0) {
               this.finishedAt = this.ticks;
               this.pause("Không có block nào trong khoảng Y đã chọn" + this.rangeLabel() + ".");
            } else if (var1.matched == var1.total || this.onlyUnsupportedLeft(var1)) {
               if (var1.matched != var1.total && this.ticks - this.unsupSaidAt > 1200) {
                  // 3.0.89: phần còn lại chỉ là ô mod không tự đặt (vd. nước chảy tự hình thành từ nguồn) → coi như xong,
                  // không lặp "Chỉ còn block bị chặn/không hỗ trợ" mãi (log 16:25:33–16:26:48)
                  this.unsupSaidAt = this.ticks;
                  this.say(
                     "Đã xây hết"
                        + this.rangeLabel()
                        + ": "
                        + var1.matched
                        + "/"
                        + var1.total
                        + ", còn "
                        + (var1.total - var1.matched)
                        + " ô mod không tự đặt ("
                        + this.unsupportedLabel(var1)
                        + ") → coi như xong."
                  );
               }

               if (++this.verifiedPasses < 2) {
                  this.waitingReason = "Kiểm tra lại toàn bộ công trình";
                  this.setStage(BuildController.Stage.WAIT_MATERIAL);
               } else {
                  this.say("Đã xác minh " + var1.matched + "/" + var1.total + " block. Thu dọn khối kê tạm.");
                  if (this.config.syncLitematicaLayer) {
                     LayerView.restore();
                  }

                  this.cleanupMode = true;
                  this.cleanupDeferred.clear();
                  this.setStage(BuildController.Stage.CLEANUP);
               }
            } else {
               this.verifiedPasses = 0;
               Integer var2 = null;

               for (Entry var4 : var1.layers.entrySet()) {
                  if (((LayerPlanner.Stat)var4.getValue()).remaining > 0 && (var2 == null || (Integer)var4.getKey() < var2)) {
                     var2 = (Integer)var4.getKey();
                  }
               }

               boolean var15 = var2 != null && var1.layers.get(var2).ready > 0;
               if (var2 != null && !var15) {
                  this.layerWaitSince.putIfAbsent(var2, this.ticks);
               } else {
                  this.layerWaitSince.clear();
               }

               int var16 = var2 == null ? 0 : this.ticks - this.layerWaitSince.getOrDefault(var2, this.ticks);
               boolean var5 = this.config.skipLayerWhenMissing && var2 != null && !var15 && var16 > 18000;
               LayerPlanner.Choice var6 = LayerPlanner.choose(var1.layers, var5);
               Integer var7 = var6.layer();
               // 3.0.124 (người dùng): khối chứa chưa đủ đồ thì chưa xong tầng → cho đồ vào trước khi lên tầng trên
               if (this.config.layerMode && this.fillGate(var7)) {
                  return;
               }

               if (this.config.layerMode && var1.gate != null) {
                  int var48 = var1.gate;
                  LayerPlanner.Stat var49 = var1.layers.computeIfAbsent(var48, var0x -> new LayerPlanner.Stat());
                  int var52 = var1.unfinished.getOrDefault(var48, 0) - var1.tempSkipped.getOrDefault(var48, 0) - var1.permSkipped.getOrDefault(var48, 0);
                  if (var52 <= 0) {
                     int var50 = var1.tempSkipped.getOrDefault(var48, 0);
                     if (var50 > 0 && this.fluidRetries.merge(var48, 1, Integer::sum) <= 8) {
                        this.skipped.removeIf(var2x -> var2x.getY() == var48 && !this.skipReason.containsKey(var2x));
                        this.attempts.keySet().removeIf(var1x -> var1x.getY() == var48);
                        this.depWaits.keySet().removeIf(var1x -> var1x.getY() == var48);
                        this.deferred.keySet().removeIf(var1x -> var1x.getY() == var48);
                        this.navFailures.keySet().removeIf(var1x -> var1x.getY() == var48);
                        AutoBuildMod.LOGGER
                           .info("[AutoBuild] Tầng Y={} còn {} khối bị bỏ qua tạm → làm lại, KHÔNG lên tầng trên", var48, var50);
                        this.say("Tầng Y=" + var48 + " chưa xong (" + var50 + " khối) → làm lại trước khi lên tầng trên.");
                        this.beginScan();
                        return;
                     }

                     if (var1.permSkipped.getOrDefault(var48, 0) > 0 && this.permRetried.add(var48)) {
                        this.skipped.removeIf(var2x -> var2x.getY() == var48);
                        this.skipReason.keySet().removeIf(var1x -> var1x.getY() == var48);
                        this.attempts.keySet().removeIf(var1x -> var1x.getY() == var48);
                        this.deferred.keySet().removeIf(var1x -> var1x.getY() == var48);
                        this.navFailures.keySet().removeIf(var1x -> var1x.getY() == var48);
                        this.fluidRetries.remove(var48);
                        AutoBuildMod.LOGGER.info("[AutoBuild] Tầng Y={} chỉ còn khối đã bỏ hẳn → thử lại toàn bộ một lần nữa, KHÔNG lên tầng trên", var48);
                        this.beginScan();
                        return;
                     }

                     this.retryLayer(var48, var1.unfinished.getOrDefault(var48, 0));
                     return;
                  }

                  if (var7 == null || var7 != var48) {
                     AutoBuildMod.LOGGER
                        .info(
                           "[AutoBuild] Giữ tầng Y={} (còn {} khối{}) — không lên Y={}",
                           new Object[]{var48, var1.unfinished.getOrDefault(var48, 0), var1.fluidLeft.containsKey(var48) ? ", còn " + var1.fluidLeft.get(var48) : "", var7}
                        );
                     var6 = new LayerPlanner.Choice(var48, var49.ready <= 0);
                     var7 = var48;
                  }
               }
               if (var7 != null && var1.readyByKey.getOrDefault(var7, List.of()).isEmpty()) {
                  int var53 = var1.blockedWrong.getOrDefault(var7, 0);
                  if (var53 > 0
                     && var1.deferredByKey.getOrDefault(var7, List.of()).isEmpty()
                     && var1.builtAbove(var7)
                     && this.digStuckLayers.add(var7)) {
                     // 3.0.107: tầng dưới chỉ còn ô bị đá chiếm mà tầng trên đã xây rồi (xây theo tầng từ dưới lên → trước đây đã để
                     // "đào sau") → không đi xuống vùng đá đào ngay (log 02:04, 08:55, 21:12: mỗi lần mở lại đều chui xuống -58)
                     AutoBuildMod.LOGGER
                        .info("[AutoBuild] Tầng Y={} chỉ còn {} ô bị đá chiếm, tầng trên đã xây → không đi xuống đào, xây tiếp tầng trên (đào sau)", var7, var53);
                     this.beginScan();
                     return;
                  }

                  if (this.digObstacle("Tầng Y=" + var7 + " đã xây hết chỗ trống đặt được")) {
                     this.digEmptyScans.remove(var7);
                     return;
                  }

                  if (var53 > 0 && var1.deferredByKey.getOrDefault(var7, List.of()).isEmpty() && this.digEmptyScans.merge(var7, 1, Integer::sum) >= 4) {
                     // Còn ô bị chiếm mà không đào tới được (không lộ mặt / không có chỗ đứng) → không khóa tầng mãi
                     this.digEmptyScans.remove(var7);
                     this.digStuckLayers.add(var7);
                     AutoBuildMod.LOGGER.info("[AutoBuild] Tầng Y={}: còn {} ô bị đá/khối khác chiếm mà chưa đào tới được → cho lên tầng trên, đào sau", var7, var53);
                     this.say("Tầng Y=" + var7 + " còn " + var53 + " ô bị đá chiếm chưa đào tới được → xây tiếp tầng trên, các ô này đào sau.");
                     this.beginScan();
                     return;
                  }
               } else if (var7 != null) {
                  this.digEmptyScans.remove(var7);
               }

               if (var7 == null && this.digObstacle("Không còn khối nào đặt được")) {
                  return;
               }

               if (this.config.layerMode && var7 != null && var2 != null && var7 > var2 && this.skipWarned.add(var7)) {
                  this.say(
                     "Tầng Y="
                        + var2
                        + " chờ đồ đã "
                        + var16 / 1200
                        + " phút → tạm lên tầng Y="
                        + var7
                        + ". Xây tầng trên trước có thể chặn đường xây tầng dưới; tắt \"Lên tầng khi thiếu đồ\" nếu không muốn."
                  );
               }

               if (this.config.layerMode) {
                  Integer var8 = null;

                  for (BlockPos var10 : this.skipped) {
                     if (this.config.inYRange(var10.getY()) && !this.skipReason.containsKey(var10) && (var8 == null || var10.getY() < var8)) {
                        var8 = var10.getY();
                     }
                  }

                  if (var8 != null && (var7 == null || var8 < var7) && (this.retriedLayers.add(var8) || this.retryFluidLayer(var8))) {
                     int var24 = var8;
                     this.skipped.removeIf(var2x -> var2x.getY() == var24 && !this.skipReason.containsKey(var2x));
                     this.attempts.keySet().removeIf(var1x -> var1x.getY() == var24);
                     this.depWaits.keySet().removeIf(var1x -> var1x.getY() == var24);
                     this.deferred.keySet().removeIf(var1x -> var1x.getY() == var24);
                     this.say("Tầng Y=" + var24 + " còn khối bị bỏ qua → làm lại tầng này trước khi lên tầng trên.");
                     this.beginScan();
                     return;
                  }
               }

               if (var7 == null) {
                  this.status = "Chỉ còn block bị chặn/không hỗ trợ. " + this.progressDetail() + (var1.unsupported > 0 ? " (" + this.unsupportedLabel(var1) + ")" : "");
                  if (this.ticks - this.unsupSaidAt > 1200) {
                     this.unsupSaidAt = this.ticks;
                     AutoBuildMod.LOGGER
                        .info(
                           "[AutoBuild] Không còn khối nào đặt được: bị chặn {} (vd. {}), chưa tải {}, mod không tự đặt {} ({})",
                           new Object[]{
                              var1.blocked,
                              var1.blockedList.isEmpty() ? "-" : var1.blockedList.getFirst().pos().toShortString() + " " + var1.blockedList.getFirst().wanted() + " bị " + var1.blockedList.getFirst().actual() + " chiếm",
                              var1.unloaded,
                              var1.unsupported,
                              this.unsupportedLabel(var1)
                           }
                        );
                  }

                  this.setStage(BuildController.Stage.WAIT_MATERIAL);
               } else if (var6.waitingMaterial()) {
                  SchematicPlan.Target var18 = var1.bestMissing.get(var7);
                  if (var18 == null) {
                     for (Entry var28 : var1.bestMissing.entrySet()) {
                        if ((Integer)var28.getKey() <= var7) {
                           var18 = (SchematicPlan.Target)var28.getValue();
                           break;
                        }
                     }
                  }

                  if (var18 != null
                     && this.config.collectFromOrders
                     && InventoryAccess.freeSlots(this.mc) <= this.config.reserveFreeSlots
                     && this.freeSpace("Túi đồ đầy khi tầng Y=" + var7 + " còn thiếu " + this.missingLabel())) {
                     return;
                  } else if (var18 != null && this.config.collectFromOrders && InventoryAccess.freeSlots(this.mc) <= this.config.reserveFreeSlots) {
                     this.pause(
                        "Túi đồ đầy (chỉ còn "
                           + InventoryAccess.freeSlots(this.mc)
                           + " ô trống, cần hơn "
                           + this.config.reserveFreeSlots
                           + "). Tầng Y="
                           + var7
                           + " còn thiếu "
                           + this.missingLabel()
                           + " mà không lấy được từ Order. Dọn bớt đồ trong túi rồi nhấn K."
                     );
                  } else if (var18 != null && this.config.collectFromOrders) {
                     this.target = var18;
                     Item var23 = this.materialFor(var18, this.stateAt(var18.pos()));
                     if (this.missing.getOrDefault(var23, 0) > this.ticks && this.allMissingOnCooldown()) {
                        this.pauseLayerIncomplete(var1, var7);
                     } else {
                        this.collect(var23, BuildController.Stage.SELECT, this.layerNeed(var1, var7, var23));
                     }
                  } else {
                     this.status = "WAITING_FOR_MATERIAL: "
                        + this.missingLabel()
                        + (
                           InventoryAccess.freeSlots(this.mc) <= this.config.reserveFreeSlots
                              ? " — túi đồ đầy, hãy bỏ bớt đồ không cần để lấy vật liệu."
                              : (this.config.collectFromOrders ? " — kiểm tra Order lại sau." : " — bỏ vật liệu vào túi.")
                        );
                     this.setStage(BuildController.Stage.WAIT_MATERIAL);
                  }
               } else {
                  Integer var17 = null;

                  for (Entry var25 : var1.layers.entrySet()) {
                     if (((LayerPlanner.Stat)var25.getValue()).remaining > 0) {
                        var17 = (Integer)var25.getKey();
                        break;
                     }
                  }

                  if (!this.depMissing.isEmpty() && this.collectDepMissing()) {
                     return;
                  }

                  if (this.config.collectFromOrders && var17 != null && !var17.equals(var7) && var1.bestMissing.containsKey(var17)) {
                     SchematicPlan.Target var21 = var1.bestMissing.get(var17);
                     this.target = var21;
                     Item var27 = this.materialFor(var21, this.stateAt(var21.pos()));
                     this.collect(var27, BuildController.Stage.SELECT, this.layerNeed(var1, var17, var27));
                  } else {
                     List<SchematicPlan.Target> var20 = (List<SchematicPlan.Target>) var1.readyByKey.get(var7);
                     if (var20 == null || var20.isEmpty()) {
                        List<SchematicPlan.Target> var26 = var1.deferredByKey.getOrDefault(var7, List.of());
                        // 3.0.98: chỉ còn ô NƯỚC đang hoãn (vd. shop lag) mà vẫn còn ô nước khác đổ được → đổ các ô đó trước,
                        // không "làm ngay khối hoãn gần nhất" (bản cũ quay lại đúng ô vừa hoãn vì lag)
                        boolean var80 = !var26.isEmpty() && var1.waterByKey.containsKey(var7) && !var1.waterByKey.get(var7).isEmpty();
                        if (var80) {
                           for (SchematicPlan.Target var81 : var26) {
                              if (!Materials.pourable(this.stateAt(var81.pos()), var81.state())) {
                                 var80 = false;
                                 break;
                              }
                           }
                        }

                        if (!var26.isEmpty() && !var80) {
                           ArrayList<SchematicPlan.Target> var11 = new ArrayList<>();

                           for (SchematicPlan.Target var13 : var26) {
                              int var14 = this.deferred.getOrDefault(var13.pos(), 0);
                              if (var14 - this.ticks <= 0) {
                                 var11.add(var13);
                              } else if (var14 - this.ticks > 40) {
                                 this.deferred.put(var13.pos(), this.ticks + 40);
                              }
                           }

                           if (var11.isEmpty()) {
                              SchematicPlan.Target var60 = null;
                              double var61 = Double.MAX_VALUE;

                              for (SchematicPlan.Target var63 : var26) {
                                 double var64 = this.mc.player.squaredDistanceTo(Vec3d.ofCenter(var63.pos()));
                                 if (var64 < var61) {
                                    var61 = var64;
                                    var60 = var63;
                                 }
                              }

                              if (var60 == null || this.ticks - this.lastDeferRush < 40) {
                                 this.status = "Chờ thử lại khối còn thiếu ở tầng này";
                                 this.setStage(BuildController.Stage.WAIT_MATERIAL);
                                 return;
                              }

                              this.lastDeferRush = this.ticks;
                              AutoBuildMod.LOGGER.info("[AutoBuild] Tầng chỉ còn khối đang hoãn → làm ngay khối gần nhất {} thay vì đứng chờ", var60.pos().toShortString());
                              var11.add(var60);
                           }

                           for (SchematicPlan.Target var30 : var11) {
                              this.deferred.remove(var30.pos());
                           }

                           var20 = var11;
                        } else {
                           if (BucketRules.solidGate(var1.solidTotal(), this.config.layerMode) != 0 || !var1.waterByKey.containsKey(var7)) {
                              this.status = "Tầng Y=" + var7 + ": không còn khối nào đặt được lúc này";
                              this.setStage(BuildController.Stage.WAIT_MATERIAL);
                              return;
                           }

                           var20 = this.infiniteWater(var1.waterByKey.get(var7));
                           this.status = "Đổ xô (nước / dung nham / tuyết bột) — bước cuối của công trình";
                           if (var20.isEmpty()) {
                              this.status = "Chờ nước tự thành nguồn (nước vô hạn)";
                              this.setStage(BuildController.Stage.WAIT_MATERIAL);
                              return;
                           }
                        }
                     }

                     this.queue.clear();
                     this.queue.addAll((Collection<? extends SchematicPlan.Target>)var20);
                     this.nextTarget(false);
                  }
               }
            }
         }
      }
   }

   private boolean occupies(BlockPos var1) {
      return this.mc.player.getBoundingBox().contract(0.001).intersects(new Box(var1));
   }

   // ===== 3.0.124: đứng trên khối thấp hơn 1 ô (rương, slab dưới…) mà chìa mép thì thân người lấn vào ô cần đặt =====
   private final Set<BlockPos> liftTried = new HashSet<>();

   /**
    * Log 3.0.123 (20:11:59–20:12:11): Hopper @ 217851 cần bấm vào mặt tây của rương ở 217852; dưới hopper trống nên chỉ đứng được
    * trên nóc rương. Rương cao 0,875 → chân thấp hơn đỉnh ô hopper 0,125 ô: chìa mép thì thân lấn vào ô hopper (game không cho
    * đặt), lùi ra thì mắt lại ở phía đông mặt tây (không nhìn thấy). Người dùng chọn: kê 1 khối tạm lên nóc rương (giữ shift để
    * không mở rương), đứng lên đó (chân cao hơn ô cần đặt), chìa mép đặt, xong đập khối tạm.
    */
   private final Map<BlockPos, Integer> liftSaidAt = new HashMap<>();

   /** 3.0.125: ghi lý do không kê khối tạm (mỗi ô tối đa 1 lần/30 giây) để đọc log khi đặt hopper/khối từ nóc rương kẹt */
   private boolean liftNo(SchematicPlan.Target var1, String var2) {
      if (this.mc.player != null && this.ticks - this.liftSaidAt.getOrDefault(var1.pos(), -100000) > 600) {
         this.liftSaidAt.put(var1.pos().toImmutable(), this.ticks);
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] [KÊ TẠM] {} @ {}: không kê khối tạm — {} (chân Y={}, đứng trên {})",
               new Object[]{
                  var1.state().getBlock().getName().getString(),
                  var1.pos().toShortString(),
                  var2,
                  String.format("%.3f", this.mc.player.getY()),
                  this.mc.world.getBlockState(BlockPos.ofFloored(this.mc.player.getX(), this.mc.player.getY() - 0.01, this.mc.player.getZ())).getBlock().getName().getString()
               }
            );
      }

      return false;
   }

   private boolean liftOverLowFloor(SchematicPlan.Target var1) {
      if (this.mc.player == null || this.plan == null) {
         return false;
      } else if (this.temporary != null || this.pillar || this.supportTarget != null) {
         return false;
      } else if (!this.mc.player.isOnGround()) {
         return this.liftNo(var1, "chưa đứng trên đất");
      } else {
         double var2 = this.mc.player.getY();
         double var4 = var2 - Math.floor(var2);
         if (var4 < 0.01 || var4 > 0.99) {
            return false;
         } else if ((double)(var1.pos().getY() + 1) <= var2 + 0.001) {
            return this.liftNo(var1, "ô cần đặt không cao hơn chân");
         } else if (this.liftTried.contains(var1.pos())) {
            return this.liftNo(var1, "đã thử kê tạm cho ô này ở tầng này");
         } else {
            BlockPos var6 = BlockPos.ofFloored(this.mc.player.getX(), var2 - 0.01, this.mc.player.getZ());
            BlockPos var7 = var6.up();
            if (var7.equals(var1.pos())) {
               return this.liftNo(var1, "ô kê trùng ô cần đặt");
            } else if (this.plan.at(var7) != null && !this.plan.at(var7).state().isAir()) {
               return this.liftNo(var1, "ô trên khối thấp " + var7.toShortString() + " có khối trong bản vẽ");
            } else if (!this.mc.world.getBlockState(var7).getCollisionShape(this.mc.world, var7).isEmpty()
               && !this.mc.world.getBlockState(var7).isReplaceable()) {
               return this.liftNo(var1, "ô trên khối thấp bị chiếm");
            } else if (!this.terrain_clear(var7.up())) {
               return this.liftNo(var1, "không đủ chỗ đầu để nhảy kê");
            } else {
               Item var8 = this.scaffoldItem();
               if (var8 == Items.AIR || !(var8 instanceof net.minecraft.item.BlockItem var9) || InventoryAccess.count(this.mc, var8) <= 0) {
                  return this.liftNo(var1, "không có khối kê trong túi");
               } else if (!this.scaffoldAllowed()) {
                  return this.liftNo(var1, "đang tắt kê tự động");
               } else {
                  this.liftTried.add(var1.pos().toImmutable());
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] {} @ {}: đứng trên {} (thấp hơn 1 ô) chìa mép thì thân lấn vào ô cần đặt → nhảy kê 1 khối tạm lên nóc {} ({}), đứng lên đó rồi chìa mép đặt",
                        new Object[]{
                           var1.state().getBlock().getName().getString(),
                           var1.pos().toShortString(),
                           this.mc.world.getBlockState(var6).getBlock().getName().getString(),
                           this.mc.world.getBlockState(var6).getBlock().getName().getString(),
                           var7.toShortString()
                        }
                     );
                  this.temporary = new SchematicPlan.Target(var7.toImmutable(), var9.getBlock().getDefaultState());
                  this.pillar = true;
                  this.aim = null;
                  this.bridgeTicks = 0;
                  this.inventoryAttempts = 0;
                  this.setStage(BuildController.Stage.EQUIP);
                  this.equip();
                  return true;
               }
            }
         }
      }
   }

   private boolean blocksMe(SchematicPlan.Target var1) {
      VoxelShape var2 = var1.state().getCollisionShape(this.mc.world, var1.pos());
      if (var2.isEmpty()) {
         return false;
      } else {
         Box var3 = this.mc.player.getBoundingBox().contract(0.001);

         for (Box var5 : var2.getBoundingBoxes()) {
            if (var5.offset(var1.pos()).intersects(var3)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean slipperyFloor() {
      if (this.mc.player != null && this.mc.world != null) {
         BlockPos var1 = BlockPos.ofFloored(this.mc.player.getX(), this.mc.player.getY() - 0.2, this.mc.player.getZ());
         return this.mc.world.getBlockState(var1).getBlock().getSlipperiness() > 0.61F;
      } else {
         return false;
      }
   }

   private double slideLimit() {
      return this.slipperyFloor() ? 0.0064 : 0.0016;
   }

    private void brake() {
        // A full-strength opposite key at tiny velocity starts a new step in the
        // other direction. Let vanilla ground friction settle instead of oscillating.
        qualityStopMovement();
    }



   private void creepToward(Vec3d var1) {
      double var2 = var1.x - this.mc.player.getX();
      double var4 = var1.z - this.mc.player.getZ();
      float var6 = MotionMath.wrap(MotionMath.yaw(var2, var4) - this.mc.player.getYaw());
      if (Math.abs(var6) < 67.5F) {
         Input.key(this.mc.options.forwardKey, true);
      }

      if (Math.abs(var6) > 112.5F) {
         Input.key(this.mc.options.backKey, true);
      }

      if (var6 > 22.5F && var6 < 157.5F) {
         Input.key(this.mc.options.rightKey, true);
      }

      if (var6 < -22.5F && var6 > -157.5F) {
         Input.key(this.mc.options.leftKey, true);
      }

      Input.sneak(this.mc, true, this.ticks);
   }

   private void stepAway(BlockPos var1) {
      Vec3d var2 = Vec3d.ofCenter(var1);
      double var3 = this.mc.player.getX() - var2.x;
      double var5 = this.mc.player.getZ() - var2.z;
      if (var3 * var3 + var5 * var5 < 0.001) {
         var3 = -Math.sin(Math.toRadians((double)(-this.mc.player.getYaw())));
         var5 = -Math.cos(Math.toRadians((double)this.mc.player.getYaw()));
      }

      float var7 = MotionMath.wrap(MotionMath.yaw(var3, var5) - this.mc.player.getYaw());
      if (Math.abs(var7) < 67.5F) {
         Input.key(this.mc.options.forwardKey, true);
      }

      if (Math.abs(var7) > 112.5F) {
         Input.key(this.mc.options.backKey, true);
      }

      if (var7 > 22.5F && var7 < 157.5F) {
         Input.key(this.mc.options.rightKey, true);
      }

      if (var7 < -22.5F && var7 > -157.5F) {
         Input.key(this.mc.options.leftKey, true);
      }

      Input.sneak(this.mc, true, this.ticks);
   }

   private boolean stillNeeded(SchematicPlan.Target var1) {
      if (!this.config.inYRange(var1.pos().getY())
         || this.deferred.getOrDefault(var1.pos(), 0) > this.ticks
         || this.placedDone.contains(var1.pos())
         || this.skipped.contains(var1.pos())) {
         return false;
      } else if (!this.mc.world.isChunkLoaded(var1.pos())) {
         return true;
      } else {
         BlockState var2 = this.mc.world.getBlockState(var1.pos());
         if (this.done(var2, var1.state()) || StateMatch.chestAwaitingPartner(var2, var1.state())) {
            return false;
         } else if (this.placedDone.contains(var1.pos())) {
            return false;
         } else {
            boolean var3 = StateMatch.configurable(var2, var1.state());
            return !var3 && !StateMatch.canAdd(var2, var1.state()) ? false : var3 || InventoryAccess.count(this.mc, this.materialFor(var1, var2)) > 0;
         }
      }
   }

   private void nextTarget(boolean var1) {
      SchematicPlan.Target var2 = this.target;
      if (var2 != null
         && this.mc.world != null
         && this.mc.player != null
         && this.mc.world.isChunkLoaded(var2.pos())
         && Materials.waterlog(this.mc.world.getBlockState(var2.pos()), var2.state())
         && !this.skipped.contains(var2.pos())
         && this.deferred.getOrDefault(var2.pos(), 0) <= this.ticks
         && this.ticks - this.waterTryTick >= 3
         && this.waterTries.merge(var2.pos().toImmutable(), 1, Integer::sum) <= 6) {
         this.waterTryTick = this.ticks;
         this.pending = null;
         this.aim = null;
         this.anglesFor = null;
         this.pillar = false;
         this.temporary = null;
         this.supportTarget = null;
         this.supportQueue.clear();
         this.spoof = null;
         this.spoofHold = 0;
         this.pit = null;
         AutoBuildMod.LOGGER
            .info("[AutoBuild] {} @ {} vẫn chưa có nước → quay lại đổ cho xong", var2.state().getBlock().getName().getString(), var2.pos().toShortString());
         this.status = "Đổ nước vào " + var2.pos().toShortString();
         this.setStage(BuildController.Stage.EQUIP);
      } else {
         this.pending = null;
         this.aim = null;
         this.pillar = false;
         this.temporary = null;
         this.target = null;
         this.supportTarget = null;
         this.supportQueue.clear();
         this.pit = null;
         this.spoof = null;
         this.spoofHold = 0;
         this.repairGoal = null;
         this.rowSupport = false;
         this.jobSupport = false;
         this.restoringPos = null;
         this.inventoryAttempts = 0;
         this.aimAttempt = 0;
         this.goalRetries = 0;
         this.targetTicks = 0;
         this.stepTicks = 0;
         this.bridgeTicks = 0;
         this.replans = 0;
         this.escapeTried = false;
         while (!this.blockerQueue.isEmpty()) {
            SchematicPlan.Target var50 = this.blockerQueue.pollFirst();
            if (this.mc.world.isChunkLoaded(var50.pos()) && this.mc.world.getBlockState(var50.pos()).isReplaceable()) {
               this.target = var50;
               this.aim = null;
               this.status = "Đặt khối chặn piston @ " + var50.pos().toShortString();
               this.beginGoals(false);
               return;
            }
         }

         if (!this.tryRepairNow() && !this.walkToRepair()) {
            this.queue.removeIf(var1x -> !this.stillNeeded(var1x));
            if (this.queue.isEmpty()) {
               if (var1) {
                  this.beginScan();
               } else {
                  this.setStage(BuildController.Stage.WAIT_MATERIAL);
               }
            } else {
               if (this.rootDepth == 0) {
                  this.depMissing.clear();
                  this.depMissingFor.clear();
               }

               Vec3d var3 = this.mc.player.getEyePos();
               double var4 = this.reach();
               if (this.priorityPos != null) {
                  SchematicPlan.Target var6 = null;

                  for (SchematicPlan.Target var8 : this.queue) {
                     if (var8.pos().equals(this.priorityPos)) {
                        var6 = var8;
                        break;
                     }
                  }

                  this.priorityPos = null;
                  if (var6 != null) {
                     this.queue.remove(var6);
                     this.target = this.resolve(var6);
                     PlacementSolver.Aim var36 = this.occupies(var6.pos())
                        ? null
                        : PlacementSolver.solve(this.mc, var3, this.target, var4, true, var6.pos().asLong() * 31L);
                     if (var36 != null && this.mc.player.isOnGround()) {
                        this.aim = var36;
                        this.setStage(BuildController.Stage.EQUIP);
                        this.equip();
                        return;
                     }

                     this.status = "Đi đặt nửa còn lại của rương đôi";
                     this.beginGoals(false);
                     return;
                  }
               }

               if (this.rootDepth == 0 && this.scaffoldRestock()) {
                  return;
               }

               if (this.rootDepth == 0 && this.materialRestock()) {
                  return;
               }

               if (this.rootDepth == 0 && this.crafterStep()) {
                  return;
               }

               if (this.rootDepth == 0 && this.fillStep()) {
                  return;
               }

               if (this.rootDepth == 0 && this.maybeSetHome()) {
                  return;
               }

               // 3.0.95: kho đồ gọn: thu lại ender chest bỏ dở, cất vật liệu tầng này không dùng, bán xô rỗng/đồ không cần
               if (this.rootDepth == 0 && (this.stashCleanupStep() || this.stashDepositStep() || this.sellStep())) {
                  return;
               }

               // 3.0.82: trước khi chọn khối theo hàng (có thể ở rất xa), đặt hết các ô đặt được ngay trong tầm với
               if (this.rootDepth == 0 && this.mc.player.isOnGround() && this.nearbyBuild("chọn khối")) {
                  return;
               }

               if ((this.rootDepth != 0 || !this.restoreStep())
                  && (this.rootDepth != 0 || !this.pendingSweep())
                  && (this.rootDepth != 0 || !this.chainStep())
                  && (this.rootDepth != 0 || !this.floorStep())
                  && (this.rootDepth != 0 || !this.tryRowJob())) {
                  this.orderQueue();
                  SchematicPlan.Target var34 = null;
                  PlacementSolver.Aim var35 = null;
                  int var37 = 0;
                  this.heavyLabel = "chọn khối";

                  for (SchematicPlan.Target var10 : this.queue) {
                     var37++;
                     if (var37 > 60 || var37 > 8 && this.overBudget()) {
                        break;
                     }

                     if (!(var3.distanceTo(Vec3d.ofCenter(var10.pos())) > var4 + 1.2)
                        && !this.standBanned(var10.pos())
                        && !this.occupies(var10.pos())
                        && this.mc.world.isChunkLoaded(var10.pos())
                        && this.hasSupport(var10.pos())) {
                        PlacementSolver.Aim var11 = PlacementSolver.solve(this.mc, var3, this.resolve(var10), var4, true, var10.pos().asLong() * 31L);
                        if (var11 == null
                           && var10.pos().getY() < this.mc.player.getBlockY()
                           && PlacementSolver.solve(
                                 this.mc,
                                 edgeEye(Navigation.feet(Navigation.cell(this.mc.player.getBlockPos())), var10.pos()),
                                 this.resolve(var10),
                                 var4,
                                 true,
                                 var10.pos().asLong() * 31L,
                                 true
                              )
                              != null) {
                           var34 = var10;
                           var35 = null;
                           break;
                        }

                        if (var11 == null
                           && this.spoofFor(var10)
                           && PlacementSolver.solveSpoof(this.mc, var3, this.resolve(var10), var4, var10.pos().asLong() * 31L, null) != null) {
                           var34 = var10;
                           var35 = null;
                           break;
                        }

                        if (var11 != null) {
                           var34 = var10;
                           var35 = var11;
                           break;
                        }
                     }
                  }

                  if (var34 != null && this.mc.player.isOnGround()) {
                     this.nextRetries = 0;
                     this.queue.remove(var34);
                     this.target = this.resolve(var34);
                     this.aim = var35;
                     this.setStage(BuildController.Stage.EQUIP);
                     this.equip();
                  } else {
                     SchematicPlan.Target var38 = null;
                     SchematicPlan.Target var39 = null;
                     ArrayList<SchematicPlan.Target> var40 = new ArrayList<>();
                     ArrayList<SchematicPlan.Target> var12 = new ArrayList<>();
                     int var13 = 0;
                     boolean var14 = !this.scaffoldOk && this.reachableValid();
                     boolean var15 = false;

                     for (SchematicPlan.Target var17 : this.queue) {
                        if (!this.mc.world.isChunkLoaded(var17.pos())) {
                           var38 = var17;
                           break;
                        }

                        if (!this.hasSupport(var17.pos())) {
                           if (var12.size() < 60) {
                              var12.add(var17);
                           }
                        } else if (var14 && !this.nearReachable(var17.pos())) {
                           var15 = true;
                        } else {
                           if (++var13 > 400) {
                              break;
                           }

                           if (this.overBudget() && this.rootDepth == 0 && this.nextRetries < 40) {
                              this.nextRetries++;
                              this.setStage(BuildController.Stage.NEXT);
                              return;
                           }

                           if (this.placeable(var17)) {
                              var38 = var17;
                              break;
                           }

                           var40.add(var17);
                        }
                     }

                     if (var38 == null) {
                        ArrayList var41 = new ArrayList(var40);
                        var41.addAll(var12);
                        if (this.resolveAny(var41)) {
                           return;
                        }
                     }

                     if (var38 != null || !this.tryRowStart(var12, var40, var14 && !this.scaffoldOk, null)) {
                        if (var38 == null && !var12.isEmpty()) {
                           for (SchematicPlan.Target var48 : var12) {
                              SchematicPlan.Target var18 = this.findRoot(var48);
                              if (var18 == null && this.rootTimedOut && this.rootDepth == 0 && this.nextRetries < 40) {
                                 this.nextRetries++;
                                 this.setStage(BuildController.Stage.NEXT);
                                 return;
                              }

                              if (var18 != null) {
                                 AutoBuildMod.LOGGER
                                    .info(
                                       "[AutoBuild] {} @ {} lơ lửng → xây khối khởi nguồn {} @ {} trước",
                                       new Object[]{
                                          var48.state().getBlock().getName().getString(),
                                          var48.pos().toShortString(),
                                          var18.state().getBlock().getName().getString(),
                                          var18.pos().toShortString()
                                       }
                                    );
                                 this.queue.remove(var18);
                                 this.deferred.remove(var18.pos());
                                 this.target = this.resolve(var18);
                                 if (this.mc.world.isChunkLoaded(this.target.pos()) && this.occupies(this.target.pos()) && this.canJumpPlace(this.target)) {
                                    this.pillar = true;
                                    this.setStage(BuildController.Stage.EQUIP);
                                    return;
                                 }

                                 this.beginGoals(false);
                                 return;
                              }
                           }

                           if (var14 && this.rootDepth == 0) {
                              this.rootDepth++;
                              this.reachable = null;
                              this.scaffoldOk = true;

                              try {
                                 this.nextTarget(var1);
                              } finally {
                                 this.rootDepth--;
                              }

                              return;
                           }

                           int var43 = Integer.MAX_VALUE;
                           var12.removeIf(var0 -> rowFacing(var0.state()) != null);
                           if (var12.isEmpty()) {
                              if (var14 && this.rootDepth == 0) {
                                 this.rootDepth++;
                                 this.reachable = null;
                                 this.scaffoldOk = true;

                                 try {
                                    this.nextTarget(var1);
                                 } finally {
                                    this.rootDepth--;
                                 }

                                 return;
                              }

                              for (SchematicPlan.Target var56 : var40) {
                                 this.deferred.put(var56.pos(), this.ticks + 40);
                              }

                              this.status = "Chờ: các hàng hopper chưa có chỗ bắt đầu";
                              this.queue.clear();
                              this.setStage(BuildController.Stage.WAIT_MATERIAL);
                              return;
                           }

                           for (int var49 = 0; var49 < Math.min(20, var12.size()); var49++) {
                              List var54 = this.supportChain(((SchematicPlan.Target)var12.get(var49)).pos());
                              if (!var54.isEmpty() && var54.size() < var43) {
                                 var43 = var54.size();
                                 var39 = (SchematicPlan.Target)var12.get(var49);
                                 if (var43 == 1) {
                                    break;
                                 }
                              }
                           }

                           if (var39 == null) {
                              var39 = (SchematicPlan.Target)var12.getFirst();
                           }
                        }

                        if (var38 == null && var39 != null) {
                           this.queue.remove(var39);
                           this.target = this.resolve(var39);
                           BlockPos var47 = this.supportSpot(var39.pos());
                           if (var47 == null) {
                              this.status = "Khối lơ lửng: đi tới cạnh để kê điểm tựa";
                              this.beginGoals(false);
                           } else {
                              this.supportTarget = new SchematicPlan.Target(var47, this.pillarState(var47));
                              this.status = "Kê khối tạm làm điểm tựa tại " + var47.toShortString();
                              PlacementSolver.Aim var52 = PlacementSolver.solve(this.mc, var3, this.supportTarget, var4, true, var47.asLong(), true);
                              if (var52 != null && this.mc.player.isOnGround()) {
                                 this.aim = var52;
                                 this.setStage(BuildController.Stage.EQUIP);
                                 this.equip();
                              } else {
                                 this.beginGoals(false);
                              }
                           }
                        } else if (var38 == null && var15 && this.rootDepth == 0) {
                           this.rootDepth++;
                           this.reachable = null;
                           this.scaffoldOk = true;

                           try {
                              this.nextTarget(var1);
                           } finally {
                              this.rootDepth--;
                           }
                        } else if (var38 == null) {
                           if (this.collectDepMissing()) {
                              return;
                           }

                           if (this.depMissing.isEmpty()) {
                              this.status = "Chờ khối phụ thuộc (vd. hopper cần rương có trước)";
                           }

                           for (SchematicPlan.Target var50 : var40) {
                              this.deferred.put(var50.pos(), this.ticks + 10);
                              if (this.depWaits.merge(var50.pos(), 1, Integer::sum) > 20
                                 && this.resolveBuild(var50) == null
                                 && !this.depMissingFor.containsKey(var50.pos())) {
                                 this.pauseDependencyLoop(var50.pos());
                                 this.say(
                                    "Bỏ qua "
                                       + var50.state().getBlock().getName().getString()
                                       + " @ "
                                       + var50.pos().toShortString()
                                       + ": chưa đặt đúng hướng được (thiếu khối phụ thuộc)."
                                 );
                              }
                           }

                           this.queue.clear();
                           this.setStage(BuildController.Stage.WAIT_MATERIAL);
                        } else {
                           if (this.lastPlaced != null && var38.pos().getManhattanDistance(this.lastPlaced) > 6 && this.rootDepth == 0) {
                              ArrayList var45 = new ArrayList();

                              for (SchematicPlan.Target var55 : this.queue) {
                                 if (var55.pos().getManhattanDistance(this.lastPlaced) <= 4 && rowFacing(var55.state()) != null) {
                                    var45.add(var55);
                                 }
                              }

                              if (!var45.isEmpty() && this.tryRowStart(var45, List.of(), false, this.lastPlaced)) {
                                 return;
                              }
                           }

                           this.queue.remove(var38);
                           SchematicPlan.Target var46 = this.resolve(var38);
                           this.target = var46;
                           if (this.mc.world.isChunkLoaded(var46.pos()) && this.occupies(var46.pos()) && this.canJumpPlace(var46)) {
                              this.pillar = true;
                              this.setStage(BuildController.Stage.EQUIP);
                              this.status = "Đang đứng trên chỗ cần đặt → nhảy lên rồi đặt";
                           } else {
                              this.beginGoals(false);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void notePlaced(BlockPos var1, BlockState var2) {
      Direction var3 = rowFacing(var2);
      if (var3 != null) {
         this.rowDir = var3.getOpposite();
      } else if (this.lastPlaced != null && this.lastPlaced.getY() == var1.getY()) {
         for (Direction var5 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            if (this.lastPlaced.offset(var5).equals(var1)) {
               this.rowDir = var5;
               break;
            }
         }
      }

      this.lastPlaced = var1.toImmutable();
      this.wakeNeighbours(var1);
   }

   private void orderQueue() {
      Vec3d var1 = this.mc.player.getPos();
      BlockPos var2 = this.lastPlaced != null && Vec3d.ofCenter(this.lastPlaced).squaredDistanceTo(var1) < 100.0 ? this.lastPlaced : null;
      if (var2 == null) {
         double var3 = Double.MAX_VALUE;

         for (SchematicPlan.Target var6 : this.queue) {
            if (this.mc.world.isChunkLoaded(var6.pos()) && this.hasSupport(var6.pos())) {
               double var7 = Vec3d.ofCenter(var6.pos()).squaredDistanceTo(var1);
               if (var7 < var3) {
                  var3 = var7;
                  var2 = var6.pos();
               }
            }
         }
      }

      if (var2 == null) {
         this.orderQueueOld();
      } else {
         BlockPos var11 = var2;
         BlockPos var4 = this.lastPlaced != null && this.rowDir != null ? this.lastPlaced.offset(this.rowDir) : null;
         HashMap var12 = new HashMap();

         for (SchematicPlan.Target var14 : this.queue) {
            BlockPos var8 = var14.pos();
            double var9 = (double)var8.getManhattanDistance(var11) + Math.sqrt(Vec3d.ofCenter(var8).squaredDistanceTo(var1)) * 0.15;
            if (!this.mc.world.isChunkLoaded(var8) || !this.hasSupport(var8)) {
               var9 += 1000.0;
            }

            if (var8.getY() != var11.getY()) {
               var9 += 3.0;
            }

            if (!this.floorPlaceable(var14)) {
               var9 += 40.0;
            }

            if (var4 != null && this.lastPlaced != null) {
               if (var8.equals(var4)) {
                  var9 -= 50.0;
               } else if (var8.getY() == this.lastPlaced.getY()
                  && (this.rowDir.getAxis() == Axis.X ? var8.getZ() == this.lastPlaced.getZ() : var8.getX() == this.lastPlaced.getX())) {
                  var9 -= 10.0;
               }
            }

            var12.put(var8, var9);
         }

         this.queue.sort(Comparator.comparingDouble(var1x -> (Double)var12.get(var1x.pos())));
      }

      this.anchorsFirst();
   }

   private final Set<BlockPos> anchorSaid = new HashSet<>();
   private int anchorWaitSaid = -1000;

   private void anchorsFirst() {
      if (this.plan != null && this.mc.world != null) {
         for (int var1 = 0; var1 < Math.min(this.queue.size(), 40); var1++) {
            SchematicPlan.Target var2 = this.queue.get(var1);
            if (this.mc.world.isChunkLoaded(var2.pos()) && this.mc.world.getBlockState(var2.pos()).isReplaceable()) {
               int var3 = PlacementSolver.supportMask(this.mc, this.resolve(var2));
               if (var3 != 0 && Integer.bitCount(var3) <= 2) {
                  boolean var4 = false;
                  SchematicPlan.Target var5 = null;

                  for (Direction var9 : Direction.values()) {
                     if ((var3 & 1 << var9.ordinal()) != 0) {
                        BlockPos var10 = var2.pos().offset(var9);
                        if (!this.mc.world.getBlockState(var10).isReplaceable()) {
                           var4 = true;
                           break;
                        }

                        SchematicPlan.Target var11 = this.plan.at(var10);
                        if (var5 == null
                           && var11 != null
                           && !var11.state().isAir()
                           && !var11.state().isReplaceable()
                           && var10.getY() <= var2.pos().getY()
                           && !this.skipped.contains(var10)) {
                           var5 = var11;
                        }
                     }
                  }

                  if (!var4 && var5 != null) {
                     int var12 = -1;

                     for (int var13 = 0; var13 < this.queue.size(); var13++) {
                        if (this.queue.get(var13).pos().equals(var5.pos())) {
                           var12 = var13;
                           break;
                        }
                     }

                     if (var12 > var1) {
                        this.queue.add(var1, this.queue.remove(var12));
                     } else if (var12 < 0) {
                        this.queue.add(var1, var5);
                     } else {
                        continue;
                     }

                     var1++;
                     if (this.anchorSaid.add(var2.pos().toImmutable())) {
                        AutoBuildMod.LOGGER
                           .info(
                              "[AutoBuild] Xây {} @ {} trước để {} @ {} có chỗ bám đúng hướng",
                              new Object[]{
                                 var5.state().getBlock().getName().getString(),
                                 var5.pos().toShortString(),
                                 var2.state().getBlock().getName().getString(),
                                 var2.pos().toShortString()
                              }
                           );
                     }
                  }
               }
            }
         }
      }
   }

   private void orderQueueOld() {
      int var1 = Integer.MAX_VALUE;
      int var2 = Integer.MIN_VALUE;
      int var3 = Integer.MAX_VALUE;
      int var4 = Integer.MIN_VALUE;

      for (SchematicPlan.Target var6 : this.queue) {
         var1 = Math.min(var1, var6.pos().getX());
         var2 = Math.max(var2, var6.pos().getX());
         var3 = Math.min(var3, var6.pos().getZ());
         var4 = Math.max(var4, var6.pos().getZ());
      }

      boolean var22 = var2 - var1 >= var4 - var3;
      Vec3d var23 = this.mc.player.getPos();
      BlockPos var7 = this.lastPlaced != null && Vec3d.ofCenter(this.lastPlaced).squaredDistanceTo(var23) < 64.0 ? this.lastPlaced : null;
      if (var7 == null) {
         double var8 = Double.MAX_VALUE;

         for (SchematicPlan.Target var11 : this.queue) {
            double var12 = Vec3d.ofCenter(var11.pos()).squaredDistanceTo(var23);
            if (var12 < var8) {
               var8 = var12;
               var7 = var11.pos();
            }
         }
      }

      int var24 = var22 ? var7.getZ() : var7.getX();
      int var9 = var22 ? var7.getX() : var7.getZ();
      HashMap var25 = new HashMap();

      for (SchematicPlan.Target var27 : this.queue) {
         BlockPos var13 = var27.pos();
         int var14 = var22 ? var13.getZ() : var13.getX();
         int var15 = var22 ? var13.getX() : var13.getZ();
         int var16 = 0;
         if (this.mc.world.isChunkLoaded(var13)) {
            for (Direction var20 : Direction.values()) {
               BlockState var21 = this.mc.world.getBlockState(var13.offset(var20));
               if (!var21.isReplaceable() && !var21.getOutlineShape(this.mc.world, var13.offset(var20)).isEmpty()) {
                  var16++;
               }
            }
         }

         double var28 = (double)Math.abs(var14 - var24) * 4.0
            + (double)Math.abs(var15 - var9)
            + Math.sqrt(Vec3d.ofCenter(var13).squaredDistanceTo(var23)) * 0.3
            + (double)(var16 == 0 ? 8 : 0)
            - (double)var16 * 0.4;
         var25.put(var13, var28);
      }

      this.queue.sort(Comparator.comparingDouble(var1x -> (Double)var25.get(var1x.pos())));
   }

   private boolean restoreStep() {
      if (!this.restore.isEmpty() && this.plan != null && !this.cleanupMode) {
         for (BlockPos var2 : new ArrayList<>(this.restore)) {
            if (this.fillHolds(var2)) {
               // khối che vừa đập để mở khối chứa: cho đồ xong mới đặt lại
               continue;
            }

            SchematicPlan.Target var3 = this.plan.at(var2);
            if (var3 != null && this.mc.world.isChunkLoaded(var2)) {
               if (!this.mc.world.getBlockState(var2).isReplaceable()) {
                  this.restore.remove(var2);
                  this.coverRestored.add(var2);
               } else {
                  SchematicPlan.Target var4 = this.plan.at(var2.down());
                  if (var4 == null || this.isFinished(var4)) {
                     if (this.restoreTries.merge(var2, 1, Integer::sum) > 4) {
                        this.restore.remove(var2);
                     } else {
                        SchematicPlan.Target var5 = this.resolve(var3);
                        if (InventoryAccess.count(this.mc, Materials.item(this.mc.world.getBlockState(var2), var5.state())) != 0) {
                           this.restoringPos = var2.toImmutable();
                           this.coverRestored.add(var2.toImmutable());
                           this.target = var5;
                           this.aim = null;
                           this.status = "Xây lại khối vừa phá tại " + var2.toShortString();
                           AutoBuildMod.LOGGER
                              .info("[AutoBuild] Xây lại khối đè đã phá: {} @ {}", var3.state().getBlock().getName().getString(), var2.toShortString());
                           this.placeOrGo();
                           return true;
                        }
                     }
                  }
               }
            } else if (var3 == null) {
               this.restore.remove(var2);
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean softReason(String var0) {
      return var0.startsWith("không có đường đi") || var0.startsWith("khối gốc không tới được") || var0.startsWith("ô đầu hàng không nằm trong hàng đợi");
   }

   private void defer(SchematicPlan.Target var1, int var2, String var3) {
      BlockPos var4 = var1.pos();
      this.deferred.put(var4, this.ticks + var2);
      boolean var5 = this.depMissingFor.containsKey(var4) || softReason(var3);
      if (var5) {
         this.attempts.remove(var4);
         this.warnNoScaffold(var1);
      }

      if (this.deferCount.getOrDefault(var4, 0) < 1 || !this.unblockAbove(var1)) {
         int var6 = this.deferCount.merge(var4, 1, Integer::sum);
         if ((var6 == 2 || var6 % 3 == 0) && this.ticks - this.deferLogged.getOrDefault(var4, -1000) > 100) {
            this.deferLogged.put(var4.toImmutable(), this.ticks);
            this.explainStuck(var1, var3, var6);
         }

         if (var6 % 3 == 0 && !var5 && !this.attempt(var1)) {
            this.skipReason.put(var1.pos().toImmutable(), "Hoãn " + var6 + " lần: " + var3);
            this.deferred.remove(var1.pos());
         }
      }
   }

   private Item scaffoldItem() {
      Identifier var1 = Identifier.tryParse(this.config.scaffoldItem);
      Block var2 = var1 == null ? Blocks.AIR : (Block)Registries.BLOCK.get(var1);
      return var2 == Blocks.AIR ? Items.AIR : var2.asItem();
   }

   private int scaffoldHave() {
      Item var1 = this.scaffoldItem();
      return var1 == Items.AIR ? 0 : InventoryAccess.count(this.mc, var1);
   }

   /**
    * 3.0.83: tự lấy thêm khối kê (mặc định Cobblestone) từ Order khi: (a) hết hẳn mà đang có khối lơ lửng cần kê, hoặc
    * (b) sắp hết (< 16) trong lúc đang dùng kê → lấy trước để khỏi đứt giữa chừng cầu/cột. Bản cũ chỉ báo
    * "trong túi không có Cobblestone" rồi đứng hoãn đi hoãn lại các khối lơ lửng (log 02:41–02:58 đứng 17 phút).
    */
   private boolean scaffoldRestock() {
      if (this.config.scaffold && this.config.collectFromOrders && !this.cleanupMode && this.mc.player != null && this.ticks - this.scaffoldOrderAt >= 200) {
         Item var1 = this.scaffoldItem();
         if (var1 == Items.AIR) {
            return false;
         } else {
            // 3.0.122 (người dùng: "trong người có khối kê mà log báo không có rồi vào order lấy"): chỉ đếm khi túi đồ đã khớp với
            // server (vừa đóng menu / vừa SWAP thì túi client có thể sai), và đếm cả tay trái
            if (!InventoryAccess.synced(this.mc)) {
               return false;
            }

            int var2 = InventoryAccess.count(this.mc, var1);
            if (this.mc.player.getOffHandStack().isOf(var1)) {
               var2 += this.mc.player.getOffHandStack().getCount();
            }

            boolean var3 = var2 <= 0 && this.ticks - this.scaffoldWantAt < 1200;
            // 3.0.122: lấy trước khi còn < 8 (trước: < 16 → đang có 10–15 khối vẫn bỏ dở để đi Order)
            boolean var4 = var2 < 8 && this.ticks - this.scaffoldUsedAt < 2400;
            if (!var3 && !var4) {
               return false;
            } else if (this.missing.getOrDefault(var1, 0) > this.ticks) {
               return false;
            } else if (InventoryAccess.freeSlots(this.mc) <= this.config.reserveFreeSlots) {
               return false;
            } else {
               this.scaffoldOrderAt = this.ticks;
               // 3.0.95: khối kê mỗi lần chỉ lấy 1 stack, hết thì lấy tiếp (đỡ chật túi)
               int var5 = var1.getMaxCount();
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] Khối kê {} còn {} ({}) → mở Order lấy thêm {} rồi xây tiếp",
                     new Object[]{var1.getName().getString(), var2, var3 ? "đang có khối lơ lửng cần kê" : "sắp hết khi đang bắc cầu/cột", var5}
                  );
               this.say("Khối kê " + var1.getName().getString() + " còn " + var2 + " → lấy thêm " + var5 + " từ Order.");
               this.target = null;
               this.temporary = null;
               this.supportTarget = null;
               this.supportQueue.clear();
               this.collect(var1, BuildController.Stage.SELECT, var5);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   // ===== 3.0.90: tự chỉnh crafter (tắt ô) ngay sau khi đặt =====
   private final LinkedHashMap<BlockPos, Integer> crafterQueue = new LinkedHashMap<>();
   private BlockPos crafterPos;
   private int crafterMask;
   private int crafterStepNo;
   private int crafterTicks;
   private int crafterNextAt;
   private int crafterOpenAt = -1000;
   private int crafterSends;
   private int crafterSent;
   private int crafterCheckAt;
   // 3.0.98: server lag — bấm mở crafter không lên menu thì bấm lại mỗi ~1 giây; 4 lần không lên → xây tiếp, 20 giây sau quay lại
   private final Map<BlockPos, Integer> crafterRetryAt = new HashMap<>();
   private int crafterClicks;
   private int crafterNoHit;

   private String crafterKey(int var1) {
      return this.plan == null ? "" : this.plan.placement.getName() + "@" + this.plan.placement.getOrigin().toShortString() + "|" + var1;
   }

   /** các tầng có crafter trong bản vẽ (trong khoảng Y) */
   public List<Integer> crafterLayers() {
      java.util.TreeSet<Integer> var1 = new java.util.TreeSet<>();
      if (this.plan != null && this.plan.loaded) {
         for (SchematicPlan.Target var3 : this.plan.targets) {
            if (SchematicPlan.isCrafter(var3.state())) {
               var1.add(var3.pos().getY());
            }
         }
      }

      return new ArrayList<>(var1);
   }

   public int crafterCount(int var1) {
      int var2 = 0;
      if (this.plan != null && this.plan.loaded) {
         for (SchematicPlan.Target var4 : this.plan.targets) {
            if (var4.pos().getY() == var1 && SchematicPlan.isCrafter(var4.state())) {
               var2++;
            }
         }
      }

      return var2;
   }

   /** kiểu tắt ô phổ biến nhất của crafter tầng Y trong bản vẽ; -1 nếu bản vẽ không có dữ liệu */
   public int schematicCrafterMask(int var1) {
      if (this.plan == null) {
         return -1;
      } else {
         HashMap<Integer, Integer> var2 = new HashMap<>();

         for (Entry<BlockPos, Integer> var4 : this.plan.crafterMasks.entrySet()) {
            if (var4.getKey().getY() == var1) {
               var2.merge(var4.getValue(), 1, Integer::sum);
            }
         }

         int var5 = -1;
         int var6 = 0;

         for (Entry<Integer, Integer> var8 : var2.entrySet()) {
            if (var8.getValue() > var6) {
               var6 = var8.getValue();
               var5 = var8.getKey();
            }
         }

         return var5;
      }
   }

   /** số kiểu khác nhau trong bản vẽ ở tầng Y (để GUI báo "bản vẽ có N kiểu") */
   public int schematicCrafterKinds(int var1) {
      if (this.plan == null) {
         return 0;
      } else {
         java.util.HashSet<Integer> var2 = new java.util.HashSet<>();

         for (Entry<BlockPos, Integer> var4 : this.plan.crafterMasks.entrySet()) {
            if (var4.getKey().getY() == var1) {
               var2.add(var4.getValue());
            }
         }

         return var2.size();
      }
   }

   /** cấu hình người chơi chỉnh cho tầng Y trong GUI; null = theo bản vẽ */
   public Integer crafterLayerOverride(int var1) {
      return this.config.crafterLayerMasks.get(this.crafterKey(var1));
   }

   public void setCrafterLayerOverride(int var1, Integer var2) {
      if (var2 == null) {
         this.config.crafterLayerMasks.remove(this.crafterKey(var1));
      } else {
         this.config.crafterLayerMasks.put(this.crafterKey(var1), var2 & 511);
      }

      this.config.saveQuietly();
   }

   /** cấu hình cần cho crafter này: theo tầng (GUI) nếu có, không thì đúng crafter đó trong bản vẽ; -1 = không biết → không chỉnh */
   private int desiredCrafterMask(BlockPos var1) {
      Integer var2 = this.crafterLayerOverride(var1.getY());
      if (var2 != null) {
         return var2;
      } else {
         Integer var3 = this.plan == null ? null : this.plan.crafterMasks.get(var1);
         return var3 == null ? -1 : var3;
      }
   }

   private void noteCrafterPlaced(BlockPos var1, BlockState var2) {
      if (this.config.configureCrafters && SchematicPlan.isCrafter(var2) && this.desiredCrafterMask(var1) > 0) {
         this.crafterQueue.putIfAbsent(var1.toImmutable(), 0);
      }
   }

   private static String maskLabel(int var0) {
      StringBuilder var1 = new StringBuilder();

      for (int var2 = 0; var2 < 9; var2++) {
         if ((var0 >> var2 & 1) != 0) {
            if (var1.length() > 0) {
               var1.append(",");
            }

            var1.append(var2 + 1);
         }
      }

      return var1.length() == 0 ? "không ô nào" : "ô " + var1;
   }

   /** crafter vừa đặt (trong tầm với, nhìn thấy) mà chưa chỉnh → mở ra tắt đúng các ô */
   private boolean crafterStep() {
      if (!this.crafterQueue.isEmpty() && this.config.configureCrafters && this.mc.player != null && this.mc.currentScreen == null) {
         Iterator<Entry<BlockPos, Integer>> var1 = this.crafterQueue.entrySet().iterator();

         while (var1.hasNext()) {
            Entry<BlockPos, Integer> var2 = var1.next();
            BlockPos var3 = var2.getKey();
            if (!this.mc.world.isChunkLoaded(var3)) {
               continue;
            }

            int var4 = this.desiredCrafterMask(var3);
            if (!SchematicPlan.isCrafter(this.mc.world.getBlockState(var3)) || var4 <= 0 || var2.getValue() >= 8) {
               var1.remove();
               this.crafterRetryAt.remove(var3);
               continue;
            }

            if (this.crafterRetryAt.getOrDefault(var3, 0) > this.ticks) {
               continue;
            }

            if (this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(var3)) > (this.reach() - 0.2) * (this.reach() - 0.2) || this.visiblePoint(var3) == null) {
               continue;
            }

            this.release();
            this.crafterPos = var3;
            this.crafterMask = var4;
            this.crafterStepNo = 0;
            this.crafterTicks = 0;
            this.crafterSends = 0;
            this.crafterOpenAt = -1000;
            this.crafterClicks = 0;
            this.crafterNoHit = 0;
            AutoBuildMod.LOGGER.info("[AutoBuild] Chỉnh crafter @ {}: tắt {} (theo {})", new Object[]{var3.toShortString(), maskLabel(var4), this.crafterLayerOverride(var3.getY()) != null ? "cài đặt tầng Y=" + var3.getY() : "bản vẽ"});
            this.setStage(BuildController.Stage.CRAFTER);
            return true;
         }
      }

      return false;
   }

   private void crafterDone(boolean var1, String var2) {
      if (this.mc.player != null && this.mc.player.currentScreenHandler instanceof net.minecraft.screen.CrafterScreenHandler) {
         this.mc.player.closeHandledScreen();
      }

      if (var1) {
         this.crafterQueue.remove(this.crafterPos);
         this.crafterRetryAt.remove(this.crafterPos);
      } else {
         this.crafterQueue.computeIfPresent(this.crafterPos, (var0, var1x) -> var1x + 1);
         if (this.crafterPos != null) {
            // 3.0.98: không làm lại NGAY crafter vừa hỏng (bản cũ lặp 3 lần × 15 giây = 45 giây đứng im) → xây tiếp, 20 giây sau quay lại
            this.crafterRetryAt.put(this.crafterPos.toImmutable(), this.ticks + 400);
         }
      }

      AutoBuildMod.LOGGER.info("[AutoBuild] Crafter @ {}: {}", this.crafterPos == null ? "-" : this.crafterPos.toShortString(), var2);
      this.crafterPos = null;
      this.setStage(BuildController.Stage.NEXT);
   }

   private void crafterTick() {
      if (this.crafterPos == null || this.mc.player == null) {
         this.setStage(BuildController.Stage.NEXT);
      } else if (++this.crafterTicks > 240) {
         this.crafterDone(false, "12 giây chưa chỉnh xong → xây tiếp, 20 giây sau quay lại");
      } else if (this.mc.player.currentScreenHandler instanceof net.minecraft.screen.CrafterScreenHandler var1) {
         // 3.0.95: nhanh hơn (người dùng: đặt crafter xuống là chỉnh xong luôn). Mỗi 2 tick gửi 1 ô khác cấu hình, KHÔNG chờ
         // server trả lời từng ô (bản cũ chờ trả lời nên mỗi ô mất ~1/4 giây ping và gửi lặp lại cùng ô). Gửi hết rồi mới kiểm tra 1 lần.
         if (this.crafterStepNo == 0) {
            this.crafterStepNo = 1;
            this.crafterSent = 0;
            this.crafterNextAt = this.crafterTicks + 2;
         }

         if (this.crafterTicks >= this.crafterNextAt) {
            for (int var2 = 0; var2 < 9; var2++) {
               boolean var3 = (this.crafterMask >> var2 & 1) != 0;
               if ((this.crafterSent >> var2 & 1) == 0 && var1.isSlotDisabled(var2) != var3) {
                  net.minecraft.screen.slot.Slot var4 = this.mc.player.currentScreenHandler.getSlot(var2);
                  if (var4 != null && var4.hasStack()) {
                     this.crafterDone(true, "ô " + (var2 + 1) + " đang có đồ → không tắt được, bỏ qua");
                     return;
                  }

                  if (++this.crafterSends > 30) {
                     this.crafterDone(false, "server không nhận thay đổi ô → thử lại sau");
                     return;
                  }

                  this.mc.interactionManager.slotChangedState(var2, this.mc.player.currentScreenHandler.syncId, !var3);
                  this.crafterSent |= 1 << var2;
                  this.crafterNextAt = this.crafterTicks + 2;
                  this.crafterCheckAt = this.crafterTicks + 2 + this.latencyTicks();
                  this.status = "Chỉnh crafter: " + (var3 ? "tắt" : "bật") + " ô " + (var2 + 1);
                  return;
               }
            }

            // đã gửi hết các ô lệch; chờ đủ 1 lượt ping rồi kiểm tra theo trạng thái server gửi về
            if (this.crafterTicks >= this.crafterCheckAt) {
               boolean var5 = true;

               for (int var6 = 0; var6 < 9; var6++) {
                  if (var1.isSlotDisabled(var6) != ((this.crafterMask >> var6 & 1) != 0)) {
                     var5 = false;
                     break;
                  }
               }

               if (var5) {
                  this.crafterDone(true, "đã tắt " + maskLabel(this.crafterMask));
               } else if (this.crafterTicks >= this.crafterCheckAt + 10) {
                  // server chưa nhận vài ô → gửi lại các ô còn lệch
                  this.crafterSent = 0;
                  this.crafterCheckAt = this.crafterTicks + 2 + this.latencyTicks();
               }
            }
         }
      } else if (this.mc.currentScreen != null) {
         if (this.crafterTicks > 20) {
            this.mc.player.closeHandledScreen();
         }
      } else {
         // chưa mở: nhìn vào crafter, không ngồi, bấm chuột phải
         Input.sneak(this.mc, false, this.ticks);
         Vec3d var5 = this.visiblePoint(this.crafterPos);
         if (var5 == null) {
            this.crafterDone(false, "không nhìn thấy crafter từ chỗ đứng → để sau");
         } else {
            Vec3d var6 = var5.subtract(this.mc.player.getEyePos());
            this.lookAt(MotionMath.yaw(var6.x, var6.z), MotionMath.pitch(var6.x, var6.y, var6.z));
            BlockHitResult var7 = PlacementSolver.ray(this.mc, this.reach());
            boolean var8 = var7.getType() == Type.BLOCK && var7.getBlockPos().equals(this.crafterPos);
            if (!var8 && this.rotationSent() && ++this.crafterNoHit > 60) {
               this.crafterDone(false, "3 giây không ngắm trúng crafter → xây tiếp, lát quay lại");
               return;
            }

            // 3.0.98: server lag — đã bấm mà menu chưa lên thì bấm lại sau ~1 giây + ping (bản cũ 1,5 giây nhưng hay kẹt chờ),
            // bấm 4 lần không lên menu → bỏ qua, xây tiếp, 20 giây sau quay lại chỉnh (không đứng chờ mãi)
            int var9 = 20 + this.latencyTicks();
            if (this.crafterClicks >= 4 && this.crafterTicks - this.crafterOpenAt > var9 + 10) {
               this.crafterDone(false, "bấm mở " + this.crafterClicks + " lần không lên menu (server lag) → xây tiếp, 20 giây sau quay lại");
               return;
            }

            if (this.rotationSent()
               && !this.mc.player.isInSneakingPose()
               && this.crafterTicks - this.crafterOpenAt > var9
               && var8) {
               this.crafterOpenAt = this.crafterTicks;
               this.clickTick = this.ticks;
               this.crafterClicks++;
               if (this.crafterClicks > 1) {
                  AutoBuildMod.LOGGER.info("[AutoBuild] Crafter @ {}: bấm mở chưa lên menu → bấm lại lần {}", this.crafterPos.toShortString(), this.crafterClicks);
               }

               if (this.tntVeto(var7)) {
                  this.crafterDone(false, "gần TNT — không bấm vào crafter");
                  return;
               }

               BlockHitResult clickVar7 = var7;
               Input.queueUse(this.mc, clickVar7, false, "crafter", () -> {
                  this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, clickVar7);
                  this.mc.player.swingHand(Hand.MAIN_HAND);
               });
            }

            this.status = "Mở crafter @ " + this.crafterPos.toShortString() + " để tắt " + maskLabel(this.crafterMask) + (this.crafterClicks > 1 ? " (bấm lần " + this.crafterClicks + ")" : "");
         }
      }
   }

   // ===== 3.0.123: cho đồ vào khối chứa theo bản vẽ (dropper, dispenser, hopper, rương, thùng, lò…) =====
   private final LinkedHashMap<BlockPos, Integer> fillQueue = new LinkedHashMap<>();
   private final Set<BlockPos> fillDone = new HashSet<>();
   private final Map<BlockPos, Integer> fillRetryAt = new HashMap<>();
   private final Map<String, Integer> fillSaid = new HashMap<>();
   private final Map<Item, Integer> fillCollectAt = new HashMap<>();
   private BlockPos fillPos;
   private BlockPos fillPartner;
   private ContainerFill.Job fillJob;
   private int fillTicks;
   private int fillOpenAt = -1000;
   private int fillClicks;
   private int fillNoHit;
   private int fillNextAt;
   private int fillCheckAt;
   private int fillActions;
   private int fillScanAt = -1000;
   private Object fillPlanFor;

   private String fillKey(BlockPos var1) {
      return this.plan.placement.getName() + "@" + this.plan.placement.getOrigin().toShortString() + "|" + var1.toShortString();
   }

   /** bản vẽ mới/nạp lại → xoá trạng thái, lấy lại danh sách khối chứa đã cho đủ đồ từ cấu hình (3.0.125: nhớ qua các lần vào game) */
   private void fillReset() {
      this.fillPlanFor = this.plan;
      this.fillQueue.clear();
      this.fillDone.clear();
      this.fillRetryAt.clear();
      this.fillNamedOnly.clear();
      this.fillGateTries.clear();
      this.fillUncover.clear();
      this.fillUncoverTries.clear();
      this.fillCollectLeft.clear();
      if (this.plan != null) {
         for (BlockPos var2 : this.plan.containerItems.keySet()) {
            if (this.config.filledContainers.contains(this.fillKey(var2))) {
               this.fillDone.add(var2.toImmutable());
            }
         }

         if (!this.fillDone.isEmpty()) {
            AutoBuildMod.LOGGER.info("[AutoBuild] Khối chứa: {} khối đã cho đủ đồ từ lần trước (không mở lại)", this.fillDone.size());
         }
      }
   }

   private void fillRemember(BlockPos var1, boolean var2) {
      if (this.plan != null && var1 != null) {
         boolean var3 = var2 ? this.config.filledContainers.add(this.fillKey(var1)) : this.config.filledContainers.remove(this.fillKey(var1));
         if (var3) {
            this.config.saveQuietly();
         }
      }
   }

   /** vừa đặt xong khối chứa có đồ trong bản vẽ → cho đồ vào ngay */
   private void noteContainerPlaced(BlockPos var1) {
      if (this.plan != null && this.plan.containerItems.containsKey(var1)) {
         if (this.fillPlanFor != this.plan) {
            this.fillReset();
         }

         this.fillRemember(var1, false);
         this.fillDone.remove(var1);
         this.fillQueue.putIfAbsent(var1.toImmutable(), 0);
      }
   }

   /** đồ cần trong khối chứa var1 (rương đôi: gộp cả nửa kia, theo thứ tự ô của menu 54 ô) */
   private Map<ContainerFill.Key, ContainerFill.Want> fillWants(BlockPos var1) {
      net.minecraft.nbt.NbtList var2 = this.plan.containerItems.get(var1);
      Map<ContainerFill.Key, ContainerFill.Want> var3 = var2 == null ? new LinkedHashMap<>() : ContainerFill.wants(var2, this.mc.world.getRegistryManager());
      this.fillPartner = null;
      BlockState var4 = this.mc.world.getBlockState(var1);
      BlockPos var5 = StateMatch.chestPartner(var1, var4);
      if (var5 != null && this.mc.world.getBlockState(var5).getBlock() == var4.getBlock()) {
         this.fillPartner = var5.toImmutable();
         net.minecraft.nbt.NbtList var6 = this.plan.containerItems.get(var5);
         Map<ContainerFill.Key, ContainerFill.Want> var7 = var6 == null ? Map.of() : ContainerFill.wants(var6, this.mc.world.getRegistryManager());
         // menu rương đôi: nửa "right" là 27 ô đầu
         boolean var8 = StateMatch.property(var4, "type").equals("right");
         var3 = var8 ? ContainerFill.merge(var3, var7, 27) : ContainerFill.merge(shiftWants(var3, 27), var7, 0);
      }

      return var3;
   }

   private static Map<ContainerFill.Key, ContainerFill.Want> shiftWants(Map<ContainerFill.Key, ContainerFill.Want> var0, int var1) {
      return ContainerFill.merge(new LinkedHashMap<>(), var0, var1);
   }

   /** khối chứa trong tầm với chưa có đúng đồ → đi mở ra cho đồ vào */
   private boolean fillStep() {
      if (this.plan == null || this.mc.player == null || this.mc.world == null || this.mc.currentScreen != null || this.cleanupMode) {
         return false;
      } else {
         if (this.fillPlanFor != this.plan) {
            this.fillReset();
         }

         if (this.ticks - this.fillScanAt >= 100 && !this.plan.containerItems.isEmpty()) {
            // khối chứa đã có sẵn (xây từ lần trước / vào lại server) mà chưa cho đồ → cũng xếp vào hàng
            this.fillScanAt = this.ticks;
            Vec3d var1 = this.mc.player.getEyePos();

            for (BlockPos var3 : this.plan.containerItems.keySet()) {
               SchematicPlan.Target var4 = this.plan.at(var3);
               if (!this.fillDone.contains(var3)
                  && var4 != null
                  && var1.squaredDistanceTo(Vec3d.ofCenter(var3)) < 64.0
                  && this.mc.world.isChunkLoaded(var3)
                  && this.mc.world.getBlockState(var3).getBlock() == var4.state().getBlock()) {
                  this.fillQueue.putIfAbsent(var3.toImmutable(), 0);
               }
            }
         }

         Iterator<Entry<BlockPos, Integer>> var5 = this.fillQueue.entrySet().iterator();

         while (var5.hasNext()) {
            Entry<BlockPos, Integer> var6 = var5.next();
            BlockPos var7 = var6.getKey();
            if (this.fillDone.contains(var7) || !this.mc.world.isChunkLoaded(var7) || var6.getValue() >= 8) {
               var5.remove();
            } else if (!ContainerFill.fillable(this.mc.world.getBlockState(var7))) {
               var5.remove();
            } else if (this.fillRetryAt.getOrDefault(var7, 0) <= this.ticks
               && !(this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(var7)) > (this.reach() - 0.2) * (this.reach() - 0.2))
               && this.visiblePoint(var7) != null
               && !this.tntUnsafeAt(var7)) {
               Map<ContainerFill.Key, ContainerFill.Want> var8 = this.fillWants(var7);
               if (var8.isEmpty()) {
                  var5.remove();
               } else {
                  this.release();
                  this.fillPos = var7;
                  this.fillJob = new ContainerFill.Job(var8);
                  this.fillTicks = 0;
                  this.fillOpenAt = -1000;
                  this.fillClicks = 0;
                  this.fillNoHit = 0;
                  this.fillNextAt = 0;
                  this.fillCheckAt = -1;
                  this.fillActions = 0;
                  AutoBuildMod.LOGGER.info("[AutoBuild] {} @ {}: bản vẽ có đồ bên trong ({}) → mở ra cho đồ vào",
                     new Object[]{this.mc.world.getBlockState(var7).getBlock().getName().getString(), var7.toShortString(), wantsLabel(var8.values())});
                  this.setStage(BuildController.Stage.FILL);
                  return true;
               }
            }
         }

         return false;
      }
   }

   // khối chứa chỉ còn thiếu đồ có tên riêng (không có trong túi) → được lên tầng
   private final Set<BlockPos> fillNamedOnly = new HashSet<>();
   private int fillGateSaidAt = -1000;
   private final Map<BlockPos, Integer> fillGateTries = new HashMap<>();
   private final Map<Item, Integer> fillCollectLeft = new HashMap<>();
   // 3.0.125: khối che đã đập để mở khối chứa bị bao kín → khối chứa đó; chưa cho đồ xong thì chưa đặt lại khối che
   private final Map<BlockPos, BlockPos> fillUncover = new HashMap<>();
   private final Map<BlockPos, Integer> fillUncoverAt = new HashMap<>();
   private final Map<BlockPos, Integer> fillUncoverTries = new HashMap<>();

   /** khối chứa ở tầng thấp hơn tầng sắp xây mà chưa đủ đồ → đi tới cho đồ (true = đã lo việc đó tick này) */
   private boolean fillGate(Integer var1) {
      if (this.plan == null || this.plan.containerItems.isEmpty() || this.mc.player == null || this.cleanupMode || this.pending != null) {
         return false;
      } else {
         if (this.fillPlanFor != this.plan) {
            this.fillReset();
         }

         BlockPos var2 = null;
         double var3 = Double.MAX_VALUE;
         boolean var5 = false;

         for (BlockPos var7 : this.plan.containerItems.keySet()) {
            if ((var1 == null || var7.getY() < var1)
               && this.config.inYRange(var7.getY())
               && !this.fillDone.contains(var7)
               && !this.fillNamedOnly.contains(var7)
               && this.mc.world.isChunkLoaded(var7)) {
               SchematicPlan.Target var8 = this.plan.at(var7);
               BlockState var9 = this.mc.world.getBlockState(var7);
               if (var8 != null && var9.getBlock() == var8.state().getBlock() && ContainerFill.fillable(var9) && this.fillQueue.getOrDefault(var7, 0) < 8) {
                  if (this.fillRetryAt.getOrDefault(var7, 0) > this.ticks) {
                     var5 = true;
                  } else {
                     double var10 = this.mc.player.squaredDistanceTo(Vec3d.ofCenter(var7));
                     if (var10 < var3) {
                        var3 = var10;
                        var2 = var7;
                     }
                  }
               }
            }
         }

         if (var2 == null) {
            if (var5) {
               // đang chờ lấy đồ (Order/Shop) cho khối chứa tầng dưới → chưa lên tầng
               this.status = "Chờ đồ cho khối chứa tầng dưới rồi mới lên tầng" + (var1 == null ? "" : " Y=" + var1);
               if (this.ticks - this.fillGateSaidAt > 600) {
                  this.fillGateSaidAt = this.ticks;
                  AutoBuildMod.LOGGER.info("[AutoBuild] {}", this.status);
               }

               this.setStage(BuildController.Stage.SELECT);
               return true;
            } else {
               return false;
            }
         } else {
            this.fillQueue.putIfAbsent(var2.toImmutable(), 0);
            if (this.fillStep()) {
               return true;
            } else {
               int var12 = this.fillGateTries.merge(var2.toImmutable(), 1, Integer::sum);
               // 3.0.125 (người dùng): khối chứa bị bao kín (không mặt nào hở) hoặc đi tới 3 lần vẫn không mở được → đập 1 khối che
               // (ưu tiên khối thường), cho đồ vào rồi đặt lại khối che
               if ((!this.exposed(var2) || var12 > 3) && this.fillUncoverStart(var2)) {
                  return true;
               }

               if (var12 > 8) {
                  // không tới được, cũng không gỡ được khối che → báo và cho lên tầng, không kẹt mãi
                  this.fillNamedOnly.add(var2.toImmutable());
                  this.say("Không tới được khối chứa @ " + var2.toShortString() + " để cho đồ (đã thử gỡ khối che) → bỏ qua, xây tiếp.");
                  return false;
               }

               if (this.ticks - this.fillGateSaidAt > 200) {
                  this.fillGateSaidAt = this.ticks;
                  AutoBuildMod.LOGGER.info("[AutoBuild] Khối chứa @ {} chưa đủ đồ → đi tới cho đồ vào trước khi lên tầng trên", var2.toShortString());
               }

               this.release();
               this.target = null;
               this.temporary = null;
               this.supportTarget = null;
               this.repairGoal = var2.toImmutable();
               this.beginGoals(false);
               return true;
            }
         }
      }
   }

   /** khối che đã đập cho khối chứa chưa cho đồ xong → chưa đặt lại (tối đa 3 phút) */
   private boolean fillHolds(BlockPos var1) {
      BlockPos var2 = this.fillUncover.get(var1);
      if (var2 == null) {
         return false;
      } else if (this.fillDone.contains(var2) || this.fillNamedOnly.contains(var2) || this.ticks - this.fillUncoverAt.getOrDefault(var1, 0) > 3600) {
         this.fillUncover.remove(var1);
         this.fillUncoverAt.remove(var1);
         return false;
      } else {
         return true;
      }
   }

   private static boolean redstoneish(BlockState var0) {
      String var1 = Registries.BLOCK.getId(var0.getBlock()).getPath();
      return var0.hasBlockEntity()
         || var0.emitsRedstonePower()
         || var1.contains("redstone")
         || var1.contains("repeater")
         || var1.contains("comparator")
         || var1.contains("observer")
         || var1.contains("piston")
         || var1.contains("rail")
         || var1.contains("lever")
         || var1.contains("button")
         || var1.contains("pressure_plate")
         || var1.contains("torch")
         || var1.contains("tripwire")
         || var1.contains("note_block")
         || var1.contains("target")
         || var1.contains("sculk")
         || var1.contains("slime")
         || var1.contains("honey");
   }

   /** chọn 1 khối che quanh khối chứa var1 để đập: khối thường trước, khối redstone/khối chứa sau cùng; đồ trong bản vẽ thì không đập */
   private boolean fillUncoverStart(BlockPos var1) {
      if (!this.config.fixWrong || this.repairing || this.sweeping || this.mc.world == null) {
         return false;
      } else {
         for (Entry<BlockPos, BlockPos> var3 : this.fillUncover.entrySet()) {
            if (var3.getValue().equals(var1) && !this.mc.world.getBlockState(var3.getKey()).isReplaceable()) {
               // đang gỡ dở khối che cho khối chứa này
               return this.tryRepairNow() || this.walkToRepair();
            }
         }

         if (this.fillUncoverTries.getOrDefault(var1, 0) >= 3) {
            return false;
         } else {
            BlockPos var4 = null;
            double var5 = Double.MAX_VALUE;
            BlockState var7 = null;

            for (Direction var11 : Direction.values()) {
               BlockPos var12 = var1.offset(var11);
               BlockState var13 = this.mc.world.getBlockState(var12);
               if (!var13.isReplaceable()
                  && this.breakableWrong(var13, var12)
                  && this.tntBreakRisk(var12) == null
                  && !this.plan.containerItems.containsKey(var12)
                  && !this.fillUncover.containsKey(var12)) {
                  // khối chứa khác có đồ trong bản vẽ thì không đập (đồ bên trong rơi ra)
                  double var14 = (redstoneish(var13) ? 100.0 : 0.0)
                     + (var11 == Direction.UP ? 0.0 : (var11 == Direction.DOWN ? 6.0 : 2.0))
                     + (this.exposed(var12) ? 0.0 : 20.0)
                     + Math.sqrt(this.mc.player.squaredDistanceTo(Vec3d.ofCenter(var12))) * 0.1;
                  if (var14 < var5) {
                     var5 = var14;
                     var4 = var12;
                     var7 = var13;
                  }
               }
            }

            if (var4 == null) {
               return false;
            } else {
               this.fillUncoverTries.merge(var1.toImmutable(), 1, Integer::sum);
               this.fillGateTries.remove(var1);
               BlockPos var15 = var4.toImmutable();
               this.fillUncover.put(var15, var1.toImmutable());
               this.fillUncoverAt.put(var15, this.ticks);
               this.placedDone.remove(var15);
               this.repairWrong.put(var15, var7);
               this.coverFor.put(var15, var1.toImmutable());
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] Khối chứa @ {} bị che kín → đập {} @ {} để mở, cho đồ xong mới đặt lại",
                     new Object[]{var1.toShortString(), var7.getBlock().getName().getString(), var15.toShortString()}
                  );
               if (!this.tryRepairNow() && !this.walkToRepair()) {
                  this.repairWrong.remove(var15);
                  this.coverFor.remove(var15);
                  this.fillUncover.remove(var15);
                  return false;
               } else {
                  return true;
               }
            }
         }
      }
   }

   private boolean tntUnsafeAt(BlockPos var1) {
      return TntGuard.tntWithin(var1, 6, null) != null;
   }

   private static String wantsLabel(Collection<ContainerFill.Want> var0) {
      StringBuilder var1 = new StringBuilder();

      for (ContainerFill.Want var3 : var0) {
         if (var1.length() > 0) {
            var1.append(", ");
         }

         var1.append(var3.count()).append(" ").append(var3.key().label());
         if (var1.length() > 120) {
            var1.append("…");
            break;
         }
      }

      return var1.toString();
   }

   private void fillDoneWith(boolean var1, int var2, String var3) {
      if (this.mc.player != null && this.mc.player.currentScreenHandler != this.mc.player.playerScreenHandler) {
         this.mc.player.closeHandledScreen();
      }

      if (this.fillPos != null) {
         if (var1) {
            this.fillNamedOnly.remove(this.fillPos);
            this.fillRemember(this.fillPos, true);
            this.fillRemember(this.fillPartner, true);
            this.fillDone.add(this.fillPos);
            this.fillQueue.remove(this.fillPos);
            this.fillRetryAt.remove(this.fillPos);
            if (this.fillPartner != null) {
               this.fillDone.add(this.fillPartner);
               this.fillQueue.remove(this.fillPartner);
            }
         } else {
            this.fillQueue.merge(this.fillPos, 1, Integer::sum);
            this.fillRetryAt.put(this.fillPos.toImmutable(), this.ticks + var2);
         }

         AutoBuildMod.LOGGER.info("[AutoBuild] Khối chứa @ {}: {}", this.fillPos.toShortString(), var3);
      }

      this.fillPos = null;
      this.fillJob = null;
      this.setStage(BuildController.Stage.NEXT);
   }

   private void fillTick() {
      if (this.fillPos == null || this.fillJob == null || this.mc.player == null) {
         this.setStage(BuildController.Stage.NEXT);
      } else if (++this.fillTicks > 600) {
         this.fillDoneWith(false, 400, "30 giây chưa cho đồ xong → xây tiếp, 20 giây sau quay lại");
      } else {
         ScreenHandler var1 = this.mc.player.currentScreenHandler;
         if (var1 != this.mc.player.playerScreenHandler && this.mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen) {
            int var2 = var1.slots.size() - 36;
            if (var2 <= 0) {
               this.fillDoneWith(false, 400, "menu mở ra không phải khối chứa");
            } else if (this.fillTicks >= this.fillNextAt) {
               this.fillNextAt = this.fillTicks + 2;
               ContainerFill.Click var3 = this.fillJob.next(var1, var2);
               if (var3 != null) {
                  if (++this.fillActions > 400) {
                     this.fillDoneWith(false, 1200, "bấm quá nhiều lần mà chưa đúng → để sau");
                     return;
                  }

                  this.mc.interactionManager.clickSlot(var1.syncId, var3.slot(), var3.button(), var3.type(), this.mc.player);
                  this.fillCheckAt = -1;
                  this.status = "Cho đồ vào khối chứa: " + var3.why();
               } else if (!var1.getCursorStack().isEmpty()) {
                  this.fillDoneWith(false, 400, "túi đầy, không trả được món trên con trỏ");
               } else if (this.fillJob.satisfied(var1, var2)) {
                  // chờ 1 lượt ping rồi xem lại theo đồ server gửi về
                  if (this.fillCheckAt < 0) {
                     this.fillCheckAt = this.fillTicks + 4 + this.latencyTicks() * 2;
                  } else if (this.fillTicks >= this.fillCheckAt) {
                     this.fillDoneWith(true, 0, "đã cho đúng đồ: " + wantsLabel(this.fillJob.wants().values()));
                  }
               } else if (!this.fillJob.missing.isEmpty()) {
                  this.fillMissing(this.fillJob.missing);
               } else {
                  this.fillDoneWith(false, 400, "không sắp được đồ (ô khối chứa không nhận?) → để sau");
               }
            }
         } else if (this.mc.currentScreen != null) {
            if (this.fillTicks > 20) {
               this.mc.player.closeHandledScreen();
            }
         } else {
            // chưa mở: đứng thẳng, nhìn vào khối, bấm chuột phải
            Input.sneak(this.mc, false, this.ticks);
            BlockState var4 = this.mc.world.getBlockState(this.fillPos);
            if (!ContainerFill.fillable(var4)) {
               this.fillDoneWith(false, 400, "khối không còn ở đó");
               return;
            }

            Vec3d var5 = this.visiblePoint(this.fillPos);
            if (var5 == null) {
               this.fillDoneWith(false, 400, "không nhìn thấy khối từ chỗ đứng → để sau");
               return;
            }

            Vec3d var6 = var5.subtract(this.mc.player.getEyePos());
            this.lookAt(MotionMath.yaw(var6.x, var6.z), MotionMath.pitch(var6.x, var6.y, var6.z));
            BlockHitResult var7 = PlacementSolver.ray(this.mc, this.reach());
            boolean var8 = var7.getType() == Type.BLOCK && var7.getBlockPos().equals(this.fillPos);
            if (!var8 && this.rotationSent() && ++this.fillNoHit > 60) {
               this.fillDoneWith(false, 400, "3 giây không ngắm trúng → xây tiếp, lát quay lại");
               return;
            }

            int var9 = 20 + this.latencyTicks();
            if (this.fillClicks >= 4 && this.fillTicks - this.fillOpenAt > var9 + 10) {
               this.fillDoneWith(false, 400, "bấm mở " + this.fillClicks + " lần không lên menu (bị chặn / server lag) → để sau");
               return;
            }

            if (this.rotationSent() && !this.mc.player.isInSneakingPose() && this.fillTicks - this.fillOpenAt > var9 && var8 && !Input.usePending()) {
               if (this.tntVeto(var7)) {
                  this.fillDoneWith(false, 2400, "gần TNT — không mở");
                  return;
               }

               this.fillOpenAt = this.fillTicks;
               this.clickTick = this.ticks;
               this.fillClicks++;
               BlockHitResult var10 = var7;
               Input.queueUse(this.mc, var10, true, "mở khối chứa", () -> {
                  this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, var10);
                  this.mc.player.swingHand(Hand.MAIN_HAND);
               });
            }

            this.status = "Mở " + var4.getBlock().getName().getString() + " @ " + this.fillPos.toShortString() + " để cho đồ vào";
         }
      }
   }

   /** thiếu đồ để cho vào: đồ có tên riêng → báo chat, để trống; đồ thường → lấy như vật liệu (ender chest → Order → /shop) */
   private void fillMissing(List<ContainerFill.Want> var1) {
      ContainerFill.Want var2 = null;
      StringBuilder var3 = new StringBuilder();

      for (ContainerFill.Want var5 : var1) {
         if (var5.key().name() != null) {
            String var6 = this.fillPos.toShortString() + "|" + var5.key().label();
            if (this.ticks - this.fillSaid.getOrDefault(var6, -100000) > 6000) {
               this.fillSaid.put(var6, this.ticks);
               this.say(
                  this.mc.world.getBlockState(this.fillPos).getBlock().getName().getString() + " @ " + this.fillPos.toShortString() + " cần " + var5.count() + " " + var5.key().label()
                     + " nhưng trong túi không có đồ đúng tên đó → để trống, xây tiếp (bỏ đồ đúng tên vào túi thì mod tự cho vào)"
               );
            }
         } else if (var2 == null) {
            var2 = var5;
         }

         if (var3.length() > 0) {
            var3.append(", ");
         }

         var3.append(var5.count()).append(" ").append(var5.key().label());
      }

      // 3.0.125: lần lấy trước có thêm được đồ (còn thiếu ít hơn) → lấy tiếp ngay, không chờ 60 giây (log: thiếu 92 Bone Meal chờ mãi)
      if (var2 != null
         && (this.ticks - this.fillCollectAt.getOrDefault(var2.key().item(), -100000) > 1200
            || var2.count() < this.fillCollectLeft.getOrDefault(var2.key().item(), Integer.MAX_VALUE))) {
         Item var7 = var2.key().item();
         this.fillCollectAt.put(var7, this.ticks);
         this.fillCollectLeft.put(var7, var2.count());
         this.fillDoneWith(false, 100, "thiếu " + var3 + " → lấy " + var7.getName().getString() + " như vật liệu rồi quay lại");
         this.say("Thiếu " + var2.count() + " " + var7.getName().getString() + " để cho vào khối chứa → lấy thêm (ender chest / Order / Shop).");
         this.collect(var7, BuildController.Stage.SELECT, var2.count());
      } else {
         // chỉ còn thiếu đồ tên riêng (hoặc vừa đi lấy rồi): giữ phần đã cho, lát nữa xem lại
         if (var2 == null && this.fillPos != null) {
            // 3.0.124: chỉ thiếu đồ tên riêng → được lên tầng (người dùng), lần sau có đồ đúng tên thì cho vào
            this.fillNamedOnly.add(this.fillPos.toImmutable());
         }

         this.fillDoneWith(false, var2 == null ? 1200 : 200, "còn thiếu " + var3 + " → để trống, " + (var2 == null ? "60" : "10") + " giây sau xem lại");
      }
   }

   // ===== 3.0.95: kho đồ gọn — ender chest làm kho tạm, gộp xô rỗng, /sell đồ không cần =====
   private static final Set<String> EXTRA_FOODS = Set.of(
      "apple", "golden_apple", "enchanted_golden_apple", "bread", "carrot", "golden_carrot", "potato", "baked_potato", "melon_slice",
      "sweet_berries", "glow_berries", "cookie", "pumpkin_pie", "mushroom_stew", "rabbit_stew", "beetroot", "beetroot_soup",
      "suspicious_stew", "dried_kelp", "honey_bottle", "chorus_fruit", "beef", "porkchop", "chicken", "mutton", "rabbit", "cod", "salmon"
   );
   private final com.donutautobuild.order.SellJob seller;
   private BuildController.Stage sellAfter = BuildController.Stage.SELECT;
   private int sellRetryAt;
   private int freeSpaceAt = -100000;
   private Object planItemsFor;
   private final Set<Item> planItemsCache = new HashSet<>();

   private static final int ST_FIND = 0;
   private static final int ST_PLACE = 1;
   private static final int ST_WAIT_PLACED = 2;
   private static final int ST_OPEN = 3;
   private static final int ST_WAIT_OPEN = 4;
   private static final int ST_TRANSFER = 5;
   private static final int ST_CONFIRM = 6;
   private static final int ST_BREAK = 7;
   private static final int ST_PICKUP = 8;
   private static Item enderChestItem;
   private static Item totemItem;

   /** tra vật phẩm theo id (không cần thêm trường Items.* mới) */
   private static Item enderChestItem() {
      if (enderChestItem == null) {
         enderChestItem = ((Block)Registries.BLOCK.get(Identifier.tryParse("minecraft:ender_chest"))).asItem();
      }

      return enderChestItem;
   }

   private static Item totemItem() {
      if (totemItem == null) {
         totemItem = (Item)Registries.ITEM.get(Identifier.tryParse("minecraft:totem_of_undying"));
      }

      return totemItem;
   }

   private boolean stashDeposit;
   private BlockPos stashLeft;
   private String stashReason = "";
   private int stashNoSpotSaidAt = -100000;
   private int stashDropBefore;
   private int stashStep = -1;
   private int stashTicks;
   private int stashStepTicks;
   private BlockPos stashPos;
   private boolean stashPlaced;
   private int stashTries;
   private int stashClickAt = -1000;
   private int stashNextMove;
   private int stashIdleAt = -1;
   private Set<Item> stashItems = Set.of();
   private Item stashWant;
   private int stashNeed;
   private int stashBefore;
   private BuildController.Stage stashAfter = BuildController.Stage.SELECT;
   private final Set<Integer> stashMoved = new HashSet<>();
   // 3.0.97: 1 lần mở ender chest = cất + lấy cả danh sách thiếu của tầng
   private Set<Item> stashDepositItems = Set.of();
   private int stashDepositLimit = Integer.MAX_VALUE;
   private int stashDeposited;
   private final LinkedHashMap<Item, Integer> stashWantMap = new LinkedHashMap<>();
   private final HashMap<Item, Set<Item>> stashGroups = new HashMap<>();
   private final HashMap<Item, Integer> stashCountBefore = new HashMap<>();
   private int stashTopLayer = Integer.MIN_VALUE;
   private Object stashTopPlan;
   private int stashBreakTries;
   private int stashFailUntil;
   private final HashMap<Item, Integer> enderSnap = new HashMap<>();
   private int enderSnapAt = -100000;
   private final HashMap<Item, Integer> stashTriedFor = new HashMap<>();
   private boolean silkWarned;
   private Item forceTool;

   /** mọi vật phẩm công trình có thể dùng (kể cả vật phẩm cùng họ thay thế, khối độn) — đồ ngoài danh sách này mới đem bán */
   private Set<Item> planItems() {
      if (this.plan == null) {
         return Set.of();
      } else {
         if (this.planItemsFor != this.plan) {
            this.planItemsFor = this.plan;
            this.planItemsCache.clear();

            for (SchematicPlan.Target var2 : this.plan.targets) {
               Item var3 = Materials.item(Blocks.AIR.getDefaultState(), var2.state());
               if (var3 != Items.AIR && this.planItemsCache.add(var3)) {
                  this.planItemsCache.addAll(this.orderAlternates(var3));
               }
            }

            Identifier var4 = Identifier.tryParse(this.config.fillerOrderItem);
            Block var5 = var4 == null ? Blocks.AIR : (Block)Registries.BLOCK.get(var4);
            if (var5 != Blocks.AIR) {
               this.planItemsCache.add(var5.asItem());
            }
         }

         return this.planItemsCache;
      }
   }

   /** vật phẩm tầng này còn cần (ô chưa xong), kèm vật phẩm cùng họ */
   private Set<Item> layerItems(int var1) {
      HashSet<Item> var2 = new HashSet<>();
      if (this.plan != null && this.mc.world != null) {
         for (SchematicPlan.Target var4 : this.plan.targets) {
            BlockPos var5 = var4.pos();
            if (var5.getY() == var1 && !this.skipped.contains(var5) && this.mc.world.isChunkLoaded(var5)) {
               BlockState var6 = this.mc.world.getBlockState(var5);
               if (!this.done(var6, var4.state())) {
                  Item var7 = Materials.item(var6, var4.state());
                  if (var2.add(var7)) {
                     var2.addAll(this.orderAlternates(var7));
                  }

                  var2.add(this.materialFor(var4, var6));
               }
            }
         }
      }

      return var2;
   }

   // 3.0.123: đồ bản vẽ cho vào khối chứa (thuốc súng, mũi tên, xô…) → không /sell, không cất ender chest
   private final Set<Item> containerKeep = new HashSet<>();
   private Object containerKeepFor;

   private Set<Item> containerKeep() {
      if (this.plan != null && this.containerKeepFor != this.plan) {
         this.containerKeepFor = this.plan;
         this.containerKeep.clear();

         for (net.minecraft.nbt.NbtList var2 : this.plan.containerItems.values()) {
            for (int var3 = 0; var3 < var2.size(); var3++) {
               Identifier var4 = Identifier.tryParse(var2.getCompound(var3).getString("id"));
               if (var4 != null && Registries.ITEM.containsId(var4)) {
                  this.containerKeep.add(Registries.ITEM.get(var4));
               }
            }
         }
      }

      return this.containerKeep;
   }

   private boolean keepAlways(ItemStack var1) {
      Item var2 = var1.getItem();
      String var3 = Registries.ITEM.getId(var2).getPath();
      return var2 == this.scaffoldItem()
         || this.containerKeep().contains(var2)
         || var2 == enderChestItem()
         || var2 == totemItem()
         || var1.isDamageable()
         || FOODS.contains(var3)
         || EXTRA_FOODS.contains(var3)
         || var3.startsWith("cooked_")
         || var3.endsWith("shulker_box")
         || var3.endsWith("bundle")
         || var3.endsWith("_bucket");
   }

   /** món đem /sell: xô rỗng, và đồ không thuộc vật liệu công trình (trừ cúp, đồ ăn, khối kê, totem, ender chest…) */
   private boolean sellable(ItemStack var1) {
      if (var1.isEmpty()) {
         return false;
      } else if (var1.isOf(Items.BUCKET)) {
         return true;
      } else {
         return this.config.sellJunk && this.plan != null && !this.keepAlways(var1) && !this.planItems().contains(var1.getItem());
      }
   }

   /** gộp các ô xô rỗng thành 1 chồng (xô rỗng chồng được 16) — thay cho ném xô ra ngoài (hopper hút làm hỏng farm) */
   private boolean qualityStackBucketsOriginal() {
      PlayerInventory var1 = this.mc.player.getInventory();
      int var2 = -1;
      int var3 = -1;

      for (int var4 = 0; var4 < 36; var4++) {
         ItemStack var5 = var1.getStack(var4);
         if (var5.isOf(Items.BUCKET) && var5.getCount() < 16) {
            if (var2 < 0) {
               var2 = var4;
            } else {
               var3 = var4;
               break;
            }
         }
      }

      if (var2 >= 0 && var3 >= 0 && this.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
         // 3.0.123: bấm túi đồ chỉ khi đứng yên, cuối tick gửi gói đóng túi
         if (!InventoryAccess.invReady(this.mc)) {
            this.status = "Dừng lại để gộp xô rỗng";
            return true;
         }

         InventoryAccess.invClicked();
         int var6 = var3 < 9 ? 36 + var3 : var3;
         int var7 = var2 < 9 ? 36 + var2 : var2;
         int var8 = this.mc.player.playerScreenHandler.syncId;
         this.mc.interactionManager.clickSlot(var8, var6, 0, SlotActionType.PICKUP, this.mc.player);
         this.mc.interactionManager.clickSlot(var8, var7, 0, SlotActionType.PICKUP, this.mc.player);
         if (!this.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            this.mc.interactionManager.clickSlot(var8, var6, 0, SlotActionType.PICKUP, this.mc.player);
         }

         this.status = "Gộp xô rỗng";
         return true;
      } else {
         this.dropBuckets = false;
         return false;
      }
   }

   /** 3.0.95: đủ xô rỗng hoặc túi sắp đầy → /sell */
   private boolean sellStep() {
      if (this.config.autoSell && !this.cleanupMode && this.mc.player != null && this.plan != null && this.ticks >= this.sellRetryAt) {
         int var1 = InventoryAccess.count(this.mc, Items.BUCKET);
         boolean var2 = InventoryAccess.freeSlots(this.mc) <= this.config.reserveFreeSlots + 1;
         if (var1 >= this.config.sellBucketsAt) {
            return this.startSell("Đã có " + var1 + " xô rỗng → /" + this.config.sellCommand + " bán bớt", BuildController.Stage.SELECT);
         } else {
            return var2 && this.startSell("Túi sắp đầy → /" + this.config.sellCommand + " bán đồ không cần", BuildController.Stage.SELECT);
         }
      } else {
         return false;
      }
   }

   private boolean startSell(String var1, BuildController.Stage var2) {
      if (this.config.autoSell && this.mc.player != null && this.ticks >= this.sellRetryAt) {
         int var3 = com.donutautobuild.order.SellJob.count(this.mc, this::sellable);
         if (var3 <= 0) {
            this.sellRetryAt = this.ticks + 600;
            return false;
         } else {
            this.sellRetryAt = this.ticks + 600;
            this.release();
            this.sellAfter = var2;
            AutoBuildMod.LOGGER.info("[AutoBuild] {} ({} món: {})", new Object[]{var1, var3, this.sellLabel()});
            this.say(var1 + " (" + this.sellLabel() + ").");
            this.seller.start(this.mc, this::sellable);
            this.setStage(BuildController.Stage.SELL);
            return true;
         }
      } else {
         return false;
      }
   }

   private String sellLabel() {
      LinkedHashMap<String, Integer> var1 = new LinkedHashMap<>();

      for (int var2 = 0; var2 < 36; var2++) {
         ItemStack var3 = this.mc.player.getInventory().getStack(var2);
         if (this.sellable(var3)) {
            var1.merge(var3.getName().getString(), var3.getCount(), Integer::sum);
         }
      }

      StringBuilder var4 = new StringBuilder();

      for (Entry<String, Integer> var6 : var1.entrySet()) {
         if (var4.length() > 0) {
            var4.append(", ");
         }

         var4.append(var6.getKey()).append(" x").append(var6.getValue());
         if (var4.length() > 90) {
            var4.append("…");
            break;
         }
      }

      return var4.toString();
   }

   private int stillTicks;
   private int airStillTicks;

   /**
    * 3.0.105: lệnh mở menu của DonutSMP (/sell, /home) chỉ lên menu khi đang đứng yên. Log 29/09: /sell gửi 265 lần chỉ lên menu 7 lần —
    * cả 7 lần đều lúc nhân vật đang đứng yên (vừa đóng /shop, vừa vào lại, đang kẹt v=0); hụt khi đang đi / đang ngồi ở mép.
    * → nhả phím, đứng thẳng, chờ đứng yên 8 tick (tối đa 3 giây) rồi mới gửi. Đang rơi / không chạm đất thì không chờ.
    */
   private boolean stillGate() {
      if (this.stageTicks <= 1) {
         this.stillTicks = 0;
      }

      if (this.stillTicks < 8 && this.stageTicks < 60 && this.mc.player != null && this.mc.player.isOnGround()) {
         this.release();
         Input.forceStand(this.mc);
         boolean var1 = !this.mc.player.isSneaking() && this.mc.player.getVelocity().horizontalLengthSquared() < 1.0E-4;
         this.stillTicks = var1 ? this.stillTicks + 1 : 0;
         this.status = "Đứng yên trước khi gửi lệnh";
         return true;
      } else {
         return false;
      }
   }

   private void sellTick() {
      if (this.stillGate()) {
         return;
      }

      this.seller.tick(this.mc);
      this.status = this.seller.status();
      if (!this.seller.active()) {
         if (this.seller.result() == com.donutautobuild.order.SellJob.Result.DONE) {
            this.say("Đã /" + this.config.sellCommand + ": " + this.seller.status() + ".");
         } else {
            // bán không được → 5 phút sau mới thử lại, không lặp
            this.sellRetryAt = this.ticks + 6000;
         }

         if (this.mc.currentScreen != null) {
            this.mc.player.closeHandledScreen();
         }

         if (this.sellAfter == BuildController.Stage.EQUIP) {
            this.setStage(BuildController.Stage.EQUIP);
         } else {
            this.beginScan();
         }
      }
   }

   /** túi đầy: cất vật liệu tầng này không dùng vào ender chest, không có gì để cất thì /sell đồ không cần */
   private boolean freeSpace(String var1) {
      if (this.ticks - this.freeSpaceAt < 400) {
         return false;
      } else {
         this.freeSpaceAt = this.ticks;
         return this.stashMakeRoom(var1) || this.startSell(var1 + " → /" + this.config.sellCommand + " bán đồ không cần", BuildController.Stage.SELECT);
      }
   }

   /** ender chest còn nằm lại do bị dừng giữa chừng → quay lại đập thu về (khi đứng gần) */
   private boolean stashCleanupStep() {
      if (this.stashLeft == null || this.mc.world == null || this.mc.player == null) {
         return false;
      } else if (this.mc.world.getBlockState(this.stashLeft).getBlock().asItem() != enderChestItem()) {
         this.stashLeft = null;
         return false;
      } else if (this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(this.stashLeft)) > (this.reach() - 0.3) * (this.reach() - 0.3)
         || this.visiblePoint(this.stashLeft) == null) {
         return false;
      } else {
         AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest @ {} còn nằm lại từ lần trước → đập thu về", this.stashLeft.toShortString());
         this.release();
         this.stashPos = this.stashLeft;
         this.stashLeft = null;
         this.stashDeposit = true;
         this.stashItems = Set.of();
         this.stashDepositItems = Set.of();
         this.stashWantMap.clear();
         this.stashWant = null;
         this.stashBreakTries = 0;
         this.stashPlaced = true;
         this.stashTicks = 0;
         this.stashNext(ST_BREAK);
         this.setStage(BuildController.Stage.STASH);
         return true;
      }
   }

   /**
    * 3.0.97: chỉ khi lên tầng MỚI (cao hơn mọi tầng đã lên) mới cất đồ thừa. Quay lại tầng dưới để sửa vài khối
    * (piston đẩy mất…) thì KHÔNG cất (bản cũ cất cả đống rồi lát nữa lại phải lấy ra).
    * Cùng lần mở đó lấy luôn những món tầng mới còn thiếu (nếu ender chest có).
    */
   private boolean stashDepositStep() {
      if (this.config.useEnderChest && this.currentLayer != null && this.plan != null && !this.cleanupMode && this.mc.player != null) {
         if (this.stashTopPlan != this.plan) {
            this.stashTopPlan = this.plan;
            this.stashTopLayer = Integer.MIN_VALUE;
         }

         if (this.currentLayer <= this.stashTopLayer) {
            return false;
         } else if (this.temporary == null && this.supportTarget == null && this.supportQueue.isEmpty() && !this.pillar && this.mc.player.isOnGround()) {
            this.stashTopLayer = this.currentLayer;
            Set<Item> var1 = this.leftovers(this.currentLayer, false);
            LinkedHashMap<Item, Integer> var2 = this.layerDeficits(this.currentLayer);
            this.keepInEnder(var2);
            if (var1.isEmpty() && var2.isEmpty()) {
               return false;
            } else {
               for (Item var4 : var2.keySet()) {
                  this.stashTriedFor.put(var4, this.ticks);
               }

               String var5 = "Lên tầng Y=" + this.currentLayer + ":"
                  + (var1.isEmpty() ? "" : " cất " + itemsLabel(var1) + " (tầng này không dùng)")
                  + (var2.isEmpty() ? "" : (var1.isEmpty() ? "" : ";") + " lấy " + itemsLabel(var2.keySet()) + " (tầng này còn thiếu)");
               return this.stashStart(var1, Integer.MAX_VALUE, var2, null, 0, BuildController.Stage.SELECT, var5 + " → mở ender chest 1 lần");
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static String itemsLabel(Collection<Item> var0) {
      StringBuilder var1 = new StringBuilder();

      for (Item var3 : var0) {
         if (var1.length() > 0) {
            var1.append(", ");
         }

         var1.append(var3.getName().getString());
         if (var1.length() > 80) {
            var1.append("…");
            break;
         }
      }

      return var1.toString();
   }

   /** vật liệu công trình trong túi mà tầng var1 không dùng; var2 = chỉ lấy món cả tầng cao nhất cũng không dùng */
   private Set<Item> leftovers(int var1, boolean var2) {
      Set<Item> var3 = this.layerItems(var1);
      Set<Item> var4 = var2 && this.stashTopLayer != Integer.MIN_VALUE && this.stashTopLayer != var1 ? this.layerItems(this.stashTopLayer) : Set.of();
      Set<Item> var5 = this.planItems();
      LinkedHashSet<Item> var6 = new LinkedHashSet<>();

      for (int var7 = 0; var7 < 36; var7++) {
         ItemStack var8 = this.mc.player.getInventory().getStack(var7);
         Item var9 = var8.getItem();
         if (!var8.isEmpty() && var5.contains(var9) && !var3.contains(var9) && !var4.contains(var9) && !this.keepAlways(var8)) {
            var6.add(var9);
         }
      }

      return var6;
   }

   /** danh sách thiếu của tầng: vật phẩm → số còn thiếu (đã trừ đồ đang có, tính cả vật phẩm cùng họ) */
   private LinkedHashMap<Item, Integer> layerDeficits(int var1) {
      LinkedHashMap<Item, Integer> var2 = new LinkedHashMap<>();
      if (this.plan != null && this.mc.world != null) {
         for (SchematicPlan.Target var4 : this.plan.targets) {
            BlockPos var5 = var4.pos();
            if (var5.getY() == var1 && !this.skipped.contains(var5) && this.mc.world.isChunkLoaded(var5)) {
               BlockState var6 = this.mc.world.getBlockState(var5);
               if (!this.done(var6, var4.state())
                  && !StateMatch.configurable(var6, var4.state())
                  && StateMatch.canAdd(var6, var4.state())
                  && !Materials.bucket(var6, var4.state())
                  && !Materials.flowing(var4.state())
                  && !var4.state().isReplaceable()) {
                  Item var7 = Materials.item(var6, var4.state());
                  if (var7 != Items.AIR && var7 != Items.BUCKET) {
                     var2.merge(var7, 1, Integer::sum);
                  }
               }
            }
         }

         var2.entrySet().removeIf(var1x -> this.groupHave(var1x.getKey()) >= var1x.getValue());
         var2.replaceAll((var1x, var2x) -> var2x - this.groupHave(var1x));
         Item var8 = this.scaffoldItem();
         if (this.config.scaffold && var8 != Items.AIR && InventoryAccess.count(this.mc, var8) < 16) {
            var2.merge(var8, var8.getMaxCount(), Math::max);
         }
      }

      return var2;
   }

   private Set<Item> groupOf(Item var1) {
      HashSet<Item> var2 = new HashSet<>(this.orderAlternates(var1));
      var2.add(var1);
      return var2;
   }

   private int groupHave(Item var1) {
      int var2 = 0;

      for (Item var4 : this.groupOf(var1)) {
         var2 += InventoryAccess.count(this.mc, var4);
      }

      return var2;
   }

   /** mở ender chest gần đây và nhớ trong đó có gì → bỏ khỏi danh sách những món chắc chắn không có */
   private void keepInEnder(Map<Item, Integer> var1) {
      if (this.ticks - this.enderSnapAt < 6000) {
         var1.keySet().removeIf(var1x -> {
            for (Item var3 : this.groupOf(var1x)) {
               if (this.enderSnap.getOrDefault(var3, 0) > 0) {
                  return false;
               }
            }

            return true;
         });
      }
   }

   /** túi đầy (nhất là lúc quay về sửa tầng dưới): chỉ cất vài ô cho có chỗ trống, không cất hết */
   private boolean stashMakeRoom(String var1) {
      if (this.config.useEnderChest && this.currentLayer != null && this.plan != null && this.mc.player != null) {
         int var2 = Math.max(2, this.config.reserveFreeSlots + 3 - InventoryAccess.freeSlots(this.mc));
         Set<Item> var3 = this.leftovers(this.currentLayer, true);
         if (var3.isEmpty()) {
            var3 = this.leftovers(this.currentLayer, false);
         }

         return !var3.isEmpty()
            && this.stashStart(var3, var2, new LinkedHashMap<>(), null, 0, BuildController.Stage.SELECT, var1 + " → cất tạm " + var2 + " ô (" + itemsLabel(var3) + ") vào ender chest cho có chỗ");
      } else {
         return false;
      }
   }

   /**
    * 3.0.97: thiếu 1 món → lập danh sách TẤT CẢ món tầng này còn thiếu, mở ender chest 1 lần lấy hết những gì có.
    * Món nào vẫn thiếu thì mới đi Order, Order không có mới tới Shop (bản cũ mở ender chest riêng cho từng món).
    */
   private boolean stashWithdrawFirst(Item var1, Set<Item> var2, int var3, BuildController.Stage var4) {
      if (this.config.useEnderChest && this.mc.player != null && this.ticks - this.stashTriedFor.getOrDefault(var1, -100000) >= 1200) {
         LinkedHashMap<Item, Integer> var5 = this.currentLayer != null ? this.layerDeficits(this.currentLayer) : new LinkedHashMap<>();
         var5.merge(var1, Math.max(1, var3), Math::max);
         var5.keySet().removeIf(var2x -> var2x != var1 && this.ticks - this.stashTriedFor.getOrDefault(var2x, -100000) < 1200);
         this.keepInEnder(var5);

         for (Item var7 : var5.keySet()) {
            this.stashTriedFor.put(var7, this.ticks);
         }

         this.stashTriedFor.put(var1, this.ticks);
         if (var5.isEmpty()) {
            AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest (xem {} giây trước) không có {} → đi Order/Shop luôn", (this.ticks - this.enderSnapAt) / 20, var1.getName().getString());
            return false;
         } else {
            // 3.0.123 (người dùng: "đặt đi đặt lại ender chest 2 lần"): vừa lên tầng mới mà chưa cất đồ thừa → cất luôn trong lần mở này,
            // không mở lần 2 ngay sau đó (stashDepositStep)
            Set<Item> var8 = Set.of();
            if (this.currentLayer != null && this.plan != null && !this.cleanupMode) {
               if (this.stashTopPlan != this.plan) {
                  this.stashTopPlan = this.plan;
                  this.stashTopLayer = Integer.MIN_VALUE;
               }

               if (this.currentLayer > this.stashTopLayer) {
                  var8 = this.leftovers(this.currentLayer, false);
               }
            }

            boolean var9 = this.stashStart(
               var8,
               var8.isEmpty() ? 0 : Integer.MAX_VALUE,
               var5,
               var1,
               Math.max(1, var3),
               var4,
               "Thiếu " + var1.getName().getString() + " → lấy 1 lần từ ender chest cả danh sách thiếu: " + itemsLabel(var5.keySet())
                  + (var8.isEmpty() ? "" : "; cùng lần đó cất " + itemsLabel(var8) + " (tầng này không dùng)")
            );
            if (var9 && this.currentLayer != null && this.currentLayer > this.stashTopLayer) {
               this.stashTopLayer = this.currentLayer;
            }

            return var9;
         }
      } else {
         return false;
      }
   }

   private boolean stashStart(Set<Item> var1, int var2, Map<Item, Integer> var3, Item var4, int var5, BuildController.Stage var6, String var7) {
      if (!this.config.useEnderChest || this.mc.player == null || this.mc.world == null || this.ticks < this.stashFailUntil) {
         return false;
      } else if (InventoryAccess.count(this.mc, enderChestItem()) <= 0) {
         if (this.ticks - this.stashFailUntil > 6000) {
            this.stashFailUntil = this.ticks + 6000;
            AutoBuildMod.LOGGER.info("[AutoBuild] Trong túi không có Ender Chest → không dùng kho ender chest (mang theo ít nhất 1 cái)");
         }

         return false;
      } else if (!this.mc.player.isOnGround() || (this.mc.player.isSubmergedInWater() || !this.mc.world.getBlockState(this.mc.player.getBlockPos()).getFluidState().isEmpty()) || this.inSnowNow() || this.mc.currentScreen != null) {
         return false;
      } else {
         this.release();
         this.stashDepositItems = var1;
         this.stashDepositLimit = var2;
         this.stashDeposited = 0;
         this.stashDeposit = !var1.isEmpty();
         this.stashWantMap.clear();
         this.stashWantMap.putAll(var3);
         this.stashGroups.clear();
         this.stashCountBefore.clear();

         for (Item var9 : var3.keySet()) {
            Set<Item> var10 = this.groupOf(var9);
            this.stashGroups.put(var9, var10);
            this.stashCountBefore.put(var9, this.groupHave(var9));
         }

         this.stashWant = var4;
         this.stashNeed = var5;
         this.stashItems = var4 == null ? Set.of() : this.groupOf(var4);
         this.stashAfter = var6;
         this.stashBefore = this.stashHave();
         this.stashStep = ST_FIND;
         this.stashTicks = 0;
         this.stashBadSpots.clear();
         this.stashSpotRetries = 0;
         this.stashStepTicks = 0;
         this.stashPos = null;
         this.stashPlaced = false;
         this.stashTries = 0;
         this.stashBreakTries = 0;
         this.stashClickAt = -1000;
         this.stashIdleAt = -1;
         this.stashMoved.clear();
         this.stashReason = var7;
         AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest: {}", var7);
         this.setStage(BuildController.Stage.STASH);
         return true;
      }
   }

   private int stashHave() {
      int var1 = 0;

      for (Item var3 : this.stashItems) {
         var1 += InventoryAccess.count(this.mc, var3);
      }

      return var1;
   }

   /**
    * Chỗ đặt ender chest. 3.0.96: bản 3.0.95 đòi ô ngoài công trình + không sát khối redstone nào trong vùng 3×4×3 → đứng trên
    * farm redstone thì KHÔNG BAO GIỜ có chỗ (log 19:50–19:55: báo "cất đồ vào ender chest" rồi "không có ô trống" 5 lần liền).
    * Giờ: ô trống ở ngang chân hoặc ngang đầu, trong vòng 3 ô, dưới là khối đặc, trên trống để mở được; được dùng tạm ô
    * công trình CHƯA xây (đập đi ngay sau vài giây). Chỉ tránh những gì thật sự bị kích hoạt khi đặt/đập: observer đang nhìn
    * vào ô đó, và piston/dispenser/dropper/crafter nằm sát ô đó (bị cập nhật khối).
    */
   private final Set<BlockPos> stashBadSpots = new HashSet<>();
   private int stashSpotRetries;

   private BlockPos stashSpot() {
      BlockPos var1 = BlockPos.ofFloored(this.mc.player.getX(), this.mc.player.getY() + 0.1, this.mc.player.getZ());
      // 3.0.125: đặt ender chest lúc NGỒI → kiểm tra tia từ mắt lúc ngồi (thấp hơn ~0,35). Bản cũ kiểm tra từ mắt lúc đứng → ngồi xuống
      // thì tia trúng cạnh khối khác, "không ngắm được mặt trên khối" (log 20:46:35)
      Vec3d var2 = PlacementSolver.poseEye(PlacementSolver.standingEye(this.mc, this.mc.player.getEyePos()), true);
      Box var3 = this.mc.player.getBoundingBox().contract(-0.05);
      double var4 = (this.reach() - 0.3) * (this.reach() - 0.3);
      BlockPos var6 = null;
      double var7 = Double.MAX_VALUE;

      for (int var9 = 0; var9 <= 1; var9++) {
         for (int var10 = -3; var10 <= 3; var10++) {
            for (int var11 = -3; var11 <= 3; var11++) {
               BlockPos var12 = var1.add(var10, var9, var11);
               if ((var10 != 0 || var11 != 0)
                  && this.mc.world.isChunkLoaded(var12)
                  && this.mc.world.getBlockState(var12).isAir()
                  && this.mc.world.getBlockState(var12.up()).isAir()
                  && this.solidAt(var12.down())
                  && !this.mc.world.getBlockState(var12.down()).isOf(Blocks.POWDER_SNOW)
                  && (this.journal == null || !this.journal.placed.containsKey(var12))
                  && !var3.intersects(new Box(var12))
                  && !(var2.squaredDistanceTo(Vec3d.ofCenter(var12)) > var4)
                  && !this.stashTriggers(var12)
                  && !this.stashBadSpots.contains(var12)
                  && this.rayHits(var2, new Vec3d((double)var12.getX() + 0.5, (double)var12.getY() - 0.01, (double)var12.getZ() + 0.5), var12.down())) {
                  SchematicPlan.Target var13 = this.plan == null ? null : this.plan.at(var12);
                  // gần nhất trước (ô sát bên thì đập xong tự nhặt lại được); ưu tiên ngang chân, ô ngoài công trình
                  double var14 = (double)Math.max(Math.abs(var10), Math.abs(var11)) * 2.0
                     + (Math.abs(var10) + Math.abs(var11)) * 0.1
                     + var9 * 0.5
                     + (var13 != null && !var13.state().isAir() ? 0.3 : 0.0);
                  if (var14 < var7) {
                     var7 = var14;
                     var6 = var12;
                  }
               }
            }
         }
      }

      return var6;
   }

   /** đặt/đập khối ở ô này có kích hoạt farm không: observer nhìn vào ô, hoặc piston/dispenser/dropper/crafter nằm sát ô */
   private boolean stashTriggers(BlockPos var1) {
      for (Direction var5 : Direction.values()) {
         BlockPos var6 = var1.offset(var5);
         BlockState var7 = this.mc.world.getBlockState(var6);
         String var8 = Registries.BLOCK.getId(var7.getBlock()).getPath();
         if (var8.equals("observer") && var7.contains(Properties.FACING) && var7.get(Properties.FACING) == var5.getOpposite()) {
            return true;
         }

         if (var8.endsWith("piston") || var8.equals("dispenser") || var8.equals("dropper") || var8.equals("crafter")) {
            return true;
         }
      }

      return false;
   }

   /** cúp có Silk Touch (đập ender chest rơi lại ender chest; cúp thường chỉ rơi 8 obsidian) */
   private Item silkPick() {
      for (int var1 = 0; var1 < 36; var1++) {
         ItemStack var2 = this.mc.player.getInventory().getStack(var1);
         if (!var2.isEmpty()
            && Registries.ITEM.getId(var2.getItem()).getPath().endsWith("_pickaxe")
            && String.valueOf(var2.getEnchantments()).contains("silk_touch")
            && var2.getMaxDamage() - var2.getDamage() >= 3) {
            return var2.getItem();
         }
      }

      return null;
   }

   private void stashNext(int var1) {
      this.stashStep = var1;
      this.stashStepTicks = 0;
   }

   private void stashFail(String var1, int var2) {
      AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest: {} → bỏ qua, {} giây sau mới dùng lại", var1, var2 / 20);
      this.stashFailUntil = this.ticks + var2;
      if (this.stashPlaced && this.stashPos != null && this.mc.world.getBlockState(this.stashPos).getBlock().asItem() == enderChestItem()) {
         // đã đặt ra rồi thì luôn đập thu lại
         if (this.mc.currentScreen != null) {
            this.mc.player.closeHandledScreen();
         }

         this.stashNext(ST_BREAK);
      } else {
         this.stashFinish();
      }
   }

   private void readEnderSnap() {
      this.enderSnap.clear();
      int var1 = this.mc.player.currentScreenHandler.slots.size() - 36;

      for (int var2 = 0; var2 < var1; var2++) {
         ItemStack var3 = this.mc.player.currentScreenHandler.getSlot(var2).getStack();
         if (!var3.isEmpty()) {
            this.enderSnap.merge(var3.getItem(), var3.getCount(), Integer::sum);
         }
      }

      this.enderSnapAt = this.ticks;
   }

   private void stashTick() {
      this.stashTicks++;
      this.stashStepTicks++;
      if (this.stashPos != null
         && this.stashStep >= ST_OPEN
         && this.stashStep < ST_CONFIRM
         && this.mc.world.getBlockState(this.stashPos).getBlock().asItem() != enderChestItem()) {
         // 3.0.105: (a) ST_CONFIRM là bước SAU khi đã đập → chest mất là đúng, trước đây báo nhầm "biến mất" (log 01:11:03);
         // (b) vừa đặt đã mất (server trả về, log 01:04:43 cùng giây) → đặt lại tại chỗ tối đa 2 lần thay vì bỏ 20 giây
         if (this.stashStep <= ST_WAIT_OPEN && ++this.stashTries <= 2) {
            AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest @ {} bị server trả về ngay sau khi đặt → đặt lại (lần {})", this.stashPos.toShortString(), this.stashTries);
            if (this.mc.currentScreen != null) {
               this.mc.player.closeHandledScreen();
            }

            this.stashPlaced = false;
            this.stashNext(ST_PLACE);
         } else {
            this.stashFail("ender chest @ " + this.stashPos.toShortString() + " biến mất", 400);
         }
      } else if (this.stashTicks > 900 && this.stashStep < ST_BREAK) {
         this.stashFail("45 giây chưa xong", 1200);
      } else {
         switch (this.stashStep) {
            case ST_FIND: {
               BlockPos var1 = this.stashSpot();
               if (var1 == null) {
                  if (this.ticks - this.stashNoSpotSaidAt > 6000) {
                     this.stashNoSpotSaidAt = this.ticks;
                     this.say("Quanh chỗ đứng không có ô an toàn để đặt ender chest (cần ô trống trên khối đặc, không sát observer/piston) → bỏ qua lần này.");
                  }

                  this.stashFail("không có ô an toàn để đặt ender chest quanh " + BlockPos.ofFloored(this.mc.player.getPos()).toShortString(), 600);
               } else {
                  this.stashPos = var1;
                  AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest: đặt tại {}", var1.toShortString());
                  this.say(this.stashReason + " (đặt ender chest @ " + var1.toShortString() + ").");
                  this.stashNext(ST_PLACE);
               }
               break;
            }
            case ST_PLACE: {
               BlockState var2 = this.mc.world.getBlockState(this.stashPos);
               if (var2.getBlock().asItem() == enderChestItem()) {
                  this.stashPlaced = true;
                  this.stashNext(ST_OPEN);
               } else if (!var2.isAir()) {
                  this.stashFail("ô đặt ender chest bị chiếm", 200);
               } else if (!this.mc.player.getMainHandStack().isOf(enderChestItem())) {
                  if (this.unacked == 0 && InventoryAccess.synced(this.mc)) {
                     InventoryAccess.equip(this.mc, enderChestItem(), this.config.workingHotbarSlot);
                  }

                  if (this.stashStepTicks > 60) {
                     this.stashFail("không cầm được ender chest lên tay", 600);
                  }
               } else {
                  // ngồi xuống rồi bấm chuột phải vào mặt trên khối bên dưới (ngồi để không mở nhầm khối có menu)
                  Input.sneak(this.mc, true, this.ticks);
                  BlockPos var3 = this.stashPos.down();
                  Vec3d var4 = new Vec3d((double)this.stashPos.getX() + 0.5, (double)this.stashPos.getY(), (double)this.stashPos.getZ() + 0.5).subtract(this.mc.player.getEyePos());
                  this.lookAt(MotionMath.yaw(var4.x, var4.z), MotionMath.pitch(var4.x, var4.y, var4.z));
                  BlockHitResult var5 = PlacementSolver.ray(this.mc, this.reach());
                  if (this.mc.player.isSneaking()
                     && this.rotationSent()
                     && this.unacked == 0
                     && InventoryAccess.synced(this.mc)
                     && this.stashStepTicks - this.stashClickAt > 10
                     && var5.getType() == Type.BLOCK
                     && var5.getBlockPos().equals(var3)
                     && var5.getSide() == Direction.UP) {
                     this.stashClickAt = this.stashStepTicks;
                     this.clickTick = this.ticks;
                     if (this.tntVeto(var5)) {
                        this.stashFail("gần TNT — không đặt ender chest ở đây", 1200);
                        return;
                     }

                     BlockHitResult clickvar5 = var5;
                     Input.queueUse(this.mc, clickvar5, false, "ender chest", () -> {
                        this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, clickvar5);
                        this.mc.player.swingHand(Hand.MAIN_HAND);
                     });
                     this.stashNext(ST_WAIT_PLACED);
                  } else if (this.stashStepTicks > 80 && this.stashSpotRetries < 3) {
                     // 3.0.125: thử ngay ô khác quanh chỗ đứng, không bỏ qua 30 giây
                     this.stashSpotRetries++;
                     this.stashBadSpots.add(this.stashPos.toImmutable());
                     AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest: không ngắm được mặt trên khối dưới {} (tâm ngắm {}) → thử ô khác", this.stashPos.toShortString(),
                        var5.getType() == Type.BLOCK ? var5.getBlockPos().toShortString() + " " + var5.getSide() : "không trúng khối");
                     this.stashPos = null;
                     this.stashNext(ST_FIND);
                  } else if (this.stashStepTicks > 80) {
                     this.stashFail("không ngắm được mặt trên khối để đặt ender chest", 600);
                  }
               }
               break;
            }
            case ST_WAIT_PLACED:
               Input.sneak(this.mc, true, this.ticks);
               if (this.mc.world.getBlockState(this.stashPos).getBlock().asItem() == enderChestItem()
                  && !this.ackWait(this.stashPos)
                  && this.stashStepTicks >= 3 + this.latencyTicks()) {
                  // 3.0.105: chờ server xác nhận khối đặt (client thấy trước khi server nhận → mở ngay thì hay "biến mất")
                  this.stashPlaced = true;
                  this.stashNext(ST_OPEN);
               } else if (this.stashStepTicks > 30 + this.latencyTicks()) {
                  if (++this.stashTries > 2) {
                     this.stashFail("bấm đặt ender chest 3 lần không được", 600);
                  } else {
                     this.stashNext(ST_PLACE);
                  }
               }
               break;
            case ST_OPEN: {
               // đứng thẳng (không ngồi) rồi bấm chuột phải vào ender chest để mở
               Input.sneak(this.mc, false, this.ticks);
               if (!this.mc.player.isSneaking()) {
                  Vec3d var6 = this.visiblePoint(this.stashPos);
                  if (var6 == null) {
                     this.stashFail("không nhìn thấy ender chest", 400);
                     return;
                  }

                  Vec3d var7 = var6.subtract(this.mc.player.getEyePos());
                  this.lookAt(MotionMath.yaw(var7.x, var7.z), MotionMath.pitch(var7.x, var7.y, var7.z));
                  BlockHitResult var8 = PlacementSolver.ray(this.mc, this.reach());
                  if (this.rotationSent() && var8.getType() == Type.BLOCK && var8.getBlockPos().equals(this.stashPos)) {
                     this.clickTick = this.ticks;
                     if (this.tntVeto(var8)) {
                        this.stashFail("gần TNT — không mở ender chest ở đây", 1200);
                        return;
                     }

                     BlockHitResult clickvar8 = var8;
                     Input.queueUse(this.mc, clickvar8, false, "ender chest", () -> {
                        this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, clickvar8);
                        this.mc.player.swingHand(Hand.MAIN_HAND);
                     });
                     this.stashNext(ST_WAIT_OPEN);
                  }
               }

               if (this.stashStepTicks > 60) {
                  this.stashFail("không mở được ender chest", 600);
               }
               break;
            }
            case ST_WAIT_OPEN:
               if (this.mc.currentScreen instanceof HandledScreen && this.mc.player.currentScreenHandler != this.mc.player.playerScreenHandler) {
                  if (this.stashStepTicks >= 3 + this.latencyTicks() && this.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                     this.readEnderSnap();
                     AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest: đã mở, trong có {} loại đồ", this.enderSnap.size());
                     this.stashIdleAt = -1;
                     this.stashNextMove = 0;
                     this.stashNext(ST_TRANSFER);
                  }
               } else if (this.stashStepTicks > 40 + this.latencyTicks()) {
                  if (++this.stashTries > 3) {
                     this.stashFail("bấm mở ender chest nhiều lần không có menu", 600);
                  } else {
                     this.stashNext(ST_OPEN);
                  }
               }
               break;
            case ST_TRANSFER: {
               if (!(this.mc.currentScreen instanceof HandledScreen) || this.mc.player.currentScreenHandler == this.mc.player.playerScreenHandler) {
                  this.stashNext(ST_BREAK);
                  return;
               }

               if (this.stashStepTicks < this.stashNextMove) {
                  return;
               }

               int var9 = this.mc.player.currentScreenHandler.slots.size() - 36;
               int var10 = this.mc.player.currentScreenHandler.syncId;
               // 1) cất đồ thừa trước (để có chỗ), tối đa stashDepositLimit ô
               if (this.stashDeposited < this.stashDepositLimit) {
                  for (int var11 = var9; var11 < var9 + 36; var11++) {
                     ItemStack var12 = this.mc.player.currentScreenHandler.getSlot(var11).getStack();
                     if (!var12.isEmpty() && this.stashDepositItems.contains(var12.getItem()) && this.stashMoved.add(var11)) {
                        this.mc.interactionManager.clickSlot(var10, var11, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                        this.stashDeposited++;
                        this.stashNextMove = this.stashStepTicks + 2;
                        this.status = "Cất " + var12.getName().getString() + " vào ender chest";
                        return;
                     }
                  }
               }

               // 2) lấy cả danh sách thiếu: đúng vật phẩm trước, rồi vật phẩm cùng họ; mỗi món lấy tới khi đủ số thiếu
               if (!this.stashWantMap.isEmpty() && InventoryAccess.freeSlots(this.mc) > this.config.reserveFreeSlots) {
                  for (int var13 = 0; var13 < 2; var13++) {
                     for (int var14 = 0; var14 < var9; var14++) {
                        ItemStack var15x = this.mc.player.currentScreenHandler.getSlot(var14).getStack();
                        if (!var15x.isEmpty() && !this.stashMoved.contains(var14)) {
                           for (Entry<Item, Integer> var16x : this.stashWantMap.entrySet()) {
                              Item var17x = var16x.getKey();
                              boolean var18x = var13 == 0 ? var15x.getItem() == var17x : this.stashGroups.getOrDefault(var17x, Set.of()).contains(var15x.getItem());
                              if (var18x && var16x.getValue() > 0 && this.groupHave(var17x) - this.stashCountBefore.getOrDefault(var17x, 0) < var16x.getValue()) {
                                 this.stashMoved.add(var14);
                                 this.mc.interactionManager.clickSlot(var10, var14, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                                 this.stashNextMove = this.stashStepTicks + 2;
                                 this.status = "Lấy " + var15x.getName().getString() + " từ ender chest";
                                 return;
                              }
                           }
                        }
                     }
                  }
               }

               // xong việc chuyển đồ → chờ server đồng bộ, ghi lại trong ender chest có gì, đóng
               if (this.stashIdleAt < 0) {
                  this.stashIdleAt = this.stashStepTicks;
               }

               if (this.stashStepTicks - this.stashIdleAt >= 4 + this.latencyTicks()) {
                  this.readEnderSnap();
                  this.mc.player.closeHandledScreen();
                  this.stashNext(ST_BREAK);
               }
               break;
            }
            case ST_BREAK: {
               if (this.mc.currentScreen != null && this.stashStepTicks > 3) {
                  this.mc.player.closeHandledScreen();
                  return;
               }

               BlockState var15 = this.mc.world.getBlockState(this.stashPos);
               if (this.stashStepTicks <= 1) {
                  this.stashDropBefore = InventoryAccess.count(this.mc, enderChestItem()) + InventoryAccess.count(this.mc, Blocks.OBSIDIAN.asItem());
               }

               if (var15.getBlock().asItem() != enderChestItem()) {
                  // 3.0.97: client thấy đã vỡ chưa chắc server đã phá (lag) → sang bước xác nhận
                  Input.key(this.mc.options.attackKey, false);
                  this.mining = null;
                  this.forceTool = null;
                  this.stashNext(ST_CONFIRM);
               } else if (this.stashStepTicks > 240) {
                  Input.key(this.mc.options.attackKey, false);
                  this.mining = null;
                  this.forceTool = null;
                  AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest @ {}: 12 giây chưa đập được → để lại, lát đứng gần sẽ đập tiếp", this.stashPos.toShortString());
                  this.stashLeft = this.stashPos;
                  this.stashFinish();
               } else {
                  Input.sneak(this.mc, false, this.ticks);
                  Item var16 = this.silkPick();
                  if (var16 == null && !this.silkWarned) {
                     this.silkWarned = true;
                     this.say("Không có cúp Silk Touch → đập ender chest sẽ chỉ rơi 8 Obsidian (mất 1 ender chest mỗi lần dùng).");
                  }

                  this.forceTool = var16;
                  this.mining = this.stashPos;
                  this.breakBlock(var15);
               }
               break;
            }
            case ST_CONFIRM: {
               // chờ server trả lời lệnh phá (hết "đang chờ xác nhận") + thêm 1 lượt ping, rồi nhìn lại ô đó
               boolean var19 = this.mc.world.getBlockState(this.stashPos).getBlock().asItem() == enderChestItem();
               if (var19 || !this.ackWait(this.stashPos) && this.stashStepTicks >= 4 + this.latencyTicks() * 2) {
                  if (var19) {
                     if (++this.stashBreakTries > 4) {
                        AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest @ {}: đập 5 lần server vẫn trả lại (lag) → để lại, lát đứng gần sẽ đập tiếp", this.stashPos.toShortString());
                        this.stashLeft = this.stashPos;
                        this.stashFinish();
                     } else {
                        AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest @ {}: client thấy vỡ nhưng server chưa phá (lag) → đập lại (lần {})", this.stashPos.toShortString(), this.stashBreakTries + 1);
                        this.stashNext(ST_BREAK);
                     }
                  } else {
                     this.stashNext(ST_PICKUP);
                  }
               }
               break;
            }
            case ST_PICKUP: {
               // ender chest bật lại (server lag trả khối về muộn) → đập lại
               if (this.mc.world.getBlockState(this.stashPos).getBlock().asItem() == enderChestItem()) {
                  AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest @ {} hiện lại sau khi đập (server lag) → đập lại", this.stashPos.toShortString());
                  this.release();
                  this.stashNext(ST_BREAK);
                  return;
               }

               // chờ nhặt ender chest/obsidian rơi ra; ô xa hơn 1 ô thì đi lại chỗ đó cho tự nhặt
               int var17 = InventoryAccess.count(this.mc, enderChestItem()) + InventoryAccess.count(this.mc, Blocks.OBSIDIAN.asItem());
               if (var17 > this.stashDropBefore) {
                  this.release();
                  this.stashFinish();
               } else if (this.stashStepTicks >= 80) {
                  this.release();
                  AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest @ {}: chưa nhặt lại được đồ rơi → xây tiếp", this.stashPos.toShortString());
                  this.stashFinish();
               } else if (this.stashStepTicks >= 12 && this.stashPos.getY() == BlockPos.ofFloored(this.mc.player.getPos().add(0.0, 0.1, 0.0)).getY()) {
                  Vec3d var18 = new Vec3d((double)this.stashPos.getX() + 0.5, (double)this.stashPos.getY(), (double)this.stashPos.getZ() + 0.5);
                  if (Math.hypot(var18.x - this.mc.player.getX(), var18.z - this.mc.player.getZ()) > 0.6) {
                     this.creepToward(var18);
                  }
               }
               break;
            }
            default:
               this.stashFinish();
         }
      }
   }

   private void stashFinish() {
      this.stashStep = -1;
      this.mining = null;
      this.forceTool = null;
      Input.key(this.mc.options.attackKey, false);
      Input.sneak(this.mc, false, this.ticks);
      if (this.mc.currentScreen != null && this.mc.player != null) {
         this.mc.player.closeHandledScreen();
      }

      // tổng kết: lấy được gì, còn thiếu gì (còn thiếu → Order, Order không có → Shop)
      StringBuilder var1 = new StringBuilder();
      StringBuilder var2 = new StringBuilder();

      for (Entry<Item, Integer> var4 : this.stashWantMap.entrySet()) {
         Item var5 = var4.getKey();
         int var6 = this.groupHave(var5) - this.stashCountBefore.getOrDefault(var5, 0);
         if (var6 > 0) {
            this.missing.remove(var5);
            this.announcedMissing.remove(var5);
            var1.append(var1.length() > 0 ? ", " : "").append(var5.getName().getString()).append(" x").append(var6);
         }

         if (var6 < var4.getValue()) {
            var2.append(var2.length() > 0 ? ", " : "").append(var5.getName().getString());
         }
      }

      if (var1.length() > 0) {
         this.say("Đã lấy từ ender chest: " + var1 + (var2.length() > 0 ? ". Còn thiếu " + var2 + " → Order/Shop." : "."));
      } else if (!this.stashWantMap.isEmpty()) {
         AutoBuildMod.LOGGER.info("[AutoBuild] Ender chest không có món nào trong danh sách thiếu ({}) → Order/Shop", var2);
      }

      this.stashWantMap.clear();
      if (this.stashWant == null) {
         this.beginScan();
      } else if (this.stashHave() - this.stashBefore > 0) {
         if (this.stashAfter == BuildController.Stage.EQUIP) {
            this.setStage(BuildController.Stage.EQUIP);
         } else {
            this.beginScan();
         }
      } else {
         // món đang cần không có trong ender chest → đi Order/Shop như bình thường (vừa xem rồi nên không mở lại ender chest)
         this.collect(this.stashWant, this.stashAfter, this.stashNeed);
      }
   }

   // ===== 3.0.88: /sethome + /home N (DonutSMP) để lên lại tầng đang xây khi lỡ ngã =====
   private int homeFailUntil;
   private int homeTpFailUntil;
   private int homeTpAt = -100000;
   private int homeLayerPending = Integer.MIN_VALUE;
   private Vec3d homeHold;

   private boolean menuBusyNow() {
      try {
         return this.menuStage()
            || this.shopping
            || this.browsing
            || this.orders.active()
            || this.homes.active()
            || this.browser.active()
            || this.shop.active()
            || this.seller.active()
            || this.survivalSupplies != null && this.survivalSupplies.active();
      } catch (Throwable var2) {
         return false;
      }
   }

   private boolean menuStage() {
      return this.stage == BuildController.Stage.ORDER
         || this.stage == BuildController.Stage.HOME
         || this.stage == BuildController.Stage.CRAFTER
         || this.stage == BuildController.Stage.FILL
         || this.stage == BuildController.Stage.STASH
         || this.stage == BuildController.Stage.SELL;
   }

   /** 3.0.92: home của mod dùng được cho tầng đang xây: đặt ở tầng này hoặc tối đa 3 tầng dưới (chỗ home được kiểm tra còn trống trước khi dùng) */
   private boolean ownHomeUsable() {
      return this.config.useHome
         && this.currentLayer != null
         && this.config.ownHomeNumber > 0
         && this.config.ownHomeLayer != Integer.MIN_VALUE
         && this.config.ownHomeLayer <= this.currentLayer
         && this.config.ownHomeLayer >= this.currentLayer - 3
         && this.mc.world != null
         && ScaffoldJournal.worldKey(this.mc).equals(this.config.ownHomeWorld);
   }

   private int homeLayerSeen = Integer.MIN_VALUE;
   private int homeLayerSince;

   private boolean ownHomeFor(int var1) {
      return this.config.ownHomeNumber > 0
         && this.config.ownHomeLayer == var1
         && this.mc.world != null
         && ScaffoldJournal.worldKey(this.mc).equals(this.config.ownHomeWorld);
   }

   /**
    * Vừa đứng lên mặt tầng mới (tầng thứ ≥ homeMinLayer), trên một khối THẬT của công trình (không phải khối tạm sẽ bị đập),
    * đầu/chân trống, không ngập nước → xóa home cũ của mod (tầng dưới, chỗ đó sắp bị tầng mới lấp) rồi /sethome tại đây.
    */
   private boolean maybeSetHome() {
      if (this.config.useHome
         && this.config.autoSetHome
         && this.config.layerMode
         && this.plan != null
         && this.currentLayer != null
         && !this.cleanupMode
         && !this.shopping
         && this.ticks >= this.homeFailUntil
         && this.mc.player != null
         && this.mc.currentScreen == null) {
         int var1 = this.currentLayer;
         if (var1 != this.homeLayerSeen) {
            this.homeLayerSeen = var1;
            this.homeLayerSince = this.ticks;
         }

         if (this.layerIndex(var1) < this.config.homeMinLayer || this.ownHomeFor(var1)) {
            return false;
         } else if (this.temporary != null || this.supportTarget != null || !this.supportQueue.isEmpty() || this.pending != null) {
            return false;
         } else if (!this.mc.player.isOnGround() || this.mc.player.getBlockY() != var1 + 1) {
            return false;
         } else {
            BlockPos var2 = this.mc.player.getBlockPos();
            BlockPos var3 = var2.down();
            SchematicPlan.Target var4 = this.plan.at(var3);
            if (var4 == null
               || this.journal == null
               || this.journal.placed.containsKey(var3)
               || !this.blocksBody(var3)
               || !this.done(this.mc.world.getBlockState(var3), var4.state())) {
               return false;
            } else if (this.blocksBody(var2)
               || this.blocksBody(var2.up())
               || Navigation.waterish(this.mc.world.getBlockState(var2))
               || Navigation.waterish(this.mc.world.getBlockState(var2.up()))) {
               return false;
            } else if ((this.planSolid(var2) || this.planSolid(var2.up())) && this.ticks - this.homeLayerSince < 1200) {
               // 3.0.92: ưu tiên chỗ mà bản vẽ để trống ở chân + đầu (tầng trên không lấp) → home dùng được cho cả các tầng sau,
               // lỡ /sethome ở tầng mới không được thì vẫn /home về gần được. 1 phút không có chỗ như vậy mới đặt chỗ thường.
               return false;
            } else {
               String var5 = ScaffoldJournal.worldKey(this.mc);
               // 3.0.99: home người dùng chỉ định (tab Home) không bao giờ bị mod xóa
               // 3.0.123: home N người dùng giao cho mod (tab Cài đặt) → luôn xoá home N cũ (nếu có) rồi /sethome N
               int var6 = this.config.modHomeNumber;
               this.release();
               this.homeLayerPending = var1;
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] Lên tầng Y={} (tầng thứ {}) → {}/sethome tại {} (đứng trên {}) để lỡ ngã thì /home về ngay",
                     new Object[]{
                        var1,
                        this.layerIndex(var1),
                        var6 > 0 ? "xóa home " + var6 + " cũ của mod rồi " : "",
                        var2.toShortString(),
                        var4.state().getBlock().getName().getString()
                     }
                  );
               this.homes.startSet(this.mc, var6, this.config.modHomeNumber);
               this.setStage(BuildController.Stage.HOME);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   /**
    * Đang ở thấp hơn mặt tầng đang xây ≥ 2 ô (lỡ ngã xuống hố/bể/tầng dưới) mà có home của mod ở tầng này → /home N lên ngay,
    * không tìm đường / bắc cầu / kê cột. Home bị lấp hoặc mất sàn thì bỏ và đặt lại sau.
    */
   /** 3.0.93: vào lại server mà đứng xa chỗ home của mod (> 48 ô) → /home về luôn (không cần đang ở thấp hơn) */
   public void afterReconnect() {
      if (this.config.useHome
         && this.mc.player != null
         && this.plan != null
         && this.plan.loaded
         && this.stage != BuildController.Stage.LOAD
         && this.config.ownHomeNumber > 0
         && this.mc.world != null
         && ScaffoldJournal.worldKey(this.mc).equals(this.config.ownHomeWorld)
         && (
            this.mc.player.getPos().squaredDistanceTo(new Vec3d(this.config.ownHomeX, this.config.ownHomeY, this.config.ownHomeZ)) > 2304.0
               || this.currentLayer != null && this.mc.player.getY() < (double)this.currentLayer - 1.05
         )) {
         // 3.0.105: vào lại mà đang ở THẤP hơn mặt tầng đang xây (log 02:04: xuất hiện ở Y=-53 trong hốc đá dưới tầng -50, kẹt 5 phút)
         // → /home về luôn, không tìm đường trong vùng đá
         this.release();
         this.homeHold = this.mc.player.getPos();
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] Vào lại server ở {} → /{} {} về chỗ đang xây",
               new Object[]{this.mc.player.getY() < (double)(this.currentLayer == null ? -10000 : this.currentLayer) - 1.05 ? "dưới mặt tầng đang xây" : "xa công trình", this.config.homeCommand, this.config.ownHomeNumber}
            );
         this.homes.startTeleport(this.mc, this.config.ownHomeNumber, new Vec3d(this.config.ownHomeX, this.config.ownHomeY, this.config.ownHomeZ));
         this.setStage(BuildController.Stage.HOME);
      }
   }

   private final Map<BlockPos, Integer> homeUpFor = new HashMap<>();
   private BlockPos lastGoalPos;
   private int lastGoalAt = -100000;
   private int homeUpPlaced = -1;
   private int homeUpStreak;

   /**
    * 3.0.99: chống vòng lặp /home. Lỗi 15:59 và 16:51: khối "đặt được ngay" ở xa không đi tới được trên mặt tầng →
    * mod đi đường vòng xuống thấp → "thấp hơn mặt tầng" → /home 2 → chọn lại đúng khối đó → lặp mãi, không xây gì.
    * Giờ: cùng một khối mà đã phải /home về 2 lần (chưa đặt thêm khối nào) → để khối đó 2 phút, làm khối/cụm khác trước.
    */
   private void noteHomeUp() {
      if (this.placedCount != this.homeUpPlaced) {
         this.homeUpPlaced = this.placedCount;
         this.homeUpStreak = 0;
         this.homeUpFor.clear();
      }

      this.homeUpStreak++;
      BlockPos var3 = this.target != null ? this.target.pos() : (this.ticks - this.lastGoalAt < 1200 ? this.lastGoalPos : null);
      SchematicPlan.Target var1 = var3 != null && this.plan != null ? this.plan.at(var3) : null;
      if (var1 != null) {
         int var2 = this.homeUpFor.merge(var1.pos().toImmutable(), 1, Integer::sum);
         if (var2 >= 2) {
            this.homeUpFor.remove(var1.pos());
            this.queue.remove(var1);
            this.defer(var1, 2400, "đi tới đó lần nào cũng rơi xuống thấp rồi phải /home về");
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] {} @ {}: đã 2 lần đi tới là rơi xuống thấp rồi /home về (chưa đặt thêm được khối nào) → để 2 phút, làm khối/cụm khác trước",
                  var1.state().getBlock().getName().getString(),
                  var1.pos().toShortString()
               );
            this.say(var1.state().getBlock().getName().getString() + " @ " + var1.pos().toShortString() + " chưa tới được từ trên cao → làm phần khác trước, lát quay lại.");
         }
      }

      if (this.homeUpStreak >= 6) {
         // nhiều lần /home liên tiếp không đặt được gì (mỗi lần một khối khác) → nghỉ /home 1 phút, tự tìm đường/bắc cầu lên
         this.homeUpStreak = 0;
         this.homeTpFailUntil = this.ticks + 1200;
         AutoBuildMod.LOGGER.info("[AutoBuild] 6 lần /home liên tiếp mà chưa đặt được khối nào → 1 phút tới tự tìm đường / bắc cầu lên thay vì /home");
      }
   }

   private int homeWaitSaidAt = -1000;

   /** vừa click đặt khối mà server chưa xác nhận xong (đang chờ khối hiện / chờ kiểm tra) */
   private boolean placeUnconfirmed() {
      return this.pending != null || !this.verify.isEmpty() || this.ticks - this.clickTick < 40 + this.latencyTicks() * 3;
   }

   private boolean tryHomeUp(String var1) {
      // 3.0.101 (người dùng): vừa đặt khối mà server lag chưa xác nhận → KHÔNG /home ngay. Lỗi 21:09:30: đặt Obsidian @ (x, -33, z)
      // từ chân Y=-34, khối chưa hiện (lag) → chọn khối khác → "Cần lên lại tầng" → /home 2 → ngay sau đó mới biết khối kia mất,
      // phải đi lại cả quãng xa. Giờ chờ xác nhận xong (mất thì đặt lại ngay từ chỗ đang đứng) rồi mới tính /home.
      if (!var1.startsWith("Bị ngã")
         && this.mc.player != null
         && this.placeUnconfirmed()) {
         if (this.ticks - this.homeWaitSaidAt > 100 && this.config.useHome && this.ownHomeUsable()) {
            this.homeWaitSaidAt = this.ticks;
            AutoBuildMod.LOGGER.info("[AutoBuild] {}: vừa đặt khối, chờ server xác nhận (lag) trước khi /home — mất thì đặt lại ngay tại chỗ", var1);
         }

         return false;
      }

      if (this.config.useHome
         && this.currentLayer != null
         && this.plan != null
         && !this.cleanupMode
         && !this.shopping
         && this.ticks >= this.homeTpFailUntil
         && this.ticks - this.homeTpAt >= 100
         && this.mc.player != null
         && this.ownHomeUsable()) {
         // home của tầng này hoặc vài tầng dưới (tầng mới chưa kịp đặt home) vẫn dùng được nếu chỗ đó chưa bị lấp — kiểm tra ngay dưới
         double var2 = this.mc.player.getY();
         if (var2 > this.config.ownHomeY - 1.9) {
            return false;
         } else if (!this.mc.player.isOnGround() && !Navigation.waterish(this.mc.world.getBlockState(this.mc.player.getBlockPos()))) {
            return false;
         } else {
            BlockPos var4 = BlockPos.ofFloored(this.config.ownHomeX, this.config.ownHomeY + 0.1, this.config.ownHomeZ);
            if (this.mc.world.isChunkLoaded(var4) && (this.blocksBody(var4) || this.blocksBody(var4.up()) || !this.blocksBody(var4.down()))) {
               AutoBuildMod.LOGGER.info("[AutoBuild] Home {} tại {} không còn an toàn (bị lấp / mất sàn) → không dùng, lát đặt lại", this.config.ownHomeNumber, var4.toShortString());
               this.config.ownHomeLayer = Integer.MIN_VALUE;
               this.config.saveQuietly();
               return false;
            } else {
               this.noteHomeUp();
               this.release();
               this.escUntil = 0;
               this.wsPos = null;
               this.homeHold = this.mc.player.getPos();
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] {}: đang ở Y={} thấp hơn mặt tầng Y={} → /{} {} về ngay, không tìm đường/bắc cầu",
                     new Object[]{var1, String.format("%.1f", var2), this.currentLayer + 1, this.config.homeCommand, this.config.ownHomeNumber}
                  );
               this.homes.startTeleport(this.mc, this.config.ownHomeNumber, new Vec3d(this.config.ownHomeX, this.config.ownHomeY, this.config.ownHomeZ));
               this.setStage(BuildController.Stage.HOME);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private void homeTick() {
      if (this.flowAt() == null && this.stillGate()) {
         return;
      }

      if (this.homes.teleporting() && this.homeHold != null && this.flowAt() != null) {
         // chờ dịch chuyển mà bị nước chảy đẩy đi → server hủy dịch chuyển; bấm phím bù dòng để đứng yên
         this.holdAt(this.homeHold.x, this.homeHold.z);
      }

      this.homes.tick(this.mc);
      this.status = this.homes.status();
      if (!this.homes.active()) {
         boolean var1 = this.homes.result() == com.donutautobuild.order.HomeManager.Result.DONE;
         if (this.homes.teleporting()) {
            if (var1) {
               this.homeTpAt = this.ticks;
               this.rbLastPos = null;
               this.rbLastVel = null;
               this.rbHn = 0;
               this.path = List.of();
               this.lastPosition = this.mc.player.getPos();
               this.stuckTicks = 0;
               this.lastProgress = this.ticks;
               this.forgiveStuckMarks();
               // 3.0.100: vừa dịch chuyển → quên "khoảng cách gần nhất từng tới" của các khối. Lỗi 20:21–20:28: lần trước đã tới
               // cách khối 3 ô, lần này đi từ home tới gần dần mà chưa gần hơn 3 ô → watchdog "10 giây không tiến triển" → /home → lặp.
               this.navBestDist.clear();
               this.say("Đã /" + this.config.homeCommand + " " + this.config.ownHomeNumber + " về tầng Y=" + this.currentLayer + ", xây tiếp.");
            } else if (this.homes.teleportLag() && !Reconnector.active()) {
               // 3.0.95: dịch chuyển về home không được do server lag → thoát ra vào lại; vào xong afterReconnect() tự /home lại
               this.say("Không dịch chuyển về home được (server lag) → thoát ra vào lại.");
               Reconnector.request(this.mc, this, "/home " + this.config.ownHomeNumber + " không dịch chuyển (server lag)");
               return;
            } else if (this.homes.homeMissing()) {
               // 3.0.125 (người dùng): home của mod không còn → quên home cũ, tự tìm đường lên; tới chỗ an toàn ở tầng đang xây
               // thì /sethome lại đúng số home đã chọn (maybeSetHome)
               int var2 = this.config.ownHomeNumber;
               this.config.ownHomeNumber = 0;
               this.config.ownHomeManual = false;
               this.config.ownHomeLayer = Integer.MIN_VALUE;
               this.config.saveQuietly();
               this.homeFailUntil = 0;
               this.say("Home " + var2 + " không còn trên server → tự tìm đường lên, tới tầng đang xây sẽ /sethome " + Math.max(var2, this.config.modHomeNumber) + " lại.");
            } else {
               this.homeTpFailUntil = this.ticks + 1200;
               AutoBuildMod.LOGGER.info("[AutoBuild] Không /home được ({}) → 1 phút tới tự tìm đường lên như cũ", this.homes.status());
            }
         } else {
            if (this.homes.deletedOld()) {
               this.config.ownHomeNumber = 0;
            }

            if (var1 && this.homes.newNumber() > 0 && this.homes.setPos() != null) {
               Vec3d var2 = this.homes.setPos();
               this.config.ownHomeManual = false;
               this.config.ownHomeNumber = this.homes.newNumber();
               this.config.ownHomeX = var2.x;
               this.config.ownHomeY = var2.y;
               this.config.ownHomeZ = var2.z;
               this.config.ownHomeLayer = this.homeLayerPending;
               this.config.ownHomeWorld = ScaffoldJournal.worldKey(this.mc);
               this.say("Đã đặt home " + this.config.ownHomeNumber + " ở tầng Y=" + this.homeLayerPending + " → lỡ ngã sẽ /home " + this.config.ownHomeNumber + " lên ngay.");
            } else {
               int var3 = this.homes.noFreeSlot() ? 12000 : (this.homes.menuFailed() ? 600 : 2400);
               this.homeFailUntil = this.ticks + var3;
               AutoBuildMod.LOGGER.info("[AutoBuild] Chưa đặt được home ({}) → xây tiếp, {} giây sau thử lại", this.homes.status(), var3 / 20);
            }

            this.config.saveQuietly();
         }

         this.target = null;
         this.aim = null;
         this.beginScan();
      }
   }

   private int matRestockAt = -1000;
   private final HashMap<Item, Integer> matOrderAt = new HashMap<>();

   /**
    * 3.0.86: hết một vật liệu của tầng đang xây (vd. Obsidian) → mở Order lấy ngay. Bản cũ: ô thiếu vật liệu bị lặng lẽ loại
    * khỏi hàng đợi (`stillNeeded()`), tầng vẫn còn khối khác đặt được (lá) nên không đi lấy; lá mất chỗ bám là obsidian nên
    * phải bắc cầu khối tạm 1–3 khối cho từng lá (log 14:00:24–14:03:03: hết obsidian, 2,5 phút chỉ bắc cầu cho lá).
    */
   private boolean materialRestock() {
      if (this.config.collectFromOrders
         && !this.cleanupMode
         && this.currentLayer != null
         && this.plan != null
         && this.mc.player != null
         && this.temporary == null
         && this.supportTarget == null
         && this.supportQueue.isEmpty()
         && this.ticks - this.matRestockAt >= 40) {
         this.matRestockAt = this.ticks;
         int var1 = this.currentLayer;
         Iterable<SchematicPlan.Target> var2 = this.floorLayer != null && this.floorLayer == var1 && !this.floorOrder.isEmpty() ? this.floorOrder : this.plan.targets;
         HashMap<Item, Integer> var3 = new HashMap<>();
         HashMap<Item, Integer> var16 = new HashMap<>();
         int var4 = 0;

         for (SchematicPlan.Target var6 : var2) {
            BlockPos var7 = var6.pos();
            if (var7.getY() == var1 && ++var4 <= 8000 && !this.skipped.contains(var7) && !this.placedDone.contains(var7) && this.mc.world.isChunkLoaded(var7)) {
               BlockState var8 = this.mc.world.getBlockState(var7);
               if (!this.done(var8, var6.state())
                  && !StateMatch.chestAwaitingPartner(var8, var6.state())
                  && !StateMatch.configurable(var8, var6.state())
                  && StateMatch.canAdd(var8, var6.state())
                  && !Materials.bucket(var8, var6.state())
                  && !Materials.flowing(var6.state())
                  && !var6.state().isReplaceable()) {
                  Item var9 = this.materialFor(var6, var8);
                  if (var9 != Items.AIR
                     && var9 != Items.WATER_BUCKET
                     && var9 != Items.LAVA_BUCKET
                     && var9 != Items.BUCKET
                     && var16.computeIfAbsent(var9, var1x -> InventoryAccess.count(this.mc, var1x)) <= 0) {
                     var3.merge(var9, 1, Integer::sum);
                  }
               }
            }
         }

         Item var10 = null;
         int var11 = 0;

         for (Entry<Item, Integer> var13 : var3.entrySet()) {
            Item var14 = var13.getKey();
            if (this.missing.getOrDefault(var14, 0) <= this.ticks && this.ticks - this.matOrderAt.getOrDefault(var14, -100000) >= 400 && var13.getValue() > var11) {
               var11 = var13.getValue();
               var10 = var14;
            }
         }

         if (var10 == null) {
            return false;
         } else if (InventoryAccess.freeSlots(this.mc) <= this.config.reserveFreeSlots) {
            return false;
         } else {
            this.matOrderAt.put(var10, this.ticks);
            int var15 = Math.min(Math.max(var11, this.layerNeedFor(var10)), Math.max(1, this.config.materialMaxStacks) * var10.getMaxCount());
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Hết {} mà tầng Y={} còn {} ô cần → mở Order lấy {} ngay (không xây vòng bằng khối tạm cho các khối bám vào nó)",
                  new Object[]{var10.getName().getString(), var1, var11, var15}
               );
            this.say("Hết " + var10.getName().getString() + " (tầng Y=" + var1 + " còn " + var11 + " ô) → lấy thêm từ Order.");
            this.target = null;
            this.aim = null;
            this.collect(var10, BuildController.Stage.SELECT, var15);
            return true;
         }
      } else {
         return false;
      }
   }

   private void warnNoScaffold(SchematicPlan.Target var1) {
      if (this.config.scaffold && this.mc.world != null && this.scaffoldHave() <= 0 && !this.hasSupport(var1.pos())) {
         // 3.0.83: có khối lơ lửng cần kê mà hết khối kê → đánh dấu để mở Order lấy thêm (không chỉ báo rồi đứng chờ)
         this.scaffoldWantAt = this.ticks;
      }

      if (this.ticks - this.scaffoldWarnAt > 1200 && this.mc.world != null && !this.hasSupport(var1.pos())) {
         Identifier var2 = Identifier.tryParse(this.config.scaffoldItem);
         Block var3 = var2 == null ? Blocks.AIR : (Block)Registries.BLOCK.get(var2);
         int var4 = var3 == Blocks.AIR ? 0 : InventoryAccess.count(this.mc, var3.asItem());
         if (!this.config.scaffold || var4 <= 0) {
            this.scaffoldWarnAt = this.ticks;
            this.say(
               var1.state().getBlock().getName().getString()
                  + " @ "
                  + var1.pos().toShortString()
                  + " lơ lửng (không có khối nào bên cạnh để bám), cần khối kê tạm — "
                  + (
                     this.config.scaffold
                        ? "trong túi không có " + var3.getName().getString() + " (khối kê chọn ở Cài đặt)."
                        : "hãy bật \"Kê khối tạm\" trong Cài đặt."
                  )
            );
         }
      }
   }

   private void explainStuck(SchematicPlan.Target var1, String var2, int var3) {
      BlockPos var4 = var1.pos();
      SchematicPlan.Target var5 = this.resolve(var1);
      BlockState var6 = this.mc.world.getBlockState(var4);
      Item var7 = Materials.item(var6, var5.state());
      StringBuilder var8 = new StringBuilder();

      for (Direction var12 : Direction.values()) {
         var8.append(var12.getName().charAt(0))
            .append('=')
            .append(this.mc.world.getBlockState(var4.offset(var12)).getBlock().getName().getString())
            .append(' ');
      }

      AutoBuildMod.LOGGER
         .info(
            "[AutoBuild] Hoãn lần {} {} @ {} ({}): đang là {}, đặt bằng {} (có {}), có chỗ bám={}, đặt được={}, cách {} khối, quanh nó: {}",
            new Object[]{
               var3,
               var1.state().getBlock().getName().getString(),
               var4.toShortString(),
               var2,
               var6.getBlock().getName().getString(),
               var7.getName().getString(),
               InventoryAccess.count(this.mc, var7),
               this.hasSupport(var4),
               this.placeable(var5),
               (int)Math.sqrt(this.mc.player.squaredDistanceTo(Vec3d.ofCenter(var4))),
               var8.toString().trim()
            }
         );
   }

   private boolean attempt(SchematicPlan.Target var1) {
      int var2 = this.attempts.merge(var1.pos(), 1, Integer::sum);
      if (var2 <= 4) {
         return true;
      } else {
         this.attempts.remove(var1.pos());
         this.skipped.add(var1.pos().toImmutable());
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] {} @ {} thất bại 4 lần → để cuối tầng, xây phần còn lại rồi quay lại (không dừng)",
               var1.state().getBlock().getName().getString(),
               var1.pos().toShortString()
            );
         return true;
      }
   }

   private boolean hasSupport(BlockPos var1) {
      for (Direction var5 : Direction.values()) {
         BlockPos var6 = var1.offset(var5);
         BlockState var7 = this.mc.world.getBlockState(var6);
         if (!var7.isReplaceable() && !var7.getOutlineShape(this.mc.world, var6).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private void ensureFloor() {
      if (this.floorLayer == null || !this.floorLayer.equals(this.currentLayer)) {
         int var1 = this.currentLayer;
         ArrayList var2 = new ArrayList();
         int var3 = Integer.MAX_VALUE;
         int var4 = Integer.MIN_VALUE;
         int var5 = Integer.MAX_VALUE;
         int var6 = Integer.MIN_VALUE;
         int var7 = 0;
         int var8 = 0;

         for (SchematicPlan.Target var10 : this.plan.targets) {
            if (var10.pos().getY() == var1) {
               var2.add(var10);
               var3 = Math.min(var3, var10.pos().getX());
               var4 = Math.max(var4, var10.pos().getX());
               var5 = Math.min(var5, var10.pos().getZ());
               var6 = Math.max(var6, var10.pos().getZ());
               Direction var11 = rowFacing(var10.state());
               if (var11 != null) {
                  if (var11.getAxis() == Axis.X) {
                     var7++;
                  } else {
                     var8++;
                  }
               }
            }
         }

         this.floorLayer = this.currentLayer;
         this.floorCursor = 0;
         this.planeStuck = 0;
         if (var2.isEmpty()) {
            this.floorOrder = List.of();
            this.floorIndex.clear();
         } else {
            boolean var21 = var7 + var8 > 8 ? var7 >= var8 : var4 - var3 >= var6 - var5;
            BlockPos var22 = this.mc.player.getBlockPos();
            int var23 = var21 ? var5 : var3;
            int var12 = var21 ? var6 : var4;
            int var13 = var21 ? var3 : var5;
            int var14 = var21 ? var4 : var6;
            int var15 = var21 ? var22.getZ() : var22.getX();
            int var16 = var21 ? var22.getX() : var22.getZ();
            boolean var17 = Math.abs(var15 - var23) <= Math.abs(var15 - var12);
            boolean var18 = Math.abs(var16 - var13) <= Math.abs(var16 - var14);
            int var19 = var14 - var13;
            var2.sort(Comparator.comparingLong((SchematicPlan.Target var8x) -> {
               int var9 = var21 ? var8x.pos().getZ() : var8x.pos().getX();
               int var10x = var21 ? var8x.pos().getX() : var8x.pos().getZ();
               int var11x = var17 ? var9 - var23 : var12 - var9;
               int var12x = var18 ? var10x - var13 : var14 - var10x;
               if ((var11x & 1) == 1) {
                  var12x = var19 - var12x;
               }

               return (long)var11x * 100000L + (long)var12x;
            }));
            this.floorOrder = var2;
            this.floorIndex.clear();

            for (int var20 = 0; var20 < var2.size(); var20++) {
               this.floorIndex.put(((SchematicPlan.Target)var2.get(var20)).pos(), var20);
            }

            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Tầng Y={}: {} khối, xây từ góc ({}, {}) theo hàng dọc trục {}",
                  new Object[]{
                     var1,
                     var2.size(),
                     ((SchematicPlan.Target)var2.getFirst()).pos().getX(),
                     ((SchematicPlan.Target)var2.getFirst()).pos().getZ(),
                     var21 ? "X" : "Z"
                  }
               );
         }
      }
   }

   private boolean onLayerPlane() {
      if (this.mc.player.isOnGround() && this.currentLayer != null) {
         int var1 = this.mc.player.getBlockY();
         return var1 == this.currentLayer + 1 || var1 == this.currentLayer;
      } else {
         return false;
      }
   }

   private boolean nearEdge(PathSearch.Cell var1) {
      BlockPos var2 = Navigation.pos(var1).down();

      for (Direction var4 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
         if (!this.solidAt(var2.offset(var4))) {
            return true;
         }
      }

      return false;
   }

   private boolean standGoalOk(PathSearch.Cell var1, SchematicPlan.Target var2) {
      Box var3 = new Box(
         (double)var1.x() + 0.2, (double)var1.y(), (double)var1.z() + 0.2, (double)var1.x() + 0.8, (double)var1.y() + 1.8, (double)var1.z() + 0.8
      );
      if (var3.intersects(new Box(var2.pos()))) {
         return false;
      } else {
         Vec3d var4 = Navigation.eye(var1);
         PlacementSolver.Aim var5 = PlacementSolver.solve(this.mc, var4, var2, this.reach(), true, var2.pos().asLong() * 31L);
         if (var5 != null && PlacementSolver.robust(this.mc, PlacementSolver.poseEye(var4, var5.sneak()), var5, this.reach())) {
            return true;
         } else {
            return var2.pos().getY() < var1.y() && Math.abs(var2.pos().getX() - var1.x()) + Math.abs(var2.pos().getZ() - var1.z()) == 1
               ? PlacementSolver.solve(this.mc, edgeEye(Navigation.feet(var1), var2.pos()), var2, this.reach(), true, var2.pos().asLong() * 31L, true) != null
               : false;
         }
      }
   }

   private List<PathSearch.Cell> planePath(SchematicPlan.Target var1, int var2) {
      int var3 = this.currentLayer;
      Navigation var4 = new Navigation(this.mc, this.config, this.plan, false, this.currentLayer);
      var4.walkOnly = true;
      PathSearch.Cell var5 = Navigation.cell(this.mc.player.getBlockPos());
      ArrayDeque var6 = new ArrayDeque();
      HashMap var7 = new HashMap();
      var6.add(var5);
      var7.put(var5, var5);
      Vec3d var8 = Vec3d.ofCenter(var1.pos());
      double var9 = (this.reach() + 1.2) * (this.reach() + 1.2);
      int var11 = 0;
      // 3.0.100: khối thuộc cụm đang làm → trong ~10 chỗ đứng gần nhất tới được, chọn chỗ với tới NHIỀU khối của cụm nhất
      // (đứng 1 chỗ xây hết các khối liên quan, như người chơi), thay vì chỗ đứng đầu tiên chỉ vừa với 1 khối.
      boolean var29 = this.inModule(var1.pos()) && this.moduleFocus.size() > 1;
      PathSearch.Cell var30 = null;
      int var31 = -1;
      int var32 = 0;
      var4.beginBatch();

      try {
         while (!var6.isEmpty() && var7.size() < var2 && var11 < 48) {
            PathSearch.Cell var24 = (PathSearch.Cell)var6.poll();
            if (Navigation.eye(var24).squaredDistanceTo(var8) <= var9 && var11++ < 48 && this.standGoalOk(var24, var1)) {
               if (var29) {
                  int var33 = this.moduleReachCount(var24);
                  if (var33 > var31) {
                     var31 = var33;
                     var30 = var24;
                  }

                  if (++var32 < 10) {
                     continue;
                  }

                  var24 = var30;
               }

               this.terrain = var4;
               return this.parentPath(var7, var24, var5);
            }

            for (int[] var16 : STEPS8) {
               PathSearch.Cell var17 = var24.add(var16[0], 0, var16[1]);
               boolean var18 = var16[0] != 0 && var16[1] != 0;
               if (!var18 || var4.clear(var24.add(var16[0], 0, 0)) && var4.clear(var24.add(0, 0, var16[1]))) {
                  for (PathSearch.Cell var20 : !var18 ? List.of(var17, var17.add(0, var24.y() == var3 ? 1 : -1, 0)) : List.of(var17)) {
                     if (!var7.containsKey(var20)
                        && (var20.y() == var3 + 1 || var20.y() == var3)
                        && var4.standable(var20)
                        && (var20.y() <= var24.y() || var4.clear(var24.add(0, 1, 0)))
                        && (var20.y() >= var24.y() || var4.clear(var17))
                        && (var20.y() != var3 || !Navigation.pos(var20).equals(var1.pos()))) {
                        var7.put(var20, var24);
                        var6.add(var20);
                     }
                  }
               }
            }

            // 3.0.99 (video người dùng): nhảy qua khe 1–2 ô / nhảy lên khối cao hơn 1 ô / nhảy xuống khối thấp hơn 1 ô
            // trên mặt tầng — trước đây chỉ đi bộ từng ô nên khối ở bên kia khe là "không đi được trên mặt tầng".
            for (PathSearch.Edge var27 : var4.jumpEdges(var24)) {
               PathSearch.Cell var28 = var27.to();
               if (!var7.containsKey(var28)
                  && (var28.y() == var3 + 1 || var28.y() == var3)
                  && (var28.y() != var3 || !Navigation.pos(var28).equals(var1.pos()))
                  && !this.occupiesCell(var28, var1.pos())) {
                  var7.put(var28, var24);
                  var6.add(var28);
               }
            }
         }

         if (var30 != null) {
            this.terrain = var4;
            return this.parentPath(var7, var30, var5);
         } else {
            return null;
         }
      } finally {
         var4.endBatch();
      }
   }

   private List<PathSearch.Cell> parentPath(Map var1, PathSearch.Cell var2, PathSearch.Cell var3) {
      LinkedList<PathSearch.Cell> var4 = new LinkedList<>();
      PathSearch.Cell var5 = var2;

      for (int var6 = 0; var6 < 100000; var6++) {
         var4.addFirst(var5);
         if (var5.equals(var3)) {
            break;
         }

         var5 = (PathSearch.Cell)var1.get(var5);
         if (var5 == null) {
            break;
         }
      }

      return var4;
   }

   /** số khối chưa xong của cụm đang làm mà đứng ở ô này với tới */
   private int moduleReachCount(PathSearch.Cell var1) {
      Vec3d var2 = Navigation.eye(var1);
      double var3 = this.reach() * this.reach();
      int var5 = 0;

      for (BlockPos var7 : this.moduleFocus) {
         if (var2.squaredDistanceTo(Vec3d.ofCenter(var7)) <= var3) {
            SchematicPlan.Target var8 = this.plan.at(var7);
            if (var8 != null && !this.done(this.mc.world.getBlockState(var7), var8.state())) {
               var5++;
            }
         }
      }

      return var5;
   }

   /** ô đứng var1 có đè lên khối cần đặt var2 không (thân người cao 2 ô) */
   private boolean occupiesCell(PathSearch.Cell var1, BlockPos var2) {
      return var1.x() == var2.getX() && var1.z() == var2.getZ() && (var1.y() == var2.getY() || var1.y() + 1 == var2.getY());
   }

   private List<PathSearch.Cell> planePathNear(BlockPos var1, int var2) {
      Navigation var3 = new Navigation(this.mc, this.config, this.plan, false, this.currentLayer);
      var3.walkOnly = true;
      PathSearch.Cell var4 = Navigation.cell(this.mc.player.getBlockPos());
      ArrayDeque var5 = new ArrayDeque();
      HashMap var6 = new HashMap();
      var5.add(var4);
      var6.put(var4, var4);
      double var7 = this.reach() - 0.5;
      var3.beginBatch();

      try {
         while (!var5.isEmpty() && var6.size() < var2) {
            PathSearch.Cell var20 = (PathSearch.Cell)var5.poll();
            Vec3d var10 = Navigation.eye(var20);
            Box var11 = new Box(
               (double)var20.x() + 0.27,
               (double)var20.y() - 1.2,
               (double)var20.z() + 0.27,
               (double)var20.x() + 0.73,
               (double)var20.y() + 1.8,
               (double)var20.z() + 0.73
            );
            if (!var11.intersects(new Box(var1))
               && var10.squaredDistanceTo(Vec3d.ofCenter(var1)) <= var7 * var7
               && this.rayHits(var10, Vec3d.ofCenter(var1), var1)) {
               LinkedList var12 = new LinkedList();
               PathSearch.Cell var13 = var20;

               while (true) {
                  var12.addFirst(var13);
                  if (var13.equals(var4)) {
                     this.terrain = var3;
                     return var12;
                  }

                  var13 = (PathSearch.Cell)var6.get(var13);
               }
            }

            for (int[] var15 : STEPS8) {
               PathSearch.Cell var16 = var20.add(var15[0], 0, var15[1]);
               if ((var15[0] == 0 || var15[1] == 0 || var3.clear(var20.add(var15[0], 0, 0)) && var3.clear(var20.add(0, 0, var15[1])))
                  && !var6.containsKey(var16)
                  && var16.y() == var20.y()
                  && var3.standable(var16)) {
                  var6.put(var16, var20);
                  var5.add(var16);
               }
            }
         }

         return null;
      } finally {
         var3.endBatch();
      }
   }

   private boolean putCursorAway() {
      PlayerInventory var1 = this.mc.player.getInventory();

      for (int var2 = 9; var2 < 36; var2++) {
         if (var1.getStack(var2).isEmpty()) {
            this.clickPlayerSlot(var2);
            return true;
         }
      }

      for (int var3 = 0; var3 < 9; var3++) {
         if (var1.getStack(var3).isEmpty()) {
            this.clickPlayerSlot(var3);
            return true;
         }
      }

      return false;
   }

   private void clickPlayerSlot(int var1) {
      int var2 = var1 < 9 ? 36 + var1 : var1;
      InventoryAccess.trace(this.mc, "cất con trỏ vào ô " + var1);
      InventoryAccess.invClicked();
      this.mc.interactionManager.clickSlot(this.mc.player.playerScreenHandler.syncId, var2, 0, SlotActionType.PICKUP, this.mc.player);
      // 3.0.121: con trỏ tự dưng cầm đồ = túi đồ client đã lệch server → sau khi cất, xin server gửi lại cả túi rồi mới đặt tiếp
      InventoryAccess.noteClick(this.mc, "đã cất con trỏ vào ô " + var1);
      InventoryAccess.markDirty(this.mc, "con trỏ cầm đồ lạ, vừa cất vào ô " + var1);
      AutoBuildMod.LOGGER.info("[AutoBuild] Cất món đồ trên con trỏ vào ô {} rồi làm tiếp", var1);
   }

   /** số ô đang chứa xô rỗng chưa đầy (≥ 2 → gộp lại) */
   private int bucketStacks() {
      int var1 = 0;

      for (int var2 = 0; var2 < 36; var2++) {
         ItemStack var3 = this.mc.player.getInventory().getStack(var2);
         if (var3.isOf(Items.BUCKET) && var3.getCount() < 16) {
            var1++;
         }
      }

      return var1;
   }

   private int sweepHoldUntil = -1000;
   private final Map<BlockPos, Integer> sweepWalks = new HashMap<>();

   /** 3.0.82: khối kê này đập được ngay khi đứng đúng chỗ (không còn làm chỗ bám cho khối chưa xây, đập không làm sập gì). */
   private boolean sweepableLater(BlockPos var1) {
      if (!this.journal.placed.containsKey(var1) || this.sweepSkip.contains(var1) || this.pistonBlockers.contains(var1)) {
         return false;
      } else {
         SchematicPlan.Target var2 = this.plan == null ? null : this.plan.at(var1);
         if (var2 != null && this.done(this.mc.world.getBlockState(var1), var2.state())) {
            return false;
         } else {
            return this.safeToRemove(var1) && (!this.rowTemps.contains(var1) && this.sweepNow.contains(var1) || !this.tempStillNeeded(var1));
         }
      }
   }

   private boolean pendingSweep() {
      if (!this.sweepSkip.isEmpty() && this.ticks - this.sweepSkipAt > 600) {
         this.sweepSkipAt = this.ticks;
         this.sweepSkip.clear();
      }

      if (this.ticks < this.sweepHoldUntil) {
         return false;
      }

      if (!this.sweepLate.isEmpty() && !this.cleanupMode) {
         this.sweepNow.removeAll(this.sweepLate);
      }

      if (!this.sweepNow.isEmpty() && !this.cleanupMode && this.journal != null) {
         if (this.sweepScaffold(true)) {
            return true;
         } else {
            for (BlockPos var2 : new ArrayList<>(this.sweepNow)) {
               if (!this.sweepSkip.contains(var2) && !this.sweepBlocked(var2)) {
                  if (this.journal.placed.containsKey(var2) && !this.mc.world.getBlockState(var2).isAir()) {
                     // 3.0.82: khối kê còn đang làm chỗ bám cho khối chưa xây (vd. hopper hướng ngang) → đứng đâu cũng không
                     // đập được. Bản cũ vẫn "bước khỏi khối kê để đập" → tới nơi không đập → lại bước → lặp vô tận.
                     if (!this.sweepableLater(var2)) {
                        continue;
                     }

                     if (this.sweepWalks.merge(var2.toImmutable(), 1, Integer::sum) > 2) {
                        this.sweepSkip.add(var2);
                        this.sweepLate.add(var2.toImmutable());
                        this.sweepNow.remove(var2);
                        AutoBuildMod.LOGGER.info("[AutoBuild] Đã 2 lần bước ra mà vẫn chưa đập được khối kê {} → để lúc thu dọn cuối, xây tiếp", var2.toShortString());
                        continue;
                     }

                     if (this.sweepWalks.size() > 512) {
                        this.sweepWalks.clear();
                     }

                     if (!(this.horizontalDist(var2) > 8.0) && this.mc.player.isOnGround()) {
                        List var3 = this.planePathNear(var2, 500);
                        if (var3 != null
                           && var3.size() > 1
                           && !this.journal.placed.containsKey(Navigation.pos((PathSearch.Cell)var3.getLast()).down())) {
                           this.target = null;
                           this.supportTarget = null;
                           this.walkPlane(var3, "Bước khỏi khối kê để đập " + var2.toShortString());
                           return true;
                        }
                     }
                  } else {
                     this.sweepNow.remove(var2);
                     this.journal.remove(var2);
                  }
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean plainBlock(BlockState var0) {
      return StateMatch.orientationFree(var0) && !Interaction.interactable(var0) && !var0.hasBlockEntity() && !needsWater(var0);
   }

   private void pushChain(BlockPos var1, BlockState var2) {
      if (this.plan != null && plainBlock(var2)) {
         if (!Objects.equals(this.chainLayer, this.currentLayer)) {
            this.chain.clear();
            this.chainLayer = this.currentLayer;
         }

         ArrayList<Direction> var3 = new ArrayList<>(List.of(Direction.DOWN, Direction.UP));

         for (Direction var5 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            if (var5 != this.rowDir) {
               var3.add(var5);
            }
         }

         if (this.rowDir != null) {
            var3.add(this.rowDir);
         }

         for (Direction var9 : var3) {
            BlockPos var6 = var1.offset(var9);
            SchematicPlan.Target var7 = this.plan.at(var6);
            if (var7 != null
               && plainBlock(var7.state())
               && this.config.inYRange(var6.getY())
               && (!this.config.layerMode || this.currentLayer == null || var6.getY() == this.currentLayer)
               && this.mc.world.isChunkLoaded(var6)
               && !this.done(this.mc.world.getBlockState(var6), var7.state())) {
               this.chain.remove(var6);
               this.chain.addFirst(var6.toImmutable());
            }
         }

         while (this.chain.size() > 96) {
            this.chain.removeLast();
         }
      }
   }

   private boolean chainStep() {
      if (this.plan == null || this.chain.isEmpty()) {
         return false;
      } else if (!Objects.equals(this.chainLayer, this.currentLayer)) {
         this.chain.clear();
         return false;
      } else {
         Vec3d var1 = this.mc.player.getEyePos();
         double var2 = this.reach();
         int var4 = 0;

         while (!this.chain.isEmpty()) {
            var4++;
            if (var4 > 12 || var4 > 3 && this.overBudget()) {
               return false;
            }

            BlockPos var5 = this.chain.pollFirst();
            SchematicPlan.Target var6 = this.plan.at(var5);
            if (var6 != null && this.stillNeeded(var6) && !this.occupies(var5) && this.hasSupport(var5) && this.placeable(var6)) {
               SchematicPlan.Target var7 = this.resolve(var6);
               if (this.mc.player.isOnGround()) {
                  PlacementSolver.Aim var8 = Vec3d.ofCenter(var5).squaredDistanceTo(var1) <= (var2 + 0.5) * (var2 + 0.5)
                     ? PlacementSolver.solve(this.mc, var1, var7, var2, true, var5.asLong() * 31L)
                     : null;
                  if (var8 != null
                     && PlacementSolver.robust(this.mc, PlacementSolver.poseEye(PlacementSolver.standingEye(this.mc, var1), var8.sneak()), var8, var2)) {
                     this.queue.remove(var6);
                     this.target = var7;
                     this.aim = var8;
                     this.status = "Xây lan: " + var7.state().getBlock().getName().getString() + " @ " + var5.toShortString();
                     this.setStage(BuildController.Stage.EQUIP);
                     this.equip();
                     return true;
                  }

                  if (var5.getY() < this.mc.player.getBlockY()
                     && Math.abs(var5.getX() - this.mc.player.getBlockX()) + Math.abs(var5.getZ() - this.mc.player.getBlockZ()) == 1
                     && PlacementSolver.solve(
                           this.mc, edgeEye(Navigation.feet(Navigation.cell(this.mc.player.getBlockPos())), var5), var7, var2, true, var5.asLong() * 31L, true
                        )
                        != null) {
                     this.queue.remove(var6);
                     this.target = var7;
                     this.aim = null;
                     this.status = "Xây lan (ngồi mép): " + var5.toShortString();
                     this.setStage(BuildController.Stage.EQUIP);
                     this.equip();
                     return true;
                  }
               }

               if (this.onLayerPlane() && this.horizontalDist(var5) <= 8.0) {
                  List var9 = this.planePath(var7, 600);
                  if (var9 != null && var9.size() <= 10) {
                     this.queue.remove(var6);
                     this.target = var7;
                     this.walkPlane(var9, "Xây lan: tới " + var5.toShortString());
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   private static boolean needsWater(BlockState var0) {
      return var0.contains(Properties.WATERLOGGED) && (Boolean)var0.get(Properties.WATERLOGGED);
   }

   private boolean driveByCandidate(SchematicPlan.Target var1, Vec3d var2, double var3) {
      if (var1 == this.target
         || !StateMatch.orientationFree(var1.state())
         || needsWater(var1.state())
         || FluidOrder.phase(var1.state()) != 0
         || Materials.bucket(Blocks.AIR.getDefaultState(), var1.state())
         || Vec3d.ofCenter(var1.pos()).squaredDistanceTo(var2) > var3 * var3
         || var1.state().isOf(Blocks.TNT)
         || TntGuard.tntWithin(var1.pos(), 12, null) != null) {
         // 3.0.104: gần TNT không đặt "tiện đường" — để luồng chính kiểm tra an toàn TNT kỹ từng khối
         return false;
      } else if (!this.occupies(var1.pos()) && this.mc.world.isChunkLoaded(var1.pos()) && this.hasSupport(var1.pos())) {
         if (this.path != null) {
            for (int var5 = Math.max(0, this.pathIndex - 1); var5 < this.path.size(); var5++) {
               BlockPos var6 = Navigation.pos(this.path.get(var5));
               if (var1.pos().equals(var6) || var1.pos().equals(var6.up())) {
                  return false;
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private boolean driveBy() {
      if (!this.cleanupMode
         && !this.repairing
         && !this.sweeping
         && this.ticks >= this.cautiousUntil
         && this.mc.currentScreen == null
         && !this.approachOnly
         && this.plan != null) {
         Vec3d var1 = this.mc.player.getEyePos();
         double var2 = this.reach();
         if (this.byTarget != null) {
            BlockState var10 = this.mc.world.getBlockState(this.byTarget.pos());
            if (++this.byTicks <= 8 && !this.done(var10, this.byTarget.state()) && var10.isReplaceable() && this.driveByCandidate(this.byTarget, var1, var2)) {
               Item var11 = Materials.item(var10, this.byTarget.state());
               if (!this.mc.player.getMainHandStack().isOf(var11)) {
                  this.byTarget = null;
                  return false;
               } else {
                  if (this.byAim != null
                     && this.rotationSent()
                     && this.cooldown == 0
                     && InventoryAccess.synced(this.mc)
                     && this.paceOk()
                     && this.mc.player.isSneaking() == this.byAim.sneak()
                     && PlacementSolver.validNow(this.mc, this.byAim, this.byTarget, var2)) {
                     BlockHitResult var12 = PlacementSolver.ray(this.mc, var2);
                     if (Input.crosshairOn(this.mc, var12.getBlockPos(), var12.getSide())) {
                        this.watchExtras(this.byTarget);
                        if (!this.placeNow(var12)) {
                           this.byTarget = null;
                           return false;
                        }

                        this.clicked(this.byTarget.pos());
                        this.queue.remove(this.plan.at(this.byTarget.pos()));
                        this.verify.addLast(new BuildController.Placed(this.byTarget.pos(), this.byTarget.state(), this.ticks + 6 + this.latencyTicks() * 2));
                        this.placedCount++;
                        this.planeStuck = 0;
                        this.byCount++;
                        this.pushChain(this.byTarget.pos(), this.byTarget.state());
                        this.cooldown = this.config.placeDelayTicks;
                        this.byTarget = null;
                        return true;
                     }
                  }

                  PlacementSolver.Aim var13 = PlacementSolver.solve(this.mc, var1, this.byTarget, var2, true, this.byTarget.pos().asLong() * 31L);
                  if (var13 != null && var13.sneak() == this.mc.player.isSneaking()) {
                     this.byAim = var13;
                     this.lookAt(PlacementSolver.aimYaw(this.mc, var13), PlacementSolver.aimPitch(this.mc, var13));
                     return true;
                  } else {
                     this.byTarget = null;
                     return false;
                  }
               }
            } else {
               this.byTarget = null;
               return false;
            }
         } else if (this.cooldown <= 0 && this.paceOk() && !this.overBudget()) {
            int var4 = 0;

            for (SchematicPlan.Target var6 : this.queue) {
               if (this.driveByCandidate(var6, var1, var2)) {
                  if (++var4 > 12) {
                     break;
                  }

                  SchematicPlan.Target var7 = this.resolve(var6);
                  Item var8 = Materials.item(this.mc.world.getBlockState(var6.pos()), var7.state());
                  if (this.mc.player.getMainHandStack().isOf(var8)) {
                     PlacementSolver.Aim var9 = PlacementSolver.solve(this.mc, var1, var7, var2, true, var6.pos().asLong() * 31L);
                     if (var9 != null
                        && var9.sneak() == this.mc.player.isSneaking()
                        && PlacementSolver.robust(this.mc, PlacementSolver.poseEye(PlacementSolver.standingEye(this.mc, var1), var9.sneak()), var9, var2)) {
                        this.byTarget = var7;
                        this.byAim = var9;
                        this.byTicks = 0;

                        this.lookAt(PlacementSolver.aimYaw(this.mc, var9), PlacementSolver.aimPitch(this.mc, var9));
                        return true;
                     }
                  }
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         this.byTarget = null;
         return false;
      }
   }

   private void walkPlane(List<PathSearch.Cell> var1, String var2) {
      this.path = var1;
      this.pathIndex = Math.min(1, var1.size());
      this.lastPosition = this.mc.player.getPos();
      this.stuckTicks = 0;
      this.walkPitch = 30.0F;
      this.planeMove = true;
      this.climbMove = false;
      this.approachOnly = false;
      this.replans = 0;
      this.status = var2;
      if (var1.size() > 1) {
         this.setStage(BuildController.Stage.MOVE);
      } else {
         this.planeMove = false;
         if (this.target == null && this.supportTarget == null) {
            this.nextTarget(true);
         } else {
            this.aim = null;
            this.setStage(BuildController.Stage.EQUIP);
            this.equip();
         }
      }
   }

   private int readyFirstSaidAt = -1000;

   /** 3.0.86: đặt được ngay từ một chỗ đứng nào đó — có mặt bám thật (khối bên cạnh/dưới/trên) hoặc chỉ còn thiếu nước. */
   private boolean readyNow(SchematicPlan.Target var1) {
      BlockPos var2 = var1.pos();
      if (!this.mc.world.isChunkLoaded(var2)) {
         return false;
      } else {
         return Materials.waterlog(this.mc.world.getBlockState(var2), var1.state()) ? true : this.hasSupport(var2) && this.placeable(var1);
      }
   }

   private boolean floorStep() {
      if (this.tryPlaceReachableOffPlane()) {
         return true;
      } else if (this.config.rowMode && this.config.layerMode && this.currentLayer != null && this.plan != null && this.mc.player != null) {
         this.ensureFloor();
         if (this.floorOrder.isEmpty()) {
            return false;
         } else {
            this.heavyLabel = "xây tầng";
            int var1 = this.currentLayer;
            if (!this.onLayerPlane()) {
               return this.climbToLayer();
            } else {
               HashSet var2 = new HashSet();

               for (SchematicPlan.Target var4 : this.queue) {
                  var2.add(var4.pos());
               }

               if (!this.sweepNow.isEmpty()) {
                  if (this.sweepScaffold(true)) {
                     return true;
                  }

                  for (BlockPos var21 : new ArrayList<>(this.sweepNow)) {
                     if (!this.sweepSkip.contains(var21) && !this.sweepBlocked(var21) && this.journal.placed.containsKey(var21)) {
                        List var5 = this.planePathNear(var21, 3000);
                        if (var5 != null && var5.size() > 1) {
                           this.target = null;
                           this.supportTarget = null;
                           this.walkPlane(var5, "Tới đập khối tạm " + var21.toShortString());
                           return true;
                        }
                     }
                  }
               }

               Vec3d var20 = this.mc.player.getEyePos();
               double var22 = this.reach();
               ArrayList<SchematicPlan.Target> var6 = new ArrayList<>();

               for (SchematicPlan.Target var8 : this.queue) {
                  if (Vec3d.ofCenter(var8.pos()).squaredDistanceTo(var20) <= (var22 + 0.5) * (var22 + 0.5) && !this.occupies(var8.pos())) {
                     var6.add(var8);
                  }

                  if (var6.size() > 80) {
                     break;
                  }
               }

               var6.sort(Comparator.comparingDouble(var1x -> -this.horizontalDist(var1x.pos())));
               int var23 = 0;

               for (SchematicPlan.Target var9 : var6) {
                  var23++;
                  if (var23 > 40 || var23 > 6 && this.overBudget()) {
                     break;
                  }

                  if (this.hasSupport(var9.pos()) && this.placeable(var9)) {
                     SchematicPlan.Target var10 = this.resolve(var9);
                     PlacementSolver.Aim var11 = PlacementSolver.solve(this.mc, var20, var10, var22, true, var10.pos().asLong() * 31L);
                     if (var11 != null
                        && PlacementSolver.robust(this.mc, PlacementSolver.poseEye(PlacementSolver.standingEye(this.mc, var20), var11.sneak()), var11, var22)) {
                        this.queue.remove(var9);
                        this.target = var10;
                        this.aim = var11;
                        this.planeStuck = 0;
                        this.status = "Đặt trong tầm: " + var10.state().getBlock().getName().getString();
                        this.setStage(BuildController.Stage.EQUIP);
                        this.equip();
                        return true;
                     }
                  }
               }

               if (this.mc.player.getBlockY() == var1 + 1) {
                  BlockPos var25 = this.mc.player.getBlockPos().down();

                  for (Direction var29 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                     BlockPos var32 = var25.offset(var29);
                     SchematicPlan.Target var12 = this.plan.at(var32);
                     if (var12 != null && var2.contains(var32) && this.hasSupport(var32) && this.placeable(var12)) {
                        SchematicPlan.Target var13 = this.resolve(var12);
                        if (PlacementSolver.solve(
                              this.mc,
                              edgeEye(Navigation.feet(Navigation.cell(this.mc.player.getBlockPos())), var32),
                              var13,
                              var22,
                              true,
                              var32.asLong() * 31L,
                              true
                           )
                           != null) {
                           this.queue.remove(var12);
                           this.target = var13;
                           this.aim = null;
                           this.planeStuck = 0;
                           this.setStage(BuildController.Stage.EQUIP);
                           this.equip();
                           return true;
                        }
                     }
                  }
               }

               int var26 = 0;
               boolean var28 = false;

               while (
                  this.floorCursor < this.floorOrder.size()
                     && !var2.contains(this.floorOrder.get(this.floorCursor).pos())
                     && this.isFinished(this.floorOrder.get(this.floorCursor))
               ) {
                  this.floorCursor++;
               }

               // 3.0.86: khối ĐẶT ĐƯỢC NGAY (có chỗ bám sẵn / chỉ thiếu nước) làm trước, khối lơ lửng cần bắc cầu tạm để sau.
               // Bản cũ đi đúng thứ tự hàng (hoặc nhảy tới ô gần nhất) bất kể ô đó có chỗ bám hay không → bắc cầu 1–3 khối tạm
               // tới lá lơ lửng trong khi các hàng obsidian ngay bên cạnh đặt được luôn vẫn chưa xây.
               ArrayList<SchematicPlan.Target> var50 = new ArrayList<>();
               ArrayList<SchematicPlan.Target> var51 = new ArrayList<>();
               int var52 = this.floorOrder.size();

               for (int var53 = 0; var53 < var52; var53++) {
                  SchematicPlan.Target var54 = this.floorOrder.get((this.floorCursor + var53) % var52);
                  if (var2.contains(var54.pos())) {
                     if (this.readyNow(var54)) {
                        var50.add(var54);
                     } else {
                        var51.add(var54);
                     }
                  }
               }

               if (!this.moduleFocus.isEmpty()) {
                  // 3.0.99: cụm đang làm dở → các khối của cụm (đã có chỗ bám) làm trước, gần trước xa sau
                  this.moduleFocus.removeIf(var1x -> {
                     SchematicPlan.Target var2x = this.plan.at(var1x);
                     return var2x == null || var1x.getY() != var1 || this.mc.world.isChunkLoaded(var1x) && this.done(this.mc.world.getBlockState(var1x), var2x.state());
                  });
                  boolean var56 = false;

                  for (SchematicPlan.Target var57 : var50) {
                     if (this.moduleFocus.contains(var57.pos())) {
                        var56 = true;
                        break;
                     }
                  }

                  if (var56) {
                     var50.sort(
                        Comparator.<SchematicPlan.Target>comparingInt(var1x -> this.moduleFocus.contains(var1x.pos()) ? 0 : 1)
                           .thenComparingDouble(var1x -> this.horizontalDist(var1x.pos()))
                     );
                     this.cursorJump = this.ticks;
                  }
               }

               ArrayList<SchematicPlan.Target> var55 = var50.isEmpty() ? var51 : var50;
               if (!var55.isEmpty() && this.ticks - this.cursorJump > 40) {
                  double var30 = this.horizontalDist(var55.getFirst().pos());
                  if (var30 > 10.0) {
                     SchematicPlan.Target var34 = null;
                     double var36 = var30 - 6.0;

                     for (SchematicPlan.Target var16 : var55) {
                        double var17 = this.horizontalDist(var16.pos());
                        if (var17 < var36) {
                           var36 = var17;
                           var34 = var16;
                        }
                     }

                     if (var34 != null && this.floorIndex.containsKey(var34.pos())) {
                        this.cursorJump = this.ticks;
                        this.floorCursor = this.floorIndex.get(var34.pos());
                        java.util.Collections.rotate(var55, -var55.indexOf(var34));
                        AutoBuildMod.LOGGER
                           .info(
                              "[AutoBuild] Ô kế tiếp cách {} khối mà ngay cạnh còn ô {} chưa xây → làm {} trước",
                              new Object[]{(int)var30, var55 == var50 ? "đặt được ngay" : "", var34.pos().toShortString()}
                           );
                     }
                  }
               }

               if (!var50.isEmpty() && !var51.isEmpty() && this.ticks - this.readyFirstSaidAt > 200) {
                  this.readyFirstSaidAt = this.ticks;
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Tầng Y={}: còn {} khối đặt được ngay (có chỗ bám sẵn) → xây hết trước, {} khối lơ lửng cần bắc cầu để sau (vd. {} @ {})",
                        new Object[]{
                           var1, var50.size(), var51.size(), var51.getFirst().state().getBlock().getName().getString(), var51.getFirst().pos().toShortString()
                        }
                     );
               }

               ArrayList<SchematicPlan.Target> var41 = new ArrayList<>(var50);
               var41.addAll(var51);
               if (this.onPlaneTop() && var50.isEmpty() && var51.size() > 1) {
                  // 3.0.78: tầng toàn khối lơ lửng (vd. lá cách sàn hopper 3 ô) → đang đứng trên mặt tầng thì làm khối GẦN NHẤT trước,
                  // bắc cầu từ khối này sang khối kia, không đi theo thứ tự hàng rồi phải nhảy xuống sàn.
                  var41.sort(Comparator.comparingDouble(var1x -> this.horizontalDist(var1x.pos())));
               }

               for (SchematicPlan.Target var33 : var41) {
                  if (var2.contains(var33.pos())) {
                     var26++;
                     if (var26 > 10 || var26 > 2 && this.overBudget()) {
                        if (this.nextRetries++ < 40) {
                           this.setStage(BuildController.Stage.NEXT);
                           return true;
                        }
                        break;
                     }

                     SchematicPlan.Target var35 = var33;
                     List var37 = List.of();
                     boolean var46 = this.mc.world.isChunkLoaded(var33.pos()) && Materials.waterlog(this.mc.world.getBlockState(var33.pos()), var33.state());
                     if (!var46 && (!this.hasSupport(var33.pos()) || !this.placeable(var33))) {
                        BuildController.BuildPlan var14 = this.resolveBuild(var33);
                        if (var14 == null) {
                           this.defer(var33, 100, "không tìm được khối gốc để bắt đầu");
                           continue;
                        }

                        var35 = var14.root();
                        this.focusModule(var35.pos());
                        var37 = var14.tempChain();
                     }

                     SchematicPlan.Target var38 = var37.isEmpty()
                        ? this.resolve(var35)
                        : new SchematicPlan.Target((BlockPos)var37.getFirst(), this.pillarState((BlockPos)var37.getFirst()));
                     if (var37.isEmpty()
                        && this.mc.player.isOnGround()
                        && !this.occupies(var35.pos())
                        && this.mc.world.isChunkLoaded(var35.pos())
                        && this.mc.player.getEyePos().distanceTo(Vec3d.ofCenter(var35.pos())) <= this.reach()
                        && (this.hasSupport(var35.pos()) || Materials.waterlog(this.mc.world.getBlockState(var35.pos()), var35.state()))
                        && !this.standBanned(var35.pos())
                        && this.placeable(var35)) {
                        this.nextRetries = 0;
                        this.planeStuck = 0;
                        this.queue.remove(var35);
                        this.target = this.resolve(var35);
                        this.status = "Trong tầm tay → đặt luôn " + var35.state().getBlock().getName().getString() + " @ " + var35.pos().toShortString();
                        this.placeOrGo();
                        return true;
                     }

                     // 3.0.100: khối sắp làm thuộc cụm nào thì ghi cụm đó → chọn chỗ đứng với tới nhiều khối của cụm nhất
                     this.focusModule(var35.pos());
                     List var39 = this.planePath(var38, 4000);
                     if (var39 == null && !var46 && var37.isEmpty()) {
                        // 3.0.100: khối đặt được (có chỗ bám) nhưng trên mặt tầng không có chỗ đứng tới được → bắc cầu ngang khối tạm
                        // trên mặt tầng tới sát nó làm chỗ đứng, KHÔNG tìm đường kiểu thường (đường đó hay đi xuống thấp rồi /home, lặp mãi).
                        BuildController.BuildPlan var58 = this.planeStandBuild(var35);
                        if (var58 != null) {
                           var37 = var58.tempChain();
                           var38 = new SchematicPlan.Target((BlockPos)var37.getFirst(), this.pillarState((BlockPos)var37.getFirst()));
                           var39 = this.planePath(var38, 4000);
                        }
                     }

                     if (var39 == null && var46 && var37.isEmpty()) {
                        // 3.0.78: lá đã đặt, chỉ thiếu nước mà trên mặt tầng chưa có chỗ đứng tới được →
                        // bắc cầu ngang tới sát lá rồi đổ nước luôn, không bỏ đi / không nhảy xuống sàn.
                        BuildController.BuildPlan var47 = this.planeBuild(var33);
                        if (var47 != null) {
                           var37 = var47.tempChain();
                           var38 = new SchematicPlan.Target((BlockPos)var37.getFirst(), this.pillarState((BlockPos)var37.getFirst()));
                           var39 = this.planePath(var38, 4000);
                        }
                     }

                     if (var39 != null) {
                        this.nextRetries = 0;
                        this.planeStuck = 0;
                        if (var37.isEmpty()) {
                           this.queue.remove(var35);
                           this.target = this.resolve(var35);
                           this.walkPlane(var39, "Tới đặt " + var35.state().getBlock().getName().getString() + " (theo hàng)");
                        } else {
                           this.queue.remove(var35);
                           this.target = this.resolve(var35);
                           if (!this.jobTemps.isEmpty()) {
                              this.sweepNow.addAll(this.jobTemps);
                              this.jobTemps.clear();
                           }

                           this.jobTemps.addAll(var37);
                           this.jobTempFor = var35.pos().toImmutable();
                           this.jobSupport = true;
                           this.supportQueue.clear();
                           this.supportQueue.addAll(var37.subList(1, var37.size()));
                           this.supportTarget = var38;
                           this.planSelfPillar(var37);
                           AutoBuildMod.LOGGER
                              .info(
                                 "[AutoBuild] {} @ {} chưa có chỗ bám → khối tạm tại {} ({} khối), đặt xong đập ngay",
                                 new Object[]{
                                    var35.state().getBlock().getName().getString(),
                                    var35.pos().toShortString(),
                                    ((BlockPos)var37.getLast()).toShortString(),
                                    var37.size()
                                 }
                              );
                           this.walkPlane(var39, "Tới đặt khối tạm " + ((BlockPos)var37.getFirst()).toShortString());
                        }

                        return true;
                     }

                     if (var37.isEmpty() && this.navFailures.getOrDefault(var35.pos(), 0) == 0 && !this.skipped.contains(var35.pos())) {
                        AutoBuildMod.LOGGER
                           .info(
                              "[AutoBuild] {} @ {}: không đi được trên mặt tầng → tìm chỗ đứng / bắc cầu kiểu thường ngay, không chờ",
                              var35.state().getBlock().getName().getString(),
                              var35.pos().toShortString()
                           );
                        this.nextRetries = 0;
                        this.planeStuck = 0;
                        this.queue.remove(var35);
                        this.target = this.resolve(var35);
                        this.aim = null;
                        this.beginGoals(false);
                        return true;
                     }

                     var28 = true;
                     this.defer(var35, 80, "không có đường đi trên mặt tầng");
                     if (var35 != var33) {
                        this.defer(var33, 80, "khối gốc không tới được");
                     }
                  }
               }

               if (var28 && this.mc.player.getBlockY() == var1 + 1 && ++this.planeStuck <= 1) {
                  this.status = "Chưa tới được khối còn lại trên mặt tầng — thử lại sau";
                  this.queue.clear();
                  this.setStage(BuildController.Stage.WAIT_MATERIAL);
                  return true;
               } else {
                  if (var28) {
                     AutoBuildMod.LOGGER.info("[AutoBuild] Tầng Y={}: còn khối không tới được trên mặt tầng → lần này dùng cách tìm đường thường", var1);
                     this.planeStuck = 0;
                  }

                  return false;
               }
            }
         }
      } else {
         return false;
      }
   }

   private BlockState pillarState(BlockPos var1) {
      Navigation var4 = new Navigation(this.mc, this.config, this.plan, true);
      BlockState var2 = var4.pillarStateAt(var1);
      if (var2 != null) {
         return var2;
      } else {
         BlockState var3 = var4.scaffoldState();
         return var3 != null ? var3 : Blocks.COBBLESTONE.getDefaultState();
      }
   }

   private boolean isFinished(SchematicPlan.Target var1) {
      BlockPos var2 = var1.pos();
      return this.skipped.contains(var2)
         || this.placedDone.contains(var2)
         || this.mc.world.isChunkLoaded(var2) && this.done(this.mc.world.getBlockState(var2), var1.state());
   }

   private double horizontalDist(BlockPos var1) {
      double var2 = (double)var1.getX() + 0.5 - this.mc.player.getX();
      double var4 = (double)var1.getZ() + 0.5 - this.mc.player.getZ();
      return Math.sqrt(var2 * var2 + var4 * var4);
   }

   private boolean touchesLayer(int var1, int var2) {
      if (this.currentLayer != null && this.mc.world != null) {
         for (Direction var4 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos var5 = new BlockPos(var1 + var4.getOffsetX(), this.currentLayer, var2 + var4.getOffsetZ());
            if (this.mc.world.isChunkLoaded(var5) && this.solidAt(var5)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean standNear(Navigation var1, BlockPos var2) {
      for (Direction var4 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
         BlockPos var5 = var2.offset(var4);
         if (var1.standable(Navigation.cell(var5.up())) || var1.standable(Navigation.cell(var5))) {
            return true;
         }
      }

      return false;
   }

   private boolean standBesideTarget(Navigation var1) {
      if (this.mc.world == null) {
         return false;
      } else {
         SchematicPlan.Target var2 = this.current();
         if (var2 != null && this.standNear(var1, var2.pos())) {
            return true;
         } else if (this.plan != null && this.currentLayer != null) {
            int var3 = 0;

            for (SchematicPlan.Target var5 : this.plan.targets) {
               BlockPos var6 = var5.pos();
               if (var6.getY() == this.currentLayer
                  && this.config.inYRange(var6.getY())
                  && !this.skipped.contains(var6)
                  && this.mc.world.isChunkLoaded(var6)
                  && !this.done(this.mc.world.getBlockState(var6), var5.state())) {
                  if (this.standNear(var1, var6)) {
                     return true;
                  }

                  if (++var3 >= 40) {
                     return false;
                  }
               }
            }

            return false;
         } else {
            return false;
         }
      }
   }

   private boolean climbToLayer() {
      // 3.0.100: chỉ thấp hơn mặt đi lại 1 ô (vd. xây Y=-33, chân đang ở Y=-34) → tự bước/nhảy lên trước (mặt tầng thường
      // ngay cạnh), chỉ /home khi tìm đường lên không được. Lỗi 20:21–20:28: vừa nhảy xuống -34 là /home ngay, lặp 6 lần.
      boolean var21 = this.mc.player.isOnGround() && this.mc.player.getY() >= (double)this.currentLayer - 1.05;
      if (!var21 && this.config.useHome && this.ownHomeUsable() && this.placeUnconfirmed()) {
         // 3.0.101: chờ server xác nhận khối vừa đặt (mất thì đặt lại tại chỗ) rồi mới /home
         this.status = "Chờ server xác nhận khối vừa đặt rồi mới /home";
         this.setStage(BuildController.Stage.NEXT);
         return true;
      } else if ((!var21 || this.ticks < this.climbFailUntil) && this.tryHomeUp("Cần lên lại mặt tầng")) {
         return true;
      } else if (this.ticks < this.climbFailUntil) {
         return false;
      } else {
         int var1 = this.currentLayer;
         if (!this.mc.player.isOnGround() && this.mc.player.getVelocity().horizontalLengthSquared() < 1.0E-4 && Math.abs(this.mc.player.getVelocity().y) < 0.01) {
            this.airStillTicks++;
         } else {
            this.airStillTicks = 0;
         }

         if (!this.mc.player.isOnGround() && !this.mc.player.isSubmergedInWater() && this.airStillTicks < 60) {
            // 3.0.78: đang trong nhịp nhảy kê / đang rơi → chờ chạm đất rồi bắc tiếp cột đang dựng,
            // không nhảy sang tìm đường kiểu thường (trước đây ra cầu ngang cao 1 khối trên sàn hopper)
            // 3.0.105: đứng yên v=0 mà game vẫn báo "chưa chạm đất" quá 3 giây (log 02:04–02:09 chờ 5 phút, 24 lần) → coi như đã chạm
            this.status = "Chờ chạm đất rồi bắc tiếp lên Y=" + var1;
            this.setStage(BuildController.Stage.NEXT);
            return true;
         }

         Navigation var2 = new Navigation(this.mc, this.config, this.plan, this.scaffoldAllowed(), this.currentLayer);
         BlockPos var3 = this.mc.player.getBlockPos();
         if (this.standBesideTarget(var2)) {
            return false;
         } else if (this.tryDirectLayerClimb(var2)) {
            return true;
         } else {
            ArrayList var4 = new ArrayList();

            for (int var5 = 0; var5 <= 14 && var4.size() < 16; var5++) {
               for (int var6 = -var5; var6 <= var5; var6++) {
                  for (int var7 = -var5; var7 <= var5; var7++) {
                     if (Math.max(Math.abs(var6), Math.abs(var7)) == var5) {
                        for (int var11 : new int[]{var1 + 1, var1}) {
                           PathSearch.Cell var12 = new PathSearch.Cell(var3.getX() + var6, var11, var3.getZ() + var7);
                           if (var2.standable(var12)) {
                              var4.add(var12);
                           }
                        }
                     }
                  }
               }
            }

            if (var4.isEmpty()) {
               SchematicPlan.Target var13 = null;

               for (SchematicPlan.Target var18 : this.floorOrder) {
                  if (!this.isFinished(var18) && this.deferred.getOrDefault(var18.pos(), 0) <= this.ticks) {
                     var13 = var18;
                     break;
                  }
               }

               if (var13 == null) {
                  return false;
               }

               for (Direction var19 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                  PathSearch.Cell var20 = Navigation.cell(var13.pos().offset(var19).up());
                  if (var2.clear(var20) && (var2.standable(var20) || var2.canScaffoldAt(Navigation.pos(var20).down()))) {
                     var4.add(var20);
                  }
               }

               PathSearch.Cell var17 = Navigation.cell(var13.pos().up());
               if (var2.clear(var17) && var2.realPillar(var13.state())) {
                  var4.add(var17);
               }
            }

            if (var4.isEmpty()) {
               return false;
            } else {
               var2.walkOnly = false;
               List var14 = var2.startCells();
               var2.bound(var14, var4, 10);
               this.terrain = var2;
               this.search = new PathSearch(var14, var4, var2, 6000);
               this.climbSearch = true;
               this.target = null;
               this.supportTarget = null;
               var4.clear();
               this.status = "Lên mặt tầng Y=" + var1;
               this.setStage(BuildController.Stage.SEARCH);
               return true;
            }
         }
      }
   }

   private void ensureRows() {
      if (this.plan != null && (!Objects.equals(this.currentLayer, this.rowsLayer) || this.rows == null)) {
         HashMap var1 = new HashMap();

         for (SchematicPlan.Target var3 : this.plan.targets) {
            if (var3.pos().getY() == this.currentLayer) {
               BlockState var4 = var3.state();
               if (!var4.isAir() && !var4.isReplaceable() && var4.getFluidState().isEmpty() && !var4.getOutlineShape(this.mc.world, var3.pos()).isEmpty()) {
                  Direction var5 = rowFacing(var4);
                  var1.put(
                     Navigation.cell(var3.pos()),
                     new RowPlanner.Info(var4.toString(), var5 == null ? 0 : var5.getOffsetX(), var5 == null ? 0 : var5.getOffsetZ())
                  );
               }
            }
         }

         this.rows = RowPlanner.plan(var1, Navigation.cell(this.mc.player.getBlockPos()), 3);
         this.rowsLayer = this.currentLayer;
         this.job = null;
         this.rowDeferred.clear();
         AutoBuildMod.LOGGER.info("[AutoBuild] Tầng Y={}: chia thành {} hàng để xây lần lượt", this.currentLayer, this.rows.size());
      }
   }

   private PathSearch.Cell rowSaidStart;
   private final Map<PathSearch.Cell, Integer> rowSaidMap = new HashMap<>();
   private int rowSaidAt = -1000;

   private RowPlanner.Row nextRow(Set<BlockPos> var1) {
      Vec3d var2 = this.mc.player.getPos();
      RowPlanner.Row var3 = null;
      double var4 = Double.MAX_VALUE;

      for (RowPlanner.Row var7 : this.rows) {
         // 3.0.84: hàng có thể đã bị lật chiều (reversed) khi hoãn → kiểm tra cả 2 đầu. Lỗi cũ: hoãn theo đầu của hàng đã lật,
         // còn đây kiểm tra đầu gốc → hoãn vô tác dụng, cùng một hàng bị chọn lại 8 lần/tick ("Hàng mới" 73.778 dòng, "Hoãn lần 117").
         if (Math.max(this.rowDeferred.getOrDefault(var7.start(), 0), this.rowDeferred.getOrDefault(var7.end(), 0)) <= this.ticks) {
            for (PathSearch.Cell var9 : var7.cells()) {
               BlockPos var10 = Navigation.pos(var9);
               if (var1.contains(var10)) {
                  double var11 = var2.squaredDistanceTo((double)var10.getX() + 0.5, (double)var10.getY() + 0.5, (double)var10.getZ() + 0.5);
                  // 3.0.84: ô chưa có chỗ bám (phải kê/bắc cầu) xếp sau ô bám được ngay — xây obsidian có chỗ bám trước, lá lơ lửng sau
                  if (var11 < var4 && !this.hasSupport(var10)) {
                     var11 += 400.0;
                  }

                  if (var11 < var4) {
                     var4 = var11;
                     var3 = var7;
                  }
               }
            }

            if (var4 <= 4.0) {
               break;
            }
         }
      }

      if (var3 == null) {
         return null;
      } else {
         if (!var3.fixed()) {
            BlockPos var13 = Navigation.pos(var3.start());
            BlockPos var14 = Navigation.pos(var3.end());
            if (var2.squaredDistanceTo((double)var14.getX() + 0.5, (double)var14.getY() + 0.5, (double)var14.getZ() + 0.5)
               < var2.squaredDistanceTo((double)var13.getX() + 0.5, (double)var13.getY() + 0.5, (double)var13.getZ() + 0.5)) {
               var3 = var3.reversed();
            }
         }

         return var3;
      }
   }

   private boolean tryRowJob() {
      if (this.config.rowMode && this.config.layerMode && this.currentLayer != null && this.plan != null) {
         this.ensureRows();
         if (this.rows.isEmpty()) {
            return false;
         } else {
            HashSet var1 = new HashSet();

            for (SchematicPlan.Target var3 : this.queue) {
               var1.add(var3.pos());
            }

            for (int var10 = 0; var10 < 8; var10++) {
               if (this.job == null) {
                  this.job = this.nextRow(var1);
                  if (this.job == null) {
                     return false;
                  }

                  // 3.0.99: log 15:27–15:28 in "Hàng mới" 20 dòng/giây (3 hàng thay nhau) → mỗi hàng chỉ báo 1 lần / 10 giây
                  PathSearch.Cell var42 = this.job.start();
                  PathSearch.Cell var43 = this.job.end();
                  if (this.ticks - Math.max(this.rowSaidMap.getOrDefault(var42, -100000), this.rowSaidMap.getOrDefault(var43, -100000)) > 200) {
                     this.rowSaidMap.put(var42, this.ticks);
                     this.rowSaidMap.put(var43, this.ticks);
                     if (this.rowSaidMap.size() > 256) {
                        this.rowSaidMap.values().removeIf(var1x -> this.ticks - var1x > 200);
                     }

                     this.rowSaidStart = this.job.start();
                     this.rowSaidAt = this.ticks;
                     AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Hàng mới: {} ô {} từ {} (hướng {},{})",
                        new Object[]{
                           this.job.cells().size(),
                           this.job.key().replaceAll("^Block\\{minecraft:|\\}", ""),
                           Navigation.pos(this.job.start()).toShortString(),
                           this.job.dx(),
                           this.job.dz()
                        }
                     );
                  }
               }

               SchematicPlan.Target var11 = null;
               int var4 = -1;
               boolean var5 = false;
               SchematicPlan.Target var40 = null;
               int var41 = -1;

               for (int var6 = 0; var6 < this.job.cells().size(); var6++) {
                  BlockPos var7 = Navigation.pos(this.job.cells().get(var6));
                  SchematicPlan.Target var8 = this.plan.at(var7);
                  if (var8 != null && !this.skipped.contains(var7) && !this.placedDone.contains(var7)) {
                     if (!this.mc.world.isChunkLoaded(var7)) {
                        var5 = true;
                        break;
                     }

                     if (!this.done(this.mc.world.getBlockState(var7), var8.state())) {
                        // 3.0.84: hàng không cần thứ tự (obsidian, lá…): ô đầu đang hoãn thì làm ô kế tiếp có trong hàng đợi,
                        // không hoãn cả hàng. Hàng có hướng (hopper ngang) vẫn phải làm đúng thứ tự.
                        if (!this.job.fixed() && !var1.contains(var7)) {
                           if (var40 == null) {
                              var40 = var8;
                              var41 = var6;
                           }

                           continue;
                        }

                        var11 = var8;
                        var4 = var6;
                        break;
                     }
                  }
               }

               if (var5) {
                  return false;
               }

               if (var11 == null && var40 != null) {
                  var11 = var40;
                  var4 = var41;
               }

               if (var11 == null) {
                  // 3.0.99: hàng không còn ô nào làm được lúc này (đã bỏ qua / đang chờ) → hoãn cả 2 đầu, không chọn lại ngay trong tick này
                  this.rowDeferred.put(this.job.start(), this.ticks + 100);
                  this.rowDeferred.put(this.job.end(), this.ticks + 100);
                  this.job = null;
               } else if (!var1.contains(var11.pos())) {
                  this.rowDeferred.put(this.job.start(), this.ticks + 100);
                  this.defer(var11, 100, "ô đầu hàng không nằm trong hàng đợi (thiếu vật liệu / đã hoãn)");
                  this.job = null;
               } else {
                  if (this.tryRequiredFrontSupport(var11, var4)) {
                     return true;
                  }

                  if (this.hasSupport(var11.pos()) && this.placeable(var11)) {
                     this.queue.remove(var11);
                     this.target = this.resolve(var11);
                     this.status = "Xây theo hàng: ô " + (var4 + 1) + "/" + this.job.cells().size();
                     this.placeOrGo();
                     return true;
                  }

                  boolean var12 = true;

                  for (int var14 = 0; var14 < var4; var14++) {
                     BlockPos var17 = Navigation.pos(this.job.cells().get(var14));
                     SchematicPlan.Target var9 = this.plan.at(var17);
                     if (var9 != null && !this.skipped.contains(var17) && !this.done(this.mc.world.getBlockState(var17), var9.state())) {
                        var12 = false;
                     }
                  }

                  if (var4 == 0 && !this.job.fixed()) {
                     SchematicPlan.Target var15 = this.plan.at(Navigation.pos(this.job.end()));
                     if (var15 != null
                        && !this.done(this.mc.world.getBlockState(var15.pos()), var15.state())
                        && this.hasSupport(var15.pos())
                        && this.placeable(var15)) {
                        this.job = this.job.reversed();
                        continue;
                     }
                  }

                  BuildController.BuildPlan var16 = this.resolveBuild(var11);
                  if (var16 != null) {
                     this.startBuildPlan(var16, var11);
                     return true;
                  }

                  this.rowDeferred.put(this.job.start(), this.ticks + 200);
                  this.defer(var11, 200, "hàng chưa có chỗ bám / chưa đặt được");
                  this.job = null;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private void placeOrGo() {
      PlacementSolver.Aim var1 = PlacementSolver.solve(this.mc, this.mc.player.getEyePos(), this.target, this.reach(), true, this.target.pos().asLong() * 31L);
      if (var1 != null && this.mc.player.isOnGround() && !this.occupies(this.target.pos())) {
         this.aim = var1;
         this.setStage(BuildController.Stage.EQUIP);
         this.equip();
      } else if (var1 == null
         && this.mc.player.isOnGround()
         && this.target.pos().getY() < this.mc.player.getBlockY()
         && PlacementSolver.solve(
               this.mc,
               edgeEye(Navigation.feet(Navigation.cell(this.mc.player.getBlockPos())), this.target.pos()),
               this.target,
               this.reach(),
               true,
               this.target.pos().asLong() * 31L,
               true
            )
            != null) {
         this.aim = null;
         this.setStage(BuildController.Stage.EQUIP);
         this.equip();
      } else {
         this.beginGoals(false);
      }
   }

   static Direction rowFacing(BlockState var0) {
      if (var0.getBlock() instanceof HopperBlock) {
         Direction var1 = (Direction)var0.get(HopperBlock.FACING);
         return var1.getAxis().isHorizontal() ? var1 : null;
      } else {
         return null;
      }
   }

   private BuildController.RowPlan rowPlan(SchematicPlan.Target var1) {
      Direction var2 = rowFacing(var1.state());
      if (var2 != null && this.plan != null) {
         BlockPos var3 = var1.pos();

         for (int var4 = 0; var4 < 128; var4++) {
            BlockPos var5 = var3.offset(var2);
            SchematicPlan.Target var6 = this.plan.at(var5);
            if (var6 == null || rowFacing(var6.state()) != var2 || !this.mc.world.isChunkLoaded(var5)) {
               break;
            }

            BlockState var7 = this.mc.world.getBlockState(var5);
            if (this.done(var7, var6.state()) || !var7.isReplaceable() || this.skipped.contains(var5)) {
               break;
            }

            var3 = var5;
         }

         return new BuildController.RowPlan(this.plan.at(var3), var3.offset(var2));
      } else {
         return null;
      }
   }

   private List<BlockPos> rowChain(BlockPos var1) {
      if (!this.scaffoldAllowed()) {
         return List.of();
      } else {
         Integer var2 = this.config.layerMode ? this.currentLayer : null;
         Navigation var3 = new Navigation(this.mc, this.config, this.plan, true, var2);
         if (var3.scaffoldState() == null || InventoryAccess.count(this.mc, var3.scaffoldState().getBlock().asItem()) == 0) {
            if (var3.scaffoldState() != null) {
               this.scaffoldWantAt = this.ticks;
            }

            return List.of();
         } else if (!var3.canScaffoldAt(var1) || this.occupies(var1)) {
            return List.of();
         } else if (this.hasSupport(var1)) {
            return List.of(var1.toImmutable());
         } else {
            ArrayList var4 = new ArrayList<>(this.supportChain(var1));
            if (var4.isEmpty()) {
               return List.of();
            } else {
               var4.add(var1.toImmutable());
               return var4;
            }
         }
      }
   }

   private boolean tempStillNeeded(BlockPos var1) {
      if (!this.cleanupMode && this.pistonBlockers.contains(var1)) {
         return true;
      }

      for (Direction var5 : Direction.values()) {
         BlockPos var6 = var1.offset(var5);
         SchematicPlan.Target var7 = this.plan.at(var6);
         if (var7 != null
            && !this.skipped.contains(var6)
            && this.config.inYRange(var6.getY())
            && (!this.config.layerMode || this.currentLayer == null || var6.getY() <= this.currentLayer)) {
            if (!this.mc.world.isChunkLoaded(var6)) {
               return true;
            }

            if (!this.done(this.mc.world.getBlockState(var6), var7.state())) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean tryRowStart(List<SchematicPlan.Target> var1, List<SchematicPlan.Target> var2, boolean var3, BlockPos var4) {
      if (this.plan == null) {
         return false;
      } else {
         HashSet var5 = new HashSet();
         SchematicPlan.Target var6 = null;
         List var7 = null;
         BlockPos var8 = null;
         double var9 = Double.MAX_VALUE;
         int var11 = 0;
         ArrayList<SchematicPlan.Target> var12 = new ArrayList<>(var1);
         var12.addAll(var2);

         for (SchematicPlan.Target var14 : var12) {
            var11++;
            if (var11 > 150 || var11 > 10 && this.overBudget()) {
               break;
            }

            BuildController.RowPlan var15 = this.rowPlan(var14);
            if (var15 != null && var15.head() != null && var5.add(var15.front())) {
               SchematicPlan.Target var16 = var15.head();
               if (this.buildableNow(var16) && this.deferred.getOrDefault(var16.pos(), 0) <= this.ticks) {
                  List var17;
                  double var18;
                  if (this.solidAt(var15.front())) {
                     if (var3 && !this.nearReachable(var16.pos()) || !this.placeable(var16)) {
                        continue;
                     }

                     var17 = List.of();
                     var18 = 0.0;
                  } else {
                     SchematicPlan.Target var20 = this.plan.at(var15.front());
                     if (var20 != null && !var20.state().isAir() || var3 && !this.nearReachable(var15.front())) {
                        continue;
                     }

                     var17 = this.rowChain(var15.front());
                     if (var17.isEmpty()) {
                        continue;
                     }

                     var18 = (double)(var17.size() * 4);
                  }

                  if (var4 == null || var15.front().getManhattanDistance(var4) <= 5) {
                     BlockPos var24 = var4 != null ? var4 : this.lastPlaced;
                     double var21 = (
                           var24 != null
                              ? (double)var15.front().getManhattanDistance(var24)
                              : Math.sqrt(this.mc.player.squaredDistanceTo(Vec3d.ofCenter(var15.front())))
                        )
                        + var18;
                     if (var21 < var9) {
                        var9 = var21;
                        var6 = var16;
                        var7 = var17;
                        var8 = var15.front();
                     }
                  }
               }
            }
         }

         if (var6 == null) {
            return false;
         } else {
            this.queue.remove(var6);
            this.deferred.remove(var6.pos());
            this.target = this.resolve(var6);
            this.nextRetries = 0;
            if (var7.isEmpty()) {
               this.status = "Hàng hopper: đặt hopper đầu hàng " + var6.pos().toShortString();
               this.beginGoals(false);
               return true;
            } else {
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] Hàng hopper hướng {}: đặt khối tạm tại {} ({} khối) rồi xây nối từ {}",
                     new Object[]{StateMatch.property(var6.state(), "facing"), var8.toShortString(), var7.size(), var6.pos().toShortString()}
                  );
               this.rowSupport = true;
               this.supportQueue.clear();
               this.supportQueue.addAll(var7.subList(1, var7.size()));
               this.supportTarget = new SchematicPlan.Target((BlockPos)var7.getFirst(), this.pillarState((BlockPos)var7.getFirst()));
               this.status = "Đặt khối tạm đầu hàng hopper tại " + var8.toShortString();
               PlacementSolver.Aim var23 = PlacementSolver.solve(
                  this.mc, this.mc.player.getEyePos(), this.supportTarget, this.reach(), true, this.supportTarget.pos().asLong(), true
               );
               if (var23 != null && this.mc.player.isOnGround()) {
                  this.aim = var23;
                  this.setStage(BuildController.Stage.EQUIP);
                  this.equip();
                  return true;
               } else {
                  this.beginGoals(false);
                  return true;
               }
            }
         }
      }
   }

   // ===== 3.0.78: bắc cầu ngang trên mặt tầng (lá nối lá), không nhảy xuống =====

   /** Đang đứng trên mặt tầng: chân ở Y = tầng + 1, đứng trên một khối nằm ở chính tầng đang xây. */
   private boolean onPlaneTop() {
      return this.currentLayer != null
         && this.mc.player != null
         && this.mc.player.isOnGround()
         && this.mc.player.getBlockY() == this.currentLayer + 1
         && !this.partialFloor();
   }

   /** Các khối ở tầng hiện tại mà mình đi bộ tới được trên mặt tầng (đứng lên trên chúng được). */
   private Set<BlockPos> planeSupports() {
      HashSet<BlockPos> var1 = new HashSet<>();
      if (this.currentLayer == null) {
         return var1;
      } else {
         int var2 = this.currentLayer + 1;
         Navigation var3 = new Navigation(this.mc, this.config, this.plan, false, this.currentLayer);
         var3.walkOnly = true;
         ArrayDeque<PathSearch.Cell> var4 = new ArrayDeque<>();
         HashSet<PathSearch.Cell> var5 = new HashSet<>();
         PathSearch.Cell var6 = Navigation.cell(this.mc.player.getBlockPos());
         var4.add(var6);
         var5.add(var6);
         BlockPos var7 = BlockPos.ofFloored(this.mc.player.getX(), this.mc.player.getY() - 0.2, this.mc.player.getZ());
         if (var7.getY() == this.currentLayer && this.solidAt(var7)) {
            var1.add(var7.toImmutable());
         }

         var3.beginBatch();

         try {
            while (!var4.isEmpty() && var5.size() < 600) {
               PathSearch.Cell var8 = var4.poll();
               BlockPos var9 = Navigation.pos(var8).down();
               if (this.solidAt(var9)) {
                  var1.add(var9.toImmutable());
               }

               for (int[] var13 : STEPS8) {
                  PathSearch.Cell var14 = var8.add(var13[0], 0, var13[1]);
                  boolean var15 = var13[0] != 0 && var13[1] != 0;
                  if (!var5.contains(var14)
                     && var14.y() == var2
                     && (!var15 || var3.clear(var8.add(var13[0], 0, 0)) && var3.clear(var8.add(0, 0, var13[1])))
                     && var3.standable(var14)) {
                     var5.add(var14);
                     var4.add(var14);
                  }
               }

               // 3.0.99: khối bên kia khe nhảy qua được cũng là chỗ bám để bắc cầu
               for (PathSearch.Edge var16 : var3.jumpEdges(var8)) {
                  if (var16.to().y() == var2 && var5.add(var16.to())) {
                     var4.add(var16.to());
                  }
               }
            }
         } finally {
            var3.endBatch();
         }

         return var1;
      }
   }

   /**
    * Chuỗi khối tạm NẰM NGANG ở đúng tầng, nối từ một khối mình đứng tới được (var2) sang ô var1 (cạnh khối cần xây).
    * Thứ tự trả về: [ô sát khối có sẵn, ..., var1] — đặt lần lượt, mỗi khối bám vào mặt bên của khối trước.
    */
   private List<BlockPos> planeChain(BlockPos var1, Set<BlockPos> var2) {
      if (this.currentLayer == null || var1.getY() != this.currentLayer || var2.isEmpty() || !this.scaffoldAllowed()) {
         return List.of();
      } else {
         Navigation var3 = new Navigation(this.mc, this.config, this.plan, true, this.currentLayer);
         BlockState var4 = var3.scaffoldState();
         if (var4 == null || InventoryAccess.count(this.mc, var4.getBlock().asItem()) == 0) {
            if (var4 != null) {
               this.scaffoldWantAt = this.ticks;
            }

            return List.of();
         } else if (!var3.canScaffoldAt(var1) || this.occupies(var1)) {
            return List.of();
         } else {
            HashMap<BlockPos, BlockPos> var5 = new HashMap<>();
            HashMap<BlockPos, Integer> var6 = new HashMap<>();
            ArrayDeque<BlockPos> var7 = new ArrayDeque<>();
            BlockPos var8 = var1.toImmutable();
            var5.put(var8, null);
            var6.put(var8, 1);
            var7.add(var8);

            while (!var7.isEmpty() && var5.size() < 400) {
               BlockPos var9 = var7.poll();

               for (Direction var11 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                  if (var2.contains(var9.offset(var11))) {
                     ArrayList<BlockPos> var12 = new ArrayList<>();

                     for (BlockPos var13 = var9; var13 != null; var13 = var5.get(var13)) {
                        var12.add(var13);
                     }

                     return var12;
                  }
               }

               int var15 = var6.get(var9);
               if (var15 < 10) {
                  for (Direction var17 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                     BlockPos var14 = var9.offset(var17).toImmutable();
                     if (!var5.containsKey(var14) && var3.canScaffoldAt(var14) && !this.occupies(var14)) {
                        var5.put(var14, var9);
                        var6.put(var14, var15 + 1);
                        var7.add(var14);
                     }
                  }
               }
            }

            return List.of();
         }
      }
   }

   /** 3.0.100: khối có chỗ bám nhưng không có chỗ đứng tới được trên mặt tầng → cầu ngang khối tạm tới sát nó (đứng lên cầu mà đặt). */
   private BuildController.BuildPlan planeStandBuild(SchematicPlan.Target var1) {
      if (!this.onPlaneTop() || var1 == null || this.currentLayer == null || var1.pos().getY() != this.currentLayer) {
         return null;
      } else {
         Set<BlockPos> var2 = this.planeSupports();
         if (var2.isEmpty()) {
            return null;
         } else {
            List<BlockPos> var3 = null;
            double var4 = Double.MAX_VALUE;

            for (Direction var7 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
               BlockPos var8 = var1.pos().offset(var7);
               SchematicPlan.Target var9 = this.plan.at(var8);
               if (!var2.contains(var8)
                  && (var9 == null || var9.state().isAir() || var9.state().isReplaceable())
                  && this.mc.world.getBlockState(var8.up()).isReplaceable()
                  && this.mc.world.getBlockState(var8.up().up()).isReplaceable()) {
                  List<BlockPos> var10 = this.planeChain(var8, var2);
                  if (!var10.isEmpty()) {
                     double var11 = (double)var10.size() * 4.0 + this.horizontalDist(var10.getFirst());
                     if (var11 < var4) {
                        var4 = var11;
                        var3 = var10;
                     }
                  }
               }
            }

            if (var3 == null) {
               return null;
            } else {
               this.planeChainFor = var1.pos().toImmutable();
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] {} @ {}: trên mặt tầng không có chỗ đứng tới được → bắc cầu ngang {} khối tạm từ {} làm chỗ đứng (không xuống thấp)",
                     new Object[]{var1.state().getBlock().getName().getString(), var1.pos().toShortString(), var3.size(), var3.getFirst().toShortString()}
                  );
               return new BuildController.BuildPlan(var1, var3);
            }
         }
      }
   }

   /** Khối lơ lửng ở tầng hiện tại khi mình đang đứng trên mặt tầng → bắc cầu ngang tới nó thay vì xây cột từ sàn dưới. */
   private BuildController.BuildPlan planeBuild(SchematicPlan.Target var1) {
      if (!this.onPlaneTop() || var1 == null || var1.pos().getY() != this.currentLayer) {
         return null;
      } else {
         Set<BlockPos> var2 = this.planeSupports();
         if (var2.isEmpty()) {
            return null;
         } else {
            List<BlockPos> var3 = null;
            double var4 = Double.MAX_VALUE;
            // 3.0.82: chỉ bắc cầu tới phía mà khối này BÁM ĐƯỢC đúng hướng. Lỗi cũ: hopper hướng đông (phải click mặt tây
            // của khối phía đông) nhưng cầu tạm lại dừng ở phía nam → tới nơi không đặt được, khối tạm "còn cần" nên không đập,
            // mod cứ bước qua bước lại đòi đập khối tạm đó (vòng lặp vô tận trong video).
            int var12 = PlacementSolver.supportMask(this.mc, this.resolve(var1));

            for (Direction var7 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
               if (var12 != 0 && (var12 & 1 << var7.ordinal()) == 0) {
                  continue;
               }

               BlockPos var8 = var1.pos().offset(var7);
               if (var2.contains(var8)) {
                  return null;
               }

               List<BlockPos> var9 = this.planeChain(var8, var2);
               if (!var9.isEmpty()) {
                  double var10 = (double)var9.size() * 4.0 + this.horizontalDist(var9.getFirst());
                  if (var10 < var4) {
                     var4 = var10;
                     var3 = var9;
                  }
               }
            }

            if (var3 == null) {
               return null;
            } else {
               this.planeChainFor = var1.pos().toImmutable();
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] {} @ {}: đang đứng trên mặt tầng Y={} → bắc cầu ngang {} khối tạm từ {} tới sát nó (không xuống sàn dưới)",
                     new Object[]{
                        var1.state().getBlock().getName().getString(),
                        var1.pos().toShortString(),
                        this.currentLayer,
                        var3.size(),
                        var3.getFirst().toShortString()
                     }
                  );
               return new BuildController.BuildPlan(var1, var3);
            }
         }
      }
   }

   private BuildController.BuildPlan resolveBuild(SchematicPlan.Target var1) {
      // 3.0.84: ƯU TIÊN KHỐI THẬT. Trước đây khi đứng trên mặt tầng, khối lơ lửng (vd. lá) được bắc cầu khối tạm NGAY,
      // trong khi ô obsidian bên cạnh đặt được luôn (có chỗ bám) → giờ tìm chuỗi khối của công trình dẫn tới chỗ bám thật trước
      // (xây obsidian trước rồi lá bám vào obsidian), không có mới bắc cầu tạm.
      boolean var30 = this.plan != null && var1 != null && !this.hasSupport(var1.pos()) && this.onPlaneTop() && var1.pos().getY() == this.currentLayer;
      if (this.plan != null && var1 != null) {
         ArrayDeque var2 = new ArrayDeque();
         HashSet var3 = new HashSet();
         ArrayList var4 = new ArrayList();
         ArrayList var5 = new ArrayList();
         HashMap<BlockPos, Integer> var31 = new HashMap<>();
         var2.add(var1);
         var3.add(var1.pos());
         var31.put(var1.pos(), 0);
         int var6 = 0;

         while (!var2.isEmpty() && var6++ < 800) {
            SchematicPlan.Target var7 = (SchematicPlan.Target)var2.poll();
            int var8 = PlacementSolver.supportMask(this.mc, this.resolve(var7));
            int var32 = var31.getOrDefault(var7.pos(), 0);

            for (Direction var12 : DIR_ORDER) {
               if ((var8 & 1 << var12.ordinal()) != 0) {
                  BlockPos var13 = var7.pos().offset(var12);
                  if (this.mc.world.isChunkLoaded(var13)) {
                     if (this.solidAt(var13)) {
                        if (this.usableRoot(var7) && this.placeable(var7) && (var32 <= 24 || !var30)) {
                           if (var32 > 0) {
                              AutoBuildMod.LOGGER
                                 .info(
                                    "[AutoBuild] {} @ {} chưa có chỗ bám → xây trước khối thật {} @ {} (cách {} khối trong chuỗi), không bắc cầu tạm",
                                    new Object[]{
                                       var1.state().getBlock().getName().getString(),
                                       var1.pos().toShortString(),
                                       var7.state().getBlock().getName().getString(),
                                       var7.pos().toShortString(),
                                       var32
                                    }
                                 );
                           }

                           return new BuildController.BuildPlan(var7, List.of());
                        }
                     } else {
                        SchematicPlan.Target var14 = this.plan.at(var13);
                        if (var14 == null || var14.state().isAir() || var14.state().isReplaceable()) {
                           if (var5.size() < 32 && !var5.contains(var13) && this.usableRoot(var7)) {
                              var4.add(var7);
                              var5.add(var13.toImmutable());
                           }
                        } else if (var3.add(var13.toImmutable())) {
                           if (this.buildableNow(var14)) {
                              var31.put(var14.pos(), var32 + 1);
                              var2.add(var14);
                           } else if (this.blockedByMaterial(var14)) {
                              this.noteDepMissing(var1, var14);
                           }
                        }
                     }
                  }
               }
            }
         }

         if (var30) {
            BuildController.BuildPlan var33 = this.planeBuild(var1);
            if (var33 != null) {
               return var33;
            }
         }

         BuildController.BuildPlan var15 = null;
         double var16 = Double.MAX_VALUE;
         BlockPos var17 = this.lastPlaced != null ? this.lastPlaced : this.mc.player.getBlockPos();

         for (int var18 = 0; var18 < var5.size(); var18++) {
            if (var15 != null && var15.tempChain().size() <= 1) {
               // 3.0.99: đã có chỗ chỉ cần 1 khối kê bám vào khối có sẵn → đủ tốt, không tìm thêm (mỗi lần tìm tốn thời gian)
               break;
            }

            List var19 = this.rowChain((BlockPos)var5.get(var18));
            if (!var19.isEmpty()) {
               double var20 = (double)(
                  var19.size() * 4
                     + ((BlockPos)var5.get(var18)).getManhattanDistance(var17)
                     + (((BlockPos)var5.get(var18)).getY() != ((SchematicPlan.Target)var4.get(var18)).pos().getY() ? 2 : 0)
               );
               if (var20 < var16) {
                  var16 = var20;
                  var15 = new BuildController.BuildPlan((SchematicPlan.Target)var4.get(var18), var19);
               }
            }
         }

         return var15;
      } else {
         return null;
      }
   }

   private boolean usableRoot(SchematicPlan.Target var1) {
      return this.deferred.getOrDefault(var1.pos(), 0) <= this.ticks && !this.skipped.contains(var1.pos()) && !this.placedDone.contains(var1.pos());
   }

   // 3.0.99 (người dùng): xây theo MODULE — tạo điểm bám cho 1 khối của một cụm lơ lửng thì xây hết cả cụm (các khối liền nhau)
   // rồi mới sang cụm khác, không đi lung tung theo hàng.
   private final Set<BlockPos> moduleFocus = new HashSet<>();
   private int moduleSaidAt = -1000;

   private void focusModule(BlockPos var1) {
      if (this.plan != null && this.currentLayer != null && var1 != null && var1.getY() == this.currentLayer && !this.moduleFocus.contains(var1)) {
         this.moduleFocus.clear();
         ArrayDeque<BlockPos> var2 = new ArrayDeque<>();
         var2.add(var1.toImmutable());
         this.moduleFocus.add(var1.toImmutable());

         while (!var2.isEmpty() && this.moduleFocus.size() < 1500) {
            BlockPos var3 = var2.poll();

            for (Direction var5 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
               BlockPos var6 = var3.offset(var5).toImmutable();
               SchematicPlan.Target var7 = this.plan.at(var6);
               if (var7 != null
                  && !var7.state().isAir()
                  && !this.moduleFocus.contains(var6)
                  && this.mc.world.isChunkLoaded(var6)
                  && !this.done(this.mc.world.getBlockState(var6), var7.state())) {
                  this.moduleFocus.add(var6);
                  var2.add(var6);
               }
            }
         }

         if (this.moduleFocus.size() > 1 && this.ticks - this.moduleSaidAt > 100) {
            this.moduleSaidAt = this.ticks;
            AutoBuildMod.LOGGER.info("[AutoBuild] Cụm (module) {} khối liền nhau quanh {} → xây hết cụm này rồi mới sang chỗ khác", this.moduleFocus.size(), var1.toShortString());
         }
      }
   }

   /** khối cần đặt thuộc cụm đang làm (và cụm còn khối chưa xong) */
   private boolean inModule(BlockPos var1) {
      return !this.moduleFocus.isEmpty() && this.moduleFocus.contains(var1);
   }

   private void startBuildPlan(BuildController.BuildPlan var1, SchematicPlan.Target var2) {
      SchematicPlan.Target var3 = var1.root();
      this.focusModule(var3.pos());
      this.queue.remove(var3);
      this.deferred.remove(var3.pos());
      if (var2 != null && !var2.pos().equals(var3.pos())) {
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] {} @ {} chưa đặt đúng hướng được → xây trước {} @ {}{}",
               new Object[]{
                  var2.state().getBlock().getName().getString(),
                  var2.pos().toShortString(),
                  var3.state().getBlock().getName().getString(),
                  var3.pos().toShortString(),
                  var1.tempChain().isEmpty() ? "" : " (cần khối tạm tại " + var1.tempChain().getLast().toShortString() + ")"
               }
            );
      }

      if (!var1.tempChain().isEmpty() && !var3.pos().equals(this.planeChainFor)) {
         Navigation var4 = new Navigation(this.mc, this.config, this.plan, false, this.config.layerMode ? this.currentLayer : null);
         if (!this.standNear(var4, var3.pos())) {
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] {} @ {} chưa có chỗ đứng cạnh → chưa dựng khối tạm, để sau",
                  new Object[]{var3.state().getBlock().getName().getString(), var3.pos().toShortString()}
               );
            this.defer(var3, 200, "chưa có chỗ đứng cạnh để dựng khối tạm");
            this.nextTarget(true);
            return;
         }
      }

      this.target = this.resolve(var3);
      this.nextRetries = 0;
      if (var1.tempChain().isEmpty()) {
         this.placeOrGo();
      } else {
         if (!this.jobTemps.isEmpty()) {
            this.sweepNow.addAll(this.jobTemps);
            this.jobTemps.clear();
         }

         List var6 = var1.tempChain();
         this.jobTemps.addAll(var6);
         this.jobTempFor = var3.pos().toImmutable();
         this.jobSupport = true;
         this.supportQueue.clear();
         this.supportQueue.addAll(var6.subList(1, var6.size()));
         this.supportTarget = new SchematicPlan.Target((BlockPos)var6.getFirst(), this.pillarState((BlockPos)var6.getFirst()));
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] {} @ {} chưa có chỗ bám đúng hướng → đặt khối tạm tại {} ({} khối), đặt xong thì đập khối tạm",
               new Object[]{var3.state().getBlock().getName().getString(), var3.pos().toShortString(), ((BlockPos)var6.getLast()).toShortString(), var6.size()}
            );
         this.status = "Đặt khối tạm tại " + ((BlockPos)var6.getLast()).toShortString();
         PlacementSolver.Aim var5 = PlacementSolver.solve(
            this.mc, this.mc.player.getEyePos(), this.supportTarget, this.reach(), true, this.supportTarget.pos().asLong(), true
         );
         if (var5 != null && this.mc.player.isOnGround()) {
            this.aim = var5;
            this.setStage(BuildController.Stage.EQUIP);
            this.equip();
         } else {
            this.beginGoals(false);
         }
      }
   }

   private boolean resolveAny(List<SchematicPlan.Target> var1) {
      int var2 = 0;

      for (SchematicPlan.Target var4 : var1) {
         var2++;
         if (var2 > 40 || var2 > 3 && this.overBudget()) {
            break;
         }

         BuildController.BuildPlan var5 = this.resolveBuild(var4);
         if (var5 != null) {
            this.startBuildPlan(var5, var4);
            return true;
         }
      }

      return false;
   }

   private int neighbourSig(BlockPos var1, BlockState var2) {
      int var3 = System.identityHashCode(this.mc.world.getBlockState(var1)) * 31 + System.identityHashCode(var2);

      for (Direction var7 : Direction.values()) {
         var3 = var3 * 31 + System.identityHashCode(this.mc.world.getBlockState(var1.offset(var7)));
      }

      return var3;
   }

   private boolean placeableCached(BlockPos var1) {
      SchematicPlan.Target var2 = this.plan == null ? null : this.plan.at(var1);
      BuildController.PlaceKey var3 = this.placeCache.get(var1);
      return var2 != null && var3 != null && var3.sig() == this.neighbourSig(var1, var2.state());
   }

   private boolean placeable(SchematicPlan.Target var1) {
      SchematicPlan.Target var2 = this.resolve(var1);
      if (!this.mc.world.isChunkLoaded(var2.pos())) {
         return true;
      } else {
         int var3 = this.neighbourSig(var2.pos(), var2.state());
         BuildController.PlaceKey var4 = this.placeCache.get(var2.pos());
         if (var4 != null && var4.sig() == var3) {
            return var4.ok();
         } else {
            boolean var5 = PlacementSolver.placeableSomewhere(this.mc, var2);
            if (this.placeCache.size() > 20000) {
               this.placeCache.clear();
            }

            this.placeCache.put(var2.pos().toImmutable(), new BuildController.PlaceKey(var3, var5));
            return var5;
         }
      }
   }

   private boolean nearReachable(BlockPos var1) {
      Boolean var2 = this.nearCache.get(var1);
      if (var2 != null) {
         return var2;
      } else {
         double var3 = this.reach() + 0.5;
         double var5 = var3 * var3;
         Vec3d var7 = Vec3d.ofCenter(var1);
         boolean var8 = false;
         if (this.reachable.size() < 1200) {
            for (PathSearch.Cell var10 : this.reachable) {
               if (Navigation.eye(var10).squaredDistanceTo(var7) <= var5) {
                  var8 = true;
                  break;
               }
            }
         } else {
            for (int var13 = -5; var13 <= 5 && !var8; var13++) {
               for (int var14 = -5; var14 <= 5 && !var8; var14++) {
                  for (int var11 = -6; var11 <= 3; var11++) {
                     PathSearch.Cell var12 = new PathSearch.Cell(var1.getX() + var13, var1.getY() + var11, var1.getZ() + var14);
                     if (this.reachable.contains(var12) && Navigation.eye(var12).squaredDistanceTo(var7) <= var5) {
                        var8 = true;
                        break;
                     }
                  }
               }
            }
         }

         if (this.nearCache.size() > 20000) {
            this.nearCache.clear();
         }

         this.nearCache.put(var1.toImmutable(), var8);
         return var8;
      }
   }

   private SchematicPlan.Target findRoot(SchematicPlan.Target var1) {
      if (this.plan == null) {
         return null;
      } else {
         int[] var2 = new int[]{0};
         long var3 = Math.max(this.tickDeadline, System.nanoTime() + 2000000L);
         boolean var5 = this.reachableValid();
         this.rootTimedOut = false;
         PathSearch.Cell var6 = RootSearch.find(Navigation.cell(var1.pos()), var1x -> {
            SchematicPlan.Target var2x = this.plan.at(Navigation.pos(var1x));
            return var2x != null && this.buildableNow(var2x);
         }, var1x -> this.hasSupport(Navigation.pos(var1x)), var5x -> {
            if (++var2[0] > 400) {
               return false;
            } else if (System.nanoTime() > var3 && !this.placeableCached(Navigation.pos(var5x))) {
               this.rootTimedOut = true;
               return false;
            } else {
               return (!var5 || this.nearReachable(Navigation.pos(var5x))) && this.placeable(this.plan.at(Navigation.pos(var5x)));
            }
         }, 3000);
         return var6 == null ? null : this.plan.at(Navigation.pos(var6));
      }
   }

   private boolean buildableNow(SchematicPlan.Target var1) {
      BlockPos var2 = var1.pos();
      if (this.config.inYRange(var2.getY()) && this.mc.world.isChunkLoaded(var2)) {
         if (this.config.layerMode && this.currentLayer != null && var2.getY() > this.currentLayer) {
            return false;
         } else if (!this.placedDone.contains(var2) && !this.skipped.contains(var2)) {
            BlockState var3 = this.mc.world.getBlockState(var2);
            if (this.done(var3, var1.state()) || StateMatch.chestAwaitingPartner(var3, var1.state())) {
               return false;
            } else if (StateMatch.canAdd(var3, var1.state()) && !Materials.bucket(var3, var1.state()) && !Materials.flowing(var1.state())) {
               return !var1.state().isReplaceable() && !var1.state().getOutlineShape(this.mc.world, var2).isEmpty()
                  ? InventoryAccess.count(this.mc, this.materialFor(var1, var3)) > 0
                  : false;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean blockedByMaterial(SchematicPlan.Target var1) {
      BlockPos var2 = var1.pos();
      if (this.config.inYRange(var2.getY()) && this.mc.world.isChunkLoaded(var2)) {
         if (this.config.layerMode && this.currentLayer != null && var2.getY() > this.currentLayer) {
            return false;
         } else if (!this.placedDone.contains(var2) && !this.skipped.contains(var2)) {
            BlockState var3 = this.mc.world.getBlockState(var2);
            if (this.done(var3, var1.state()) || StateMatch.chestAwaitingPartner(var3, var1.state())) {
               return false;
            } else if (StateMatch.canAdd(var3, var1.state()) && !Materials.bucket(var3, var1.state()) && !Materials.flowing(var1.state())) {
               return !var1.state().isReplaceable() && !var1.state().getOutlineShape(this.mc.world, var2).isEmpty()
                  ? InventoryAccess.count(this.mc, this.materialFor(var1, var3)) <= 0
                  : false;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void noteDepMissing(SchematicPlan.Target var1, SchematicPlan.Target var2) {
      Item var3 = this.materialFor(var2, this.stateAt(var2.pos()));
      this.depMissing.merge(var3, 1, Integer::sum);
      this.depMissingFor.putIfAbsent(var1.pos().toImmutable(), var3);
   }

   private int layerNeedFor(Item var1) {
      for (BuildController.MaterialRow var3 : this.layerMaterials) {
         if (var3.item() == var1) {
            return Math.max(1, var3.needed() - var3.have());
         }
      }

      return 1;
   }

   private String depMissingLabel() {
      StringBuilder var1 = new StringBuilder();

      for (Entry var3 : this.depMissing.entrySet()) {
         if (var1.length() > 0) {
            var1.append(", ");
         }

         var1.append(((Item)var3.getKey()).getName().getString()).append(" x").append(this.layerNeedFor((Item)var3.getKey()));
         if (var1.length() > 70) {
            break;
         }
      }

      return var1.length() == 0 ? "vật liệu" : var1.toString();
   }

   private boolean collectDepMissing() {
      if (!this.depMissing.isEmpty() && this.plan != null && this.mc.player != null) {
         this.depMissing.keySet().removeIf(var1x -> InventoryAccess.count(this.mc, var1x) > 0);
         this.depMissingFor.values().removeIf(var1x -> !this.depMissing.containsKey(var1x));
         if (this.depMissing.isEmpty()) {
            this.depStallSince = -1;
            return false;
         } else {
            Item var1 = null;
            int var2 = 0;
            boolean var3 = false;

            for (Entry var5 : this.depMissing.entrySet()) {
               if (this.missing.getOrDefault(var5.getKey(), 0) > this.ticks || this.ticks - this.depOrdered.getOrDefault(var5.getKey(), -100000) < 400) {
                  var3 = true;
               } else if ((Integer)var5.getValue() > var2) {
                  var2 = (Integer)var5.getValue();
                  var1 = (Item)var5.getKey();
               }
            }

            boolean var6 = InventoryAccess.freeSlots(this.mc) > this.config.reserveFreeSlots;
            if (var1 == null || this.config.collectFromOrders && !var6) {
               this.status = (var6 ? "Thiếu khối phụ thuộc: " : "Túi đồ đầy, chưa lấy được: ") + this.depMissingLabel() + " — phải đặt trước khối khác.";
               if (this.depStallSince < 0) {
                  this.depStallSince = this.ticks;
               }

               if (!var3 && this.ticks - this.depStallSince > 18000) {
                  this.depStallSince = -1;
                  this.say(
                     "Vẫn đang chờ tại tầng Y="
                        + this.currentLayer
                        + " (không dừng): thiếu "
                        + this.depMissingLabel()
                        + ". Những khối này phải đặt trước vì khối khác cần click vào mặt bên của chúng; mod sẽ tự đi lấy lại khi Order có hàng."
                  );
               }

               return false;
            } else {
               int var7 = this.layerNeedFor(var1);
               if (this.ticks - this.depSaid.getOrDefault(var1, -100000) > 600) {
                  this.depSaid.put(var1, this.ticks);
                  this.say(
                     "Tầng Y="
                        + this.currentLayer
                        + " kẹt vì thiếu "
                        + var1.getName().getString()
                        + " (cần "
                        + var7
                        + "): khối này phải đặt trước thì khối bên cạnh mới có mặt để click."
                  );
               }

               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] Khối phụ thuộc thiếu vật liệu: {} (trong túi {}, cần {}) → đi lấy ở Order một lần, sau đó xây lại từ đầu",
                     new Object[]{var1.getName().getString(), InventoryAccess.count(this.mc, var1), var7}
                  );
               this.depStallSince = -1;
               this.depOrdered.put(var1, this.ticks);
               this.depMissing.clear();
               this.depMissingFor.clear();
               this.queue.clear();
               this.target = null;
               this.collect(var1, BuildController.Stage.SELECT, var7);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private boolean floorPlaceable(SchematicPlan.Target var1) {
      return (PlacementSolver.supportMask(this.mc, this.resolve(var1)) & 1 << Direction.DOWN.ordinal()) != 0;
   }

   private void wakeNeighbours(BlockPos var1) {
      this.depStallSince = -1;

      for (Direction var5 : Direction.values()) {
         BlockPos var6 = var1.offset(var5);
         if (this.deferred.remove(var6) != null) {
            this.deferCount.remove(var6);
            this.attempts.remove(var6);
            this.depMissingFor.remove(var6);
         }
      }
   }

   private boolean scaffoldAllowed() {
      if (!this.config.scaffold) {
         return false;
      } else {
         if (this.journal.placed.size() >= this.config.maxScaffoldBlocks) {
            this.journal.prune(this.mc);
         }

         if (this.journal.placed.size() >= this.config.maxScaffoldBlocks) {
            AutoBuildMod.LOGGER.warn("[AutoBuild] Sổ khối kê đầy ({} khối) — hãy dùng /autobuild cleanup", this.journal.placed.size());
            return false;
         } else {
            return true;
         }
      }
   }

   private List<BlockPos> supportChain(BlockPos var1) {
      if (!this.scaffoldAllowed()) {
         return List.of();
      } else {
         Integer var2 = this.config.layerMode ? this.currentLayer : null;
         Navigation var3 = new Navigation(this.mc, this.config, this.plan, true, var2);
         BlockState var4 = var3.scaffoldState();
         if (var4 != null && InventoryAccess.count(this.mc, var4.getBlock().asItem()) != 0) {
            List<PathSearch.Cell> var5 = ScaffoldSearch.find(Navigation.cell(var1), var2x -> {
               BlockPos var3x = Navigation.pos(var2x);
               return !this.occupies(var3x) && var3.canScaffoldAt(var3x);
            }, var1x -> this.hasSupport(Navigation.pos(var1x)), 12, 4096);
            ArrayList var6 = new ArrayList(var5.size());

            for (PathSearch.Cell var8 : var5) {
               var6.add(Navigation.pos(var8).toImmutable());
            }

            return var6;
         } else {
            return List.of();
         }
      }
   }

   private BlockPos supportSpot(BlockPos var1) {
      List var2 = this.supportChain(var1);
      if (var2.isEmpty()) {
         return null;
      } else {
         this.supportQueue.clear();
         this.supportQueue.addAll(var2.subList(1, var2.size()));
         return (BlockPos)var2.getFirst();
      }
   }

   private void watchExtras(SchematicPlan.Target var1) {
      ArrayList var2 = new ArrayList();

      for (int var3 = -1; var3 <= 1; var3++) {
         for (int var4 = -1; var4 <= 1; var4++) {
            for (int var5 = -1; var5 <= 1; var5++) {
               if (var3 != 0 || var4 != 0 || var5 != 0) {
                  BlockPos var6 = var1.pos().add(var3, var4, var5);
                  if (this.mc.world.getBlockState(var6).isReplaceable()) {
                     var2.add(var6.toImmutable());
                  }
               }
            }
         }
      }

      this.extraChecks
         .addLast(new BuildController.ExtraCheck(var1.pos().toImmutable(), var1.state().getBlock(), var2, this.ticks + 6 + this.latencyTicks() * 3));

      while (this.extraChecks.size() > 64) {
         this.extraChecks.pollFirst();
      }
   }

   private void checkExtras() {
      while (!this.extraChecks.isEmpty() && this.extraChecks.peekFirst().checkAt() <= this.ticks) {
         BuildController.ExtraCheck var1 = this.extraChecks.pollFirst();

         for (BlockPos var3 : var1.air()) {
            if (this.mc.world.isChunkLoaded(var3) && !this.ackWait(var3)) {
               BlockState var4 = this.mc.world.getBlockState(var3);
               if (!var4.isReplaceable()
                  && var4.getBlock() == var1.block()
                  && !this.journal.placed.containsKey(var3)
                  && !this.repairWrong.containsKey(var3)
                  && !this.recentPlaced.containsKey(var3)
                  && !this.jobTemps.contains(var3)
                  && !this.supportQueue.contains(var3)) {
                  SchematicPlan.Target var5 = this.plan == null ? null : this.plan.at(var3);
                  if (var5 == null || var5.state().getBlock() != var4.getBlock() || !StateMatch.placementMatches(var4, var5.state())) {
                     AutoBuildMod.LOGGER
                        .info(
                           "[AutoBuild] Đặt lố/sai (lag) {} tại {} (định đặt ở {}) → đập ngay",
                           new Object[]{var4.getBlock().getName().getString(), var3.toShortString(), var1.pos().toShortString()}
                        );
                     this.placedDone.remove(var3);
                     this.repairWrong.put(var3.toImmutable(), var4);
                  }
               }
            }
         }
      }
   }

   private void verifyPlacements() {
      this.checkExtras();

      while (!this.verify.isEmpty() && this.verify.peekFirst().checkAt() <= this.ticks) {
         BuildController.Placed var1 = this.verify.pollFirst();
         if (this.mc.world.isChunkLoaded(var1.pos())) {
            BlockState var2 = this.mc.world.getBlockState(var1.pos());
            boolean var3 = var2.getBlock() == var1.want().getBlock() && !StateMatch.placementMatches(var2, var1.want());
            if (var3 && (this.stage == BuildController.Stage.WAIT_BLOCK || this.menuStage() || this.repairing)) {
               this.verify.addLast(new BuildController.Placed(var1.pos(), var1.want(), this.ticks + 2));
               break;
            }

            if (var3 && this.startRepair(var1.pos(), var2)) {
               return;
            }

            if (!this.done(var2, var1.want()) && (var2.getBlock() != var1.want().getBlock() || StateMatch.canAdd(var2, var1.want()))) {
               if (StateMatch.placementMatches(var2, var1.want())) {
                  this.notePlacementVerified(var1.pos());
this.failures.remove(var1.pos());
                  this.spoofWorked(var1.pos());
                  this.confirmed();
                  this.notePlaceOk();
               } else if (var2.isReplaceable() || StateMatch.canAdd(var2, var1.want())) {
                  int var4 = this.failures.merge(var1.pos(), 1, Integer::sum);
                  this.notePlaceFail(var1.pos(), var1.want().getBlock().getName().getString() + " đặt rồi không hiện");
                  if (!this.running) {
                     return;
                  }

                  if (var2.isReplaceable()) {
                     this.noteLost();
                  }

                  boolean var54 = this.spoofPlaced.remove(var1.pos());
                  if (!var54) {
                     this.plainVanishAt = this.ticks;
                  }

                  if (var4 >= 2 && var54) {
                     this.banSpoof(var1.pos(), var1.want(), var4);
                  }

                  this.placedDone.remove(var1.pos());
                  this.cautiousUntil = this.ticks + this.qualityCautionTicks();
                  if (var4 >= 2 && var4 < 5 && this.mc.player != null) {
                     this.badStands.computeIfAbsent(var1.pos().toImmutable(), var0x -> new HashSet<>()).add(Navigation.cell(this.mc.player.getBlockPos()));
                     this.closerFor.add(var1.pos().toImmutable());
                  }

                  if (var4 < 5) {
                     AutoBuildMod.LOGGER
                        .info(
                           var4 >= 2
                              ? "[AutoBuild] {} @ {} đặt rồi nhưng không hiện (lần {}/5) → làm lại NGAY khối này, đổi sang chỗ đứng gần hơn"
                              : "[AutoBuild] {} @ {} đặt rồi nhưng không hiện (lần {}/5) → làm lại NGAY khối này",
                           new Object[]{var1.want().getBlock().getName().getString(), var1.pos().toShortString(), var4}
                        );
                     this.deferred.remove(var1.pos());
                     SchematicPlan.Target var5 = this.plan == null ? null : this.plan.at(var1.pos());
                     if (var5 != null) {
                        this.queue.remove(var5);
                        this.queue.addFirst(var5);
                        this.priorityPos = var5.pos().toImmutable();
                     }
                  } else {
                     if (var2.isReplaceable() && this.ghosts.put(var1.pos().toImmutable(), this.ticks) == null) {
                        Navigation.GHOSTS.add(var1.pos().toImmutable());
                     }

                     if (this.mc.player != null) {
                        PathSearch.Cell var53 = Navigation.cell(this.mc.player.getBlockPos());
                        this.badStands.computeIfAbsent(var1.pos().toImmutable(), var0x -> new HashSet<>()).add(var53);
                        this.leanFailed.add(var1.pos().toImmutable());
                        if (var4 == 5) {
                           AutoBuildMod.LOGGER
                              .info(
                                 "[AutoBuild] {} @ {} đặt 5 lần từ chỗ đứng {} vẫn không hiện → coi ô là GHOST BLOCK (không đi vào), đổi chỗ đứng khác để đặt",
                                 new Object[]{var1.want().getBlock().getName().getString(), var1.pos().toShortString(), var53}
                              );
                           this.say(
                              var1.want().getBlock().getName().getString()
                                 + " @ "
                                 + var1.pos().toShortString()
                                 + " đặt 5 lần vẫn không hiện → đổi chỗ đứng. Nếu vẫn mất, thử đặt tay 1 cái ở đây xem server có chặn không."
                           );
                        }
                     }

                     if (var4 >= 12 && !this.skipped.contains(var1.pos())) {
                        this.skipped.add(var1.pos().toImmutable());
                        AutoBuildMod.LOGGER
                           .info("[AutoBuild] {} mất sau khi đặt {} lần → tạm để cuối tầng, tầng chưa được coi là xong", var1.pos().toShortString(), var4);
                     } else {
                        this.deferred.put(var1.pos().toImmutable(), this.ticks + this.qualityRetryTicks());
                        AutoBuildMod.LOGGER
                           .info(
                              "[AutoBuild] {} ({}) mất sau khi đặt (lần {}), giờ ô là {} → đặt lại từ chỗ đứng khác sau khoảng chờ ngắn theo ping",
                              new Object[]{var1.pos().toShortString(), var1.want().getBlock().getName().getString(), var4, var2}
                           );
                     }
                  }
               } else if (this.journal.placed.containsKey(var1.pos()) || this.recentPlaced.containsKey(var1.pos())) {
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] {} tại {} đang là {} (mình đặt nhầm) → đập rồi đặt lại",
                        new Object[]{var1.want().getBlock().getName().getString(), var1.pos().toShortString(), var2.getBlock().getName().getString()}
                     );
                  this.journal.remove(var1.pos());
                  this.placedDone.remove(var1.pos());
                  this.repairWrong.put(var1.pos().toImmutable(), var2);
                  // 3.0.105: đặt ra khối KHÁC loại (vd. định đặt Cobblestone mà ra Oak Leaves) = đồ trên tay server lệch với client
                  // (log 02:55–02:58: 15 lần ngay sau khi mua xô ở /shop) → bắt server gửi lại túi đồ trước khi đặt tiếp
                  InventoryAccess.dumpTrace(this.mc, "Đặt ra " + var2.getBlock().getName().getString() + " thay vì " + var1.want().getBlock().getName().getString() + " @ " + var1.pos().toShortString());
                  InventoryAccess.forceResync(this.mc, "đặt ra " + var2.getBlock().getName().getString() + " thay vì " + var1.want().getBlock().getName().getString());
                  this.notePlaceFail(var1.pos(), "đặt ra " + var2.getBlock().getName().getString() + " thay vì " + var1.want().getBlock().getName().getString());
                  if (!this.running) {
                     return;
                  }
               } else if (this.config.fixWrong && this.breakableWrong(var2, var1.pos())) {
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] {} tại {} đang là {} (khối sai) → đập rồi đặt lại",
                        new Object[]{var1.want().getBlock().getName().getString(), var1.pos().toShortString(), var2.getBlock().getName().getString()}
                     );
                  this.placedDone.remove(var1.pos());
                  this.repairWrong.put(var1.pos().toImmutable(), var2);
               } else {
                  AutoBuildMod.LOGGER.info("[AutoBuild] {} tại {} thành {}, bỏ qua", new Object[]{var1.want(), var1.pos().toShortString(), var2});
                  this.markPlacedDone(this.placedDone, var1.pos());
               }
            } else {
               this.notePlacementVerified(var1.pos());
this.failures.remove(var1.pos());
               this.markPlacedDone(this.placedDone, var1.pos());
               this.spoofWorked(var1.pos());
               this.confirmed();
               this.notePlaceOk();
            }
         }
      }
   }

   /** 3.0.99: khối đặt bằng góc giả đã hiện đúng → góc giả đang được server chấp nhận */
   private void spoofWorked(BlockPos var1) {
      if (this.spoofPlaced.remove(var1)) {
         this.spoofOkAt = this.ticks;
         this.spoofOkCount++;
         this.noSpoof.remove(var1);
      }
   }

   /**
    * 3.0.99: khối đặt bằng góc giả bị mất. Chỉ cấm góc giả khi CHƯA có bằng chứng góc giả chạy được;
    * nếu vừa có khối góc giả khác hiện đúng, hoặc khối thường cũng đang mất (server lag) → không cấm.
    * Cấm cũng chỉ 1 phút rồi tự thử lại góc giả — không khối nào bị chặn vĩnh viễn.
    */
   private void banSpoof(BlockPos var1, BlockState var2, int var3) {
      boolean var4 = this.spoofOkCount >= 2 && this.ticks - this.spoofOkAt < 6000;
      boolean var5 = this.ticks - this.plainVanishAt < 400;
      if (!var4 && !var5 && var3 < 6) {
         this.noSpoof.put(var1.toImmutable(), this.ticks + 1200);
         this.lookUpCache.remove(var1);
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] {} @ {} đặt bằng GÓC GIẢ rồi biến mất → 1 phút tới thử đặt bằng góc thật, sau đó lại dùng góc giả",
               var2.getBlock().getName().getString(),
               var1.toShortString()
            );
      } else {
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] {} @ {} (góc giả) mất lần {} nhưng {} → vẫn dùng góc giả, đặt lại",
               new Object[]{
                  var2.getBlock().getName().getString(),
                  var1.toShortString(),
                  var3,
                  var5 ? "khối thường cũng đang mất (server lag)" : "góc giả vừa đặt được " + this.spoofOkCount + " khối khác"
               }
            );
      }
   }

   private boolean spoofBanned(BlockPos var1) {
      Integer var2 = this.noSpoof.get(var1);
      if (var2 == null) {
         return false;
      } else if (var2 <= this.ticks) {
         this.noSpoof.remove(var1);
         this.lookUpCache.remove(var1);
         return false;
      } else {
         return true;
      }
   }

    private boolean breakableWrong(BlockState actual, BlockPos pos) {
        if (actual == null || actual.isAir() || actual.getBlock() == Blocks.WATER
            || actual.getBlock() == Blocks.LAVA || mc.world == null || actual.getHardness(mc.world, pos) < 0) return false;
        // A waterlogged solid is still a solid which can need rebuilding.
        if (actual.hasBlockEntity() && mc.world.getBlockEntity(pos) instanceof Inventory contents)
            for (int slot = 0; slot < contents.size(); slot++) if (!contents.getStack(slot).isEmpty()) return false;
        return true;
    }



   private boolean wrongBlockAt(BlockPos var1, BlockState var2) {
      SchematicPlan.Target var3 = this.plan == null ? null : this.plan.at(var1);
      if (var3 != null && !var3.state().isAir() && var2 != null) {
         return var2.getBlock() == var3.state().getBlock() ? false : !this.done(var2, var3.state());
      } else {
         return false;
      }
   }

   private void heartbeat() {
      if (this.running
         && !this.cleanupMode
         && !this.menuStage()
         && this.ticks - this.lastProgress >= 100
         && this.ticks - this.beatAt >= 100) {
         this.beatAt = this.ticks;
         SchematicPlan.Target var1 = this.current();
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] [{}] {} | mục tiêu {} | đứng {} | v={} | queue {} | trượt={}",
               new Object[]{
                  this.stage,
                  this.status,
                  var1 == null ? "-" : var1.pos().toShortString() + " cách " + (int)Math.sqrt(this.mc.player.squaredDistanceTo(Vec3d.ofCenter(var1.pos()))),
                  this.mc.player.getBlockPos().toShortString(),
                  String.format("%.3f", Math.sqrt(this.mc.player.getVelocity().horizontalLengthSquared())),
                  this.queue.size(),
                  this.slipperyFloor()
               }
            );
      }
   }

   private void stallCheck() {
      if (this.running && !this.menuStage() && this.stage != BuildController.Stage.LOAD && !this.cleanupMode) {
         if (this.ticks - this.lastProgress >= 600 && this.ticks - this.stallSaid >= 600) {
            this.stallSaid = this.ticks;
            String var1 = this.status
               + " | bước "
               + this.stage
               + " | hàng đợi "
               + this.queue.size()
               + " | tầng Y="
               + this.currentLayer
               + " | đứng tại "
               + (this.mc.player == null ? "-" : this.mc.player.getBlockPos().toShortString())
               + (this.gates.isEmpty() ? "" : " | chặn: " + this.gates);
            AutoBuildMod.LOGGER.info("[AutoBuild] Đứng yên {} giây chưa đặt được khối nào — {}", (this.ticks - this.lastProgress) / 20, var1);
            this.say("Đứng yên " + (this.ticks - this.lastProgress) / 20 + "s: " + this.status);
         }
      } else {
         this.lastProgress = this.ticks;
      }
   }

    private void scanWrongNear() {
        if (!running || plan == null || !plan.loaded || mc.player == null || mc.world == null
            || journal == null || cleanupMode || menuStage() || stage == Stage.LOAD) return;
        if (qualityPlan != plan) { qualityPlan = plan; qualityIndex = 0; }
        // A bounded full pass also revisits old lower layers and saved false-done cells.
        long deadline = System.nanoTime() + 1_000_000L;
        int tested = 0;
        while (!plan.targets.isEmpty() && tested++ < 128 && System.nanoTime() < deadline) {
            if (qualityIndex >= plan.targets.size()) qualityIndex = 0;
            qualityObserve(plan.targets.get(qualityIndex++));
        }
        if (currentLayer != null && ticks - qualityLastNear >= 20) {
            qualityLastNear = ticks;
            BlockPos player = mc.player.getBlockPos();
            for (int y = -1; y <= 1; y++) for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                Target wanted = plan.at(new BlockPos(player.getX() + x, currentLayer + y, player.getZ() + z));
                if (wanted != null) qualityObserve(wanted);
            }
        }
    }



    private boolean walkToRepair() {
        if (bucketPlacementPriority() || !config.fixWrong || repairing || sweeping || cleanupMode || pending != null || menuStage()
            || mc.player == null || mc.world == null) return false;
        BlockPos best = null; double distance = Double.MAX_VALUE;
        for (var entry : new ArrayList<>(repairWrong.entrySet())) {
            BlockPos pos = entry.getKey();
            if (!mc.world.isChunkLoaded(pos) || obstacleWaiting(pos) || !qualityRepairReady(pos)
                || obstacles.contains(pos) && !exposed(pos)) continue;
            BlockState actual = mc.world.getBlockState(pos);
            if ((!qualityNeedsRepair(pos, actual) && !coverFor.containsKey(pos)) || !breakableWrong(actual, pos) || tntBreakRisk(pos) != null) continue;
            double d = mc.player.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (d < distance) { best = pos; distance = d; }
        }
        if (best == null) return false;
        beginQualityRepair(best);
        return true;
    }



   // ===== 3.0.79: xây chỗ trống trước, khối chặn (đá/đất tự nhiên) đào sau =====
   private final Set<BlockPos> everPlaced = new HashSet<>();
   private final Set<BlockPos> obstacles = new HashSet<>();
   private final Set<Integer> digStuckLayers = new HashSet<>();
   private final Set<Integer> loadedDigStuck = new HashSet<>();
   private final Map<Integer, Integer> digEmptyScans = new HashMap<>();
   private boolean digForce;
   private int digSaidAt = -1000;
   private static final Set<String> NATURAL = Set.of(
      "minecraft:stone", "minecraft:deepslate", "minecraft:tuff", "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
      "minecraft:calcite", "minecraft:dirt", "minecraft:grass_block", "minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:podzol",
      "minecraft:mycelium", "minecraft:gravel", "minecraft:sand", "minecraft:red_sand", "minecraft:sandstone", "minecraft:red_sandstone",
      "minecraft:clay", "minecraft:netherrack", "minecraft:dripstone_block", "minecraft:smooth_basalt", "minecraft:moss_block",
      "minecraft:mud", "minecraft:snow_block", "minecraft:terracotta", "minecraft:blackstone", "minecraft:basalt", "minecraft:soul_sand",
      "minecraft:soul_soil", "minecraft:end_stone", "minecraft:magma_block", "minecraft:obsidian", "minecraft:cobblestone",
      "minecraft:cobbled_deepslate", "minecraft:mossy_cobblestone", "minecraft:amethyst_block", "minecraft:budding_amethyst",
      "minecraft:pointed_dripstone", "minecraft:glow_lichen", "minecraft:sculk", "minecraft:infested_deepslate", "minecraft:infested_stone"
   );

   /** Khối do chính mod (hoặc lần xây trước) đặt / từng đúng → sai là phải sửa ngay. Ngược lại là đá, đất có sẵn chắn chỗ → để đào sau. */
   private boolean ours(BlockPos var1) {
      return this.everPlaced.contains(var1)
         || this.placedDone.contains(var1)
         || this.recentPlaced.containsKey(var1)
         || this.journal != null && this.journal.placed.containsKey(var1);
   }

   private static boolean naturalBlock(BlockState var0) {
      if (var0 == null) {
         return false;
      } else {
         String var1 = Registries.BLOCK.getId(var0.getBlock()).toString();
         return NATURAL.contains(var1) || var1.endsWith("_ore") || var1.endsWith("terracotta");
      }
   }

   private void noteWrong(BlockPos var1) {
      if (this.ours(var1)) {
         this.obstacles.remove(var1);
      } else {
         this.obstacles.add(var1.toImmutable());
      }
   }

   /** Còn khối đặt được ngay ở tầng này không (không tính khối đang hoãn / đã bỏ qua). Không còn → mới tới lượt đào khối chặn. */
   private boolean digPhase() {
      if (this.digForce) {
         return true;
      } else if (this.target != null
         && this.repairGoal == null
         && (this.stage == BuildController.Stage.AIM || this.stage == BuildController.Stage.EQUIP || this.stage == BuildController.Stage.WAIT_BLOCK)) {
         return false;
      } else {
         for (SchematicPlan.Target var2 : this.queue) {
            BlockPos var3 = var2.pos();
            if ((this.currentLayer == null || var3.getY() == this.currentLayer)
               && this.deferred.getOrDefault(var3, 0) <= this.ticks
               && !this.skipped.contains(var3)) {
               return false;
            }
         }

         return true;
      }
   }

   private boolean exposed(BlockPos var1) {
      for (Direction var5 : Direction.values()) {
         BlockPos var6 = var1.offset(var5);
         if (this.mc.world.getBlockState(var6).getCollisionShape(this.mc.world, var6).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private boolean obstacleWaiting(BlockPos var1) {
      return this.obstacles.contains(var1) && !this.digPhase();
   }

   /** Gọi khi tầng không còn khối nào đặt được: đào khối chặn gần nhất (lộ mặt ra ngoài) rồi đặt đúng khối vào đó. */
   private boolean digObstacle(String var1) {
      if (this.config.fixWrong && !this.cleanupMode && !this.repairing && !this.sweeping && this.mc.player != null) {
         boolean var2 = false;

         for (BlockPos var4 : this.repairWrong.keySet()) {
            if (this.repairTries.getOrDefault(var4, 0) <= 4) {
               var2 = true;
               break;
            }
         }

         if (!var2) {
            return false;
         } else {
            this.digForce = true;

            boolean var6;
            try {
               this.queue.clear();
               var6 = this.tryRepairNow() || this.walkToRepair();
            } finally {
               this.digForce = false;
            }

            if (var6 && this.ticks - this.digSaidAt > 200) {
               this.digSaidAt = this.ticks;
               AutoBuildMod.LOGGER.info("[AutoBuild] {} → giờ mới đào khối chặn (đá/đất có sẵn) trong công trình, đào xong đặt đúng khối", var1);
            }

            return var6;
         }
      } else {
         return false;
      }
   }

    private boolean repairAllowed() { return true; }



   private int massWrongCheckedAt = -100000;
   private int[] realignOffset;
   private String realignMsg;

   /** 3.0.98: dời placement Litematica theo độ lệch vừa dò được rồi đọc lại bản vẽ, không dừng. true = đã làm trong tick này */
   private boolean realignStep() {
      if (this.realignOffset == null) {
         return false;
      } else {
         int[] var1 = this.realignOffset;
         String var2 = this.realignMsg == null ? "" : this.realignMsg;
         this.realignOffset = null;
         this.realignMsg = null;

         try {
            fi.dy.masa.litematica.schematic.placement.SchematicPlacement var3 = this.plan == null ? null : this.plan.placement;
            if (var3 != null && !var3.isLocked()) {
               BlockPos var4 = var3.getOrigin();
               BlockPos var5 = var4.add(var1[0], var1[1], var1[2]);
               var3.setOrigin(var5, var0 -> AutoBuildMod.LOGGER.info("[AutoBuild] Litematica: {}", var0));
               this.say(var2 + " → placement bị lệch (" + var1[0] + ", " + var1[1] + ", " + var1[2] + ") so với phần đã xây: tự dời placement " + var4.toShortString() + " → " + var5.toShortString() + " cho khớp rồi xây tiếp.");
               this.reloadPlanRunning();
               return true;
            }
         } catch (Throwable var6) {
            AutoBuildMod.LOGGER.warn("[AutoBuild] Không dời được placement Litematica ({})", var6.toString());
         }

         this.massWrongConfirmed = true;
         return false;
      }
   }

   /** 3.0.98: đọc lại bản vẽ từ placement (vừa dời) mà KHÔNG dừng mod */
   private void reloadPlanRunning() {
      try {
         SchematicPlan var1 = new SchematicPlan();
         this.plan = var1;
         this.planWorld = this.mc.world;
         this.worldKey = ScaffoldJournal.worldKey(this.mc);
         this.currentLayer = null;
         this.buildFluidPhase = 0;
         this.placedDone.clear();
         this.repairWrong.clear();
         this.repairTries.clear();
         this.obstacles.clear();
         this.massWrongConfirmed = false;
         this.queue.clear();
         this.skipped.clear();
         this.skipReason.clear();
         this.deferred.clear();
         this.autoWater.clear();
         this.target = null;
         this.temporary = null;
         this.supportTarget = null;
         this.aim = null;
         this.pending = null;
         this.mining = null;
         this.sweeping = false;
         this.repairing = false;
         this.scan = null;
         this.release();
         this.setStage(BuildController.Stage.LOAD);
      } catch (Exception var2) {
         AutoBuildMod.LOGGER.warn("[AutoBuild] Không đọc lại được placement sau khi dời ({}) → xây tiếp theo bản vẽ cũ", var2.getMessage());
         this.massWrongConfirmed = true;
      }
   }

   private boolean massWrongDecide(int var1) {
      if (this.ticks - this.massWrongCheckedAt < 400) {
         return false;
      } else {
         this.massWrongCheckedAt = this.ticks;
         StringBuilder var2 = new StringBuilder();
         int var3 = 0;

         for (Entry<BlockPos, BlockState> var5 : this.repairWrong.entrySet()) {
            if (!this.obstacles.contains(var5.getKey()) || !naturalBlock(var5.getValue())) {
               SchematicPlan.Target var6 = this.plan == null ? null : this.plan.at(var5.getKey());
               String var7 = var5.getKey().toShortString()
                  + ": đang là "
                  + var5.getValue().getBlock().getName().getString()
                  + ", bản vẽ cần "
                  + (var6 == null ? "?" : var6.state().getBlock().getName().getString());
               if (var3 < 40) {
                  AutoBuildMod.LOGGER.info("[AutoBuild]   khối sai #{}: {}", var3 + 1, var7);
               }

               if (var3 < 3) {
                  var2.append(var2.length() == 0 ? "" : "; ").append(var7);
               }

               var3++;
            }
         }

         int[] var8 = this.alignScan();
         String var9 = "Phát hiện " + var1 + " khối sai cùng lúc (vd. " + var2 + ")";
         if (var8 == null) {
            this.massWrongConfirmed = true;
            this.say(var9 + " → sửa từng khối, xây tiếp.");
            return true;
         } else {
            int var10 = var8[0];
            int var11 = var8[1];
            int var12 = var8[2];
            int var13 = var8[3];
            int var14 = var8[4];
            int var15 = var8[5];
            int var16 = var8[6];
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Dò lệch placement: lệch tốt nhất ({}, {}, {}) khớp {}/{} khối, không lệch khớp {}/{} (ô đã có khối {})",
                  new Object[]{var10, var11, var12, var13, var15, var14, var15, var16}
               );
            boolean var17 = var10 != 0 || var11 != 0 || var12 != 0;
            if (var17 && var13 >= var14 + Math.max(20, var15 * 15 / 100) && var13 * 10 >= var16 * 6) {
               fi.dy.masa.litematica.schematic.placement.SchematicPlacement var18 = this.plan.placement;

               try {
                  if (var18 != null && !var18.isLocked()) {
                     // dời ở đầu tick sau (đang giữa bước xây, đổi bản vẽ ngay ở đây thì các bước sau trong tick này dùng nhầm bản vẽ)
                     this.realignOffset = new int[]{var10, var11, var12};
                     this.realignMsg = var9;
                     this.repairWrong.clear();
                     return false;
                  }
               } catch (Throwable var24) {
                  AutoBuildMod.LOGGER.warn("[AutoBuild] Không dời được placement Litematica ({})", var24.toString());
               }
            }

            if (var16 > 0 && var14 * 10 >= var16 * 7) {
               // bản vẽ khớp ≥ 70% phần đã xây → placement đúng, các khối kia sai thật (lag / bị đẩy / đặt nhầm) → sửa
               this.massWrongConfirmed = true;
               this.say(var9 + " → placement không lệch (khớp " + var14 * 100 / var16 + "% phần đã xây) → đập và đặt lại đúng, không dừng.");
               return true;
            } else {
               // không chắc (khớp ít mà cũng không tìm được độ lệch rõ) → KHÔNG đập hàng loạt; bỏ qua các khối sai này, xây tiếp phần khác
               int var21 = 0;

               for (Entry<BlockPos, BlockState> var23 : new ArrayList<>(this.repairWrong.entrySet())) {
                  if (!this.obstacles.contains(var23.getKey()) || !naturalBlock(var23.getValue())) {
                     this.skipped.add(var23.getKey().toImmutable());
                     this.skipReason.put(var23.getKey().toImmutable(), "Khối sai hàng loạt (" + var23.getValue().getBlock().getName().getString() + ") — không tự đập, kiểm tra tay");
                     this.repairWrong.remove(var23.getKey());
                     var21++;
                  }
               }

               this.say(var9 + " → chưa chắc placement đúng (khớp " + (var16 > 0 ? var14 * 100 / var16 : 0) + "% phần đã xây) → KHÔNG đập, bỏ qua " + var21 + " khối đó (xem tab Bị chặn), xây tiếp.");
               return false;
            }
         }
      }
   }

   /**
    * 3.0.98: so bản vẽ với phần đã xây ở các độ lệch (-3..3, -2..2, -3..3). Trả về {dx, dy, dz, số khớp tốt nhất, số khớp khi không lệch,
    * số ô đem so, số ô (không lệch) đang có khối}. null nếu không đủ dữ liệu.
    */
   private int[] alignScan() {
      if (this.plan != null && this.plan.loaded && this.mc.world != null) {
         int var1 = this.currentLayer != null ? this.currentLayer : this.plan.maxY;
         ArrayList<SchematicPlan.Target> var2 = new ArrayList<>();

         for (SchematicPlan.Target var4 : this.plan.targets) {
            if (var4.pos().getY() > var1) {
               break;
            }

            BlockState var5 = var4.state();
            if (this.config.inYRange(var4.pos().getY())
               && !var5.isAir()
               && var5.getFluidState().isEmpty()
               && this.mc.world.isChunkLoaded(var4.pos())) {
               var2.add(var4);
            }
         }

         if (var2.size() < 30) {
            return null;
         } else {
            int var6 = Math.max(1, var2.size() / 1500);
            int var7 = 0;
            int var8 = 0;
            int var9 = 0;
            int var10 = -1;
            int var11 = 0;
            int var12 = 0;
            int var13 = 0;

            for (int var14 = -2; var14 <= 2; var14++) {
               for (int var15 = -3; var15 <= 3; var15++) {
                  for (int var16 = -3; var16 <= 3; var16++) {
                     int var17 = 0;
                     int var18 = 0;
                     int var19 = 0;

                     for (int var20 = 0; var20 < var2.size(); var20 += var6) {
                        SchematicPlan.Target var21 = var2.get(var20);
                        BlockState var22 = this.mc.world.getBlockState(var21.pos().add(var15, var14, var16));
                        var18++;
                        if (var22.getBlock() == var21.state().getBlock()) {
                           var17++;
                        }

                        if (var15 == 0 && var14 == 0 && var16 == 0 && !var22.isAir() && var22.getFluidState().isEmpty()) {
                           var19++;
                        }
                     }

                     if (var15 == 0 && var14 == 0 && var16 == 0) {
                        var11 = var17;
                        var13 = var19;
                     }

                     var12 = var18;
                     if (var17 > var10 || var17 == var10 && var15 == 0 && var14 == 0 && var16 == 0) {
                        var10 = var17;
                        var7 = var15;
                        var8 = var14;
                        var9 = var16;
                     }
                  }
               }
            }

            return new int[]{var7, var8, var9, var10, var11, var12, var13};
         }
      } else {
         return null;
      }
   }

    private boolean tryRepairNow() {
        if (bucketPlacementPriority() || !config.fixWrong || repairing || sweeping || cleanupMode
            || mc.player == null || mc.world == null || pending != null || menuStage()) return false;
        for (var entry : new ArrayList<>(repairWrong.entrySet())) {
            BlockPos pos = entry.getKey();
            if (!mc.world.isChunkLoaded(pos) || obstacleWaiting(pos) || !qualityRepairReady(pos)) continue;
            BlockState actual = mc.world.getBlockState(pos);
            if (actual.isReplaceable() || !qualityNeedsRepair(pos, actual) && !coverFor.containsKey(pos)) {
                repairWrong.remove(pos); placedDone.remove(pos); continue;
            }
            if (!breakableWrong(actual, pos) || tntBreakRisk(pos) != null || !safeMiningStand(Navigation.cell(mc.player.getBlockPos()), pos)
                || mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > reach() * reach() || visiblePoint(pos) == null) continue;
            beginQualityRepair(pos);
            return true;
        }
        return false;
    }



    private boolean startRepair(BlockPos pos, BlockState actual) {
        if (cleanupMode || !qualityNeedsRepair(pos, actual) || !breakableWrong(actual, pos)) return false;
        placedDone.remove(pos);
        repairWrong.put(pos.toImmutable(), actual);
        return tryRepairNow() || walkToRepair();
    }



   private boolean allMissingOnCooldown() {
      boolean var1 = false;

      for (BuildController.MaterialRow var3 : this.layerMaterials) {
         if (var3.have() == 0) {
            var1 = true;
            if (this.missing.getOrDefault(var3.item(), 0) <= this.ticks) {
               return false;
            }
         }
      }

      return var1;
   }

   private int incompleteSaidAt = -100000;

   private void pauseLayerIncomplete(BuildController.Scan var1, Integer var2) {
      LayerPlanner.Stat var3 = var1.layers.get(var2);
      // 3.0.81: không dừng — chờ rồi tự đi Order lại khi hết thời gian chờ
      String var4 = "Tầng Y="
         + var2
         + " đã xây hết phần làm được. Còn "
         + (var3 == null ? 0 : var3.remaining)
         + " khối chờ "
         + this.missingLabel()
         + " (Order chưa có hàng) → tự thử lấy lại sau, không dừng.";
      this.status = var4;
      if (this.ticks - this.incompleteSaidAt > 6000) {
         this.incompleteSaidAt = this.ticks;
         this.say(var4);
      }

      this.setStage(BuildController.Stage.WAIT_MATERIAL);
   }

   private String missingLabel() {
      StringBuilder var1 = new StringBuilder();

      for (BuildController.MaterialRow var3 : this.layerMaterials) {
         if (var3.have() == 0) {
            if (var1.length() > 0) {
               var1.append(", ");
            }

            var1.append(var3.item().getName().getString());
            if (var1.length() > 60) {
               break;
            }
         }
      }

      return var1.length() == 0 ? "vật liệu" : var1.toString();
   }

   private long seed() {
      return (this.target == null ? 0L : this.target.pos().asLong()) * 31L + (long)this.aimAttempt;
   }

   private BlockPos selfPillarBase;

   private void planSelfPillar(List<BlockPos> var1) {
      this.selfPillarBase = null;
      if (var1 != null && var1.size() >= 2) {
         BlockPos var2 = var1.getFirst();

         for (int var3 = 1; var3 < var1.size(); var3++) {
            BlockPos var4 = var1.get(var3);
            if (var4.getX() != var2.getX() || var4.getZ() != var2.getZ() || var4.getY() != var2.getY() + var3) {
               return;
            }
         }

         if (this.solidAt(var2.down())
            && this.mc.world.getBlockState(var2).isReplaceable()
            && this.mc.world.getBlockState(var1.getLast().up()).isReplaceable()
            && this.mc.world.getBlockState(var1.getLast().up().up()).isReplaceable()) {
            this.selfPillarBase = var2.toImmutable();
            AutoBuildMod.LOGGER
               .info("[AutoBuild] Khối tạm dạng cột {} khối → đứng vào {} rồi tự nhảy kê lên (đập khối cũ dưới chân), không xây từ bên cạnh", var1.size(), var2.toShortString());
         }
      }
   }

   private void beginGoals(boolean var1) {
      // 3.0.92: đang ở dưới thấp (chủ động xuống xây / đập kê / lỡ ngã) mà khối cần làm ở tầng đang xây → /home lên ngay, không tìm đường/kê cột
      // 3.0.101: khối cần đặt đang trong tầm với (vd. vừa đặt hụt vì lag, đặt lại) → tìm chỗ đứng quanh đây, không /home đi xa
      if (!var1
         && this.currentLayer != null
         && this.current() != null
         && this.current().pos().getY() >= this.currentLayer - 1
         && this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(this.current().pos())) > (this.reach() + 1.5) * (this.reach() + 1.5)
         && this.tryHomeUp("Cần lên lại tầng đang xây để đặt " + this.current().state().getBlock().getName().getString())) {
         return;
      }

      this.nextRetries = 0;
      if (!var1 && this.target != null && this.current() == this.target) {
         this.lastGoalPos = this.target.pos().toImmutable();
         this.lastGoalAt = this.ticks;
      }

      Integer var2 = !var1 && this.config.layerMode && this.currentLayer != null ? this.currentLayer : null;
      if (this.selfPillarBase != null && (this.supportTarget == null || var1)) {
         this.selfPillarBase = null;
      }

      if (!var1 && this.selfPillarBase != null && this.current() == this.supportTarget
         && this.supportTarget.pos().getX() == this.selfPillarBase.getX() && this.supportTarget.pos().getZ() == this.selfPillarBase.getZ()) {
         BlockPos var40 = this.mc.player.getBlockPos();
         if (var40.getX() == this.selfPillarBase.getX() && var40.getZ() == this.selfPillarBase.getZ() && var40.getY() == this.supportTarget.pos().getY()) {
            this.aim = null;
            this.setStage(BuildController.Stage.EQUIP);
            return;
         }

         if (this.supportTarget.pos().equals(this.selfPillarBase)) {
            this.terrain = new Navigation(this.mc, this.config, this.plan, false, null);
            this.goals.clear();
            this.goals.add(Navigation.cell(this.selfPillarBase));
            List var41 = this.terrain.startCells();
            this.terrain.walkOnly = true;
            this.terrain.bound(var41, this.goals, 16);
            this.search = new PathSearch(var41, this.goals, this.terrain, 8000);
            this.scaffoldPass = true;
            this.status = "Đi vào chân cột để tự bắc lên";
            this.setStage(BuildController.Stage.SEARCH);
            return;
         }

         this.selfPillarBase = null;
      }

      this.terrain = new Navigation(this.mc, this.config, this.plan, !var1 && this.scaffoldAllowed(), var2);
      // 3.0.99: đang ở trên mặt tầng (chân ≥ Y tầng) → đường đi xuống thấp hơn rất đắt. Lỗi 15:59 / 16:51: đường tới khối xa
      // đi xuống Y=-37 rồi kẹt, "thấp hơn mặt tầng" → /home 2 → lại đi xuống → vòng lặp vô tận.
      boolean var42 = var2 != null && this.mc.player.getY() >= (double)var2 - 0.5;
      if (var42) {
         this.terrain.minFeetY = var2;
      }

      this.goals.clear();
      this.goalIndex = 0;
      this.scaffoldPass = false;
      this.walkGoalCount = 0;
      this.gStand = 0;
      this.gNoAim = 0;
      this.gNotRobust = 0;
      this.gNoEdge = 0;
      this.gFar = 0;
      this.gAngle = 0;
      BlockPos var3 = var1 ? this.mining : (this.current() != null ? this.current().pos() : this.repairGoal);
      if (var3 == null) {
         this.nextTarget(true);
      } else {
         this.floatingGoal = false;
         this.spoofGoals = !var1 && !this.floatingGoal && this.current() == this.target && this.target != null && this.spoofFor(this.target);
         this.approachOnly = !this.mc.world.isChunkLoaded(var3) || this.mc.player.squaredDistanceTo(Vec3d.ofCenter(var3)) > 2025.0;
         ArrayList var4 = new ArrayList();
         Vec3d var5 = this.mc.player.getPos();
         if (this.approachOnly) {
            Vec3d var22 = Vec3d.ofCenter(var3).subtract(var5);
            Vec3d var23 = var5.add(var22.normalize().multiply(Math.min(24.0, var22.length())));
            BlockPos var8 = BlockPos.ofFloored(var23);
            // 3.0.99: đang trên mặt tầng → điểm dừng giữa đường cũng phải ở trên mặt tầng (chân Y tầng hoặc Y tầng + 1)
            int var43 = var42 ? var2 - var8.getY() : -4;
            int var44 = var42 ? var2 + 1 - var8.getY() : 4;

            for (int var24 = -5; var24 <= 5; var24++) {
               for (int var26 = -5; var26 <= 5; var26++) {
                  for (int var28 = var43; var28 <= var44; var28++) {
                     PathSearch.Cell var30 = Navigation.cell(var8.add(var24, var28, var26));
                     var4.add(new BuildController.Candidate(var30, Navigation.feet(var30).squaredDistanceTo(var23), false));
                  }
               }
            }
         } else {
            Vec3d var6 = Vec3d.ofCenter(var3);
            double var7 = this.reach();
            ArrayList<Vec3d> var9 = new ArrayList<>();
            if (!var1) {
               for (SchematicPlan.Target var11 : this.queue) {
                  Vec3d var12 = Vec3d.ofCenter(var11.pos());
                  if (var12.squaredDistanceTo(var6) <= 49.0) {
                     var9.add(var12);
                  }

                  if (var9.size() >= 120) {
                     break;
                  }
               }
            }

            boolean var91WaterStandCheck = !var1 && this.waterPourTarget();

            for (int var25 = -5; var25 <= 5; var25++) {
               for (int var27 = -5; var27 <= 5; var27++) {
                  for (int var29 = -3; var29 <= 2; var29++) {
                     BlockPos var13 = var3.add(var25, var29, var27);
                     if (var1 || (!var13.equals(var3) || this.floatingGoal) && !var13.up().equals(var3)) {
                        PathSearch.Cell var14 = Navigation.cell(var13);
                        Vec3d var15 = Navigation.eye(var14);
                        if (!(var15.distanceTo(var6) > var7 + 0.4)) {
                           int var16 = 0;

                           for (Vec3d var18 : var9) {
                              if (var15.squaredDistanceTo(var18) <= (var7 - 0.3) * (var7 - 0.3)) {
                                 var16++;
                              }
                           }

                           double var31 = Math.hypot((double)var25, (double)var27);
                           boolean var90 = !var1 && var25 == 0 && var27 == 0 && var29 == 1 && this.waterPourTarget();
                           // 3.0.98: đổ nước thì ưu tiên chỗ đứng trên khối khô; đứng trong nước bị dòng chảy đẩy, không đứng yên
                           // được để đổ, ngồi ra mép cũng bị trôi (log 00:19–00:25) → phạt nặng chỗ đứng đang có nước / sắp đổ nước
                           double var91 = 0.0;
                           if (!var1 && var91WaterStandCheck) {
                              if (!this.mc.world.getBlockState(var13).getFluidState().isEmpty() || !this.mc.world.getBlockState(var13.down()).getFluidState().isEmpty()) {
                                 var91 = 14.0;
                              } else {
                                 SchematicPlan.Target var92 = this.plan == null ? null : this.plan.at(var13);
                                 if (var92 != null && var92.state().isOf(Blocks.WATER)) {
                                    var91 = 6.0;
                                 }
                              }
                           }
                           boolean var19 = !var1 && var29 == 1 && var31 <= 1.01 && this.solidAt(var13.down());
                           double var20 = (double)(-var16) * 1.2
                              + (double)(var19 ? -10 : (var29 < 0 ? 3 : 0))
                              + Math.abs(var31 - (this.bucketCloser && this.waterPourTarget() || !var1 && (this.leanFailed.contains(var3) || this.closerFor.contains(var3)) ? 1.0 : 2.8))
                                 * (!var1 && this.closerFor.contains(var3) ? 4.0 : 1.5)
                              + (!var1 && this.closerFor.contains(var3) ? Navigation.eye(var14).distanceTo(var6) * 2.0 : 0.0)
                              + Math.sqrt(Navigation.feet(var14).squaredDistanceTo(var5)) * 0.35
                              + (double)Math.abs(var29) * 0.6
                              + (var90 ? -40.0 : 0.0)
                              + var91
                              + this.random.nextDouble() * 0.4;
                           var4.add(new BuildController.Candidate(var14, var20, false));
                        }
                     }
                  }
               }
            }
         }

         var4.sort(Comparator.comparingDouble(BuildController.Candidate::score));
         this.ranked = var4;
         this.setStage(BuildController.Stage.GOALS);
         this.status = "Tìm chỗ đứng và mặt đặt phù hợp";
      }
   }

   private boolean solidAt(BlockPos var1) {
      BlockState var2 = this.mc.world.getBlockState(var1);
      return !var2.isReplaceable() && !var2.getOutlineShape(this.mc.world, var1).isEmpty();
   }

   private boolean diggable(BlockPos var1) {
      BlockState var2 = this.mc.world.getBlockState(var1);
      if (var2.isAir() || !var2.getFluidState().isEmpty() || this.plan.at(var1) != null || this.journal.placed.containsKey(var1)) {
         return false;
      } else if (!var2.hasBlockEntity() && !(var2.getBlock() instanceof FallingBlock)) {
         float var3 = var2.getHardness(this.mc.world, var1);
         if (!(var3 < 0.0F) && !(var3 > 3.0F)) {
            if (!var2.isToolRequired()) {
               return true;
            } else {
               for (int var4 = 0; var4 < 36; var4++) {
                  if (this.mc.player.getInventory().getStack(var4).isSuitableFor(var2)) {
                     return true;
                  }
               }

               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private BuildController.PitPlan planPit(SchematicPlan.Target var1) {
      BlockPos var2 = var1.pos();

      for (Direction var4 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
         boolean var5 = this.solidAt(var2.up());

         for (Direction var7 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            if (var7.getAxis() != var4.getAxis() && this.solidAt(var2.offset(var7))) {
               var5 = true;
            }
         }

         if (var5) {
            BlockPos var13 = var2.offset(var4);
            if (this.mc.world.getBlockState(var13).isReplaceable()
               && this.mc.world.getBlockState(var13.up()).isReplaceable()
               && this.mc.world.getBlockState(var13).getFluidState().isEmpty()
               && this.diggable(var13.down())
               && this.diggable(var13.down(2))
               && this.solidAt(var13.down(3))) {
               boolean var14 = false;

               for (int var8 = 1; var8 <= 2 && !var14; var8++) {
                  for (Direction var12 : Direction.values()) {
                     if (!this.mc.world.getBlockState(var13.down(var8).offset(var12)).getFluidState().isEmpty()) {
                        var14 = true;
                     }
                  }
               }

               if (!var14) {
                  return new BuildController.PitPlan(var1, var13.toImmutable());
               }
            }
         }
      }

      return null;
   }

   private boolean centerOn(BlockPos var1) {
      double var2 = (double)var1.getX() + 0.5 - this.mc.player.getX();
      double var4 = (double)var1.getZ() + 0.5 - this.mc.player.getZ();
      if (var2 * var2 + var4 * var4 < 0.0225) {
         return true;
      } else {
         float var6 = MotionMath.wrap(MotionMath.yaw(var2, var4) - this.mc.player.getYaw());
         if (Math.abs(var6) < 67.5F) {
            Input.key(this.mc.options.forwardKey, true);
         }

         if (Math.abs(var6) > 112.5F) {
            Input.key(this.mc.options.backKey, true);
         }

         if (var6 > 22.5F && var6 < 157.5F) {
            Input.key(this.mc.options.rightKey, true);
         }

         if (var6 < -22.5F && var6 > -157.5F) {
            Input.key(this.mc.options.leftKey, true);
         }

         return false;
      }
   }

   private void pitStep(int var1) {
      this.pitStep = var1;
      this.pitStepTicks = 0;
      this.aim = null;
   }

   private void pitAbort(String var1) {
      AutoBuildMod.LOGGER.info("[AutoBuild] Hố tại {}: {}", this.pit.column().toShortString(), var1);
      this.pitFailed = true;
      this.mc.interactionManager.cancelBlockBreaking();
      this.pitStep(4);
   }

   private void pitTick() {
      if (this.pit == null) {
         this.beginScan();
      } else {
         BlockPos var1 = this.pit.column();
         int var2 = var1.getY();
         this.pitTicks++;
         this.pitStepTicks++;
         if (this.pitTicks > 900 && this.pitStep < 4) {
            this.pitAbort("quá lâu");
         } else {
            SchematicPlan.Target var3 = this.pit.target();
            switch (this.pitStep) {
               case 0:
               case 1:
                  BlockPos var4 = var1.down(this.pitStep + 1);
                  this.status = "Đào hố để đặt " + var3.state().getBlock().getName().getString() + " (" + (this.pitStep + 1) + "/2)";
                  if (this.mc.world.getBlockState(var4).isReplaceable()) {
                     if (this.ackWait(var4)) {
                        return;
                     }

                     if (this.mc.player.isOnGround() && this.mc.player.getY() < (double)var4.getY() + 0.5) {
                        this.pitStep(this.pitStep + 1);
                     } else if (this.mc.player.isOnGround()) {
                        this.centerOn(var1);
                     }

                     return;
                  }

                  if (!this.centerOn(var1)) {
                     return;
                  }

                  if (!this.diggable(var4)) {
                     this.pitAbort("khối không đào được");
                     return;
                  }

                  this.mining = var4;
                  this.breakBlock(this.mc.world.getBlockState(var4));
                  if (this.pitStepTicks > 200) {
                     this.pitAbort("đào quá lâu");
                  }
                  break;
               case 2:
                  this.status = "Dưới hố: nhìn lên đặt " + var3.state().getBlock().getName().getString();
                  Input.sneak(this.mc, true, this.ticks);
                  if (this.done(this.mc.world.getBlockState(var3.pos()), var3.state())) {
                     this.pitStep(4);
                     return;
                  }

                  Item var5 = Materials.item(this.mc.world.getBlockState(var3.pos()), var3.state());
                  if (InventoryAccess.count(this.mc, var5) == 0) {
                     this.pitAbort("hết " + var5.getName().getString());
                     return;
                  }

                  if (!this.mc.player.getMainHandStack().isOf(var5)) {
                     InventoryAccess.equip(this.mc, var5, this.config.workingHotbarSlot);
                     return;
                  }

                  if (!this.mc.player.isInSneakingPose() || !this.mc.player.isOnGround()) {
                     return;
                  }

                  if (this.aim == null) {
                     this.aim = PlacementSolver.solve(this.mc, this.mc.player.getEyePos(), var3, this.reach(), true, var3.pos().asLong(), true);
                  }

                  if (this.aim == null) {
                     if (this.pitStepTicks > 40) {
                        this.pitAbort("dưới hố vẫn không có góc nhìn lên hợp lệ");
                     }

                     return;
                  }

                  if (this.sendPlace(var3, PlacementSolver.aimYaw(this.mc, this.aim), PlacementSolver.aimPitch(this.mc, this.aim))) {
                     this.pitStep(3);
                  } else if (this.pitStepTicks > 60) {
                     this.pitAbort("không click được");
                  }
                  break;
               case 3:
                  Input.sneak(this.mc, true, this.ticks);
                  BlockState var6 = this.mc.world.getBlockState(var3.pos());
                  if (this.done(var6, var3.state())) {
                     this.markPlacedDone(this.placedDone, var3.pos());
                     this.placedCount++;
                     this.lastPlaced = var3.pos();
                     this.attempts.remove(var3.pos());
                     this.verify.addLast(new BuildController.Placed(var3.pos(), var3.state(), this.ticks + 4 + this.latencyTicks() * 2));
                     this.pitStep(4);
                     return;
                  }

                  if (this.pitStepTicks > 6 + this.latencyTicks() * 2) {
                     if (++this.pitRetries > 2) {
                        this.pitAbort("server không nhận");
                     } else {
                        this.pitStep(2);
                     }
                  }
                  break;
               case 4:
               case 5:
                  BlockPos var7 = var1.down(this.pitStep == 4 ? 2 : 1);
                  this.status = "Lấp hố (" + (this.pitStep - 3) + "/2)";
                  if (this.solidAt(var7)) {
                     this.pitStep(this.pitStep + 1);
                     return;
                  }

                  if (this.pitStep == 4 && this.mc.player.getY() > (double)var7.getY() + 1.5 && this.mc.player.isOnGround()) {
                     this.pitStep(5);
                     return;
                  }

                  BlockState var8 = new Navigation(this.mc, this.config, this.plan, true).scaffoldState();
                  if (var8 == null) {
                     this.finishPit("không có khối để lấp hố");
                     return;
                  }

                  Item var9 = var8.getBlock().asItem();
                  if (InventoryAccess.count(this.mc, var9) == 0) {
                     this.finishPit("hết " + var9.getName().getString() + " để lấp hố");
                     return;
                  }

                  if (!this.mc.player.getMainHandStack().isOf(var9)) {
                     InventoryAccess.equip(this.mc, var9, this.config.workingHotbarSlot);
                     return;
                  }

                  // 3.0.81: lấp hố bằng đúng kiểu nhảy kê của cột (nhìn thẳng xuống 90°, nhảy, chân vượt mặt ô thì
                  // click mặt trên khối dưới chân). Bản cũ nhảy liên tục rồi mới giải góc giữa không trung → không kịp đặt.
                  if (this.pitStepTicks > 240) {
                     this.finishPit("không lấp được hố");
                     return;
                  }

                  Input.sneak(this.mc, true, this.ticks);
                  if (this.mc.player.isOnGround()) {
                     if (this.pitJumped && this.ticks - this.pitJumpAt < 8) {
                        return;
                     }

                     this.pitJumped = false;
                     if (!this.centerOn(var1)) {
                        return;
                     }

                     if (this.mc.player.getVelocity().horizontalLengthSquared() > 4.0E-4) {
                        this.brake();
                        return;
                     }

                     this.lookAt(this.mc.player.getYaw(), 90.0F);
                     if (this.rotationSent() && this.paceOk() && !this.ackWait(var7.down()) && this.mc.player.isInSneakingPose()) {
                        this.pitJumped = true;
                        this.pitJumpAt = this.ticks;
                        Input.key(this.mc.options.jumpKey, true);
                     }
                  } else {
                     this.lookAt(this.mc.player.getYaw(), 90.0F);
                     if (PillarGeometry.clearToPlace(this.mc.player.getBoundingBox(), var7) && this.rotationSent() && this.cooldown <= 0) {
                        BlockHitResult var12 = this.pillarTopHit(var7);
                        if (var12 != null) {
                           this.clickTick = this.ticks;
                           this.placeNow(var12);
                           this.clicked(var7);
                           this.cooldown = 2;
                           AutoBuildMod.LOGGER.info("[AutoBuild] Lấp hố: kê {} trong nhịp nhảy, chân Y={}", var7.toShortString(), String.format("%.2f", this.mc.player.getY()));
                        }
                     }
                  }
                  break;
               default:
                  this.finishPit(null);
            }
         }
      }
   }

   private boolean pitJumped;
   private int pitJumpAt;

   private void finishPit(String var1) {
      BuildController.PitPlan var2 = this.pit;
      this.pit = null;
      this.aim = null;
      this.mining = null;
      if (var1 != null) {
         this.say("Hố tại " + var2.column().toShortString() + ": " + var1 + " — hãy lấp tay.");
      }

      if (this.pitFailed) {
         this.deferred.put(var2.target().pos(), this.ticks + 200);
         if (!this.attempt(var2.target())) {
            this.skipReason.put(var2.target().pos(), "Cần nhìn lên: đào hố không đặt được (đặt tay)");
         }
      }

      this.pitFailed = false;
      this.pitRetries = 0;
      this.nextTarget(true);
   }

   private boolean sendPlace(SchematicPlan.Target var1, float var2, float var3) {
      if (this.mc.currentScreen == null
         && this.aim != null
         && InventoryAccess.synced(this.mc)
         && !Materials.bucket(this.mc.world.getBlockState(var1.pos()), var1.state())
         && this.mc.player.getMainHandStack().isOf(Materials.item(this.mc.world.getBlockState(var1.pos()), var1.state()))) {
         this.lookAt(var2, var3);
         if (!PlacementSolver.validNow(this.mc, this.aim, var1, this.reach())) {
            return false;
         } else {
            float[] var4 = new float[]{this.mc.player.getYaw(), this.yawHistory[0], this.yawHistory[1], this.yawHistory[2]};
            if (!PlacementSolver.serverSafe(this.mc, this.aim, var1, this.reach(), var4)) {
               return false;
            } else if (this.rotationSent() && this.paceOk()) {
               BlockHitResult var5 = PlacementSolver.ray(this.mc, this.reach());
               if (!Input.crosshairOn(this.mc, var5.getBlockPos(), var5.getSide())) {
                  return false;
               } else {
                  this.clickTick = this.ticks;
                  if (!this.placeNow(var5)) {
                     return false;
                  }

                  this.clicked(var1.pos());
                  return true;
               }
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private boolean hasLowerStand(BlockPos var1) {
      for (int var2 = -3; var2 <= 3; var2++) {
         for (int var3 = -3; var3 <= 3; var3++) {
            for (int var4 = -4; var4 <= -1; var4++) {
               PathSearch.Cell var5 = Navigation.cell(var1.add(var2, var4, var3));
               if (this.terrain != null && this.terrain.standable(var5)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private Vec3d edgeSpot(BlockPos var1) {
      BlockPos var2 = this.mc.player.getBlockPos();
      Vec3d var3 = null;
      double var4 = Double.MAX_VALUE;

      for (Direction var7 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
         BlockPos var8 = var1.offset(var7);
         Direction var9 = var7.getOpposite();
         if (this.solidAt(var8) && !Navigation.hazard(this.mc.world.getBlockState(var8)) && this.terrain_clear(var8.up())) {
            Vec3d var10 = new Vec3d(
               (double)var8.getX() + 0.5 + (double)var9.getOffsetX() * 0.78,
               (double)(var8.getY() + 1),
               (double)var8.getZ() + 0.5 + (double)var9.getOffsetZ() * 0.78
            );
            boolean var11 = var2.equals(var8.up()) || var2.equals(var1.up());
            double var12 = var10.squaredDistanceTo(this.mc.player.getPos());
            if (var11 && !(var12 > 2.5) && var12 < var4) {
               var4 = var12;
               var3 = var10;
            }
         }

         BlockPos var18 = var8.down();
         if (this.solidAt(var18) && !Navigation.hazard(this.mc.world.getBlockState(var18)) && this.terrain_clear(var8)) {
            Vec3d var19 = new Vec3d(
               (double)var8.getX() + 0.5 + (double)var9.getOffsetX() * 0.78, (double)var8.getY(), (double)var8.getZ() + 0.5 + (double)var9.getOffsetZ() * 0.78
            );
            boolean var21 = var2.equals(var8) || var2.equals(var1);
            double var13 = var19.squaredDistanceTo(this.mc.player.getPos());
            if (var21 && !(var13 > 2.5) && var13 < var4) {
               var4 = var13;
               var3 = var19;
            }
         }
      }

      if (var3 == null && this.mc.player.isOnGround()) {
         Vec3d var15 = Navigation.feet(Navigation.cell(var2));
         double var16 = (double)var1.getX() + 0.5 - var15.x;
         double var17 = (double)var1.getZ() + 0.5 - var15.z;
         if (var16 * var16 + var17 * var17 > 0.04) {
            double var20 = 0.0;
            double var22 = 0.0;
            if (Math.abs(var16) >= Math.abs(var17)) {
               var20 = Math.signum(var16) * Math.min(0.78, Math.abs(var16));
            } else {
               var22 = Math.signum(var17) * Math.min(0.78, Math.abs(var17));
            }

            var3 = new Vec3d(var15.x + var20, var15.y, var15.z + var22);
         }
      }

      return var3;
   }

   private boolean terrain_clear(BlockPos var1) {
      return this.mc.world.getBlockState(var1).getCollisionShape(this.mc.world, var1).isEmpty()
         && this.mc.world.getBlockState(var1.up()).getCollisionShape(this.mc.world, var1.up()).isEmpty();
   }

   /** 3.0.95: khối cao hơn 1 ô khi va chạm (hàng rào, cổng rào, tường cao 1,5 ô) */
   static boolean tallCollision(BlockState var0) {
      Identifier var1 = Registries.BLOCK.getId(var0.getBlock());
      String var2 = var1 == null ? "" : var1.getPath();
      return var2.endsWith("_fence") || var2.endsWith("_fence_gate") || var2.endsWith("_wall");
   }

   /**
    * 3.0.95: hàng rào/tường cao 1,5 ô → ngồi chìa người ra mép thì thân người đè lên ô đặt (hộp va chạm của hàng rào lên tới
    * y+1,5 cao hơn chân) → server/client không cho đặt (log 18:28:25: "Ngồi ra sát mép để đặt: Spruce Fence" rồi "Quá lâu chưa đặt được").
    * Chỉ được chìa mép khi chân cao hơn đỉnh hộp va chạm của khối đó.
    */
   private boolean leanBlocked(Vec3d var0, BlockPos var1) {
      if (this.plan == null) {
         return false;
      } else {
         SchematicPlan.Target var2 = this.plan.at(var1);
         return var2 != null && tallCollision(var2.state()) && var1.getY() + 1.5 > var0.y + 0.001;
      }
   }

   private Vec3d edgeEye(Vec3d var0, BlockPos var1) {
      if (this.leanBlocked(var0, var1)) {
         return var0.add(0.0, 1.62, 0.0);
      }

      double var2 = (double)var1.getX() + 0.5 - var0.x;
      double var4 = (double)var1.getZ() + 0.5 - var0.z;
      double var6 = 0.0;
      double var8 = 0.0;
      if (Math.abs(var2) >= Math.abs(var4)) {
         var6 = Math.signum(var2) * Math.min(0.78, Math.abs(var2));
      } else {
         var8 = Math.signum(var4) * Math.min(0.78, Math.abs(var4));
      }

      return var0.add(var6, 1.62, var8);
   }

   private boolean unblockAbove(SchematicPlan.Target var1) {
      if (this.config.fixWrong && var1 != null && this.plan != null && this.currentLayer != null && !this.cleanupMode && this.digPhase()) {
         BlockPos var2 = var1.pos().up();
         if (var2.getY() > this.currentLayer && this.mc.world.isChunkLoaded(var2)) {
            BlockState var3 = this.mc.world.getBlockState(var2);
            if (var3.isReplaceable() || this.repairWrong.containsKey(var2) || this.repairTries.getOrDefault(var2, 0) > 4) {
               return false;
            } else if (!this.breakableWrong(var3, var2)) {
               return false;
            } else {
               this.placedDone.remove(var2);
               this.repairWrong.put(var2.toImmutable(), var3);
               this.coverFor.put(var2.toImmutable(), var1.pos().toImmutable());
               this.skipped.remove(var1.pos());
               this.skipReason.remove(var1.pos());
               this.attempts.remove(var1.pos());
               this.deferred.remove(var1.pos());
               this.deferCount.remove(var1.pos());
               this.navFailures.remove(var1.pos());
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] {} @ {} bị khối {} của tầng trên ({}) đè lên → phá khối đó ra rồi đặt",
                     new Object[]{
                        var1.state().getBlock().getName().getString(), var1.pos().toShortString(), var3.getBlock().getName().getString(), var2.toShortString()
                     }
                  );
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void navigationFailed(String var1) {
      this.waitingReason = var1;
      AutoBuildMod.LOGGER
         .info(
            "[AutoBuild] Bỏ qua tạm {}: {}",
            this.target == null ? "(đập khối) " + this.goalLabel() : this.target.state().getBlock().getName().getString() + " @ " + this.target.pos().toShortString(),
            var1
         );
      if (this.target != null && var1.startsWith("Chưa có chỗ đứng") && this.unblockAbove(this.target)) {
         this.nextTarget(true);
      } else if (this.cleanupMode) {
         if (this.mining != null) {
            this.cleanupDeferred.add(this.mining);
         }

         this.setStage(BuildController.Stage.CLEANUP);
      } else {
         if (this.target != null) {
            int var2 = this.navFailures.merge(this.target.pos(), 1, Integer::sum);
            this.attempt(this.target);
            this.deferred.put(this.target.pos(), this.ticks + Math.min(400, 40 * var2));
         }

         this.nextTarget(true);
      }
   }

   private int hazardSaidAt = -1000;
   private BlockPos trimPos;
   private Direction trimDir;
   private int trimTicks;
   private final Set<BlockPos> trimSkip = new HashSet<>();

   private final Map<BlockPos, Integer> ghosts = new HashMap<>();
   private int ghostSaidAt = -1000;

   private void ghostTick() {
      if (!Navigation.AVOID.isEmpty() && this.ticks - this.avoidSince > 600) {
         Navigation.AVOID.clear();
      }

      if (this.mc.player != null && this.mc.world != null && !this.ghosts.isEmpty()) {
         Iterator<Entry<BlockPos, Integer>> var1 = this.ghosts.entrySet().iterator();

         while (var1.hasNext()) {
            Entry<BlockPos, Integer> var2 = var1.next();
            BlockPos var3 = var2.getKey();
            if (!this.mc.world.getBlockState(var3).isReplaceable() || this.ticks - var2.getValue() > 1200) {
               var1.remove();
               Navigation.GHOSTS.remove(var3);
            }
         }

      }
   }

   private static final Set<String> FOODS = Set.of(
      "cooked_beef", "cooked_porkchop", "golden_carrot", "bread", "baked_potato", "cooked_chicken", "cooked_mutton", "cooked_salmon", "cooked_cod",
      "cooked_rabbit", "pumpkin_pie", "apple", "carrot", "melon_slice", "sweet_berries", "cookie", "beetroot", "potato", "dried_kelp", "mushroom_stew",
      "rabbit_stew", "beetroot_soup"
   );
   private boolean eating;
   private int eatStart;
   private int eatHunger;
   private Item eatItem;
   private int eatSaidAt = -100000;

   private Item foodItem() {
      if (this.mc.player == null) {
         return null;
      } else {
         Item var1 = null;
         int var2 = -1;

         for (int var3 = 0; var3 < 36; var3++) {
            ItemStack var4 = this.mc.player.getInventory().getStack(var3);
            if (!var4.isEmpty()) {
               String var5 = Registries.ITEM.getId(var4.getItem()).getPath();
               if (FOODS.contains(var5)) {
                  int var6 = var5.startsWith("cooked_") || var5.equals("golden_carrot") || var5.equals("bread") || var5.equals("baked_potato") ? 2 : 1;
                  if (var6 > var2) {
                     var2 = var6;
                     var1 = var4.getItem();
                  }
               }
            }
         }

         return var1;
      }
   }

   private void stopEat(String var1) {
      if (this.eating) {
         Input.key(this.mc.options.useKey, false);
         this.eating = false;
         this.lastProgress = this.ticks;
         this.aim = null;
         this.anglesFor = null;
         AutoBuildMod.LOGGER.info("[AutoBuild] Ăn xong ({}) → xây tiếp", var1);
      }
   }

   private boolean autoEatBuild() {
      if (this.mc.player == null) {
         return false;
      } else {
         int var1 = this.mc.player.getHungerManager().getFoodLevel();
         if (!this.eating) {
            if (var1 > 14
               || this.stage == BuildController.Stage.WAIT_BLOCK
               || this.menuStage()
               || this.stage == BuildController.Stage.MINE
               || this.stage == BuildController.Stage.PIT
               || this.shopping
               || this.pillar
               || this.pending != null
               || this.mc.currentScreen != null
               || !this.mc.player.isOnGround()) {
               return false;
            }

            Item var2 = this.foodItem();
            if (var2 == null) {
               if (var1 <= 10 && this.ticks - this.eatSaidAt > 1200) {
                  this.eatSaidAt = this.ticks;
                  this.say("Sắp đói (" + var1 + "/20) mà trong túi không có đồ ăn — bỏ đồ ăn vào túi để mod tự ăn.");
               }

               return false;
            }

            this.eating = true;
            this.eatStart = this.ticks;
            this.eatHunger = var1;
            this.eatItem = var2;
            AutoBuildMod.LOGGER.info("[AutoBuild] Thanh đói {}/20 → tự ăn {} rồi xây tiếp", var1, var2.getName().getString());
         }

         if (var1 > this.eatHunger && this.ticks - this.eatStart > 10) {
            this.stopEat("đói " + this.eatHunger + " → " + var1);
            return false;
         } else if (this.ticks - this.eatStart > 120) {
            this.stopEat("hết thời gian");
            return false;
         } else if (!this.mc.player.getMainHandStack().isOf(this.eatItem)) {
            Input.key(this.mc.options.useKey, false);
            if (InventoryAccess.count(this.mc, this.eatItem) == 0) {
               this.stopEat("hết đồ ăn");
               return false;
            } else {
               InventoryAccess.equip(this.mc, this.eatItem, this.config.workingHotbarSlot);
               this.status = "Lấy đồ ăn lên tay";
               return true;
            }
         } else {
            this.mc.player.setPitch(-90.0F);
            Input.key(this.mc.options.useKey, true);
            this.status = "Đang ăn " + this.eatItem.getName().getString();
            return true;
         }
      }
   }

   private int snowTicks;
   private int snowSaidAt = -1000;

   private int snowScoopAt = -1000;
   private boolean snowDigging;

   /** 3.0.95: các ô Powder Snow đang chạm vào thân người (tính cả khi chỉ chạm mép, không chỉ ô ở chân) */
   private List<BlockPos> snowCells() {
      ArrayList<BlockPos> var1 = new ArrayList<>();
      if (this.mc.player != null && this.mc.world != null) {
         Box var2 = this.mc.player.getBoundingBox().contract(0.001);

         for (int var3 = (int)Math.floor(var2.minX); var3 <= (int)Math.floor(var2.maxX); var3++) {
            for (int var4 = (int)Math.floor(var2.minY); var4 <= (int)Math.floor(var2.maxY); var4++) {
               for (int var5 = (int)Math.floor(var2.minZ); var5 <= (int)Math.floor(var2.maxZ); var5++) {
                  BlockPos var6 = new BlockPos(var3, var4, var5);
                  if (this.mc.world.getBlockState(var6).isOf(Blocks.POWDER_SNOW)) {
                     var1.add(var6);
                  }
               }
            }
         }
      }

      return var1;
   }

   private boolean inSnowNow() {
      return !this.snowCells().isEmpty();
   }

   /**
    * Thoát Powder Snow. 3.0.95: bản cũ chỉ đi sang ô trống cạnh bên + nhảy; trong tuyết không nhảy lên được, ô cạnh cao hơn
    * hoặc bị chặn là kẹt tới chết cóng (máu xuống thấp thì mod còn tự dừng → đứng yên trong tuyết).
    * Giờ: chạm mép tuyết → lùi về giữa ô đang đứng; ở trong tuyết → thử bước ra 15 tick, không ra được thì múc ô tuyết bằng
    * xô rỗng (mất ngay) hoặc đập ô tuyết; đang kẹt tuyết thì không dừng vì máu thấp.
    */
   private boolean snowEscape() {
      if (this.mc.player == null || this.mc.world == null) {
         return false;
      } else {
         List<BlockPos> var1 = this.snowCells();
         if (var1.isEmpty()) {
            if (this.snowDigging) {
               this.snowDigging = false;
               this.mining = null;
               Input.key(this.mc.options.attackKey, false);
            }

            this.snowTicks = 0;
            return false;
         } else {
            this.snowTicks++;
            BlockPos var2 = BlockPos.ofFloored(this.mc.player.getX(), this.mc.player.getY() + 0.1, this.mc.player.getZ());
            BlockPos var3 = var2.up();
            boolean var4 = this.mc.world.getBlockState(var2).isOf(Blocks.POWDER_SNOW);
            boolean var5 = this.mc.world.getBlockState(var3).isOf(Blocks.POWDER_SNOW);
            if (this.ticks - this.snowSaidAt > 100) {
               this.snowSaidAt = this.ticks;
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] Chạm Powder Snow {} (chân {}, đầu {}, máu {}) → thoát ra",
                     new Object[]{var1, var4 ? "trong tuyết" : "không", var5 ? "trong tuyết" : "không", this.mc.player.getHealth()}
                  );
            }

            this.status = "Thoát khỏi Powder Snow";
            if (!var4 && !var5) {
               // chỉ chạm mép ô tuyết bên cạnh → lùi về giữa ô đang đứng
               Input.sneak(this.mc, false, this.ticks);
               this.mc.player.setYaw(MotionMath.yaw((double)var2.getX() + 0.5 - this.mc.player.getX(), (double)var2.getZ() + 0.5 - this.mc.player.getZ()));
               Input.key(this.mc.options.forwardKey, true);
               return true;
            } else {
               Direction var6 = null;
               double var7 = Double.MAX_VALUE;

               for (Direction var12 : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                  BlockPos var13 = var2.offset(var12);
                  BlockState var14 = this.mc.world.getBlockState(var13);
                  BlockState var15 = this.mc.world.getBlockState(var13.up());
                  if (var14.getCollisionShape(this.mc.world, var13).isEmpty()
                     && !var14.isOf(Blocks.POWDER_SNOW)
                     && var15.getCollisionShape(this.mc.world, var13.up()).isEmpty()
                     && !var15.isOf(Blocks.POWDER_SNOW)) {
                     double var16 = Vec3d.ofCenter(var13).squaredDistanceTo(this.mc.player.getPos());
                     if (!this.mc.world.getBlockState(var13.down()).getCollisionShape(this.mc.world, var13.down()).isEmpty()) {
                        var16 -= 4.0;
                     }

                     if (var16 < var7) {
                        var7 = var16;
                        var6 = var12;
                     }
                  }
               }

               if (var6 != null && this.snowTicks <= 15) {
                  Vec3d var18 = Vec3d.ofCenter(var2.offset(var6));
                  this.mc.player.setYaw(MotionMath.yaw(var18.x - this.mc.player.getX(), var18.z - this.mc.player.getZ()));
                  Input.key(this.mc.options.forwardKey, true);
                  Input.key(this.mc.options.jumpKey, true);
                  return true;
               } else {
                  // bước ra không được → bỏ ô tuyết đang nhốt người: ô ở đầu trước (tia nhìn xuất phát từ trong đó), rồi ô ở chân
                  Input.key(this.mc.options.forwardKey, false);
                  Input.key(this.mc.options.jumpKey, false);
                  Input.sneak(this.mc, false, this.ticks);
                  BlockPos var19 = var5 ? var3 : var2;
                  if (InventoryAccess.count(this.mc, Items.BUCKET) > 0) {
                     if (!this.mc.player.getMainHandStack().isOf(Items.BUCKET)) {
                        InventoryAccess.equip(this.mc, Items.BUCKET, this.config.workingHotbarSlot);
                        return true;
                     }

                     this.lookAt(this.mc.player.getYaw(), var19.equals(var3) ? 0.0F : 90.0F);
                     if (this.rotationSent() && this.ticks - this.snowScoopAt > 10 && this.unacked == 0 && InventoryAccess.synced(this.mc)) {
                        this.snowScoopAt = this.ticks;
                        this.clickTick = this.ticks;
                        Input.queueUse(this.mc, null, false, "múc tuyết", () -> {
                           Item var41 = this.mc.player.getMainHandStack().getItem();
                           int var42 = this.mc.player.getMainHandStack().getCount();
                           this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
                           this.qualityBucketUsed(var41, var42, "múc tuyết");
                           this.mc.player.swingHand(Hand.MAIN_HAND);
                        });
                        AutoBuildMod.LOGGER.info("[AutoBuild] Kẹt trong Powder Snow @ {} → múc ô tuyết bằng xô rỗng để thoát", var19.toShortString());
                     }

                     return true;
                  } else {
                     if (!this.snowDigging) {
                        AutoBuildMod.LOGGER.info("[AutoBuild] Kẹt trong Powder Snow @ {}, không có xô rỗng → đập ô tuyết để thoát", var19.toShortString());
                     }

                     this.snowDigging = true;
                     this.mining = var19;
                     this.breakBlock(this.mc.world.getBlockState(var19));
                     return true;
                  }
               }
            }
         }
      }
   }

   private BlockPos ghostEscFor;
   private int ghostEscTicks;

   private boolean ghostEscape() {
      if (this.mc.player != null && !this.ghosts.isEmpty()) {
         Box var1 = this.mc.player.getBoundingBox().contract(0.01);

         for (BlockPos var3 : this.ghosts.keySet()) {
            if (var1.intersects(new Box(var3))) {
               if (!var3.equals(this.ghostEscFor)) {
                  this.ghostEscFor = var3;
                  this.ghostEscTicks = 0;
               }

               if (++this.ghostEscTicks > 40) {
                  AutoBuildMod.LOGGER.info("[AutoBuild] Đứng trong ô {} đã 2 giây mà không bị kẹt → ô này không phải ghost block, bỏ đánh dấu", var3.toShortString());
                  this.ghosts.remove(var3);
                  Navigation.GHOSTS.remove(var3);
                  this.ghostEscFor = null;
                  return false;
               }

               if (this.ticks - this.ghostSaidAt > 60) {
                  this.ghostSaidAt = this.ticks;
                  AutoBuildMod.LOGGER.info("[AutoBuild] Đang đứng trong ghost block {} → bước ra ngoài", var3.toShortString());
               }

               this.status = "Kẹt trong ghost block " + var3.toShortString() + " → bước ra";
               this.stepAway(var3);
               return true;
            }
         }
      }

      return false;
   }

   /**
    * 3.0.81: chỉ ngồi (đi chậm) khi cạnh bên là chỗ NGUY HIỂM: lava/lửa/nước/tuyết bột, hoặc hố sâu ≥ 3 ô.
    * Bản cũ gặp bất kỳ lỗ nào (kể cả bậc thấp 1 ô) cũng ngồi → bò cả đoạn đường.
    */
   private boolean hazardNear() {
      BlockPos var1 = this.mc.player.getBlockPos();

      for (int var2 = -1; var2 <= 1; var2++) {
         for (int var3 = -1; var3 <= 1; var3++) {
            if (var2 != 0 || var3 != 0) {
               BlockPos var4 = var1.add(var2, 0, var3);
               if (!this.mc.world.isChunkLoaded(var4)) {
                  continue;
               }

               BlockState var5 = this.mc.world.getBlockState(var4);
               if (var5.isOf(Blocks.LAVA) || var5.isOf(Blocks.FIRE)) {
                  return true;
               }

               if (!var5.getCollisionShape(this.mc.world, var4).isEmpty()) {
                  continue;
               }

               for (int var6 = 1; var6 <= 4; var6++) {
                  BlockPos var7 = var4.down(var6);
                  BlockState var8 = this.mc.world.getBlockState(var7);
                  if (Navigation.hazard(var8)) {
                     return true;
                  }

                  if (!var8.getCollisionShape(this.mc.world, var7).isEmpty()) {
                     break;
                  }

                  if (var6 >= 3) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private boolean replanAfterBackoff;

   /** Đoạn kế tiếp đổi hướng (rẽ) ở waypoint này. */
   private boolean turnAt(int var1) {
      if (this.path != null && var1 > 0 && var1 + 1 < this.path.size()) {
         PathSearch.Cell var2 = this.path.get(var1 - 1);
         PathSearch.Cell var3 = this.path.get(var1);
         PathSearch.Cell var4 = this.path.get(var1 + 1);
         return Integer.signum(var3.x() - var2.x()) != Integer.signum(var4.x() - var3.x())
            || Integer.signum(var3.z() - var2.z()) != Integer.signum(var4.z() - var3.z())
            || var3.y() != var4.y();
      } else {
         return false;
      }
   }

   /** Đã chạy/trượt/nhảy QUA waypoint trên đoạn thẳng → bỏ qua nó, không quay lại đúng tâm ô. */
   private void skipPassedWaypoints() {
      if (this.path != null && this.terrain != null && this.mc.player.isOnGround()) {
         for (int var1 = 0; var1 < 4 && this.pathIndex > 0 && this.pathIndex + 1 < this.path.size(); var1++) {
            PathSearch.Cell var2 = this.path.get(this.pathIndex - 1);
            PathSearch.Cell var3 = this.path.get(this.pathIndex);
            PathSearch.Cell var4 = this.path.get(this.pathIndex + 1);
            int var5 = var3.x() - var2.x();
            int var6 = var3.z() - var2.z();
            if (var2.y() != var3.y()
               || var3.y() != var4.y()
               || Math.abs(var5) + Math.abs(var6) != 1
               || var4.x() - var3.x() != var5
               || var4.z() - var3.z() != var6
               || !this.terrain.standable(var3)
               || !this.terrain.standable(var4)) {
               return;
            }

            double var7 = this.mc.player.getX() - ((double)var3.x() + 0.5);
            double var9 = this.mc.player.getZ() - ((double)var3.z() + 0.5);
            double var11 = var7 * (double)var5 + var9 * (double)var6;
            double var13 = Math.abs(var7 * (double)var6 - var9 * (double)var5);
            if (!(var11 > -0.15) || !(var13 < 0.45) || !(Math.abs(this.mc.player.getY() - (double)var3.y()) < 0.6)) {
               return;
            }

            this.pathIndex++;
            this.stuckTicks = 0;
         }
      }
   }

   /** 3.0.82: khoảng lệch ngang của người chơi so với đường thẳng nối tâm ô a → tâm ô b. */
   private double lateralOff(PathSearch.Cell var1, PathSearch.Cell var2) {
      double var3 = (double)(var2.x() - var1.x());
      double var5 = (double)(var2.z() - var1.z());
      double var7 = Math.sqrt(var3 * var3 + var5 * var5);
      if (var7 < 1.0E-6) {
         return 0.0;
      } else {
         double var9 = this.mc.player.getX() - ((double)var1.x() + 0.5);
         double var11 = this.mc.player.getZ() - ((double)var1.z() + 0.5);
         return Math.abs(var9 * var5 - var11 * var3) / var7;
      }
   }

   /** 3.0.82: số ô thẳng, cùng độ cao, đứng được liên tiếp tính từ waypoint hiện tại (tối đa 9). */
   private int straightRun() {
      if (this.path != null && this.pathIndex > 0 && this.pathIndex < this.path.size() && this.terrain != null) {
         PathSearch.Cell var1 = this.path.get(this.pathIndex - 1);
         PathSearch.Cell var2 = this.path.get(this.pathIndex);
         int var3 = var2.x() - var1.x();
         int var4 = var2.z() - var1.z();
         if (Math.abs(var3) + Math.abs(var4) != 1 || var2.y() != var1.y()) {
            return 0;
         } else {
            int var5 = 0;

            for (int var6 = this.pathIndex; var6 < this.path.size() && var5 < 9; var6++) {
               PathSearch.Cell var7 = this.path.get(var6 - 1);
               PathSearch.Cell var8 = this.path.get(var6);
               if (var8.y() != var1.y()
                  || var8.x() - var7.x() != var3
                  || var8.z() - var7.z() != var4
                  || !this.terrain.standable(var8)
                  || this.recentPlaced.containsKey(Navigation.pos(var8).down())) {
                  break;
               }

               var5++;
            }

            return var5;
         }
      } else {
         return 0;
      }
   }

   /**
    * 3.0.82: kiểu PathExecutor của Baritone — nhân vật ĐANG ĐỨNG trong ô nào của đường đi thì coi như đã tới ô đó
    * (kể cả ô nằm sau waypoint hiện tại, ví dụ nhảy/trượt quá), không bắt quay lại đúng tâm ô cũ.
    * Bước tiếp theo là đi ngang/xuống/nhảy khe → nhắm luôn ô sau (đoạn thẳng từ bất kỳ điểm nào trong ô tới tâm ô kề
    * vẫn nằm trong 2 ô đó nên không cắt góc ra ngoài). Bước chéo / leo lên / cần đặt khối kê thì vẫn chờ tới gần tâm như cũ.
    */
   private void catchUpPath() {
      if (this.path != null && this.terrain != null && this.pathIndex < this.path.size() && this.mc.player.isOnGround()) {
         int var1 = (int)Math.floor(this.mc.player.getX());
         int var2 = (int)Math.floor(this.mc.player.getZ());
         double var3 = this.mc.player.getY();
         int var5 = this.path.size() - 1;

         for (int var6 = Math.min(var5 - 1, this.pathIndex + 8); var6 >= this.pathIndex; var6--) {
            PathSearch.Cell var7 = this.path.get(var6);
            if (var7.x() == var1 && var7.z() == var2 && Math.abs(var3 - (double)var7.y()) < 0.7 && this.terrain.standable(var7)) {
               PathSearch.Cell var8 = this.path.get(var6 + 1);
               int var9 = Math.abs(var8.x() - var7.x());
               int var10 = Math.abs(var8.z() - var7.z());
               boolean var11 = var9 > 0 && var10 > 0;
               boolean var12 = var8.y() > var7.y();
               boolean var13 = var11 || var12 || !this.terrain.standable(var8) || !this.terrain.clear(var8);
               int var14 = var13 ? var6 : var6 + 1;
               if (var14 > this.pathIndex) {
                  this.pathIndex = var14;
                  this.stuckTicks = 0;
                  this.targetTicks = 0;
               }

               return;
            }
         }
      }
   }

   private final Map<BlockPos, Double> navBestDist = new HashMap<>();
   private List<PathSearch.Cell> progressPath;
   private int progressPathMax;
   private int navProgressAt;

   /**
    * 3.0.82: tới GẦN khối cần đặt hơn mức gần nhất từng đạt (≥ 1 ô) cũng là tiến triển → watchdog 10 giây không cắt đường dài.
    * Chỉ tính khi có khối đích thật; nhớ mức gần nhất theo từng khối nên đi qua đi lại / đổi qua lại 2 đích không được tính.
    */
   private void noteNavProgress() {
      if (this.target != null && this.plan != null && this.plan.at(this.target.pos()) != null) {
         BlockPos var1 = this.target.pos();
         double var2 = Math.sqrt(this.mc.player.squaredDistanceTo(Vec3d.ofCenter(var1)));
         Double var4 = this.navBestDist.get(var1);
         if (var4 == null) {
            if (this.navBestDist.size() > 256) {
               this.navBestDist.clear();
            }

            this.navBestDist.put(var1.toImmutable(), var2);
         } else if (var2 < var4 - 1.0) {
            this.navBestDist.put(var1.toImmutable(), var2);
            this.navProgressAt = this.ticks;
         }
      }
   }

   private final Map<BlockPos, Integer> nearbyTried = new HashMap<>();
   private int nearbyCheckAt = -1000;
   private int nearbySaidAt = -1000;

   /**
    * 3.0.82: XÂY GẦN TRƯỚC. Đang đi (hoặc sắp đi xa) mà có ô của tầng nằm trong tầm với, đặt được ngay từ chỗ đứng
    * (có vật liệu, có chỗ bám, đúng hướng) → đặt luôn rồi mới đi tiếp. Xét cả ô bị hoãn/bỏ qua vì lý do ĐƯỜNG ĐI
    * (không tới được, kẹt, hàng chưa tới lượt...) vì giờ đã đứng trong tầm. Không đụng: observer kích hoạt piston (xây sau cùng),
    * khối cần nước/xô, khối đang chờ khối phụ thuộc, ô nằm trên đường sắp đi.
    */
   private boolean nearbyBuild(String var1) {
      if (this.plan != null
         && this.currentLayer != null
         && this.running
         && !this.cleanupMode
         && !this.repairing
         && !this.sweeping
         && !this.approachOnly
         && this.repairGoal == null
         && this.temporary == null
         && this.supportTarget == null
         && this.pit == null
         && this.selfPillarBase == null
         && !this.climbMove
         && this.mc.currentScreen == null
         && this.restoringPos == null
         && (this.target == null || this.plan.at(this.target.pos()) != null && !this.pistonBlockers.contains(this.target.pos()))
         && this.mc.player.isOnGround()
         && !(this.slipperyFloor() && this.mc.player.getVelocity().horizontalLengthSquared() > 0.01)) {
         Vec3d var2 = this.mc.player.getEyePos();
         double var3 = this.reach();
         BlockPos var5 = this.mc.player.getBlockPos();
         int var6 = (int)Math.ceil(var3);
         HashSet<BlockPos> var7 = new HashSet<>();
         if (this.path != null) {
            for (int var8 = Math.max(0, this.pathIndex - 1); var8 < this.path.size(); var8++) {
               BlockPos var9 = Navigation.pos(this.path.get(var8));
               var7.add(var9);
               var7.add(var9.up());
            }
         }

         ArrayList<SchematicPlan.Target> var20 = new ArrayList<>();
         HashMap<BlockPos, Double> var21 = new HashMap<>();

         for (int var10 = -3; var10 <= 3; var10++) {
            for (int var11 = -var6; var11 <= var6; var11++) {
               for (int var12 = -var6; var12 <= var6; var12++) {
                  BlockPos var13 = var5.add(var11, var10, var12);
                  int var14 = var13.getY();
                  if (var14 <= this.currentLayer && var14 >= this.currentLayer - 2 && this.config.inYRange(var14)) {
                     double var15 = Vec3d.ofCenter(var13).squaredDistanceTo(var2);
                     if (!(var15 > var3 * var3)) {
                        SchematicPlan.Target var17 = this.plan.at(var13);
                        if (var17 != null
                           && !var17.state().isAir()
                           && (this.target == null || !var13.equals(this.target.pos()))
                           && this.nearbyTried.getOrDefault(var13, -1000) <= this.ticks
                           && !var7.contains(var13)
                           && !this.autoWater.contains(var13)
                           && this.nearbyOk(var17)) {
                           BlockPos var18 = var13.toImmutable();
                           var20.add(var17);
                           var21.put(var18, var15);
                        }
                     }
                  }
               }
            }
         }

         if (var20.isEmpty()) {
            return false;
         } else {
            var20.sort(Comparator.comparingDouble(var1x -> var21.getOrDefault(var1x.pos(), 99.0)));
            int var22 = 0;

            for (SchematicPlan.Target var24 : var20) {
               if (++var22 > 6 || var22 > 2 && this.overBudget()) {
                  break;
               }

               SchematicPlan.Target var25 = this.resolve(var24);
               PlacementSolver.Aim var26 = PlacementSolver.solve(this.mc, var2, var25, var3, true, var24.pos().asLong() * 31L);
               if (var26 != null && PlacementSolver.robust(this.mc, PlacementSolver.poseEye(PlacementSolver.standingEye(this.mc, var2), var26.sneak()), var26, var3)) {
                  BlockPos var27 = var24.pos().toImmutable();
                  SchematicPlan.Target var28 = this.target;
                  if (var28 != null && this.plan.at(var28.pos()) != null) {
                     SchematicPlan.Target var29 = this.plan.at(var28.pos());
                     if (!this.queue.contains(var29)) {
                        this.queue.addFirst(var29);
                     }
                  }

                  this.nearbyTried.put(var27, this.ticks + 300);
                  if (this.nearbyTried.size() > 4000) {
                     this.nearbyTried.clear();
                  }

                  this.skipped.remove(var27);
                  this.deferred.remove(var27);
                  this.queue.remove(var24);
                  this.release();
                  this.pending = null;
                  this.pillar = false;
                  this.spoof = null;
                  this.spoofHold = 0;
                  this.anglesFor = null;
                  this.rowSupport = false;
                  this.jobSupport = false;
                  this.restoringPos = null;
                  this.inventoryAttempts = 0;
                  this.aimAttempt = 0;
                  this.targetTicks = 0;
                  this.stepTicks = 0;
                  this.bridgeTicks = 0;
                  this.replans = 0;
                  this.planeMove = false;
                  this.target = var25;
                  this.aim = var26;
                  this.status = "Xây gần trước: " + var25.state().getBlock().getName().getString() + " @ " + var27.toShortString();
                  if (this.ticks - this.nearbySaidAt > 60) {
                     this.nearbySaidAt = this.ticks;
                     AutoBuildMod.LOGGER
                        .info(
                           "[AutoBuild] Xây gần trước ({}): {} @ {} trong tầm với → đặt luôn{}",
                           new Object[]{
                              var1,
                              var25.state().getBlock().getName().getString(),
                              var27.toShortString(),
                              var28 == null ? "" : ", rồi mới đi tiếp tới " + var28.pos().toShortString()
                           }
                        );
                  }

                  this.setStage(BuildController.Stage.EQUIP);
                  this.equip();
                  return true;
               }

               // chưa có góc đặt từ chỗ này → 2 giây sau (đã đi chỗ khác) mới xét lại ô này
               this.nearbyTried.put(var24.pos().toImmutable(), this.ticks + 40);
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private boolean nearbyOk(SchematicPlan.Target var1) {
      BlockPos var2 = var1.pos();
      if (this.placedDone.contains(var2) || !this.mc.world.isChunkLoaded(var2)) {
         return false;
      } else if (this.deferred.getOrDefault(var2, 0) > this.ticks || this.skipped.contains(var2) || this.failures.getOrDefault(var2, 0) >= 5) {
         // 3.0.105: log 02:59–08:54: khối vừa "đặt 5 lần vẫn mất → để lại" bị "Xây gần trước" chọn lại ngay (và còn xoá dấu hoãn)
         // → 1.700 lần/giờ lặp đúng 11 ô. Giờ tôn trọng dấu hoãn / bỏ qua.
         return false;
      } else if (this.skipped.contains(var2) && this.skipReason.getOrDefault(var2, "").startsWith("Cần nhìn lên")) {
         return false;
      } else if (this.depMissingFor.containsKey(var2) || this.depWaits.getOrDefault(var2, 0) > 0 || this.restore.contains(var2)) {
         return false;
      } else {
         BlockState var3 = this.mc.world.getBlockState(var2);
         BlockState var4 = var1.state();
         if (!var3.isReplaceable() || this.done(var3, var4) || !StateMatch.canAdd(var3, var4)) {
            return false;
         } else if (Materials.bucket(var3, var4)
            || Materials.flowing(var4)
            || needsWater(var4)
            || FluidOrder.phase(var4) != 0
            || !Materials.supported(var3, var4)
            || StateMatch.chestAwaitingPartner(var3, var4)
            || var4.getBlock() instanceof ObserverBlock && var4.contains(Properties.FACING) && var4.get(Properties.FACING) == Direction.UP) {
            return false;
         } else if (this.triggerHeld(var1) || this.blockerHeld(var2) || this.occupies(var2) || !this.hasSupport(var2)) {
            return false;
         } else {
            return InventoryAccess.count(this.mc, this.materialFor(var1, var3)) > 0 && this.placeable(var1);
         }
      }
   }

   /** Trên đường đi, 4 ô tới có trần trống (y+2) để nhảy. */
   private boolean headroomAhead(int var1) {
      if (this.path == null) {
         return false;
      } else {
         for (int var2 = this.pathIndex; var2 < Math.min(this.path.size(), this.pathIndex + var1); var2++) {
            BlockPos var3 = Navigation.pos(this.path.get(var2)).up().up();
            if (!this.mc.world.getBlockState(var3).getCollisionShape(this.mc.world, var3).isEmpty()) {
               return false;
            }
         }

         BlockPos var4 = this.mc.player.getBlockPos().up().up();
         return this.mc.world.getBlockState(var4).getCollisionShape(this.mc.world, var4).isEmpty();
      }
   }

   /** Số ô khe cần nhảy qua giữa 2 waypoint (0 = bước thường). */
   private int parkourGap(PathSearch.Cell var1, PathSearch.Cell var2) {
      // 3.0.100: khe 1–3 ô cùng độ cao / xuống thấp 1 ô, khe 1–2 ô khi nhảy lên cao 1 ô (chạy nhanh + nhảy)
      if (var1 != null && var2 != null && Math.abs(var1.y() - var2.y()) <= 1) {
         int var3 = Math.abs(var2.x() - var1.x());
         int var4 = Math.abs(var2.z() - var1.z());
         int var5 = (var3 == 0 || var4 == 0) && var3 + var4 >= 2 && var3 + var4 <= 4 ? var3 + var4 - 1 : 0;
         return var2.y() > var1.y() && var5 > 2 ? 0 : var5;
      } else {
         return 0;
      }
   }

   /** 3.0.99: quãng bay ngang sau đúng var3 tick nếu nhảy ngay (dùng cho nhảy LÊN khối cao hơn: phải qua mép khối trước khi rơi dưới +1) */
   static double jumpTravel(double var0, boolean var2, float var3, int var4) {
      double var5 = (double)(var3 * 0.91F);
      double var7 = (var2 ? 0.13 : 0.1) * (0.21600002 / (double)(var3 * var3 * var3)) * 0.98;
      double var9 = Math.max(0.0, var0) + (var2 ? 0.2 : 0.0) + var7;
      double var11 = var9;
      var9 *= var5;
      double var13 = var2 ? 0.02548 : 0.0196;

      for (int var15 = 1; var15 < var4; var15++) {
         var9 += var13;
         var11 += var9;
         var9 *= 0.91;
      }

      return var11;
   }

   private int parkourRunupFor = -1;
   private int parkourSaidAt = -1000;

   /**
    * 3.0.82: quãng bay ngang (tính từ vị trí hiện tại) nếu bấm nhảy NGAY tick này, theo đúng vật lý vanilla:
    * tick bật nhảy còn ma sát đất 0,546 (+0,2 nếu đang chạy nhanh), rồi 11 tick bay với cản không khí 0,91.
    * v0 = vận tốc ngang theo hướng nhảy (getVelocity() sau tick trước).
    */
   static double jumpReach(double var0, boolean var2) {
      return jumpReach(var0, var2, 0.6F);
   }

   /** slip = độ trơn khối đang đứng (0,6 thường; băng 0,98; băng xanh 0,989) — tick bật nhảy dùng ma sát của khối đó. */
   static double jumpReach(double var0, boolean var2, float var3) {
      double var4 = (double)(var3 * 0.91F);
      double var6 = (var2 ? 0.13 : 0.1) * (0.21600002 / (double)(var3 * var3 * var3)) * 0.98;
      double var5 = Math.max(0.0, var0) + (var2 ? 0.2 : 0.0) + var6;
      double var7 = var5;
      var5 *= var4;
      double var9 = var2 ? 0.02548 : 0.0196;

      for (int var11 = 0; var11 < 11; var11++) {
         var5 += var9;
         var7 += var5;
         var5 *= 0.91;
      }

      return var7;
   }

   /**
    * 3.0.82: nhảy qua khe kiểu Baritone nhưng TÍNH TRƯỚC điểm rơi.
    * Lỗi 3.0.81: luôn chạy nhanh + nhảy muộn (0,28–0,75 sau tâm ô) → bay ~3,6 ô, rơi quá ô đích 1–2 ô, rồi mod
    * bắt quay lại đúng tâm ô đích (quay đầu), bộ dò "bị giật" còn tưởng cú quay đầu đó là bị server kéo lại.
    * Giờ: khe 1 ô đi bộ (không chạy), khe 2 ô chạy nhanh; mỗi tick tính điểm rơi nếu nhảy ngay, điểm rơi vừa tới
    * ô đích (hơi trước tâm) thì nhảy; tới mép cuối cùng thì nhảy luôn, không bao giờ đi bộ rơi xuống khe.
    */
   private void parkourStep(PathSearch.Cell var1, PathSearch.Cell var2, int var3) {
      // 3.0.102 (người dùng + log 21:29–21:32): lùi lấy đà CHỈ trong ô đang đứng; tính toạ độ số lẻ chính xác; trên không tự chỉnh đà.
      // Lỗi 3.0.101: tới ô nhảy lệch ngang 0,44–0,45 (vào từ góc), mặt quay về TÂM ô đích (chéo) → bấm S lùi theo đường chéo,
      // lệch ngang tăng tới mép bên (ảnh: đứng sát góc khối) → rơi → /home → lặp 8 lần.
      // Giờ: mặt khoá thẳng theo trục nhảy, bấm A/D kéo người về giữa làn; lùi tối đa tới -0,25 ô sau tâm (tính cả quán tính);
      // mỗi tick MÔ PHỎNG cú nhảy (vật lý vanilla: trục Y trước trục ngang, đập mặt bên khi nhảy lên) để biết sẽ chạm ở đâu,
      // nhảy ở tick tốt nhất; trên không mỗi tick chọn giữ W / thả W / giữ S cho điểm chạm gần giữa khối đích nhất.
      int var4 = Integer.signum(var2.x() - var1.x());
      int var5 = Integer.signum(var2.z() - var1.z());
      double var6 = (this.mc.player.getX() - ((double)var1.x() + 0.5)) * (double)var4 + (this.mc.player.getZ() - ((double)var1.z() + 0.5)) * (double)var5;
      double var8 = this.mc.player.getVelocity().x * (double)var4 + this.mc.player.getVelocity().z * (double)var5;
      // lệch ngang khỏi trục nhảy (0 = giữa làn) và vận tốc ngang
      double var27 = (this.mc.player.getX() - ((double)var1.x() + 0.5)) * (double)Math.abs(var5) + (this.mc.player.getZ() - ((double)var1.z() + 0.5)) * (double)Math.abs(var4);
      double var29 = this.mc.player.getVelocity().x * (double)Math.abs(var5) + this.mc.player.getVelocity().z * (double)Math.abs(var4);
      int var26 = var2.y() - var1.y();
      boolean var12 = (var3 >= 2 || var26 > 0) && this.mc.player.getHungerManager().getFoodLevel() > 6;
      // sau ô đích đường còn đi thẳng tiếp cùng độ cao → chạm xa hơn một chút cũng được
      boolean var13 = false;
      if (this.path != null && this.pathIndex + 1 < this.path.size()) {
         PathSearch.Cell var14 = this.path.get(this.pathIndex + 1);
         var13 = var14.y() == var2.y() && var14.x() - var2.x() == var4 && var14.z() - var2.z() == var5;
      }

      if (this.parkourIdx != this.pathIndex || this.parkourPath != this.path) {
         this.parkourIdx = this.pathIndex;
         this.parkourPath = this.path;
         this.parkourRunups = 0;
         this.parkourRunupFor = -1;
         this.parkourBackTicks = 0;
      }

      String var31 = "khe " + var3 + " ô" + (var26 > 0 ? " lên cao 1 ô" : (var26 < 0 ? " xuống thấp 1 ô" : ""));
      float var44 = MotionMath.yaw((double)var4, (double)var5);
      double var34 = (double)var3 + 0.55;
      double var36 = (double)var3 + (var13 ? 2.3 : 1.3);
      double var56 = (double)var3 + (var13 ? 1.1 : 0.9);
      if (!this.mc.player.isOnGround()) {
         // trên không: "vừa tới nơi là nhả W" — thử giữ W / thả / giữ S, chọn cách cho điểm chạm gần điểm đích nhất
         if (Math.abs(var27) <= 0.8 && var6 > -1.0 && var6 < (double)var3 + 3.0) {
            this.mc.player.setYaw(var44);
            double var65 = this.mc.player.getY() - (double)var1.y();
            double var67 = this.mc.player.getVelocity().y;
            // 3.0.103 (người dùng): tới nơi là thả hết phím. Nửa người đã ở trên khối đích → thả hết (chỉ S nếu thả vẫn trôi quá khối);
            // chưa tới: nếu thả phím mà vẫn chạm được khối đích thì thả, thả mà hụt thì giữ W
            double var74 = simulateFlight(var6, var8, var65, var67, false, 0, var3, var26);
            int var70;
            if (var6 >= (double)var3 + 0.5) {
               var70 = !Double.isNaN(var74) && var74 > var36 ? -1 : 0;
            } else if (!Double.isNaN(var74) && var74 >= var34) {
               var70 = var74 <= var36 ? 0 : -1;
            } else {
               var70 = 1;
            }

            Input.key(this.mc.options.forwardKey, var70 > 0);
            Input.key(this.mc.options.backKey, var70 < 0);
            Input.key(this.mc.options.leftKey, false);
            Input.key(this.mc.options.rightKey, false);
            Input.key(this.mc.options.jumpKey, false);
            this.landBrakeCell = var2;
            this.landBrakeX = var4;
            this.landBrakeZ = var5;
            this.landBrakeUntil = this.ticks + 12;
            this.status = "Đang bay qua " + var31 + (var70 > 0 ? " (giữ W)" : (var70 < 0 ? " (hãm S)" : " (thả W)"));
         } else {
            this.status = "Đang bay qua " + var31;
         }

         return;
      } else if (var6 > 0.85 || var6 < -0.85 || Math.abs(var27) > 0.8) {
         // không còn đứng trên ô nhảy (bị đẩy / đã rơi) → để phần đi đường xử lý
         return;
      }

      Input.forceSneak(this.mc, false, this.ticks);
      Input.forceSprint(this.mc, var12, this.ticks);
      // khoá mặt thẳng theo trục nhảy (không nhìn chéo về tâm ô đích)
      this.mc.player.setYaw(var44);
      // A/D kéo người về giữa làn: var45 = lệch ngang tăng bao nhiêu khi bấm A (trái)
      double var45 = Math.cos(Math.toRadians((double)var44)) * (double)Math.abs(var5) + Math.sin(Math.toRadians((double)var44)) * (double)Math.abs(var4);
      double var47 = var27 + var29 * 2.0;
      boolean var49 = Math.abs(var47) > 0.05 && var47 * var45 < 0.0;
      boolean var50 = Math.abs(var47) > 0.05 && var47 * var45 > 0.0;
      Input.key(this.mc.options.leftKey, var49);
      Input.key(this.mc.options.rightKey, var50);
      Input.key(this.mc.options.jumpKey, false);
      boolean var51 = this.mc.player.isSneaking();
      // cú nhảy (tick sau) có chạy nhanh không: đang giữ W + phím chạy thì tick sau chạy nhanh
      boolean var15 = var12 && !var51;
      BlockPos var24 = BlockPos.ofFloored(this.mc.player.getX(), this.mc.player.getY() - 0.2, this.mc.player.getZ());
      float var25 = this.mc.world.getBlockState(var24).getBlock().getSlipperiness();
      double var52 = (var15 ? 0.13 : 0.1) * (0.21600002 / (double)(var25 * var25 * var25)) * 0.98 * (var49 || var50 ? 0.7071 : 1.0);
      // nhảy ngay: điểm qua mặt phẳng ô đích khi trên không giữ W (xa nhất) / giữ S (gần nhất); tương tự nếu đi thêm 1 tick rồi nhảy
      double var18 = simulateCross(var6, var8, var15, var25, 1, var3, var26);
      double var62 = simulateCross(var6, var8, var15, var25, -1, var3, var26);
      double var20 = var6 + var8 + var52;
      double var63 = (var8 + var52) * (double)(var25 * 0.91F);
      double var54 = simulateCross(var20, var63, var15, var25, 1, var3, var26);
      double var64 = simulateCross(var20, var63, var15, var25, -1, var3, var26);
      boolean var43 = jumpWindow(var18, var62, var34, var36);
      boolean var58 = jumpWindow(var54, var64, var34, var36);
      double var76 = jumpMargin(var18, var62, var56);
      double var78 = jumpMargin(var54, var64, var56);
      boolean var39 = Math.abs(var27) <= 0.15 && Math.abs(var29) <= 0.04;
      boolean var40 = var8 >= (var15 ? 0.08 : 0.05);
      boolean var41 = var20 > 0.72;
      // nhảy đi bộ qua khe 1 ô cùng độ cao: đứng sát mép rồi mới nhảy
      boolean var42 = var26 != 0 || var15 || var3 >= 2 || var6 >= 0.1;
      // nhảy đi bộ mà người vẫn đang chạy nhanh (từ đoạn trước) → tầm bay khác tính toán, thả W cho thôi chạy
      boolean var59 = !var12 && this.mc.player.isSprinting();
      if (this.parkourRunupFor == this.pathIndex) {
         // 3.0.103: lùi 1 chút về khoảng giữa ô đang đứng (điểm dừng dự kiến, tính cả quán tính, ≤ tâm ô) rồi tiến lại
         double var60 = var6 + Math.min(0.0, var8) * 1.2;
         if (var60 > 0.0 && var6 > -0.1 && ++this.parkourBackTicks < 25) {
            this.status = "Lùi lấy đà (S) trong ô để nhảy qua " + var31;
            Input.key(this.mc.options.forwardKey, false);
            Input.key(this.mc.options.backKey, true);
            return;
         }

         this.parkourRunupFor = -1;
         this.parkourBackTicks = 0;
      }

      Input.key(this.mc.options.backKey, false);
      if (var59) {
         this.status = "Thôi chạy nhanh trước " + var31;
         Input.key(this.mc.options.forwardKey, false);
         Input.key(this.mc.options.backKey, var41);
         return;
      }

      // 3.0.103 (người dùng): đơn giản — tới vùng nhảy (đi thường khe 1 ô ngang: cách tâm ≥ 0,15, không cần sát mép;
      // chạy nhanh / nhảy lên / nhảy xuống: ở đâu trong ô cũng được) mà mô phỏng thấy tới được khối đích (trên không tự chỉnh) → nhảy.
      // Bản 3.0.101–3.0.102 đòi thêm thẳng làn ≤ 0,15, đủ đà, chọn tick tốt nhất → tiến lùi mãi không nhảy (log 22:29:43–22:29:53).
      double var80 = var26 == 0 && !var15 && var3 < 2 ? 0.15 : -0.3;
      boolean var61 = var6 >= var80 && var43 && !var51 && Math.abs(var27) <= 0.3 && Math.abs(var29) <= 0.08;

      if (var61) {
         Input.key(this.mc.options.forwardKey, true);
         Input.key(this.mc.options.jumpKey, true);
         Input.key(this.mc.options.leftKey, false);
         Input.key(this.mc.options.rightKey, false);
         this.status = "Nhảy qua " + var31;
         if (this.ticks - this.parkourSaidAt > 40) {
            this.parkourSaidAt = this.ticks;
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Nhảy qua {}: ô đứng {} {}, cách tâm {}, lệch ngang {}, vận tốc {}, chạy nhanh={}, mô phỏng chạm {}–{} (khối đích {}–{})",
                  new Object[]{
                     var31,
                     var1.x(),
                     var1.z(),
                     String.format(Locale.ROOT, "%.3f", var6),
                     String.format(Locale.ROOT, "%.3f", var27),
                     String.format(Locale.ROOT, "%.3f", var8),
                     var15,
                     Double.isNaN(var62) ? "-" : String.format(Locale.ROOT, "%.3f", var62),
                     String.format(Locale.ROOT, "%.3f", var18),
                     String.format(Locale.ROOT, "%.1f", (double)var3 + 0.5),
                     String.format(Locale.ROOT, "%.1f", (double)var3 + 1.5)
                  }
               );
         }
      } else if (var41) {
         // đi tiếp sẽ ra khỏi mép ô mà chưa nhảy được → dừng bằng S; chưa đủ đà / lệch → lùi trong ô rồi chạy lại
         this.parkourRunups++;
         this.parkourRunupFor = this.pathIndex;
         this.parkourBackTicks = 0;
         Input.key(this.mc.options.forwardKey, false);
         Input.key(this.mc.options.backKey, true);
         this.status = "Lùi lấy đà (S) trong ô để nhảy qua " + var31;
         if (this.ticks - this.parkourSaidAt > 40) {
            this.parkourSaidAt = this.ticks;
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Chưa nhảy qua {}: ô đứng {} {}, cách tâm {}, lệch ngang {}, vận tốc {}, ngồi={}, mô phỏng chạm {}–{} (cần {}–{}) → lùi trong ô (S)",
                  new Object[]{
                     var31,
                     var1.x(),
                     var1.z(),
                     String.format(Locale.ROOT, "%.3f", var6),
                     String.format(Locale.ROOT, "%.3f", var27),
                     String.format(Locale.ROOT, "%.3f", var8),
                     var51,
                     Double.isNaN(var62) ? "-" : String.format(Locale.ROOT, "%.3f", var62),
                     Double.isNaN(var18) ? "hụt" : String.format(Locale.ROOT, "%.3f", var18),
                     String.format(Locale.ROOT, "%.2f", var34),
                     String.format(Locale.ROOT, "%.2f", var36)
                  }
               );
         }
      } else {
         // chưa tới lúc nhảy: đi tiếp về phía mép, vừa đi vừa về giữa làn
         Input.key(this.mc.options.forwardKey, true);
         this.status = "Lấy đà nhảy qua " + var31;
      }
   }

   /** khoảng điểm chạm với được [giữ S, giữ W] có chạm vào khoảng an toàn [lo, hi] của khối đích không */
   private static boolean jumpWindow(double var0, double var2, double var4, double var6) {
      return !Double.isNaN(var0) && var0 >= var4 && (Double.isNaN(var2) || var2 <= var6);
   }

   /** điểm đích nằm sâu trong khoảng với được bao nhiêu (âm = ngoài khoảng) */
   private static double jumpMargin(double var0, double var2, double var4) {
      if (Double.isNaN(var0)) {
         return -9.0;
      } else {
         double var6 = Double.isNaN(var2) ? -99.0 : var2;
         return Math.min(var0 - var4, var4 - var6);
      }
   }

   /**
    * 3.0.102: nhảy NGAY tick này (đang giữ W, chạy nhanh = var4), trên không giữ phím var6 (+1 W, 0 thả, -1 S) →
    * vị trí tâm người dọc trục lúc đi xuống qua mặt phẳng ô đích (chênh cao var8). NaN nếu rơi lại ô đứng trước đó.
    * Vật lý vanilla: tick bật nhảy dùng ma sát khối đứng (+0,2 nếu chạy nhanh), trên không cản 0,91, trọng lực 0,08·0,98;
    * trục Y di chuyển TRƯỚC trục ngang; nhảy lên cao mà chân còn thấp hơn mặt khối đích thì mép trước người đập mặt bên → mất đà ngang.
    */
   static double simulateCross(double var0, double var2, boolean var4, float var5, int var6, int var7, int var8) {
      double var9 = (var4 ? 0.13 : 0.1) * (0.21600002 / (double)(var5 * var5 * var5)) * 0.98;
      double var11 = (double)var7 + 0.5;
      double var13 = var0;
      double var15 = 0.0;
      double var17 = 0.42;
      double var19 = var2 + var9 + (var4 ? 0.2 : 0.0);
      double var21 = (var4 && var6 > 0 ? 0.02548 : 0.0196) * (double)var6;

      for (int var23 = 0; var23 < 40; var23++) {
         if (var23 > 0) {
            var19 += var21;
         }

         double var24 = var15 + var17;
         if (var17 < 0.0) {
            if ((double)var8 <= var15 && (double)var8 > var24) {
               return var13;
            }

            if (var8 < 0 && 0.0 <= var15 && 0.0 > var24 && var13 - 0.3 < 0.5) {
               return Double.NaN;
            }
         }

         var15 = var24;
         double var26 = var13 + var19;
         if (var8 > 0 && var15 < (double)var8 && var26 + 0.3 > var11 && var13 + 0.3 <= var11 + 1.0E-9) {
            var26 = var11 - 0.3;
            var19 = 0.0;
         }

         var13 = var26;
         var19 *= var23 == 0 ? (double)(var5 * 0.91F) : 0.91;
         var17 = (var17 - 0.08) * 0.98;
      }

      return Double.NaN;
   }

   /** 3.0.102: đang bay (vị trí/vận tốc dọc trục, độ cao chân so với ô đứng, vận tốc Y), giữ phím var9 tới lúc qua mặt phẳng ô đích */
   static double simulateFlight(double var0, double var2, double var4, double var6, boolean var8, int var9, int var10, int var11) {
      double var12 = (double)var10 + 0.5;
      double var14 = (var8 && var9 > 0 ? 0.02548 : 0.0196) * (double)var9;
      double var16 = var0;
      double var18 = var2;
      double var20 = var4;
      double var22 = var6;

      for (int var24 = 0; var24 < 40; var24++) {
         var18 += var14;
         double var25 = var20 + var22;
         if (var22 < 0.0) {
            if ((double)var11 <= var20 && (double)var11 > var25) {
               return var16;
            }

            if (var11 < 0 && 0.0 <= var20 && 0.0 > var25 && var16 - 0.3 < 0.5) {
               return Double.NaN;
            }
         }

         var20 = var25;
         double var27 = var16 + var18;
         if (var11 > 0 && var20 < (double)var11 && var27 + 0.3 > var12 && var16 + 0.3 <= var12 + 1.0E-9) {
            var27 = var12 - 0.3;
            var18 = 0.0;
         }

         var16 = var27;
         var18 *= 0.91;
         var22 = (var22 - 0.08) * 0.98;
         if (var20 < (double)Math.min(0, var11) - 3.0) {
            return Double.NaN;
         }
      }

      return Double.NaN;
   }

   // 3.0.102: vừa nhảy xong chạm khối đích → hãm (S theo trục nhảy) cho khỏi trượt quá khối khi đường rẽ hướng khác
   private PathSearch.Cell landBrakeCell;
   private int landBrakeX;
   private int landBrakeZ;
   private int landBrakeUntil = -1000;

   private void landBrake(PathSearch.Cell var1) {
      if (this.landBrakeCell != null && this.ticks < this.landBrakeUntil && this.mc.player.isOnGround()) {
         PathSearch.Cell var2 = this.landBrakeCell;
         int var3 = this.landBrakeX;
         int var4 = this.landBrakeZ;
         // đường đi tiếp thẳng cùng hướng nhảy, cùng độ cao → không cần hãm
         boolean var5 = var1 != null
            && var1.y() == var2.y()
            && (var1.x() - var2.x()) * var3 + (var1.z() - var2.z()) * var4 >= 1
            && (var1.x() - var2.x()) * Math.abs(var4) + (var1.z() - var2.z()) * Math.abs(var3) == 0;
         double var6 = (this.mc.player.getX() - ((double)var2.x() + 0.5)) * (double)var3 + (this.mc.player.getZ() - ((double)var2.z() + 0.5)) * (double)var4;
         double var8 = this.mc.player.getVelocity().x * (double)var3 + this.mc.player.getVelocity().z * (double)var4;
         if (!var5 && Math.abs(var6) < 1.2 && var8 > 0.02) {
            // 3.0.103 (người dùng): chạm đích là thả HẾT phím cho khỏi trôi; chỉ bấm S khi trượt tiếp sẽ ra khỏi khối
            this.mc.player.setYaw(MotionMath.yaw((double)var3, (double)var4));
            Input.key(this.mc.options.forwardKey, false);
            Input.key(this.mc.options.leftKey, false);
            Input.key(this.mc.options.rightKey, false);
            Input.key(this.mc.options.jumpKey, false);
            boolean var10 = var6 + var8 * 1.2 > 0.35;
            Input.key(this.mc.options.backKey, var10);
            this.status = var10 ? "Vừa chạm khối đích → hãm (S) để không trượt quá" : "Vừa chạm khối đích → thả hết phím";
         } else {
            this.landBrakeUntil = -1000;
         }
      }
   }

   private int parkourIdx = -1;
   private List<PathSearch.Cell> parkourPath;
   private int parkourRunups;
   private int parkourBackTicks;

   private boolean pillarTrimTick() {
      if (this.cleanupMode || this.sweeping || this.repairing || this.journal == null || this.mc.player == null || this.mc.world == null) {
         return false;
      } else {
         if (this.trimPos == null) {
            if (!this.mc.player.isOnGround() || this.mining != null) {
               return false;
            }

            BlockPos var1 = BlockPos.ofFloored(this.mc.player.getX(), this.mc.player.getY() - 0.2, this.mc.player.getZ());
            BlockPos var2 = var1.down();
            if (!this.journal.placed.containsKey(var1) || !this.journal.placed.containsKey(var2) || this.trimSkip.contains(var2)) {
               return false;
            }

            if (this.plan != null && this.plan.at(var2) != null) {
               return false;
            }

            if (this.mc.world.getBlockState(var2).isReplaceable()) {
               this.journal.remove(var2);
               return false;
            }

            Direction var3 = null;

            for (Direction var5 : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
               if (this.mc.world.getBlockState(var2.offset(var5)).isReplaceable()
                  && this.mc.world.getBlockState(var1.offset(var5)).isReplaceable()
                  && this.mc.world.getBlockState(var1.up().offset(var5)).isReplaceable()
                  && this.mc.world.getBlockState(var1.up().up().offset(var5)).isReplaceable()) {
                  var3 = var5;
                  break;
               }
            }

            if (var3 == null) {
               this.trimSkip.add(var2.toImmutable());
               return false;
            }

            this.trimPos = var2.toImmutable();
            this.trimDir = var3;
            this.trimTicks = 0;
            AutoBuildMod.LOGGER.info("[AutoBuild] Bắc cột: đập khối kê cũ {} dưới chân (không để lại khối kê), chìa người về {}", this.trimPos.toShortString(), var3);
         }

         BlockPos var6 = this.trimPos.up();
         BlockState var7 = this.mc.world.getBlockState(this.trimPos);
         if (++this.trimTicks > 160) {
            AutoBuildMod.LOGGER.info("[AutoBuild] Bắc cột: 8 giây chưa đập được {} → để thu dọn sau", this.trimPos.toShortString());
            this.mc.interactionManager.cancelBlockBreaking();
            this.trimSkip.add(this.trimPos);
            this.trimPos = null;
            this.mining = null;
            return false;
         } else if (var7.isReplaceable()) {
            this.journal.remove(this.trimPos);
            this.sweepNow.remove(this.trimPos);
            this.mining = null;
            Vec3d var8 = new Vec3d((double)var6.getX() + 0.5, this.mc.player.getY(), (double)var6.getZ() + 0.5);
            double var9 = var8.x - this.mc.player.getX();
            double var11 = var8.z - this.mc.player.getZ();
            if (var9 * var9 + var11 * var11 > 0.01 && this.trimTicks < 150) {
               this.status = "Bắc cột: về giữa khối";
               this.creepToward(var8);
               return true;
            } else {
               this.trimPos = null;
               this.lastProgress = this.ticks;
               return false;
            }
         } else {
            this.status = "Bắc cột: đập khối kê cũ dưới chân " + this.trimPos.toShortString();
            Input.sneak(this.mc, true, this.ticks);
            this.mining = this.trimPos;
            if (this.visiblePoint(this.trimPos) == null) {
               int var13 = this.trimDir == Direction.EAST ? 1 : (this.trimDir == Direction.WEST ? -1 : 0);
               int var14 = this.trimDir == Direction.SOUTH ? 1 : (this.trimDir == Direction.NORTH ? -1 : 0);
               Vec3d var15 = new Vec3d((double)var6.getX() + 0.5 + (double)var13 * 0.78, this.mc.player.getY(), (double)var6.getZ() + 0.5 + (double)var14 * 0.78);
               this.creepToward(var15);
               return true;
            } else {
               if (this.mc.player.getVelocity().horizontalLengthSquared() > this.slideLimit()) {
                  this.brake();
               }

               this.breakBlock(var7);
               return true;
            }
         }
      }
   }

   private Vec3d idlePos;
   private int idleTicks;
   private BlockPos idleFor;
   private int idleHits;
   private int idleHitAt = -100000;

   /**
    * 3.0.84: MOVE mà nhân vật không nhúc nhích 4 giây (bất kể đang chờ nhánh nào) → tính lại đường; lần 2 liền cho cùng
    * khối thì bỏ tạm khối đó. Lỗi cũ: sau "đặt rồi nhưng không hiện → làm lại NGAY" đứng v=0 hơn 20 giây mới bị watchdog cắt.
    */
   private boolean moveIdleGuard() {
      Vec3d var1 = this.mc.player.getPos();
      if (this.stageTicks <= 1) {
         this.idleTicks = 0;
      }

      if (this.idlePos != null && var1.squaredDistanceTo(this.idlePos) < 1.0E-4 && this.trimPos == null && !this.sweeping) {
         this.idleTicks++;
      } else {
         this.idleTicks = 0;
      }

      this.idlePos = var1;
      if (this.idleTicks < 80) {
         return false;
      } else {
         this.idleTicks = 0;
         BlockPos var2 = this.target == null ? null : this.target.pos().toImmutable();
         if (var2 == null || !var2.equals(this.idleFor) || this.ticks - this.idleHitAt > 600) {
            this.idleHits = 0;
         }

         this.idleFor = var2;
         this.idleHitAt = this.ticks;
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] [MOVE] Đứng yên 4 giây không nhúc nhích (trạng thái: {}) → {}",
               this.status,
               this.idleHits == 0 ? "đứng thẳng, tính lại đường" : "bỏ tạm khối này, làm khối khác"
            );
         this.release();
         if (this.idleHits++ == 0) {
            this.standUntil = this.ticks + 12;
            if (this.mc.player.isOnGround()) {
               Input.key(this.mc.options.jumpKey, true);
            }

            this.beginGoals(this.cleanupMode);
         } else {
            this.idleHits = 0;
            this.navigationFailed("Đứng yên trong lúc đi quá lâu");
         }

         return true;
      }
   }

   private void move() {
      this.qualityPrimeWaterlog();
      if (this.backoffTicks > 0) {
         this.backoffTicks--;
         this.rbLastPos = null;
         this.rbLastVel = null;
         Input.forceSneak(this.mc, false, this.ticks);
         if (this.backoffTicks > 12) {
            // 3.0.81: đứng yên hẳn vài tick cho server khớp vị trí
            this.status = "Bị giật lại → dừng hẳn";
            if (this.mc.player.getVelocity().horizontalLengthSquared() > 1.0E-4) {
               this.brake();
            }
         } else {
            // rồi lùi ngược hướng vừa đi ~1 ô (nhìn vẫn theo hướng cũ, bấm S)
            this.status = "Bị giật lại → lùi ra rồi đi tiếp";
            Input.key(this.mc.options.backKey, true);
            if (this.hazardNear()) {
               Input.sneak(this.mc, true, this.ticks);
            }
         }

         if (this.backoffTicks == 0) {
            this.lastPosition = this.mc.player.getPos();
            this.stuckTicks = 0;
            this.mvAnchor = null;
            if (this.replanAfterBackoff) {
               this.replanAfterBackoff = false;
               AutoBuildMod.LOGGER.info("[AutoBuild] Đã lùi ra khỏi chỗ bị giật → tìm đường khác (né ô vừa kẹt), vẫn giữ khối đang làm");
               this.release();
               if (this.target == null && this.supportTarget == null && this.temporary == null && !this.cleanupMode) {
                  this.climbMove = false;
                  this.planeMove = false;
                  this.nextTarget(true);
               } else {
                  this.beginGoals(this.cleanupMode);
               }
            }
         }

         return;
      }

      if (this.moveIdleGuard()) {
         return;
      }

      if (this.pillarTrimTick()) {
         return;
      }

      if (!this.trySweepTrail()) {
         this.status = this.approachOnly ? "Đi tới vùng công trình" : "Đang đi tới vị trí đặt";
         if (this.pathIndex < this.path.size() || !this.climbMove && (!this.planeMove || this.target != null || this.supportTarget != null)) {
            if (this.pathIndex >= this.path.size()) {
               if (this.approachOnly) {
                  if (!this.cleanupMode && this.target == null) {
                     this.beginScan();
                  } else {
                     this.beginGoals(this.cleanupMode);
                  }
               } else if (this.cleanupMode) {
                  this.setStage(BuildController.Stage.MINE);
               } else if (this.repairGoal != null) {
                  this.repairGoal = null;
                  if (!this.tryRepairNow()) {
                     this.nextTarget(true);
                  }
               } else if (this.pit != null && this.target != null && this.pit.target().pos().equals(this.target.pos())) {
                  this.pitStep = 0;
                  this.pitTicks = 0;
                  this.setStage(BuildController.Stage.PIT);
               } else {
                  this.aim = null;
                  this.setStage(BuildController.Stage.EQUIP);
               }
            } else if (this.repairGoal != null && this.stageTicks % 4 == 0 && this.tryRepairNow()) {
               this.repairGoal = null;
            } else {
               if (!this.approachOnly
                  && !this.cleanupMode
                  && this.repairGoal == null
                  && this.temporary == null
                  && this.current() != null
                  && !(this.selfPillarBase != null && this.current() == this.supportTarget)
                  && this.mc.player.isOnGround()
                  && !this.occupies(this.current().pos())
                  && this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(this.current().pos())) <= (this.reach() + 0.9) * (this.reach() + 0.9)) {
                  PlacementSolver.Aim var1 = PlacementSolver.solve(this.mc, this.mc.player.getEyePos(), this.current(), this.reach(), true, this.seed());
                  if (var1 != null) {
                     this.aim = var1;
                     this.setStage(BuildController.Stage.EQUIP);
                     this.equip();
                     return;
                  }
               }

               // 3.0.82: mỗi lần chạm đất (tối đa 4 tick/lần) xem quanh có ô đặt được ngay không → đặt luôn rồi đi tiếp
               if (this.moveDepth == 0 && this.mc.player.isOnGround() && this.ticks - this.nearbyCheckAt >= 4) {
                  this.nearbyCheckAt = this.ticks;
                  if (this.nearbyBuild("move")) {
                     return;
                  }
               }

               this.catchUpPath();
               this.noteNavProgress();
               PathSearch.Cell var18 = this.path.get(this.pathIndex);
               Vec3d var2 = Navigation.feet(var18);
               double var3 = Math.hypot(this.mc.player.getX() - var2.x, this.mc.player.getZ() - var2.z);
               boolean var5 = this.pathIndex == this.path.size() - 1;
               // 3.0.98: đang lội nước (giữ nhảy để nổi) thì chân ít chạm đất → vẫn tính là tới ô khi đã ở trên ô đó
               boolean var48 = !var5 && this.inWaterNow();
               if (var3 < (var5 ? 0.35 : 0.5)
                  && Math.abs(this.mc.player.getY() - var2.y) < (var48 ? 0.95 : 0.55)
                  && (this.mc.player.isOnGround() || var48 && this.mc.player.getY() >= var2.y - 0.2)) {
                  this.pathIndex++;
                  // 3.0.100: đi thêm được 1 ô trên đường (ô xa nhất từng tới của đường này) cũng là tiến triển
                  if (this.path != this.progressPath) {
                     this.progressPath = this.path;
                     this.progressPathMax = 0;
                  }

                  if (this.pathIndex > this.progressPathMax) {
                     this.progressPathMax = this.pathIndex;
                     this.navProgressAt = this.ticks;
                  }
                  this.stuckTicks = 0;
                  this.targetTicks = 0;
                  if (this.pathIndex < this.path.size() && this.moveDepth++ < 3) {
                     this.move();
                  } else {
                     this.moveDepth = 0;
                     if (this.pathIndex >= this.path.size()) {
                        if (!this.climbMove && (!this.planeMove || this.target != null || this.supportTarget != null)) {
                           if (this.approachOnly) {
                              if (!this.cleanupMode && this.target == null) {
                                 this.beginScan();
                              } else {
                                 this.beginGoals(this.cleanupMode);
                              }
                           } else if (this.cleanupMode) {
                              this.setStage(BuildController.Stage.MINE);
                           } else if (this.repairGoal != null) {
                              this.repairGoal = null;
                              if (!this.tryRepairNow()) {
                                 this.nextTarget(true);
                              }
                           } else if (this.pit != null && this.target != null && this.pit.target().pos().equals(this.target.pos())) {
                              this.pitStep = 0;
                              this.pitTicks = 0;
                              this.setStage(BuildController.Stage.PIT);
                           } else if (!this.sweepScaffold(true)) {
                              this.aim = null;
                              this.setStage(BuildController.Stage.EQUIP);
                              this.equip();
                           }
                        } else {
                           this.climbMove = false;
                           this.planeMove = false;
                           this.nextTarget(true);
                        }
                     }
                  }
               } else {
                  this.moveDepth = 0;
                  PathSearch.Cell var6 = this.path.get(Math.max(0, this.pathIndex - 1));
                  if (!this.terrain.clear(var18)) {
                     this.navigationFailed("Đường đi vừa bị chặn.");
                  } else if (!this.terrain.standable(var18)) {
                     BlockPos var7 = Navigation.pos(var18).down();
                     BlockState var8 = this.terrain.designFloor(var7);
                     BlockState var9 = var8 != null
                        ? var8
                        : (this.terrain.canScaffoldAt(var7) && this.scaffoldAllowed() ? this.terrain.pillarStateAt(var7) : null);
                     if (var9 == null) {
                        this.navigationFailed(
                           "Không thể đặt khối kê tại "
                              + var7.toShortString()
                              + (
                                 this.plan != null && this.plan.at(var7) != null
                                    ? " (ô cần " + this.plan.at(var7).state().getBlock().getName().getString() + ", không có trong túi)"
                                    : ""
                              )
                        );
                     } else {
                        if (var8 != null) {
                           AutoBuildMod.LOGGER
                              .info("[AutoBuild] Sàn đường đi {} là khối cần xây → đặt {} luôn", var7.toShortString(), var8.getBlock().getName().getString());
                           SchematicPlan.Target var10 = this.plan.at(var7);
                           if (var10 != null) {
                              this.queue.remove(var10);
                           }
                        }

                        this.temporary = new SchematicPlan.Target(var7, var9);
                        this.bridgeTicks = 0;
                        this.pillar = var6.x() == var18.x() && var6.z() == var18.z() && var18.y() == var6.y() + 1;
                        this.inventoryAttempts = 0;
                        this.aim = null;
                        this.setStage(BuildController.Stage.EQUIP);
                        this.equip();
                     }
                  } else if (!this.floorConfirmed(var18)) {
                     this.status = "Chờ server xác nhận khối dưới chân (ping " + this.pingMs() + " ms)";
                     if (this.mc.player.isOnGround() && this.mc.player.getVelocity().horizontalLengthSquared() > 0.002) {
                        this.brake();
                     }

                     Input.sneak(this.mc, true, this.ticks);
                  } else if (this.stageTicks % 5 == 0 && !this.terrain.edgeStillValid(var6, var18)) {
                     this.navigationFailed("Đường đi thay đổi; đang tính lại.");
                  } else if (this.moveStallCheck(var18)) {
                     return;
                  } else {
                     boolean var19 = this.driveBy();
                     // 3.0.87: đang lội nước chảy → quay mặt chếch ngược dòng để đi thẳng tới điểm (nước đẩy 0,014/tick, đi trong nước 0,02/tick)
                     float var20 = this.flowYaw(var2.x - this.mc.player.getX(), var2.z - this.mc.player.getZ());
                     int var40 = this.parkourGap(var6, var18);
                     // 3.0.82: độ lệch ngang khỏi đường thẳng (ô trước → ô tới)
                     double var44 = this.lateralOff(var6, var18);
                     // 3.0.82: chỉ đi ngồi ở chỗ SẮP DỪNG / SẮP RẼ (quán tính có thể trượt quá mép) hoặc khi đang lệch hẳn khỏi đường,
                     // và cạnh đó là vực nguy hiểm. Đường thẳng thì bước giữa ô, không cần ngồi (bản cũ ngồi suốt → bò).
                     boolean var45 = var5 || this.turnAt(this.pathIndex);
                     if (var18.y() >= this.mc.player.getBlockY()
                        && this.mc.player.isOnGround()
                        && var40 == 0
                        && (var45 && var3 < 1.3 || var44 > 0.35)
                        && this.hazardNear()) {
                        Input.sneak(this.mc, true, this.ticks);
                        if (this.ticks - this.hazardSaidAt > 200) {
                           this.hazardSaidAt = this.ticks;
                           AutoBuildMod.LOGGER.info("[AutoBuild] Sắp dừng/rẽ cạnh vực nguy hiểm → ngồi đoạn ngắn này (đường thẳng thì đi thường)");
                        }
                     }

                     // 3.0.82: đang bay (nhảy/rơi) mà đã vượt qua điểm tới → KHÔNG quay ngoắt 180° giữa không trung
                     boolean var46 = !this.mc.player.isOnGround() && Math.abs(MotionMath.wrap(var20 - this.mc.player.getYaw())) > 60.0F;
                     // 3.0.122: vừa bấm chuột phải (cú bấm còn chờ game xử lý / gói góc nhìn chưa đi) → chưa quay đầu
                     if (!var19 && this.ticks >= this.placeLockUntil && !Input.usePending()) {
                        if (var3 > 0.25 && !var46) {
                           this.mc.player.setYaw(this.mc.player.getYaw() + MotionMath.wrap(var20 - this.mc.player.getYaw()));
                        }

                        this.mc.player.setPitch(this.walkPitch);
                     }

                     float var21 = MotionMath.wrap(var20 - this.mc.player.getYaw());
                     if (var46) {
                        this.status = "Đang bay qua điểm tới, giữ hướng";
                     } else if (this.slipperyFloor()
                        && var40 == 0
                        && var3 < 1.8
                        && this.mc.player.getVelocity().horizontalLengthSquared() > 0.004
                        && (var45 || var44 > 0.35 && this.hazardNear())) {
                        // 3.0.81: trên băng chỉ phanh khi sắp dừng (điểm cuối), sắp rẽ, hoặc cạnh vực nguy hiểm
                        this.status = "Phanh trên băng";
                        this.brake();
                     } else {
                        if (Math.abs(var21) < 67.5F) {
                           Input.key(this.mc.options.forwardKey, true);
                        }

                        if (Math.abs(var21) > 112.5F) {
                           Input.key(this.mc.options.backKey, true);
                        }

                        if (var21 > 22.5F && var21 < 157.5F) {
                           Input.key(this.mc.options.rightKey, true);
                        }

                        if (var21 < -22.5F && var21 > -157.5F) {
                           Input.key(this.mc.options.leftKey, true);
                        }

                        if ((double)var18.y() > this.mc.player.getY() + 0.6 && this.mc.player.isOnGround() && var40 == 0) {
                           Input.key(this.mc.options.jumpKey, true);
                        }

                        boolean var22 = this.path.size() - this.pathIndex > 4;
                        int var11 = var18.x() - var6.x();
                        int var12 = var18.z() - var6.z();

                        for (int var13 = this.pathIndex; var22 && var13 < Math.min(this.path.size(), this.pathIndex + 5); var13++) {
                           PathSearch.Cell var14 = this.path.get(var13 - 1);
                           PathSearch.Cell var15 = this.path.get(var13);
                           if (var15.y() != var18.y() || var15.x() - var14.x() != var11 || var15.z() - var14.z() != var12 || !this.terrain.standable(var15)) {
                              var22 = false;
                           }
                        }

                        if (var22 && Math.abs(var21) < 20.0F && var44 < 0.4) {
                           Input.sprint(this.mc, true, this.ticks);
                           // 3.0.82: vừa chạy vừa nhảy khi phía trước còn ≥ 6 ô thẳng, phẳng, đứng được, đủ trần, đang đi giữa đường.
                           // Cú nhảy bay ~3,6 ô nên cần 6 ô thẳng để rơi vẫn trên đường; cạnh đường có hố cũng không sao vì đi thẳng.
                           if (Math.abs(var21) < 10.0F
                              && var44 < 0.25
                              && this.mc.player.isOnGround()
                              && this.straightRun() >= 6
                              && !this.slipperyFloor()
                              && this.headroomAhead(6)
                              && !this.hazardNear()
                              && this.mc.player.isSprinting()
                              && !this.mc.player.isSneaking()
                              && this.mc.player.getVelocity().horizontalLengthSquared() > 0.04) {
                              // 3.0.92: chỉ nhảy khi ĐANG chạy nhanh thật (sprint) và đã có đà — nhảy lúc đi thường không nhanh hơn
                              Input.key(this.mc.options.jumpKey, true);
                           }
                        }

                        if (var40 > 0) {
                           this.parkourStep(var6, var18, var40);
                        } else {
                           this.landBrake(var18);
                        }

                        if (this.planeMove && var40 == 0 && (var45 && var3 < 1.3 || var44 > 0.35) && this.shouldSneakAtEdge(var18)) {
                           Input.sneak(this.mc, true, this.ticks);
                        }

                        // 3.0.78: bước kế tiếp là nhảy xuống thấp hơn → phải ĐỨNG thẳng. Nếu còn ngồi
                        // (release() giữ ngồi khi đang chìa ra mép) thì game không cho bước khỏi mép →
                        // nhân vật cứ nhích qua nhích lại trên khối lá như trong video.
                        if (this.mc.player.isOnGround() && (double)var18.y() < this.mc.player.getY() - 0.6 || this.ticks < this.standUntil || var40 > 0) {
                           Input.forceSneak(this.mc, false, this.ticks);
                        }

                        // 3.0.98 (người dùng): lội nước thì giữ nút nhảy để nổi lên — đi nhanh hơn, đi ngược dòng được;
                        // không ngồi trong nước (ngồi thì không đủ đà, luôn bị nước đẩy). Bước kế tiếp thấp hơn thì không nhảy.
                        boolean var47 = this.inWaterNow();
                        if (var47 && !((double)var18.y() < this.mc.player.getY() - 0.6)) {
                           Input.key(this.mc.options.jumpKey, true);
                           Input.forceSneak(this.mc, false, this.ticks);
                        }

                        if (this.mc.player.getPos().squaredDistanceTo(this.lastPosition) < 3.0E-4) {
                           this.stuckTicks++;
                        } else {
                           this.stuckTicks = 0;
                        }

                        this.lastPosition = this.mc.player.getPos();
                        if (this.stuckTicks >= 10 && this.stuckTicks % 10 == 0 && this.mc.player.isOnGround() && var40 == 0) {
                           Input.key(this.mc.options.jumpKey, true);
                        }

                        double var23 = (double)Math.min(var6.y(), var18.y()) - this.mc.player.getY();
                        double var24 = Math.min(
                           Navigation.feet(var6).squaredDistanceTo(this.mc.player.getPos().x, Navigation.feet(var6).y, this.mc.player.getPos().z),
                           Navigation.feet(var18).squaredDistanceTo(this.mc.player.getPos().x, Navigation.feet(var18).y, this.mc.player.getPos().z)
                        );
                        if (!this.mc.player.isOnGround() || !(var23 > 0.6) && !(var24 > (var47 ? 12.25 : 6.25))) {
                           if (this.stuckTicks > 30 || this.stageTicks > 800) {
                              if (this.replans++ < 4) {
                                 this.release();
                                 this.beginGoals(this.cleanupMode);
                                 return;
                              }

                              this.navigationFailed("Nhân vật bị kẹt; thử đường khác.");
                           }
                        } else {
                           AutoBuildMod.LOGGER.info("[AutoBuild] {} khi đi tới {} → tìm đường lại", var23 > 0.6 ? "Bị ngã" : "Lệch đường", var18);
                           // 3.0.88: ngã sâu xuống dưới mặt tầng đang xây mà có home ở tầng này → /home lên ngay
                           if (var23 > 0.6
                              && (this.target == null || this.currentLayer == null || this.target.pos().getY() >= this.currentLayer - 1)
                              && this.tryHomeUp("Bị ngã")) {
                              return;
                           }

                           if (!this.planeMove && !this.climbMove) {
                              if (this.replans++ < 6) {
                                 this.release();
                                 this.beginGoals(this.cleanupMode);
                              } else {
                                 this.navigationFailed("Liên tục bị ngã/lệch đường.");
                              }
                           } else {
                              this.planeMove = false;
                              this.climbMove = false;
                              this.release();
                              if (this.target != null && !this.queue.contains(this.target)) {
                                 SchematicPlan.Target var17 = this.plan.at(this.target.pos());
                                 if (var17 != null) {
                                    this.queue.addFirst(var17);
                                 }
                              }

                              this.nextTarget(true);
                           }
                        }
                     }
                  }
               }
            }
         } else {
            this.climbMove = false;
            this.planeMove = false;
            this.nextTarget(true);
         }
      }
   }

   private void equip() {
      this.qualityPrimeWaterlog();
      SchematicPlan.Target var1 = this.current();
      if (var1 == null) {
         this.beginScan();
      } else {
         if (this.aim != null && this.aim.sneak()) {
            Input.sneak(this.mc, true, this.ticks);
         }

         if (StateMatch.configurable(this.mc.world.getBlockState(var1.pos()), var1.state())) {
            this.setStage(BuildController.Stage.AIM);
         } else {
            Item var2 = Materials.item(this.mc.world.getBlockState(var1.pos()), var1.state());
            boolean var3 = this.temporary != null || this.supportTarget != null;
            if (InventoryAccess.count(this.mc, var2) == 0 && !var3 && this.config.fastMode()) {
               SchematicPlan.Target var4 = this.plan.at(var1.pos());
               if (var4 != null) {
                  SchematicPlan.Target var5 = this.resolve(var4);
                  if (var5.state() != var1.state()) {
                     this.target = var5;
                     this.aim = null;
                     return;
                  }

                  var2 = this.materialFor(var4, this.mc.world.getBlockState(var1.pos()));
               }
            }

            if (InventoryAccess.count(this.mc, var2) == 0) {
               int var6 = var3 ? Math.min(this.config.materialMaxStacks, 2) * var2.getMaxCount() : this.scanNeed(var2);
               this.collect(var2, var3 ? BuildController.Stage.EQUIP : BuildController.Stage.SELECT, var6);
            } else if (this.mc.player.getMainHandStack().isOf(var2)) {
               this.inventoryAttempts = 0;
               this.setStage(BuildController.Stage.AIM);
               this.aim();
            } else if (this.unacked > 0 || !InventoryAccess.synced(this.mc) || this.mc.currentScreen != null || this.ticks < this.placeLockUntil) {
               this.status = "Chờ server xong thao tác trước rồi mới đổi sang " + var2.getName().getString();
            } else if (InventoryAccess.equip(this.mc, var2, this.config.workingHotbarSlot)) {
               this.inventoryAttempts = 0;
               this.setStage(BuildController.Stage.AIM);
               this.aim();
            } else if (++this.inventoryAttempts > 60) {
               this.inventoryAttempts = 0;
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] Chưa đưa được {} lên tay (tay đang cầm {}, con trỏ {}) → để khối này lại sau",
                     new Object[]{
                        var2.getName().getString(),
                        this.mc.player.getMainHandStack().getItem().getName().getString(),
                        this.mc.player.currentScreenHandler.getCursorStack().isEmpty() ? "trống" : "đang giữ đồ"
                     }
                  );
               if (this.target != null) {
                  this.deferred.put(this.target.pos(), this.ticks + 100);
               }

               this.nextTarget(true);
            }
         }
      }
   }

   private int scanNeed(Item var1) {
      for (BuildController.MaterialRow var3 : this.layerMaterials) {
         if (var3.item() == var1) {
            return Math.max(1, var3.needed());
         }
      }

      return var1.getMaxCount();
   }

   private int layerNeed(BuildController.Scan var1, Integer var2, Item var3) {
      return this.config.layerMode && var2 != null
         ? Math.max(1, var1.layerNeed.getOrDefault(var2, Map.of()).getOrDefault(var3, 1))
         : var1.totalNeed.getOrDefault(var3, 1);
   }

   public int layerIndex(int var1) {
      return this.plan == null ? 0 : var1 - this.plan.minY + 1;
   }

   public int layerCount() {
      return this.plan == null ? 0 : this.plan.maxY - this.plan.minY + 1;
   }

   private void aim() {
      this.qualityPrepareHand();
      if (this.qualityWaterlogAim()) {
         return;
      }

      SchematicPlan.Target var1 = this.current();
      if (var1 == null) {
         this.beginScan();
      } else if (!this.tryRepairNow()) {
         boolean var2 = StateMatch.configurable(this.mc.world.getBlockState(var1.pos()), var1.state());
         if (this.aim != null && this.aim.sneak()) {
            Input.sneak(this.mc, true, this.ticks);
         }

         if (this.done(this.mc.world.getBlockState(var1.pos()), var1.state())) {
            this.finishTarget();
         } else if (!var2 && !this.mc.player.getMainHandStack().isOf(Materials.item(this.mc.world.getBlockState(var1.pos()), var1.state()))) {
            this.setStage(BuildController.Stage.EQUIP);
         } else if (this.selfPillarBase != null && var1 == this.supportTarget && !this.pillar && this.mc.player.isOnGround() && this.pillarTrimTick()) {
            return;
         } else if (!this.pillar
            && this.selfPillarBase != null
            && var1 == this.supportTarget
            && !this.mc.player.getBlockPos().equals(var1.pos())) {
            if (var1.pos().equals(this.selfPillarBase)) {
               this.beginGoals(false);
            } else {
               AutoBuildMod.LOGGER.info("[AutoBuild] Không đứng trong cột kê {} → đặt khối kê từ bên cạnh", var1.pos().toShortString());
               this.selfPillarBase = null;
            }
         } else if (this.pillar) {
            this.pillarTick(var1);
         } else if (!var2 && !this.pillar && this.temporary == null && this.blocksMe(var1)) {
            if (this.liftOverLowFloor(var1)) {
               return;
            } else if (this.canJumpPlace(var1)) {
               this.pillar = true;
               this.aim = null;
               this.status = "Đứng chắn → nhảy lên rồi đặt";
            } else {
               this.status = "Đứng chắn chỗ đặt → lùi ra";
               this.stepAway(var1.pos());
               if (++this.stepTicks > 20) {
                  this.stepTicks = 0;
                  this.beginGoals(false);
               }
            }
         } else if (!var2
            && var1 == this.target
            && this.supportTarget == null
            && this.temporary == null
            && !this.pillar
            && !Materials.waterlog(this.mc.world.getBlockState(var1.pos()), var1.state())
            && !this.hasSupport(var1.pos())) {
            SchematicPlan.Target var11 = this.plan.at(var1.pos());
            BuildController.BuildPlan var13 = this.resolveBuild(var11 != null ? var11 : var1);
            if (var13 != null) {
               if (var11 != null && !var13.root().pos().equals(var1.pos()) && !this.queue.contains(var11)) {
                  this.queue.addFirst(var11);
               }

               this.aim = null;
               this.startBuildPlan(var13, var11);
            } else {
               SchematicPlan.Target var17 = rowFacing(var1.state()) != null ? null : this.findRoot(var1);
               if (var17 != null) {
                  SchematicPlan.Target var20 = this.plan.at(var1.pos());
                  if (var20 != null && !this.queue.contains(var20)) {
                     this.queue.addFirst(var20);
                  }

                  this.queue.remove(var17);
                  this.deferred.remove(var17.pos());
                  this.target = this.resolve(var17);
                  this.aim = null;
                  this.beginGoals(false);
               } else if (rowFacing(var1.state()) != null) {
                  SchematicPlan.Target var21 = this.plan.at(var1.pos());
                  if (var21 != null && !this.queue.contains(var21)) {
                     this.queue.addFirst(var21);
                  }

                  if (!this.tryRowStart(List.of(var1), List.of(), false, null)) {
                     this.navigationFailed("Hàng hopper chưa có chỗ đặt khối tạm ở đầu hàng.");
                  }
               } else {
                  BlockPos var22 = this.supportSpot(var1.pos());
                  if (var22 == null) {
                     this.navigationFailed("Khối lơ lửng chưa có chỗ kê điểm tựa.");
                  } else {
                     this.supportTarget = new SchematicPlan.Target(var22, this.pillarState(var22));
                     this.aim = null;
                     this.bridgeTicks = 0;
                     this.setStage(BuildController.Stage.EQUIP);
                  }
               }
            }
         } else {
            boolean var3 = this.temporary != null && !this.pillar || var1 == this.supportTarget;
            if (var3) {
               Input.sneak(this.mc, true, this.ticks);
            }

            if (this.aim == null || !PlacementSolver.stillReachable(this.mc, this.aim, this.reach()) || this.aimInvalidTicks > 30) {
               if (this.aimInvalidTicks > 30) {
                  this.aimAttempt++;
               }

               this.aimInvalidTicks = 0;
               this.aim = PlacementSolver.solve(this.mc, this.mc.player.getEyePos(), var1, this.reach(), true, this.seed(), var3 || this.overhanging());
            }

            if (this.aim == null && !var2 && var1 == this.target && this.temporary == null && this.supportTarget == null && !this.pillar && this.spoofFor(var1)
               )
             {
               if (this.spoof == null || this.spoofHold == 0 && this.stageTicks % 10 == 0) {
                  this.spoof = PlacementSolver.solveSpoof(this.mc, this.mc.player.getEyePos(), var1, this.reach(), this.seed(), this.heads());
               }

               if (this.spoof != null) {
                  if (this.spoof.aim().sneak()) {
                     Input.sneak(this.mc, true, this.ticks);
                  }

                  boolean var12 = this.mc.player.isOnGround()
                     && this.mc.player.isSneaking() == this.spoof.aim().sneak()
                     && this.mc.player.isInSneakingPose() == this.spoof.aim().sneak();
                  this.status = "Đặt (góc gửi riêng): " + var1.state().getBlock().getName().getString() + " @ " + var1.pos().toShortString();
                  if (this.mc.player.isOnGround() && this.mc.player.getVelocity().horizontalLengthSquared() > 0.004) {
                     this.brake();
                  }

                  float var16 = PlacementSolver.aimYaw(this.mc, this.spoof.aim());
                  float var19 = PlacementSolver.aimPitch(this.mc, this.spoof.aim());
                  boolean var24 = Math.abs(MotionMath.wrap(var16 - this.mc.player.getYaw())) > 0.05F || Math.abs(var19 - this.mc.player.getPitch()) > 0.05F;
                  if (this.spoofHold == 0) {
                     if (var12 && this.cooldown <= 0 && !var24) {
                        if (!PlacementSolver.spoofValidNow(this.mc, this.spoof, var1, this.reach())) {
                           if (this.stageTicks > 40) {
                              this.spoof = null;
                              this.beginGoals(false);
                           }

                           return;
                        }

                        this.spoofHit = PlacementSolver.ray(this.mc, this.reach());
                        this.lookAt(this.mc.player.getYaw() + MotionMath.wrap(this.spoof.sendYaw() - this.mc.player.getYaw()), this.spoof.sendPitch());
                        this.spoofHold = 1;
                        return;
                     }

                     this.lookAt(var16, var19);
                     return;
                  }

                  if (var12
                     && this.rotationSent()
                     && !(Math.abs(this.mc.player.getPitch() - this.spoof.sendPitch()) > 0.01F)
                     && !(Math.abs(MotionMath.wrap(this.mc.player.getYaw() - this.spoof.sendYaw())) > 0.01F)) {
                     if (this.spoofClick(var1, this.spoof)) {
                        this.spoofHold = 0;
                     } else {
                        this.spoofHold = 0;
                        if (this.stageTicks > 40) {
                           this.spoof = null;
                           this.beginGoals(false);
                        }
                     }

                     return;
                  }

                  if (++this.spoofHold > 25) {
                     this.spoofHold = 0;
                  } else if (Math.abs(this.mc.player.getPitch() - this.spoof.sendPitch()) > 0.01F
                     || Math.abs(MotionMath.wrap(this.mc.player.getYaw() - this.spoof.sendYaw())) > 0.01F) {
                     this.lookAt(this.mc.player.getYaw() + MotionMath.wrap(this.spoof.sendYaw() - this.mc.player.getYaw()), this.spoof.sendPitch());
                  }

                  return;
               }
            }

            boolean var4 = !this.pillar
               && !var2
               && var1.pos().getY() <= this.mc.player.getBlockY()
               && this.hasSupport(var1.pos())
               && !(tallCollision(var1.state()) && var1.pos().getY() + 1.5 > this.mc.player.getY() + 0.001)
               // 3.0.98: đang đứng trong nước → không ngồi ra mép (nước đẩy trôi, không đủ đà tới mép)
               && !this.inWaterNow();
            if (this.aim == null && !var3 && var4) {
               this.aim = PlacementSolver.solve(this.mc, this.mc.player.getEyePos(), var1, this.reach(), true, this.seed(), true);
            }

            if (this.aim == null && var4 && !var3 && this.leanFailed.contains(var1.pos())) {
               var4 = false;
            }

            if (this.aim == null && var4 && !var3 && this.bridgeTicks == 0) {
               Vec3d var5 = edgeEye(Navigation.feet(Navigation.cell(this.mc.player.getBlockPos())), var1.pos());
               if (PlacementSolver.solve(this.mc, var5, var1, this.reach(), true, this.seed(), true) == null
                  && (!this.spoofFor(var1) || PlacementSolver.solveSpoof(this.mc, var5, var1, this.reach(), this.seed(), null) == null)) {
                  var4 = false;
               }
            }

            if (this.aim == null && var4 && !var3 && this.liftOverLowFloor(var1)) {
               return;
            }

            if (this.aim == null && var4 && !var3) {
               if (++this.bridgeTicks > 100) {
                  this.bridgeTicks = 0;
                  this.markLeanFailed(var1);
                  this.navigationFailed("Ngồi ra mép vẫn chưa đặt được " + var1.pos().toShortString());
               } else {
                  Input.sneak(this.mc, true, this.ticks);
                  this.status = "Ngồi ra sát mép để đặt: " + var1.state().getBlock().getName().getString();
                  if (this.mc.player.isSneaking() && this.mc.player.isOnGround()) {
                     BlockPos var15 = BlockPos.ofFloored(this.mc.player.getX(), this.mc.player.getY() - 0.2, this.mc.player.getZ());
                     if (this.ackWait(var15)) {
                        this.status = "Chờ server xác nhận khối dưới chân (ping " + this.pingMs() + " ms)";
                     } else {
                        Vec3d var18 = this.edgeSpot(var1.pos());
                        if (var18 == null) {
                           this.bridgeTicks = 0;
                           this.markLeanFailed(var1);
                           this.navigationFailed("Không có mép an toàn để ngồi chìa ra đặt " + var1.pos().toShortString() + " → tìm chỗ đứng khác");
                           return;
                        } else {
                           double var23 = var18.x - this.mc.player.getX();
                           double var9 = var18.z - this.mc.player.getZ();
                           if (var23 * var23 + var9 * var9 > 0.0025000000000000005) {
                              this.creepToward(var18);
                           } else if (this.mc.player.getVelocity().horizontalLengthSquared() > this.slideLimit()) {
                              this.brake();
                           } else {
                              this.aim = PlacementSolver.solve(this.mc, this.mc.player.getEyePos(), var1, this.reach(), true, this.seed(), true);
                              if (this.aim == null && this.bridgeTicks > 40) {
                                 this.bridgeTicks = 0;
                                 this.markLeanFailed(var1);
                                 this.navigationFailed("Tới mép nhưng chưa có góc đặt đúng hướng cho " + var1.pos().toShortString());
                              }
                           }
                        }
                     }
                  }
               }
            } else if (this.aim == null && var3) {
               if (++this.bridgeTicks > 60) {
                  this.bridgeTicks = 0;
                  this.temporary = null;
                  this.supportTarget = null;
                  this.navigationFailed("Không bắc được khối kê tại " + var1.pos().toShortString());
               } else {
                  this.creepToward(Vec3d.ofCenter(var1.pos()));
                  this.status = "Bắc cầu: ngồi ra sát mép để đặt khối kê";
               }
            } else if (this.aim == null) {
               if (this.pillar) {
                  this.mc.player.setPitch(88.0F);
               }

               if (this.pillar && this.temporary == null && this.stageTicks > 30) {
                  this.pillar = false;
                  this.beginGoals(false);
               } else if ((this.pillar || this.temporary != null || var4 || this.stageTicks > 30 || !this.settleOnArrival())
                  && (!this.pillar && this.stageTicks > 2 || this.stageTicks > 60)) {
                  if (!this.pillar && this.temporary == null) {
                     this.logAimFailure(var1, var4);
                  }

                  if (this.temporary != null) {
                     this.temporary = null;
                     this.pillar = false;
                     this.navigationFailed("Chưa đặt được khối kê trong tầm với.");
                  } else if (this.supportTarget != null) {
                     this.supportTarget = null;
                     this.navigationFailed("Chưa kê được điểm tựa.");
                  } else {
                     // 3.0.92: đứng đây không có góc đặt hợp lệ (vd. comparator facing=west phải quay mặt về đông mà mặt bám nằm chếch)
                     // → cấm chỗ đứng này cho khối này rồi mới tìm chỗ khác; bản cũ tìm lại ra đúng chỗ cũ, lặp mãi (log 17:24:38–17:24:54)
                     PathSearch.Cell var70 = Navigation.cell(this.mc.player.getBlockPos());
                     if (this.badStands.computeIfAbsent(var1.pos().toImmutable(), var0x -> new HashSet<>()).add(var70)) {
                        AutoBuildMod.LOGGER
                           .info(
                              "[AutoBuild] {} @ {}: đứng ở {} không có góc đặt đúng hướng → cấm chỗ đứng này, tìm chỗ khác",
                              new Object[]{var1.state().getBlock().getName().getString(), var1.pos().toShortString(), var70}
                           );
                     }

                     if (++this.goalRetries > 5) {
                        this.navigationFailed("Tới nơi nhưng chưa có góc đặt hợp lệ.");
                     } else {
                        this.beginGoals(false);
                     }
                  }
               }
            } else {
               if (!this.aim.sneak() && this.waterIntoBlock(var1)) {
                  Input.forceSneak(this.mc, false, this.ticks);
               } else {
                  Input.sneak(this.mc, this.aim.sneak(), this.ticks);
               }

               if (this.aim != this.anglesFor) {
                  this.anglesFor = this.aim;
                  this.aimYawC = PlacementSolver.aimYaw(this.mc, this.aim);
                  this.aimPitchC = PlacementSolver.aimPitch(this.mc, this.aim);
               }

               float var14 = this.aimYawC;
               float var6 = this.aimPitchC;
               boolean var7 = (this.mc.player.isOnGround() || this.pillar)
                  && this.mc.player.isSneaking() == this.aim.sneak()
                  && this.mc.player.isInSneakingPose() == this.aim.sneak();
               this.status = (this.temporary != null ? "Đặt khối kê: " : "Đặt: ")
                  + var1.state().getBlock().getName().getString()
                  + " @ "
                  + var1.pos().toShortString();
               if (var4 && this.aim.sneak()) {
                  if (this.mc.player.getVelocity().horizontalLengthSquared() > this.slideLimit()) {
                     this.gate("đang trượt");
                     this.brake();
                     this.lookAt(var14, var6);
                     return;
                  }
               } else if (!var3 && this.mc.player.isOnGround() && this.mc.player.getVelocity().horizontalLengthSquared() > this.slideLimit()) {
                  this.brake();
               }

               if (!var7 && !this.aim.sneak() && this.overhanging() && !this.waterIntoBlock(var1)) {
                  PlacementSolver.Aim var8 = PlacementSolver.solve(this.mc, this.mc.player.getEyePos(), var1, this.reach(), true, this.seed(), true);
                  this.gate("tư thế (tính lại khi ngồi)");
                  this.aim = var8;
                  this.anglesFor = null;
               } else if (!var7 && ++this.poseTicks > 10) {
                  this.poseTicks = 0;
                  this.aim = PlacementSolver.solve(
                     this.mc, this.mc.player.getEyePos(), var1, this.reach(), true, this.seed(), this.mc.player.isInSneakingPose()
                  );
                  this.anglesFor = null;
                  this.gate("tư thế (giải lại theo tư thế hiện tại)");
               } else {
                  if (var7) {
                     this.poseTicks = 0;
                  }

                  if (!var7) {
                     this.gate("tư thế");
                  } else if (this.cooldown > 0) {
                     this.gate("hồi chiêu");
                  }

                  if (var7 && !this.pillar && this.temporary == null && !this.mc.player.isOnGround()) {
                     this.gate("đang bay");
                     this.lookAt(var14, var6);
                     return;
                  }

                  if (var7 && this.cooldown <= 0) {
                     if (!this.clickNow(var1, var14, var6) && !this.pillar && ++this.aimInvalidTicks > 5) {
                        this.aimAttempt++;
                        this.aim = null;
                        this.anglesFor = null;
                        this.aimInvalidTicks = 0;
                     }
                  } else if (!this.pillar) {
                     this.lookAt(var14, var6);
                  }
               }
            }
         }
      }
   }

   private int placeLockUntil = -1000;
   private int wrongRefuse;

   private void lookAt(float var1, float var2) {
      if (this.ticks < this.placeLockUntil) {
         return;
      }

      float var3 = this.mc.player.getYaw();
      float var4 = MotionMath.wrap(var1 - var3);
      float var5 = Math.clamp(var2, -90.0F, 90.0F);
      if (Math.abs(var4) > 0.01F) {
         this.mc.player.setYaw(var3 + var4);
      }

      if (Math.abs(var5 - this.mc.player.getPitch()) > 0.01F) {
         this.mc.player.setPitch(var5);
      }
   }

   private boolean rotationSent() {
      return Math.abs(MotionMath.wrap(this.sentYaw - this.mc.player.getYaw())) <= 0.01F && Math.abs(this.sentPitch - this.mc.player.getPitch()) <= 0.01F;
   }

   private boolean ackWait(BlockPos var1) {
      return this.pendingSince >= 0 && this.ticks - this.pendingSince > 60 ? false : ServerAck.pending(this.mc, var1);
   }

   private long rttStart = -1L;
   private int rttMs = 0;

   private int tabPing() {
      try {
         PlayerListEntry var1 = this.mc.getNetworkHandler() == null ? null : this.mc.getNetworkHandler().getPlayerListEntry(this.mc.player.getUuid());
         return var1 == null ? 0 : Math.max(0, var1.getLatency());
      } catch (Exception var2) {
         return 0;
      }
   }

   private int pingMs() {
      int var1 = this.tabPing();
      int var2 = var1 > 0 ? Math.max(var1, this.rttMs) : (this.rttMs > 0 ? this.rttMs : 200);
      return Math.max(50, Math.min(1500, var2));
   }

   private int maxUnacked() {
      int var1 = this.pingMs();
      // 3.0.96: cho gửi trước nhiều khối hơn khi ping cao (server xử lý gói tin theo đúng thứ tự)
      // 3.0.122 (người dùng: đặt liên tục, kiểm sau): cho đặt trước nhiều khối hơn; khối bám vào khối chưa được xác nhận vẫn phải chờ
      // (aim: "mặt click là khối server chưa xác nhận"), khối mất thì vòng kiểm tra đặt lại
      return var1 <= 120 ? 6 : (var1 <= 300 ? 5 : 4);
   }

   private int pingDelayTicks() {
      int var1 = this.pingMs();
      return var1 >= 350 ? 3 : (var1 >= 200 ? 2 : (var1 >= 120 ? 1 : 0));
   }

   private boolean paceOk() {
      if (this.unacked < this.maxUnacked()) {
         return true;
      } else {
         this.status = "Chờ server xác nhận khối vừa đặt (ping " + this.pingMs() + " ms)";
         return false;
      }
   }

   private boolean qualityPlaceNowOriginal(BlockHitResult var1) {

      if (this.tntVeto(var1)) {
         return false;
      }

      this.placeLockUntil = this.ticks + 3;
      this.wrongRefuse = 0;
      // 3.0.122: bấm chuột phải thật; thao tác chạy lúc game xử lý cú bấm (đầu tick sau, trước gói góc nhìn — đúng thứ tự vanilla)
      Input.queueUse(this.mc, var1, true, "đặt khối @ " + var1.getBlockPos().offset(var1.getSide()).toShortString(), () -> {
         Item var4 = this.mc.player.getMainHandStack().getItem();
         int var5 = this.mc.player.getMainHandStack().getCount();
         ActionResult var2 = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, var1);
         if (var2.isAccepted()) {
            this.mc.player.swingHand(Hand.MAIN_HAND);
         } else if (BucketRules.shopBucket(this.mc.player.getMainHandStack().getItem())) {
            ActionResult var3 = this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
            if (var3 != null && var3.isAccepted()) {
               this.mc.player.swingHand(Hand.MAIN_HAND);
            }
         }

         this.qualityBucketUsed(var4, var5, "đổ xô");
      });
      return true;
   }

   private final Map<BlockPos, Integer> lowerWaterSaid = new HashMap<>();
   private int lowerCursor;
   private int lowerSaidAt = -1000;

   private void lowerLayerWatch() {
      if (this.bucketPlacementPriority()) {
         return;
      }

      if (this.running
         && this.config.layerMode
         && this.currentLayer != null
         && this.plan != null
         && this.mc.world != null
         && !this.shopping
         && !this.cleanupMode
         && this.pending == null
         && this.ticks - this.lowerSaidAt > 60
         && this.ticks % 10 == 0
         && (this.stage == BuildController.Stage.MOVE
            || this.stage == BuildController.Stage.GOALS
            || this.stage == BuildController.Stage.SEARCH
            || this.stage == BuildController.Stage.AIM
            || this.stage == BuildController.Stage.EQUIP
            || this.stage == BuildController.Stage.NEXT
            || this.stage == BuildController.Stage.WAIT_MATERIAL)) {
         int var1 = this.plan.targets.size();

         for (int var2 = 0; var2 < 300 && var1 > 0; var2++) {
            if (this.lowerCursor >= var1) {
               this.lowerCursor = 0;
            }

            SchematicPlan.Target var3 = this.plan.targets.get(this.lowerCursor++);
            BlockPos var4 = var3.pos();
            if (var4.getY() < this.currentLayer
               && this.config.inYRange(var4.getY())
               && !Materials.flowing(var3.state())
               && !this.fillHolds(var4)
               && !this.skipped.contains(var4)
               && this.trusted(var4)
               && this.mc.world.isChunkLoaded(var4)) {
               BlockState var5 = this.mc.world.getBlockState(var4);
               if (var5.isOf(Blocks.WATER) && washable(this.mc, var3.state(), var4)) {
                  // 3.0.100: khối dễ trôi (dây…) ở tầng dưới đang có nước chảy qua → quay về đặt cũng mất; xây tiếp tầng này
                  // (các khối chặn nước ở tầng này), nước hết chảy thì mới quay về đặt
                  if (this.ticks - this.lowerWaterSaid.getOrDefault(var4, -100000) > 1200) {
                     this.lowerWaterSaid.put(var4.toImmutable(), this.ticks);
                     AutoBuildMod.LOGGER
                        .info(
                           "[AutoBuild] Tầng dưới Y={} mất {} @ {} nhưng ô đó đang có nước chảy qua → chưa quay về, chặn nước xong mới đặt lại",
                           new Object[]{var4.getY(), var3.state().getBlock().getName().getString(), var4.toShortString()}
                        );
                  }

                  continue;
               }

               if (!this.done(var5, var3.state()) && var5.isReplaceable() && Materials.supported(var5, var3.state())) {
                  this.lowerSaidAt = this.ticks;
                  String var6 = this.pistonNear(var4);
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Tầng dưới Y={} mất {} @ {}{} → quay về xây lại tầng đó trước, không xây tiếp Y={}",
                        new Object[]{var4.getY(), var3.state().getBlock().getName().getString(), var4.toShortString(), var6 == null ? "" : " (bị " + var6 + " đẩy đi)", this.currentLayer}
                     );
                  this.placedDone.remove(var4);
                  if (!this.jobTemps.isEmpty()) {
                     this.sweepNow.addAll(this.jobTemps);
                     this.jobTemps.clear();
                  }

                  this.supportTarget = null;
                  this.supportQueue.clear();
                  this.selfPillarBase = null;
                  this.job = null;
                  Input.key(this.mc.options.jumpKey, false);
                  this.beginScan();
                  return;
               }
            }
         }
      }
   }

   private boolean placedRecently(BlockPos var1) {
      Integer var2 = this.recentPlaced.get(var1);
      return var2 != null && this.ticks - var2 < 20 + this.latencyTicks() * 2;
   }

   private void clicked(BlockPos var1) {
      this.notePlacementClick(var1);
      this.everPlaced.add(var1.toImmutable());
      this.obstacles.remove(var1);
      this.unacked++;
      this.ackPending = true;
      this.lastProgress = this.ticks;
      this.recentPlaced.put(var1.toImmutable(), this.ticks);
      if (this.recentPlaced.size() > 64) {
         this.recentPlaced.values().removeIf(var1x -> var1x < this.ticks - 200);
      }
   }

   private boolean floorConfirmed(PathSearch.Cell var1) {
      BlockPos var2 = Navigation.pos(var1).down();
      Integer var3 = this.recentPlaced.get(var2);
      if (var3 == null) {
         return true;
      } else if (!this.ackWait(var2) && this.ticks - var3 >= 2) {
         this.recentPlaced.remove(var2);
         return true;
      } else {
         return false;
      }
   }

   private int latencyTicks() {
      return Math.clamp((long)((this.pingMs() + 49) / 50), 1, 30);
   }

   private double mwX;
   private double mwZ;
   private int mwTicks;

   private int avoidSince = -100000;

   private boolean maybeServerBlock(BlockPos var1) {
      if (this.recentPlaced.containsKey(var1) || this.journal != null && this.journal.placed.containsKey(var1) || this.failures.containsKey(var1)) {
         return true;
      } else {
         SchematicPlan.Target var2 = this.plan == null ? null : this.plan.at(var1);
         return var2 != null && !var2.state().isAir() && this.placedDone.contains(var1);
      }
   }

   private int backoffTicks;
   private Vec3d rbLastPos;
   private Vec3d rbLastVel;
   private int rbCount;
   private int rbFirstAt;

   private final double[] rbHx = new double[64];
   private final double[] rbHy = new double[64];
   private final double[] rbHz = new double[64];
   private int rbHn;

   /**
    * 3.0.82: server kéo lại (setback) = dịch chuyển tức thời về đúng một vị trí CŨ mà client đã gửi vài tick trước.
    * Bản 3.0.81 chỉ so độ lệch so với vận tốc → cú nhảy chạy (+0,2 lực đẩy) lúc quay đầu, đâm tường, trượt băng
    * đều bị tưởng là "bị giật" → dừng, lùi, rồi né ô, đổi đường (quay đầu đi chỗ khác). Giờ phải khớp lịch sử vị trí mới tính.
    */
   private boolean rbMatchesHistory(Vec3d var1) {
      int var2 = Math.min(this.rbHn, 64);

      for (int var3 = 2; var3 <= var2; var3++) {
         int var4 = Math.floorMod(this.rbHn - var3, 64);
         double var5 = var1.x - this.rbHx[var4];
         double var7 = var1.z - this.rbHz[var4];
         if (var5 * var5 + var7 * var7 < 0.0225 && Math.abs(var1.y - this.rbHy[var4]) < 0.6) {
            return true;
         }
      }

      return false;
   }

   private void rbRecord(Vec3d var1) {
      int var2 = Math.floorMod(this.rbHn, 64);
      this.rbHx[var2] = var1.x;
      this.rbHy[var2] = var1.y;
      this.rbHz[var2] = var1.z;
      this.rbHn++;
   }

   private void moveWatch() {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.rbHn > 0) {
            int var40 = Math.floorMod(this.rbHn - 1, 64);
            double var41 = this.mc.player.getX() - this.rbHx[var40];
            double var43 = this.mc.player.getZ() - this.rbHz[var40];
            if (var41 * var41 + var43 * var43 > 64.0) {
               this.rbHn = 0;
            }
         }
      }

      if (this.running && this.mc.player != null && this.mc.world != null && this.stage == BuildController.Stage.MOVE) {
         Vec3d var20 = this.mc.player.getPos();
         if (this.rbLastPos != null && this.rbLastVel != null && this.backoffTicks <= 0) {
            double var26 = var20.x - this.rbLastPos.x;
            double var28 = var20.z - this.rbLastPos.z;
            double var22 = Math.max(1.0E-4, Math.sqrt(this.rbLastVel.horizontalLengthSquared()));
            double var41 = Math.sqrt(var26 * var26 + var28 * var28);
            double var42 = Math.hypot(var26 - this.rbLastVel.x, var28 - this.rbLastVel.z);
            // lệch hẳn khỏi quỹ đạo vật lý (>0,35 ô trong 1 tick) VÀ rơi đúng vào vị trí cũ trong ~3 giây qua → server kéo lại thật
            boolean var30 = var42 > 0.35 && var41 > 0.3 && this.rbMatchesHistory(var20) || var41 > 2.5 && var42 > 2.0;
            if (var30) {
               if (this.rbCount == 0 || this.ticks - this.rbFirstAt > 300) {
                  this.rbCount = 0;
                  this.rbFirstAt = this.ticks;
               }

               this.rbCount++;
               this.lastRubberAt = this.ticks;
               this.backoffTicks = 16;
               BlockPos var33 = BlockPos.ofFloored(
                  this.rbLastPos.x + this.rbLastVel.x / var22 * 0.8, this.rbLastPos.y + 0.1, this.rbLastPos.z + this.rbLastVel.z / var22 * 0.8
               );
               // 3.0.98: bị giật lại nhiều lần quanh cùng một chỗ (ghost block server còn mà client không thấy) → /home thoát ra
               if (this.ghostAreaNote(var33)) {
                  this.rbLastPos = null;
                  this.rbLastVel = null;
                  return;
               }
               if (this.rbCount >= 2) {
                  int var34 = 0;

                  for (BlockPos var36 : new BlockPos[]{var33, var33.up(), var33.down()}) {
                     if (this.mc.world.getBlockState(var36).isReplaceable()
                        && this.maybeServerBlock(var36)
                        && this.ghosts.put(var36.toImmutable(), this.ticks) == null) {
                        Navigation.GHOSTS.add(var36.toImmutable());
                        var34++;
                     }
                  }

                  Navigation.AVOID.add(var33.toImmutable());
                  this.avoidSince = this.ticks;
                  this.replanAfterBackoff = true;
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Bị server giật lại lần {} gần {} → dừng, lùi ra, rồi đi đường khác né ô này ({} ô nghi ghost)",
                        new Object[]{this.rbCount, var33.toShortString(), var34}
                     );
                  this.logRubberContext();
               } else {
                  AutoBuildMod.LOGGER.info("[AutoBuild] Bị server giật lại gần {} → dừng hẳn, lùi ra ~1 ô rồi đi tiếp", var33.toShortString());
                  this.logRubberContext();
               }

               this.rbLastPos = null;
               this.rbLastVel = null;
               return;
            }
         }

         this.rbLastPos = var20;
         this.rbLastVel = this.mc.player.getVelocity();
         this.rbRecord(var20);
         boolean var1 = this.mc.player.getVelocity().horizontalLengthSquared() > 0.001;
         double var2 = this.mc.player.getX() - this.mwX;
         double var4 = this.mc.player.getZ() - this.mwZ;
         if (var2 * var2 + var4 * var4 > 0.09) {
            this.mwX = this.mc.player.getX();
            this.mwZ = this.mc.player.getZ();
            this.mwTicks = 0;
         } else if (var1 && ++this.mwTicks > 30) {
            this.mwTicks = 0;
            float var6 = this.mc.player.getYaw();
            double var7 = -Math.sin(Math.toRadians(var6));
            double var9 = Math.cos(Math.toRadians(var6));
            BlockPos var11 = BlockPos.ofFloored(this.mc.player.getX() + var7 * 0.8, this.mc.player.getY() + 0.1, this.mc.player.getZ() + var9 * 0.8);
            int var12 = 0;

            for (BlockPos var14 : new BlockPos[]{var11, var11.up(), var11.down()}) {
               if (this.mc.world.getBlockState(var14).isReplaceable()
                  && this.maybeServerBlock(var14)
                  && this.ghosts.put(var14.toImmutable(), this.ticks) == null) {
                  Navigation.GHOSTS.add(var14.toImmutable());
                  var12++;
               }
            }

            if (var12 > 0) {
               AutoBuildMod.LOGGER.info("[AutoBuild] Đi mãi không qua được gần {} (bị kéo lại) → đánh dấu {} ô GHOST BLOCK phía trước, tìm đường khác", var11.toShortString(), var12);
               this.navigationFailed("Kẹt vào ghost block phía trước");
            }
         }
      } else {
         this.mwTicks = 0;
         if (this.mc.player != null) {
            this.mwX = this.mc.player.getX();
            this.mwZ = this.mc.player.getZ();
            this.rbRecord(this.mc.player.getPos());
            this.rbLastPos = null;
            this.rbLastVel = null;
         }
      }
   }

   private void waitBlock() {
      if (this.qualityWaterlogWait()) {
         return;
      }

      if (this.sneakHeld) {
         Input.sneak(this.mc, true, this.ticks);
      }

      BlockState var1 = this.mc.world.getBlockState(this.pending.pos());
      boolean var2 = this.done(var1, this.pending.state()) || StateMatch.placementMatches(var1, this.pending.state());
      if (!this.temporaryPending && this.pending.pos().equals(this.holdPos)) {
         if (this.ticks < this.holdUntil) {
            int var40 = this.failures.getOrDefault(this.pending.pos(), 0) + 1;
            this.status = "Chờ server xác nhận " + this.pending.state().getBlock().getName().getString() + " (lần đặt " + var40 + "/5)";
            if (this.sneakHeld) {
               Input.sneak(this.mc, true, this.ticks);
            }

            return;
         }

         this.holdPos = null;
         if (!var2 && (var1.isReplaceable() || StateMatch.canAdd(var1, this.pending.state()))) {
            this.retryVanished(this.pending, var1);
            return;
         }
      }

      if (!this.temporaryPending
         && this.pending.pos().equals(this.pourPos)
         && Materials.waterlog(var1, this.pending.state())
         && this.ticks - this.pourAt < 8 + this.latencyTicks() * 3) {
         // 3.0.78: vừa đổ xô vào lá → client chưa thấy waterlogged cho tới khi server gửi về.
         // Trước đây kết luận ngay "vẫn cần nước" → quay sang khối khác, bỏ dở lá + khối kê của nó.
         this.status = "Chờ server báo nước đã vào " + this.pending.pos().toShortString();
         return;
      }

      if (!this.temporaryPending) {
         if (!var2) {
            if (this.stageTicks >= 3) {
               if (!var1.isReplaceable() && !StateMatch.canAdd(var1, this.pending.state())) {
                  if (this.clickTick >= 0
                     && this.ticks - this.clickTick < 200
                     && var1.getBlock() != this.pending.state().getBlock()
                     && var1.getBlock().asItem() == this.mc.player.getMainHandStack().getItem()) {
                     AutoBuildMod.LOGGER
                        .info(
                           "[AutoBuild] Server đặt {} thay vì {} tại {} → đồ trên tay lệch với server, đồng bộ lại túi đồ và sẽ đập khối sai",
                           new Object[]{var1.getBlock().getName().getString(), this.pending.state().getBlock().getName().getString(), this.pending.pos().toShortString()}
                        );
                     InventoryAccess.dumpTrace(this.mc, "Server đặt " + var1.getBlock().getName().getString() + " thay vì " + this.pending.state().getBlock().getName().getString());
                     InventoryAccess.resync(this.mc);
                     this.notePlaceFail(this.pending.pos(), "server đặt " + var1.getBlock().getName().getString() + " thay vì " + this.pending.state().getBlock().getName().getString());
                     if (!this.running) {
                        return;
                     }
                  } else {
                     this.markPlacedDone(this.placedDone, this.pending.pos());
                  }

                  this.lastPlaced = this.pending.pos();
                  this.noteTemporaryPlacementVerified();
                  this.finishTarget();
               } else if (this.checkWaitFailure()) {
                  return;
               } else if (++this.aimAttempt > 3) {
                  this.deferred.put(this.pending.pos(), this.ticks + 160);
                  SchematicPlan.Target var3 = this.plan.at(this.pending.pos());
                  if (var3 != null) {
                     this.attempt(var3);
                  }

                  this.nextTarget(true);
               } else {
                  this.aim = null;
                  if (this.checkWaitFailure()) {
                  return;
               }

               this.setStage(BuildController.Stage.AIM);
               }
            }
         } else {
            if (this.pending.pos().equals(this.pourPos) && this.done(var1, this.pending.state()) && !this.ackWait(this.pending.pos())) {
               // 3.0.121: server đã xác nhận xô đổ đúng ô → món trên tay (xô rỗng) client đoán là đúng
               InventoryAccess.pourConfirmed(this.mc);
            }

            this.verify.addLast(new BuildController.Placed(this.pending.pos(), this.pending.state(), this.ticks + 4 + this.latencyTicks() * 2));
            this.placedCount++;
            this.planeStuck = 0;
            this.notePlaced(this.pending.pos(), this.pending.state());
            this.noteCrafterPlaced(this.pending.pos(), var1);
            this.noteContainerPlaced(this.pending.pos());
            this.pushChain(this.pending.pos(), this.pending.state());

            if (this.pistonBlockers.contains(this.pending.pos())) {
               SchematicPlan.Target var51 = this.plan == null ? null : this.plan.at(this.pending.pos());
               if (var51 == null || var51.state().getBlock() != var1.getBlock()) {
                  this.journal.add(this.pending.pos(), var1);
                  this.scaffoldUsedAt = this.ticks;
               }
            }
            boolean var6 = Materials.waterlog(var1, this.pending.state());
            if (!var6 && this.jobTempFor != null && this.jobTempFor.equals(this.pending.pos())) {
               this.sweepNow.addAll(this.jobTemps);
               this.jobTemps.clear();
               this.jobTempFor = null;
            }

            this.navFailures.remove(this.pending.pos());
            this.attempts.remove(this.pending.pos());
            this.bridgeDefers.remove(this.pending.pos());
            this.scaffoldOk = false;
            this.reachable = null;
            if (StateMatch.chestAwaitingPartner(var1, this.pending.state())) {
               this.priorityPos = StateMatch.chestPartner(this.pending.pos(), this.pending.state());
            }

            boolean var4 = StateMatch.configurable(var1, this.pending.state());
            if (var4 && this.configureClicks.merge(this.pending.pos(), 1, Integer::sum) > 4) {
               var4 = false;
            }

            if (Materials.waterlog(var1, this.pending.state())) {
               if (this.pending.pos().equals(this.pourPos) && this.ticks - this.pourAt < 200) {
                  // 3.0.108: vừa đổ xô vào ô này mà vẫn chưa ngập nước = 1 lần đặt (đổ) hỏng
                  this.notePlaceFail(this.pending.pos(), "đổ nước không vào");
                  this.pourPos = null;
                  if (!this.running) {
                     return;
                  }
               }

               if (this.waterAskedTooMuch(this.pending.pos())) {
                  // 3.0.105: log 03:00–08:54 lặp "cần nước → đổ xô" ~5 lần/giây ở cùng 1 ô suốt 6 giờ → giới hạn, để ô đó 1 phút
                  this.queue.remove(this.pending);
                  this.pending = null;
                  this.aim = null;
                  this.anglesFor = null;
                  this.target = null;
                  this.beginScan();
                  return;
               }

               AutoBuildMod.LOGGER
                  .info("[AutoBuild] {} @ {} cần nước → đổ xô nước vào", var1.getBlock().getName().getString(), this.pending.pos().toShortString());
               this.aim = null;
               this.anglesFor = null;
               this.setStage(BuildController.Stage.EQUIP);
            } else if (!this.done(var1, this.pending.state())
               && !StateMatch.chestAwaitingPartner(var1, this.pending.state())
               && (var4 || StateMatch.canAdd(var1, this.pending.state()) && !var1.isReplaceable() && !Materials.bucket(var1, this.pending.state()))) {
               this.aim = null;
               if (this.checkWaitFailure()) {
                  return;
               }

               this.setStage(BuildController.Stage.AIM);
            } else {
               this.noteTemporaryPlacementVerified();
                  this.finishTarget();
            }
         }
      } else {
         int var7 = 2 + this.latencyTicks() * 2;
         boolean var8 = this.ackWait(this.pending.pos());
         if (this.stageTicks >= 2 && (!var8 || this.stageTicks >= var7 + 40)) {
            if (var2) {
               SchematicPlan.Target var5 = this.plan == null ? null : this.plan.at(this.pending.pos());
               if (var5 == null || !this.done(var1, var5.state())) {
                  this.journal.add(this.pending.pos(), var1);
                  this.scaffoldUsedAt = this.ticks;
                  if (this.temporary != null) {
                     this.sweepNow.add(this.pending.pos().toImmutable());
                  }
               }

               this.targetTicks = 0;
               this.noteTemporaryPlacementVerified();
                  this.finishTarget();
            } else if (!var1.isReplaceable()) {
               boolean var9 = var1.isSideSolidFullSquare(this.mc.world, this.pending.pos(), Direction.UP);
               boolean var10 = var1.getBlock().asItem() == this.mc.player.getMainHandStack().getItem();
               if (var10 && var1.getBlock() != this.pending.state().getBlock()) {
                  InventoryAccess.resync(this.mc);
               }

               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] Ô kê tạm {} đang là {} (không phải {}); {} → {}",
                     new Object[]{
                        this.pending.pos().toShortString(),
                        var1,
                        this.pending.state().getBlock().getName().getString(),
                        var10 ? "do mình vừa đặt" : "có sẵn từ trước",
                        var9 ? "đứng/bám được nên dùng luôn làm khối kê" : "không dùng được, tìm đường khác"
                     }
                  );
               if (var9) {
                  if (var10) {
                     this.journal.add(this.pending.pos(), var1);
                  this.scaffoldUsedAt = this.ticks;
                     if (this.temporary != null) {
                        this.sweepNow.add(this.pending.pos().toImmutable());
                     }
                  }

                  this.targetTicks = 0;
                  this.noteTemporaryPlacementVerified();
                  this.finishTarget();
               } else {
                  this.temporary = null;
                  this.pending = null;
                  this.navigationFailed("Ô kê tạm bị khối khác chiếm");
               }
            } else if (this.stageTicks > var7 + 20 && !var8) {
               this.aim = null;
               if (this.checkWaitFailure()) {
                  return;
               }

               this.setStage(BuildController.Stage.AIM);
            }
         }
      }
   }

   private void finishTarget() {
      this.rememberCurrentTemporary();
      this.pending = null;
      this.aim = null;
      if (this.temporary != null) {
         this.temporary = null;
         this.pillar = false;
         this.setStage(BuildController.Stage.MOVE);
      } else if (this.supportTarget != null) {
         if (!this.jobSupport) {
            if (this.rowSupport) {
               this.rowTemps.add(this.supportTarget.pos().toImmutable());
            } else {
               this.sweepNow.add(this.supportTarget.pos());
            }
         }

         if (!this.supportQueue.isEmpty()) {
            BlockPos var2 = this.supportQueue.poll();
            this.supportTarget = new SchematicPlan.Target(var2, this.pillarState(var2));
            this.bridgeTicks = 0;
            this.setStage(BuildController.Stage.EQUIP);
            return;
         }

         this.supportTarget = null;
         this.aim = null;
         this.rowSupport = false;
         this.jobSupport = false;
         PlacementSolver.Aim var1 = this.target == null
            ? null
            : PlacementSolver.solve(this.mc, this.mc.player.getEyePos(), this.target, this.reach(), true, this.seed());
         if (var1 != null) {
            this.aim = var1;
            this.setStage(BuildController.Stage.EQUIP);
         } else if (this.target != null) {
            this.beginGoals(false);
         } else {
            this.nextTarget(true);
         }
      } else if (!this.sweepScaffold(false)) {
         this.nextTarget(true);
      }
   }

   private void hintSpoof() {
      if (this.gAngle > 0 && this.gFar == 0 && !this.spoofAllowed() && this.ticks - this.spoofHintAt > 2400) {
         SchematicPlan.Target var1 = this.current();
         if (var1 != null && this.mc.world != null && !PlacementSolver.placeableSomewhere(this.mc, var1, true)) {
            this.spoofHintAt = this.ticks;
            this.say(
               var1.state().getBlock().getName().getString()
                  + " @ "
                  + var1.pos().toShortString()
                  + ": đứng trong tầm với nhưng mọi mặt click đều cho ra hướng sai (khối này quay theo hướng nhìn). Bật \"Xoay góc giả\" (rotationSpoof) trong Cài đặt để đặt đúng hướng."
            );
         }
      }
   }

   private boolean spoofAllowed() {
      if ("OFF".equals(this.config.rotationSpoof)) {
         return false;
      } else if ("ON".equals(this.config.rotationSpoof)) {
         return true;
      } else if (this.mc.isInSingleplayer()) {
         return true;
      } else {
         ServerInfo var1 = this.mc.getCurrentServerEntry();
         return var1 != null && var1.address != null && var1.address.toLowerCase(Locale.ROOT).contains("donutsmp");
      }
   }

   private boolean spoofFor(SchematicPlan.Target var1) {
      if (var1 != null && this.spoofBanned(var1.pos())) {
         return false;
      } else if (var1 != null && this.mc.world != null && Materials.bucket(this.mc.world.getBlockState(var1.pos()), var1.state())) {
         return false;
      } else if (var1 != null && this.spoofAllowed()) {
         Integer var2 = this.lookUpCache.get(var1.pos());
         if (var2 == null || var2 != Integer.MAX_VALUE && var2 <= this.ticks) {
            boolean var3 = PlacementSolver.needsLookUp(this.mc, var1) || !PlacementSolver.placeableSomewhere(this.mc, var1, true);
            this.lookUpCache.put(var1.pos().toImmutable(), var3 ? Integer.MAX_VALUE : this.ticks + 100);
            return var3;
         } else {
            return var2 == Integer.MAX_VALUE;
         }
      } else {
         return false;
      }
   }

   private float[] heads() {
      return new float[]{this.mc.player.getYaw(), this.yawHistory[0], this.yawHistory[1], this.yawHistory[2]};
   }

   private boolean spoofClick(SchematicPlan.Target var1, PlacementSolver.SpoofAim var2) {
      if (!this.qualityClickReady(var1)) {
         return false;
      }

      if (this.mc.currentScreen != null || this.mc.player.getHealth() <= (float)this.config.minHealth) {
         return false;
      } else if (this.plan != null && !this.plan.unchanged()) {
         this.placementChangedAt = this.ticks;
         this.pause("Placement vừa thay đổi.");
         return false;
      } else {
         BlockHitResult var3 = this.spoofHit;
         if (var3 == null
            || !InventoryAccess.synced(this.mc)
            || Materials.bucket(this.mc.world.getBlockState(var1.pos()), var1.state())
            || !(this.mc.player.getMainHandStack().getItem() instanceof BlockItem)
            || !this.mc.player.getMainHandStack().isOf(Materials.item(this.mc.world.getBlockState(var1.pos()), var1.state()))
            || this.mc.player.getEyePos().squaredDistanceTo(var3.getPos()) > this.reach() * this.reach()
            || !this.mc.world.getBlockState(var1.pos()).isReplaceable()) {
            return false;
         } else if (!this.paceOk()) {
            return false;
         } else if (this.tntUnsafe(var1) || this.tntVeto(var3)) {
            // 3.0.104: gần TNT — kiểm tra trước khi bấm
            this.spoofHit = null;
            return false;
         } else {
            this.placeLockUntil = this.ticks + 3;
            // 3.0.122: chuột phải thật (xem Input.queueUse); góc gửi giữ nguyên tới hết tick sau (placeLockUntil)
            Input.queueUse(this.mc, var3, false, "đặt khối (góc gửi) @ " + var1.pos().toShortString(), () -> {
               ActionResult var4 = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, var3);
               if (var4.isAccepted()) {
                  this.mc.player.swingHand(Hand.MAIN_HAND);
               }
            });
            this.spoofHit = null;
            this.spoofPlaced.add(var1.pos().toImmutable());
            this.clicked(var1.pos());

            this.sneakHeld = var2.aim().sneak();
            this.clickTick = this.ticks;
            this.pending = var1;
            this.temporaryPending = false;
            this.settledTicks = 0;
            this.setStage(BuildController.Stage.WAIT_BLOCK);
            this.cooldown = this.config.placeDelayTicks;
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Đặt {} @ {} bằng góc gửi yaw={} pitch={}",
                  new Object[]{var1.state().getBlock().getName().getString(), var1.pos().toShortString(), var2.sendYaw(), var2.sendPitch()}
               );
            return true;
         }
      }
   }

   private boolean sweepScaffold(boolean var1) {
      if (var1 && this.sweepNow.isEmpty()) {
         return false;
      } else {
         this.resumeAfterSweep = var1;
         if (this.config.sweepScaffold && this.plan != null && this.journal != null && !this.journal.placed.isEmpty() && this.mc.player.isOnGround()) {
            Box var2 = this.mc.player.getBoundingBox().expand(0.03, 0.0, 0.03).stretch(0.0, -1.2, 0.0);
            double var3 = this.reach() * this.reach();
            ArrayList<BlockPos> var5 = new ArrayList<>(this.sweepNow);
            if (!var1) {
               for (BlockPos var7 : this.journal.placed.keySet()) {
                  if (!this.sweepNow.contains(var7)) {
                     var5.add(var7);
                  }
               }
            }

            for (BlockPos var13 : var5) {
               if (!this.journal.placed.containsKey(var13)) {
                  this.sweepNow.remove(var13);
               } else if (!this.cleanupMode && this.pistonBlockers.contains(var13)) {
                  this.sweepNow.remove(var13);
               } else if (!this.sweepSkip.contains(var13) && !this.sweepBlocked(var13) && (this.plan.inside(var13) || this.sweepNow.contains(var13))) {
                  SchematicPlan.Target var8 = this.plan.at(var13);
                  if (var8 != null && this.done(this.mc.world.getBlockState(var13), var8.state())) {
                     this.sweepSkip.add(var13);
                  } else if (!(this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(var13)) > var3)
                     && !var2.intersects(new Box(var13))
                     && this.safeMiningStand(Navigation.cell(this.mc.player.getBlockPos()), var13)
                     && this.safeToRemove(var13)
                     && (!this.rowTemps.contains(var13) && this.sweepNow.contains(var13) || !this.tempStillNeeded(var13))) {
                     BlockState var9 = this.mc.world.getBlockState(var13);
                     if (var9.isAir()) {
                        this.journal.remove(var13);
                        this.sweepNow.remove(var13);
                     } else if (!Registries.BLOCK.getId(var9.getBlock()).toString().equals(this.journal.placed.get(var13))) {
                        this.sweepSkip.add(var13);
                     } else {
                        boolean var10 = !var9.isToolRequired();

                        for (int var11 = 0; var11 < 36 && !var10; var11++) {
                           if (this.mc.player.getInventory().getStack(var11).isSuitableFor(var9)) {
                              var10 = true;
                           }
                        }

                        if (var10 && this.visiblePoint(var13) == null) {
                           // 3.0.98: chưa nhìn thấy từ chỗ này → đừng cầm cúp ra rồi lại bỏ; 10 giây sau (đã đi chỗ khác) mới xét lại
                           this.sweepFailUntil.put(var13.toImmutable(), this.ticks + 200);
                           continue;
                        }

                        if (var10) {
                           this.release();
                           this.sweeping = true;
                           this.mining = var13;
                           this.miningTicks = 0;
                           this.aim = null;
                           this.pending = null;
                           this.setStage(BuildController.Stage.MINE);
                           return true;
                        }

                        this.sweepSkip.add(var13);
                     }
                  }
               }
            }

            return false;
         } else {
            return false;
         }
      }
   }

   private boolean safeToRemove(BlockPos var1) {
      BlockState var2 = this.mc.world.getBlockState(var1.up());
      if (var2.isAir()) {
         return true;
      } else {
         return var2.getBlock() instanceof FallingBlock
            ? false
            : var2.isSideSolidFullSquare(this.mc.world, var1.up(), Direction.DOWN)
               || var2.isFullCube(this.mc.world, var1.up())
               || var2.getBlock() instanceof LeavesBlock;
      }
   }

   /**
    * 3.0.90: chế độ nhanh chấp nhận khối cùng họ (Materials.family: glass, glass_pane, slab, stairs, carpet…) → khi đi Order
    * lấy vật phẩm thiếu thì order nào có một trong các vật phẩm đó cũng lấy (vd. thiếu Black Stained Glass mà có order Glass).
    */
   private java.util.Set<Item> orderAlternates(Item var1) {
      if (this.config.fastMode() && var1 instanceof BlockItem var2) {
         String var3 = Materials.family(var2.getBlock());
         if (var3 != null) {
            java.util.HashSet<Item> var4 = new java.util.HashSet<>();

            for (Block var6 : Materials.members(var3)) {
               Item var7 = var6.asItem();
               if (var7 != Items.AIR && var7 != var1) {
                  var4.add(var7);
               }
            }

            return var4;
         }
      }

      return java.util.Set.of();
   }

   private int orderHave() {
      int var1 = 0;

      for (Item var3 : this.orders.accepted()) {
         var1 += InventoryAccess.count(this.mc, var3);
      }

      return Math.max(var1, this.orderItem == null ? 0 : InventoryAccess.count(this.mc, this.orderItem));
   }

   /** 3.0.95: menu Order/Shop kẹt hoặc server lag → không dừng: đóng màn hình, hoãn vật phẩm này ~60 giây, xây phần khác */
   private void menuHiccup(String var1) {
      if (this.mc.player != null && this.mc.currentScreen != null) {
         this.mc.player.closeHandledScreen();
      }

      if (this.orderItem != null) {
         this.missing.put(this.orderItem, this.ticks + 1200);
      }

      AutoBuildMod.LOGGER.info("[AutoBuild] {} → xây phần khác, 60 giây nữa thử lại (không dừng)", var1);
      this.status = var1;
      if (this.target != null) {
         this.deferred.put(this.target.pos(), this.ticks + 1200);
      }

      if (this.temporary != null || this.supportTarget != null) {
         this.dropScaffoldJob(this.orderItem);
      }

      this.shopping = false;
      this.browsing = false;
      this.beginScan();
   }

   private void orderEmpty(String var1) {
      this.missing.put(this.orderItem, this.ticks + this.config.orderRecheckSeconds * 20);
      if (this.announcedMissing.add(this.orderItem)) {
         this.say("Thiếu " + this.orderItem.getName().getString() + " (" + var1 + "). Xây tiếp phần khác, " + this.config.orderRecheckSeconds + " giây nữa kiểm tra lại.");
      } else {
         AutoBuildMod.LOGGER.info("[AutoBuild] Thiếu {} ({})", this.orderItem.getName().getString(), var1);
      }

      this.waitingReason = var1;
      if (this.target != null) {
         this.deferred.put(this.target.pos(), this.ticks + this.config.orderRecheckSeconds * 20);
      }

      if (this.temporary != null || this.supportTarget != null) {
         this.dropScaffoldJob(this.orderItem);
      }

      this.beginScan();
   }

   private void collect(Item var1, BuildController.Stage var2, int var3) {
      if (!this.collectShopBucket(var1, var2)) {
         this.release();
         if (!this.config.buyBuckets || var1 != Items.WATER_BUCKET && var1 != Items.LAVA_BUCKET) {
            if (!this.config.collectFromOrders) {
               this.pause("Thiếu " + var1.getName().getString() + "; bỏ vào túi rồi nhấn K.");
            } else if (this.missing.getOrDefault(var1, 0) > this.ticks) {
               if (this.target != null) {
                  this.deferred.put(this.target.pos(), this.missing.get(var1));
               }

               if (this.temporary != null || this.supportTarget != null) {
                  // 3.0.83: hết khối kê mà Order chưa có hàng → bỏ khối cần kê này, xây phần khác, lát nữa lấy lại (không dừng)
                  this.dropScaffoldJob(var1);
               }

               this.beginScan();
            } else if (InventoryAccess.freeSlots(this.mc) <= this.config.reserveFreeSlots) {
               this.inventoryFull(var1);
            } else {
               // 3.0.95: xem trong ender chest trước khi mở Order/Shop
               java.util.HashSet<Item> var5 = new java.util.HashSet<>(this.orderAlternates(var1));
               var5.add(var1);
               if (this.stashWithdrawFirst(var1, var5, var3, var2)) {
                  return;
               }

               this.orderItem = var1;
               this.afterOrder = var2;
               java.util.Set<Item> var4 = this.orderAlternates(var1);
               if (!var4.isEmpty()) {
                  AutoBuildMod.LOGGER.info("[AutoBuild] Order: lấy {} — không có thì lấy vật phẩm cùng họ thay thế ({} loại, vd. Glass cho Stained Glass)", var1.getName().getString(), var4.size());
               }

               this.orderNeed = Math.max(1, var3);
               this.browsing = false;
               this.orders.start(var1, Math.max(1, var3), var4);
               this.setStage(BuildController.Stage.ORDER);
            }
         } else {
            this.buyBucket(var1, var2);
         }
      }
   }

   /** 3.0.83: bỏ việc đang kê (thiếu khối kê), hoãn khối cần kê, để xây phần khác thay vì dừng. */
   private void dropScaffoldJob(Item var1) {
      SchematicPlan.Target var2 = this.target;
      AutoBuildMod.LOGGER
         .info(
            "[AutoBuild] Chưa có {} để kê{} → xây phần khác, {} giây nữa lấy lại từ Order (không dừng)",
            new Object[]{var1.getName().getString(), var2 == null ? "" : " cho " + var2.pos().toShortString(), this.config.orderRecheckSeconds}
         );
      if (var2 != null) {
         this.deferred.put(var2.pos().toImmutable(), this.ticks + this.config.orderRecheckSeconds * 20);
      }

      this.temporary = null;
      this.supportTarget = null;
      this.supportQueue.clear();
      this.target = null;
      this.pillar = false;
   }

   private void inventoryFull(Item var1) {
      if (this.freeSpace("Túi đồ đầy, cần chỗ để lấy " + var1.getName().getString())) {
         return;
      }

      this.missing.put(var1, this.ticks + 400);
      if (this.fullWarned.add(var1)) {
         this.say("Túi đồ đầy, chưa lấy được " + var1.getName().getString() + " → xây trước những khối đang có, lát nữa quay lại lấy.");
      }

      if (this.target != null) {
         this.deferred.put(this.target.pos(), this.ticks + 400);
      }

      if (this.temporary != null || this.supportTarget != null) {
         this.dropScaffoldJob(var1);
      }

      this.beginScan();
   }

   private void orderTick() {
      if (this.browsing) {
         this.browser.tick(this.mc);
         this.status = this.browser.status();
         if (!this.browser.active()) {
            this.browsing = false;
            if (this.browser.result() == com.donutautobuild.order.ShopBrowser.Result.BOUGHT && this.orderHave() > 0) {
               this.missing.remove(this.orderItem);
               this.announcedMissing.remove(this.orderItem);
               this.say(this.browser.status() + " (Order không có hàng).");
               if (this.afterOrder == BuildController.Stage.EQUIP) {
                  this.setStage(BuildController.Stage.EQUIP);
               } else {
                  this.beginScan();
               }
            } else {
               this.orderEmpty("Order và /" + this.config.shopCommand + " đều chưa có (" + this.browser.status() + ")");
            }
         }
      } else if (this.holdBucketPurchase()) {
         if (this.shopping) {
            this.shop.tick(this.mc);
            this.status = this.shop.status();
            if (!this.shop.active()) {
               this.shopping = false;
               switch (this.shop.result()) {
                  case BLOCKED:
                     // 3.0.95: không dừng việc xây vì menu kẹt/lag: đóng màn hình, xây phần khác, 10 giây sau thử lại
                     this.menuHiccup(this.shop.status());
                     return;
                  case BOUGHT:
                     this.missing.remove(this.orderItem);
                     this.announcedMissing.remove(this.orderItem);
                     if (this.bucketDrifted) {
                        // 3.0.98: bị đẩy lệch chỗ lúc đang mua → tính lại góc đổ từ chỗ đứng hiện tại
                        this.bucketDrifted = false;
                        this.aim = null;
                        this.anglesFor = null;
                     }

                     if (this.afterOrder == BuildController.Stage.EQUIP) {
                        this.setStage(BuildController.Stage.EQUIP);
                     } else {
                        this.beginScan();
                     }

                     return;
                  default:
                     this.bucketDrifted = false;
                     if (this.shop.lagged()) {
                        // 3.0.98 (người dùng): bấm 5 giây chưa mua được = server lag → đóng shop, xây tiếp, ô này đổ sau
                        this.missing.put(this.orderItem, this.ticks + 100);
                        if (this.target != null) {
                           this.deferred.put(this.target.pos(), this.ticks + 300);
                        }

                        this.status = "Shop lag → xây tiếp, lát quay lại đổ";
                        this.beginScan();
                        return;
                     }

                     this.missing.put(this.orderItem, this.ticks + 400);
                     if (this.announcedMissing.add(this.orderItem)) {
                        this.say("Chưa mua được " + this.orderItem.getName().getString() + ": " + this.shop.status() + " — xây phần khác, 20 giây nữa thử lại.");
                     }

                     if (this.target != null) {
                        this.deferred.put(this.target.pos(), this.ticks + 400);
                     }

                     this.beginScan();
               }
            }
         } else {
            this.orders.tick(this.mc);
            this.status = this.orders.status();
            if (!this.orders.active()) {
               if (this.orders.result() == OrderCollector.Result.BLOCKED) {
                  this.menuHiccup(this.orders.status());
               } else if (this.orders.noMenu() && this.orderHave() <= 0) {
                  // 3.0.95: /order không mở được / lag (không phải hết hàng) → xây phần khác, 60 giây sau thử lại /order (không thoát ra vào lại, không sang /shop)
                  this.menuHiccup(this.orders.status());
               } else if (this.orders.result() == OrderCollector.Result.FULL) {
                  this.inventoryFull(this.orderItem);
               } else if (this.orderHave() > 0) {
                  this.missing.remove(this.orderItem);
                  this.announcedMissing.remove(this.orderItem);
                  this.fullWarned.remove(this.orderItem);
                  if (this.afterOrder == BuildController.Stage.EQUIP) {
                     this.setStage(BuildController.Stage.EQUIP);
                  } else {
                     this.beginScan();
                  }
               } else if (this.config.shopFallback
                  && this.orderItem != Items.WATER_BUCKET
                  && this.orderItem != Items.LAVA_BUCKET
                  && this.orderItem != Items.POWDER_SNOW_BUCKET) {
                  // 3.0.91: Order không có hàng → thử /shop (không mua nếu giá > trần)
                  // 3.0.124 (người dùng): lần nào Order trống cũng mở /shop (bỏ nghỉ 3 phút giữa 2 lần thử shop)
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Order không có {} → tìm mua {} ở /{} (trần giá ${})",
                        new Object[]{this.orderItem.getName().getString(), this.orderNeed, this.config.shopCommand, this.config.shopMaxPrice}
                     );
                  this.browsing = true;
                  this.browser.start(this.mc, this.orderItem, this.orders.accepted(), this.orderNeed, this.config.shopMaxPrice);
               } else {
                  this.orderEmpty("Order chưa có hàng");
               }
            }
         }
      }
   }

   private boolean safeMiningStand(PathSearch.Cell var1, BlockPos var2) {
      return var2 != null && (var1.x() != var2.getX() || var1.z() != var2.getZ() || Math.abs(var1.y() - var2.getY()) > 1);
   }

   private void cleanupTick() {
      this.release();
      this.mining = null;
      ArrayList<BlockPos> var1 = new ArrayList<>(this.journal.placed.keySet());
      var1.sort(Comparator.comparingInt(Vec3i::getY).reversed().thenComparingDouble(var1x -> this.mc.player.squaredDistanceTo(Vec3d.ofCenter(var1x))));

      for (BlockPos var3 : var1) {
         if (!this.cleanupDeferred.contains(var3)) {
            if (this.mc.world.isChunkLoaded(var3)) {
               BlockState var4 = this.mc.world.getBlockState(var3);
               if (var4.isAir()) {
                  this.journal.remove(var3);
               } else if (Registries.BLOCK.getId(var4.getBlock()).toString().equals(this.journal.placed.get(var3))
                  && this.mc.world.getBlockEntity(var3) == null) {
                  if ((this.plan == null || this.plan.at(var3) == null || !this.done(var4, this.plan.at(var3).state()))
                     && (this.mc.world.getBlockState(var3.up()).isAir() || this.journal.placed.containsKey(var3.up()))) {
                     this.mining = var3;
                     this.miningTicks = 0;
                     if (this.safeMiningStand(Navigation.cell(this.mc.player.getBlockPos()), var3)
                        && this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(var3)) < this.reach() * this.reach()) {
                        this.setStage(BuildController.Stage.MINE);
                     } else {
                        this.beginGoals(true);
                     }

                     return;
                  }

                  this.cleanupDeferred.add(var3);
               } else {
                  this.cleanupDeferred.add(var3);
               }
            } else {
               this.cleanupDeferred.add(var3);
            }
         }
      }

      if (!this.journal.placed.isEmpty() && this.cleanupPasses < 4) {
         this.cleanupPasses++;
         this.cleanupDeferred.clear();
         this.sweepSkip.clear();
         this.reachable = null;
         this.status = "Dọn lại lượt " + this.cleanupPasses + " (" + this.journal.placed.size() + " khối kê)";
         AutoBuildMod.LOGGER.info("[AutoBuild] Còn {} khối kê chưa dọn → đi vòng nữa (lượt {})", this.journal.placed.size(), this.cleanupPasses);
         this.beginGoals(true);
      } else {
         // 3.0.108: xây xong → vẫn giữ "đang chạy" theo ý người dùng, 10 phút sau tự kiểm tra lại (khối bị phá/đẩy thì xây lại)
         this.finishedAt = this.ticks;
         this.pause(
            (this.plan == null ? "Thu dọn xong." : "Công trình đã xây và kiểm tra xong" + this.rangeLabel() + ".")
               + (this.journal.placed.isEmpty() ? "" : " Còn " + this.journal.placed.size() + " khối kê chưa thu dọn được; dùng /autobuild cleanup.")
               + (
                  this.plan != null && this.config.yEnd != null && this.config.yEnd < this.plan.maxY
                     ? " Công trình còn tới Y=" + this.plan.maxY + " nhưng đang giới hạn Y cuối = " + this.config.yEnd + " → nâng \"Y cuối\" trong Cài đặt (U) rồi nhấn K để xây tiếp."
                     : ""
               )
         );
      }
   }

   private void mineTick() {
      if (this.mining != null && this.tntAbandonMining()) {
         return;
      }

      if (!this.repairing && !this.sweeping) {
         if (this.mining == null) {
            this.setStage(BuildController.Stage.CLEANUP);
         } else {
            this.miningTicks++;
            BlockState var3 = this.mc.world.getBlockState(this.mining);
            if (var3.isAir()) {
               if (this.brokenConfirmed()) {
                  this.journal.remove(this.mining);
                  this.mining = null;
                  this.confirmed();
                  this.setStage(BuildController.Stage.CLEANUP);
               }
            } else if (this.safeMiningStand(Navigation.cell(this.mc.player.getBlockPos()), this.mining) && this.mc.player.isOnGround()) {
               if (var3.isAir()
                  || Registries.BLOCK.getId(var3.getBlock()).toString().equals(this.journal.placed.get(this.mining))
                     && this.mc.world.getBlockState(this.mining.up()).isAir()) {
                  if (this.breakBlock(var3)) {
                     if (this.miningTicks > 240) {
                        this.mc.interactionManager.cancelBlockBreaking();
                        this.cleanupDeferred.add(this.mining);
                        this.setStage(BuildController.Stage.CLEANUP);
                     }

                     this.status = "Thu dọn khối kê: " + this.mining.toShortString();
                  }
               } else {
                  this.cleanupDeferred.add(this.mining);
                  this.setStage(BuildController.Stage.CLEANUP);
               }
            } else {
               this.beginGoals(true);
            }
         }
      } else {
         BlockState var1 = this.mining == null ? null : this.mc.world.getBlockState(this.mining);
         if (var1 != null && (!var1.isReplaceable() || !this.brokenConfirmed())) {
            if (!this.repairing) {
               if (this.miningTicks++ > 120
                  || !this.safeMiningStand(Navigation.cell(this.mc.player.getBlockPos()), this.mining)
                  || this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(this.mining)) > this.reach() * this.reach()
                  || this.miningTicks > 16 && this.visiblePoint(this.mining) == null) {
                  boolean var20 = this.miningTicks > 16 && this.visiblePoint(this.mining) == null;
                  // 3.0.98: KHÔNG thử lại khối kê này trong 60 giây. Lỗi cũ (log 00:14–00:16 và 00:27): cứ ~0,6 giây lại chọn đập
                  // cùng một khối kê không nhìn thấy → cầm cúp → bỏ → cầm xô đổ nước → lại cầm cúp… nhân vật đứng/ngồi, quay qua quay lại
                  // và đổi cúp ↔ xô liên tục suốt 2 phút mà không làm được gì.
                  int var21 = this.sweepFails.merge(this.mining.toImmutable(), 1, Integer::sum);
                  this.sweepFailUntil.put(this.mining.toImmutable(), this.ticks + 1200);
                  if (var20 && var21 <= 2) {
                     AutoBuildMod.LOGGER.info("[AutoBuild] Không nhìn thấy khối kê {} từ chỗ đang đứng → để 60 giây sau / lúc thu dọn, xây tiếp", this.mining.toShortString());
                  }

                  this.mc.interactionManager.cancelBlockBreaking();
                  this.sweepSkip.add(this.mining);
                  this.sweepNow.remove(this.mining);
                  this.endSideMining();
               } else if (var1.isReplaceable()) {
                  this.miningTicks--;
               } else {
                  this.breakBlock(var1);
                  this.status = (this.sweeping ? "Dọn khối kê: " : "Phá khối đặt sai hướng: ") + this.mining.toShortString();
               }
            } else {
               this.miningTicks++;
               if (!this.safeMiningStand(Navigation.cell(this.mc.player.getBlockPos()), this.mining)) {
                  this.stepAway(this.mining);
                  if (++this.repairStepTicks > 40) {
                     this.mc.interactionManager.cancelBlockBreaking();
                     this.endSideMining();
                  }
               } else if (this.visiblePoint(this.mining) == null) {
                  this.mc.interactionManager.cancelBlockBreaking();
                  this.repairing = false;
                  BlockPos var2 = this.mining;
                  this.mining = null;
                  this.repairGoal = var2;
                  this.target = this.plan != null && this.plan.at(var2) != null ? this.resolve(this.plan.at(var2)) : null;
                  this.beginGoals(false);
               } else if (this.miningTicks > 200) {
                  this.mc.interactionManager.cancelBlockBreaking();
                  this.endSideMining();
               } else {
                  this.breakBlock(var1);
                  this.status = "Phá khối đặt sai hướng: " + this.mining.toShortString();
               }
            }
         } else {
            if (this.sweeping && this.mining != null) {
               this.journal.remove(this.mining);
               this.sweepNow.remove(this.mining);
               this.confirmed();
            }

            if (this.repairing && this.mining != null) {
               this.repairWrong.remove(this.mining);
            }

            this.endSideMining();
         }
      }
   }

   private void endSideMining() {
      this.qualityRepairEnded();
      if (!this.resumeTrailMoveAfterSweep()) {
         this.lastProgress = this.ticks;
         boolean var1 = this.sweeping;
         boolean var2 = this.repairing;
         BlockPos var3 = this.mining;
         this.repairing = false;
         this.sweeping = false;
         this.mining = null;
         if (var2 && var3 != null && this.coverFor.containsKey(var3)) {
            BlockPos var6 = this.coverFor.remove(var3);
            if (this.plan != null && this.plan.at(var3) != null) {
               this.restore.add(var3.toImmutable());
               this.restoreTries.remove(var3);
            }

            SchematicPlan.Target var5 = this.plan == null ? null : this.plan.at(var6);
            this.pending = null;
            this.pillar = false;
            this.temporary = null;
            this.supportTarget = null;
            this.supportQueue.clear();
            this.pit = null;
            this.spoof = null;
            this.spoofHold = 0;
            this.aim = null;
            this.inventoryAttempts = 0;
            this.aimAttempt = 0;
            this.goalRetries = 0;
            this.targetTicks = 0;
            this.stepTicks = 0;
            this.bridgeTicks = 0;
            this.replans = 0;
            if (var5 != null && !this.isFinished(var5)) {
               this.deferred.remove(var6);
               this.skipped.remove(var6);
               this.deferCount.remove(var6);
               this.target = this.resolve(var5);
               this.status = "Đã gỡ khối đè, đặt " + var5.state().getBlock().getName().getString() + " @ " + var6.toShortString();
               this.placeOrGo();
            } else {
               this.nextTarget(true);
            }
         } else if (var2 && var3 != null && this.plan != null && this.plan.at(var3) != null && this.mc.world.getBlockState(var3).isReplaceable()) {
            this.pending = null;
            this.pillar = false;
            this.temporary = null;
            this.supportTarget = null;
            this.supportQueue.clear();
            this.pit = null;
            this.spoof = null;
            this.spoofHold = 0;
            this.inventoryAttempts = 0;
            this.aimAttempt = 0;
            this.goalRetries = 0;
            this.targetTicks = 0;
            this.stepTicks = 0;
            this.bridgeTicks = 0;
            this.replans = 0;
            this.target = this.resolve(this.plan.at(var3));
            this.aim = null;
            this.setStage(BuildController.Stage.EQUIP);
            this.equip();
         } else if (var1) {
            boolean var4 = this.resumeAfterSweep;
            if (this.sweepScaffold(var4)) {
               return;
            }

            if (var4 && this.target != null) {
               this.aim = null;
               this.setStage(BuildController.Stage.EQUIP);
            } else {
               this.nextTarget(true);
            }
         } else {
            this.beginScan();
         }
      }
   }

   private Vec3d visiblePoint(BlockPos var1) {
      Vec3d var2 = this.mc.player.getEyePos();
      double var3 = this.reach() * this.reach();
      Vec3d var5 = Vec3d.ofCenter(var1);
      if (var2.squaredDistanceTo(var5) <= var3 && this.rayHits(var2, var5, var1)) {
         return var5;
      } else {
         for (Direction var9 : Direction.values()) {
            for (double[] var13 : new double[][]{{0.5, 0.5}, {0.2, 0.2}, {0.8, 0.8}, {0.2, 0.8}, {0.8, 0.2}}) {
               double var14 = (double)var1.getX() + (var9.getAxis() == Axis.X ? (var9.getDirection() == AxisDirection.POSITIVE ? 0.999 : 0.001) : var13[0]);
               double var16 = (double)var1.getY()
                  + (
                     var9.getAxis() == Axis.Y
                        ? (var9.getDirection() == AxisDirection.POSITIVE ? 0.999 : 0.001)
                        : (var9.getAxis() == Axis.X ? var13[0] : var13[1])
                  );
               double var18 = (double)var1.getZ() + (var9.getAxis() == Axis.Z ? (var9.getDirection() == AxisDirection.POSITIVE ? 0.999 : 0.001) : var13[1]);
               Vec3d var20 = new Vec3d(var14, var16, var18);
               if (var2.squaredDistanceTo(var20) <= var3 && this.rayHits(var2, var20, var1)) {
                  return var20;
               }
            }
         }

         return null;
      }
   }

   private boolean rayHits(Vec3d var1, Vec3d var2, BlockPos var3) {
      BlockHitResult var4 = this.mc
         .world
         .raycast(new RaycastContext(var1, var2.add(var2.subtract(var1).normalize().multiply(0.01)), ShapeType.OUTLINE, FluidHandling.NONE, this.mc.player));
      return var4.getType() == Type.BLOCK && var4.getBlockPos().equals(var3);
   }

   private boolean brokenConfirmed() {
      if (this.mining == null) {
         return true;
      } else {
         if (!this.mining.equals(this.brokeSeenPos)) {
            this.brokeSeenPos = this.mining.toImmutable();
            this.brokeSeenAt = this.ticks;
         }

         return !this.ackWait(this.mining) && this.ticks - this.brokeSeenAt >= 3;
      }
   }

    private boolean breakBlock(BlockState actual) {
        // 3.0.122: KHÔNG nhả chuột trái ở đầu mỗi lần gọi nữa. Bản cũ nhả rồi mới bấm lại; chỉ cần 1 tick chưa kịp gửi góc nhìn
        // (rotationSent) hay ngắm lệch là chuột bị nhả → game huỷ đập (gửi ABORT) → vết nứt mất, server tính lại từ đầu
        // ("client thấy vỡ nhưng server chưa phá"). Giờ giữ chuột như người thật: đã giữ trên đúng khối thì giữ tiếp.
        boolean holding = mining != null && mining.equals(qualityAttackPos) && mc.options.attackKey.isPressed();
        if (mining == null || actual.isReplaceable()) { stopAttack(); return true; }
        if (tntBreakRisk(mining) != null) {
            stopAttack();
            if (mc.interactionManager.isBreakingBlock()) mc.interactionManager.cancelBlockBreaking();
            status = "An toàn TNT: không đập " + actual.getBlock().getName().getString() + " @ " + mining.toShortString();
            return true;
        }
        // 3.0.122 (người dùng): khối có nước bên trong (lá, slab, thang… waterlogged) → múc nước bằng xô rỗng TRƯỚC khi đập,
        // đập thẳng thì nước tràn ra làm hỏng công trình
        if (drainBeforeBreak(actual)) return true;
        brokeSeenPos = null;
        Item tool = null; float speed = 1F;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() < 3) continue;
            float candidate = stack.getMiningSpeedMultiplier(actual);
            if (candidate > speed) { speed = candidate; tool = stack.getItem(); }
        }
        if (forceTool != null && InventoryAccess.count(mc, forceTool) > 0) tool = forceTool;
        if (tool != null && !mc.player.getMainHandStack().isOf(tool)) {
            stopAttack();
            if (!InventoryAccess.equip(mc, tool, config.workingHotbarSlot)) return false;
        }
        if (!InventoryAccess.synced(mc)) { if (!holding) stopAttack(); else keepAttack(); return true; }
        BlockHitResult live = PlacementSolver.ray(mc, reach());
        boolean liveOn = live.getType() == HitResult.Type.BLOCK && live.getBlockPos().equals(mining)
            && mc.player.getEyePos().squaredDistanceTo(live.getPos()) <= reach() * reach();
        if (holding && liveOn && Input.canHoldAttack(mc)) {
            // đang đập đúng khối: không xoay đầu theo điểm ngắm mới (xoay liên tục làm góc gửi lên server đổi mỗi tick)
            keepAttack();
            return true;
        }
        if (!mining.equals(minePointPos) || minePoint == null || !rayHits(mc.player.getEyePos(), minePoint, mining)) {
            minePoint = visiblePoint(mining); minePointPos = mining.toImmutable();
        }
        if (minePoint == null) { stopAttack(); return true; }
        Vec3d delta = minePoint.subtract(mc.player.getEyePos());
        lookAt(MotionMath.yaw(delta.x, delta.z), MotionMath.pitch(delta.x, delta.y, delta.z));
        live = PlacementSolver.ray(mc, reach());
        if (!rotationSent() || live.getType() != HitResult.Type.BLOCK || !live.getBlockPos().equals(mining)
            || mc.player.getEyePos().squaredDistanceTo(live.getPos()) > reach() * reach()
            || !Input.canHoldAttack(mc) || Input.usePending()) {
            if (!holding) stopAttack();
            return true;
        }
        // Bấm chuột trái thật (Input.flush → KeyBinding.setKeyPressed + onKeyPressed): game tự doAttack + handleBlockBreaking.
        mc.crosshairTarget = live;
        if (!holding) AutoBuildMod.LOGGER.debug("[AutoBuild] Giữ chuột trái đập {} @ {}", actual.getBlock().getName().getString(), mining.toShortString());
        qualityAttackPos = mining.toImmutable();
        keepAttack();
        return true;
    }

    /** 3.0.122: giữ chuột trái thêm 1 tick trên khối đang đập */
    private void keepAttack() {
        if (qualityAttackPos == null) return;
        qualityAttackTick = ticks;
        Input.key(mc.options.attackKey, true);
    }

    /** 3.0.122: nhả chuột trái hẳn (không giữ tiếp ở cuối tick) */
    private void stopAttack() {
        qualityAttackPos = null;
        Input.key(mc.options.attackKey, false);
    }

    // ===== 3.0.122: múc nước khỏi khối trước khi đập =====
    private BlockPos drainPos;
    private int drainTries;
    private int drainClickAt = -1000;
    private int drainSaidAt = -1000;
    /** cần mua xô rỗng (SurvivalSupplies) */
    boolean needEmptyBucket;
    private final Map<BlockPos, Integer> drainGiveUp = new HashMap<>();

    private static boolean waterInside(BlockState state) {
        return state != null && state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED);
    }

    /** true = đang lo múc nước (không đập trong tick này) */
    private boolean drainBeforeBreak(BlockState actual) {
        if (!waterInside(actual)) {
            if (mining != null && mining.equals(drainPos)) {
                AutoBuildMod.LOGGER.info("[AutoBuild] Đã múc hết nước khỏi {} @ {}", actual.getBlock().getName().getString(), drainPos.toShortString());
                drainPos = null;
                drainTries = 0;
                InventoryAccess.markDirty(mc, "múc nước khỏi khối");
                // múc xong mà khối đã đúng bản vẽ (vd. lá thừa nước) → không đập nữa
                Target wanted = plan == null ? null : plan.at(mining);
                if (wanted != null && done(actual, wanted.state()) && repairing) {
                    stopAttack();
                    endSideMining();
                    return true;
                }
            }
            return false;
        }
        stopAttack();
        if (mc.interactionManager.isBreakingBlock()) mc.interactionManager.cancelBlockBreaking();
        if (!mining.equals(drainPos)) {
            drainPos = mining.toImmutable();
            drainTries = 0;
            AutoBuildMod.LOGGER.info("[AutoBuild] {} @ {} có nước bên trong → múc nước bằng xô rỗng trước, rồi mới đập (không làm tràn nước)",
                actual.getBlock().getName().getString(), drainPos.toShortString());
        }
        if (InventoryAccess.count(mc, Items.BUCKET) == 0) {
            needEmptyBucket = true;
            status = "Cần xô rỗng để múc nước ở " + mining.toShortString() + " → mua ở /shop";
            if (ticks - drainSaidAt > 400) {
                drainSaidAt = ticks;
                AutoBuildMod.LOGGER.info("[AutoBuild] Không có xô rỗng để múc nước ở {} → mua xô ở /shop, chưa đập khối này", mining.toShortString());
            }
            return true;
        }
        if (drainTries >= 6) {
            if (drainGiveUp.merge(mining.toImmutable(), 1, Integer::sum) == 1)
                AutoBuildMod.LOGGER.info("[AutoBuild] Múc 6 lần mà {} vẫn còn nước → không đập (nước sẽ tràn), để lại", mining.toShortString());
            status = "Không múc được nước ở " + mining.toShortString() + " → không đập";
            if (repairing || sweeping) {
                sweepSkip.add(mining.toImmutable());
                sweepNow.remove(mining);
                endSideMining();
            }
            return true;
        }
        if (!mc.player.getMainHandStack().isOf(Items.BUCKET)) {
            InventoryAccess.equip(mc, Items.BUCKET, config.workingHotbarSlot);
            status = "Cầm xô rỗng để múc nước";
            return true;
        }
        if (!InventoryAccess.synced(mc)) return true;
        Vec3d point = visiblePoint(mining);
        if (point == null) point = Vec3d.ofCenter(mining);
        Vec3d delta = point.subtract(mc.player.getEyePos());
        lookAt(MotionMath.yaw(delta.x, delta.z), MotionMath.pitch(delta.x, delta.y, delta.z));
        status = "Múc nước khỏi " + actual.getBlock().getName().getString() + " @ " + mining.toShortString();
        if (!rotationSent() || ticks - drainClickAt < 8 + latencyTicks() * 2 || unacked > 0 || Input.usePending()) return true;
        BlockPos at = mining.toImmutable();
        // xô rỗng tự nhắm theo hướng nhìn (BucketItem.use) → kiểm tra tia nước trúng đúng ô
        BlockHitResult fluidRay = mc.world.raycast(new RaycastContext(mc.player.getEyePos(),
            mc.player.getEyePos().add(mc.player.getRotationVec(1F).multiply(mc.player.getBlockInteractionRange())),
            ShapeType.OUTLINE, FluidHandling.SOURCE_ONLY, mc.player));
        if (fluidRay.getType() != HitResult.Type.BLOCK || !fluidRay.getBlockPos().equals(at)) {
            status = "Ngắm xô vào " + at.toShortString();
            return true;
        }
        drainClickAt = ticks;
        drainTries++;
        clickTick = ticks;
        Input.queueUse(mc, null, false, "múc nước khỏi khối @ " + at.toShortString(), () -> {
            Item before = mc.player.getMainHandStack().getItem();
            int count = mc.player.getMainHandStack().getCount();
            ActionResult r = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            if (r != null && r.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
            qualityBucketUsed(before, count, "múc nước khỏi khối");
        });
        return true;
    }



   private void enforceStrictLayer(BuildController.Scan var1) {
      if (this.config.layerMode && this.plan != null && this.mc.world != null && var1 != null && var1.prepped && var1.gate != null) {
         int var2 = var1.gate;
         int var3 = var1.unfinished.getOrDefault(var2, 0);
         LayerPlanner.Stat var8 = var1.layers.computeIfAbsent(var2, var0 -> new LayerPlanner.Stat());
         var8.remaining = Math.max(var8.remaining, var3);
         Integer var9 = this.currentLayer;
         this.currentLayer = var2;
         Map<Item, Integer> var10 = var1.layerAll.getOrDefault(var2, Map.of());
         this.currentLayerTotal = var10.values().stream().mapToInt(Integer::intValue).sum();
         this.currentLayerDone = Math.max(0, this.currentLayerTotal - var3);
         if (this.running && this.config.syncLitematicaLayer && !this.cleanupMode) {
            com.donutautobuild.schematic.SchematicWorkspace.syncLayer(var2);
         }

         if (var9 != null && var9 > var2) {
            this.queue.removeIf(var1x -> var1x.pos().getY() > var2);
            if (this.target != null && this.target.pos().getY() > var2 && this.temporary == null && this.supportTarget == null) {
               this.target = null;
               this.aim = null;
               this.anglesFor = null;
               if (this.stage == BuildController.Stage.AIM || this.stage == BuildController.Stage.EQUIP || this.stage == BuildController.Stage.MOVE) {
                  this.release();
                  this.setStage(BuildController.Stage.NEXT);
               }
            }
         }

         if (var9 != null && var9 > var2 && this.ticks - this.strictLayerWarnAt > 200) {
            this.strictLayerWarnAt = this.ticks;
            AutoBuildMod.LOGGER.warn("[AutoBuild] Chặn chuyển tầng {} → {} vì tầng {} còn {} khối chưa xong", new Object[]{var9, var2, var2, var3});
         }
      }
   }

   private boolean tryPlaceReachableOffPlane() {
      if (this.config.layerMode
         && this.currentLayer != null
         && this.mc.player != null
         && this.mc.player.isOnGround()
         && !this.onLayerPlane()
         && this.queue != null
         && !this.queue.isEmpty()) {
         Vec3d var1 = this.mc.player.getEyePos();
         double var2 = this.reach();
         ArrayList<SchematicPlan.Target> var4 = new ArrayList<>(this.queue);
         var4.sort(Comparator.comparingDouble(var1x -> var1.squaredDistanceTo(Vec3d.ofCenter(var1x.pos()))));
         int var5 = 0;

         for (SchematicPlan.Target var7 : var4) {
            var5++;
            if (var5 <= 80
               && var7.pos().getY() == this.currentLayer
               && !this.occupies(var7.pos())
               && this.mc.world.isChunkLoaded(var7.pos())
               && this.hasSupport(var7.pos())
               && !(var1.distanceTo(Vec3d.ofCenter(var7.pos())) > var2 + 1.2)) {
               SchematicPlan.Target var8 = this.resolve(var7);
               PlacementSolver.Aim var9 = PlacementSolver.solve(this.mc, var1, var8, var2, true, var7.pos().asLong() * 31L);
               if (var9 != null
                  && PlacementSolver.robust(this.mc, PlacementSolver.poseEye(PlacementSolver.standingEye(this.mc, var1), var9.sneak()), var9, var2)) {
                  this.queue.remove(var7);
                  this.target = var8;
                  this.aim = var9;
                  this.status = "Đặt ngay từ vị trí hiện tại: " + var8.state().getBlock().getName().getString() + " @ " + var8.pos().toShortString();
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Đang thấp hơn mặt tầng nhưng {} @ {} nằm trong tầm → đặt ngay",
                        var8.state().getBlock().getName().getString(),
                        var8.pos().toShortString()
                     );
                  this.setStage(BuildController.Stage.EQUIP);
                  this.equip();
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean finishSkippedLayer() {
      if (this.plan != null && this.currentLayer != null && this.mc.world != null && !this.cleanupMode && this.config.layerMode) {
         int var1 = 0;
         int var2 = 0;

         for (SchematicPlan.Target var4 : this.plan.targets) {
            BlockPos var5 = var4.pos();
            if (var5.getY() == this.currentLayer
               && this.config.inYRange(var5.getY())
               && this.mc.world.isChunkLoaded(var5)
               && !this.placedDone.contains(var5)
               && !this.done(this.mc.world.getBlockState(var5), var4.state())) {
               if (this.skipped.contains(var5)) {
                  var1++;
               } else {
                  var2++;
               }
            }
         }

         if (var1 != 0 && var2 <= 0 && this.layerFinishTries.merge(this.currentLayer, 1, Integer::sum) <= 3) {
            this.lastProgress = this.ticks;
            this.loopProgressTick = this.ticks;
            this.loopRecoveries = 0;
            this.loopNextAction = this.ticks + 160;
            this.climbSearch = false;
            this.climbFailUntil = 0;
            this.search = null;
            this.release();
            this.status = "Tầng Y=" + this.currentLayer + " chỉ còn " + var1 + " khối không đặt được → xây tiếp";
            AutoBuildMod.LOGGER.info("[AutoBuild] Tầng Y={} chỉ còn {} khối không đặt được → xây tiếp thay vì dừng", this.currentLayer, var1);
            this.setStage(BuildController.Stage.NEXT);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private int skipStuckBlocks() {
      if (this.plan != null && this.currentLayer != null && this.mc.world != null) {
         ArrayList<BlockPos> var1 = new ArrayList<>();

         // 3.0.84: chỉ bỏ qua khối đang làm + tối đa 12 khối gần nhất đã hoãn ≥ 3 lần. Bản cũ bỏ qua MỌI khối đang hoãn
         // của tầng (hàng nghìn khối lúc nhân vật bị kẹt, rồi lần 2 thành "bỏ hẳn") → tầng -47 kẹt 1460 khối.
         boolean var20 = this.ticks - this.stuckSeenAt < 2400;
         Vec3d var21 = this.mc.player.getPos();
         ArrayList<BlockPos> var22 = new ArrayList<>();
         if (!var20) {
            for (SchematicPlan.Target var3 : this.plan.targets) {
               BlockPos var4 = var3.pos();
               if (var4.getY() == this.currentLayer
                  && this.config.inYRange(var4.getY())
                  && !this.skipped.contains(var4)
                  && !this.placedDone.contains(var4)
                  && this.mc.world.isChunkLoaded(var4)
                  && this.deferCount.getOrDefault(var4, 0) >= 3
                  && !this.done(this.mc.world.getBlockState(var4), var3.state())) {
                  var22.add(var4.toImmutable());
               }
            }

            var22.sort(Comparator.comparingDouble(var1x -> Vec3d.ofCenter(var1x).squaredDistanceTo(var21)));
         }

         if (this.current() != null && !this.skipped.contains(this.current().pos())) {
            var1.add(this.current().pos().toImmutable());
         }

         for (int var23 = 0; var23 < Math.min(12, var22.size()); var23++) {
            if (!var1.contains(var22.get(var23))) {
               var1.add(var22.get(var23));
            }
         }

         for (BlockPos var6 : var1) {
            int var7 = this.stuckSkips.getOrDefault(var6, 0) + 1;
            this.stuckSkips.put(var6, var7);
            this.skipped.add(var6);
            this.deferred.remove(var6);
            this.deferCount.remove(var6);
            this.attempts.remove(var6);
            if (var7 >= 3 && !var20) {
               // 3.0.99: không bỏ hẳn khối nào — chỉ để cuối tầng, xây xong phần còn lại sẽ thử lại (retryLayer / postponedLayers)
               this.skipReason.put(var6, "Kẹt lâu ở tầng Y=" + this.currentLayer + " → để cuối tầng, sẽ thử lại");
               AutoBuildMod.LOGGER.info("[AutoBuild] Khối kẹt @ {} (lần {}) → để cuối tầng, xây xong phần còn lại sẽ thử lại", var6.toShortString(), var7);
            } else {
               AutoBuildMod.LOGGER.info("[AutoBuild] Tạm bỏ qua khối kẹt @ {}, sẽ thử lại sau khi xây xong phần còn lại của tầng", var6.toShortString());
            }
         }

         return var1.size();
      } else {
         return 0;
      }
   }

   private boolean breakLoopIfStalled() {
      if (!this.running || this.menuStage() || this.stage == BuildController.Stage.LOAD) {
         return false;
      } else if (Math.max(this.lastProgress, this.navProgressAt) != this.loopProgressTick) {
         // 3.0.82: đang đi gần đích hơn cũng tính là tiến triển (đường dài 60 ô không còn bị cắt ở giây thứ 10 rồi quay đầu)
         this.loopProgressTick = Math.max(this.lastProgress, this.navProgressAt);
         this.loopRecoveries = 0;
         this.loopNextAction = 0;
         return false;
      } else {
         int var1 = this.ticks - this.loopProgressTick;
         if (var1 < 200 || this.ticks < this.loopNextAction) {
            return false;
         } else if (this.cleanupMode) {
            if (this.mining != null) {
               this.cleanupDeferred.add(this.mining);
            }

            this.lastProgress = this.ticks;
            this.loopProgressTick = this.ticks;
            this.mining = null;
            this.release();
            AutoBuildMod.LOGGER.info("[AutoBuild] Thu dọn: 10 giây không đập được khối kê này → để sau, dọn khối khác (không dừng)");
            this.setStage(BuildController.Stage.CLEANUP);
            return true;
         } else if (this.loopRecoveries++ == 0) {
            this.loopNextAction = this.ticks + 160;
            SchematicPlan.Target var4 = this.target;
            if (var4 != null && this.plan != null) {
               SchematicPlan.Target var5 = this.plan.at(var4.pos());
               if (var5 != null && !this.queue.contains(var5)) {
                  this.queue.addFirst(var5);
               }
            }

            this.climbSearch = false;
            this.climbFailUntil = 0;
            this.search = null;
            this.release();
            this.status = "Thoát vòng lặp " + this.stage + ", thử cách khác tại tầng Y=" + this.currentLayer;
            AutoBuildMod.LOGGER.warn("[AutoBuild] {} giây không tiến triển ở {} → hủy nhánh hiện tại và chọn lại, không chuyển tầng", var1 / 20, this.stage);
            this.setStage(BuildController.Stage.NEXT);
            return true;
         } else {
            int var2 = this.skipStuckBlocks();
            if (var2 > 0) {
               this.loopNextAction = this.ticks + 160;
               this.loopRecoveries = 1;
               this.lastProgress = this.ticks;
               this.loopProgressTick = this.ticks;
               this.climbSearch = false;
               this.climbFailUntil = 0;
               this.search = null;
               this.release();
               this.status = "Bỏ qua " + var2 + " khối kẹt ở tầng Y=" + this.currentLayer + " — xây tiếp phần còn lại";
               AutoBuildMod.LOGGER
                  .warn("[AutoBuild] {} giây không tiến triển ở {} → bỏ qua {} khối kẹt, xây tiếp thay vì dừng", new Object[]{var1 / 20, this.stage, var2});
               this.setStage(BuildController.Stage.NEXT);
               return true;
            } else if (this.finishSkippedLayer()) {
               return true;
            } else {
               this.loopNextAction = this.ticks + 160;
               this.loopRecoveries = 1;
               this.lastProgress = this.ticks;
               this.loopProgressTick = this.ticks;
               this.climbSearch = false;
               this.climbFailUntil = 0;
               this.search = null;
               this.release();
               // 3.0.82: KHÔNG gọi retryLayer ở đây nữa. Bản 3.0.81 coi "18 giây đứng lặp một thao tác" là "tầng xây không được"
               // → 3 lần là hoãn CẢ TẦNG (3205 khối chưa xây) rồi lên tầng trên. Kẹt thao tác thì reset thao tác, không bỏ tầng.
               this.stallRecover(var1 / 20);
               return true;
            }
         }
      }
   }

   private int stallRecoverCount;
   private int stallRecoverAt = -100000;
   private BlockPos stallRecoverPos;
   // 3.0.98: kẹt ghost block (log 21:36–21:37: 75 giây bị giật lại quanh 1 ô ở Y=-54, người dùng phải tự /home)
   private BlockPos ghostAreaPos;
   private int ghostAreaCount;
   private int ghostAreaFirst;
   private BlockPos ghostEscapePos;
   private int ghostEscapeAt = -100000;

   /** ghi 1 lần bị giật lại tại var1; đủ 5 lần trong 60 giây quanh cùng chỗ (≤ 4 ô) → thoát ra. true = đã xử lý (đang /home hoặc vào lại) */
   /** 3.0.123: hoàn cảnh lúc bị server kéo lùi — để dò nguyên nhân nếu còn bị giật */
   private void logRubberContext() {
      try {
         Vec3d var1 = this.mc.player.getVelocity();
         BlockPos var2 = this.mc.player.getBlockPos().down();
         AutoBuildMod.LOGGER.info(
            "[AutoBuild] [GIẬT] bấm túi đồ cách {} ms | đặt khối cách {} tick | chạy nhanh={} ngồi={} trên đất={} | v={} | phím W={} S={} A={} D={} nhảy={} | dưới chân {} (chưa xác nhận={}) | ping {} ms",
            new Object[]{
               InventoryAccess.sinceInvClick(),
               this.ticks - this.clickTick,
               this.mc.player.isSprinting(),
               this.mc.player.isSneaking(),
               this.mc.player.isOnGround(),
               String.format(Locale.ROOT, "%.3f", Math.sqrt(var1.horizontalLengthSquared())),
               this.mc.options.forwardKey.isPressed(),
               this.mc.options.backKey.isPressed(),
               this.mc.options.leftKey.isPressed(),
               this.mc.options.rightKey.isPressed(),
               this.mc.options.jumpKey.isPressed(),
               this.mc.world.getBlockState(var2).getBlock().getName().getString(),
               this.ackWait(var2) || this.unverified(var2),
               this.pingMs()
            }
         );
      } catch (Throwable var3) {
      }
   }

   private boolean ghostAreaNote(BlockPos var1) {
      if (this.ghostAreaPos == null || this.ghostAreaPos.getManhattanDistance(var1) > 5 || this.ticks - this.ghostAreaFirst > 1200) {
         this.ghostAreaPos = var1.toImmutable();
         this.ghostAreaCount = 0;
         this.ghostAreaFirst = this.ticks;
      }

      if (++this.ghostAreaCount >= 5) {
         int var2 = (this.ticks - this.ghostAreaFirst) / 20;
         BlockPos var3 = this.ghostAreaPos;
         this.ghostAreaPos = null;
         this.ghostAreaCount = 0;
         return this.ghostFlee(var3, "bị server giật lại 5 lần trong " + var2 + " giây quanh " + var3.toShortString());
      } else {
         return false;
      }
   }

   /**
    * 3.0.98: kẹt ở một chỗ không đi được (ghost block): né vùng đó 60 giây, hoãn các khối quanh đó,
    * rồi /home về home của mod để thoát ra (như người dùng tự làm). Cùng chỗ kẹt lại lần 2 trong 5 phút (ghost vẫn còn)
    * hoặc không có home dùng được → thoát ra vào lại server (tải lại chunk, hết ghost block).
    */
   private boolean ghostFlee(BlockPos var1, String var2) {
      if (!this.running || this.mc.player == null || this.mc.world == null || this.menuStage() || this.stage == BuildController.Stage.HOME) {
         return false;
      } else {
         for (int var3 = -2; var3 <= 2; var3++) {
            for (int var4 = -2; var4 <= 2; var4++) {
               for (int var5 = -1; var5 <= 1; var5++) {
                  BlockPos var6 = var1.add(var3, var5, var4);
                  Navigation.AVOID.add(var6.toImmutable());
                  if (this.plan != null && this.plan.at(var6) != null && Math.abs(var3) <= 1 && Math.abs(var4) <= 1) {
                     this.deferred.put(var6.toImmutable(), this.ticks + 1200);
                  }
               }
            }
         }

         this.avoidSince = this.ticks;
         boolean var7 = this.ghostEscapePos != null && this.ghostEscapePos.getManhattanDistance(var1) <= 8 && this.ticks - this.ghostEscapeAt < 6000;
         this.ghostEscapePos = var1.toImmutable();
         this.ghostEscapeAt = this.ticks;
         this.release();
         this.target = null;
         this.temporary = null;
         this.supportTarget = null;
         this.supportQueue.clear();
         this.aim = null;
         this.pending = null;
         this.sweeping = false;
         this.repairing = false;
         this.mining = null;
         boolean var8 = this.config.useHome
            && this.config.ownHomeNumber > 0
            && ScaffoldJournal.worldKey(this.mc).equals(this.config.ownHomeWorld)
            && this.ticks >= this.homeTpFailUntil
            && this.ticks - this.homeTpAt >= 100;
         if (!var7 && var8) {
            this.homeHold = this.mc.player.getPos();
            this.escUntil = 0;
            this.wsPos = null;
            AutoBuildMod.LOGGER.info("[AutoBuild] Kẹt ghost block: {} → /{} {} thoát ra, né chỗ này 60 giây rồi xây tiếp", new Object[]{var2, this.config.homeCommand, this.config.ownHomeNumber});
            this.say("Kẹt ghost block (" + var2 + ") → /" + this.config.homeCommand + " " + this.config.ownHomeNumber + " thoát ra, xây tiếp chỗ khác.");
            this.homes.startTeleport(this.mc, this.config.ownHomeNumber, new Vec3d(this.config.ownHomeX, this.config.ownHomeY, this.config.ownHomeZ));
            this.setStage(BuildController.Stage.HOME);
            return true;
         } else {
            String var9 = var7 ? "kẹt ghost block lần 2 ở cùng chỗ (" + var2 + ")" : "kẹt ghost block (" + var2 + "), chưa có home của mod để /home";
            AutoBuildMod.LOGGER.info("[AutoBuild] {} → thoát ra vào lại để server gửi lại khối đúng", var9);
            Reconnector.request(this.mc, this, var9);
            return true;
         }
      }
   }

   /** 3.0.82: kẹt lặp một thao tác (dọn kê, đi hàng, leo tầng...) mà không có khối kẹt nào để bỏ qua → bỏ thao tác đó, quét lại. */
   private void stallRecover(int var1) {
      BlockPos var2 = this.mc.player.getBlockPos().toImmutable();
      if (this.ticks - this.stallRecoverAt > 2400 || this.stallRecoverPos == null || this.stallRecoverPos.getManhattanDistance(var2) > 3) {
         this.stallRecoverCount = 0;
      }

      this.stallRecoverCount++;
      this.stallRecoverAt = this.ticks;
      this.stallRecoverPos = var2;
      int var3 = this.sweepNow.size();
      this.sweepSkip.addAll(this.sweepNow);
      this.sweepNow.clear();
      this.sweepSkipAt = this.ticks;
      this.sweepHoldUntil = this.ticks + 1200;
      if (this.trailTemps != null) {
         this.trailTemps.clear();
      }

      this.job = null;
      this.planeMove = false;
      this.climbMove = false;
      this.chain.clear();
      this.target = null;
      this.supportTarget = null;
      this.supportQueue.clear();
      this.temporary = null;
      if (this.stallRecoverCount >= 2 && this.stage == BuildController.Stage.MOVE && this.ticks - this.lastRubberAt < 400
         && this.ghostFlee(var2, "10 giây không tiến triển lần " + this.stallRecoverCount + " tại " + var2.toShortString() + ", vừa bị giật lại")) {
         this.stallRecoverCount = 0;
         return;
      }

      if (this.stallRecoverCount >= 2) {
         // cùng một chỗ kẹt lần 2+ → né ô đang đứng và các ô quanh chân một lúc để chọn chỗ đứng/đường khác
         for (Direction var5 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            Navigation.AVOID.add(var2.offset(var5).toImmutable());
         }

         Navigation.AVOID.add(var2);
         this.avoidSince = this.ticks;
      }

      AutoBuildMod.LOGGER
         .warn(
            "[AutoBuild] {} giây không tiến triển ở {} (lần {} tại {}): không có khối kẹt nào để bỏ qua → bỏ thao tác đang lặp (hoãn dọn {} khối kê 60 giây), quét lại tầng Y={}, KHÔNG bỏ tầng",
            new Object[]{var1, this.stage, this.stallRecoverCount, var2.toShortString(), var3, this.currentLayer}
         );
      this.status = "Thoát vòng lặp → quét lại tầng Y=" + this.currentLayer;
      this.beginScan();
   }

   private boolean pauseDependencyLoop(BlockPos var1) {
      SchematicPlan.Target var2 = this.plan == null ? null : this.plan.at(var1);
      String var3 = var2 == null ? "khối" : var2.state().getBlock().getName().getString();
      Item var4 = this.depMissingFor.get(var1);
      this.deferred.put(var1.toImmutable(), this.ticks + 600);
      this.depWaits.put(var1.toImmutable(), 0);
      AutoBuildMod.LOGGER
         .info(
            "[AutoBuild] Hoãn {} @ {} 30 giây: {} → làm khối khác rồi thử lại (không dừng)",
            new Object[]{
               var3,
               var1.toShortString(),
               var4 == null ? "chưa có khối phụ thuộc sau nhiều lần thử" : "cần có " + var4.getName().getString() + " bên cạnh trước, mà trong túi không còn"
            }
         );
      return false;
   }

   // 3.0.98: khối kê vừa đập hỏng (không nhìn thấy / không đứng được) → không thử lại trước mốc này
   private final Map<BlockPos, Integer> sweepFailUntil = new HashMap<>();
   private final Map<BlockPos, Integer> sweepFails = new HashMap<>();

   private boolean sweepBlocked(BlockPos var1) {
      if (this.sweepFailUntil.isEmpty()) {
         return false;
      } else {
         Integer var2 = this.sweepFailUntil.get(var1);
         if (var2 == null) {
            return false;
         } else if (var2 <= this.ticks) {
            this.sweepFailUntil.remove(var1);
            return false;
         } else {
            return true;
         }
      }
   }

   private Set<BlockPos> trailTemps() {
      if (this.trailTemps == null) {
         this.trailTemps = new LinkedHashSet<>();
      }

      return this.trailTemps;
   }

   private Map<BlockPos, Integer> trailDeferred() {
      if (this.trailDeferred == null) {
         this.trailDeferred = new HashMap<>();
      }

      return this.trailDeferred;
   }

   private void rememberCurrentTemporary() {
      if (this.sweepSkip != null && this.sweepNow != null && !this.sweepNow.isEmpty()) {
         this.sweepSkip.removeAll(this.sweepNow);
      }

      if (this.temporary != null && this.journal != null && this.journal.placed.containsKey(this.temporary.pos())) {
         this.trailTemps().add(this.temporary.pos().toImmutable());
      }
   }

   private int lastRubberAt = -1000;

   /** khối ở xa thân người ≥ d ô theo phương ngang (tâm khối so với tâm người): chưa đập khối tạm mình vừa bước khỏi mép. */
   private boolean horizFar(BlockPos var1, double var2) {
      double var4 = (double)var1.getX() + 0.5 - this.mc.player.getX();
      double var6 = (double)var1.getZ() + 0.5 - this.mc.player.getZ();
      return var4 * var4 + var6 * var6 >= var2 * var2;
   }

   private boolean trySweepTrail() {
      // 3.0.85: vừa bị server giật lại thì vị trí thật chưa chắc, đường đi sắp đi lại qua khối tạm vừa rời → chưa đập
      // (log 13:42: bắc cầu tạm → bị giật lại → "Rời khối tạm → đập ngay" → lùi ra rơi xuống bể nước).
      if (this.ticks - this.lastRubberAt < 60 || this.backoffTicks > 0) {
         return false;
      } else if (this.temporary == null
         && this.supportTarget == null
         && this.config.sweepScaffold
         && this.journal != null
         && this.trailTemps != null
         && !this.trailTemps.isEmpty()
         && this.path != null
         && this.pathIndex < this.path.size()
         && this.mc.player.isOnGround()) {
         HashSet var1 = new HashSet();

         for (int var2 = this.pathIndex; var2 < this.path.size(); var2++) {
            var1.add(Navigation.pos(this.path.get(var2)).down());
         }

         Box var8 = this.mc.player.getBoundingBox().expand(0.04, 0.0, 0.04).stretch(0.0, -0.25, 0.0);
         double var3 = this.reach() * this.reach();
         Iterator var5 = this.trailTemps.iterator();

         while (var5.hasNext()) {
            BlockPos var6 = (BlockPos)var5.next();
            if (!this.journal.placed.containsKey(var6)) {
               var5.remove();
            } else if (this.trailDeferred().getOrDefault(var6, 0) <= this.ticks
               && !this.sweepBlocked(var6)
               && !var1.contains(var6)
               && !var8.intersects(new Box(var6))
               && this.horizFar(var6, 1.5)
               && !(this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(var6)) > var3)
               && this.safeMiningStand(Navigation.cell(this.mc.player.getBlockPos()), var6)
               && this.safeToRemove(var6)
               && this.visiblePoint(var6) != null) {
               BlockState var7 = this.mc.world.getBlockState(var6);
               if (var7.isAir()) {
                  this.journal.remove(var6);
                  this.sweepNow.remove(var6);
                  var5.remove();
               } else {
                  if (Registries.BLOCK.getId(var7.getBlock()).toString().equals(this.journal.placed.get(var6))) {
                     this.release();
                     this.sweeping = true;
                     this.mining = var6;
                     this.miningTicks = 0;
                     this.aim = null;
                     this.pending = null;
                     this.status = "Thu hồi ngay khối tạm " + var6.toShortString();
                     AutoBuildMod.LOGGER.info("[AutoBuild] Rời khối tạm @ {} → đập ngay rồi đi tiếp", var6.toShortString());
                     this.setStage(BuildController.Stage.MINE);
                     return true;
                  }

                  var5.remove();
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean resumeTrailMoveAfterSweep() {
      if (this.sweeping
         && this.path != null
         && this.pathIndex < this.path.size()
         && this.mining != null
         && this.trailTemps != null
         && this.trailTemps.contains(this.mining)) {
         BlockPos var1 = this.mining.toImmutable();
         boolean var2 = this.mc.world.getBlockState(var1).isReplaceable() || !this.journal.placed.containsKey(var1);
         if (var2) {
            this.trailTemps.remove(var1);
            this.trailDeferred().remove(var1);
            this.sweepNow.remove(var1);
         } else if (this.sweepBlocked(var1)) {
            this.trailDeferred().put(var1, this.sweepFailUntil.getOrDefault(var1, this.ticks + 1200));
         } else {
            this.sweepNow.add(var1);
            this.trailDeferred().put(var1, this.ticks + 80);
         }

         this.sweeping = false;
         this.repairing = false;
         this.mining = null;
         this.setStage(BuildController.Stage.MOVE);
         this.status = var2 ? "Đã thu hồi khối tạm, tiếp tục đi" : "Tạm hoãn thu hồi, tiếp tục đi";
         return true;
      } else {
         return false;
      }
   }

   private int roadFailUntil;
   private int lastDeferRush = -1000;
   private int standUntil = -1000;
   private int pourAt = -1000;
   private BlockPos pourPos;
   private BlockPos planeChainFor;
   private Vec3d mvAnchor;
   private int mvAnchorAt;
   private int mvStall;
   private int mvStage = -1;
   private int mvLastCall = -1000;

   /**
    * 3.0.78: phát hiện "đi mà không tới" theo QUÃNG ĐƯỜNG thực trong 2,5 giây, không theo từng tick.
    * Lỗi cũ: nhân vật ngồi ở mép khối lá, nhích qua nhích lại 0,02–0,06 khối/tick nên bộ đếm kẹt
    * (so từng tick) không bao giờ tăng → đứng MOVE 10–18 giây mới bị watchdog chung cắt.
    */
   private boolean moveStallCheck(PathSearch.Cell var1) {
      Vec3d var2 = this.mc.player.getPos();
      boolean var4 = this.ticks - this.mvLastCall > 2;
      this.mvLastCall = this.ticks;
      if (this.mvAnchor == null || var4 || this.mvStage != this.stageEnterTick() || var2.squaredDistanceTo(this.mvAnchor) > 0.16) {
         if (this.mvAnchor != null && var2.squaredDistanceTo(this.mvAnchor) > 0.16) {
            this.mvStall = 0;
         }

         this.mvAnchor = var2;
         this.mvAnchorAt = this.ticks;
         this.mvStage = this.stageEnterTick();
         return false;
      } else if (this.ticks - this.mvAnchorAt < 50) {
         return false;
      } else {
         this.mvAnchor = var2;
         this.mvAnchorAt = this.ticks;
         if (++this.mvStall == 1) {
            if ((double)var1.y() < this.mc.player.getY() - 0.6) {
               this.standUntil = this.ticks + 12;
            }

            if (this.mc.player.isOnGround()) {
               Input.key(this.mc.options.jumpKey, true);
            }

            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Đi 2,5 giây không nhích tới {} (đang ngồi={}) → đứng thẳng dậy + nhảy thử",
                  var1,
                  this.mc.player.isSneaking()
               );
            return false;
         } else {
            this.mvStall = 0;
            BlockPos var3 = Navigation.pos(var1);
            Navigation.AVOID.add(var3.toImmutable());
            this.avoidSince = this.ticks;
            AutoBuildMod.LOGGER.info("[AutoBuild] Vẫn kẹt khi đi tới {} → tránh ô này 30 giây, tìm đường khác", var3.toShortString());
            this.release();
            this.navigationFailed("Kẹt khi đi tới " + var3.toShortString() + " → đổi đường");
            return true;
         }
      }
   }

   private int bowlTicks;
   private int bowlSaidAt = -1000;

   /**
    * 3.0.78: đang lọt trong lòng hopper (chân Y=x.6875) thì không nhảy kê được → ngồi nhích ra sát vành
    * (hộp va chạm chạm vành hopper → chân về đúng Y nguyên) rồi mới bắc cột, thay vì bỏ sang tìm đường thường.
    */
   private boolean bowlEscape() {
      if (this.mc.player == null || !this.mc.player.isOnGround()) {
         return false;
      } else if (++this.bowlTicks > 60) {
         this.bowlTicks = 0;
         return false;
      } else {
         double var1 = Math.floor(this.mc.player.getX());
         double var3 = Math.floor(this.mc.player.getZ());
         double var5 = this.mc.player.getX() - var1 - 0.5;
         double var7 = this.mc.player.getZ() - var3 - 0.5;
         Vec3d var9;
         if (Math.abs(var5) >= Math.abs(var7)) {
            var9 = new Vec3d(var1 + 0.5 + (var5 >= 0.0 ? 0.42 : -0.42), this.mc.player.getY(), this.mc.player.getZ());
         } else {
            var9 = new Vec3d(this.mc.player.getX(), this.mc.player.getY(), var3 + 0.5 + (var7 >= 0.0 ? 0.42 : -0.42));
         }

         this.creepToward(var9);
         this.status = "Nhích ra vành hopper rồi mới bắc cột";
         if (this.ticks - this.bowlSaidAt > 200) {
            this.bowlSaidAt = this.ticks;
            AutoBuildMod.LOGGER
               .info("[AutoBuild] Đang lọt lòng hopper (Y={}) → nhích ra vành cho chân về Y nguyên rồi bắc cột tại chỗ", String.format("%.2f", this.mc.player.getY()));
         }

         this.setStage(BuildController.Stage.NEXT);
         return true;
      }
   }

   private String goalLabel() {
      BlockPos var1 = this.cleanupMode ? this.mining : (this.current() != null ? this.current().pos() : (this.repairGoal != null ? this.repairGoal : this.mining));
      return var1 == null ? "-" : var1.toShortString();
   }

   private BlockPos holdPos;
   private int holdUntil;
   private int cautiousUntil = -1000;
   private final Set<BlockPos> closerFor = new HashSet<>();

   /**
    * 3.0.80: khối vừa đặt bị server trả về (không hiện). Thử lại NGAY chính khối đó, không chuyển khối khác:
    * lần 1–2 đặt lại từ chỗ đang đứng (điểm ngắm khác), từ lần 3 cấm chỗ đứng này và tìm chỗ đứng GẦN hơn,
    * đủ 5 lần mới để lại và làm khối khác.
    */
   private void retryVanished(SchematicPlan.Target var1, BlockState var2) {
      BlockPos var3 = var1.pos().toImmutable();
      int var4 = this.failures.merge(var3, 1, Integer::sum);
      this.notePlaceFail(var3, var1.state().getBlock().getName().getString() + " bị server trả về");
      if (!this.running) {
         return;
      }

      this.cautiousUntil = this.ticks + this.qualityCautionTicks();
      this.placedDone.remove(var3);
      this.priorityPos = null;
      SchematicPlan.Target var5 = this.plan == null ? null : this.plan.at(var3);
      if (var5 != null) {
         this.queue.remove(var5);
      }

      PathSearch.Cell var6 = Navigation.cell(this.mc.player.getBlockPos());
      if (var4 >= 5) {
         this.badStands.computeIfAbsent(var3, var0 -> new HashSet<>()).add(var6);
         this.closerFor.remove(var3);
         this.deferred.put(var3, this.ticks + this.qualityDeferredRetryTicks());
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] {} @ {} đặt 5 lần (đã đổi chỗ đứng) vẫn bị server trả về → để lại, làm khối khác rồi quay lại sau",
               var1.state().getBlock().getName().getString(),
               var3.toShortString()
            );
         this.say(var1.state().getBlock().getName().getString() + " @ " + var3.toShortString() + " đặt 5 lần vẫn mất → để lại, làm khối khác trước.");
         this.pending = null;
         this.aim = null;
         this.nextTarget(true);
      } else if (var4 >= 2) {
         this.badStands.computeIfAbsent(var3, var0 -> new HashSet<>()).add(var6);
         this.closerFor.add(var3);
         this.leanFailed.add(var3);
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] {} @ {} đặt lần {}/5 bị mất (từ chỗ đứng {}) → đổi góc: tới chỗ đứng gần hơn rồi đặt lại ngay",
               new Object[]{var1.state().getBlock().getName().getString(), var3.toShortString(), var4, var6}
            );
         this.pending = null;
         this.aim = null;
         this.anglesFor = null;
         this.target = this.resolve(var5 != null ? var5 : var1);
         this.beginGoals(false);
      } else {
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] {} @ {} đặt lần {}/5 bị mất → đặt lại ngay chính khối này (điểm ngắm khác)",
               new Object[]{var1.state().getBlock().getName().getString(), var3.toShortString(), var4}
            );
         this.aimAttempt++;
         this.pending = null;
         this.aim = null;
         this.anglesFor = null;
         this.target = this.resolve(var5 != null ? var5 : var1);
         this.setStage(BuildController.Stage.AIM);
      }
   }

   private int crashWindowAt = -100000;
   private int crashCount;
   private final Map<Integer, Integer> layerRounds = new HashMap<>();
   private final Map<Integer, Integer> layerRoundPlaced = new HashMap<>();
   private final Set<Integer> postponedLayers = new HashSet<>();

   /**
    * 3.0.81: không bao giờ dừng vì tầng còn khối bỏ qua. Xóa sạch mọi dấu "bỏ qua / hoãn / chỗ đứng hỏng" của các ô tầng đó
    * rồi thử lại TẤT CẢ. 3 vòng liền không đặt thêm được khối nào → tạm để các khối đó cuối cùng, xây tiếp tầng trên,
    * xây xong hết sẽ quay lại thử tiếp (vòng lặp không dừng).
    */
   private void retryLayer(int var1, int var2) {
      Integer var3 = this.layerRoundPlaced.get(var1);
      int var4 = var3 != null && var3 == this.placedCount ? this.layerRounds.merge(var1, 1, Integer::sum) : 1;
      if (var4 == 1) {
         this.layerRounds.put(var1, 1);
      }

      this.layerRoundPlaced.put(var1, this.placedCount);
      if (var4 > 3) {
         this.layerRounds.remove(var1);
         this.layerRoundPlaced.remove(var1);
         this.postponedLayers.add(var1);
         AutoBuildMod.LOGGER.info("[AutoBuild] Tầng Y={}: thử lại 3 vòng không đặt thêm được → để {} khối này cuối cùng, xây tiếp tầng trên (không dừng)", var1, var2);
         this.say("Tầng Y=" + var1 + ": " + var2 + " khối thử lại 3 vòng chưa được → để cuối, xây tiếp tầng trên, xong sẽ quay lại.");
         this.beginScan();
      } else {
         this.skipped.removeIf(var1x -> var1x.getY() == var1);
         this.skipReason.keySet().removeIf(var1x -> var1x.getY() == var1);
         this.attempts.keySet().removeIf(var1x -> var1x.getY() == var1);
         this.deferred.keySet().removeIf(var1x -> var1x.getY() == var1);
         this.deferCount.keySet().removeIf(var1x -> var1x.getY() == var1);
         this.navFailures.keySet().removeIf(var1x -> var1x.getY() == var1);
         this.stuckSkips.keySet().removeIf(var1x -> var1x.getY() == var1);
         this.failures.keySet().removeIf(var1x -> var1x.getY() == var1);
         this.badStands.keySet().removeIf(var1x -> var1x.getY() == var1);
         this.leanFailed.removeIf(var1x -> var1x.getY() == var1);
      this.liftTried.removeIf(var1x -> var1x.getY() == var1);
         this.noSpoof.keySet().removeIf(var1x -> var1x.getY() == var1);
         this.closerFor.removeIf(var1x -> var1x.getY() == var1);
         this.repairTries.keySet().removeIf(var1x -> var1x.getY() == var1);
         this.depWaits.keySet().removeIf(var1x -> var1x.getY() == var1);
         this.fluidRetries.remove(var1);
         this.permRetried.remove(var1);
         this.layerFinishTries.remove(var1);
         this.digStuckLayers.remove(var1);
         this.lastProgress = this.ticks;
         AutoBuildMod.LOGGER.info("[AutoBuild] Tầng Y={} còn {} khối chưa xong → thử lại TẤT CẢ (vòng {}/3), không dừng", new Object[]{var1, var2, var4});
         this.say("Tầng Y=" + var1 + " còn " + var2 + " khối → thử lại tất cả (vòng " + var4 + "/3).");
         this.beginScan();
      }
   }

   private int stageEnterTick() {
      return this.ticks - this.stageTicks;
   }

   private boolean partialFloor() {
      // 3.0.78: chỉ xét khi đã chạm đất; ngưỡng 0.05 để lúc vừa tiếp đất (Y=77.02) không bị nhận nhầm là "lòng hopper"
      if (!this.mc.player.isOnGround()) {
         return false;
      }

      double var1 = this.mc.player.getY() - Math.floor(this.mc.player.getY());
      return var1 > 0.05 && var1 < 0.95;
   }

   private boolean tryDirectLayerClimb(Navigation var1) {
      if (this.ticks < this.roadFailUntil) {
         return false;
      } else if (this.partialFloor() && this.bowlEscape()) {
         return true;
      } else if (this.partialFloor()) {
         this.roadFailUntil = this.ticks + 100;
         AutoBuildMod.LOGGER.info("[AutoBuild] Đang đứng trên khối thấp (Y={}, vd. trong lòng hopper) → không bắc tháp từ đây, tìm chỗ khác", String.format("%.2f", this.mc.player.getY()));
         return false;
      } else if (this.currentLayer != null && this.mc.player != null && this.mc.player.isOnGround() && this.scaffoldAllowed()) {
         this.bowlTicks = 0;
         PathSearch.Cell var2 = Navigation.cell(this.mc.player.getBlockPos());
         int var3 = this.currentLayer + 1;
         int var4 = var3 - var2.y();
         if (var4 > 0 && var4 <= 12) {
            List var5 = null;

            label171:
            for (int var6 = 0; var6 < 2 && var5 == null; var6++) {
               for (int var7 = 0; var7 <= 4; var7++) {
                  for (int var8 = -var7; var8 <= var7; var8++) {
                     for (int var9 = -var7; var9 <= var7; var9++) {
                        if (Math.max(Math.abs(var8), Math.abs(var9)) == var7) {
                           for (int var10 = 0; var10 < 2; var10++) {
                              ArrayList var11 = new ArrayList();
                              var11.add(var2);
                              int var12 = var2.x();
                              int var13 = var2.z();
                              boolean var14 = true;
                              int var15 = var10 == 0 ? var8 : 0;
                              int var16 = var10 == 0 ? 0 : var9;
                              int var17 = var10 == 0 ? 0 : var8;
                              int var18 = var10 == 0 ? var9 : 0;

                              for (int var19 = 0; var19 < 2 && var14; var19++) {
                                 int var20 = var19 == 0 ? var15 : var17;
                                 int var21 = var19 == 0 ? var16 : var18;

                                 while (var20 != 0 || var21 != 0) {
                                    if (var20 != 0) {
                                       int var22 = Integer.signum(var20);
                                       var12 += var22;
                                       var20 -= var22;
                                    } else {
                                       int var28 = Integer.signum(var21);
                                       var13 += var28;
                                       var21 -= var28;
                                    }

                                    PathSearch.Cell var29 = new PathSearch.Cell(var12, var2.y(), var13);
                                    BlockPos var23 = Navigation.pos(var29).down();
                                    if (!var1.clear(var29)
                                       || !var1.standable(var29) && (!var1.canScaffoldAt(var23) || var1.lowBridge(var29))
                                       || var1.standable(var29) && !this.mc.world.getBlockState(var23).isSideSolidFullSquare(this.mc.world, var23, Direction.UP)) {
                                       var14 = false;
                                       break;
                                    }

                                    var11.add(var29);
                                 }
                              }

                              for (int var25 = var2.y() + 1; var14 && var25 <= var3; var25++) {
                                 PathSearch.Cell var26 = new PathSearch.Cell(var12, var25, var13);
                                 BlockPos var27 = Navigation.pos(var26).down();
                                 if (!var1.clear(var26) || !var1.standable(var26) && !var1.canScaffoldAt(var27)) {
                                    var14 = false;
                                 } else {
                                    var11.add(var26);
                                 }
                              }

                              if (var14 && (var6 == 1 || this.touchesLayer(var12, var13))) {
                                 var5 = List.copyOf(var11);
                                 continue label171;
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (var5 == null) {
               return false;
            } else {
               this.terrain = var1;
               this.path = var5;
               this.pathIndex = 1;
               this.lastPosition = this.mc.player.getPos();
               this.stuckTicks = 0;
               this.walkPitch = 30.0F;
               this.climbMove = true;
               this.climbSearch = false;
               this.planeMove = false;
               this.approachOnly = false;
               this.replans = 0;
               this.target = null;
               this.supportTarget = null;
               this.status = "Bắc tháp tạm lên mặt tầng Y=" + this.currentLayer;
               PathSearch.Cell var24 = (PathSearch.Cell)var5.getLast();
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] Chưa có mặt đứng ở Y={} → dựng đường tạm {} bước tới cột {} {}",
                     new Object[]{this.currentLayer, var5.size() - 1, var24.x(), var24.z()}
                  );
               this.setStage(BuildController.Stage.MOVE);
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean shouldSneakAtEdge(PathSearch.Cell var1) {
      PathSearch.Cell var2 = Navigation.cell(this.mc.player.getBlockPos());
      return var1.y() == var2.y() - 1 && Math.abs(var1.x() - var2.x()) + Math.abs(var1.z() - var2.z()) == 1 && this.terrain.edgeStillValid(var2, var1)
         ? false
         : this.nearEdge(var1);
   }

   private boolean tryRequiredFrontSupport(SchematicPlan.Target var1, int var2) {
      LinkedHashMap<BlockPos, String> var3 = new LinkedHashMap<>();
      if (this.job != null && this.job.fixed() && var2 == 0) {
         var3.put(Navigation.pos(this.job.front()), "đầu hàng định hướng");
      }

      if (var1.state().getBlock() instanceof ObserverBlock
         && var1.state().contains(Properties.FACING)
         && (!this.hasSupport(var1.pos()) || this.navFailures.getOrDefault(var1.pos(), 0) > 0)) {
         Direction var4 = (Direction)var1.state().get(Properties.FACING);
         var3.put(var1.pos().offset(var4), "phía trước observer");
      }

      if (!this.placeable(var1)) {
         int var12 = PlacementSolver.supportMask(this.mc, this.resolve(var1));
         Direction[] var5 = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};

         for (Direction var9 : var5) {
            if ((var12 & 1 << var9.ordinal()) != 0) {
               var3.putIfAbsent(var1.pos().offset(var9), "mặt bám đúng hướng");
            }
         }
      }

      for (Entry var70 : var3.entrySet()) {
         BlockPos var71 = (BlockPos)var70.getKey();
         SchematicPlan.Target var72 = this.plan.at(var71);
         if (var72 != null
            && this.mc.world.isChunkLoaded(var71)
            && !var72.state().isAir()
            && !this.mc.world.getBlockState(var71).isReplaceable()
            && !this.done(this.mc.world.getBlockState(var71), var72.state())
            && !StateMatch.canAdd(this.mc.world.getBlockState(var71), var72.state())) {
            if (this.ticks - this.anchorWaitSaid > 100) {
               this.anchorWaitSaid = this.ticks;
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] {} @ {} không bám vào {} @ {} vì ô đó đang là khối SAI ({}) → chờ đập và đặt lại đúng trước",
                     new Object[]{
                        var1.state().getBlock().getName().getString(),
                        var1.pos().toShortString(),
                        var72.state().getBlock().getName().getString(),
                        var71.toShortString(),
                        this.mc.world.getBlockState(var71).getBlock().getName().getString()
                     }
                  );
            }

            this.deferred.put(var1.pos(), this.ticks + 60);
            this.job = null;
            this.target = null;
            this.setStage(BuildController.Stage.NEXT);
            return true;
         }
      }

      for (Entry var55 : var3.entrySet()) {
         BlockPos var56 = (BlockPos)var55.getKey();
         SchematicPlan.Target var57 = this.plan.at(var56);
         if (this.mc.world.isChunkLoaded(var56)
            && this.mc.world.getBlockState(var56).isReplaceable()
            && var57 != null
            && !var57.state().isAir()
            && !var57.state().isReplaceable()
            && (this.skipped.contains(var56) || this.deferred.getOrDefault(var56, 0) > this.ticks)
            && (!"mặt bám đúng hướng".equals(var55.getValue()) || this.hasSupport(var56))) {
            // 3.0.99: ô bám cũng đang LƠ LỬNG (không có gì để bám) thì chờ nó là chờ mãi (tầng 20: obsidian chờ obsidian chờ obsidian…)
            // → không chờ, để resolveBuild tìm trong cả cụm một khối có chỗ bám / chỗ đặt 1 khối kê.
            if (this.ticks - this.anchorWaitSaid > 100) {
               this.anchorWaitSaid = this.ticks;
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] {} @ {} chờ {} @ {} đặt xong trước (đang hoãn) → chưa cầm {} ra thử",
                     new Object[]{
                        var1.state().getBlock().getName().getString(),
                        var1.pos().toShortString(),
                        var57.state().getBlock().getName().getString(),
                        var56.toShortString(),
                        var1.state().getBlock().getName().getString()
                     }
                  );
            }

            this.deferred.put(var1.pos(), Math.max(this.deferred.getOrDefault(var56, 0), this.ticks + 60));
            this.job = null;
            this.target = null;
            this.setStage(BuildController.Stage.NEXT);
            return true;
         }
      }

      for (Entry var50 : var3.entrySet()) {
         BlockPos var51 = (BlockPos)var50.getKey();
         if (this.mc.world.isChunkLoaded(var51) && this.mc.world.getBlockState(var51).isReplaceable() && !this.skipped.contains(var51)) {
            SchematicPlan.Target var52 = this.plan.at(var51);
            if (var52 != null
               && !var52.state().isAir()
               && !var52.state().isReplaceable()
               && var51.getY() <= var1.pos().getY() + 1
               && (!"mặt bám đúng hướng".equals(var50.getValue()) || this.hasSupport(var51) && this.placeable(var52))) {
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] {} @ {} cần bám vào {} @ {} ({}) → xây {} trước",
                     new Object[]{
                        var1.state().getBlock().getName().getString(),
                        var1.pos().toShortString(),
                        var52.state().getBlock().getName().getString(),
                        var51.toShortString(),
                        var50.getValue(),
                        var52.state().getBlock().getName().getString()
                     }
                  );
               this.queue.removeIf(var1x -> var1x.pos().equals(var51));
               this.deferred.remove(var51);
               if (!this.queue.contains(var1)) {
                  this.queue.addFirst(var1);
               }

               this.target = this.resolve(var52);
               this.nextRetries = 0;
               this.aim = null;
               this.beginGoals(false);
               return true;
            }
         }
      }

      BlockPos var13 = null;
      String var14 = null;
      List var15 = List.of();

      for (Entry var17 : var3.entrySet()) {
         BlockPos var18 = (BlockPos)var17.getKey();
         if (this.mc.world.isChunkLoaded(var18) && this.mc.world.getBlockState(var18).isReplaceable()) {
            SchematicPlan.Target var10 = this.plan.at(var18);
            if (var10 == null || var10.state().isAir() || var10.state().isReplaceable()) {
               List var11 = this.rowChain(var18);
               if (!var11.isEmpty() && (var13 == null || var11.size() < var15.size())) {
                  var13 = var18.toImmutable();
                  var14 = (String)var17.getValue();
                  var15 = var11;
               }
            }
         }
      }

      if (var13 == null) {
         return false;
      } else {
         if (!this.jobTemps.isEmpty()) {
            this.sweepNow.addAll(this.jobTemps);
            this.jobTemps.clear();
         }

         this.queue.remove(var1);
         this.deferred.remove(var1.pos());
         this.attempts.remove(var1.pos());
         this.navFailures.remove(var1.pos());
         this.target = this.resolve(var1);
         this.nextRetries = 0;
         this.jobTemps.addAll(var15);
         this.jobTempFor = var1.pos().toImmutable();
         this.jobSupport = true;
         this.rowSupport = false;
         this.supportQueue.clear();
         this.supportQueue.addAll(var15.subList(1, var15.size()));
         this.supportTarget = new SchematicPlan.Target((BlockPos)var15.getFirst(), this.pillarState((BlockPos)var15.getFirst()));
         this.planSelfPillar(var15);
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] {} @ {}: dựng {} khối tạm tới {} tại {}, xây xong sẽ quay lại đập",
               new Object[]{var1.state().getBlock().getName().getString(), var1.pos().toShortString(), var15.size(), var14, var13.toShortString()}
            );
         this.status = "Dựng khối tạm " + var14 + " tại " + var13.toShortString();
         this.beginGoals(false);
         return true;
      }
   }

   private BlockHitResult pillarTopHit(BlockPos var1) {
      Vec3d var2 = this.mc.player.getEyePos();
      BlockHitResult var3 = this.mc
         .world
         .raycast(new RaycastContext(var2, var2.add(0.0, -this.reach(), 0.0), ShapeType.OUTLINE, FluidHandling.NONE, this.mc.player));
      return var3.getType() == Type.BLOCK && var3.getSide() == Direction.UP && var3.getBlockPos().up().equals(var1) ? var3 : null;
   }

   private void pillarFailed(String var1) {
      if (this.climbMove || var1.contains("quá thấp")) {
         this.roadFailUntil = this.ticks + 200;
         this.climbFailUntil = this.ticks + 200;
      }

      Input.key(this.mc.options.jumpKey, false);
      this.pillar = false;
      this.pillarFor = null;
      this.pillarJumpIssued = false;
      this.pillarWasAirborne = false;
      this.aim = null;
      this.anglesFor = null;
      this.navigationFailed(var1);
   }

   private void pillarTick(SchematicPlan.Target var1) {
      BlockPos var2 = var1.pos();
      if (!this.config.layerMode
         || this.currentLayer == null
         || this.cleanupMode
         || var2.getY() <= this.currentLayer
         || (var1 == this.temporary || var1 == this.supportTarget) && (this.plan == null || this.plan.at(var2) == null)) {
         if (!var2.equals(this.pillarFor)) {
            this.pillarFor = var2.toImmutable();
            this.pillarTicks = 0;
            this.pillarJumps = 0;
            this.pillarJumpIssued = false;
            this.pillarWasAirborne = false;
            this.pillarVertical = false;
            this.aim = null;
            this.gates.clear();
         }

         Input.sneak(this.mc, true, this.ticks);
         Input.key(this.mc.options.jumpKey, false);
         if (++this.pillarTicks <= 140 && (this.pillarJumps < 4 || !this.mc.player.isOnGround() || !this.pillarWasAirborne)) {
            if (this.mc.player.isOnGround()) {
               if (this.pillarJumpIssued) {
                  if (this.pillarWasAirborne) {
                     this.pillarJumpIssued = false;
                     this.pillarWasAirborne = false;
                     this.aim = null;
                     return;
                  }

                  if (this.ticks - this.pillarLaunchTick < 8) {
                     return;
                  }

                  this.pillarJumpIssued = false;
                  this.aim = null;
                  this.gate("không rời mặt đất sau nhịp nhảy");
               }

               if (!PillarGeometry.reachableTop(this.mc.player.getY(), var2)) {
                  this.pillarFailed("Mặt đứng Y=" + this.mc.player.getY() + " quá thấp để nhảy kê ô " + var2.toShortString());
               } else {
                  boolean var11 = PillarGeometry.sameColumn(this.mc.player.getX(), this.mc.player.getZ(), var2);
                  BlockHitResult var4 = var11 ? this.pillarTopHit(var2) : null;
                  if (var4 != null && this.mc.world.getBlockState(var2.down()).isSideSolidFullSquare(this.mc.world, var2.down(), Direction.UP)) {
                     Vec3d var5 = new Vec3d((double)var2.getX() + 0.5, this.mc.player.getY(), (double)var2.getZ() + 0.5);
                     double var6 = var5.x - this.mc.player.getX();
                     double var8 = var5.z - this.mc.player.getZ();
                     if (var6 * var6 + var8 * var8 > 0.01) {
                        this.status = "Vào giữa ô trước khi nhảy kê";
                        this.creepToward(var5);
                        return;
                     }
                  }

                  if (this.mc.player.getVelocity().horizontalLengthSquared() > 4.0E-4) {
                     this.brake();
                  } else if (this.mc.player.isInSneakingPose()) {
                     if (this.aim == null) {
                        this.pillarVertical = var4 != null && StateMatch.orientationFree(var1.state());
                        if (this.pillarVertical) {
                           this.pillarYaw = this.mc.player.getYaw();
                           this.pillarPitch = 90.0F;
                           this.aim = new PlacementSolver.Aim(var4, this.pillarYaw, this.pillarPitch, false, true);
                        } else {
                           Vec3d var12 = this.mc.player.getEyePos().add(0.0, 1.15, 0.0);
                           this.aim = PlacementSolver.solve(this.mc, var12, var1, this.reach(), true, var2.asLong() * 31L, true);
                           if (this.aim == null) {
                              // 3.0.84: cột kê ở ô này không có mặt đặt → né ô trên nó 30 giây, để các khối sau không chọn lại đúng cột hỏng
                              // (log: 5 khối nước liền nhau cùng hỏng ở 1 cột kê, Y=-51)
                              Navigation.AVOID.add(var2.up().toImmutable());
                              this.avoidSince = this.ticks;
                              this.pillarFailed("Không có mặt đặt khi nhảy tới " + var2.toShortString());
                              return;
                           }

                           Vec3d var13 = this.aim.hit().getPos().subtract(var12);
                           this.pillarYaw = MotionMath.yaw(var13.x, var13.z);
                           this.pillarPitch = MotionMath.pitch(var13.x, var13.y, var13.z);
                        }

                        this.anglesFor = this.aim;
                        this.aimYawC = this.pillarYaw;
                        this.aimPitchC = this.pillarPitch;
                     }

                     this.lookAt(this.pillarYaw, this.pillarPitch);
                     this.status = "Ngắm xuống trước khi nhảy kê @ " + var2.toShortString();
                     if (this.rotationSent() && !this.ackWait(this.aim.hit().getBlockPos()) && this.paceOk()) {
                        this.pillarLaunchTick = this.ticks;
                        this.pillarJumpIssued = true;
                        this.pillarJumps++;
                        Input.key(this.mc.options.jumpKey, true);
                        AutoBuildMod.LOGGER
                           .info(
                              "[AutoBuild] Nhảy kê #{} @ {}: chân Y={}, pitch={}, thẳng cột={}",
                              new Object[]{this.pillarJumps, var2.toShortString(), this.mc.player.getY(), this.pillarPitch, this.pillarVertical}
                           );
                     }
                  }
               }
            } else {
               this.pillarWasAirborne = true;
               if (this.aim != null) {
                  this.lookAt(this.pillarYaw, this.pillarPitch);
                  if (this.pillarVertical) {
                     BlockHitResult var10 = this.pillarTopHit(var2);
                     if (var10 == null) {
                        this.gate("rời cột hoặc mặt dưới bị che");
                        return;
                     }

                     this.aim = new PlacementSolver.Aim(var10, this.pillarYaw, 90.0F, false, true);
                  }

                  if (!PillarGeometry.clearToPlace(this.mc.player.getBoundingBox(), var2)) {
                     this.status = "Chờ chân vượt mặt ô kê";
                  } else {
                     this.status = "Đặt khối kê ngay trong nhịp nhảy";
                     if (this.clickNow(var1, this.pillarYaw, this.pillarPitch) && this.pending == var1) {
                        AutoBuildMod.LOGGER
                           .info(
                              "[AutoBuild] Đặt kê trong nhịp nhảy @ {}, chân Y={}, vận tốc Y={}",
                              new Object[]{var2.toShortString(), this.mc.player.getY(), this.mc.player.getVelocity().y}
                           );
                     }
                  }
               }
            }
         } else {
            String var3 = this.gates.toString();
            this.gates.clear();
            this.pillarFailed(
               "Không kê được @ " + var2.toShortString() + " sau " + this.pillarJumps + " lần nhảy; chân Y=" + this.mc.player.getY() + ", chặn=" + var3
            );
         }
      } else {
         this.pillarFailed("Không đặt khối công trình cao hơn tầng đang xây (Y=" + this.currentLayer + ")");
      }
   }

   private final Map<BlockPos, Integer> pistonSaid = new HashMap<>();

   private static boolean pushBlocker(BlockState var0) {
      if (var0.isOf(Blocks.OBSIDIAN) || var0.isOf(Blocks.CRYING_OBSIDIAN) || var0.isOf(Blocks.BEDROCK) || var0.isOf(Blocks.REINFORCED_DEEPSLATE) || var0.isOf(Blocks.RESPAWN_ANCHOR)) {
         return true;
      } else {
         return var0.hasBlockEntity() && !Registries.BLOCK.getId(var0.getBlock()).getPath().endsWith("shulker_box");
      }
   }

   private BlockPos pistonHoldCell(SchematicPlan.Target var1) {
      if (this.plan != null && var1 != null && this.mc.world != null && var1.state().isOf(Blocks.POWDER_SNOW)) {
         for (Direction var5 : Direction.values()) {
            SchematicPlan.Target var6 = this.plan.at(var1.pos().offset(var5.getOpposite()));
            if (var6 != null
               && var6.state().getBlock() instanceof net.minecraft.block.PistonBlock
               && var6.state().contains(Properties.FACING)
               && var6.state().get(Properties.FACING) == var5) {
               BlockPos var7 = var1.pos();

               for (int var8 = 0; var8 < 12; var8++) {
                  var7 = var7.offset(var5);
                  SchematicPlan.Target var9 = this.plan.at(var7);
                  if (var9 == null || var9.state().isAir()) {
                     break;
                  }

                  if (!this.mc.world.isChunkLoaded(var7) || !this.done(this.mc.world.getBlockState(var7), var9.state())) {
                     return var7;
                  }

                  if (pushBlocker(var9.state())) {
                     break;
                  }
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private final Set<BlockPos> pistonBlockers = new HashSet<>();
   private final java.util.ArrayDeque<SchematicPlan.Target> blockerQueue = new java.util.ArrayDeque<>();

   private boolean blockerHeld(BlockPos var1) {
      return this.pistonBlockers.contains(var1) && (this.currentLayer == null || var1.getY() > this.currentLayer || this.plan == null || this.plan.at(var1) == null);
   }

   private Direction pistonDirFor(BlockPos var1) {
      if (this.plan != null && this.mc.world != null) {
         for (Direction var5 : Direction.values()) {
            BlockPos var6 = var1;

            for (int var8 = 0; var8 < 12; var8++) {
               var6 = var6.offset(var5.getOpposite());
               SchematicPlan.Target var9 = this.plan.at(var6);
               if (var9 != null
                  && var9.state().getBlock() instanceof net.minecraft.block.PistonBlock
                  && var9.state().contains(Properties.FACING)
                  && var9.state().get(Properties.FACING) == var5) {
                  if (var5 == Direction.UP) {
                     return var5;
                  }

                  break;
               }

               BlockState var10 = this.mc.world.getBlockState(var6);
               if (var10.isReplaceable() || pushBlocker(var10)) {
                  break;
               }
            }
         }
      }

      return null;
   }

   private void planPistonBlocker(BlockPos var1, BlockState var2) {
      if (var2 == null || var2.isReplaceable() || pushBlocker(var2) || this.plan == null) {
         return;
      }

      Direction var3 = this.pistonDirFor(var1);
      if (var3 == null) {
         return;
      }

      ArrayList<BlockPos> var4 = new ArrayList<>();
      SchematicPlan.Target var5 = null;
      BlockPos var6 = var1;

      for (int var7 = 0; var7 < 3; var7++) {
         var6 = var6.offset(var3);
         if (!this.mc.world.isChunkLoaded(var6)) {
            return;
         }

         SchematicPlan.Target var8 = this.plan.at(var6);
         BlockState var9 = this.mc.world.getBlockState(var6);
         if (pushBlocker(var9)) {
            break;
         }

         if (var8 != null && !var8.state().isAir() && pushBlocker(var8.state())) {
            var5 = var8;
            break;
         }

         if (var9.isReplaceable()) {
            var4.add(var6.toImmutable());
         }
      }

      if (var5 == null && var4.isEmpty()) {
         return;
      }

      if (var5 == null) {
         AutoBuildMod.LOGGER.info("[AutoBuild] {} @ {} nằm trước piston nhưng phía trên không có khối công trình nào piston không đẩy được → không chặn tạm", var2.getBlock().getName().getString(), var1.toShortString());
         return;
      }

      BlockState var10 = new Navigation(this.mc, this.config, this.plan, true).scaffoldState();
      if (var10 == null) {
         var10 = Blocks.COBBLESTONE.getDefaultState();
      }

      for (BlockPos var12 : var4) {
         this.pistonBlockers.add(var12);
         this.blockerQueue.addLast(new SchematicPlan.Target(var12, var10));
      }

      if (this.mc.world.getBlockState(var5.pos()).isReplaceable()) {
         this.pistonBlockers.add(var5.pos().toImmutable());
         this.blockerQueue.addLast(var5);
      }

      AutoBuildMod.LOGGER
         .info(
            "[AutoBuild] {} @ {} nằm trước piston → chặn ngay: {} khối tạm {} rồi {} @ {} (khối công trình piston không đẩy được) ở trên cùng",
            new Object[]{
               var2.getBlock().getName().getString(),
               var1.toShortString(),
               var4.size(),
               var10.getBlock().getName().getString(),
               var5.state().getBlock().getName().getString(),
               var5.pos().toShortString()
            }
         );
   }

   private boolean triggersReleased;
   private final Map<BlockPos, Boolean> triggerCache = new HashMap<>();

   private boolean pistonAt(BlockPos var1) {
      SchematicPlan.Target var2 = this.plan == null ? null : this.plan.at(var1);
      return var2 != null && var2.state().getBlock() instanceof net.minecraft.block.PistonBlock;
   }

   boolean pistonTrigger(SchematicPlan.Target var1) {
      if (this.plan != null && var1 != null && var1.state().getBlock() instanceof ObserverBlock && var1.state().contains(Properties.FACING)) {
         Boolean var2 = this.triggerCache.get(var1.pos());
         if (var2 != null) {
            return var2;
         } else {
            Direction var3 = (Direction)var1.state().get(Properties.FACING);
            BlockPos var4 = var1.pos().offset(var3.getOpposite());
            boolean var5 = this.pistonAt(var4);
            if (!var5) {
               SchematicPlan.Target var6 = this.plan.at(var4);
               if (var6 != null && !var6.state().isAir() && var6.state().isFullCube(this.mc.world, var4) && !(var6.state().getBlock() instanceof ObserverBlock)) {
                  for (Direction var10 : Direction.values()) {
                     if (!var4.offset(var10).equals(var1.pos()) && this.pistonAt(var4.offset(var10))) {
                        var5 = true;
                        break;
                     }
                  }
               }
            }

            this.triggerCache.put(var1.pos().toImmutable(), var5);
            return var5;
         }
      } else {
         return false;
      }
   }

   /** 3.0.90: tầng đã xây xong phần khác → được đặt observer kích hoạt piston của tầng đó (không đợi xong cả công trình) */
   private final java.util.Set<Integer> triggerLayers = new java.util.HashSet<>();

   private boolean triggerHeld(SchematicPlan.Target var1) {
      if (this.triggersReleased || !this.pistonTrigger(var1) && !this.tntHold(var1)) {
         return false;
      } else {
         return !this.config.layerMode || !this.triggerLayers.contains(var1.pos().getY()) || !this.watchFinal(var1);
      }
   }

   /**
    * Ô observer nhìn vào (phía FACING) đã ở trạng thái cuối (đúng bản vẽ / không khí mà bản vẽ để trống / ngoài khoảng Y) →
    * đặt observer lúc này không bị kích hoạt về sau bởi việc xây. Ô đó còn phải xây (thường ở tầng trên) thì chờ.
    */
   private boolean watchFinal(SchematicPlan.Target var1) {
      if (!var1.state().contains(Properties.FACING)) {
         return true;
      } else {
         BlockPos var2 = var1.pos().offset((Direction)var1.state().get(Properties.FACING));
         if (!this.config.inYRange(var2.getY())) {
            return true;
         } else if (!this.mc.world.isChunkLoaded(var2)) {
            return false;
         } else {
            BlockState var3 = this.mc.world.getBlockState(var2);
            SchematicPlan.Target var4 = this.plan.at(var2);
            if (var4 == null) {
               return this.journal == null || !this.journal.placed.containsKey(var2);
            } else {
               return this.done(var3, var4.state());
            }
         }
      }
   }

   private String pistonNear(BlockPos var1) {
      if (this.plan == null) {
         return null;
      } else {
         for (Direction var5 : Direction.values()) {
            BlockPos var6 = var1.offset(var5.getOpposite());
            SchematicPlan.Target var7 = this.plan.at(var6);
            if (var7 != null
               && var7.state().getBlock() instanceof net.minecraft.block.PistonBlock
               && var7.state().contains(Properties.FACING)
               && var7.state().get(Properties.FACING) == var5) {
               return "piston @ " + var6.toShortString();
            }
         }

         return null;
      }
   }

   private boolean pistonHeld(SchematicPlan.Target var1) {
      BlockPos var2 = this.pistonHoldCell(var1);
      if (var2 == null) {
         return false;
      } else {
         Integer var3 = this.pistonSaid.get(var1.pos());
         if (var3 == null || this.ticks - var3 > 1200) {
            this.pistonSaid.put(var1.pos().toImmutable(), this.ticks);
            SchematicPlan.Target var4 = this.plan.at(var2);
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] {} @ {} nằm ngay trước mặt piston → chưa đặt (piston sẽ đẩy nó lên chỗ khác); đợi {} @ {} chặn phía trên xong rồi quay lại đặt",
                  new Object[]{
                     var1.state().getBlock().getName().getString(),
                     var1.pos().toShortString(),
                     var4 == null ? "?" : var4.state().getBlock().getName().getString(),
                     var2.toShortString()
                  }
               );
         }

         return true;
      }
   }

   private int rotChangedAt;
   private float rotLastYaw;
   private float rotLastPitch;

   private boolean directionalReady() {
      float var1 = this.mc.player.getYaw();
      float var2 = this.mc.player.getPitch();
      if (Math.abs(MotionMath.wrap(var1 - this.rotLastYaw)) > 0.01F || Math.abs(var2 - this.rotLastPitch) > 0.01F) {
         this.rotLastYaw = var1;
         this.rotLastPitch = var2;
         this.rotChangedAt = this.ticks;
      }

      // 3.0.96: server xử lý gói tin theo thứ tự nên hướng khối chỉ phụ thuộc góc nhìn đã gửi trước gói đặt khối;
      // không cần chờ khối trước được xác nhận hẳn (bản cũ chờ đủ 1 lượt ping ~6 tick sau MỖI khối có hướng)
      if (this.unacked > 1) {
         this.gate("khối có hướng: chờ server xác nhận khối trước");
         return false;
      } else if (this.ticks - this.rotChangedAt < 2) {
         this.gate("khối có hướng: chờ góc nhìn gửi lên server");
         return false;
      } else if (this.ticks - this.poseChangedAt < 2 + this.latencyTicks() / 2) {
         this.gate("khối có hướng: chờ tư thế ổn định");
         return false;
      } else {
         return true;
      }
   }

   private boolean tempPlanConflict(SchematicPlan.Target var1) {
      if (var1 != null && this.pistonBlockers.contains(var1.pos())) {
         return false;
      } else if (var1 != null && this.plan != null && (var1 == this.temporary || var1 == this.supportTarget)) {
         SchematicPlan.Target var2 = this.plan.at(var1.pos());
         return var2 != null && !var2.state().isAir() && !var2.state().isReplaceable() && var2.state().getBlock() != var1.state().getBlock();
      } else {
         return false;
      }
   }

   private int tempPlanSaidAt = -1000;

   private boolean tempInPlanCell(SchematicPlan.Target var1) {
      if (!this.tempPlanConflict(var1)) {
         return false;
      } else {
         SchematicPlan.Target var2 = this.plan.at(var1.pos());
         if (this.ticks - this.tempPlanSaidAt > 40) {
            this.tempPlanSaidAt = this.ticks;
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Chặn đặt khối kê {} vào ô {} (ô này của {} trong công trình) → tìm cách khác",
                  new Object[]{var1.state().getBlock().getName().getString(), var1.pos().toShortString(), var2.state().getBlock().getName().getString()}
               );
         }

         Input.key(this.mc.options.jumpKey, false);
         this.pillar = false;
         this.pillarFor = null;
         this.pending = null;
         this.temporary = null;
         this.supportTarget = null;
         this.supportQueue.clear();
         this.aim = null;
         this.anglesFor = null;
         this.navigationFailed("Ô kê tạm trùng ô công trình");
         return true;
      }
   }

   private boolean canJumpPlace(SchematicPlan.Target var1) {
      if (Materials.bucket(this.mc.world.getBlockState(var1.pos()), var1.state()) || this.tempPlanConflict(var1) || !StateMatch.orientationFree(var1.state())) {
         return false;
      } else if (var1 != this.temporary
         && var1 != this.supportTarget
         && (!var1.state().isFullCube(this.mc.world, var1.pos()) || var1.state().hasBlockEntity() || Interaction.interactable(var1.state()))) {
         return false;
      } else if (this.mc.player.isOnGround() && PillarGeometry.reachableTop(this.mc.player.getY(), var1.pos())) {
         Vec3d var2 = PlacementSolver.standingEye(this.mc, this.mc.player.getEyePos()).add(0.0, 1.15, 0.0);
         return PlacementSolver.solve(this.mc, var2, var1, this.reach(), true, var1.pos().asLong() * 31L, true) != null;
      } else {
         return false;
      }
   }

   private boolean bucketMiss;
   private int bucketCenterTicks;
   private final Map<BlockPos, Integer> bucketStandFails = new HashMap<>();
   private int bucketUnsafeTicks;
   private int bucketAimTicks;
   private boolean bucketCloser;
   private boolean bucketDrifted;

   private boolean waterIntoBlock(SchematicPlan.Target var1) {
      return var1 != null && this.mc.world != null && this.mc.world.isChunkLoaded(var1.pos()) && Materials.waterlog(this.mc.world.getBlockState(var1.pos()), var1.state());
   }

   private boolean waterPourTarget() {
      SchematicPlan.Target var1 = this.current();
      if (var1 != null && this.mc.world != null && this.mc.world.isChunkLoaded(var1.pos())) {
         BlockState var2 = this.mc.world.getBlockState(var1.pos());
         return Materials.bucket(var2, var1.state()) && !var1.state().isOf(Blocks.LAVA);
      } else {
         return false;
      }
   }

    private boolean bucketReady(Target intended) {
        bucketMiss = false;
        boolean intoBlock = waterIntoBlock(intended);
        int settle = intoBlock ? 2 : 6 + latencyTicks() * 2;
        String reason = null;
        if (intoBlock && (mc.player.isSneaking() || mc.player.isInSneakingPose() || aim != null && aim.sneak())) {
            Input.forceSneak(mc, false, ticks);
            if (aim != null && aim.sneak()) { aim = null; anglesFor = null; }
            reason = "xô nước vào khối: nhả Shift và đợi tư thế đứng";
        } else if (!mc.player.isOnGround() && !inWaterNow()) reason = "xô: chưa đứng trên mặt đất";
        else if (ticks - poseChangedAt < settle) reason = "xô: chờ tư thế đứng/ngồi ổn định với server";
        else if (ticks - screenAt < settle) reason = "xô: chờ sau khi đóng menu";
        else if (mc.player.getVelocity().horizontalLengthSquared() > (inWaterNow() ? 9.0E-4 : 1.0E-4)) {
            brake(); reason = "xô: chờ đứng yên hẳn";
        } else if (intoBlock && ackWait(intended.pos())) {
            reason = "xô: chờ server xác nhận khối lá trước khi đổ";
        } else if (!bucketLands(intended)) {
            bucketMiss = true; reason = "xô: tia chưa chắc rơi đúng ô " + intended.pos().toShortString();
        }
        if (reason == null) return true;
        status = reason; gate(reason);
        if (ticks - bucketGateSaidAt > 40) {
            bucketGateSaidAt = ticks;
            AutoBuildMod.LOGGER.info("[AutoBuild] Chưa đổ {} @ {}: {}", new Object[]{intended.state().getBlock().getName().getString(), intended.pos().toShortString(), reason});
        }
        return false;
    }



   private boolean qualityBucketLandsOriginal(SchematicPlan.Target var1) {
      BlockState var2 = this.mc.world.getBlockState(var1.pos());
      boolean var3 = Materials.waterlog(var2, var1.state());
      boolean var4 = !var1.state().isOf(Blocks.LAVA);
      Vec3d var5 = this.mc.player.getEyePos();
      Vec3d var6 = this.mc.player.getRotationVec(1.0F).multiply(this.mc.player.getBlockInteractionRange());
      double[][] var16 = new double[][]{{0.0, 0.0, 0.0}};

      for (double[] var10 : var16) {
         Vec3d var11 = var5.add(var10[0], var10[1], var10[2]);
         BlockHitResult var12 = this.mc.world.raycast(new RaycastContext(var11, var11.add(var6), ShapeType.OUTLINE, FluidHandling.NONE, this.mc.player));
         if (var12.getType() != Type.BLOCK) {
            return false;
         }

         BlockPos var13 = var12.getBlockPos();
         BlockState var14 = this.mc.world.getBlockState(var13);
         if (Navigation.GHOSTS.contains(var13) || this.placedRecently(var13)) {
            return false;
         }

         BlockPos var15;
         if (var1.state().isOf(Blocks.POWDER_SNOW)) {
            if (var13.getY() > var1.pos().getY() || var14.isReplaceable()) {
               return false;
            }

            var15 = var14.isReplaceable() ? var13 : var13.offset(var12.getSide());
         } else if (var4 && var14.contains(Properties.WATERLOGGED) && !(Boolean)var14.get(Properties.WATERLOGGED)) {
            var15 = var13;
         } else {
            var15 = var13.offset(var12.getSide());
         }

         if (var3 ? !var13.equals(var1.pos()) : !var15.equals(var1.pos())) {
            return false;
         }
      }

      return true;
   }

   private boolean clickNow(SchematicPlan.Target var1, float var2, float var3) {
      if (!this.qualityClickReady(var1)) {
         return true;
      }

      if (this.waterOnWashable(var1)) {
         return false;
      } else if (this.deferEarlyFluid(var1)) {
         return false;
      } else if (this.tntUnsafe(var1)) {
         return false;
      } else if (this.mc.currentScreen != null) {
         return false;
      } else if (this.plan != null && !this.plan.unchanged()) {
         this.placementChangedAt = this.ticks;
         this.pause("Placement vừa thay đổi.");
         return false;
      } else if (this.mc.player.getHealth() <= (float)this.config.minHealth) {
         return false;
      } else if (!InventoryAccess.synced(this.mc)) {
         this.gate("chờ server xác nhận đổi đồ trên tay");
         return true;
      } else if (this.tempInPlanCell(var1)) {
         return false;
      } else if (!StateMatch.orientationFree(var1.state()) && !StateMatch.configurable(this.mc.world.getBlockState(var1.pos()), var1.state()) && !this.directionalReady()) {
         this.lookAt(var2, var3);
         return true;
      } else {
         this.lookAt(var2, var3);
         if (!PlacementSolver.validNow(this.mc, this.aim, var1, this.reach())) {
            this.gate("tia ngắm lệch mặt");
            return false;
         } else {
            float[] var4 = new float[]{this.mc.player.getYaw(), this.yawHistory[0], this.yawHistory[1], this.yawHistory[2]};
            if (!PlacementSolver.serverSafe(this.mc, this.aim, var1, this.reach(), var4)) {
               this.gate("hướng server");
               this.aimInvalidTicks = 0;
               return true;
            } else if (!this.rotationSent()) {
               this.gate("góc chưa gửi");
               this.aimInvalidTicks = 0;
               return true;
            } else if (!this.paceOk()) {
               this.gate("chờ server xác nhận (" + this.unacked + "/" + this.maxUnacked() + ")");
               this.aimInvalidTicks = 0;
               return true;
            } else if (Input.usePending()) {
               // 3.0.122: cú chuột phải trước còn chờ game xử lý (đầu tick sau)
               this.gate("chờ cú chuột phải trước");
               this.aimInvalidTicks = 0;
               return true;
            } else if (Materials.bucket(this.mc.world.getBlockState(var1.pos()), var1.state()) && !this.bucketReady(var1)) {
               return !this.bucketMiss;
            } else {
               BlockHitResult var5 = PlacementSolver.ray(this.mc, this.reach());
               boolean var6 = this.pillar || this.mc.player.getVelocity().horizontalLengthSquared() > 0.0016;
               if (!this.pillar && !BuildController.qualityCrosshairOn(this.mc, var5.getBlockPos(), var5.getSide(), this)) {
                  this.gate("tâm ngắm game khác (" + this.describeCrosshair() + ")");
                  if (++this.crosshairMiss < 4) {
                     return true;
                  }

                  var6 = true;
               }

               if (var6 && Materials.bucket(this.mc.world.getBlockState(var1.pos()), var1.state())) {
                  this.gate("xô nước cần đứng yên và tâm ngắm đúng ô");
                  this.brake();
                  return true;
               } else {
                  BlockPos var7 = var5.getBlockPos();
                  BlockPos var8 = !var7.equals(var1.pos()) && !this.mc.world.getBlockState(var7).isReplaceable() ? var7.offset(var5.getSide()) : var7;
                  boolean var9 = StateMatch.configurable(this.mc.world.getBlockState(var1.pos()), var1.state());
                  boolean var10 = var1 == this.temporary || var1 == this.supportTarget;
                  boolean var11 = this.plan != null && this.plan.at(var1.pos()) != null;
                  if (var9
                     || !this.config.layerMode
                     || this.currentLayer == null
                     || this.cleanupMode
                     || var1.pos().getY() <= this.currentLayer
                     || var1.pos().equals(this.restoringPos)
                     || this.pistonBlockers.contains(var1.pos())
                     || var10 && !var11) {
                     if (var9
                        || var8.equals(var1.pos())
                           && (this.pistonBlockers.contains(var8) || !this.wrongBlockAt(var8, var1.state()))
                           && this.mc.player.getMainHandStack().isOf(Materials.item(this.mc.world.getBlockState(var1.pos()), var1.state()))) {
                        this.crosshairMiss = 0;
                        this.sneakHeld = this.aim.sneak();
                        this.clickTick = this.ticks;
                        this.pending = var1;
                        this.temporaryPending = this.temporary != null || var1 == this.supportTarget;
                        this.settledTicks = 0;
                        this.setStage(BuildController.Stage.WAIT_BLOCK);
                        this.watchExtras(var1);
                        if (Materials.bucket(this.mc.world.getBlockState(var1.pos()), var1.state())) {
                           this.dropBuckets = true;
                           this.pourAt = this.ticks;
                           this.pourPos = var1.pos().toImmutable();
                        }

                        if (!var9
                           && (
                              this.ackWait(var5.getBlockPos())
                                 || Navigation.GHOSTS.contains(var5.getBlockPos())
                                 || this.ticks < this.cautiousUntil && this.unverified(var5.getBlockPos())
                           )) {
                           // 3.0.106: đang có khối bị server trả về (chế độ cẩn thận) → không bám vào khối vừa đặt chưa kiểm tra xong.
                           // Khối đó mà là ghost (server không có) thì server đặt khối mới VÀO CHÍNH ô đó → sai loại ở ô bên cạnh.
                           this.gate("mặt click là khối server chưa xác nhận");
                           this.pending = null;
                           this.setStage(BuildController.Stage.AIM);
                           return true;
                        }

                        if (!this.placeNow(var5)) {
                           // 3.0.104: lưới an toàn TNT chặn cú bấm → không tính là đã đặt, hoãn khối này
                           this.pending = null;
                           this.setStage(BuildController.Stage.AIM);
                           this.tntRefuse(var1, this.tntVetoWhy, var9);
                           return false;
                        }

                        if (var1.state().isOf(Blocks.TNT)) {
                           TntGuard.TNT.add(var1.pos().toImmutable());
                        }

                        if (!var9
                           && !this.temporaryPending
                           && !Materials.bucket(this.mc.world.getBlockState(var1.pos()), var1.state())
                           && (this.failures.getOrDefault(var1.pos(), 0) >= 1 || this.ticks < this.cautiousUntil)) {
                           // 3.0.80: vùng hay mất khối → đứng chờ server xác nhận khối NÀY rồi mới làm khối khác,
                           // mất thì thử lại ngay chính nó (lần 1–2 tại chỗ, lần 3–5 đổi chỗ đứng gần hơn)
                           this.holdPos = var1.pos().toImmutable();
                           this.holdUntil = this.ticks + this.qualityHoldTicks() + this.latencyTicks() * 2;
                        } else {
                           this.holdPos = null;
                        }

                        this.gates.clear();
                        this.clicked(var1.pos());
                        this.cooldown = this.config.placeDelayTicks
                           + (this.config.randomDelayTicks > 0 ? this.random.nextInt(this.config.randomDelayTicks + 1) : 0);
                        return true;
                     } else {
                        this.gate("ô nhận khối không khớp (" + var8.toShortString() + ")");
                        if (++this.wrongRefuse > 20) {
                           this.wrongRefuse = 0;
                           this.aim = null;
                           this.anglesFor = null;
                           this.navigationFailed("Mặt click hiện tại luôn cho khối vào sai ô → tìm chỗ đứng khác");
                           return false;
                        }

                        if (this.wrongRefuse == 1 || this.wrongRefuse == 20)
                        AutoBuildMod.LOGGER
                           .info(
                              "[AutoBuild] Chặn đặt sai: định đặt {} ở {} nhưng khối sẽ vào {} (đang cầm {})",
                              new Object[]{
                                 var1.state().getBlock().getName().getString(),
                                 var1.pos().toShortString(),
                                 var8.toShortString(),
                                 this.mc.player.getMainHandStack().getItem().getName().getString()
                              }
                           );
                        this.aim = null;
                        this.anglesFor = null;
                        this.aimInvalidTicks = 0;
                        return false;
                     }
                  } else {
                     this.gate("cao hơn tầng đang xây");
                     if (var1 == this.target && this.temporary == null && this.supportTarget == null) {
                        AutoBuildMod.LOGGER
                           .info(
                              "[AutoBuild] {} @ {} thuộc tầng trên, nhưng mod đã quay về tầng Y={} → bỏ mục tiêu này, xây tầng Y={} trước",
                              new Object[]{var1.state().getBlock().getName().getString(), var1.pos().toShortString(), this.currentLayer, this.currentLayer}
                           );
                        this.queue.removeIf(var1x -> var1x.pos().getY() > this.currentLayer);
                        this.target = null;
                        this.aim = null;
                        this.anglesFor = null;
                        this.release();
                        this.setStage(BuildController.Stage.NEXT);
                        return true;
                     }

                     AutoBuildMod.LOGGER
                        .info(
                           "[AutoBuild] Chặn đặt {} ở {}: cao hơn tầng đang xây Y={} (chỉ xây đúng tầng)",
                           new Object[]{var1.state().getBlock().getName().getString(), var1.pos().toShortString(), this.currentLayer}
                        );
                     if (var1 == this.temporary) {
                        this.temporary = null;
                        this.pillar = false;
                     }

                     if (var1 == this.supportTarget) {
                        this.supportTarget = null;
                     }

                     this.aim = null;
                     this.anglesFor = null;
                     this.aimInvalidTicks = 0;
                     return false;
                  }
               }
            }
         }
      }
   }

   private boolean collectShopBucket(Item var1, BuildController.Stage var2) {
      if (this.config.buyBuckets && BucketRules.shopBucket(var1)) {
         int var3 = this.missing.getOrDefault(var1, 0) - this.ticks;
         if (var3 > 0) {
            this.release();
            this.status = "Shop chưa phản hồi; thử mua " + var1.getName().getString() + " lại sau " + (var3 / 20 + 1) + " giây";
            this.setStage(BuildController.Stage.WAIT_MATERIAL);
            return true;
         }

         this.buyBucket(var1, var2);
         return true;
      } else {
         return false;
      }
   }

   /** 3.0.98: chân (hoặc thân) đang ở trong nước */
   private boolean inWaterNow() {
      if (this.mc.player != null && this.mc.world != null) {
         BlockPos var1 = BlockPos.ofFloored(this.mc.player.getX(), this.mc.player.getY() + 0.05, this.mc.player.getZ());
         return this.mc.player.isSubmergedInWater() || Navigation.waterish(this.mc.world.getBlockState(var1));
      } else {
         return false;
      }
   }

   private PathSearch.Cell bucketEdgeStand;

   private boolean beginLeanForPour(SchematicPlan.Target var1) {
      if (this.mc.player == null || this.mc.world == null || !this.mc.player.isOnGround() || this.waterIntoBlock(var1)) {
         return false;
      } else if (this.inWaterNow()) {
         // 3.0.98: đứng trong nước thì ngồi ra mép chỉ bị dòng nước đẩy trôi, không bao giờ tới mép (log 00:19–00:21) → tìm chỗ đứng khô
         this.badStands.computeIfAbsent(var1.pos().toImmutable(), var0 -> new HashSet<>()).add(Navigation.cell(this.mc.player.getBlockPos()));
         return false;
      } else if (var1.pos().equals(this.bucketEdgeFor)) {
         return false;
      } else if (this.mc.player.getBoundingBox().intersects(new Box(var1.pos()))) {
         return false;
      } else if (this.badStands.getOrDefault(var1.pos(), Set.of()).contains(Navigation.cell(this.mc.player.getBlockPos()))) {
         // 3.0.84: chỗ đứng này đã chìa mép thất bại → không chìa lại ở đây nữa
         return false;
      } else {
         Vec3d var2 = edgeEye(Navigation.feet(Navigation.cell(this.mc.player.getBlockPos())), var1.pos());
         if (PlacementSolver.solve(this.mc, var2, var1, this.reach(), true, var1.pos().asLong() * 31L, true) == null) {
            return false;
         } else {
            this.bucketEdgeFor = var1.pos().toImmutable();
            this.bucketEdgeTicks = 0;
            this.bucketEdgeStand = Navigation.cell(this.mc.player.getBlockPos());
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Đứng giữa ô không với tới {} @ {} → ngồi chìa ra mép rồi mới mua xô",
                  var1.state().getBlock().getName().getString(),
                  var1.pos().toShortString()
               );
            return true;
         }
      }
   }

   private boolean leanForPour(SchematicPlan.Target var1) {
      if (this.mc.player == null || var1 == null || !var1.pos().equals(this.bucketEdgeFor)) {
         return false;
      } else if (++this.bucketEdgeTicks > 120 || this.inWaterNow() && this.bucketEdgeTicks > 10) {
         this.bucketEdgeFor = null;
         this.bucketEdgeTicks = 0;
         // 3.0.84: cấm hẳn chỗ đứng này cho ô đổ này rồi mới tìm chỗ khác. Lỗi cũ: chỉ xóa cờ → lần sau lại chìa mép
         // đúng chỗ cũ (log: 1 chỗ đứng ở Y=-49 lặp 3 lần, 18–24 giây/khối).
         // 3.0.98: cấm cả ô BẮT ĐẦU ngồi ra mép (bị nước đẩy trôi sang ô khác thì bản cũ cấm nhầm ô đang trôi tới → lại chìa mép
         // đúng chỗ cũ, lặp 1 phút: log 00:19:22–00:20:04)
         this.badStands.computeIfAbsent(var1.pos().toImmutable(), var0 -> new HashSet<>()).add(Navigation.cell(this.mc.player.getBlockPos()));
         if (this.bucketEdgeStand != null) {
            this.badStands.get(var1.pos().toImmutable()).add(this.bucketEdgeStand);
         }

         this.bucketWalkFor = null;
         AutoBuildMod.LOGGER.info("[AutoBuild] Chìa ra mép 6 giây vẫn chưa đổ được {} → cấm chỗ đứng {}, tìm chỗ đứng khác", var1.pos().toShortString(), this.mc.player.getBlockPos().toShortString());
         return false;
      } else {
         this.setStage(BuildController.Stage.EQUIP);
         Input.sneak(this.mc, true, this.ticks);
         this.status = "Ngồi chìa ra mép để đổ @ " + var1.pos().toShortString();
         if (!this.mc.player.isInSneakingPose()) {
            this.brake();
            return true;
         } else {
            Vec3d var2 = this.edgeSpot(var1.pos());
            if (var2 == null) {
               this.creepToward(Vec3d.ofCenter(var1.pos()));
               return true;
            } else {
               double var3 = var2.x - this.mc.player.getX();
               double var5 = var2.z - this.mc.player.getZ();
               if (var3 * var3 + var5 * var5 > 0.0025000000000000005) {
                  this.creepToward(var2);
                  return true;
               } else if (this.mc.player.getVelocity().horizontalLengthSquared() > this.slideLimit()) {
                  this.brake();
                  return true;
               } else {
                  return false;
               }
            }
         }
      }
   }

   /** đứng vững, không mở màn hình nào → mua xô ngay tại chỗ */
   private boolean readyToBuyHere() {
      if (this.mc.currentScreen != null) {
         return false;
      } else if (!this.mc.player.isOnGround() && !this.inWaterNow()) {
         this.status = "Chờ đứng vững trước khi mua xô";
         this.setStage(BuildController.Stage.EQUIP);
         return false;
      } else if (this.mc.player.getVelocity().horizontalLengthSquared() > (this.inWaterNow() ? 1.2E-3 : 4.0E-4)) {
         this.brake();
         this.status = "Dừng lại để mua xô";
         this.setStage(BuildController.Stage.EQUIP);
         return false;
      } else {
         this.bucketStandPos = null;
         this.bucketBuyPos = null;
         this.bucketAimTicks = 0;
         this.bucketWalkFor = null;
         return true;
      }
   }

   private boolean prepareBucketPurchase(SchematicPlan.Target var1) {
      if (this.mc.currentScreen != null) {
         return false;
      } else if (!this.mc.player.isOnGround() && !this.inWaterNow()) {
         this.status = "Chờ đứng vững trước khi mua xô";
         this.setStage(BuildController.Stage.EQUIP);
         return false;
      } else if (this.leanForPour(var1)) {
         return false;
      } else if (this.mc.player.getVelocity().horizontalLengthSquared() > (this.inWaterNow() ? 1.2E-3 : 4.0E-4)) {
         this.brake();
         this.status = "Dừng tại vị trí đổ trước khi mua xô";
         this.setStage(BuildController.Stage.EQUIP);
         return false;
      } else {
         PlacementSolver.Aim var2 = PlacementSolver.solve(
            this.mc, this.mc.player.getEyePos(), var1, this.reach(), true, var1.pos().asLong() + (long)(this.bucketUnsafeTicks / 5), true
         );
         if (!this.mc.player.getBoundingBox().intersects(new Box(var1.pos())) && var2 != null && ++this.bucketAimTicks > 40) {
            BlockHitResult var21 = PlacementSolver.ray(this.mc, this.reach());
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Canh {} @ {} 2 giây chưa được: ngồi={}/cần {}, góc đã gửi={}, tâm ngắm={} {} → đi lại gần hơn",
                  new Object[]{
                     var1.state().getBlock().getName().getString(),
                     var1.pos().toShortString(),
                     this.mc.player.isInSneakingPose(),
                     var2.sneak(),
                     this.rotationSent(),
                     var21.getType() == Type.BLOCK ? var21.getBlockPos().toShortString() : "không trúng khối",
                     var21.getSide()
                  }
               );
            this.bucketAimTicks = 0;
            this.aim = null;
            this.bucketWalkFor = null;
            this.bucketCloser = true;
            this.beginGoals(false);
            return false;
         } else if (!this.mc.player.getBoundingBox().intersects(new Box(var1.pos())) && var2 != null) {
            this.aim = var2;
            if (!var2.sneak() && this.waterIntoBlock(var1)) {
               Input.forceSneak(this.mc, false, this.ticks);
            } else {
               Input.sneak(this.mc, var2.sneak(), this.ticks);
            }

            this.lookAt(var2.yaw(), var2.pitch());
            this.setStage(BuildController.Stage.EQUIP);
            this.status = "Canh mặt đặt @ " + var1.pos().toShortString() + " trước khi mua xô";
            if (this.mc.player.isInSneakingPose() == var2.sneak() && this.rotationSent()) {
               BlockState var3 = this.mc.world.getBlockState(var1.pos());
               if (!BucketRules.hitMatches(var3, var1.state(), var1.pos(), PlacementSolver.ray(this.mc, this.reach()))) {
                  return false;
               } else if (!this.bucketLands(var1)) {
                  if (++this.bucketUnsafeTicks > 20 && this.waterPourTarget()) {
                     this.bucketUnsafeTicks = 0;
                     this.aim = null;
                     this.bucketWalkFor = null;
                     AutoBuildMod.LOGGER
                        .info("[AutoBuild] Góc đổ {} @ {} chưa chắc chắn → đổi chỗ đứng (ưu tiên đứng ngay trên ô, nhìn thẳng xuống)", var1.state().getBlock().getName().getString(), var1.pos().toShortString());
                     this.beginGoals(false);
                  }

                  return false;
               } else {
                  this.bucketUnsafeTicks = 0;
                  this.bucketAimTicks = 0;
                  this.bucketCloser = false;
                  this.bucketStandPos = this.mc.player.getPos();
                  this.bucketBuyPos = var1.pos().toImmutable();
                  this.bucketEdgeFor = null;
                  this.bucketEdgeTicks = 0;
                  this.bucketRayWarned = false;
                  return true;
               }
            } else {
               return false;
            }
         } else {
            this.aim = null;
            this.bucketAimTicks = 0;
            if (this.beginLeanForPour(var1)) {
               this.leanForPour(var1);
               return false;
            }

            boolean var20 = var1.pos().equals(this.bucketWalkFor)
               && (this.stage == BuildController.Stage.GOALS || this.stage == BuildController.Stage.SEARCH || this.stage == BuildController.Stage.MOVE);
            if (!var20) {
               PathSearch.Cell var21 = Navigation.cell(this.mc.player.getBlockPos());
               if (var1.pos().equals(this.bucketWalkFor)) {
                  // 3.0.84: vừa đi tới chỗ đứng đã chọn mà vẫn không ngắm được → trước hết vào giữa ô, không được thì cấm chỗ này.
                  // Lỗi cũ: gọi tìm chỗ đứng lại ngay → chọn đúng ô đang đứng → lặp 15+ lần/giây "Đi tới chỗ đặt Water … trước".
                  Vec3d var22 = Navigation.feet(var21);
                  double var23 = Math.hypot(var22.x - this.mc.player.getX(), var22.z - this.mc.player.getZ());
                  if (var23 > 0.15 && this.bucketCenterTicks++ < 30) {
                     this.status = "Vào giữa ô rồi canh đổ @ " + var1.pos().toShortString();
                     this.setStage(BuildController.Stage.EQUIP);
                     this.creepToward(var22);
                     return false;
                  }

                  this.bucketCenterTicks = 0;
                  this.badStands.computeIfAbsent(var1.pos().toImmutable(), var0 -> new HashSet<>()).add(var21);
                  int var24 = this.bucketStandFails.merge(var1.pos().toImmutable(), 1, Integer::sum);
                  AutoBuildMod.LOGGER
                     .info("[AutoBuild] Tới {} mà không ngắm được {} @ {} → bỏ chỗ đứng này (lần {})", new Object[]{var21, var1.state().getBlock().getName().getString(), var1.pos().toShortString(), var24});
                  if (var24 > 4) {
                     this.bucketStandFails.remove(var1.pos());
                     this.bucketWalkFor = null;
                     this.defer(var1, 400, "không có chỗ đứng đổ được");
                     this.nextTarget(true);
                     return false;
                  }
               } else {
                  this.bucketCenterTicks = 0;
               }

               this.bucketWalkFor = var1.pos().toImmutable();
               AutoBuildMod.LOGGER
                  .info("[AutoBuild] Đi tới chỗ đặt {} @ {} trước; chưa mua xô", var1.state().getBlock().getName().getString(), var1.pos().toShortString());
               this.beginGoals(false);
            }

            this.status = "Đi tới vị trí đổ @ " + var1.pos().toShortString() + "; tới nơi mới mua xô";
            return false;
         }
      }
   }

   /**
    * 3.0.83: số xô nên mua trong 1 lần mở /shop = số ô còn phải đổ đúng loại nước/lava đó, giới hạn theo ô trống trong túi
    * (chừa ô dự phòng). Mỗi click trong shop = 1 lần mua; xô đầy không xếp chồng nên mỗi xô 1 ô.
    */
   private int bucketBuyCount(Item var1) {
      if (this.plan != null && this.mc.world != null) {
         int var2 = 0;
         // 3.0.94: chỉ đếm ô CÙNG TẦNG + CÙNG BƯỚC đổ với ô đang đổ. Lỗi cũ: đếm ô lava của mọi tầng chưa xây trong cả dải Y
         // (luôn ≥ số ô trống trong túi) → lần nào cũng mua đầy túi (5–7 xô) dù tầng này chỉ cần vài xô → dư xô mang lên tầng sau.
         SchematicPlan.Target var8 = this.current();
         int var9 = var8 != null ? var8.pos().getY() : (this.currentLayer != null ? this.currentLayer : Integer.MIN_VALUE);
         int var10 = var8 != null ? FluidOrder.phase(var8.state()) : Integer.MIN_VALUE;

         for (SchematicPlan.Target var4 : this.plan.targets) {
            BlockPos var5 = var4.pos();
            if (var9 != Integer.MIN_VALUE && var5.getY() != var9) {
               continue;
            }

            if (var10 != Integer.MIN_VALUE && FluidOrder.phase(var4.state()) != var10) {
               continue;
            }

            if (this.config.inYRange(var5.getY()) && !this.skipped.contains(var5) && !this.autoWater.contains(var5) && this.mc.world.isChunkLoaded(var5)) {
               BlockState var6 = this.mc.world.getBlockState(var5);
               if (!this.done(var6, var4.state()) && BucketRules.placeableBucket(var6, var4.state()) && Materials.item(var6, var4.state()) == var1) {
                  if (++var2 >= 64) {
                     break;
                  }
               }
            }
         }

         int var7 = InventoryAccess.freeSlots(this.mc) - this.config.reserveFreeSlots;
         return Math.max(1, Math.min(var2, Math.min(var7, 32)));
      } else {
         return 1;
      }
   }

   private void buyBucket(Item var1, BuildController.Stage var2) {
      if (!this.deferEarlyFluid(this.current())) {
         this.release();
         SchematicPlan.Target var3 = this.current();
         if (var3 != null && this.mc.world.isChunkLoaded(var3.pos())) {
            BlockState var4 = this.mc.world.getBlockState(var3.pos());
            if (this.done(var4, var3.state())) {
               this.finishTarget();
            } else if (!BucketRules.placeableBucket(var4, var3.state()) || Materials.item(var4, var3.state()) != var1) {
               this.beginScan();
            } else if (InventoryAccess.count(this.mc, var1) > 0) {
               this.setStage(BuildController.Stage.EQUIP);
            } else if (this.readyToBuyHere()) {
               // 3.0.125 (người dùng: "cứ chờ đổi water bucket nhưng trong người không có → chờ mãi"): trong túi chưa có xô thì
               // đứng yên mua ngay tại chỗ, có xô rồi mới đi tới ngắm đổ. Bản cũ đòi ngắm trúng mặt đổ trước khi mua → tâm ngắm trúng
               // khối bên cạnh, "đi lại gần hơn" về đúng chỗ cũ, lặp mãi (log 20:48:02–20:48:35, Smooth Stone Slab Y=-53).
               this.missing.remove(var1);
               this.deferred.remove(var3.pos());
               if (InventoryAccess.freeSlots(this.mc) < 1) {
                  // 3.0.95: không ném xô rỗng nữa → cất đồ vào ender chest / bán bớt để có chỗ
                  if (!this.freeSpace("Túi đầy, cần 1 ô để mua " + var1.getName().getString())) {
                     this.pause("Túi đồ đầy; dọn một ô để mua " + var1.getName().getString() + " rồi nhấn K. Khối cần đổ vẫn chưa hoàn tất.");
                  }
               } else {
                  this.orderItem = var1;
                  this.afterOrder = BuildController.Stage.EQUIP;
                  this.targetTicks = 0;
                  this.shopping = true;
                  int var5 = this.bucketBuyCount(var1);
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Cần đổ ô {} mà trong túi chưa có xô → mua {} {} ngay tại chỗ (1 lần mở shop), có xô rồi mới đi tới đổ",
                        new Object[]{var3.pos().toShortString(), var5, var1.getName().getString()}
                     );
                  this.bucketDrifted = false;
                  this.shop.start(this.mc, var1, var5);
                  this.setStage(BuildController.Stage.ORDER);
               }
            }
         } else {
            this.beginScan();
         }
      }
   }

   // ===== 3.0.98: nước vô hạn — 2 ô nguồn nước kề nhau tự tạo nguồn ở ô giữa (ô đó phải có khối rắn / nguồn nước bên dưới) =====
   private final Set<BlockPos> autoWater = new HashSet<>();
   private final Map<BlockPos, Integer> autoWaterSeen = new HashMap<>();
   private boolean infiniteWaterOff;
   private int autoWaterSaidAt = -100000;

   private boolean waterSource(BlockState var1) {
      return var1.isOf(Blocks.WATER) && Materials.source(var1);
   }

   /**
    * Trong danh sách ô nước cần đổ của tầng, chọn ít ô nhất phải tự đổ sao cho các ô còn lại tự thành nguồn
    * (vd. 3 ô thẳng hàng → chỉ đổ 2 ô ngoài cùng, ô giữa tự có). Các ô "tự thành" không đưa vào hàng đợi, không mua xô cho chúng.
    * Ô nào đã đủ 2 nguồn kề + có đỡ bên dưới mà 3 giây vẫn không thành nguồn (server tắt nước vô hạn) → tắt cách này, đổ từng ô như cũ.
    */
   private List<SchematicPlan.Target> infiniteWater(List<SchematicPlan.Target> var1) {
      this.autoWater.clear();
      if (var1 == null || var1.size() < 3 || this.infiniteWaterOff || this.mc.world == null) {
         return var1;
      } else {
         HashMap<Long, Integer> var2 = new HashMap<>();
         ArrayList<SchematicPlan.Target> var3 = new ArrayList<>();

         for (SchematicPlan.Target var5 : var1) {
            BlockPos var6 = var5.pos();
            if (var5.state().isOf(Blocks.WATER) && Materials.source(var5.state()) && this.mc.world.isChunkLoaded(var6) && !var2.containsKey(var6.asLong())) {
               BlockState var7 = this.mc.world.getBlockState(var6);
               if (!this.waterSource(var7) && (var7.isReplaceable() || var7.isOf(Blocks.WATER))) {
                  BlockState var8 = this.mc.world.getBlockState(var6.down());
                  if (this.waterSource(var8) || !var8.getCollisionShape(this.mc.world, var6.down()).isEmpty()) {
                     var2.put(var6.asLong(), var3.size());
                     var3.add(var5);
                  }
               }
            }
         }

         int var9 = var3.size();
         if (var9 < 3) {
            return var1;
         } else {
            int[][] var10 = new int[var9][];
            int[] var11 = new int[var9];
            boolean[] var12 = new boolean[var9];

            for (int var13 = 0; var13 < var9; var13++) {
               BlockPos var14 = var3.get(var13).pos();
               int[] var15 = new int[4];
               int var16 = 0;

               for (Direction var18 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                  BlockPos var19 = var14.offset(var18);
                  Integer var20 = var2.get(var19.asLong());
                  if (var20 != null) {
                     var15[var16++] = var20;
                  } else if (this.waterSource(this.mc.world.getBlockState(var19))) {
                     var11[var13]++;
                  }
               }

               var10[var13] = java.util.Arrays.copyOf(var15, var16);
            }

            // ô đã đủ 2 nguồn kề sẵn (tính cả nước ngoài vùng) mà mãi không thành nguồn → server không cho nước vô hạn
            for (int var21 = 0; var21 < var9; var21++) {
               BlockPos var22 = var3.get(var21).pos();
               if (var11[var21] >= 2) {
                  int var23 = this.autoWaterSeen.computeIfAbsent(var22.toImmutable(), var1x -> this.ticks);
                  if (this.ticks - var23 > 100 + this.latencyTicks() * 2) {
                     this.infiniteWaterOff = true;
                     this.autoWaterSeen.clear();
                     AutoBuildMod.LOGGER.info("[AutoBuild] Nước vô hạn không tự thành nguồn ở {} (đã có 2 nguồn kề hơn 3 giây) → server tắt cơ chế này, đổ từng ô như cũ", var22.toShortString());
                     this.say("Server không cho nước vô hạn (ô " + var22.toShortString() + " không tự thành nguồn) → đổ từng ô.");
                     return var1;
                  }
               } else {
                  this.autoWaterSeen.remove(var22);
               }
            }

            if (this.autoWaterSeen.size() > 2048) {
               this.autoWaterSeen.clear();
            }

            ArrayDeque<Integer> var24 = new ArrayDeque<>();
            ArrayList<Integer> var25 = new ArrayList<>();
            ArrayList<Integer> var26 = new ArrayList<>();

            for (int var27 = 0; var27 < var9; var27++) {
               if (!var12[var27] && var11[var27] >= 2) {
                  this.waterClosure(var27, var10, var11, var12, var24, null, null);
               }
            }

            boolean[] var28 = new boolean[var9];
            int var29 = 0;

            // ô không thể tự thành (ít hơn 2 ô kề có thể có nước) → bắt buộc đổ
            for (int var30 = 0; var30 < var9; var30++) {
               if (!var12[var30] && var10[var30].length + var11[var30] < 2) {
                  var28[var30] = true;
                  var29++;
                  this.waterClosure(var30, var10, var11, var12, var24, null, null);
               }
            }

            for (int var31 = 0; var31 < var9 * 2; var31++) {
               int var32 = -1;
               int var33 = -1;
               int var34 = 0;

               for (int var35 = 0; var35 < var9 && var34 < 250; var35++) {
                  if (!var12[var35]) {
                     var34++;
                     var25.clear();
                     var26.clear();
                     int var36 = this.waterClosure(var35, var10, var11, var12, var24, var25, var26);

                     for (int var38 : var26) {
                        var11[var38]--;
                     }

                     for (int var40 : var25) {
                        var12[var40] = false;
                     }

                     BlockPos var41 = var3.get(var35).pos();
                     if (var36 > var33 || var36 == var33 && var32 >= 0 && var41.getX() + var41.getZ() < var3.get(var32).pos().getX() + var3.get(var32).pos().getZ()) {
                        var33 = var36;
                        var32 = var35;
                     }
                  }
               }

               if (var32 < 0) {
                  break;
               }

               var28[var32] = true;
               var29++;
               this.waterClosure(var32, var10, var11, var12, var24, null, null);
            }

            for (int var42 = 0; var42 < var9; var42++) {
               if (!var12[var42]) {
                  return var1;
               }
            }

            ArrayList<SchematicPlan.Target> var43 = new ArrayList<>();
            HashSet<BlockPos> var44 = new HashSet<>();

            for (int var45 = 0; var45 < var9; var45++) {
               if (!var28[var45]) {
                  var44.add(var3.get(var45).pos().toImmutable());
               }
            }

            for (SchematicPlan.Target var47 : var1) {
               if (!var44.contains(var47.pos())) {
                  var43.add(var47);
               }
            }

            if (var43.isEmpty()) {
               // mọi ô còn lại đã đủ 2 nguồn kề → chỉ chờ nước tự thành (vài tick); không mua xô đổ vào những ô đó.
               // Quá ~5 giây vẫn không thành → phần kiểm tra phía trên tắt nước vô hạn, đổ từng ô.
               this.autoWater.addAll(var44);
               return var43;
            } else {
               this.autoWater.addAll(var44);
               if (!var44.isEmpty() && this.ticks - this.autoWaterSaidAt > 600) {
                  this.autoWaterSaidAt = this.ticks;
                  AutoBuildMod.LOGGER.info("[AutoBuild] Nước vô hạn: {} ô nước chỉ cần đổ {} ô, {} ô còn lại tự thành nguồn (bớt {} lần mua xô)", new Object[]{var9, var29, var44.size(), var44.size()});
                  this.say("Nước vô hạn: " + var9 + " ô nước chỉ cần đổ " + var29 + " ô, " + var44.size() + " ô tự thành nguồn.");
               }

               return var43;
            }
         }
      }
   }

   /** thêm ô var1 vào vùng có nước nguồn, lan ra các ô đủ 2 nguồn kề; ghi lại thay đổi (var6/var7) để hoàn tác khi chỉ thử. Trả về số ô thêm được. */
   private int waterClosure(int var1, int[][] var2, int[] var3, boolean[] var4, ArrayDeque<Integer> var5, List<Integer> var6, List<Integer> var7) {
      if (var4[var1]) {
         return 0;
      } else {
         var5.clear();
         var4[var1] = true;
         if (var6 != null) {
            var6.add(var1);
         }

         var5.add(var1);
         int var8 = 0;

         while (!var5.isEmpty()) {
            int var9 = var5.poll();
            var8++;

            for (int var13 : var2[var9]) {
               if (!var4[var13]) {
                  var3[var13]++;
                  if (var7 != null) {
                     var7.add(var13);
                  }

                  if (var3[var13] >= 2) {
                     var4[var13] = true;
                     if (var6 != null) {
                        var6.add(var13);
                     }

                     var5.add(var13);
                  }
               }
            }
         }

         return var8;
      }
   }

    private boolean bucketPlacementPriority() {
        if (!running || cleanupMode || mc.player == null || mc.world == null
            || !(stage == Stage.EQUIP || stage == Stage.AIM || stage == Stage.WAIT_BLOCK
                || stage == Stage.MOVE || stage == Stage.GOALS || stage == Stage.SEARCH)) return false;
        Target intended = current();
        if (intended == null || skipped.contains(intended.pos())
            || deferred.getOrDefault(intended.pos(), 0) > ticks || !mc.world.isChunkLoaded(intended.pos())) return false;
        BlockState actual = mc.world.getBlockState(intended.pos());
        return !done(actual, intended.state()) && BucketRules.placeableBucket(actual, intended.state())
            && (pending != null || InventoryAccess.count(mc, Materials.item(actual, intended.state())) > 0);
    }



   private boolean includeFluidPhase(BuildController.Scan var1, SchematicPlan.Target var2) {
      int var3 = FluidOrder.phase(var2.state());
      if (var3 < 0) {
         return true;
      } else if (this.config.layerMode && var1.gate != null && var2.pos().getY() != var1.gate) {
         return false;
      } else {
         return var3 == this.buildFluidPhase;
      }
   }

   private boolean finishFluidPhase(BuildController.Scan var1) {
      return false;
   }

   private boolean deferEarlyFluid(SchematicPlan.Target var1) {
      if (var1 == null) {
         return false;
      } else {
         int var2 = FluidOrder.phase(var1.state());
         if (var2 <= 0) {
            return false;
         } else {
            int var3 = var2;
            if (this.plan != null && this.mc.world != null) {
               for (SchematicPlan.Target var5 : this.plan.targets) {
                  if (this.config.inYRange(var5.pos().getY())
                     && (!this.config.layerMode || var5.pos().getY() == var1.pos().getY())
                     && !this.skipped.contains(var5.pos())) {
                     int var6 = FluidOrder.phase(var5.state());
                     if (var6 >= 0 && var6 < var3) {
                        if (!this.mc.world.isChunkLoaded(var5.pos())) {
                           var3 = var6;
                        } else {
                           BlockState var7 = this.mc.world.getBlockState(var5.pos());
                           if (!this.done(var7, var5.state())
                              && !StateMatch.chestAwaitingPartner(var7, var5.state())
                              && (StateMatch.canAdd(var7, var5.state()) || StateMatch.configurable(var7, var5.state()))
                              && Materials.supported(var7, var5.state())) {
                              var3 = var6;
                           }
                        }

                        if (var3 == 0) {
                           break;
                        }
                     }
                  }
               }
            }

            // 3.0.100 (người dùng, lần dừng 20:25): trước khi đổ nước, mô phỏng nước chảy — chỉ đổ khi nước bị chặn đúng như bản vẽ.
            // Lỗi cũ: chờ 5 giây là "đổ luôn" dù xung quanh chưa có gì chặn → nước ở (x, -33, z) chảy xuống tầng -36,
            // cuốn mất dây (tripwire) → mod đặt dây 5 lần đều mất rồi dừng.
            String var8 = var2 == 1 ? this.waterSpillCached(var1.pos()) : null;
            if (var3 >= var2) {
               if (var8 != null && !this.spillOverride(var1.pos(), var8)) {
                  return this.holdWater(var1, var8);
               }

               this.fluidHoldFor = null;
               return false;
            } else if (var2 == 1 && var8 == null) {
               // nước đã bị chặn kín (vd. đã đặt bảng che hướng chảy) → đổ luôn cùng cụm, không cần chờ xây hết tầng
               if (!var1.pos().equals(this.fluidHoldSaid)) {
                  this.fluidHoldSaid = var1.pos().toImmutable();
                  AutoBuildMod.LOGGER.info("[AutoBuild] Nước @ {} đã bị chặn kín, không chảy ra ngoài bản vẽ → đổ luôn cùng cụm", var1.pos().toShortString());
               }

               this.fluidHoldFor = null;
               return false;
            } else {
               if (var8 != null) {
                  this.buildFluidPhase = var3;
                  return this.holdWater(var1, var8);
               }

               if (!var1.pos().equals(this.fluidHoldFor)) {
                  this.fluidHoldFor = var1.pos().toImmutable();
                  this.fluidHoldSince = this.ticks;
               } else if (this.ticks - this.fluidHoldSince > 100) {
                  if (!var1.pos().equals(this.fluidHoldSaid)) {
                     this.fluidHoldSaid = var1.pos().toImmutable();
                     AutoBuildMod.LOGGER
                        .info("[AutoBuild] Đã chờ 5 giây ở {} mà tầng còn {} → đổ luôn thay vì giữ xô", var1.pos().toShortString(), FluidOrder.label(var3));
                  }

                  return false;
               }

               this.buildFluidPhase = var3;
               this.status = "Chưa đổ " + var1.state().getBlock().getName().getString() + ": hoàn tất " + FluidOrder.label(this.buildFluidPhase) + " trước";
               if (this.ticks - this.fluidHoldAt > 40) {
                  this.fluidHoldAt = this.ticks;
                  AutoBuildMod.LOGGER.info("[AutoBuild] Giữ xô, chưa đổ ở {}: {}", var1.pos().toShortString(), this.status);
                  this.beginScan();
               }

               return true;
            }
         }
      }
   }

   // ===== 3.0.100: nước chảy vào ô cần đặt khối dễ trôi (dây, redstone…) =====
   private BlockPos scoopSrc;
   private int scoopUntil;
   private int scoopAt = -1000;
   private final Map<BlockPos, Integer> washWaitSaid = new HashMap<>();

   /**
    * Ô cần đặt khối dễ trôi mà đang có nước chảy qua → đặt bao nhiêu lần cũng mất (log 20:24:34–20:25:03: Tripwire đặt 5 lần).
    * Tìm nguồn nước đang chảy tới ô này: là nước mình đã đổ (bản vẽ có nước ở đó) và với tới → múc lại bằng xô rỗng (sẽ đổ lại
    * khi đã chặn kín). Không với tới → để khối này lại, xây các khối khác (các khối chặn nước) trước.
    */
   private boolean waterOnWashable(SchematicPlan.Target var1) {
      if (var1 != null && this.plan != null && this.mc.world != null && this.temporary == null && this.supportTarget == null) {
         BlockPos var2 = var1.pos();
         if (!this.mc.world.getBlockState(var2).isOf(Blocks.WATER) || !washable(this.mc, var1.state(), var2)) {
            return false;
         } else {
            BlockPos var3 = this.waterOrigin(var2);
            SchematicPlan.Target var4 = var3 == null ? null : this.plan.at(var3);
            boolean var5 = var4 != null && var4.state().isOf(Blocks.WATER);
            if (var5
               && TntGuard.tntWithin(var3, 16, null) == null
               && InventoryAccess.count(this.mc, Items.BUCKET) > 0
               && this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(var3)) <= this.reach() * this.reach()) {
               this.scoopSrc = var3.toImmutable();
               this.scoopUntil = this.ticks + 80;
               this.release();
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] {} @ {}: nước từ nguồn {} (mình đã đổ) đang chảy qua ô này → múc lại nước, chặn kín rồi mới đổ lại",
                     new Object[]{var1.state().getBlock().getName().getString(), var2.toShortString(), var3.toShortString()}
                  );
               return true;
            } else {
               if (this.ticks - this.washWaitSaid.getOrDefault(var2, -100000) > 600) {
                  this.washWaitSaid.put(var2.toImmutable(), this.ticks);
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] {} @ {}: đang có nước chảy qua ô{} → đặt vào cũng bị cuốn mất; để lại, xây các khối khác (chặn nước) trước",
                        new Object[]{
                           var1.state().getBlock().getName().getString(),
                           var2.toShortString(),
                           var3 == null ? "" : " (từ nguồn " + var3.toShortString() + (var5 ? ", xa quá tầm múc" : "") + ")"
                        }
                     );
               }

               this.deferred.put(var2.toImmutable(), this.ticks + 600);
               this.queue.remove(var1);
               this.failures.remove(var2);
               this.target = null;
               this.aim = null;
               this.beginScan();
               return true;
            }
         }
      } else {
         return false;
      }
   }

   /** nguồn nước (level 0) gần nhất đang chảy tới ô var1: đi ngược dòng qua các ô nước phía trên và bên cạnh */
   private BlockPos waterOrigin(BlockPos var1) {
      ArrayDeque<BlockPos> var2 = new ArrayDeque<>();
      HashSet<BlockPos> var3 = new HashSet<>();
      var2.add(var1.toImmutable());
      var3.add(var1.toImmutable());

      while (!var2.isEmpty() && var3.size() < 600) {
         BlockPos var4 = var2.poll();
         BlockState var5 = this.mc.world.getBlockState(var4);
         if (Materials.source(var5)) {
            return var4;
         }

         BlockPos[] var6 = new BlockPos[]{var4.up(), var4.offset(Direction.NORTH), var4.offset(Direction.SOUTH), var4.offset(Direction.WEST), var4.offset(Direction.EAST)};

         for (BlockPos var10 : var6) {
            if (this.mc.world.isChunkLoaded(var10) && this.mc.world.getBlockState(var10).isOf(Blocks.WATER) && var3.add(var10.toImmutable())) {
               var2.add(var10.toImmutable());
            }
         }
      }

      return null;
   }

   /** múc nguồn nước scoopSrc bằng xô rỗng: cầm xô, nhìn vào tâm ô nước, dùng xô */
   private boolean scoopTick() {
      if (this.scoopSrc == null || this.mc.player == null || this.mc.world == null) {
         return false;
      } else {
         boolean var1 = Materials.source(this.mc.world.getBlockState(this.scoopSrc));
         if (!var1 || this.ticks > this.scoopUntil || InventoryAccess.count(this.mc, Items.BUCKET) == 0 || this.mc.currentScreen != null) {
            AutoBuildMod.LOGGER
               .info(var1 ? "[AutoBuild] Chưa múc được nước @ {} → để lại, xây khối khác trước" : "[AutoBuild] Đã múc lại nước @ {} → chặn kín rồi mới đổ lại", this.scoopSrc.toShortString());
            if (!var1) {
               this.deferred.put(this.scoopSrc, this.ticks + 600);
            }

            this.scoopSrc = null;
            Input.key(this.mc.options.useKey, false);
            this.target = null;
            this.aim = null;
            this.beginScan();
            return true;
         } else {
            this.status = "Múc lại nước @ " + this.scoopSrc.toShortString();
            Input.key(this.mc.options.forwardKey, false);
            Input.key(this.mc.options.jumpKey, false);
            if (!this.mc.player.getMainHandStack().isOf(Items.BUCKET)) {
               InventoryAccess.equip(this.mc, Items.BUCKET, this.config.workingHotbarSlot);
               return true;
            } else {
               Vec3d var2 = this.mc.player.getEyePos();
               Vec3d var3 = new Vec3d((double)this.scoopSrc.getX() + 0.5, (double)this.scoopSrc.getY() + 0.4, (double)this.scoopSrc.getZ() + 0.5);
               this.lookAt(MotionMath.yaw(var3.x - var2.x, var3.z - var2.z), MotionMath.pitch(var3.x - var2.x, var3.y - var2.y, var3.z - var2.z));
               if (this.rotationSent() && this.mc.player.isOnGround() && this.ticks - this.scoopAt > 8 && this.unacked == 0 && InventoryAccess.synced(this.mc)) {
                  this.scoopAt = this.ticks;
                  this.clickTick = this.ticks;
                  // 3.0.122: chuột phải thật
                  Input.queueUse(this.mc, null, false, "múc nước @ " + this.scoopSrc.toShortString(), () -> {
                     Item var5 = this.mc.player.getMainHandStack().getItem();
                     int var6 = this.mc.player.getMainHandStack().getCount();
                     ActionResult var4 = this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
                     this.qualityBucketUsed(var5, var6, "múc nước");
                     if (var4 != null && var4.isAccepted()) {
                        this.mc.player.swingHand(Hand.MAIN_HAND);
                     }
                  });
               }

               return true;
            }
         }
      }
   }

   // ===== 3.0.100: mô phỏng nước chảy trước khi đổ =====
   private final Map<BlockPos, Integer> waterHeldSince = new HashMap<>();
   private final Map<BlockPos, Integer> waterHoldSaid = new HashMap<>();
   private BlockPos spillCachePos;
   private String spillCacheVal;
   private int spillCacheAt = -1000;

   private java.util.Set<BlockPos> spillCells = java.util.Set.of();

   private String waterSpillCached(BlockPos var1) {
      if (var1.equals(this.spillCachePos) && this.ticks - this.spillCacheAt < 20) {
         return this.spillCacheVal;
      } else {
         this.spillCachePos = var1.toImmutable();
         this.spillCacheAt = this.ticks;
         this.spillCacheVal = this.waterSpill(var1);
         return this.spillCacheVal;
      }
   }

   /**
    * Đổ nước nguồn ở var1 thì nước sẽ chảy tới đâu (theo luật vanilla: chảy xuống được thì chỉ chảy xuống; không thì loang ngang
    * tối đa 7 ô, chảy xuống lại loang tiếp). Trả về null nếu nước chỉ ở trong các ô bản vẽ có nước / khối sẽ đặt sau;
    * ngược lại trả về lý do (cuốn trôi khối, chảy vào ô bản vẽ để trống, chảy ra ngoài bản vẽ…).
    * Khối tạm của mod (sẽ bị đập) và khối thừa không tính là chặn nước.
    */
   private String waterSpill(BlockPos var1) {
      this.spillCells = java.util.Set.of();
      if (this.plan != null && this.mc.world != null) {
         BlockState var2 = this.mc.world.getBlockState(var1);
         if (!var2.isAir() && var2.contains(Properties.WATERLOGGED) && var2.isFullCube(this.mc.world, var1)) {
            // khối đặc chứa nước (vd. lá ngập nước) → nước không chảy ra
            return null;
         } else {
            HashMap<BlockPos, Integer> var3 = new HashMap<>();
            ArrayDeque<BlockPos> var4 = new ArrayDeque<>();
            var3.put(var1.toImmutable(), 0);
            var4.add(var1.toImmutable());
            // 3.0.104: nhớ các ô nước sẽ chảy tới → kiểm tra observer đang nhìn các ô đó (gần TNT)
            this.spillCells = var3.keySet();

            while (!var4.isEmpty()) {
               BlockPos var5 = var4.poll();
               int var6 = var3.get(var5);
               if (var3.size() > 1200) {
                  return "nước chảy quá xa (> 1200 ô)";
               }

               BlockPos var7 = var5.down().toImmutable();
               String var8 = this.waterCell(var7);
               if (var8 != null) {
                  if (!var8.isEmpty()) {
                     return var8;
                  }

                  Integer var9 = var3.get(var7);
                  if (var9 == null || var9 > 0) {
                     var3.put(var7, 0);
                     var4.add(var7);
                  }
               } else if (var6 < 7) {
                  for (Direction var11 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                     BlockPos var12 = var5.offset(var11).toImmutable();
                     String var13 = this.waterCell(var12);
                     if (var13 != null) {
                        if (!var13.isEmpty()) {
                           return var13;
                        }

                        Integer var14 = var3.get(var12);
                        if (var14 == null || var14 > var6 + 1) {
                           var3.put(var12, var6 + 1);
                           var4.add(var12);
                        }
                     }
                  }
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   /** null = nước không vào được ô này; "" = nước vào được và không sao; chuỗi khác = lý do không được đổ */
   private String waterCell(BlockPos var1) {
      if (!this.mc.world.isChunkLoaded(var1)) {
         return "ô " + var1.toShortString() + " chưa tải";
      } else {
         BlockState var2 = this.mc.world.getBlockState(var1);
         if (var2.isOf(Blocks.LAVA)) {
            return "nước gặp lava @ " + var1.toShortString();
         } else if (var2.isOf(Blocks.WATER)) {
            return "";
         } else {
            SchematicPlan.Target var3 = this.plan.at(var1);
            boolean var4 = this.journal != null && this.journal.placed.containsKey(var1) && (var3 == null || var3.state().isAir());
            if (!var4 && waterBlocks(this.mc, var2, var1)) {
               return null;
            } else if (!var2.isAir() && !var4) {
               return "nước sẽ cuốn trôi " + var2.getBlock().getName().getString() + " @ " + var1.toShortString();
            } else if (var3 == null) {
               return var4 ? "khối tạm @ " + var1.toShortString() + " đang chặn nước, đập đi nước sẽ chảy ra ngoài bản vẽ" : "nước chảy ra ngoài bản vẽ @ " + var1.toShortString();
            } else {
               BlockState var5 = var3.state();
               if (var5.isOf(Blocks.WATER) || var5.contains(Properties.WATERLOGGED) && (Boolean)var5.get(Properties.WATERLOGGED)) {
                  return "";
               } else if (var5.isAir()) {
                  return var4
                     ? "khối tạm @ " + var1.toShortString() + " đang chặn nước (bản vẽ để trống), đập đi nước sẽ chảy ra"
                     : "nước chảy vào ô bản vẽ để trống @ " + var1.toShortString();
               } else if (washable(this.mc, var5, var1)) {
                  return "ô " + var1.toShortString() + " sẽ đặt " + var5.getBlock().getName().getString() + " → nước cuốn mất";
               } else {
                  // khối công trình chưa đặt: nước tạm chảy qua, đặt khối sau sẽ chặn lại
                  return "";
               }
            }
         }
      }
   }

   /** 3.0.100: khối bị nước chảy phá (dây, redstone, đuốc, ray, nút, hoa…): không có hình va chạm, không chứa nước, không phải bảng/cửa */
   static boolean washable(net.minecraft.client.MinecraftClient var0, BlockState var1, BlockPos var2) {
      if (var1 == null || var1.isAir() || !var1.getFluidState().isEmpty() || var1.contains(Properties.WATERLOGGED)) {
         return false;
      } else {
         Block var3 = var1.getBlock();
         return !(var3 instanceof net.minecraft.block.DoorBlock)
            && !(var3 instanceof net.minecraft.block.SignBlock)
            && !(var3 instanceof net.minecraft.block.WallSignBlock)
            && !(var3 instanceof net.minecraft.block.HangingSignBlock)
            && !(var3 instanceof net.minecraft.block.WallHangingSignBlock)
            && !movementSolid(var1.getCollisionShape(var0.world, var2));
      }
   }

   /**
    * Giống AbstractBlockState.isSolid() của vanilla (nước chỉ phá được khối "không đặc"): hình va chạm rỗng → không đặc;
    * cạnh trung bình của hộp bao ≥ 0,729 hoặc cao ≥ 1 ô → đặc. Vd. dây, đuốc, ray, thảm, repeater bị nước phá; slab, hopper thì không.
    */
   static boolean movementSolid(net.minecraft.util.shape.VoxelShape var0) {
      if (var0 == null || var0.isEmpty()) {
         return false;
      } else {
         double var1 = var0.getMax(Direction.Axis.X) - var0.getMin(Direction.Axis.X);
         double var3 = var0.getMax(Direction.Axis.Y) - var0.getMin(Direction.Axis.Y);
         double var5 = var0.getMax(Direction.Axis.Z) - var0.getMin(Direction.Axis.Z);
         return (var1 + var3 + var5) / 3.0 >= 0.7291666666666666 || var3 >= 1.0;
      }
   }

   /** nước chảy KHÔNG vào được ô đang là khối này (khối có va chạm, khối chứa được nước, bảng, cửa) */
   static boolean waterBlocks(net.minecraft.client.MinecraftClient var0, BlockState var1, BlockPos var2) {
      if (var1.isAir()) {
         return false;
      } else if (!var1.getFluidState().isEmpty()) {
         return var1.contains(Properties.WATERLOGGED);
      } else {
         return var1.contains(Properties.WATERLOGGED) || !washable(var0, var1, var2);
      }
   }

   /** 3 phút vẫn chỉ vướng "chảy vào ô bản vẽ để trống" (không phá khối nào) mà tầng đã xong hết phần khác → coi bản vẽ thiếu nước chảy, đổ */
   private boolean spillOverride(BlockPos var1, String var2) {
      if (var2.contains("cuốn") || var2.contains("lava") || var2.contains("khối tạm") || var2.contains("chưa tải")) {
         return false;
      } else {
         int var3 = this.waterHeldSince.computeIfAbsent(var1.toImmutable(), var1x -> this.ticks);
         if (this.ticks - var3 > 3600) {
            AutoBuildMod.LOGGER.info("[AutoBuild] Nước @ {}: đã giữ 3 phút, chỉ còn vướng \"{}\" (không phá khối nào) → đổ", var1.toShortString(), var2);
            this.waterHeldSince.remove(var1);
            return true;
         } else {
            return false;
         }
      }
   }

   private boolean holdWater(SchematicPlan.Target var1, String var2) {
      BlockPos var3 = var1.pos().toImmutable();
      this.waterHeldSince.putIfAbsent(var3, this.ticks);
      this.status = "Chưa đổ nước @ " + var3.toShortString() + ": " + var2;
      if (this.ticks - this.waterHoldSaid.getOrDefault(var3, -100000) > 400) {
         this.waterHoldSaid.put(var3, this.ticks);
         AutoBuildMod.LOGGER.info("[AutoBuild] Giữ xô, chưa đổ nước @ {}: {} → xây các khối chặn nước trước", var3.toShortString(), var2);
      }

      this.deferred.put(var3, this.ticks + 300);
      this.queue.remove(var1);
      if (this.ticks - this.fluidHoldAt > 40) {
         this.fluidHoldAt = this.ticks;
         this.beginScan();
      }

      return true;
   }


   // ===== 3.0.105: chống kẹt dài hạn (log 29/09 02:59–08:54: 6 giờ không đặt thêm được khối nào) =====
   private int confirmedAt;
   private int bestMatched = -1;
   private int stuckRelogs;
   private final ArrayDeque<Integer> lostTimes = new ArrayDeque<>();
   private final Map<BlockPos, Integer> waterAsks = new HashMap<>();
   private final Map<BlockPos, Integer> waterAsksAt = new HashMap<>();
   private int doneSavedSize = -1;
   private int doneSavedAt;

   /** có tiến triển THẬT (server đã xác nhận khối đặt / khối đập) */
   private void confirmed() {
      this.confirmedAt = this.ticks;
      this.stuckRelogs = 0;
   }

   /**
    * Người dùng (29/09): "nếu sau 3 phút vẫn không đặt thêm được khối nào thì thoát ra vào lại". Thời gian ở trong menu server
    * (Order, /home, ender chest, crafter, /sell, /shop) và lúc chờ vật liệu không tính. Vào lại vẫn kẹt tiếp → lần sau chờ lâu dần
    * (3, 3, 5, 5, 10 phút…) để không vào ra liên tục khi thật sự không còn gì đặt được.
    */
   private void stuckWatch() {
      if (!this.running || this.plan == null || this.mc.player == null || Reconnector.active()) {
         this.confirmedAt = this.ticks;
      } else if (this.menuStage()
         || this.shopping
         || this.browsing
         || this.mc.currentScreen != null
         || this.stage == BuildController.Stage.LOAD
         || this.stage == BuildController.Stage.IDLE
         || this.stage == BuildController.Stage.WAIT_MATERIAL) {
         // đứng im đồng hồ (không xoá) — kẹt vòng mua đồ liên tục vẫn bị phát hiện
         this.confirmedAt++;
      } else {
         int var1 = this.stuckRelogs <= 1 ? 3600 : (this.stuckRelogs <= 3 ? 6000 : 12000);
         int var2 = this.ticks - this.confirmedAt;
         if (var2 >= var1 && this.mc.getCurrentServerEntry() != null) {
            this.stuckRelogs++;
            this.confirmedAt = this.ticks;
            String var3 = var2 / 1200 + " phút không đặt thêm được khối nào (lần " + this.stuckRelogs + ")";
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] {} — {} | bước {} | tầng Y={} | đứng tại {} → thoát ra vào lại server (server gửi lại túi đồ + khối, hết ghost/lệch đồ)",
                  new Object[]{var3, this.status, this.stage, this.currentLayer, this.mc.player.getBlockPos().toShortString()}
               );
            this.saveDone(true);
            Reconnector.request(this.mc, this, var3);
         }
      }
   }

   /** bị server trả về liên tục (≥ 4 khối trong 30 giây) → gần như chắc chắn lệch đồ trên tay → đồng bộ lại túi đồ */
   private void noteLost() {
      this.lostTimes.addLast(this.ticks);

      while (!this.lostTimes.isEmpty() && this.ticks - this.lostTimes.peekFirst() > 600) {
         this.lostTimes.pollFirst();
      }

      if (this.lostTimes.size() >= 4 && InventoryAccess.forceResync(this.mc, this.lostTimes.size() + " khối bị server trả về trong 30 giây")) {
         this.lostTimes.clear();
      }
   }

   /**
    * DonutSMP giấu khối ở xa dưới lòng đất (anti-xray): log 29/09 cho thấy sau mỗi lần vào lại / dịch chuyển, cả chục tầng đã xây
    * xong (Y=-42…-49, 795 ô/tầng) ở cách ≥ 15 ô đọc ra trống/đá → "từng xong nhưng giờ chưa đúng" hàng loạt, kéo mod xuống làm lại
    * tầng dưới, chui vào vùng đá. Chỉ tin khối đọc được trong 12 ô quanh người; xa hơn mà mình đã xác nhận xong thì vẫn coi là xong.
    */
   boolean trusted(BlockPos var1) {
      return this.mc.player == null || this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(var1)) <= 144.0;
   }

   /** lá (khối ngập nước) đòi đổ nước liên tục mà không vào: quá 4 lần trong 2 phút → để ô đó 1 phút, đồng bộ túi đồ */
   private boolean qualityWaterAskedOriginal(BlockPos var1) {
      BlockPos var2 = var1.toImmutable();
      if (this.ticks - this.waterAsksAt.getOrDefault(var2, -100000) > 2400) {
         this.waterAsks.put(var2, 0);
         this.waterAsksAt.put(var2, this.ticks);
      }

      int var3 = this.waterAsks.merge(var2, 1, Integer::sum);
      if (var3 > 4) {
         this.waterAsks.put(var2, 0);
         this.waterAsksAt.put(var2, this.ticks);
         this.deferred.put(var2, this.ticks + 1200);
         AutoBuildMod.LOGGER.info("[AutoBuild] {} đòi đổ nước {} lần trong 2 phút mà nước không vào → để ô này 1 phút, đồng bộ lại túi đồ", var2.toShortString(), var3);
         InventoryAccess.forceResync(this.mc, "đổ nước nhiều lần không vào");
         return true;
      } else {
         return false;
      }
   }

   // ===== 3.0.108: không tự dừng — chỉ dừng khi người dùng bấm K; 25 lần đặt hỏng liên tiếp → thoát ra vào lại =====
   private boolean wanted;
   private int autoNextAt;
   private int autoTries;
   private Object autoScreen;
   private int autoScreenSince = -1;
   private int healthWaitSince = -1;
   private int healthWaiveUntil = -100000;
   private int finishedAt = -100000;
   private int respawnAt = -100000;
   private int cursorDropAt = -100000;
   private int worldWaitSaidAt = -100000;
   private boolean autoKeys;
   private int placementChangedAt = -100000;
   private String autoSig;
   private int autoSigSince;
   private int placeFails;
   private int failRelogs;
   private final Map<BlockPos, Integer> failCountedAt = new HashMap<>();

   /** đặt được 1 khối thật (server xác nhận) → xoá bộ đếm "đặt hỏng liên tiếp" */
    private void notePlaceOk() {
        // A successful unrelated cell must not erase failures of the stuck cells.
        failRelogs = 0;
    }


   /**
    * Người dùng (29/09, log 22:20:02–22:23:08): hơn 3 phút mọi lần đặt đều "không hiện / bị server trả về", đặt ra nhầm loại, đổ nước
    * không vào; tự thoát ra vào lại thì hết ngay. Watchdog 3 phút của 3.0.105 không kịp vì đồng hồ của nó còn tính cả việc đập khối sai.
    * Giờ: đếm từng lần đặt / đổ hỏng; 25 lần LIÊN TIẾP (không xen lần nào thành công) → thoát ra vào lại. Vào lại vẫn hỏng tiếp
    * (chưa đặt lại được khối nào) thì lần sau cần 50, rồi 100 lần — tránh ra vào liên tục khi ô đó thật sự không đặt được.
    */
    private void notePlaceFail(BlockPos pos, String reason) {
        if (!running || Reconnector.active() || mc.player == null) return;
        if (!recoveryBudget().failed(pos.asLong(), ticks)) return;
        placeFails = recoveryBudget().outstanding();
        if (placeFails % 5 == 0 && placeFails < PlacementRecoveryBudget.LIMIT)
            AutoBuildMod.LOGGER.info("[AutoBuild] {}/25 lần đặt thất bại chưa khắc phục → đủ 25 lần sẽ thoát ra vào lại", placeFails);
        if (!recoveryBudget().reconnectDue()) return;
        String why = placeFails + "/25 lần đặt thất bại (" + reason + " @ " + pos.toShortString() + ")";
        saveDone(true);
        release();
        Input.key(mc.options.useKey, false);
        failRelogs++;
        AutoBuildMod.LOGGER.info("[AutoBuild] {} → thoát ra vào lại server", why);
        Reconnector.request(mc, this, why);
    }


   private String selectedSig() {
      try {
         fi.dy.masa.litematica.schematic.placement.SchematicPlacement var1 = fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager()
            .getSelectedSchematicPlacement();
         return var1 == null ? "-" : SchematicPlan.layoutKey(var1) + ":" + var1.isEnabled();
      } catch (Throwable var2) {
         return "?";
      }
   }

   /** tắt "tự mở menu ESC khi chuyển sang cửa sổ khác" (F3+P) — menu đó làm mod đứng chờ khi người dùng không ngồi máy */
   private void noPauseOnLostFocus() {
      try {
         if (this.mc.options != null && this.mc.options.pauseOnLostFocus) {
            this.mc.options.pauseOnLostFocus = false;
            AutoBuildMod.LOGGER.info("[AutoBuild] Tắt \"tạm dừng khi rời cửa sổ\" (F3+P) để mod chạy tiếp khi bạn chuyển sang cửa sổ khác");
         }
      } catch (Throwable var2) {
      }
   }

   /** túi đầy mà con trỏ đang cầm đồ (không cất được) → ném món đó ra đất thay vì dừng */
   private boolean dropCursor() {
      if (this.mc.player != null && this.mc.interactionManager != null && this.ticks - this.cursorDropAt >= 20) {
         ItemStack var1 = this.mc.player.currentScreenHandler.getCursorStack();
         if (var1.isEmpty()) {
            return true;
         } else {
            this.cursorDropAt = this.ticks;
            AutoBuildMod.LOGGER.info("[AutoBuild] Túi đầy mà con trỏ đang cầm {} x{} → ném ra đất để xây tiếp (không dừng)", var1.getName().getString(), var1.getCount());
            this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, -999, 0, SlotActionType.PICKUP, this.mc.player);
            if (this.mc.player.currentScreenHandler == this.mc.player.playerScreenHandler) {
               InventoryAccess.invClicked();
            }

            InventoryAccess.noteClick(this.mc, "ném đồ trên con trỏ");
            InventoryAccess.markDirty(this.mc, "vừa ném đồ trên con trỏ");
            return true;
         }
      } else {
         return this.ticks - this.cursorDropAt < 20;
      }
   }

   /**
    * Người dùng (29/09): "tự chạy mà không tự dừng, chỉ dừng khi tôi chủ động bấm K". Mọi lần mod tự tạm dừng (mở menu, máu thấp,
    * chết, ngồi xe, rơi lava, đổi placement, lỗi lặp, túi đầy, mất kết nối, vào lại server mà chưa bắt đầu được…) giờ chỉ là tạm:
    * mod tự xử lý nguyên nhân rồi tự chạy lại (thử lại cách nhau 3 giây → tối đa 1 phút).
    */
   private void autoResumeTick() {
      if (!this.wanted || this.running || Reconnector.active()) {
         this.autoScreen = null;
         return;
      }

      if (this.mc.player == null || this.mc.world == null || this.mc.interactionManager == null) {
         if (this.mc.currentScreen instanceof DisconnectedScreen && this.ticks >= this.autoNextAt) {
            this.autoNextAt = this.ticks + 100;
            Reconnector.requestLost(this.mc, "Mất kết nối (mod vẫn đang bật)");
         }

         return;
      }

      if (!this.mc.player.isAlive()) {
         if (this.ticks - this.respawnAt >= 60) {
            this.respawnAt = this.ticks;
            AutoBuildMod.LOGGER.info("[AutoBuild] Nhân vật đã chết → tự bấm hồi sinh, hồi sinh xong tự về chỗ đang xây");

            try {
               this.mc.player.requestRespawn();
            } catch (Throwable var6) {
               AutoBuildMod.LOGGER.warn("[AutoBuild] Không bấm hồi sinh được: {}", var6.toString());
            }
         }

         this.status = "Chờ hồi sinh… (K để dừng hẳn)";
         this.autoNextAt = Math.max(this.autoNextAt, this.ticks + 60);
         return;
      }

      if (this.mc.currentScreen instanceof DeathScreen) {
         this.mc.setScreen(null);
         return;
      }

      if (this.mc.currentScreen != null) {
         if (this.autoScreen != this.mc.currentScreen) {
            this.autoScreen = this.mc.currentScreen;
            this.autoScreenSince = this.ticks;
         }

         if (this.mc.currentScreen instanceof AbstractSignEditScreen) {
            this.mc.currentScreen.close();
         } else if (this.mc.currentScreen instanceof HandledScreen
            && this.mc.player.currentScreenHandler != this.mc.player.playerScreenHandler
            && this.ticks - this.autoScreenSince >= 400) {
            // menu của server (shop, order, rương, hopper…) tự bật lên và nằm yên 20 giây → đóng rồi xây tiếp
            AutoBuildMod.LOGGER
               .info("[AutoBuild] Menu \"{}\" mở {} giây không ai dùng → tự đóng rồi xây tiếp", this.mc.currentScreen.getTitle().getString(), (this.ticks - this.autoScreenSince) / 20);
            this.mc.player.closeHandledScreen();
            this.autoScreenSince = this.ticks;
         }

         this.status = "Đang mở menu — đóng menu là mod tự xây tiếp (K để dừng hẳn)";
         return;
      }

      this.autoScreen = null;
      if (this.mc.player.hasVehicle()) {
         // ngồi xe/thuyền → giữ Shift để xuống
         this.autoKeys = true;
         Input.key(this.mc.options.sneakKey, this.ticks % 20 < 10);
         this.status = "Đang ngồi trên xe/thuyền → xuống rồi xây tiếp";
         return;
      }

      if (this.mc.player.isInLava()) {
         this.autoKeys = true;
         Input.key(this.mc.options.jumpKey, true);
         Input.key(this.mc.options.forwardKey, true);
         this.status = "Rơi vào lava → bơi ra rồi xây tiếp";
         return;
      }

      if (this.autoKeys) {
         // chỉ nhả các phím chính mod vừa giữ (không đụng phím người chơi đang bấm)
         this.autoKeys = false;
         Input.key(this.mc.options.jumpKey, false);
         Input.key(this.mc.options.forwardKey, false);
         Input.key(this.mc.options.sneakKey, false);
      }

      if (this.ticks >= this.autoNextAt) {
         if (this.finishedAt > -100000 && this.ticks - this.finishedAt < 12000) {
            this.status = "Đã xây xong — " + (12000 - (this.ticks - this.finishedAt)) / 1200 + " phút nữa tự kiểm tra lại (K để dừng hẳn)";
            this.autoNextAt = this.ticks + 100;
         } else if (this.plan != null && this.plan.loaded && this.worldKey != null && !this.worldKey.equals(ScaffoldJournal.worldKey(this.mc))) {
            this.status = "Đang ở thế giới/server khác (" + ScaffoldJournal.worldKey(this.mc) + ") — về lại " + this.worldKey + " là tự xây tiếp";
            if (this.ticks - this.worldWaitSaidAt > 6000) {
               this.worldWaitSaidAt = this.ticks;
               AutoBuildMod.LOGGER.info("[AutoBuild] {}", this.status);
            }

            this.autoNextAt = this.ticks + 100;
         } else if (this.mc.player.isCreative() || this.mc.player.isSpectator()) {
            this.status = "Mod chỉ chạy trong Survival — về Survival là tự xây tiếp";
            this.autoNextAt = this.ticks + 200;
         } else {
            if (this.mc.player.getHealth() <= (float)this.config.minHealth && !this.inSnowNow()) {
               if (this.healthWaitSince < 0) {
                  this.healthWaitSince = this.ticks;
               }

               if (this.ticks - this.healthWaitSince < 1800) {
                  this.status = "Máu thấp (" + (int)this.mc.player.getHealth() + ") → chờ hồi máu rồi tự xây tiếp (K để dừng hẳn)";
                  this.autoNextAt = this.ticks + 40;
                  return;
               }

               // chờ 90 giây mà máu không lên (đói, không có đồ ăn) → xây tiếp, 5 phút tới không dừng vì máu
               this.healthWaiveUntil = this.ticks + 6000;
               AutoBuildMod.LOGGER.info("[AutoBuild] Chờ hồi máu 90 giây mà máu vẫn {} → xây tiếp (5 phút tới không dừng vì máu)", (int)this.mc.player.getHealth());
            }

            this.healthWaitSince = -1;
            String var8 = this.selectedSig();
            if (!var8.equals(this.autoSig)) {
               this.autoSig = var8;
               this.autoSigSince = this.ticks;
            }

            if (this.ticks - this.placementChangedAt < 600 || this.ticks - this.placementChangedAt < 12000 && this.ticks - this.autoSigSince < 600) {
               // người dùng đang dời/xoay placement → chờ nó đứng yên 30 giây rồi mới đọc lại (không xây theo vị trí đang kéo dở)
               this.status = "Placement vừa đổi → chờ 30 giây không đổi nữa rồi tự đọc lại (K để dừng hẳn)";
               this.autoNextAt = this.ticks + 20;
               return;
            }

            this.autoTries++;
            AutoBuildMod.LOGGER.info("[AutoBuild] Tự chạy lại (lần {}) — lúc tạm dừng: {}", this.autoTries, this.status);
            this.noPauseOnLostFocus();
            this.start();
            if (this.running) {
               this.finishedAt = -100000;
               this.afterReconnect();
            }

            int[] var7 = new int[]{60, 100, 200, 400, 600, 1200};
            this.autoNextAt = this.ticks + var7[Math.min(this.autoTries, var7.length) - 1];
         }
      }
   }

   // --- lưu danh sách khối đã xác nhận xong (để lần sau mở game vẫn biết tầng dưới đã xây, không tin khối bị giấu ở xa) ---
   private static final java.nio.file.Path DONE_FILE = net.fabricmc.loader.api.FabricLoader.getInstance()
      .getConfigDir()
      .resolve("donut-autobuild-done.dat");

   /**
    * 3.0.107 (log 29/09 21:12:43–21:13:12): bắt đầu xây "Unnamed" được, dừng 7 giây, nhấn K lại thì 7 lần liền "Chọn và bật một
    * placement trong Litematica trước". Lỗi: mỗi lần nhấn K, start() thấy placement đang chọn trong Litematica không còn đúng cái đang
    * xây (bấm lại vào placement đang chọn trong danh sách của Litematica là BỎ chọn; nút Enabled là tắt) → đọc lại bản vẽ từ placement
    * đang chọn = không có → báo lỗi và dừng, dù bản vẽ đang xây vẫn còn nguyên trong bộ nhớ.
    * Giờ: Litematica không chọn gì / placement đang chọn bị tắt / đang chọn chính placement cũ → giữ bản vẽ đang xây, tự chọn lại và bật
    * placement của nó. Chỉ đọc bản vẽ mới khi người dùng chọn một placement KHÁC đang bật.
    */
   private boolean keepPlanSelectionLost() {
      if (this.plan == null
         || !this.plan.loaded
         || this.preparing
         || this.plan.placement == null
         || this.worldKey == null
         || !this.worldKey.equals(ScaffoldJournal.worldKey(this.mc))) {
         return false;
      } else {
         try {
            fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager var1 = fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager();
            fi.dy.masa.litematica.schematic.placement.SchematicPlacement var2 = var1.getSelectedSchematicPlacement();
            fi.dy.masa.litematica.schematic.placement.SchematicPlacement var3 = this.plan.placement;
            if (var2 != null && var2 != var3 && var2.isEnabled()) {
               return false;
            } else if (var2 == var3 && var3.isEnabled()) {
               // 3.0.108: vẫn chọn đúng placement đang xây và đang bật mà khác đi = bị dời/xoay/nạp lại file → đọc bản vẽ mới
               return false;
            } else if (this.plan.layout != null && !this.plan.layout.equals(SchematicPlan.layoutKey(var3))) {
               // placement đang xây đã bị dời/xoay trong lúc bị bỏ chọn/tắt → không giữ bản vẽ cũ (sẽ xây sai chỗ)
               return false;
            } else {
               boolean var4 = var1.getAllSchematicsPlacements().contains(var3);
               if (!var4) {
                  var1.addSchematicPlacement(var3, false);
               }

               if (var1.getSelectedSchematicPlacement() != var3) {
                  var1.setSelectedSchematicPlacement(var3);
               }

               if (!var3.isEnabled()) {
                  var3.setEnabled(true);
               }

               this.plan.rebind(var3);
               String var5 = var2 == null
                  ? "Litematica đang không chọn placement nào → tự chọn lại \"" + var3.getName() + "\" đang xây"
                  : (var2 == var3 ? "Placement \"" + var3.getName() + "\" đang bị tắt trong Litematica → tự bật lại" : "Giữ bản vẽ đang xây");
               this.say(var5 + (var4 ? "" : " (đã thêm lại vào Litematica)") + ".");
               return true;
            }
         } catch (Throwable var6) {
            AutoBuildMod.LOGGER.warn("[AutoBuild] Không giữ được bản vẽ đang xây khi Litematica bỏ chọn ({})", var6.toString());
            return false;
         }
      }
   }

   /** tên + gốc placement đã xây lần trước (đọc từ file lưu tiến độ) → SchematicPlan tự chọn đúng cái đó nếu Litematica chưa chọn gì */
   private void placementHint() {
      try {
         if (java.nio.file.Files.exists(DONE_FILE)) {
            try (java.io.DataInputStream var1 = new java.io.DataInputStream(
                  new java.io.BufferedInputStream(java.nio.file.Files.newInputStream(DONE_FILE))
               )) {
               String[] var2 = var1.readUTF().split("\\|");
               if (var2.length >= 4) {
                  String[] var3 = var2[var2.length - 1].split(",");
                  SchematicPlan.hintName = var2[var2.length - 2];
                  SchematicPlan.hintOrigin = new BlockPos(Integer.parseInt(var3[0]), Integer.parseInt(var3[1]), Integer.parseInt(var3[2]));
               }
            }
         }
      } catch (Throwable var6) {
      }
   }

   private String doneKey() {
      if (this.plan != null && this.plan.placement != null && this.mc.world != null && this.mc.player != null) {
         BlockPos var1 = this.plan.placement.getOrigin();
         return ScaffoldJournal.worldKey(this.mc) + "|" + this.plan.placement.getName() + "|" + var1.getX() + "," + var1.getY() + "," + var1.getZ();
      } else {
         return null;
      }
   }

   private void saveDone(boolean var1) {
      String var2 = this.doneKey();
      if (var2 != null && !this.placedDone.isEmpty() && (var1 || this.placedDone.size() != this.doneSavedSize)) {
         try (java.io.DataOutputStream var3 = new java.io.DataOutputStream(
               new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(DONE_FILE))
            )) {
            var3.writeUTF(var2);
            var3.writeInt(this.placedDone.size());

            for (BlockPos var5 : this.placedDone) {
               var3.writeLong(var5.asLong());
            }

            // 3.0.107: các tầng dưới chỉ còn ô bị đá chiếm (để đào sau) → mở lại game không đi xuống đào nữa
            var3.writeInt(this.digStuckLayers.size());

            for (int var6 : this.digStuckLayers) {
               var3.writeInt(var6);
            }

            this.doneSavedSize = this.placedDone.size();
         } catch (Exception var8) {
            AutoBuildMod.LOGGER.warn("[AutoBuild] Không lưu được danh sách khối đã xong: {}", var8.toString());
         }
      }
   }

   private void loadDone() {
      String var1 = this.doneKey();
      if (var1 != null && java.nio.file.Files.exists(DONE_FILE)) {
         try (java.io.DataInputStream var2 = new java.io.DataInputStream(
               new java.io.BufferedInputStream(java.nio.file.Files.newInputStream(DONE_FILE))
            )) {
            if (var1.equals(var2.readUTF())) {
               int var3 = var2.readInt();

               for (int var4 = 0; var4 < var3 && var4 < 2000000; var4++) {
                  long var5 = var2.readLong();
                  this.markPlacedDone(this.placedDone, new BlockPos((int)(var5 >> 38), (int)(var5 << 52 >> 52), (int)(var5 << 26 >> 38)));
               }

               this.doneSavedSize = this.placedDone.size();

               try {
                  int var9 = var2.readInt();

                  for (int var10 = 0; var10 < var9 && var10 < 4096; var10++) {
                     this.loadedDigStuck.add(var2.readInt());
                  }
               } catch (java.io.EOFException var11) {
               }

               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] Nạp {} khối đã xây xong từ lần trước (khối ở xa bị server giấu vẫn được coi là xong); tầng để đào sau: {}",
                     var3,
                     this.loadedDigStuck
                  );
            }
         } catch (Exception var8) {
            AutoBuildMod.LOGGER.warn("[AutoBuild] Không đọc được danh sách khối đã xong: {}", var8.toString());
         }
      }
   }

   private void doneSaveTick() {
      if (this.running && this.ticks - this.doneSavedAt >= 2400) {
         this.doneSavedAt = this.ticks;
         this.saveDone(false);
      }
   }


   // ===== 3.0.104: an toàn TNT (người dùng: "mỗi việc làm gần TNT đều phải xem xét có làm nó kích hoạt không") =====
   private BlockPos tntScanCenter;
   private int tntScanDy = 99;
   private int tntScanDone = -1000;
   private int tntSaidAt = -100000;
   private final ArrayList<BlockPos> tntScanTnt = new ArrayList<>();
   private final ArrayList<BlockPos> tntScanSensors = new ArrayList<>();
   private Block sculkA;
   private Block sculkB;
   private final Map<BlockPos, Integer> tntSaid = new HashMap<>();
   private final Map<BlockPos, Integer> tntDefers = new HashMap<>();
   private final Map<Long, Integer> tntRiskAt = new HashMap<>();
   private final Map<Long, String> tntRiskVal = new HashMap<>();
   private final Map<BlockPos, Boolean> tntHoldCache = new HashMap<>();
   private String tntVetoWhy = "";

   /** quét TNT + cảm biến sculk quanh người (49×33×49 ô), mỗi tick 3 lớp Y để không giật; xong 1 lượt thì thay danh sách */
   private void tntScanTick() {
      if (!this.running || this.mc.world == null || this.mc.player == null) {
         return;
      }

      if (this.sculkA == null) {
         this.sculkA = (Block)Registries.BLOCK.get(Identifier.tryParse("minecraft:sculk_sensor"));
         this.sculkB = (Block)Registries.BLOCK.get(Identifier.tryParse("minecraft:calibrated_sculk_sensor"));
      }

      if (this.tntScanDy > 16) {
         if (this.ticks - this.tntScanDone < 10) {
            return;
         }

         this.tntScanCenter = this.mc.player.getBlockPos().toImmutable();
         this.tntScanDy = -16;
         this.tntScanTnt.clear();
         this.tntScanSensors.clear();
      }

      int var1 = this.mc.world.getBottomY();
      int var2 = this.mc.world.getTopYInclusive();

      for (int var3 = 0; var3 < 3 && this.tntScanDy <= 16; var3++) {
         int var4 = this.tntScanCenter.getY() + this.tntScanDy++;
         if (var4 >= var1 && var4 <= var2) {
            for (int var5 = -24; var5 <= 24; var5++) {
               for (int var6 = -24; var6 <= 24; var6++) {
                  BlockPos var7 = new BlockPos(this.tntScanCenter.getX() + var5, var4, this.tntScanCenter.getZ() + var6);
                  BlockState var8 = this.mc.world.getBlockState(var7);
                  if (!var8.isAir()) {
                     Block var9 = var8.getBlock();
                     if (var9 == Blocks.TNT) {
                        if (this.tntScanTnt.size() < 4096) {
                           this.tntScanTnt.add(var7);
                        }
                     } else if ((var9 == this.sculkA || var9 == this.sculkB) && this.tntScanSensors.size() < 256) {
                        this.tntScanSensors.add(var7);
                     }
                  }
               }
            }
         }
      }

      if (this.tntScanDy > 16) {
         boolean var10 = TntGuard.TNT.isEmpty();
         TntGuard.TNT.clear();
         TntGuard.TNT.addAll(this.tntScanTnt);
         TntGuard.SENSORS.clear();
         TntGuard.SENSORS.addAll(this.tntScanSensors);
         this.tntScanDone = this.ticks;
         if (var10 && !TntGuard.TNT.isEmpty() && this.ticks - this.tntSaidAt > 6000) {
            this.tntSaidAt = this.ticks;
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Có {} khối TNT quanh đây (vd. @ {}) → mọi việc đặt / đập / chỉnh / đổ nước gần TNT đều kiểm tra trước xem có làm TNT nổ không",
                  this.tntScanTnt.size(),
                  this.tntScanTnt.get(0).toShortString()
               );
         }
      }
   }

   /** kiểm tra (có nhớ 10 tick) 1 việc tại ô var1: var2 = trạng thái sau khi làm (null = đập), var3 = chỉ chỉnh khối có sẵn */
   private String tntRisk(BlockPos var1, BlockState var2, boolean var3) {
      if (this.mc.world == null) {
         return null;
      } else {
         long var4 = var1.asLong() * 31L + (long)(var2 == null ? 7 : var2.hashCode()) * 2L + (var3 ? 1L : 0L);
         Integer var6 = this.tntRiskAt.get(var4);
         if (var6 != null && this.ticks - var6 < 10) {
            String var7 = this.tntRiskVal.get(var4);
            return var7 != null && !var7.isEmpty() ? var7 : null;
         } else {
            if (this.tntRiskAt.size() > 4000) {
               this.tntRiskAt.clear();
               this.tntRiskVal.clear();
            }

            String var8;
            try {
               var8 = TntGuard.check(this.mc.world, var1, var2, var3);
            } catch (Throwable var10) {
               // không tính được thì coi là nguy hiểm (chỉ xảy ra khi đã có TNT quanh đây)
               var8 = "không kiểm tra được (" + var10.getClass().getSimpleName() + ") — coi như nguy hiểm";
               AutoBuildMod.LOGGER.warn("[AutoBuild] Lỗi kiểm tra an toàn TNT @ {}: {}", var1.toShortString(), var10.toString());
            }

            this.tntRiskAt.put(var4, this.ticks);
            this.tntRiskVal.put(var4, var8 == null ? "" : var8);
            return var8;
         }
      }
   }

   private String tntBreakRisk(BlockPos var1) {
      return this.tntRisk(var1, null, false);
   }

   /** 3.0.104: đặt / chỉnh / đổ nước ở khối var1 có thể làm TNT nổ → không làm, hoãn lại (true = đã xử lý) */
   private boolean tntUnsafe(SchematicPlan.Target var1) {
      if (var1 == null || this.mc.world == null || this.mc.player == null) {
         return false;
      } else {
         BlockPos var2 = var1.pos();
         BlockState var3 = this.mc.world.getBlockState(var2);
         boolean var4 = StateMatch.configurable(var3, var1.state());
         String var5 = this.tntRisk(var2, var1.state(), var4);
         if (var5 == null && var1.state().isOf(Blocks.WATER) && TntGuard.near(this.mc.world, var2, 24)) {
            // nước sẽ chảy tới các ô này — observer đang nhìn các ô đó sẽ phát xung
            this.waterSpillCached(var2);

            try {
               var5 = TntGuard.flood(this.mc.world, this.spillCells);
            } catch (Throwable var7) {
               var5 = "không kiểm tra được đường nước chảy — coi như nguy hiểm";
            }
         }

         if (var5 == null) {
            return false;
         } else {
            this.tntRefuse(var1, var5, var4);
            return true;
         }
      }
   }

   /** không làm việc var1 vì TNT: hoãn (tăng dần), 4 lần vẫn nguy hiểm → để lại cho người chơi làm tay, báo trong chat */
   private void tntRefuseRisk(SchematicPlan.Target var1, String var2, boolean var3) {
      BlockPos var4 = var1.pos().toImmutable();
      String var5 = var1.state().getBlock().getName().getString();
      String var6 = var3 ? "chỉnh " : "đặt ";
      boolean var7 = var1 == this.temporary || var1 == this.supportTarget || this.plan == null || this.plan.at(var4) == null;
      int var8 = this.tntDefers.merge(var4, 1, Integer::sum);
      this.status = "An toàn TNT: chưa " + var6 + var5 + " @ " + var4.toShortString() + " — " + var2;
      if (this.ticks - this.tntSaid.getOrDefault(var4, -100000) > 600) {
         this.tntSaid.put(var4, this.ticks);
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] AN TOÀN TNT: không {}{} @ {} vì có thể làm TNT nổ: {} → để lại, làm khối khác (lần {})",
               new Object[]{var6, var5, var4.toShortString(), var2, var8}
            );
      }

      this.release();
      this.aim = null;
      this.anglesFor = null;
      this.pending = null;
      if (var7) {
         // khối kê / khối tạm của mod: bỏ; khối chính đang cần nó thì hoãn
         this.temporary = null;
         this.supportTarget = null;
         this.supportQueue.clear();
         this.pillar = false;
         this.pit = null;
         this.spoof = null;
         this.spoofHold = 0;
         if (this.target != null && this.target != var1) {
            this.deferred.put(this.target.pos().toImmutable(), this.ticks + 600);
            this.queue.remove(this.target);
         }
      } else if (var8 >= 4) {
         this.skipped.add(var4);
         this.skipReason.put(var4, "Gần TNT: " + var2 + " — không tự " + var6.trim() + ", hãy làm tay khi chắc an toàn (nhấn K để thử lại)");
         this.say("CẢNH BÁO TNT: không " + var6 + var5 + " @ " + var4.toShortString() + " vì có thể làm TNT nổ (" + var2 + ") → để bạn làm tay.");
      } else {
         this.deferred.put(var4, this.ticks + 600 * var8);
      }

      this.queue.remove(var1);
      this.failures.remove(var4);
      this.target = null;
      this.beginScan();
   }

   /** lưới an toàn cuối cùng trước MỌI cú bấm chuột phải của mod (đặt khối, đổ nước, mở rương/crafter, chỉnh repeater…) */
   private boolean tntVeto(BlockHitResult var1) {
      this.tntVetoWhy = "";
      if (this.mc.world != null && this.mc.player != null && var1 != null && var1.getType() == Type.BLOCK) {
         BlockPos var2 = var1.getBlockPos();
         ItemStack var3 = this.mc.player.getMainHandStack();
         String var4 = Registries.ITEM.getId(var3.getItem()).toString();
         BlockState var5 = this.mc.world.getBlockState(var2);
         String var6 = null;
         BlockPos var7 = var2;
         if (var4.equals("minecraft:flint_and_steel") || var4.equals("minecraft:fire_charge")) {
            if (TntGuard.near(this.mc.world, var2, 24)) {
               var6 = "đang cầm " + var4.replace("minecraft:", "") + " gần TNT";
            }
         } else {
            if (!this.mc.player.isSneaking() && Interaction.interactable(var5)) {
               // bấm vào khối có chức năng (repeater, cửa, rương…) = chỉnh / mở nó
               var6 = this.tntRisk(var2, var5, true);
            }

            if (var6 == null) {
               var7 = var5.isReplaceable() ? var2 : var2.offset(var1.getSide());
               BlockState var8 = null;
               if (var3.getItem() instanceof BlockItem var9) {
                  SchematicPlan.Target var10 = this.plan == null ? null : this.plan.at(var7);
                  var8 = var10 != null && Materials.item(this.mc.world.getBlockState(var7), var10.state()) == var3.getItem()
                     ? var10.state()
                     : var9.getBlock().getDefaultState();
               } else if (var3.isOf(Items.WATER_BUCKET)) {
                  var8 = Blocks.WATER.getDefaultState();
               } else if (var3.isOf(Items.LAVA_BUCKET)) {
                  var8 = Blocks.LAVA.getDefaultState();
               }

               if (var8 != null) {
                  var6 = this.tntRisk(var7, var8, false);
               }
            }
         }

         if (var6 == null) {
            return false;
         } else {
            this.tntVetoWhy = var6;
            this.status = "An toàn TNT: chặn cú bấm @ " + var7.toShortString() + " — " + var6;
            if (this.ticks - this.tntSaid.getOrDefault(var7, -100000) > 200) {
               this.tntSaid.put(var7.toImmutable(), this.ticks);
               AutoBuildMod.LOGGER.info("[AutoBuild] AN TOÀN TNT: chặn cú bấm chuột phải (ô {}, tay cầm {}): {}", new Object[]{var7.toShortString(), var4, var6});
            }

            return true;
         }
      } else {
         return false;
      }
   }

   /** đang đập khối kê / khối sai / thu dọn mà việc đập có thể làm TNT nổ → bỏ khối đó, làm việc khác (true = đã xử lý) */
   private boolean tntAbandonMining() {
      String var1 = this.tntBreakRisk(this.mining);
      if (var1 == null) {
         return false;
      } else {
         BlockPos var2 = this.mining.toImmutable();
         String var3 = this.mc.world.getBlockState(var2).getBlock().getName().getString();
         this.stopAttack();
         if (this.mc.interactionManager.isBreakingBlock()) {
            this.mc.interactionManager.cancelBlockBreaking();
         }

         if (this.ticks - this.tntSaid.getOrDefault(var2, -100000) > 600) {
            this.tntSaid.put(var2, this.ticks);
            this.say(
               "CẢNH BÁO TNT: không đập "
                  + var3
                  + " @ "
                  + var2.toShortString()
                  + " vì có thể làm TNT nổ ("
                  + var1
                  + ") → để lại"
                  + (this.sweeping ? " (khối kê của mod — dọn tay khi an toàn)" : "")
            );
         }

         if (this.sweeping) {
            this.sweepSkip.add(var2);
            this.sweepNow.remove(var2);
            this.sweepFailUntil.put(var2, this.ticks + 72000);
            this.endSideMining();
         } else if (this.repairing) {
            this.repairWrong.remove(var2);
            if (this.plan != null && this.plan.at(var2) != null) {
               this.skipped.add(var2);
               this.skipReason.put(var2, "Gần TNT: khối sai ở đây, đập có thể làm TNT nổ (" + var1 + ") — hãy sửa tay");
            }

            this.endSideMining();
         } else {
            this.cleanupDeferred.add(var2);
            this.mining = null;
            this.setStage(BuildController.Stage.CLEANUP);
         }

         return true;
      }
   }

   /** TNT trong bản vẽ nằm cạnh mạch đỏ / máy (≤ 4 ô) → đặt sau cùng của tầng: mạch xây xong, đứng yên rồi mới có TNT */
   boolean tntHold(SchematicPlan.Target var1) {
      if (this.plan != null && var1 != null && var1.state().isOf(Blocks.TNT)) {
         Boolean var2 = this.tntHoldCache.get(var1.pos());
         if (var2 != null) {
            return var2;
         } else {
            boolean var3 = false;
            BlockPos var4 = var1.pos();

            for (int var5 = -4; var5 <= 4 && !var3; var5++) {
               for (int var6 = -4; var6 <= 4 && !var3; var6++) {
                  for (int var7 = -4; var7 <= 4 && !var3; var7++) {
                     SchematicPlan.Target var8 = this.plan.at(var4.add(var5, var6, var7));
                     if (var8 != null && TntGuard.redstoneish(var8.state())) {
                        var3 = true;
                     }
                  }
               }
            }

            this.tntHoldCache.put(var4.toImmutable(), var3);
            return var3;
         }
      } else {
         return false;
      }
   }

   private boolean retryFluidLayer(int var1) {
      if (this.plan == null || this.mc.world == null) {
         return false;
      } else if (this.fluidRetries.getOrDefault(var1, 0) >= 4) {
         return false;
      } else {
         for (SchematicPlan.Target var3 : this.plan.targets) {
            if (var3.pos().getY() == var1
               && FluidOrder.phase(var3.state()) > 0
               && this.skipped.contains(var3.pos())
               && !this.skipReason.containsKey(var3.pos())) {
               this.fluidRetries.merge(var1, 1, Integer::sum);
               AutoBuildMod.LOGGER
                  .info("[AutoBuild] Tầng Y={} còn {} chưa đổ (bị bỏ qua tạm) → quay lại làm cho xong trước khi lên tầng trên", var1, var3.state().getBlock().getName().getString());
               return true;
            }
         }

         return false;
      }
   }

   private boolean tryCollectLayerBucket(BuildController.Scan var1) {
      if (this.config.buyBuckets && !this.cleanupMode && this.plan != null && this.mc.world != null && this.mc.player != null) {
         int var2 = this.config.layerMode && this.currentLayer != null ? this.currentLayer : 0;
         LayerPlanner.Stat var3 = var1.layers.get(var2);
         if (var3 != null && (var1.solidRemaining.getOrDefault(var2, 0) <= 0 || var3.ready <= 0)) {
            SchematicPlan.Target var4 = null;
            double var5 = Double.MAX_VALUE;

            for (SchematicPlan.Target var8 : this.plan.targets) {
               BlockPos var9 = var8.pos();
               if (FluidOrder.phase(var8.state()) == this.buildFluidPhase
                  && this.config.inYRange(var9.getY())
                  && (!this.config.layerMode || Objects.equals(this.currentLayer, var9.getY()))
                  && !this.skipped.contains(var9)
                  && this.deferred.getOrDefault(var9, 0) <= this.ticks
                  && this.mc.world.isChunkLoaded(var9)) {
                  BlockState var10 = this.mc.world.getBlockState(var9);
                  if (!this.done(var10, var8.state()) && StateMatch.canAdd(var10, var8.state()) && BucketRules.placeableBucket(var10, var8.state())) {
                     Item var11 = Materials.item(var10, var8.state());
                     if (BucketRules.shopBucket(var11) && InventoryAccess.count(this.mc, var11) <= 0 && this.missing.getOrDefault(var11, 0) <= this.ticks) {
                        double var12 = this.mc.player.squaredDistanceTo(Vec3d.ofCenter(var9));
                        if (var12 < var5) {
                           var4 = var8;
                           var5 = var12;
                        }
                     }
                  }
               }
            }

            if (var4 == null) {
               return false;
            } else {
               this.target = var4;
               this.targetTicks = 0;
               this.aim = null;
               this.buyBucket(Materials.item(this.mc.world.getBlockState(var4.pos()), var4.state()), BuildController.Stage.EQUIP);
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean shouldDeferBridge(int var1) {
      if (var1 > 0 && var1 <= this.config.maxBridgeBlocks && this.target != null) {
         if (this.approachBudget == null) {
            this.approachBudget = new ApproachBudget();
         }

         return this.approachBudget
            .defer(this.mc.world, this.plan, this.currentLayer, this.buildFluidPhase, this.placedCount, this.mc.player.getPos(), this.target.pos());
      } else {
         return false;
      }
   }

   private void rememberReachable() {
      this.reachableTick = this.ticks;
      this.reachableWorld = this.mc.world;
      this.reachablePlan = this.plan;
      this.reachableLayer = this.currentLayer;
      this.reachablePhase = this.buildFluidPhase;
      this.reachablePlaced = this.placedCount;
      this.nearCache.clear();
   }

   private boolean reachableValid() {
      int var1 = this.ticks - this.reachableTick;
      if (this.reachable != null
         && this.mc.player != null
         && this.mc.world != null
         && this.reachableWorld == this.mc.world
         && this.reachablePlan == this.plan
         && Objects.equals(this.reachableLayer, this.currentLayer)
         && this.reachablePhase == this.buildFluidPhase
         && this.reachablePlaced == this.placedCount
         && var1 >= 0
         && var1 <= 40) {
         for (PathSearch.Cell var3 : this.terrain == null ? List.of(Navigation.cell(this.mc.player.getBlockPos())) : this.terrain.startCells()) {
            if (this.reachable.contains(var3)) {
               return true;
            }
         }
      }

      this.reachable = null;
      this.nearCache.clear();
      return false;
   }

   private void collectGoals() {
      long var1 = System.nanoTime() + 8000000L;
      if (this.tickDeadline > 0L) {
         var1 = Math.min(var1, this.tickDeadline);
      }

      this.heavyLabel = "tìm chỗ đứng";
      int var3 = this.approachOnly ? 8 : (this.scaffoldPass ? this.walkGoalCount + 3 : 6);
      int var4 = 0;
      this.terrain.beginBatch();

      try {
         while (this.goals.size() < var3 && var4++ < 96 && System.nanoTime() < var1) {
            if (this.goalIndex >= this.ranked.size()) {
               if (this.scaffoldPass || !this.goals.isEmpty() || this.cleanupMode) {
                  break;
               }

               this.scaffoldPass = true;
               this.goalIndex = 0;
               if (this.ranked.isEmpty()) {
                  break;
               }
            }

            PathSearch.Cell var5 = this.ranked.get(this.goalIndex++).cell();
            if (!this.cleanupMode
               && this.current() != null
               && this.badStands.getOrDefault(this.current().pos(), Set.of()).contains(var5)) {
               continue;
            }

            boolean var6 = this.terrain.standable(var5);
            if (this.scaffoldPass != var6 && (var6 || this.terrain.clear(var5) && this.terrain.canScaffoldAt(Navigation.pos(var5).down()))) {
               if (!var6 && !this.cleanupMode) {
                  SchematicPlan.Target var7 = this.plan == null ? null : this.plan.at(Navigation.pos(var5));
                  if (var7 != null && !this.done(this.mc.world.getBlockState(Navigation.pos(var5)), var7.state())) {
                     continue;
                  }
               }

               if (!this.cleanupMode || this.safeMiningStand(var5, this.mining)) {
                  if (this.approachOnly) {
                     this.goals.add(var5);
                  } else if (this.cleanupMode) {
                     Vec3d var22 = Navigation.eye(var5);
                     Vec3d var24 = Vec3d.ofCenter(this.mining);
                     if (!(var22.squaredDistanceTo(var24) > this.reach() * this.reach())) {
                        BlockHitResult var25 = this.mc.world.raycast(new RaycastContext(var22, var24, ShapeType.OUTLINE, FluidHandling.NONE, this.mc.player));
                        if (var25.getType() == Type.BLOCK && var25.getBlockPos().equals(this.mining)) {
                           this.goals.add(var5);
                        }
                     }
                  } else if (this.repairGoal != null) {
                     Vec3d var21 = Navigation.eye(var5);
                     if (this.safeMiningStand(var5, this.repairGoal)
                        && var21.squaredDistanceTo(Vec3d.ofCenter(this.repairGoal)) <= (this.reach() - 0.3) * (this.reach() - 0.3)
                        && this.rayHits(var21, Vec3d.ofCenter(this.repairGoal), this.repairGoal)) {
                        this.goals.add(var5);
                     }
                  } else if (this.floatingGoal) {
                     BlockPos var20 = this.target.pos();
                     int var23 = Math.abs(var5.x() - var20.getX()) + Math.abs(var5.z() - var20.getZ());
                     if (var5.y() == var20.getY() && var23 <= 1) {
                        this.goals.add(var5);
                     }
                  } else {
                     Vec3d var19 = Navigation.feet(var5);
                     SchematicPlan.Target var8 = this.current();
                     this.gStand++;
                     PlacementSolver.Aim var9 = PlacementSolver.solve(this.mc, var19.add(0.0, 1.62, 0.0), var8, this.reach(), true, this.seed());
                     boolean var10 = var9 != null
                        && PlacementSolver.robust(this.mc, PlacementSolver.poseEye(var19.add(0.0, 1.62, 0.0), var9.sneak()), var9, this.reach());
                     if (var9 == null) {
                        this.gNoAim++;
                        double var11 = var19.add(0.0, 1.62, 0.0).distanceTo(Vec3d.ofCenter(var8.pos()));
                        if (var11 > this.reach() + 0.9) {
                           this.gFar++;
                        } else {
                           this.gAngle++;
                        }
                     } else if (!var10) {
                        this.gNotRobust++;
                     }

                     if (var10) {
                        this.goals.add(var5);
                     } else if (this.spoofGoals
                        && var8 == this.target
                        && PlacementSolver.solveSpoof(this.mc, var19.add(0.0, 1.62, 0.0), var8, this.reach(), this.seed(), null) != null) {
                        this.goals.add(var5);
                     } else if (var5.y() >= var8.pos().getY() && !this.leanFailed.contains(var8.pos())) {
                        Vec3d var26 = edgeEye(var19, var8.pos());
                        if (PlacementSolver.solve(this.mc, var26, var8, this.reach(), true, this.seed(), true) != null
                           || this.spoofGoals
                              && var8 == this.target
                              && PlacementSolver.solveSpoof(this.mc, var26, var8, this.reach(), this.seed(), null) != null) {
                           this.goals.add(var5);
                        } else {
                           this.gNoEdge++;
                        }
                     }
                  }
               }
            }
         }
      } finally {
         this.terrain.endBatch();
      }

      if (this.goalIndex >= this.ranked.size() && (this.scaffoldPass || !this.goals.isEmpty() || this.cleanupMode) || this.goals.size() >= var3) {
         if (this.goals.isEmpty()
            && !this.cleanupMode
            && this.current() == this.target
            && this.target != null
            && !this.spoofFor(this.target)
            && !this.hasLowerStand(this.target.pos())
            && PlacementSolver.needsLookUp(this.mc, this.target)) {
            BuildController.PitPlan var16 = this.planPit(this.target);
            if (var16 == null) {
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] {} @ {} cần nhìn lên nhưng chưa có khối bên cạnh để click / chỗ đào hố → để sau",
                     this.target.state(),
                     this.target.pos().toShortString()
                  );
               this.deferred.put(this.target.pos(), this.ticks + 200);
               if (this.attempt(this.target)) {
                  this.skipReason.remove(this.target.pos());
               } else {
                  this.skipReason.put(this.target.pos(), "Cần nhìn lên: không có chỗ đào hố / khối bên cạnh (đặt tay)");
               }

               this.nextTarget(true);
               return;
            }

            this.pit = var16;
            this.goals.add(Navigation.cell(var16.column()));
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] {} @ {} → đào hố tại {} để nhìn lên mà đặt",
                  new Object[]{this.target.state().getBlock().getName().getString(), this.target.pos().toShortString(), var16.column().toShortString()}
               );
         }

         if (this.goals.isEmpty()) {
            this.navigationFailed(
               "Chưa có chỗ đứng/mặt đặt đúng hướng tại "
                  + this.goalLabel()
                  + " (xét "
                  + this.ranked.size()
                  + " ô, đứng được "
                  + this.gStand
                  + ", xa quá tầm "
                  + this.gFar
                  + ", trong tầm nhưng sai hướng "
                  + this.gAngle
                  + ", góc không chắc "
                  + this.gNotRobust
                  + ", ngồi ra mép cũng không được "
                  + this.gNoEdge
                  + ")"
            );
            this.hintSpoof();
         } else {
            List var17 = this.terrain.startCells();
            boolean var18 = !this.scaffoldPass && !this.cleanupMode;
            this.terrain.walkOnly = var18;
            if (var18) {
               this.walkGoalCount = this.goals.size();
            }

            this.terrain.bound(var17, this.goals, var18 ? 16 : 10);
            this.search = new PathSearch(var17, this.goals, this.terrain, var18 ? 8000 : Math.min(this.config.pathNodeLimit, 6000));
            this.setStage(BuildController.Stage.SEARCH);
         }
      }
   }

   private int noPathSaidAt = -1000;
   private PathSearch.Cell noPathSaidCell;
   private PathSearch.Cell deadStart;
   private int deadStartAt = -100000;
   private int deadStartCount;
   private Vec3d escGoal;
   private int escUntil;
   private int escStartTick;
   private int escTries;
   private int escStuckTicks;
   private Vec3d escLastPos;
   private int stuckSeenAt = -100000;

   /**
    * 3.0.84: đang đứng ở ô mà bộ tìm đường coi là "không đứng được, 0 lối ra" (vd. trong dòng nước, lọt khe, trên khối lạ)
    * → tự đi thẳng ra ô đứng được gần nhất (bán kính 5), nhảy khi bị chặn/đang trong nước, rồi mới tìm đường lại.
    */
   private void beginStuckEscape(PathSearch.Cell var1) {
      this.stuckSeenAt = this.ticks;
      Navigation var2 = new Navigation(this.mc, this.config, this.plan, false, this.currentLayer);
      PathSearch.Cell var3 = null;
      double var4 = Double.MAX_VALUE;

      for (int var6 = 1; var6 <= 5 && var3 == null; var6++) {
         for (int var7 = -var6; var7 <= var6; var7++) {
            for (int var8 = -var6; var8 <= var6; var8++) {
               if (Math.max(Math.abs(var7), Math.abs(var8)) == var6) {
                  for (int var9 : new int[]{0, 1, -1, 2}) {
                     PathSearch.Cell var10 = var1.add(var7, var9, var8);
                     if (var2.standable(var10) && !Navigation.waterish(this.mc.world.getBlockState(Navigation.pos(var10)))) {
                        double var11 = (double)(var7 * var7 + var8 * var8) + (double)Math.abs(var9) * 0.5;
                        if (var11 < var4) {
                           var4 = var11;
                           var3 = var10;
                        }
                     }
                  }
               }
            }
         }
      }

      this.release();
      this.escTries++;
      this.escStartTick = this.ticks;
      this.escUntil = this.ticks + 120;
      this.escStuckTicks = 0;
      this.escLastPos = this.mc.player.getPos();
      this.escGoal = var3 == null ? null : Navigation.feet(var3);
      AutoBuildMod.LOGGER
         .info(
            "[AutoBuild] Đứng ở ô không đi được {} (trong nước: {}) → tự bước ra {} rồi mới tìm đường lại (lần {})",
            new Object[]{
               var1,
               Navigation.waterish(this.mc.world.getBlockState(this.mc.player.getBlockPos())),
               var3 == null ? "hướng thoáng nhất (không thấy ô khô gần)" : var3.toString(),
               this.escTries
            }
         );
      this.status = "Bị kẹt ở ô không đi được → tự bước ra";
   }

   private BlockPos wsPos;
   private BlockPos wsStand;
   private List<BlockPos> wsPath = List.of();
   private int wsPathIdx;
   private int wsStage;
   private int wsTicks;
   private int wsLastAt = -1000;
   private final HashMap<BlockPos, Integer> wsBad = new HashMap<>();

   /** ô có vật cản va chạm (không đi xuyên / không ngóc đầu lên được) */
   private boolean blocksBody(BlockPos var1) {
      return !this.mc.world.getBlockState(var1).getCollisionShape(this.mc.world, var1).isEmpty();
   }

   /** ô trong công trình là khối đặc (không phải không khí/nước) → khối kê tạm không được đặt vào đó */
   private boolean planSolid(BlockPos var1) {
      if (this.plan == null) {
         return false;
      } else {
         SchematicPlan.Target var2 = this.plan.at(var1);
         return var2 != null && !var2.state().isAir() && !var2.state().isReplaceable();
      }
   }

   /**
    * 3.0.87: lực đẩy của nước chảy lên người (vanilla Entity.updateMovementInFluid: trung bình hướng chảy các ô nước chạm thân,
    * × 0,014 mỗi tick; đi bộ trong nước chỉ tăng tốc 0,02 mỗi tick) → trả về hướng chảy trung bình (chưa nhân 0,014), null nếu không có.
    */
   private Vec3d flowAt() {
      if (this.mc.player == null || this.mc.world == null) {
         return null;
      } else {
         Box var1 = this.mc.player.getBoundingBox().contract(0.001);
         int var2 = (int)Math.floor(var1.minX);
         int var3 = (int)Math.ceil(var1.maxX);
         int var4 = (int)Math.floor(var1.minY);
         int var5 = (int)Math.ceil(var1.maxY);
         int var6 = (int)Math.floor(var1.minZ);
         int var7 = (int)Math.ceil(var1.maxZ);
         double var8 = 0.0;
         double var10 = 0.0;
         int var12 = 0;

         for (int var13 = var2; var13 < var3; var13++) {
            for (int var14 = var4; var14 < var5; var14++) {
               for (int var15 = var6; var15 < var7; var15++) {
                  BlockPos var16 = new BlockPos(var13, var14, var15);
                  BlockState var17 = this.mc.world.getBlockState(var16);
                  if (Navigation.waterish(var17)) {
                     FluidState var18 = var17.getFluidState();
                     double var19 = (double)var14 + (double)var18.getHeight(this.mc.world, var16) - var1.minY;
                     if (var19 >= 0.0) {
                        Vec3d var21 = var18.getVelocity(this.mc.world, var16);
                        if (var21 != null) {
                           double var22 = var19 < 0.4 ? var19 : 1.0;
                           var8 += var21.x * var22;
                           var10 += var21.z * var22;
                        }

                        var12++;
                     }
                  }
               }
            }
         }

         if (var12 == 0 || var8 * var8 + var10 * var10 < 1.0E-6) {
            return null;
         } else {
            return new Vec3d(var8 / (double)var12, 0.0, var10 / (double)var12);
         }
      }
   }

   /**
    * 3.0.87: hướng quay mặt (bấm W) để đi THẲNG tới đích khi đang bị nước chảy đẩy: nghiêng ngược dòng đủ để triệt phần đẩy ngang
    * (đi bộ trong nước 0,02/tick, dòng đẩy 0,014/tick). Không có dòng chảy thì là hướng thẳng tới đích.
    */
   private float flowYaw(double var1, double var3) {
      float var5 = MotionMath.yaw(var1, var3);
      Vec3d var6 = this.flowAt();
      double var7 = Math.sqrt(var1 * var1 + var3 * var3);
      if (var6 != null && !(var7 < 1.0E-4)) {
         double var9 = var1 / var7;
         double var11 = var3 / var7;
         double var13 = var6.x * 0.014;
         double var15 = var6.z * 0.014;
         double var17 = var13 * var9 + var15 * var11;
         double var19 = var13 - var17 * var9;
         double var21 = var15 - var17 * var11;
         double var23 = Math.sqrt(var19 * var19 + var21 * var21);
         if (var23 < 1.0E-5) {
            return var5;
         } else {
            double var25 = Math.min(0.95, var23 / 0.02);
            double var27 = Math.sqrt(1.0 - var25 * var25);
            return MotionMath.yaw(var27 * var9 - var25 * var19 / var23, var27 * var11 - var25 * var21 / var23);
         }
      } else {
         return var5;
      }
   }

   /** 3.0.87: đứng yên tại một điểm trong nước chảy (vừa ngắm đặt khối vừa giữ chỗ): bấm W/A/S/D theo hướng nhìn hiện tại để bù dòng. */
   private void holdAt(double var1, double var3) {
      double var5 = var1 - this.mc.player.getX();
      double var7 = var3 - this.mc.player.getZ();
      double var9 = Math.sqrt(var5 * var5 + var7 * var7);
      double var11 = 0.0;
      double var13 = 0.0;
      if (var9 > 0.08) {
         double var15 = Math.min(1.0, var9 / 0.3);
         var11 = var5 / var9 * var15;
         var13 = var7 / var9 * var15;
      }

      Vec3d var17 = this.flowAt();
      if (var17 != null) {
         var11 -= var17.x * 0.7;
         var13 -= var17.z * 0.7;
      }

      double var18 = Math.sqrt(var11 * var11 + var13 * var13);
      if (!(var18 < 0.15)) {
         double var20 = Math.toRadians((double)this.mc.player.getYaw());
         double var22 = var11 * -Math.sin(var20) + var13 * Math.cos(var20);
         double var24 = var11 * Math.cos(var20) + var13 * Math.sin(var20);
         Input.key(this.mc.options.forwardKey, var22 > 0.38 * var18);
         Input.key(this.mc.options.backKey, var22 < -0.38 * var18);
         Input.key(this.mc.options.leftKey, var24 > 0.38 * var18);
         Input.key(this.mc.options.rightKey, var24 < -0.38 * var18);
      }
   }

   /**
    * 3.0.85/3.0.87: đang ở đáy bể/hố nước, thành bao quanh → đặt 1 bậc kê vào ô nước bên cạnh rồi trèo lên, sau đó kê cột ra.
    * 3.0.87: chọn CHỖ ĐỨNG + HƯỚNG đặt bậc sao cho trèo lên được: trên đầu chỗ đứng trống 2 ô (ngóc đầu lên được), trên bậc trống
    * 2 ô, ô thứ 3 trên bậc cũng trống và không phải ô của công trình (để kê cột tiếp). Bản 3.0.85 chỉ xét ô cạnh chỗ đang đứng nên
    * đặt bậc ngay dưới lá/khối tạm tầng trên → đầu cụng, đứng im (log 14:44:48–14:45:28).
    */
   private boolean beginWaterStep() {
      Item var1 = this.scaffoldItem();
      if (!this.config.scaffold || var1 == Items.AIR || !(var1 instanceof BlockItem var2)) {
         return false;
      } else if (InventoryAccess.count(this.mc, var1) <= 0) {
         this.scaffoldWantAt = this.ticks;
         return false;
      } else {
         BlockState var3 = var2.getBlock().getDefaultState();
         BlockPos var4 = this.mc.player.getBlockPos().toImmutable();
         this.wsBad.values().removeIf(var1x -> var1x < this.ticks);
         // các ô đi được trong bể (cùng độ cao chân, thân lọt qua) — BFS tối đa 80 ô, bán kính 6
         HashMap<BlockPos, BlockPos> var5 = new HashMap<>();
         HashMap<BlockPos, Integer> var6 = new HashMap<>();
         ArrayDeque<BlockPos> var7 = new ArrayDeque<>();
         var5.put(var4, null);
         var6.put(var4, 0);
         var7.add(var4);

         while (!var7.isEmpty() && var6.size() < 80) {
            BlockPos var8 = var7.poll();

            for (Direction var10 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
               BlockPos var11 = var8.offset(var10).toImmutable();
               if (!var6.containsKey(var11)
                  && Math.abs(var11.getX() - var4.getX()) <= 6
                  && Math.abs(var11.getZ() - var4.getZ()) <= 6
                  && this.mc.world.isChunkLoaded(var11)
                  && !this.blocksBody(var11)
                  && !this.blocksBody(var11.up())
                  && !Navigation.hazardDry(this.mc.world.getBlockState(var11))
                  && this.blocksBody(var11.down())) {
                  var5.put(var11, var8);
                  var6.put(var11, var6.get(var8) + 1);
                  var7.add(var11);
               }
            }
         }

         // chấm điểm (chỗ đứng s, ô bậc n)
         ArrayList<Object[]> var12 = new ArrayList<>();

         for (Entry<BlockPos, Integer> var14 : var6.entrySet()) {
            BlockPos var15 = var14.getKey();
            if (!this.blocksBody(var15.up().up())) {
               for (Direction var17 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                  BlockPos var18 = var15.offset(var17).toImmutable();
                  BlockState var19 = this.mc.world.getBlockState(var18);
                  if (var19.isReplaceable()
                     && !var19.isOf(Blocks.LAVA)
                     && !this.wsBad.containsKey(var18)
                     && !this.blocksBody(var18.up())
                     && !this.blocksBody(var18.up().up())
                     && !Navigation.hazardDry(this.mc.world.getBlockState(var18.up()))
                     && !Navigation.hazardDry(this.mc.world.getBlockState(var18.up().up()))) {
                     int var20 = var14.getValue() * 3
                        + (Navigation.waterish(this.mc.world.getBlockState(var18.up())) ? 100 : 0)
                        + (this.planSolid(var18.up().up()) ? 15 : 0)
                        + (this.blocksBody(var18.up().up().up()) ? 8 : 0)
                        + (this.planSolid(var18.up().up().up()) ? 4 : 0)
                        + (this.planSolid(var18) ? 20 : 0)
                        + (var18.equals(var4) ? 2 : 0);
                     var12.add(new Object[]{var20, var15, var18});
                  }
               }
            }
         }

         var12.sort(Comparator.comparingInt(var0 -> (Integer)var0[0]));
         BlockPos var21 = null;
         BlockPos var22 = null;
         int var23 = 0;

         for (Object[] var25 : var12) {
            if (++var23 > 16) {
               break;
            }

            BlockPos var26 = (BlockPos)var25[1];
            BlockPos var27 = (BlockPos)var25[2];
            Vec3d var28 = new Vec3d((double)var26.getX() + 0.5, (double)var26.getY() + 1.62, (double)var26.getZ() + 0.5);
            if (PlacementSolver.solve(this.mc, var28, new SchematicPlan.Target(var27, var3), this.reach(), true, var27.asLong() * 31L) != null) {
               var21 = var26;
               var22 = var27;
               break;
            }
         }

         if (var22 == null) {
            AutoBuildMod.LOGGER
               .info("[AutoBuild] Đang ở đáy bể nước {} mà không có chỗ nào trên đầu trống để đặt bậc trèo lên ({} ô xét) → thử cách khác", var4.toShortString(), var6.size());
            return false;
         } else {
            ArrayList<BlockPos> var29 = new ArrayList<>();

            for (BlockPos var30 = var21; var30 != null; var30 = var5.get(var30)) {
               var29.add(0, var30);
            }

            this.release();
            this.wsPos = var22;
            this.wsStand = var21;
            this.wsPath = var29;
            this.wsPathIdx = 0;
            this.wsStage = var21.equals(var4) ? 0 : -1;
            this.wsTicks = 0;
            this.escUntil = 0;
            this.stuckSeenAt = this.ticks;
            Vec3d var31 = this.flowAt();
            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Rơi xuống bể nước ở {} → {}đặt bậc kê tại {} (trên bậc trống {} ô, trên đầu chỗ đứng trống), trèo lên rồi kê cột ra{}",
                  new Object[]{
                     var4.toShortString(),
                     var21.equals(var4) ? "" : "bơi sang " + var21.toShortString() + " (" + (var29.size() - 1) + " ô) rồi ",
                     var22.toShortString(),
                     this.blocksBody(var22.up().up().up()) ? 2 : 3,
                     var31 == null ? "" : String.format(" (nước chảy đẩy hướng %.2f, %.2f → bù dòng)", var31.x, var31.z)
                  }
               );
            this.status = "Rơi xuống bể → đặt bậc kê để trèo ra";
            return true;
         }
      }
   }

   private boolean waterStepTick() {
      if (this.wsPos == null || this.mc.player == null) {
         return false;
      } else if (++this.wsTicks > 320) {
         AutoBuildMod.LOGGER.info("[AutoBuild] 16 giây chưa trèo ra khỏi bể bằng bậc kê {} → cấm ô này 1 phút, chọn chỗ khác", this.wsPos.toShortString());
         this.wsBad.put(this.wsPos, this.ticks + 1200);
         this.wsPos = null;
         this.wsLastAt = this.ticks - 80;
         this.release();
         this.beginScan();
         return true;
      } else {
         this.release();
         BlockState var1 = this.mc.world.getBlockState(this.wsPos);
         if (this.wsStage < 0) {
            // bơi/lội sang chỗ đứng đã chọn, theo đường BFS trong bể, bù dòng nước
            while (this.wsPathIdx < this.wsPath.size() - 1) {
               BlockPos var2 = this.wsPath.get(this.wsPathIdx);
               double var3 = (double)var2.getX() + 0.5 - this.mc.player.getX();
               double var5 = (double)var2.getZ() + 0.5 - this.mc.player.getZ();
               if (!(var3 * var3 + var5 * var5 < 0.16)) {
                  break;
               }

               this.wsPathIdx++;
            }

            BlockPos var7 = this.wsPath.isEmpty() ? this.wsStand : this.wsPath.get(Math.min(this.wsPathIdx, this.wsPath.size() - 1));
            double var8 = (double)this.wsStand.getX() + 0.5 - this.mc.player.getX();
            double var10 = (double)this.wsStand.getZ() + 0.5 - this.mc.player.getZ();
            if (var8 * var8 + var10 * var10 < 0.06) {
               this.wsStage = 0;
               return true;
            } else {
               double var12 = (double)var7.getX() + 0.5 - this.mc.player.getX();
               double var14 = (double)var7.getZ() + 0.5 - this.mc.player.getZ();
               this.mc.player.setYaw(this.mc.player.getYaw() + MotionMath.wrap(this.flowYaw(var12, var14) - this.mc.player.getYaw()));
               this.mc.player.setPitch(20.0F);
               if (var12 * var12 + var14 * var14 < 0.25) {
                  this.holdAt((double)var7.getX() + 0.5, (double)var7.getZ() + 0.5);
               } else {
                  Input.key(this.mc.options.forwardKey, true);
               }

               this.status = "Lội sang chỗ đặt bậc " + this.wsStand.toShortString();
               return true;
            }
         } else if (this.wsStage == 0) {
            if (!var1.isReplaceable()) {
               this.wsStage = 1;
               if (this.journal != null && !this.journal.placed.containsKey(this.wsPos)) {
                  this.journal.add(this.wsPos, var1);
                  this.scaffoldUsedAt = this.ticks;
               }

               return true;
            } else {
               Item var16 = this.scaffoldItem();
               if (!(var16 instanceof BlockItem var17)) {
                  this.wsPos = null;
                  return false;
               } else {
                  // giữ đúng giữa chỗ đứng (nước chảy đẩy lệch → thân chồng lên ô bậc thì đặt bị từ chối)
                  this.holdAt((double)this.wsStand.getX() + 0.5, (double)this.wsStand.getZ() + 0.5);
                  if (this.occupies(this.wsPos)) {
                     this.status = "Nước đẩy lệch → về giữa ô rồi mới đặt bậc";
                     return true;
                  } else if (!this.mc.player.getMainHandStack().isOf(var16)) {
                     if (this.unacked == 0 && InventoryAccess.synced(this.mc) && this.mc.currentScreen == null) {
                        InventoryAccess.equip(this.mc, var16, this.config.workingHotbarSlot);
                     }

                     this.status = "Cầm khối kê để đặt bậc trèo ra";
                     return true;
                  } else {
                     PlacementSolver.Aim var18 = PlacementSolver.solve(
                        this.mc, this.mc.player.getEyePos(), new SchematicPlan.Target(this.wsPos, var17.getBlock().getDefaultState()), this.reach(), true, this.wsPos.asLong() * 31L
                     );
                     if (var18 == null) {
                        if (this.wsTicks > 100) {
                           this.wsBad.put(this.wsPos, this.ticks + 1200);
                           this.wsPos = null;
                           this.wsLastAt = this.ticks - 80;
                           this.beginScan();
                        }

                        return true;
                     } else {
                        Input.sneak(this.mc, var18.sneak(), this.ticks);
                        this.lookAt(var18.yaw(), var18.pitch());
                        if (this.rotationSent()
                           && this.cooldown <= 0
                           && this.paceOk()
                           && InventoryAccess.synced(this.mc)
                           && this.mc.player.isInSneakingPose() == var18.sneak()) {
                           BlockHitResult var19 = PlacementSolver.ray(this.mc, this.reach());
                           if (var19.getBlockPos().equals(var18.hit().getBlockPos()) && var19.getSide() == var18.hit().getSide()) {
                              this.placeNow(var19);
                              this.clicked(this.wsPos);
                              this.cooldown = 4 + this.pingDelayTicks();
                           }
                        }

                        this.status = "Đặt bậc kê trong bể @ " + this.wsPos.toShortString();
                        return true;
                     }
                  }
               }
            }
         } else if (var1.isReplaceable()) {
            // bậc kê biến mất (server trả về) → đặt lại
            this.wsStage = 0;
            return true;
         } else {
            double var20 = (double)this.wsPos.getX() + 0.5 - this.mc.player.getX();
            double var22 = (double)this.wsPos.getZ() + 0.5 - this.mc.player.getZ();
            if (this.mc.player.isOnGround() && this.mc.player.getY() > (double)this.wsPos.getY() + 0.9 && var20 * var20 + var22 * var22 < 0.2) {
               AutoBuildMod.LOGGER.info("[AutoBuild] Đã trèo lên bậc kê {} (khô) → nhảy kê cột ra khỏi bể", this.wsPos.toShortString());
               this.wsPos = null;
               this.escapeTried = false;
               this.forgiveStuckMarks();
               this.beginScan();
               return true;
            } else {
               // trèo: ép vào cạnh bậc + nhảy (vanilla: trong nước mà ép vào cạnh khối thì được đẩy lên), quay mặt bù dòng nước
               Input.forceSneak(this.mc, false, this.ticks);
               this.mc.player.setYaw(this.mc.player.getYaw() + MotionMath.wrap(this.flowYaw(var20, var22) - this.mc.player.getYaw()));
               this.mc.player.setPitch(20.0F);
               Input.key(this.mc.options.forwardKey, true);
               Input.key(this.mc.options.jumpKey, this.mc.player.getY() < (double)this.wsPos.getY() + 0.9);
               this.status = "Trèo lên bậc kê " + this.wsPos.toShortString();
               return true;
            }
         }
      }
   }

   private boolean stuckEscapeTick() {
      if (this.waterStepTick()) {
         return true;
      } else if (this.escUntil <= 0 || this.mc.player == null) {
         return false;
      } else if (this.ticks > this.escUntil) {
         this.escUntil = 0;
         this.release();
         AutoBuildMod.LOGGER.info("[AutoBuild] 6 giây chưa bước ra được → đánh dấu tránh ô đang đứng, tìm đường lại");
         Navigation.AVOID.add(this.mc.player.getBlockPos().toImmutable());
         this.avoidSince = this.ticks;
         this.beginScan();
         return true;
      } else {
         this.release();
         boolean var1 = Navigation.waterish(this.mc.world.getBlockState(this.mc.player.getBlockPos()));
         Vec3d var2 = this.escGoal;
         if (var2 == null) {
            // không thấy ô khô: bơi/nhảy theo hướng đang nhìn, đổi hướng mỗi 1,5 giây
            float var3 = (float)((this.ticks - this.escStartTick) / 30 * 90);
            this.mc.player.setYaw(var3);
            Input.key(this.mc.options.forwardKey, true);
            Input.key(this.mc.options.jumpKey, true);
            this.status = "Kẹt → thử bơi/nhảy ra";
            return true;
         } else {
            double var4 = var2.x - this.mc.player.getX();
            double var6 = var2.z - this.mc.player.getZ();
            double var8 = Math.sqrt(var4 * var4 + var6 * var6);
            if (var8 < 0.35 && this.mc.player.isOnGround() && Math.abs(this.mc.player.getY() - var2.y) < 0.6) {
               this.escUntil = 0;
               this.escGoal = null;
               this.brake();
               AutoBuildMod.LOGGER.info("[AutoBuild] Đã bước ra khỏi chỗ kẹt → xóa các đánh dấu hoãn/bỏ qua do kẹt ở tầng Y={}, tìm đường lại", this.currentLayer);
               this.forgiveStuckMarks();
               this.beginScan();
               return true;
            } else {
               this.mc.player.setYaw(this.mc.player.getYaw() + MotionMath.wrap(this.flowYaw(var4, var6) - this.mc.player.getYaw()));
               this.mc.player.setPitch(20.0F);
               Input.key(this.mc.options.forwardKey, true);
               Vec3d var10 = this.mc.player.getPos();
               if (this.escLastPos != null && var10.squaredDistanceTo(this.escLastPos) < 4.0E-4) {
                  this.escStuckTicks++;
               } else {
                  this.escStuckTicks = 0;
               }

               this.escLastPos = var10;
               if (var1 || var2.y > this.mc.player.getY() + 0.5 || this.escStuckTicks > 4) {
                  Input.key(this.mc.options.jumpKey, true);
               }

               this.status = "Kẹt → bước ra " + BlockPos.ofFloored(var2).toShortString();
               return true;
            }
         }
      }
   }

   /**
    * 3.0.84: trong lúc kẹt, mọi khối đều "không có đường" → bị hoãn / bỏ qua hàng loạt (log: 10.952 lần "Bỏ hẳn khối kẹt").
    * Thoát ra rồi thì xóa hết các đánh dấu đó của tầng đang xây (và 2 tầng dưới) để xây lại bình thường.
    */
   private void forgiveStuckMarks() {
      if (this.currentLayer != null) {
         int var1 = this.currentLayer;
         java.util.function.Predicate<BlockPos> var2 = var1x -> var1x.getY() <= var1 && var1x.getY() >= var1 - 2;
         this.skipped.removeIf(var2);
         this.skipReason.keySet().removeIf(var2);
         this.deferred.keySet().removeIf(var2);
         this.deferCount.keySet().removeIf(var2);
         this.navFailures.keySet().removeIf(var2);
         this.stuckSkips.keySet().removeIf(var2);
         this.attempts.keySet().removeIf(var2);
         this.rowDeferred.clear();
         this.postponedLayers.removeIf(var1x -> var1x <= var1 && var1x >= var1 - 2);
         this.layerRounds.keySet().removeIf(var1x -> var1x <= var1 && var1x >= var1 - 2);
         this.layerRoundPlaced.keySet().removeIf(var1x -> var1x <= var1 && var1x >= var1 - 2);
         this.permRetried.removeIf(var1x -> var1x <= var1 && var1x >= var1 - 2);
      }
   }

   private void search() {
      this.status = "Tính đường đi";
      this.heavyLabel = "tìm đường";
      this.search.advanceFor(18000000L);
      if (this.search.done()) {
         if (this.search.result() == null && this.climbSearch) {
            this.climbSearch = false;
            this.climbFailUntil = this.ticks + 200;
            AutoBuildMod.LOGGER.info("[AutoBuild] Chưa tìm được đường lên mặt tầng Y={} (đã xét {} ô)", this.currentLayer, this.search.explored());
            this.nextTarget(true);
         } else if (this.search.result() == null && this.selfPillarBase != null && this.current() == this.supportTarget) {
            AutoBuildMod.LOGGER.info("[AutoBuild] Không đi bộ tới được chân cột {} → dựng khối kê kiểu thường", this.selfPillarBase.toShortString());
            this.selfPillarBase = null;
            this.beginGoals(false);
         } else if (this.search.result() == null && this.terrain.walkOnly) {
            if (this.search.exhausted() && !this.cleanupMode) {
               this.reachable = new HashSet<>(this.search.reached());
               this.rememberReachable();
            }

            AutoBuildMod.LOGGER
               .info(
                  "[AutoBuild] Chưa có đường đi bộ tới {} (đã xét {} ô; {}) → xét bắc cầu/kê khối",
                  new Object[]{
                     this.current() == null ? "-" : this.current().pos().toShortString(),
                     this.search.explored(),
                     this.search.exhausted()
                        ? "đã xét hết vùng"
                        : (
                           this.search.timedOut()
                              ? "hết thời gian tính; không ghi nhớ là vùng không tới được"
                              : "chạm giới hạn số ô; không ghi nhớ là vùng không tới được"
                        )
                  }
               );
            this.terrain.walkOnly = false;
            this.scaffoldPass = true;
            this.goalIndex = 0;
            this.setStage(BuildController.Stage.GOALS);
         } else if (this.search.result() == null) {
            PathSearch.Cell var1 = Navigation.cell(this.mc.player.getBlockPos());
            boolean var30 = this.terrain.standable(var1);
            int var31 = this.terrain.neighbors(var1).size();
            if (this.ticks - this.noPathSaidAt > 100 || !var1.equals(this.noPathSaidCell)) {
               // 3.0.84: không in lặp mỗi tick (log cũ 151.409 dòng giống hệt nhau)
               this.noPathSaidAt = this.ticks;
               this.noPathSaidCell = var1;
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] Chưa tìm được đường: xuất phát {} (đứng được: {}, lối ra: {}), đã xét {} ô, {} chỗ đứng đích, ví dụ {}",
                     new Object[]{var1, var30, var31, this.search.explored(), this.goals.size(), this.goals.isEmpty() ? "-" : this.goals.getFirst()}
                  );
            }

            // 3.0.84: cùng một ô xuất phát mà tìm đường hỏng ngay từ đầu (≤ 1 ô) nhiều lần → ô này là bế tắc:
            // KHÔNG tính lại đường cho từng khối nữa (trước đây lặp 8 tiếng), mà tự bước ra khỏi ô đó.
            if (this.search.explored() <= 1 && !this.cleanupMode && (!var30 || var31 == 0)) {
               if (!var1.equals(this.deadStart) || this.ticks - this.deadStartAt > 400) {
                  this.deadStart = var1;
                  this.deadStartCount = 0;
               }

               this.deadStartAt = this.ticks;
               if (++this.deadStartCount >= 3) {
                  this.deadStartCount = 0;
                  this.beginStuckEscape(var1);
                  return;
               }
            }

            // 3.0.85: rơi xuống hố/bể (vùng đi được nhỏ và đã xét hết, vd. bể nước 7×7 thành cao 2–3 ô) → bị nhốt.
            // Bản cũ chỉ thử "kê khối dưới chân" khi xét < 40 ô, nên bể 49 ô thì không bao giờ thử → đứng mãi, bỏ qua mọi khối.
            boolean var32 = this.search.exhausted() && this.search.explored() < 400 && !this.cleanupMode;
            if (var32
               && this.currentLayer != null
               && (this.target == null || this.target.pos().getY() >= this.currentLayer - 1)
               && this.tryHomeUp("Bị nhốt trong hố/bể " + this.search.explored() + " ô")) {
               return;
            }

            if (var32 && Navigation.waterish(this.mc.world.getBlockState(this.mc.player.getBlockPos())) && this.ticks - this.wsLastAt > 100) {
               this.wsLastAt = this.ticks;
               if (this.beginWaterStep()) {
                  return;
               }
            }

            if ((this.search.explored() < 40 || var32) && !this.escapeTried && !this.cleanupMode && this.mc.player.isOnGround()) {
               this.escapeTried = true;
               if (this.search.explored() >= 40) {
                  // 3.0.85: vùng kín lớn (hố/bể) — các khối bị bỏ qua trong lúc kẹt trong hố được xây lại sau khi ra
                  AutoBuildMod.LOGGER.info("[AutoBuild] Bị nhốt trong hố {} ô, không có đường ra → kê cột leo lên", this.search.explored());
                  this.forgiveStuckMarks();
               }

               this.terrain.escape = true;
               this.terrain.dropLimit = this.mc.player.getHealth() - 3.0F > (float)this.config.minHealth ? 6 : 4;
               this.terrain.walkOnly = false;
               this.search = new PathSearch(this.terrain.startCells(), this.goals, this.terrain, this.config.pathNodeLimit);
               this.status = "Bị kẹt → kê khối leo lên";
               AutoBuildMod.LOGGER.info("[AutoBuild] Bị kẹt tại {}, thử kê khối dưới chân để thoát", var1);
            } else if ((this.search.explored() < 40 || var32) && this.replans++ < 4) {
               this.beginGoals(false);
            } else {
               this.navigationFailed("Chưa tìm được đường đi trong vùng đã tải.");
            }
         } else {
            List var4 = this.search.result();
            int var2 = 0;

            for (int var3 = 1; var3 < var4.size(); var3++) {
               if (!this.terrain.standable((PathSearch.Cell)var4.get(var3))) {
                  var2++;
               }
            }

            if (var2 > 0
               && !this.approachOnly
               && !this.cleanupMode
               && this.target != null
               && !this.queue.isEmpty()
               && !this.scaffoldOk
               && this.shouldDeferBridge(var2)) {
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] {} @ {} cần {} khối kê → xét mục tiêu khác (tối đa 2 lần tại chỗ)",
                     new Object[]{this.target.state().getBlock().getName().getString(), this.target.pos().toShortString(), var2}
                  );
               this.deferred.put(this.target.pos(), this.ticks + 60);
               this.nextTarget(true);
            } else if (var2 > this.config.maxBridgeBlocks && !this.cleanupMode && !this.scaffoldOk && this.target != null) {
               AutoBuildMod.LOGGER
                  .info(
                     "[AutoBuild] {} @ {} cần bắc cầu {} khối (quá {}) → không kê nữa, để sau",
                     new Object[]{this.target.state().getBlock().getName().getString(), this.target.pos().toShortString(), var2, this.config.maxBridgeBlocks}
                  );
               this.navigationFailed("Cần bắc cầu " + var2 + " khối tạm (quá " + this.config.maxBridgeBlocks + ") — bỏ qua cho đỡ rác.");
            } else {
               if (var2 > 0) {
                  AutoBuildMod.LOGGER.info("[AutoBuild] Không có đường đi bộ → bắc cầu {} khối tới {}", var2, var4.getLast());
               }

               this.climbMove = this.climbSearch;
               this.climbSearch = false;
               this.planeMove = false;
               this.path = var4;
               this.pathIndex = Math.min(1, this.path.size());
               this.lastPosition = this.mc.player.getPos();
               this.stuckTicks = 0;
               this.walkPitch = 30.0F;
               this.setStage(BuildController.Stage.MOVE);
            }
         }
      }
   }

   private boolean bucketRayStillHits(Vec3d var1, PlacementSolver.Aim var2) {
      Vec3d var3 = var2.hit().getPos();
      if (var1.squaredDistanceTo(var3) > this.reach() * this.reach()) {
         return false;
      } else {
         Vec3d var4 = var3.subtract(var1).normalize();
         BlockHitResult var5 = this.mc
            .world
            .raycast(new RaycastContext(var1, var3.add(var4.multiply(0.01)), ShapeType.OUTLINE, FluidHandling.NONE, this.mc.player));
         return var5.getType() == Type.BLOCK && var5.getBlockPos().equals(var2.hit().getBlockPos()) && var5.getSide() == var2.hit().getSide();
      }
   }

   private boolean holdBucketPurchase() {
      if (this.shopping && this.bucketStandPos != null && this.bucketBuyPos != null) {
         SchematicPlan.Target var1 = this.current();
         String var2 = null;
         if (var1 != null && var1.pos().equals(this.bucketBuyPos)) {
            if (this.done(this.mc.world.getBlockState(var1.pos()), var1.state())) {
               this.shop.cancel(this.mc);
               this.shopping = false;
               this.bucketStandPos = null;
               this.bucketBuyPos = null;
               this.finishTarget();
               return false;
            }

            if (this.mc.player.getPos().squaredDistanceTo(this.bucketStandPos) > 0.49) {
               // 3.0.98: lỡ bị nước đẩy lệch lúc shop đang mở thì vẫn mua tiếp (xô vẫn dùng được), mua xong mới tính lại góc đổ.
               // Bản cũ hủy phiên mua ngay → mở /shop lại liên tục mà không đổ được ô nào (log 00:19–00:20).
               if (!this.bucketDrifted) {
                  this.bucketDrifted = true;
                  AutoBuildMod.LOGGER.info("[AutoBuild] Shop: bị đẩy lệch khỏi chỗ đổ {} lúc đang mua → mua tiếp, xong sẽ ngắm lại", this.bucketBuyPos.toShortString());
               }
            } else if (this.bucketDrifted) {
               // đã lệch trước đó: chờ mua xong
            } else if (this.aim != null) {
               Input.sneak(this.mc, this.overhanging(), this.ticks);
               Vec3d var3 = PlacementSolver.poseEye(PlacementSolver.standingEye(this.mc, this.mc.player.getEyePos()), this.aim.sneak());
               if (!this.bucketRayStillHits(var3, this.aim) && !this.bucketRayWarned) {
                  this.bucketRayWarned = true;
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Shop: tia ngắm tới {} lệch khi mở menu (mắt {}, ngồi={}, v={}) → vẫn mua, đặt lại góc sau khi mua",
                        new Object[]{
                           this.bucketBuyPos.toShortString(),
                           var3,
                           this.mc.player.isInSneakingPose(),
                           String.format("%.4f", this.mc.player.getVelocity().horizontalLengthSquared())
                        }
                     );
               }
            } else {
               var2 = "mất góc đặt đã chuẩn bị";
            }
         } else {
            var2 = "mục tiêu đã đổi";
         }

         if (var2 == null) {
            return true;
         } else {
            AutoBuildMod.LOGGER.info("[AutoBuild] Shop: hủy phiên mua tại {} vì {}; sẽ kiểm tra lại chỗ đứng", this.bucketBuyPos.toShortString(), var2);
            this.shop.cancel(this.mc);
            this.shopping = false;
            this.bucketStandPos = null;
            this.bucketBuyPos = null;
            this.status = "Kiểm tra lại vị trí đổ: " + var2;
            this.setStage(BuildController.Stage.EQUIP);
            return false;
         }
      } else {
         return true;
      }
   }

   public static record Blocked(BlockPos pos, String wanted, String actual) {
   }

   private static record BuildPlan(SchematicPlan.Target root, List<BlockPos> tempChain) {
   }

   private static record Candidate(PathSearch.Cell cell, double score, boolean scaffold) {
   }

   private static record ExtraCheck(BlockPos pos, Block block, List<BlockPos> air, int checkAt) {
   }

   public static record MaterialRow(Item item, int needed, int have) {
   }

   private static record PitPlan(SchematicPlan.Target target, BlockPos column) {
   }

   private static record PlaceKey(int sig, boolean ok) {
   }

   /** khối vừa đặt còn nằm trong hàng chờ kiểm tra (chưa chắc server có) */
   private boolean unverified(BlockPos var1) {
      for (BuildController.Placed var3 : this.verify) {
         if (var3.pos().equals(var1)) {
            return true;
         }
      }

      return false;
   }

   private static record Placed(BlockPos pos, BlockState want, int checkAt) {
   }

   private static record RowPlan(SchematicPlan.Target head, BlockPos front) {
   }

   private final class Scan {
      final boolean choose;
      int index;
      int matched;
      int blocked;
      int unloaded;
      int unsupported;
      int total;
      // 3.0.89: ô mod không tự đặt được, theo loại (để báo rõ thay vì chỉ "Không hỗ trợ: N")
      final Map<String, Integer> unsupportedKinds = new LinkedHashMap<>();
      final Map<String, BlockPos> unsupportedAt = new HashMap<>();
      final TreeMap<Integer, LayerPlanner.Stat> layers = new TreeMap<>();
      final Map<Integer, SchematicPlan.Target> bestReady = new HashMap<>();
      final Map<Integer, SchematicPlan.Target> bestMissing = new HashMap<>();
      final Map<Integer, Double> bestReadyScore = new HashMap<>();
      final Map<Integer, Double> bestMissingScore = new HashMap<>();
      final Map<Integer, Map<Item, Integer>> layerNeed = new HashMap<>();
      final Map<Integer, Map<Item, Integer>> layerAll = new HashMap<>();
      final Map<Item, Integer> totalNeed = new LinkedHashMap<>();
      final List<BuildController.Blocked> blockedList = new ArrayList<>();
      final Map<Item, Integer> haveCache = new HashMap<>();
      final Map<Integer, List<SchematicPlan.Target>> readyByKey = new HashMap<>();
      final Map<BlockState, Item> materialCache = new HashMap<>();
      final Map<Integer, List<SchematicPlan.Target>> deferredByKey = new HashMap<>();
      final Map<Integer, List<SchematicPlan.Target>> waterByKey = new HashMap<>();
      final Map<Integer, Integer> solidRemaining = new HashMap<>();
      final Map<Integer, Integer> waterRemaining = new HashMap<>();
      final Map<BlockPos, BlockState> wrongFound = new LinkedHashMap<>();
      boolean finished;
      int[] fluidRemaining;
      boolean prepped;
      int prepIndex;
      /** 3.0.105: tầng dưới đã xây xong mà phần ở xa bị server giấu (đọc ra trống/đá) → coi phần xa là xong */
      final java.util.Set<Integer> hiddenDone = new java.util.HashSet<>();
      boolean trustChecked;

      /**
       * Lấy mẫu các ô trong 12 ô quanh người (đọc được thật) của từng tầng: tầng có ≥ 12 ô mẫu, ≥ 95% đúng bản vẽ, và có tầng CAO hơn
       * đã xây ≥ 50% (xây theo tầng từ dưới lên → tầng này đã xong từ trước) → ô ở xa của tầng đó đọc ra trống/đá là do server giấu.
       */
      final HashMap<Integer, int[]> trustStat = new HashMap<>();

      /** có tầng cao hơn var1 đã xây ≥ 50% (theo mẫu đọc được thật quanh người) */
      boolean builtAbove(int var1) {
         for (Entry<Integer, int[]> var3 : this.trustStat.entrySet()) {
            if (var3.getKey() > var1 && var3.getValue()[0] >= 12 && var3.getValue()[1] * 2 >= var3.getValue()[0]) {
               return true;
            }
         }

         return false;
      }

      void checkTrust() {
         this.trustChecked = true;
         HashMap<Integer, int[]> var1 = this.trustStat;

         for (SchematicPlan.Target var3 : BuildController.this.plan.targets) {
            BlockPos var4 = var3.pos();
            if (BuildController.this.config.inYRange(var4.getY())
               && !Materials.flowing(var3.state())
               && BuildController.this.trusted(var4)
               && BuildController.this.mc.world.isChunkLoaded(var4)) {
               int[] var5 = var1.computeIfAbsent(var4.getY(), var0 -> new int[2]);
               var5[0]++;
               if (BuildController.this.done(BuildController.this.mc.world.getBlockState(var4), var3.state())) {
                  var5[1]++;
               }
            }
         }

         for (Entry<Integer, int[]> var7 : var1.entrySet()) {
            int[] var8 = var7.getValue();
            if (var8[0] >= 12 && var8[1] >= var8[0] * 0.95) {
               boolean var9 = false;

               for (Entry<Integer, int[]> var11 : var1.entrySet()) {
                  if (var11.getKey() > var7.getKey() && var11.getValue()[0] >= 12 && var11.getValue()[1] * 2 >= var11.getValue()[0]) {
                     var9 = true;
                     break;
                  }
               }

               if (var9) {
                  this.hiddenDone.add(var7.getKey());
               }
            }
         }
      }

      /** ô ở xa (> 12 ô) mà mình đã xác nhận xong, hoặc thuộc tầng dưới đã xong → không tin số đọc (server giấu khối xa) */
      boolean assumeDone(SchematicPlan.Target var1, BlockState var3) {
         // 3.0.111 (bản vá ngoài): không coi ô là xong dựa trên lần đặt trước; chỉ tin thế giới thật
         return false;
      }
      Integer gate;
      final TreeMap<Integer, Integer> unfinished = new TreeMap<>();
      final Map<Integer, Integer> blockedWrong = new HashMap<>();
      final Map<Integer, Integer> minPhase = new HashMap<>();
      final Map<Integer, Integer> tempSkipped = new HashMap<>();
      final Map<Integer, Integer> permSkipped = new HashMap<>();
      final Map<Integer, BlockPos> skipExample = new HashMap<>();
      final Map<Integer, String> fluidLeft = new HashMap<>();
      int triggerLeft;
      final TreeMap<Integer, Integer> triggerByLayer = new TreeMap<>();

      boolean prepass(long var1) {
         if (!this.trustChecked) {
            this.checkTrust();
         }

         while (this.prepIndex < BuildController.this.plan.targets.size() && System.nanoTime() < var1) {
            SchematicPlan.Target var3 = BuildController.this.plan.targets.get(this.prepIndex++);
            int var4 = var3.pos().getY();
            if (BuildController.this.config.inYRange(var4)
               && !Materials.flowing(var3.state())
               && !(BuildController.this.config.layerMode && BuildController.this.postponedLayers.contains(var4))) {
               int var5 = BuildController.this.config.layerMode ? var4 : 0;
               if (BuildController.this.mc.world.isChunkLoaded(var3.pos())) {
                  BlockState var6 = BuildController.this.mc.world.getBlockState(var3.pos());
                  if (StateMatch.chestAwaitingPartner(var6, var3.state()) || BuildController.this.done(var6, var3.state())) {
                     continue;
                  }

                  if (this.assumeDone(var3, var6)) {
                     continue;
                  }

                  if (!StateMatch.canAdd(var6, var3.state()) && !StateMatch.configurable(var6, var3.state())) {
                     // 3.0.79: ô đang bị khối khác (đá/đất có sẵn, khối đặt sai) chiếm vẫn là việc CHƯA XONG của tầng →
                     // giữ tầng này, xây hết chỗ trống rồi đào khối đó ra và đặt đúng khối, sau mới lên tầng trên.
                     if (BuildController.this.config.fixWrong
                        && BuildController.this.breakableWrong(var6, var3.pos())
                        && BuildController.this.repairTries.getOrDefault(var3.pos(), 0) <= 4
                        && !BuildController.this.skipped.contains(var3.pos())
                        && !BuildController.this.digStuckLayers.contains(var5)
                        && !BuildController.this.blockerHeld(var3.pos())) {
                        this.unfinished.merge(var5, 1, Integer::sum);
                        this.blockedWrong.merge(var5, 1, Integer::sum);
                     }

                     continue;
                  }

                  if (!Materials.supported(var6, var3.state())) {
                     continue;
                  }
               }

               if (BuildController.this.triggerHeld(var3)) {
                  this.triggerLeft++;
                  this.triggerByLayer.merge(var5, 1, Integer::sum);
                  continue;
               }

               this.unfinished.merge(var5, 1, Integer::sum);
               int var7 = FluidOrder.phase(var3.state());
               if (var7 > 0) {
                  this.fluidLeft.putIfAbsent(var5, var3.state().getBlock().getName().getString() + " @ " + var3.pos().toShortString());
               }

               if (BuildController.this.skipped.contains(var3.pos())) {
                  if (BuildController.this.skipReason.containsKey(var3.pos())) {
                     this.permSkipped.merge(var5, 1, Integer::sum);
                  } else {
                     this.tempSkipped.merge(var5, 1, Integer::sum);
                  }

                  this.skipExample.putIfAbsent(var5, var3.pos().toImmutable());
               } else {
                  this.minPhase.merge(var5, Math.max(0, var7), Math::min);
               }
            }
         }

         if (this.prepIndex < BuildController.this.plan.targets.size()) {
            return false;
         } else {
            this.prepped = true;
            this.gate = this.unfinished.isEmpty() ? null : this.unfinished.firstKey();
            if (this.choose && this.gate != null) {
               int var8 = this.minPhase.getOrDefault(this.gate, BuildController.this.buildFluidPhase);
               if (var8 != BuildController.this.buildFluidPhase) {
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Tầng Y={}: chuyển bước {} → {}",
                        new Object[]{this.gate, FluidOrder.label(BuildController.this.buildFluidPhase), FluidOrder.label(var8)}
                     );
                  BuildController.this.buildFluidPhase = var8;
               }
            }

            return true;
         }
      }

      int solidTotal() {
         int var1 = 0;

         for (int var3 : this.solidRemaining.values()) {
            var1 += var3;
         }

         return var1;
      }

      Scan(boolean nullx) {
         this.choose = nullx;
      }

      int have(Item var1) {
         return this.haveCache.computeIfAbsent(var1, var1x -> InventoryAccess.count(BuildController.this.mc, var1x));
      }

      boolean advance() {
         long var1 = System.nanoTime() + 18000000L;
         if (!this.prepped && !this.prepass(var1)) {
            return false;
         }

         while (this.index < BuildController.this.plan.targets.size() && System.nanoTime() < var1) {
            SchematicPlan.Target var3 = BuildController.this.plan.targets.get(this.index++);
            int var4 = var3.pos().getY();
            if (BuildController.this.config.inYRange(var4)) {
               this.total++;
               int var5 = BuildController.this.config.layerMode ? var4 : 0;
               this.layerAll.computeIfAbsent(var4, var0 -> new HashMap<>()).merge(var3.state().getBlock().asItem(), 1, Integer::sum);
               BlockState var6 = Blocks.AIR.getDefaultState();
               boolean var7 = BuildController.this.mc.world.isChunkLoaded(var3.pos());
               boolean var47 = BuildController.this.includeFluidPhase(this, var3);
               {
                  if (var7) {
                     var6 = BuildController.this.mc.world.getBlockState(var3.pos());
                     var3.pos();
                     if (StateMatch.chestAwaitingPartner(var6, var3.state())) {
                        this.matched++;
                        continue;
                     }

                     if (BuildController.this.fillHolds(var3.pos())) {
                        // 3.0.125: khối che đã đập để mở khối chứa → cho đồ xong mới đặt lại
                        this.matched++;
                        continue;
                     }

                     if (BuildController.this.done(var6, var3.state())) {
                        if (this.choose) {
                           BuildController.this.markPlacedDone(BuildController.this.placedDone, var3.pos());
                        }

                        this.matched++;
                        continue;
                     }

                     if (this.assumeDone(var3, var6)) {
                        // 3.0.105: đã xác nhận xong, giờ ở xa (> 12 ô) đọc ra khác → server giấu khối ở xa, không tin; tới gần sẽ kiểm tra lại
                        this.matched++;
                        continue;
                     }

                     if (BuildController.this.skipped.contains(var3.pos())) {
                        this.blocked++;
                        if (this.blockedList.size() < 300) {
                           this.blockedList
                              .add(
                                 new BuildController.Blocked(
                                    var3.pos(),
                                    var3.state().getBlock().getName().getString(),
                                    BuildController.this.skipReason.getOrDefault(var3.pos(), "Bỏ qua sau 6 lần thử (nhấn K để thử lại)")
                                 )
                              );
                        }
                        continue;
                     }

                     if (!StateMatch.canAdd(var6, var3.state()) && !StateMatch.configurable(var6, var3.state())) {
                        if (BuildController.this.config.fixWrong
                           && BuildController.this.breakableWrong(var6, var3.pos())
                           && !BuildController.this.blockerHeld(var3.pos())
                           && BuildController.this.trusted(var3.pos())
                           && this.wrongFound.size() < 64) {
                           this.wrongFound.put(var3.pos().toImmutable(), var6);
                        }

                        this.blocked++;
                        if (this.blockedList.size() < 300) {
                           this.blockedList
                              .add(
                                 new BuildController.Blocked(var3.pos(), var3.state().getBlock().getName().getString(), var6.getBlock().getName().getString())
                              );
                        }
                        continue;
                     }

                     if (Materials.flowing(var3.state()) || !Materials.supported(var6, var3.state())) {
                        this.unsupported++;
                        String var96 = var3.state().getBlock().getName().getString()
                           + (Materials.flowing(var3.state()) ? " chảy (tự hình thành từ nguồn)" : " (không có vật phẩm để đặt)");
                        this.unsupportedKinds.merge(var96, 1, Integer::sum);
                        this.unsupportedAt.putIfAbsent(var96, var3.pos().toImmutable());
                        continue;
                     }

                     if (BuildController.this.triggerHeld(var3)) {
                        this.unsupported++;
                        String var97 = var3.state().isOf(Blocks.TNT)
                           ? "TNT cạnh mạch đỏ (đặt sau cùng của tầng cho an toàn)"
                           : var3.state().getBlock().getName().getString() + " kích hoạt piston (đặt cuối cùng)";
                        this.unsupportedKinds.merge(var97, 1, Integer::sum);
                        this.unsupportedAt.putIfAbsent(var97, var3.pos().toImmutable());
                        continue;
                     }

                     if (this.choose && BuildController.this.placedDone.remove(var3.pos())) {
                        AutoBuildMod.LOGGER.info("[AutoBuild] {} từng xong nhưng giờ chưa đúng/trống → đưa lại vào danh sách xây", var3.pos().toShortString());
                        String var95 = BuildController.this.pistonNear(var3.pos());
                        if (var95 != null) {
                           AutoBuildMod.LOGGER.info("[AutoBuild] {} nằm trước mặt {} → nhiều khả năng bị piston đẩy đi; tầng Y={} sẽ được làm lại trước khi lên tiếp", new Object[]{var3.pos().toShortString(), var95, var3.pos().getY()});
                        }
                        if (var6.isReplaceable() && BuildController.this.ghosts.put(var3.pos().toImmutable(), BuildController.this.ticks) == null) {
                           Navigation.GHOSTS.add(var3.pos().toImmutable());
                        }
                     }
                  } else {
                     this.unloaded++;
                  }

                  LayerPlanner.Stat var8 = this.layers.computeIfAbsent(var5, var0 -> new LayerPlanner.Stat());
                  var8.remaining++;
                  boolean var9 = Materials.pourable(var6, var3.state());
                  if (!var9) {
                     this.solidRemaining.merge(var5, 1, Integer::sum);
                  } else {
                     this.waterRemaining.merge(var5, 1, Integer::sum);
                  }

                  boolean var10 = var7 && StateMatch.configurable(var6, var3.state());
                  BlockState var11 = var6;
                  Item var12 = var10
                     ? Items.AIR
                     : (
                        BuildController.this.substitutable(var3.state())
                           ? (
                              var11.isAir()
                                 ? this.materialCache.computeIfAbsent(var3.state(), var3x -> BuildController.this.materialFor(var3, var11))
                                 : BuildController.this.materialFor(var3, var11)
                           )
                           : Materials.item(var6, var3.state())
                     );
                  if (!var10) {
                     this.layerNeed.computeIfAbsent(var5, var0 -> new LinkedHashMap<>()).merge(var12, 1, Integer::sum);
                     this.totalNeed.merge(var12, 1, Integer::sum);
                  }

                  if (this.choose && var47) {
                     if (BuildController.this.deferred.getOrDefault(var3.pos(), 0) <= BuildController.this.ticks) {
                        double var13 = BuildController.this.mc.player.squaredDistanceTo(Vec3d.ofCenter(var3.pos()));
                        double var15 = var13
                           + (BuildController.this.config.layerMode ? 0.0 : Math.max(0.0, (double)var4 - BuildController.this.mc.player.getY()) * 6.0);
                        if (var9) {
                           var15 += 1000000.0;
                        }

                        boolean var17 = var10 || this.have(var12) > 0;
                        if (var17) {
                           var8.ready++;
                           (var9 ? this.waterByKey : this.readyByKey).computeIfAbsent(var5, var0 -> new ArrayList<>()).add(var3);
                           if (var15 < this.bestReadyScore.getOrDefault(var5, Double.MAX_VALUE)) {
                              this.bestReadyScore.put(var5, var15);
                              this.bestReady.put(var5, var3);
                           }
                        } else if (BuildController.this.missing.getOrDefault(var12, 0) <= BuildController.this.ticks
                           && var15 < this.bestMissingScore.getOrDefault(var5, Double.MAX_VALUE)) {
                           this.bestMissingScore.put(var5, var15);
                           this.bestMissing.put(var5, var3);
                        }
                     } else if (var10 || this.have(var12) > 0) {
                        var8.ready++;
                        this.deferredByKey.computeIfAbsent(var5, var0 -> new ArrayList<>()).add(var3);
                     }
                  }
               }
            }
         }

         if (this.index < BuildController.this.plan.targets.size()) {
            return false;
         } else {
            if (!this.finished && this.choose) {
               this.finished = true;
               if (BucketRules.solidGate(this.solidTotal(), BuildController.this.config.layerMode) > 0) {
                  this.waterRemaining.forEach((var1x, var2) -> {
                     LayerPlanner.Stat var3x = this.layers.get(var1x);
                     if (var3x != null) {
                        var3x.remaining = var3x.remaining - var2;
                        var3x.ready = var3x.ready - this.waterByKey.getOrDefault(var1x, List.of()).size();
                        if (var3x.remaining <= 0) {
                           this.layers.remove(var1x);
                        }
                     }
                  });
               }
            }

            return true;
         }
      }
   }

   public static enum Stage {
      IDLE,
      LOAD,
      SELECT,
      GOALS,
      SEARCH,
      MOVE,
      EQUIP,
      AIM,
      WAIT_BLOCK,
      ORDER,
      HOME,
      CRAFTER,
      FILL,
      STASH,
      SELL,
      WAIT_MATERIAL,
      CLEANUP,
      MINE,
      PIT,
      NEXT;

      private Stage() {
      }
   }

    private PlacementRecoveryBudget recoveryBudget() {
        if (recoveryBudget == null) recoveryBudget = new PlacementRecoveryBudget();
        return recoveryBudget;
    }


    private void notePlacementClick(BlockPos pos) {
        InventoryAccess.trace(mc, "bấm đặt @ " + pos.toShortString());
        recoveryBudget().clicked(pos.asLong(), ticks);
    }


    private void qualityPlacementVerifiedOriginal(BlockPos pos) {
        recoveryBudget().verified(pos.asLong());
        placeFails = recoveryBudget().outstanding();
    }


    public void resetPlacementRecovery() {
      this.resetQualityRecovery();
        recoveryBudget().reset();
        placeFails = 0;
        failCountedAt.clear();
    }


    private boolean checkWaitFailure() {
        if (pending != null && mc.world.getBlockState(pending.pos()).isReplaceable()
            && !done(mc.world.getBlockState(pending.pos()), pending.state()))
            notePlaceFail(pending.pos(), "đã click nhưng server không nhận khối");
        return !running;
    }


    private void noteTemporaryPlacementVerified() {
        if (temporaryPending && pending != null && mc.world != null) {
            net.minecraft.block.BlockState actual = mc.world.getBlockState(pending.pos());
            if (done(actual, pending.state()) || StateMatch.placementMatches(actual, pending.state()))
                notePlacementVerified(pending.pos());
        }
    }


    private boolean autoEat() {
        Stage before = stage;
        // Eating is allowed during mining and while waiting for health to recover.
        if (!running || stage == Stage.MINE || stage == Stage.PIT) stage = Stage.SELECT;
        try { return autoEatBuild(); }
        finally { stage = before; }
    }


    private void cancelSurvivalSupplies() {
        if (survivalSupplies != null) survivalSupplies.cancel(mc);
    }


    private boolean survivalSupplyTick() {
        if (Reconnector.active() || (!running && !wanted) || mc.player == null || mc.world == null
            || mc.interactionManager == null || !mc.player.isAlive() || mc.player.isCreative()
            || mc.player.isSpectator() || mc.player.isInLava() || mc.player.hasVehicle()) {
            cancelSurvivalSupplies();
            return false;
        }
        if (survivalSupplies != null && survivalSupplies.active()) {
            release();
            Input.key(mc.options.useKey, false);
            // 3.0.121: theo dõi cả menu /shop của việc mua đồ ăn/cúp → đóng menu xong cũng kiểm tra lại túi đồ trước khi đặt
            InventoryAccess.menuTick(mc, false);
            boolean active = survivalSupplies.tick(mc, ticks);
            status = survivalSupplies.status();
            confirmedAt = lastProgress = ticks;
            if (!active) {
                AutoBuildMod.LOGGER.info("[AutoBuild] Tiếp tế: {} → tiếp tục", status);
                aim = null; anglesFor = null;
                // A paused health recovery can now eat before waiting for regeneration.
                autoNextAt = ticks;
            }
            return true;
        }
        if (eating && !running) {
            release();
            // 3.0.121: lúc tạm dừng vẫn xử lý đồng bộ túi đồ để cầm được đồ ăn ngay (không phải chờ 8 giây)
            InventoryAccess.menuTick(mc, wanted);
            return autoEat();
        }
        if (plan == null || preparing || (running && menuStage()) || shopping || browsing
            || eating || pillar || pending != null || mc.currentScreen != null
            || !mc.player.isOnGround() || overhanging()) return false;
        boolean needsFood = mc.player.getHungerManager().getFoodLevel() <= 14 && foodItem() == null;
        boolean needsPick = running && !SurvivalSupplies.hasPickaxe(mc);
        if (survivalSupplies == null) survivalSupplies = new SurvivalSupplies(config);
        // 3.0.122: cần xô rỗng để múc nước khỏi khối trước khi đập → mua ở /shop
        if (needEmptyBucket && InventoryAccess.count(mc, Items.BUCKET) > 0) needEmptyBucket = false;
        boolean needsBucket = running && needEmptyBucket;
        if ((needsFood || needsPick || needsBucket) && survivalSupplies.start(mc, ticks, needsFood, needsPick, needsBucket)) {
            stopEat("tiếp tế");
            release();
            Input.key(mc.options.useKey, false);
            if (mc.interactionManager.isBreakingBlock()) mc.interactionManager.cancelBlockBreaking();
            status = survivalSupplies.status();
            AutoBuildMod.LOGGER.info("[AutoBuild] Thiếu {} → tự mua ở /{}", needsFood ? "đồ ăn" : "cúp", config.shopCommand);
            return true;
        }
        if (!running && wanted && mc.player.getHealth() <= config.minHealth
            && mc.currentScreen == null && foodItem() != null) {
            release();
            InventoryAccess.menuTick(mc, true);
            return autoEat();
        }
        return false;
    }


    private void qualityStopMovement() {
        Input.key(mc.options.forwardKey, false); Input.key(mc.options.backKey, false);
        Input.key(mc.options.leftKey, false); Input.key(mc.options.rightKey, false);
        Input.key(mc.options.jumpKey, false);
        Input.forceSprint(mc, false, ticks);
    }



    private void qualityReleaseForTick() {
        boolean held = mc.options.attackKey.isPressed();
        release();
        // Only the routine tick reset preserves a requested held attack. Explicit
        // release/pause/menu transitions still release the mouse immediately.
        if (held && running && qualityAttackPos != null && ticks - qualityAttackTick <= 1
            && mc.player != null && mc.world != null && mc.currentScreen == null
            && Input.canHoldAttack(mc) && !mc.world.getBlockState(qualityAttackPos).isReplaceable()
            && Input.crosshairOn(mc, qualityAttackPos, null)) Input.key(mc.options.attackKey, true);
    }



    private void tickInner() {
        try { qualityTickInnerOriginal(); }
        finally { qualityFinishInputs(); }
    }



    private void qualityFinishInputs() {
        if (mc.player != null && mc.world != null && mc.options != null) {
            // Pause/userStop already release mod inputs. While idle, the physical
            // mouse belongs to the user and must remain usable for manual mining.
            if (!running) qualityAttackPos = null;
            else if (qualityAttackPos != null) {
                // 3.0.122: người thật giữ chuột liên tục; vài tick mod bận việc khác (chưa gọi breakBlock) thì vẫn giữ,
                // miễn tâm ngắm còn trên đúng khối đang đập. Nhả giữa chừng = game huỷ đập, server tính lại từ đầu.
                BlockHitResult live = PlacementSolver.ray(mc, reach());
                boolean keep = ticks - qualityAttackTick <= 3 && mc.currentScreen == null && Input.canHoldAttack(mc)
                    && !Input.usePending() && qualityAttackPos.equals(mining)
                    && !mc.world.getBlockState(qualityAttackPos).isReplaceable()
                    && live.getType() == HitResult.Type.BLOCK && live.getBlockPos().equals(qualityAttackPos);
                if (keep) Input.key(mc.options.attackKey, true);
                else { Input.key(mc.options.attackKey, false); qualityAttackPos = null; }
            }
            qualityMovementGuard();
            // 3.0.123: đang chờ đứng yên để bấm túi đồ (đổi đồ / xin gửi lại túi đồ) → dừng hẳn, thôi chạy nhanh. Đang bay (nhảy) thì
            // không phanh giữa không trung
            if (running && InventoryAccess.stillWanted() && mc.player.isOnGround() && qualityAttackPos == null) {
                qualityStopMovement();
                Input.forceSprint(mc, false, ticks);
                if (!status.startsWith("Dừng lại")) status = "Dừng lại để đổi đồ trong túi (như người thật mở túi)";
            }
            // 3.0.123: tay trái luôn cầm totem
            if (running && ticks - offhandCheckAt >= 40 && mc.currentScreen == null && !shopping && pending == null) {
                offhandCheckAt = ticks;
                InventoryAccess.offhandTotem(mc);
            }
        }
    }

    private int offhandCheckAt = -1000;



    private boolean qualityValidatedTravel() {
        if (stage != Stage.MOVE || terrain == null || path == null || pathIndex <= 0 || pathIndex >= path.size()) return false;
        var previous = path.get(pathIndex - 1); var next = path.get(pathIndex);
        int span = Math.abs(next.x() - previous.x()) + Math.abs(next.z() - previous.z());
        boolean gap = (next.x() == previous.x() || next.z() == previous.z()) && span >= 2 && span <= 4;
        boolean down = next.y() < mc.player.getY() - .6;
        return (gap || down) && terrain.standable(next) && floorConfirmed(next) && terrain.edgeStillValid(previous, next);
    }



    private int qualityDropDepth() {
        if (stage == Stage.MOVE && path != null && pathIndex < path.size())
            return Math.max(1, Math.min(6, (int)Math.ceil(mc.player.getY() - path.get(pathIndex).y())));
        return 1;
    }



    private boolean qualityDescentCorridor(double x, double z) {
        var from = path.get(pathIndex - 1); var to = path.get(pathIndex);
        double dx = to.x() - from.x(), dz = to.z() - from.z(), span = Math.hypot(dx, dz);
        if (span < .01) return false;
        dx /= span; dz /= span;
        double px = x - from.x() - .5, pz = z - from.z() - .5;
        double along = px * dx + pz * dz;
        return Math.abs(px * dz - pz * dx) <= .55 && along >= -.6 && along <= span + .6;
    }



    private boolean qualityFootprintSafe(double x, double y, double z, boolean stepDown) {
        var body = mc.player.getBoundingBox();
        double halfX = (body.maxX - body.minX) * .5 - .001;
        double halfZ = (body.maxZ - body.minZ) * .5 - .001;
        double minX = x - halfX, maxX = x + halfX, minZ = z - halfZ, maxZ = z + halfZ;
        // The validated descent grants lower support only along its own edge.
        // Sideways residual velocity must not authorize dropping into another hole.
        int depth = stepDown && qualityDescentCorridor(x, z) ? qualityDropDepth() : 0;
        double minFeet = y - depth;
        double rise = mc.options.jumpKey.isPressed() ? 1.05 : .6;
        boolean supported = false;
        double supportTop = Double.NEGATIVE_INFINITY;
        List<BlockPos> hazards = new ArrayList<>();
        // Vanilla collision support is an overlapping area, not support at all
        // four corners. Partial edge support must allow walking back onto a floor.
        for (int bx = (int)Math.floor(minX); bx <= (int)Math.floor(maxX); bx++)
            for (int bz = (int)Math.floor(minZ); bz <= (int)Math.floor(maxZ); bz++)
                for (int by = (int)Math.floor(minFeet - .65); by <= (int)Math.floor(y + 1.8); by++) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    if (!mc.world.isChunkLoaded(pos)) return false;
                    BlockState actual = mc.world.getBlockState(pos);
                    if (Navigation.hazardDry(actual)) {
                        // A hazard below the level of an existing floor does not
                        // touch the player. The unsupported case is rejected below.
                        hazards.add(pos);
                        continue;
                    }
                    for (net.minecraft.util.math.Box box : actual.getCollisionShape(mc.world, pos).getBoundingBoxes()) {
                        double top = by + box.maxY;
                        if (top < minFeet - .65 || top > y + rise || ackWait(pos)) continue;
                        double overlapX = Math.min(maxX, bx + box.maxX) - Math.max(minX, bx + box.minX);
                        double overlapZ = Math.min(maxZ, bz + box.maxZ) - Math.max(minZ, bz + box.minZ);
                        if (overlapX > 1.0E-5 && overlapZ > 1.0E-5) {
                            supported = true; supportTop = Math.max(supportTop, top);
                        }
                    }
                }
        if (!supported) return false;
        for (BlockPos hazard : hazards)
            if (hazard.getY() + 1 > supportTop + 1.0E-5 && hazard.getY() < y + 1.8) return false;
        return true;
    }



    private boolean qualityJumpDirectionSafe(Vec3d velocity, double ix, double iz) {
        var from = path.get(pathIndex - 1); var to = path.get(pathIndex);
        double dx = to.x() - from.x(), dz = to.z() - from.z(), span = Math.hypot(dx, dz);
        dx /= span; dz /= span;
        double px = mc.player.getX() - from.x() - .5, pz = mc.player.getZ() - from.z() - .5;
        double acceleration = mc.options.sprintKey.isPressed() ? .13 : .10;
        double vx = velocity.x + ix * acceleration, vz = velocity.z + iz * acceleration;
        if (mc.options.sprintKey.isPressed()) { vx += dx * .2; vz += dz * .2; }
        if (Math.abs(px * dz - pz * dx) > .55 || Math.abs((px + vx) * dz - (pz + vz) * dx) > .55
            || vx * dx + vz * dz < .02) return false;
        var airborne = mc.player.getBoundingBox().offset(vx, .42, vz);
        if (!mc.world.isSpaceEmpty(mc.player, airborne)) return false;
        for (int bx = (int)Math.floor(airborne.minX); bx <= (int)Math.floor(airborne.maxX); bx++)
            for (int bz = (int)Math.floor(airborne.minZ); bz <= (int)Math.floor(airborne.maxZ); bz++)
                for (int by = (int)Math.floor(airborne.minY); by <= (int)Math.floor(airborne.maxY); by++) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    if (!mc.world.isChunkLoaded(pos) || Navigation.hazardDry(mc.world.getBlockState(pos))) return false;
                }
        return true;
    }



    private boolean qualitySweptStepSafe(double x, double y, double z, double dx, double dz, boolean stepDown) {
        int samples = Math.max(1, (int)Math.ceil(Math.hypot(dx, dz) / .08));
        for (int i = 1; i <= samples; i++) {
            double part = (double)i / samples;
            if (!qualityFootprintSafe(x + dx * part, y, z + dz * part, stepDown)) return false;
        }
        return true;
    }



    private boolean qualityTrajectorySafe(Vec3d velocity, double ix, double iz, double acceleration, boolean stepDown, int steps) {
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        double vx = velocity.x, vz = velocity.z;
        for (int step = 0; step < steps; step++) {
            vx += ix * acceleration; vz += iz * acceleration;
            if (!qualitySweptStepSafe(x, y, z, vx, vz, stepDown)) return false;
            x += vx; z += vz;
            BlockPos floor = BlockPos.ofFloored(x, y - .2, z);
            double friction = .91 * mc.world.getBlockState(floor).getBlock().getSlipperiness();
            vx *= friction; vz *= friction;
        }
        return true;
    }



    private boolean qualityMovementGuard() {
        if (!running || mc.player == null || mc.world == null || !mc.player.isOnGround()) return false;
        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double left = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        Vec3d v = mc.player.getVelocity();
        if (forward == 0 && left == 0 && v.horizontalLengthSquared() <= 1.0E-4) { qualityUnsafeSince = -1; return false; }
        double yaw = Math.toRadians(mc.player.getYaw()), norm = Math.max(1, Math.hypot(forward, left));
        double ix = (-Math.sin(yaw) * forward + Math.cos(yaw) * left) / norm;
        double iz = ( Math.cos(yaw) * forward + Math.sin(yaw) * left) / norm;
        boolean travel = qualityValidatedTravel();
        boolean gap = travel && Math.abs(path.get(pathIndex).x() - path.get(pathIndex - 1).x())
            + Math.abs(path.get(pathIndex).z() - path.get(pathIndex - 1).z()) >= 2;
        boolean stepDown = travel && !gap && path.get(pathIndex).y() < mc.player.getY() - .6;
        if (travel) {
            qualityCautiousUntil = 0;
            Input.forceSneak(mc, false, ticks);
            if (gap && mc.options.jumpKey.isPressed()) {
                if (qualityJumpDirectionSafe(v, ix, iz)) { qualityUnsafeSince = -1; return false; }
                Input.key(mc.options.jumpKey, false);
            }
        }
        if (!travel && (forward != 0 || left != 0) && ticks < qualityCautiousUntil) {
            Input.forceSneak(mc, true, ticks); Input.forceSprint(mc, false, ticks);
        }
        double acceleration = mc.options.sneakKey.isPressed() ? .03 : mc.options.sprintKey.isPressed() ? .13 : .10;
        // A distant preview only reduces speed. It cannot veto a valid first step
        // or assume we keep walking past a waypoint when the controller will turn.
        if (!travel && (forward != 0 || left != 0)
            && !qualityTrajectorySafe(v, ix, iz, acceleration, false, 3)) {
            qualityCautiousUntil = ticks + 8;
            Input.forceSneak(mc, true, ticks); Input.forceSprint(mc, false, ticks);
            acceleration = .03;
        }
        if (qualityTrajectorySafe(v, ix, iz, acceleration, stepDown, 1)) { qualityUnsafeSince = -1; return false; }
        qualityStopMovement(); Input.forceSneak(mc, true, ticks);
        if (qualityUnsafeSince <= 0) qualityUnsafeSince = ticks;
        status = "Bước tiếp theo ra khỏi sàn an toàn → tìm đường lại";
        if (stage == Stage.MOVE && ticks - qualityUnsafeSince >= 6 && ticks - qualityReplanAt >= 20) {
            qualityReplanAt = ticks; qualityUnsafeSince = -1;
            AutoBuildMod.LOGGER.info("[AutoBuild] {} @ {}", status, mc.player.getBlockPos().toShortString());
            BlockPos goal = current() == null ? mining : current().pos();
            if (!Objects.equals(goal, qualityNavTarget)) { qualityNavTarget = goal; qualityReplans = 0; }
            if (++qualityReplans > 2) navigationFailed("Bước tiếp theo thật sự ra khỏi sàn, thử đường/mục tiêu khác trước");
            else beginGoals(cleanupMode);
        }
        return true;
    }



    private Map<BlockPos, Integer> qualityRetries() {
        if (qualityRetryAt == null) qualityRetryAt = new HashMap<>();
        return qualityRetryAt;
    }



    private boolean qualityRepairReady(BlockPos pos) {
        // Navigation and layer postponements apply to repair work too. Auditing a wrong
        // cell must not immediately undo the decision to build somewhere else first.
        if (skipped.contains(pos) || deferred.getOrDefault(pos, 0) > ticks) return false;
        if (repairTries.getOrDefault(pos, 0) > 4 || repairs.getOrDefault(pos, 0) > 2) {
            Integer retry = qualityRetries().get(pos);
            if (retry == null) {
                qualityRetries().put(pos.toImmutable(), ticks + 200);
                placedDone.remove(pos);
                return false;
            }
            if (ticks < retry) return false;
            repairs.remove(pos); repairTries.remove(pos); qualityRetries().remove(pos);
        }
        return qualityRetries().getOrDefault(pos, 0) <= ticks;
    }



    private boolean qualityNeedsRepair(BlockPos pos, BlockState actual) {
        Target wanted = plan == null ? null : plan.at(pos);
        // Existing queued blockers outside the schematic still need removal for navigation/support.
        return wanted == null || BuildQuality.repairNeeded(actual, wanted.state(), config.fastMode());
    }



    private void qualityObserve(Target wanted) {
        BlockPos pos = wanted.pos();
        if (!config.inYRange(pos.getY()) || !mc.world.isChunkLoaded(pos) || ackWait(pos)
            || recentPlaced.containsKey(pos) && ticks - recentPlaced.get(pos) < 20 + latencyTicks() * 3) return;
        BlockState actual = mc.world.getBlockState(pos);
        int action = BuildQuality.classify(actual, wanted.state(), config.fastMode());
        if (action == BuildQuality.COMPLETE) {
            if (!coverFor.containsKey(pos)) repairWrong.remove(pos);
            repairs.remove(pos); repairTries.remove(pos);
            qualityRetries().remove(pos);
            return;
        }
        placedDone.remove(pos);
        if (action != BuildQuality.REPAIR || !config.fixWrong || !breakableWrong(actual, pos) || blockerHeld(pos)
            || tntBreakRisk(pos) != null) return;
        // Retry limits cause a delay, never a false success or a permanent skipped cell.
        if (!qualityRepairReady(pos)) return;
        if (repairWrong.size() >= 64 && !repairWrong.containsKey(pos)) return;
        if (!repairWrong.containsKey(pos)) {
            noteWrong(pos);
            AutoBuildMod.LOGGER.info("[AutoBuild] {} @ {} cần {} → đưa vào hàng sửa", new Object[]{actual, pos.toShortString(), wanted.state()});
        }
        repairWrong.put(pos.toImmutable(), actual);
    }



    private boolean markPlacedDone(Set<BlockPos> destination, Object value) {
        if (!(value instanceof BlockPos pos) || plan == null || mc.world == null || !mc.world.isChunkLoaded(pos)) return false;
        Target wanted = plan.at(pos);
        if (wanted == null || !done(mc.world.getBlockState(pos), wanted.state())) {
            destination.remove(pos);
            if (wanted != null) qualityObserve(wanted);
            return false;
        }
        return destination.add(pos.toImmutable());
    }



    private void beginQualityRepair(BlockPos pos) {
        repairTries.merge(pos, 1, Integer::sum);
        release(); target = temporary = supportTarget = pending = null;
        aim = anglesFor = null; pillar = false; repairing = true; repairGoal = null;
        mining = pos.toImmutable(); miningTicks = repairStepTicks = 0; placedDone.remove(pos);
        setStage(Stage.MINE);
        status = "Phá khối sai, sau đó đặt lại đúng @ " + pos.toShortString();
        AutoBuildMod.LOGGER.info("[AutoBuild] {}", status);
    }



    private void qualityRepairEnded() {
        if (!repairing || mining == null || mc.world == null || !mc.world.isChunkLoaded(mining)) return;
        if (mc.world.getBlockState(mining).isReplaceable()) {
            repairs.remove(mining); repairTries.remove(mining); qualityRetries().remove(mining); placedDone.remove(mining);
        } else if (qualityNeedsRepair(mining, mc.world.getBlockState(mining))) {
            placedDone.remove(mining);
            qualityRetries().put(mining.toImmutable(), ticks + 100);
        }
    }



    private void resetQualityRecovery() {
        if (heldPlacementGuard != null) heldPlacementGuard.reset();
        if (qualityRetryAt != null) qualityRetryAt.clear();
        repairs.clear(); repairTries.clear();
        qualityWaterRequestPos = null;
    }



    private void qualityResetPlacementVeto() { tntVetoWhy = ""; }



    private void tntRefuse(Target intended, String reason, boolean configure) {
        // placeNow can also decline an unsynced hand or a mismatched receiver.
        // Only an actual TNT reason may remove/defer a target as unsafe.
        if (reason == null || reason.isBlank()) {
            status = "Chờ đúng đồ và đúng ô nhận trước khi đặt @ " + intended.pos().toShortString();
            return;
        }
        tntRefuseRisk(intended, reason, configure);
    }



    private BlockPos qualityReceiver(BlockHitResult hit) {
        BlockState face = mc.world.getBlockState(hit.getBlockPos());
        ItemStack held = mc.player.getMainHandStack();
        if (held.isOf(Items.WATER_BUCKET) && face.getBlock() instanceof FluidFillable fillable
            && fillable.canFillWithFluid(mc.player, mc.world, hit.getBlockPos(), face, Fluids.WATER))
            return hit.getBlockPos();
        if (held.getItem() instanceof BucketItem) return hit.getBlockPos().offset(hit.getSide());
        return face.isReplaceable() ? hit.getBlockPos() : hit.getBlockPos().offset(hit.getSide());
    }



    private void qualityPrimeWaterlog() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null
            || temporary != null || supportTarget != null || byTarget != null || pillar
            || mc.player.currentScreenHandler != mc.player.playerScreenHandler
            || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        Target intended = current();
        if (!waterIntoBlock(intended) || InventoryAccess.count(mc, Items.WATER_BUCKET) == 0) return;
        if (!mc.player.getMainHandStack().isOf(Items.WATER_BUCKET) && unacked == 0 && InventoryAccess.synced(mc))
            InventoryAccess.equip(mc, Items.WATER_BUCKET, config.workingHotbarSlot);
        qualityClickReady(intended);
    }



    private boolean qualityWaterlogAim() {
        Target intended = current();
        if (mc.player == null || mc.world == null || temporary != null || supportTarget != null
            || pillar || !waterIntoBlock(intended)) return false;
        // The general AIM branch can lean while crouching, but waterlogging uses a
        // standing ray. Release crouch before solving so these two paths cannot fight.
        release();
        Input.forceSneak(mc, false, ticks);
        bridgeTicks = bucketEdgeTicks = 0; bucketEdgeFor = null;
        if (aim != null && aim.sneak()) { aim = null; anglesFor = null; }
        if (!mc.player.getMainHandStack().isOf(Items.WATER_BUCKET)) {
            setStage(Stage.EQUIP);
            return true;
        }
        if (!qualityClickReady(intended)) return true;
        if (mc.player.isSneaking() || mc.player.isInSneakingPose()) {
            status = "Nhả Shift để đổ nước vào " + intended.pos().toShortString();
            return true;
        }
        if (!mc.player.isOnGround() && !inWaterNow()) {
            status = "Chờ chạm đất để đổ nước vào " + intended.pos().toShortString();
            return true;
        }
        if (mc.player.getVelocity().horizontalLengthSquared() > (inWaterNow() ? 9.0E-4 : 1.0E-4)) {
            brake(); status = "Dừng tại ô đổ nước " + intended.pos().toShortString();
            return true;
        }
        BlockHitResult live = PlacementSolver.ray(mc, reach());
        if (live.getType() == HitResult.Type.BLOCK && live.getBlockPos().equals(intended.pos())
            && mc.player.getEyePos().squaredDistanceTo(live.getPos()) <= reach() * reach()) {
            // Every face of this fillable block leads to the same receiver. Reuse
            // the actual ray instead of repeatedly selecting another sampled face.
            aim = new Aim(live, mc.player.getYaw(), mc.player.getPitch(), false, false);
        } else {
            aim = PlacementSolver.solve(mc, mc.player.getEyePos(), intended, reach(), true, seed(), false);
        }
        anglesFor = null;
        if (aim == null) {
            bucketCloser = true;
            badStands.computeIfAbsent(intended.pos().toImmutable(), ignored -> new HashSet<>())
                .add(Navigation.cell(mc.player.getBlockPos()));
            status = "Ngoài tầm đổ nước → tìm chỗ đứng gần hơn @ " + intended.pos().toShortString();
            AutoBuildMod.LOGGER.info("[AutoBuild] {}", status);
            if (++goalRetries > 5) navigationFailed("Chưa có góc đổ nước trong tầm từ các chỗ đứng đã thử");
            else beginGoals(false);
            return true;
        }
        status = "Đổ nước vào " + intended.pos().toShortString();
        if (cooldown <= 0 && !clickNow(intended, PlacementSolver.aimYaw(mc, aim), PlacementSolver.aimPitch(mc, aim))
            && stage == Stage.AIM) { aim = null; anglesFor = null; }
        return true;
    }



    private static boolean qualityCrosshairOn(MinecraftClient client, BlockPos pos, Direction side, BuildController controller) {
        Target intended = controller.current();
        return Input.crosshairOn(client, pos, controller.waterIntoBlock(intended) && intended.pos().equals(pos) ? null : side);
    }



    private void qualityPrepareHand() {
        if (mc.player == null || mc.world == null) return;
        Target intended = current();
        if (intended != null) qualityClickReady(intended);
    }



    private int qualityCautionTicks() { return Math.max(60, latencyTicks() * 4); }



    private int qualityRetryTicks() { return Math.max(6, 2 + latencyTicks() * 2); }



    private int qualityDeferredRetryTicks() { return Math.max(20, 8 + latencyTicks() * 4); }



    private int qualityHoldTicks() {
        Target intended = pending;
        if (intended == null || mc.world == null) return 8;
        BlockState actual = mc.world.getBlockState(intended.pos());
        return StateMatch.orientationFree(intended.state()) && !Materials.bucket(actual, intended.state())
            && !intended.state().hasBlockEntity() && !Interaction.interactable(intended.state()) ? 3 : 8;
    }



    private boolean qualityHeldItemReady(Item required) {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null
            || mc.player.currentScreenHandler != mc.player.playerScreenHandler
            || !mc.player.currentScreenHandler.getCursorStack().isEmpty() || !InventoryAccess.synced(mc)) {
            if (heldPlacementGuard != null) heldPlacementGuard.reset();
            return false;
        }
        if (heldPlacementGuard == null) heldPlacementGuard = new HeldPlacementGuard();
        ItemStack held = mc.player.getMainHandStack();
        boolean ready = heldPlacementGuard.ready(held.isEmpty() ? null : held.getItem(), required,
            mc.player.getInventory().selectedSlot, mc.player.currentScreenHandler.getRevision(), ticks, latencyTicks() + 1);
        if (!ready) status = "Chờ đổi đúng đồ trên tay: cần " + required.getName().getString();
        return ready;
    }



    private boolean qualityClickReady(Target intended) {
        if (intended == null || mc.world == null) return false;
        BlockState actual = mc.world.getBlockState(intended.pos());
        if (StateMatch.configurable(actual, intended.state())) return true;
        return qualityHeldItemReady(Materials.item(actual, intended.state()));
    }



    private boolean qualityPlaceReady(BlockHitResult hit) {
        if (mc.player == null || mc.world == null) return false;
        Target onFace = plan == null ? null : plan.at(hit.getBlockPos());
        BlockState face = mc.world.getBlockState(hit.getBlockPos());
        if (onFace != null && !mc.player.isSneaking() && StateMatch.configurable(face, onFace.state())) return true;
        BlockPos receiver = qualityReceiver(hit);
        if (pending != null && !pending.pos().equals(receiver)) return false;
        Target intended = pending != null && pending.pos().equals(receiver) ? pending
            : byTarget != null && byTarget.pos().equals(receiver) ? byTarget
            : current() != null && current().pos().equals(receiver) ? current() : null;
        Target schematic = plan == null ? null : plan.at(receiver);
        if (intended == null && schematic != null) intended = resolve(schematic);
        if (intended != null) return qualityClickReady(intended);
        // Scaffolds outside the design also wait for the selected hand to stabilize.
        Item held = mc.player.getMainHandStack().getItem();
        return held instanceof net.minecraft.item.BlockItem && qualityHeldItemReady(held);
    }



    public boolean qualitySchematicReady() {
        return plan != null && plan.loaded && plan.unchanged();
    }


    private boolean bucketLands(Target intended) {
        if (!waterIntoBlock(intended)) return qualityBucketLandsOriginal(intended);
        BlockHitResult live = PlacementSolver.ray(mc, Math.min(reach(), mc.player.getBlockInteractionRange()));
        if (live.getType() != HitResult.Type.BLOCK || !live.getBlockPos().equals(intended.pos())
            || Navigation.GHOSTS.contains(intended.pos()) || ackWait(intended.pos())) return false;
        BlockState actual = mc.world.getBlockState(intended.pos());
        // A confirmed dry leaf remains fillable even immediately after its body
        // was placed. A mere recentPlaced timestamp is not a missing receiver.
        return actual.getBlock() instanceof FluidFillable fillable
            && fillable.canFillWithFluid(mc.player, mc.world, intended.pos(), actual, Fluids.WATER);
    }


    private boolean placeNow(BlockHitResult hit) {
      this.qualityResetPlacementVeto();
      if (!this.qualityPlaceReady(hit)) {
         return false;
      }

        Target intended = pending != null ? pending : current();
        if (!waterIntoBlock(intended) || !mc.player.getMainHandStack().isOf(Items.WATER_BUCKET))
            return qualityPlaceNowOriginal(hit);
        if (!hit.getBlockPos().equals(intended.pos()) || !rotationSent() || !bucketReady(intended) || tntVeto(hit)) return false;
        placeLockUntil = ticks + 3;
        // Filled BucketItem pours in use/interactItem, not useOnBlock. Calling
        // interactBlock first can consume the click without invoking the bucket.
        notePlacementClick(intended.pos());
        BlockPos at = intended.pos().toImmutable();
        qualityWaterRequestPos = at; qualityWaterRequestAt = ticks;
        // 3.0.122: chuột phải thật (Input.queueUse)
        Input.queueUse(mc, hit, true, "đổ xô vào lá @ " + at.toShortString(), () -> {
            Item heldBefore = mc.player.getMainHandStack().getItem();
            int countBefore = mc.player.getMainHandStack().getCount();
            ActionResult result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            qualityBucketUsed(heldBefore, countBefore, "đổ xô vào lá");
            if (result != null && result.isAccepted()) {
                mc.player.swingHand(Hand.MAIN_HAND);
                qualityWaterRequestAt = ticks;
                AutoBuildMod.LOGGER.info("[AutoBuild] Đã gửi dùng xô nước @ {} → chờ nước vào lá", at.toShortString());
                return;
            }
            qualityWaterRequestPos = null; pourPos = null;
            cooldown = qualityRetryTicks();
            notePlaceFail(at, "thao tác dùng xô nước chưa được nhận");
            status = "Chưa dùng được xô → giữ mục tiêu và thử lại @ " + at.toShortString();
        });
        return true;
    }


    private boolean waterAskedTooMuch(BlockPos pos) {
        // Seeing a dry leaf body (or waiting for aim/hand/ACK) is not a pour.
        return pos.equals(qualityWaterRequestPos) && qualityWaterAskedOriginal(pos);
    }


    private boolean stackBuckets() {
        if (stage == Stage.WAIT_BLOCK && pending != null && pending.pos().equals(qualityWaterRequestPos)) return false;
        // 3.0.121: trước đây gộp xô mỗi 2 tick (0,1 giây), mỗi lần 2–3 cú bấm dựa trên túi đồ client ĐOÁN (xô vừa đổ chưa chắc
        // đã đổ, cú gộp trước chưa được server trả lời). Server làm khác dự đoán → đồ bị tráo giữa các ô, con trỏ cầm đồ "ma"
        // (log 22:17:58 "Cất món đồ trên con trỏ" ×3) → thanh nóng client khác server → đặt nhầm loại. Giờ: chỉ gộp khi túi đồ
        // đã được server xác nhận (không chờ ACK, không đang đồng bộ, đã qua 1 lượt ping sau cú bấm trước).
        if (unacked > 0 || !InventoryAccess.synced(mc)) return false;
        boolean clicked = qualityStackBucketsOriginal();
        if (clicked) InventoryAccess.noteClick(mc, "gộp xô rỗng");
        return clicked;
    }


    private void notePlacementVerified(BlockPos pos) {
        Target wanted = plan == null ? null : plan.at(pos);
        if (wanted != null && mc.world != null && mc.world.isChunkLoaded(pos)
            && (Materials.waterlog(mc.world.getBlockState(pos), wanted.state())
                || wanted.state().contains(net.minecraft.state.property.Properties.WATERLOGGED)
                    && (Boolean) wanted.state().get(net.minecraft.state.property.Properties.WATERLOGGED) && ackWait(pos))) return;
        qualityPlacementVerifiedOriginal(pos);
    }


    private boolean qualityWaterlogWait() {
        if (pending == null || temporaryPending || mc.world == null
            || !pending.state().contains(net.minecraft.state.property.Properties.WATERLOGGED)
            || !(Boolean) pending.state().get(net.minecraft.state.property.Properties.WATERLOGGED)) return false;
        BlockPos pos = pending.pos();
        BlockState actual = mc.world.getBlockState(pos);
        boolean requested = pos.equals(qualityWaterRequestPos);
        if (requested) {
            release(); Input.forceSneak(mc, false, ticks);
            int elapsed = ticks - qualityWaterRequestAt;
            if (done(actual, pending.state()) && elapsed >= 2 && !ackWait(pos)) {
                InventoryAccess.pourConfirmed(mc);
                qualityWaterRequestPos = null;
                waterAsks.remove(pos); waterAsksAt.remove(pos);
                return false; // The retained confirmation path finishes the target once.
            }
            int timeout = 8 + latencyTicks() * 3;
            if (elapsed < timeout || ackWait(pos) && elapsed < Math.max(60, timeout)) {
                status = "Chờ server xác nhận nước vào lá @ " + pos.toShortString();
                return true;
            }
            if (Materials.waterlog(actual, pending.state())) {
                notePlaceFail(pos, "đã dùng xô nhưng server chưa xác nhận nước vào lá");
                if (!running || Reconnector.active()) return true;
                boolean abandon = waterAskedTooMuch(pos);
                qualityWaterRequestPos = null; pourPos = null;
                pending = null; aim = null; anglesFor = null;
                if (abandon) {
                    queue.remove(target); target = null; beginScan();
                } else {
                    setStage(Stage.EQUIP);
                    status = "Nước chưa vào → thử lại đúng ô lá @ " + pos.toShortString();
                }
                return true;
            }
            qualityWaterRequestPos = null;
            return false; // A vanished/changed receiver keeps the existing recovery path.
        }
        if (!Materials.waterlog(actual, pending.state())) return false;
        // The leaf body exists, but no water use has been sent. Do not enqueue a
        // successful wet-leaf verification or count this as a failed pour.
        pending = null; aim = null; anglesFor = null;
        setStage(Stage.EQUIP);
        status = "Lá còn khô → giữ mục tiêu và chuẩn bị xô @ " + pos.toShortString();
        return true;
    }


    /**
     * 3.0.121: dùng xô (đổ/múc) → client tự đổi món trên tay theo dự đoán (Xô nước → Xô rỗng…). Server từ chối thì KHÔNG gửi lại ô đó,
     * client giữ món sai mãi. Có đổi → túi đồ "chưa kiểm chứng" cho tới khi server xác nhận nước vào hoặc gửi lại cả túi.
     */
    private void qualityBucketUsed(Item before, int count, String what) {
        if (mc.player == null) return;
        ItemStack now = mc.player.getMainHandStack();
        if (now.getItem() == before && now.getCount() == count) return;
        String id = net.minecraft.registry.Registries.ITEM.getId(before).getPath();
        if (!id.endsWith("bucket")) return;
        InventoryAccess.trace(mc, what + ": " + id + " → " + net.minecraft.registry.Registries.ITEM.getId(now.getItem()).getPath() + " (dự đoán)");
        // đổ xô: server xác nhận nước vào ô thì khỏi đồng bộ; múc: luôn kiểm tra lại cả túi
        if (what.startsWith("múc")) InventoryAccess.markDirty(mc, what + " (client tự đổi món trên tay)");
        else InventoryAccess.markPour(mc);
    }

}
