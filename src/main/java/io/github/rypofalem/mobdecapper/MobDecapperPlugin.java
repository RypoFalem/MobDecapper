package io.github.rypofalem.mobdecapper;

import lombok.AllArgsConstructor;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

import lombok.EqualsAndHashCode;

public class MobDecapperPlugin extends JavaPlugin implements Listener {

	int localCap = 50;
	int localRange = 256;
	HashMap<ChunkID, ChunkInfo> chunkMobs;
	int counterSeconds = 0; //one second counterSeconds
	HashSet<Player> playerListeners; //players listening to debug info


	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		chunkMobs = new HashMap<>();
		playerListeners = new HashSet<>();
		Bukkit.getScheduler().runTaskTimer(this, () -> counterSeconds++, 1, 20);
		saveDefaultConfig();
		loadConfig();
	}

	void loadConfig(){
		if(getConfig().isInt("localCap")){
			localCap = Math.max(10, getConfig().getInt("localCap"));
		}
		if(getConfig().isInt("localRange")){
			localRange = Math.max(64, getConfig().getInt("localRange"));
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
		if(args == null || args.length < 1) return false;
		if(args[0].equalsIgnoreCase("reload")){
			loadConfig();
			sender.sendMessage("Reloaded MobDecapper config.");
			return true;
		}else if(args[0].equalsIgnoreCase("players")){
			String output = "";
			for(World world : Bukkit.getWorlds()){
				output += "\n" + world.getName() + "\n";
				for(Player player : world.getPlayers()){
					int count =  0;
					for(Entity mob : player.getNearbyEntities(512, 256,512)){
						if(isHostile(mob)) count++;
					}
					output+=String.format("    %s: %d\n", player.getName(), count);
				}
			}
			sender.sendMessage(output);
			return true;
		} else if(args[0].equalsIgnoreCase("chunks")){
			@AllArgsConstructor
			class LocationCount implements Comparable<LocationCount>{
				int count;
				Location location;
				@Override
				public int compareTo(LocationCount other) {
					return other.count - count;
				}
			}
			Map<String, ArrayList<LocationCount>> worlds = new HashMap<>();
			for(ChunkInfo chunk : chunkMobs.values()){
				Location location = chunk.location;
				String world = location.getWorld().getName();
				sender.sendMessage(location.toVector().toBlockVector().toString());
				if(!worlds.containsKey(world)){
					worlds.put(world, new ArrayList<>());
				}
				int count = 0;
				for(Entity mob : location.getWorld().getNearbyEntities(location, localRange, 256, localRange)){
					if(isHostile(mob)) count++;
				}
				worlds.get(world).add(new LocationCount(count, location));
			}
			String output = "";
			for(String world : worlds.keySet()){
				worlds.get(world).sort(LocationCount::compareTo);
				output += "\n"+ world + ":\n";
				for(LocationCount chunk : worlds.get(world)){
					output += String.format("    %d, %d : %d\n",
							chunk.location.getBlockX(), chunk.location.getBlockZ(), chunk.count);
				}
			}
			sender.sendMessage(output);
			return true;
		} else if(args[0].equalsIgnoreCase("listen")){
			if(sender instanceof Player){
				if(playerListeners.contains(sender)){
					playerListeners.remove(sender);
					sender.sendMessage("No longer listening to spawn events");
				} else {
					playerListeners.add((Player)sender);
					sender.sendMessage("Now listening to spawn events");
				}
				return true;
			}
		} else if(args[0].equalsIgnoreCase("listtypes")){
			String message = "\n";
			for(World world : Bukkit.getWorlds()){
				HashMap<EntityType, Integer> typeCount = new HashMap<>();
				for(Entity entity : world.getEntities()){
					EntityType type = entity.getType();
					if(!typeCount.containsKey(type)) typeCount.put(type, 0);
					typeCount.put(type, typeCount.get(type) + 1);
				}
				message += world.getName() + ":\n";
				for(EntityType type : typeCount.keySet()){
					message += " " + type.toString() + ": " + typeCount.get(type).toString() + "\n";
				}
			}
			sender.sendMessage(message);
			return true;
		}
		return false;
	}

	@EventHandler (priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onMobSpawn(CreatureSpawnEvent event){
		if(event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
		if(!isHostile(event.getEntity())){
			return;
		}
		Chunk chunk = event.getLocation().getChunk();
		ChunkID id = new ChunkID(chunk);
		if(!chunkMobs.containsKey(id)){
			chunkMobs.put(id, new ChunkInfo(event.getLocation()));
		}
		if(chunkMobs.get(id).isTooManyMobs()){
			event.setCancelled(true);
		}
		for(Player listener : playerListeners){
			if(Math.abs(event.getLocation().distanceSquared(listener.getLocation())) <= 128 * 128){
				Location loc = event.getEntity().getLocation();
				String state = String.format("%s%s%s",
						ChatColor.BOLD.toString(),
						event.isCancelled() ? ChatColor.RED.toString() + "✗" : ChatColor.GREEN.toString() + "✓",
						ChatColor.RESET.toString());
				String message = String.format("%s %s @ %d, %d, %d", state, event.getEntity().getType().toString(),
						loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
				listener.sendMessage(message);
			}
		}
	}

	@EventHandler (priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkUnload(ChunkUnloadEvent event){
		chunkMobs.remove(new ChunkID(event.getChunk()));
	}

	//100% OC plz don't steal
	public static boolean isHostile(Entity entity) {
		//if(!entity.isValid()) return false; checks can happen before the entity spawns so check validity elsewhere
		switch (entity.getType()) {
			case CREEPER:
			case SKELETON:
			case SPIDER:
			case GIANT:
			case ZOMBIE:
			case SLIME:
			case GHAST:
			case PIG_ZOMBIE:
			case ENDERMAN:
			case CAVE_SPIDER:
			case SILVERFISH:
			case BLAZE:
			case MAGMA_CUBE:
			case ENDER_DRAGON:
			case WITHER:
			case WITHER_SKELETON:
			case WITCH:
			case ENDERMITE:
			case GUARDIAN:
			case STRAY:
			case HUSK:
			case ZOMBIE_VILLAGER:
			case VEX:
			case EVOKER:
			case SHULKER:
			case VINDICATOR:
				return true;
			default:
				return entity instanceof Monster;
		}
	}

	public class ChunkInfo{
		boolean tooManyMobs;
		int nextCheck;
		Location location; //a location within the chunk
		final int cooldown = 10; //seconds

		ChunkInfo(Location location){
			this.location = location.getChunk().getBlock(7,0,7).getLocation();
			tooManyMobs = true;
			nextCheck = counterSeconds + cooldown;
		}

		public boolean isTooManyMobs(){
			if(counterSeconds <= nextCheck){
				return tooManyMobs;
			}
			Collection<Entity> entityList = location.getWorld()
					.getNearbyEntities(location, localRange, 256, localRange);
			if(entityList.size() < localCap){
				return tooManyMobs = false;
			}
			int localMobs = 0;
			for(Entity entity : entityList){
				if(entity.isValid() && isHostile(entity)){
					if(++localMobs >= localCap){
						nextCheck = counterSeconds + cooldown; //wait 10 seconds before checking again if it's full
						return tooManyMobs = true;
					}
				}
			}
			return tooManyMobs = false;
		}
	}

	@EqualsAndHashCode
	public class ChunkID{
		World world;
		int x;
		int z;

		ChunkID(Chunk chunk){
			world = chunk.getWorld();
			x = chunk.getX();
			z = chunk.getZ();
		}
	}
}