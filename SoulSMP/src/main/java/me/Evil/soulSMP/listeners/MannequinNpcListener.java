package me.Evil.soulSMP.npc;

import me.Evil.soulSMP.bank.BankGUI;
import me.Evil.soulSMP.store.StoreManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Right-click interactions for mannequin NPCs.
 */
public class MannequinNpcListener implements Listener {

    private final MannequinNpcManager npcManager;
    private final StoreManager storeManager;

    public MannequinNpcListener(MannequinNpcManager npcManager, StoreManager storeManager) {
        this.npcManager = npcManager;
        this.storeManager = storeManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity clicked = e.getRightClicked();
        NpcType type = npcManager.getType(clicked);
        if (type == null) return;

        e.setCancelled(true);

        Player p = e.getPlayer();
        switch (type) {
            case SHOP -> storeManager.openMain(p);
            case BANK -> BankGUI.open(p);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!npcManager.isNpc(e.getEntity())) return;
        e.setCancelled(true);
    }
}
