package co.tantleffbeef.discdownloader;

import co.aikar.commands.BukkitCommandManager;
import co.tantleffbeef.mcplanes.ResourceApi;
import co.tantleffbeef.mcplanes.ResourceManager;
import com.google.gson.Gson;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;

public class DiscDownloader extends JavaPlugin {

    private final String DATA_PACK_FOLDER = "world/datapacks/DiscDownloader_DP/";
    private final String DP_AUDIO_PATH = "data/disc_downloader/jukebox_song/";
    private final String RESOURCE_PACK_FOLDER = "assets/minecraft/";
    private final String RP_AUDIO_PATH = "sounds/records/";
    private ResourceManager resourceManager;
    private Gson gson;


    @Override
    public void onEnable() {
        getLogger().info("Starting Disc Downloader");

        getConfig().addDefault("max-song-length-seconds", 300);

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
                    RESOURCE_PACK_FOLDER , RESOURCE_PACK_FOLDER + RP_AUDIO_PATH));

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

        StringBuilder soundObjects = new StringBuilder();
        for (File audio : audios) {
            resourceManager.addSpecificFileAtSpecificPlace(audio, RESOURCE_PACK_FOLDER + RP_AUDIO_PATH + audio.getName());

            String name = audio.getName().split("\\.")[0];

            soundObjects.append(
                    """
                        
                        "music_disc.%s": {
                            "sounds": [ { "name" : "records/%s" } ]
                        },
                    """.formatted(name, name)
            );
        }


        File soundsJson = new File(audioFolder, "sounds.json");

        try (Writer writer = new FileWriter(soundsJson)) {
            writer.write("{\n");
            writer.write(soundObjects.deleteCharAt(soundObjects.length() - 1).toString());
            writer.write("\n}");
//            writer.flush();
        }

        resourceManager.addSpecificFileAtSpecificPlace(soundsJson, RESOURCE_PACK_FOLDER + "sounds.json");

    }

    private void initializeDataPack() {
        // make sure the directory exists, create it if it doesn't
        File audioDirectory = new File(DATA_PACK_FOLDER + DP_AUDIO_PATH);
        if (!audioDirectory.exists())
            getLogger().info("Was able to create DP Directories: " + audioDirectory.mkdirs());

        // add pack mcmeta file if it doesn't exist
        try {

            File packMcmeta = new File(DATA_PACK_FOLDER + "pack.mcmeta");
            if (!packMcmeta.exists()) {

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
