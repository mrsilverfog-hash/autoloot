package net.tropimon.autoloot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class AutolootClient implements ClientModInitializer {

    private boolean processing = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.currentScreen == null) {
                processing = false;
                return;
            }

            if (processing) return;

            // On vérifie qu'on est bien dans un coffre/tonneau Lootr
            if (client.currentScreen instanceof HandledScreen<?> screen 
                && screen.getScreenHandler().getClass().getName().toLowerCase().contains("lootr")) {
                
                var container = screen.getScreenHandler();
                var playerInv = client.player.getInventory();

                // On cherche un item qui est DANS le coffre ET dans notre inventaire
                for (int i = 0; i < 27; i++) { // Les slots du coffre
                    ItemStack chestStack = container.getSlot(i).getStack();
                    if (chestStack.isEmpty()) continue;

                    for (int j = 0; j < playerInv.size(); j++) {
                        if (!playerInv.getStack(j).isEmpty() && 
                            playerInv.getStack(j).getItem() == chestStack.getItem()) {
                            
                            // On déplace l'item instantanément
                            client.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                            processing = true; // On attend le prochain tick pour éviter de saturer
                            return;
                        }
                    }
                }
            }
        });
    }
}
