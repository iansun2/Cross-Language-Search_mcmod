package dev.iansun.crosslanguagesearch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.emi.emi.search.ModQuery;
import dev.emi.emi.search.NameQuery;
import dev.emi.emi.search.TooltipQuery;
import dev.iansun.crosslanguagesearch.SearchText;

/**
 * Items not in the index (e.g. stacks with special data) are matched live by EMI; apply the same normalization there.
 */
@Mixin(value = { NameQuery.class, TooltipQuery.class, ModQuery.class }, remap = false)
public abstract class UnbakedMatchMixin {

    @WrapOperation(method = "matchesUnbaked", at = @At(value = "INVOKE", target = "Ljava/lang/String;toLowerCase()Ljava/lang/String;"))
    private String cls$foldText(String s, Operation<String> original) {
        return SearchText.fold(s);
    }
}
