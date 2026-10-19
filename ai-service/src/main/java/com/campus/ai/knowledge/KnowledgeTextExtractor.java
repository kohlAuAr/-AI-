package com.campus.ai.knowledge;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class KnowledgeTextExtractor {
    private static final int MAX_TEXT_BYTES = 128 * 1024;
    private KnowledgeTextExtractor() {}

    static String extract(String name, byte[] bytes) {
        String lower = name.toLowerCase(Locale.ROOT);
        boolean pdf = lower.endsWith(".pdf");
        if (!(pdf || lower.endsWith(".md") || lower.endsWith(".txt"))) throw invalid("仅支持 UTF-8 的 .md / .txt 或文字版 .pdf 资料");
        if (bytes.length == 0) throw invalid("资料文件必须非空");
        if (bytes.length > (pdf ? 5 * 1024 * 1024 : MAX_TEXT_BYTES)) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, pdf ? "PDF 文件不能超过 5MB" : "文本资料不能超过 128KB");
        }
        String content;
        if (pdf) content = extractPdf(bytes);
        else {
            try { content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
            catch (CharacterCodingException error) { throw invalid("资料不是有效的 UTF-8 文本"); }
        }
        if (content.indexOf('\0') >= 0) throw invalid("资料包含非文本内容");
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) throw invalid("提取后的正文不能超过 128KB，请拆分资料后上传");
        return content;
    }

    private static String extractPdf(byte[] bytes) {
        if (!new String(bytes, 0, Math.min(5, bytes.length), StandardCharsets.US_ASCII).equals("%PDF-")) throw invalid("文件不是有效的 PDF，请重新导出后上传");
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) throw invalid("暂不支持加密 PDF，请移除密码和加密后上传");
            if (document.getNumberOfPages() > 50) throw invalid("PDF 不能超过 50 页，请拆分后上传");
            var output = new BoundedTextWriter();
            var stripper = new PDFTextStripper(); stripper.setSortByPosition(true);
            stripper.writeText(document, output);
            String text = output.text.toString().strip();
            if (text.isBlank()) throw invalid("PDF 未提取到正文，暂不支持纯扫描件或图片 PDF，请转成文字版后上传");
            return text;
        } catch (InvalidPasswordException error) { throw invalid("暂不支持加密 PDF，请移除密码和加密后上传"); }
        catch (IOException | IllegalArgumentException error) { throw invalid("PDF 无法解析或已损坏，请重新导出文字版 PDF 后上传"); }
    }

    // Abort growing text output before retaining a complete oversized decompressed document.
    private static final class BoundedTextWriter extends Writer {
        final StringBuilder text = new StringBuilder();
        @Override public void write(char[] chars, int offset, int length) {
            if (text.length() + length > MAX_TEXT_BYTES) throw invalid("提取后的正文不能超过 128KB，请拆分资料后上传");
            text.append(chars, offset, length);
        }
        @Override public void flush() {}
        @Override public void close() {}
    }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
