package com.donutautobuild.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.block.ObserverBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.RedstoneBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.WallHangingSignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

public final class Materials {
   private static final Set<Block> NOT_DECORATIVE = Set.of(
      Blocks.SLIME_BLOCK,
      Blocks.HONEY_BLOCK,
      Blocks.REDSTONE_LAMP,
      Blocks.NOTE_BLOCK,
      Blocks.TARGET,
      Blocks.TNT,
      Blocks.MAGMA_BLOCK,
      Blocks.BUDDING_AMETHYST,
      Blocks.SPONGE,
      Blocks.WET_SPONGE,
      Blocks.OBSIDIAN,
      Blocks.CRYING_OBSIDIAN,
      Blocks.RESPAWN_ANCHOR,
      Blocks.REINFORCED_DEEPSLATE,
      Blocks.REDSTONE_ORE,
      Blocks.DEEPSLATE_REDSTONE_ORE,
      Blocks.INFESTED_STONE,
      Blocks.SCULK_SENSOR,
      Blocks.ICE,
      Blocks.PACKED_ICE,
      Blocks.BLUE_ICE,
      Blocks.FROSTED_ICE,
      Blocks.SNOW_BLOCK,
      Blocks.SOUL_SAND,
      Blocks.SOUL_SOIL,
      Blocks.MUD,
      Blocks.GLOWSTONE,
      Blocks.SEA_LANTERN,
      Blocks.SHROOMLIGHT,
      Blocks.JACK_O_LANTERN,
      Blocks.OCHRE_FROGLIGHT,
      Blocks.VERDANT_FROGLIGHT,
      Blocks.PEARLESCENT_FROGLIGHT,
      Blocks.BEDROCK
   );
   private static Map<String, List<Block>> families;

   private Materials() {
   }

   public static boolean source(BlockState var0) {
      return (var0.isOf(Blocks.WATER) || var0.isOf(Blocks.LAVA)) && (Integer)var0.get(Properties.LEVEL_15) == 0;
   }

   public static boolean flowing(BlockState var0) {
      return (var0.isOf(Blocks.WATER) || var0.isOf(Blocks.LAVA)) && !source(var0);
   }

   public static boolean waterlog(BlockState var0, BlockState var1) {
      return var0.getBlock() == var1.getBlock()
         && var1.contains(Properties.WATERLOGGED)
         && (Boolean)var1.get(Properties.WATERLOGGED)
         && !(Boolean)var0.get(Properties.WATERLOGGED);
   }

   public static Item item(BlockState var0, BlockState var1) {
      if (var1.isOf(Blocks.WATER) || waterlog(var0, var1)) {
         return Items.WATER_BUCKET;
      } else if (var1.isOf(Blocks.POWDER_SNOW)) {
         return Items.POWDER_SNOW_BUCKET;
      } else {
         return var1.isOf(Blocks.LAVA) ? Items.LAVA_BUCKET : var1.getBlock().asItem();
      }
   }

   public static boolean supported(BlockState var0, BlockState var1) {
      return var1.getBlock().asItem() instanceof BlockItem || source(var1) || var1.isOf(Blocks.POWDER_SNOW);
   }

   public static boolean bucket(BlockState var0, BlockState var1) {
      return source(var1) || waterlog(var0, var1) || var1.isOf(Blocks.POWDER_SNOW);
   }

   public static boolean pourable(BlockState var0, BlockState var1) {
      return source(var1) || waterlog(var0, var1);
   }

