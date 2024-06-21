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
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import javax.annotation.Nullable;
import java.io.*;
import java.security.MessageDigest;
import java.util.List;

@CommandAlias("disc")
public class DownloadCommand extends BaseCommand {

    private final YoutubeDownloader downloader;
    private final Encoder encoder;
    private final Plugin plugin;
    private final String dataPackAudioPath;
    private final ResourceManager resourceManager; // mcp
    private final String resourcePackFolder;
    private final String resourcePackAudioPath;
    private final File pluginAudioData;
    private final Gson gson;

    public DownloadCommand(Plugin plugin, String dataPackAudioFolder, ResourceManager resourceManager, String resourcePackFolder,
                           String resourcePackAudioPath, Gson gson) {

        downloader = new YoutubeDownloader();
        encoder = new Encoder();

        this.plugin = plugin;
        this.resourceManager = resourceManager;
        this.resourcePackFolder = resourcePackFolder;
        this.resourcePackAudioPath = resourcePackAudioPath;
        dataPackAudioPath = dataPackAudioFolder;
        this.gson = gson;

        pluginAudioData = new File(plugin.getDataFolder(), "audio");

    }

    @Default
    public void addAndGiveDisc(Player caller, String videoIdOrURL, @Optional String newName) {

        String name = addDisc(videoIdOrURL, newName);

        giveDisc(caller, name);

    }

    @Subcommand("add|a")
    public String addDisc(String videoIdOrURL, @Optional String newName) {
        String videoId;
        if (videoIdOrURL.contains("watch?v=")) // maybe better way?
            videoId = videoIdOrURL.substring(videoIdOrURL.indexOf("watch?v=") + 8);

        else // already an ID
            videoId = videoIdOrURL;

        /*File audio = */
        return downloadAudio(videoId, newName).getName().split("\\.")[0];
    }

    @Subcommand("give|g")
    public void giveDisc(Player caller, String name) {
        // maybe search internal list or sounds rp/dp folder for a file name containing given name

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
    private File downloadAudio(String videoId, @Nullable String newName) {
        RequestVideoInfo infoRequest = new RequestVideoInfo(videoId);
        Response<VideoInfo> infoResponse = downloader.getVideoInfo(infoRequest);
        VideoInfo video = infoResponse.data();

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

        return convertedAudio;
    }

    private void convertToOgg(File input, File output) {
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("libvorbis");
        audio.setBitRate(128000);
        audio.setChannels(2);
        audio.setSamplingRate(44100);

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("ogg");
        attrs.setAudioAttributes(audio);

        try {
            MultimediaObject inputMediaObject = new MultimediaObject(input);
            encoder.encode(inputMediaObject, output, attrs);
        } catch (EncoderException e) {
            e.printStackTrace();
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

    // this it the worst
    private void generateSoundsJson() {
        File[] audios = pluginAudioData.listFiles();

        if (audios == null)
            return;

        JsonObject sounds = new JsonObject();

        for (File audio : audios) {

            String soundName = audio.getName().split("\\.")[0];

            JsonObject sound = new JsonObject();
            sound.addProperty("name", "records/" + soundName);

            JsonArray soundsArray = new JsonArray();
            soundsArray.add(sounds);

            JsonObject disc = new JsonObject();
            disc.add("sounds", soundsArray);

            sounds.add("music_disc." + soundName, disc);
        }

        File soundsJson = new File(pluginAudioData, "sounds.json");

        try (Writer writer2 = new FileWriter(soundsJson)) {
            gson.toJson(sounds, writer2);
        } catch (IOException e) {
            e.printStackTrace();
        }

        resourceManager.addSpecificFileAtSpecificPlace(soundsJson, resourcePackFolder);
    }


    @Subcommand("search|s")
    public void searchSong(Player caller, boolean downloadTop, String query) {
        RequestSearchResult searchRequest = new RequestSearchResult(query)
                .type(TypeField.VIDEO)
//                .forceExactQuery(true)
                .sortBy(SortField.RELEVANCE); // maybe do view count

        SearchResult search = downloader.search(searchRequest).data();

        caller.sendMessage("Finding videos for query: \"" + search.autoCorrectedQuery() + "\"");
        List<SearchResultVideoDetails> searchResults = search.videos();

        if (downloadTop) {
            var video = searchResults.get(0);

            caller.sendMessage("Downloading: " + video.title() + " - " + video.author());
            downloadAudio(video.videoId(), null);
        }
        else // list search results
            for (SearchResultVideoDetails result : searchResults) {
                caller.sendMessage(result.title() + " - " + result.author() +
                        ChatColor.GREEN + ChatColor.ITALIC + " [ID: " + result.videoId() + "]");
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
