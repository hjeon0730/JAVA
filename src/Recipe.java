import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 메뉴 하나의 레시피
 * - 재료: "바닐라시럽 20ml"처럼 한 줄에 재료와 양을 함께 적는다
 * - 사이즈: 사이즈마다 재료(양)가 다를 수 있다. 사이즈가 없는 메뉴는 재료 목록 하나만 가진다
 * - 레시피(제조 순서)와 토핑은 사이즈와 상관없이 같다
 * - 토핑: 휘핑크림, 시럽 드리즐, 파우더처럼 컵 위에 올리는 것 (없는 메뉴는 빈 목록)
 * - 사진: 음료 사진 파일 위치 (recipes.txt가 있는 폴더 기준, 예: images/ICE_바닐라라떼.png). 없으면 ""
 */
public class Recipe {
    /** 사이즈 구분이 없는 메뉴의 재료 목록 키 (20oz 하나뿐인 메뉴) */
    public static final String NO_SIZE = "";
    /** 매장 컵 사이즈 (작은 것부터). 바뀌면 여기만 고치면 된다 */
    public static final String[] SIZE_OPTIONS = {"14oz", "20oz", "32oz"};
    /** 기본 사이즈. 대부분의 메뉴는 이 사이즈 하나만 있다 */
    public static final String BASE_SIZE = "20oz";

    private String name;
    private String category;
    private Map<String, List<String>> ingredientsBySize;   // 사이즈 이름 → 재료 목록 (입력 순서 유지)
    private List<String> steps;
    private List<String> toppings;
    private String photo = "";

    public Recipe(String name, String category, Map<String, List<String>> ingredientsBySize,
                  List<String> steps, List<String> toppings) {
        this.name = name;
        this.category = category;
        setIngredientsBySize(ingredientsBySize);
        this.steps = new ArrayList<>(steps);
        this.toppings = new ArrayList<>(toppings);
    }

    /** 사이즈 구분이 없는 메뉴를 만들 때 */
    public static Recipe withoutSize(String name, String category, List<String> ingredients,
                                     List<String> steps, List<String> toppings) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put(NO_SIZE, ingredients);
        return new Recipe(name, category, map, steps, toppings);
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    /** 사진 파일 위치 (없으면 "") */
    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo == null ? "" : photo.trim();
    }

    public boolean hasPhoto() {
        return !photo.isEmpty();
    }

    /** 사이즈별로 재료가 나뉘어 있는지 */
    public boolean hasSizes() {
        return !ingredientsBySize.containsKey(NO_SIZE);
    }

    /** 사이즈 이름 목록 (사이즈가 없는 메뉴는 빈 목록) */
    public List<String> getSizes() {
        return hasSizes() ? new ArrayList<>(ingredientsBySize.keySet()) : new ArrayList<>();
    }

    /** 검색했을 때 먼저 보여줄 사이즈: 20oz가 있으면 20oz, 없으면 가장 작은 사이즈 (사이즈가 없으면 NO_SIZE) */
    public String getDefaultSize() {
        if (!hasSizes()) return NO_SIZE;
        return ingredientsBySize.containsKey(BASE_SIZE) ? BASE_SIZE : getSizes().get(0);
    }

    /** 해당 사이즈의 재료 목록 (사이즈가 없는 메뉴는 NO_SIZE) */
    public List<String> getIngredients(String size) {
        return ingredientsBySize.get(size);
    }

    public Map<String, List<String>> getIngredientsBySize() {
        return ingredientsBySize;
    }

    public void setIngredientsBySize(Map<String, List<String>> ingredientsBySize) {
        this.ingredientsBySize = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : ingredientsBySize.entrySet()) {
            this.ingredientsBySize.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
    }

    public List<String> getSteps() {
        return steps;
    }

    public void setSteps(List<String> steps) {
        this.steps = new ArrayList<>(steps);
    }

    public List<String> getToppings() {
        return toppings;
    }

    public void setToppings(List<String> toppings) {
        this.toppings = new ArrayList<>(toppings);
    }

    /** 컵 위에 토핑이 올라가는 메뉴인지 */
    public boolean hasToppings() {
        return !toppings.isEmpty();
    }
}
