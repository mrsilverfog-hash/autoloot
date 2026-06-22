package net.tropimon.autoloot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AutolootClient implements ClientModInitializer {

    private KeyBinding toggleKey;
    private boolean autolootEnabled = false;
    private boolean actionDone = false;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autoloot.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.autoloot"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;

        while (toggleKey.wasPressed()) {
            autolootEnabled = !autolootEnabled;
            client.player.sendMessage(Text.literal("[Autoloot] " + (autolootEnabled ? "Activé" : "Désactivé")), true);
        }

        if (autolootEnabled && client.currentScreen instanceof HandledScreen<?> screen) {
            
            // Si le conteneur est ouvert et qu'on n'a pas encore cliqué sur Z
            if (!actionDone && screen.getScreenHandler() instanceof GenericContainerScreenHandler) {
                
                // On cherche le bouton "Z" en testant tous les widgets de l'écran
                for (var element : screen.children()) {
                    if (element instanceof ClickableWidget widget) {
                        // Le bouton Z est généralement le plus proche du coin haut-droit 
                        // de la zone du conteneur. On cible une zone proche du coin droit.
                        int x = widget.getX();
                        int y = widget.getY();
                        
                        // Si le widget est dans la zone du bouton de tri (en haut à droite)
                        if (x > screen.width / 2 + 100 && y < screen.height / 2 - 50) {
                            widget.onClick(0, 0); // Simulation du clic physique
                            actionDone = true;
                            return;
                        }
                    }
                }
            }
        } else if (client.currentScreen == null) {
            actionDone = false;
        }
    }
}
