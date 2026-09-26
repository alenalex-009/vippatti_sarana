
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Repairs mojibake in Android string resources.
 *
 * <p>The translated locale string resources had their UTF-8 bytes read back
 * through a legacy 8-bit codepage, so Devanagari / Telugu / Tamil / Bengali
 * text was stored as letter-by-letter Latin-1 (or Windows-1252) sequences
 * rather than native script. This tool reconstructs the original bytes and
 * decodes them as strict UTF-8, restoring the native script.
 *
 * <p>Guarantees:
 <ul>
 *   <li>Nothing is transliterated. A run is rewritten only when re-encoding it to
 *       its original bytes and decoding those bytes as strict UTF-8 yields real
 *       text; otherwise the original characters are kept verbatim.</li>
 *   <li>Only characters that can appear inside a mojibake run (U+0080..U+00FF and
 *       the cp1252 specials) are candidates. Real Unicode - Indic, emoji, CJK -
 *       is never a candidate, so already-correct translations are untouched.</li>
 * </ul>
 */
public final class MojibakeRepair {

  /** cp1252 byte -> Unicode, for the 0x80-0x9F window. */
  private static final int[] CP1252 = {
    0x20AC, 0x0081, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021,
    0x02C6, 0x2030, 0x0160, 0x2039, 0x0152, 0x008D, 0x017D, 0x008F,
    0x0090, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013, 0x2014,
    0x02DC, 0x2122, 0x0161, 0x203A, 0x0153, 0x009D, 0x017E, 0x0178,
  };

  /** True when a character can take part in a mojibake run at all. */
  private static boolean isCandidate(int cp) {
    return candidatesFor(cp).length > 0;
  }

  private static String decodeStrict(byte[] seq) {
    CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      CharBuffer cb = dec.decode(ByteBuffer.wrap(seq));
      return cb.toString();
    } catch (CharacterCodingException e) {
      return null;
    }
  }

  /**
   * Decodes one maximal run of candidate characters, backtracking over byte
   * readings and returning the first fully valid UTF-8 interpretation, or
   * {@code null} when no such interpretation exists.
   */
  private static String decodeRun(int[] cps, int start, int len) {
    StringBuilder out = new StringBuilder();
    boolean[] done = new boolean[] { false };
    walk(cps, start, len, 0, out, done);
    return done[0] ? out.toString() : null;
  }
  /**
   * All byte readings a character could stand for, lowest first. Returns an
   * empty array for characters that cannot appear inside a mojibake run.
   */
  private static int[] candidatesFor(int cp) {
    int[] buf = new int[2];
    int n = 0;
    if (cp >= 0x80 && cp <= 0xFF) {
      buf[n++] = cp;                       // Latin-1 identity
    }
    for (int b = 0; b < CP1252.length; b++) {
      if (CP1252[b] == cp) {               // cp1252 reading of that byte
        int cand = 0x80 + b;
        boolean seen = false;
        for (int i = 0; i < n; i++) {
          seen |= buf[i] == cand;
        }
        if (!seen && n < buf.length) {
          buf[n++] = cand;
        }
      }
    }
    int[] out = new int[n];
    System.arraycopy(buf, 0, out, 0, n);
    java.util.Arrays.sort(out);
    return out;
  }

  private static void walk(int[] cps, int start, int len, int pos,
                          StringBuilder out, boolean[] done) {
    if (done[0]) {
      return;
    }
    if (pos >= len) {
      done[0] = true;
      return;
    }
    for (int lead : candidatesFor(cps[start + pos])) {
      if (done[0]) {
        return;
      }
      final int width;
      if (lead >= 0xC2 && lead <= 0xDF) {
        width = 2;
      } else if (lead >= 0xE0 && lead <= 0xEF) {
        width = 3;
      } else {
        continue;   // not a UTF-8 lead byte
      }
      if (pos + width > len) {
        continue;
      }
      byte[] seq = new byte[width];
      seq[0] = (byte) lead;
      boolean ok = true;
      for (int w = 1; w < width; w++) {
        int[] conts = candidatesFor(cps[start + pos + w]);
        int cont = -1;
        for (int c : conts) {
          if (c >= 0x80 && c <= 0xBF) {
            if (cont >= 0) {
              cont = -1;                 // ambiguous: refuse to guess
              break;
            }
            cont = c;
          }
        }
        if (cont < 0) {
          ok = false;
          break;
        }
        seq[w] = (byte) cont;
      }
      if (!ok) {
        continue;
      }
      String text = decodeStrict(seq);
      if (text == null || text.length() != 1) {
        continue;
      }
      out.append(text);
      walk(cps, start, len, pos + width, out, done);
      if (!done[0]) {
        out.setLength(out.length() - 1);   // backtrack
      }
    }
  }

  /** Repairs one resource file in place. Returns the number of runs rewritten. */
  private static int repairFile(Path file) throws IOException {
    byte[] raw = Files.readAllBytes(file);
    boolean bom = raw.length >= 3
        && (raw[0] & 0xFF) == 0xEF && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF;
    String text;
    try {
      CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT);
      text = dec.decode(ByteBuffer.wrap(raw)).toString();
    } catch (CharacterCodingException e) {
      throw new IOException("invalid UTF-8 in " + file, e);
    }
    if (bom && !text.isEmpty() && text.charAt(0) == '\uFEFF') {
      text = text.substring(1);
    }

    int n = text.length();
    int[] cps = new int[n];
    for (int i = 0; i < n; i++) {
      cps[i] = text.charAt(i);
    }

    StringBuilder out = new StringBuilder(n);
    int fixed = 0;
    int i = 0;
    while (i < n) {
      if (isCandidate(cps[i])) {
        int j = i;
        while (j < n && isCandidate(cps[j])) {
          j++;
        }
        String decoded = decodeRun(cps, i, j - i);
        if (decoded != null) {
          out.append(decoded);
          fixed++;
        } else {
          out.append(text, i, j);   // not decodable: preserve verbatim
        }
        i = j;
      } else {
        out.append(text.charAt(i));
        i++;
      }
    }

    if (fixed > 0 || bom) {
      Files.write(file, out.toString().getBytes(StandardCharsets.UTF_8));
    }
    return fixed;
  }

  public static void main(String[] args) throws IOException {
    Path res = Paths.get(args[0]);
    List<Path> files = new ArrayList<>();
    try (Stream<Path> s = Files.walk(res)) {
      s.filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith(".xml"))
          .filter(p -> {
            Path parent = p.getParent();
            if (parent == null) {
              return false;
            }
            String dir = parent.getFileName().toString();
            return dir.equals("values") || dir.matches("values-[a-z]{2}");
          })
          .forEach(files::add);
    }
    files.sort(Comparator.comparing(Path::toString));
    for (Path p : files) {
      System.out.printf("%-46s repairedRuns=%d%n",
          res.relativize(p).toString().replace('\\', '/'), repairFile(p));
    }
  }

  private MojibakeRepair() {}
}
