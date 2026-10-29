package dev.iansun.crosslanguagesearch.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.search.ModQuery;
import dev.emi.emi.search.NameQuery;
import dev.emi.emi.search.TooltipQuery;
import dev.iansun.crosslanguagesearch.SearchDebug;

/** 診斷：每個子查詢從索引命中了幾個物品 */
@Mixin(value = { NameQuery.class, TooltipQuery.class, ModQuery.class }, remap = false)
public abstract class QueryDebugMixin {
    @Shadow
    @Final
    private Set<EmiStack> valid;

    @Shadow
    @Final
    private String name;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cls$logHits(String query, CallbackInfo ci) {
        if (SearchDebug.ENABLED) {
            SearchDebug.log("{} \"{}\" -> index query \"{}\" matched {}", getClass().getSimpleName(), query, name, valid.size());
        }
    }
}
