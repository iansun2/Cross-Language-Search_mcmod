package dev.iansun.crosslanguagesearch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.emi.emi.search.NameQuery;
import dev.emi.emi.search.TooltipQuery;
import dev.iansun.crosslanguagesearch.SearchText;
import net.minecraft.network.chat.Component;

/**
 * 即時比對時同樣附上英文版本。
 */
@Mixin(value = { NameQuery.class, TooltipQuery.class }, remap = false)
public abstract class UnbakedTextMixin {

    @WrapOperation(method = "matchesUnbaked", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;"))
    private String cls$appendSecondary(Component component, Operation<String> original) {
        return SearchText.withSecondary(component, original.call(component));
    }
}
