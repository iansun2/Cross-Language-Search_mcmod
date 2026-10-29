package dev.iansun.crosslanguagesearch.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.emi.emi.api.stack.EmiStack;
import dev.iansun.crosslanguagesearch.DynamicTooltips;

/**
 * EMI 用 bakedStacks.contains(stack) 決定走索引或即時比對。
 * 動態工具提示的物品回傳 false，讓它們每次搜尋都即時重算。
 */
@Mixin(targets = "dev.emi.emi.search.EmiSearch$CompiledQuery", remap = false)
public abstract class CompiledQueryMixin {

    @WrapOperation(method = "test", at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"))
    private boolean cls$skipDynamic(Set<?> set, Object stack, Operation<Boolean> original) {
        if (stack instanceof EmiStack emiStack && DynamicTooltips.isDynamic(emiStack)) return false;
        return original.call(set, stack);
    }
}
