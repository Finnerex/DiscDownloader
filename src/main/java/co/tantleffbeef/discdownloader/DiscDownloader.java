package co.tantleffbeef.discdownloader;

import co.aikar.commands.BukkitCommandManager;
import co.tantleffbeef.mcplanes.ResourceApi;
import co.tantleffbeef.mcplanes.ResourceManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;


import java.io.*;
import java.util.jar.JarFile;

public class DiscDownloader extends JavaPlugin {

    private final String DATA_PACK_FOLDER = "/world/datapacks/DiscDownloader_DP/";
    private final String DP_AUDIO_PATH = "data/disc_downloader/jukebox_song/";
    private final String RESOURCE_PACK_FOLDER = "assets/minecraft/";
    private final String RP_AUDIO_PATH = "sounds/records/";
    private ResourceManager resourceManager;
    private Gson gson;


    @Override
    public void onEnable() {
        getLogger().info("Starting Disc Downloader");

        gson = new GsonBuilder().setPrettyPrinting().create();

        // use MCP Api for the resource pack (meaning this wont work if you dont got McPlanes)
        final var rApiProvider = getServer().getServicesManager().getRegistration(ResourceApi.class);
        if (rApiProvider == null)
            throw new RuntimeException("Can't find ResourceApi!");

        final var rApi = rApiProvider.getProvider();
        resourceManager = rApi.getResourceManager();

        initializeDataPack();

        try {
            initializeResourcePack();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // register the command
        BukkitCommandManager commandManager = new BukkitCommandManager(this);

        commandManager.registerCommand(
            new DownloadCommand(this, DATA_PACK_FOLDER + DP_AUDIO_PATH, resourceManager,
                    RESOURCE_PACK_FOLDER , RESOURCE_PACK_FOLDER + RP_AUDIO_PATH, gson));

    }

    private void initializeResourcePack() throws IOException {
        File audioFolder = new File(getDataFolder(), "audio");

        if (!audioFolder.exists()) {
            audioFolder.mkdirs();
            return;
        }

        File[] audios = audioFolder.listFiles();

        if (audios == null)
            return;

        JsonObject sounds = new JsonObject();

        for (File audio : audios) {
            resourceManager.addSpecificFileAtSpecificPlace(audio, RESOURCE_PACK_FOLDER + RP_AUDIO_PATH + audio.getName());

            String name = audio.getName().split("\\.")[0];

            JsonObject sound = new JsonObject();
            sound.addProperty("name", "records/" + name);

            JsonArray soundsArray = new JsonArray();
            soundsArray.add(sounds);

            JsonObject disc = new JsonObject();
            disc.add("sounds", soundsArray);

            sounds.add("music_disc." + name, disc);
        }

        File soundsJson = new File(audioFolder, "sounds.json");

        try (Writer writer = new FileWriter(soundsJson)) {
            gson.toJson(sounds, writer);
        }

    }

    private void initializeDataPack() {
        // make sure the directory exists, create it if it doesn't
        File audioDirectory = new File(DATA_PACK_FOLDER + DP_AUDIO_PATH);
        if (!audioDirectory.exists())
            audioDirectory.mkdirs();

        // add pack mcmeta file if it doesn't exist
        try {

            File packMcmeta = new File(DATA_PACK_FOLDER + "pack.mcmeta");
            if (packMcmeta.createNewFile()) {
                getLogger().info("creating mcmeta file");

                FileWriter writer = new FileWriter(packMcmeta);

                // neat text block
                writer.write(
                        """
                            {
                                "pack": {
                                    "description": "Autogenerated by DiscDownloader",
                                    "pack_format": 48
                                }
                            }
                            """);
                writer.close();

            }
            else
                getLogger().info("mcmeta already exists");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}