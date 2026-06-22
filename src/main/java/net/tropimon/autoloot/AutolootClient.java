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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
            && screen.getScreenHandler() instanceof GenericContainerScreenHandler container && !actionDone) {

            // 1. On récupère les items du coffre
            List<ItemStack> chestItems = new ArrayList<>();
            for (int i = 0; i < container.getInventory().size(); i++) {
                ItemStack stack = container.getSlot(i).getStack();
                if (!stack.isEmpty()) chestItems.add(stack);
            }

            // 2. Tri "maison" par nom d'item (pour que le serveur voie un ordre logique)
            chestItems.sort(Comparator.comparing(s -> s.getItem().getName().getString()));

            // 3. Aspiration : si l'item existe dans votre inventaire, on le prend
            var playerInv = client.player.getInventory();
            for (ItemStack chestStack : chestItems) {
                for (int j = 0; j < playerInv.size(); j++) {
                    if (!playerInv.getStack(j).isEmpty() && playerInv.getStack(j).getItem() == chestStack.getItem()) {
                        // On trouve le slot original de l'item pour le cliquer
                        for (int k = 0; k < container.getInventory().size(); k++) {
                            if (container.getSlot(k).getStack() == chestStack) {
                                client.interactionManager.clickSlot(container.syncId, k, 0, SlotActionType.QUICK_MOVE, client.player);
                                break;
                            }
                        }
                        break;
                    }
                }
            }
            actionDone = true;
        } else if (client.currentScreen == null) {
            actionDone = false;
        }
    }
}
