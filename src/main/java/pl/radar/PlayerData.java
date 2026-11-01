package pl.radar;

import net.minecraft.entity.player.PlayerEntity;

public class PlayerData {
    public final PlayerEntity player;
    public final String name;
    public final double dist;

    // MineStar (z nametaga); -1 = nie znaleziono
    public int sword = -1, set = -1, skulls = -1;

    // Anarchia (ze zbroi)
    public int diamondPieces, netheritePieces, purplePieces;
    public int minEnch, minProt, totalEnch;
    public int tier; // 0 brak, 1 anarchiczny I, 2 anarchiczny II

    public PlayerData(PlayerEntity p, double dist) {
        this.player = p;
        this.name = p.getName().getString();
        this.dist = dist;
    }
}
