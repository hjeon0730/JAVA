import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 앱 창(SwingMain)이 쓰는 레시피 기능 모음.
 * 파일 불러오기·다시 읽기, 등록·수정·삭제와 저장, 관리자 비밀번호 확인을 한곳에서 처리한다.
 * 화면은 입력을 모아 이 클래스에 넘기고, 결과나 오류 문장(ServiceException)을 받아 보여주기만 한다.
 */
public class RecipeService {

    /** 등록·수정·삭제를 거절할 때, 사용자에게 보여줄 이유 문장을 담는 오류 */
    public static class ServiceException extends Exception {
        private static final long serialVersionUID = 1L;

        public ServiceException(String message) {
            super(message);
        }
    }

    /** 사진을 모아 두는 폴더 이름 (recipes.txt 옆) */
    public static final String PHOTO_FOLDER = "images";
    /** 넣을 수 있는 사진 종류 */
    public static final String[] PHOTO_TYPES = {"jpg", "jpeg", "png", "gif"};

    private final RecipeBook book = new RecipeBook();
    private final RecipeFileStore store;
    private final AdminAuth admin;
    private final List<String> warnings = new ArrayList<>();
    private long fileStamp = -1;
    private boolean defaultPasswordCreated = false;

    public RecipeService(Path recipeFile) {
        this.store = new RecipeFileStore(recipeFile);
        Path dir = recipeFile.toAbsolutePath().getParent();
        this.admin = new AdminAuth(dir.resolve("admin.txt"));
    }

    // ------------------------------------------------------------
    // 불러오기 (R4)
    // ------------------------------------------------------------

    /** 레시피와 관리자 비밀번호를 불러온다 */
    public synchronized void load() {
        loadRecipes();
        try {
            defaultPasswordCreated = admin.load();
        } catch (IOException e) {
            warnings.add("관리자 비밀번호 파일을 읽지 못했습니다: " + e.getMessage());
        }
    }

    /** recipes.txt를 (다시) 읽는다. 문제가 있으면 warnings에 남긴다 */
    private void loadRecipes() {
        book.clear();
        warnings.clear();
        try {
            RecipeFileStore.LoadResult result = store.load();
            if (!result.fileExists) {
                warnings.add("레시피 파일을 찾지 못해 빈 목록으로 시작했습니다: " + file());
            }
            for (Recipe r : result.recipes) {
                if (!book.add(r)) {
                    warnings.add("'" + r.getName() + "'이(가) 두 번 적혀 있어 처음 것만 사용합니다.");
                }
            }
            for (int line : result.skippedLines) {
                warnings.add("recipes.txt " + line + "번째 줄부터 시작하는 메뉴는 형식이 맞지 않아 건너뛰었습니다."
                        + " (메뉴명, 카테고리, 재료, 레시피가 모두 있는지 확인하세요."
                        + " 여기서 저장하면 원래 파일은 " + store.backupPath().getFileName() + "로 남겨 둡니다)");
            }
        } catch (IOException e) {
            warnings.add("레시피 파일을 읽지 못했습니다: " + e.getMessage());
        }
        fileStamp = currentStamp();
    }

    /** 메모장 등으로 recipes.txt가 바뀌었으면 다시 읽는다. 다시 읽었으면 true */
    public synchronized boolean reloadIfChanged() {
        if (currentStamp() == fileStamp) {
            return false;
        }
        loadRecipes();
        return true;
    }

