package me.flukky;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Main extends JavaPlugin implements Listener, TabCompleter {
    private File homesFile;
    private FileConfiguration homesConfig;
    private final Set<UUID> teleportingPlayers = new HashSet<>();
    private final Map<UUID, BukkitRunnable> countdownTasks = new HashMap<>(); // สำหรับจัดการกับการนับถอยหลัง

    @Override
    public void onEnable() {
        homesFile = new File(getDataFolder(), "homes.yml");
        if (!homesFile.exists()) {
            saveResource("homes.yml", false);
        }
        homesConfig = YamlConfiguration.loadConfiguration(homesFile);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveHomes();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (command.getName().equalsIgnoreCase("sethome")) {
                if (args.length < 1) {
                    player.sendMessage(ChatColor.RED + "กรุณาระบุชื่อบ้าน!");
                    return true;
                }
                String homeName = args[0];
                setHome(player, homeName);
                player.sendMessage("บ้าน " + homeName + " ได้ถูกตั้งค่าแล้ว!");
                return true;
            } else if (command.getName().equalsIgnoreCase("home")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "กรุณาระบุชื่อบ้านและค่าใช้จ่าย (exp หรือ diamond)!");
                    return true;
                }
                String homeName = args[0];
                String costType = args[1];

                if (costType.equalsIgnoreCase("exp")) {
                    if (player.getLevel() < 10) {
                        player.sendMessage(ChatColor.RED + "คุณต้องมี level อย่างน้อย 10 เพื่อใช้คำสั่งนี้!");
                        return true;
                    }
                } else if (costType.equalsIgnoreCase("diamond")) {
                    if (!player.getInventory().contains(Material.DIAMOND, 15)) {
                        player.sendMessage(ChatColor.RED + "คุณต้องมี diamond อย่างน้อย 15 เพื่อใช้คำสั่งนี้!");
                        return true;
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "ใช้ exp หรือ diamond เท่านั้น");
                    return true;
                }

                teleportingPlayers.add(player.getUniqueId()); // เพิ่มผู้เล่นในขณะที่กำลัง teleport

                // เริ่มการนับถอยหลัง
                BukkitRunnable countdownTask = new BukkitRunnable() {
                    int countdown = 5;

                    @Override
                    public void run() {
                        if (countdown > 0) {
                            player.sendMessage(ChatColor.YELLOW + "กำลังกลับบ้านใน " + countdown + "...");
                            countdown--;
                        } else {
                            this.cancel();
                            Location homeLocation = getHome(player, homeName);
                            if (homeLocation != null) {
                                player.teleport(homeLocation);
                                player.sendMessage(ChatColor.GREEN + "กลับไปยังบ้าน " + ChatColor.YELLOW + homeName + ChatColor.GREEN + " แล้ว!");
                                if (costType.equalsIgnoreCase("exp")) {
                                    player.setLevel(player.getLevel() - 10);
                                } else if (costType.equalsIgnoreCase("diamond")) {
                                    player.getInventory().removeItem(new ItemStack(Material.DIAMOND, 15));
                                }
                            } else {
                                player.sendMessage(ChatColor.RED + "ไม่พบบ้าน " + ChatColor.YELLOW + homeName + ChatColor.RED + "!");
                            }
                            teleportingPlayers.remove(player.getUniqueId()); // ลบผู้เล่นออกจากการรอ
                        }
                    }
                };

                countdownTasks.put(player.getUniqueId(), countdownTask); // เก็บ Task ใน Map
                countdownTask.runTaskTimer(this, 0, 20); // 20 ticks = 1 second

                return true;
            }
        }
        return false;
    }

    private void setHome(Player player, String homeName) {
        String playerName = player.getName();
        Location location = player.getLocation();
        String path = playerName + "." + homeName;

        homesConfig.set(path + ".world", location.getWorld().getName());
        homesConfig.set(path + ".x", location.getX());
        homesConfig.set(path + ".y", location.getY());
        homesConfig.set(path + ".z", location.getZ());
        homesConfig.set(path + ".pitch", location.getPitch());
        homesConfig.set(path + ".yaw", location.getYaw());

        saveHomes();
    }

    private Location getHome(Player player, String homeName) {
        String playerName = player.getName();
        String path = playerName + "." + homeName;

        if (homesConfig.contains(path)) {
            String worldName = homesConfig.getString(path + ".world");
            double x = homesConfig.getDouble(path + ".x");
            double y = homesConfig.getDouble(path + ".y");
            double z = homesConfig.getDouble(path + ".z");
            float pitch = (float) homesConfig.getDouble(path + ".pitch");
            float yaw = (float) homesConfig.getDouble(path + ".yaw");

            return new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
        }
        return null;
    }

    private void saveHomes() {
        try {
            homesConfig.save(homesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("sethome")) {
            if (args.length == 1) {
                // ตรวจสอบว่า sender มีข้อมูลใน config หรือไม่
                ConfigurationSection section = homesConfig.getConfigurationSection(sender.getName());
                if (section != null) {
                    // เติมชื่อบ้าน
                    for (String home : section.getKeys(false)) {
                        completions.add(home);
                    }
                }
            }
        } else if (command.getName().equalsIgnoreCase("home")) {
            if (args.length == 1) {
                // ตรวจสอบว่า sender มีข้อมูลใน config หรือไม่
                ConfigurationSection section = homesConfig.getConfigurationSection(sender.getName());
                if (section != null) {
                    // เติมชื่อบ้าน
                    for (String home : section.getKeys(false)) {
                        completions.add(home);
                    }
                }
            } else if (args.length == 2) {
                // เติมประเภทค่าใช้จ่าย
                completions.add("exp");
                completions.add("diamond");
            }
        }

        Collections.sort(completions);
        return completions;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        if (teleportingPlayers.contains(player.getUniqueId())) {
            // ตรวจสอบว่าผู้เล่นได้ขยับตำแหน่งในแนวราบ (x, z) หรือไม่
            if (!event.getFrom().getBlock().equals(event.getTo().getBlock())) {
                player.sendMessage(ChatColor.RED + "ยกเลิกการกลับบ้านเพราะคุณทำการเดิน!");
                teleportingPlayers.remove(player.getUniqueId()); // ลบผู้เล่นออกจากการรอ
                BukkitRunnable countdownTask = countdownTasks.get(player.getUniqueId());
                if (countdownTask != null) {
                    countdownTask.cancel(); // ยกเลิกการนับถอยหลัง
                    countdownTasks.remove(player.getUniqueId()); // ลบ Task ออกจาก Map
                }
            }
        }
    }

}
