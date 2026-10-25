package com.donutautomine.mine;

import com.donutautomine.AutoMineMod;
import com.donutautomine.MineConfig;
import com.donutautomine.order.FillerReserve;
import com.donutautomine.order.GuiWatch;
import com.donutautomine.order.Inv;
import com.donutautomine.order.OrderCollector;
import com.donutautomine.order.SellJob;
import com.donutautomine.order.ShopBrowser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.world.EmptyBlockView;

public final class MineController {
   private final MinecraftClient mc = MinecraftClient.getInstance();
   private final MineConfig config;
   private final Aim aim = new Aim();
   private final Ghost ghost;
   private final Tools tools;
   private final SellJob sellJob;
   private final ShopBrowser shop;
   private final OrderCollector fillerOrders;
   private Region region;
   private int layer = -1;
   private LayerPlan plan;
   private MineController.State state = MineController.State.IDLE;
   private String status = "Chưa chạy";
   private String lastFail = "";
   private int ticks;
   private MineController.Task task;
   private int errors;
   /** 1.14.4: nghỉ tới tick này rồi làm lại (thay cho dừng hẳn) */
   private int cooldownUntil;
   private String cooldownWhy = "";
   private int strikes;
   private int cleared;
   private long startedAt;
   private int layerRemaining = -1;
   private int layerCells = 1;
   private int layerUnloaded;
   private int scanAt = -1000;
   private int calSamples;
   private double calSum;
   private Boolean threeByThree;
   private final Map<BlockPos, Integer> fails = new HashMap<>();
   private final Set<BlockPos> skipped = new HashSet<>();
   private final Set<BlockPos> avoid = new HashSet<>();
   private final Map<BlockPos, Integer> plugFails = new HashMap<>();
   private int nextSellAt;
   private int noXpUntil = -1;
   private int foodWarnAt = -10000;
   private int fillerWarnAt = -10000;
   private boolean durWarned;
   private BlockPos lastSafe;
   private int stuckFallTicks;
   private final Set<Long> badPits = new HashSet<>();
   private int progressAt;
   private int progressMark = -1;
   private int recoveries;
   /** chỉ để kiểm thử: tổng số lần làm lại vì kẹt */
   public int recoveriesTotal;
   private int[] laneSweep;
   private int laneSweepTries;
   private String prevTask = "—";
   private long climbAnnounced = Long.MIN_VALUE;
   private long entryAnnounced = Long.MIN_VALUE;
   private boolean entryNeeded;
   private int entryAttempts;
   private int entryCorner;
   private final Set<Long> triedCorners = new HashSet<>();
   private int descendFails;
   private boolean entryOpened;
   private int failStreak;
   private int failMark = Integer.MIN_VALUE;
   private final Map<BlockPos, Integer> retryAt = new HashMap<>();
   private int layerQuietSince = -1;
   private int finalQuietSince = -1;
   /** 1.14.2: kiểm tra sau mỗi nhát chạy nền (không bắt việc đập đứng chờ): {tick, khối, các ô 3x3 lúc đập} */
   private final List<Object[]> deferred = new ArrayList<>();
   private float stableYaw = Float.NaN;
   private float stablePitch = Float.NaN;
   private int rotationStableTicks;
   private final HeldPlacementGuard heldFiller = new HeldPlacementGuard();
   private boolean fillerStable;
   private final Map<BlockPos, Integer> recentStrikes = new HashMap<>();
   private final Set<BlockPos> waterDams = new HashSet<>();
   private final Set<BlockPos> waterReplacements = new LinkedHashSet<>();
   private int waterCheckAt = -100;
   private int waterRetryAt;

   private boolean damInPlane(BlockPos var1, Direction var2) {
      return Cells.plane(var1, var2).stream().anyMatch(this.waterDams::contains);
   }

   private String regionKey() {
      return this.region.x0 + ":" + this.region.y0 + ":" + this.region.z0 + ":" + this.region.x1 + ":" + this.region.y1 + ":" + this.region.z1;
   }

   private void saveWaterDams() {
      this.config.waterDamRegion = this.regionKey();
      this.config.waterDamCells = this.waterDams.stream().<Long>map(BlockPos::asLong).toList();
      this.config.saveQuietly();
   }

   public void createRegionAtPlayer() {
      if (this.mc.player != null && this.mc.world != null) {
         this.config.regionX = this.feet().getX();
         this.config.regionTopY = this.feet().getY() - 1;
         this.config.regionZ = this.feet().getZ();
         this.config.regionPlaced = true;
         this.config.customRegion = true;
         this.config.saveQuietly();
         this.loadRegion();
      }
   }

   public MineController(MineConfig var1) {
      this.config = var1;
      this.ghost = new Ghost(var1);
      this.tools = new Tools(var1);
      this.sellJob = new SellJob(var1);
      this.shop = new ShopBrowser(var1);
      this.fillerOrders = new OrderCollector(var1);
   }

   public MineController.State state() {
      return this.state;
   }

   public boolean running() {
      return this.state == MineController.State.RUNNING;
   }

   public String status() {
      return this.status;
   }

   public Region region() {
      return this.region;
   }

   public int layer() {
      return this.layer;
   }

   public LayerPlan plan() {
      return this.plan;
   }

   public int strikes() {
      return this.strikes;
   }

   public int cleared() {
      return this.cleared;
   }

   public int layerRemaining() {
      return this.layerRemaining;
   }

   public int layerCells() {
      return this.layerCells;
   }

   public Boolean threeByThree() {
      return this.threeByThree;
   }

   public Tools tools() {
      return this.tools;
   }

   public int skippedCount() {
      return this.skipped.size();
   }

   public String taskLabel() {
      return this.task == null ? "—" : this.task.label();
   }

   /** 1.14.4: số tick không đào thêm được gì thì làm lại, không dừng (kiểm thử đặt ngắn hơn) */
   public static int watchdogTicks = 1200;

   /** chỉ để kiểm thử: dòng chẩn đoán của việc đang chạy (gametest bật debugTrace) */
   public static boolean debugTrace;
   public String debugLine = "";

   public double strikesPerMinute() {
      if (this.startedAt <= 0L) {
         return 0.0;
      } else {
         double var1 = (System.currentTimeMillis() - this.startedAt) / 60000.0;
         return var1 < 0.05 ? 0.0 : this.strikes / var1;
      }
   }

   public void say(String var1) {
      AutoMineMod.LOGGER.info("[AutoMine] {}", var1);
      if (this.mc.player != null) {
         this.mc.player.sendMessage(Text.literal("§b[AutoMine] §f" + var1), false);
      }
   }

   public void toggle() {
      if (this.running()) {
         this.pause("Đã tạm dừng. Nhấn J để chạy tiếp.");
      } else {
         this.start();
      }
   }

   public boolean loadRegion() {
      try {
         Region var1;
         if (this.config.customRegion) {
            if (!this.config.regionPlaced) {
               if (this.mc.player == null) {
                  throw new IllegalStateException("Vào thế giới rồi tạo vùng đào.");
               }

               this.config.regionX = this.feet().getX();
               this.config.regionTopY = this.feet().getY() - 1;
               this.config.regionZ = this.feet().getZ();
               this.config.regionPlaced = true;
               this.config.saveQuietly();
            }

            var1 = Region.dimensions(
               this.config.regionX, this.config.regionTopY, this.config.regionZ, this.config.regionSizeX, this.config.regionSizeY, this.config.regionSizeZ
            );
            if (var1.y0 < this.mc.world.getBottomY() || var1.y1 > this.mc.world.getTopYInclusive()) {
               throw new IllegalStateException("Vùng đào vượt giới hạn Y của thế giới.");
            }
         } else {
            var1 = Region.fromSelectedPlacement();
         }

         boolean var2 = this.region == null
            || var1.x0 != this.region.x0
            || var1.y0 != this.region.y0
            || var1.z0 != this.region.z0
            || var1.x1 != this.region.x1
            || var1.y1 != this.region.y1
            || var1.z1 != this.region.z1;
         this.region = var1;
         if (var2) {
            this.stopActions();
            this.layer = -1;
            this.plan = null;
            this.laneSweep = null;
            this.skipped.clear();
            this.hardFails.clear();
            this.gaveUp.clear();
            this.fails.clear();
            this.waterDams.clear();
            this.clearMiddleTries();
            this.waterReplacements.clear();
            this.retryAt.clear();
            if (this.regionKey().equals(this.config.waterDamRegion) && this.config.waterDamCells != null) {
               for (long var4 : this.config.waterDamCells) {
                  this.waterDams.add(BlockPos.fromLong(var4));
               }
            }
         }

         return true;
      } catch (IllegalStateException var6) {
         this.say("§c" + var6.getMessage());
         this.status = var6.getMessage();
         return false;
      } catch (Throwable var7) {
         AutoMineMod.LOGGER.warn("[AutoMine] Lỗi đọc placement", var7);
         this.say("§cKhông đọc được placement Litematica: " + var7);
         return false;
      }
   }

