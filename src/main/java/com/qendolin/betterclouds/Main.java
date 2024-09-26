package com.qendolin.betterclouds;

import com.google.gson.FieldNamingPolicy;
import com.mojang.blaze3d.platform.GlDebugInfo;
import com.qendolin.betterclouds.clouds.Debug;
import com.qendolin.betterclouds.compat.*;
import com.qendolin.betterclouds.platform.EventHooks;
import com.qendolin.betterclouds.platform.ModLoader;
import com.qendolin.betterclouds.platform.ModVersion;
import com.qendolin.betterclouds.renderdoc.RenderDoc;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL32;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class Main {
    public static final String MODID = "betterclouds";
    public static final boolean IS_DEV = ModLoader.isDevelopmentEnvironment();
    public static final boolean IS_CLIENT = ModLoader.isClientEnvironment();
    public static final NamedLogger LOGGER = new NamedLogger(LogManager.getLogger(MODID), !IS_DEV);

    public static GLCompat glCompat = null;
    public static ModVersion version = null;

    private static final Path CONFIG_PATH = ModLoader.getConfigDir().resolve("betterclouds-v1.json");
    private static ConfigClassHandler<Config> config;
    private static boolean isInitialized = false;

    public static void initGlCompat() {
        LOGGER.info("Initializing OpenGL compat");
        try {
            glCompat = new GLCompat(IS_DEV);
        } catch (Exception e) {
            Telemetry.INSTANCE.sendUnhandledException(e);
            throw e;
        }

        if (glCompat.isIncompatible()) {
            LOGGER.warn("Your GPU (or configuration) is not compatible with Better Clouds. Try updating your drivers?");
            LOGGER.info(" - Vendor:       {}", glCompat.getString(GL32.GL_VENDOR));
            LOGGER.info(" - Renderer:     {}", glCompat.getString(GL32.GL_RENDERER));
            LOGGER.info(" - GL Version:   {}", glCompat.getString(GL32.GL_VERSION));
            LOGGER.info(" - GLSL Version: {}", glCompat.getString(GL32.GL_SHADING_LANGUAGE_VERSION));
            LOGGER.info(" - Extensions:   {}", String.join(", ", glCompat.supportedCheckedExtensions));
            LOGGER.info(" - Functions:    {}", String.join(", ", glCompat.supportedCheckedFunctions));
        } else if (glCompat.isPartiallyIncompatible()) {
            LOGGER.warn("Your GPU is not fully compatible with Better Clouds.");
            for (String fallback : glCompat.usedFallbacks()) {
                LOGGER.info("- Using {} fallback", fallback);
            }
        }

        sendSystemDetailsTelemetry();
    }

    public static Config getConfig() {
        return config.instance();
    }

    public static boolean isProfilingEnabled() {
        return Debug.profileInterval > 0;
    }

    public static void debugChatMessage(String id, Object... args) {
        debugChatMessage(Text.translatable(debugChatMessageKey(id), args));
    }

    public static void debugChatMessage(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;
        client.inGameHud.getChatHud().addMessage(Text.literal("§e[§bBC§b§e]§r ").append(message));
    }

    public static String debugChatMessageKey(String id) {
        return MODID + ".message." + id;
    }

    public static ModVersion getVersion() {
        return version;
    }

    public static ConfigClassHandler<Config> getConfigHandler() {
        return config;
    }

    public static void initializeClientEvents() {
        EventHooks.instance.onClientStarted(client -> glCompat.enableDebugOutputSynchronousDev());
        EventHooks.instance.onWorldJoin(client -> {
            if (glCompat.isIncompatible()) {
                CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)
                    .execute(() -> client.execute(Main::sendGpuIncompatibleChatMessage));
            } else if (glCompat.isPartiallyIncompatible()) {
                CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)
                    .execute(() -> client.execute(Main::sendGpuPartiallyIncompatibleChatMessage));
            }
            if (HardwareCompat.isMaybeIncompatible()) {
                CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)
                    .execute(() -> client.execute(Main::sendHardwareMaybeIncompatibleChatMessage));
            }
            if (RenderDoc.isAvailable()) {
                Main.debugChatMessage("renderdoc.load.ready", RenderDoc.getAPIVersion());
            }
        });
        EventHooks.instance.onClientResourcesReload(() -> ShaderPresetLoader.INSTANCE);
        EventHooks.instance.onClientCommandRegistration(Commands::register);
    }

    public static void initializeClient() {
        if (!IS_CLIENT)
            throw new IllegalStateException("Minecraft environment is not 'client' but the client initializer was called");
        if(isInitialized) return;
        isInitialized = true;

        initConfig();
        loadConfig();

        version = ModLoader.getModVersion(MODID);

        DistantHorizonsCompat.initialize();
        IrisCompat.initialize();

        sendSystemDetailsTelemetry();

        if (!IS_DEV) return;
        LOGGER.info("Initialized in dev mode, performance might vary");
    }

    public static boolean initialized() {
        return isInitialized;
    }

    private static void initConfig() {
        if (!IS_CLIENT || config != null) return;

        config = ConfigClassHandler.createBuilder(Config.class)
            .id(Identifier.of(MODID, "betterclouds-v1"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                .appendGsonBuilder(b -> b
                    .setLenient()
                    .serializeNulls()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .setPrettyPrinting()
                    .registerTypeAdapter(Config.class, Config.INSTANCE_CREATOR)
                    .registerTypeAdapter(Config.ShaderConfigPreset.class, Config.ShaderConfigPreset.INSTANCE_CREATOR)
                    .registerTypeAdapter(RegistryKey.class, Config.REGISTRY_KEY_SERIALIZER))
                .setPath(CONFIG_PATH)
                .setJson5(false)
                .build())
            .build();
    }

    private static void loadConfig() {
        assert config != null;

        try {
            config.load();
            return;
        } catch (Exception loadException) {
            LOGGER.error("Failed to load config: ", loadException);
        }

        File file = CONFIG_PATH.toFile();
        if (file.exists() && file.isFile()) {
            String backupName = FilenameUtils.getBaseName(file.getName()) +
                "-backup-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) +
                "." + FilenameUtils.getExtension(file.getName());
            Path backup = Path.of(CONFIG_PATH.toAbsolutePath().getParent().toString(), backupName);
            try {
                Files.copy(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Created config backup at: {}", backup);
            } catch (Exception backupException) {
                LOGGER.error("Failed to create config backup: ", backupException);
            }
        } else if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            LOGGER.info("Deleted old config");
        }

        try {
            config.save();
            LOGGER.info("Created new config");
            config.load();
        } catch (Exception loadException) {
            LOGGER.error("Failed to load config again, please report this issue: ", loadException);
        }
    }

    private static void sendSystemDetailsTelemetry() {
        if(!isInitialized || glCompat == null) return;

        if (getConfig().lastTelemetryVersion >= Telemetry.VERSION) return;
        Telemetry.INSTANCE.sendSystemInfo()
            .whenComplete((success, throwable) -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (success && client != null) {
                    client.execute(() -> {
                        getConfig().lastTelemetryVersion = Telemetry.VERSION;
                        config.save();
                    });
                }
            });
    }

    public static void sendGpuIncompatibleChatMessage() {
        if (!getConfig().gpuIncompatibleMessageEnabled) return;
        debugChatMessage(
            Text.translatable(debugChatMessageKey("gpuIncompatible"))
                .append(Text.literal("\n - "))
                .append(Text.translatable(debugChatMessageKey("generic.disable"))
                    .styled(style -> style.withItalic(true).withUnderline(true).withColor(Formatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/betterclouds:config gpuIncompatibleMessage false")))));
    }

    public static void sendGpuPartiallyIncompatibleChatMessage() {
        if (!getConfig().gpuIncompatibleMessageEnabled) return;
        debugChatMessage(
            Text.translatable(debugChatMessageKey("gpuPartiallyIncompatible"))
                .append(Text.literal("\n - "))
                .append(Text.translatable(debugChatMessageKey("generic.disable"))
                    .styled(style -> style.withItalic(true).withUnderline(true).withColor(Formatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/betterclouds:config gpuIncompatibleMessage false")))));
    }

    public static void sendHardwareMaybeIncompatibleChatMessage() {
        if (!getConfig().gpuIncompatibleMessageEnabled) return;
        debugChatMessage(
            Text.translatable(debugChatMessageKey("hwMaybeIncompatible"), GlDebugInfo.getCpuInfo(), GlDebugInfo.getRenderer())
                .append(Text.literal("\n - "))
                .append(Text.translatable(debugChatMessageKey("generic.disable"))
                    .styled(style -> style.withItalic(true).withUnderline(true).withColor(Formatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/betterclouds:config gpuIncompatibleMessage false")))));
    }
}
