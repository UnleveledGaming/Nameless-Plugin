package com.namelessmc.plugin.NamelessSpigot;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.namelessmc.NamelessAPI.NamelessAPI;
import com.namelessmc.plugin.NamelessSpigot.commands.CommandWithArgs;
import com.namelessmc.plugin.NamelessSpigot.commands.GetNotificationsCommand;
import com.namelessmc.plugin.NamelessSpigot.commands.GetUserCommand;
import com.namelessmc.plugin.NamelessSpigot.commands.NamelessCommand;
import com.namelessmc.plugin.NamelessSpigot.commands.RegisterCommand;
import com.namelessmc.plugin.NamelessSpigot.commands.ReportCommand;
import com.namelessmc.plugin.NamelessSpigot.commands.SetGroupCommand;
import com.namelessmc.plugin.NamelessSpigot.hooks.MVdWPlaceholderUtil;
import com.namelessmc.plugin.NamelessSpigot.hooks.PAPIPlaceholderUtil;
import com.namelessmc.plugin.NamelessSpigot.player.PlayerEventListener;

public class NamelessPlugin extends JavaPlugin {

	private static NamelessPlugin instance;

	public static URL baseApiURL;

	boolean useGroups = false;
	
	@Override
	public void onEnable() {
		instance = this;
		
		try {
			Config.initialize();
		} catch (IOException e) {
			Chat.log(Level.SEVERE, "Unable to load config.");
			e.printStackTrace();
			return;
		}
			
		if (!checkConnection()) return;
			
		initHooks();
			
		// Connection is successful, move on with registering listeners and commands.
		registerCommands();
		getServer().getPluginManager().registerEvents(new PlayerEventListener(), this);
			
		// Start saving data files every 15 minutes
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new SaveConfig(), 5*60*20, 5*60*20);
	}
	
	@Override
	public void onDisable() {
		// Save all configuration files that require saving
		try {
			for (Config config : Config.values()) {
				if (config.autoSave()) config.saveConfig();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private boolean checkConnection() {
		YamlConfiguration config = Config.MAIN.getConfig();
		String url = config.getString("api-url");
		if (url.equals("")) {
			Chat.log(Level.SEVERE, "No API URL set in the NamelessMC configuration. Nothing will work until you set the correct url.");
			return false; // Prevent registering of commands, listeners, etc.
		} else {
			try {
				baseApiURL = new URL(url);
			} catch (MalformedURLException e) {
				// There is an exception, so the connection was not successful.
				Chat.log(Level.SEVERE, "Invalid API Url/Key. Nothing will work until you set the correct url.");
				Chat.log(Level.SEVERE, "Error: " + e.getMessage());
				return false; // Prevent registering of commands, listeners, etc.
			}

			Exception exception = NamelessAPI.checkWebAPIConnection(baseApiURL);
			if (exception != null) {
				// There is an exception, so the connection was unsuccessful.
				Chat.log(Level.SEVERE, "Invalid API Url/Key. Nothing will work until you set the correct url.");
				Chat.log(Level.SEVERE, "Error: " + exception.getMessage());
				return false; // Prevent registering of commands, listeners, etc.
			}
		}
		return true;
	}

	// Currently disabled.
	/*public void checkForUpdate() {
		if (getAPI().getConfigManager().getConfig().getBoolean("update-checker")) {
			UpdateChecker updateChecker = new UpdateChecker(this);
			if (updateChecker.updateNeeded()) {
				for (String msg : updateChecker.getConsoleUpdateMessage()) {
					NamelessChat.sendToLog(NamelessMessages.PREFIX_WARNING, msg);
				}
			} else {
				NamelessChat.sendToLog(NamelessMessages.PREFIX_INFO, "&aFound no new updates!");
			}
		} else {
			NamelessChat.sendToLog(NamelessMessages.PREFIX_WARNING,
					"&CIt is recommended to enable update checker.");
		}
	}*/

	private void registerCommands() {
		getServer().getPluginCommand("nameless").setExecutor(new NamelessCommand());
		
		YamlConfiguration commandsConfig = Config.COMMANDS.getConfig();
		
		try {
			Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
			field.setAccessible(true);
			CommandMap map = (CommandMap) field.get(Bukkit.getServer());
			
			String name = this.getName(); //Get name of plugin from config.yml just in case we ever change it

			boolean subcommands = Config.COMMANDS.getConfig().getBoolean("subcommands.enabled", true);
			boolean individual = Config.COMMANDS.getConfig().getBoolean("individual.enabled", true);

			if (individual) {
				if (commandsConfig.getBoolean("enable-registration")) {
					map.register(name, new RegisterCommand(commandsConfig.getString("individual.register")));
				}

				map.register(name, new GetUserCommand(commandsConfig.getString("individual.user-info")));

				map.register(name, new GetNotificationsCommand(commandsConfig.getString("individual.notifications")));

				map.register(name, new SetGroupCommand(commandsConfig.getString("individual.set-group")));

				if (commandsConfig.getBoolean("enable-reports")) {
					map.register(name, new ReportCommand(commandsConfig.getString("individual.report")));
				}
			}

			if (subcommands) {
				map.register(name, new CommandWithArgs(commandsConfig.getString("subcommands.main")));
			}
			
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
	}

	private void initHooks() {
		if (Bukkit.getPluginManager().isPluginEnabled("MVdWPlaceholderAPI")) {
			MVdWPlaceholderUtil placeholders = new MVdWPlaceholderUtil();
			placeholders.hook();
		}
		
		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			PAPIPlaceholderUtil placeholders = new PAPIPlaceholderUtil();
			placeholders.hook();
		}
	}

	public static NamelessPlugin getInstance() {
		return instance;
	}

	public static class SaveConfig implements Runnable {

		@Override
		public void run() {
			NamelessPlugin plugin = NamelessPlugin.getInstance();
			plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
				try {
					for (Config config : Config.values()) {
						if (config.autoSave())
							config.saveConfig();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		}

	}

}