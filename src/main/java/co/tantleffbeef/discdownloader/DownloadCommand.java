package co.tantleffbeef.discdownloader;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import co.tantleffbeef.mcplanes.ResourceManager;
import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.request.RequestSearchResult;
import com.github.kiulian.downloader.downloader.request.RequestVideoFileDownload;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.model.search.SearchResult;
import com.github.kiulian.downloader.model.search.SearchResultVideoDetails;
import com.github.kiulian.downloader.model.search.field.SortField;
import com.github.kiulian.downloader.model.search.field.TypeField;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.JukeboxSong;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.io.*;
import java.util.List;

@CommandAlias("disc")
public class DownloadCommand extends BaseCommand {

    private final YoutubeDownloader downloader;
    private final Plugin plugin;
    private final String dataPackAudioPath;
    private final ResourceManager resourceManager; // mcp
    private final String resourcePackFolder;
    private final String resourcePackAudioPath;
    private final File pluginAudioData;
    private final int maxSongLengthSeconds;

    public DownloadCommand(Plugin plugin, String dataPackAudioFolder, ResourceManager resourceManager, String resourcePackFolder,
                           String resourcePackAudioPath) {

        downloader = new YoutubeDownloader();

        this.plugin = plugin;
        this.resourceManager = resourceManager;
        this.resourcePackFolder = resourcePackFolder;
        this.resourcePackAudioPath = resourcePackAudioPath;
        dataPackAudioPath = dataPackAudioFolder;

        pluginAudioData = new File(plugin.getDataFolder(), "audio");

        maxSongLengthSeconds = plugin.getConfig().getInt("max-song-length-seconds");

    }

    @Default // might not work because resources might have to be reloaded for disc to be given
    public void addAndGiveDisc(Player caller, String videoIdOrURL, @Optional String newName) {

        String name = addDisc(caller, videoIdOrURL, newName);

        giveDisc(caller, name);

    }

    @Subcommand("add|a")
    public String addDisc(Player caller, String videoIdOrURL, @Optional String newName) {
        String videoId;
        if (videoIdOrURL.contains("watch?v=")) // maybe better way?
            videoId = videoIdOrURL.substring(videoIdOrURL.indexOf("watch?v=") + 8);

        else // already an ID
            videoId = videoIdOrURL;

        /*File audio = */
        caller.sendMessage("Your disc will be available next time the server restarts.");
        return downloadAudio(caller, videoId, newName).getName().split("\\.")[0];
    }

    @Subcommand("give|g")
    public void giveDisc(Player caller, String name) {
        name = name.replace(' ', '_');

//        ItemStack disc = new ItemStack(Material.MUSIC_DISC_13);
//
//        ItemMeta meta = disc.getItemMeta();
//        JukeboxPlayableComponent component = meta.getJukeboxPlayable();
//        component.setSongKey(new NamespacedKey(plugin, name));
//
//        meta.setJukeboxPlayable(component);
//
//        disc.setItemMeta(meta);
//
//        caller.getInventory().addItem(disc);
//        caller.updateInventory();

        // give the disc to the player
         plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                "give " + caller.getDisplayName() +
                " minecraft:music_disc_13[jukebox_playable={song:'disc_downloader:" + name + "'}]"
         );
    }

    @Subcommand("remove|delete|d")
    public void removeAudio(Player caller, String name) {

        // remove from datapack
        File[] jsonFiles = new File(dataPackAudioPath).listFiles();

        if (jsonFiles == null) {
            caller.sendMessage(ChatColor.RED + "No audio found!");
            return;
        }

        // me when linear search
        for (File f : jsonFiles) {
            if (f.getName().contains(name)) {
                f.delete();
                return;
            }
        }

        // remove from resource pack
        File[] audioFiles = pluginAudioData.listFiles();

        if (audioFiles == null) {
            caller.sendMessage(ChatColor.RED + "No audio found!");
            return;
        }

        // me when linear search
        for (File f : audioFiles) {
            if (f.getName().contains(name)) {
                f.delete();
                return;
            }
        }

        generateSoundsJson();

    }

    @Subcommand("list|l")
    public void listAudio(Player caller) {
        File[] files = new File(dataPackAudioPath).listFiles();

        if (files == null || files.length < 1) {
            caller.sendMessage(ChatColor.RED + "No audio found!");
            return;
        }

        StringBuilder message = new StringBuilder();

        for (File f : files) {
            message.append(f.getName().split("\\.")[0]).append(", ");
        }

        message.delete(message.length() - 2, message.length());

        caller.sendMessage(message.toString());

        plugin.getServer().getRegistry(JukeboxSong.class).stream().forEach((song) -> caller.sendMessage(song.getKey().toString()));
    }

    // TODO: make async?
    private File downloadAudio(Player caller, String videoId, @Nullable String newName) {
        RequestVideoInfo infoRequest = new RequestVideoInfo(videoId);
        Response<VideoInfo> infoResponse = downloader.getVideoInfo(infoRequest);
        VideoInfo video = infoResponse.data();

        if (video.details().lengthSeconds() > maxSongLengthSeconds) {
            caller.sendMessage(ChatColor.RED + "This is longer than the maximum length (" + maxSongLengthSeconds + " seconds)");
            throw new RuntimeException("Video submitted with too long length"); // could probably return something but would be difficult
        }

        RequestVideoFileDownload downloadRequest = new RequestVideoFileDownload(video.bestAudioFormat())
                .saveTo(pluginAudioData)
                .overwriteIfExists(true);

        String name;
        String description;
        if (newName != null) {
            description = newName;
            name = newName.replace(' ', '_').toLowerCase().replace("'", "");
        } else {
            description = video.details().title();
            name = video.details().title().replace(' ', '_').toLowerCase().replace("'", "");
        }

        downloadRequest.renameTo(name);

        Response<File> downloadResponse = downloader.downloadVideoFile(downloadRequest);
        File rawAudio = downloadResponse.data();
//        String name = rawAudio.getName().split("\\.")[0];

        File convertedAudio = new File(rawAudio.getParentFile(), name + ".ogg");
        convertToOgg(rawAudio.getPath(), convertedAudio.getPath());
        rawAudio.delete();

        createJsonEntry(name, video.details().lengthSeconds(), description);
        generateSoundsJson();

        resourceManager.addSpecificFileAtSpecificPlace(convertedAudio, resourcePackAudioPath + convertedAudio.getName());

        caller.sendMessage("Download complete!");

        return convertedAudio;
    }


