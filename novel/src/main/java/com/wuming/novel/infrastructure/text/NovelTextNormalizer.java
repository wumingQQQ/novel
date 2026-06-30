package com.wuming.novel.infrastructure.text;

import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class NovelTextNormalizer {
    public String normalizeForPrompt(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u3000', ' ')  // 全角空格
                .replace('\u00A0', ' ')  // 不换行空格
                .replace("\uFEFF", "") // 零宽不换行空格，BOM
                .replace("\u200B", "") // 零宽空格
                .lines()
                .map(line -> line.trim().replaceAll("[ \\t\\f]+", " "))  // 制表符，换页符
                .collect(Collectors.joining("\n"))
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public String normalizeForMatch(String text) {
        String promptText = normalizeForPrompt(text);
        StringBuilder builder = new StringBuilder(promptText.length());
        for (int i = 0; i < promptText.length(); i++) {
            appendNormalizedMatchChar(builder, promptText.charAt(i));
        }
        return builder.toString();
    }

    void appendNormalizedMatchChar(StringBuilder builder, char ch) {
        switch (ch) {
            case '“', '”', '「', '」', '『', '』' -> builder.append('"');
            case '‘', '’' -> builder.append('\'');
            case '：' -> builder.append(':');
            case '；' -> builder.append(';');
            case '，' -> builder.append(',');
            case '。' -> builder.append('.');
            case '？' -> builder.append('?');
            case '！' -> builder.append('!');
            case '—' -> builder.append('-');
            case '…' -> builder.append('.');
            default -> builder.append(ch);
        }
    }
}
