package appeng.client.gui.implementations;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.input.Keyboard;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.IDropToFillTextField;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerOreFilter;
import appeng.container.slot.SlotFake;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IOreFilterable;
import appeng.parts.automation.PartSharedItemBus;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.misc.TileCellWorkbench;
import appeng.util.prioitylist.OreFilteredList;
import codechicken.nei.VisiblityData;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.TaggedInventoryArea;
import cpw.mods.fml.common.Optional;

@Optional.Interface(modid = "NotEnoughItems", iface = "codechicken.nei.api.INEIGuiHandler")
public class GuiOreFilter extends AEBaseGui implements IDropToFillTextField, INEIGuiHandler {

    private MEGuiTextField textField;

    private GuiImgButton itemCanOrNot;

    public GuiOreFilter(InventoryPlayer ip, IOreFilterable obj) {
        super(new ContainerOreFilter(ip, obj));
        this.xSize = 256;

        this.textField = new MEGuiTextField(231, 12) {

            @Override
            public void onTextChange(final String oldText) {
                final String text = getText();

                if (!text.equals(oldText)) {
                    ((ContainerOreFilter) inventorySlots).setFilter(text);
                }
            }

        };

        this.textField.setMaxStringLength(120);
        // 144, 58
        this.itemCanOrNot = new GuiImgButton(0, 0, Settings.CAN_PASS_THROUGH, YesNo.NO);
        this.itemCanOrNot.enabled = false;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.textField.x = this.guiLeft + 12;
        this.textField.y = this.guiTop + 35;
        this.textField.setFocused(true);

        ((ContainerOreFilter) this.inventorySlots).setTextField(this.textField);

        this.itemCanOrNot.xPosition = this.guiLeft + 144;
        this.itemCanOrNot.yPosition = this.guiTop + 58;
        this.buttonList.add(this.itemCanOrNot);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRendererObj.drawString(GuiText.OreFilterLabel.getLocal(), 12, 8, GuiColors.OreFilterLabel.getColor());
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/orefilter.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        this.textField.drawTextBox();
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        SlotFake itemToCheck = ((ContainerOreFilter) inventorySlots).getItemToCheck();
        if (getSlotArea(itemToCheck).contains(xCoord, yCoord)) {
            itemToCheck.clearStack();
        } else {
            this.textField.mouseClicked(xCoord, yCoord, btn);
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            this.sendUpdateFilterToServer();
            this.textField.setFocused(false);

            if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
                final Object target = ((AEBaseContainer) this.inventorySlots).getTarget();
                GuiBridge OriginalGui = null;
                if (target instanceof PartStorageBus) OriginalGui = GuiBridge.GUI_STORAGEBUS;
                else if (target instanceof PartSharedItemBus) OriginalGui = GuiBridge.GUI_BUS;
                else if (target instanceof TileCellWorkbench) OriginalGui = GuiBridge.GUI_CELL_WORKBENCH;

                if (OriginalGui != null) NetworkHandler.instance.sendToServer(new PacketSwitchGuis(OriginalGui));
                else this.mc.thePlayer.closeScreen();
            }

        } else if (!this.textField.textboxKeyTyped(character, key)) {
            super.keyTyped(character, key);
        }
    }

    private void sendUpdateFilterToServer() {
        try {
            NetworkHandler.instance.sendToServer(new PacketValueConfig("OreFilter", this.textField.getText()));
            this.checkFilter();
        } catch (IOException e) {
            AELog.debug(e);
        }
    }

    public boolean isOverTextField(final int mousex, final int mousey) {
        return textField.isMouseIn(mousex, mousey);
    }

    public void setTextFieldValue(final String displayName, final int mousex, final int mousey, final ItemStack stack) {
        final int[] ores = OreDictionary.getOreIDs(stack);

        if (ores.length > 0) {
            textField.setText(OreDictionary.getOreName(ores[0]));
        } else {
            textField.setText(displayName);
        }
    }

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        return currentVisibility;
    }

    @Override
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return Collections.emptyList();
    }

    @Override
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return null;
    }

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mouseX, int mouseY, ItemStack draggedStack, int button) {
        SlotFake itemToCheck = ((ContainerOreFilter) inventorySlots).getItemToCheck();
        if (getSlotArea(itemToCheck).contains(mouseX, mouseY)) {
            ItemStack copyStack = draggedStack.copy();
            copyStack.stackSize = 1;
            itemToCheck.putStack(copyStack);
            this.checkFilter();
            return true;
        }
        return false;
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        return false;
    }

    private Rectangle getSlotArea(SlotFake slot) {
        return new Rectangle(guiLeft + slot.getX(), guiTop + slot.getY(), 16, 16);
    }

    public void checkFilter() {
        OreFilteredList oreFilteredList = new OreFilteredList(this.textField.getText());
        SlotFake itemToCheck = ((ContainerOreFilter) inventorySlots).getItemToCheck();
        if (itemToCheck.getStack() != null) {
            boolean isListed = oreFilteredList.isListed(itemToCheck.getAEStack());
            this.itemCanOrNot.set(isListed ? YesNo.YES : YesNo.NO);
        }
    }
}
