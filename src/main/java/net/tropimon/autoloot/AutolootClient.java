package net.tropimon.autoloot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

public class AutolootClient implements ClientModInitializer {

    private KeyBinding toggleKey;
    private boolean autolootEnabled = true; 
    private int state = 0; // 0: Attente, 1: Pause initialisation, 2: Clic Z, 3: Pause Tri, 4: Aspiration, 5: Fini
    private int timer = 0;

    @Override
    public void onInitializeClient() {
        // Raccourci clavier sur la touche 'V' pour activer/désactiver au besoin
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autoloot.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.autoloot"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;

        while (toggleKey.wasPressed()) {
            autolootEnabled = !autolootEnabled;
            client.player.sendMessage(Text.literal("[Autoloot] " + (autolootEnabled ? "Activé" : "Désactivé")), true);
            state = 0;
        }

        if (!autolootEnabled) return;

        if (client.currentScreen instanceof HandledScreen<?> screen) {
            String className = screen.getScreenHandler().getClass().getName().toLowerCase();
            
            // FILTRE STRICT : Uniquement les conteneurs du mod Lootr
            if (className.contains("lootr")) {
                
                // Étape 1 : On attend 5 ticks que l'interface se charge complètement
                if (state == 0) {
                    timer = 5;
                    state = 1;
                } 
                else if (state == 1) {
                    if (--timer <= 0) state = 2;
                }
                // Étape 2 : Clic virtuel sur le bouton Z
                else if (state == 2) {
                    try {
                        // Utilisation de la réflexion pour contourner les restrictions de compilation 'protected'
                        Field xField = HandledScreen.class.getDeclaredField("x");
                        xField.setAccessible(true);
                        int guiX = xField.getInt(screen);

                        Field yField = HandledScreen.class.getDeclaredField("y");
                        yField.setAccessible(true);
                        int guiY = yField.getInt(screen);

                        Field widthField = HandledScreen.class.getDeclaredField("backgroundWidth");
                        widthField.setAccessible(true);
                        int bgWidth = widthField.getInt(screen);

                        // Positionnement horizontal et vertical du bouton Z dans l'interface
                        double clickX = guiX + bgWidth - 23; 
                        double clickY = guiY + 5; 

                        // Envoi d'un événement de clic gauche direct à l'interface de Minecraft
                        screen.mouseClicked(clickX, clickY, 0); 
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    state = 3;
                    timer = 10; // Pause de 10 ticks (0.5s) pour laisser le serveur trier les objets
                } 
                // Étape 3 : Attente de la validation du tri par le serveur
                else if (state == 3) {
                    if (--timer <= 0) state = 4;
                } 
                // Étape 4 : Aspiration intelligente
                else if (state == 4) {
                    var container = screen.getScreenHandler();
                    var playerInv = client.player.getInventory();
                    boolean itemMoved = false;

                    for (int i = 0; i < container.slots.size(); i++) {
                        if (i >= 27) break; // Ne pas toucher aux objets de notre propre inventaire

                        ItemStack stack = container.getSlot(i).getStack();
                        if (stack.isEmpty()) continue;
                        
                        // Si l'objet du coffre existe déjà dans notre inventaire, on le prend
                        for (int j = 0; j < playerInv.size(); j++) {
                            if (!playerInv.getStack(j).isEmpty() && playerInv.getStack(j).getItem() == stack.getItem()) {
                                client.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                                itemMoved = true;
                                break;
                            }
                        }
                        if (itemMoved) return; // Un seul objet traité par tick pour éviter d'être déconnecté
                    }
                    
                    // Si plus aucun objet ne correspond après un scan complet, l'action est terminée
                    if (!itemMoved) {
                        state = 5; 
                    }
                }
            }
        } else {
            state = 0; // Réinitialisation automatique dès que le coffre est fermé
        }
    }
}
