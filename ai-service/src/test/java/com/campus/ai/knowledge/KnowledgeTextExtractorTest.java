package com.campus.ai.knowledge;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KnowledgeTextExtractorTest {
    static byte[] pdf(String text, int pages, String password) throws Exception {
        try (var doc = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                var page = new PDPage(); doc.addPage(page);
                if (!text.isEmpty()) try (var stream = new PDPageContentStream(doc, page)) {
                    stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700); stream.showText(text); stream.endText();
                }
            }
            if (password != null) doc.protect(new StandardProtectionPolicy("owner-password", password, new AccessPermission()));
            doc.save(output); return output.toByteArray();
        }
    }
    @Test void extractsTextPdfAndPreservesUtf8Text() throws Exception {
        assertThat(KnowledgeTextExtractor.extract("campus.PDF", pdf("Campus photo workshop: bring a camera.", 2, null)))
                .contains("Campus photo workshop: bring a camera.");
        assertThat(KnowledgeTextExtractor.extract("rules.md", "摄影社欢迎新手。".getBytes(StandardCharsets.UTF_8))).isEqualTo("摄影社欢迎新手。");
    }
    @Test void rejectsScannedOrBlankPdf() throws Exception {
        assertThatThrownBy(() -> KnowledgeTextExtractor.extract("scan.pdf", pdf("", 1, null))).hasMessageContaining("扫描");
    }
    @Test void rejectsEncryptedPdfEvenWithEmptyOpeningPassword() throws Exception {
        for (String password : new String[]{"secret", ""}) {
            byte[] bytes = pdf("Private rules", 1, password);
            assertThatThrownBy(() -> KnowledgeTextExtractor.extract("locked.pdf", bytes)).hasMessageContaining("加密");
        }
    }
    @Test void rejectsSpoofedDamagedAndUnsupportedFiles() {
        for (byte[] bytes : new byte[][]{"not a PDF".getBytes(StandardCharsets.UTF_8), "%PDF-1.7\nbroken".getBytes(StandardCharsets.UTF_8)}) {
            assertThatThrownBy(() -> KnowledgeTextExtractor.extract("fake.pdf", bytes)).hasMessageContaining("PDF");
        }
        assertThatThrownBy(() -> KnowledgeTextExtractor.extract("rules.docx", new byte[]{1})).hasMessageContaining(".pdf");
        assertThatThrownBy(() -> KnowledgeTextExtractor.extract("bad.txt", new byte[]{(byte) 0xc3, 0x28})).hasMessageContaining("UTF-8");
        assertThatThrownBy(() -> KnowledgeTextExtractor.extract("nul.txt", new byte[]{0})).hasMessageContaining("非文本");
    }
    @Test void enforcesBytePageAndExtractedTextLimits() throws Exception {
        assertThatThrownBy(() -> KnowledgeTextExtractor.extract("large.pdf", new byte[5 * 1024 * 1024 + 1])).hasMessageContaining("5MB");
        assertThatThrownBy(() -> KnowledgeTextExtractor.extract("large.txt", new byte[128 * 1024 + 1])).hasMessageContaining("128KB");
        assertThatThrownBy(() -> KnowledgeTextExtractor.extract("empty.pdf", new byte[0])).hasMessageContaining("非空");
        assertThatThrownBy(() -> KnowledgeTextExtractor.extract("pages.pdf", pdf("page", 51, null))).hasMessageContaining("50 页");
        byte[] compressedText = pdf("a".repeat(3000), 50, null);
        assertThat(compressedText.length).isLessThan(128 * 1024);
        assertThatThrownBy(() -> KnowledgeTextExtractor.extract("expanded.pdf", compressedText)).hasMessageContaining("正文不能超过 128KB");
        assertThatThrownBy(() -> KnowledgeTextExtractor.extract("utf8-size.pdf", pdf("é".repeat(3000), 30, null))).hasMessageContaining("正文不能超过 128KB");
    }
}
