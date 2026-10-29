package dev.iansun.crosslanguagesearch.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.emi.emi.search.EmiSearch;
import dev.iansun.crosslanguagesearch.DynamicTooltips;
import dev.iansun.crosslanguagesearch.SearchDebug;
import dev.iansun.crosslanguagesearch.SearchText;
import net.minecraft.network.chat.Component;

/**
 * 建索引端：每段文字附上英文版本，並把小寫轉換換成完整的正規化。
 */
@Mixin(value = EmiSearch.class, remap = false)
public abstract class EmiSearchMixin {

    @Inject(method = "bake", at = @At("HEAD"))
    private static void cls$reloadLanguage(CallbackInfo ci) {
        SearchText.ensureLoaded();
        DynamicTooltips.requestReset();
    }

    /** 名稱、工具提示、註冊別名的 Component 都在這裡轉成字串 */
    @WrapOperation(method = "bake", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;"))
    private static String cls$appendSecondary(Component component, Operation<String> original) {
        return SearchText.withSecondary(component, original.call(component));
    }

    /** 所有放進索引的字串都會呼叫 toLowerCase，改成 fold 讓繁簡、全半形一致 */
    @WrapOperation(method = "bake", at = @At(value = "INVOKE", target = "Ljava/lang/String;toLowerCase()Ljava/lang/String;"))
    private static String cls$fold(String s, Operation<String> original) {
        String folded = SearchText.fold(s);
        if (SearchDebug.ENABLED) SearchDebug.probeIndexed(folded);
        return folded;
    }

    @Inject(method = "search", at = @At("HEAD"))
    private static void cls$debugStart(String query, CallbackInfo ci) {
        SearchDebug.searchStarted(query);
    }

    @Inject(method = "apply", at = @At("HEAD"))
    private static void cls$debugApply(@Coerce Object worker, List<?> stacks, CallbackInfo ci) {
        SearchDebug.searchApplied(stacks == null ? -1 : stacks.size());
    }
}
