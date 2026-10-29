package dev.iansun.crosslanguagesearch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.emi.emi.search.AliasQuery;
import dev.emi.emi.search.ModQuery;
import dev.emi.emi.search.NameQuery;
import dev.emi.emi.search.TooltipQuery;
import dev.iansun.crosslanguagesearch.SearchText;

/**
 * Query side: user input goes through the same normalization as the index.
 */
@Mixin(value = { NameQuery.class, TooltipQuery.class, ModQuery.class, AliasQuery.class }, remap = false)
public abstract class QueryInitMixin {

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/lang/String;toLowerCase()Ljava/lang/String;"))
    private String cls$foldQuery(String s, Operation<String> original) {
        return SearchText.fold(s);
    }
}
