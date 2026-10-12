package com.donutautobuild.build;

import com.donutautobuild.logic.MotionMath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class PlacementSolver {
   private static final float[][] PERTURB = new float[][]{
      {3.5F, 0.0F}, {-3.5F, 0.0F}, {0.0F, 3.5F}, {0.0F, -3.5F}, {2.5F, 2.5F}, {-2.5F, -2.5F}, {2.5F, -2.5F}, {-2.5F, 2.5F}
   };
   private static final Box FULL = new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
   private static final double[][] FACE_POINTS = new double[][]{{0.5, 0.5}, {0.5, 0.25}, {0.5, 0.75}, {0.25, 0.5}, {0.75, 0.5}};
   private static final float[] SPOOF_PITCH = new float[]{-90.0F, -75.0F, -60.0F, -20.0F, 0.0F, 20.0F, 60.0F, 75.0F, 90.0F};
   private static final float[] SPOOF_YAW = new float[]{0.0F, 90.0F, 180.0F, -90.0F};
   private static final Map<BlockState, Integer> SUPPORT_MASK = new HashMap<>();
   public static final int ALL_DIRS = 63;
   public static final double SNEAK_DROP = 0.3500000000000001;

   private PlacementSolver() {
   }

   public static PlacementSolver.Aim solve(MinecraftClient var0, Vec3d var1, SchematicPlan.Target var2, double var3, boolean var5) {
      return solve(var0, var1, var2, var3, var5, var2.pos().asLong());
   }

   public static PlacementSolver.Aim solve(MinecraftClient var0, Vec3d var1, SchematicPlan.Target var2, double var3, boolean var5, long var6) {
      return solve(var0, var1, var2, var3, var5, var6, false);
   }

   private static boolean STRICT_VERT;
   private static float MIN_PITCH;

   public static boolean verticalLook(BlockState var0) {
      if (var0 != null && var0.contains(net.minecraft.state.property.Properties.FACING)) {
         Direction var1 = (Direction)var0.get(net.minecraft.state.property.Properties.FACING);
         if (var1 == Direction.UP || var1 == Direction.DOWN) {
            return var0.getBlock() instanceof net.minecraft.block.PistonBlock
               || var0.getBlock() instanceof net.minecraft.block.ObserverBlock
               || var0.getBlock() instanceof net.minecraft.block.DispenserBlock;
         }
      }

      return false;
   }

   public static PlacementSolver.Aim solve(MinecraftClient var0, Vec3d var1, SchematicPlan.Target var2, double var3, boolean var5, long var6, boolean var8) {
      if (verticalLook(var2.state())) {
         try {
            STRICT_VERT = true;
            MIN_PITCH = 60.0F;
            PlacementSolver.Aim var9 = solveCore(var0, var1, var2, var3, var5, var6, var8);
            if (var9 != null) {
               return var9;
            }

            STRICT_VERT = false;
            MIN_PITCH = 50.0F;
            return solveCore(var0, var1, var2, var3, var5, var6, var8);
         } finally {
            STRICT_VERT = false;
            MIN_PITCH = 0.0F;
         }
      } else {
         return solveCore(var0, var1, var2, var3, var5, var6, var8);
      }
   }

   private static PlacementSolver.Aim solveCore(MinecraftClient var0, Vec3d var1, SchematicPlan.Target var2, double var3, boolean var5, long var6, boolean var8) {
      if (!var0.world.isChunkLoaded(var2.pos())) {
         return null;
      } else {
         BlockState var9 = var0.world.getBlockState(var2.pos());
         boolean var10 = StateMatch.configurable(var9, var2.state());
         boolean var11 = Materials.bucket(var9, var2.state());
         Item var12 = Materials.item(var9, var2.state());
         if (!(var12 instanceof BlockItem) && !var10 && !var11) {
            return null;
         } else if (!var10 && !StateMatch.canAdd(var9, var2.state())) {
            return null;
         } else {
            ItemStack var13 = var0.player.getMainHandStack().isOf(var12) ? var0.player.getMainHandStack() : new ItemStack(var12);
            Random var14 = new Random(var6);
            boolean var15 = !var10 && Materials.topFaceOnly(var2.state());
            BlockPos var16 = StateMatch.chestPartner(var2.pos(), var2.state());
            boolean var17 = var16 != null && var0.world.getBlockState(var16).getBlock() == var2.state().getBlock();
            ArrayList<BlockPos> var18 = new ArrayList<>();
            if (!var9.isAir() && (var10 || var9.getBlock() == var2.state().getBlock())) {
               var18.add(var2.pos());
            }

            if (!var10) {
               for (Direction var22 : Direction.values()) {
                  var18.add(var2.pos().offset(var22));
               }
            }

            PlacementSolver.Aim var74 = null;
            double var75 = Double.MAX_VALUE;
            Vec3d var76 = standingEye(var0, var1);

            for (BlockPos var24 : var18) {
               BlockState var25 = var0.world.getBlockState(var24);
               VoxelShape var26 = var25.getOutlineShape(var0.world, var24);
               if (!var26.isEmpty() && (!var25.isReplaceable() || var24.equals(var2.pos()))) {
                  boolean var27 = !var10 && Interaction.interactable(var25);
                  boolean[] var28 = Materials.waterlog(var9, var2.state())
                     ? new boolean[]{false}
                     : (!var27 && !var8 ? (var10 ? new boolean[]{false} : new boolean[]{false, true}) : new boolean[]{true});

                  for (Box var30 : var26.getBoundingBoxes()) {
                     for (Direction var34 : Direction.values()) {
                        if ((var24.equals(var2.pos()) || var24.offset(var34).equals(var2.pos()))
                           && (!var15 || var34 == Direction.UP && var24.equals(var2.pos().down()))) {
                           if (STRICT_VERT && !(var34 == Direction.UP && var24.equals(var2.pos().down()) || var34 == Direction.DOWN && var24.equals(var2.pos().up()))) {
                              continue;
                           }

                           if (var11) {
                              boolean var35 = Materials.waterlog(var9, var2.state()) && var24.equals(var2.pos());
                              if (!var35 && !var24.offset(var34).equals(var2.pos())) {
                                 continue;
                              }

                              if (var2.state().isOf(Blocks.POWDER_SNOW) && (var24.getY() > var2.pos().getY() || Navigation.GHOSTS.contains(var24))) {
                                 continue;
                              }
                           }

                           for (double var38 : new double[]{0.5, 0.25, 0.75}) {
                              label184:
                              for (double var43 : new double[]{0.5, 0.25, 0.75}) {
                                 double var45 = Math.clamp(var38 + (var14.nextDouble() - 0.5) * 0.12, 0.08, 0.92);
                                 double var47 = Math.clamp(var43 + (var14.nextDouble() - 0.5) * 0.12, 0.08, 0.92);
                                 Vec3d var49 = surface(var30, var24, var34, var45, var47);
                                 double var50 = var14.nextDouble() * 0.15;
                                 boolean[] var52 = var28;
                                 int var53 = var28.length;
                                 int var54 = 0;

                                 boolean var55;
                                 double var56;
                                 BlockHitResult var58;
                                 float var59;
                                 float var60;
                                 while (true) {
                                    if (var54 >= var53) {
                                       continue label184;
                                    }

                                    var55 = var52[var54];
                                    Vec3d var61 = poseEye(var76, var55);
                                    var56 = var61.squaredDistanceTo(var49);
                                    if (!(var56 > var3 * var3) && !(var56 < 0.01)) {
                                       Vec3d var62 = var49.subtract(var61);
                                       var58 = var0.world
                                          .raycast(
                                             new RaycastContext(
                                                var61, var49.add(var62.normalize().multiply(0.005)), ShapeType.OUTLINE, FluidHandling.NONE, var0.player
                                             )
                                          );
                                       if (var58.getType() == Type.BLOCK && var58.getBlockPos().equals(var24) && var58.getSide() == var34) {
                                          var59 = MotionMath.yaw(var62.x, var62.z);
                                          var60 = MotionMath.pitch(var62.x, var62.y, var62.z);
                                          if (MIN_PITCH > 0.0F && Math.abs(var60) < MIN_PITCH) {
                                             var54++;
                                             continue;
                                          }

                                          double var63 = Math.sqrt(var56)
                                             + (double)Math.abs(MotionMath.wrap(var59 - var0.player.getYaw())) * 0.01
                                             + (double)Math.abs(var60 - var0.player.getPitch()) * 0.01
                                             + var50
                                             + (var55 != var0.player.isSneaking() ? 0.3 : 0.0);
                                          if (var63 >= var75) {
                                             var54++;
                                             continue;
                                          }

                                          if (var11 || var10) {
                                             break;
                                          }

                                          BlockItem var65 = (BlockItem)var12;
                                          ItemStack var66 = var13;
                                          if (predicts(var0, var65, var13, var58, var59, var60, var2, var5, var17, var55)) {
                                             boolean var67 = true;

                                             for (float[] var71 : PERTURB) {
                                                float var72 = var59 + var71[0];
                                                float var73 = Math.clamp(var60 + var71[1], -90.0F, 90.0F);
                                                if (!predicts(var0, var65, var66, var58, var72, var73, var2, true, var17, var55)) {
                                                   var67 = false;
                                                   break;
                                                }
                                             }

                                             if (var67) {
                                                break;
                                             }
                                          }
                                       }
                                    }

                                    var54++;
                                 }

                                 double var78 = Math.sqrt(var56)
                                    + (double)Math.abs(MotionMath.wrap(var59 - var0.player.getYaw())) * 0.01
                                    + (double)Math.abs(var60 - var0.player.getPitch()) * 0.01
                                    + var50;
                                 if (var55 != var0.player.isSneaking()) {
                                    var78 += 0.3;
                                 }

                                 if (var78 < var75) {
                                    var75 = var78;
                                    var74 = new PlacementSolver.Aim(var58, var59, var60, var10, var55);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            return var74;
         }
      }
   }

   public static PlacementSolver.SpoofAim solveSpoof(MinecraftClient var0, Vec3d var1, SchematicPlan.Target var2, double var3, long var5, float[] var7) {
      if (!var0.world.isChunkLoaded(var2.pos())) {
         return null;
      } else {
         BlockState var8 = var0.world.getBlockState(var2.pos());
         if (!StateMatch.configurable(var8, var2.state()) && !Materials.bucket(var8, var2.state())) {
            if (Materials.item(var8, var2.state()) instanceof BlockItem var9 && StateMatch.canAdd(var8, var2.state())) {
               ItemStack var57 = var0.player.getMainHandStack().isOf(var9) ? var0.player.getMainHandStack() : new ItemStack(var9);
               boolean var11 = Materials.topFaceOnly(var2.state());
               BlockPos var12 = StateMatch.chestPartner(var2.pos(), var2.state());
               boolean var13 = var12 != null && var0.world.getBlockState(var12).getBlock() == var2.state().getBlock();
               Vec3d var14 = standingEye(var0, var1);
               PlacementSolver.SpoofAim var15 = null;
               double var16 = Double.MAX_VALUE;

               for (Direction var21 : Direction.values()) {
                  BlockPos var22 = var2.pos().offset(var21);
                  BlockState var23 = var0.world.getBlockState(var22);
                  VoxelShape var24 = var23.getOutlineShape(var0.world, var22);
                  if (!var24.isEmpty() && !var23.isReplaceable()) {
                     Direction var25 = var21.getOpposite();
                     if (!var11 || var25 == Direction.UP) {
                        boolean var26 = Interaction.interactable(var23);
                        boolean[] var27 = var26 ? new boolean[]{true} : new boolean[]{var0.player.isSneaking(), !var0.player.isSneaking()};

                        for (boolean var31 : var27) {
                           Vec3d var32 = poseEye(var14, var31);

                           for (Box var34 : var24.getBoundingBoxes()) {
                              for (double[] var38 : FACE_POINTS) {
                                 Vec3d var39 = surface(var34, var22, var25, var38[0], var38[1]);
                                 double var40 = var32.squaredDistanceTo(var39);
                                 if (!(var40 > var3 * var3) && !(var40 < 0.01)) {
                                    Vec3d var42 = var39.subtract(var32);
                                    BlockHitResult var43 = var0.world
                                       .raycast(
                                          new RaycastContext(
                                             var32, var39.add(var42.normalize().multiply(0.005)), ShapeType.OUTLINE, FluidHandling.NONE, var0.player
                                          )
                                       );
                                    if (var43.getType() == Type.BLOCK && var43.getBlockPos().equals(var22) && var43.getSide() == var25) {
                                       float var44 = MotionMath.yaw(var42.x, var42.z);
                                       float var45 = MotionMath.pitch(var42.x, var42.y, var42.z);
                                       float[] var46 = new float[SPOOF_YAW.length + 1];
                                       var46[0] = var44;
                                       System.arraycopy(SPOOF_YAW, 0, var46, 1, SPOOF_YAW.length);

                                       for (float var50 : var46) {
                                          for (float var54 : SPOOF_PITCH) {
                                             if (predictsFor(var0, var9, var57, var43, var50, var54, var2, var13, var31, var7, var50 == var44)) {
                                                double var55 = Math.sqrt(var40)
                                                   + (double)Math.abs(var54 - var45) * 0.01
                                                   + (double)(var50 == var44 ? 0 : 1)
                                                   + (var31 == var0.player.isSneaking() ? 0.0 : 0.4);
                                                if (var55 < var16) {
                                                   var16 = var55;
                                                   var15 = new PlacementSolver.SpoofAim(
                                                      new PlacementSolver.Aim(var43, var44, var45, false, var31), var50, var54
                                                   );
                                                }
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
                  }
               }

               return var15;
            }

            return null;
         } else {
            return null;
         }
      }
   }

   private static boolean predictsFor(
      MinecraftClient var0,
      BlockItem var1,
      ItemStack var2,
      BlockHitResult var3,
      float var4,
      float var5,
      SchematicPlan.Target var6,
      boolean var7,
      boolean var8,
      float[] var9,
      boolean var10
   ) {
      float[] var11;
      if (var10 && var9 != null) {
         var11 = new float[var9.length + 1];
         var11[0] = var4;
         System.arraycopy(var9, 0, var11, 1, var9.length);
      } else {
         var11 = new float[]{var4};
      }

      for (float var15 : var11) {
         ItemPlacementContext var16 = var1.getPlacementContext(new PlacementSolver.ProjectedContext(var0, var2, var3, var4, var15, var5, var8));
         if (var16 == null || !var16.getBlockPos().equals(var6.pos()) || !var16.canPlace()) {
            return false;
         }

         BlockState var17 = PlacementAccess.state(var1, var16);
         if (var17 == null) {
            var17 = var1.getBlock().getPlacementState(var16);
         }

         if (var7 ? !StateMatch.matches(var17, var6.state()) : !StateMatch.placementMatches(var17, var6.state())) {
            return false;
         }
      }

      return true;
   }

   public static boolean spoofValidNow(MinecraftClient var0, PlacementSolver.SpoofAim var1, SchematicPlan.Target var2, double var3) {
      BlockHitResult var5 = ray(var0, var3);
      if (var5.getType() == Type.BLOCK && var5.getBlockPos().equals(var1.aim().hit().getBlockPos()) && var5.getSide() == var1.aim().hit().getSide()) {
         if (!(var0.player.getMainHandStack().getItem() instanceof BlockItem var6)) {
            return false;
         } else {
            ItemPlacementContext var8 = var6.getPlacementContext(new ItemPlacementContext(var0.player, Hand.MAIN_HAND, var0.player.getMainHandStack(), var5));
            return var8 != null && var8.canPlace() && var8.getBlockPos().equals(var2.pos());
         }
      } else {
         return false;
      }
   }

   private static boolean predicts(
      MinecraftClient var0,
      BlockItem var1,
      ItemStack var2,
      BlockHitResult var3,
      float var4,
      float var5,
      SchematicPlan.Target var6,
      boolean var7,
      boolean var8,
      boolean var9
   ) {
      ItemPlacementContext var10 = var7
         ? new PlacementSolver.ProjectedContext(var0, var2, var3, var4, var4, var5, var9)
         : new ItemPlacementContext(var0.player, Hand.MAIN_HAND, var2, var3);
      var10 = var1.getPlacementContext(var10);
      if (var10 != null && var10.getBlockPos().equals(var6.pos()) && var10.canPlace()) {
         BlockState var11 = PlacementAccess.state(var1, (ItemPlacementContext)var10);
         if (var11 == null && var7) {
            var11 = var1.getBlock().getPlacementState((ItemPlacementContext)var10);
         }

         return var8 ? StateMatch.matches(var11, var6.state()) : StateMatch.placementMatches(var11, var6.state());
      } else {
         return false;
      }
   }

   public static boolean faces(float var0, float var1, Direction var2) {
      double var3 = (double)var1 * (Math.PI / 180.0);
      double var5 = (double)(-var0) * (Math.PI / 180.0);
      double var7 = Math.sin(var5) * Math.cos(var3);
      double var9 = -Math.sin(var3);
      double var11 = Math.cos(var5) * Math.cos(var3);
      double var13 = var2 == Direction.UP ? 1.0 : (var2 == Direction.DOWN ? -1.0 : 0.0);
      return var7 * (double)var2.getOffsetX() + var9 * var13 + var11 * (double)var2.getOffsetZ() < -0.05;
   }

   public static boolean placeableSomewhere(MinecraftClient var0, SchematicPlan.Target var1) {
      return placeableSomewhere(var0, var1, false);
   }

   public static boolean placeableSomewhere(MinecraftClient var0, SchematicPlan.Target var1, boolean var2) {
      if (!var0.world.isChunkLoaded(var1.pos())) {
         return true;
      } else {
         BlockState var3 = var0.world.getBlockState(var1.pos());
         if (!StateMatch.configurable(var3, var1.state()) && !Materials.bucket(var3, var1.state())) {
            if (Materials.item(var3, var1.state()) instanceof BlockItem var4 && StateMatch.canAdd(var3, var1.state())) {
               boolean var29 = Materials.topFaceOnly(var1.state());
               BlockPos var6 = StateMatch.chestPartner(var1.pos(), var1.state());
               boolean var7 = var6 != null && var0.world.getBlockState(var6).getBlock() == var1.state().getBlock();
               ItemStack var8 = new ItemStack(var4);
               ArrayList<BlockPos> var9 = new ArrayList<>();
               if (!var3.isAir() && var3.getBlock() == var1.state().getBlock()) {
                  var9.add(var1.pos());
               }

               for (Direction var13 : Direction.values()) {
                  var9.add(var1.pos().offset(var13));
               }

               for (BlockPos var31 : var9) {
                  BlockState var32 = var0.world.getBlockState(var31);
                  if (!var32.getOutlineShape(var0.world, var31).isEmpty() && (!var32.isReplaceable() || var31.equals(var1.pos()))) {
                     for (Direction var16 : Direction.values()) {
                        if ((var31.equals(var1.pos()) || var31.offset(var16).equals(var1.pos()))
                           && (!var29 || var16 == Direction.UP && var31.equals(var1.pos().down()))) {
                           for (double[] var20 : FACE_POINTS) {
                              Vec3d var21 = surface(FULL, var31, var16, var20[0], var20[1]);
                              BlockHitResult var22 = new BlockHitResult(var21, var16, var31, false);
                              float[] var23 = var2
                                 ? new float[]{-80.0F, -70.0F, -45.0F, -20.0F, -5.0F, 5.0F, 20.0F, 45.0F, 70.0F, 80.0F}
                                 : new float[]{-70.0F, -20.0F, 20.0F, 70.0F};

                              for (float var24 = -180.0F; var24 < 180.0F; var24 += 45.0F) {
                                 for (float var28 : var23) {
                                    if ((!var2 || faces(var24, var28, var16))
                                       && (
                                          predicts(var0, var4, var8, var22, var24, var28, var1, true, var7, false)
                                             || predicts(var0, var4, var8, var22, var24, var28, var1, true, var7, true)
                                       )) {
                                       return true;
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               return false;
            }

            return false;
         } else {
            return true;
         }
      }
   }

   public static int supportMask(MinecraftClient var0, SchematicPlan.Target var1) {
      Integer var2 = SUPPORT_MASK.get(var1.state());
      if (var2 != null) {
         return var2;
      } else {
         BlockState var3 = Blocks.AIR.getDefaultState();
         if (!Materials.bucket(var3, var1.state()) && Materials.item(var3, var1.state()) instanceof BlockItem var4) {
            boolean var22 = Materials.topFaceOnly(var1.state());
            ItemStack var6 = new ItemStack(var4);
            int var7 = 0;

            for (Direction var11 : Direction.values()) {
               if (!var22 || var11 == Direction.DOWN) {
                  BlockPos var12 = var1.pos().offset(var11);
                  Direction var13 = var11.getOpposite();
                  boolean var14 = false;

                  for (int var15 = 0; var15 < FACE_POINTS.length && !var14; var15++) {
                     BlockHitResult var16 = new BlockHitResult(surface(FULL, var12, var13, FACE_POINTS[var15][0], FACE_POINTS[var15][1]), var13, var12, false);

                     for (float var17 = -180.0F; var17 < 180.0F && !var14; var17 += 45.0F) {
                        for (float var21 : new float[]{-70.0F, -20.0F, 20.0F, 70.0F}) {
                           if (predictsHyp(var0, var4, var6, var16, var17, var21, var1, false)
                              || predictsHyp(var0, var4, var6, var16, var17, var21, var1, true)) {
                              var14 = true;
                              break;
                           }
                        }
                     }
                  }

                  if (var14) {
                     var7 |= 1 << var11.ordinal();
                  }
               }
            }

            if (var7 == 0) {
               var7 = 63;
            }

            SUPPORT_MASK.put(var1.state(), var7);
            return var7;
         } else {
            return 63;
         }
      }
   }

   private static boolean predictsHyp(
      MinecraftClient var0, BlockItem var1, ItemStack var2, BlockHitResult var3, float var4, float var5, SchematicPlan.Target var6, boolean var7
   ) {
      ItemPlacementContext var8 = var1.getPlacementContext(new PlacementSolver.ProjectedContext(var0, var2, var3, var4, var4, var5, var7).hypothetical());
      if (var8 != null && var8.getBlockPos().equals(var6.pos())) {
         BlockState var9;
         try {
            var9 = var1.getBlock().getPlacementState(var8);
         } catch (RuntimeException var11) {
            return false;
         }

         return var9 != null && StateMatch.placementMatches(var9, var6.state());
      } else {
         return false;
      }
   }

   public static boolean robust(MinecraftClient var0, Vec3d var1, PlacementSolver.Aim var2, double var3) {
      Vec3d var5 = var2.hit().getPos();

      for (double[] var9 : new double[][]{{0.15, 0.0}, {-0.15, 0.0}, {0.0, 0.15}, {0.0, -0.15}}) {
         Vec3d var10 = var1.add(var9[0], 0.0, var9[1]);
         if (var10.squaredDistanceTo(var5) > var3 * var3) {
            return false;
         }

         Vec3d var11 = var5.subtract(var10).normalize();
         BlockHitResult var12 = var0.world
            .raycast(new RaycastContext(var10, var5.add(var11.multiply(0.01)), ShapeType.OUTLINE, FluidHandling.NONE, var0.player));
         if (var12.getType() != Type.BLOCK || !var12.getBlockPos().equals(var2.hit().getBlockPos()) || var12.getSide() != var2.hit().getSide()) {
            return false;
         }
      }

      return true;
   }

   public static Vec3d standingEye(MinecraftClient var0, Vec3d var1) {
      return var0.player.isInSneakingPose() && var1.equals(var0.player.getEyePos()) ? var1.add(0.0, 0.3500000000000001, 0.0) : var1;
   }

   public static Vec3d poseEye(Vec3d var0, boolean var1) {
      return var1 ? var0.subtract(0.0, 0.3500000000000001, 0.0) : var0;
   }

   public static Vec3d eyeFor(MinecraftClient var0, boolean var1) {
      return poseEye(standingEye(var0, var0.player.getEyePos()), var1);
   }

   public static boolean needsLookUp(MinecraftClient var0, SchematicPlan.Target var1) {
      BlockState var2 = var0.world.getBlockState(var1.pos());
      if (Materials.item(var2, var1.state()) instanceof BlockItem var3 && !StateMatch.configurable(var2, var1.state())) {
         ItemStack var23 = new ItemStack(var3);
         boolean var5 = false;

         for (Direction var9 : Direction.values()) {
            BlockPos var10 = var1.pos().offset(var9);
            Direction var11 = var9.getOpposite();

            for (double[] var15 : FACE_POINTS) {
               BlockHitResult var16 = new BlockHitResult(surface(FULL, var10, var11, var15[0], var15[1]), var11, var10, false);

               for (float var17 = -180.0F; var17 < 180.0F; var17 += 45.0F) {
                  for (float var21 : new float[]{-70.0F, -30.0F, 0.0F, 30.0F, 70.0F}) {
                     boolean var22 = predicts(var0, var3, var23, var16, var17, var21, var1, true, false, false)
                        || predicts(var0, var3, var23, var16, var17, var21, var1, true, false, true);
                     if (var22) {
                        if (var21 >= 0.0F) {
                           return false;
                        }

                        var5 = true;
                     }
                  }
               }
            }
         }

         return var5;
      }

      return false;
   }

   public static boolean stillReachable(MinecraftClient var0, PlacementSolver.Aim var1, double var2) {
      Vec3d var4 = eyeFor(var0, var1.sneak());
      Vec3d var5 = var1.hit().getPos();
      if (var4.squaredDistanceTo(var5) > var2 * var2) {
         return false;
      } else {
         Vec3d var6 = var5.subtract(var4);
         BlockHitResult var7 = var0.world
            .raycast(new RaycastContext(var4, var5.add(var6.normalize().multiply(0.005)), ShapeType.OUTLINE, FluidHandling.NONE, var0.player));
         return var7.getType() == Type.BLOCK && var7.getBlockPos().equals(var1.hit().getBlockPos()) && var7.getSide() == var1.hit().getSide();
      }
   }

   public static float aimYaw(MinecraftClient var0, PlacementSolver.Aim var1) {
      Vec3d var2 = var1.hit().getPos().subtract(eyeFor(var0, var1.sneak()));
      return MotionMath.yaw(var2.x, var2.z);
   }

   public static float aimPitch(MinecraftClient var0, PlacementSolver.Aim var1) {
      Vec3d var2 = var1.hit().getPos().subtract(eyeFor(var0, var1.sneak()));
      return MotionMath.pitch(var2.x, var2.y, var2.z);
   }

   public static BlockHitResult ray(MinecraftClient var0, double var1) {
      Vec3d var3 = var0.player.getEyePos();
      return var0.world
         .raycast(new RaycastContext(var3, var3.add(var0.player.getRotationVec(1.0F).multiply(var1)), ShapeType.OUTLINE, FluidHandling.NONE, var0.player));
   }

   public static boolean validNow(MinecraftClient var0, PlacementSolver.Aim var1, SchematicPlan.Target var2, double var3) {
      BlockHitResult var5 = ray(var0, var3);
      if (var5.getType() != Type.BLOCK || !var5.getBlockPos().equals(var1.hit().getBlockPos()) || var5.getSide() != var1.hit().getSide()) {
         return false;
      } else if (var0.player.getEyePos().squaredDistanceTo(var5.getPos()) > var3 * var3) {
         return false;
      } else if (var1.configure()) {
         return StateMatch.configurable(var0.world.getBlockState(var2.pos()), var2.state());
      } else {
         BlockState var6 = var0.world.getBlockState(var2.pos());
         if (!Materials.bucket(var6, var2.state())) {
            if (!(var0.player.getMainHandStack().getItem() instanceof BlockItem var7)) {
               return false;
            } else {
               ItemPlacementContext var11 = var7.getPlacementContext(
                  new ItemPlacementContext(var0.player, Hand.MAIN_HAND, var0.player.getMainHandStack(), var5)
               );
               if (var11 != null && var11.canPlace() && var11.getBlockPos().equals(var2.pos())) {
                  BlockState var9 = PlacementAccess.state(var7, var11);
                  BlockPos var10 = StateMatch.chestPartner(var2.pos(), var2.state());
                  return var10 != null && var0.world.getBlockState(var10).getBlock() == var2.state().getBlock()
                     ? StateMatch.matches(var9, var2.state())
                     : StateMatch.placementMatches(var9, var2.state());
               } else {
                  return false;
               }
            }
         } else if (!var0.player.getMainHandStack().isOf(Materials.item(var6, var2.state()))) {
            return false;
         } else {
            return var0.player.getBoundingBox().intersects(new Box(var2.pos()))
               ? false
               : Materials.waterlog(var6, var2.state()) && var5.getBlockPos().equals(var2.pos())
                  || var6.isReplaceable() && var5.getBlockPos().offset(var5.getSide()).equals(var2.pos());
         }
      }
   }

   public static boolean serverSafe(MinecraftClient var0, PlacementSolver.Aim var1, SchematicPlan.Target var2, double var3, float[] var5) {
      if (var1.configure()) {
         return true;
      } else {
         BlockState var6 = var0.world.getBlockState(var2.pos());
         if (Materials.bucket(var6, var2.state())) {
            return true;
         } else if (var0.player.getMainHandStack().getItem() instanceof BlockItem var7) {
            BlockHitResult var19 = ray(var0, var3);
            if (var19.getType() != Type.BLOCK) {
               return false;
            } else {
               BlockPos var9 = StateMatch.chestPartner(var2.pos(), var2.state());
               boolean var10 = var9 != null && var0.world.getBlockState(var9).getBlock() == var2.state().getBlock();
               float var11 = var0.player.getYaw();
               float var12 = var0.player.getPitch();

               for (float var16 : var5) {
                  ItemPlacementContext var17 = var7.getPlacementContext(
                     new PlacementSolver.ProjectedContext(var0, var0.player.getMainHandStack(), var19, var11, var16, var12, var1.sneak())
                  );
                  if (var17 == null || !var17.getBlockPos().equals(var2.pos())) {
                     return false;
                  }

                  BlockState var18 = PlacementAccess.state(var7, var17);
                  if (var18 == null) {
                     var18 = var7.getBlock().getPlacementState(var17);
                  }

                  if (var10 ? !StateMatch.matches(var18, var2.state()) : !StateMatch.placementMatches(var18, var2.state())) {
                     return false;
                  }
               }

               return true;
            }
         } else {
            return false;
         }
      }
   }

   private static Vec3d surface(Box var0, BlockPos var1, Direction var2, double var3, double var5) {
      double var7 = var0.minX + (var0.maxX - var0.minX) * var3;
      double var9 = var0.minY + (var0.maxY - var0.minY) * var5;
      double var11 = var0.minZ + (var0.maxZ - var0.minZ) * var5;
      switch (var2) {
         case UP:
            var9 = var0.maxY;
            break;
         case DOWN:
            var9 = var0.minY;
            break;
         case NORTH:
            var11 = var0.minZ;
            break;
         case SOUTH:
            var11 = var0.maxZ;
            break;
         case WEST:
            var7 = var0.minX;
            var11 = var0.minZ + (var0.maxZ - var0.minZ) * var3;
            break;
         case EAST:
            var7 = var0.maxX;
            var11 = var0.minZ + (var0.maxZ - var0.minZ) * var3;
      }

      return new Vec3d((double)var1.getX() + var7, (double)var1.getY() + var9, (double)var1.getZ() + var11);
   }

   public static Direction[] entityFacingOrder(float var0, float var1) {
      float var2 = var1 * (float) (Math.PI / 180.0);
      float var3 = -var0 * (float) (Math.PI / 180.0);
      float var4 = MathHelper.sin(var2);
      float var5 = MathHelper.cos(var2);
      float var6 = MathHelper.sin(var3);
      float var7 = MathHelper.cos(var3);
      boolean var8 = var6 > 0.0F;
      boolean var9 = var4 < 0.0F;
      boolean var10 = var7 > 0.0F;
      float var11 = var8 ? var6 : -var6;
      float var12 = var9 ? -var4 : var4;
      float var13 = var10 ? var7 : -var7;
      float var14 = var11 * var5;
      float var15 = var13 * var5;
      Direction var16 = var8 ? Direction.EAST : Direction.WEST;
      Direction var17 = var9 ? Direction.UP : Direction.DOWN;
      Direction var18 = var10 ? Direction.SOUTH : Direction.NORTH;
      if (var11 > var13) {
         if (var12 > var14) {
            return closest(var17, var16, var18);
         } else {
            return var15 > var12 ? closest(var16, var18, var17) : closest(var16, var17, var18);
         }
      } else if (var12 > var15) {
         return closest(var17, var18, var16);
      } else {
         return var14 > var12 ? closest(var18, var16, var17) : closest(var18, var17, var16);
      }
   }

   private static Direction[] closest(Direction var0, Direction var1, Direction var2) {
      return new Direction[]{var0, var1, var2, var2.getOpposite(), var1.getOpposite(), var0.getOpposite()};
   }

   public static Direction[] projectedDirections(float var0, float var1) {
      return entityFacingOrder(var0, var1);
   }

   public static record Aim(BlockHitResult hit, float yaw, float pitch, boolean configure, boolean sneak) {
   }

   private static final class ProjectedContext extends ItemPlacementContext {
      private final float yaw;
      private final float headYaw;
      private final float pitch;
      private final boolean sneak;

      ProjectedContext(MinecraftClient var1, ItemStack var2, BlockHitResult var3, float var4, float var5, float var6, boolean var7) {
         super(var1.player, Hand.MAIN_HAND, var2, var3);
         this.yaw = var4;
         this.headYaw = var5;
         this.pitch = var6;
         this.sneak = var7;
      }

      PlacementSolver.ProjectedContext hypothetical() {
         this.canReplaceExisting = false;
         return this;
      }

      public float getPlayerYaw() {
         return this.yaw;
      }

      public boolean shouldCancelInteraction() {
         return this.sneak;
      }

      public Direction getHorizontalPlayerFacing() {
         return Direction.fromHorizontalDegrees((double)this.yaw);
      }

      public Direction getPlayerLookDirection() {
         return PlacementSolver.entityFacingOrder(this.headYaw, this.pitch)[0];
      }

      public Direction getVerticalPlayerLookDirection() {
         return this.pitch < 0.0F ? Direction.UP : Direction.DOWN;
      }

      public Direction[] getPlacementDirections() {
         Direction[] var1 = PlacementSolver.entityFacingOrder(this.headYaw, this.pitch);
         if (this.canReplaceExisting()) {
            return var1;
         } else {
            Direction var2 = this.getSide().getOpposite();
            int var3 = 0;

            while (var3 < var1.length && var1[var3] != var2) {
               var3++;
            }

            if (var3 > 0) {
               System.arraycopy(var1, 0, var1, 1, var3);
               var1[0] = var2;
            }

            return var1;
         }
      }
   }

   public static record SpoofAim(PlacementSolver.Aim aim, float sendYaw, float sendPitch) {
   }
}