public static void convertToOgg(String inputFilePath, String outputFilePath) {
    // Construct the ffmpeg command
    String[] command = {"ffmpeg", "-i", inputFilePath, "-c:a", "libvorbis", "-ac", "1", "-q:a", "4", outputFilePath};

    // Execute the command
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    Process process;
    try {
        process = processBuilder.start();
        int exitCode = process.waitFor(); // perhaps async that bih
//        if (exitCode == 0)
//            System.out.println("Conversion successful.");
//        else
//            System.err.println("Error during conversion. Exit code: " + exitCode);

    } catch (IOException | InterruptedException e) {
        e.printStackTrace();
    }
}

    private void createJsonEntry(String name, float lengthSeconds, String description) {

        try (Writer writer = new FileWriter(dataPackAudioPath + name + ".json")) {

            writer.write(
                    """
                    {
                        "comparator_output" : 1,
                        "description" : "%s",
                        "length_in_seconds" : %f,
                        "sound_event" : {
                            "sound_id" : "minecraft:music_disc.%s"
                        }
                    }
                    """
                    .formatted(description, lengthSeconds, name)
            );

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // this is the worst
    private void generateSoundsJson() {
        File[] audios = pluginAudioData.listFiles();

        if (audios == null)
            return;

        StringBuilder soundObjects = new StringBuilder();
        for (File audio : audios) {
            resourceManager.addSpecificFileAtSpecificPlace(audio, resourcePackAudioPath + audio.getName());

            String name = audio.getName().split("\\.")[0];

            soundObjects.append(
                    """
                        
                        "music_disc.%s": {
                            "sounds": [ { "name" : "records/%s" } ]
                        },
                    """.formatted(name, name)
            );
        }


        File soundsJson = new File(plugin.getDataFolder(), "sounds.json");

        try (Writer writer = new FileWriter(soundsJson)) {
            writer.write("{\n");
            if (soundObjects.length() > 0)
                writer.write(soundObjects.deleteCharAt(soundObjects.lastIndexOf(",")).toString());
            writer.write("\n}");
//            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        resourceManager.addSpecificFileAtSpecificPlace(soundsJson, resourcePackFolder + "sounds.json");
    }


    @Subcommand("search|s")
    public void searchSong(Player caller, String query, @Optional Boolean downloadTop) {
        RequestSearchResult searchRequest = new RequestSearchResult(query)
                .type(TypeField.VIDEO)
//                .forceExactQuery(true)
                .sortBy(SortField.RELEVANCE); // maybe do view count

        SearchResult search = downloader.search(searchRequest).data();

        caller.sendMessage("Finding videos for query: \"" + search.autoCorrectedQuery() + "\"");
        List<SearchResultVideoDetails> searchResults = search.videos();

        if (downloadTop == null || !downloadTop) { // list search results
            for (SearchResultVideoDetails result : searchResults) {
                caller.sendMessage(result.title() + " - " + result.author() +
                        ChatColor.GREEN + ChatColor.ITALIC + " [ID: " + result.videoId() + "]");
            }
        } else { // download the top video
            var video = searchResults.get(0);

            caller.sendMessage("Downloading: " + video.title() + " - " + video.author());
            downloadAudio(caller, video.videoId(), null);
        }

    }

//    @Subcommand("reload|r")
//    public void reloadResources(Player caller) {
//        // Reload the player's resources with the new pack version
//
//        plugin.getServer().reloadData();
//
//        // so probably
//        resourceManager.compileResourcesAsync(plugin.getServer().getScheduler());
//
////        resourceManager.sendResourcesToPlayer(caller);
//    }

    @Subcommand("purgeAll")
    public void purgeAll(Player caller) {
        if (!caller.isOp()) {
            caller.sendMessage(ChatColor.RED + "You do not have permission to do that!");
            return;
        }

        // delete all datapack entries
        File[] jsonFiles = new File(dataPackAudioPath).listFiles();
        if (jsonFiles == null)
            return;

        for (File f : jsonFiles) {
            f.delete();
        }

        // delete all resource pack files
        File[] audioFiles = pluginAudioData.listFiles();
        if (audioFiles == null)
            return;

        for (File f : audioFiles) {
            f.delete();
        }

        generateSoundsJson();
        // files in the actual resource pack will be deleted once the server restarts

    }

}
