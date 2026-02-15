package io.b2mash.b2b.b2bstrawman.template;

/** Result of PDF rendering containing the PDF bytes, a suggested filename, and an HTML preview. */
public record PdfResult(byte[] pdfBytes, String fileName, String htmlPreview) {}
