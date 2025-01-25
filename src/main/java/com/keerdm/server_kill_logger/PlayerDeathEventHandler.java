package com.keerdm.server_kill_logger;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerDeathEventHandler {

    public static class DeathInfo {
        public String playerName;
        public String playerUUID;
        public String logDateUtc;
        public String logDateLocal;
        public String causeOfDeath;
        public String damageType;
        public String sourceEntity;
        public String location;
        public String biome;
        public String gamemode;
        public String opStatus;
        public List<NearbyPlayerInfo> nearbyPlayers;
        public int xpLevel;
        public float xpPoints;
        public float health;
        public float maxHealth;
        public int foodLevel;
        public float saturation;
        public String difficulty;
        public long dayTime;
        public String weather;
        public int moonPhase;
        public EquippedItems equippedItems;
        public List<InventoryItem> inventoryContents;

        public static class NearbyPlayerInfo {
            public String name;
            public double distance;
            public String location;
            public String dimension;
        }

        public static class EquippedItems {
            public String helmet;
            public String chestplate;
            public String leggings;
            public String boots;
            public String mainHand;
            public String offHand;
        }

        public static class InventoryItem {
            public int count;
            public String name;
        }
    }

    @SubscribeEvent
    public static DeathInfo onPlayerDeath(LivingDeathEvent event) {
        // Check if the entity is a player
        if (!(event.getEntity() instanceof Player player)) return null;

        // Get current time in UTC
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
        // Also get server local time
        ZonedDateTime nowLocal = ZonedDateTime.now();

        DeathInfo deathInfo = new DeathInfo();

        try {
            // Create main logs directory if it doesn't exist
            File logsDir = new File("player_death_logs");
            if (!logsDir.exists()) {
                logsDir.mkdir();
            }

            // Create player-specific directory
            File playerDir = new File("player_death_logs/" + player.getName().getString());
            if (!playerDir.exists()) {
                playerDir.mkdir();
            }

            // Create more readable date formats
            String fileDate = nowUtc.format(DateTimeFormatter.ofPattern("MMMM-dd-yyyy_HH-mm-ss"));
            String logDateUtc = nowUtc.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm:ss 'UTC'"));
            String logDateLocal = nowLocal.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm:ss z"));

            // Death location
            BlockPos deathPos = player.blockPosition();
            String dimension = player.level().dimension().location().toString();

            // Populate DeathInfo object
            deathInfo.playerName = player.getName().getString();
            deathInfo.playerUUID = player.getStringUUID();
            deathInfo.logDateUtc = logDateUtc;
            deathInfo.logDateLocal = logDateLocal;
            deathInfo.causeOfDeath = event.getSource().getLocalizedDeathMessage(player).getString();
            deathInfo.damageType = event.getSource().type().msgId();
            deathInfo.sourceEntity = event.getSource().getEntity() != null
                    ? event.getSource().getEntity().getName().getString()
                    : "Unknown";
            deathInfo.location = String.format("[%d, %d, %d] in %s",
                    deathPos.getX(), deathPos.getY(), deathPos.getZ(), dimension);
            deathInfo.biome = player.level().getBiome(deathPos).unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse("Unknown Biome");

            // Gamemode and OP Status
            if (player instanceof ServerPlayer serverPlayer) {
                GameType gameMode = serverPlayer.gameMode.getGameModeForPlayer();
                deathInfo.gamemode = gameMode.name();

                boolean isOp = serverPlayer.getServer().getPlayerList().isOp(serverPlayer.getGameProfile());
                int opLevel = serverPlayer.getServer().getPlayerList().getOps().get(serverPlayer.getGameProfile()) != null
                        ? serverPlayer.getServer().getPlayerList().getOps().get(serverPlayer.getGameProfile()).getLevel()
                        : 0;
                deathInfo.opStatus = isOp ? "Yes (Level " + opLevel + ")" : "No";
            }

            // Nearby Players
            List<Player> nearbyPlayersList = getNearbyPlayers(player, 50);
            deathInfo.nearbyPlayers = new ArrayList<>();
            for (Player nearbyPlayer : nearbyPlayersList) {
                if (!nearbyPlayer.equals(player)) {
                    DeathInfo.NearbyPlayerInfo nearbyPlayerInfo = new DeathInfo.NearbyPlayerInfo();
                    nearbyPlayerInfo.name = nearbyPlayer.getName().getString();
                    nearbyPlayerInfo.distance = nearbyPlayer.distanceTo(player);
                    BlockPos nearbyPlayerPos = nearbyPlayer.blockPosition();
                    nearbyPlayerInfo.location = String.format("[%d, %d, %d]",
                            nearbyPlayerPos.getX(), nearbyPlayerPos.getY(), nearbyPlayerPos.getZ());
                    nearbyPlayerInfo.dimension = nearbyPlayer.level().dimension().location().toString();
                    deathInfo.nearbyPlayers.add(nearbyPlayerInfo);
                }
            }

            // Player stats
            deathInfo.xpLevel = player.experienceLevel;
            deathInfo.xpPoints = player.experienceProgress;
            deathInfo.health = player.getHealth();
            deathInfo.maxHealth = player.getMaxHealth();
            deathInfo.foodLevel = player.getFoodData().getFoodLevel();
            deathInfo.saturation = player.getFoodData().getSaturationLevel();

            // Game conditions
            deathInfo.difficulty = player.level().getDifficulty().toString();
            deathInfo.dayTime = player.level().getDayTime();
            deathInfo.weather = getWeatherString(player.level());
            deathInfo.moonPhase = player.level().getMoonPhase();

            // Equipped Items
            DeathInfo.EquippedItems equippedItems = new DeathInfo.EquippedItems();
            equippedItems.helmet = formatItemStack(player.getInventory().getArmor(3));
            equippedItems.chestplate = formatItemStack(player.getInventory().getArmor(2));
            equippedItems.leggings = formatItemStack(player.getInventory().getArmor(1));
            equippedItems.boots = formatItemStack(player.getInventory().getArmor(0));
            equippedItems.mainHand = formatItemStack(player.getMainHandItem());
            equippedItems.offHand = formatItemStack(player.getOffhandItem());
            deathInfo.equippedItems = equippedItems;

            // Inventory Contents
            deathInfo.inventoryContents = new ArrayList<>();
            for (ItemStack item : player.getInventory().items) {
                if (!item.isEmpty()) {
                    DeathInfo.InventoryItem inventoryItem = new DeathInfo.InventoryItem();
                    inventoryItem.count = item.getCount();
                    inventoryItem.name = formatItemStack(item);
                    deathInfo.inventoryContents.add(inventoryItem);
                }
            }

            // Create log file
            String fileName = String.format("player_death_logs/%s/%s.txt",
                    player.getName().getString(),
                    fileDate);

            try (FileWriter writer = new FileWriter(fileName)) {
                // Write all the same information to the log file
                writer.write("Death Information:\n");
                writer.write("----------------\n");
                writer.write("Player: " + deathInfo.playerName + "\n");
                writer.write("UUID: " + deathInfo.playerUUID + "\n");
                writer.write("Time (UTC+0): " + deathInfo.logDateUtc + "\n");
                writer.write("Time (Server Local): " + deathInfo.logDateLocal + "\n");

                // Damage Details
                writer.write("\nDamage Details:\n");
                writer.write("----------------\n");
                writer.write("Cause of Death: " + deathInfo.causeOfDeath + "\n");
                writer.write("Damage Type: " + deathInfo.damageType + "\n");
                writer.write("Source Entity: " + deathInfo.sourceEntity + "\n");

                // Location and Biome
                writer.write("Location: " + deathInfo.location + "\n");
                writer.write("Biome: " + deathInfo.biome + "\n");

                // Nearby Players
                writer.write("\nNearby Players:\n");
                writer.write("----------------\n");
                if (deathInfo.nearbyPlayers.isEmpty()) {
                    writer.write("No players nearby\n");
                } else {
                    for (DeathInfo.NearbyPlayerInfo nearbyPlayer : deathInfo.nearbyPlayers) {
                        writer.write(String.format("* %s (%.1f blocks away)\n",
                                nearbyPlayer.name, nearbyPlayer.distance));
                        writer.write(String.format("  Location: %s in %s\n",
                                nearbyPlayer.location, nearbyPlayer.dimension));
                    }
                }

                // Player Stats
                writer.write("\nPlayer Stats at Death:\n");
                writer.write("----------------\n");
                writer.write(String.format("XP Level: %d\n", deathInfo.xpLevel));
                writer.write(String.format("XP Points: %.2f\n", deathInfo.xpPoints));
                writer.write(String.format("Health: %.1f/%.1f\n", deathInfo.health, deathInfo.maxHealth));
                writer.write(String.format("Food Level: %d/20\n", deathInfo.foodLevel));
                writer.write(String.format("Saturation: %.1f\n", deathInfo.saturation));

                // Game Conditions
                writer.write("\nGame Conditions:\n");
                writer.write("----------------\n");
                writer.write("Difficulty: " + deathInfo.difficulty + "\n");
                writer.write(String.format("Day Time: %d (Minecraft ticks)\n", deathInfo.dayTime));
                writer.write("Weather: " + deathInfo.weather + "\n");
                writer.write(String.format("Moon Phase: %d/8\n", deathInfo.moonPhase));

                // Equipped Items
                writer.write("\nEquipped Items:\n");
                writer.write("----------------\n");
                writer.write("Helmet: " + deathInfo.equippedItems.helmet + "\n");
                writer.write("Chestplate: " + deathInfo.equippedItems.chestplate + "\n");
                writer.write("Leggings: " + deathInfo.equippedItems.leggings + "\n");
                writer.write("Boots: " + deathInfo.equippedItems.boots + "\n");
                writer.write("Main Hand: " + deathInfo.equippedItems.mainHand + "\n");
                writer.write("Off Hand: " + deathInfo.equippedItems.offHand + "\n");

                // Inventory Contents
                writer.write("\nInventory Contents:\n");
                writer.write("----------------\n");
                if (deathInfo.inventoryContents.isEmpty()) {
                    writer.write("Inventory is empty\n");
                } else {
                    for (DeathInfo.InventoryItem item : deathInfo.inventoryContents) {
                        writer.write(String.format("%dx %s\n", item.count, item.name));
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return deathInfo;
    }

    private static List<Player> getNearbyPlayers(Player centerPlayer, double radius) {
        List<Player> nearbyPlayers = new ArrayList<>();

        // Get all players in the same dimension
        for (Player otherPlayer : centerPlayer.level().players()) {
            // Skip if in different dimensions
            if (!otherPlayer.level().dimension().equals(centerPlayer.level().dimension())) continue;

            // Calculate distance
            double distance = otherPlayer.distanceTo(centerPlayer);

            // Add to list if within radius
            if (distance <= radius) {
                nearbyPlayers.add(otherPlayer);
            }
        }

        return nearbyPlayers;
    }

    private static String formatItemStack(ItemStack item) {
        if (item == null || item.isEmpty()) return "Empty";

        // Get the registry ID of the item
        ResourceLocation itemId = item.getItem().builtInRegistryHolder().key().location();
        String itemIdString = itemId != null ? itemId.toString() : "unknown:item";

        String itemDesc = item.getDisplayName().getString();

        // Combine registry ID and display name
        String formattedItem = String.format("%s (%s)", itemDesc, itemIdString);

        // Add NBT tag information if present
        if (item.hasTag()) {
            formattedItem += " (NBT: " + item.getTag().toString() + ")";
        }
        return formattedItem;
    }

    private static String getWeatherString(Level level) {
        if (level.isThundering()) return "Thunderstorm";
        if (level.isRaining()) return "Rain";
        return "Clear";
    }
}