package com.wuming.novel.text;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TextAnchorMatcher {
    private final NovelTextNormalizer normalizer;

    public int indexOf(String content, String anchor) {
        if (isBlank(content) || isBlank(anchor)) {
            return -1;
        }

        int exactIndex = content.indexOf(anchor);
        if (exactIndex >= 0) {
            return exactIndex;
        }

        String normalizedContent = normalizer.normalizeForMatch(content);
        String normalizedAnchor = normalizer.normalizeForMatch(anchor);
        return normalizedContent.indexOf(normalizedAnchor);
    }

    public boolean containsQuote(String content, String quote) {
        return indexOf(content, quote) >= 0;
    }

    public boolean containsQuoteInAny(Iterable<String> contents, String quote) {
        if (isBlank(quote)) {
            return false;
        }
        for (String content : contents) {
            if (containsQuote(content, quote)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
