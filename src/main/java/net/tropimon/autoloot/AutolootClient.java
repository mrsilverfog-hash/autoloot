package net.tropimon.autoloot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class AutolootClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.currentScreen == null) return;

            // On vérifie le nom complet de la classe du conteneur
            String containerClassName = client.currentScreen.getScreenHandler().getClass().getName().toLowerCase();

            // Condition stricte : le nom doit contenir "lootr" (pour coffres et tonneaux Lootr)
            if (containerClassName.contains("lootr")) {
                
                var container = client.currentScreen.getScreenHandler();
                var playerInv = client.player.getInventory();

                // On scanne uniquement les slots du coffre (généralement 0 à 26)
                for (int i = 0; i < container.slots.size(); i++) {
                    // On s'arrête avant les slots de l'inventaire joueur
                    if (i >= 27) break; 

                    ItemStack chestStack = container.getSlot(i).getStack();
                    if (chestStack.isEmpty()) continue;

                    // Comparaison : est-ce que cet item est dans mon inventaire ?
                    for (int j = 0; j < playerInv.size(); j++) {
                        if (!playerInv.getStack(j).isEmpty() && 
                            playerInv.getStack(j).getItem() == chestStack.getItem()) {
                            
                            // Transfert automatique
                            client.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                            return; // Un seul item par tick pour éviter les erreurs réseau
                        }
                    }
                }
            }
        });
    }
}
