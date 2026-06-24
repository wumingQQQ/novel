package com.wuming.novel.text;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TextAnchorMatcher {
    private final NovelTextNormalizer normalizer;

    public Optional<TextMatch> find(String content, String anchor) {
        if (isBlank(content) || isBlank(anchor)) {
            return Optional.empty();
        }

        int exactIndex = content.indexOf(anchor);
        if (exactIndex >= 0) {
            return Optional.of(new TextMatch(
                    exactIndex,
                    exactIndex + anchor.length(),
                    MatchMode.EXACT
            ));
        }

        IndexedText normalizedContent = normalizeContentWithIndexes(content);
        String normalizedAnchor = normalizer.normalizeForMatch(anchor);
        int normalizedIndex = normalizedContent.text().indexOf(normalizedAnchor);
        if (normalizedIndex < 0) {
            return Optional.empty();
        }

        int normalizedEndIndex = normalizedIndex + normalizedAnchor.length() - 1;
        return Optional.of(new TextMatch(
                normalizedContent.originalIndexAt(normalizedIndex),
                normalizedContent.originalIndexAt(normalizedEndIndex) + 1,
                MatchMode.NORMALIZED
        ));
    }

    public boolean containsQuote(String content, String quote) {
        return find(content, quote).isPresent();
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

    private IndexedText normalizeContentWithIndexes(String content) {
        StringBuilder builder = new StringBuilder(content.length());
        int[] indexes = new int[content.length()];
        int normalizedLength = 0;
        boolean previousWhitespace = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = normalizeBaseChar(content.charAt(i));
            if (ch == 0) {
                continue;
            }
            if (isCollapsibleWhitespace(ch)) {
                if (previousWhitespace) {
                    continue;
                }
                ch = ' ';
                previousWhitespace = true;
            } else {
                previousWhitespace = false;
            }

            int beforeLength = builder.length();
            normalizer.appendNormalizedMatchChar(builder, ch);
            for (int j = beforeLength; j < builder.length(); j++) {
                indexes[normalizedLength++] = i;
            }
        }

        int[] usedIndexes = new int[normalizedLength];
        System.arraycopy(indexes, 0, usedIndexes, 0, normalizedLength);
        return new IndexedText(builder.toString(), usedIndexes);
    }

    private char normalizeBaseChar(char ch) {
        return switch (ch) {
            case '\r' -> '\n';
            case '\u3000', '\u00A0' -> ' ';
            case '\uFEFF', '\u200B' -> 0;
            default -> ch;
        };
    }

    private boolean isCollapsibleWhitespace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\f';
    }

    private record IndexedText(String text, int[] originalIndexes) {
        int originalIndexAt(int normalizedIndex) {
            if (normalizedIndex < 0 || normalizedIndex >= originalIndexes.length) {
                return -1;
            }
            return originalIndexes[normalizedIndex];
        }
    }
}
