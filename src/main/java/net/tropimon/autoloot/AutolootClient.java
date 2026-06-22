package net.tropimon.autoloot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public class AutolootClient implements ClientModInitializer {

    private static final String LOOTR_NAMESPACE = "lootr";
    
    private static final int SORT_DELAY_TICKS = 2;
    private static final int SCAN_DELAY_TICKS = 5;
    private static final int CLICKS_PER_TICK = 3;
    private static final int RETRY_DELAY_TICKS = 10;
    private static final int MAX_ATTEMPTS = 5;

    private KeyBinding toggleKey;
    private boolean autolootEnabled = false;
    private boolean currentContainerIsLootr = false;
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
            this.ticksLeft = RETRY_DELAY_TICKS;
            this.attemptsLeft = MAX_ATTEMPTS;
        }
    }

    @Override
    public void onInitializeClient() {
        // Remplacez GLFW_KEY_V par votre touche préférée
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autoloot.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V, 
                "key.categories.autoloot"
        ));

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            Identifier blockId = Registries.BLOCK.getId(world.getBlockState(hitResult.getBlockPos()).getBlock());
            currentContainerIsLootr = blockId.getNamespace().equals(LOOTR_NAMESPACE);
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;

        // Gestion de l'activation avec message à l'écran
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

        if (autolootEnabled && currentContainerIsLootr && !actionDone) {
            ticksWaited++;

            if (ticksWaited == SORT_DELAY_TICKS) {
                long handle = client.getWindow().getHandle();
                client.keyboard.onKey(handle, GLFW.GLFW_KEY_R, 0, GLFW.GLFW_PRESS, 0);
                client.keyboard.onKey(handle, GLFW.GLFW_KEY_R, 0, GLFW.GLFW_RELEASE, 0);
            }

            if (ticksWaited >= SCAN_DELAY_TICKS) {
                queueMatchingItems(client, client.player.currentScreenHandler);
                actionDone = true;
            }
        }

        if (client.player.currentScreenHandler instanceof GenericContainerScreenHandler containerHandler) {
            sendNewClicks(client, containerHandler);
            checkVerifications(client, containerHandler);
        }
    }

    private void queueMatchingItems(MinecraftClient client, net.minecraft.screen.ScreenHandler handler) {
        if (!(handler instanceof GenericContainerScreenHandler containerHandler)) return;
        var playerInventory = client.player.getInventory();
        int containerSize = containerHandler.getInventory().size();

        for (int i = 0; i < containerSize; i++) {
            ItemStack containerStack = containerHandler.getSlot(i).getStack();
            if (containerStack.isEmpty()) continue;

            boolean alreadyOwned = false;
            for (int j = 0; j < playerInventory.size(); j++) {
                ItemStack invStack = playerInventory.getStack(j);
                if (!invStack.isEmpty() && invStack.getItem() == containerStack.getItem()) {
                    alreadyOwned = true;
                    break;
                }
            }

            if (alreadyOwned && !pendingGrabSlots.contains(i)) {
                pendingGrabSlots.add(i);
            }
        }
    }

    private void sendNewClicks(MinecraftClient client, GenericContainerScreenHandler containerHandler) {
        int sent = 0;
        while (!pendingGrabSlots.isEmpty() && sent < CLICKS_PER_TICK) {
            int slotIndex = pendingGrabSlots.poll();
            client.interactionManager.clickSlot(containerHandler.syncId, slotIndex, 0, SlotActionType.QUICK_MOVE, client.player);
            verifying.add(new PendingVerify(slotIndex));
            sent++;
        }
    }

    private void checkVerifications(MinecraftClient client, GenericContainerScreenHandler containerHandler) {
        Iterator<PendingVerify> it = verifying.iterator();
        while (it.hasNext()) {
            PendingVerify entry = it.next();
            entry.ticksLeft--;
            if (entry.ticksLeft > 0) continue;
            
            if (containerHandler.getSlot(entry.slotIndex).getStack().isEmpty()) { 
                it.remove(); 
            } else if (entry.attemptsLeft > 0) {
                entry.attemptsLeft--;
                entry.ticksLeft = RETRY_DELAY_TICKS;
                client.interactionManager.clickSlot(containerHandler.syncId, entry.slotIndex, 0, SlotActionType.QUICK_MOVE, client.player);
            } else { 
                it.remove(); 
            }
        }
    }
}
