package me.Evil.soulSMP.util;

import me.Evil.soulSMP.vault.TeamVaultHolder;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Utility for serializing and deserializing Bukkit Inventories
 * to/from Base64 strings so they can be stored in YAML.
 */
public class InventoryUtils {

    public static String toBase64(Inventory inventory) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Write size
            dataOutput.writeInt(inventory.getSize());

            // Write every item
            for (int i = 0; i < inventory.getSize(); i++) {
                dataOutput.writeObject(inventory.getItem(i));
            }

            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize inventory to Base64.", e);
        }
    }

    public static Inventory fromBase64(String data, String title, InventoryHolder holder) {
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            int size = dataInput.readInt();
            Inventory inventory = Bukkit.createInventory(holder, size, title);

            for (int i = 0; i < size; i++) {
                Object obj = dataInput.readObject();
                if (obj instanceof ItemStack item) {
                    inventory.setItem(i, item);
                } else {
                    inventory.setItem(i, null);
                }
            }

            dataInput.close();

            if (holder instanceof TeamVaultHolder tvh) {
                tvh.setInventory(inventory);
            }

            return inventory;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize inventory from Base64.", e);
        }
    }
}
