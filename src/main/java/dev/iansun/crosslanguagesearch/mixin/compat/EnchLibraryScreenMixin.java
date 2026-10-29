package dev.iansun.crosslanguagesearch.mixin.compat;

import java.util.Locale;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.iansun.crosslanguagesearch.SearchText;
import net.minecraft.network.chat.Component;

/**
 * Apothic Enchanting library filter: append English to enchantment names, and fold both sides.
 */
@Mixin(targets = "dev.shadowsoffire.apothic_enchanting.library.EnchLibraryScreen", remap = false)
public abstract class EnchLibraryScreenMixin {

    @WrapOperation(method = "isAllowedBySearch", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;"))
    private String cls$appendSecondary(Component component, Operation<String> original) {
        return SearchText.withSecondary(component, original.call(component));
    }

    @WrapOperation(method = "isAllowedBySearch", at = @At(value = "INVOKE", target = "Ljava/lang/String;toLowerCase(Ljava/util/Locale;)Ljava/lang/String;"))
    private String cls$fold(String s, Locale locale, Operation<String> original) {
        return SearchText.fold(s);
    }
}
