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

import java.util.ArrayDeque;
import java.util.Deque;

public class AutolootClient implements ClientModInitializer {

    private KeyBinding toggleKey;
    private boolean autolootEnabled = false;
    private final Deque<Integer> pendingGrabSlots = new ArrayDeque<>();

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
            && screen.getScreenHandler() instanceof GenericContainerScreenHandler container) {
            
            // On scanne en permanence tant que le coffre est ouvert
            // Si un item correspond, on l'ajoute à la file
            var playerInv = client.player.getInventory();
            for (int i = 0; i < container.getInventory().size(); i++) {
                ItemStack stack = container.getSlot(i).getStack();
                if (stack.isEmpty()) continue;

                for (int j = 0; j < playerInv.size(); j++) {
                    if (!playerInv.getStack(j).isEmpty() && playerInv.getStack(j).getItem() == stack.getItem()) {
                        if (!pendingGrabSlots.contains(i)) pendingGrabSlots.add(i);
                        break;
                    }
                }
            }

            // On vide la file de manière espacée (très rapide)
            if (!pendingGrabSlots.isEmpty() && client.world.getTime() % 2 == 0) {
                client.interactionManager.clickSlot(container.syncId, pendingGrabSlots.poll(), 0, SlotActionType.QUICK_MOVE, client.player);
            }
        }
    }
}
