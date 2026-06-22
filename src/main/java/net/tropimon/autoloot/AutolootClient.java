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

    // Le namespace utilisé par le mod Lootr pour ses coffres/tonneaux de butin
    private static final String LOOTR_NAMESPACE = "lootr";

    private KeyBinding toggleKey;

    // Etat actuel de l'option (activée ou non par le joueur)
    private boolean autolootEnabled = false;

    // Vrai si le dernier conteneur cliqué par le joueur est un coffre/tonneau Lootr
    private boolean currentContainerIsLootr = false;

    // Empêche de relancer la récupération à chaque tick pendant qu'un même coffre reste ouvert
    private boolean alreadyGrabbedForThisOpen = false;

    @Override
    public void onInitializeClient() {
        // La touche n'a aucune valeur par défaut : le joueur la choisit lui-même
        // dans Options > Contrôles > Autoloot
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autoloot.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.autoloot"
        ));

        // A chaque clic-droit sur un bloc, on retient si c'est un coffre/tonneau Lootr
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

        // Gère l'appui sur la touche : ça inverse simplement l'état activé/désactivé
        while (toggleKey.wasPressed()) {
            autolootEnabled = !autolootEnabled;
            client.player.sendMessage(
                    Text.translatable(autolootEnabled
                            ? "message.autoloot.enabled"
                            : "message.autoloot.disabled"),
                    true
            );
        }

        // Si aucun coffre/inventaire n'est ouvert, on réinitialise et on s'arrête là
        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            alreadyGrabbedForThisOpen = false;
            return;
        }

        // Si l'option est activée et qu'on vient d'ouvrir un coffre/tonneau Lootr,
        // on récupère automatiquement les objets déjà possédés (une seule fois par ouverture)
        if (autolootEnabled && currentContainerIsLootr && !alreadyGrabbedForThisOpen) {
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
                // QUICK_MOVE = le même comportement qu'un shift-clic : prend tout le stack
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
