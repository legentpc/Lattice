package com.moulberry.lattice.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@ApiStatus.Internal
public abstract class DraggableListWidget<T> extends AbstractWidget implements WidgetExtraFunctionality {

    private static final Component DRAG_HANDLE = Component.literal("\u2261");
    private static final Component REMOVE_CROSS = Component.literal("\u2715");
    private static final Component ADD_ENTRY = Component.literal("+ Add");
    private static final int REMOVE_BUTTON_WIDTH = 15;

    private final Font font;
    private final List<T> values;
    private final T[] allValues;
    private final boolean allowDeleting;
    private final boolean requireNonEmpty;
    private final int rowHeight;

    private boolean showAddRow;
    private int dragIndex = -1;
    private boolean dragging = false;
    private double dragOffsetY = 0;

    private boolean showingAddSelection = false;
    private AddSelection addSelection = null;

    @SafeVarargs
    public DraggableListWidget(int x, int y, int width, Font font, Component title, List<T> initialValues,
                               boolean allowDeleting, boolean requireNonEmpty, T... allValues) {
        super(x, y, width, 0, title);
        this.font = font;
        this.values = initialValues == null ? new ArrayList<>() : new ArrayList<>(initialValues);
        this.allValues = allValues;
        this.allowDeleting = allowDeleting;
        this.requireNonEmpty = requireNonEmpty;
        this.rowHeight = font.lineHeight + 5;
        this.updateHeight();
    }

    public abstract void setValue(List<T> values);

    @Override
    public @Nullable GuiEventListener getPopup() {
        return this.showingAddSelection ? this.addSelection : null;
    }

    private void updateHeight() {
        this.showAddRow = !this.getRemainingValues().isEmpty();
        int rows = this.values.size() + (this.showAddRow ? 1 : 0);
        this.height = 2 + this.rowHeight * rows;
    }

    private void notifyValueChanged() {
        this.updateHeight();
        this.setValue(new ArrayList<>(this.values));
    }

    private boolean canDeleteRightNow() {
        return this.allowDeleting && (this.values.size() > 1 || !this.requireNonEmpty);
    }

    private List<T> getRemainingValues() {
        List<T> remaining = new ArrayList<>();
        for (T value : this.allValues) {
            if (!this.values.contains(value)) {
                remaining.add(value);
            }
        }
        return remaining;
    }

    private int rowIndexAt(double mouseY) {
        int relativeY = (int) (mouseY - this.getY() - 1);
        if (relativeY < 0) {
            return -1;
        }
        return relativeY / this.rowHeight;
    }

