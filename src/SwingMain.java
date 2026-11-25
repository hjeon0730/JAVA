import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 레시피 앱 창 (Swing).
 * VS Code에서 F5로 실행하면 "메뉴 레시피 바로 찾기" 프로그램 창 하나가 뜬다.
 * 화면은 이 클래스가 맡고, 검색·등록·수정·삭제·저장 규칙은 RecipeService가 맡는다.
 *
 * 화면 구성
 *  - 위: 제목, 검색창, [+ 레시피 등록], [비밀번호 변경]
 *  - 노란 안내: recipes.txt에서 형식이 틀려 건너뛴 메뉴 등
 *  - 왼쪽: 카테고리별 메뉴 목록 (검색하면 검색 결과)
 *  - 오른쪽: 고른 메뉴의 레시피 카드 (사이즈 버튼, 재료, 레시피, 토핑, 수정/삭제)
 *  - 아래: 레시피 파일 위치
 */
public class SwingMain {

    // ---- 화면 색 (연노랑 계열). 색을 바꿀 때는 여기만 고치면 된다 ----
    static final Color BG = new Color(0xFFF8D9);          // 창 바탕: 연노랑
    static final Color HEADER = new Color(0xFFE88F);      // 위쪽 줄: 조금 진한 노랑
    static final Color PANEL = new Color(0xFFFEF6);       // 목록·카드 바탕: 노란 기가 도는 흰색
    static final Color INK = new Color(0x3B3112);         // 글자
    static final Color MUTED = new Color(0x857650);       // 흐린 글자
    static final Color LINE = new Color(0xF0E2A6);        // 테두리
    static final Color BRAND = new Color(0xFFD54F);       // 고른 사이즈·확인 버튼: 노랑
    static final Color BRAND_LINE = new Color(0xE8B930);  // 노란 버튼 테두리
    static final Color BRAND_SOFT = new Color(0xFFF2BF);  // 마우스를 올린 메뉴, 고른 메뉴
    static final Color HOT = new Color(0xC2492D);
    static final Color ICE = new Color(0x2F6F9F);
    static final Color TOPPING = new Color(0xA86F00);
    static final Color DANGER = new Color(0xB3261E);

    static final String EMPTY_MESSAGE = "왼쪽에서 메뉴를 고르거나, 위에서 메뉴 이름이나 초성을 검색하세요.";

    /** 한글이 잘 보이는 글꼴 이름 (윈도우: 맑은 고딕, 맥: 애플 SD 고딕) */
    static String fontName = "Dialog";

    final RecipeService service;
    final JFrame frame = new JFrame("메뉴 레시피 바로 찾기");
    final JTextField searchField = new JTextField();
    final JPanel warningBox = new JPanel(new BorderLayout());
    final JLabel warningLabel = new JLabel();
    final JPanel listPanel = new JPanel();
    final JPanel detailPanel = new JPanel();
    final JLabel fileLabel = new JLabel();

    /** 사진 고르는 창. 기본은 파일 선택 창(JFileChooser) */
    java.util.function.Function<Component, java.io.File> photoPicker = SwingMain::choosePhotoFile;

    /** 지금 오른쪽에 보이는 메뉴와 사이즈 (null 사이즈 = 전체) */
    Recipe current;
    String currentSize;

    public SwingMain(Path recipeFile) {
        this.service = new RecipeService(recipeFile);
    }

    public static void main(String[] args) {
        Path file = AppPaths.recipeFile(args);
        SwingUtilities.invokeLater(() -> {
            setupLookAndFeel();
            SwingMain app = new SwingMain(file);
            app.start();
        });
    }

    // ------------------------------------------------------------
    // 창 만들기
    // ------------------------------------------------------------

    void start() {
        service.load();
        buildFrame();
        refreshAll();
        if (service.isDefaultPasswordCreated()) {
            toastLater("관리자 비밀번호가 기본값(" + AdminAuth.DEFAULT_PASSWORD + ")으로 설정되었습니다.");
        }
        // 메모장으로 recipes.txt를 고치면 2초 안에 화면에 반영한다
        Timer watcher = new Timer(2000, e -> {
            if (service.reloadIfChanged()) {
                refreshAll();
            }
        });
        watcher.start();
        frame.setVisible(true);
        searchField.requestFocusInWindow();
    }

