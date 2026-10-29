package dev.iansun.crosslanguagesearch.mixin.compat;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;

import dev.iansun.crosslanguagesearch.SearchText;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/**
 * JECh 用 PinIn 的 TreeSearcher 取代 EMI 的 SuffixArray，但 TreeSearcher 內部的 Accelerator 快取沒有同步。
 * EMI 每次輸入都開新的搜尋執行緒，前一個還沒跑完時兩者同時搜尋，就會拋出
 * 「IndexSet$Storage.get(int) because "data" is null」並讓整次搜尋沒有結果。
 * 這裡讓所有 FakeArray 的讀寫共用一把鎖。
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
