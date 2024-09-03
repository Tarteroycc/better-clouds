package com.qendolin.betterclouds.mixin.optional;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.qendolin.betterclouds.Main;
import net.minecraft.client.render.DimensionEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// This mixin exists for compat with sodium extras
@Mixin(value = DimensionEffects.Overworld.class, priority = 1100)
public abstract class DimensionEffectsOverworldMixin extends DimensionEffects {
    public DimensionEffectsOverworldMixin(float cloudsHeight, boolean alternateSkyColor, SkyType skyType, boolean brightenLighting, boolean darkened) {
        super(cloudsHeight, alternateSkyColor, skyType, brightenLighting, darkened);
    }

    // Note: getCloudsHeight doesn't exist at compile time. Because of that, the full descriptor is required.
    @SuppressWarnings({"UnresolvedMixinReference", "MixinAnnotationTarget"})
    @ModifyReturnValue(method = "getCloudsHeight()F", at = @At("RETURN"), expect = 0, require = 0)
    private float addCloudsYOffset(float value) {
        if(!Main.getConfig().enabled) return value;
        return value + Main.getConfig().yOffset;
    }
}
