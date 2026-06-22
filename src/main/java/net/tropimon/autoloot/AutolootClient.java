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
import java.util.Deque;

public class AutolootClient implements ClientModInitializer {

    private static final String LOOTR_NAMESPACE = "lootr";
    private static final int SCAN_DELAY_TICKS = 5;
    private static final int CLICKS_PER_TICK = 2;

    private KeyBinding toggleKey;
    private boolean autolootEnabled = false;
    private boolean currentContainerIsLootr = false;
    private boolean alreadyQueuedForThisOpen = false;
    private boolean screenJustOpened = false;
    private int ticksWaited = 0;

    private final Deque<Integer> pendingGrabSlots = new ArrayDeque<>();

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

        processGrabQueue(client);
    }

    private void queueMatchingItems(MinecraftClient client, net.minecraft.screen.ScreenHandler handler) {
        if (!(handler instanceof GenericContainerScreenHandler containerHandler)) {
            client.player.sendMessage(Text.literal("[Autoloot debug] handler = " + handler.getClass().getSimpleName()), false);
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

        // --- DEBUG TEMPORAIRE ---
        client.player.sendMessage(
                Text.literal("[Autoloot debug] containerSize=" + containerSize + " | slots en file=" + pendingGrabSlots),
                false
        );
        // --- FIN DEBUG ---

        if (!pendingGrabSlots.isEmpty()) {
            client.player.sendMessage(Text.translatable("message.autoloot.grabbed", pendingGrabSlots.size()), true);
        }
    }

    private void processGrabQueue(MinecraftClient client) {
        if (pendingGrabSlots.isEmpty()) {
            return;
        }
        if (!(client.player.currentScreenHandler instanceof GenericContainerScreenHandler containerHandler)) {
            pendingGrabSlots.clear();
            return;
        }

        int sent = 0;
        while (!pendingGrabSlots.isEmpty() && sent < CLICKS_PER_TICK) {
            int slotIndex = pendingGrabSlots.poll();

            // --- DEBUG TEMPORAIRE ---
            ItemStack beforeClick = containerHandler.getSlot(slotIndex).getStack();
            client.player.sendMessage(
                    Text.literal("[Autoloot debug] Clic envoyé sur slot " + slotIndex + " contenant " + beforeClick.getCount() + "x " + beforeClick.getItem()),
                    false
            );
            // --- FIN DEBUG ---

            client.interactionManager.clickSlot(
                    containerHandler.syncId,
                    slotIndex,
                    0,
                    SlotActionType.QUICK_MOVE,
                    client.player
            );
            sent++;
        }
    }
}
