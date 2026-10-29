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
 * 不在索引裡的物品（例如帶有特殊資料的物品）由 EMI 即時比對，這裡也要用同一套正規化。
 */
@Mixin(value = { NameQuery.class, TooltipQuery.class, ModQuery.class }, remap = false)
public abstract class UnbakedMatchMixin {

    @WrapOperation(method = "matchesUnbaked", at = @At(value = "INVOKE", target = "Ljava/lang/String;toLowerCase()Ljava/lang/String;"))
    private String cls$foldText(String s, Operation<String> original) {
        return SearchText.fold(s);
    }
}
