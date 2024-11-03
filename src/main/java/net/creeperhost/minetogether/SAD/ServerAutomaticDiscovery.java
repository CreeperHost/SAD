package net.creeperhost.minetogether.SAD;
import net.creeperhost.minetogether.SAD.payloads.Advertise;
import net.creeperhost.minetogether.SAD.payloads.Enquiry;
import net.creeperhost.minetogether.SAD.responses.Server;
import com.google.gson.Gson;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
@Mod(ServerAutomaticDiscovery.MODID)
public class ServerAutomaticDiscovery
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "mt_sad";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static CompletableFuture<?> multiplayerScreenUpdateThread;
    public static boolean hasUpdated = false;
    public static HashMap<String, ServerData> discoveredServers = new HashMap<>();
    private static Gson gson = new Gson();

    public ServerAutomaticDiscovery(IEventBus modEventBus, ModContainer modContainer)
    {
        NeoForge.EVENT_BUS.register(this);
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void screenInit(ScreenEvent.Init.Post event)
    {
        if (event.getScreen() instanceof JoinMultiplayerScreen) {
            hasUpdated = true;
            if (multiplayerScreenUpdateThread == null) {
                multiplayerScreenUpdateThread = CompletableFuture.runAsync(() -> {
                    while (Minecraft.getInstance().isRunning()) {
                        if (Minecraft.getInstance().screen instanceof JoinMultiplayerScreen screen) {
                            if (hasUpdated) {
                                List<String> keys = new ArrayList<>();
                                ServerList newList = new ServerList(Minecraft.getInstance());
                                for (int i = 0; i < screen.servers.size(); i++) {
                                    ServerData server = screen.servers.get(i);
                                    keys.add(server.ip);
                                    newList.add(server, false);
                                }
                                discoveredServers.forEach((key, serverData) -> {
                                    if (!keys.contains(key)) {
                                        newList.add(serverData, false);
                                    }
                                });
                                hasUpdated = false;
                                screen.serverSelectionList.updateOnlineServers(newList);
                            }
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event)
    {
        if(event.getServer() != null && event.getServer().isDedicatedServer()) {
            CompletableFuture.runAsync(() -> {
                LOGGER.info("Beginning MineTogether SAD broadcasting");
                while (event.getServer().isRunning()) {
                    try {
                        doServerAdvertisment(event.getServer());
                        Thread.sleep(120000);
                    } catch (InterruptedException | MalformedURLException | ProtocolException | URISyntaxException e) {
                        LOGGER.error("Unable to advertise server; {}", e);
                    } catch (IOException e) {
                        LOGGER.error(e.getMessage());
                    }
                }
            });
        }
    }

    private static List<Server> doServerEnquiry(UUID uuid) throws IOException, InterruptedException, URISyntaxException {
        Enquiry payload = new Enquiry();
        payload.id = uuid.toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("https://api.creeper.host/minetogether/sad"))
                .headers("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        net.creeperhost.minetogether.SAD.responses.Enquiry enquiry = gson.fromJson(response.body(), net.creeperhost.minetogether.SAD.responses.Enquiry.class);
        return enquiry.servers;
    }
    private static void doServerAdvertisment(MinecraftServer server) throws IOException, URISyntaxException, InterruptedException {
        Advertise payload = new Advertise();
        String[] ops = server.getPlayerList().getOps().getUserList();
        String[] allowlist = server.getPlayerList().getWhiteList().getUserList();
        for (String user : ops) {
            server.getProfileCache().get(user).ifPresent(profile -> payload.targets.add(profile));
        }
        for (String user : allowlist) {
            server.getProfileCache().get(user).ifPresent(profile -> payload.targets.add(profile));
        }
        payload.serverPort = server.getPort();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("https://api.creeper.host/minetogether/sad"))
                .headers("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();
        HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            CompletableFuture.runAsync(() -> {
                LOGGER.info("Beginning MineTogether SAD enquiries");
                while (Minecraft.getInstance().isRunning()) {
                    UUID id = Minecraft.getInstance().getUser().getProfileId();
                    try {
                        List<Server> servers = doServerEnquiry(id);
                        HashMap<String, ServerData> replacementDiscovered = new HashMap<>();
                        if(!servers.isEmpty()) {
                            for (Server server : servers) {
                                String ipStr = server.ip + ":" + server.port;
                                ServerData mojangServerData = new ServerData(server.name, ipStr, ServerData.Type.REALM);//TODO: Select appropriate thing here
                                replacementDiscovered.put(ipStr, mojangServerData);
                            }
                            if (discoveredServers.size() != replacementDiscovered.size()) {
                                hasUpdated = true;
                            }
//                        hasUpdated = (discoveredServers.size() != replacementDiscovered.size());
                            discoveredServers = replacementDiscovered;
                        }
                        Thread.sleep(15000);
                    } catch (IOException | URISyntaxException e) {
                        LOGGER.error("Unable to fetch server list; {}", e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }
}