    private long currentStamp() {
        try {
            Path p = store.getPath();
            return Files.exists(p) ? Files.getLastModifiedTime(p).toMillis() + Files.size(p) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------
    // 조회 (R1, R1-2, R2, R8)
    // ------------------------------------------------------------

    public synchronized Map<String, List<Recipe>> groupByCategory() {
        return book.groupByCategory();
    }

    public synchronized Recipe find(String name) {
        return book.findExact(name);
    }

    /** 정확히 맞는 메뉴를 먼저, 그다음 부분·초성 검색 결과 */
    public synchronized List<Recipe> search(String keyword) {
        List<Recipe> results = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) return results;
        Recipe exact = book.findExact(keyword);
        if (exact != null) results.add(exact);
        for (Recipe r : book.searchPartial(keyword)) {
            if (r != exact) results.add(r);
        }
        return results;
    }

    public synchronized int size() {
        return book.size();
    }

    public synchronized List<String> warnings() {
        return Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public Path file() {
        return store.getPath().toAbsolutePath();
    }

    public boolean isDefaultPasswordCreated() {
        return defaultPasswordCreated;
    }

    // ------------------------------------------------------------
    // 등록·수정·삭제 (R3, R3-2, R4-2, R5, R7)
    // ------------------------------------------------------------

    /**
     * 신메뉴 등록 (비밀번호 없이). temp: "HOT", "ICE", 그 밖은 구분 없음. 등록된 메뉴명을 돌려준다.
     * photo: 사용자가 고른 사진 파일 (없으면 null). images 폴더로 복사해 둔다
     */
    public synchronized String add(String baseName, String temp, String category, List<String> sizes,
                                   Map<String, String> ingredientText, String steps, String toppings,
                                   Path photo)
            throws ServiceException {
        baseName = baseName == null ? "" : baseName.trim();
        if (baseName.isEmpty()) throw new ServiceException("메뉴명을 입력해 주세요.");
        String name = ("HOT".equals(temp) ? "HOT " : "ICE".equals(temp) ? "ICE " : "") + baseName;
        if (book.findExact(name) != null) {
            throw new ServiceException("'" + name + "'은(는) 이미 등록된 메뉴입니다.");
        }
        Recipe recipe = build(name, category, sizes, ingredientText, steps, toppings);
        if (photo != null) {
            recipe.setPhoto(copyPhoto(photo, name));
        }
        book.add(recipe);
        save();
        return name;
    }

    /**
     * 관리자 비밀번호 확인 후 메뉴 내용 전체를 바꾼다.
     * 사진: newPhoto를 고르면 바꾸고, removePhoto면 빼고, 둘 다 아니면 원래 사진을 그대로 둔다
     */
    public synchronized void update(String name, String password, String category, List<String> sizes,
                                    Map<String, String> ingredientText, String steps, String toppings,
                                    Path newPhoto, boolean removePhoto)
            throws ServiceException {
        checkPassword(password);
        Recipe old = book.findExact(name);
        if (old == null) throw new ServiceException("등록되지 않은 메뉴입니다.");
        Recipe updated = build(old.getName(), category, sizes, ingredientText, steps, toppings);
        if (newPhoto != null) {
            updated.setPhoto(copyPhoto(newPhoto, old.getName()));
        } else if (!removePhoto) {
            updated.setPhoto(old.getPhoto());
        }
        book.replace(old.getName(), updated);
        save();
        if (!old.getPhoto().equals(updated.getPhoto())) {
            deleteIfUnused(old.getPhoto());
        }
    }

    /** 관리자 비밀번호 확인 후 삭제 */
    public synchronized void delete(String name, String password) throws ServiceException {
        checkPassword(password);
        Recipe r = book.findExact(name);
        if (r == null) throw new ServiceException("등록되지 않은 메뉴입니다.");
        book.remove(r.getName());
        save();
        deleteIfUnused(r.getPhoto());
    }

    /** 현재 비밀번호 확인 후 새 비밀번호로 변경 */
    public synchronized void changePassword(String current, String next) throws ServiceException {
        if (!admin.check(current)) throw new ServiceException("현재 비밀번호가 틀렸습니다.");
        if (next == null || next.trim().isEmpty()) throw new ServiceException("새 비밀번호를 입력해 주세요.");
        try {
            admin.change(next.trim());
        } catch (IOException e) {
            throw new ServiceException("비밀번호 저장에 실패했습니다: " + e.getMessage());
        }
    }

    private void checkPassword(String password) throws ServiceException {
        if (!admin.check(password)) throw new ServiceException("관리자 비밀번호가 틀렸습니다.");
    }

    // ------------------------------------------------------------
    // 사진 (R9)
    // ------------------------------------------------------------

    /** 사진이 들어가는 폴더: recipes.txt 옆의 images 폴더 */
    public Path photoFolder() {
        return file().getParent().resolve(PHOTO_FOLDER);
    }

    /** 레시피의 사진 파일 위치 (사진이 없으면 null). recipes.txt가 있는 폴더 기준 */
    public Path photoPath(Recipe r) {
        if (r == null || !r.hasPhoto()) return null;
        try {
            Path p = Paths.get(r.getPhoto());
            return p.isAbsolute() ? p : file().getParent().resolve(p).normalize();
        } catch (java.nio.file.InvalidPathException e) {
            return null;   // 이 컴퓨터에서 쓸 수 없는 파일 이름 → 사진 없음으로 보여 준다
        }
    }

    /**
     * 고른 사진을 images 폴더에 "메뉴명.확장자"로 복사하고, recipes.txt에 적을 위치(images/...)를 돌려준다.
     * 사진 파일이 아니면 거절한다. 원본 파일은 그대로 둔다
     */
    private String copyPhoto(Path source, String menuName) throws ServiceException {
        if (!Files.isRegularFile(source)) throw new ServiceException("사진 파일을 찾을 수 없습니다: " + source);
        String fileName = source.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
        if (!Arrays.asList(PHOTO_TYPES).contains(ext)) {
            throw new ServiceException("사진은 jpg, png, gif 파일만 넣을 수 있습니다.");
        }
        try {
            if (ImageIO.read(source.toFile()) == null) {
                throw new ServiceException("사진 파일을 열 수 없습니다. 다른 사진을 골라 주세요.");
            }
            Path target = photoFolder().resolve(photoFileName(menuName) + "." + ext);
            Files.createDirectories(target.getParent());
            if (!target.toAbsolutePath().normalize().equals(source.toAbsolutePath().normalize())) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return PHOTO_FOLDER + "/" + target.getFileName();
        } catch (IOException e) {
            throw new ServiceException("사진을 복사하지 못했습니다: " + e.getMessage());
        }
    }

    /**
     * 사진 파일 이름: 한글 파일 이름은 압축을 풀거나 다른 컴퓨터로 옮길 때 깨질 수 있어서
     * 영문·숫자만 쓴다. 메뉴명이 영문이면 그대로(띄어쓰기는 _), 한글이 있으면 메뉴명으로 만든 번호를 붙인다.
     * 같은 메뉴는 늘 같은 이름이라 사진을 바꾸면 덮어쓴다. (ICE 바닐라라떼 → menu_1a2b3c4d)
     */
    static String photoFileName(String menuName) {
        String name = menuName.trim().replaceAll("\\s+", "_");
        if (name.matches("[A-Za-z0-9_\\-]+")) {
            return name.toLowerCase();
        }
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(menuName.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return String.format("menu_%08x", crc.getValue());
    }

    /** 바뀌거나 지운 메뉴의 사진을, 다른 메뉴가 쓰지 않고 images 폴더 안에 있으면 지운다 */
    private void deleteIfUnused(String photo) {
        if (photo == null || photo.isEmpty()) return;
        for (Recipe r : book.getAll()) {
            if (r.getPhoto().equals(photo)) return;
        }
        Path p;
        try {
            p = file().getParent().resolve(photo).normalize();
        } catch (java.nio.file.InvalidPathException e) {
            return;
        }
        if (p.startsWith(photoFolder().normalize())) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignore) {
                // 못 지워도 레시피에는 영향이 없다
            }
        }
    }

    /** 변경 직후 파일에 저장한다 (R4-2) */
    private void save() throws ServiceException {
        try {
            store.save(book.getAll());
            fileStamp = currentStamp();     // 직접 저장한 변경은 다시 읽지 않는다
        } catch (IOException e) {
            throw new ServiceException("저장에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 입력값으로 레시피를 만든다 (등록·수정 공통 규칙).
     * sizes는 정해진 사이즈만 받아 작은 것부터 정렬하고, 비어 있으면 기본 20oz.
     * 20oz 하나뿐이면 "재료:" 한 칸(NO_SIZE)으로 둔다.
     */
    public static Recipe build(String name, String category, List<String> sizes,
                               Map<String, String> ingredientText, String steps, String toppings)
            throws ServiceException {
        category = category == null ? "" : category.trim();
        if (category.isEmpty()) throw new ServiceException("카테고리를 입력해 주세요.");

        List<String> picked = new ArrayList<>();
        for (String s : Recipe.SIZE_OPTIONS) {
            if (sizes != null && sizes.contains(s)) picked.add(s);
        }
        if (picked.isEmpty()) picked.add(Recipe.BASE_SIZE);

        Map<String, List<String>> ingredients = new LinkedHashMap<>();
        for (String size : picked) {
            List<String> items = lines(ingredientText == null ? null : ingredientText.get(size));
            if (items.isEmpty()) throw new ServiceException(size + " 재료를 한 줄 이상 입력해 주세요.");
            ingredients.put(size, items);
        }
        if (picked.size() == 1 && picked.get(0).equals(Recipe.BASE_SIZE)) {
            ingredients.put(Recipe.NO_SIZE, ingredients.remove(Recipe.BASE_SIZE));
        }

        List<String> stepList = lines(steps);
        if (stepList.isEmpty()) throw new ServiceException("레시피(제조 순서)를 한 줄 이상 입력해 주세요.");
        return new Recipe(name, category, ingredients, stepList, lines(toppings));
    }

    /** 여러 줄 입력을 줄 목록으로. 빈 줄은 버린다 */
    public static List<String> lines(String text) {
        List<String> list = new ArrayList<>();
        if (text == null) return list;
        for (String line : text.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) list.add(line.trim());
        }
        return list;
    }
}
