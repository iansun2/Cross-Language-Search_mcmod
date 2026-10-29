package dev.iansun.crosslanguagesearch.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.resources.language.ClientLanguage;

/** 讀取目前語言的完整翻譯表，用來建立中英反查表 */
@Mixin(ClientLanguage.class)
public interface ClientLanguageAccessor {
    @Accessor("storage")
    Map<String, String> cls$getStorage();
}
