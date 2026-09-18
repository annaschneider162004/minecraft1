package com.annaschneider.minecraft1.blueprint;

import net.minecraft.world.level.block.Blocks;

public final class Templates {
    private Templates() { }

    public static Blueprint create(String template) {
        return switch (template.toLowerCase()) {
            case "house" -> house();
            case "castle" -> castle();
            case "village" -> village();
            default -> null;
        };
    }

    private static Blueprint house() {
        Blueprint b = new Blueprint("house");
        for (int x = 0; x < 9; x++) for (int z = 0; z < 7; z++) b.add(x, 0, z, Blocks.STONE_BRICKS);
        for (int y = 1; y <= 4; y++) for (int x = 0; x < 9; x++) for (int z = 0; z < 7; z++)
            if (x == 0 || x == 8 || z == 0 || z == 6) b.add(x, y, z, Blocks.OAK_PLANKS);
        for (int x = 0; x < 9; x++) for (int z = 0; z < 7; z++) b.add(x, 5, z, Blocks.SPRUCE_PLANKS);
        for (int y = 1; y <= 2; y++) b.add(4, y, 0, Blocks.AIR);
        return b;
    }

    private static Blueprint castle() {
        Blueprint b = new Blueprint("castle");
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) if (Math.abs(x) == 8 || Math.abs(z) == 8) for (int y = 0; y < 5; y++) b.add(x, y, z, Blocks.STONE_BRICKS);
        for (int[] c : new int[][]{{-8,-8},{-8,8},{8,-8},{8,8}}) for (int x = c[0]-1; x <= c[0]+1; x++) for (int z = c[1]-1; z <= c[1]+1; z++) for (int y=0;y<9;y++) b.add(x,y,z,Blocks.COBBLESTONE);
        for (int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) b.add(x,0,z,Blocks.STONE_BRICKS);
        return b;
    }

    private static Blueprint village() {
        Blueprint b = new Blueprint("village");
        Blueprint house = house();
        for (BlueprintBlock p : house.blocks()) {
            b.blocks().add(new BlueprintBlock(p.relativePos().offset(-6, 0, -4), p.block()));
            b.blocks().add(new BlueprintBlock(p.relativePos().offset(6, 0, 4), p.block()));
        }
        for (int x=-10;x<=10;x++) for(int z=-2;z<=2;z++) b.add(x,0,z,Blocks.PATH);
        return b;
    }
}
