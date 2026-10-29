package dev.iansun.crosslanguagesearch.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


import dev.iansun.crosslanguagesearch.SearchText;

/**
 * Reverie Foundry 目錄（材料、特性）的搜尋。
 * 只在我們比對成功時提前回傳 true，否則交回原本的邏輯（例如裝了 JECh 時的拼音搜尋）。
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
