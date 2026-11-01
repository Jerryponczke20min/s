package com.example.fakebonemeal;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sadzi trawe i kwiatki TYLKO W KLIENCIE. Serwer o niczym nie wie, wiec dziala na kazdym serwerze.
 * Rosliny istnieja tylko w Twojej kopii swiata - gdy je zniszczysz (albo serwer odswiezy chunk), znikaja.
 */
public final class FakeBloom {

    private static int clickIndex = 0;

    private FakeBloom() {
    }

    public static void resetIndex() {
        clickIndex = 0;
    }

    /**
     * Wywolywane z mixina zanim klient wysle pakiet do serwera.
     * Zwraca true, gdy kliknięcie zostalo obsluzone lokalnie (pakiet NIE zostanie wyslany).
     */
    public static boolean tryUse(ClientPlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!ClientSettings.enabled || player.isSneaking() || player.isSpectator()) {
            return false; // Shift = normalne uzycie itemu
        }
        ItemStack held = player.getStackInHand(hand);
        if (!VisualState.isFake(held)) {
            return false;
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        if (!world.getBlockState(pos).isOf(Blocks.GRASS_BLOCK)) {
            return false;
        }

        VisualState.fakeUse(held);

        List<Integer> pattern = ClientSettings.pattern;
        int flowers = pattern.isEmpty() ? 0 : pattern.get(clickIndex % pattern.size());
        clickIndex = pattern.isEmpty() ? 0 : (clickIndex + 1) % pattern.size();

        bloom(world, pos, flowers);

        if (ClientSettings.effects) {
            world.syncWorldEvent(WorldEvents.BONE_MEAL_USED, pos.up(), 15);
        }
        return true;
    }

    private static void set(ClientWorld world, BlockPos pos, net.minecraft.block.BlockState state) {
        world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }

    private static void bloom(ClientWorld world, BlockPos center, int flowers) {
        int r = ClientSettings.radius;

        // wolne miejsca na trawie w promieniu (dopasowane do wzgorz i dolin)
        List<BlockPos> spots = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + r) {
                    continue;
                }
                for (int dy = 3; dy >= -3; dy--) {
                    BlockPos ground = center.add(dx, dy, dz);
                    if (world.getBlockState(ground).isOf(Blocks.GRASS_BLOCK)
                            && world.getBlockState(ground.up()).isAir()) {
                        spots.add(ground.up());
                        break;
                    }
                }
            }
        }
        Random rnd = world.getRandom();
        Collections.shuffle(spots, new java.util.Random(rnd.nextLong()));

        List<Block> allowed = new ArrayList<>();
        for (int i = 0; i < FakeBoneMeal.FLOWERS.length; i++) {
            if ((ClientSettings.flowerMask & (1 << i)) != 0) {
                allowed.add(FakeBoneMeal.FLOWERS[i]);
            }
        }
        if (allowed.isEmpty()) {
            Collections.addAll(allowed, FakeBoneMeal.FLOWERS);
        }

        int idx = 0;
        for (int i = 0; i < flowers && idx < spots.size(); i++) {
            BlockPos p = spots.get(idx++);
            set(world, p, allowed.get(rnd.nextInt(allowed.size())).getDefaultState());
        }
        for (int i = 0; i < ClientSettings.grass && idx < spots.size(); i++) {
            BlockPos p = spots.get(idx++);
            if (ClientSettings.tall && rnd.nextInt(4) == 0 && world.getBlockState(p.up()).isAir()) {
                set(world, p, Blocks.TALL_GRASS.getDefaultState()
                        .with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
                set(world, p.up(), Blocks.TALL_GRASS.getDefaultState()
                        .with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            } else {
                set(world, p, Blocks.SHORT_GRASS.getDefaultState());
            }
        }
    }
}
