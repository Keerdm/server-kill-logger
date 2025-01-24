package com.keerdm.server_kill_logger;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.*;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerKillEntityEventHandler {

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        // Check if the killer is a player
        if (!(event.getSource().getEntity() instanceof Player killer)) return;

        // Get current times
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
        ZonedDateTime nowLocal = ZonedDateTime.now();

        try {
            // Create main logs directory if it doesn't exist
            File logsDir = new File("player_kill_entity_logs");
            if (!logsDir.exists()) {
                logsDir.mkdir();
            }

            // Create killer's directory
            File killerDir = new File("player_kill_entity_logs/" + killer.getName().getString());
            if (!killerDir.exists()) {
                killerDir.mkdir();
            }

            // Create more readable date formats
            String fileDate = nowUtc.format(DateTimeFormatter.ofPattern("MMMM-dd-yyyy"));

            // Prepare kill file name
            String fileName = String.format("player_kill_entity_logs/%s/%s.txt",
                    killer.getName().getString(),
                    fileDate);

            // Prepare kill data
            Map<String, Integer> entityKills = new HashMap<>();
            Map<String, Integer> namedEntityKills = new HashMap<>();
            Map<String, Integer> playerKills = new HashMap<>();

            // Attempt to read existing kill data
            readExistingKillData(fileName, entityKills, namedEntityKills, playerKills);

            // Handle non-player entity kills
            if (!(event.getEntity() instanceof Player)) {
                Entity killedEntity = event.getEntity();

                String entityType = parseEntityType(killedEntity);
                String entityName = killedEntity.getName().getString();

                // Decide how to track the kill
                if (isUnnamedEntity(killedEntity, entityName)) {
                    // Unnamed entity
                    entityKills.merge(entityType, 1, Integer::sum);
                } else {
                    // Named entity
                    String namedEntityKey = String.format("%s (name:%s)", entityType, entityName);
                    namedEntityKills.merge(namedEntityKey, 1, Integer::sum);
                }
            }

            // Handle player kills
            if (event.getEntity() instanceof Player killedPlayer) {
                String playerKey = String.format("%s (uuid:%s)",
                        killedPlayer.getName().getString(),
                        killedPlayer.getStringUUID());
                playerKills.merge(playerKey, 1, Integer::sum);
            }

            // Write updated kills to file
            writeKillsToFile(fileName, killer, nowUtc, nowLocal,
                    entityKills, namedEntityKills, playerKills);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void readExistingKillData(String fileName,
                                             Map<String, Integer> entityKills,
                                             Map<String, Integer> namedEntityKills,
                                             Map<String, Integer> playerKills) {

        try {
            File file = new File(fileName);
            if (!file.exists()) return;

            List<String> lines = Files.readAllLines(file.toPath());
            boolean inEntitySection = false;
            boolean inNamedEntitySection = false;
            boolean inPlayerKillSection = false;

            for (String line : lines) {
                line = line.trim();

                if (line.equals("Entities Killed")) {
                    inEntitySection = true;
                    inNamedEntitySection = false;
                    inPlayerKillSection = false;
                    continue;
                }

                if (line.equals("----------------")) continue;

                if (line.isEmpty()) {
                    inEntitySection = false;
                    inNamedEntitySection = false;
                    inPlayerKillSection = false;
                    continue;
                }

                if (line.equals("Player Kills")) {
                    inPlayerKillSection = true;
                    inEntitySection = false;
                    inNamedEntitySection = false;
                    continue;
                }

                // Parse kills
                if (inPlayerKillSection && line.contains("x")) {
                    String[] parts = line.split(" x");
                    if (parts.length == 2) {
                        playerKills.put(parts[0], Integer.parseInt(parts[1]));
                    }
                }

                if (inEntitySection && line.contains("x")) {
                    String[] parts = line.split(" x");
                    if (parts.length == 2) {
                        entityKills.put(parts[0], Integer.parseInt(parts[1]));
                    }
                }

                if (line.startsWith("minecraft:") || line.contains("(name:") && line.contains("x")) {
                    inNamedEntitySection = true;
                    inEntitySection = false;
                }

                if (inNamedEntitySection && line.contains("x")) {
                    String[] parts = line.split(" x");
                    if (parts.length == 2) {
                        namedEntityKills.put(parts[0], Integer.parseInt(parts[1]));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeKillsToFile(String fileName, Player killer,
                                         ZonedDateTime nowUtc, ZonedDateTime nowLocal,
                                         Map<String, Integer> dailyEntityKills,
                                         Map<String, Integer> dailyNamedEntityKills,
                                         Map<String, Integer> dailyPlayerKills) throws IOException {

        try (FileWriter writer = new FileWriter(fileName)) {
            // Player Details Section
            writer.write("Player Details\n");
            writer.write("----------------\n");
            writer.write("Name: " + killer.getName().getString() + "\n");
            writer.write("UUID: " + killer.getStringUUID() + "\n");
            writer.write("Date (UTC): " + nowUtc.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")) + "\n");
            writer.write("Date (Server): " + nowLocal.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")) + "\n\n");

            // Player Kills Section
            writer.write("Player Kills\n");
            writer.write("----------------\n");
            if (dailyPlayerKills != null && !dailyPlayerKills.isEmpty()) {
                dailyPlayerKills.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(entry -> {
                            try {
                                writer.write(String.format("%s x%d\n", entry.getKey(), entry.getValue()));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            } else {
                writer.write("No players killed\n");
            }
            writer.write("\n");

            // Entities Killed Section
            writer.write("Entities Killed\n");
            writer.write("----------------\n");

            // First, write unnamed/generic entities
            if (dailyEntityKills != null && !dailyEntityKills.isEmpty()) {
                dailyEntityKills.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(entry -> {
                            try {
                                writer.write(String.format("%s x%d\n", entry.getKey(), entry.getValue()));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }

            // Then, write named entities
            if (dailyNamedEntityKills != null && !dailyNamedEntityKills.isEmpty()) {
                dailyNamedEntityKills.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(entry -> {
                            try {
                                writer.write(String.format("%s x%d\n", entry.getKey(), entry.getValue()));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }

            // If no kills at all
            if ((dailyEntityKills == null || dailyEntityKills.isEmpty()) &&
                    (dailyNamedEntityKills == null || dailyNamedEntityKills.isEmpty())) {
                writer.write("No entities killed\n");
            }
        }
    }

    private static String parseEntityType(Entity entity) {
        String fullType = entity.getType().toString();
        return Arrays.stream(fullType.split("\\."))
                .reduce((first, second) -> second)
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
                .orElse(fullType);
    }

    private static boolean isUnnamedEntity(Entity entity, String entityName) {
        return entityName == null ||
                entityName.isEmpty() ||
                entityName.equalsIgnoreCase("entity") ||
                entityName.equalsIgnoreCase(parseEntityType(entity));
    }
}