    void buildFrame() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1180, 800);
        frame.setMinimumSize(new Dimension(820, 560));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(18, 12));
        body.setBackground(BG);
        body.setBorder(BorderFactory.createEmptyBorder(14, 20, 8, 20));

        // 노란 안내 (평소에는 숨김)
        // 바탕이 노랑이라 안내 칸은 주황빛으로 눈에 띄게 한다
        warningBox.setBackground(new Color(0xFFE6CF));
        warningBox.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(new Color(0xF2B47E), 12), BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        warningLabel.setForeground(new Color(0x7A3B00));
        warningLabel.setFont(font(Font.PLAIN, 13));
        warningBox.add(warningLabel, BorderLayout.CENTER);
        warningBox.setVisible(false);
        body.add(warningBox, BorderLayout.NORTH);

        // 왼쪽 메뉴 목록
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(PANEL);
        listPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane listScroll = card(topHolder(listPanel));
        listScroll.setPreferredSize(new Dimension(300, 400));
        body.add(listScroll, BorderLayout.WEST);

        // 오른쪽 레시피 카드
        detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
        detailPanel.setBackground(PANEL);
        detailPanel.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        body.add(card(topHolder(detailPanel)), BorderLayout.CENTER);

        root.add(body, BorderLayout.CENTER);

        fileLabel.setFont(font(Font.PLAIN, 12));
        fileLabel.setForeground(MUTED);
        fileLabel.setBorder(BorderFactory.createEmptyBorder(0, 22, 10, 20));
        root.add(fileLabel, BorderLayout.SOUTH);

        frame.setContentPane(root);
    }

    JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(HEADER);
        header.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));

        JLabel title = new JLabel("메뉴 레시피 바로 찾기");
        title.setForeground(INK);
        title.setFont(font(Font.BOLD, 18));
        header.add(title, BorderLayout.WEST);

        // 둥근 검색창 + 안내 문구
        searchField.setFont(font(Font.PLAIN, 16));
        searchField.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        searchField.setOpaque(false);
        JPanel searchWrap = new RoundPanel(Color.WHITE, 999);
        searchWrap.setLayout(new BorderLayout());
        searchWrap.add(new PlaceholderLayer(searchField, "메뉴 이름이나 초성으로 검색"));
        header.add(searchWrap, BorderLayout.CENTER);

        // 입력이 멈추고 0.2초 뒤에 검색 (글자마다 검색하지 않도록)
        Timer debounce = new Timer(200, e -> runSearch());
        debounce.setRepeats(false);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });
        searchField.addActionListener(e -> { debounce.stop(); runSearch(); });   // Enter
        searchField.registerKeyboardAction(e -> searchField.setText(""),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(headerButton("+ 레시피 등록", e -> openRecipeDialog(null)));
        actions.add(headerButton("비밀번호 변경", e -> openPasswordDialog()));
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    // ------------------------------------------------------------
    // 화면 갱신
    // ------------------------------------------------------------

    /** 파일을 (다시) 읽은 뒤: 안내, 목록, 카드, 파일 위치를 모두 새로 그린다 */
    void refreshAll() {
        List<String> warnings = service.warnings();
        if (warnings.isEmpty()) {
            warningBox.setVisible(false);
        } else {
            StringBuilder sb = new StringBuilder("<html><div style='width:" + Math.max(500, frame.getWidth() - 120)
                    + "px'><b>레시피 파일 안내</b>");
            for (String w : warnings) sb.append("<br>• ").append(esc(w));
            warningLabel.setText(sb.append("</div></html>").toString());
            warningBox.setVisible(true);
        }
        fileLabel.setText("레시피 파일: " + service.file() + "  ·  메모장으로 고치면 자동으로 반영됩니다");

        // 보고 있던 메뉴가 바뀌었거나 지워졌으면 새 내용으로
        if (current != null) {
            Recipe again = service.find(current.getName());
            if (again == null) {
                current = null;
            } else {
                String keep = currentSize;
                current = again;
                currentSize = keep == null || again.getSizes().contains(keep) ? keep : again.getDefaultSize();
                if (!again.hasSizes()) currentSize = null;
            }
        }
        runSearch();
        renderDetail();
    }

    /** 검색창 내용대로 왼쪽 목록을 그린다. 결과가 하나면 바로 연다 */
    void runSearch() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            renderList(null);
            return;
        }
        List<Recipe> results = service.search(keyword);
        renderList(results);
        if (results.size() == 1 && results.get(0) != current) {
            openRecipe(results.get(0));
        }
    }

    void renderList(List<Recipe> results) {
        listPanel.removeAll();
        if (results != null) {
            if (results.isEmpty()) {
                listPanel.add(emptyLabel("\"" + searchField.getText().trim() + "\"은(는) 등록되지 않은 메뉴입니다."));
            } else {
                listPanel.add(groupLabel("검색 결과 " + results.size() + "개"));
                for (Recipe r : results) listPanel.add(new MenuRow(r));
            }
        } else {
            Map<String, List<Recipe>> groups = service.groupByCategory();
            if (groups.isEmpty()) listPanel.add(emptyLabel("등록된 메뉴가 없습니다."));
            for (Map.Entry<String, List<Recipe>> e : groups.entrySet()) {
                listPanel.add(groupLabel(e.getKey() + " · " + e.getValue().size() + "개"));
                for (Recipe r : e.getValue()) listPanel.add(new MenuRow(r));
            }
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    void openRecipe(Recipe r) {
        current = r;
        currentSize = r.hasSizes() ? r.getDefaultSize() : null;
        renderDetail();
        listPanel.repaint();      // 고른 메뉴 강조
    }

    /** 오른쪽 레시피 카드 */
    void renderDetail() {
        detailPanel.removeAll();
        Recipe r = current;
        if (r == null) {
            detailPanel.add(emptyLabel(EMPTY_MESSAGE));
            detailPanel.revalidate();
            detailPanel.repaint();
            return;
        }

        // 제목 + 지금 사이즈
        JPanel titleRow = row(FlowLayout.LEFT, 8);
        JLabel title = new JLabel(r.getName());
        title.setFont(font(Font.BOLD, 24));
        title.setForeground(INK);
        titleRow.add(title);
        if (currentSize != null) titleRow.add(new Tag(currentSize, MUTED, LINE));
        detailPanel.add(titleRow);

        String meta = r.getCategory() + (r.hasSizes() ? " · 사이즈 " + String.join(", ", r.getSizes()) : "");
        JLabel metaLabel = new JLabel(meta);
        metaLabel.setFont(font(Font.PLAIN, 14));
        metaLabel.setForeground(MUTED);
        metaLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 12, 0));
        detailPanel.add(leftAlign(metaLabel));

        // 음료 사진 (있는 메뉴만)
        if (r.hasPhoto()) {
            Path photo = service.photoPath(r);
            java.awt.Image img = loadPhoto(photo, 360, 240);
            JComponent photoBox;
            if (img != null) {
                photoBox = new PhotoView(img);
            } else {
                JLabel missing = new JLabel("사진 파일을 찾을 수 없어요: " + r.getPhoto());
                missing.setFont(font(Font.PLAIN, 13));
                missing.setForeground(MUTED);
                photoBox = missing;
            }
            // BorderLayout의 WEST에 두면 사진 크기 그대로 왼쪽에 붙는다
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
            wrap.add(photoBox, BorderLayout.WEST);
            wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
            wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrap.getPreferredSize().height));
            detailPanel.add(wrap);
        }

        // 사이즈 버튼: 사이즈가 두 개 이상일 때만
        if (r.getSizes().size() > 1) {
            JPanel sizes = row(FlowLayout.LEFT, 6);
            for (String s : r.getSizes()) {
                String label = s + (s.equals(Recipe.BASE_SIZE) ? " (기본)" : "");
                sizes.add(chip(label, s.equals(currentSize), () -> { currentSize = s; renderDetail(); }));
            }
            sizes.add(chip("전체", currentSize == null, () -> { currentSize = null; renderDetail(); }));
            sizes.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
            detailPanel.add(sizes);
        }

        // 재료·레시피·토핑은 HTML로 그린다 (줄바꿈과 목록 기호가 자연스럽다)
        JEditorPane body = new JEditorPane("text/html", recipeHtml(r));
        body.setEditable(false);
        body.setOpaque(false);
        body.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        body.setFont(font(Font.PLAIN, 15));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailPanel.add(body);

        JPanel actions = row(FlowLayout.LEFT, 8);
        actions.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        actions.add(new FlatButton("수정", PANEL, INK, LINE, e -> openRecipeDialog(current)));
        actions.add(new FlatButton("삭제", PANEL, DANGER, new Color(0xE9C3BF), e -> openDeleteDialog()));
        detailPanel.add(actions);

        detailPanel.revalidate();
        detailPanel.repaint();
        SwingUtilities.invokeLater(() -> detailPanel.scrollRectToVisible(new java.awt.Rectangle(0, 0, 1, 1)));
    }

    /** 재료(지금 사이즈) + 레시피 + 토핑 */
    String recipeHtml(Recipe r) {
        StringBuilder h = new StringBuilder("<html><body style='color:#3b3112'>");
        h.append(sectionTitle("재료"));
        List<String> shown = new ArrayList<>();
        if (!r.hasSizes()) shown.add(Recipe.NO_SIZE);
        else if (currentSize == null) shown.addAll(r.getSizes());
        else shown.add(currentSize);
        boolean showLabel = r.hasSizes() && (currentSize == null || r.getSizes().size() == 1);
        for (String size : shown) {
            if (showLabel) h.append("<div style='color:#9a6400; font-weight:bold; margin-top:6px'>").append(esc(size)).append("</div>");
            h.append("<ul style='margin-top:2px; margin-left:20px'>");
            for (String item : r.getIngredients(size)) h.append("<li>").append(esc(item)).append("</li>");
            h.append("</ul>");
        }
        h.append(sectionTitle("레시피")).append("<ol style='margin-top:2px; margin-left:24px'>");
        for (String step : r.getSteps()) h.append("<li style='margin-bottom:4px'>").append(esc(step)).append("</li>");
        h.append("</ol>");
        if (r.hasToppings()) {
            h.append(sectionTitle("컵 위 토핑"))
             .append("<table width='100%' cellpadding='8' cellspacing='0' style='background-color:#fff3cc; border:1px solid #efd88a'><tr><td>");
            for (String t : r.getToppings()) h.append("• ").append(esc(t)).append("<br>");
            h.append("</td></tr></table>");
        }
        return h.append("</body></html>").toString();
    }

    static String sectionTitle(String text) {
        return "<div style='color:#857650; font-size:11pt; font-weight:bold; margin-top:12px'>" + text + "</div>";
    }

    // ------------------------------------------------------------
    // 등록 / 수정 창
    // ------------------------------------------------------------

    /** editing이 null이면 등록, 아니면 수정 (수정은 관리자 비밀번호 필요) */
    void openRecipeDialog(Recipe editing) {
        boolean isEdit = editing != null;
        JDialog dlg = dialog(isEdit ? "레시피 수정 - " + editing.getName() : "레시피 등록");
        JPanel form = formPanel();

        JTextField name = textField();
        JRadioButton hot = radio("HOT"), ice = radio("ICE"), none = radio("구분 없음");
        ButtonGroup tempGroup = new ButtonGroup();
        tempGroup.add(hot); tempGroup.add(ice); tempGroup.add(none);
        none.setSelected(true);

        JComboBox<String> category = new JComboBox<>(service.groupByCategory().keySet().toArray(new String[0]));
        category.setEditable(true);
        category.setFont(font(Font.PLAIN, 15));
        category.setPreferredSize(new Dimension(320, 34));
        category.setSelectedItem(isEdit ? editing.getCategory() : "");

        if (isEdit) {
            JLabel fixed = new JLabel(editing.getName() + "   (메뉴명은 바꿀 수 없어요)");
            fixed.setFont(font(Font.BOLD, 15));
            form.add(fieldLabel("메뉴명"));
            form.add(leftAlign(fixed));
        } else {
            form.add(fieldLabel("메뉴명"));
            form.add(name);
            form.add(fieldLabel("HOT / ICE"));
            JPanel temps = row(FlowLayout.LEFT, 14);
            temps.add(hot); temps.add(ice); temps.add(none);
            form.add(temps);
        }
        form.add(fieldLabel("카테고리 (목록에서 고르거나 새로 입력)"));
        form.add(leftAlign(category));

        // 음료 사진: 고르면 미리보기, 저장할 때 images 폴더로 복사된다 (R9)
        Path[] chosenPhoto = {null};          // 새로 고른 사진
        boolean[] removePhoto = {false};      // 수정에서 사진 빼기
        Path oldPhoto = isEdit ? service.photoPath(editing) : null;
        PhotoView preview = new PhotoView(null);
        preview.setPreferredSize(new Dimension(96, 96));
        JLabel photoName = new JLabel();
        photoName.setFont(font(Font.PLAIN, 13));
        photoName.setForeground(MUTED);
        FlatButton pick = new FlatButton("사진 선택…", PANEL, INK, BRAND_LINE, null);
        FlatButton clear = new FlatButton("사진 빼기", PANEL, MUTED, LINE, null);
        Runnable syncPhoto = () -> {
            Path shown = chosenPhoto[0] != null ? chosenPhoto[0] : removePhoto[0] ? null : oldPhoto;
            preview.setImage(shown == null ? null : loadPhoto(shown, 96, 96));
            photoName.setText(shown == null ? "사진 없음 (선택)" : shown.getFileName().toString());
            clear.setVisible(shown != null);
        };
        pick.addActionListener(e -> {
            java.io.File f = photoPicker.apply(dlg);
            if (f != null) {
                chosenPhoto[0] = f.toPath();
                removePhoto[0] = false;
                syncPhoto.run();
            }
        });
        clear.addActionListener(e -> {
            chosenPhoto[0] = null;
            removePhoto[0] = true;
            syncPhoto.run();
        });
        JPanel photoButtons = new JPanel();
        photoButtons.setLayout(new BoxLayout(photoButtons, BoxLayout.Y_AXIS));
        photoButtons.setOpaque(false);
        photoButtons.add(photoName);
        photoButtons.add(Box.createVerticalStrut(8));
        JPanel pb = row(FlowLayout.LEFT, 6);
        pb.add(pick);
        pb.add(clear);
        photoButtons.add(pb);
        photoName.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel photoRow = row(FlowLayout.LEFT, 14);
        photoRow.add(preview);
        photoRow.add(photoButtons);
        form.add(fieldLabel("음료 사진 (jpg, png, gif · 없어도 됨)"));
        form.add(photoRow);
        syncPhoto.run();

        // 사이즈 체크 → 고른 사이즈마다 재료 칸이 생긴다
        form.add(fieldLabel("사이즈 (안 고르면 20oz 하나)"));
        JPanel sizeChecks = row(FlowLayout.LEFT, 14);
        Map<String, JCheckBox> checks = new LinkedHashMap<>();
        Map<String, JTextArea> areas = new LinkedHashMap<>();
        Map<String, JComponent> areaBlocks = new LinkedHashMap<>();
        JPanel ingredientBox = new JPanel();
        ingredientBox.setLayout(new BoxLayout(ingredientBox, BoxLayout.Y_AXIS));
        ingredientBox.setOpaque(false);
        ingredientBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (String size : Recipe.SIZE_OPTIONS) {
            JCheckBox cb = new JCheckBox(size);
            cb.setFont(font(Font.PLAIN, 15));
            cb.setOpaque(false);
            checks.put(size, cb);
            sizeChecks.add(cb);
            JTextArea area = textArea(3);
            areas.put(size, area);
            JPanel block = new JPanel(new BorderLayout());
            block.setOpaque(false);
            block.setAlignmentX(Component.LEFT_ALIGNMENT);
            block.add(fieldLabel(size + " 재료 (한 줄에 하나, 예: 바닐라시럽 20ml)"), BorderLayout.NORTH);
            block.add(scroll(area), BorderLayout.CENTER);
            areaBlocks.put(size, block);
            ingredientBox.add(block);
        }
        form.add(sizeChecks);
        form.add(ingredientBox);

        JTextArea steps = textArea(4);
        JTextArea toppings = textArea(2);
        form.add(fieldLabel("레시피 (한 줄에 한 단계)"));
        form.add(scroll(steps));
        form.add(fieldLabel("컵 위 토핑 (없으면 비워 두기, 예: 휘핑크림, 초코 드리즐)"));
        form.add(scroll(toppings));

        JPasswordField password = new JPasswordField();
        if (isEdit) {
            form.add(fieldLabel("관리자 비밀번호"));
            styleField(password);
            form.add(password);
        }

        // 수정이면 지금 내용을 채워 둔다
        if (isEdit) {
            if (editing.hasSizes()) {
                for (String s : editing.getSizes()) {
                    checks.get(s).setSelected(true);
                    areas.get(s).setText(String.join("\n", editing.getIngredients(s)));
                }
            } else {
                areas.get(Recipe.BASE_SIZE).setText(String.join("\n", editing.getIngredients(Recipe.NO_SIZE)));
            }
            steps.setText(String.join("\n", editing.getSteps()));
            toppings.setText(String.join("\n", editing.getToppings()));
        }

        Runnable syncAreas = () -> {
            boolean any = false;
            for (JCheckBox cb : checks.values()) any |= cb.isSelected();
            for (String s : Recipe.SIZE_OPTIONS) {
                boolean show = checks.get(s).isSelected() || (!any && s.equals(Recipe.BASE_SIZE));
                areaBlocks.get(s).setVisible(show);
            }
            ingredientBox.revalidate();
            fitToScreen(dlg);
        };
        for (JCheckBox cb : checks.values()) cb.addActionListener(e -> syncAreas.run());

        JLabel error = errorLabel();
        form.add(error);

        FlatButton save = primaryButton(isEdit ? "수정 저장" : "등록", null);
        save.addActionListener(e -> {
            List<String> sizes = new ArrayList<>();
            Map<String, String> texts = new HashMap<>();
            for (String s : Recipe.SIZE_OPTIONS) {
                if (checks.get(s).isSelected()) sizes.add(s);
                texts.put(s, areas.get(s).getText());
            }
            String cat = category.getEditor().getItem() == null ? "" : category.getEditor().getItem().toString();
            try {
                String savedName;
                if (isEdit) {
                    savedName = editing.getName();
                    service.update(savedName, new String(password.getPassword()), cat, sizes, texts,
                            steps.getText(), toppings.getText(), chosenPhoto[0], removePhoto[0]);
                } else {
                    String temp = hot.isSelected() ? "HOT" : ice.isSelected() ? "ICE" : "NONE";
                    savedName = service.add(name.getText(), temp, cat, sizes, texts, steps.getText(),
                            toppings.getText(), chosenPhoto[0]);
                }
                dlg.dispose();
                afterChange(savedName, isEdit ? "수정했습니다." : "등록했습니다.");
            } catch (RecipeService.ServiceException ex) {
                error.setText(ex.getMessage());
                fitToScreen(dlg);
            }
        });

        finishDialog(dlg, form, save, isEdit ? password : name);
        syncAreas.run();
        dlg.setLocationRelativeTo(frame);
        dlg.setVisible(true);
    }

    /** 등록·수정 뒤: 검색창을 비우고 그 메뉴를 연다 */
    void afterChange(String name, String message) {
        current = service.find(name);
        currentSize = current != null && current.hasSizes() ? current.getDefaultSize() : null;
        searchField.setText("");
        refreshAll();
        toast(message);
    }

    // ------------------------------------------------------------
    // 삭제 / 비밀번호 변경 창
    // ------------------------------------------------------------

    void openDeleteDialog() {
        if (current == null) return;
        String name = current.getName();
        JDialog dlg = dialog("레시피 삭제");
        JPanel form = formPanel();
        JLabel ask = new JLabel("'" + name + "' 레시피를 삭제할까요?");
        ask.setFont(font(Font.BOLD, 16));
        form.add(leftAlign(ask));
        form.add(fieldLabel("관리자 비밀번호"));
        JPasswordField pw = new JPasswordField();
        styleField(pw);
        form.add(pw);
        JLabel error = errorLabel();
        form.add(error);

        FlatButton ok = new FlatButton("삭제", DANGER, Color.WHITE, DANGER, null);
        ok.addActionListener(e -> {
            try {
                service.delete(name, new String(pw.getPassword()));
                dlg.dispose();
                current = null;
                refreshAll();
                toast("삭제했습니다.");
            } catch (RecipeService.ServiceException ex) {
                error.setText(ex.getMessage());
                fitToScreen(dlg);
            }
        });
        finishDialog(dlg, form, ok, pw);
        dlg.setLocationRelativeTo(frame);
        dlg.setVisible(true);
    }

    void openPasswordDialog() {
        JDialog dlg = dialog("관리자 비밀번호 변경");
        JPanel form = formPanel();
        JPasswordField now = new JPasswordField(), next = new JPasswordField();
        styleField(now);
        styleField(next);
        form.add(fieldLabel("현재 비밀번호"));
        form.add(now);
        form.add(fieldLabel("새 비밀번호"));
        form.add(next);
        JLabel error = errorLabel();
        form.add(error);
        FlatButton ok = primaryButton("변경", null);
        ok.addActionListener(e -> {
            try {
                service.changePassword(new String(now.getPassword()), new String(next.getPassword()));
                dlg.dispose();
                toast("비밀번호를 변경했습니다.");
            } catch (RecipeService.ServiceException ex) {
                error.setText(ex.getMessage());
                fitToScreen(dlg);
            }
        });
        finishDialog(dlg, form, ok, now);
        dlg.setLocationRelativeTo(frame);
        dlg.setVisible(true);
    }

    // ------------------------------------------------------------
    // 작은 화면 부품
    // ------------------------------------------------------------

    /** 왼쪽 목록의 메뉴 한 줄: 이름 + HOT/ICE, 사이즈, 토핑 표시 */
    class MenuRow extends JPanel {
        final Recipe recipe;
        boolean hover;

        MenuRow(Recipe recipe) {
            super(new BorderLayout(6, 0));
            this.recipe = recipe;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 8));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            String name = recipe.getName();
            String shown = name.startsWith("HOT ") || name.startsWith("ICE ") ? name.substring(4) : name;
            JLabel label = new JLabel(shown);
            label.setFont(font(Font.PLAIN, 15));
            label.setForeground(INK);
            add(label, BorderLayout.CENTER);

            // 왼쪽 작은 사진 (사진이 없으면 연노랑 칸에 컵 모양)
            PhotoView thumb = new PhotoView(recipe.hasPhoto() ? loadPhoto(service.photoPath(recipe), 34, 34) : null);
            thumb.setPreferredSize(new Dimension(34, 34));
            add(thumb, BorderLayout.WEST);

            JPanel tags = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            tags.setOpaque(false);
            if (name.startsWith("HOT ")) tags.add(new Tag("HOT", HOT, new Color(0xEFC7BC)));
            if (name.startsWith("ICE ")) tags.add(new Tag("ICE", ICE, new Color(0xBFD6E8)));
            if (recipe.hasSizes()) tags.add(new Tag(String.join("/", recipe.getSizes()), MUTED, LINE));
            if (recipe.hasToppings()) tags.add(new Tag("토핑", TOPPING, new Color(0xEBCF85)));
            JPanel tagsCenter = new JPanel(new java.awt.GridBagLayout());   // 표시를 세로 가운데에
            tagsCenter.setOpaque(false);
            tagsCenter.add(tags);
            add(tagsCenter, BorderLayout.EAST);

            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mouseClicked(MouseEvent e) { openRecipe(MenuRow.this.recipe); }
            });
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            boolean active = current != null && current.getName().equals(recipe.getName());
            if (hover || active) {
                Graphics2D g2 = smooth(g);
                g2.setColor(BRAND_SOFT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    /** 둥근 테두리의 작은 표시 (HOT, ICE, 14oz/20oz, 토핑) */
    static class Tag extends JLabel {
        final Color border;

        Tag(String text, Color fg, Color border) {
            super(text);
            this.border = border;
            setForeground(fg);
            setFont(font(Font.PLAIN, 11));
            setBorder(BorderFactory.createEmptyBorder(1, 7, 1, 7));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** 운영체제 모양과 상관없이 같은 모양으로 보이는 둥근 버튼 */
    static class FlatButton extends JButton {
        Color fill, line;
        final int radius;

        FlatButton(String text, Color fill, Color fg, Color line, java.awt.event.ActionListener action) {
            this(text, fill, fg, line, 10, action);
        }

        FlatButton(String text, Color fill, Color fg, Color line, int radius, java.awt.event.ActionListener action) {
            super(text);
            this.fill = fill;
            this.line = line;
            this.radius = radius;
            setForeground(fg);
            setFont(font(Font.PLAIN, 14));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (action != null) addActionListener(action);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            Color f = fill;
            if (getModel().isRollover() && f != null) f = mix(f, Color.BLACK, 0.06);
            if (getModel().isPressed() && f != null) f = mix(f, Color.BLACK, 0.12);
            int r = Math.min(radius, getHeight());
            if (f != null) {
                g2.setColor(f);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
            }
            if (line != null) {
                g2.setColor(line);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** 배경이 둥근 패널 (검색창 바탕) */
    static class RoundPanel extends JPanel {
        final Color fill;
        final int radius;

        RoundPanel(Color fill, int radius) {
            this.fill = fill;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(fill);
            int r = Math.min(radius, getHeight());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), r, r);
            g2.dispose();
        }
    }

    /** 둥근 선 테두리 (카드, 안내 상자) */
    static class RoundBorder implements Border {
        final Color color;
        final int radius;

        RoundBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = smooth(g);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
            g2.dispose();
        }

        public Insets getBorderInsets(Component c) { return new Insets(2, 2, 2, 2); }

        public boolean isBorderOpaque() { return false; }
    }

    /** 비어 있을 때 회색 안내 문구를 보여 주는 입력칸 */
    static class PlaceholderLayer extends JPanel {
        PlaceholderLayer(JTextField field, String hint) {
            super(new BorderLayout());
            setOpaque(false);
            JLabel ph = new JLabel(hint);
            ph.setFont(field.getFont());
            ph.setForeground(new Color(0xB0A178));
            ph.setBorder(BorderFactory.createEmptyBorder(0, 17, 0, 0));
            javax.swing.OverlayLayout layout = new javax.swing.OverlayLayout(this);
            setLayout(layout);
            field.setAlignmentX(0f);
            ph.setAlignmentX(0f);
            add(field);
            add(ph);
            field.getDocument().addDocumentListener(new DocumentListener() {
                void sync() { ph.setVisible(field.getText().isEmpty()); }
                public void insertUpdate(DocumentEvent e) { sync(); }
                public void removeUpdate(DocumentEvent e) { sync(); }
                public void changedUpdate(DocumentEvent e) { sync(); }
            });
        }
    }

    /**
     * 둥근 모서리로 사진을 보여 주는 칸. 사진이 없으면 연노랑 칸에 컵 모양을 그린다.
     * 크기를 따로 정하지 않으면 사진 크기에 맞춘다
     */
    static class PhotoView extends JComponent {
        java.awt.Image image;

        PhotoView(java.awt.Image image) {
            this.image = image;
            if (image != null) {
                setPreferredSize(new Dimension(image.getWidth(null), image.getHeight(null)));
            }
        }

        void setImage(java.awt.Image image) {
            this.image = image;
            repaint();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            int arc = Math.min(18, Math.min(w, h) / 3);
            java.awt.geom.RoundRectangle2D box = new java.awt.geom.RoundRectangle2D.Float(0, 0, w - 1, h - 1, arc, arc);
            if (image != null) {
                int iw = image.getWidth(null), ih = image.getHeight(null);
                g2.setClip(box);
                g2.setColor(BRAND_SOFT);
                g2.fill(box);
                g2.drawImage(image, (w - iw) / 2, (h - ih) / 2, null);   // 가운데에
                g2.setClip(null);
            } else {
                g2.setColor(BRAND_SOFT);
                g2.fill(box);
                // 간단한 컵 모양
                g2.setColor(new Color(0xE3C35A));
                g2.setStroke(new BasicStroke(Math.max(1.5f, w / 30f)));
                int cw = w * 2 / 5, ch = h / 2, cx = (w - cw) / 2, cy = h / 4;
                java.awt.Polygon cup = new java.awt.Polygon(
                        new int[] {cx, cx + cw, cx + cw * 5 / 6, cx + cw / 6},
                        new int[] {cy, cy, cy + ch, cy + ch}, 4);
                g2.draw(cup);
                g2.drawLine(cx - w / 20, cy, cx + cw + w / 20, cy);
            }
            g2.setClip(null);
            g2.setColor(LINE);
            g2.draw(box);
            g2.dispose();
        }
    }

    /** 사진을 읽어 maxW × maxH 안에 들어가게 줄인다. 같은 사진은 다시 읽지 않는다. 못 읽으면 null */
    static java.awt.Image loadPhoto(Path file, int maxW, int maxH) {
        if (file == null || !java.nio.file.Files.isRegularFile(file)) return null;
        try {
            long stamp = java.nio.file.Files.getLastModifiedTime(file).toMillis();
            String key = file.toAbsolutePath() + "|" + stamp + "|" + maxW + "x" + maxH;
            java.awt.Image cached = PHOTO_CACHE.get(key);
            if (cached != null) return cached;
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(file.toFile());
            if (src == null) return null;
            double scale = Math.min(1.0, Math.min((double) maxW / src.getWidth(), (double) maxH / src.getHeight()));
            int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            PHOTO_CACHE.put(key, out);
            return out;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    static final Map<String, java.awt.Image> PHOTO_CACHE = new HashMap<>();

    /** 파일 선택 창으로 사진 하나를 고른다. 취소하면 null */
    static java.io.File choosePhotoFile(Component parent) {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle("음료 사진 고르기");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "사진 (jpg, png, gif)", RecipeService.PHOTO_TYPES));
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser.showOpenDialog(parent) == javax.swing.JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile() : null;
    }

    /** 스크롤바를 화살표 없는 연노랑 막대로 바꾼다 (기본 파란 막대가 노란 화면과 어울리지 않아서) */
    static void flatScrollBar(JScrollPane sp) {
        for (javax.swing.JScrollBar bar : new javax.swing.JScrollBar[] {sp.getVerticalScrollBar(), sp.getHorizontalScrollBar()}) {
            bar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
                @Override
                protected void configureScrollBarColors() {
                    thumbColor = new Color(0xEBD27A);
                    trackColor = PANEL;
                }

                @Override
                protected JButton createDecreaseButton(int orientation) { return zeroButton(); }

                @Override
                protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

                @Override
                protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle r) {
                    if (r.isEmpty()) return;
                    Graphics2D g2 = smooth(g);
                    g2.setColor(isDragging ? new Color(0xDDBE55) : thumbColor);
                    g2.fillRoundRect(r.x + 3, r.y + 2, r.width - 6, r.height - 4, 8, 8);
                    g2.dispose();
                }

                @Override
                protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
                    g.setColor(trackColor);
                    g.fillRect(r.x, r.y, r.width, r.height);
                }
            });
            bar.setOpaque(false);
        }
    }

    static JButton zeroButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        return b;
    }

    /** 내용을 위쪽에 붙이고, 가로는 창 너비에 맞춰 줄바꿈하는 스크롤 내용판 */
    static class TopHolder extends JPanel implements javax.swing.Scrollable {
        TopHolder(JComponent content) {
            super(new BorderLayout());
            setBackground(PANEL);
            add(content, BorderLayout.NORTH);
        }

        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }

        public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }

        public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return r.height - 32; }

        public boolean getScrollableTracksViewportWidth() { return true; }

        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static JComponent topHolder(JComponent content) {
        return new TopHolder(content);
    }

    // ---- 부품 만드는 도우미 ----

    static JScrollPane card(JComponent inside) {
        JScrollPane sp = new JScrollPane(inside, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(new RoundBorder(LINE, 14));
        sp.getViewport().setBackground(PANEL);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        flatScrollBar(sp);
        return sp;
    }

    static FlatButton headerButton(String text, java.awt.event.ActionListener action) {
        FlatButton b = new FlatButton(text, PANEL, INK, BRAND_LINE, action);
        return b;
    }

    static FlatButton primaryButton(String text, java.awt.event.ActionListener action) {
        return new FlatButton(text, BRAND, INK, BRAND_LINE, action);
    }

    /** 사이즈 고르는 둥근 버튼. 고른 것은 갈색으로 칠한다 */
    static FlatButton chip(String text, boolean on, Runnable action) {
        return new FlatButton(text, on ? BRAND : PANEL, INK, on ? BRAND_LINE : LINE, 999,
                e -> action.run());
    }

    static JLabel groupLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(font(Font.BOLD, 13));
        l.setForeground(MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(10, 8, 4, 8));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static JComponent emptyLabel(String text) {
        JLabel l = new JLabel("<html>" + esc(text) + "</html>");
        l.setFont(font(Font.PLAIN, 15));
        l.setForeground(MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** 왼쪽부터 가로로 늘어놓는 줄. 부품 사이에만 gap만큼 띄운다 */
    static JPanel row(int align, int gap) {
        JPanel p = new JPanel() {
            @Override
            public Component add(Component c) {
                if (getComponentCount() > 0) super.add(Box.createHorizontalStrut(gap));
                return super.add(c);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    static JComponent leftAlign(JComponent c) {
        JPanel p = row(FlowLayout.LEFT, 0);
        p.add(c);
        return p;
    }

    JDialog dialog(String title) {
        JDialog d = new JDialog(frame, title, true);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return d;
    }

    static JPanel formPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(16, 22, 6, 22));
        return p;
    }

    /** 폼 + [취소][확인] 버튼을 창에 붙인다. Enter는 확인 (여러 줄 칸에서는 줄바꿈) */
    static void finishDialog(JDialog dlg, JPanel form, FlatButton ok, JComponent firstFocus) {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(PANEL);
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 22, 18, 22));
        buttons.add(new FlatButton("취소", PANEL, INK, LINE, e -> dlg.dispose()));
        buttons.add(ok);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PANEL);
        JScrollPane sp = new JScrollPane(form, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        flatScrollBar(sp);
        root.add(sp, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dlg.setContentPane(root);
        dlg.getRootPane().setDefaultButton(ok);
        fitToScreen(dlg);
        SwingUtilities.invokeLater(firstFocus::requestFocusInWindow);
    }

    /** 창 크기를 내용에 맞추되, 화면보다 크면 화면 높이에 맞추고 안쪽을 스크롤한다 */
    static void fitToScreen(JDialog dlg) {
        dlg.pack();
        java.awt.Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int maxH = screen.height - 60;
        if (dlg.getHeight() > maxH) {
            dlg.setSize(dlg.getWidth() + 16, maxH);    // 세로 스크롤바 자리만큼 넓힌다
        }
    }

    static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(font(Font.PLAIN, 13));
        l.setForeground(MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static JLabel errorLabel() {
        JLabel l = new JLabel(" ");
        l.setFont(font(Font.PLAIN, 13));
        l.setForeground(DANGER);
        l.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static JTextField textField() {
        JTextField f = new JTextField();
        styleField(f);
        return f;
    }

    static void styleField(JTextField f) {
        f.setFont(font(Font.PLAIN, 15));
        f.setBorder(BorderFactory.createCompoundBorder(new RoundBorder(LINE, 10),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, f.getPreferredSize().height));
        f.setColumns(32);
    }

    static JTextArea textArea(int rows) {
        JTextArea a = new JTextArea(rows, 36);
        a.setFont(font(Font.PLAIN, 15));
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        // Tab은 다음 칸으로 이동
        a.setFocusTraversalKeys(java.awt.KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        a.setFocusTraversalKeys(java.awt.KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        return a;
    }

    static JScrollPane scroll(JTextArea area) {
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(new RoundBorder(LINE, 10));
        flatScrollBar(sp);
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        return sp;
    }

    static JRadioButton radio(String text) {
        JRadioButton r = new JRadioButton(text);
        r.setFont(font(Font.PLAIN, 15));
        r.setOpaque(false);
        return r;
    }

    /** 화면 아래에 잠깐 뜨는 알림 (토스트) */
    void toast(String message) {
        JDialog t = new JDialog(frame);
        t.setUndecorated(true);
        t.setFocusableWindowState(false);
        JLabel l = new JLabel(message, SwingConstants.CENTER);
        l.setFont(font(Font.PLAIN, 14));
        l.setForeground(Color.WHITE);
        l.setOpaque(true);
        l.setBackground(INK);
        l.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
        t.setContentPane(l);
        t.pack();
        t.setLocation(frame.getX() + (frame.getWidth() - t.getWidth()) / 2,
                frame.getY() + frame.getHeight() - t.getHeight() - 40);
        t.setVisible(true);
        Timer close = new Timer(1800, e -> t.dispose());
        close.setRepeats(false);
        close.start();
    }

    void toastLater(String message) {
        Timer later = new Timer(600, e -> toast(message));
        later.setRepeats(false);
        later.start();
    }

    // ---- 공통 ----

    static Font font(int style, int size) {
        return new Font(fontName, style, size);
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g2;
    }

    static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t),
                (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 한글 글꼴을 고르고, 기본 창 모양을 정한다 */
    static void setupLookAndFeel() {
        List<String> installed = Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String candidate : new String[] {"Malgun Gothic", "맑은 고딕", "Apple SD Gothic Neo",
                "AppleGothic", "Noto Sans CJK KR", "Noto Sans KR", "NanumGothic", "나눔고딕"}) {
            if (installed.contains(candidate)) {
                fontName = candidate;
                break;
            }
        }
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignore) {
            // 기본 모양 그대로 사용
        }
        UIManager.put("swing.boldMetal", Boolean.FALSE);
        Font base = font(Font.PLAIN, 14);
        for (String key : new String[] {"Label.font", "Button.font", "TextField.font", "TextArea.font",
                "PasswordField.font", "ComboBox.font", "CheckBox.font", "RadioButton.font",
                "OptionPane.messageFont", "OptionPane.buttonFont", "ToolTip.font", "List.font"}) {
            UIManager.put(key, base);
        }
        UIManager.put("Panel.background", PANEL);
        UIManager.put("CheckBox.background", PANEL);
        UIManager.put("RadioButton.background", PANEL);
        UIManager.put("ComboBox.background", PANEL);
        UIManager.put("ComboBox.selectionBackground", BRAND_SOFT);
        UIManager.put("ComboBox.selectionForeground", INK);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("TextField.caretForeground", INK);
    }
}
