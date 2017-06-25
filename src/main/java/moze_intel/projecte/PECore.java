package moze_intel.projecte;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.config.NBTWhitelistParser;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.handlers.InternalAbilities;
import moze_intel.projecte.handlers.InternalTimers;
import moze_intel.projecte.impl.AlchBagImpl;
import moze_intel.projecte.impl.IMCHandler;
import moze_intel.projecte.impl.KnowledgeImpl;
import moze_intel.projecte.impl.TransmutationOffline;
import moze_intel.projecte.integration.Integration;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.ThreadCheckUUID;
import moze_intel.projecte.network.ThreadCheckUpdate;
import moze_intel.projecte.network.commands.ProjectECMD;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.proxies.IProxy;
import moze_intel.projecte.utils.AchievementHandler;
import moze_intel.projecte.utils.DummyIStorage;
import moze_intel.projecte.utils.GuiHandler;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod(modid = PECore.MODID, name = PECore.MODNAME, version = PECore.VERSION, acceptedMinecraftVersions = "[1.12,]", dependencies = PECore.DEPS)
public class PECore
{
	public static final String MODID = "projecte";
	public static final String MODNAME = "ProjectE";
	public static final String VERSION = "@VERSION@";
	public static final String DEPS = "required-after:forge@[13.20.0.2253,);after:baubles@[1.3.3,);after:jei@[4.6.0,)";
	public static final GameProfile FAKEPLAYER_GAMEPROFILE = new GameProfile(UUID.fromString("590e39c7-9fb6-471b-a4c2-c0e539b2423d"), "[" + MODNAME + "]");
	public static File CONFIG_DIR;
	public static File PREGENERATED_EMC_FILE;
	public static boolean DEV_ENVIRONMENT;
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Instance(MODID)
	public static PECore instance;
	
	@SidedProxy(clientSide = "moze_intel.projecte.proxies.ClientProxy", serverSide = "moze_intel.projecte.proxies.ServerProxy")
	public static IProxy proxy;

	public static final List<String> uuids = new ArrayList<>();

	public static void debugLog(String msg, Object... args)
	{
		if (DEV_ENVIRONMENT || ProjectEConfig.misc.debugLogging)
		{
			LOGGER.info(msg, args);
		} else
		{
			LOGGER.debug(msg, args);
		}
	}

	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		DEV_ENVIRONMENT = ((Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment"));

		CONFIG_DIR = new File(event.getModConfigurationDirectory(), MODNAME);
		
		if (!CONFIG_DIR.exists())
		{
			CONFIG_DIR.mkdirs();
		}

		PREGENERATED_EMC_FILE = new File(CONFIG_DIR, "pregenerated_emc.json");

		PacketHandler.register();

		AlchBagImpl.init();
		KnowledgeImpl.init();
		CapabilityManager.INSTANCE.register(InternalTimers.class, new DummyIStorage<>(), InternalTimers::new);
		CapabilityManager.INSTANCE.register(InternalAbilities.class, new DummyIStorage<>(), () -> new InternalAbilities(null));
		
		NetworkRegistry.INSTANCE.registerGuiHandler(PECore.instance, new GuiHandler());

		proxy.registerKeyBinds();
		ObjHandler.register();

		proxy.registerRenderers();

	}
	
	@EventHandler
	public void load(FMLInitializationEvent event)
	{
		proxy.registerLayerRenderers();
		AchievementHandler.init();
	}
	
	@EventHandler
	public void postInit(FMLPostInitializationEvent event)
	{
		NBTWhitelistParser.init();
		proxy.initializeManual();
		
		Integration.init();
	}
	
	@Mod.EventHandler
	public void serverStarting(FMLServerStartingEvent event)
	{
		event.registerServerCommand(new ProjectECMD());

		if (!ThreadCheckUpdate.hasRunServer())
		{
			new ThreadCheckUpdate(true).start();
		}

		if (!ThreadCheckUUID.hasRunServer())
		{
			new ThreadCheckUUID(true).start();
		}

		long start = System.currentTimeMillis();

		CustomEMCParser.init();

		LOGGER.info("Starting server-side EMC mapping.");

		EMCMapper.map();

		LOGGER.info("Registered " + EMCMapper.emc.size() + " EMC values. (took " + (System.currentTimeMillis() - start) + " ms)");
	}

	@Mod.EventHandler
	public void serverStopping (FMLServerStoppingEvent event)
	{
		TransmutationOffline.cleanAll();
	}
	
	@Mod.EventHandler
	public void serverQuit(FMLServerStoppedEvent event)
	{
		Transmutation.clearCache();
		LOGGER.debug("Cleared cached tome knowledge");

		EMCMapper.clearMaps();
		LOGGER.info("Completed server-stop actions.");
	}

	@Mod.EventHandler
	public void onIMCMessage(FMLInterModComms.IMCEvent event)
	{
		for (FMLInterModComms.IMCMessage msg : event.getMessages())
		{
			IMCHandler.handleIMC(msg);
		}
	}
}
