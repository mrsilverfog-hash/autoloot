package net.tropimon.autoloot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
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

        if (autolootEnabled && client.currentScreen instanceof HandledScreen<?> screen 
            && screen.getScreenHandler() instanceof GenericContainerScreenHandler && !actionDone) {
            
            // Calcul de la position du bouton Z basé sur l'image
            // Le bouton Z est dans le coin haut-droit de l'inventaire
            int x = screen.getX() + screen.getBackgroundWidth() - 30; 
            int y = screen.getY() + 5; 

            // Déplacement du curseur réel
            long handle = client.getWindow().getHandle();
            GLFW.glfwSetCursorPos(handle, x, y);

            // Simulation d'un vrai clic souris
            client.mouse.onMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
            client.mouse.onMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
            
            actionDone = true;
        } else if (client.currentScreen == null) {
            actionDone = false;
        }
    }
}
