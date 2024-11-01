package net.creeperhost.minetogether.SAD.mixin;

import net.creeperhost.minetogether.SAD.ServerAutomaticDiscovery;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public class MixinJoinMultiplayerScreen
{
    @Inject(at = @At("TAIL"), method = "refreshServerList")
    public void refreshServerList(CallbackInfo ci)
    {
        ServerAutomaticDiscovery.hasUpdated = true;
    }

    @Inject(at = @At("TAIL"), method = "addServerCallback")
    public void addServer(CallbackInfo ci)
    {
        ServerAutomaticDiscovery.hasUpdated = true;
    }

    @Inject(at = @At("TAIL"), method = "editServerCallback")
    public void editServer(CallbackInfo ci)
    {
        ServerAutomaticDiscovery.hasUpdated = true;
    }

    @Inject(at = @At("TAIL"), method = "deleteCallback")
    public void deleteServer(CallbackInfo ci)
    {
        ServerAutomaticDiscovery.hasUpdated = true;
    }
}
