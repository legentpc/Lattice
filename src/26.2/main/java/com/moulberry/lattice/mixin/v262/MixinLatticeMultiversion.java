package com.moulberry.lattice.mixin.v262;

import com.moulberry.lattice.LatticeMultiversion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(LatticeMultiversion.class)
public class MixinLatticeMultiversion {

    @Overwrite
    public static void setScreen(Minecraft minecraft, Screen screen) {
        minecraft.gui.setScreen(screen);
    }

}
