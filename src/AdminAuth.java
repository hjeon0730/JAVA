import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 관리자 비밀번호 관리 (레시피 수정·삭제 전에 확인)
 * 비밀번호는 admin.txt에 저장한다. 파일이 없으면 기본 비밀번호 1234로 만든다.
 */
public class AdminAuth {
    public static final String DEFAULT_PASSWORD = "1234";

    private final Path path;
    private String password;

    public AdminAuth(Path path) {
        this.path = path;
    }

    /** 비밀번호 파일을 읽는다. 없으면 기본값으로 만들고 true를 돌려준다 */
    public boolean load() throws IOException {
        if (Files.exists(path)) {
            String saved = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
            if (!saved.isEmpty()) {
                password = saved;
                return false;
            }
        }
        password = DEFAULT_PASSWORD;
        save();
        return true;
    }

    public boolean check(String input) {
        return input != null && input.equals(password);
    }

    public void change(String newPassword) throws IOException {
        password = newPassword;
        save();
    }

    private void save() throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, password.getBytes(StandardCharsets.UTF_8));
    }
}
