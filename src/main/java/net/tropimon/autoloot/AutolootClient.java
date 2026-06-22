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
    private static final int SCAN_DELAY_TICKS = 5;
    private static final int CLICKS_PER_TICK = 2;
    private static final int RETRY_DELAY_TICKS = 20;
    private static final int MAX_ATTEMPTS = 5;

    private KeyBinding toggleKey;
    private boolean autolootEnabled = false;
    private boolean currentContainerIsLootr = false;
    private boolean alreadyQueuedForThisOpen = false;
    private boolean screenJustOpened = false;
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
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autoloot.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.autoloot"
        ));

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            Identifier blockId = Registries.BLOCK.getId(
                    world.getBlockState(hitResult.getBlockPos()).getBlock()
            );
            currentContainerIsLootr = blockId.getNamespace().equals(LOOTR_NAMESPACE)
                    && (blockId.getPath().contains("lootr_chest") || blockId.getPath().contains("lootr_barrel"));
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;

        while (toggleKey.wasPressed()) {
            autolootEnabled = !autolootEnabled;
            client.player.sendMessage(Text.translatable(autolootEnabled ? "message.autoloot.enabled" : "message.autoloot.disabled"), true);
        }

        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            alreadyQueuedForThisOpen = false;
            screenJustOpened = false;
            ticksWaited = 0;
            pendingGrabSlots.clear();
            verifying.clear();
            return;
        }

        if (!alreadyQueuedForThisOpen) {
            if (!screenJustOpened) {
                screenJustOpened = true;
                ticksWaited = 0;
            } else {
                ticksWaited++;
                if (autolootEnabled && currentContainerIsLootr && ticksWaited >= SCAN_DELAY_TICKS) {
                    queueMatchingItems(client, client.player.currentScreenHandler);
                    alreadyQueuedForThisOpen = true;
                }
            }
        }

        if (!(client.player.currentScreenHandler instanceof GenericContainerScreenHandler containerHandler)) return;

        sendNewClicks(client, containerHandler);
        checkVerifications(client, containerHandler);
    }

    private void queueMatchingItems(MinecraftClient client, net.minecraft.screen.ScreenHandler handler) {
        if (!(handler instanceof GenericContainerScreenHandler containerHandler)) return;

        var playerInventory = client.player.getInventory();
        int containerSize = containerHandler.getInventory().size();
        
        List<Integer> prioritySlots = new ArrayList<>();
        List<Integer> otherSlots = new ArrayList<>();

        for (int i = 0; i < containerSize; i++) {
            ItemStack containerStack = containerHandler.getSlot(i).getStack();
            
            // LOG DE DÉBOGAGE
            if (i == 16 || i == 26) {
                System.out.println("[DEBUG] Slot " + i + " contient : " + (containerStack.isEmpty() ? "VIDE" : containerStack.getItem().toString()));
            }

            if (containerStack.isEmpty()) continue;

            if (i == 16 || i == 26) {
                prioritySlots.add(i);
                continue;
            }

            boolean alreadyOwned = false;
            for (int j = 0; j < playerInventory.size(); j++) {
                ItemStack invStack = playerInventory.getStack(j);
                if (!invStack.isEmpty() && invStack.getItem() == containerStack.getItem()) {
                    alreadyOwned = true;
                    break;
                }
            }

            if (alreadyOwned) {
                otherSlots.add(i);
            }
        }
        
        pendingGrabSlots.addAll(prioritySlots);
        pendingGrabSlots.addAll(otherSlots);
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

            ItemStack stillThere = containerHandler.getSlot(entry.slotIndex).getStack();
            if (stillThere.isEmpty()) {
                it.remove();
                continue;
            }

            if (entry.attemptsLeft > 0) {
                entry.attemptsLeft--;
                entry.ticksLeft = RETRY_DELAY_TICKS;
                client.interactionManager.clickSlot(containerHandler.syncId, entry.slotIndex, 0, SlotActionType.QUICK_MOVE, client.player);
            } else {
                it.remove();
            }
        }
    }
}
