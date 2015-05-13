package com.elmakers.mine.bukkit.block;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.elmakers.mine.bukkit.api.magic.Messages;
import com.elmakers.mine.bukkit.integration.VaultController;
import com.elmakers.mine.bukkit.utility.CompatibilityUtils;
import com.elmakers.mine.bukkit.utility.InventoryUtils;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.TreeSpecies;
import org.bukkit.block.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.elmakers.mine.bukkit.utility.NMSUtils;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * A utility class for presenting a Material in its entirety, including Material variants.
 * 
 * This will probably need an overhaul for 1.8, but I'm hoping that using this class everywhere as an intermediate for 
 * the concept of "material type" will allow for a relatively easy transition. We'll see.
 * 
 * In the meantime, this class primary uses String-based "keys" to identify a material. This is not
 * necessarily meant to be a friendly or printable name, though the class is capable of generating a semi-friendly
 * name, which will be the key lowercased and with underscores replaced with spaces. It will also attempt to create
 * a nice name for the variant, such as "blue wool". There is no DB for this, it is all based on the internal Bukkit
 * Material and MaterialData enumerations.
 * 
 * Some examples of keys:
 * wool
 * diamond_block
 * monster_egg
 * wool:15 (for black wool)
 * 
 * This class may also handle special "brushes", and is extended in the MagicPlugin as MaterialBrush. In this case
 * there may be other non-material keys such as clone, copy, schematic:lantern, map, etc.
 * 
 * When used as a storage mechanism for Block or Material data, this class will store the following bits of information:
 * 
 * - Base Material type
 * - Data/durability of material
 * - Sign Text
 * - Command Block Text
 * - Custom Name of Block (Skull, Command block name)
 * - InventoryHolder contents
 * 
 * If persisted to a ConfigurationSection, this will currently only store the base Material and data, extra metadata will not 
 * be saved.
 */
public class MaterialAndData implements com.elmakers.mine.bukkit.api.block.MaterialAndData {
    protected Material material;
    protected Short data;
    protected String commandLine = null;
    protected String customName = null;
    protected boolean isValid = true;
    protected BlockFace rotation = null;
    protected Object customData = null;
    protected SkullType skullType = null;
    protected DyeColor color = null;
    protected Object tileEntityData = null;

    public Material DEFAULT_MATERIAL = Material.AIR;

    public MaterialAndData() {
        material = DEFAULT_MATERIAL;
        data = 0;
    }

    public MaterialAndData(final Material material) {
        this.material = material;
        this.data = 0;
    }

    public MaterialAndData(final Material material, final  short data) {
        this.material = material;
        this.data = data;
    }

