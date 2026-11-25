import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 레시피 파일 저장과 불러오기 (R4)
 *
 * 파일 형식 (UTF-8, 메뉴마다 메뉴 이름 → 카테고리 → (사진) → 재료 → 레시피 → 토핑 순서):
 *
 *   메뉴: ICE 바닐라라떼
 *   카테고리: 커피
 *   사진: images/ICE_바닐라라떼.png      ← 사진이 있는 메뉴만
 *   재료(14oz):
 *   - 에스프레소 2샷
 *   - 바닐라시럽 20ml
 *   재료(20oz):
 *   - 에스프레소 3샷
 *   - 바닐라시럽 30ml
 *   레시피:
 *   1. 컵에 바닐라시럽을 넣는다
 *   2. 얼음을 채우고 우유를 붓는다
 *   토핑: 없음
 *
 * 사이즈가 없는 메뉴는 "재료:" 한 칸만, 사이즈가 있는 메뉴는 "재료(사이즈):" 칸을 사이즈마다 적는다.
 * 재료와 토핑은 "- "로, 레시피는 "1. "처럼 번호로 시작한다.
 * '#'으로 시작하는 줄과 빈 줄은 무시한다.
 */
public class RecipeFileStore {
    private static final Pattern STEP_LINE = Pattern.compile("^\\d+\\.\\s*(.*)$");
    private static final Pattern ANY_HEADER =
            Pattern.compile("^(메뉴|카테고리|사진|레시피|토핑|재료\\s*(?:\\([^)]*\\))?)\\s*[:：]\\s*(.*)$");
    private static final Pattern INGREDIENT_HEADER = Pattern.compile("^재료(\\(.+\\))?:.*$");
    private static final Pattern SIZE_HEADER = Pattern.compile("^재료\\((.+?)\\):.*$");
    private static final String NO_TOPPING = "없음";

    private final Path path;
    /** 마지막으로 읽을 때 건너뛴 메뉴가 있었는지. 있으면 저장 전에 원래 파일을 백업한다 */
    private boolean hadSkipped = false;

