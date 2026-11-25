import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 프로젝트 폴더(src 폴더와 recipes.txt가 있는 곳)를 찾는다.
 * VS Code에서 recipe-finder의 상위 폴더를 열고 실행해도 recipes.txt를 찾을 수 있게 한다.
 */
public class AppPaths {
    private AppPaths() {
    }

    /** 지금 폴더, 그 안의 recipe-finder 폴더, 위쪽 폴더 순서로 src/SwingMain.java가 있는 곳을 찾는다 */
    public static Path projectRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path[] candidates = {cwd, cwd.resolve("recipe-finder"), cwd.getParent()};
        for (Path dir : candidates) {
            if (dir != null && Files.exists(dir.resolve("src").resolve("SwingMain.java"))) {
                return dir;
            }
        }
        return cwd;   // 못 찾으면 지금 폴더
    }

    /** 실행할 때 파일을 따로 주지 않았으면 프로젝트 폴더의 recipes.txt */
    public static Path recipeFile(String[] args) {
        return args.length > 0 ? Paths.get(args[0]) : projectRoot().resolve("recipes.txt");
    }
}