   public void start() {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.loadRegion()) {
            String var1 = this.tools.choose(this.mc);
            if (var1 == null) {
               this.say("§cKhông có cúp nào trong túi.");
            } else {
               if (this.config.disablePauseOnLostFocus) {
                  try {
                     this.mc.options.pauseOnLostFocus = false;
                  } catch (Throwable var3) {
                  }
               }

               this.state = MineController.State.RUNNING;
               this.task = null;
               this.errors = 0;
               BotInput.install(this.mc);
               BotInput.INSTANCE.clear();
               this.aim.reset();
               this.ghost.reset();
               this.avoid.clear();
               this.plugFails.clear();
               this.durWarned = false;
               if (this.startedAt <= 0L) {
                  this.startedAt = System.currentTimeMillis();
               }

               this.say("Bắt đầu đào " + this.region.describe() + ". Cúp: " + var1 + ".");
               if (this.region.sizeX() < 3 || this.region.sizeZ() < 3 || this.region.sizeY() < 3) {
                  this.say("§eKhung mỏng hơn 3 ô ở một trục → cúp 3x3 sẽ phá lấn ra ngoài khung 1 ô ở phía đó.");
               }

               this.status = "Đang tìm tầng cần đào";
            }
         }
      }
   }

   public void pause(String var1) {
      if (this.state == MineController.State.RUNNING) {
         this.say(var1);
      }

      this.state = this.state == MineController.State.DONE ? MineController.State.DONE : MineController.State.IDLE;
      this.stopActions();
      this.status = var1;
   }

   private void stopActions() {
      MouseHold.release();
      if (this.task != null) {
         this.task.abort();
      }

      this.task = null;
      if (this.sellJob.active()) {
         this.sellJob.cancel(this.mc);
      }

      if (this.shop.active()) {
         this.shop.cancel(this.mc);
      }

      if (this.fillerOrders.active()) {
         this.fillerOrders.cancel(this.mc);
      }

      ServerSync.restoreBreaking(this.mc);
      if (this.mc.interactionManager != null && this.mc.interactionManager.isBreakingBlock()) {
         this.mc.interactionManager.cancelBlockBreaking();
      }

      if (this.mc.options != null) {
         Keys.releaseAll(this.mc);
      }

      if (FreeCam.active()) {
         BotInput.INSTANCE.clear();
      } else {
         BotInput.uninstall(this.mc);
      }
   }

   public void resetProgress() {
      this.layer = -1;
      this.plan = null;
      this.laneSweep = null;
      this.skipped.clear();
      this.hardFails.clear();
      this.gaveUp.clear();
      this.fails.clear();
      this.strikes = 0;
      this.clearMiddleTries();
      this.cleared = 0;
      this.retryAt.clear();
      this.layerQuietSince = this.finalQuietSince = -1;
      this.startedAt = 0L;
      this.calSamples = 0;
      this.calSum = 0.0;
      this.threeByThree = null;
   }

   public void startFromLayer(int var1) {
      if (this.region != null || this.loadRegion()) {
         this.layer = Math.max(0, Math.min(this.region.layerCount() - 1, var1 - 1));
         this.plan = null;
         this.laneSweep = null;
         this.say(
            "Sẽ đào từ tầng "
               + (this.layer + 1)
               + "/"
               + this.region.layerCount()
               + " (Y "
               + this.region.floor(this.layer)
               + "…"
               + this.region.top(this.layer)
               + ")."
         );
      }
   }

   public void tick() {
      if (this.state == MineController.State.RUNNING) {
         if (this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
            this.ticks++;
            DigLog.tick = this.ticks;
            FreeCam.adoptExternal(this.mc);
            BotInput.install(this.mc);
            ServerSync.restoreBreaking(this.mc);

            try {
               if (this.ticks < this.cooldownUntil) {
                  Keys.releaseMove(this.mc);
                  this.status = this.cooldownWhy + " → thử lại sau " + (this.cooldownUntil - this.ticks + 19) / 20 + " giây";
                  return;
               }

               this.tickInner();
               this.errors = Math.max(0, this.errors - 1);
            } catch (Throwable var5) {
               this.errors += 20;
               AutoMineMod.LOGGER.error("[AutoMine] Lỗi trong vòng đào", var5);
               if (this.task != null) {
                  this.task.abort();
               }

               this.task = null;
               Keys.releaseAll(this.mc);
               if (this.errors > 200) {
                  // 1.14.4: không dừng — nghỉ 5 giây, làm lại từ đầu tầng
                  this.errors = 0;
                  this.cooldownUntil = this.ticks + 100;
                  this.cooldownWhy = "lỗi lặp lại (" + var5 + ")";
                  this.recover("Lỗi lặp lại (" + var5 + ", xem latest.log)");
               }
            } finally {
               if (this.mc.player != null) {
                  this.stableYaw = this.mc.player.getYaw();
                  this.stablePitch = this.mc.player.getPitch();
               }

               ServerSync.hideBreaking(this.mc);
            }
         } else {
            this.pause("Rời thế giới → dừng.");
         }
      }
   }

   private void tickInner() {
      this.consumeBreak();
      GuiWatch.tick(this.mc);
      this.ghost.tick(this.mc);
      if (!this.sellJob.active() && !this.shop.active() && !(this.task instanceof MineController.FillerSupplyTask)) {
         if (this.mc.player.isDead() || this.mc.player.getHealth() <= 0.0F) {
            this.pause("§cNhân vật đã chết → dừng.");
         } else if (this.mc.currentScreen != null) {
            Keys.releaseMove(this.mc);
            this.status = "Đang mở màn hình — chờ đóng";
         } else {
            Keys.stand(this.mc);
            this.trackSafe();
            boolean var3 = this.task != null && this.task.walking();
            if (this.ghost.watchMove(this.mc, this.ticks, var3)) {
               Direction var2 = this.mc.player.getHorizontalFacing();
               this.ghost.requestAround(this.feet(), var2);
               if (this.task != null) {
                  this.task.onSetback();
               }
            }

            if (this.mc.player.isInLava()) {
               this.runExclusive(new MineController.EscapeLavaTask());
            } else {
               if (this.config.refillFillerFromOrder
                  && this.fillerCount() == 0
                  && this.mc.player.isOnGround()
                  && !(this.task instanceof MineController.SellTask)
                  && (this.task == null || !this.task.exclusive())) {
                  if (Inv.freeSlots(this.mc) > 0) {
                     this.runExclusive(new MineController.FillerSupplyTask());
                     return;
                  }

                  if (this.config.autoSell && this.ticks >= this.nextSellAt) {
                     this.runExclusive(new MineController.SellTask());
                     return;
                  }
               }

               if (this.region != null
                  && this.layer >= 0
                  && this.feet().getY() < this.region.floor(this.layer)
                  && (this.task == null || this.task instanceof MineController.ClimbTask && this.ticks % 20 == 0)
                  && !this.layerHasWork(this.layer)) {
                  if (this.task != null) {
                     this.task.abort();
                  }

                  this.task = null;
                  int var15 = this.nearestKnownLayer();
                  if (var15 >= 0) {
                     this.layer = var15;
                     this.plan = null;
                     this.laneSweep = null;
                  } else {
                     this.task = this.inspectUnknownLayer();
                     if (this.task == null) {
                        return;
                     }
                  }
               }

               float var16 = this.mc.player.getYaw();
               float var6 = this.mc.player.getPitch();
               if (Math.abs(MathHelper.wrapDegrees(var16 - this.stableYaw)) <= 0.5F && Math.abs(var6 - this.stablePitch) <= 0.5F) {
                  this.rotationStableTicks++;
               } else {
                  this.rotationStableTicks = 0;
               }

               ItemStack var7 = this.mc.player.getMainHandStack();
               Item var8 = this.safeFiller(var7) ? var7.getItem() : null;
               this.fillerStable = this.heldFiller.ready(var8, var8, this.mc.player.getInventory().selectedSlot, 0, this.ticks, 2);
               if (this.config.plugFluids
                  && this.region != null
                  && !(this.task instanceof MineController.WaterTask)
                  && this.ticks >= this.waterRetryAt
                  && this.ticks - this.waterCheckAt >= 20
                  && (this.task == null || !this.task.exclusive())) {
                  this.waterCheckAt = this.ticks;
                  WaterControl.Survey var9 = WaterControl.survey(this.mc, this.region, this.feet());
                  if (var9.wet() || !this.waterReplacements.isEmpty()) {
                     if (this.task != null) {
                        this.task.abort();
                     }

                     this.task = new MineController.WaterTask();
                  }
               }

               if (this.region != null
                  && this.layer >= 0
                  && this.layer < this.region.layerCount()
                  && this.feet().getY() < this.region.floor(this.layer)
                  && !(this.task != null && this.task.walking() && this.feet().getY() == this.region.floor(this.layer) - 1)
                  && !(this.task instanceof MineController.ClimbTask)
                  && !(this.task instanceof MineController.DescendTask)
                  && !(this.task instanceof MineController.EntryTask)
                  && !(this.task instanceof MineController.WaterTask)
                  && this.layerHasWork(this.layer)) {
                  Keys.releaseMove(this.mc);
                  this.mc.interactionManager.cancelBlockBreaking();
                  if (!this.mc.player.isOnGround()) {
                     this.status = "Đang rơi — chờ chạm đất để lên lại sàn";
                     return;
                  }

                  if (this.task != null) {
                     this.task.abort();
                  }

                  this.task = new MineController.ClimbTask(this.region.floor(this.layer));
               }

               MineController.Task var12;
               if ((this.task == null || !this.task.exclusive()) && (var12 = this.urgent()) != null) {
                  if (this.task != null) {
                     this.task.abort();
                  }

                  this.task = var12;
               }

               if (this.state == MineController.State.RUNNING) {
                  if ((this.task == null || this.task.interruptible()) && (var12 = this.maintenance()) != null) {
                     if (this.task != null) {
                        this.task.abort();
                     }

                     this.task = var12;
                  }

                  if (this.state == MineController.State.RUNNING) {
                     if (this.region != null && this.layer >= 0 && this.layer < this.region.layerCount() && this.ticks - this.scanAt > 60) {
                        this.scanLayer();
                     }

                     this.runDeferred();
                     this.watchdog();
                     if (this.state == MineController.State.RUNNING) {
                        if (this.task == null) {
                           this.task = this.decide();
                           if (this.task == null) {
                              return;
                           }
                        }

                        var12 = this.task;
                        MineController.Result var1;
                        if ((var1 = this.task.tick()) != MineController.Result.RUNNING) {
                           if (var1 == MineController.Result.FAILED && var12 instanceof MineController.WalkTask var10 && var10.onFail != null) {
                              var10.onFail.run();
                           }

                           boolean var19 = false;
                           if (var1 == MineController.Result.FAILED) {
                              this.lastFail = var12.label() + ": " + var12.why;
                              AutoMineMod.LOGGER.info("[AutoMine] Việc không xong — {}", this.lastFail);
                              if (var12 instanceof MineController.DescendTask var11) {
                                 if (var11.inLayer) {
                                    this.descendFails++;
                                 } else if (!var11.fatal) {
                                    this.entryNeeded = true;
                                 }
                              } else if (var12 instanceof MineController.EntryTask && ++this.entryAttempts >= 2) {
                                 var19 = true;
                              }

                              int var17 = this.progressMark * 31 + this.skipped.size() * 7 + (this.plan != null ? this.plan.index : 0);
                              if (var17 == this.failMark) {
                                 this.failStreak++;
                              } else {
                                 this.failMark = var17;
                                 this.failStreak = 1;
                              }
                           } else if (var12 instanceof MineController.EntryTask) {
                              this.descendFails = 0;
                           }

                           boolean var18 = var1 == MineController.Result.FAILED && var12.fatal;
                           String var20 = var12.why;
                           this.prevTask = var12.label();
                           var12.finish();
                           if (this.task == var12) {
                              this.task = null;
                           }

                           if (var18) {
                              // 1.14.4: không dừng — chờ 30 giây rồi làm lại (vd. Order chưa giao khối kê thì lúc đó thử lại)
                              this.cooldownUntil = this.ticks + 600;
                              this.cooldownWhy = var20;
                              this.recover(var20);
                           } else if (var19) {
                              this.switchCorner();
                           } else if (this.failStreak >= 20 && this.state == MineController.State.RUNNING) {
                              this.failStreak = 0;
                              this.recover("20 việc liền hỏng (" + this.lastFail + ")");
                           }
                        }
                     }
                  }
               }
            }
         }
      } else {
         if (this.task != null) {
            MineController.Task var4 = this.task;
            MineController.Result var5 = var4.tick();
            if (var5 != MineController.Result.RUNNING) {
               var4.finish();
               this.task = null;
               if (var5 == MineController.Result.FAILED && var4.fatal) {
                  // 1.14.4: không dừng — chờ 30 giây rồi làm lại
                  this.cooldownUntil = this.ticks + 600;
                  this.cooldownWhy = var4.why;
                  this.say("§e" + var4.why + " → thử lại sau 30 giây (không dừng).");
               }
            }
         }

         this.progressAt = this.ticks;
      }
   }

   private void watchdog() {
      if (this.task instanceof MineController.HealTask
         || this.task instanceof MineController.EatTask
         || this.task instanceof MineController.RepairTask
         || this.task instanceof MineController.BuyXpTask
         || this.task instanceof MineController.SellTask) {
         // ăn / hồi máu / sửa cúp / bán đồ không phải là kẹt
         this.progressAt = this.ticks;
         return;
      }

      // 1.14.4: chỉ khối server giữ vỡ mới là tiến triển (đập vỡ rồi bị trả lại mãi một khối thì vẫn là kẹt)
      int var1 = this.confirmed * 7 + Math.max(0, this.layer) * 100003;
      if (var1 != this.progressMark) {
         this.progressMark = var1;
         this.progressAt = this.ticks;
         this.recoveries = 0;
      } else if (this.ticks - this.progressAt >= watchdogTicks) {
         this.progressAt = this.ticks;
         this.recover((watchdogTicks / 20) + " giây không đào thêm được gì" + (this.lastFail.isEmpty() ? "" : " (" + this.lastFail + ")"));
      }
   }

   /**
    * 1.14.4: không đào được → KHÔNG dừng (trước đây 6 phút không tiến triển / 20 việc liền hỏng / hết góc vào tầng / lỗi lặp lại là dừng).
    * Lần lượt: làm lại kế hoạch tầng + đồng bộ ghost quanh người → đi chỗ khác trong tầng rồi làm lại → vào lại tầng từ góc khác; lặp mãi.
    */
   private void recover(String var1) {
      this.recoveries++;
      this.recoveriesTotal++;
      int var2 = (this.recoveries - 1) % 3;
      if (this.task != null) {
         this.task.abort();
      }

      this.task = null;
      Keys.releaseAll(this.mc);
      this.plan = null;
      this.laneSweep = null;
      this.avoid.clear();
      this.badPits.clear();
      this.fails.clear();
      this.retryAt.clear();
      this.plugFails.clear();
      this.expected.clear();
      this.recentStrikes.clear();
      this.clearMiddleTries();
      this.failStreak = 0;
      if (this.mc.player != null) {
         for (Direction var4 : Direction.Type.HORIZONTAL) {
            this.ghost.requestAround(this.feet(), var4);
         }
      }

      String var5 = "làm lại kế hoạch tầng, đồng bộ ghost quanh người";
      if (var2 == 1 && this.region != null && this.layer >= 0 && this.layer < this.region.layerCount() && this.mc.player != null) {
         BlockPos var6 = this.recoverSpot();
         if (var6 != null) {
            int var7 = this.region.floor(this.layer);
            PathFinder.Dig var8 = new PathFinder.Dig(this.region, false, var7, false);
            MineController.WalkTask var9 = new MineController.WalkTask(var6, 0.6, var8, "đi chỗ khác rồi làm lại", 20000, 1.0);
            var9.yLo = var7;
            var9.yHi = var7;
            this.task = var9;
            var5 = "đi tới " + var6.toShortString() + " rồi làm lại";
         }
      } else if (var2 == 2) {
         this.entryNeeded = true;
         this.entryAttempts = 0;
         this.triedCorners.clear();
         var5 = "vào lại tầng";
      }

      this.say("§e" + var1 + " → " + var5 + " (lần " + this.recoveries + ", không dừng).");
   }

   /** 1.14.4: ô sàn tầng đứng được, cách chỗ đang đứng 6–20 ô (càng xa chỗ kẹt càng tốt, đổi hướng mỗi lần) */
   private BlockPos recoverSpot() {
      int var1 = this.region.floor(this.layer);
      BlockPos var2 = this.feet();
      BlockPos var3 = null;
      double var4 = -Double.MAX_VALUE;
      double var6 = this.recoveries * 2.399963;

      for (int var8 = this.region.x0; var8 <= this.region.x1; var8 += 2) {
         for (int var9 = this.region.z0; var9 <= this.region.z1; var9 += 2) {
            BlockPos var10 = new BlockPos(var8, var1, var9);
            double var11 = Math.hypot(var8 - var2.getX(), var9 - var2.getZ());
            if (var11 >= 6.0 && var11 <= 20.0 && Cells.loaded(this.mc, var10) && Cells.standable(this.mc, var10) && !this.avoid.contains(var10)) {
               // hướng xoay theo số lần làm lại để mỗi lần đi một phía khác
               double var13 = Math.cos(Math.atan2(var9 - var2.getZ(), var8 - var2.getX()) - var6);
               double var15 = var11 * 0.2 + var13 * 3.0;
               if (var15 > var4) {
                  var4 = var15;
                  var3 = var10;
               }
            }
         }
      }

      return var3;
   }

   private void runExclusive(MineController.Task var1) {
      if (this.task == null || this.task.getClass() != var1.getClass()) {
         if (this.task != null) {
            this.task.abort();
         }

         this.task = var1;
      }

      if (this.task.tick() != MineController.Result.RUNNING) {
         this.task.finish();
         this.task = null;
      }
   }

   private void trackSafe() {
      BlockPos var1;
      if (this.mc.player.isOnGround() && !this.mc.player.isTouchingWater() && !this.mc.player.isInLava() && Cells.standable(this.mc, var1 = this.feet())) {
         this.lastSafe = var1;
      }
   }

   BlockPos feet() {
      return BlockPos.ofFloored(this.mc.player.getX(), this.mc.player.getY() + 0.2, this.mc.player.getZ());
   }

   double reach() {
      return Math.max(2.5, this.mc.player.getBlockInteractionRange() - this.config.reachMargin);
   }

   private boolean look(Vec3d var1) {
      Vec3d var2 = var1.subtract(this.mc.player.getEyePos());
      return this.lookAngles(Aim.yawTo(var2.x, var2.z), Aim.pitchTo(var2.x, var2.y, var2.z));
   }

   /** 1.14.0: quay tức thì; góc vừa đổi thì đếm lại tick ổn định để server nhận hướng nhìn mới trước khi đập. */
   private boolean lookAngles(float var1, float var2) {
      float var3 = this.mc.player.getYaw();
      float var4 = this.mc.player.getPitch();
      this.aim.look(this.mc, var1, var2, 0.0, 0.0, 0);
      if (Math.abs(MathHelper.wrapDegrees(this.mc.player.getYaw() - var3)) > 0.5F || Math.abs(this.mc.player.getPitch() - var4) > 0.5F) {
         this.rotationStableTicks = 0;
      }

      return true;
   }

   private boolean lookAlongTravel(double var1, double var3) {
      double var5 = var1 - this.mc.player.getX();
      double var7 = var3 - this.mc.player.getZ();
      float var9 = Math.hypot(var5, var7) > 0.01 ? Aim.yawTo(var5, var7) : this.mc.player.getYaw();
      return this.lookAngles(var9, 0.0F);
   }

   private boolean crosshairOn(BlockPos var1, Direction var2) {
      HitResult var3 = this.mc.player.raycast(this.mc.player.getBlockInteractionRange(), 1.0F, false);
      if (!(var3 instanceof BlockHitResult var4)) {
         return false;
      } else if (var3.getType() != Type.BLOCK) {
         return false;
      } else if (!var4.getBlockPos().equals(var1)) {
         return false;
      } else {
         return var2 == null ? true : var4.getSide() == var2;
      }
   }

   private boolean skippedOrClear(BlockPos var1) {
      return !Cells.mineable(this.mc, var1);
   }

   private void fail(BlockPos var1, String var2) {
      if (this.hardFails.merge(var1.toImmutable(), 1, Integer::sum) >= 15 && this.gaveUp.add(var1.toImmutable())) {
         this.say("§eBỏ qua ô " + var1.toShortString() + ": 15 lần không phá được (" + var2 + ") → đào tiếp phần khác.");
      }

      int var3 = this.fails.merge(var1.toImmutable(), 1, Integer::sum);
      this.retryAt.put(var1.toImmutable(), this.ticks + Math.min(100, 10 * var3));
      this.ghost.request(var1);
      AutoMineMod.LOGGER.info("[AutoMine] Thử lại ô {} sau {} lần: {}", new Object[]{var1.toShortString(), var3, var2});
   }

   /** 1.14.5: khối bot đang giữ chuột đập → danh sách ô 3x3 còn lại lúc bắt đầu (để kiểm tra sau nhát) */
   private int diagLogs;
   private BlockPos holdPos;
   private List<BlockPos> holdBefore;
   private BlockPos lastBroke;
   private int lastBrokeAt = -100;

   /**
    * 1.14.5: giữ chuột đập khối var1 (var2 = mặt 3x3 cần cùng trục, null = nhát lẻ). Game tự đập ở lượt đọc phím kế tiếp khi hướng nhìn
    * server đã nhận trúng khối (MouseHold); khối vỡ thì tick sau consumeBreak() ghi nhận và hẹn kiểm tra server trả lại / 3x3.
    */
   void hold(BlockPos var1, Direction var2, List<BlockPos> var3) {
      if (!var1.equals(this.holdPos)) {
         this.holdPos = var1.toImmutable();
         this.holdBefore = var3;
      }

      MouseHold.want(var1, var2);
   }

   /** đầu mỗi tick: khối giữ chuột đã vỡ (vỡ lúc game đọc phím) → đếm nhát, hẹn kiểm tra */
   private void consumeBreak() {
      if (this.holdPos != null && !MouseHold.wanted() && Cells.loaded(this.mc, this.holdPos) && !Cells.mineable(this.mc, this.holdPos)) {
         if (this.holdPos.equals(MouseHold.heldPos())) {
            this.strikes++;
            this.recentStrikes.put(this.holdPos, this.ticks);
            this.deferCheck(this.holdPos, this.holdBefore);
            this.lastBroke = this.holdPos;
            this.lastBrokeAt = this.ticks;
         }

         this.holdPos = null;
         this.holdBefore = null;
      }
   }

   /**
    * 1.14.5: được bắc cầu / kê cột không. Trong tầng thì không (hố lõm thì đi vòng / đi xuống rồi lên / nhảy, không có đường thì đào
    * phần khác trước — bắc cầu chỉ tạo thêm khối phải đào); chỉ cho khi đã làm lại vì kẹt 3 lần liền mà vẫn không đi đâu được.
    */
   boolean bridgeAllowed() {
      return this.recoveries >= 3 && this.fillerCount() > 0;
   }

   /** khối var1 vừa vỡ do bot giữ chuột (trong 2 tick gần nhất) */
   boolean justBroke(BlockPos var1) {
      return var1.equals(this.lastBroke) && this.ticks - this.lastBrokeAt <= 2;
   }

   /** 1.14.3: số lần đã đập lại khối giữa lát bị server trả lại / vị trí đứng lúc phát hiện lần đầu (gốc của thang đổi góc) */
   private final Map<BlockPos, Integer> middleTries = new HashMap<>();
   private final Map<BlockPos, Vec3d> middleBase = new HashMap<>();
   /** 1.14.3: khối giữa thử đủ 5 lần vẫn hiện lại → cả mặt 3x3 của lát đó để vét cuối tầng, hàng đào tiếp lát kế */
   private final Set<BlockPos> middleDeferred = new HashSet<>();
   private final List<List<LayerPlan.Slice>> suspendedRows = new ArrayList<>();
   private final Set<BlockPos> suspendedCells = new HashSet<>();
   private final Map<BlockPos, Integer> resumedRows = new HashMap<>();
   private BlockPos loopRowAt;
   private int loopRowConfirmed;
   private int loopRowCount;
   /** 1.14.3: khối giữa các lát hàng đã đi qua lúc thấy trống (vừa đập vỡ / sẵn trống) — hiện lại mới là bị trả lại, cần đập lại */
   private final Set<BlockPos> middleSeenClear = new HashSet<>();
   /** 1.14.4: chỗ đứng lúc đập khối giữa lần đầu → thang lùi/ngang tính từ đó (đổi hẳn góc so với nhát hỏng) */
   private final Map<BlockPos, Vec3d> middleStrikeAt = new HashMap<>();
   /** 1.14.4: số khối server đã xác nhận vỡ (không bị trả lại) — thước đo tiến triển của watchdog */
   private int confirmed;
   /** 1.14.4: số lần hỏng cộng dồn của từng ô (không xoá khi làm lại); đủ 15 lần thì bỏ qua ô đó để đào tiếp phần khác */
   private final Map<BlockPos, Integer> hardFails = new HashMap<>();
   private final Set<BlockPos> gaveUp = new HashSet<>();

   private void clearMiddleTries() {
      this.middleTries.clear();
      this.middleBase.clear();
      this.middleDeferred.clear();
      this.middleSeenClear.clear();
      this.middleStrikeAt.clear();
      this.suspendedRows.clear();
      this.resumedRows.clear();
      this.loopRowAt = null;
   }

   /** 1.14.2: ô sẽ vỡ theo nhát 3x3 vừa đập (chờ server báo) → tới hạn này không đập lại / không đi tìm chỗ đứng để đập */
   private final Map<BlockPos, Integer> expected = new HashMap<>();

   /** 1.14.2: đang đứng trong hố nông ngay dưới sàn tầng → ô sàn kề bên bước lên được (null nếu không có) */
   BlockPos dipExit() {
      BlockPos var1 = this.feet();
      if (this.region != null && this.layer >= 0 && var1.getY() == this.region.floor(this.layer) - 1 && Cells.passable(this.mc, var1.up(2))) {
         BlockPos var2 = null;
         double var3 = Double.MAX_VALUE;

         for (int var5 = -1; var5 <= 1; var5++) {
            for (int var6 = -1; var6 <= 1; var6++) {
               BlockPos var7 = var1.add(var5, 1, var6);
               if ((var5 != 0 || var6 != 0)
                  && (var5 == 0 || var6 == 0 || Cells.clearBody(this.mc, var1.add(var5, 0, 0)) && Cells.clearBody(this.mc, var1.add(0, 0, var6)))
                  && Cells.standable(this.mc, var7)
                  && !this.avoid.contains(var7)) {
                  double var8 = var7.getSquaredDistance(this.mc.player.getPos());
                  if (var8 < var3) {
                     var3 = var8;
                     var2 = var7;
                  }
               }
            }
         }

         return var2;
      } else {
         return null;
      }
   }

   /** ô sàn tầng (Y = var1) đứng được gần nhất trong bán kính var2 */
   private BlockPos nearFloor(int var1, int var2) {
      BlockPos var3 = this.feet();
      BlockPos var4 = null;
      double var5 = Double.MAX_VALUE;

      for (int var7 = -var2; var7 <= var2; var7++) {
         for (int var8 = -var2; var8 <= var2; var8++) {
            BlockPos var9 = new BlockPos(var3.getX() + var7, var1, var3.getZ() + var8);
            double var10;
            if (this.region.insideColumn(var9.getX(), var9.getZ()) && (var10 = var9.getSquaredDistance(var3)) < var5 && Cells.standable(this.mc, var9)) {
               var5 = var10;
               var4 = var9;
            }
         }
      }

      return var4;
   }

   void deferCheck(BlockPos var1, List<BlockPos> var2) {
      this.deferred.add(new Object[]{this.ticks, var1.toImmutable(), var2 == null ? List.of() : var2});
      if (var2 != null && !Boolean.FALSE.equals(this.threeByThree)) {
         int var3 = this.ticks + 4 + ServerSync.pingTicks(this.mc);

         for (BlockPos var5 : var2) {
            this.expected.put(var5.toImmutable(), var3);
         }
      }
   }

   boolean expectedGone(BlockPos var1) {
      Integer var2 = this.expected.get(var1);
      if (var2 == null) {
         return false;
      } else if (this.ticks <= var2 && Cells.mineable(this.mc, var1)) {
         return true;
      } else {
         this.expected.remove(var1);
         return false;
      }
   }

   /** khối bị server trả lại → tính là lần hỏng (thử lại sau); đếm ô 3x3 vỡ để biết cúp có phá 3x3; xin đồng bộ ô còn lại */
   @SuppressWarnings("unchecked")
   private void runDeferred() {
      int var1 = this.settleTicks();

      for (int var2 = 0; var2 < this.deferred.size(); var2++) {
         Object[] var3 = this.deferred.get(var2);
         if (this.ticks - (Integer)var3[0] >= var1) {
            BlockPos var4 = (BlockPos)var3[1];
            List<BlockPos> var5 = (List<BlockPos>)var3[2];
            if (Cells.loaded(this.mc, var4) && Cells.mineable(this.mc, var4)) {
               this.fail(var4, "server trả lại khối");
               if (this.diagLogs++ < 200) {
                  // 1.14.6: thứ tự gói đã gửi cho khối này (thiếu "bắt đầu", quay mặt sau khi đập…) để biết vì sao server trả lại
                  AutoMineMod.LOGGER.info(
                     "[AutoMine] Gói đập gửi cho {}: {} (đứng đất={}, ngồi={})",
                     new Object[]{var4.toShortString(), DigLog.about(var4, 120), this.mc.player.isOnGround(), this.mc.player.isSneaking()}
                  );
               }
            } else {
               this.fails.remove(var4);
               this.retryAt.remove(var4);
               this.hardFails.remove(var4);
               this.confirmed++;
            }

            int var6 = 0;

            for (BlockPos var8 : var5) {
               if (!Cells.mineable(this.mc, var8)) {
                  var6++;
               } else if (this.config.ghostResync && (this.threeByThree == null || this.threeByThree)) {
                  this.ghost.request(var8);
               }
            }

            if (var5.size() - var6 >= 2 && this.diagLogs < 200) {
               // 1.14.5: chẩn đoán "sót 1 dải giữa các hàng": nhát 3x3 để lại những ô nào (lệch so với khối đập), trục đang nhìn
               this.diagLogs++;
               StringBuilder var12 = new StringBuilder();

               for (BlockPos var14 : var5) {
                  if (Cells.mineable(this.mc, var14)) {
                     var12.append(' ').append(var14.getX() - var4.getX()).append(',').append(var14.getY() - var4.getY()).append(',').append(var14.getZ() - var4.getZ());
                  }
               }

               AutoMineMod.LOGGER.info(
                  "[AutoMine] 3x3 tại {} còn {}/{} ô:{} (hướng nhìn {})",
                  new Object[]{var4.toShortString(), var5.size() - var6, var5.size(), var12, BreakGeometry.normal(this.mc.player.getYaw(), this.mc.player.getPitch())}
               );
            }

            this.cleared += var6 + 1;
            this.confirmed += var6;
            this.calibrate(var5.size(), var6);
            this.deferred.remove(var2--);
            var5.forEach(this.expected::remove);
         }
      }
   }

   private boolean retryReady(BlockPos var1) {
      return this.ticks >= this.retryAt.getOrDefault(var1, 0);
   }

   /**
    * 1.14.2: không cần đợi góc nhìn đứng yên 1 tick (đang đi thì góc đổi liên tục → trước đây chờ ~10 tick mới đập):
    * mặt 3x3 chỉ phụ thuộc trục của hướng nhìn, nên đủ khi hướng nhìn server đang biết (gửi ở tick trước) đã chắc chắn cùng trục.
    */
   private boolean drillCrosshairOn(BlockPos var1, Direction var2) {
      return (
               this.rotationStableTicks >= 1
                  || var2 != null && !Float.isNaN(this.stableYaw) && BreakGeometry.firm(this.stableYaw, this.stablePitch, var2.getAxis())
            )
            && var2 != null
            && this.crosshairOn(var1, var2)
            && BreakGeometry.normal(this.mc.player.getYaw(), this.mc.player.getPitch()).getAxis() == var2.getAxis()
         ? this.mc.player.raycast(this.mc.player.getBlockInteractionRange(), 1.0F, false) instanceof BlockHitResult var4
            && BreakGeometry.aligned(this.mc.player.getEyePos(), var4.getPos(), var2)
         : false;
   }

   /**
    * 1.14.3: đứng yên mà dưới 2 góc chân có sàn (vd. đáp lệch sát mép rãnh) → nhích về tâm ô sàn gần nhất dưới chân để đứng thẳng đập 3x3,
    * không ngồi im chờ. Trả true khi đang nhích.
    */
   boolean recentre() {
      if (this.mc.player != null
         && this.mc.player.isOnGround()
         && !(this.mc.player.getVelocity().horizontalLength() > 0.03)
         && SafeMotion.edgeSneak(this.mc)) {
         int var1 = (int)Math.floor(this.mc.player.getY() + 0.02) - 1;
         double var2 = Double.NaN;
         double var4 = 0.0;
         double var6 = Double.MAX_VALUE;

         for (double var10 : new double[]{-0.28, 0.28}) {
            for (double var14 : new double[]{-0.28, 0.28}) {
               BlockPos var16 = BlockPos.ofFloored(this.mc.player.getX() + var10, var1, this.mc.player.getZ() + var14);
               double var17 = Math.hypot(var16.getX() + 0.5 - this.mc.player.getX(), var16.getZ() + 0.5 - this.mc.player.getZ());
               if (Cells.floor(this.mc, var16) && var17 < var6) {
                  var6 = var17;
                  var2 = var16.getX() + 0.5;
                  var4 = var16.getZ() + 0.5;
               }
            }
         }

         if (!Double.isNaN(var2)) {
            Keys.drive(this.mc, var2, var4, 0.1, true);
            return true;
         }
      }

      return false;
   }

   /** 1.14.4: khối đầu tiên chắn tia nhìn từ mắt tới tâm mặt var2 của var1 (null nếu không bị che) */
   private BlockPos occluder(BlockPos var1, Direction var2) {
      Vec3d var3 = this.mc.player.getEyePos();
      Vec3d var4 = Cells.faceCenter(var1, var2).subtract(Vec3d.of(var2.getVector()).multiply(0.05));
      BlockHitResult var5 = this.mc
         .world
         .raycast(
            new net.minecraft.world.RaycastContext(
               var3, var4, net.minecraft.world.RaycastContext.ShapeType.OUTLINE, net.minecraft.world.RaycastContext.FluidHandling.NONE, this.mc.player
            )
         );
      return var5.getType() == Type.BLOCK && !var5.getBlockPos().equals(var1) ? var5.getBlockPos().toImmutable() : null;
   }

   private boolean protectedFooting(BlockPos var1, Direction var2) {
      return this.protectedFooting(Cells.plane(var1, var2));
   }

   private boolean protectedFooting(List<BlockPos> var1) {
      Box var2 = this.mc.player.getBoundingBox();
      int var3 = (int)Math.floor(this.mc.player.getY() + 0.02) - 1;
      HashSet var4 = new HashSet(var1);

      for (int var5 = (int)Math.floor(var2.minX); var5 <= (int)Math.floor(var2.maxX); var5++) {
         for (int var6 = (int)Math.floor(var2.minZ); var6 <= (int)Math.floor(var2.maxZ); var6++) {
            if (var4.contains(new BlockPos(var5, var3, var6)) && Cells.mineable(this.mc, new BlockPos(var5, var3, var6))) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean fallingBlocksNearby() {
      return !this.mc
         .world
         .getEntitiesByClass(
            FallingBlockEntity.class,
            new Box(
               this.region.x0 - 1, this.mc.world.getBottomY(), this.region.z0 - 1, this.region.x1 + 2, this.mc.world.getTopYInclusive() + 1, this.region.z1 + 2
            ),
            var0 -> true
         )
         .isEmpty();
   }

   private boolean quietClear(boolean var1) {
      if (this.fallingBlocksNearby()) {
         this.layerQuietSince = this.finalQuietSince = -1;
         return false;
      } else {
         int var2 = var1 ? this.finalQuietSince : this.layerQuietSince;
         if (var2 < 0) {
            var2 = this.ticks;
            if (var1) {
               this.finalQuietSince = var2;
            } else {
               this.layerQuietSince = var2;
            }
         }

         // 1.14.2: không chờ cố định — xong ngay khi không còn khối đang rơi, không còn nhát chờ server xác nhận / kiểm tra nền (tối thiểu 5 tick)
         return this.ticks - var2 >= 5 && this.deferred.isEmpty() && ServerSync.pendingCount(this.mc) == 0 || this.ticks - var2 >= Math.max(20, this.settleTicks());
      }
   }

   private int layerYMin() {
      return this.region != null && this.layer >= 0 ? this.region.floor(this.layer) : this.mc.player.getBlockY() - 3;
   }

   private int layerYMax() {
      return this.region != null && this.layer >= 0 ? this.region.floor(this.layer) + 1 : this.mc.player.getBlockY() + 3;
   }

   private boolean pickReady() {
      return this.tools.isOurPick(this.mc.player.getMainHandStack());
   }

   private boolean equipPick() {
      return this.pickReady() ? true : Inv.equip(this.mc, this.tools::isOurPick, 0);
   }

   private boolean equipFiller() {
      return this.safeFiller(this.mc.player.getMainHandStack()) ? true : Inv.equip(this.mc, this::safeFiller, 7);
   }

   private int fillerCount() {
      return SellJob.count(this.mc, this::safeFiller);
   }

   private boolean fillerClickReady() {
      return this.fillerStable && this.safeFiller(this.mc.player.getMainHandStack());
   }

   private boolean safeFiller(ItemStack var1) {
      if (this.tools.isFiller(var1) && var1.getItem() instanceof BlockItem var2 && !(var2.getBlock() instanceof FallingBlock)) {
         BlockState var4 = var2.getBlock().getDefaultState();
         return var4.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)
            && !var4.hasBlockEntity()
            && var4.getFluidState().isEmpty()
            && !Cells.lava(var4)
            && !var4.isOf(Blocks.MAGMA_BLOCK)
            && !var4.isOf(Blocks.TNT);
      } else {
         return false;
      }
   }

   private MineController.Task urgent() {
      BlockPos var1;
      if (this.mc.player.isInsideWall() && Cells.mineable(this.mc, var1 = BlockPos.ofFloored(this.mc.player.getEyePos()))) {
         return new MineController.UnstuckTask(var1);
      } else if (this.config.plugFluids && (var1 = this.fluidToPlug(true)) != null) {
         return new MineController.PlugTask(var1, "chặn lava");
      } else {
         float var2 = this.mc.player.getHealth();
         if (var2 <= 4.0F
            && !this.mc.player.isInLava()
            && !(this.task instanceof MineController.HealTask)
            && !(this.task instanceof MineController.EatTask)) {
            // 1.14.4: không dừng — ăn rồi đứng chờ hồi máu, hồi xong đào tiếp
            Item var3 = this.tools.bestFood(this.mc);
            if (var3 != null && this.mc.player.getHungerManager().getFoodLevel() < 20) {
               return new MineController.EatTask(var3);
            } else {
               return new MineController.HealTask();
            }
         } else {
            return null;
         }
      }
   }

   private MineController.Task maintenance() {
      if (!this.mc.player.isOnGround() && !this.mc.player.isTouchingWater()) {
         return null;
      } else {
         int var3 = this.mc.player.getHungerManager().getFoodLevel();
         if (this.config.autoEat && var3 <= this.config.eatAtHunger) {
            Item var2 = this.tools.bestFood(this.mc);
            if (var2 != null) {
               return new MineController.EatTask(var2);
            }

            if (var3 <= 8 && this.ticks - this.foodWarnAt > 1200) {
               this.foodWarnAt = this.ticks;
               this.say("§eĐói (" + var3 + "/20) mà trong túi không có đồ ăn.");
            }
         }

         if (this.mc.player.getHealth() <= this.config.minHealth) {
            Item var5 = this.tools.bestFood(this.mc);
            if (var3 < 20 && var5 != null && this.config.autoEat) {
               return new MineController.EatTask(var5);
            }

            if (var3 >= 18) {
               return new MineController.HealTask();
            }
         }

         ItemStack var6;
         if ((var6 = this.tools.findPick(this.mc)).isEmpty()) {
            // 1.14.4: không dừng — chờ có cúp trong túi (kiểm tra lại mỗi 5 giây)
            this.cooldownUntil = this.ticks + 100;
            this.cooldownWhy = "Không thấy cúp \"" + this.tools.pickLabel() + "\" trong túi";
            return null;
         } else {
            int var4 = Tools.durability(var6);
            if (var4 >= 0 && var4 <= this.config.repairAt) {
               if (this.config.xpRepair && Inv.count(this.mc, Tools::isXp) > 0) {
                  return new MineController.RepairTask();
               }

               if (this.config.xpRepair && this.config.buyXp && (this.noXpUntil < 0 || this.ticks > this.noXpUntil)) {
                  return new MineController.BuyXpTask();
               }

               if (var4 <= this.config.hardStopDurability) {
                  // 1.14.4: không đào cho vỡ cúp nhưng cũng không dừng — 1 phút sau thử mua/lấy bình XP lại
                  this.cooldownUntil = this.ticks + 1200;
                  this.cooldownWhy = "Cúp còn " + var4 + " độ bền, chưa có bình XP";
                  this.noXpUntil = -1;
                  return null;
               }

               if (!this.durWarned) {
                  this.durWarned = true;
                  this.say("§eCúp còn " + var4 + " độ bền, chưa có bình XP → đào tiếp, còn " + this.config.hardStopDurability + " thì dừng.");
               }
            } else {
               this.durWarned = false;
            }

            return this.config.autoSell && Inv.freeSlots(this.mc) <= this.config.sellWhenFree && this.ticks >= this.nextSellAt
               ? new MineController.SellTask()
               : null;
         }
      }
   }

   private BlockPos fluidToPlug(boolean var1) {
      BlockPos var2 = this.feet();
      BlockPos var3 = null;
      double var4 = Double.MAX_VALUE;
      int var6 = var1 ? 3 : 2;

      for (int var7 = -var6; var7 <= var6; var7++) {
         for (int var8 = -1; var8 <= 3; var8++) {
            for (int var9 = -var6; var9 <= var6; var9++) {
               BlockPos var11 = var2.add(var7, var8, var9);
               if (Cells.loaded(this.mc, var11)) {
                  BlockState var12 = Cells.state(this.mc, var11);
                  boolean var10;
                  if (var1) {
                     var10 = Cells.lava(var12);
                  } else {
                     var10 = Cells.water(var12)
                        && var12.getFluidState().isStill()
                        && this.region != null
                        && this.region.inside(var11)
                        && var12.getBlock() instanceof FluidBlock;
                  }

                  if (var10 && this.plugFails.getOrDefault(var11, 0) < 4 && this.exposedFluid(var11)) {
                     double var13 = var11.getSquaredDistance(this.mc.player.getPos());
                     if (var1 && var12.getFluidState().isStill()) {
                        var13 -= 2.0;
                     }

                     if (var13 < var4) {
                        var4 = var13;
                        var3 = var11.toImmutable();
                     }
                  }
               }
            }
         }
      }

      return var3;
   }

   private boolean exposedFluid(BlockPos var1) {
      for (Direction var5 : Direction.values()) {
         BlockState var6;
         if (var5 != Direction.UP
            && ((var6 = Cells.state(this.mc, var1.offset(var5))).isAir() || Cells.passable(this.mc, var1.offset(var5)) && var6.getFluidState().isEmpty())) {
            return true;
         }
      }

      return false;
   }

   private MineController.Task decide() {
      if (this.region == null && !this.loadRegion()) {
         this.pause("Không có placement để đào.");
         return null;
      } else if (!this.mc.player.isOnGround() && !this.mc.player.isTouchingWater()) {
         Keys.releaseMove(this.mc);
         this.status = "Đang rơi…";
         if (++this.stuckFallTicks > 200) {
            this.stuckFallTicks = 0;
            Keys.jump(this.mc, true);
         }

         return null;
      } else {
         this.stuckFallTicks = 0;
         if (this.layer < 0 || this.layer >= this.region.layerCount()) {
            this.layer = this.nearestKnownLayer();
            if (this.layer < 0) {
               this.layer = this.firstLayerWithWork(0);
            }

            this.plan = null;
            this.laneSweep = null;
            if (this.layer < 0) {
               this.finishAll();
               return null;
            }
         }

         int var5 = this.region.floor(this.layer);
         BlockPos var6 = this.feet();
         if (this.plan == null || this.plan.layer != this.layer) {
            this.plan = new LayerPlan(this.region, this.layer, var6.getX(), var6.getZ());
            this.laneSweep = null;
            this.laneSweepTries = 0;
            this.scanAt = -1000;
            this.entryNeeded = false;
            this.entryAttempts = 0;
            this.entryCorner = 0;
            this.triedCorners.clear();
            this.descendFails = 0;
            this.entryOpened = false;
            AutoMineMod.LOGGER
               .info(
                  "[AutoMine] Tầng {}/{} (Y {}…{}): {} hàng, {} nhát dự kiến, bắt đầu ở góc {} (đang đứng Y={})",
                  new Object[]{
                     this.layer + 1,
                     this.region.layerCount(),
                     var5,
                     this.region.top(this.layer),
                     this.plan.strips,
                     this.plan.slices.size(),
                     this.plan.pitX + "," + this.plan.pitZ,
                     var6.getY()
                  }
               );
         }

         BlockPos var42;
         if (var6.getY() > var5) {
            return new MineController.DescendTask(var5);
         } else if (var6.getY() == var5 - 1 && (var42 = this.dipExit()) != null) {
            // 1.14.2: đứng trong hố nông 1 ô → bước thẳng lên sàn tầng, không bắc cột
            MineController.WalkTask var43 = new MineController.WalkTask(var42, 0.3, false, "bước lên khỏi hố nông");
            var43.yLo = var5 - 1;
            var43.yHi = var5;
            return var43;
         } else if (var6.getY() == var5 - 1 && (var42 = this.nearFloor(var5, 6)) != null) {
            // rãnh nông rộng: đi trong rãnh tới chỗ bước lên được
            MineController.WalkTask var44 = new MineController.WalkTask(var42, 0.3, false, "ra khỏi rãnh nông");
            var44.goalTest = var2x -> var2x.getY() == var5;
            var44.yLo = var5 - 1;
            var44.yHi = var5;
            return var44;
         } else if (var6.getY() < var5) {
            return (MineController.Task)(this.layerHasWork(this.layer) ? new MineController.ClimbTask(var5) : this.inspectUnknownLayer());
         } else if (!this.region.insideColumn(var6.getX(), var6.getZ())) {
            int var17 = this.region.sizeX() >= 3 ? 1 : 0;
            int var20 = this.region.sizeZ() >= 3 ? 1 : 0;
            BlockPos var22 = new BlockPos(
               Math.clamp((long)var6.getX(), this.region.x0 + var17, this.region.x1 - var17),
               var5,
               Math.clamp((long)var6.getZ(), this.region.z0 + var20, this.region.z1 - var20)
            );
            PathFinder.Dig var23 = new PathFinder.Dig(this.region, true, var5, true);
            var23.spill = true;
            return new MineController.WalkTask(var22, 0.22, var23, "tới vùng cần đào", 12000, 1.0);
         } else {
            this.entryOpened = true;
            if (this.ticks - this.scanAt > 40) {
               this.scanLayer();
            }

            if (this.laneSweep != null) {
               List var3 = this.leftoversIn(this.laneSweep[0], this.laneSweep[1]);
               int var2 = this.plan.slices.get(Math.min(this.laneSweep[0], this.plan.slices.size() - 1)).strip() + 1;
               if (!var3.isEmpty() && this.laneSweepTries < Math.min(30, 3 + 2 * var3.size())) {
                  this.laneSweepTries++;
                  this.status = "Hàng " + var2 + "/" + this.plan.strips + ": vét " + var3.size() + " ô sót";
                  MineController.Task var1 = this.chooseStrike(var3, "sót hàng " + var2, false);
                  if (var1 != null) {
                     return var1;
                  }
               }

               if (!var3.isEmpty()) {
                  AutoMineMod.LOGGER.info("[AutoMine] Hàng {}: {} ô sót chưa đập được từ trong hàng → để vét cuối tầng", var2, var3.size());
               }

               this.plan.index = Math.max(this.plan.index, this.laneSweep[1]);
               this.laneSweep = null;
               this.laneSweepTries = 0;
            }

            while (this.plan.current() != null && this.sliceClear(this.plan.current())) {
               this.plan.index++;
            }

            LayerPlan.Slice var15 = this.plan.current();
            if (var15 != null) {
               if (!Cells.loaded(this.mc, var15.center())) {
                  this.plan.index++;
                  return null;
               } else {
                  this.status = "Tầng " + (this.layer + 1) + "/" + this.region.layerCount() + " · hàng " + (var15.strip() + 1) + "/" + this.plan.strips;
                  if (Cells.mineable(this.mc, var15.center())
                     && !this.skipped.contains(var15.center())
                     && !this.middleDeferred.contains(var15.center())
                     && Cells.solid(this.mc, var15.center().offset(var15.face()))) {
                     int var14 = this.plan.runEnd(this.plan.index);

                     LayerPlan.Slice var16;
                     // 1.14.6: dừng dò ở khối giữa để dành — trước đây đào ngược bắt đầu đúng khối đó, hàng báo "bị che" rồi lại lập đào ngược, lặp mãi
                     for (int var19 = this.plan.index;
                        var19 < var14
                           && (var16 = this.plan.slices.get(var19)).face().getAxis() == var15.face().getAxis()
                           && !this.skipped.contains(var16.center())
                           && !this.middleDeferred.contains(var16.center());
                        var19++
                     ) {
                        if (Cells.mineable(this.mc, var16.center())) {
                           if (!Cells.solid(this.mc, var16.center().offset(var16.face()))) {
                              break;
                           }

                           if (!Cells.solid(this.mc, var16.center().offset(var16.face().getOpposite()))) {
                              ArrayList var21 = new ArrayList();

                              for (int var10 = var19; var10 >= this.plan.index; var10--) {
                                 LayerPlan.Slice var11 = this.plan.slices.get(var10);
                                 var21.add(new LayerPlan.Slice(var11.center(), var11.face().getOpposite(), var11.strip(), -1));
                              }

                              if (this.rowLooping(var16.center())) {
                                 return null;
                              }

                              AutoMineMod.LOGGER
                                 .info(
                                    "[AutoMine] Hàng {}: đầu hàng chưa lộ mặt → đào ngược {} lát từ {} về đầu hàng",
                                    new Object[]{var15.strip() + 1, var21.size(), var16.center().toShortString()}
                                 );
                              return new MineController.RowTask(this.plan, var21);
                           }
                        }
                     }
                  }

                  return this.rowLooping(var15.center()) ? null : new MineController.RowTask(this.plan, this.plan.index);
               }
            } else {
               MineController.Task var24 = this.resumeRow();
               if (var24 != null) {
                  return var24;
               }

               List<BlockPos> var7 = this.scanLayer();
               if (var7.isEmpty()) {
                  if (this.layerUnloaded > 0) {
                     this.status = "Chờ tải chunk (" + this.layerUnloaded + " ô chưa tải) — đi về phía đó";
                     BlockPos var13 = this.unloadedHint();
                     return var13 != null ? new MineController.WalkTask(var13, 3.0, false, "tới phần chưa tải") : null;
                  } else if (!this.quietClear(false)) {
                     this.status = "Chờ sỏi/cát rơi và kiểm tra lại tầng";
                     return null;
                  } else {
                     this.layerQuietSince = -1;
                     int var18 = this.skippedInLayer();
                     this.say(
                        "Xong tầng " + (this.layer + 1) + "/" + this.region.layerCount() + (var18 > 0 ? " (bỏ qua " + var18 + " ô không đào được)" : "") + "."
                     );
                     this.layer = this.firstLayerWithWork(this.layer + 1);
                     this.plan = null;
                     this.laneSweep = null;
                     this.recentStrikes.clear();
                     this.fails.clear();
                     this.clearMiddleTries();
                     this.avoid.clear();
                     this.badPits.clear();
                     if (this.layer < 0) {
                        this.finishAll();
                     }

                     return null;
                  }
               } else {
                  this.status = "Vét sót tầng " + (this.layer + 1) + ": còn " + var7.size() + " ô";
                  MineController.Task var12 = this.chooseStrike(var7, "vét sót", false);
                  if (var12 == null) {
                     var12 = this.chooseStrike(var7, "vét sót (lẹm sàn)", true);
                  }

                  if (var12 != null) {
                     return var12;
                  } else {
                     // 1.14.1: còn ô sót là đi tới ngay (ô đang chờ thử lại xếp sau), không đứng chờ
                     List<BlockPos> var13 = new ArrayList<>(var7);
                     var13.removeIf(this.waterDams::contains);
                     boolean var29 = var13.stream().anyMatch(this::retryReady);
                     var13.sort(
                        Comparator.comparingDouble(var2x -> (var29 && !this.retryReady(var2x) ? 1.0E7 : 0.0) + var2x.getSquaredDistance(this.mc.player.getPos()))
                     );

                     for (int var30 = 0; var30 < Math.min(6, var13.size()); var30++) {
                        MineController.Task var31 = this.approachStrike(var13.get(var30));
                        if (var31 != null) {
                           this.status = "Vét sót tầng " + (this.layer + 1) + ": còn " + var7.size() + " ô → đi tới " + var13.get(var30).toShortString();
                           return var31;
                        }
                     }

                     this.lastFail = "không tới được " + var7.size() + " ô sót";
                     this.status = "Còn " + var7.size() + " ô sót không tới được (giữ đập nước / ngoài khung)";
                     return null;
                  }
               }
            }
         }
      }
   }

   private boolean pitOpen() {
      int var1 = this.region.floor(this.layer);
      int var2 = this.region.top(this.layer);

      for (int var3 = -1; var3 <= 1; var3++) {
         for (int var4 = -1; var4 <= 1; var4++) {
            for (int var5 = var1; var5 <= var2; var5++) {
               BlockPos var6 = new BlockPos(this.plan.pitX + var3, var5, this.plan.pitZ + var4);
               if (this.region.inside(var6)) {
                  if (!Cells.loaded(this.mc, var6)) {
                     return false;
                  }

                  if (Cells.mineable(this.mc, var6)) {
                     return false;
                  }
               }
            }
         }
      }

      return true;
   }

   private void switchCorner() {
      this.entryCorner++;
      this.entryAttempts = 0;
      this.entryNeeded = true;
      if (this.entryCorner >= 4) {
         // 1.14.4: hết góc → không dừng: thử lại cả 4 góc sau khi đi chỗ khác
         this.entryCorner = 0;
         this.triedCorners.clear();
         this.recover("Không vào được góc nào của tầng " + (this.layer + 1) + " (" + this.lastFail + ")");
      } else {
         if (this.plan != null) {
            this.triedCorners.add(BlockPos.asLong(this.plan.pitX, 0, this.plan.pitZ));
         }

         BlockPos var1 = this.feet();
         ArrayList<int[]> var2 = new ArrayList<>(
            List.of(
               new int[]{this.region.x0, this.region.z0},
               new int[]{this.region.x1, this.region.z0},
               new int[]{this.region.x0, this.region.z1},
               new int[]{this.region.x1, this.region.z1}
            )
         );
         var2.sort(Comparator.comparingDouble(var1x -> Math.hypot(var1x[0] - var1.getX(), var1x[1] - var1.getZ())));
         LayerPlan var3 = null;

         for (int[] var5 : var2) {
            LayerPlan var6 = new LayerPlan(this.region, this.layer, var5[0], var5[1]);
            if (!this.triedCorners.contains(BlockPos.asLong(var6.pitX, 0, var6.pitZ))) {
               var3 = var6;
               break;
            }
         }

         if (var3 == null) {
            this.entryCorner = 0;
            this.triedCorners.clear();
            this.recover("Không vào được góc nào của tầng " + (this.layer + 1) + " (" + this.lastFail + ")");
         } else {
            this.plan = var3;
            this.laneSweep = null;
            this.entryOpened = false;
            this.say("§eKhông vào được góc cũ → thử góc " + this.plan.pitX + "," + this.plan.pitZ + " của tầng " + (this.layer + 1) + ".");
         }
      }
   }

   private List<BlockPos> leftoversIn(int var1, int var2) {
      LinkedHashSet var3 = new LinkedHashSet();
      int var4 = this.region.floor(this.layer);

      for (int var5 = Math.max(0, var1); var5 < var2 && var5 < this.plan.slices.size(); var5++) {
         LayerPlan.Slice var6 = this.plan.slices.get(var5);
         if (Cells.loaded(this.mc, var6.center())) {
            for (BlockPos var8 : Cells.plane(var6.center(), var6.face())) {
               if (this.region.inside(var8) && var8.getY() >= var4 && !this.skippedOrClear(var8)) {
                  var3.add(var8.toImmutable());
               }
            }
         }
      }

      return new ArrayList<>(var3);
   }

   private int settleTicks() {
      return Math.max(16, 8 + 2 * ServerSync.pingTicks(this.mc));
   }

   /**
    * 1.14.1: đi tới ô sót ngay, không đòi tìm trọn đường trước (bản cũ tìm đường giới hạn 3000 ô, không ra là đứng chờ thử lại).
    * Chọn chỗ đứng gần nhất (±4 ô) nhìn thấy được ô sót; không có chỗ nào nhìn thấy thì đào đường tới sát ô đó.
    */
   private MineController.Task approachStrike(BlockPos var1) {
      if (this.region.inside(var1) && !this.waterDams.contains(var1)) {
         int var2 = this.region.floor(this.layer);
         BlockPos var3 = this.feet();
         ArrayList<BlockPos> var4 = new ArrayList<>();

         for (int var5 = -4; var5 <= 4; var5++) {
            for (int var6 = -4; var6 <= 4; var6++) {
               BlockPos var7 = new BlockPos(var1.getX() + var5, var2, var1.getZ() + var6);
               if (this.region.insideColumn(var7.getX(), var7.getZ())
                  && !this.avoid.contains(var7)
                  && Cells.standable(this.mc, var7)
                  && Cells.clearBody(this.mc, var7)
                  && !ServerSync.pending(this.mc, var7.down())
                  && !var1.equals(var7.down())) {
                  var4.add(var7);
               }
            }
         }

         var4.sort(Comparator.comparingDouble(var2x -> Math.sqrt(var2x.getSquaredDistance(var3)) + Math.sqrt(var2x.getSquaredDistance(var1)) * 0.5));

         for (BlockPos var14 : var4) {
            for (Direction var10 : Direction.values()) {
               if (this.miningPoint(this.miningEyeAt(var14, true), var1, var10, true) != null) {
                  MineController.StrikeTask var17 = new MineController.StrikeTask(var1, var10, "tới và đào ô sót", true);
                  var17.preferStand = var14;
                  var17.mineApproach = true;
                  AutoMineMod.LOGGER.info("[AutoMine] Vét sót: đi tới ô {} (đứng {}, đang ở {})", new Object[]{var1.toShortString(), var14.toShortString(), var3.toShortString()});
                  return var17;
               }
            }
         }

         BlockPos var15 = new BlockPos(var1.getX(), var2, var1.getZ());
         PathFinder.Dig var16 = new PathFinder.Dig(this.region, false, var2, false);
         MineController.WalkTask var18 = new MineController.WalkTask(var15, 0.4, var16, "đào đường tới ô sót " + var1.toShortString(), 20000, 1.0);
         var18.goalTest = var2x -> var2x.getY() == var2 && Math.max(Math.abs(var2x.getX() - var1.getX()), Math.abs(var2x.getZ() - var1.getZ())) <= 1;
         var18.yLo = var2;
         var18.yHi = var2;
         var18.onFail = () -> this.fail(var1, "không đào được đường tới");
         AutoMineMod.LOGGER.info("[AutoMine] Vét sót: chưa có chỗ đứng nhìn thấy ô {} → đào đường tới sát nó", var1.toShortString());
         return var18;
      } else {
         return null;
      }
   }

   private boolean near(BlockPos var1, int var2) {
      return var1.getX() >= this.region.x0 - var2
         && var1.getX() <= this.region.x1 + var2
         && var1.getZ() >= this.region.z0 - var2
         && var1.getZ() <= this.region.z1 + var2;
   }

   private void finishAll() {
      int var1 = this.firstLayerWithWork(0);
      if (var1 >= 0) {
         this.layer = var1;
         this.plan = null;
         this.laneSweep = null;
         this.finalQuietSince = -1;
         this.status = "Quét lại khung: có khối còn sót hoặc sỏi/cát vừa rơi";
      } else {
         Keys.releaseMove(this.mc);
         if (!this.quietClear(true)) {
            this.status = "Kiểm tra toàn khung lần cuối, chờ sỏi/cát ổn định";
         } else {
            this.state = MineController.State.DONE;
            this.stopActions();
            this.status = "Đã đào sạch khung";
            this.say("§aĐã đào sạch " + this.region.describe() + " — " + this.strikes + " nhát.");
            this.layer = -1;
            this.plan = null;
            this.laneSweep = null;
         }
      }
   }

   /** 1.14.6: phần hàng bị khối giữa để dành chắn — hết các hàng thì quay lại đào nốt đúng làn đó (mỗi phần tối đa 2 lần) */
   /** ô không được đào khi tìm đường: đập nước + mặt 3x3 các phần hàng để dành (1.14.6) */
   Set<BlockPos> keepIntact() {
      this.refreshSuspendedCells();
      if (this.suspendedCells.isEmpty()) {
         return this.waterDams;
      } else {
         HashSet<BlockPos> var1 = new HashSet<>(this.waterDams);
         var1.addAll(this.suspendedCells);
         return var1;
      }
   }

   private void refreshSuspendedCells() {
      this.suspendedCells.clear();

      for (List<LayerPlan.Slice> var2 : this.suspendedRows) {
         for (LayerPlan.Slice var4 : var2) {
            for (BlockPos var6 : Cells.plane(var4.center(), var4.face())) {
               if (this.region != null && this.region.inside(var6)) {
                  this.suspendedCells.add(var6.toImmutable());
               }
            }
         }
      }
   }

   /** mặt 3x3 của nhát đào đường chạm phần hàng để dành (1.14.6) */
   boolean suspendedInPlane(BlockPos var1, Direction var2) {
      if (this.suspendedRows.isEmpty()) {
         return false;
      } else {
         this.refreshSuspendedCells();

         for (BlockPos var4 : Cells.plane(var1, var2)) {
            if (this.suspendedCells.contains(var4) && Cells.mineable(this.mc, var4)) {
               return true;
            }
         }

         return false;
      }
   }

   void suspendRow(List<LayerPlan.Slice> var1) {
      if (!var1.isEmpty()) {
         BlockPos var2 = var1.getFirst().center();
         for (List<LayerPlan.Slice> var4 : this.suspendedRows) {
            if (var4.getFirst().center().equals(var2)) {
               return;
            }
         }

         this.suspendedRows.add(new ArrayList<>(var1));
      }
   }

   private MineController.Task resumeRow() {
      while (!this.suspendedRows.isEmpty()) {
         List<LayerPlan.Slice> var1 = this.suspendedRows.removeFirst();
         ArrayList<LayerPlan.Slice> var2 = new ArrayList<>();

         for (LayerPlan.Slice var4 : var1) {
            if (!this.sliceClear(var4) || this.middleDeferred.contains(var4.center())) {
               var2.add(var4);
            }
         }

         BlockPos var8 = var1.getFirst().center();
         int var9 = this.resumedRows.merge(var8, 1, Integer::sum);
         if (!var2.isEmpty() && var9 <= 2) {
            // khối giữa để dành được thử lại từ đầu (đủ thang 5 lần), các ô 3x3 của nó không còn tính là để dành
            for (LayerPlan.Slice var6 : var2) {
               for (BlockPos var7 : Cells.plane(var6.center(), var6.face())) {
                  this.middleDeferred.remove(var7);
               }

               this.middleTries.remove(var6.center());
            }

            AutoMineMod.LOGGER.info(
               "[AutoMine] Quay lại hàng {}: đào nốt {} lát từ {} (lần {})", new Object[]{var2.getFirst().strip() + 1, var2.size(), var2.getFirst().center().toShortString(), var9}
            );
            return new MineController.RowTask(this.plan, var2);
         }
      }

      return null;
   }

   /** 1.14.6: lập cùng một hàng từ cùng một lát lặp lại mà không đập được gì → bỏ phần hàng đó cho vét cuối tầng, không lặp mãi */
   private boolean rowLooping(BlockPos var1) {
      if (var1.equals(this.loopRowAt) && this.confirmed == this.loopRowConfirmed) {
         if (++this.loopRowCount >= 4) {
            AutoMineMod.LOGGER.info("[AutoMine] Hàng {}: lập lại {} lần từ {} mà không đào thêm được → để vét cuối tầng", this.plan.current() == null ? "?" : this.plan.current().strip() + 1, this.loopRowCount, var1.toShortString());
            this.plan.index = Math.max(this.plan.index + 1, this.plan.runEnd(this.plan.index));
            this.loopRowAt = null;
            this.loopRowCount = 0;
            return true;
         }
      } else {
         this.loopRowAt = var1.toImmutable();
         this.loopRowConfirmed = this.confirmed;
         this.loopRowCount = 1;
      }

      return false;
   }

   private boolean sliceClear(LayerPlan.Slice var1) {
      int var2 = this.region.floor(this.layer);
      if (!Cells.loaded(this.mc, var1.center())) {
         return false;
      } else {
         for (BlockPos var4 : Cells.plane(var1.center(), var1.face())) {
            if (this.region.inside(var4) && var4.getY() >= var2 && !this.skippedOrClear(var4)) {
               return false;
            }
         }

         return true;
      }
   }

   private boolean layerHasWork(int var1) {
      for (int var2 = this.region.floor(var1); var2 <= this.region.top(var1); var2++) {
         for (int var3 = this.region.x0; var3 <= this.region.x1; var3++) {
            for (int var4 = this.region.z0; var4 <= this.region.z1; var4++) {
               BlockPos var5 = new BlockPos(var3, var2, var4);
               if (Cells.loaded(this.mc, var5) && Cells.mineable(this.mc, var5) && !this.gaveUp.contains(var5)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private int nearestKnownLayer() {
      int var1 = -1;
      int var2 = Integer.MAX_VALUE;
      int var3 = this.feet().getY();

      for (int var4 = 0; var4 < this.region.layerCount(); var4++) {
         if (this.layerHasWork(var4)) {
            int var5 = Math.abs(this.region.floor(var4) - var3);
            if (var5 < var2) {
               var1 = var4;
               var2 = var5;
            }
         }
      }

      return var1;
   }

   private int firstLayerWithWork(int var1) {
      for (int var2 = Math.max(0, var1); var2 < this.region.layerCount(); var2++) {
         if (this.layerHasWork(var2)) {
            return var2;
         }
      }

      for (int var5 = Math.max(0, var1); var5 < this.region.layerCount(); var5++) {
         for (int var3 = this.region.x0; var3 <= this.region.x1; var3++) {
            for (int var4 = this.region.z0; var4 <= this.region.z1; var4++) {
               if (!Cells.loaded(this.mc, new BlockPos(var3, this.region.floor(var5), var4))) {
                  return var5;
               }
            }
         }
      }

      return -1;
   }

   private MineController.Task inspectUnknownLayer() {
      Keys.releaseMove(this.mc);
      this.status = "Tầng phía trên chưa thấy khối cần đào — kiểm tra chunk ở độ cao hiện tại";
      BlockPos var1 = this.feet();
      BlockPos var2 = null;
      double var3 = Double.MAX_VALUE;

      for (int var5 = this.region.x0; var5 <= this.region.x1; var5 += 4) {
         for (int var6 = this.region.z0; var6 <= this.region.z1; var6 += 4) {
            BlockPos var7 = new BlockPos(var5, this.region.floor(this.layer), var6);
            double var8 = var7.getSquaredDistance(var1);
            if (!Cells.loaded(this.mc, var7) && var8 < var3) {
               var3 = var8;
               var2 = var7;
            }
         }
      }

      if (var2 == null) {
         this.layer = -1;
         this.plan = null;
         return null;
      } else {
         int var10 = Integer.signum(var2.getX() - var1.getX());
         int var11 = Integer.signum(var2.getZ() - var1.getZ());
         if (var10 == 0 && var11 == 0) {
            return null;
         } else {
            BlockPos var12 = var1.add(var10, 0, var10 != 0 ? 0 : var11);
            MineController.WalkTask var13 = new MineController.WalkTask(var12, 0.22, true, "tới chunk chưa tải ở cùng độ cao");
            var13.yLo = var1.getY();
            var13.yHi = var1.getY();
            return var13;
         }
      }
   }

   private List<BlockPos> scanLayer() {
      ArrayList var1 = new ArrayList();
      int var2 = 0;
      int var3 = this.region.floor(this.layer);
      int var4 = this.region.top(this.layer);
      int var5 = 0;

      for (int var6 = var3; var6 <= var4; var6++) {
         for (int var7 = this.region.x0; var7 <= this.region.x1; var7++) {
            for (int var8 = this.region.z0; var8 <= this.region.z1; var8++) {
               var5++;
               BlockPos var9 = new BlockPos(var7, var6, var8);
               if (!Cells.loaded(this.mc, var9)) {
                  var2++;
               } else if (Cells.mineable(this.mc, var9) && !this.gaveUp.contains(var9)) {
                  var1.add(var9);
               }
            }
         }
      }

      this.layerCells = Math.max(1, var5);
      if (!var1.isEmpty() || var2 > 0) {
         this.layerQuietSince = this.finalQuietSince = -1;
      }

      this.layerRemaining = var1.size() + var2;
      this.layerUnloaded = var2;
      this.scanAt = this.ticks;
      return var1;
   }

   private int skippedInLayer() {
      int var1 = 0;
      int var2 = this.region.floor(this.layer);
      int var3 = this.region.top(this.layer);

      for (BlockPos var5 : this.skipped) {
         if (var5.getY() >= var2 && var5.getY() <= var3) {
            var1++;
         }
      }

      for (BlockPos var7 : this.gaveUp) {
         if (var7.getY() >= var2 && var7.getY() <= var3 && !this.skipped.contains(var7)) {
            var1++;
         }
      }

      return var1;
   }

   private BlockPos unloadedHint() {
      BlockPos var1 = this.feet();
      BlockPos var2 = null;
      double var3 = Double.MAX_VALUE;
      int var5 = this.region.floor(this.layer);

      for (int var6 = this.region.x0; var6 <= this.region.x1; var6 += 4) {
         for (int var7 = this.region.z0; var7 <= this.region.z1; var7 += 4) {
            BlockPos var10 = new BlockPos(var6, var5, var7);
            double var8;
            if (!Cells.loaded(this.mc, var10) && (var8 = var10.getSquaredDistance(var1)) < var3) {
               var3 = var8;
               var2 = var10;
            }
         }
      }

      if (var2 == null) {
         return null;
      } else {
         Map<BlockPos, Integer> var14 = PathFinder.distances(this.mc, var1, 24, this.layerYMin(), this.layerYMax(), 4000, this.avoid);
         BlockPos var15 = null;
         double var16 = Double.MAX_VALUE;

         for (BlockPos var11 : var14.keySet()) {
            double var12 = var11.getSquaredDistance(var2);
            if (var12 < var16) {
               var16 = var12;
               var15 = var11;
            }
         }

         return var15 != null && !var15.equals(var1) ? var15 : null;
      }
   }

   private Vec3d miningPoint(Vec3d var1, BlockPos var2, Direction var3, boolean var4) {
      return var4
         ? Cells.visibleFacePoint(this.mc, var1, var2, var3, this.reach() - 0.05)
         : Cells.drillFacePoint(this.mc, var1, var2, var3, this.reach() - 0.05);
   }

   private Vec3d miningEyeAt(BlockPos var1, boolean var2) {
      float var3 = this.mc.player.getEyeHeight(var2 ? EntityPose.CROUCHING : EntityPose.STANDING);
      return new Vec3d(var1.getX() + 0.5, var1.getY() + var3, var1.getZ() + 0.5);
   }

   private List<BlockPos> cleanupPlane(BlockPos var1, Direction var2, boolean var3, boolean var4) {
      List<BlockPos> var5 = var3 ? List.of(var1) : Cells.plane(var1, var2);
      int var6 = this.layer >= 0 ? this.region.floor(this.layer) : this.feet().getY();

      for (BlockPos var8 : var5) {
         if (Cells.mineable(this.mc, var8) && (this.waterDams.contains(var8) || !this.region.inside(var8) || !var4 && var8.getY() < var6)) {
            return null;
         }
      }

      return var5;
   }

   /**
    * 1.14.0: chọn nhát đập vét sót.
    * - Xét CHUNG chỗ đang đứng và các chỗ đứng đi tới được (trước đây hễ đứng tại chỗ đập được khối nào là đập ngay,
    *   kể cả khối trên/dưới của cột 3 khối → 2 nhát, trong khi bước 1 ô để đập khối giữa thì 1 nhát là xong).
    * - Điểm = số khối cần đào mặt 3x3 phá được; mặt 3x3 lấn sang tầng khác bị trừ nặng (đập khối giữa tầng theo mặt ngang
    *   phủ đúng 3 lớp của tầng, không đụng tầng trên/dưới); mỗi bước đi trừ 1,5.
    */
   private MineController.Task chooseStrike(List<BlockPos> var1, String var2, boolean var3) {
      if (var1.isEmpty()) {
         return null;
      } else {
         BlockPos var4 = this.feet();
         List<BlockPos> var5 = new ArrayList<>(var1);
         // 1.14.3: khối đang chờ thử lại (server vừa trả lại) → không đập lẻ khối xung quanh nó; tới lượt thì đập 3x3 vào chính nó
         List<BlockPos> var42 = new ArrayList<>();

         for (BlockPos var44 : var1) {
            if (!this.retryReady(var44) && !this.waterDams.contains(var44)) {
               var42.add(var44);
            }
         }

         var5.removeIf(
            var2x -> !this.retryReady(var2x)
               || this.gaveUp.contains(var2x)
               || this.waterDams.contains(var2x)
               || this.expectedGone(var2x)
               || var42.stream()
                  .anyMatch(var1x -> Math.abs(var1x.getX() - var2x.getX()) <= 1 && Math.abs(var1x.getY() - var2x.getY()) <= 1 && Math.abs(var1x.getZ() - var2x.getZ()) <= 1)
         );
         var5.sort(Comparator.comparingDouble(var1x -> var1x.getSquaredDistance(this.mc.player.getPos())));
         if (var5.size() > 32) {
            var5 = var5.subList(0, 32);
         }

         HashSet<BlockPos> var6 = new HashSet<>(var1);
         int var7 = this.layer >= 0 ? this.region.floor(this.layer) : var4.getY();
         int var8 = this.layer >= 0 ? this.region.top(this.layer) : var4.getY() + 2;
         boolean var9 = var4.getY() == var7 && this.mc.player.isOnGround();
         List<Entry<BlockPos, Integer>> var10 = new ArrayList<>(
            PathFinder.distances(this.mc, var4, 14, var7, var7, 3000, this.avoid, this.bridgeAllowed()).entrySet()
         );
         var10.sort(Comparator.comparingInt(Entry::getValue));
         double var11 = -Double.MAX_VALUE;
         BlockPos var13 = null;
         BlockPos var14 = null;
         Direction var15 = null;
         boolean var16 = false;

         for (BlockPos var18 : var5) {
            double var19 = Math.sqrt(var18.getSquaredDistance(var4)) * 0.5;

            for (Direction var22 : Direction.values()) {
               for (boolean var26 : new boolean[]{false, true}) {
                  if (var26 || !Boolean.FALSE.equals(this.threeByThree) && this.fails.getOrDefault(var18, 0) < 3) {
                     List<BlockPos> var27 = this.cleanupPlane(var18, var22, var26, var3);
                     if (var27 != null) {
                        double var33 = StrikeScore.score(
                           var18,
                           var22,
                           var26,
                           var27,
                           var6,
                           var1x -> Cells.mineable(this.mc, var1x),
                           var1x -> this.region.inside(var1x) && !this.skipped.contains(var1x),
                           var7,
                           var8
                        );
                        if (!Double.isNaN(var33)) {
                           var33 -= var19;
                           if (!(var33 <= var11)) {
                              if (var9 && !this.planeHasFloorOf(var27, var4) && !this.protectedFooting(var27)) {
                                 Vec3d var35 = var26 ? this.mc.player.getPos().add(0.0, this.mc.player.getEyeHeight(EntityPose.CROUCHING), 0.0) : this.mc.player.getEyePos();
                                 if (this.miningPoint(var35, var18, var22, var26) != null) {
                                    var11 = var33;
                                    var13 = var18;
                                    var15 = var22;
                                    var14 = null;
                                    var16 = var26;
                                    continue;
                                 }
                              }

                              for (Entry<BlockPos, Integer> var37 : var10) {
                                 double var38 = var33 - var37.getValue() * 1.5;
                                 if (var38 <= var11) {
                                    break;
                                 }

                                 BlockPos var40 = var37.getKey();
                                 if (!(var40.getSquaredDistance(var18) > 30.0)
                                    && !this.planeHasFloorOf(var27, var40)
                                    && this.miningPoint(this.miningEyeAt(var40, var26), var18, var22, var26) != null) {
                                    var11 = var38;
                                    var13 = var18;
                                    var15 = var22;
                                    var14 = var40;
                                    var16 = var26;
                                    break;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         if (var13 == null) {
            return null;
         } else {
            MineController.StrikeTask var41 = new MineController.StrikeTask(var13, var15, var2, true);
            var41.single = var16 || this.damInPlane(var13, var15);
            var41.preferStand = var14;
            return var41;
         }
      }
   }

   private boolean planeHasFloorOf(List<BlockPos> var1, BlockPos var2) {
      BlockPos var3 = var2.down();
      if (!Cells.mineable(this.mc, var3)) {
         return false;
      } else {
         for (BlockPos var5 : var1) {
            if (var5.equals(var3)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean standsOnPlane(BlockPos var1, BlockPos var2, Direction var3, boolean var4) {
      return this.planeHasFloorOf(var4 ? Cells.plane(var2, var3) : List.of(var2), var1);
   }

   private BlockPos findStand(BlockPos var1, Direction var2, int var3, int var4, boolean var5, Set<BlockPos> var6) {
      Map<BlockPos, Integer> var7 = PathFinder.distances(this.mc, this.feet(), 16, var3, var4, 4000, this.avoid, this.bridgeAllowed());
      List<BlockPos> var8 = !var5 && !Boolean.FALSE.equals(this.threeByThree) ? Cells.plane(var1, var2) : List.of(var1);
      BlockPos var9 = null;
      double var10 = Double.MAX_VALUE;

      for (Entry<BlockPos, Integer> var13 : var7.entrySet()) {
         BlockPos var14 = (BlockPos)var13.getKey();
         double var15 = ((Integer)var13.getValue()).intValue() + Math.sqrt(var14.getSquaredDistance(this.mc.player.getPos())) * 0.1;
         if (!var6.contains(var14)
            && !(var15 >= var10)
            && !(var14.getSquaredDistance(var1) > 30.0)
            && !this.planeHasFloorOf(var8, var14)
            && this.miningPoint(this.miningEyeAt(var14, var5), var1, var2, var5) != null) {
            var9 = var14;
            var10 = var15;
         }
      }

      return var9;
   }

   private void calibrate(int var1, int var2) {
      if (var1 >= 4) {
         this.calSamples++;
         this.calSum += (double)var2 / var1;
         if (this.calSamples >= 3 && this.threeByThree == null) {
            double var3 = this.calSum / this.calSamples;
            if (var3 >= 0.45) {
               this.threeByThree = true;
               this.say("Cúp phá 3x3 ✓ (trung bình " + Math.round(var3 * 8.0) + "/8 ô xung quanh mỗi nhát).");
            } else if (this.calSum == 0.0) {
               this.threeByThree = false;
               this.say(
                  "§eCúp đang cầm KHÔNG phá 3x3 (0 ô xung quanh sau "
                     + this.calSamples
                     + " nhát) → vẫn đào tiếp từng khối. Kiểm tra lại cúp (đặt tên vào \"Tên cúp\" trong menu H)."
               );
            }
         }
      }
   }

   private final class BuyXpTask extends MineController.Task {
      boolean started;

      @Override
      String label() {
         return "Mua bình XP ở /"
            + MineController.this.config.shopCommand
            + (MineController.this.shop.active() ? " · " + MineController.this.shop.status() : "");
      }

      @Override
      MineController.Result tick() {
         this.age++;
         Keys.releaseMove(MineController.this.mc);
         if (!this.started) {
            this.started = true;
            MineController.this.say(
               "Hết bình XP → mua "
                  + MineController.this.config.xpBuyAmount
                  + " ở /"
                  + MineController.this.config.shopCommand
                  + " (trần giá "
                  + MineController.this.config.shopMaxPrice
                  + ")."
            );
            MineController.this.shop
               .start(MineController.this.mc, Items.EXPERIENCE_BOTTLE, MineController.this.config.xpBuyAmount, MineController.this.config.shopMaxPrice);
            return MineController.Result.RUNNING;
         } else if (MineController.this.shop.active()) {
            MineController.this.shop.tick(MineController.this.mc);
            MineController.this.status = MineController.this.shop.status();
            return MineController.Result.RUNNING;
         } else if (MineController.this.shop.result() == ShopBrowser.Result.BOUGHT) {
            MineController.this.say(MineController.this.shop.status());
            return MineController.Result.DONE;
         } else {
            MineController.this.noXpUntil = MineController.this.ticks + 6000;
            MineController.this.say("§eKhông mua được bình XP: " + MineController.this.shop.status() + " → thử lại sau 5 phút.");
            return this.failWith(MineController.this.shop.status());
         }
      }

      @Override
      void abort() {
         if (MineController.this.shop.active()) {
            MineController.this.shop.cancel(MineController.this.mc);
         }

         super.abort();
      }
   }

   private final class ClimbTask extends MineController.Task {
      final int target;
      MineController.WalkTask walk;
      MineController.PillarTask pillar;
      int attempts;

      ClimbTask(int nullx) {
         this.target = nullx;
      }

      @Override
      String label() {
         return "Lên lại sàn Y=" + this.target + (this.walk != null ? " · " + this.walk.label() : (this.pillar != null ? " · kê cột" : ""));
      }

      @Override
      boolean walking() {
         return this.walk != null && this.walk.walking();
      }

      @Override
      boolean interruptible() {
         return false;
      }

      @Override
      MineController.Result tick() {
         this.age++;
         if ((this.age == 1 || this.age % 20 == 0)
            && MineController.this.region != null
            && MineController.this.layer >= 0
            && this.target == MineController.this.region.floor(MineController.this.layer)
            && !MineController.this.layerHasWork(MineController.this.layer)) {
            return MineController.Result.DONE;
         } else if (MineController.this.mc.player.isOnGround()
            && MineController.this.feet().getY() >= this.target
            && MineController.this.region.insideColumn(MineController.this.feet().getX(), MineController.this.feet().getZ())) {
            return MineController.Result.DONE;
         } else if (this.age > 3600) {
            return this.failWith("Bắc cầu lên lại quá 3 phút");
         } else if (this.pillar != null) {
            MineController.Result var5 = this.pillar.tick();
            if (var5 == MineController.Result.RUNNING) {
               return var5;
            } else {
               this.pillar.finish();
               this.pillar = null;
               return var5 == MineController.Result.FAILED && ++this.attempts > 3
                  ? this.failWith("Không kê được cột để lên lại")
                  : MineController.Result.RUNNING;
            }
         } else if (this.walk != null) {
            MineController.Result var4 = this.walk.tick();
            if (var4 == MineController.Result.RUNNING) {
               return var4;
            } else {
               String var6 = this.walk.why;
               this.walk.finish();
               this.walk = null;
               if (var4 == MineController.Result.FAILED) {
                  // 1.14.5: đi bộ → đào bậc (3 lần) → kê cột
                  AutoMineMod.LOGGER.info("[AutoMine] Lên lại sàn lần {} không được: {}", this.attempts + 1, var6);
                  if (++this.attempts < 4) {
                     return MineController.Result.RUNNING;
                  }

                  if (MineController.this.fillerCount() == 0) {
                     this.fatal = true;
                     return this.failWith("Hết khối bắc cầu để lên lại");
                  }

                  if (this.attempts > 4) {
                     return this.failWith("Không tìm được đường bắc cầu/cột lên lại");
                  }

                  this.pillar = MineController.this.new PillarTask(MineController.this.feet());
               }

               return MineController.Result.RUNNING;
            }
         } else if (!MineController.this.mc.player.isOnGround()) {
            Keys.releaseMove(MineController.this.mc);
            return MineController.Result.RUNNING;
         } else {
            BlockPos var1 = MineController.this.feet();
            if (var1.getY() >= this.target && MineController.this.region.insideColumn(var1.getX(), var1.getZ())) {
               return MineController.Result.DONE;
            } else if (var1.getY() < this.target
               && MineController.this.region.insideColumn(var1.getX(), var1.getZ())
               && this.attempts >= 4
               && MineController.this.fillerCount() > 0) {
               // 1.14.5: đi bộ ra và đào bậc lên đều không được mới kê cột
               this.pillar = MineController.this.new PillarTask(var1);
               return MineController.Result.RUNNING;
            } else if (this.attempts == 0) {
               // 1.14.5: rơi xuống hố → tìm đường đi bộ lên (dốc tự nhiên, bậc 1 ô) tới sàn tầng, không đặt khối
               BlockPos var5 = new BlockPos(
                  Math.clamp((long)var1.getX(), MineController.this.region.x0, MineController.this.region.x1),
                  this.target,
                  Math.clamp((long)var1.getZ(), MineController.this.region.z0, MineController.this.region.z1)
               );
               this.walk = MineController.this.new WalkTask(var5, 0.3, false, "lên lại sàn (đi bộ)");
               this.walk.goalTest = var1x -> var1x.getY() == this.target && MineController.this.region.insideColumn(var1x.getX(), var1x.getZ());
               this.walk.yLo = Math.min(var1.getY(), this.target);
               this.walk.yHi = Math.max(var1.getY(), this.target);
               return MineController.Result.RUNNING;
            } else {
               BlockPos var2 = new BlockPos(
                  Math.clamp((long)var1.getX(), MineController.this.region.x0, MineController.this.region.x1),
                  this.target,
                  Math.clamp((long)var1.getZ(), MineController.this.region.z0, MineController.this.region.z1)
               );
               // 1.14.5: lần 2 đào bậc thang lên (không đặt khối); từ lần 3 mới được kê cột / bắc cầu
               boolean var4 = this.attempts >= 4 || MineController.this.bridgeAllowed();
               PathFinder.Dig var3 = new PathFinder.Dig(MineController.this.region, false, Math.min(MineController.this.region.y0, var1.getY()), var4);
               this.walk = MineController.this.new WalkTask(var2, 0.24, var3, var4 ? "bắc cầu lên lại sàn" : "đào bậc lên lại sàn", 20000, 1.0);
               this.walk.verticalAccess = true;
               this.walk.bridgeOk = var4;
               this.walk.goalTest = var1x -> var1x.getY() == this.target && MineController.this.region.insideColumn(var1x.getX(), var1x.getZ());
               this.walk.yLo = Math.min(var1.getY(), this.target);
               this.walk.yHi = Math.max(var1.getY(), this.target);
               return MineController.Result.RUNNING;
            }
         }
      }

      @Override
      void finish() {
         if (this.walk != null) {
            this.walk.finish();
         }

         if (this.pillar != null) {
            this.pillar.finish();
         }

         super.finish();
      }
   }

   private final class DescendTask extends MineController.Task {
      final int floor;
      final int startY;
      final boolean inLayer;
      MineController.WalkTask walk;
      MineController.StrikeTask strike;
      int failures;

      DescendTask(int nullx) {
         this.floor = nullx;
         this.startY = MineController.this.feet().getY();
         this.inLayer = this.startY <= MineController.this.region.top(MineController.this.layer);
      }

      @Override
      String label() {
         return "Tự đào xuống Y=" + this.floor + (this.strike != null ? " · " + this.strike.label() : "");
      }

      @Override
      boolean walking() {
         return this.walk != null;
      }

      @Override
      boolean interruptible() {
         return this.strike == null;
      }

      @Override
      MineController.Result tick() {
         this.age++;
         if (this.age > 3600) {
            return this.failWith("Chưa xuống được tầng sau 3 phút");
         } else {
            BlockPos var1 = MineController.this.feet();
            if (MineController.this.mc.player.isOnGround() && var1.getY() <= this.floor && MineController.this.region.insideColumn(var1.getX(), var1.getZ())) {
               return MineController.Result.DONE;
            } else if (this.strike != null) {
               MineController.Result var14 = this.strike.tick();
               if (var14 == MineController.Result.RUNNING) {
                  return var14;
               } else {
                  this.strike.finish();
                  this.strike = null;
                  return var14 == MineController.Result.FAILED && ++this.failures >= 3
                     ? this.failWith("Không đập được khối dưới chân")
                     : MineController.Result.RUNNING;
               }
            } else if (this.walk != null) {
               MineController.Result var13 = this.walk.tick();
               if (var13 == MineController.Result.RUNNING) {
                  return var13;
               } else {
                  String var15 = this.walk.why;
                  this.walk.finish();
                  this.walk = null;
                  return var13 == MineController.Result.FAILED ? this.failWith(var15) : MineController.Result.RUNNING;
               }
            } else {
               Keys.releaseMove(MineController.this.mc);
               if (!MineController.this.mc.player.isOnGround()) {
                  return MineController.Result.RUNNING;
               } else {
                  int var2 = MineController.this.region.sizeX() >= 3 ? 1 : 0;
                  int var3 = MineController.this.region.sizeZ() >= 3 ? 1 : 0;
                  int var4 = Math.clamp((long)var1.getX(), MineController.this.region.x0 + var2, MineController.this.region.x1 - var2);
                  int var5 = Math.clamp((long)var1.getZ(), MineController.this.region.z0 + var3, MineController.this.region.z1 - var3);
                  if (var1.getX() == var4 && var1.getZ() == var5) {
                     if (!(Keys.drive(MineController.this.mc, var4 + 0.5, var5 + 0.5, 0.12, true) > 0.16)
                        && !(MineController.this.mc.player.getVelocity().horizontalLength() > 0.04)) {
                        BlockPos var16 = var1.down();
                        if (Cells.mineable(MineController.this.mc, var16)
                           && Cells.floor(MineController.this.mc, var16.down())
                           && !ServerSync.pending(MineController.this.mc, var16.down())) {
                           this.strike = MineController.this.new StrikeTask(var16, Direction.UP, "đào xuống từng bậc", false);
                           return MineController.Result.RUNNING;
                        } else {
                           if (this.stepDown(var1)) {
                              return MineController.Result.RUNNING;
                           }

                           PathFinder.Dig var17 = new PathFinder.Dig(MineController.this.region, true, this.floor, true);
                           var17.spill = true;
                           this.walk = MineController.this.new WalkTask(new BlockPos(var4, this.floor, var5), 0.2, var17, "mở đường xuống quanh hố", 16000, 1.0);
                           this.walk.replans = 2;
                           this.walk.goalTest = var1x -> var1x.getY() == this.floor && MineController.this.region.insideColumn(var1x.getX(), var1x.getZ());
                           this.walk.yLo = this.floor;
                           this.walk.yHi = var1.getY();
                           return MineController.Result.RUNNING;
                        }
                     } else {
                        return MineController.Result.RUNNING;
                     }
                  } else if (this.stepDown(var1)) {
                     // 1.14.5: bên cạnh có hố / bậc thấp hơn trong khung → bước xuống luôn, không đi tới cột trong (có thể là ô trên miệng hố,
                     // không đứng được → tìm đường mãi không ra, rồi bắc cầu lấp hố)
                     return MineController.Result.RUNNING;
                  } else {
                     PathFinder.Dig var6 = new PathFinder.Dig(MineController.this.region, true, this.floor, true);
                     var6.spill = true;
                     this.walk = MineController.this.new WalkTask(
                        new BlockPos(var4, var1.getY(), var5), 0.18, var6, "tới cột gần nhất để đào xuống", 12000, 1.0
                     );
                     return MineController.Result.RUNNING;
                  }
               }
            }
         }
      }

      /** ô kề bên thấp hơn 1–3 ô (vẫn trên sàn tầng cần xuống), trong khung, đứng được, đường rơi trống → bước xuống đó */
      private boolean stepDown(BlockPos var1) {
         for (Direction var3 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            for (int var4 = 1; var4 <= 3; var4++) {
               BlockPos var5 = var1.offset(var3).down(var4);
               if (var5.getY() >= this.floor
                  && MineController.this.region.insideColumn(var5.getX(), var5.getZ())
                  && Cells.standable(MineController.this.mc, var5)
                  && !Cells.hazardAt(MineController.this.mc, var5.down())) {
                  boolean var6 = true;

                  for (int var7 = var5.getY(); var7 <= var1.getY() + 1; var7++) {
                     if (!Cells.passable(MineController.this.mc, new BlockPos(var5.getX(), var7, var5.getZ()))) {
                        var6 = false;
                     }
                  }

                  if (var6) {
                     this.walk = MineController.this.new WalkTask(var5, 0.18, false, "bước xuống ô có sàn đỡ");
                     this.walk.yLo = this.floor;
                     return true;
                  }
               }
            }
         }

         return false;
      }

      @Override
      void finish() {
         if (this.walk != null) {
            this.walk.finish();
         }

         if (this.strike != null) {
            this.strike.finish();
         }

         super.finish();
      }
   }

   private final class EatTask extends MineController.Task {
      final Item food;
      final int startHunger;

      EatTask(Item nullx) {
         this.food = nullx;
         this.startHunger = MineController.this.mc.player.getHungerManager().getFoodLevel();
      }

      @Override
      String label() {
         return "Ăn " + this.food.getName().getString();
      }

      @Override
      MineController.Result tick() {
         this.age++;
         int var1 = MineController.this.mc.player.getHungerManager().getFoodLevel();
         if (var1 > this.startHunger && this.age > 10 && !MineController.this.mc.player.isUsingItem()) {
            Keys.use(MineController.this.mc, false);
            AutoMineMod.LOGGER.info("[AutoMine] Ăn xong ({} → {})", this.startHunger, var1);
            return MineController.Result.DONE;
         } else if (this.age > 140) {
            Keys.use(MineController.this.mc, false);
            return this.failWith("ăn quá lâu");
         } else {
            Keys.releaseMove(MineController.this.mc);
            if (!MineController.this.mc.player.getMainHandStack().isOf(this.food)) {
               Keys.use(MineController.this.mc, false);
               if (Inv.count(MineController.this.mc, this.food) == 0) {
                  return this.failWith("hết đồ ăn");
               } else {
                  Inv.equip(MineController.this.mc, var1x -> var1x.isOf(this.food), 8);
                  return MineController.Result.RUNNING;
               }
            } else {
               MineController.this.lookAngles(MineController.this.mc.player.getYaw(), -88.0F);
               Keys.use(MineController.this.mc, true);
               if (!MineController.this.mc.player.isUsingItem() && this.age > 12 && this.age % 10 == 0) {
                  MineController.this.mc.interactionManager.interactItem(MineController.this.mc.player, Hand.MAIN_HAND);
               }

               MineController.this.status = "Đang ăn " + this.food.getName().getString() + " (" + var1 + "/20)";
               return MineController.Result.RUNNING;
            }
         }
      }
   }

   private final class EntryTask extends MineController.Task {
      final BlockPos target;
      final boolean outside;
      MineController.WalkTask walk;
      int stage;
      List<MineController.Hit> opens;
      int openIdx;
      MineController.StrikeTask strike;

      EntryTask(BlockPos nullx, boolean nullxx) {
         this.target = nullx.toImmutable();
         this.outside = nullxx;
      }

      @Override
      String label() {
         return "Vào góc bắt đầu tầng "
            + (MineController.this.layer + 1)
            + " tại "
            + this.target.toShortString()
            + (this.strike != null ? " · " + this.strike.label() : (this.walk != null ? " · " + this.walk.label() : ""));
      }

      @Override
      boolean walking() {
         return this.walk != null && this.walk.walking();
      }

      @Override
      boolean interruptible() {
         return this.stage == 0 ? this.walk == null || this.walk.interruptible() : this.strike == null;
      }

      @Override
      MineController.Result tick() {
         this.age++;
         if (this.age > 12000) {
            return this.failWith("vào góc bắt đầu tầng quá 10 phút");
         } else {
            BlockPos var1 = MineController.this.feet();
            int var2 = MineController.this.region.floor(MineController.this.layer);
            if (this.stage == 0) {
               if (MineController.this.mc.player.isOnGround() && var1.equals(this.target)) {
                  if (this.walk != null) {
                     this.walk.finish();
                     this.walk = null;
                  }

                  this.stage = 1;
                  return MineController.Result.RUNNING;
               } else {
                  if (this.walk == null) {
                     if (!MineController.this.mc.player.isOnGround()) {
                        return MineController.Result.RUNNING;
                     }

                     boolean var5 = var1.getY() < var2;
                     PathFinder.Dig var12 = new PathFinder.Dig(MineController.this.region, this.outside, var5 ? Integer.MIN_VALUE : var2, true);
                     var12.spill = true;
                     this.walk = MineController.this.new WalkTask(
                        this.target, 0.25, var12, "vào góc bắt đầu tầng " + (MineController.this.layer + 1), 40000, 1.8
                     );
                     this.walk.yLo = var5 ? var1.getY() - 2 : var2;
                     this.walk.yHi = Math.max(var1.getY(), var2);
                     long var6 = (long)MineController.this.layer << 40 ^ this.target.asLong() ^ (this.outside ? 7L : 0L);
                     if (var6 != MineController.this.entryAnnounced) {
                        MineController.this.entryAnnounced = var6;
                        MineController.this.say(
                           "Tự tìm đường tới góc bắt đầu tầng "
                              + (MineController.this.layer + 1)
                              + " ("
                              + this.target.toShortString()
                              + ", đang đứng Y="
                              + var1.getY()
                              + "): đá chắn thì đập, thấp thì làm bậc/bắc cột, cao thì đào xuống"
                              + (this.outside ? " — cho phép đào cả ngoài khung" : "")
                              + "."
                        );
                     }
                  }

                  MineController.Result var10;
                  if ((var10 = this.walk.tick()) == MineController.Result.RUNNING) {
                     return var10;
                  } else {
                     String var13 = this.walk.why;
                     this.walk.finish();
                     this.walk = null;
                     return var10 == MineController.Result.FAILED ? this.failWith(var13) : MineController.Result.RUNNING;
                  }
               }
            } else if (this.strike != null) {
               MineController.Result var9 = this.strike.tick();
               if (var9 == MineController.Result.RUNNING) {
                  return var9;
               } else {
                  this.strike.finish();
                  this.strike = null;
                  this.openIdx++;
                  return MineController.Result.RUNNING;
               }
            } else {
               if (this.opens == null) {
                  this.opens = new ArrayList<>();
                  this.opens.add(new MineController.Hit(this.target.up(2), Direction.DOWN));

                  for (Object var4 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                     this.opens.add(new MineController.Hit(this.target.offset((Direction)var4).up(), ((Direction)var4).getOpposite()));
                  }
               }

               for (PathFinder.Dig var8 = new PathFinder.Dig(MineController.this.region, false, var2, false); this.openIdx < this.opens.size(); this.openIdx++) {
                  Object var11 = this.opens.get(this.openIdx);
                  if (Cells.mineable(MineController.this.mc, ((MineController.Hit)var11).c())
                     && MineController.this.region.inside(((MineController.Hit)var11).c())
                     && var8.planeOk(MineController.this.mc, ((MineController.Hit)var11).c(), ((MineController.Hit)var11).f())) {
                     this.strike = MineController.this.new StrikeTask(
                        ((MineController.Hit)var11).c(), ((MineController.Hit)var11).f(), "mở giếng góc tầng", false
                     );
                     return MineController.Result.RUNNING;
                  }
               }

               AutoMineMod.LOGGER.info("[AutoMine] Đã vào góc bắt đầu tầng {} tại {} và mở giếng", MineController.this.layer + 1, this.target.toShortString());
               MineController.this.entryAttempts = 0;
               MineController.this.entryOpened = true;
               return MineController.Result.DONE;
            }
         }
      }

      @Override
      void finish() {
         if (this.walk != null) {
            this.walk.finish();
         }

         if (this.strike != null) {
            this.strike.finish();
         }

         super.finish();
      }
   }

   private final class EscapeLavaTask extends MineController.Task {
      @Override
      String label() {
         return "Thoát lava";
      }

      @Override
      boolean exclusive() {
         return true;
      }

      @Override
      MineController.Result tick() {
         this.age++;
         if (!MineController.this.mc.player.isInLava()) {
            Keys.releaseMove(MineController.this.mc);
            Keys.jump(MineController.this.mc, false);
            return MineController.Result.DONE;
         } else {
            MineController.this.status = "§cĐang trong lava → chạy ra";
            Keys.jump(MineController.this.mc, true);
            if (MineController.this.lastSafe != null) {
               MineController.this.lookAlongTravel(MineController.this.lastSafe.getX() + 0.5, MineController.this.lastSafe.getZ() + 0.5);
               Keys.drive(MineController.this.mc, MineController.this.lastSafe.getX() + 0.5, MineController.this.lastSafe.getZ() + 0.5, 0.1, false);
            } else {
               Keys.drive(
                  MineController.this.mc,
                  MineController.this.mc.player.getX() - MineController.this.mc.player.getVelocity().x * 10.0,
                  MineController.this.mc.player.getZ() - MineController.this.mc.player.getVelocity().z * 10.0,
                  0.1,
                  false
               );
            }

            return this.age > 200 ? this.failWith("không thoát được lava") : MineController.Result.RUNNING;
         }
      }
   }

   private final class FillerSupplyTask extends MineController.Task {
      int retryAt;
      boolean started;

      @Override
      String label() {
         return "Lấy một stack Cobblestone từ Order";
      }

      @Override
      boolean exclusive() {
         return true;
      }

      @Override
      MineController.Result tick() {
         this.age++;
         Keys.releaseAll(MineController.this.mc);
         if (MineController.this.fillerCount() > 0 && !MineController.this.fillerOrders.active()) {
            return MineController.Result.DONE;
         } else {
            if (!this.started && this.age >= this.retryAt) {
               MineController.this.fillerOrders.start(Items.COBBLESTONE, 64);
               this.started = true;
            }

            if (MineController.this.fillerOrders.active()) {
               MineController.this.fillerOrders.tick(MineController.this.mc);
               MineController.this.status = MineController.this.fillerOrders.status();
               return MineController.Result.RUNNING;
            } else {
               if (this.started) {
                  if (MineController.this.fillerCount() > 0) {
                     MineController.this.failStreak = 0;
                     return MineController.Result.DONE;
                  }

                  if (MineController.this.fillerOrders.result() == OrderCollector.Result.BLOCKED) {
                     this.fatal = true;
                     return this.failWith(MineController.this.fillerOrders.status());
                  }

                  this.started = false;
                  this.retryAt = this.age + 200;
               }

               MineController.this.status = "Order chưa có Cobblestone được giao — chờ rồi lấy lại một stack";
               return MineController.Result.RUNNING;
            }
         }
      }

      @Override
      void finish() {
         if (MineController.this.fillerOrders.active()) {
            MineController.this.fillerOrders.cancel(MineController.this.mc);
         }

         super.finish();
      }
   }

   private final class HealTask extends MineController.Task {
      @Override
      String label() {
         return "Chờ hồi máu";
      }

      @Override
      MineController.Result tick() {
         this.age++;
         Keys.releaseMove(MineController.this.mc);
         float var1 = MineController.this.mc.player.getHealth();
         MineController.this.status = "Máu thấp (" + (int)var1 + ") → đứng chờ hồi";
         if (!(var1 >= Math.min(20, MineController.this.config.minHealth + 6)) && this.age <= 1200) {
            return MineController.this.mc.player.getHungerManager().getFoodLevel() < 18 ? MineController.Result.DONE : MineController.Result.RUNNING;
         } else {
            return MineController.Result.DONE;
         }
      }
   }

   private record Hit(BlockPos c, Direction f) {
   }

   /**
    * 1.14.0: nhảy qua khe theo cơ chế AutoBuild 3.0.121 (parkourStep): mặt khoá theo trục nhảy, A/D kéo về giữa làn,
    * mỗi tick mô phỏng điểm chạm nếu nhảy ngay, tới lúc chạm được ô đích thì nhảy (không đứng chờ, không lùi về điểm cố định);
    * trên không chọn giữ W / thả / giữ S cho điểm chạm gần ô đích nhất; chạm đất thì hãm nếu đường rẽ hướng khác.
    */
   private final class JumpTask extends MineController.Task {
      final BlockPos from;
      final BlockPos to;
      final int sx;
      final int sz;
      final int gap;
      final int dy;
      boolean straightAfter;
      boolean airborne;
      int runupFor;
      int backTicks;
      int runups;
      int landTicks;

      JumpTask(BlockPos nullx, BlockPos nullxx) {
         this.from = nullx.toImmutable();
         this.to = nullxx.toImmutable();
         this.sx = Integer.signum(nullxx.getX() - nullx.getX());
         this.sz = Integer.signum(nullxx.getZ() - nullx.getZ());
         this.gap = Math.max(1, Parkour.gap(nullxx.getX() - nullx.getX(), nullxx.getZ() - nullx.getZ()));
         this.dy = nullxx.getY() - nullx.getY();
      }

      @Override
      String label() {
         return "Nhảy qua khe " + this.gap + " ô" + (this.dy > 0 ? " lên 1 ô" : (this.dy < 0 ? " xuống 1 ô" : ""));
      }

      @Override
      boolean walking() {
         return true;
      }

      private void keys(boolean var1, boolean var2, boolean var3, boolean var4, boolean var5) {
         BotInput var6 = BotInput.INSTANCE;
         var6.forward = var1;
         var6.back = var2;
         var6.left = var3;
         var6.right = var4;
         var6.jumping = var5;
         var6.sneaking = false;
      }

      @Override
      MineController.Result tick() {
         this.age++;
         ClientPlayerEntity var1 = MineController.this.mc.player;
         if (this.age > 200) {
            return this.failWith("Nhảy qua khe quá lâu");
         } else if (!Cells.standable(MineController.this.mc, this.to)) {
            return this.failWith("Ô đáp không còn an toàn");
         } else {
            float var2 = Aim.yawTo(this.sx, this.sz);
            double var3 = (var1.getX() - (this.from.getX() + 0.5)) * this.sx + (var1.getZ() - (this.from.getZ() + 0.5)) * this.sz;
            double var5 = var1.getVelocity().x * this.sx + var1.getVelocity().z * this.sz;
            double var7 = (var1.getX() - (this.from.getX() + 0.5)) * Math.abs(this.sz) + (var1.getZ() - (this.from.getZ() + 0.5)) * Math.abs(this.sx);
            double var9 = var1.getVelocity().x * Math.abs(this.sz) + var1.getVelocity().z * Math.abs(this.sx);
            double var11 = this.gap + 0.55;
            double var13 = this.gap + (this.straightAfter ? 2.3 : 1.3);
            boolean var15 = (this.gap >= 2 || this.dy > 0) && var1.getHungerManager().getFoodLevel() > 6;
            if (!var1.isOnGround() && !var1.isTouchingWater()) {
               this.airborne = true;
               if (Math.abs(var7) <= 0.8 && var3 > -1.0 && var3 < this.gap + 3.0) {
                  var1.setYaw(var2);
                  var1.setHeadYaw(var2);
                  double var16 = Parkour.simulateFlight(var3, var5, var1.getY() - this.from.getY(), var1.getVelocity().y, false, 0, this.gap, this.dy);
                  int var18;
                  if (var3 >= this.gap + 0.5) {
                     var18 = !Double.isNaN(var16) && var16 > var13 ? -1 : 0;
                  } else if (!Double.isNaN(var16) && var16 >= var11) {
                     var18 = var16 <= var13 ? 0 : -1;
                  } else {
                     var18 = 1;
                  }

                  this.keys(var18 > 0, var18 < 0, false, false, false);
                  MineController.this.status = "Đang bay qua khe " + this.gap + " ô";
               } else {
                  this.keys(false, false, false, false, false);
               }

               return this.age > 120 && var1.getY() < this.to.getY() - 1.5 ? this.failWith("Rơi khỏi đường nhảy") : MineController.Result.RUNNING;
            } else {
               BlockPos var19 = MineController.this.feet();
               if (this.airborne) {
                  if (!var19.equals(this.to)) {
                     this.keys(false, false, false, false, false);
                     return this.failWith("Không đáp đúng ô " + this.to.toShortString());
                  }

                  double var20 = (var1.getX() - (this.to.getX() + 0.5)) * this.sx + (var1.getZ() - (this.to.getZ() + 0.5)) * this.sz;
                  if (!this.straightAfter && ++this.landTicks < 8 && var5 > 0.02 && var20 + var5 * 1.2 > 0.35) {
                     var1.setYaw(var2);
                     this.keys(false, true, false, false, false);
                     MineController.this.status = "Vừa chạm ô đích → hãm (S)";
                     return MineController.Result.RUNNING;
                  } else {
                     this.keys(false, false, false, false, false);
                     return MineController.Result.DONE;
                  }
               } else if (var19.equals(this.to)) {
                  return MineController.Result.DONE;
               } else if (var3 > 0.85 || var3 < -0.85 || Math.abs(var7) > 0.8 || var19.getY() != this.from.getY()) {
                  if (this.age > 60) {
                     return this.failWith("Không tới được ô lấy đà " + this.from.toShortString());
                  } else {
                     MineController.this.lookAlongTravel(this.from.getX() + 0.5, this.from.getZ() + 0.5);
                     Keys.drive(MineController.this.mc, this.from.getX() + 0.5, this.from.getZ() + 0.5, 0.2, true);
                     BotInput.INSTANCE.sneaking = false;
                     return MineController.Result.RUNNING;
                  }
               } else {
                  var1.setYaw(var2);
                  var1.setHeadYaw(var2);
                  var1.setPitch(0.0F);
                  double var22 = Math.cos(Math.toRadians(var2)) * Math.abs(this.sz) + Math.sin(Math.toRadians(var2)) * Math.abs(this.sx);
                  double var24 = var7 + var9 * 2.0;
                  boolean var26 = Math.abs(var24) > 0.05 && var24 * var22 < 0.0;
                  boolean var27 = Math.abs(var24) > 0.05 && var24 * var22 > 0.0;
                  boolean var28 = var1.isSneaking();
                  boolean var29 = var15 && !var28;
                  BlockPos var30 = BlockPos.ofFloored(var1.getX(), var1.getY() - 0.2, var1.getZ());
                  float var31 = MineController.this.mc.world.getBlockState(var30).getBlock().getSlipperiness();
                  double var32 = (var29 ? 0.13 : 0.1) * (0.21600002 / (var31 * var31 * var31)) * 0.98 * (!var26 && !var27 ? 1.0 : 0.7071);
                  double var34 = Parkour.simulateCross(var3, var5, var29, var31, 1, this.gap, this.dy);
                  double var36 = Parkour.simulateCross(var3, var5, var29, var31, -1, this.gap, this.dy);
                  boolean var38 = Parkour.window(var34, var36, var11, var13);
                  double var39 = var3 + var5 + var32;
                  BotInput.INSTANCE.sprinting = var15;
                  var1.setSprinting(var15 && !var28);
                  if (this.runupFor > 0) {
                     double var41 = var3 + Math.min(0.0, var5) * 1.2;
                     if (var41 > 0.0 && var3 > -0.1 && ++this.backTicks < 25) {
                        this.keys(false, true, var26, var27, false);
                        MineController.this.status = "Lùi lấy đà trong ô để nhảy qua khe " + this.gap + " ô";
                        return MineController.Result.RUNNING;
                     }

                     this.runupFor = 0;
                     this.backTicks = 0;
                  }

                  if (!var15 && var1.isSprinting()) {
                     var1.setSprinting(false);
                     this.keys(false, var39 > 0.72, false, false, false);
                     return MineController.Result.RUNNING;
                  } else {
                     double var43 = this.dy == 0 && !var29 && this.gap < 2 ? 0.15 : -0.3;
                     if (var3 >= var43 && var38 && !var28 && Math.abs(var7) <= 0.4 && Math.abs(var9) <= 0.12) {
                        this.keys(true, false, false, false, true);
                        MineController.this.status = "Nhảy qua khe " + this.gap + " ô";
                     } else if (var39 > 0.72) {
                        if (++this.runups > 6) {
                           return this.failWith("Lấy đà 6 lần vẫn chưa nhảy được");
                        }

                        this.runupFor = 1;
                        this.backTicks = 0;
                        this.keys(false, true, false, false, false);
                        MineController.this.status = "Lùi lấy đà trong ô để nhảy qua khe " + this.gap + " ô";
                     } else {
                        this.keys(true, false, var26, var27, false);
                        MineController.this.status = "Lấy đà nhảy qua khe " + this.gap + " ô";
                     }

                     return MineController.Result.RUNNING;
                  }
               }
            }
         }
      }

      @Override
      void finish() {
         BotInput.INSTANCE.clearMove();
         BotInput.INSTANCE.jumping = false;
         BotInput.INSTANCE.sprinting = false;
         super.finish();
      }
   }

   private final class PillarTask extends MineController.Task {
      final int fromY;
      final int px;
      final int pz;
      int baseY = Integer.MIN_VALUE;
      int clicks;
      int lastClickAge = -100;
      int fails;
      int settle;
      int waitUntil;
      int launchAge;
      boolean airborne;
      MineController.StrikeTask head;

      PillarTask(BlockPos nullx) {
         this.fromY = nullx.getY();
         this.px = nullx.getX();
         this.pz = nullx.getZ();
      }

      @Override
      String label() {
         return "Bắc cột lên Y=" + (this.fromY + 1) + " ở " + this.px + "," + this.pz + (this.head != null ? " · " + this.head.label() : "");
      }

      @Override
      MineController.Result tick() {
         this.age++;
         if (this.age > 800) {
            return this.failWith("bắc 1 bậc cột quá 40 giây");
         } else {
            BlockPos var1 = MineController.this.feet();
            if (this.head != null) {
               MineController.Result var8 = this.head.tick();
               if (var8 == MineController.Result.RUNNING) {
                  return var8;
               } else {
                  this.head.finish();
                  this.head = null;
                  return var8 == MineController.Result.FAILED ? this.failWith("không phá được khối trên đầu") : MineController.Result.RUNNING;
               }
            } else if (MineController.this.mc.player.isTouchingWater() && !MineController.this.mc.player.isOnGround() && this.baseY == Integer.MIN_VALUE) {
               BotInput.INSTANCE.sneaking = true;
               Keys.driveAir(MineController.this.mc, this.px + 0.5, this.pz + 0.5, 0.1, true);
               return MineController.Result.RUNNING;
            } else if (MineController.this.mc.player.isOnGround()) {
               Keys.jump(MineController.this.mc, false);
               if (var1.getY() > this.fromY) {
                  Keys.releaseMove(MineController.this.mc);
                  if (var1.getX() == this.px && var1.getZ() == this.pz) {
                     BlockPos var10 = new BlockPos(this.px, this.fromY, this.pz);
                     return Cells.floor(MineController.this.mc, var10)
                           && !ServerSync.pending(MineController.this.mc, var10)
                           && this.age - this.lastClickAge >= MineController.this.settleTicks()
                        ? MineController.Result.DONE
                        : MineController.Result.RUNNING;
                  } else {
                     return this.failWith("Đáp lệch khỏi cột kê");
                  }
               } else if (this.baseY != Integer.MIN_VALUE && !this.airborne && this.age - this.launchAge < 8) {
                  Keys.releaseMove(MineController.this.mc);
                  Keys.jump(MineController.this.mc, true);
                  return MineController.Result.RUNNING;
               } else {
                  if (this.baseY != Integer.MIN_VALUE) {
                     BlockPos var6 = new BlockPos(this.px, this.baseY, this.pz);
                     this.baseY = Integer.MIN_VALUE;
                     this.fails++;
                     AutoMineMod.LOGGER
                        .info(
                           "[AutoMine] Bắc cột: lần nhảy {} ở {} không lên được (click {} lần; ô kê giờ là {}; tay cầm {}; trên đầu {} / {})",
                           new Object[]{
                              this.fails,
                              var6.toShortString(),
                              this.clicks,
                              Cells.state(MineController.this.mc, var6).getBlock().getName().getString(),
                              MineController.this.mc.player.getMainHandStack().getName().getString(),
                              Cells.state(MineController.this.mc, var6.up(2)).getBlock().getName().getString(),
                              Cells.state(MineController.this.mc, var6.up(3)).getBlock().getName().getString()
                           }
                        );
                     MineController.this.ghost.request(var6);
                     MineController.this.ghost.request(var6.down());
                     MineController.this.ghost.request(var6.up(2));
                     MineController.this.ghost.request(var6.up(3));
                     this.waitUntil = this.age + 4 + 2 * ServerSync.pingTicks(MineController.this.mc);
                     if (this.fails >= 3) {
                        return this.failWith("nhảy kê khối 3 lần không lên được ở " + this.px + "," + this.pz);
                     }
                  }

                  if (this.age < this.waitUntil) {
                     Keys.releaseMove(MineController.this.mc);
                     return MineController.Result.RUNNING;
                  } else if (var1.getX() == this.px && var1.getZ() == this.pz) {
                     BlockPos var7 = var1.up(2);
                     if (Cells.mineable(MineController.this.mc, var7)) {
                        this.head = MineController.this.new StrikeTask(var7, Direction.DOWN, "phá khối trên đầu để bắc cột", false);
                        return MineController.Result.RUNNING;
                     } else if (!Cells.passable(MineController.this.mc, var7) && !WaterControl.water(MineController.this.mc, var7)) {
                        return this.failWith("trên đầu là khối không phá được");
                     } else if (MineController.this.fillerCount() == 0) {
                        if (MineController.this.ticks - MineController.this.fillerWarnAt > 600) {
                           MineController.this.fillerWarnAt = MineController.this.ticks;
                           MineController.this.say("§eCần khối kê (cobblestone/deepslate) để bắc cột mà túi hết.");
                        }

                        return this.failWith("hết khối kê");
                     } else if (!MineController.this.equipFiller()) {
                        return MineController.Result.RUNNING;
                     } else {
                        double var9 = Keys.drive(MineController.this.mc, this.px + 0.5, this.pz + 0.5, 0.1, true);
                        boolean var5 = MineController.this.lookAngles(MineController.this.mc.player.getYaw(), 90.0F);
                        if (!(var9 > 0.14)
                           && !(
                              MineController.this.mc.player.getVelocity().horizontalLength() > (MineController.this.mc.player.isTouchingWater() ? 0.12 : 0.03)
                           )
                           && var5) {
                           Keys.releaseMove(MineController.this.mc);
                           if (++this.settle < 2) {
                              return MineController.Result.RUNNING;
                           } else {
                              this.settle = 0;
                              this.baseY = var1.getY();
                              this.launchAge = this.age;
                              this.airborne = false;
                              this.clicks = 0;
                              Keys.jump(MineController.this.mc, true);
                              return MineController.Result.RUNNING;
                           }
                        } else {
                           this.settle = 0;
                           return MineController.Result.RUNNING;
                        }
                     }
                  } else if (Math.abs(var1.getX() - this.px) + Math.abs(var1.getZ() - this.pz) <= 1
                     && Cells.standable(MineController.this.mc, new BlockPos(this.px, var1.getY(), this.pz))) {
                     Keys.drive(MineController.this.mc, this.px + 0.5, this.pz + 0.5, 0.1, true);
                     return MineController.Result.RUNNING;
                  } else {
                     return this.failWith("trôi khỏi cột bắc");
                  }
               }
            } else {
               Keys.releaseMove(MineController.this.mc);
               Keys.jump(MineController.this.mc, false);
               this.airborne = true;
               if (MineController.this.mc.player.isTouchingWater() && this.baseY != Integer.MIN_VALUE) {
                  Keys.driveAir(MineController.this.mc, this.px + 0.5, this.pz + 0.5, 0.06, true);
                  double var10002 = this.baseY;
                  Keys.jump(MineController.this.mc, MineController.this.mc.player.getBoundingBox().minY < var10002 + 1.01);
               }

               MineController.this.lookAngles(MineController.this.mc.player.getYaw(), 90.0F);
               if (this.baseY != Integer.MIN_VALUE
                  && BreakGeometry.clearToPlace(MineController.this.mc.player.getBoundingBox(), new BlockPos(this.px, this.baseY, this.pz))
                  && this.clicks < 6) {
                  BlockPos var2 = new BlockPos(this.px, this.baseY, this.pz);
                  BlockPos var3 = var2.down();
                  if ((Cells.passable(MineController.this.mc, var2) || WaterControl.water(MineController.this.mc, var2))
                     && Cells.solid(MineController.this.mc, var3)) {
                     if (!MineController.this.safeFiller(MineController.this.mc.player.getMainHandStack())) {
                        MineController.this.equipFiller();
                     }

                     if (MineController.this.fillerClickReady() && this.age - this.lastClickAge >= 2 && !ServerSync.pending(MineController.this.mc, var3)) {
                        Vec3d var4 = new Vec3d(this.px + 0.5, this.baseY, this.pz + 0.5);
                        MineController.this.mc
                           .interactionManager
                           .interactBlock(MineController.this.mc.player, Hand.MAIN_HAND, new BlockHitResult(var4, Direction.UP, var3, false));
                        MineController.this.mc.player.swingHand(Hand.MAIN_HAND);
                        this.clicks++;
                        this.lastClickAge = this.age;
                     }
                  }
               }

               return MineController.Result.RUNNING;
            }
         }
      }

      @Override
      void finish() {
         if (this.head != null) {
            this.head.finish();
         }

         super.finish();
      }
   }

   private final class PlugTask extends MineController.Task {
      final BlockPos cell;
      final String what;
      BlockPos against;
      Direction side;
      Vec3d point;
      MineController.WalkTask walk;
      int clicks;
      int clickAt;
      int settledAt = -1;
      final boolean fillAir;
      boolean edge;
      int edgeTicks;
      Direction edgeDir;
      BlockPos edgeFrom;

      PlugTask(BlockPos nullx, String nullxx) {
         this(nullx, nullxx, false);
      }

      PlugTask(BlockPos nullx, String nullxx, boolean nullxxx) {
         this.cell = nullx;
         this.what = nullxx;
         this.fillAir = nullxxx;
      }

      @Override
      String label() {
         return this.what + " tại " + this.cell.toShortString();
      }

      @Override
      boolean exclusive() {
         return this.what.contains("lava");
      }

      @Override
      boolean walking() {
         return this.walk != null;
      }

      private boolean edgeStandFor(BlockPos var1) {
         return this.fillAir
            && var1.getY() - 1 == this.cell.getY()
            && Math.abs(var1.getX() - this.cell.getX()) + Math.abs(var1.getZ() - this.cell.getZ()) == 1
            && Cells.solid(MineController.this.mc, var1.down());
      }

      private MineController.Result edgeTick(BlockPos var1) {
         if (!this.edge) {
            this.edge = true;
            this.edgeFrom = var1;
            this.edgeDir = Direction.getFacing(this.cell.getX() - var1.getX(), 0.0F, this.cell.getZ() - var1.getZ());
            AutoMineMod.LOGGER.info("[AutoMine] {} {}: hố sâu, đứng giữa ô không thấy mặt nào → ngồi chìa ra mép để đặt", this.what, this.cell.toShortString());
         }

         var1 = this.edgeFrom;
         if (++this.edgeTicks > 140) {
            MineController.this.plugFails.merge(this.cell, 2, Integer::sum);
            return this.failWith("ngồi sát mép vẫn không thấy mặt để đặt vào " + this.cell.toShortString());
         } else {
            BotInput.INSTANCE.sneaking = true;
            if (!MineController.this.equipFiller()) {
               return MineController.Result.RUNNING;
            } else {
               Direction var2 = this.edgeDir;
               if (BotInput.INSTANCE.sneakFree == 0 && MineController.this.mc.player.isOnGround() && this.pickFace(MineController.this.mc.player.getEyePos())) {
                  Keys.releaseMove(MineController.this.mc);
                  return this.clickTick();
               } else {
                  Keys.lean(MineController.this.mc, var1.getX() + 0.5 + var2.getOffsetX() * 0.8, var1.getZ() + 0.5 + var2.getOffsetZ() * 0.8);
                  MineController.this.look(
                     new Vec3d(var1.getX() + 0.5 + var2.getOffsetX() * 0.5, var1.getY() - 0.5, var1.getZ() + 0.5 + var2.getOffsetZ() * 0.5)
                  );
                  MineController.this.status = this.what + " " + this.cell.toShortString() + " (ngồi sát mép)";
                  return MineController.Result.RUNNING;
               }
            }
         }
      }

      private MineController.Result clickTick() {
         boolean var1 = MineController.this.look(this.point);
         if (var1 && MineController.this.fillerClickReady() && MineController.this.crosshairOn(this.against, this.side)) {
            if (this.settledAt < 0) {
               this.settledAt = this.age;
            }

            if (this.age - this.settledAt >= 1 && this.age - this.clickAt > 6 + ServerSync.pingTicks(MineController.this.mc)) {
               if (++this.clicks > 4) {
                  MineController.this.plugFails.merge(this.cell, 2, Integer::sum);
                  return this.failWith("đặt 4 lần không được");
               }

               MineController.this.mc
                  .interactionManager
                  .interactBlock(MineController.this.mc.player, Hand.MAIN_HAND, new BlockHitResult(this.point, this.side, this.against, false));
               MineController.this.mc.player.swingHand(Hand.MAIN_HAND);
               this.clickAt = this.age;
            }
         } else {
            this.settledAt = -1;
         }

         MineController.this.status = this.what + " " + this.cell.toShortString() + (this.edge ? " (ngồi sát mép)" : "");
         return MineController.Result.RUNNING;
      }

      private boolean pickFace(Vec3d var1) {
         double var2 = MineController.this.reach();
         double var4 = Double.MAX_VALUE;
         this.against = null;

         for (Direction var9 : Direction.values()) {
            BlockPos var14 = this.cell.offset(var9);
            double var10;
            Direction var12;
            Vec3d var13;
            if (Cells.solid(MineController.this.mc, var14)
               && !Cells.lava(Cells.state(MineController.this.mc, var14))
               && !Cells.water(Cells.state(MineController.this.mc, var14))
               && (var13 = Cells.visibleFacePoint(MineController.this.mc, var1, var14, var12 = var9.getOpposite(), var2)) != null
               && (var10 = var13.squaredDistanceTo(var1)) < var4) {
               var4 = var10;
               this.against = var14;
               this.side = var12;
               this.point = var13;
            }
         }

         return this.against != null;
      }

      private BlockPos waterStand() {
         BlockPos var1 = null;
         double var2 = Double.MAX_VALUE;

         for (int var4 = this.cell.getX() - 3; var4 <= this.cell.getX() + 3; var4++) {
            for (int var5 = this.cell.getZ() - 3; var5 <= this.cell.getZ() + 3; var5++) {
               for (int var6 = this.cell.getY() - 3; var6 <= this.cell.getY() + 1; var6++) {
                  BlockPos var7 = new BlockPos(var4, var6, var5);
                  if (!new Box(var7).stretch(0.0, 0.8, 0.0).intersects(new Box(this.cell))
                     && DirectRoute.body(MineController.this.mc, var7, true)
                     && (Cells.floor(MineController.this.mc, var7.down()) || MineController.this.fillerCount() != 0)
                     && this.pickFace(Cells.eyeAt(var7))) {
                     double var8 = var7.getSquaredDistance(MineController.this.feet()) + 10 * Math.abs(var7.getY() - MineController.this.feet().getY());
                     if (var8 < var2) {
                        var2 = var8;
                        var1 = var7;
                     }
                  }
               }
            }
         }

         return var1;
      }

      @Override
      MineController.Result tick() {
         this.age++;
         if (this.age > 400) {
            MineController.this.plugFails.merge(this.cell, 1, Integer::sum);
            return this.failWith("quá 20 giây");
         } else {
            BlockState var2 = Cells.state(MineController.this.mc, this.cell);
            if (this.fillAir
               ? !Cells.floor(MineController.this.mc, this.cell)
                  || ServerSync.pending(MineController.this.mc, this.cell)
                  || this.clicks != 0 && this.age - this.clickAt < MineController.this.settleTicks()
               : Cells.lava(var2) || Cells.water(var2)) {
               if (this.fillAir && Cells.floor(MineController.this.mc, this.cell)) {
                  Keys.releaseMove(MineController.this.mc);
                  MineController.this.status = "Chờ server xác nhận khối " + this.cell.toShortString();
                  return MineController.Result.RUNNING;
               } else if (MineController.this.fillerCount() == 0) {
                  MineController.this.plugFails.merge(this.cell, 4, Integer::sum);
                  if (MineController.this.ticks - MineController.this.fillerWarnAt > 600) {
                     MineController.this.fillerWarnAt = MineController.this.ticks;
                     MineController.this.say("§eCần khối kê (cobblestone/deepslate) để " + this.what + " mà túi hết.");
                  }

                  return this.failWith("hết khối kê");
               } else {
                  if (this.walk != null) {
                     MineController.Result var1 = this.walk.tick();
                     if (var1 == MineController.Result.RUNNING
                        && (
                           MineController.this.mc.player.getBoundingBox().intersects(new Box(this.cell))
                              || !this.pickFace(MineController.this.mc.player.getEyePos())
                        )) {
                        return var1;
                     }

                     this.walk.finish();
                     this.walk = null;
                     if (var1 == MineController.Result.FAILED) {
                        MineController.this.plugFails.merge(this.cell, 1, Integer::sum);
                        return this.failWith("không tới được chỗ " + this.what);
                     }
                  }

                  if (this.edge) {
                     if (BotInput.INSTANCE.sneakFree == 0 && this.pickFace(MineController.this.mc.player.getEyePos())) {
                        BotInput.INSTANCE.sneaking = true;
                        Keys.releaseMove(MineController.this.mc);
                        return !MineController.this.equipFiller() ? MineController.Result.RUNNING : this.clickTick();
                     } else {
                        return this.edgeTick(MineController.this.feet());
                     }
                  } else if (MineController.this.mc.player.getBoundingBox().intersects(new Box(this.cell))) {
                     BlockPos var11 = MineController.this.feet();

                     for (Direction var13 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                        BlockPos var16 = var11.offset(var13);
                        if (DirectRoute.body(MineController.this.mc, var16, true) && Cells.floor(MineController.this.mc, var16.down())) {
                           this.walk = MineController.this.new WalkTask(var16, 0.18, false, "tránh ô đang bịt nước");
                           this.walk.waterAccess = true;
                           return MineController.Result.RUNNING;
                        }
                     }

                     return MineController.Result.RUNNING;
                  } else if (!this.pickFace(MineController.this.mc.player.getEyePos())) {
                     BlockPos var10 = MineController.this.feet();
                     if (MineController.this.mc.player.isOnGround() && this.edgeStandFor(var10)) {
                        return this.edgeTick(var10);
                     } else {
                        Map<BlockPos, Integer> var3 = PathFinder.distances(
                           MineController.this.mc, var10, 8, ((Vec3i)var10).getY() - 2, ((Vec3i)var10).getY() + 2, 1500, MineController.this.avoid
                        );
                        BlockPos var4 = null;
                        int var5 = Integer.MAX_VALUE;

                        for (Entry<BlockPos, Integer> var7 : var3.entrySet()) {
                           if ((Integer)var7.getValue() < var5
                              && !(((BlockPos)var7.getKey()).getSquaredDistance(this.cell) > 25.0)
                              && !((BlockPos)var7.getKey()).equals(this.cell)
                              && !((BlockPos)var7.getKey()).up().equals(this.cell)
                              && this.pickFace(Cells.eyeAt((BlockPos)var7.getKey()))) {
                              var4 = (BlockPos)var7.getKey();
                              var5 = (Integer)var7.getValue();
                           }
                        }

                        if (var4 == null) {
                           if (WaterControl.water(MineController.this.mc, this.cell)) {
                              BlockPos var14 = this.waterStand();
                              if (var14 != null) {
                                 PathFinder.Dig var18 = new PathFinder.Dig(
                                    MineController.this.region, true, Math.min(MineController.this.feet().getY(), var14.getY()), true
                                 );
                                 this.walk = MineController.this.new WalkTask(var14, 0.18, var18, "kê lên/tới chỗ bịt nước", 12000, 1.0);
                                 this.walk.verticalAccess = true;
                                 this.walk.waterAccess = true;
                                 return MineController.Result.RUNNING;
                              }
                           }

                           Object var15 = null;
                           int var17 = Integer.MAX_VALUE;

                           for (Entry<BlockPos, Integer> var9 : var3.entrySet()) {
                              if ((Integer)var9.getValue() < var17 && this.edgeStandFor((BlockPos)var9.getKey())) {
                                 var15 = var9.getKey();
                                 var17 = (Integer)var9.getValue();
                              }
                           }

                           if (var15 == null) {
                              MineController.this.plugFails.merge(this.cell, 2, Integer::sum);
                              return this.failWith("không có mặt nào để đặt khối vào " + this.cell.toShortString());
                           } else if (((Vec3i)var15).equals(var10)) {
                              return this.edgeTick(var10);
                           } else {
                              this.walk = MineController.this.new WalkTask((BlockPos)var15, 0.3, false, "tới mép " + this.what);
                              return MineController.Result.RUNNING;
                           }
                        } else {
                           this.walk = MineController.this.new WalkTask(var4, 0.3, false, "tới chỗ " + this.what);
                           this.walk.waterAccess = true;
                           return MineController.Result.RUNNING;
                        }
                     }
                  } else {
                     Keys.releaseMove(MineController.this.mc);
                     return !MineController.this.equipFiller() ? MineController.Result.RUNNING : this.clickTick();
                  }
               }
            } else {
               return MineController.Result.DONE;
            }
         }
      }

      @Override
      void finish() {
         if (this.walk != null) {
            this.walk.finish();
         }

         super.finish();
      }
   }

   private final class RepairTask extends MineController.Task {
      int phase;
      int thrown;
      int lastThrow;
      int startDur;
      int phaseAge;

      @Override
      String label() {
         return "Hồi cúp bằng bình XP (đã ném " + this.thrown + ")";
      }

      @Override
      MineController.Result tick() {
         this.age++;
         this.phaseAge++;
         Keys.releaseMove(MineController.this.mc);
         ItemStack var2 = MineController.this.tools.findPick(MineController.this.mc);
         int var3 = Tools.durability(var2);
         int var1 = var2.isEmpty() ? 0 : var2.getMaxDamage();
         if (this.age == 1) {
            this.startDur = var3;
            MineController.this.say("Cúp còn " + var3 + "/" + var1 + " → ném bình XP hồi cúp.");
         }

         if (this.age > 6000) {
            return this.failWith("hồi cúp quá 5 phút");
         } else {
            switch (this.phase) {
               case 0:
                  if (!MineController.this.equipPick()) {
                     return MineController.Result.RUNNING;
                  }

                  if (!Tools.isXp(MineController.this.mc.player.getOffHandStack())) {
                     int var7 = Inv.findSlot(MineController.this.mc, Tools::isXp);
                     if (var7 < 0) {
                        return this.failWith("hết bình XP");
                     }

                     if (Inv.swapOffhand(MineController.this.mc, var7)) {
                        AutoMineMod.LOGGER.info("[AutoMine] Đưa bình XP ở ô {} sang tay phụ", var7);
                     }

                     return MineController.Result.RUNNING;
                  }

                  this.phase = 1;
                  this.phaseAge = 0;
                  break;
               case 1:
                  if (var1 - var3 <= MineController.this.config.repairMissing) {
                     this.phase = 2;
                     this.phaseAge = 0;
                     return MineController.Result.RUNNING;
                  }

                  if (!MineController.this.pickReady()) {
                     MineController.this.equipPick();
                     return MineController.Result.RUNNING;
                  }

                  if (!Tools.isXp(MineController.this.mc.player.getOffHandStack())) {
                     if (Inv.findSlot(MineController.this.mc, Tools::isXp) < 0) {
                        this.phase = 2;
                        this.phaseAge = 0;
                        return MineController.Result.RUNNING;
                     }

                     this.phase = 0;
                     return MineController.Result.RUNNING;
                  }

                  boolean var6 = MineController.this.lookAngles(MineController.this.mc.player.getYaw(), 85.0F);
                  if (var6 && this.age - this.lastThrow >= 4) {
                     MineController.this.mc.interactionManager.interactItem(MineController.this.mc.player, Hand.OFF_HAND);
                     MineController.this.mc.player.swingHand(Hand.OFF_HAND);
                     this.thrown++;
                     this.lastThrow = this.age;
                  }

                  MineController.this.status = "Ném bình XP: cúp " + var3 + "/" + var1;
                  break;
               default:
                  List var5 = MineController.this.mc
                     .world
                     .getEntitiesByClass(ExperienceOrbEntity.class, MineController.this.mc.player.getBoundingBox().expand(4.0), var0 -> true);
                  if (var5.isEmpty() || this.phaseAge > 80) {
                     MineController.this.say("Hồi cúp xong: " + this.startDur + " → " + var3 + "/" + var1 + " (ném " + this.thrown + " bình).");
                     return MineController.Result.DONE;
                  }

                  MineController.this.status = "Chờ hút hết XP (" + var5.size() + ")";
            }

            return MineController.Result.RUNNING;
         }
      }
   }

   private static enum Result {
      RUNNING,
      DONE,
      FAILED;
   }

   private final class RowTask extends MineController.Task {
      final LayerPlan lp;
      final int base;
      final int runStart;
      final List<LayerPlan.Slice> run;
      final int strip;
      final boolean reverse;
      final long startMs = System.currentTimeMillis();
      int idx;
      BlockPos target;
      int targetSince;
      int breakTicks;
      int offTicks;
      int idle;
      int blocked;
      double lastX = Double.NaN;
      double lastZ;
      MineController.Task sub;
      MineController.PlugTask fill;
      int handovers;
      boolean settling;
      int settleUntil;
      int done;
      final List<Object[]> checks = new ArrayList<>();
      List<BlockPos> pendingBefore;
      BlockPos navFor;
      int jumpFails;
      int detours;
      int blockedRounds;
      int aimSwitch;

      RowTask(LayerPlan nullx, int nullxx) {
         this.lp = nullx;
         this.base = nullxx;
         this.run = nullx.runFrom(nullxx);
         int var5 = nullx.slices.get(nullxx).run();
         int var4 = nullxx;

         while (var4 > 0 && nullx.slices.get(var4 - 1).run() == var5) {
            var4--;
         }

         this.runStart = var4;
         this.strip = this.run.getFirst().strip();
         this.reverse = false;
      }

      RowTask(LayerPlan nullx, List<LayerPlan.Slice> nullxx) {
         this.lp = nullx;
         this.base = -1;
         this.runStart = -1;
         this.run = nullxx;
         this.strip = ((LayerPlan.Slice)nullxx.getFirst()).strip();
         this.reverse = true;
      }

      @Override
      String label() {
         return "Hàng "
            + (this.strip + 1)
            + "/"
            + this.lp.strips
            + " · đào liền "
            + this.done
            + "/"
            + this.run.size()
            + (this.sub != null ? " · " + this.sub.label() : (this.fill != null ? " · " + this.fill.label() : ""));
      }

      @Override
      boolean walking() {
         // 1.14.5: đang đi qua hố nông bằng việc con (WalkTask) cũng là đang đi — không coi là rơi xuống hố rồi bật "lên lại sàn"
         return this.sub == null && this.fill == null || this.sub != null && this.sub.walking();
      }

      @Override
      boolean interruptible() {
         return this.sub == null && this.fill == null && this.breakTicks == 0;
      }

      @Override
      void onSetback() {
         Keys.releaseMove(MineController.this.mc);
         this.idle = 0;
      }

      private double along(Vec3d var1, BlockPos var2, Direction var3) {
         return (var2.getX() + 0.5 - var1.x) * var3.getOffsetX() + (var2.getZ() + 0.5 - var1.z) * var3.getOffsetZ();
      }

      /**
       * 1.14.2: mặt đập của lát; đang đứng ở phía bên kia lát (vd. lát đầu hàng sát mép khung, đường đi đưa tới phía sau)
       * thì đập mặt đối diện — cùng trục nên mặt 3x3 y hệt. Trước đây đứng chờ ~90 tick rồi mới giao cho StrikeTask.
       */
      private Direction rowFace(BlockPos var1, Direction var2) {
         Vec3d var3 = MineController.this.mc.player.getEyePos();
         if (Cells.drillFacePoint(MineController.this.mc, var3, var1, var2, MineController.this.reach()) != null) {
            return var2;
         } else {
            return Cells.drillFacePoint(MineController.this.mc, var3, var1, var2.getOpposite(), MineController.this.reach()) != null ? var2.getOpposite() : var2;
         }
      }

      /**
       * 1.14.3: khối giữa của 2 lát vừa qua hiện lại (server trả lại, ghost, sỏi rơi) → đập lại ngay theo thang đổi góc (MiddleRetry),
       * không bao giờ bỏ qua để đập khối xung quanh; thử đủ 5 lần vẫn hiện lại thì để dành cuối tầng, hàng đào tiếp.
       */
      private MineController.Task middleBack() {
         // 1.14.4: khối gần người trước (khối gần che khối xa), khối nhìn thấy được trước; lần nào cũng tính (kể cả sỏi) → không lặp mãi
         ArrayList<Integer> var1 = new ArrayList<>();

         for (int var2 = Math.min(this.idx, this.run.size()) - 1; var2 >= Math.max(0, this.idx - 2); var2--) {
            BlockPos var3 = this.run.get(var2).center();
            if (MineController.this.region.inside(var3)
               && Cells.loaded(MineController.this.mc, var3)
               && Cells.mineable(MineController.this.mc, var3)
               && !MineController.this.skipped.contains(var3)
               && !MineController.this.middleDeferred.contains(var3)
               && !MineController.this.gaveUp.contains(var3)
               && !MineController.this.waterDams.contains(var3)
               && (MineController.this.middleSeenClear.contains(var3) || Cells.state(MineController.this.mc, var3).getBlock() instanceof FallingBlock)) {
               var1.add(var2);
            }
         }

         Vec3d var11 = MineController.this.mc.player.getEyePos();
         var1.sort(Comparator.comparingDouble(var2x -> {
            LayerPlan.Slice var3x = this.run.get(var2x);
            boolean var4x = Cells.drillFacePoint(MineController.this.mc, var11, var3x.center(), this.rowFace(var3x.center(), var3x.face()), MineController.this.reach()) != null;
            return (var4x ? 0.0 : 100.0) + var11.distanceTo(Vec3d.ofCenter(var3x.center()));
         }));

         for (int var12 : var1) {
            LayerPlan.Slice var13 = this.run.get(var12);
            BlockPos var14 = var13.center();
            boolean var15 = Cells.state(MineController.this.mc, var14).getBlock() instanceof FallingBlock;
            int var16 = MineController.this.middleTries.getOrDefault(var14, 0) + 1;
            if (var16 > 5) {
               // cả mặt 3x3 của lát đó để cuối tầng: không đập lẻ khối xung quanh khi bị chắn tầm nhìn tới lát kế
               for (BlockPos var17 : Cells.plane(var14, var13.face())) {
                  MineController.this.middleDeferred.add(var17.toImmutable());
               }

               MineController.this.middleBase.remove(var14);
               MineController.this.fails.remove(var14);
               MineController.this.retryAt.remove(var14);
               AutoMineMod.LOGGER.info("[AutoMine] Khối giữa {}: đập 5 lần vẫn hiện lại → để vét cuối tầng, đào lát kế", var14.toShortString());
            } else {
               MineController.this.middleTries.put(var14.toImmutable(), var16);
               return MineController.this.new MiddleRetry(var14, this.rowFace(var14, var13.face()), var16, var15);
            }
         }

         return null;
      }

      /** 1.14.3: ô đáp bên kia hố (cách 2..4 ô) còn khối nhưng là khối sắp vỡ theo nhát 3x3 vừa đập (chờ server báo) */
      private boolean landingPending(Direction var1) {
         BlockPos var2 = MineController.this.feet();

         for (int var3 = 2; var3 <= 4; var3++) {
            BlockPos var4 = var2.offset(var1, var3);
            if (MineController.this.expectedGone(var4) || MineController.this.expectedGone(var4.up())) {
               return true;
            }
         }

         return false;
      }

      private List<BlockPos> planeLeft(LayerPlan.Slice var1) {
         ArrayList var2 = new ArrayList();
         int var3 = MineController.this.region.floor(MineController.this.layer);

         for (BlockPos var5 : Cells.plane(var1.center(), var1.face())) {
            if (!var5.equals(var1.center()) && MineController.this.region.inside(var5) && var5.getY() >= var3 && Cells.mineable(MineController.this.mc, var5)) {
               var2.add(var5);
            }
         }

         return var2;
      }

      private MineController.WalkTask navToLane(BlockPos var1, Direction var2) {
         int var3 = MineController.this.region.floor(MineController.this.layer);
         BlockPos var4 = new BlockPos(
            Math.clamp((long)(var1.getX() + var2.getOffsetX() * 2), MineController.this.region.x0, MineController.this.region.x1),
            var3,
            Math.clamp((long)(var1.getZ() + var2.getOffsetZ() * 2), MineController.this.region.z0, MineController.this.region.z1)
         );
         PathFinder.Dig var5 = new PathFinder.Dig(MineController.this.region, false, var3, false);
         MineController.WalkTask var6 = MineController.this.new WalkTask(var4, 0.4, var5, "tới hàng " + (this.strip + 1), 20000, 1.4);
         var6.goalTest = var3x -> var3x.getY() == var3
            && Math.abs(var3x.getX() - var4.getX()) + Math.abs(var3x.getZ() - var4.getZ()) <= 1
            && MineController.this.region.insideColumn(var3x.getX(), var3x.getZ());
         var6.yLo = var3;
         var6.yHi = var3;
         return var6;
      }

      private List<BlockPos> blockers(int var1) {
         LinkedHashSet var3 = new LinkedHashSet();
         int var4 = MineController.this.region.floor(MineController.this.layer);

         for (int var5 = var1 - 2; var5 < var1; var5++) {
            Object var2;
            if (var5 >= 0) {
               var2 = this.run.get(Math.min(var5, this.run.size() - 1));
            } else {
               if (this.reverse || this.base + var5 < 0) {
                  continue;
               }

               var2 = this.lp.slices.get(this.base + var5);
            }

            if (MineController.this.middleDeferred.contains(((LayerPlan.Slice)var2).center())) {
               continue;
            }

            for (BlockPos var7 : Cells.plane(((LayerPlan.Slice)var2).center(), ((LayerPlan.Slice)var2).face())) {
               if (MineController.this.region.inside(var7)
                  && var7.getY() >= var4
                  && Cells.mineable(MineController.this.mc, var7)
                  && !MineController.this.skipped.contains(var7)) {
                  var3.add(var7.toImmutable());
               }
            }
         }

         LayerPlan.Slice var13 = this.run.get(Math.min(var1, this.run.size() - 1));
         BlockPos var12 = var13.center().offset(var13.face());
         Direction var14 = var13.face().getOpposite();
         BlockPos var15 = MineController.this.feet().offset(var14);

         for (BlockPos var11 : new BlockPos[]{var12, var12.down(), var12.up(), var15, var15.up()}) {
            if (MineController.this.region.inside(var11)
               && var11.getY() >= var4
               && Cells.mineable(MineController.this.mc, var11)
               && !MineController.this.skipped.contains(var11)
               && !MineController.this.middleDeferred.contains(var11)) {
               var3.add(var11.toImmutable());
            }
         }

         return new ArrayList<>(var3);
      }

      private void runChecks(boolean var1) {
         int var2 = MineController.this.settleTicks();

         for (int var3 = 0; var3 < this.checks.size(); var3++) {
            Object[] var4 = this.checks.get(var3);
            if (var1 || MineController.this.ticks - (Integer)var4[0] >= var2) {
               @SuppressWarnings("unchecked") List<BlockPos> var5 = (List<BlockPos>)var4[1];
               int var6 = 0;

               for (BlockPos var8 : var5) {
                  if (!Cells.mineable(MineController.this.mc, var8)) {
                     var6++;
                  }
               }

               MineController.this.cleared += var6 + 1;
               MineController.this.calibrate(var5.size(), var6);
               this.checks.remove(var3--);
            }
         }
      }

      private void stopBreaking() {
         if (this.breakTicks > 0 && MineController.this.mc.interactionManager.isBreakingBlock()) {
            MineController.this.mc.interactionManager.cancelBlockBreaking();
         }

         this.breakTicks = 0;
      }

      @Override
      MineController.Result tick() {
         this.age++;
         if (MineController.this.plan != this.lp) {
            return MineController.Result.DONE;
         } else if (this.sub != null) {
            MineController.Result var26 = this.sub.tick();
            if (var26 == MineController.Result.RUNNING) {
               return var26;
            } else {
               if (var26 == MineController.Result.FAILED && this.sub instanceof MineController.JumpTask) {
                  this.jumpFails++;
               }

               this.sub.finish();
               this.sub = null;
               this.idle = 0;
               this.blocked = 0;
               this.lastX = Double.NaN;
               return MineController.Result.RUNNING;
            }
         } else if (this.fill != null) {
            MineController.Result var25 = this.fill.tick();
            if (var25 == MineController.Result.RUNNING) {
               return var25;
            } else {
               this.fill.finish();
               this.fill = null;
               this.idle = 0;
               if (var25 == MineController.Result.FAILED && this.idx < this.run.size()) {
                  this.sub = MineController.this.new StrikeTask(
                     this.run.get(this.idx).center(), this.run.get(this.idx).face(), "hàng " + (this.strip + 1), true
                  );
               }

               return MineController.Result.RUNNING;
            }
         } else {
            int var3 = MineController.this.region.floor(MineController.this.layer);
            if (MineController.this.mc.player.isOnGround() && MineController.this.feet().getY() != var3) {
               AutoMineMod.LOGGER
                  .info(
                     "[AutoMine] Hàng {}: chân ở Y={} ≠ sàn tầng {} tại {} → sửa độ cao trước",
                     new Object[]{this.strip + 1, MineController.this.feet().getY(), var3, MineController.this.feet().toShortString()}
                  );
               return MineController.Result.DONE;
            } else {
               MineController.Task var27 = this.middleBack();
               if (var27 != null) {
                  this.stopBreaking();
                  Keys.releaseMove(MineController.this.mc);
                  this.sub = var27;
                  return MineController.Result.RUNNING;
               }

               BlockPos var2;
               while (
                  this.idx < this.run.size()
                     && Cells.loaded(MineController.this.mc, var2 = this.run.get(this.idx).center())
                     && (MineController.this.skipped.contains(var2) || MineController.this.gaveUp.contains(var2) || !Cells.mineable(MineController.this.mc, var2))
               ) {
                  // 1.14.2: khối thừa quanh lát đã trống để dành vét một lượt cuối tầng; chỉ phá ngay sỏi/cát vừa rơi lại vào hàng
                  if (!Cells.mineable(MineController.this.mc, var2)) {
                     MineController.this.middleSeenClear.add(var2.toImmutable());
                     if (MineController.this.justBroke(var2) && var2.equals(this.target) && this.breakTicks > 0) {
                        // 1.14.5: lát vừa vỡ do giữ chuột (vỡ lúc game đọc phím)
                        MineController.this.middleStrikeAt.put(var2.toImmutable(), MineController.this.mc.player.getPos());
                        this.done++;
                        this.pendingBefore = null;
                        this.breakTicks = 0;
                        this.idle = 0;
                        this.blocked = 0;
                        this.handovers = 0;
                     }
                  }

                  List<BlockPos> var4 = this.planeLeft(this.run.get(this.idx));
                  var4.removeIf(var0 -> !(Cells.state(MineController.this.mc, var0).getBlock() instanceof FallingBlock));
                  MineController.Task var5 = var4.isEmpty() ? null : MineController.this.chooseStrike(var4, "sỏi/cát rơi lại vào hàng", false);
                  if (var5 != null) {
                     this.stopBreaking();
                     Keys.releaseMove(MineController.this.mc);
                     this.sub = var5;
                     return MineController.Result.RUNNING;
                  }

                  this.idx++;
               }

               if (!this.reverse) {
                  this.lp.index = Math.max(this.lp.index, this.base + this.idx);
               }

               this.runChecks(false);
               if (this.idx < this.run.size() && Cells.loaded(MineController.this.mc, this.run.get(this.idx).center())) {
                  LayerPlan.Slice var2x = this.run.get(this.idx);
                  BlockPos var28 = var2x.center();
                  Direction var30 = this.rowFace(var28, var2x.face());
                  if (!var28.equals(this.target)) {
                     this.target = var28;
                     this.targetSince = this.age;
                     this.breakTicks = 0;
                     this.offTicks = 0;
                     this.detours = 0;
                     this.blockedRounds = 0;
                     this.aimSwitch = 0;
                  }

                  if (!MineController.this.equipPick()) {
                     Keys.releaseMove(MineController.this.mc);
                     return MineController.Result.RUNNING;
                  } else {
                     Keys.stand(MineController.this.mc);
                     if (BotInput.INSTANCE.sneaking && MineController.this.recentre()) {
                        this.stopBreaking();
                        return MineController.Result.RUNNING;
                     }

                     if (MineController.this.damInPlane(var28, var30)) {
                        this.stopBreaking();
                        this.sub = MineController.this.new StrikeTask(var28, var30, "đào sát đập chắn nước", true);
                        return MineController.Result.RUNNING;
                     } else {
                        if (MineController.this.drillCrosshairOn(var28, var30)
                           && !MineController.this.protectedFooting(var28, var30)
                           && BotInput.INSTANCE.sneakFree >= 1) {
                           if (this.breakTicks == 0) {
                              this.pendingBefore = this.planeLeft(var2x);
                           }

                           MineController.this.hold(var28, var30, this.pendingBefore);
                           this.breakTicks++;
                           this.offTicks = 0;
                           if (!Cells.mineable(MineController.this.mc, var28)) {
                              // (1.14.5: khối vỡ lúc game đọc phím → ghi nhận ở vòng while đầu tick, nhánh này giữ cho chắc)
                              MineController.this.middleSeenClear.add(var28.toImmutable());
                              MineController.this.middleStrikeAt.put(var28.toImmutable(), MineController.this.mc.player.getPos());
                              this.done++;
                              this.pendingBefore = null;
                              this.breakTicks = 0;
                              this.idle = 0;
                              this.blocked = 0;
                              this.handovers = 0;
                              this.idx++;

                              while (
                                 this.idx < this.run.size()
                                    && (
                                       MineController.this.skipped.contains(this.run.get(this.idx).center())
                                          || MineController.this.gaveUp.contains(this.run.get(this.idx).center())
                                          || !Cells.mineable(MineController.this.mc, this.run.get(this.idx).center())
                                    )
                              ) {
                                 if (!Cells.mineable(MineController.this.mc, this.run.get(this.idx).center())) {
                                    MineController.this.middleSeenClear.add(this.run.get(this.idx).center().toImmutable());
                                 }

                                 this.idx++;
                              }

                              if (!this.reverse) {
                                 this.lp.index = Math.max(this.lp.index, this.base + this.idx);
                              }
                           }
                        } else if (this.breakTicks > 0 && ++this.offTicks > 4) {
                           this.stopBreaking();
                           this.offTicks = 0;
                           // 1.14.5: đập lại thì ngắm điểm khác trên cùng khối
                           this.aimSwitch++;
                        }

                        if (debugTrace) {
                           MineController.this.debugLine = "row tgt=" + var28.toShortString() + " face=" + var30 + " cross=" + MineController.this.drillCrosshairOn(var28, var30)
                              + " foot=" + MineController.this.protectedFooting(var28, var30) + " sneakFree=" + BotInput.INSTANCE.sneakFree + " stable=" + MineController.this.rotationStableTicks
                              + " idle=" + this.idle + " blocked=" + this.blocked + " brk=" + this.breakTicks;
                        }

                        Object var31 = this.idx < this.run.size() ? this.run.get(this.idx) : var2x;
                        BlockPos var32 = ((LayerPlan.Slice)var31).center();
                        Direction var33 = this.rowFace(var32, ((LayerPlan.Slice)var31).face());
                        Direction var9 = var33.getOpposite();
                        List<Vec3d> var47 = Cells.facePoints(
                           MineController.this.mc, MineController.this.mc.player.getEyePos(), var32, var33, MineController.this.reach(), true
                        );
                        Vec3d var10 = var47.isEmpty() ? null : var47.get(this.aimSwitch % var47.size());
                        MineController.this.look(var10 != null ? var10 : Cells.faceCenter(var32, var33));
                        double var11 = this.along(MineController.this.mc.player.getPos(), var32, var9);
                        double var13 = var32.getX() + 0.5 - var9.getOffsetX() * 1.9;
                        double var15 = var32.getZ() + 0.5 - var9.getOffsetZ() * 1.9;
                        double var17 = Math.hypot(var13 - MineController.this.mc.player.getX(), var15 - MineController.this.mc.player.getZ());
                        if (MineController.this.mc.player.isOnGround() && var10 == null && (var17 > 6.0 || this.idle > 12) && !var32.equals(this.navFor)) {
                           this.navFor = var32;
                           Keys.releaseMove(MineController.this.mc);
                           this.sub = this.navToLane(var32, var33);
                           return MineController.Result.RUNNING;
                        } else {
                           if (MineController.this.mc.player.isOnGround() && (var10 == null || var11 > 2.9)) {
                              BlockPos var19 = MineController.this.feet().offset(var9);
                              if (Cells.passable(MineController.this.mc, var19)
                                 && Cells.passable(MineController.this.mc, var19.up())
                                 && !Cells.floor(MineController.this.mc, var19.down())
                                 && MineController.this.region.insideColumn(var19.getX(), var19.getZ())) {
                                 // 1.14.0: hố trên sàn → nhảy qua như AutoBuild; chỉ lấp khi không nhảy được (đặt khối xuống rồi tầng sau lại phải đào)
                                 BlockPos var37 = new AutoBuildNavigation(
                                       MineController.this.mc, MineController.this.feet(), null, var3, var3, false, false, MineController.this.avoid
                                    )
                                    .jumpTarget(MineController.this.feet(), var9);
                                 // 1.14.3: đang đập dở lát với tới được từ đây → đập xong rồi nhảy (nhảy giữa chừng thì mất công đập)
                                 if (var37 != null
                                    && this.jumpFails < 2
                                    && !(var10 != null && this.breakTicks > 0)
                                    && MineController.this.region.insideColumn(var37.getX(), var37.getZ())) {
                                    MineController.JumpTask var38 = MineController.this.new JumpTask(MineController.this.feet(), var37);
                                    var38.straightAfter = true;
                                    this.sub = var38;
                                    return MineController.Result.RUNNING;
                                 }

                                 // 1.14.2: không nhảy được nhưng hố chỉ sâu 1 ô và bên kia có sàn → đi xuống rồi bước lên, không lấp
                                 if (this.jumpFails < 2 && DirectRoute.dipCrossable(MineController.this.mc, MineController.this.feet(), var9, MineController.this.avoid)) {
                                    BlockPos var39 = null;

                                    for (int var40 = 2; var40 <= 6 && var39 == null; var40++) {
                                       BlockPos var41 = MineController.this.feet().offset(var9, var40);
                                       if (Cells.standable(MineController.this.mc, var41)) {
                                          var39 = var41;
                                       }
                                    }

                                    if (var39 != null && MineController.this.region.insideColumn(var39.getX(), var39.getZ())) {
                                       MineController.WalkTask var42 = MineController.this.new WalkTask(var39, 0.3, false, "đi qua hố nông");
                                       var42.yLo = var3 - 1;
                                       var42.yHi = var3;
                                       this.sub = var42;
                                       return MineController.Result.RUNNING;
                                    }
                                 }

                                 // 1.14.3: chưa nhảy được vì ô đáp còn khối (lát bên kia chưa đào, hoặc nhát 3x3 vừa rồi chưa được server báo vỡ nốt)
                                 // mà lát đang đào vẫn với tới từ đây → đứng đập tiếp, ô đáp trống thì nhảy; trước đây lấp rãnh ngay tick đó
                                 if (var10 == null && !this.landingPending(var9)) {
                                    Keys.releaseMove(MineController.this.mc);
                                    if (MineController.this.bridgeAllowed()) {
                                       this.fill = MineController.this.new PlugTask(var19.down(), "lấp hố trên sàn", true);
                                       return MineController.Result.RUNNING;
                                    }

                                    // 1.14.5: không lấp hố — phần hàng bên kia hố đào từ phía khác (vét cuối tầng / hàng sau tới từ đầu kia)
                                    AutoMineMod.LOGGER.info(
                                       "[AutoMine] Hàng {}: hố ở {} không nhảy / đi qua được → không lấp, {} lát còn lại đào từ phía khác",
                                       new Object[]{this.strip + 1, var19.down().toShortString(), this.run.size() - this.idx}
                                    );
                                    this.stopBreaking();
                                    this.idx = this.run.size();
                                    if (!this.reverse) {
                                       this.lp.index = Math.max(this.lp.index, this.base + this.idx);
                                    }

                                    return MineController.Result.RUNNING;
                                 }

                                 Keys.releaseMove(MineController.this.mc);
                              } else {
                                 double var20 = var32.getX() + 0.5 - var9.getOffsetX() * 1.9;
                                 double var22 = var32.getZ() + 0.5 - var9.getOffsetZ() * 1.9;
                                 Keys.drive(MineController.this.mc, var20, var22, 0.12, true);
                              }
                           } else {
                              Keys.releaseMove(MineController.this.mc);
                           }

                           if (var10 == null
                              && MineController.this.mc.player.isOnGround()
                              && var11 <= 3.3
                              && MineController.this.middleDeferred.contains(var32.offset(var33))) {
                              // 1.14.3: lát kế bị mặt 3x3 để dành cuối tầng che kín → các lát sau cũng không với được từ phía này;
                              // kết thúc hàng, phần còn lại vét cuối tầng (trước đây thử từng lát, mỗi lát đứng chờ cả chục giây)
                              AutoMineMod.LOGGER.info("[AutoMine] Hàng {}: {} lát sau khối giữa để dành bị che → đào hàng kế, hết hàng quay lại đào nốt", this.strip + 1, this.run.size() - this.idx);
                              // 1.14.6: không bỏ cả phần còn lại của hàng cho vét cuối tầng (vét lẻ để sót dải); ghi lại để đào nốt đúng làn hàng này
                              int var48 = this.idx;
                              for (int var49 = this.idx - 1; var49 >= Math.max(0, this.idx - 3); var49--) {
                                 if (this.run.get(var49).center().equals(var32.offset(var33))) {
                                    var48 = var49;
                                    break;
                                 }
                              }

                              // chỉ các lát thẳng của hàng này (không lấy các lát rẽ sang hàng kế ở cuối hàng — chúng chắn đường sang hàng kế)
                              int var50 = var48;
                              while (var50 < this.run.size() && this.run.get(var50).face().getAxis() == this.run.get(var48).face().getAxis()) {
                                 var50++;
                              }

                              MineController.this.suspendRow(this.run.subList(var48, var50));
                              this.stopBreaking();
                              Keys.releaseMove(MineController.this.mc);
                              this.idx = this.run.size();
                              if (!this.reverse) {
                                 this.lp.index = Math.max(this.lp.index, this.base + this.idx);
                              }

                              return MineController.Result.RUNNING;
                           }

                           if (var10 == null && MineController.this.mc.player.isOnGround() && var11 <= 3.3) {
                              if (++this.blocked == 6) {
                                 MineController.this.ghost.requestAround(MineController.this.feet(), var9);
                              }

                              // vòng đầu chờ đồng bộ ghost (khối che có thể là ghost); các vòng sau của cùng lát làm luôn
                              if (this.blocked >= (this.blockedRounds == 0 ? 6 + MineController.this.settleTicks() : 4)) {
                                 this.blockedRounds++;
                                 // bị che: như cũ đập khối chắn mặt hàng (khối giữa bị trả lại đã do MiddleRetry lo trước);
                                 // 1.14.4: không có khối nào để đập thì đi vào làn của lát (nếu chưa thử), rồi phá đúng khối chắn tia nhìn
                                 // tới mặt lát, vẫn không được thì đi vòng tới chỗ nhìn thấy lát (StrikeTask tự tìm chỗ đứng / đào đường tới)
                                 // — trước đây đứng đó chờ tiếp, hoặc đi về làn rồi lại lên (đi đi lại lại)
                                 this.blocked = 0;
                                 List<BlockPos> var1 = this.blockers(this.idx);
                                 MineController.Task var34 = var1.isEmpty()
                                    ? null
                                    : MineController.this.chooseStrike(var1, "khối che mặt hàng " + (this.strip + 1), false);
                                 if (var34 == null && !var32.equals(this.navFor)) {
                                    // chưa thử đi vào làn của lát này (vừa đi vừa đào) → thử trước, thường là tới nơi thì nhìn thấy
                                    this.navFor = var32;
                                    var34 = this.navToLane(var32, var33);
                                 }

                                 if (var34 == null) {
                                    BlockPos var46 = MineController.this.occluder(var32, var33);
                                    if (var46 != null
                                       && MineController.this.region.inside(var46)
                                       && var46.getY() >= var3
                                       && Cells.mineable(MineController.this.mc, var46)
                                       && !MineController.this.middleDeferred.contains(var46)
                                       && !MineController.this.waterDams.contains(var46)
                                       && !MineController.this.skipped.contains(var46)) {
                                       var34 = MineController.this.chooseStrike(List.of(var46), "khối che lát hàng " + (this.strip + 1), false);
                                    }
                                 }

                                 if (var34 == null) {
                                    if (++this.detours > 3) {
                                       AutoMineMod.LOGGER.info("[AutoMine] Hàng {}: lát {} đi vòng 3 lần chưa đập được → để vét cuối tầng", this.strip + 1, var32.toShortString());
                                       MineController.this.fail(var32, "đi vòng 3 lần chưa đập được");
                                       this.stopBreaking();
                                       this.idx++;
                                       return MineController.Result.RUNNING;
                                    }

                                    // đi vòng: chỗ đứng gần nhìn thấy lát (kể cả mặt khác), không có thì đào đường tới sát lát
                                    var34 = MineController.this.approachStrike(var32);
                                    if (var34 == null) {
                                       var34 = MineController.this.new StrikeTask(var32, var33, "hàng " + (this.strip + 1) + " · đi vòng", true);
                                    }
                                 }

                                 this.sub = var34;
                                 this.stopBreaking();
                                 Keys.releaseMove(MineController.this.mc);
                                 return MineController.Result.RUNNING;
                              }
                           } else {
                              this.blocked = 0;
                           }

                           double var35 = Double.isNaN(this.lastX)
                              ? 1.0
                              : Math.hypot(MineController.this.mc.player.getX() - this.lastX, MineController.this.mc.player.getZ() - this.lastZ);
                           this.lastX = MineController.this.mc.player.getX();
                           this.lastZ = MineController.this.mc.player.getZ();
                           this.idle = this.breakTicks <= 0 && !(var35 > 0.02) ? ++this.idle : 0;
                           if (this.idle == 25) {
                              MineController.this.ghost.requestAround(MineController.this.feet(), var9);
                           }

                           if (this.idle > 25 + MineController.this.settleTicks()) {
                              this.idle = 0;
                              List var36 = this.blockers(this.idx);
                              MineController.Task var21 = var36.isEmpty() ? null : MineController.this.chooseStrike(var36, "phá khối chắn đường", false);
                              if (var21 != null) {
                                 this.sub = var21;
                              } else if (++this.handovers == 1) {
                                 this.sub = this.navToLane(var32, var33);
                              } else if (this.handovers <= 3) {
                                 this.sub = MineController.this.new StrikeTask(var32, var33, "hàng " + (this.strip + 1), true);
                              } else {
                                 MineController.this.fail(var32, "đào liền không tới được");
                                 MineController.this.fail(var32, "đào liền không tới được");
                                 MineController.this.fail(var32, "đào liền không tới được");
                                 this.handovers = 0;
                                 this.idx++;
                              }

                              Keys.releaseMove(MineController.this.mc);
                              return MineController.Result.RUNNING;
                           } else {
                              if (this.age - this.targetSince > 400) {
                                 this.stopBreaking();
                                 MineController.this.fail(var28, "đập 20 giây chưa vỡ");
                                 this.idx++;
                              }

                              MineController.this.status = "Tầng "
                                 + (MineController.this.layer + 1)
                                 + "/"
                                 + MineController.this.region.layerCount()
                                 + " · hàng "
                                 + (this.strip + 1)
                                 + "/"
                                 + this.lp.strips
                                 + " · "
                                 + this.done
                                 + "/"
                                 + this.run.size()
                                 + " · "
                                 + Cells.state(MineController.this.mc, var32).getBlock().getName().getString();
                              return MineController.Result.RUNNING;
                           }
                        }
                     }
                  }
               } else {
                  Keys.releaseMove(MineController.this.mc);
                  this.stopBreaking();
                  if (!this.settling) {
                     // 1.14.2: hết hàng đi tiếp ngay; kiểm tra ô 3x3 / đồng bộ ghost chạy nền (deferCheck), khối thừa để cuối tầng
                     this.settling = true;
                     this.settleUntil = this.age;
                  }

                  if (this.age < this.settleUntil) {
                     MineController.this.status = "Hết hàng " + (this.strip + 1) + " — chờ server cập nhật các ô 3x3";
                     return MineController.Result.RUNNING;
                  } else {
                     this.runChecks(true);
                     if (this.idx >= this.run.size() && !this.reverse) {
                        AutoMineMod.LOGGER
                           .info(
                              "[AutoMine] Xong hàng {}/{} (tầng {}): {} nhát liền trong {} giây",
                              new Object[]{
                                 this.strip + 1,
                                 this.lp.strips,
                                 MineController.this.layer + 1,
                                 this.done,
                                 String.format("%.1f", (System.currentTimeMillis() - this.startMs) / 1000.0)
                              }
                           );
                     }

                     return MineController.Result.DONE;
                  }
               }
            }
         }
      }

      @Override
      void finish() {
         if (this.sub != null) {
            this.sub.finish();
         }

         if (this.fill != null) {
            this.fill.finish();
         }

         this.runChecks(false);
         this.checks.clear();
         super.finish();
      }
   }

   private final class SellTask extends MineController.Task {
      boolean started;
      Set<Integer> reserve = Set.of();
      int dropIdx;
      int dropAt;
      boolean dropping;
      float dropYaw;

      @Override
      String label() {
         return this.dropping
            ? "Vứt đá rác"
            : "Bán đồ /" + MineController.this.config.sellCommand + (MineController.this.sellJob.active() ? " · " + MineController.this.sellJob.status() : "");
      }

      private Predicate<ItemStack> sellable() {
         return var1 -> {
            if (MineController.this.tools.keep(var1)) {
               return false;
            } else {
               return MineController.this.safeFiller(var1) ? true : true;
            }
         };
      }

      @Override
      MineController.Result tick() {
         this.age++;
         Keys.releaseMove(MineController.this.mc);
         if (this.dropping) {
            return this.dropTick();
         } else if (!this.started) {
            this.started = true;
            this.reserve = FillerReserve.slots(
               MineController.this.mc.player.getInventory(), MineController.this::safeFiller, MineController.this.config.keepFiller
            );
            AutoMineMod.LOGGER
               .info("[AutoMine] Bán đồ: giữ riêng {} ô khối kê (tối thiểu {} khối)", this.reserve.size(), Math.max(64, MineController.this.config.keepFiller));
            MineController.this.sellJob.start(MineController.this.mc, this.sellable(), this.reserve);
            return MineController.Result.RUNNING;
         } else if (MineController.this.sellJob.active()) {
            MineController.this.sellJob.tick(MineController.this.mc);
            MineController.this.status = MineController.this.sellJob.status();
            return MineController.Result.RUNNING;
         } else {
            boolean var1 = MineController.this.sellJob.result() == SellJob.Result.DONE
               && Inv.freeSlots(MineController.this.mc) > MineController.this.config.sellWhenFree;
            if (var1) {
               MineController.this.nextSellAt = MineController.this.ticks + 100;
               AutoMineMod.LOGGER.info("[AutoMine] {}", MineController.this.sellJob.status());
               return MineController.Result.DONE;
            } else {
               MineController.this.nextSellAt = MineController.this.ticks + 6000;
               if (MineController.this.config.dropJunkIfSellFails) {
                  MineController.this.say(
                     "§e/" + MineController.this.config.sellCommand + " không bán hết (" + MineController.this.sellJob.status() + ") → vứt đá rác để có chỗ."
                  );
                  this.reserve = FillerReserve.slots(
                     MineController.this.mc.player.getInventory(), MineController.this::safeFiller, MineController.this.config.keepFiller
                  );
                  this.dropping = true;
                  this.dropYaw = MineController.this.mc.player.getYaw() + 180.0F;
                  return MineController.Result.RUNNING;
               } else {
                  MineController.this.say(
                     "§e/"
                        + MineController.this.config.sellCommand
                        + " không bán được: "
                        + MineController.this.sellJob.status()
                        + " → đào tiếp, 5 phút sau thử lại."
                  );
                  return this.failWith(MineController.this.sellJob.status());
               }
            }
         }
      }

      private MineController.Result dropTick() {
         if (!MineController.this.lookAngles(this.dropYaw, 10.0F)) {
            return MineController.Result.RUNNING;
         } else if (this.age - this.dropAt < 3) {
            return MineController.Result.RUNNING;
         } else {
            while (this.dropIdx < 36) {
               ItemStack var1 = MineController.this.mc.player.getInventory().getStack(this.dropIdx);
               if (!this.reserve.contains(this.dropIdx)
                  && !var1.isEmpty()
                  && MineController.this.tools.isJunk(var1)
                  && !MineController.this.tools.keep(var1)
                  && (!MineController.this.safeFiller(var1) || MineController.this.fillerCount() - var1.getCount() >= MineController.this.config.keepFiller)) {
                  if (Inv.dropStack(MineController.this.mc, this.dropIdx)) {
                     this.dropAt = this.age;
                     this.dropIdx++;
                     return MineController.Result.RUNNING;
                  } else {
                     return MineController.Result.RUNNING;
                  }
               }

               this.dropIdx++;
            }

            return MineController.Result.DONE;
         }
      }

      @Override
      void abort() {
         if (MineController.this.sellJob.active()) {
            MineController.this.sellJob.cancel(MineController.this.mc);
         }

         super.abort();
      }
   }

   public static enum State {
      IDLE,
      RUNNING,
      DONE;
   }

   /**
    * 1.14.3: khối giữa của lát vừa đập vỡ rồi hiện lại (server trả lại / ghost) → đổi góc rồi đập lại ngay, không đụng khối xung quanh:
    * lần 1 lùi 0,5 ô (ra xa mặt đập), lần 2 lùi thêm 0,5, lần 3 bước ngang trái 0,5, lần 4 ngang phải 0,5, lần 5 ngồi đập lẻ riêng khối giữa.
    * Sau 5 lần vẫn hiện lại thì RowTask để dành nó tới cuối tầng. Sỏi/cát rơi vào chỗ khối giữa thì đập tại chỗ (không lùi/ngang).
    * 1.14.4: mỗi lần ngắm một điểm khác trên cùng khối (cả mặt đối diện cùng trục); từ chỗ đứng không nhìn thấy / không ngắm trúng được /
    * đứng trên mặt 3x3 của nó thì đi vòng tới chỗ nhìn thấy rồi đập (trước đây đứng ngắm mãi "đập" không ra). Lần nào cũng tính, kể cả sỏi.
    */
   private final class MiddleRetry extends MineController.Task {
      final BlockPos c;
      final Direction f;
      final int attempt;
      final boolean single;
      final boolean falling;
      Vec3d goal;
      int phase;
      int phaseAge;
      int brokeAt;
      int pointIdx;
      int aimTicks;
      int blockedTicks;
      Direction aimFace;
      MineController.Task around;

      MiddleRetry(BlockPos nullx, Direction nullxx, int nullxxx, boolean nullxxxx) {
         this.c = nullx.toImmutable();
         this.f = nullxx;
         this.attempt = nullxxx;
         this.single = nullxxx >= 5;
         this.falling = nullxxxx;
         this.aimFace = nullxx;
         Vec3d var4 = MineController.this.mc.player.getPos();
         Vec3d var5 = nullxxxx
            ? var4
            : MineController.this.middleBase.computeIfAbsent(this.c, var1x -> MineController.this.middleStrikeAt.getOrDefault(var1x, var4));
         double var6 = this.f.getOffsetX();
         double var8 = this.f.getOffsetZ();
         // trái của hướng nhìn (nhìn ngược pháp tuyến mặt đập)
         double var10 = -var8;
         double var12 = var6;
         double[] var14 = nullxxxx ? null : switch (nullxxx) {
            case 1 -> new double[]{0.5, 0.0};
            case 2 -> new double[]{1.0, 0.0};
            case 3 -> new double[]{1.0, 0.5};
            case 4 -> new double[]{1.0, -0.5};
            default -> null;
         };
         if (var14 != null && this.f.getAxis() != Axis.Y) {
            Vec3d var15 = new Vec3d(var5.x + var6 * var14[0] + var10 * var14[1], var4.y, var5.z + var8 * var14[0] + var12 * var14[1]);
            if (this.standOk(var15)) {
               this.goal = var15;
            } else {
               AutoMineMod.LOGGER.info("[AutoMine] Khối giữa {} lần {}: chỗ đổi góc {} không đứng được → đập lại tại chỗ", new Object[]{
                  this.c.toShortString(), nullxxx, String.format("%.2f %.2f", var15.x, var15.z)
               });
            }
         }

         AutoMineMod.LOGGER.info("[AutoMine] Khối giữa {} hiện lại{} → đập lại lần {}{}", new Object[]{
            this.c.toShortString(),
            nullxxxx ? " (sỏi/cát rơi vào)" : "",
            nullxxx,
            this.single ? " (ngồi đập lẻ)" : (this.goal != null ? String.format(" từ %.2f %.2f", this.goal.x, this.goal.z) : " tại chỗ")
         });
      }

      /** chỗ đứng mới: thân người trống, ít nhất 2 góc chân có sàn, mặt đập vẫn trong tầm với */
      private boolean standOk(Vec3d var1) {
         int var2 = (int)Math.floor(var1.y + 0.02);
         BlockPos var3 = BlockPos.ofFloored(var1.x, var2, var1.z);
         if (!Cells.clearBody(MineController.this.mc, var3) || MineController.this.avoid.contains(var3)) {
            return false;
         } else {
            for (double var5 : new double[]{-0.3, 0.3}) {
               for (double var8 : new double[]{-0.3, 0.3}) {
                  if (!Cells.clearBody(MineController.this.mc, BlockPos.ofFloored(var1.x + var5, var2, var1.z + var8))) {
                     return false;
                  }
               }
            }

            Vec3d var11 = new Vec3d(var1.x, var1.y + MineController.this.mc.player.getStandingEyeHeight(), var1.z);
            return SafeMotion.supportedAny(MineController.this.mc, var1.x, var1.z, var2 - 1, var2)
               && var11.distanceTo(Cells.faceCenter(this.c, this.f)) <= MineController.this.reach() - 0.3;
         }
      }

      /** các điểm ngắm được: nhát 3x3 → 2 mặt cùng trục với mặt lát (cùng mặt phẳng 3x3), nhát lẻ → mọi mặt */
      private List<Object[]> aimPoints(Vec3d var1) {
         ArrayList<Object[]> var2 = new ArrayList<>();
         List<Direction> var3 = this.single ? new ArrayList<>(List.of(Direction.values())) : new ArrayList<>(List.of(this.f, this.f.getOpposite()));
         var3.remove(this.f);
         var3.addFirst(this.f);

         for (Direction var5 : var3) {
            for (Vec3d var7 : Cells.facePoints(MineController.this.mc, var1, this.c, var5, MineController.this.reach() - 0.05, !this.single)) {
               var2.add(new Object[]{var7, var5});
            }
         }

         return var2;
      }

      /** không đập được từ chỗ này → đi vòng tới chỗ nhìn thấy khối giữa rồi đập (StrikeTask tự tìm chỗ đứng / đào đường tới) */
      private void walkAround(String var1) {
         MineController.Task var2 = MineController.this.approachStrike(this.c);
         if (var2 instanceof MineController.StrikeTask var3) {
            var3.single = this.single;
         } else if (var2 == null) {
            MineController.StrikeTask var4 = MineController.this.new StrikeTask(this.c, this.f, "khối giữa · đi vòng", true);
            var4.single = this.single;
            var2 = var4;
         }

         AutoMineMod.LOGGER.info("[AutoMine] Khối giữa {} lần {}: {} → đi vòng: {}", new Object[]{this.c.toShortString(), this.attempt, var1, var2.label()});
         Keys.releaseMove(MineController.this.mc);
         this.around = var2;
      }

      @Override
      String label() {
         String[] var1 = new String[]{"đổi chỗ", "đứng vững", "đập", "chờ server"};
         return "Đập lại khối giữa "
            + this.c.toShortString()
            + " lần "
            + this.attempt
            + (this.single ? " (lẻ)" : "")
            + " · "
            + (this.around != null ? "đi vòng · " + this.around.label() : var1[Math.min(this.phase, 3)]);
      }

      @Override
      boolean walking() {
         return this.around != null && this.around.walking();
      }

      @Override
      boolean interruptible() {
         return this.phase != 2 && this.around == null;
      }

      @Override
      void onSetback() {
         if (this.around != null) {
            this.around.onSetback();
         }
      }

      @Override
      void finish() {
         if (this.around != null) {
            this.around.finish();
            this.around = null;
         }

         super.finish();
      }

      private void to(int var1) {
         this.phase = var1;
         this.phaseAge = 0;
      }

      @Override
      MineController.Result tick() {
         this.age++;
         this.phaseAge++;
         if (this.around != null) {
            MineController.this.status = this.label();
            MineController.Result var11 = this.around.tick();
            if (var11 == MineController.Result.RUNNING && this.age <= 600) {
               return var11;
            } else {
               this.around.finish();
               this.around = null;
               if (!Cells.mineable(MineController.this.mc, this.c)) {
                  this.brokeAt = MineController.this.ticks;
                  this.to(3);
                  return MineController.Result.RUNNING;
               } else {
                  return MineController.Result.DONE;
               }
            }
         } else if (this.phase < 3 && !Cells.mineable(MineController.this.mc, this.c)) {
            if (this.phase == 2 && MineController.this.justBroke(this.c)) {
               // 1.14.5: vỡ do giữ chuột → chờ server xem có hiện lại không
               this.brokeAt = MineController.this.ticks;
               this.to(3);
               return MineController.Result.RUNNING;
            }

            return MineController.Result.DONE;
         } else if (this.age > 240) {
            AutoMineMod.LOGGER.info("[AutoMine] Khối giữa {} lần {}: quá 12 giây chưa đập được", this.c.toShortString(), this.attempt);
            return MineController.Result.DONE;
         } else if (!MineController.this.equipPick()) {
            Keys.releaseMove(MineController.this.mc);
            return MineController.Result.RUNNING;
         } else {
            BotInput var1 = BotInput.INSTANCE;
            Vec3d var2 = MineController.this.mc.player.getEyePos();
            List<Object[]> var3 = this.phase == 3 ? List.of() : this.aimPoints(var2);
            Object[] var4 = var3.isEmpty() ? null : var3.get((this.attempt + this.pointIdx) % var3.size());
            if (var4 != null) {
               this.aimFace = (Direction)var4[1];
               MineController.this.look((Vec3d)var4[0]);
            } else if (this.phase != 3) {
               MineController.this.look(Cells.faceCenter(this.c, this.f));
            }

            MineController.this.status = this.label();
            switch (this.phase) {
               case 0:
                  if (this.goal != null
                     && this.phaseAge <= 30
                     && Math.hypot(this.goal.x - MineController.this.mc.player.getX(), this.goal.z - MineController.this.mc.player.getZ()) > 0.08) {
                     // nhích 0,5 ô: đi ngồi cho chậm mà đúng chỗ (đi thường trôi quá 0,1–0,2 ô)
                     var1.sneaking = true;
                     Keys.drive(MineController.this.mc, this.goal.x, this.goal.z, 0.05, true);
                     return MineController.Result.RUNNING;
                  }

                  Keys.releaseMove(MineController.this.mc);
                  this.to(1);
               case 1:
                  // đứng vững (hết trôi) rồi mới đập: nhát lẻ thì ngồi hẳn, nhát 3x3 thì đứng thẳng
                  Keys.releaseMove(MineController.this.mc);
                  boolean var5 = !this.single && SafeMotion.edgeSneak(MineController.this.mc);
                  if (var5 && MineController.this.recentre()) {
                     var1.sneaking = true;
                     return MineController.Result.RUNNING;
                  }

                  var1.sneaking = this.single || var5;
                  if (var5
                     || this.phaseAge < 12 && MineController.this.mc.player.getVelocity().horizontalLength() > 0.02
                     || (this.single ? var1.sneakFree != 0 : var1.sneakFree < 1)) {
                     return MineController.Result.RUNNING;
                  }

                  this.to(2);
               case 2:
                  var1.sneaking = this.single;
                  boolean var6 = MineController.this.protectedFooting(this.single ? List.of(this.c) : Cells.plane(this.c, this.aimFace));
                  if (var4 == null || var6) {
                     // không nhìn thấy điểm nào / đang đứng trên mặt 3x3 của nó → đi vòng ngay, không đứng ngắm mãi
                     if (++this.blockedTicks >= 4) {
                        this.walkAround(var6 ? "đang đứng trên mặt 3x3 của nó" : "không nhìn thấy từ chỗ đứng");
                     }

                     return MineController.Result.RUNNING;
                  } else {
                     this.blockedTicks = 0;
                     boolean var7 = this.single
                        ? MineController.this.rotationStableTicks >= 1 && MineController.this.crosshairOn(this.c, this.aimFace)
                        : MineController.this.drillCrosshairOn(this.c, this.aimFace);
                     if (var7 && (this.single ? var1.sneakFree == 0 : var1.sneakFree >= 1)) {
                        this.aimTicks = 0;
                        List<BlockPos> var8 = new ArrayList<>();
                        if (!this.single) {
                           int var9 = MineController.this.region.floor(MineController.this.layer);

                           for (BlockPos var10 : Cells.plane(this.c, this.aimFace)) {
                              if (!var10.equals(this.c) && MineController.this.region.inside(var10) && var10.getY() >= var9 && Cells.mineable(MineController.this.mc, var10)) {
                                 var8.add(var10.toImmutable());
                              }
                           }
                        }

                        MineController.this.hold(this.c, this.single ? null : this.aimFace, this.single ? null : var8);

                        return MineController.Result.RUNNING;
                     } else {
                        // ngắm điểm này 6 tick chưa trúng → điểm khác trên cùng khối; thử hết các điểm thì đi vòng
                        if (++this.aimTicks > 6) {
                           this.aimTicks = 0;
                           if (++this.pointIdx >= var3.size()) {
                              this.walkAround("ngắm " + var3.size() + " điểm không trúng");
                           }
                        }

                        return MineController.Result.RUNNING;
                     }
                  }
               default:
                  // chờ server: hiện lại thì trả về ngay (RowTask đổi góc lần kế), giữ được qua khoảng ping thì coi như xong
                  Keys.releaseMove(MineController.this.mc);
                  var1.sneaking = false;
                  if (Cells.mineable(MineController.this.mc, this.c)) {
                     return MineController.Result.DONE;
                  } else if (MineController.this.ticks - this.brokeAt >= Math.min(MineController.this.settleTicks(), 4 + 2 * ServerSync.pingTicks(MineController.this.mc))) {
                     AutoMineMod.LOGGER.info("[AutoMine] Khối giữa {}: lần {} vỡ hẳn", this.c.toShortString(), this.attempt);
                     MineController.this.middleTries.remove(this.c);
                     MineController.this.middleBase.remove(this.c);
                     return MineController.Result.DONE;
                  } else {
                     return MineController.Result.RUNNING;
                  }
            }
         }
      }
   }

   private final class StrikeTask extends MineController.Task {
      final BlockPos c;
      Direction f;
      final String what;
      final boolean mayWalk;
      boolean single;
      boolean failureRecorded;
      boolean mineApproach;
      final Set<BlockPos> triedStands = new HashSet<>();
      BlockPos preferStand;
      MineController.WalkTask walk;
      int phase;
      int phaseAge;
      int brokeAt;
      int settledAt = -1;
      int offTicks;
      int reverts;
      int walks;
      double expected;
      Vec3d point;
      List<BlockPos> before = List.of();
      int waitUntil;
      boolean resynced;
      double pointEyeY = Double.NaN;
      int aimSwitch;

      StrikeTask(BlockPos nullx, Direction nullxx, String nullxxx, boolean nullxxxx) {
         this.c = nullx.toImmutable();
         this.f = nullxx;
         this.what = nullxxx;
         this.mayWalk = nullxxxx;
         // 1.14.3: hỏng 1–2 lần vẫn đập 3x3 (đập lẻ khối giữa thì 8 khối quanh lại phải đập từng khối); hỏng từ 3 lần mới đập lẻ
         this.single = MineController.this.damInPlane(this.c, this.f)
            || Boolean.FALSE.equals(MineController.this.threeByThree)
            || MineController.this.fails.getOrDefault(this.c, 0) >= 3;
      }

      @Override
      MineController.Result failWith(String var1) {
         if (!this.failureRecorded) {
            this.failureRecorded = true;
            MineController.this.fail(this.c, var1);
         }

         return super.failWith(var1);
      }

      private boolean aimed() {
         return this.single
            ? MineController.this.rotationStableTicks >= 1 && MineController.this.crosshairOn(this.c, this.f)
            : MineController.this.drillCrosshairOn(this.c, this.f);
      }

      private boolean holdMiningPosture() {
         // 1.14.3: sát mép vẫn đập 3x3 (đứng yên thì không rơi); trước đây cứ 1 góc chân lơ lửng là chuyển sang ngồi đập lẻ từng khối.
         // Chỉ ngồi chờ khi đang trôi về chỗ hụt chân; hụt chân mà đứng yên thì nhích vào tâm ô sàn.
         boolean var4 = !this.single && SafeMotion.edgeSneak(MineController.this.mc);
         Keys.releaseMove(MineController.this.mc);
         BotInput var1 = BotInput.INSTANCE;
         if (var4 && MineController.this.recentre()) {
            var1.sneaking = true;
         } else {
            var1.sneaking = this.single || var4;
         }

         if (!var4 && var1.playerInput.sneak() == this.single && (this.single ? var1.sneakFree == 0 : var1.sneakFree >= 1)) {
            double var2 = MineController.this.mc.player.getEyePos().y;
            if (Double.isNaN(this.pointEyeY) || Math.abs(var2 - this.pointEyeY) > 1.0E-4) {
               // 1.14.4: đập lại (hỏng / ngắm mãi không trúng) thì ngắm điểm khác trên cùng khối
               List<Vec3d> var5 = Cells.facePoints(
                  MineController.this.mc, MineController.this.mc.player.getEyePos(), this.c, this.f, MineController.this.reach() - 0.05, !this.single
               );
               this.point = var5.isEmpty()
                  ? null
                  : var5.get((MineController.this.hardFails.getOrDefault(this.c, 0) + this.reverts + this.aimSwitch) % var5.size());
               this.pointEyeY = var2;
               this.settledAt = -1;
               this.offTicks = 0;
               MineController.this.rotationStableTicks = 0;
            }

            return true;
         } else {
            this.point = null;
            this.pointEyeY = Double.NaN;
            this.settledAt = -1;
            this.offTicks = 0;
            return false;
         }
      }

      private Vec3d visiblePoint() {
         Vec3d var1 = MineController.this.mc.player.getEyePos();
         boolean var2 = MineController.this.region != null
            && MineController.this.region.inside(this.c)
            && MineController.this.layer >= 0
            && this.c.getY() >= MineController.this.region.floor(MineController.this.layer)
            && !MineController.this.protectedFooting(List.of(this.c));

         // 1.14.0: thử mặt đã chọn trước (mặt 3x3 phủ nhiều khối nhất), các mặt khác chỉ khi mặt đó không nhìn thấy
         List<Direction> var13 = new ArrayList<>(List.of(Direction.values()));
         var13.remove(this.f);
         var13.addFirst(this.f);

         for (boolean var6 : this.single ? new boolean[]{true} : (var2 ? new boolean[]{false, true} : new boolean[]{false})) {
            for (Direction var10 : var13) {
               if (var2 || var10 == this.f) {
                  List var11 = var6 ? List.of(this.c) : Cells.plane(this.c, var10);
                  if ((!var2 || MineController.this.cleanupPlane(this.c, var10, var6, false) != null)
                     && (
                        !this.mayWalk && var10.getAxis() == Axis.Y && this.c.getY() < MineController.this.feet().getY()
                           || !MineController.this.protectedFooting(var11)
                     )) {
                     Vec3d var12 = MineController.this.miningPoint(var1, this.c, var10, var6);
                     if (var12 != null) {
                        this.single = var6;
                        this.f = var10;
                        return var12;
                     }
                  }
               }
            }
         }

         return null;
      }

      @Override
      String label() {
         String[] var1 = new String[]{"tới chỗ đứng", "ngắm", "đập", "chờ server", "kiểm tra", "đồng bộ ghost"};
         return "Đập "
            + this.c.toShortString()
            + " ("
            + this.what
            + ") · "
            + var1[Math.min(this.phase, 5)]
            + (this.walk != null ? " · " + this.walk.label() : "");
      }

      @Override
      boolean walking() {
         return this.walk != null && this.walk.walking();
      }

      @Override
      boolean interruptible() {
         return this.phase == 0 || this.phase == 1 || this.phase >= 4;
      }

      @Override
      void onSetback() {
         if (this.walk != null) {
            this.walk.onSetback();
         }
      }

      private void to(int var1) {
         this.phase = var1;
         this.phaseAge = 0;
      }

      @Override
      MineController.Result tick() {
         if (MineController.this.waterDams.contains(this.c) && Cells.mineable(MineController.this.mc, this.c)) {
            return this.failWith("Giữ đập chắn nước; tìm đường vòng");
         } else {
            this.age++;
            this.phaseAge++;
            if (this.age > 1200) {
               return this.failWith("quá 60 giây");
            } else {
               switch (this.phase) {
                  case 0:
                     if (!Cells.mineable(MineController.this.mc, this.c)) {
                        return MineController.Result.DONE;
                     } else if (this.walk == null && !this.holdMiningPosture()) {
                        return MineController.Result.RUNNING;
                     } else {
                        boolean var8 = this.single;
                        Vec3d var9 = this.visiblePoint();
                        if (this.single != var8) {
                           this.pointEyeY = Double.NaN;
                           this.holdMiningPosture();
                           return MineController.Result.RUNNING;
                        } else {
                           if (var9 != null
                              && this.mayWalk
                              && !this.single
                              && MineController.this.standsOnPlane(
                                 MineController.this.feet(), this.c, this.f, MineController.this.threeByThree == null || MineController.this.threeByThree
                              )) {
                              var9 = null;
                           }

                           if (var9 == null
                              || !MineController.this.mc.player.isOnGround()
                              || this.walk != null && !(MineController.this.mc.player.getVelocity().horizontalLength() < 0.12)) {
                              if (!this.mayWalk) {
                                 if (this.phaseAge > 40) {
                                    return this.failWith("không nhìn thấy mặt " + this.f.asString());
                                 }

                                 if (var9 == null && this.phaseAge % 10 == 0) {
                                    MineController.this.look(Cells.faceCenter(this.c, this.f));
                                 }

                                 return MineController.Result.RUNNING;
                              } else {
                                 if (this.walk == null) {
                                    BlockPos var6 = this.preferStand;
                                    this.preferStand = null;
                                    if (var6 == null || this.triedStands.contains(var6) || !Cells.standable(MineController.this.mc, var6)) {
                                       int var10 = MineController.this.layer >= 0 && MineController.this.region.inside(this.c)
                                          ? MineController.this.region.floor(MineController.this.layer)
                                          : this.c.getY() - 3;
                                       int var13 = MineController.this.layer >= 0 && MineController.this.region.inside(this.c)
                                          ? MineController.this.region.floor(MineController.this.layer)
                                          : this.c.getY() + 2;
                                       var6 = MineController.this.findStand(
                                          this.c,
                                          this.f,
                                          Math.min(var10, MineController.this.feet().getY()),
                                          Math.max(var13, MineController.this.feet().getY()),
                                          this.single,
                                          this.triedStands
                                       );
                                    }

                                    if (var6 == null) {
                                       return this.failWith("không có chỗ đứng nhìn thấy mặt " + this.f.asString());
                                    }

                                    if (++this.walks > 4) {
                                       return this.failWith("đi tới chỗ đứng 4 lần vẫn chưa thấy mặt");
                                    }

                                    if (this.mineApproach) {
                                       int var11 = MineController.this.region.floor(MineController.this.layer);
                                       PathFinder.Dig var14 = new PathFinder.Dig(MineController.this.region, false, var11, false);
                                       var14.preserved = MineController.this.waterDams;
                                       this.walk = MineController.this.new WalkTask(var6, 0.2, var14, "tới chỗ đứng đào ô sót", 20000, 1.0);
                                       this.walk.yLo = var11;
                                       this.walk.yHi = var11;
                                    } else {
                                       this.walk = MineController.this.new WalkTask(var6, 0.2, false, "tới chỗ đứng");
                                    }
                                 }

                                 MineController.Result var7;
                                 if ((var7 = this.walk.tick()) != MineController.Result.RUNNING) {
                                    this.triedStands.add(this.walk.goal);
                                    String var12 = this.walk.why;
                                    this.walk.finish();
                                    this.walk = null;
                                    if (var7 == MineController.Result.FAILED && this.walks >= 4) {
                                       return this.failWith(var12);
                                    }
                                 }

                                 return MineController.Result.RUNNING;
                              }
                           } else {
                              if (this.walk != null) {
                                 this.walk.finish();
                                 this.walk = null;
                              }

                              Keys.releaseMove(MineController.this.mc);
                              this.point = var9;
                              this.pointEyeY = MineController.this.mc.player.getEyePos().y;
                              this.before = new ArrayList<>();

                              for (BlockPos var5 : Cells.plane(this.c, this.f)) {
                                 if (!this.single
                                    && !var5.equals(this.c)
                                    && MineController.this.region != null
                                    && MineController.this.region.inside(var5)
                                    && Cells.mineable(MineController.this.mc, var5)) {
                                    this.before.add(var5);
                                 }
                              }

                              this.expected = Cells.breakTicks(MineController.this.mc, this.c);
                              this.to(1);
                              return MineController.Result.RUNNING;
                           }
                        }
                     }
                  case 1:
                     if (!Cells.mineable(MineController.this.mc, this.c)) {
                        return MineController.Result.DONE;
                     } else if (!this.holdMiningPosture()) {
                        return MineController.Result.RUNNING;
                     } else if (!MineController.this.equipPick()) {
                        if (this.phaseAge > 60) {
                           return this.failWith("không cầm được cúp");
                        }

                        return MineController.Result.RUNNING;
                     } else if (this.aimed()) {
                        this.brokeAt = -1;
                        this.offTicks = 0;
                        this.to(2);
                        return this.breakStep();
                     } else {
                        if (this.phaseAge % 8 == 0 || this.point == null) {
                           boolean var2 = this.single;
                           Vec3d var3 = this.visiblePoint();
                           if (this.single != var2) {
                              this.pointEyeY = Double.NaN;
                              this.holdMiningPosture();
                              return MineController.Result.RUNNING;
                           }

                           if (var3 == null) {
                              this.to(0);
                              return MineController.Result.RUNNING;
                           }

                           this.point = var3;
                        }

                        if (MineController.this.look(this.point) && this.aimed()) {
                           if (this.settledAt < 0) {
                              this.settledAt = this.age;
                           }

                           if (this.age - this.settledAt >= 1) {
                              this.brokeAt = -1;
                              this.offTicks = 0;
                              this.to(2);
                           }
                        } else {
                           this.settledAt = -1;
                        }

                        if (this.phaseAge > 80) {
                           if (!this.aimed()) {
                              return this.failWith("ngắm mãi không trúng mặt " + this.f.asString());
                           }

                           this.to(2);
                        }

                        return MineController.Result.RUNNING;
                     }
                  case 2:
                     return this.breakStep();
                  case 3:
                     int var1 = 2 + ServerSync.pingTicks(MineController.this.mc);
                     if ((!ServerSync.pending(MineController.this.mc, this.c) || this.phaseAge >= 80) && this.phaseAge >= var1) {
                        this.to(4);
                        return MineController.Result.RUNNING;
                     }

                     return MineController.Result.RUNNING;
                  case 4:
                     return this.verifyStep();
                  default:
                     return this.age < this.waitUntil ? MineController.Result.RUNNING : MineController.Result.DONE;
               }
            }
         }
      }

      private MineController.Result breakStep() {
         if (!Cells.mineable(MineController.this.mc, this.c)) {
            // 1.14.2: vỡ là xong ngay — kiểm tra server trả lại / ô 3x3 / đồng bộ ghost chạy nền (deferCheck),
            // trước đây đứng chờ "chờ server" + "kiểm tra" + "đồng bộ ghost" sau mỗi nhát
            // 1.14.5: đếm nhát / hẹn kiểm tra do consumeBreak() làm (khối vỡ lúc game đọc phím)
            this.brokeAt = this.age;
            return MineController.Result.DONE;
         } else if (!this.holdMiningPosture()) {
            return MineController.Result.RUNNING;
         } else if (!MineController.this.pickReady()) {
            this.to(1);
            return MineController.Result.RUNNING;
         } else if (!this.aimed()) {
            if (this.point != null) {
               MineController.this.look(this.point);
            }

            if (++this.offTicks > 12) {
               this.aimSwitch++;
               this.pointEyeY = Double.NaN;
               MineController.this.mc.interactionManager.cancelBlockBreaking();
               this.to(1);
               return MineController.Result.RUNNING;
            } else {
               return MineController.Result.RUNNING;
            }
         } else {
            this.offTicks = 0;
            if (!this.mayWalk && this.f.getAxis() == Axis.Y && this.c.getY() < MineController.this.feet().getY()
               || (this.single ? !MineController.this.protectedFooting(List.of(this.c)) : !MineController.this.protectedFooting(this.c, this.f))) {
               if (this.phaseAge == 1) {
                  this.before = new ArrayList<>();

                  for (BlockPos var2 : Cells.plane(
                     this.c, BreakGeometry.normal(MineController.this.mc.player.getYaw(), MineController.this.mc.player.getPitch())
                  )) {
                     if (!this.single
                        && !var2.equals(this.c)
                        && MineController.this.region != null
                        && MineController.this.region.inside(var2)
                        && Cells.mineable(MineController.this.mc, var2)) {
                        this.before.add(var2);
                     }
                  }
               }

               MineController.this.hold(this.c, this.single ? null : this.f, this.single ? List.of() : this.before);
               if (this.point != null) {
                  MineController.this.look(this.point);
               }

               if (this.phaseAge > this.expected * 2.0 + 60.0) {
                  MineController.this.mc.interactionManager.cancelBlockBreaking();
                  return this.failWith("đập " + this.phaseAge + " tick chưa vỡ");
               } else {
                  MineController.this.status = "Đập " + Cells.state(MineController.this.mc, this.c).getBlock().getName().getString() + " (" + this.what + ")";
                  return MineController.Result.RUNNING;
               }
            } else {
               MineController.this.mc.interactionManager.cancelBlockBreaking();
               this.to(0);
               return MineController.Result.RUNNING;
            }
         }
      }

      private MineController.Result verifyStep() {
         if (Cells.mineable(MineController.this.mc, this.c)) {
            if (++this.reverts >= 8) {
               return this.failWith("server trả lại khối 8 lần");
            } else {
               MineController.this.ghost.request(this.c);
               this.to(1);
               return MineController.Result.RUNNING;
            }
         } else {
            int var1 = 0;
            ArrayList<BlockPos> var2 = new ArrayList<>();

            for (BlockPos var4 : this.before) {
               if (Cells.mineable(MineController.this.mc, var4)) {
                  var2.add(var4);
               } else {
                  var1++;
               }
            }

            if (this.brokeAt > 0) {
               MineController.this.cleared += var1 + 1;
               MineController.this.calibrate(this.before.size(), var1);
            }

            MineController.this.fails.remove(this.c);
            MineController.this.retryAt.remove(this.c);
            if (!var2.isEmpty()
               && MineController.this.config.ghostResync
               && !this.resynced
               && (MineController.this.threeByThree == null || MineController.this.threeByThree)) {
               this.resynced = true;

               for (BlockPos var6 : var2) {
                  MineController.this.ghost.request(var6);
               }

               this.waitUntil = this.age + MineController.this.settleTicks();
               this.to(5);
               return MineController.Result.RUNNING;
            } else {
               return MineController.Result.DONE;
            }
         }
      }

      @Override
      void finish() {
         if (this.walk != null) {
            this.walk.finish();
         }

         super.finish();
      }
   }

   private abstract class Task {
      int age;
      String why = "";
      boolean fatal;

      abstract MineController.Result tick();

      abstract String label();

      boolean walking() {
         return false;
      }

      boolean exclusive() {
         return false;
      }

      boolean interruptible() {
         return false;
      }

      void onSetback() {
      }

      void abort() {
         this.finish();
      }

      void finish() {
         MouseHold.release();
         ServerSync.restoreBreaking(MineController.this.mc);
         if (MineController.this.mc.interactionManager != null && MineController.this.mc.interactionManager.isBreakingBlock()) {
            MineController.this.mc.interactionManager.cancelBlockBreaking();
         }

         Keys.releaseMove(MineController.this.mc);
         Keys.use(MineController.this.mc, false);
      }

      MineController.Result failWith(String var1) {
         this.why = var1;
         return MineController.Result.FAILED;
      }
   }

   private final class UnstuckTask extends MineController.Task {
      final BlockPos p;

      UnstuckTask(BlockPos nullx) {
         this.p = nullx.toImmutable();
      }

      @Override
      String label() {
         return "Kẹt trong khối " + this.p.toShortString() + " → phá ra";
      }

      @Override
      boolean exclusive() {
         return true;
      }

      @Override
      MineController.Result tick() {
         this.age++;
         if (Cells.mineable(MineController.this.mc, this.p) && MineController.this.mc.player.isInsideWall()) {
            if (this.age > 200) {
               return this.failWith("phá mãi không ra");
            } else if (!MineController.this.equipPick()) {
               return MineController.Result.RUNNING;
            } else {
               MineController.this.hold(this.p, null, null);
               MineController.this.status = "Kẹt trong khối → phá";
               return MineController.Result.RUNNING;
            }
         } else {
            if (this.age > 2) {
               MineController.this.ghost.request(this.p);
            }

            return MineController.Result.DONE;
         }
      }
   }

   private final class WalkTask extends MineController.Task {
      BlockPos goal;
      final double tol;
      final boolean allowMine;
      final String what;
      PathFinder.Dig dig;
      PathFinder.Dig activeDig;
      boolean keepRelaxed;
      Predicate<BlockPos> goalTest;
      Integer yLo;
      Integer yHi;
      int maxNodes = 6000;
      double weight = 1.0;
      List<PathFinder.Step> path;
      AutoBuildNavigation.Search routeSearch;
      boolean directPath;
      boolean waterAccess;
      boolean verticalAccess;
      /** 1.14.5: mặc định không bắc cầu; chỉ việc cần (kê cột lên lại sàn khi đi bộ / đào bậc đều hỏng) mới bật */
      boolean bridgeOk;
      int idx;
      int stuck;
      double bestDist = Double.MAX_VALUE;
      int replans;
      MineController.Task sub;
      int jumpHold;
      int lastJumpAge = -100;
      BlockPos jumpCell;
      BlockPos airGoal;
      int airGoalAge;
      int noRise;
      Runnable onFail;
      int aimIdx = -1;
      boolean reentered;
      int directRejects;

      WalkTask(BlockPos nullx, double nullxx, boolean nullxxx, String nullxxxx) {
         this.goal = nullx.toImmutable();
         this.tol = nullxx;
         this.allowMine = nullxxx;
         this.what = nullxxxx;
      }

      WalkTask(BlockPos nullx, double nullxx, PathFinder.Dig nullxxx, String nullxxxx, int nullxxxxx, double nullxxxxxx) {
         this(nullx, nullxx, false, nullxxxx);
         this.dig = nullxxx;
         this.maxNodes = nullxxxxx;
         this.weight = nullxxxxxx;
      }

      @Override
      String label() {
         return "Đi " + this.what + (this.sub != null ? " · " + this.sub.label() : "");
      }

      @Override
      boolean walking() {
         return this.sub == null;
      }

      @Override
      boolean interruptible() {
         return this.sub == null && MineController.this.mc.player.isOnGround();
      }

      @Override
      void onSetback() {
         this.stuck = 0;
         this.bestDist = Double.MAX_VALUE;
         Keys.releaseMove(MineController.this.mc);
         if (MineController.this.ghost.setbacks % 3 == 0 && MineController.this.ghost.lastSetbackAt != null) {
            MineController.this.avoid.add(MineController.this.ghost.lastSetbackAt);
            this.path = null;
         }
      }

      private boolean arrived(BlockPos var1) {
         if (!MineController.this.mc.player.isOnGround()
            || !Cells.floor(MineController.this.mc, var1.down())
            || ServerSync.pending(MineController.this.mc, var1.down())) {
            return false;
         } else {
            return this.goalTest != null
               ? MineController.this.mc.player.isOnGround() && this.goalTest.test(var1)
               : Math.abs(var1.getX() - this.goal.getX()) + Math.abs(var1.getZ() - this.goal.getZ()) == 0 && var1.getY() == this.goal.getY();
         }
      }

      /** ô kế tiếp theo trục hướng về đích (đường thẳng chỉ giữ 1 bước, dùng để đào trước ô sau) */
      private BlockPos towardGoal(BlockPos var1) {
         int var2 = this.goal.getX() - var1.getX();
         int var3 = this.goal.getZ() - var1.getZ();
         if (var2 == 0 && var3 == 0) {
            return null;
         } else {
            return Math.abs(var2) >= Math.abs(var3) ? var1.add(Integer.signum(var2), 0, 0) : var1.add(0, 0, Integer.signum(var3));
         }
      }

      BlockPos drillAt;
      int drillSince;
      int drillFails;
      List<BlockPos> drillBefore;

      /**
       * 1.14.2: đào khối chắn đường ngay trong WalkTask: ngắm (tức thì), giữ đập, khối vỡ (phía client) là đi tiếp ngay;
       * kiểm tra server chạy nền. moving = đang đi tới ô trước (đào trước ô kế tiếp), không dừng chân.
       * Trả false nếu không đào thẳng được (không thấy mặt / đập lẻ phải ngồi / cúp không 3x3) → để StrikeTask làm.
       */
      private boolean drill(PathFinder.Strike var1, boolean var2) {
         BlockPos var3 = var1.c();
         if (this.drillFails >= 2
            || !Cells.mineable(MineController.this.mc, var3)
            || Boolean.FALSE.equals(MineController.this.threeByThree)
            || MineController.this.fails.getOrDefault(var3, 0) > 0
            || MineController.this.damInPlane(var3, var1.f())
            || !this.keepRelaxed && MineController.this.suspendedInPlane(var3, var1.f())) {
            return false;
         } else {
            Vec3d var4 = MineController.this.mc.player.getEyePos();
            Direction var5 = var1.f();
            Vec3d var6 = Cells.drillFacePoint(MineController.this.mc, var4, var3, var5, MineController.this.reach());
            if (var6 == null) {
               for (Direction var10 : Direction.values()) {
                  if (var10 != var1.f() && (this.activeDig == null || this.activeDig.strikeAllowed(MineController.this.mc, var3, var10))) {
                     var6 = Cells.drillFacePoint(MineController.this.mc, var4, var3, var10, MineController.this.reach());
                     if (var6 != null) {
                        var5 = var10;
                        break;
                     }
                  }
               }
            }

            if (var6 == null || MineController.this.protectedFooting(var3, var5)) {
               return false;
            } else if (!MineController.this.equipPick()) {
               return !var2;
            } else {
               if (!var3.equals(this.drillAt)) {
                  this.drillAt = var3.toImmutable();
                  this.drillSince = this.age;
                  this.drillBefore = null;
               }

               if (!var2) {
                  Keys.releaseMove(MineController.this.mc);
               }

               BotInput.INSTANCE.sneaking = false;
               MineController.this.look(var6);
               MineController.this.status = "Đào đường: " + Cells.state(MineController.this.mc, var3).getBlock().getName().getString();
               if (BotInput.INSTANCE.sneakFree >= 1 && MineController.this.drillCrosshairOn(var3, var5)) {
                  if (this.drillBefore == null) {
                     this.drillBefore = new ArrayList<>();

                     for (BlockPos var8 : Cells.plane(var3, var5)) {
                        if (!var8.equals(var3) && Cells.mineable(MineController.this.mc, var8)) {
                           this.drillBefore.add(var8.toImmutable());
                        }
                     }
                  }

                  MineController.this.hold(var3, var5, this.drillBefore);
               }

               if (this.age - this.drillSince > Cells.breakTicks(MineController.this.mc, var3) * 2.0 + 60.0) {
                  MineController.this.mc.interactionManager.cancelBlockBreaking();
                  this.drillFails++;
                  this.drillAt = null;
                  return false;
               } else {
                  return true;
               }
            }
         }
      }

      /** 1.14.5: khối chắn trước mặt (thân/đầu ở ô kế theo hướng đi) thuộc khung → giữ chuột phá nó. Trả true khi đang phá. */
      private boolean breakThrough(BlockPos var1, BlockPos var2) {
         if (MineController.this.region != null && MineController.this.layer >= 0 && !var1.equals(var2)) {
            Direction var3 = Direction.getFacing(var2.getX() - var1.getX(), 0.0F, var2.getZ() - var1.getZ());
            int var4 = MineController.this.region.floor(MineController.this.layer);

            for (BlockPos var8 : new BlockPos[]{var1.offset(var3).up(), var1.offset(var3), var1.offset(var3).up(2), var2.up(), var2}) {
               if (MineController.this.region.inside(var8)
                  && var8.getY() >= var4
                  && Cells.mineable(MineController.this.mc, var8)
                  && !MineController.this.waterDams.contains(var8)
                  && !MineController.this.gaveUp.contains(var8)
                  && this.drill(new PathFinder.Strike(var8, var3.getOpposite()), false)) {
                  return true;
               }
            }
         }

         return false;
      }

      /** 1.14.0: bước giữa đường đi thẳng → vào tới ô là tính đã qua, không phải đứng vào tâm ô rồi mới đi tiếp. */
      private boolean through(int var1) {
         if (this.path != null && var1 < this.path.size()) {
            PathFinder.Step var2 = this.path.get(var1);
            if (var1 == this.path.size() - 1 && this.directPath && this.goalTest == null && var2.kind() != PathFinder.Kind.PILLAR && var2.kind() != PathFinder.Kind.BRIDGE) {
               // 1.14.2: bước của đường thẳng (kể cả ô vừa đào) chưa phải đích → đi qua luôn, không hãm / ngồi
               return !var2.feet().equals(this.goal);
            } else if (var2.mine() || var2.kind() == PathFinder.Kind.PILLAR || var2.kind() == PathFinder.Kind.BRIDGE) {
               return false;
            } else if (var1 == this.path.size() - 1) {
               return false;
            } else {
               PathFinder.Kind var3 = this.path.get(var1 + 1).kind();
               return var3 == PathFinder.Kind.WALK || var3 == PathFinder.Kind.JUMP;
            }
         } else {
            return false;
         }
      }

      private boolean dropNear(BlockPos var1) {
         for (Direction var3 : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos var4 = var1.offset(var3);
            if (Cells.passable(MineController.this.mc, var4)
               && Cells.passable(MineController.this.mc, var4.up())
               && Cells.passable(MineController.this.mc, var4.down())
               && !Cells.floor(MineController.this.mc, var4.down(2))) {
               return true;
            }
         }

         return false;
      }

      /** chạy việc con; null = việc con vừa xong, đi tiếp ngay trong tick này (1.14.2: trước đây mất 1 tick đứng yên) */
      private MineController.Result tickSub() {
         MineController.Result var14 = this.sub.tick();
         if (var14 == MineController.Result.RUNNING) {
            return var14;
         } else {
            String var18 = this.sub.why;
            boolean var23 = this.sub instanceof MineController.PlugTask;
            if (var14 == MineController.Result.FAILED && this.directPath && this.path != null && this.idx < this.path.size()) {
               MineController.this.avoid.add(this.path.get(this.idx).feet());
            }

            this.sub.finish();
            this.sub = null;
            if (var14 == MineController.Result.FAILED) {
               if (var23 && this.path != null && this.idx < this.path.size()) {
                  MineController.this.avoid.add(this.path.get(this.idx).feet());
               }

               this.path = null;
               if (++this.replans > 4) {
                  return this.failWith("không phá/bắc được đường: " + var18);
               }

               return MineController.Result.RUNNING;
            } else {
               return null;
            }
         }
      }

      /** bắt đầu việc con và chạy luôn tick đầu của nó */
      private MineController.Result startSub(MineController.Task var1) {
         this.sub = var1;
         MineController.Result var2 = this.tickSub();
         return var2 != null ? var2 : MineController.Result.RUNNING;
      }

      @Override
      MineController.Result tick() {
         this.age++;
         if (this.sub != null) {
            MineController.Result var40 = this.tickSub();
            if (var40 != null) {
               return var40;
            }
         }

         {
            BlockPos var3 = MineController.this.feet();
            if (this.arrived(var3)) {
               double var17 = this.goalTest != null ? 0.0 : Keys.drive(MineController.this.mc, this.goal.getX() + 0.5, this.goal.getZ() + 0.5, this.tol, true);
               if (!(var17 <= this.tol + 0.05) && !(this.tol >= 0.5)) {
                  return MineController.Result.RUNNING;
               } else {
                  Keys.releaseMove(MineController.this.mc);
                  return MineController.Result.DONE;
               }
            } else if (this.goalTest == null && this.tol >= 2.0 && Math.sqrt(var3.getSquaredDistance(this.goal)) <= this.tol) {
               Keys.releaseMove(MineController.this.mc);
               return MineController.Result.DONE;
            } else {
               if (this.path == null) {
                  if (!MineController.this.mc.player.isOnGround() && !MineController.this.mc.player.isTouchingWater()) {
                     Keys.releaseMove(MineController.this.mc);
                     return MineController.Result.RUNNING;
                  }

                  int var2 = Math.min(this.goal.getY(), var3.getY()) - 3;
                  int var4 = Math.max(this.goal.getY(), var3.getY()) + 2;
                  if (MineController.this.region != null
                     && MineController.this.layer >= 0
                     && MineController.this.region.inside(this.goal)
                     && var3.getY() >= MineController.this.region.floor(MineController.this.layer)) {
                     int var5 = MineController.this.region.floor(MineController.this.layer);
                     var2 = Math.max(var2, var5);
                     if (var3.getY() == var5 && this.goal.getY() == var5 && MineController.this.region.insideColumn(var3.getX(), var3.getZ())) {
                        var4 = var5;
                     }
                  }

                  if (this.yLo != null) {
                     var2 = this.yLo;
                  }

                  if (this.yHi != null) {
                     var4 = this.yHi;
                  }

                  PathFinder.Dig var1 = this.dig;
                  if (this.dig == null && this.allowMine && MineController.this.region != null) {
                     var1 = new PathFinder.Dig(MineController.this.region, true, var2, true);
                  }

                  // 1.14.5: không kê cột trong đường đi (đào bậc lên thay), trừ khi việc cho phép hoặc đã kẹt 3 lần liền
                  if (var1 != null && var1.pillar && (MineController.this.fillerCount() == 0 || !this.bridgeOk && !MineController.this.bridgeAllowed())) {
                     boolean var19 = var1.spill;
                     var1 = new PathFinder.Dig(var1.region, var1.outside, var1.minBreakY, false);
                     var1.spill = var19;
                  }

                  if (this.waterAccess && var1 == null) {
                     var1 = new PathFinder.Dig(MineController.this.region, true, var2, true);
                  }

                  if (!this.verticalAccess
                     && MineController.this.region != null
                     && MineController.this.layer >= 0
                     && var3.getY() == MineController.this.region.floor(MineController.this.layer)) {
                     var2 = var3.getY();
                     var4 = var3.getY();
                     if (this.goal.getY() > var4) {
                        this.goal = new BlockPos(this.goal.getX(), var4, this.goal.getZ());
                     }

                     if (var1 != null) {
                        PathFinder.Dig var20 = var1;
                        PathFinder.Dig var6 = new PathFinder.Dig(var20.region, var20.outside, var20.minBreakY, false);
                        var6.spill = var20.spill;
                        var1 = var6;
                     }
                  }

                  this.activeDig = var1;
                  if (this.activeDig != null) {
                     // 1.14.6: không đào đường xuyên qua phần hàng đang để dành (đào lẻ trong mặt 3x3 của hàng đó → đào lại lệch làn, sót dải)
                     this.activeDig.preserved = this.keepRelaxed ? MineController.this.waterDams : MineController.this.keepIntact();
                  }

                  BlockPos var21 = this.goal;
                  Predicate var24 = this.goalTest != null ? this.goalTest : var1x -> var1x.equals(var21);
                  // 1.14.2: đi trong một tầng (Y cố định) → được xuống hố nông 1 ô rồi bước lên thay vì bắc cầu
                  boolean var41 = var2 == var4 && !this.waterAccess;
                  // 1.14.5: không bắc cầu (đi vòng / xuống hố nông / nhảy / đào xuyên khối trong khung); chỉ khi việc cho phép
                  // hoặc đã làm lại vì kẹt 3 lần liền
                  boolean var42 = MineController.this.fillerCount() > 0 && (this.bridgeOk || MineController.this.bridgeAllowed());
                  PathFinder.Step var7 = this.replans < 2 && this.directRejects < 3
                     ? DirectRoute.next(
                        MineController.this.mc,
                        var3,
                        this.goal,
                        this.activeDig,
                        var42,
                        this.waterAccess,
                        MineController.this.avoid,
                        var41
                     )
                     : null;
                  if (var7 != null && var7.feet().getY() >= var2 - (var41 ? 1 : 0) && var7.feet().getY() <= var4) {
                     this.path = List.of(new PathFinder.Step(var3, false, PathFinder.Kind.WALK), var7);
                     this.directPath = true;
                     this.routeSearch = null;
                  } else {
                     this.directPath = false;
                     if (this.routeSearch == null || !this.routeSearch.anchor().equals(var3)) {
                        AutoBuildNavigation var8 = new AutoBuildNavigation(
                           MineController.this.mc,
                           var3,
                           this.goal,
                           var2,
                           var4,
                           var42,
                           this.activeDig != null && this.activeDig.pillar,
                           MineController.this.avoid
                        );
                        if (var41) {
                           var8.dipY = var2 - 1;
                        }

                        this.routeSearch = var8.new Search(var24, this.goalTest == null, this.maxNodes);
                     }

                     this.routeSearch.advance();
                     if (!this.routeSearch.done()) {
                        Keys.releaseMove(MineController.this.mc);
                        MineController.this.status = "Tìm đường đi/bắc cầu: " + this.what;
                        return MineController.Result.RUNNING;
                     }

                     this.path = this.routeSearch.result();
                     this.routeSearch = null;
                     if (this.path == null && var1 != null) {
                        this.path = PathFinder.find(
                           MineController.this.mc,
                           var3,
                           var24,
                           this.goal,
                           this.maxNodes,
                           var1,
                           var2,
                           var4,
                           MineController.this.avoid,
                           Math.min(1.0, this.weight)
                        );
                     }
                  }

                  this.idx = 1;
                  this.bestDist = Double.MAX_VALUE;
                  this.stuck = 0;
                  if (this.path == null && !this.keepRelaxed && var1 != null && var1.preserved != MineController.this.waterDams) {
                     // không có đường nào khác → được đào qua phần hàng để dành (không đứng im)
                     this.keepRelaxed = true;
                     AutoMineMod.LOGGER.info("[AutoMine] {}: không có đường tránh phần hàng để dành → cho đào qua", this.what);
                     return MineController.Result.RUNNING;
                  }

                  if (this.path == null) {
                     return this.failWith("không tìm được đường tới " + this.goal.toShortString() + (var1 != null ? " (kể cả đào)" : ""));
                  }

                  if (this.goalTest != null && !this.directPath) {
                     this.goal = this.path.getLast().feet();
                  }

                  if (var1 != null && !this.directPath) {
                     int[] var26 = new int[PathFinder.Kind.values().length];

                     for (PathFinder.Step var10 : this.path) {
                        int var11 = var10.kind().ordinal();
                        var26[var11]++;
                     }

                     AutoMineMod.LOGGER
                        .info(
                           "[AutoMine] Đường {} → {}: {} bước (đi {}, đập ngang {}, bậc lên {}, bậc xuống {}, đào xuống {}, bắc cột {})",
                           new Object[]{
                              this.what, this.goal.toShortString(), this.path.size() - 1, var26[0] - 1, var26[1], var26[2], var26[3], var26[4], var26[5]
                           }
                        );
                  }
               }

               if (!MineController.this.mc.player.isOnGround() && !MineController.this.mc.player.isTouchingWater()) {
                  if (this.airGoal != null && this.age - this.airGoalAge < 30 && Cells.standable(MineController.this.mc, this.airGoal)) {
                     Keys.jump(MineController.this.mc, false);
                     Keys.driveAir(MineController.this.mc, this.airGoal.getX() + 0.5, this.airGoal.getZ() + 0.5, 0.15, true);
                  } else {
                     Keys.releaseMove(MineController.this.mc);
                  }

                  return MineController.Result.RUNNING;
               } else {
                  this.airGoal = null;

                  for (int var13 = this.idx; var13 < Math.min(this.path.size(), this.idx + 5); var13++) {
                     BlockPos var15 = this.path.get(var13).feet();
                     if (var15.equals(var3)
                        && (
                           this.through(var13)
                              || !(
                                 Math.hypot(MineController.this.mc.player.getX() - var15.getX() - 0.5, MineController.this.mc.player.getZ() - var15.getZ() - 0.5)
                                    > 0.5
                              )
                        )
                        && Cells.floor(MineController.this.mc, var15.down())
                        && !ServerSync.pending(MineController.this.mc, var15.down())) {
                        this.idx = var13 + 1;
                        this.bestDist = Double.MAX_VALUE;
                        this.stuck = 0;
                     }
                  }

                  if (this.idx >= this.path.size()) {
                     this.path = null;
                     if (this.directPath && !this.reentered) {
                        // 1.14.2: hết bước đường thẳng → tính bước mới và đi luôn trong tick này (trước đây đứng 1 tick, lại bị ngồi ở mép hố)
                        this.reentered = true;

                        try {
                           this.age--;
                           return this.tick();
                        } finally {
                           this.reentered = false;
                        }
                     }

                     return !this.directPath && ++this.replans > 6 ? this.failWith("Đi mãi không tới") : MineController.Result.RUNNING;
                  } else {
                     PathFinder.Step var16 = this.path.get(this.idx);
                     BlockPos var22 = var16.feet();
                     if (!this.verticalAccess
                        && MineController.this.region != null
                        && MineController.this.layer >= 0
                        && var3.getY() == MineController.this.region.floor(MineController.this.layer)
                        && var22.getY() > var3.getY()) {
                        Keys.releaseMove(MineController.this.mc);
                        this.path = null;
                        this.routeSearch = null;
                        return this.failWith("Chặn đường cũ kê lên quá tầng cần đào");
                     } else if (this.directPath
                        && var16.kind() == PathFinder.Kind.WALK
                        && var22.getY() == var3.getY()
                        && var22.getSquaredDistance(var3) > 2.0
                        && !DirectRoute.clearSegment(
                           MineController.this.mc, MineController.this.mc.player.getPos(), var22, this.waterAccess, MineController.this.avoid
                        )) {
                        // 1.14.1: đường thẳng bị từ chối 3 lần liền → chuyển sang tìm đường A* (trước đây lặp mãi mỗi tick, đứng im không ghi log)
                        if (++this.directRejects == 3) {
                           AutoMineMod.LOGGER.info("[AutoMine] Đi {}: đường thẳng tới {} bị chặn 3 lần ở {} → tìm đường vòng", new Object[]{this.what, var22.toShortString(), var3.toShortString()});
                        }

                        Keys.releaseMove(MineController.this.mc);
                        this.path = null;
                        this.routeSearch = null;
                        return MineController.Result.RUNNING;
                     } else if (var16.kind() == PathFinder.Kind.JUMP) {
                        MineController.JumpTask var27 = MineController.this.new JumpTask(var3, var22);
                        // 1.14.2: sau ô đáp còn đi tiếp về phía trước (kể cả đường thẳng tới đích) → không hãm khi đáp
                        BlockPos var28 = this.idx + 1 < this.path.size() ? this.path.get(this.idx + 1).feet() : (this.directPath ? this.goal : null);
                        var27.straightAfter = var28 != null
                           && var28.getY() == var22.getY()
                           && (var28.getX() - var22.getX()) * var27.sx + (var28.getZ() - var22.getZ()) * var27.sz > 0;
                        return this.startSub(var27);
                     } else if (var16.kind() == PathFinder.Kind.BRIDGE && !Cells.floor(MineController.this.mc, var22.down())) {
                        Keys.releaseMove(MineController.this.mc);
                        BotInput.INSTANCE.sneaking = true;
                        if (MineController.this.fillerCount() == 0) {
                           return this.failWith("Hết khối bắc cầu");
                        } else if (Cells.scaffoldAt(MineController.this.mc, var22.down())
                           || this.waterAccess && WaterControl.water(MineController.this.mc, var22.down())) {
                           this.sub = MineController.this.new PlugTask(var22.down(), "bắc cầu theo đường autobuild", true);
                           return MineController.Result.RUNNING;
                        } else {
                           this.path = null;
                           return MineController.Result.RUNNING;
                        }
                     } else if (var16.kind() == PathFinder.Kind.PILLAR
                        || var16.mine()
                        || (
                              this.waterAccess
                                 ? DirectRoute.body(MineController.this.mc, var22, true) && Cells.floor(MineController.this.mc, var22.down())
                                 : Cells.standable(MineController.this.mc, var22) || Cells.wading(MineController.this.mc, var22)
                           )
                           && !ServerSync.pending(MineController.this.mc, var22.down())) {
                        if (var16.kind() == PathFinder.Kind.PILLAR) {
                           if (var3.getX() == var22.getX() && var3.getZ() == var22.getZ() && var3.getY() == var22.getY() - 1) {
                              Keys.releaseMove(MineController.this.mc);
                              this.sub = MineController.this.new PillarTask(var3);
                              return MineController.Result.RUNNING;
                           } else {
                              this.path = null;
                              return MineController.Result.RUNNING;
                           }
                        } else {
                           if (var16.mine() || this.dig != null) {
                              PathFinder.Strike var12 = PathFinder.strikeFor(MineController.this.mc, var3, var22);
                              if (var12 != null
                                 && var12 != PathFinder.INVALID
                                 && this.activeDig != null
                                 && !this.activeDig.strikeAllowed(MineController.this.mc, var12.c(), var12.f())) {
                                 var12 = PathFinder.INVALID;
                              }

                              if (var12 == PathFinder.INVALID) {
                                 this.path = null;
                                 if (++this.replans > 6) {
                                    return this.failWith("đường đào bị chặn ở " + var22.toShortString());
                                 }

                                 return MineController.Result.RUNNING;
                              }

                              if (var12 != null && MineController.this.expectedGone(var12.c())) {
                                 // 1.14.2: khối này sẽ vỡ theo nhát 3x3 vừa rồi — chờ server báo, không đập lại; vẫn bước tới (khối chặn thì đứng sát)
                                 MineController.this.lookAlongTravel(var22.getX() + 0.5, var22.getZ() + 0.5);
                                 BotInput.INSTANCE.sneaking = false;
                                 Keys.driveStep(MineController.this.mc, var22.getX() + 0.5, var22.getZ() + 0.5, 0.18, false, var22.getY());
                                 MineController.this.status = "Chờ 3x3 vỡ nốt: " + var12.c().toShortString();
                                 return MineController.Result.RUNNING;
                              }

                              if (var12 != null) {
                                 // 1.14.2: đào liền ngay trong lúc đi (giữ chuột, vỡ là đi tiếp), chỉ giao cho StrikeTask khi không đào thẳng được
                                 if (this.drill(var12, false)) {
                                    return MineController.Result.RUNNING;
                                 }

                                 Keys.releaseMove(MineController.this.mc);
                                 return this.startSub(MineController.this.new StrikeTask(var12.c(), var12.f(), "phá đường", false));
                              }

                              if (var16.kind() == PathFinder.Kind.DIGDOWN) {
                                 MineController.this.ghost.request(var3.down());
                                 Keys.releaseMove(MineController.this.mc);
                                 if (++this.stuck > 40) {
                                    this.path = null;
                                    this.stuck = 0;
                                    if (++this.replans > 6) {
                                       return this.failWith("đào xuống không rơi được");
                                    }
                                 }

                                 return MineController.Result.RUNNING;
                              }
                           }

                           // 1.14.0 (kiểu AutoBuild): nhìn trước tối đa 5 ô thẳng thông cùng độ cao, đi một mạch tới ô xa nhất,
                           // quay mặt tức thì, chạy nhanh trên đoạn dài; chỉ ngồi khi sắp DỪNG cạnh hố (không bò suốt đường).
                           int var29 = this.idx;
                           if (var16.kind() == PathFinder.Kind.WALK && !var16.mine() && var22.getY() == var3.getY()) {
                              for (int var30 = this.idx + 1; var30 < Math.min(this.path.size(), this.idx + 6); var30++) {
                                 PathFinder.Step var31 = this.path.get(var30);
                                 if (var31.kind() != PathFinder.Kind.WALK
                                    || var31.mine()
                                    || var31.feet().getY() != var3.getY()
                                    || !DirectRoute.clearSegment(
                                       MineController.this.mc, MineController.this.mc.player.getPos(), var31.feet(), this.waterAccess, MineController.this.avoid
                                    )) {
                                    break;
                                 }

                                 var29 = var30;
                              }
                           }

                           if (var29 != this.aimIdx) {
                              this.aimIdx = var29;
                              this.bestDist = Double.MAX_VALUE;
                           }

                           BlockPos var32 = this.path.get(var29).feet();
                           boolean var33 = var29 == this.path.size() - 1 && !this.through(var29);
                           // 1.14.2: ô sau ô đang tới cần đào mà đã với tới → vừa đi vừa đào luôn
                           boolean var35 = false;
                           BlockPos var37 = this.idx + 1 < this.path.size() ? this.path.get(this.idx + 1).feet() : (this.directPath ? this.towardGoal(var22) : null);
                           if (this.activeDig != null && var29 == this.idx && var37 != null && var22.getY() == var3.getY()) {
                              PathFinder.Strike var36 = PathFinder.strikeFor(MineController.this.mc, var22, var37);
                              if (var36 != null
                                 && var36 != PathFinder.INVALID
                                 && !MineController.this.expectedGone(var36.c())
                                 && this.activeDig.strikeAllowed(MineController.this.mc, var36.c(), var36.f())) {
                                 var35 = this.drill(var36, true);
                              }
                           }

                           if (!var35) {
                              MineController.this.lookAlongTravel(var32.getX() + 0.5, var32.getZ() + 0.5);
                           }

                           BotInput.INSTANCE.sneaking = false;
                           if (var22.getY() != var3.getY()) {
                              this.airGoal = var22;
                              this.airGoalAge = this.age;
                           }

                           double var25 = Keys.driveStep(
                              MineController.this.mc, var32.getX() + 0.5, var32.getZ() + 0.5, var33 ? this.tol : 0.18, var33, var32.getY()
                           );
                           if (var33 && var25 < 1.3 && var32.getY() == var3.getY() && (this.dropNear(var32) || this.dropNear(var3))) {
                              BotInput.INSTANCE.sneaking = true;
                           }

                           boolean var34 = var25 > 2.5
                              && !BotInput.INSTANCE.sneaking
                              && BotInput.INSTANCE.forward
                              && !this.waterAccess
                              && MineController.this.mc.player.getHungerManager().getFoodLevel() > 6;
                           BotInput.INSTANCE.sprinting = var34;
                           MineController.this.mc.player.setSprinting(var34);

                           if (var22.getY() > var3.getY() && var25 < 1.3 && MineController.this.mc.player.isOnGround() && this.age - this.lastJumpAge > 5) {
                              this.lastJumpAge = this.age;
                              if (var3.equals(this.jumpCell)) {
                                 this.noRise++;
                              } else {
                                 this.jumpCell = var3;
                                 this.noRise = 0;
                              }

                              if (this.noRise >= 3) {
                                 AutoMineMod.LOGGER
                                    .info(
                                       "[AutoMine] Đi {}: nhảy 3 lần ở {} không lên được bậc {} → đồng bộ ô, né bậc, tìm đường khác",
                                       new Object[]{this.what, var3.toShortString(), var22.toShortString()}
                                    );
                                 MineController.this.ghost.request(var22);
                                 MineController.this.ghost.request(var22.up());
                                 MineController.this.ghost.request(var22.down());
                                 MineController.this.ghost.request(var3.up(2));
                                 MineController.this.avoid.add(var22.toImmutable());
                                 this.path = null;
                                 this.noRise = 0;
                                 this.jumpCell = null;
                                 Keys.releaseMove(MineController.this.mc);
                                 if (++this.replans > 6) {
                                    return this.failWith("nhảy mãi không lên được bậc " + var22.toShortString());
                                 }

                                 return MineController.Result.RUNNING;
                              }

                              this.jumpHold = 2;
                           }

                           Keys.jump(MineController.this.mc, this.jumpHold-- > 0);
                           if (var25 < this.bestDist - 0.05) {
                              this.bestDist = var25;
                              this.stuck = 0;
                           } else if (this.stuck >= 24 && this.breakThrough(var3, var22)) {
                              // 1.14.5: đâm vào khối trong khung (thường là ghost vừa đồng bộ lại) → phá xuyên qua, không đi tới đi lui
                              this.stuck = 24;
                              return MineController.Result.RUNNING;
                           } else if (++this.stuck == 20) {
                              MineController.this.ghost.requestAround(var3, Direction.getFacing(var22.getX() - var3.getX(), 0.0F, var22.getZ() - var3.getZ()));
                              if (var22.getY() > var3.getY()) {
                                 this.jumpHold = 3;
                              }
                           } else if (this.stuck > 70) {
                              this.path = null;
                              if (++this.replans > 5) {
                                 return this.failWith("kẹt khi đi tới " + this.goal.toShortString());
                              }
                           }

                           return MineController.Result.RUNNING;
                        }
                     } else {
                        Keys.releaseMove(MineController.this.mc);
                        if (ServerSync.pending(MineController.this.mc, var22.down())) {
                           return MineController.Result.RUNNING;
                        } else {
                           this.path = null;
                           return MineController.Result.RUNNING;
                        }
                     }
                  }
               }
            }
         }
      }

      @Override
      void finish() {
         if (this.sub != null) {
            this.sub.finish();
         }

         super.finish();
      }
   }

   private final class WaterTask extends MineController.Task {
      MineController.Task sub;
      BlockPos target;
      boolean outside;
      boolean cleaning;
      int drySince = -1;
      int flowSince = -1;

      @Override
      String label() {
         return "Xử lý nước: " + (this.sub != null ? this.sub.label() : "tìm đường nước tràn");
      }

      @Override
      boolean exclusive() {
         return true;
      }

      @Override
      boolean walking() {
         return this.sub != null && this.sub.walking();
      }

      @Override
      MineController.Result tick() {
         this.age++;
         if (this.age > 6000) {
            MineController.this.waterRetryAt = MineController.this.ticks + 100;
            return this.failWith("Chặn nước quá lâu, sẽ khảo sát lại");
         } else if (MineController.this.mc.player.isInLava()) {
            MineController.this.waterRetryAt = MineController.this.ticks + 40;
            return this.failWith("Ưu tiên thoát lava");
         } else if (this.sub != null) {
            MineController.Result var9 = this.sub.tick();
            if (var9 == MineController.Result.RUNNING) {
               return var9;
            } else {
               this.sub.finish();
               this.sub = null;
               if (var9 == MineController.Result.FAILED) {
                  MineController.this.waterRetryAt = MineController.this.ticks + 60;
                  return this.failWith("Chưa tới được chỗ bịt nước " + this.target.toShortString());
               } else {
                  if (this.cleaning) {
                     MineController.this.waterReplacements.remove(this.target);
                  } else if (Cells.floor(MineController.this.mc, this.target) && !ServerSync.pending(MineController.this.mc, this.target)) {
                     if (this.outside) {
                        MineController.this.waterDams.add(this.target);
                        MineController.this.saveWaterDams();
                     } else {
                        MineController.this.waterReplacements.add(this.target);
                     }
                  }

                  return MineController.Result.RUNNING;
               }
            }
         } else {
            Keys.releaseMove(MineController.this.mc);
            WaterControl.Survey var1 = WaterControl.survey(MineController.this.mc, MineController.this.region, MineController.this.feet());
            this.target = var1.next();
            this.cleaning = false;
            if (this.target != null) {
               this.drySince = -1;
               this.flowSince = -1;
               this.outside = !MineController.this.region.inside(this.target);
               this.sub = MineController.this.new PlugTask(this.target, this.outside ? "bịt nước bên ngoài khung" : "thay nguồn nước bằng khối", true);
               return MineController.Result.RUNNING;
            } else if (var1.wet()) {
               this.drySince = -1;
               if (this.flowSince < 0) {
                  this.flowSince = this.age;
               }

               MineController.this.status = var1.complete() ? "Đã bịt đường vào — chờ dòng nước rút" : "Tìm tiếp đường nước ngoài vùng đang tải";
               if (var1.complete() && this.age - this.flowSince > 100 && !var1.flowing().isEmpty()) {
                  this.target = var1.flowing().getFirst();
                  this.outside = false;
                  this.sub = MineController.this.new PlugTask(this.target, "dọn nước còn đọng", true);
               }

               return MineController.Result.RUNNING;
            } else {
               if (this.drySince < 0) {
                  this.drySince = this.age;
               }

               if (this.age - this.drySince < Math.max(40, MineController.this.settleTicks() * 2)) {
                  return MineController.Result.RUNNING;
               } else {
                  for (BlockPos var3 : new ArrayList<>(MineController.this.waterReplacements)) {
                     if (!Cells.mineable(MineController.this.mc, var3)) {
                        MineController.this.waterReplacements.remove(var3);
                     } else if (var3.equals(MineController.this.feet().down())) {
                        MineController.this.waterReplacements.remove(var3);
                     } else {
                        for (Direction var7 : Direction.values()) {
                           if (Cells.drillFacePoint(MineController.this.mc, MineController.this.mc.player.getEyePos(), var3, var7, MineController.this.reach())
                              != null) {
                              this.target = var3;
                              this.cleaning = true;
                              MineController.StrikeTask var8 = MineController.this.new StrikeTask(var3, var7, "đập khối thay nguồn nước", false);
                              var8.single = true;
                              this.sub = var8;
                              return MineController.Result.RUNNING;
                           }
                        }

                        MineController.this.waterReplacements.remove(var3);
                     }
                  }

                  MineController.this.waterRetryAt = MineController.this.ticks + 40;
                  MineController.this.scanAt = -1000;
                  return MineController.Result.DONE;
               }
            }
         }
      }

      @Override
      void finish() {
         if (this.sub != null) {
            this.sub.finish();
         }

         super.finish();
      }
   }
}
