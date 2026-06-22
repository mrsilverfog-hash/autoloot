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

    // Attente après l'ouverture avant de scanner, pour que le contenu soit bien synchronisé
    private static final int SCAN_DELAY_TICKS = 5;

    // Nombre d'objets traités au maximum par tick (chacun = 2 clics : prendre + poser)
    private static final int GRABS_PER_TICK = 2;

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

        if (!(client.player.currentScreenHandler instanceof GenericContainerScreenHandler containerHandler)) {
            return;
        }

        processGrabQueue(client, containerHandler);
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

    private void processGrabQueue(MinecraftClient client, GenericContainerScreenHandler containerHandler) {
        int done = 0;
        while (!pendingGrabSlots.isEmpty() && done < GRABS_PER_TICK) {
            int slotIndex = pendingGrabSlots.poll();
            transferViaPickup(client, containerHandler, slotIndex);
            done++;
        }
    }

    // Transfère un objet du coffre vers l'inventaire en 2 clics simples
    // (prendre, puis poser), exactement comme le ferait un tri manuel,
    // plutôt qu'un shift-clic (QUICK_MOVE) qui pose problème sur certains slots.
    private void transferViaPickup(MinecraftClient client, GenericContainerScreenHandler containerHandler, int containerSlotIndex) {
        ItemStack stack = containerHandler.getSlot(containerSlotIndex).getStack();
        if (stack.isEmpty()) {
            return;
        }

        // Étape 1 : on prend l'objet du coffre (il va sur le curseur)
        client.interactionManager.clickSlot(
                containerHandler.syncId, containerSlotIndex, 0, SlotActionType.PICKUP, client.player
        );

        int containerSize = containerHandler.getInventory().size();
        int totalSlots = containerHandler.slots.size();

        // Étape 2a : on cherche d'abord une pile déjà existante du même objet, avec de la place
        Integer targetSlot = null;
        boolean isExistingStack = false;
        for (int i = containerSize; i < totalSlots; i++) {
            ItemStack invStack = containerHandler.getSlot(i).getStack();
            if (!invStack.isEmpty() && invStack.getItem() == stack.getItem()
                    && invStack.getCount() < invStack.getMaxCount()) {
                targetSlot = i;
                isExistingStack = true;
                break;
            }
        }

        // Étape 2b : sinon, un slot vide
        if (targetSlot == null) {
            for (int i = containerSize; i < totalSlots; i++) {
                if (containerHandler.getSlot(i).getStack().isEmpty()) {
                    targetSlot = i;
                    break;
                }
            }
        }

        if (targetSlot != null) {
            // On pose l'objet dans l'inventaire
            client.interactionManager.clickSlot(
                    containerHandler.syncId, targetSlot, 0, SlotActionType.PICKUP, client.player
            );
            // --- DEBUG TEMPORAIRE ---
            ItemStack after = containerHandler.getSlot(targetSlot).getStack();
            client.player.sendMessage(
                    Text.literal("[Autoloot debug] " + stack.getItem() + " -> slot " + targetSlot
                            + " | pile existante=" + isExistingStack
                            + " | total apres=" + after.getCount()),
                    false
            );
            // --- FIN DEBUG ---
        } else {
            // Pas de place : on remet l'objet là où on l'a pris
            client.interactionManager.clickSlot(
                    containerHandler.syncId, containerSlotIndex, 0, SlotActionType.PICKUP, client.player
            );
        }
    }
}
