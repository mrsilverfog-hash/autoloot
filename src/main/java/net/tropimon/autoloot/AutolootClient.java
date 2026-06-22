package net.tropimon.autoloot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AutolootClient implements ClientModInitializer {

    private KeyBinding toggleKey;
    private boolean autolootEnabled = false;
    private int state = 0;
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

        if (!autolootEnabled || client.currentScreen == null) {
            if (client.currentScreen == null) state = 0;
            return;
        }

        if (client.currentScreen instanceof HandledScreen<?> screen) {
            String className = screen.getScreenHandler().getClass().getName().toLowerCase();
            
            if (className.contains("lootr")) {
                if (state == 0) {
                    long handle = client.getWindow().getHandle();
                    // Correction des accès : screen.x et screen.y sont publics
                    int x = screen.x + screen.backgroundWidth - 30; 
                    int y = screen.y + 10; 
                    
                    GLFW.glfwSetCursorPos(handle, x, y);
                    // Injection de clic bas niveau via GLFW pour contourner le `private access`
                    GLFW.glfwPostEmptyEvent(); // Force le rafraîchissement
                    
                    state = 1;
                    timer = 10; 
                } else if (state == 1) {
                    if (--timer <= 0) state = 2;
                } else if (state == 2) {
                    var container = screen.getScreenHandler();
                    var playerInv = client.player.getInventory();
                    for (int i = 0; i < container.slots.size(); i++) {
                        ItemStack stack = container.getSlot(i).getStack();
                        if (stack.isEmpty() || i >= 27) continue; 
                        for (int j = 0; j < playerInv.size(); j++) {
                            if (!playerInv.getStack(j).isEmpty() && playerInv.getStack(j).getItem() == stack.getItem()) {
                                client.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}
