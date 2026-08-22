package com.ai.llm.pgvector;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * PDF 텍스트 추출 서비스.
 *
 * 1차: PDFBox로 페이지별 텍스트를 추출합니다.
 * 2차: 특정 페이지의 추출된 텍스트가 비정상적으로 짧으면(표/이미지가 텍스트로
 *      제대로 안 뽑힌 것으로 의심), 그 페이지만 이미지로 렌더링한 뒤
 *      Tesseract OCR로 다시 추출해서 보완합니다.
 *
 * 모든 페이지를 OCR로 돌리면 매우 느리므로, PDFBox가 실패한 페이지만 선택적으로 처리합니다.
 */
@Service
public class PdfTextExtractionService {

    // 이 값보다 짧은 텍스트가 나온 페이지는 OCR으로 재시도합니다.
    private static final int MIN_TEXT_LENGTH_PER_PAGE = 80;

    // OCR 렌더링 해상도. 높을수록 정확하지만 느립니다. 200~300이 적당합니다.
    private static final float OCR_RENDER_DPI = 250f;

    // 클래스 필드에 추가
    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractionService.class);

    @Value("${tesseract.datapath:C:/Program Files/Tesseract-OCR/tessdata}")
    private String tessDataPath;

    public String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            StringBuilder fullText = new StringBuilder();
            int totalPages = document.getNumberOfPages();

            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            Tesseract tesseract = buildTesseract();

            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);

                if (pageText.trim().length() < MIN_TEXT_LENGTH_PER_PAGE) {
                    // PDFBox 추출이 부실한 페이지 -> OCR로 재시도
                    String ocrText = tryOcr(renderer, tesseract, page - 1);
                    if (ocrText != null && ocrText.length() > pageText.length()) {
                        // System.out.printf(...) 부분을 이렇게 교체
                        log.info("[OCR] {}페이지: PDFBox {}자 -> OCR {}자로 보완", page, pageText.trim().length(), ocrText.length());
                        pageText = ocrText;
                    }
                }

                fullText.append(pageText).append("\n");
            }

            return fullText.toString();
        }
    }

    private Tesseract buildTesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessDataPath);
        tesseract.setLanguage("kor+eng"); // 한글+영문 혼용 문서 대응
        return tesseract;
    }

    private String tryOcr(PDFRenderer renderer, Tesseract tesseract, int pageIndex) {
        try {
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, OCR_RENDER_DPI);
            return tesseract.doOCR(image);
        } catch (IOException | TesseractException e) {
            // System.err.printf(...) 부분을 이렇게 교체
            log.warn("[OCR] {}페이지 OCR 실패: {}", pageIndex + 1, e.getMessage());
            return null;
        }
    }
}