    @SuppressWarnings("deprecation")
    public MaterialAndData(ItemStack item) {
        this.material = item.getType();
        this.data = item.getDurability();
        if (this.material == Material.SKULL_ITEM)
        {
            ItemMeta meta = item.getItemMeta();
            this.customData = InventoryUtils.getSkullProfile(meta);
            try {
                this.skullType = SkullType.values()[this.data];
            } catch (Exception ex) {

            }
        } else if (this.material == Material.STANDING_BANNER || this.material == Material.WALL_BANNER || this.material == Material.BANNER) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta instanceof BannerMeta)
            {
                BannerMeta banner = (BannerMeta)meta;
                this.customData = banner.getPatterns();
                this.color = banner.getBaseColor();
            }
        }
    }

    public MaterialAndData(Block block) {
        updateFrom(block);
    }

    public MaterialAndData(com.elmakers.mine.bukkit.api.block.MaterialAndData other) {
        updateFrom(other);
    }

    public MaterialAndData(final Material material, final  byte data, final String customName) {
        this(material, data);
        this.customName = customName;
    }

    @SuppressWarnings("deprecation")
    public MaterialAndData(String materialKey) {
        this();
        update(materialKey);
    }

    public void update(String materialKey) {
        if (materialKey == null || materialKey.length() == 0) {
            isValid = false;
            return;
        }
        String[] pieces = splitMaterialKey(materialKey);
        Short data = 0;
        Material material = null;

        try {
            if (pieces.length > 0) {
                if (pieces[0].equals("*")) {
                    material = null;
                } else {
                    // Legacy material id loading
                    try {
                        Integer id = Integer.parseInt(pieces[0]);
                        material = Material.getMaterial(id);
                    } catch (Exception ex) {
                        material = Material.getMaterial(pieces[0].toUpperCase());
                    }
                }
            }
        } catch (Exception ex) {
            material = null;
        }
        try {
            if (pieces.length > 1) {
                // Some special-cases
                if (pieces[1].equals("*")) {
                    data = null;
                }
                else if (material == Material.MOB_SPAWNER) {
                    customName = pieces[1];
                    setMaterial(Material.MOB_SPAWNER, (short) 0);
                    return;
                }
                else if (material == Material.SKULL_ITEM) {
                    if (pieces.length > 2) {
                        setMaterial(Material.SKULL_ITEM, (short)3);
                        skullType = SkullType.PLAYER;
                        String dataString = pieces[1];
                        for (int i = 2; i < pieces.length; i++) {
                            dataString += ":" + pieces[i];
                        }
                        ItemStack item = InventoryUtils.getURLSkull(dataString);
                        customData = InventoryUtils.getSkullProfile(item.getItemMeta());
                    } else {
                        try {
                            data = Short.parseShort(pieces[1]);
                            setMaterial(Material.SKULL_ITEM, data);
                        } catch (Exception ex) {
                            setMaterial(Material.SKULL_ITEM, (short)3);
                            skullType = SkullType.PLAYER;
                            ItemStack item = InventoryUtils.getPlayerSkull(pieces[1]);
                            customData = InventoryUtils.getSkullProfile(item.getItemMeta());
                        }
                    }
                    return;
                }
                else if (material.getId() == 176 || material.getId() == 177 || material.getId() == 425) {
                    color = null;
                    try {
                        short colorIndex = Short.parseShort(pieces[1]);
                        setMaterial(material, colorIndex);
                        color = DyeColor.values()[colorIndex];
                    }
                    catch (Exception ex) {
                        color = null;
                    }
                    return;
                } else {
                    try {
                        data = Short.parseShort(pieces[1]);
                    } catch (Exception ex) {
                        data = 0;
                    }
                }
            }
        } catch (Exception ex) {
            material = null;
        }

        if (material == null) {
            this.setMaterial(null, null);
            isValid = false;
        } else {
            setMaterial(material, data);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public int hashCode() {
        // Note that this does not incorporate any metadata!
        return (material.getId() << 16) | data;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MaterialAndData)) {
            return false;
        }

        MaterialAndData other = (MaterialAndData)obj;
        return other.data == data && other.material == material;
    }

    public void updateFrom(com.elmakers.mine.bukkit.api.block.MaterialAndData other) {
        material = other.getMaterial();
        data = other.getData();
        if (other instanceof MaterialAndData) {
            MaterialAndData o = (MaterialAndData)other;
            commandLine = o.commandLine;
            customName = o.customName;
            isValid = o.isValid;
            skullType = o.skullType;
            customData = o.customData;
            color = o.color;
            tileEntityData = o.tileEntityData;
        }
    }

    public void setMaterial(Material material, short data) {
        setMaterial(material, (Short)data);
    }

    public void setMaterial(Material material, Short data) {
        this.material = material;
        this.data = data;
        commandLine = null;
        customName = null;
        skullType = null;
        customData = null;
        color = null;
        tileEntityData = null;

        isValid = true;
    }

    @SuppressWarnings("deprecation")
    public void setMaterialId(int id) {
        this.material = Material.getMaterial(id);
    }

    public void setMaterial(Material material) {
        setMaterial(material, (byte)0);
    }

    public void updateFrom(Block block) {
        updateFrom(block, null);
    }

    @SuppressWarnings("deprecation")
    public void updateFrom(Block block, Set<Material> restrictedMaterials) {
        if (block == null) {
            isValid = false;
            return;
        }
        if (!block.getChunk().isLoaded()) {
            block.getChunk().load(true);
            return;
        }

        Material blockMaterial = block.getType();
        if (restrictedMaterials != null && restrictedMaterials.contains(blockMaterial)) {
            isValid = false;
            return;
        }
        // Look for special block states
        commandLine = null;
        customName = null;
        skullType = null;
        customData = null;
        color = null;
        tileEntityData = null;

        material = blockMaterial;
        data = (short)block.getData();

        try {
            BlockState blockState = block.getState();
            if (material == Material.FLOWER_POT || blockState instanceof InventoryHolder || blockState instanceof Sign) {
                tileEntityData = NMSUtils.getTileEntityData(block.getLocation());
            } else if (blockState instanceof CommandBlock){
                // This seems to occasionally throw exceptions...
                CommandBlock command = (CommandBlock)blockState;
                commandLine = command.getCommand();
                customName = command.getName();
            } else if (blockState instanceof Skull) {
                Skull skull = (Skull)blockState;
                rotation = skull.getRotation();
                skullType = skull.getSkullType();
                customData = CompatibilityUtils.getSkullProfile(skull);
            } else if (blockState instanceof CreatureSpawner) {
                CreatureSpawner spawner = (CreatureSpawner)blockState;
                customName = spawner.getCreatureTypeName();
            } else if (blockMaterial == Material.STANDING_BANNER || blockMaterial == Material.WALL_BANNER) {
                if (blockState != null && blockState instanceof Banner) {
                    Banner banner = (Banner)blockState;
                    customData = banner.getPatterns();
                    color = banner.getBaseColor();
                    data = (short) color.getDyeData();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        isValid = true;
    }

    public void modify(Block block) {
        modify(block, false);
    }

    @SuppressWarnings("deprecation")
    public void modify(Block block, boolean applyPhysics) {
        if (!isValid) return;

        try {
            BlockState blockState = block.getState();
            // Clear chests so they don't dump their contents.
            if (blockState instanceof InventoryHolder) {
                NMSUtils.clearItems(block.getLocation());
            }

            if (material != null) {
                byte blockData = data != null ? (byte)(short)data : block.getData();
                block.setTypeIdAndData(material.getId(), blockData, applyPhysics);
                blockState = block.getState();
            }

            // Set tile entity data first
            // Command blocks still prefer internal data for parameterized commands
            if (blockState != null && blockState instanceof CommandBlock && commandLine != null) {
                CommandBlock command = (CommandBlock)blockState;
                command.setCommand(commandLine);
                if (customName != null) {
                    command.setName(customName);
                }
                command.update();
            } else if (tileEntityData != null) {
                // Tile entity data overrides everything else, and may replace all of this in the future.
                NMSUtils.setTileEntityData(block.getLocation(), tileEntityData);
            } else if (blockState != null && (material == Material.STANDING_BANNER || material == Material.WALL_BANNER) && (customData != null || color != null)) {
                if (blockState != null && blockState instanceof Banner) {
                    Banner banner = (Banner)blockState;
                    if (customData != null && customData instanceof List)
                    {
                        banner.setPatterns((List<Pattern>)customData);
                    }
                    if (color != null)
                    {
                        banner.setBaseColor(color);
                    }
                }
                blockState.update(true, false);
            } else if (blockState != null && blockState instanceof Skull) {
                Skull skull = (Skull)blockState;
                if (skullType != null) {
                    skull.setSkullType(skullType);
                }
                if (rotation != null) {
                    skull.setRotation(rotation);
                }
                if (customData != null) {
                    CompatibilityUtils.setSkullProfile(skull, customData);
                }
                skull.update(true, false);
            } else if (blockState != null && blockState instanceof CreatureSpawner && customName != null && customName.length() > 0) {
                CreatureSpawner spawner = (CreatureSpawner)blockState;
                spawner.setCreatureTypeByName(customName);
                spawner.update();
            }
        } catch (Exception ex) {
            Bukkit.getLogger().warning("Error updating block state: " + ex.getMessage());
        }
    }

    @Override
    public Short getData() {
        return data;
    }

    @Override
    public Byte getBlockData() {
        return data == null ? null : (byte)(short)data;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    public String getKey() {
        return getKey(data);
    }

    public String getKey(Short data) {
        String materialKey = material == null ? "*" : material.name().toLowerCase();
        if (data == null) {
            materialKey += ":*";
        } else {
            // Some special keys
            if (material == Material.SKULL_ITEM && customData != null) {
                materialKey += ":" + InventoryUtils.getProfileURL(customData);
            }
            else if (material == Material.MOB_SPAWNER && customName != null && customName.length() > 0) {
                materialKey += ":" + customName;
            }
            else if ((material.getId() == 176 || material.getId() == 177 || material.getId() == 425) && color != null) {
                materialKey += ":" + color.ordinal();
            }
            else if (data != 0) {
                materialKey += ":" + data;
            }
        }

        return materialKey;
    }

    public String getWildDataKey() {
        return getKey(null);
    }

    // TODO: Should this just be !isDifferent .. ? It's fast right now.
    @SuppressWarnings("deprecation")
    public boolean is(Block block) {
        return material == block.getType() && data == block.getData();
    }

    @SuppressWarnings("deprecation")
    public boolean isDifferent(Block block) {
        Material blockMaterial = block.getType();
        byte blockData = block.getData();
        if ((material != null && blockMaterial != material) || (data != null && blockData != data)) {
            return true;
        }

        // Special cases
        if (material.getId() == 176 || material.getId() == 177) {
            // Can't compare patterns for now
            return true;
        }

        BlockState blockState = block.getState();
        if (blockState instanceof Sign) {
            // Not digging into sign text
            return true;
        } else if (blockState instanceof CommandBlock && commandLine != null) {
            CommandBlock command = (CommandBlock)blockState;
            if (!command.getCommand().equals(commandLine)) {
                return true;
            }
        } else if (blockState instanceof InventoryHolder) {
            // Just copy it over.... not going to compare inventories :P
            return true;
        }

        return false;
    }

    @Override
    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public String getCustomName() {
        return customName;
    }

    @SuppressWarnings("deprecation")
    public ItemStack getItemStack(int amount)
    {
        ItemStack stack = new ItemStack(material, amount, data);
        applyToItem(stack);
        return stack;
    }

    public ItemStack applyToItem(ItemStack stack)
    {
        stack.setType(material);
        stack.setDurability(data);
        if (material == Material.SKULL_ITEM)
        {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta instanceof SkullMeta && customData != null)
            {
                SkullMeta skullMeta = (SkullMeta)meta;
                InventoryUtils.setSkullProfile(skullMeta, customData);
                stack.setItemMeta(meta);
            }
        } else if (material == Material.STANDING_BANNER || material == Material.WALL_BANNER || material == Material.BANNER) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta instanceof BannerMeta)
            {
                BannerMeta banner = (BannerMeta)meta;
                if (this.customData != null && customData instanceof List)
                {
                    banner.setPatterns((List<Pattern>)this.customData);
                }
                if (this.color != null)
                {
                    banner.setBaseColor(this.color);
                }
            }
        }
        return stack;
    }

    public static String[] splitMaterialKey(String materialKey) {
        if (materialKey.contains("|")) {
            return StringUtils.split(materialKey, "|");
        } else if (materialKey.contains(":")) {
            return StringUtils.split(materialKey, ":");
        }

        return new String[] { materialKey };
    }

    public boolean isValid()
    {
        return isValid;
    }

    public static String getMaterialName(ItemStack item) {
        MaterialAndData material = new MaterialAndData(item);
        return material.getName();
    }

    @SuppressWarnings("deprecation")
    public static String getMaterialName(Block block) {
        MaterialAndData material = new MaterialAndData(block);
        return material.getName();
    }

    public String getName() {
        return getName(null);
    }


    @SuppressWarnings("deprecation")
    public String getBaseName() {
        if (material == null) {
            return null;
        }
        return material.name().toLowerCase().replace('_', ' ');
    }

        @SuppressWarnings("deprecation")
    public String getName(Messages messages) {
        if (!isValid()) return null;
        VaultController controller = VaultController.getInstance();
        if (controller != null && data != null) {
            try {
                String vaultName = controller.getItemName(material, data);
                if (vaultName != null && !vaultName.isEmpty()) {
                    return vaultName;
                }
            } catch (Throwable ex) {
                // Vault apparently throws exceptions on invalid item types
                // So we're just going to ignore it.
            }
        }

        String customName = getCustomName();
        String materialName = material.name();

        // This is the "right" way to do this, but relies on Bukkit actually updating Material in a timely fashion :P
        /*
        MaterialData materialData = material.getNewData((byte)(short)data);
        if (materialData instanceof Colorable) {
            materialName += " " + ((Colorable)materialData).getColor().name();
        }
        if (materialData instanceof Tree) {
            Tree tree = (Tree)materialData;
            materialName += " " + tree.getSpecies().name() + " " + tree.getDirection().name();
        }
        if (materialData instanceof Stairs) {
            Stairs stairs = (Stairs)materialData;
            materialName += " " + stairs.getFacing().name();
            // TODO: Ascending/descending directions?
        }
        if (materialData instanceof WoodenStep) {
            WoodenStep step = (WoodenStep)materialData;
            materialName += " " + step.getSpecies().name();
        }
        */

        if (data != null) {
             if (material == Material.CARPET || material == Material.STAINED_GLASS || material == Material.STAINED_CLAY || material == Material.STAINED_GLASS_PANE || material == Material.WOOL) {
                // Note that getByDyeData doesn't work for stained glass or clay. Kind of misleading?
                DyeColor color = DyeColor.getByWoolData((byte)(short)data);
                if (color != null) {
                    materialName = color.name().toLowerCase().replace('_', ' ') + " " + materialName;
                }
            } else if (material == Material.WOOD || material == Material.LOG || material == Material.SAPLING || material == Material.LEAVES
                     || material == Material.LOG_2 || material == Material.LEAVES_2) {
                TreeSpecies treeSpecies = TreeSpecies.getByData((byte)(short)data);
                if (treeSpecies != null) {
                    materialName = treeSpecies.name().toLowerCase().replace('_', ' ') + " " + materialName;
                }
            } else if (material == Material.MOB_SPAWNER && customName != null && customName.length() > 0) {
                materialName = materialName + " (" + customName + ")";
            } else if ((material.getId() == 176 || material.getId() == 177 || material.getId() == 425) && color != null) {
                 materialName = color.name().toLowerCase() + " " + materialName;
            }
        } else {
            materialName = materialName + messages.get("material.wildcard");
        }

        materialName = materialName.toLowerCase().replace('_', ' ');
        return materialName;
    }

    @Override
    public void setCommandLine(String command) {
        commandLine = command;
    }

    @Override
    public String getCommandLine() {
        return commandLine;
    }

    @Override
    public void setData(Short data) {
        this.data = data;
    }

    @Override
    public void setRawData(Object data) {
        this.tileEntityData = data;
    }

    @Override
    public String toString() {
        return material + "@" + data;
    }
}
