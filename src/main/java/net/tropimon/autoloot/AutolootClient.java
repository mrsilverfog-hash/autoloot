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
    private static final int VERIFY_DELAY_TICKS = 4;
    private static final int MAX_ATTEMPTS = 4;

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
            this.ticksLeft = VERIFY_DELAY_TICKS;
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
                    && (blockId.getPath().equals("lootr_chest") || blockId.getPath().equals("lootr_barrel"));
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        while (toggleKey.wasPressed()) {
            autolootEnabled = !autolootEnabled;
            client.player.sendMessage(
                    Text.translatable(autolootEnabled
                            ? "message.autoloot.enabled"
                            : "message.autoloot.disabled"),
                    true
            );
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

        if (!(client.player.currentScreenHandler instanceof GenericContainerScreenHandler containerHandler)) {
            return;
        }

        sendNewClicks(client, containerHandler);
        checkVerifications(client, containerHandler);
    }

    private void queueMatchingItems(MinecraftClient client, net.minecraft.screen.ScreenHandler handler) {
        if (!(handler instanceof GenericContainerScreenHandler containerHandler)) {
            return;
        }

        int containerSize = containerHandler.getInventory().size();
        var playerInventory = client.player.getInventory();

        for (int i = 0; i < containerSize; i++) {
            ItemStack containerStack = containerHandler.getSlot(i).getStack();
            if (containerStack.isEmpty()) {
                continue;
            }

            boolean alreadyOwned = false;
            for (ItemStack invStack : playerInventory.main) {
                if (!invStack.isEmpty() && invStack.getItem() == containerStack.getItem()) {
                    alreadyOwned = true;
                    break;
                }
            }

            if (alreadyOwned) {
                pendingGrabSlots.add(i);
            }
        }

        if (!pendingGrabSlots.isEmpty()) {
            client.player.sendMessage(Text.translatable("message.autoloot.grabbed", pendingGrabSlots.size()), true);
        }
    }

    private void sendNewClicks(MinecraftClient client, GenericContainerScreenHandler containerHandler) {
        int sent = 0;
        while (!pendingGrabSlots.isEmpty() && sent < CLICKS_PER_TICK) {
            int slotIndex = pendingGrabSlots.poll();

            // --- DEBUG TEMPORAIRE ---
            net.minecraft.screen.slot.Slot slotObj = containerHandler.getSlot(slotIndex);
            client.player.sendMessage(
                    Text.literal("[Autoloot debug] Slot " + slotIndex
                            + " | classe=" + slotObj.getClass().getName()
                            + " | inventory=" + slotObj.inventory.getClass().getName()
                            + " | invSlot index=" + slotObj.getIndex()),
                    false
            );
            // --- FIN DEBUG ---

            client.interactionManager.clickSlot(
                    containerHandler.syncId, slotIndex, 0, SlotActionType.QUICK_MOVE, client.player
            );
            verifying.add(new PendingVerify(slotIndex));
            sent++;
        }
    }

    private void checkVerifications(MinecraftClient client, GenericContainerScreenHandler containerHandler) {
        Iterator<PendingVerify> it = verifying.iterator();
        while (it.hasNext()) {
            PendingVerify entry = it.next();
            entry.ticksLeft--;
            if (entry.ticksLeft > 0) {
                continue;
            }

            ItemStack stillThere = containerHandler.getSlot(entry.slotIndex).getStack();
            if (stillThere.isEmpty()) {
                it.remove();
                continue;
            }

            if (entry.attemptsLeft > 0) {
                entry.attemptsLeft--;
                entry.ticksLeft = VERIFY_DELAY_TICKS;
                client.interactionManager.clickSlot(
                        containerHandler.syncId, entry.slotIndex, 0, SlotActionType.QUICK_MOVE, client.player
                );
            } else {
                client.player.sendMessage(
                        Text.literal("[Autoloot] Impossible de récupérer le slot " + entry.slotIndex + " après plusieurs essais"),
                        false
                );
                it.remove();
            }
        }
    }
}
