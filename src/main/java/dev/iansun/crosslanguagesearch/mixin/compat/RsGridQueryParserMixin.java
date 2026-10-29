package dev.iansun.crosslanguagesearch.mixin.compat;

import java.util.LinkedHashSet;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepository;
import com.refinedmods.refinedstorage.common.api.grid.GridResourceAttributeKeys;
import com.refinedmods.refinedstorage.common.api.grid.view.GridResource;
import com.refinedmods.refinedstorage.common.api.grid.view.GridResourceAttributeKey;
import com.refinedmods.refinedstorage.common.grid.query.GridQueryParser;
import com.refinedmods.refinedstorage.common.grid.view.ItemGridResource;

import dev.iansun.crosslanguagesearch.SearchText;

/**
 * Refined Storage grid search: both the query and item names go through normalize(); folding there unifies Traditional/Simplified.
 * Names also get their reverse-lookup translation appended.
 */
@Mixin(value = GridQueryParser.class, remap = false)
public abstract class RsGridQueryParserMixin {

    @Inject(method = "normalize", at = @At("HEAD"), cancellable = true)
    private static void cls$fold(String value, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(SearchText.foldCached(value.trim()));
    }

    @WrapOperation(method = "lambda$parseLiteral$8", at = @At(value = "INVOKE", target = "Lcom/refinedmods/refinedstorage/common/api/grid/view/GridResource;getName()Ljava/lang/String;"))
    private static String cls$nameWithSecondary(GridResource resource, Operation<String> original) {
        return withSecondary(original.call(resource));
    }

    @WrapOperation(method = "lambda$parseLiteral$8", at = @At(value = "INVOKE", target = "Lcom/refinedmods/refinedstorage/common/api/grid/view/GridResource;getSearchableNames()Ljava/util/Set;"))
    private static Set<String> cls$namesWithSecondary(GridResource resource, Operation<Set<String>> original) {
        Set<String> names = original.call(resource);
        if (names == null || names.isEmpty()) return names;
        Set<String> result = new LinkedHashSet<>(names.size());
        for (String name : names) result.add(withSecondary(name));
        return result;
    }

    /** $ tooltip search: render English straight from the item's components instead of the reverse lookup */
    @Inject(method = "lambda$attributeMatch$7", at = @At("HEAD"), cancellable = true)
    private static void cls$tooltip(Set<GridResourceAttributeKey> keys, String query, ResourceRepository<?> repository,
            GridResource resource, CallbackInfoReturnable<Boolean> cir) {
        if (!keys.contains(GridResourceAttributeKeys.TOOLTIP) || !(resource instanceof ItemGridResource item)) return;
        String q = SearchText.foldCached(query.trim());
        if (!q.isEmpty() && SearchText.tooltipSecondary(item.getItemStack()).contains(q)) cir.setReturnValue(true);
    }

    /** @, #, $ attribute search (mod, tag, tooltip). Parameter order: query, attribute value */
    @Inject(method = "lambda$attributeMatch$6", at = @At("HEAD"), cancellable = true)
    private static void cls$attribute(String query, String value, CallbackInfoReturnable<Boolean> cir) {
        if (SearchText.plainContains(value, query)) cir.setReturnValue(true);
    }

    private static String withSecondary(String name) {
        if (name == null) return null;
        String en = SearchText.plainSecondary(name);
        return en.isEmpty() ? name : name + SearchText.SEPARATOR + en;
    }
}
