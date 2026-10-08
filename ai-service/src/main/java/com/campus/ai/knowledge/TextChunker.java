package com.campus.ai.knowledge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Adapted from PaiSmart ParseService paragraph/sentence splitting. See THIRD-PARTY-NOTICES.md. */
@Component
public class TextChunker {
    private static final int CHUNK_SIZE = 600;
    private static final int OVERLAP = 80;

    public List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : text.replace("\r\n", "\n").split("\n\n+")) {
            if (paragraph.isBlank()) continue;
            paragraph = paragraph.trim();
            if (paragraph.length() > CHUNK_SIZE) {
                flush(currentChunk, chunks);
                chunks.addAll(splitLongParagraph(paragraph));
            } else if (currentChunk.length() + paragraph.length() + (currentChunk.isEmpty() ? 0 : 2) > CHUNK_SIZE) {
                flush(currentChunk, chunks);
                currentChunk.append(paragraph);
            } else {
                if (!currentChunk.isEmpty()) currentChunk.append("\n\n");
                currentChunk.append(paragraph);
            }
        }
        flush(currentChunk, chunks);
        List<String> overlapped = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String previous = i == 0 ? "" : chunks.get(i - 1);
            String overlap = previous.substring(Math.max(0, previous.length() - OVERLAP));
            overlapped.add(overlap.isEmpty() ? chunks.get(i) : overlap + "\n\n" + chunks.get(i));
        }
        return overlapped;
    }

    private List<String> splitLongParagraph(String paragraph) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        String[] sentences = paragraph.split("(?<=[。！？；])|(?<=[.!?;])\\s+");
        for (String sentence : sentences) {
            if (currentChunk.length() + sentence.length() > CHUNK_SIZE) {
                flush(currentChunk, chunks);
                if (sentence.length() > CHUNK_SIZE) {
                    for (int start = 0; start < sentence.length(); start += CHUNK_SIZE) {
                        chunks.add(sentence.substring(start, Math.min(start + CHUNK_SIZE, sentence.length())));
                    }
                } else currentChunk.append(sentence);
            } else currentChunk.append(sentence);
        }
        flush(currentChunk, chunks);
        return chunks;
    }

    private void flush(StringBuilder current, List<String> chunks) {
        if (!current.isEmpty()) chunks.add(current.toString().trim());
        current.setLength(0);
    }
}
