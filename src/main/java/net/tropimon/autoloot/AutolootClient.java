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
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AutolootClient implements ClientModInitializer {

    private static final String LOOTR_NAMESPACE = "lootr";

    // Nombre de ticks à attendre après l'ouverture avant de scanner le conteneur,
    // pour laisser le temps au jeu de recevoir tout le contenu depuis le serveur
    private static final int SCAN_DELAY_TICKS = 5;

    private KeyBinding toggleKey;
    private boolean autolootEnabled = false;
    private boolean currentContainerIsLootr = false;
    private boolean alreadyGrabbedForThisOpen = false;
    private boolean screenJustOpened = false;
    private int ticksWaited = 0;

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
            alreadyGrabbedForThisOpen = false;
            screenJustOpened = false;
            ticksWaited = 0;
            return;
        }

        if (alreadyGrabbedForThisOpen) {
            return;
        }

        // On attend quelques ticks après l'ouverture pour être sûr que le contenu
        // du coffre/tonneau est bien arrivé du serveur avant de le scanner
        if (!screenJustOpened) {
            screenJustOpened = true;
            ticksWaited = 0;
            return;
        }

        ticksWaited++;
        if (autolootEnabled && currentContainerIsLootr && ticksWaited >= SCAN_DELAY_TICKS) {
            grabMatchingItems(client, client.player.currentScreenHandler);
            alreadyGrabbedForThisOpen = true;
        }
    }

    private void grabMatchingItems(MinecraftClient client, ScreenHandler handler) {
        if (!(handler instanceof GenericContainerScreenHandler containerHandler)) {
            return;
        }

        int containerSize = containerHandler.getInventory().size();
        var playerInventory = client.player.getInventory();
        int grabbed = 0;

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
                // QUICK_MOVE = comportement shift-clic : remplit d'abord les piles existantes,
                // puis va dans un slot vide s'il en reste pour le surplus
                client.interactionManager.clickSlot(
                        containerHandler.syncId,
                        i,
                        0,
                        SlotActionType.QUICK_MOVE,
                        client.player
                );
                grabbed++;
            }
        }

        if (grabbed > 0) {
            client.player.sendMessage(Text.translatable("message.autoloot.grabbed", grabbed), true);
        }
    }
}
