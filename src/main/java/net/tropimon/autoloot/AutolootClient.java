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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutolootClient implements ClientModInitializer {

    private KeyBinding toggleKey;
    private boolean autolootEnabled = false;
    private boolean processed = false;

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
            processed = false;
        }

        if (autolootEnabled && client.currentScreen instanceof HandledScreen<?> screen 
            && screen.getScreenHandler().getClass().getName().toLowerCase().contains("lootr") && !processed) {

            var container = screen.getScreenHandler();
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < 27; i++) if (!container.getSlot(i).getStack().isEmpty()) slots.add(i);

            // Tri par ID d'item (Ordre logique)
            slots.sort(Comparator.comparing(i -> container.getSlot(i).getStack().getItem().toString()));

            // On effectue les échanges (swaps) pour trier physiquement dans le coffre
            for (int i = 0; i < slots.size(); i++) {
                if (i != slots.get(i)) {
                    client.interactionManager.clickSlot(container.syncId, slots.get(i), 0, SlotActionType.PICKUP, client.player);
                    client.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.PICKUP, client.player);
                }
            }
            
            processed = true; // Tri terminé
        } else if (client.currentScreen == null) {
            processed = false;
        }
    }
}
