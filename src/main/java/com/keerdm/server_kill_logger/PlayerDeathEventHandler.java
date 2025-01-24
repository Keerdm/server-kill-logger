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
import java.util.List;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerDeathEventHandler {

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        // Check if the entity is a player
        if (!(event.getEntity() instanceof Player player)) return;

        // Get current time in UTC
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
        // Also get server local time
        ZonedDateTime nowLocal = ZonedDateTime.now();

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

            // Get death location
            BlockPos deathPos = player.blockPosition();
            String dimension = player.level().dimension().location().toString();

            // Create log file with readable timestamp
            String fileName = String.format("player_death_logs/%s/%s.txt",
                    player.getName().getString(),
                    fileDate);

            FileWriter writer = new FileWriter(fileName);

            // Write enhanced death information
            writer.write("Death Information:\n");
            writer.write("----------------\n");
            writer.write("Player: " + player.getName().getString() + "\n");
            writer.write("UUID: " + player.getStringUUID() + "\n");
            writer.write("Time (UTC+0): " + logDateUtc + "\n");
            writer.write("Time (Server Local): " + logDateLocal + "\n");

            // Damage Details
            writer.write("\nDamage Details:\n");
            writer.write("----------------\n");
            writer.write("Cause of Death: " + event.getSource().getLocalizedDeathMessage(player).getString() + "\n");
            writer.write("Damage Type: " + event.getSource().type().msgId() + "\n");

            // Additional damage information if possible
            if (event.getSource().getEntity() != null) {
                writer.write("Source Entity: " + event.getSource().getEntity().getName().getString() + "\n");
            }

            // Location and Biome
            writer.write(String.format("Location: [%d, %d, %d] in %s\n",
                    deathPos.getX(), deathPos.getY(), deathPos.getZ(), dimension));

            // Get Biome
            Biome biome = player.level().getBiome(deathPos).value();
            writer.write("Biome: " + player.level().getBiome(deathPos).unwrapKey().map(key -> key.location().toString()).orElse("Unknown Biome") + "\n");

            // Gamemode and OP Status
            writer.write("\nPlayer Status:\n");
            writer.write("----------------\n");

            if (player instanceof ServerPlayer serverPlayer) {
                GameType gameMode = serverPlayer.gameMode.getGameModeForPlayer();
                writer.write("Gamemode: " + gameMode.name() + "\n");

                // OP Status
                boolean isOp = serverPlayer.getServer().getPlayerList().isOp(serverPlayer.getGameProfile());
                int opLevel = serverPlayer.getServer().getPlayerList().getOps().get(serverPlayer.getGameProfile()) != null
                        ? serverPlayer.getServer().getPlayerList().getOps().get(serverPlayer.getGameProfile()).getLevel()
                        : 0;
                writer.write("OP Status: " + (isOp ? "Yes (Level " + opLevel + ")" : "No") + "\n");
            }

            // Nearby Players
            List<Player> nearbyPlayers = getNearbyPlayers(player, 50);
            writer.write("\nNearby Players:\n");
            writer.write("----------------\n");
            if (nearbyPlayers.isEmpty()) {
                writer.write("No players nearby\n");
            } else {
                boolean foundNearbyPlayers = false;
                for (Player nearbyPlayer : nearbyPlayers) {
                    if (!nearbyPlayer.equals(player)) {
                        foundNearbyPlayers = true;
                        double distance = nearbyPlayer.distanceTo(player);
                        BlockPos nearbyPlayerPos = nearbyPlayer.blockPosition();
                        writer.write(String.format("* %s (%.1f blocks away)\n",
                                nearbyPlayer.getName().getString(),
                                distance));
                        writer.write(String.format("  Location: [%d, %d, %d] in %s\n",
                                nearbyPlayerPos.getX(),
                                nearbyPlayerPos.getY(),
                                nearbyPlayerPos.getZ(),
                                nearbyPlayer.level().dimension().location().toString()));
                    }
                }

                if (!foundNearbyPlayers) {
                    writer.write("No players nearby\n");
                }
            }

            // Player stats
            writer.write("\nPlayer Stats at Death:\n");
            writer.write("----------------\n");
            writer.write(String.format("XP Level: %d\n", player.experienceLevel));
            writer.write(String.format("XP Points: %.2f\n", player.experienceProgress));
            writer.write(String.format("Health: %.1f/%.1f\n", player.getHealth(), player.getMaxHealth()));
            writer.write(String.format("Food Level: %d/20\n", player.getFoodData().getFoodLevel()));
            writer.write(String.format("Saturation: %.1f\n", player.getFoodData().getSaturationLevel()));

            // Game conditions
            writer.write("\nGame Conditions:\n");
            writer.write("----------------\n");
            writer.write(String.format("Difficulty: %s\n", player.level().getDifficulty()));
            writer.write(String.format("Day Time: %d (Minecraft ticks)\n", player.level().getDayTime()));
            writer.write(String.format("Weather: %s\n", getWeatherString(player.level())));
            writer.write(String.format("Moon Phase: %d/8\n", player.level().getMoonPhase()));

            // Equipped Items
            writer.write("\nEquipped Items:\n");
            writer.write("----------------\n");
            writer.write(String.format("Helmet: %s\n", formatItemStack(player.getInventory().getArmor(3))));
            writer.write(String.format("Chestplate: %s\n", formatItemStack(player.getInventory().getArmor(2))));
            writer.write(String.format("Leggings: %s\n", formatItemStack(player.getInventory().getArmor(1))));
            writer.write(String.format("Boots: %s\n", formatItemStack(player.getInventory().getArmor(0))));
            writer.write(String.format("Main Hand: %s\n", formatItemStack(player.getMainHandItem())));
            writer.write(String.format("Off Hand: %s\n", formatItemStack(player.getOffhandItem())));

            // Full Inventory Contents
            writer.write("\nInventory Contents:\n");
            writer.write("----------------\n");

            boolean hasItems = false;
            for (ItemStack item : player.getInventory().items) {
                if (!item.isEmpty()) {
                    hasItems = true;
                    writer.write(String.format("%dx %s\n",
                            item.getCount(),
                            formatItemStack(item)));
                }
            }

            if (!hasItems) {
                writer.write("Inventory is empty\n");
            }

            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
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