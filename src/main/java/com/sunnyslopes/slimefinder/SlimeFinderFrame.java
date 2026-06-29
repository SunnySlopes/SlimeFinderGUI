package com.sunnyslopes.slimefinder;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.Locale;
import java.util.ResourceBundle;
import java.text.MessageFormat;

public class SlimeFinderFrame extends JFrame {
    // ResourceBundle for internationalization
    private ResourceBundle messages;
    // 默认值 - 单种子搜索（块坐标）
    private static final int DEFAULT_MIN_X = -65536;
    private static final int DEFAULT_MAX_X = 65535;
    private static final int DEFAULT_MIN_Z = -65536;
    private static final int DEFAULT_MAX_Z = 65535;
    private static final int BOUNDARY_MIN_X = -30_000_000;
    private static final int BOUNDARY_MAX_X = 30_000_000;
    private static final int BOUNDARY_MIN_Z = -30_000_000;
    private static final int BOUNDARY_MAX_Z = 30_000_000;
    // 默认值 - 从种子列表搜索（块坐标）
    private static final int DEFAULT_LIST_MIN_X = -4096;
    private static final int DEFAULT_LIST_MAX_X = 4095;
    private static final int DEFAULT_LIST_MIN_Z = -4096;
    private static final int DEFAULT_LIST_MAX_Z = 4095;

    private static final double SLIME_AREA_FULL = Math.PI * (128 * 128 - 24 * 24);
    /** 史莱姆面积：默认 20%，上限 30%，下限 10% */
    private static final int MIN_SLIME_AREA_UI = SlimeSearchRunner.MIN_MIN_SLIME_AREA;
    private static final int DEFAULT_SLIME_AREA_UI = SlimeSearchRunner.DEFAULT_MIN_SLIME_AREA;
    private static final int MAX_SLIME_AREA_UI = SlimeSearchRunner.MAX_MIN_SLIME_AREA;
    private static final double MIN_SLIME_RATIO_UI = SlimeSearchRunner.MIN_MIN_RATIO;
    private static final double DEFAULT_SLIME_RATIO_UI = SlimeSearchRunner.DEFAULT_MIN_RATIO;
    private static final double MAX_SLIME_RATIO_UI = SlimeSearchRunner.MAX_MIN_RATIO;

    private static final String[] VERSION_OPTIONS = {"1.18", "1.19~26.1", "26.2+"};

    // 单种子搜索相关组件
    private JLabel searchSeedLabel;
    private JLabel searchThreadCountLabel;
    private JLabel searchMinSlimeAreaLabel;
    private JLabel searchMinSlimeRatioLabel;
    private JLabel searchMinXLabel;
    private JLabel searchMaxXLabel;
    private JLabel searchMinZLabel;
    private JLabel searchMaxZLabel;
    private JLabel searchLanguageLabel;
    private JTextField searchSeedField;
    private JTextField searchThreadCountField;
    private JTextField searchMinSlimeAreaField;
    private JTextField searchMinSlimeRatioField;
    private JTextField minXField;
    private JTextField maxXField;
    private JTextField minZField;
    private JTextField maxZField;
    private JComboBox<String> searchVersionComboBox;
    private JLabel searchMcVersionLabel;
    private JCheckBox searchBiomeConversionCheckBox;
    private boolean searchSlimeParamUpdating = false;
    private JButton searchStartButton;
    private JButton searchPauseButton;
    private JButton searchStopButton;
    private JButton searchResetButton;
    private JButton searchResetWorldButton;
    private JButton searchExportButton;
    private JButton searchSortButton;
    private JProgressBar searchProgressBar;
    private JLabel searchElapsedTimeLabel;
    private JLabel searchRemainingTimeLabel;
    private JLabel searchCreditLabel;
    private JPanel searchRightPanel;
    private JTextArea searchResultArea;
    private JLabel listSearchCreditLabel;
    private JPanel listSearchRightPanel;
    private SlimeSearchRunner slimeRunner;
    private volatile boolean isSearchRunning = false;
    private volatile boolean isSearchPaused = false;
    private long lastSearchSeed = 0;
    private int lastSearchMinX = 0;
    private int lastSearchMaxX = 0;
    private int lastSearchMinZ = 0;
    private int lastSearchMaxZ = 0;
    private int lastSearchMinArea = 0;
    private int lastSearchThreadCount = 0;
    /**
     * Monotonic token: increment before clearing single-seed results, again on stop/done so any
     * invokeLater from a previous run sees token mismatch and does not append (fixes stale lines after new search).
     */
    private final AtomicLong singleSearchResultToken = new AtomicLong(0);
    private final AtomicLong listSearchResultToken = new AtomicLong(0);

    // 从种子列表搜索相关组件
    private JButton listSearchSeedFileButton;
    private JLabel listSearchSeedFileLabel;
    private JLabel listSearchSeedFileTitleLabel;
    private JLabel listSearchThreadCountLabel;
    private JLabel listSearchMinSlimeAreaLabel;
    private JLabel listSearchMinSlimeRatioLabel;
    private JLabel listSearchMinXLabel;
    private JLabel listSearchMaxXLabel;
    private JLabel listSearchMinZLabel;
    private JLabel listSearchMaxZLabel;
    private File selectedSeedFile;
    private JTextField listSearchThreadCountField;
    private JTextField listMinSlimeAreaField;
    private JTextField listMinSlimeRatioField;
    private JTextField listMinXField;
    private JTextField listMaxXField;
    private JTextField listMinZField;
    private JTextField listMaxZField;
    private boolean listSlimeParamUpdating = false;
    private JComboBox<String> listVersionComboBox;
    private JLabel listMcVersionLabel;
    private JCheckBox listBiomeConversionCheckBox;
    private JComboBox<String> languageComboBox;
    private JButton listSearchStartButton;
    private JButton listSearchPauseButton;
    private JButton listSearchStopButton;
    private JButton listSearchResetButton;
    private JButton listSearchExportButton;
    private JButton listSearchExportSeedListButton;
    private JButton listSortByAreaButton;
    private JButton listSortByDistanceButton;
    private JProgressBar listSearchProgressBar;
    private JLabel listSearchElapsedTimeLabel;
    private JLabel listSearchRemainingTimeLabel;
    private JLabel listSearchCurrentSeedProgressLabel;
    private JTextArea listSearchResultArea;
    private SlimeSearchRunner listSlimeRunner;
    private volatile boolean isListSearchRunning = false;
    private volatile boolean isListSearchPaused = false;
    private int lastListSearchMinX = 0;
    private int lastListSearchMaxX = 0;
    private int lastListSearchMinZ = 0;
    private int lastListSearchMaxZ = 0;
    private int lastListSearchMinArea = 0;
    private int lastListSearchThreadCount = 0;
    // 存储每个种子的结果
    private final Map<Long, List<String>> seedResults = new HashMap<>();

    // 加载的字体
    private Font loadedFont = null;
    // 当前语言Locale
    private Locale currentLocale;

