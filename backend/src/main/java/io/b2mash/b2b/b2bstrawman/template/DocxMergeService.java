package io.b2mash.b2b.b2bstrawman.template;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

/**
 * Merges {{variable}} placeholders in .docx templates with values from a context map. Handles the
 * "split run" problem where MS Word fragments a single placeholder across multiple XML runs.
 */
public class DocxMergeService {

  private static final Pattern FIELD_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

  record FieldMatch(int start, int end, String path) {}

  /**
   * Merges a .docx template with the provided context, replacing all {{variable}} placeholders.
   *
   * @param templateStream the .docx template as an input stream
   * @param context the variable context map (supports dot-path nested maps)
   * @return the merged document as bytes
   */
  public byte[] merge(InputStream templateStream, Map<String, Object> context) throws IOException {
    try (XWPFDocument doc = new XWPFDocument(templateStream)) {
      for (XWPFParagraph para : doc.getParagraphs()) {
        mergeParagraph(para, context);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    }
  }

  void mergeParagraph(XWPFParagraph para, Map<String, Object> context) {
    List<XWPFRun> runs = para.getRuns();
    if (runs == null || runs.isEmpty()) return;

    // Step 1: Build concatenated text + offset map
    StringBuilder fullText = new StringBuilder();
    int[] runStartOffsets = new int[runs.size()];
    for (int i = 0; i < runs.size(); i++) {
      runStartOffsets[i] = fullText.length();
      String text = runs.get(i).getText(0);
      if (text != null) fullText.append(text);
    }

    // Step 2: Find all {{...}} matches
    Matcher matcher = FIELD_PATTERN.matcher(fullText);
    List<FieldMatch> matches = new ArrayList<>();
    while (matcher.find()) {
      matches.add(new FieldMatch(matcher.start(), matcher.end(), matcher.group(1)));
    }
    if (matches.isEmpty()) return;

    // Step 3: Process right-to-left to preserve offsets
    Collections.reverse(matches);
    for (FieldMatch match : matches) {
      String resolved = resolveVariable(match.path(), context);
      replaceAcrossRuns(runs, runStartOffsets, match.start(), match.end(), resolved);
    }
  }

  void replaceAcrossRuns(
      List<XWPFRun> runs, int[] runStartOffsets, int matchStart, int matchEnd, String resolved) {
    // Find first and last run indices
    int firstRunIdx = -1;
    int lastRunIdx = -1;

    for (int i = 0; i < runs.size(); i++) {
      String text = runs.get(i).getText(0);
      int runLength = text != null ? text.length() : 0;
      int runEnd = runStartOffsets[i] + runLength;

      if (firstRunIdx == -1 && matchStart < runEnd) {
        firstRunIdx = i;
      }
      if (matchEnd - 1 < runEnd) {
        lastRunIdx = i;
        break;
      }
    }

    if (firstRunIdx == -1 || lastRunIdx == -1) return;

    int localStart = matchStart - runStartOffsets[firstRunIdx];

    if (firstRunIdx == lastRunIdx) {
      // Single-run case
      String originalText = runs.get(firstRunIdx).getText(0);
      int localEnd = matchEnd - runStartOffsets[firstRunIdx];
      runs.get(firstRunIdx)
          .setText(
              originalText.substring(0, localStart) + resolved + originalText.substring(localEnd),
              0);
    } else {
      // Multi-run case
      // First run: keep text before match, append resolved value
      String firstText = runs.get(firstRunIdx).getText(0);
      runs.get(firstRunIdx).setText(firstText.substring(0, localStart) + resolved, 0);

      // Middle runs: clear text
      for (int i = firstRunIdx + 1; i < lastRunIdx; i++) {
        runs.get(i).setText("", 0);
      }

      // Last run: remove matched portion from start
      String lastText = runs.get(lastRunIdx).getText(0);
      int localEnd = matchEnd - runStartOffsets[lastRunIdx];
      runs.get(lastRunIdx).setText(lastText.substring(localEnd), 0);
    }
  }

  String resolveVariable(String path, Map<String, Object> context) {
    String[] parts = path.trim().split("\\.");
    Object current = context;
    for (String part : parts) {
      if (current == null || !(current instanceof Map<?, ?> map)) {
        return "";
      }
      current = map.get(part);
    }
    return current == null ? "" : current.toString();
  }
}
