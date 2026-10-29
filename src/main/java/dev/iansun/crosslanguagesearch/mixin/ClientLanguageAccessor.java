package dev.iansun.crosslanguagesearch.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.resources.language.ClientLanguage;

/** Reads the full translation table of a language, used to build the reverse lookup */
@Mixin(ClientLanguage.class)
public interface ClientLanguageAccessor {
    @Accessor("storage")
    Map<String, String> cls$getStorage();
}