    public RecipeFileStore(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    /** 불러오기 결과: 레시피 목록 + 건너뛴 메뉴의 줄 번호 + 파일 존재 여부 */
    public static class LoadResult {
        public final List<Recipe> recipes = new ArrayList<>();
        public final List<Integer> skippedLines = new ArrayList<>();
        public boolean fileExists = true;
        public boolean ansiFile = false;   // 메모장 ANSI(MS949)로 저장된 파일을 읽었는지
    }

    /** 읽는 중인 메뉴 하나 */
    private static class Block {
        int startLine;
        String name;
        String category;
        String photo = "";
        Map<String, List<String>> ingredients = new LinkedHashMap<>();
        String currentSize = null;
        List<String> steps = new ArrayList<>();
        List<String> toppings = new ArrayList<>();
        boolean broken = false;
        String section = "";   // 지금 읽는 칸: 재료 / 레시피 / 토핑
    }

    public LoadResult load() throws IOException {
        LoadResult result = new LoadResult();
        if (!Files.exists(path)) {
            result.fileExists = false;   // T13: 파일이 없으면 빈 목록으로 시작
            return result;
        }

        List<String> lines = readLines(result);
        Block block = null;
        for (int i = 0; i < lines.size(); i++) {
            int lineNo = i + 1;
            String line = lines.get(i).trim();
            if (i == 0 && line.startsWith("\uFEFF")) {   // 메모장 BOM 제거
                line = line.substring(1).trim();
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            line = normalizeHeader(line);   // "메뉴 :", "재료 (20oz) ：" 같은 표기를 맞춘다

            if (line.startsWith("메뉴:")) {
                finish(block, result);
                block = new Block();
                block.startLine = lineNo;
                block.name = valueOf(line);
            } else if (block == null) {
                result.skippedLines.add(lineNo);    // 메뉴: 줄 없이 시작한 내용
            } else if (line.startsWith("카테고리:")) {
                block.category = valueOf(line);
            } else if (line.startsWith("사진:")) {
                block.photo = valueOf(line).replace('\\', '/');   // 메모장에 "images\a.png"로 적어도 됨
            } else if (INGREDIENT_HEADER.matcher(line).matches()) {
                String size = parseSizeHeader(line);
                if (size == null || block.ingredients.containsKey(size)) {
                    block.broken = true;          // 알 수 없는 형식이거나 같은 사이즈가 두 번
                } else {
                    block.section = "재료";
                    block.currentSize = size;
                    block.ingredients.put(size, new ArrayList<>());
                    String v = valueOf(line);     // "재료: 우유 200ml"처럼 같은 줄에 적은 경우
                    if (!v.isEmpty()) block.ingredients.get(size).add(v);
                }
            } else if (line.startsWith("레시피:")) {
                block.section = "레시피";
                String v = valueOf(line);
                if (!v.isEmpty()) block.steps.add(v);
            } else if (line.startsWith("토핑:")) {
                block.section = "토핑";
                String v = valueOf(line);
                if (!v.isEmpty() && !v.equals(NO_TOPPING)) {
                    block.toppings.add(v);
                }
            } else if (!addItem(block, line)) {
                block.broken = true;   // 어느 칸에 속하는지 알 수 없는 줄
            }
        }
        finish(block, result);
        hadSkipped = !result.skippedLines.isEmpty();
        return result;
    }

    /**
     * 칸(재료/레시피/토핑) 안의 한 줄을 넣는다. "- ", "* ", "• "나 "1. "로 시작하지 않아도
     * 메모장으로 적은 그대로 받아 준다. 칸이 정해지지 않았으면 false
     */
    private static boolean addItem(Block block, String line) {
        String item = line;
        if (item.startsWith("-") || item.startsWith("*") || item.startsWith("•")) {
            item = item.substring(1).trim();
        } else {
            Matcher m = STEP_LINE.matcher(item);
            if (m.matches()) item = m.group(1).trim();
        }
        if (item.isEmpty()) return true;
        switch (block.section) {
            case "재료": block.ingredients.get(block.currentSize).add(item); return true;
            case "토핑": if (!item.equals(NO_TOPPING)) block.toppings.add(item); return true;
            case "레시피": block.steps.add(item); return true;
            default: return false;
        }
    }

    /** 건너뛴 메뉴가 있을 때 원래 파일을 남겨 두는 곳 */
    public Path backupPath() {
        String name = path.getFileName().toString().replaceFirst("\\.txt$", "");
        return path.resolveSibling(name + "_backup.txt");
    }

    /** "메뉴 : 이름", "재료 (20oz) ：" → "메뉴: 이름", "재료(20oz):" */
    private static String normalizeHeader(String line) {
        Matcher m = ANY_HEADER.matcher(line);
        if (!m.matches()) return line;
        String key = m.group(1).replaceAll("\\s+", "");
        return key + ":" + (m.group(2).isEmpty() ? "" : " " + m.group(2).trim());
    }

    /**
     * UTF-8로 읽고, 메모장에서 "ANSI"로 저장해 UTF-8이 아니면 한국어 Windows 인코딩(MS949)으로 다시 읽는다.
     * 다음에 저장할 때는 UTF-8로 저장된다.
     */
    private List<String> readLines(LoadResult result) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, Charset.forName("MS949"));
            result.ansiFile = true;
        }
        return Arrays.asList(text.split("\\r?\\n", -1));
    }

    /** "재료:" → 사이즈 없음(""), "재료(20oz):" → "20oz", 그 밖에는 null */
    private static String parseSizeHeader(String line) {
        if (line.startsWith("재료:")) {
            return Recipe.NO_SIZE;
        }
        Matcher m = SIZE_HEADER.matcher(line);
        if (!m.matches()) return null;
        // "20온즈", "20 OZ"처럼 적어도 "20oz"로 맞춘다
        String size = m.group(1).replaceAll("\\s+", "").toLowerCase().replace("온즈", "oz");
        return size.isEmpty() ? null : size;
    }

    private static String valueOf(String line) {
        return line.substring(line.indexOf(':') + 1).trim();
    }

    /** 다 읽은 메뉴를 검사해서 목록에 넣거나, 형식이 틀리면 건너뛴다 (T14) */
    private void finish(Block b, LoadResult result) {
        if (b == null) {
            return;
        }
        boolean ok = !b.broken
                && b.name != null && !b.name.isEmpty()
                && b.category != null && !b.category.isEmpty()
                && !b.ingredients.isEmpty() && !b.steps.isEmpty();
        // 사이즈마다 재료가 하나 이상 있어야 하고, "재료:"와 "재료(사이즈):"를 섞어 쓰면 안 된다
        if (ok) {
            for (List<String> list : b.ingredients.values()) {
                if (list.isEmpty()) ok = false;
            }
            if (b.ingredients.containsKey(Recipe.NO_SIZE) && b.ingredients.size() > 1) ok = false;
        }
        if (ok) {
            Recipe r = new Recipe(b.name, b.category, b.ingredients, b.steps, b.toppings);
            r.setPhoto(b.photo.equals(NO_TOPPING) ? "" : b.photo);   // "사진: 없음"도 받아 준다
            result.recipes.add(r);
        } else {
            result.skippedLines.add(b.startLine);
        }
    }

    /** 전체 레시피를 파일에 저장 (등록·수정·삭제할 때마다 호출) */
    public void save(Collection<Recipe> recipes) throws IOException {
        // 형식이 틀려 건너뛴 메뉴는 목록에 없어서, 그대로 저장하면 파일에서 사라진다.
        // 고칠 수 있도록 원래 파일을 recipes_backup.txt로 한 번 남겨 둔다.
        if (hadSkipped && Files.exists(path)) {
            Files.copy(path, backupPath(), StandardCopyOption.REPLACE_EXISTING);
            hadSkipped = false;
        }
        List<String> lines = new ArrayList<>();
        lines.add("# 메뉴마다 메뉴 → 카테고리 → (사진) → 재료 → 레시피 → 토핑 순서");
        lines.add("# 재료·토핑은 \"- \"로, 레시피는 \"1. \"처럼 번호로 시작. 사이즈별 재료는 \"재료(20oz):\"처럼 적음");
        for (Recipe r : recipes) {
            lines.add("");
            lines.add("메뉴: " + r.getName());
            lines.add("카테고리: " + r.getCategory());
            if (r.hasPhoto()) {
                lines.add("사진: " + r.getPhoto());
            }
            for (Map.Entry<String, List<String>> e : r.getIngredientsBySize().entrySet()) {
                lines.add(e.getKey().isEmpty() ? "재료:" : "재료(" + e.getKey() + "):");
                for (String ing : e.getValue()) {
                    lines.add("- " + ing);
                }
            }
            lines.add("레시피:");
            List<String> steps = r.getSteps();
            for (int i = 0; i < steps.size(); i++) {
                lines.add((i + 1) + ". " + steps.get(i));
            }
            if (r.hasToppings()) {
                lines.add("토핑:");
                for (String t : r.getToppings()) {
                    lines.add("- " + t);
                }
            } else {
                lines.add("토핑: " + NO_TOPPING);
            }
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }
}
