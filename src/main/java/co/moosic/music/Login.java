package co.moosic.music;

import com.kaaz.configuration.ConfigurationBuilder;
import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;


import javax.security.auth.login.LoginException;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;


public class Login {
    static JDA Jda;
    static TrackScheduler scheduler;
    private static int PORT = 9090;

    public static void main(String[] args) throws Exception {
        try {
            new ConfigurationBuilder(Config.class, new File("bot.cfg")).build(true);
        } catch (Exception exc) {
            exc.printStackTrace();
            System.exit(1);
        }
        try {
            if (isNas()) {
                System.out.println("Enabling native audio sending");
                Jda = JDABuilder.createDefault(Config.discord_token)
                        .setAudioSendFactory(new NativeAudioSendFactory())
                        .build().awaitReady();
            } else {
                Jda = JDABuilder.createDefault(Config.discord_token)
                        .build().awaitReady();
            }
            Jda.addEventListener(new MessageHandler());
            System.out.println("Use this url to add me:\n" + "https://discordapp.com/oauth2/authorize?client_id=" + Jda.getSelfUser().getId() + "&scope=bot");
        } catch (LoginException | InterruptedException e) {
            e.printStackTrace();
            System.exit(1);
        }
        AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
        playerManager.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
        System.out.println("Created new player manager");
        VoiceChannel channel = Jda.getVoiceChannelById(Config.voice_channel_id);
        if (channel == null) {
            System.out.println("Could not find the channel, make sure the ID is correct and that the bot can see it.");
            System.exit(1);
        }
        AudioManager audioManager = channel.getGuild().getAudioManager();
        try {
            audioManager.openAudioConnection(channel);
            System.out.println("Joined designated voice channel " + channel.getName());
        } catch (Exception ex) {
            System.out.println("Failed to join the voice channel! " + ex.getMessage());
            System.exit(1);
        }
        AudioPlayer player = playerManager.createPlayer();
        audioManager.setSendingHandler(new AudioPlayerSendHandler(player));
        scheduler = new TrackScheduler(player, playerManager);
        player.addListener(scheduler);
        player.setVolume(Config.volume);
        final ServerSocket server = new ServerSocket(PORT);
        System.out.println("Listening on port 8080...");
        while (true) {
            try (Socket client = server.accept()) {
                InputStreamReader reader = new InputStreamReader(client.getInputStream());
                BufferedReader br = new BufferedReader(reader);
                String line = br.readLine();
                String path = "";
                while (!line.isEmpty()) {
                    System.out.println(line);
                    if (line.contains("POST")) {
                        path = line;
                    }
                    line = br.readLine();
                }
                path = path.replaceAll("POST ", "");
                path = path.replaceAll(" HTTP/1.1", "");
                System.out.println(path);
                if (path.contains("/play")) {
                    // play song
                    String song = path.replaceAll("/play/", "");
                    System.out.println(song);
                    scheduler.PlayTrack(song);

                } else if (path.contains("/stop")) {
                    // Stop
                }
                String response = "HTTP/1.1 200 OK\n" +
                        "Access-Control-Allow-Origin: *\n" +
                        "Connection: Closed";
                client.getOutputStream().write(response.getBytes("UTF-8"));
            } catch (Exception e) {
                System.err.println(e);
            }
        }
    }

    private static boolean isNas() {
        String os = System.getProperty("os.name");
        return (os.contains("Windows") || os.contains("Linux"))
                && !System.getProperty("os.arch").equalsIgnoreCase("arm")
                && !System.getProperty("os.arch").equalsIgnoreCase("arm-linux");

    }
}