    private boolean isOverRemoveButton(double mouseX) {
        return mouseX >= this.getX() + this.getWidth() - REMOVE_BUTTON_WIDTH;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();

        // Border and background, matching the style of DropdownWidget's selection list
        int borderColour = this.isFocused() ? 0xFFFFFFFF : 0xFF000000;
        guiGraphics.fill(x, y, x + width, y + 1, borderColour);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, borderColour);
        guiGraphics.fill(x, y + 1, x + 1, y + height - 1, borderColour);
        guiGraphics.fill(x + width - 1, y + 1, x + width, y + height - 1, borderColour);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xF0101010);

        boolean canDelete = this.canDeleteRightNow();
        boolean hoveringList = this.active && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;

        int rowY = y + 1;
        for (int i = 0; i < this.values.size(); i++) {
            if (this.dragging && i == this.dragIndex) {
                // Leave a highlighted gap where the dragged row will be dropped
                guiGraphics.fill(x + 1, rowY, x + width - 1, rowY + this.rowHeight, 0x22FFFFFF);
            } else {
                boolean hoveredRow = !this.dragging && hoveringList && mouseY >= rowY && mouseY < rowY + this.rowHeight;
                if (hoveredRow) {
                    guiGraphics.fill(x + 1, rowY, x + width - 1, rowY + this.rowHeight, 0x18FFFFFF);
                }
                boolean removeHovered = hoveredRow && this.isOverRemoveButton(mouseX);
                this.extractRow(guiGraphics, this.values.get(i), x, rowY, width, canDelete, removeHovered);
            }
            rowY += this.rowHeight;
        }

        if (this.showAddRow) {
            boolean hoveredRow = !this.dragging && hoveringList && mouseY >= rowY && mouseY < rowY + this.rowHeight;
            if (hoveredRow) {
                guiGraphics.fill(x + 1, rowY, x + width - 1, rowY + this.rowHeight, 0x18FFFFFF);
            }
            guiGraphics.centeredText(this.font, ADD_ENTRY, x + width / 2, rowY + 3, 0xFFA0A0A0);
        }

        // The dragged row is rendered last so it appears above everything else
        if (this.dragging && this.dragIndex >= 0 && this.dragIndex < this.values.size()) {
            int floatingY = (int) (mouseY - this.dragOffsetY);
            floatingY = Math.max(y + 1, Math.min(y + height - 1 - this.rowHeight, floatingY));
            guiGraphics.fill(x + 1, floatingY, x + width - 1, floatingY + this.rowHeight, 0xF0202026);
            this.extractRow(guiGraphics, this.values.get(this.dragIndex), x, floatingY, width, false, false);
        }
    }

    private void extractRow(GuiGraphicsExtractor guiGraphics, T value, int x, int rowY, int width, boolean showRemove, boolean removeHovered) {
        int textY = rowY + 3;
        guiGraphics.text(this.font, DRAG_HANDLE, x + 5, textY, 0xFFA0A0A0);
        guiGraphics.text(this.font, Component.literal(value.toString()), x + 16, textY, 0xFFFFFFFF);
        if (showRemove) {
            guiGraphics.text(this.font, REMOVE_CROSS, x + width - 12, textY, removeHovered ? 0xFFFF5555 : 0xFFA0A0A0);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 || !this.isMouseOver(event.x(), event.y())) {
            return false;
        }

        int index = this.rowIndexAt(event.y());
        if (index >= 0 && index < this.values.size()) {
            if (this.canDeleteRightNow() && this.isOverRemoveButton(event.x())) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.values.remove(index);
                this.notifyValueChanged();
            } else {
                this.dragIndex = index;
                this.dragging = false;
                this.dragOffsetY = event.y() - (this.getY() + 1 + index * this.rowHeight);
            }
        } else if (index == this.values.size() && this.showAddRow) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.openAddSelection();
        }

        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragIndex < 0 || this.values.isEmpty()) {
            return false;
        }

        this.dragging = true;

        int target = (int) Math.floor((event.y() - this.getY() - 1) / this.rowHeight);
        target = Math.max(0, Math.min(this.values.size() - 1, target));

        if (target != this.dragIndex) {
            this.values.add(target, this.values.remove(this.dragIndex));
            this.dragIndex = target;
            this.notifyValueChanged();
        }

        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragIndex >= 0) {
            this.dragIndex = -1;
            this.dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            this.showingAddSelection = false;
            this.addSelection = null;
            this.dragIndex = -1;
            this.dragging = false;
        }
    }

    private void openAddSelection() {
        List<T> remaining = this.getRemainingValues();
        if (remaining.isEmpty()) {
            return;
        }

        int itemHeight = this.font.lineHeight + 2;
        int contentHeight = remaining.size() * itemHeight + 4;
        int selectionHeight = Math.min(contentHeight, 100);
        this.addSelection = new AddSelection(Minecraft.getInstance(), this.getWidth(), selectionHeight, this.getY(), itemHeight);

        List<AddEntry> entries = new ArrayList<>(remaining.size());
        for (T value : remaining) {
            entries.add(new AddEntry(value));
        }
        this.addSelection.replaceEntries(entries);
        this.showingAddSelection = true;
    }

    private void addValue(T value) {
        this.values.add(value);
        this.notifyValueChanged();
        this.showingAddSelection = false;
        this.addSelection = null;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
    }

    private class AddSelection extends ObjectSelectionList<AddEntry> {
        public AddSelection(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        private boolean replaceEntries = false;

        @Override
        protected void extractListBackground(GuiGraphicsExtractor guiGraphics) {
            guiGraphics.fill(
                    this.getX(),
                    this.getY(),
                    this.getRight(),
                    this.getBottom(),
                    0xF0101010
            );

            int minX = this.getX();
            int minY = this.getY()-1;
            int maxX = minX+this.getWidth();
            int maxY = minY+this.getHeight()+2;
            int colour = DraggableListWidget.this.isFocused() ? 0xFFFFFFFF : 0xFF000000;
            guiGraphics.fill(minX, minY, maxX, minY + 1, colour);
            guiGraphics.fill(minX, maxY - 1, maxX, maxY, colour);
            guiGraphics.fill(minX, minY + 1, minX + 1, maxY - 1, colour);
            guiGraphics.fill(maxX - 1, minY + 1, maxX, maxY - 1, colour);

            if (this.replaceEntries) {
                this.replaceEntries = false;
                this.replaceEntries(new ArrayList<>(this.children()));
            }
        }

        @Override
        public void setX(int x) {
            this.replaceEntries |= x != this.getX();
            super.setX(x);
        }

        @Override
        public void setY(int y) {
            this.replaceEntries |= y != this.getY();
            super.setY(y);
        }

        @Override
        protected void extractListSeparators(GuiGraphicsExtractor guiGraphics) {
        }

        @Override
        public int getRowWidth() {
            return Math.max(52, this.getWidth()) - 52;
        }

        @Override
        protected int scrollBarX() {
            return this.getRowRight() + 8;
        }

        @Override
        public void visitWidgets(Consumer<AbstractWidget> consumer) {
        }
    }

    public class AddEntry extends ObjectSelectionList.Entry<AddEntry> {
        final T value;

        public AddEntry(final T value) {
            this.value = value;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            graphics.text(DraggableListWidget.this.font,
                    Component.literal(this.value.toString()), this.getX(), this.getY(), -1);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.isSelection()) {
                DraggableListWidget.this.addValue(this.value);
            }
            return true;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            DraggableListWidget.this.addValue(this.value);
            super.mouseClicked(event, doubleClick);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", this.value);
        }
    }
}
