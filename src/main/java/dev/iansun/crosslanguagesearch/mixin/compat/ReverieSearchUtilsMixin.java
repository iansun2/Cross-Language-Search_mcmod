package dev.iansun.crosslanguagesearch.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


import dev.iansun.crosslanguagesearch.SearchText;

/**
 * Reverie Foundry catalog search (materials and traits).
 * Returns true early only when we match; otherwise the original logic runs (e.g. pinyin search when JECh is installed).
 */
@Mixin(targets = "com.drowningfish233.reveriefoundry.Utils.SearchUtils", remap = false)
public abstract class ReverieSearchUtilsMixin {

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private static void cls$matches(String text, String query, CallbackInfoReturnable<Boolean> cir) {
        if (text != null && query != null && !query.isEmpty() && SearchText.plainContains(text, query)) {
            cir.setReturnValue(true);
        }
    }
}
