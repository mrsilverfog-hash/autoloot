package net.tropimon.autoloot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AutolootClient implements ClientModInitializer {

    private KeyBinding toggleKey;
    private boolean autolootEnabled = false;
    private int state = 0; // 0: Rien, 1: Clic Z en attente, 2: Aspiration
    private int timer = 0;

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
            state = 0;
        }

        if (!autolootEnabled) return;

        if (client.currentScreen instanceof HandledScreen<?> screen && screen.getScreenHandler() instanceof GenericContainerScreenHandler container) {
            
            // Étape 1 : Clic sur le bouton Z
            if (state == 0) {
                long handle = client.getWindow().getHandle();
                // Coordonnées approximatives du bouton Z (ajustez si besoin)
                int x = screen.getX() + screen.getBackgroundWidth() - 30; 
                int y = screen.getY() + 10; 
                
                GLFW.glfwSetCursorPos(handle, x, y);
                client.mouse.onMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
                client.mouse.onMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
                
                state = 1;
                timer = 10; // Attend 10 ticks (0.5s) que le tri se fasse
            } 
            // Étape 2 : Attente
            else if (state == 1) {
                timer--;
                if (timer <= 0) state = 2;
            } 
            // Étape 3 : Aspiration
            else if (state == 2) {
                var playerInv = client.player.getInventory();
                for (int i = 0; i < container.getInventory().size(); i++) {
                    ItemStack stack = container.getSlot(i).getStack();
                    if (stack.isEmpty()) continue;
                    for (int j = 0; j < playerInv.size(); j++) {
                        if (!playerInv.getStack(j).isEmpty() && playerInv.getStack(j).getItem() == stack.getItem()) {
                            client.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                            return; // Un item par tick
                        }
                    }
                }
            }
        } else {
            state = 0; // Reset quand on ferme l'inventaire
        }
    }
}
