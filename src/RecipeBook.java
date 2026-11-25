import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 등록된 레시피 전체를 관리한다.
 * 메뉴명을 비교용 키로 바꿔 저장해서, 띄어쓰기가 달라도 같은 메뉴로 본다.
 * (예: "ICE 바닐라 라떼" = "ice바닐라라떼" = "아이스바닐라라떼")
 * 검색어에 "ㅂㄴㄹ"처럼 자음이 들어 있으면 초성으로 찾는다.
 */
public class RecipeBook {
    private final Map<String, Recipe> recipes = new LinkedHashMap<>();

    /**
     * 비교용 키: 공백을 모두 지우고 영문은 소문자로 바꾼다.
     * "아이스"는 ice, "핫"은 hot으로 바꿔 한글로 입력해도 찾을 수 있게 한다.
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String key = text.replaceAll("\\s+", "").toLowerCase();
        return key.replace("아이스", "ice").replace("핫", "hot");
    }

    /** 새 메뉴 추가. 띄어쓰기만 다른 같은 이름이 있으면 false */
    public boolean add(Recipe recipe) {
        String key = normalize(recipe.getName());
        if (recipes.containsKey(key)) {
            return false;
        }
        recipes.put(key, recipe);
        return true;
    }

    /** 메뉴명이 일치하는 레시피(띄어쓰기 무시), 없으면 null (R1) */
    public Recipe findExact(String name) {
        return recipes.get(normalize(name));
    }

    /**
     * 메뉴명에 검색어가 포함된 레시피 목록, 띄어쓰기 무시 (R1-2)
     * 검색어에 자음(ㄱ~ㅎ)이 들어 있으면 초성끼리 비교한다 (R8)
     */
    public List<Recipe> searchPartial(String keyword) {
        String key = normalize(keyword);
        List<Recipe> result = new ArrayList<>();
        if (key.isEmpty()) {
            return result;
        }
        if (!hasConsonantJamo(key)) {
            for (Map.Entry<String, Recipe> e : recipes.entrySet()) {
                if (e.getKey().contains(key)) {
                    result.add(e.getValue());
                }
            }
            return result;
        }

        // 초성 비교: "아이스→ice, 핫→hot"으로 바꾼 이름과 원래 이름 둘 다 확인한다
        // (그래야 "ICE ㅂㄴㄹ"도, 핫초코의 "ㅎㅊㅋ"도 찾을 수 있다)
        String target = toChosung(key);
        String rawTarget = toChosung(compact(keyword));
        for (Map.Entry<String, Recipe> e : recipes.entrySet()) {
            String name = toChosung(e.getKey());
            String rawName = toChosung(compact(e.getValue().getName()));
            if (name.contains(target) || rawName.contains(rawTarget)) {
                result.add(e.getValue());
            }
        }
        return result;
    }

    // ------------------------------------------------------------
    // 초성 검색 (R8)
    // ------------------------------------------------------------

    /** 한글 음절의 초성 19개 (유니코드 순서) */
    private static final char[] CHOSUNG = {
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    /** 공백을 지우고 영문을 소문자로만 바꾼 값 (아이스/핫 변환 없음) */
    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase();
    }

    /** 검색어에 자음(ㄱ~ㅎ)이 하나라도 있는지 */
    private static boolean hasConsonantJamo(String text) {
        for (char c : text.toCharArray()) {
            if (c >= 'ㄱ' && c <= 'ㅎ') {
                return true;
            }
        }
        return false;
    }

    /**
     * 한글 음절을 초성으로 바꾼다. 된소리는 예사소리로 바꿔
     * "ㅂㄴㄹㄹㄸ"와 "ㅂㄴㄹㄹㄷ" 둘 다 바닐라라떼를 찾게 한다. 한글이 아닌 글자는 그대로 둔다.
     */
    static String toChosung(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 0xAC00 && c <= 0xD7A3) {
                sb.append(plain(CHOSUNG[(c - 0xAC00) / (21 * 28)]));
            } else {
                sb.append(plain(c));
            }
        }
        return sb.toString();
    }

    private static char plain(char c) {
        switch (c) {
            case 'ㄲ': return 'ㄱ';
            case 'ㄸ': return 'ㄷ';
            case 'ㅃ': return 'ㅂ';
            case 'ㅆ': return 'ㅅ';
            case 'ㅉ': return 'ㅈ';
            default: return c;
        }
    }

    /**
     * 같은 이름의 메뉴 내용을 새 레시피로 바꾼다 (앱 창의 수정). 없는 메뉴면 false.
     * 메뉴를 바꾸는 일을 레시피 관리 한곳에서 하도록 둔다.
     */
    public boolean replace(String name, Recipe updated) {
        String key = normalize(name);
        if (!recipes.containsKey(key) || !key.equals(normalize(updated.getName()))) {
            return false;
        }
        recipes.put(key, updated);   // LinkedHashMap은 기존 키의 순서를 유지한다
        return true;
    }

    /** 메뉴 삭제. 없는 메뉴면 false */
    public boolean remove(String name) {
        return recipes.remove(normalize(name)) != null;
    }

    /** 카테고리별로 묶은 메뉴 목록 (R2), 등록 순서 유지 */
    public Map<String, List<Recipe>> groupByCategory() {
        Map<String, List<Recipe>> groups = new LinkedHashMap<>();
        for (Recipe r : recipes.values()) {
            groups.computeIfAbsent(r.getCategory(), k -> new ArrayList<>()).add(r);
        }
        return groups;
    }

    /** 목록을 비운다 (파일을 다시 읽을 때) */
    public void clear() {
        recipes.clear();
    }

    public Collection<Recipe> getAll() {
        return recipes.values();
    }

    public int size() {
        return recipes.size();
    }

    public boolean isEmpty() {
        return recipes.isEmpty();
    }
}