   public static boolean decorative(BlockState var0) {
      if (var0 != null && !var0.isAir() && var0.getFluidState().isEmpty()) {
         Block var1 = var0.getBlock();
         if (var1.getSlipperiness() != 0.6F || var1.getVelocityMultiplier() != 1.0F || var1.getJumpVelocityMultiplier() != 1.0F || var0.getLuminance() > 0) {
            return false;
         } else if (NOT_DECORATIVE.contains(var1)
            || var1 instanceof FallingBlock
            || var1 instanceof RedstoneBlock
            || var1 instanceof ObserverBlock
            || var1 instanceof PistonBlock
            || var1 instanceof DispenserBlock) {
            return false;
         } else if (!var0.hasBlockEntity() && !var0.emitsRedstonePower() && var0.getPistonBehavior() == PistonBehavior.NORMAL) {
            return var0.getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) < 0.0F
               ? false
               : var0.isOpaqueFullCube()
                  && var0.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)
                  && var0.isSolidBlock(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static int blastClass(BlockState var0) {
      float var1 = var0.getBlock().getBlastResistance();
      return var1 >= 1000.0F ? 3 : (var1 >= 100.0F ? 2 : (var1 >= 4.0F ? 1 : 0));
   }

   private static int toolClass(BlockState var0) {
      return var0.isToolRequired() ? 1 : 0;
   }

   public static boolean swappable(BlockState var0, BlockState var1) {
      if (var0 != null && var1 != null) {
         return var0.getBlock() == var1.getBlock()
            ? true
            : decorative(var0) && decorative(var1) && blastClass(var1) >= blastClass(var0) && toolClass(var0) == toolClass(var1);
      } else {
         return false;
      }
   }

   public static String family(Block var0) {
      String var1 = Registries.BLOCK.getId(var0).getPath();
      if (var0 instanceof WallHangingSignBlock) {
         return "wall_hanging_sign";
      } else if (var0 instanceof HangingSignBlock) {
         return "hanging_sign";
      } else if (var0 instanceof WallSignBlock) {
         return "wall_sign";
      } else if (var0 instanceof SignBlock) {
         return "sign";
      } else if (var0 instanceof ButtonBlock) {
         return !var1.equals("stone_button") && !var1.equals("polished_blackstone_button") ? "button_wood" : "button_stone";
      } else if (var0 instanceof PressurePlateBlock) {
         return !var1.equals("stone_pressure_plate") && !var1.equals("polished_blackstone_pressure_plate") ? "plate_wood" : "plate_stone";
      } else if (var0 instanceof TrapdoorBlock) {
         return !var1.contains("iron") && !var1.contains("copper") ? "trapdoor_wood" : null;
      } else if (!(var0 instanceof DoorBlock)) {
         if (var0 instanceof FenceGateBlock) {
            return "fence_gate";
         } else if (var0 instanceof FenceBlock) {
            return var1.equals("nether_brick_fence") ? null : "fence_wood";
         } else if (var0 instanceof SlabBlock) {
            return "slab";
         } else if (var0 instanceof StairsBlock) {
            return "stairs";
         } else if (var0 instanceof WallBlock) {
            return "wall";
         } else if (var0 instanceof CarpetBlock && var1.endsWith("_carpet") && !var1.contains("moss")) {
            return "carpet";
         } else if (!var1.equals("glass") && !var1.endsWith("_stained_glass")) {
            return !var1.equals("glass_pane") && !var1.endsWith("_stained_glass_pane") ? null : "glass_pane";
         } else {
            return "glass";
         }
      } else {
         return !var1.contains("iron") && !var1.contains("copper") ? "door_wood" : null;
      }
   }

   public static List<Block> members(String var0) {
      if (families == null) {
         HashMap<String, List<Block>> var1 = new HashMap<>();

         for (Block var3 : Registries.BLOCK) {
            String var4 = family(var3);
            if (var4 != null && var3.asItem() != Items.AIR) {
               var1.computeIfAbsent(var4, var0x -> new ArrayList<>()).add(var3);
            }
         }

         families = var1;
      }

      return families.getOrDefault(var0, List.of());
   }

   public static BlockState copyProperties(BlockState var0, Block var1) {
      BlockState var2 = var1.getDefaultState();

      for (Property var4 : var0.getProperties()) {
         if (var2.contains(var4)) {
            var2 = (BlockState)var2.with(var4, var0.get(var4));
         }
      }

      return var2;
   }

   public static boolean topFaceOnly(BlockState var0) {
      Block var1 = var0.getBlock();
      return var1 instanceof AbstractRedstoneGateBlock
         || var1 instanceof RedstoneWireBlock
         || var1 instanceof AbstractRailBlock
         || var1 instanceof AbstractPressurePlateBlock
         || var1 instanceof CarpetBlock;
   }
}
