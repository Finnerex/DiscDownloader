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
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
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
        return downloadAudio(caller, videoId, newName).getName().split("\\.")[0];
    }

    @Subcommand("give|g")
    public void giveDisc(Player caller, String name) {
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

        String fileName = name + ".json";

        // me when linear search
        for (File f : jsonFiles) {
            if (f.getName().equals(fileName)) {
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

        fileName = name + ".ogg";

        // me when linear search
        for (File f : audioFiles) {
            if (f.getName().equals(fileName)) {
                f.delete();
                return;
            }
        }

        generateSoundsJson();

    }

    @Subcommand("list|l")
    public void listAudio(Player caller) {
        File[] files = new File(dataPackAudioPath).listFiles();

        if (files == null) {
            caller.sendMessage(ChatColor.RED + "No audio found!");
            return;
        }

        StringBuilder message = new StringBuilder();

        for (File f : files) {
            message.append(f.getName().split("\\.")[0]).append(", ");
        }

        message.delete(message.length() - 2, message.length());

        caller.sendMessage(message.toString());
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

        if (newName != null)
            downloadRequest.renameTo(newName);


        Response<File> downloadResponse = downloader.downloadVideoFile(downloadRequest);
        File rawAudio = downloadResponse.data();
        String name = rawAudio.getName().split("\\.")[0];

        File convertedAudio = new File(rawAudio.getParentFile(), name + ".ogg");
        convertToOgg(rawAudio, convertedAudio);
        rawAudio.delete();

        createJsonEntry(name, video.details().lengthSeconds());
        generateSoundsJson();

        resourceManager.addSpecificFileAtSpecificPlace(convertedAudio, resourcePackAudioPath + convertedAudio.getName());

        caller.sendMessage("Download complete!");

        return convertedAudio;
    }

    public void convertToOgg(File inputFile, File outputFile) {
        try {
            // Get the audio file
//            AudioFile audioFile = AudioFileIO.read(inputFile);
//            AudioHeader audioHeader = audioFile.getAudioHeader();

            // Get audio input stream from the input file
            AudioInputStream inputStream = AudioSystem.getAudioInputStream(inputFile);

            // Define the audio format for the OGG file
            AudioFormat baseFormat = inputStream.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );

            // Get the decoded audio input stream
            AudioInputStream decodedStream = AudioSystem.getAudioInputStream(decodedFormat, inputStream);

            // Write the decoded audio input stream to the output OGG file
            AudioSystem.write(decodedStream, new AudioFileFormat.Type("OGG", "ogg"), outputFile);

            System.out.println("Conversion to OGG completed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error during conversion: " + e.getMessage());
        }
    }

    private void createJsonEntry(String name, float lengthSeconds) {

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
                            .formatted(name, lengthSeconds, name)
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
            writer.write(soundObjects.deleteCharAt(soundObjects.length() - 1).toString());
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

    @Subcommand("reload|r")
    public void reloadResources(Player caller) {
        // Reload the player's resources with the new pack version

        // so probably
        resourceManager.compileResourcesAsync(plugin.getServer().getScheduler());

        resourceManager.sendResourcesToPlayer(caller);
    }

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
