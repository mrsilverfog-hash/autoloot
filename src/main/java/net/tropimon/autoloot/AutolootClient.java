package net.tropimon.autoloot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class AutolootClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.currentScreen == null) return;

            // Vérifie si c'est un coffre Lootr
            if (client.currentScreen instanceof HandledScreen<?> screen 
                && screen.getScreenHandler().getClass().getName().toLowerCase().contains("lootr")) {
                
                var container = screen.getScreenHandler();
                var playerInv = client.player.getInventory();

                // On boucle sur les 27 slots du coffre
                for (int i = 0; i < 27; i++) {
                    ItemStack chestStack = container.getSlot(i).getStack();
                    if (chestStack.isEmpty()) continue;

                    // Si l'objet est déjà dans l'inventaire, on aspire
                    for (int j = 0; j < playerInv.size(); j++) {
                        if (!playerInv.getStack(j).isEmpty() && 
                            playerInv.getStack(j).getItem() == chestStack.getItem()) {
                            
                            // Maj + Clic sur l'objet
                            client.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                            return; 
                        }
                    }
                }
            }
        });
    }
}
