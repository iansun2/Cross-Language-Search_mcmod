package dev.iansun.crosslanguagesearch.mixin.compat;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;

import dev.iansun.crosslanguagesearch.SearchText;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/**
 * JECh replaces EMI's SuffixArray with PinIn's TreeSearcher, whose internal Accelerator cache is not synchronized.
 * EMI starts a new search thread on every keystroke; when the previous one is still running, both search at once and throw
 * "IndexSet$Storage.get(int) because "data" is null", leaving the whole search with no results.
 * Here all FakeArray reads and writes share one lock.
 */
@Mixin(targets = "me.towdium.jecharacters.utils.FakeArray", remap = false)
public abstract class JechFakeArrayMixin {

    @WrapMethod(method = "search")
    private List<?> cls$lockSearch(String query, Operation<List<?>> original) {
        synchronized (SearchText.PININ_LOCK) {
            return original.call(query);
        }
    }

    @WrapMethod(method = "add")
    private void cls$lockAdd(Object value, String text, Operation<Void> original) {
        synchronized (SearchText.PININ_LOCK) {
            original.call(value, text);
        }
    }
}