    public SlimeFinderFrame() {
        // 初始化ResourceBundle，根据系统语言选择
        Locale systemLocale = Locale.getDefault();
        // 如果系统语言是中文（zh-cn、zh-hk、zh-tw），使用中文资源，否则使用英文
        if (systemLocale.getLanguage().equals("zh")) {
            String country = systemLocale.getCountry().toLowerCase();
            if (country.equals("cn") || country.equals("hk") || country.equals("tw")) {
                currentLocale = new Locale("zh", "CN");
            } else {
                currentLocale = new Locale("en", "US");
            }
        } else {
            currentLocale = new Locale("en", "US");
        }
        try {
            messages = ResourceBundle.getBundle("messages", currentLocale);
        } catch (Exception e) {
            // 如果加载失败，使用默认的英文
            currentLocale = new Locale("en", "US");
            messages = ResourceBundle.getBundle("messages", currentLocale);
        }

        setTitle(getString("window.title"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 设置窗口图标
        setWindowIcon();

        // 设置中文字体
        setChineseFont();

        // 创建标签页
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab(getString("tab.singleSeedSearch"), createSingleSeedSearchPanel());
        tabbedPane.addTab(getString("tab.listSearch"), createListSearchPanel());
        add(tabbedPane, BorderLayout.CENTER);

        pack();
        setSize(1500, 1000);
        setLocationRelativeTo(null);
    }

    /** Credit 区域补充说明（不含内存估算与阶段列表） */
    private String buildCreditMemoryPhaseHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append(getString("hint.minSlimeRatioRange")).append("<br>");
        sb.append(getString("hint.outputHint"));
        sb.append("<br><br>").append(getString("credit.biomeConversion.header"));
        sb.append("<br>").append(getString("credit.biomeConversion.river"));
        sb.append("<br>").append(getString("credit.biomeConversion.dripstone_caves"));
        sb.append("<br>").append(getString("credit.biomeConversion.old_growth_pine_taiga"));
        sb.append("<br>").append(getString("credit.biomeConversion.mushroom_fields"));
        sb.append("<br>").append(getString("credit.biomeConversion.deep_dark"));
        sb.append("<br>").append(getString("credit.biomeConversion.sulfur_caves"));
        return sb.toString();
    }

    /**
     * 获取合并后的 credit 文本
     */
    private String buildCreditText() {
        return getString("credit.text", buildCreditMemoryPhaseHtml());
    }

    /**
     * 获取本地化字符串
     */
    private String getString(String key) {
        try {
            return messages.getString(key);
        } catch (Exception e) {
            return key; // 如果找不到，返回key本身
        }
    }

    /**
     * 获取格式化字符串
     */
    private String getString(String key, Object... args) {
        try {
            return MessageFormat.format(messages.getString(key), args);
        } catch (Exception e) {
            return key; // 如果找不到，返回key本身
        }
    }

    // 创建单种子搜索面板
    private JPanel createSingleSeedSearchPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 左侧：输入和进度
        JPanel leftPanel = new JPanel(new BorderLayout());

        // 输入区域
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Seed 输入
        gbc.gridx = 0;
        gbc.gridy = 0;
        searchSeedLabel = new JLabel(getString("label.seed"));
        searchSeedLabel.setFont(getLoadedFont());
        inputPanel.add(searchSeedLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        searchSeedField = new JTextField("", 20);
        // 添加输入验证，非整数时提示
        searchSeedField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(searchSeedField, getString("label.seed").replace(":", ""));
            }
        });
        inputPanel.add(searchSeedField, gbc);

        // Thread Count 输入
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchThreadCountLabel = new JLabel(getString("label.threadCount"));
        searchThreadCountLabel.setFont(getLoadedFont());
        inputPanel.add(searchThreadCountLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        searchThreadCountField = new JTextField(String.valueOf(Runtime.getRuntime().availableProcessors()), 20);
        // 添加输入验证，非整数时提示
        searchThreadCountField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(searchThreadCountField, getString("label.threadCount").replace(":", ""));
            }
        });
        inputPanel.add(searchThreadCountField, gbc);

        // 最低河流面积
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchMinSlimeAreaLabel = new JLabel(getString("label.minSlimeArea"));
        searchMinSlimeAreaLabel.setFont(getLoadedFont());
        inputPanel.add(searchMinSlimeAreaLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        searchMinSlimeAreaField = new JTextField(String.valueOf(DEFAULT_SLIME_AREA_UI), 20);
        searchMinSlimeAreaField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                syncSearchRatioFromArea();
            }
        });
        inputPanel.add(searchMinSlimeAreaField, gbc);

        // 最低河流群系占比
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchMinSlimeRatioLabel = new JLabel(getString("label.minSlimeRatio"));
        searchMinSlimeRatioLabel.setFont(getLoadedFont());
        inputPanel.add(searchMinSlimeRatioLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        searchMinSlimeRatioField = new JTextField(String.format("%.2f", DEFAULT_SLIME_RATIO_UI), 20);
        searchMinSlimeRatioField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                syncSearchAreaFromRatio();
            }
        });
        inputPanel.add(searchMinSlimeRatioField, gbc);

        // MinX 输入
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel minXLabel = new JLabel(getString("label.minX"));
        minXLabel.setFont(getLoadedFont());
        inputPanel.add(minXLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        minXField = new JTextField(String.valueOf(DEFAULT_MIN_X), 20);
        // 添加输入验证，非整数时提示
        minXField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(minXField, getString("label.minX").replace(":", ""));
            }
        });
        inputPanel.add(minXField, gbc);

        // MaxX 输入
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel maxXLabel = new JLabel(getString("label.maxX"));
        maxXLabel.setFont(getLoadedFont());
        inputPanel.add(maxXLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        maxXField = new JTextField(String.valueOf(DEFAULT_MAX_X), 20);
        maxXField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(maxXField, getString("label.maxX").replace(":", ""));
            }
        });
        inputPanel.add(maxXField, gbc);

        // MinZ 输入
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel minZLabel = new JLabel(getString("label.minZ"));
        minZLabel.setFont(getLoadedFont());
        inputPanel.add(minZLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        minZField = new JTextField(String.valueOf(DEFAULT_MIN_Z), 20);
        minZField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(minZField, getString("label.minZ").replace(":", ""));
            }
        });
        inputPanel.add(minZField, gbc);

        // MaxZ 输入
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel maxZLabel = new JLabel(getString("label.maxZ"));
        maxZLabel.setFont(getLoadedFont());
        inputPanel.add(maxZLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        maxZField = new JTextField(String.valueOf(DEFAULT_MAX_Z), 20);
        maxZField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(maxZField, getString("label.maxZ").replace(":", ""));
            }
        });
        inputPanel.add(maxZField, gbc);

        // MC 版本
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchMcVersionLabel = new JLabel(getString("label.mcVersion"));
        searchMcVersionLabel.setFont(getLoadedFont());
        inputPanel.add(searchMcVersionLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        searchVersionComboBox = new JComboBox<>(VERSION_OPTIONS);
        searchVersionComboBox.setSelectedIndex(VERSION_OPTIONS.length - 1);
        inputPanel.add(searchVersionComboBox, gbc);

        // 群系折算
        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchBiomeConversionCheckBox = new JCheckBox(getString("label.biomeConversion"));
        searchBiomeConversionCheckBox.setFont(getLoadedFont());
        gbc.gridwidth = 2;
        inputPanel.add(searchBiomeConversionCheckBox, gbc);
        gbc.gridwidth = 1;

        // 语言选择下拉框
        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        searchLanguageLabel = new JLabel(getString("label.language"));
        searchLanguageLabel.setFont(getLoadedFont());
        inputPanel.add(searchLanguageLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        String[] languageOptions = {"Chinese", "English"};
        languageComboBox = new JComboBox<>(languageOptions);
        // 根据当前语言设置默认选项
        if (currentLocale.getLanguage().equals("zh")) {
            languageComboBox.setSelectedIndex(0);
        } else {
            languageComboBox.setSelectedIndex(1);
        }
        languageComboBox.addActionListener(e -> changeLanguage());
        inputPanel.add(languageComboBox, gbc);

        // 按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout());
        searchStartButton = new JButton(getString("button.startSearch"));
        searchPauseButton = new JButton(getString("button.pause"));
        searchStopButton = new JButton(getString("button.stop"));
        searchResetButton = new JButton(getString("button.reset"));
        searchResetWorldButton = new JButton(getString("button.resetWorldBorder"));
        searchPauseButton.setEnabled(false);
        searchStopButton.setEnabled(false);
        buttonPanel.add(searchStartButton);
        buttonPanel.add(searchPauseButton);
        buttonPanel.add(searchStopButton);
        buttonPanel.add(searchResetButton);
        buttonPanel.add(searchResetWorldButton);

        // Static credit text above buttons
        JPanel creditPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        creditPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        searchCreditLabel = new JLabel(buildCreditText());
        searchCreditLabel.setFont(getLoadedFont()); // 使用加载的字体
        creditPanel.add(searchCreditLabel);

        // 将 credit 和按钮放在一个容器中，credit 在上，按钮在下
        JPanel creditButtonPanel = new JPanel(new BorderLayout());
        creditButtonPanel.add(creditPanel, BorderLayout.NORTH);
        creditButtonPanel.add(buttonPanel, BorderLayout.SOUTH);

        // 进度区域
        JPanel progressPanel = new JPanel(new GridBagLayout());
        GridBagConstraints pgc = new GridBagConstraints();
        pgc.insets = new Insets(5, 5, 5, 5);
        pgc.anchor = GridBagConstraints.WEST;
        pgc.fill = GridBagConstraints.HORIZONTAL;
        pgc.weightx = 1.0;

        pgc.gridx = 0;
        pgc.gridy = 0;
        pgc.gridwidth = 2;
        searchProgressBar = new JProgressBar(0, 100);
        searchProgressBar.setStringPainted(true);
        searchProgressBar.setString(getString("progress.format", 0, 0, 0.0));
        progressPanel.add(searchProgressBar, pgc);

        pgc.gridwidth = 1;
        pgc.gridy = 1;
        searchElapsedTimeLabel = new JLabel(getString("elapsedTime", formatTime(0)));
        progressPanel.add(searchElapsedTimeLabel, pgc);

        pgc.gridy = 3;
        searchRemainingTimeLabel = new JLabel(getString("remainingTime.calculating"));
        progressPanel.add(searchRemainingTimeLabel, pgc);

        leftPanel.add(inputPanel, BorderLayout.NORTH);
        leftPanel.add(creditButtonPanel, BorderLayout.CENTER);

        // Progress area in separate container (credit merged into label above)
        JPanel leftBottomPanel = new JPanel(new BorderLayout());
        leftBottomPanel.add(progressPanel, BorderLayout.NORTH);

        JPanel leftContainer = new JPanel(new BorderLayout());
        leftContainer.add(leftPanel, BorderLayout.CENTER);
        leftContainer.add(leftBottomPanel, BorderLayout.SOUTH);

        // 右侧：结果显示
        searchRightPanel = new JPanel(new BorderLayout());
        searchRightPanel.setBorder(BorderFactory.createTitledBorder(getString("result.border")));
        searchResultArea = new JTextArea();
        searchResultArea.setEditable(false);
        searchResultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(searchResultArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        JPanel exportSortPanel = new JPanel(new FlowLayout());
        searchExportButton = new JButton(getString("button.export"));
        searchExportButton.addActionListener(e -> exportSearchResults());
        searchSortButton = new JButton(getString("button.sort"));
        searchSortButton.addActionListener(e -> sortSearchResults());
        exportSortPanel.add(searchExportButton);
        exportSortPanel.add(searchSortButton);
        searchRightPanel.add(scrollPane, BorderLayout.CENTER);
        searchRightPanel.add(exportSortPanel, BorderLayout.SOUTH);

        // 使用 JSplitPane 分割
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftContainer, searchRightPanel);
        splitPane.setDividerLocation(600);
        splitPane.setResizeWeight(0.5);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        // 添加事件监听
        searchStartButton.addActionListener(e -> startSearch());
        searchPauseButton.addActionListener(e -> toggleSearchPause());
        searchStopButton.addActionListener(e -> stopSearch());
        searchResetButton.addActionListener(e -> resetSearchToDefaults());
        searchResetWorldButton.addActionListener(e -> resetSearchToWorldBounds());

        // 添加输入字段监听，检测参数变化
        addSearchParameterListeners();

        return mainPanel;
    }


    // 添加搜索参数监听器，检测参数变化（不包括线程数）
    private void addSearchParameterListeners() {
        // 种子变化监听（在已有监听器基础上添加检查）
        searchSeedField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }
        });

        // 最低河流面积/占比：仅 focusLost 时联动，避免输入时被覆盖

        // 坐标变化监听
        minXField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }
        });
        maxXField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }
        });
        minZField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }
        });
        maxZField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                checkSearchParameterChange();
            }
        });
    }

    // 检查搜索参数是否变化
    private void checkSearchParameterChange() {
        if (isSearchRunning && !isSearchPaused) {
            return; // 运行中且未暂停，不检查
        }

        if (!isSearchPaused) {
            return; // 未暂停，不检查
        }

        try {
            String seedText = searchSeedField.getText().trim();
            if (seedText.isEmpty()) {
                return;
            }
            long seed = Long.parseLong(seedText);
            int minArea = parseMinSlimeArea(searchMinSlimeAreaField);
            if (minArea < 0) minArea = 0;
            int minX = Integer.parseInt(minXField.getText().trim());
            int maxX = Integer.parseInt(maxXField.getText().trim());
            int minZ = Integer.parseInt(minZField.getText().trim());
            int maxZ = Integer.parseInt(maxZField.getText().trim());

            // 如果参数变化且处于暂停状态，重置进度（线程数变化不触发重置）
            if (minArea < 0) minArea = 0;
            if (seed != lastSearchSeed || minX != lastSearchMinX ||
                    maxX != lastSearchMaxX || minZ != lastSearchMinZ || maxZ != lastSearchMaxZ ||
                    minArea != lastSearchMinArea) {
                if (slimeRunner != null) {
                    slimeRunner.stop();
                }
                isSearchRunning = false;
                isSearchPaused = false;
                searchStartButton.setEnabled(true);
                searchPauseButton.setEnabled(false);
                searchPauseButton.setText(getString("button.pause"));
                searchStopButton.setEnabled(false);
                searchResetButton.setEnabled(true);
                if (searchResetWorldButton != null) searchResetWorldButton.setEnabled(true);
                searchSeedField.setEnabled(true);
                searchThreadCountField.setEnabled(true);
                searchMinSlimeAreaField.setEnabled(true);
                searchMinSlimeRatioField.setEnabled(true);
                minXField.setEnabled(true);
                maxXField.setEnabled(true);
                minZField.setEnabled(true);
                maxZField.setEnabled(true);
                if (languageComboBox != null) {
                    languageComboBox.setEnabled(true);
                }
                searchResultArea.setText("");
                searchProgressBar.setIndeterminate(false);
                searchProgressBar.setValue(0);
                searchProgressBar.setString(getString("progress.format", 0, 0, 0.0));
                searchRemainingTimeLabel.setText(getString("remainingTime.reset"));
            }
        } catch (NumberFormatException e) {
            // 忽略无效输入
        }
    }


    // 验证整数输入
    private void validateIntegerInput(JTextField field, String fieldName) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return; // 空值不验证，会在开始运行时验证
        }
        try {
            // 尝试解析为double，检查是否为整数
            double value = Double.parseDouble(text);
            if (value != Math.floor(value)) {
                JOptionPane.showMessageDialog(this, getString("validation.integerRequired", fieldName), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                field.requestFocus();
            }
        } catch (NumberFormatException e) {
            // 不是数字，会在开始运行时验证
        }
    }

    /**
     * 单种子 Tab：根据面积同步占比（仅 Tab 内联动），面积限制在 [MIN_SLIME_AREA_UI, MAX_SLIME_AREA_UI]
     */
    private void syncSearchRatioFromArea() {
        if (searchSlimeParamUpdating || searchMinSlimeAreaField == null || searchMinSlimeRatioField == null) return;
        try {
            int area = Integer.parseInt(searchMinSlimeAreaField.getText().trim());
            area = Math.max(MIN_SLIME_AREA_UI, Math.min(MAX_SLIME_AREA_UI, area));
            searchSlimeParamUpdating = true;
            double ratio = SLIME_AREA_FULL > 0 ? (area / SLIME_AREA_FULL * 100.0) : 0;
            ratio = Math.max(MIN_SLIME_RATIO_UI, Math.min(MAX_SLIME_RATIO_UI, ratio));
            searchMinSlimeRatioField.setText(String.format("%.2f", ratio));
            searchMinSlimeAreaField.setText(String.valueOf(area));
        } catch (NumberFormatException ignored) {
        } finally {
            searchSlimeParamUpdating = false;
        }
    }

    /**
     * 单种子 Tab：根据占比同步面积（仅 Tab 内联动），占比限制在 [MIN_SLIME_RATIO_UI, MAX_SLIME_RATIO_UI]
     */
    private void syncSearchAreaFromRatio() {
        if (searchSlimeParamUpdating || searchMinSlimeAreaField == null || searchMinSlimeRatioField == null) return;
        try {
            double ratio = Double.parseDouble(searchMinSlimeRatioField.getText().trim());
            ratio = Math.max(MIN_SLIME_RATIO_UI, Math.min(MAX_SLIME_RATIO_UI, ratio));
            searchSlimeParamUpdating = true;
            int area = (int) Math.round(SLIME_AREA_FULL * ratio / 100.0);
            area = Math.max(MIN_SLIME_AREA_UI, Math.min(MAX_SLIME_AREA_UI, area));
            searchMinSlimeAreaField.setText(String.valueOf(area));
            searchMinSlimeRatioField.setText(String.format("%.2f", ratio));
        } catch (NumberFormatException ignored) {
        } finally {
            searchSlimeParamUpdating = false;
        }
    }

    /**
     * 列表 Tab：根据面积同步占比，面积限制在 [MIN_SLIME_AREA_UI, MAX_SLIME_AREA_UI]
     */
    private void syncListRatioFromArea() {
        if (listSlimeParamUpdating || listMinSlimeAreaField == null || listMinSlimeRatioField == null) return;
        try {
            int area = Integer.parseInt(listMinSlimeAreaField.getText().trim());
            area = Math.max(MIN_SLIME_AREA_UI, Math.min(MAX_SLIME_AREA_UI, area));
            listSlimeParamUpdating = true;
            double ratio = SLIME_AREA_FULL > 0 ? (area / SLIME_AREA_FULL * 100.0) : 0;
            ratio = Math.max(MIN_SLIME_RATIO_UI, Math.min(MAX_SLIME_RATIO_UI, ratio));
            listMinSlimeRatioField.setText(String.format("%.2f", ratio));
            listMinSlimeAreaField.setText(String.valueOf(area));
        } catch (NumberFormatException ignored) {
        } finally {
            listSlimeParamUpdating = false;
        }
    }

    /**
     * 列表 Tab：根据占比同步面积，占比限制在 [MIN_SLIME_RATIO_UI, MAX_SLIME_RATIO_UI]
     */
    private void syncListAreaFromRatio() {
        if (listSlimeParamUpdating || listMinSlimeAreaField == null || listMinSlimeRatioField == null) return;
        try {
            double ratio = Double.parseDouble(listMinSlimeRatioField.getText().trim());
            ratio = Math.max(MIN_SLIME_RATIO_UI, Math.min(MAX_SLIME_RATIO_UI, ratio));
            listSlimeParamUpdating = true;
            int area = (int) Math.round(SLIME_AREA_FULL * ratio / 100.0);
            area = Math.max(MIN_SLIME_AREA_UI, Math.min(MAX_SLIME_AREA_UI, area));
            listMinSlimeAreaField.setText(String.valueOf(area));
            listMinSlimeRatioField.setText(String.format("%.2f", ratio));
        } catch (NumberFormatException ignored) {
        } finally {
            listSlimeParamUpdating = false;
        }
    }

    /**
     * 解析最低河流面积，无效返回 -1；有效范围为 [MIN_SLIME_AREA_UI, MAX_SLIME_AREA_UI]
     */
    private int parseMinSlimeArea(JTextField field) {
        if (field == null) return -1;
        try {
            int a = Integer.parseInt(field.getText().trim());
            return a >= MIN_SLIME_AREA_UI && a <= MAX_SLIME_AREA_UI ? a : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // 排序搜索结果（按面积从大到小，格式为 x z area ratio%）

    /**
     * 从结果行解析排序用面积（勾选群系折算时用折算后面积，否则用总面积）
     */
    private int parseAreaFromResultLine(String line) {
        boolean useConverted = searchBiomeConversionCheckBox != null && searchBiomeConversionCheckBox.isSelected();
        return SlimeSearchRunner.parseSortAreaFromLine(line, useConverted);
    }

    private int parseListSortAreaFromResultLine(String line) {
        boolean useConverted = listBiomeConversionCheckBox != null && listBiomeConversionCheckBox.isSelected();
        return SlimeSearchRunner.parseSortAreaFromLine(line, useConverted);
    }

    private void sortSearchResults() {
        String text = searchResultArea.getText().trim();
        if (text.isEmpty()) return;
        String[] lines = text.split("\n");
        List<String> withArea = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int area = parseAreaFromResultLine(line);
            if (area >= 0) {
                withArea.add(line);
            } else {
                other.add(line);
            }
        }
        withArea.sort((a, b) -> {
            int areaA = parseAreaFromResultLine(a);
            int areaB = parseAreaFromResultLine(b);
            return Integer.compare(areaB, areaA);
        });
        StringBuilder sb = new StringBuilder();
        for (String s : withArea) sb.append(s).append("\n");
        for (String s : other) sb.append(s).append("\n");
        searchResultArea.setText(sb.toString());
    }

    // 搜索相关方法（河流多阶段搜索）
    private void startSearch() {
        if (isSearchRunning && isSearchPaused) {
            if (slimeRunner != null) {
                slimeRunner.resume();
                isSearchPaused = false;
                searchPauseButton.setText(getString("button.pause"));
            }
            return;
        }

        try {
            // 验证种子
            String seedText = searchSeedField.getText().trim();
            if (seedText.isEmpty()) {
                JOptionPane.showMessageDialog(this, getString("error.seedRequired"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 检查种子是否为整数
            double seedDouble;
            try {
                seedDouble = Double.parseDouble(seedText);
                if (seedDouble != Math.floor(seedDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.seedMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.seedFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 检查种子是否超过MC正常种子边界（绝对值超过2^63-1）
            long seed;
            try {
                seed = Long.parseLong(seedText);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.seedOutOfRange"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 验证线程数
            String threadText = searchThreadCountField.getText().trim();
            double threadDouble;
            try {
                threadDouble = Double.parseDouble(threadText);
                if (threadDouble != Math.floor(threadDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.threadCountMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.threadCountFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            int threadCount = (int) threadDouble;
            if (threadCount < 1) {
                JOptionPane.showMessageDialog(this, getString("error.threadCountRequired"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 检查线程数是否超过CPU核数
            int cpuThreads = Runtime.getRuntime().availableProcessors();
            if (threadCount > cpuThreads) {
                int result = JOptionPane.showConfirmDialog(
                        this,
                        getString("error.threadCountExceedsCPU", cpuThreads, cpuThreads),
                        getString("prompt.adjustThreadCount"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );
                if (result == JOptionPane.YES_OPTION) {
                    threadCount = cpuThreads;
                    searchThreadCountField.setText(String.valueOf(cpuThreads));
                } else {
                    return;
                }
            }

            int minArea = parseMinSlimeArea(searchMinSlimeAreaField);
            if (minArea < 0) {
                String msg = getString("error.minSlimeAreaInvalid");
                try {
                    int raw = Integer.parseInt(searchMinSlimeAreaField.getText().trim());
                    if (raw > MAX_SLIME_AREA_UI) {
                        msg = getString("error.minSlimeAreaTooLarge");
                    }
                } catch (NumberFormatException ignored) {
                }
                JOptionPane.showMessageDialog(this, msg, getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 验证XZ坐标
            String minXText = minXField.getText().trim();
            String maxXText = maxXField.getText().trim();
            String minZText = minZField.getText().trim();
            String maxZText = maxZField.getText().trim();

            // 检查是否为整数
            double minXDouble, maxXDouble, minZDouble, maxZDouble;
            try {
                minXDouble = Double.parseDouble(minXText);
                if (minXDouble != Math.floor(minXDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.minXMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    minXField.setText(String.valueOf(DEFAULT_MIN_X));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.minXFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                minXField.setText(String.valueOf(DEFAULT_MIN_X));
                return;
            }

            try {
                maxXDouble = Double.parseDouble(maxXText);
                if (maxXDouble != Math.floor(maxXDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.maxXMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    maxXField.setText(String.valueOf(DEFAULT_MAX_X));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.maxXFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                maxXField.setText(String.valueOf(DEFAULT_MAX_X));
                return;
            }

            try {
                minZDouble = Double.parseDouble(minZText);
                if (minZDouble != Math.floor(minZDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.minZMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    minZField.setText(String.valueOf(DEFAULT_MIN_Z));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.minZFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                minZField.setText(String.valueOf(DEFAULT_MIN_Z));
                return;
            }

            try {
                maxZDouble = Double.parseDouble(maxZText);
                if (maxZDouble != Math.floor(maxZDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.maxZMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    maxZField.setText(String.valueOf(DEFAULT_MAX_Z));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.maxZFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                maxZField.setText(String.valueOf(DEFAULT_MAX_Z));
                return;
            }

            int minX = (int) minXDouble;
            int maxX = (int) maxXDouble;
            int minZ = (int) minZDouble;
            int maxZ = (int) maxZDouble;

            if (minX >= maxX) {
                JOptionPane.showMessageDialog(this, getString("error.minXGreaterThanMaxX"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (minZ >= maxZ) {
                JOptionPane.showMessageDialog(this, getString("error.minZGreaterThanMaxZ"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            long searchArea = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
            if (searchArea > SlimeSearchRunner.MAX_SEARCH_AREA_BLOCKS) {
                JOptionPane.showMessageDialog(this, getString("error.searchAreaTooLarge"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            int searchMinX = minX - SlimeSearchRunner.RING_OUTER;
            int searchMaxX = maxX + SlimeSearchRunner.RING_OUTER;
            int searchMinZ = minZ - SlimeSearchRunner.RING_OUTER;
            int searchMaxZ = maxZ + SlimeSearchRunner.RING_OUTER;
            int baseX1 = (searchMinX / SlimeSearchRunner.PHASE1_GRID_STEP) * SlimeSearchRunner.PHASE1_GRID_STEP;
            int baseZ1 = (searchMinZ / SlimeSearchRunner.PHASE1_GRID_STEP) * SlimeSearchRunner.PHASE1_GRID_STEP;
            long widthSteps1 = (searchMaxX - baseX1) / SlimeSearchRunner.PHASE1_GRID_STEP + 1;
            long heightSteps1 = (searchMaxZ - baseZ1) / SlimeSearchRunner.PHASE1_GRID_STEP + 1;
            long totalGrid = widthSteps1 * heightSteps1;
            if (totalGrid > SlimeSearchRunner.MAX_SEARCH_AREA_BLOCKS) {
                JOptionPane.showMessageDialog(this, getString("error.searchAreaTooLarge"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 检查世界边界：minX < -30000000, maxX > 30000000, minZ < -30000000, maxZ > 30000000
            boolean outOfBounds = minX < BOUNDARY_MIN_X || maxX > BOUNDARY_MAX_X || minZ < BOUNDARY_MIN_Z || maxZ > BOUNDARY_MAX_Z;

            if (outOfBounds) {
                int result = JOptionPane.showConfirmDialog(
                        this,
                        getString("error.outOfBounds"),
                        getString("prompt.warning"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (result != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            lastSearchSeed = seed;
            lastSearchMinX = minX;
            lastSearchMaxX = maxX;
            lastSearchMinZ = minZ;
            lastSearchMaxZ = maxZ;
            lastSearchMinArea = minArea;
            lastSearchThreadCount = threadCount;

            isSearchRunning = true;
            isSearchPaused = false;
            searchStartButton.setEnabled(false);
            searchPauseButton.setEnabled(true);
            searchPauseButton.setText(getString("button.pause"));
            searchStopButton.setEnabled(true);
            searchResetButton.setEnabled(false);
            if (searchResetWorldButton != null) searchResetWorldButton.setEnabled(false);
            searchSeedField.setEnabled(false);
            searchThreadCountField.setEnabled(false);
            searchMinSlimeAreaField.setEnabled(false);
            searchMinSlimeRatioField.setEnabled(false);
            minXField.setEnabled(false);
            maxXField.setEnabled(false);
            minZField.setEnabled(false);
            maxZField.setEnabled(false);
            if (searchVersionComboBox != null) searchVersionComboBox.setEnabled(false);
            if (searchBiomeConversionCheckBox != null) searchBiomeConversionCheckBox.setEnabled(false);
            if (languageComboBox != null) languageComboBox.setEnabled(false);
            final long resultToken = singleSearchResultToken.incrementAndGet();
            searchResultArea.setText("");
            searchProgressBar.setIndeterminate(false);
            searchProgressBar.setValue(0);
            searchProgressBar.setString(getString("progress.format", 0, 0, 0.0));
            searchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(0)));
            searchRemainingTimeLabel.setText(getString("remainingTime.calculating"));

            int mcVersion = resolveMcVersion(searchVersionComboBox);
            int versionTier = resolveVersionTier(searchVersionComboBox);
            boolean biomeConversion = searchBiomeConversionCheckBox != null && searchBiomeConversionCheckBox.isSelected();

            slimeRunner = new SlimeSearchRunner();
            slimeRunner.startSlimeSearch(seed, minX, maxX, minZ, maxZ, minArea,
                mcVersion, versionTier, biomeConversion, threadCount,
                getString("result.convertedArea"),
                info -> updateSlimeSearchProgress(resultToken, info),
                line -> addSearchResult(resultToken, line));

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, getString("error.invalidNumber"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleSearchPause() {
        if (slimeRunner == null || !isSearchRunning) return;
        if (isSearchPaused) {
            slimeRunner.resume();
            isSearchPaused = false;
            searchPauseButton.setText(getString("button.pause"));
        } else {
            slimeRunner.pause();
            isSearchPaused = true;
            searchPauseButton.setText(getString("button.resume"));
        }
        // 运行中（含暂停）不允许改线程数；JNI 线程数仅在开始搜索时传入
        searchThreadCountField.setEnabled(false);
    }

    private void stopSearch() {
        if (slimeRunner != null) {
            slimeRunner.stop();
        }
        singleSearchResultToken.incrementAndGet();
        enableSearchControls();
        if (searchProgressBar != null) {
            searchProgressBar.setIndeterminate(false);
        }
        searchStopButton.setEnabled(false);
        searchRemainingTimeLabel.setText(getString("remainingTime.stopped"));
    }

    private void resetSearchToDefaults() {
        minXField.setText(String.valueOf(DEFAULT_MIN_X));
        maxXField.setText(String.valueOf(DEFAULT_MAX_X));
        minZField.setText(String.valueOf(DEFAULT_MIN_Z));
        maxZField.setText(String.valueOf(DEFAULT_MAX_Z));
    }

    private void resetSearchToWorldBounds() {
        minXField.setText(String.valueOf(BOUNDARY_MIN_X));
        maxXField.setText(String.valueOf(BOUNDARY_MAX_X));
        minZField.setText(String.valueOf(BOUNDARY_MIN_Z));
        maxZField.setText(String.valueOf(BOUNDARY_MAX_Z));
    }

    // 创建从种子列表搜索面板
    private JPanel createListSearchPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 左侧：输入和进度
        JPanel leftPanel = new JPanel(new BorderLayout());

        // 输入区域
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Seed 文件选择
        gbc.gridx = 0;
        gbc.gridy = 0;
        listSearchSeedFileTitleLabel = new JLabel(getString("label.seedFile"));
        listSearchSeedFileTitleLabel.setFont(getLoadedFont());
        inputPanel.add(listSearchSeedFileTitleLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JPanel seedFilePanel = new JPanel(new BorderLayout());
        listSearchSeedFileButton = new JButton(getString("button.selectFile"));
        listSearchSeedFileButton.addActionListener(e -> selectSeedFile());
        listSearchSeedFileLabel = new JLabel(getString("label.noFileSelected"));
        listSearchSeedFileLabel.setFont(getLoadedFont());
        seedFilePanel.add(listSearchSeedFileButton, BorderLayout.WEST);
        seedFilePanel.add(listSearchSeedFileLabel, BorderLayout.CENTER);
        inputPanel.add(seedFilePanel, gbc);

        // Thread Count 输入
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        listSearchThreadCountLabel = new JLabel(getString("label.threadCount"));
        listSearchThreadCountLabel.setFont(getLoadedFont());
        inputPanel.add(listSearchThreadCountLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        listSearchThreadCountField = new JTextField(String.valueOf(Runtime.getRuntime().availableProcessors()), 20);
        listSearchThreadCountField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(listSearchThreadCountField, getString("label.threadCount").replace(":", ""));
            }
        });
        inputPanel.add(listSearchThreadCountField, gbc);

        // 最低河流面积
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        listSearchMinSlimeAreaLabel = new JLabel(getString("label.minSlimeArea"));
        listSearchMinSlimeAreaLabel.setFont(getLoadedFont());
        inputPanel.add(listSearchMinSlimeAreaLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        listMinSlimeAreaField = new JTextField(String.valueOf(DEFAULT_SLIME_AREA_UI), 20);
        listMinSlimeAreaField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                syncListRatioFromArea();
            }
        });
        inputPanel.add(listMinSlimeAreaField, gbc);

        // 最低河流群系占比
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        listSearchMinSlimeRatioLabel = new JLabel(getString("label.minSlimeRatio"));
        listSearchMinSlimeRatioLabel.setFont(getLoadedFont());
        inputPanel.add(listSearchMinSlimeRatioLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        listMinSlimeRatioField = new JTextField(String.format("%.2f", DEFAULT_SLIME_RATIO_UI), 20);
        listMinSlimeRatioField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                syncListAreaFromRatio();
            }
        });
        inputPanel.add(listMinSlimeRatioField, gbc);

        // MinX 输入
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        listSearchMinXLabel = new JLabel(getString("label.minX"));
        listSearchMinXLabel.setFont(getLoadedFont());
        inputPanel.add(listSearchMinXLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        listMinXField = new JTextField(String.valueOf(DEFAULT_LIST_MIN_X), 20);
        listMinXField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(listMinXField, getString("label.minX").replace(":", ""));
            }
        });
        inputPanel.add(listMinXField, gbc);

        // MaxX 输入
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        listSearchMaxXLabel = new JLabel(getString("label.maxX"));
        listSearchMaxXLabel.setFont(getLoadedFont());
        inputPanel.add(listSearchMaxXLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        listMaxXField = new JTextField(String.valueOf(DEFAULT_LIST_MAX_X), 20);
        listMaxXField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(listMaxXField, getString("label.maxX").replace(":", ""));
            }
        });
        inputPanel.add(listMaxXField, gbc);

        // MinZ 输入
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        listSearchMinZLabel = new JLabel(getString("label.minZ"));
        listSearchMinZLabel.setFont(getLoadedFont());
        inputPanel.add(listSearchMinZLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        listMinZField = new JTextField(String.valueOf(DEFAULT_LIST_MIN_Z), 20);
        listMinZField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(listMinZField, getString("label.minZ").replace(":", ""));
            }
        });
        inputPanel.add(listMinZField, gbc);

        // MaxZ 输入
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        listSearchMaxZLabel = new JLabel(getString("label.maxZ"));
        listSearchMaxZLabel.setFont(getLoadedFont());
        inputPanel.add(listSearchMaxZLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        listMaxZField = new JTextField(String.valueOf(DEFAULT_LIST_MAX_Z), 20);
        listMaxZField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                validateIntegerInput(listMaxZField, getString("label.maxZ").replace(":", ""));
            }
        });
        inputPanel.add(listMaxZField, gbc);

        // MC 版本
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        listMcVersionLabel = new JLabel(getString("label.mcVersion"));
        listMcVersionLabel.setFont(getLoadedFont());
        inputPanel.add(listMcVersionLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        listVersionComboBox = new JComboBox<>(VERSION_OPTIONS);
        listVersionComboBox.setSelectedIndex(VERSION_OPTIONS.length - 1);
        inputPanel.add(listVersionComboBox, gbc);

        // 群系折算
        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        listBiomeConversionCheckBox = new JCheckBox(getString("label.biomeConversion"));
        listBiomeConversionCheckBox.setFont(getLoadedFont());
        gbc.gridwidth = 2;
        inputPanel.add(listBiomeConversionCheckBox, gbc);
        gbc.gridwidth = 1;

        // 列表 Tab 内面积↔占比：仅 focusLost 时联动，避免输入时被覆盖

        // 按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout());
        listSearchStartButton = new JButton(getString("button.startSearch"));
        listSearchPauseButton = new JButton(getString("button.pause"));
        listSearchStopButton = new JButton(getString("button.stop"));
        listSearchResetButton = new JButton(getString("button.resetList"));
        listSearchPauseButton.setEnabled(false);
        listSearchStopButton.setEnabled(false);
        buttonPanel.add(listSearchStartButton);
        buttonPanel.add(listSearchPauseButton);
        buttonPanel.add(listSearchStopButton);
        buttonPanel.add(listSearchResetButton);

        // Static credit text above buttons
        JPanel creditPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        creditPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        listSearchCreditLabel = new JLabel(buildCreditText());
        listSearchCreditLabel.setFont(getLoadedFont());
        creditPanel.add(listSearchCreditLabel);

        // 将 credit 和按钮放在一个容器中，credit 在上，按钮在下
        JPanel creditButtonPanel = new JPanel(new BorderLayout());
        creditButtonPanel.add(creditPanel, BorderLayout.NORTH);
        creditButtonPanel.add(buttonPanel, BorderLayout.SOUTH);

        // 进度区域
        JPanel progressPanel = new JPanel(new GridBagLayout());
        GridBagConstraints pgc = new GridBagConstraints();
        pgc.insets = new Insets(5, 5, 5, 5);
        pgc.anchor = GridBagConstraints.WEST;
        pgc.fill = GridBagConstraints.HORIZONTAL;
        pgc.weightx = 1.0;

        pgc.gridx = 0;
        pgc.gridy = 0;
        pgc.gridwidth = 2;
        listSearchProgressBar = new JProgressBar(0, 100);
        listSearchProgressBar.setStringPainted(true);
        listSearchProgressBar.setString(getString("progress.total", 0, 0, 0.0));
        progressPanel.add(listSearchProgressBar, pgc);

        pgc.gridwidth = 1;
        pgc.gridy = 1;
        listSearchElapsedTimeLabel = new JLabel(getString("elapsedTime", formatTime(0)));
        progressPanel.add(listSearchElapsedTimeLabel, pgc);

        pgc.gridy = 2;
        listSearchCurrentSeedProgressLabel = new JLabel(getString("currentSeed.default"));
        listSearchCurrentSeedProgressLabel.setFont(getLoadedFont());
        progressPanel.add(listSearchCurrentSeedProgressLabel, pgc);

        pgc.gridy = 3;
        listSearchRemainingTimeLabel = new JLabel(getString("remainingTime.calculating"));
        progressPanel.add(listSearchRemainingTimeLabel, pgc);

        leftPanel.add(inputPanel, BorderLayout.NORTH);
        leftPanel.add(creditButtonPanel, BorderLayout.CENTER);

        // 将进度区域放在另一个容器中
        JPanel leftBottomPanel = new JPanel(new BorderLayout());
        leftBottomPanel.add(progressPanel, BorderLayout.CENTER);

        JPanel leftContainer = new JPanel(new BorderLayout());
        leftContainer.add(leftPanel, BorderLayout.CENTER);
        leftContainer.add(leftBottomPanel, BorderLayout.SOUTH);

        // 右侧：结果显示
        listSearchRightPanel = new JPanel(new BorderLayout());
        listSearchRightPanel.setBorder(BorderFactory.createTitledBorder(getString("result.border")));
        listSearchResultArea = new JTextArea();
        listSearchResultArea.setEditable(false);
        listSearchResultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(listSearchResultArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        JPanel exportPanel = new JPanel(new FlowLayout());
        listSearchExportButton = new JButton(getString("button.export"));
        listSearchExportButton.addActionListener(e -> exportListSearchResults());
        listSearchExportSeedListButton = new JButton(getString("button.exportSeedList"));
        listSearchExportSeedListButton.addActionListener(e -> exportSeedList());
        listSortByAreaButton = new JButton(getString("button.sortByArea"));
        listSortByAreaButton.addActionListener(e -> sortListByArea());
        listSortByDistanceButton = new JButton(getString("button.sortByDistance"));
        listSortByDistanceButton.addActionListener(e -> sortListByDistance());
        exportPanel.add(listSearchExportButton);
        exportPanel.add(listSearchExportSeedListButton);
        exportPanel.add(listSortByAreaButton);
        exportPanel.add(listSortByDistanceButton);
        listSearchRightPanel.add(scrollPane, BorderLayout.CENTER);
        listSearchRightPanel.add(exportPanel, BorderLayout.SOUTH);

        // 使用 JSplitPane 分割
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftContainer, listSearchRightPanel);
        splitPane.setDividerLocation(600);
        splitPane.setResizeWeight(0.5);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        // 添加事件监听
        listSearchStartButton.addActionListener(e -> startListSearch());
        listSearchPauseButton.addActionListener(e -> toggleListSearchPause());
        listSearchStopButton.addActionListener(e -> stopListSearch());
        listSearchResetButton.addActionListener(e -> resetListSearchToDefaults());

        // 添加输入字段监听，检测参数变化
        addListSearchParameterListeners();

        return mainPanel;
    }

    private void updateSlimeSearchProgress(long resultToken, SlimeSearchRunner.ProgressInfo info) {
        SwingUtilities.invokeLater(() -> {
            if (resultToken != singleSearchResultToken.get()) {
                return;
            }
            if (info.done()) {
                singleSearchResultToken.incrementAndGet();
                if (isSearchRunning) {
                    enableSearchControls();
                    searchProgressBar.setIndeterminate(false);
                    searchProgressBar.setValue(100);
                    searchProgressBar.setString(getString("progress.complete", info.processed(), info.total()));
                    searchRemainingTimeLabel.setText(getString("remainingTime.completed"));
                }
                return;
            }
            if (!isSearchRunning) return;
            if (info.refiningResults()) {
                searchProgressBar.setIndeterminate(true);
                searchProgressBar.setString(getString("progress.consolidating"));
                if (isSearchPaused) {
                    searchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(info.elapsedMs())));
                    searchRemainingTimeLabel.setText(getString("remainingTime.paused"));
                } else {
                    searchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(info.elapsedMs())));
                    searchRemainingTimeLabel.setText(getString("remainingTime.calculating"));
                }
                return;
            }
            searchProgressBar.setIndeterminate(false);
            int progress = (int) Math.min(100, info.percentage());
            searchProgressBar.setValue(progress);
            searchProgressBar.setString(getString("progress.format", info.processed(), info.total(), info.percentage()));
            if (isSearchPaused) {
                searchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(info.elapsedMs())));
                searchRemainingTimeLabel.setText(getString("remainingTime.paused"));
                return;
            }
            searchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(info.elapsedMs())));
            searchRemainingTimeLabel.setText(info.remainingMs() > 0 ? getString("remainingTime", formatTime(info.remainingMs())) : getString("remainingTime.calculating"));
        });
    }

    private void enableSearchControls() {
        isSearchRunning = false;
        isSearchPaused = false;
        searchStartButton.setEnabled(true);
        searchPauseButton.setEnabled(false);
        searchPauseButton.setText(getString("button.pause"));
        searchStopButton.setEnabled(false);
        searchResetButton.setEnabled(true);
        if (searchResetWorldButton != null) {
            searchResetWorldButton.setEnabled(true);
        }
        searchSeedField.setEnabled(true);
        searchThreadCountField.setEnabled(true);
        searchMinSlimeAreaField.setEnabled(true);
        searchMinSlimeRatioField.setEnabled(true);
        minXField.setEnabled(true);
        maxXField.setEnabled(true);
        minZField.setEnabled(true);
        maxZField.setEnabled(true);
        if (searchVersionComboBox != null) searchVersionComboBox.setEnabled(true);
        if (searchBiomeConversionCheckBox != null) searchBiomeConversionCheckBox.setEnabled(true);
        if (languageComboBox != null) languageComboBox.setEnabled(true);
    }

    private void addSearchResult(long resultToken, String result) {
        SwingUtilities.invokeLater(() -> {
            if (resultToken != singleSearchResultToken.get()) {
                return;
            }
            if (result == null || result.isEmpty()) {
                return;
            }
            searchResultArea.append(result);
            if (!result.endsWith("\n")) {
                searchResultArea.append("\n");
            }
            searchResultArea.setCaretPosition(searchResultArea.getDocument().getLength());
        });
    }

    private void exportSearchResults() {
        String resultText = searchResultArea.getText();
        if (resultText.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, getString("error.noResultsToExport"), getString("prompt.information"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(getString("dialog.exportResults"));
        fileChooser.setFileFilter(new FileNameExtensionFilter(getString("dialog.textFiles"), "txt"));
        fileChooser.setSelectedFile(new File(getString("file.searchOutput")));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                // 导出所有结果，包括带有"x"标记的无法生成的结果
                writer.print(resultText);
                JOptionPane.showMessageDialog(this, getString("success.export"), getString("prompt.success"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, getString("error.exportFailed", e.getMessage()), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    private String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return getString("time.format", days, hours, minutes, seconds);
    }

    /**
     * 设置窗口图标
     * 图标文件应放在 src/main/resources/icon.png 或 icon.ico
     */
    private void setWindowIcon() {
        try {
            // 尝试从资源文件加载图标
            java.net.URL iconURL = getClass().getResource("/icon.png");
            if (iconURL == null) {
                iconURL = getClass().getResource("/icon.ico");
            }
            if (iconURL != null) {
                ImageIcon icon = new ImageIcon(iconURL);
                setIconImage(icon.getImage());
            } else {
                // 如果没有找到图标文件，可以创建一个简单的默认图标
                // 或者使用系统默认图标（不设置）
                System.out.println("提示: 未找到图标文件 (icon.png 或 icon.ico)，使用系统默认图标");
            }
        } catch (Exception e) {
            System.err.println("设置图标时出错: " + e.getMessage());
        }
    }

    /**
     * 设置字体
     * 从资源文件加载 font.ttf 字体
     */
    private void setChineseFont() {
        try {
            // 从资源文件加载 font.ttf
            java.io.InputStream fontStream = getClass().getResourceAsStream("/font.ttf");
            if (fontStream == null) {
                System.err.println("错误: 未找到字体文件 font.ttf，请确保文件位于 src/main/resources/font.ttf");
                return;
            }

            // 创建字体
            Font font = Font.createFont(Font.TRUETYPE_FONT, fontStream);
            fontStream.close();

            // 注册字体到系统
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(font);

            // 创建指定大小的字体并保存
            loadedFont = font.deriveFont(Font.PLAIN, 12f);

            // 设置全局字体
            UIManager.put("Label.font", loadedFont);
            UIManager.put("Button.font", loadedFont);
            UIManager.put("TextField.font", loadedFont);
            UIManager.put("TextArea.font", loadedFont);
            UIManager.put("ComboBox.font", loadedFont);
            UIManager.put("TabbedPane.font", loadedFont);
            UIManager.put("ProgressBar.font", loadedFont);
            UIManager.put("ToolTip.font", loadedFont);
            UIManager.put("Menu.font", loadedFont);
            UIManager.put("MenuItem.font", loadedFont);
            UIManager.put("CheckBox.font", loadedFont);
            UIManager.put("RadioButton.font", loadedFont);
            UIManager.put("List.font", loadedFont);
            UIManager.put("Table.font", loadedFont);
            UIManager.put("Tree.font", loadedFont);

            System.out.println("成功加载字体: " + font.getFontName() + " (大小: " + loadedFont.getSize() + ")");
        } catch (FontFormatException e) {
            System.err.println("字体文件格式错误: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("读取字体文件时出错: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("加载字体时出错: " + e.getMessage());
        }
    }

    /**
     * 获取加载的字体，如果未加载则返回默认字体
     */
    private Font getLoadedFont() {
        if (loadedFont != null) {
            return loadedFont;
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    /**
     * 切换语言
     */
    private void changeLanguage() {
        // 如果正在搜索或暂停，不允许切换语言
        if (isSearchRunning || isSearchPaused || isListSearchRunning || isListSearchPaused) {
            // 恢复原来的选择
            if (languageComboBox != null) {
                if (currentLocale.getLanguage().equals("zh")) {
                    languageComboBox.setSelectedIndex(0);
                } else {
                    languageComboBox.setSelectedIndex(1);
                }
            }
            return;
        }

        if (languageComboBox != null && languageComboBox.getSelectedIndex() < 0) {
            return;
        }

        // 根据下拉框索引设置 Locale（0=中文，1=英文）
        Locale newLocale;
        int langIndex = languageComboBox != null ? languageComboBox.getSelectedIndex() : 0;
        if (langIndex == 0) {
            newLocale = Locale.SIMPLIFIED_CHINESE;
        } else {
            newLocale = Locale.US;
        }

        // 如果语言没有变化，不执行切换
        if (newLocale.equals(currentLocale)) {
            return;
        }

        // 重新加载ResourceBundle
        try {
            currentLocale = newLocale;
            messages = ResourceBundle.getBundle("messages", currentLocale);
        } catch (Exception e) {
            System.err.println("加载语言资源失败: " + e.getMessage());
            return;
        }


        // 更新所有UI文本
        updateUITexts();
    }

    /**
     * 更新所有UI文本
     */
    private void updateUITexts() {
        SwingUtilities.invokeLater(() -> {
            // 更新窗口标题
            setTitle(getString("window.title"));

            // 更新标签页标题
            JTabbedPane tabbedPane = (JTabbedPane) getContentPane().getComponent(0);
            if (tabbedPane != null) {
                tabbedPane.setTitleAt(0, getString("tab.singleSeedSearch"));
                tabbedPane.setTitleAt(1, getString("tab.listSearch"));
            }

            // 更新单种子搜索面板的所有文本
            updateSingleSeedSearchTexts();

            // 更新列表搜索面板的所有文本
            updateListSearchTexts();
        });
    }

    /**
     * 更新单种子搜索面板的文本
     */
    private void updateSingleSeedSearchTexts() {
        // 更新所有标签文本
        if (searchSeedLabel != null) {
            searchSeedLabel.setText(getString("label.seed"));
        }
        if (searchThreadCountLabel != null) {
            searchThreadCountLabel.setText(getString("label.threadCount"));
        }
        if (searchMinSlimeAreaLabel != null) {
            searchMinSlimeAreaLabel.setText(getString("label.minSlimeArea"));
        }
        if (searchMinSlimeRatioLabel != null) {
            searchMinSlimeRatioLabel.setText(getString("label.minSlimeRatio"));
        }
        if (searchMinXLabel != null) {
            searchMinXLabel.setText(getString("label.minX"));
        }
        if (searchMaxXLabel != null) {
            searchMaxXLabel.setText(getString("label.maxX"));
        }
        if (searchMinZLabel != null) {
            searchMinZLabel.setText(getString("label.minZ"));
        }
        if (searchMaxZLabel != null) {
            searchMaxZLabel.setText(getString("label.maxZ"));
        }
        if (searchMcVersionLabel != null) {
            searchMcVersionLabel.setText(getString("label.mcVersion"));
        }
        if (searchBiomeConversionCheckBox != null) {
            searchBiomeConversionCheckBox.setText(getString("label.biomeConversion"));
        }
        if (searchLanguageLabel != null) {
            searchLanguageLabel.setText(getString("label.language"));
        }

        // 更新credit文本
        if (searchCreditLabel != null) {
            searchCreditLabel.setText(buildCreditText());
        }

        // 更新右侧面板边框
        if (searchRightPanel != null) {
            searchRightPanel.setBorder(BorderFactory.createTitledBorder(getString("result.border")));
        }

        // 更新按钮文本
        if (searchStartButton != null) {
            searchStartButton.setText(getString("button.startSearch"));
        }
        if (searchPauseButton != null) {
            searchPauseButton.setText(isSearchPaused ? getString("button.resume") : getString("button.pause"));
        }
        if (searchStopButton != null) {
            searchStopButton.setText(getString("button.stop"));
        }
        if (searchResetButton != null) {
            searchResetButton.setText(getString("button.reset"));
        }
        if (searchResetWorldButton != null) {
            searchResetWorldButton.setText(getString("button.resetWorldBorder"));
        }
        if (searchExportButton != null) {
            searchExportButton.setText(getString("button.export"));
        }
        if (searchSortButton != null) {
            searchSortButton.setText(getString("button.sort"));
        }

        // 更新进度条和标签
        if (searchProgressBar != null && !isSearchRunning) {
            searchProgressBar.setIndeterminate(false);
            searchProgressBar.setString(getString("progress.format", 0, 0, 0.0));
        }
        if (searchElapsedTimeLabel != null && !isSearchRunning) {
            searchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(0)));
        }
        if (searchRemainingTimeLabel != null && !isSearchRunning) {
            searchRemainingTimeLabel.setText(getString("remainingTime.calculating"));
        }
    }

    /**
     * 更新列表搜索面板的文本
     */
    private void updateListSearchTexts() {
        // 更新所有标签文本
        if (listSearchSeedFileTitleLabel != null) {
            listSearchSeedFileTitleLabel.setText(getString("label.seedFile"));
        }
        if (listSearchThreadCountLabel != null) {
            listSearchThreadCountLabel.setText(getString("label.threadCount"));
        }
        if (listSearchMinXLabel != null) {
            listSearchMinXLabel.setText(getString("label.minX"));
        }
        if (listSearchMaxXLabel != null) {
            listSearchMaxXLabel.setText(getString("label.maxX"));
        }
        if (listSearchMinZLabel != null) {
            listSearchMinZLabel.setText(getString("label.minZ"));
        }
        if (listSearchMaxZLabel != null) {
            listSearchMaxZLabel.setText(getString("label.maxZ"));
        }
        if (listSearchMinSlimeAreaLabel != null) {
            listSearchMinSlimeAreaLabel.setText(getString("label.minSlimeArea"));
        }
        if (listSearchMinSlimeRatioLabel != null) {
            listSearchMinSlimeRatioLabel.setText(getString("label.minSlimeRatio"));
        }
        if (listMcVersionLabel != null) {
            listMcVersionLabel.setText(getString("label.mcVersion"));
        }
        if (listBiomeConversionCheckBox != null) {
            listBiomeConversionCheckBox.setText(getString("label.biomeConversion"));
        }

        // 更新按钮文本
        if (listSearchStartButton != null) {
            listSearchStartButton.setText(getString("button.startSearch"));
        }
        if (listSearchPauseButton != null) {
            listSearchPauseButton.setText(isListSearchPaused ? getString("button.resume") : getString("button.pause"));
        }
        if (listSearchStopButton != null) {
            listSearchStopButton.setText(getString("button.stop"));
        }
        if (listSearchResetButton != null) {
            listSearchResetButton.setText(getString("button.resetList"));
        }
        if (listSearchExportButton != null) {
            listSearchExportButton.setText(getString("button.export"));
        }
        if (listSearchExportSeedListButton != null) {
            listSearchExportSeedListButton.setText(getString("button.exportSeedList"));
        }
        if (listSortByAreaButton != null) {
            listSortByAreaButton.setText(getString("button.sortByArea"));
        }
        if (listSortByDistanceButton != null) {
            listSortByDistanceButton.setText(getString("button.sortByDistance"));
        }
        if (listSearchSeedFileButton != null) {
            listSearchSeedFileButton.setText(getString("button.selectFile"));
        }

        // 更新种子文件标签
        if (listSearchSeedFileLabel != null) {
            if (selectedSeedFile != null && selectedSeedFile.exists()) {
                listSearchSeedFileLabel.setText(selectedSeedFile.getName());
            } else {
                listSearchSeedFileLabel.setText(getString("label.noFileSelected"));
            }
        }

        // 更新进度条和标签
        if (listSearchProgressBar != null && !isListSearchRunning) {
            listSearchProgressBar.setString(getString("progress.total", 0, 0, 0.0));
        }
        if (listSearchElapsedTimeLabel != null && !isListSearchRunning) {
            listSearchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(0)));
        }
        if (listSearchRemainingTimeLabel != null && !isListSearchRunning) {
            listSearchRemainingTimeLabel.setText(getString("remainingTime.calculating"));
        }
        if (listSearchCurrentSeedProgressLabel != null && !isListSearchRunning) {
            listSearchCurrentSeedProgressLabel.setText(getString("currentSeed.default"));
        }

        // 更新credit文本
        if (listSearchCreditLabel != null) {
            listSearchCreditLabel.setText(buildCreditText());
        }

        // 更新右侧面板边框
        if (listSearchRightPanel != null) {
            listSearchRightPanel.setBorder(BorderFactory.createTitledBorder(getString("result.border")));
        }
    }

    // ========== 从种子列表搜索相关方法 ==========

    // 添加搜索参数监听器，检测参数变化（不包括线程数）
    private void addListSearchParameterListeners() {
        listMinSlimeAreaField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }
        });
        listMinSlimeRatioField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }
        });

        // 坐标变化监听
        listMinXField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }
        });
        listMaxXField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }
        });
        listMinZField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }
        });
        listMaxZField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                checkListSearchParameterChange();
            }
        });
    }

    // 检查搜索参数是否变化（除了线程数）
    private void checkListSearchParameterChange() {
        if (isListSearchRunning && !isListSearchPaused) {
            return; // 运行中且未暂停，不检查
        }

        if (!isListSearchPaused) {
            return; // 未暂停，不检查
        }

        try {
            if (selectedSeedFile == null) return;
            int minArea = parseMinSlimeArea(listMinSlimeAreaField);
            if (minArea < 0) minArea = 0;
            int minX = Integer.parseInt(listMinXField.getText().trim());
            int maxX = Integer.parseInt(listMaxXField.getText().trim());
            int minZ = Integer.parseInt(listMinZField.getText().trim());
            int maxZ = Integer.parseInt(listMaxZField.getText().trim());

            if (minX != lastListSearchMinX || maxX != lastListSearchMaxX || minZ != lastListSearchMinZ || maxZ != lastListSearchMaxZ || minArea != lastListSearchMinArea) {
                if (listSlimeRunner != null) listSlimeRunner.stop();
                isListSearchRunning = false;
                isListSearchPaused = false;
                listSearchStartButton.setEnabled(true);
                listSearchPauseButton.setEnabled(false);
                listSearchPauseButton.setText(getString("button.pause"));
                listSearchStopButton.setEnabled(false);
                listSearchResetButton.setEnabled(true);
                listSearchSeedFileButton.setEnabled(true);
                listSearchThreadCountField.setEnabled(true);
                listMinSlimeAreaField.setEnabled(true);
                listMinSlimeRatioField.setEnabled(true);
                listMinXField.setEnabled(true);
                listMaxXField.setEnabled(true);
                listMinZField.setEnabled(true);
                listMaxZField.setEnabled(true);
                if (listVersionComboBox != null) listVersionComboBox.setEnabled(true);
                if (listBiomeConversionCheckBox != null) listBiomeConversionCheckBox.setEnabled(true);
                listSearchResultToken.incrementAndGet();
                listSearchResultArea.setText("");
                listSearchProgressBar.setValue(0);
                listSearchProgressBar.setString(getString("progress.total", 0, 0, 0.0));
                listSearchCurrentSeedProgressLabel.setText(getString("currentSeed.default"));
                listSearchRemainingTimeLabel.setText(getString("remainingTime.reset"));
            }
        } catch (NumberFormatException e) {
            // ignore
        }
    }

    // 选择种子文件
    private void selectSeedFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(getString("dialog.selectSeedFile"));
        fileChooser.setFileFilter(new FileNameExtensionFilter(getString("dialog.textFiles"), "txt"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedSeedFile = fileChooser.getSelectedFile();
            listSearchSeedFileLabel.setText(selectedSeedFile.getName());
        }
    }

    // 读取种子列表
    private List<Long> readSeedList(File file) throws IOException {
        List<Long> seeds = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    long seed = Long.parseLong(line);
                    seeds.add(seed);
                } catch (NumberFormatException e) {
                    // 跳过无效的种子行
                    System.err.println("跳过无效的种子行: " + line);
                }
            }
        }
        return seeds;
    }

    // 列表搜索（河流多阶段，多种子顺序执行）
    private void startListSearch() {
        if (isListSearchRunning && isListSearchPaused) {
            if (listSlimeRunner != null) {
                listSlimeRunner.resume();
                isListSearchPaused = false;
                listSearchPauseButton.setText(getString("button.pause"));
            }
            return;
        }

        try {
            // 验证种子文件
            if (selectedSeedFile == null || !selectedSeedFile.exists()) {
                JOptionPane.showMessageDialog(this, getString("error.seedFileRequired"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 读取种子列表
            List<Long> seeds;
            try {
                seeds = readSeedList(selectedSeedFile);
                if (seeds.isEmpty()) {
                    JOptionPane.showMessageDialog(this, getString("error.seedFileEmpty"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                // 在导入时设置进度条为 0/种子数
                final long totalSeeds = seeds.size();
                SwingUtilities.invokeLater(() -> {
                    listSearchProgressBar.setMaximum((int) totalSeeds);
                    listSearchProgressBar.setValue(0);
                    listSearchProgressBar.setString(getString("progress.seedsDone", 0, totalSeeds));
                    listSearchCurrentSeedProgressLabel.setText(getString("currentSeed.default"));
                });
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, getString("error.seedFileReadFailed", e.getMessage()), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 验证线程数
            String threadText = listSearchThreadCountField.getText().trim();
            double threadDouble;
            try {
                threadDouble = Double.parseDouble(threadText);
                if (threadDouble != Math.floor(threadDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.threadCountMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.threadCountFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            int threadCount = (int) threadDouble;
            if (threadCount < 1) {
                JOptionPane.showMessageDialog(this, getString("error.threadCountRequired"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 检查线程数是否超过CPU核数
            int cpuThreads = Runtime.getRuntime().availableProcessors();
            if (threadCount > cpuThreads) {
                int result = JOptionPane.showConfirmDialog(
                        this,
                        getString("error.threadCountExceedsCPU", cpuThreads, cpuThreads),
                        getString("prompt.adjustThreadCount"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );
                if (result == JOptionPane.YES_OPTION) {
                    threadCount = cpuThreads;
                    listSearchThreadCountField.setText(String.valueOf(cpuThreads));
                } else {
                    return;
                }
            }

            int minArea = parseMinSlimeArea(listMinSlimeAreaField);
            if (minArea < 0) {
                String msg = getString("error.minSlimeAreaInvalid");
                try {
                    int raw = Integer.parseInt(listMinSlimeAreaField.getText().trim());
                    if (raw > MAX_SLIME_AREA_UI) {
                        msg = getString("error.minSlimeAreaTooLarge");
                    }
                } catch (NumberFormatException ignored) {
                }
                JOptionPane.showMessageDialog(this, msg, getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 验证XZ坐标
            String minXText = listMinXField.getText().trim();
            String maxXText = listMaxXField.getText().trim();
            String minZText = listMinZField.getText().trim();
            String maxZText = listMaxZField.getText().trim();

            // 检查是否为整数
            double minXDouble, maxXDouble, minZDouble, maxZDouble;
            try {
                minXDouble = Double.parseDouble(minXText);
                if (minXDouble != Math.floor(minXDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.minXMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    listMinXField.setText(String.valueOf(DEFAULT_LIST_MIN_X));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.minXFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                listMinXField.setText(String.valueOf(DEFAULT_LIST_MIN_X));
                return;
            }

            try {
                maxXDouble = Double.parseDouble(maxXText);
                if (maxXDouble != Math.floor(maxXDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.maxXMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    listMaxXField.setText(String.valueOf(DEFAULT_LIST_MAX_X));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.maxXFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                listMaxXField.setText(String.valueOf(DEFAULT_LIST_MAX_X));
                return;
            }

            try {
                minZDouble = Double.parseDouble(minZText);
                if (minZDouble != Math.floor(minZDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.minZMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    listMinZField.setText(String.valueOf(DEFAULT_LIST_MIN_Z));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.minZFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                listMinZField.setText(String.valueOf(DEFAULT_LIST_MIN_Z));
                return;
            }

            try {
                maxZDouble = Double.parseDouble(maxZText);
                if (maxZDouble != Math.floor(maxZDouble)) {
                    JOptionPane.showMessageDialog(this, getString("error.maxZMustBeInteger"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                    listMaxZField.setText(String.valueOf(DEFAULT_LIST_MAX_Z));
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, getString("error.maxZFormatError"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                listMaxZField.setText(String.valueOf(DEFAULT_LIST_MAX_Z));
                return;
            }

            int minX = (int) minXDouble;
            int maxX = (int) maxXDouble;
            int minZ = (int) minZDouble;
            int maxZ = (int) maxZDouble;

            if (minX >= maxX) {
                JOptionPane.showMessageDialog(this, getString("error.minXGreaterThanMaxX"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (minZ >= maxZ) {
                JOptionPane.showMessageDialog(this, getString("error.minZGreaterThanMaxZ"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            long listSearchArea = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
            if (listSearchArea > SlimeSearchRunner.MAX_SEARCH_AREA_BLOCKS) {
                JOptionPane.showMessageDialog(this, getString("error.searchAreaTooLarge"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            int listSearchMinX = minX - SlimeSearchRunner.RING_OUTER;
            int listSearchMaxX = maxX + SlimeSearchRunner.RING_OUTER;
            int listSearchMinZ = minZ - SlimeSearchRunner.RING_OUTER;
            int listSearchMaxZ = maxZ + SlimeSearchRunner.RING_OUTER;
            int listBaseX1 = (listSearchMinX / SlimeSearchRunner.PHASE1_GRID_STEP) * SlimeSearchRunner.PHASE1_GRID_STEP;
            int listBaseZ1 = (listSearchMinZ / SlimeSearchRunner.PHASE1_GRID_STEP) * SlimeSearchRunner.PHASE1_GRID_STEP;
            long listWidthSteps1 = (listSearchMaxX - listBaseX1) / SlimeSearchRunner.PHASE1_GRID_STEP + 1;
            long listHeightSteps1 = (listSearchMaxZ - listBaseZ1) / SlimeSearchRunner.PHASE1_GRID_STEP + 1;
            long listTotalGrid = listWidthSteps1 * listHeightSteps1;
            if (listTotalGrid > SlimeSearchRunner.MAX_SEARCH_AREA_BLOCKS) {
                JOptionPane.showMessageDialog(this, getString("error.searchAreaTooLarge"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            lastListSearchMinX = minX;
            lastListSearchMaxX = maxX;
            lastListSearchMinZ = minZ;
            lastListSearchMaxZ = maxZ;
            lastListSearchMinArea = minArea;
            lastListSearchThreadCount = threadCount;

            isListSearchRunning = true;
            isListSearchPaused = false;
            listSearchStartButton.setEnabled(false);
            listSearchPauseButton.setEnabled(true);
            listSearchPauseButton.setText(getString("button.pause"));
            listSearchStopButton.setEnabled(true);
            listSearchResetButton.setEnabled(false);
            listSearchSeedFileButton.setEnabled(false);
            listSearchThreadCountField.setEnabled(false);
            listMinSlimeAreaField.setEnabled(false);
            listMinSlimeRatioField.setEnabled(false);
            listMinXField.setEnabled(false);
            listMaxXField.setEnabled(false);
            listMinZField.setEnabled(false);
            listMaxZField.setEnabled(false);
            if (listVersionComboBox != null) listVersionComboBox.setEnabled(false);
            if (listBiomeConversionCheckBox != null) listBiomeConversionCheckBox.setEnabled(false);
            final long listResultToken = listSearchResultToken.incrementAndGet();
            listSearchResultArea.setText("");
            listSearchProgressBar.setMaximum((int) seeds.size());
            listSearchProgressBar.setValue(0);
            listSearchProgressBar.setString(getString("progress.seedsDone", 0, seeds.size()));
            listSearchCurrentSeedProgressLabel.setText(getString("currentSeed.default"));
            listSearchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(0)));
            listSearchRemainingTimeLabel.setText(getString("remainingTime.calculating"));

            seedResults.clear();

            final int finalMinArea = minArea;
            final int finalThreadCount = threadCount;
            final int finalMcVersion = resolveMcVersion(listVersionComboBox);
            final int finalVersionTier = resolveVersionTier(listVersionComboBox);
            final boolean finalBiomeConversion = listBiomeConversionCheckBox != null && listBiomeConversionCheckBox.isSelected();
            final String finalConvertedAreaLabel = getString("result.convertedArea");
            final long totalSeeds = seeds.size();
            final long startTime = System.currentTimeMillis();
            // 暂停时间跟踪
            final long[] pausedTimeRef = {0}; // 累计暂停时间
            final long[] pauseStartTimeRef = {0}; // 暂停开始时间

            // 启动进度监控线程，定期更新时间显示
            Thread progressMonitorThread = new Thread(() -> {
                while (isListSearchRunning) {
                    try {
                        Thread.sleep(100); // 每100ms更新一次

                        // 更新暂停时间跟踪
                        if (isListSearchPaused) {
                            // 记录暂停开始时间
                            if (pauseStartTimeRef[0] == 0) {
                                pauseStartTimeRef[0] = System.currentTimeMillis();
                            }
                        } else {
                            // 如果从暂停恢复，累计暂停时间
                            if (pauseStartTimeRef[0] > 0) {
                                pausedTimeRef[0] += System.currentTimeMillis() - pauseStartTimeRef[0];
                                pauseStartTimeRef[0] = 0;
                            }
                        }

                        // 计算实际已用时间（排除暂停时间）
                        long currentPausedTime = pausedTimeRef[0];
                        if (pauseStartTimeRef[0] > 0) {
                            // 如果当前正在暂停，也要计入当前暂停时间
                            currentPausedTime += System.currentTimeMillis() - pauseStartTimeRef[0];
                        }
                        final long elapsedMs = System.currentTimeMillis() - startTime - currentPausedTime;

                        // 获取当前完成的种子数（需要从UI获取或使用共享变量）
                        SwingUtilities.invokeLater(() -> {
                            if (listResultToken != listSearchResultToken.get()) {
                                return;
                            }
                            int currentProgress = listSearchProgressBar.getValue();
                            if (currentProgress > 0 && currentProgress < totalSeeds) {
                                if (isListSearchPaused) {
                                    listSearchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(elapsedMs)));
                                    listSearchRemainingTimeLabel.setText(getString("remainingTime.paused"));
                                    return;
                                }
                                final long remainingMs = elapsedMs > 0 ? (elapsedMs * (totalSeeds - currentProgress) / currentProgress) : 0;
                                listSearchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(elapsedMs)));
                                if (remainingMs > 0) {
                                    listSearchRemainingTimeLabel.setText(getString("remainingTime", formatTime(remainingMs)));
                                } else {
                                    listSearchRemainingTimeLabel.setText(getString("remainingTime.calculating"));
                                }
                            }
                        });
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            progressMonitorThread.setDaemon(true);
            progressMonitorThread.start();

            new Thread(() -> {
                final int[] processedSeedsRef = {0};
                final long[] lastProgressUpdate = {0};
                final long PROGRESS_INTERVAL_MS = 100;

                for (int seedIndex = 0; seedIndex < seeds.size(); seedIndex++) {
                    if (!isListSearchRunning) break;

                    final long seed = seeds.get(seedIndex);
                    final int currentSeedIndex = seedIndex + 1;
                    lastProgressUpdate[0] = 0;
                    seedResults.put(seed, new ArrayList<>());

                    listSlimeRunner = new SlimeSearchRunner();
                    final long currentSeed = seed;
                    Consumer<String> seedResultCallback = result -> {
                        if (result == null || result.isEmpty()) {
                            return;
                        }
                        List<String> lines = seedResults.get(currentSeed);
                        for (String line : result.split("\n", -1)) {
                            if (!line.isEmpty()) {
                                lines.add(line);
                            }
                        }
                    };
                    Consumer<SlimeSearchRunner.ProgressInfo> seedProgressCallback = info -> {
                        if (info.done()) return;
                        if (listResultToken != listSearchResultToken.get()) return;
                        if (isListSearchPaused && info.nativePauseSettled()) {
                            return;
                        }
                        if (info.refiningResults()) {
                            lastProgressUpdate[0] = System.currentTimeMillis();
                            SwingUtilities.invokeLater(() -> {
                                if (listResultToken != listSearchResultToken.get()) {
                                    return;
                                }
                                if (isListSearchRunning) {
                                    listSearchCurrentSeedProgressLabel.setText(getString("progress.consolidating"));
                                }
                            });
                            return;
                        }
                        long now = System.currentTimeMillis();
                        if (now - lastProgressUpdate[0] < PROGRESS_INTERVAL_MS) return;
                        lastProgressUpdate[0] = now;
                        final long proc = info.processed();
                        final long tot = info.total();
                        final double pct = tot > 0 ? Math.min(100.0, proc * 100.0 / tot) : 0;
                        SwingUtilities.invokeLater(() -> {
                            if (listResultToken != listSearchResultToken.get()) {
                                return;
                            }
                            if (isListSearchRunning) {
                                listSearchCurrentSeedProgressLabel.setText(
                                        getString("currentSeed", currentSeedIndex, totalSeeds, proc, tot, pct));
                            }
                        });
                    };

                    listSlimeRunner.runSlimeSearchBlocking(seed, minX, maxX, minZ, maxZ, finalMinArea,
                        finalMcVersion, finalVersionTier, finalBiomeConversion, finalThreadCount,
                        finalConvertedAreaLabel, seedProgressCallback, seedResultCallback);

                    while (listSlimeRunner.isRunning() && isListSearchRunning) {
                        while (isListSearchPaused && isListSearchRunning) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        if (!isListSearchRunning) break;
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    if (!isListSearchRunning) break;

                    processedSeedsRef[0]++;
                    final int completedSeeds = processedSeedsRef[0];
                    SwingUtilities.invokeLater(() -> {
                        if (listResultToken != listSearchResultToken.get()) {
                            return;
                        }
                        listSearchProgressBar.setValue(completedSeeds);
                        listSearchProgressBar.setString(getString("progress.seedsDone", completedSeeds, totalSeeds));
                    });

                    List<String> results = seedResults.get(seed);
                    if (!results.isEmpty()) {
                        SwingUtilities.invokeLater(() -> {
                            if (listResultToken != listSearchResultToken.get()) {
                                return;
                            }
//                            if (listSearchResultArea.getDocument().getLength() > 0) {
//                                listSearchResultArea.append("\n");
//                            }
                            listSearchResultArea.append(seed + "\n");
                            for (String result : results) {
                                listSearchResultArea.append(result + "\n");
                            }
                            listSearchResultArea.setCaretPosition(listSearchResultArea.getDocument().getLength());
                        });
                    }
                }

                // 若已停止：保持界面停留在“正常显示”的最后一帧，不进入“已完成”状态
                if (!isListSearchRunning) {
                    return;
                }

                // 所有种子处理完成
                // 计算最终已用时间（排除暂停时间）
                long finalPausedTime = pausedTimeRef[0];
                if (pauseStartTimeRef[0] > 0) {
                    // 如果结束时还在暂停，也要计入当前暂停时间
                    finalPausedTime += System.currentTimeMillis() - pauseStartTimeRef[0];
                }
                final long finalElapsedMs = System.currentTimeMillis() - startTime - finalPausedTime;
                SwingUtilities.invokeLater(() -> {
                    if (listResultToken != listSearchResultToken.get()) {
                        return;
                    }
                    listSearchResultToken.incrementAndGet();
                    isListSearchRunning = false;
                    isListSearchPaused = false;
                    listSearchStartButton.setEnabled(true);
                    listSearchPauseButton.setEnabled(false);
                    listSearchPauseButton.setText(getString("button.pause"));
                    listSearchStopButton.setEnabled(false);
                    listSearchResetButton.setEnabled(true);
                    listSearchSeedFileButton.setEnabled(true);
                    listSearchThreadCountField.setEnabled(true);
                    listMinSlimeAreaField.setEnabled(true);
                    listMinSlimeRatioField.setEnabled(true);
                    listMinXField.setEnabled(true);
                    listMaxXField.setEnabled(true);
                    listMinZField.setEnabled(true);
                    listMaxZField.setEnabled(true);
                if (listVersionComboBox != null) listVersionComboBox.setEnabled(true);
                if (listBiomeConversionCheckBox != null) listBiomeConversionCheckBox.setEnabled(true);
                    listSearchProgressBar.setValue((int) totalSeeds);
                    listSearchProgressBar.setString(getString("progress.seedsDone", totalSeeds, totalSeeds));
                    listSearchCurrentSeedProgressLabel.setText(getString("currentSeed.complete"));
                    listSearchElapsedTimeLabel.setText(getString("elapsedTime", formatTime(finalElapsedMs)));
                    listSearchRemainingTimeLabel.setText(getString("remainingTime.completed"));
                });
            }).start();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, getString("error.invalidNumber"), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleListSearchPause() {
        if (listSlimeRunner == null || !isListSearchRunning) return;
        if (isListSearchPaused) {
            listSlimeRunner.resume();
            isListSearchPaused = false;
            listSearchPauseButton.setText(getString("button.pause"));
        } else {
            listSlimeRunner.pause();
            isListSearchPaused = true;
            listSearchPauseButton.setText(getString("button.resume"));
        }
        listSearchThreadCountField.setEnabled(false);
    }

    private void stopListSearch() {
        if (listSlimeRunner != null) {
            listSlimeRunner.stop();
        }
        listSearchResultToken.incrementAndGet();
        isListSearchRunning = false;
        isListSearchPaused = false;
        listSearchStartButton.setEnabled(true);
        listSearchPauseButton.setEnabled(false);
        listSearchPauseButton.setText(getString("button.pause"));
        listSearchStopButton.setEnabled(false);
        listSearchResetButton.setEnabled(true);
        listSearchSeedFileButton.setEnabled(true);
        listSearchThreadCountField.setEnabled(true);
        listMinSlimeAreaField.setEnabled(true);
        listMinSlimeRatioField.setEnabled(true);
        listMinXField.setEnabled(true);
        listMaxXField.setEnabled(true);
        listMinZField.setEnabled(true);
        listMaxZField.setEnabled(true);
    }

    private void resetListSearchToDefaults() {
        listMinXField.setText(String.valueOf(DEFAULT_LIST_MIN_X));
        listMaxXField.setText(String.valueOf(DEFAULT_LIST_MAX_X));
        listMinZField.setText(String.valueOf(DEFAULT_LIST_MIN_Z));
        listMaxZField.setText(String.valueOf(DEFAULT_LIST_MAX_Z));
    }

    // 解析结果文本，返回种子与河流结果行（x z area ratio%）的映射
    private Map<Long, List<ListSlimeResult>> parseListResults() {
        Map<Long, List<ListSlimeResult>> parsedResults = new HashMap<>();
        String text = listSearchResultArea.getText().trim();
        if (text.isEmpty()) return parsedResults;
        String[] lines = text.split("\n");
        Long currentSeed = null;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                long seed = Long.parseLong(line);
                currentSeed = seed;
                parsedResults.putIfAbsent(seed, new ArrayList<>());
            } catch (NumberFormatException e) {
                if (currentSeed != null && line.startsWith("/tp ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 5) {
                        try {
                            int x = Integer.parseInt(parts[1]);
                            int z = Integer.parseInt(parts[3]);
                            int area = parseListSortAreaFromResultLine(line);
                            if (area >= 0) {
                                parsedResults.get(currentSeed).add(new ListSlimeResult(x, z, area, line));
                            }
                        } catch (NumberFormatException ex) { /* skip */ }
                    }
                }
            }
        }
        return parsedResults;
    }

    private static class ListSlimeResult {
        final int x, z, area;
        final String originalLine;

        ListSlimeResult(int x, int z, int area, String originalLine) {
            this.x = x;
            this.z = z;
            this.area = area;
            this.originalLine = originalLine;
        }

        double distanceSquared() {
            return (double) x * x + (double) z * z;
        }
    }

    private void sortListByArea() {
        Map<Long, List<ListSlimeResult>> parsed = parseListResults();
        if (parsed.isEmpty()) return;
        List<Map.Entry<Long, Integer>> byMaxArea = new ArrayList<>();
        for (Map.Entry<Long, List<ListSlimeResult>> e : parsed.entrySet()) {
            int maxArea = e.getValue().stream().mapToInt(r -> r.area).max().orElse(0);
            byMaxArea.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), maxArea));
        }
        byMaxArea.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Long, Integer> e : byMaxArea) {
            sb.append(e.getKey()).append("\n");
            for (ListSlimeResult r : parsed.get(e.getKey())) sb.append(r.originalLine).append("\n");
        }
        listSearchResultArea.setText(sb.toString());
    }

    private void sortListByDistance() {
        Map<Long, List<ListSlimeResult>> parsed = parseListResults();
        if (parsed.isEmpty()) return;
        List<Map.Entry<Long, Double>> byMinDist = new ArrayList<>();
        for (Map.Entry<Long, List<ListSlimeResult>> e : parsed.entrySet()) {
            double minD = e.getValue().stream().mapToDouble(ListSlimeResult::distanceSquared).min().orElse(0);
            byMinDist.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), minD));
        }
        byMinDist.sort((a, b) -> Double.compare(a.getValue(), b.getValue()));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Long, Double> e : byMinDist) {
            sb.append(e.getKey()).append("\n");
            for (ListSlimeResult r : parsed.get(e.getKey())) sb.append(r.originalLine).append("\n");
        }
        listSearchResultArea.setText(sb.toString());
    }

    private void exportListSearchResults() {
        if (listSearchResultArea.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, getString("error.noResultsToExport"), getString("prompt.information"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(getString("dialog.exportResults"));
        fileChooser.setFileFilter(new FileNameExtensionFilter(getString("dialog.textFiles"), "txt"));
        fileChooser.setSelectedFile(new File(getString("file.searchOutput")));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.print(listSearchResultArea.getText());
                JOptionPane.showMessageDialog(this, getString("success.export"), getString("prompt.success"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, getString("error.exportFailed", e.getMessage()), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // 导出种子列表（不含/tp坐标）
    private void exportSeedList() {
        if (listSearchResultArea.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, getString("error.noResultsToExport"), getString("prompt.information"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 解析结果，提取所有种子
        String text = listSearchResultArea.getText().trim();
        String[] lines = text.split("\n");
        List<Long> seeds = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            // 跳过/tp开头的坐标行
            if (line.startsWith("/tp ")) {
                continue;
            }
            // 尝试解析为种子
            try {
                long seed = Long.parseLong(line);
                if (!seeds.contains(seed)) {
                    seeds.add(seed);
                }
            } catch (NumberFormatException e) {
                // 忽略无效行
            }
        }

        if (seeds.isEmpty()) {
            JOptionPane.showMessageDialog(this, getString("error.noSeedsFound"), getString("prompt.information"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(getString("dialog.exportSeedList"));
        fileChooser.setFileFilter(new FileNameExtensionFilter(getString("dialog.textFiles"), "txt"));
        fileChooser.setSelectedFile(new File(getString("file.seedList")));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                for (Long seed : seeds) {
                    writer.println(seed);
                }
                JOptionPane.showMessageDialog(this, getString("success.exportSeeds", seeds.size()), getString("prompt.success"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, getString("error.exportFailed", e.getMessage()), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private int resolveMcVersion(JComboBox<String> combo) {
        if (combo == null) {
            return SlimeSearchRunner.MC_NEWEST;
        }
        return switch (combo.getSelectedIndex()) {
            case 0 -> SlimeSearchRunner.MC_1_18;
            case 1 -> SlimeSearchRunner.MC_26_1;
            default -> SlimeSearchRunner.MC_NEWEST;
        };
    }

    private int resolveVersionTier(JComboBox<String> combo) {
        if (combo == null) {
            return SlimeFinderBridge.VERSION_TIER_26_2_PLUS;
        }
        int idx = combo.getSelectedIndex();
        if (idx <= 0) {
            return SlimeFinderBridge.VERSION_TIER_1_18;
        }
        if (idx == 1) {
            return SlimeFinderBridge.VERSION_TIER_1_19_26_1;
        }
        return SlimeFinderBridge.VERSION_TIER_26_2_PLUS;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new SlimeFinderFrame().setVisible(true);
        });
    }
}

