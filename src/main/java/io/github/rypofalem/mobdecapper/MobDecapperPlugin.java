package io.github.rypofalem.mobdecapper;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;

import lombok.EqualsAndHashCode;

public class MobDecapperPlugin extends JavaPlugin implements Listener {

	int localCap = 50;
	int localRange = 128;
	HashMap<ChunkID, ChunkInfo> chunkMobs;
	int counter = 0; //one second counter


	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		chunkMobs = new HashMap<>();
		Bukkit.getScheduler().runTaskTimer(this, () -> counter++, 1, 20);
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
		if(args != null && args.length > 0 && args[0].equalsIgnoreCase("reload")){
			loadConfig();
			sender.sendMessage("Reloaded MobDecapper config.");
			return true;
		}
		return false;
	}

	@EventHandler (priority = EventPriority.LOW, ignoreCancelled = true)
	public void onMobSpawn(CreatureSpawnEvent event){
		if(event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
		if(!isHostile(event.getEntity())) return;
		Chunk chunk = event.getLocation().getChunk();
		ChunkID id = new ChunkID(chunk);
		if(!chunkMobs.containsKey(id)){
			chunkMobs.put(id, new ChunkInfo(event.getLocation()));
		}
		if(chunkMobs.get(id).isTooManyMobs()){
			event.setCancelled(true);
		}
	}

	@EventHandler (priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkUnload(ChunkUnloadEvent event){
		chunkMobs.remove(new ChunkID(event.getChunk()));
	}

	//100% OC plz don't steal
	public static boolean isHostile(Entity entity) {
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
			nextCheck = counter;
		}

		ChunkInfo(Chunk chunk){
			this(chunk.getBlock(7,0,7).getLocation());
		}

		public boolean isTooManyMobs(){
			if(counter <= nextCheck) return tooManyMobs;
			Collection<Entity> entityList = location.getWorld().getNearbyEntities(location, localRange, 256, localRange);
			if(entityList.size() < localCap) return false;
			int localMobs = 0;
			for(Entity entity : entityList){
				if(entity.isValid() && isHostile(entity)){
					if(++localMobs >= localCap){
						tooManyMobs = true;
						nextCheck = counter + cooldown;
						return true;
					}
				}
			}
			nextCheck = counter + cooldown;
			return false;
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

		ChunkID(Location location){
			world = location.getWorld();
			x = location.getChunk().getX();
			z = location.getChunk().getZ();
		}
	}
}