package net.creeperhost.minetogether.SAD.payloads;

import com.mojang.authlib.GameProfile;

import java.util.ArrayList;
import java.util.List;

public class Advertise {
    public List<GameProfile> targets = new ArrayList<>();
    public int serverPort = 25565;
    private final String type = "advertisement";
}
