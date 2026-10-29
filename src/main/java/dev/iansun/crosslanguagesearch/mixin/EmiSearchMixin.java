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
 * Index side: append other-language variants to every text, and replace lowercasing with full normalization.
 */
@Mixin(value = EmiSearch.class, remap = false)
public abstract class EmiSearchMixin {

    @Inject(method = "bake", at = @At("HEAD"))
    private static void cls$reloadLanguage(CallbackInfo ci) {
        SearchText.ensureLoaded();
        DynamicTooltips.requestReset();
    }

    /** Names, tooltips and registry aliases are all turned into strings here */
    @WrapOperation(method = "bake", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;"))
    private static String cls$appendSecondary(Component component, Operation<String> original) {
        return SearchText.withSecondary(component, original.call(component));
    }

    /** Every indexed string goes through toLowerCase; replacing it with fold unifies Traditional/Simplified and full/half-width */
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
