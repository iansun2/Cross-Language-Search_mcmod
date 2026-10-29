package dev.iansun.crosslanguagesearch.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.neoforged.fml.loading.LoadingModList;

/**
 * Each mixin is applied only when its target mod is installed.
 * Mixins not listed here depend only on Minecraft itself and are always applied.
 */
public class CrossLanguageSearchMixinPlugin implements IMixinConfigPlugin {
    private static final String BASE = "dev.iansun.crosslanguagesearch.mixin.";
    private static final String COMPAT = BASE + "compat.";

    private static final Map<String, String> REQUIRED_MOD = Map.ofEntries(
            Map.entry(BASE + "CompiledQueryMixin", "emi"),
            Map.entry(BASE + "EmiSearchMixin", "emi"),
            Map.entry(BASE + "QueryDebugMixin", "emi"),
            Map.entry(BASE + "QueryInitMixin", "emi"),
            Map.entry(BASE + "UnbakedMatchMixin", "emi"),
            Map.entry(BASE + "UnbakedTextMixin", "emi"),
            Map.entry(COMPAT + "RsGridQueryParserMixin", "refinedstorage"),
            Map.entry(COMPAT + "EnchLibraryScreenMixin", "apothic_enchanting"),
            Map.entry(COMPAT + "ReverieSearchUtilsMixin", "reveriefoundry"),
            Map.entry(COMPAT + "JechFakeArrayMixin", "jecharacters"));

    public static boolean isLoaded(String modId) {
        return LoadingModList.get().getModFileById(modId) != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String modId = REQUIRED_MOD.get(mixinClassName);
        return modId == null || isLoaded(modId);
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
