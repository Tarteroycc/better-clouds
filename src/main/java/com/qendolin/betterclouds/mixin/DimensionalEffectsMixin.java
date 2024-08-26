package com.qendolin.betterclouds.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.qendolin.betterclouds.Main;
import com.qendolin.betterclouds.compat.SodiumExtraCompat;
import net.minecraft.client.render.DimensionEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DimensionEffects.class)
public abstract class DimensionalEffectsMixin {

    // This doesn't work with sodium extras see DimensionEffectsOverworldMixin

    @ModifyReturnValue(method = "getCloudsHeight", at = @At("RETURN"))
    public float addCloudsYOffset(float value) {
        if (SodiumExtraCompat.IS_LOADED) return value;
        //noinspection ConstantValue,EqualsBetweenInconvertibleTypes
        if (!this.getClass().equals(DimensionEffects.Overworld.class)) return value;
        if (!Main.getConfig().enabled) return value;

        return value + Main.getConfig().yOffset;
    }
}
