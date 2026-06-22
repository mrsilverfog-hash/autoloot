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
import java.util.List;
import java.util.ArrayList;

public class AutolootClient implements ClientModInitializer {

    private KeyBinding toggleKey;
    private boolean autolootEnabled = false;
    private boolean actionDone = false;
    private int ticksWaited = 0;

    private final Deque<Integer> pendingGrabSlots = new ArrayDeque<>();
    private final List<PendingVerify> verifying = new ArrayList<>();

    private static class PendingVerify {
        final int slotIndex;
        int ticksLeft;
        int attemptsLeft;

        PendingVerify(int slotIndex) {
            this.slotIndex = slotIndex;
            this.ticksLeft = 10;
            this.attemptsLeft = 5;
        }
    }

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autoloot.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.autoloot"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;

        while (toggleKey.wasPressed()) {
            autolootEnabled = !autolootEnabled;
            client.player.sendMessage(Text.literal("[Autoloot] " + (autolootEnabled ? "Activé" : "Désactivé")), true);
        }

        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            actionDone = false;
            ticksWaited = 0;
            pendingGrabSlots.clear();
            verifying.clear();
            return;
        }

        if (autolootEnabled && client.player.currentScreenHandler instanceof GenericContainerScreenHandler container) {
            
            if (!actionDone) {
                ticksWaited++;
                
                // Déclenchement du tri serveur par clic sur le bouton de tri (slot spécial)
                // Le slot "-999" est le slot hors inventaire utilisé par les mods pour les boutons de tri
                if (ticksWaited == 2) {
                    client.interactionManager.clickSlot(container.syncId, -999, 0, SlotActionType.QUICK_CRAFT, client.player);
                }
                
                // Scan et collecte après 10 ticks
                if (ticksWaited >= 10) {
                    queueMatchingItems(client, container);
                    actionDone = true;
                }
            }

            sendNewClicks(client, container);
            checkVerifications(client, container);
        }
    }

    private void queueMatchingItems(MinecraftClient client, GenericContainerScreenHandler container) {
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
    }

    private void sendNewClicks(MinecraftClient client, GenericContainerScreenHandler container) {
        int sent = 0;
        while (!pendingGrabSlots.isEmpty() && sent < 3) {
            client.interactionManager.clickSlot(container.syncId, pendingGrabSlots.poll(), 0, SlotActionType.QUICK_MOVE, client.player);
            sent++;
        }
    }

    private void checkVerifications(MinecraftClient client, GenericContainerScreenHandler container) {
        verifying.removeIf(v -> {
            v.ticksLeft--;
            if (v.ticksLeft > 0) return false;
            if (!container.getSlot(v.slotIndex).getStack().isEmpty() && v.attemptsLeft-- > 0) {
                client.interactionManager.clickSlot(container.syncId, v.slotIndex, 0, SlotActionType.QUICK_MOVE, client.player);
                v.ticksLeft = 10;
                return false;
            }
            return true;
        });
    }
}
