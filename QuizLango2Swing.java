import java.awt.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

public class QuizLango2Swing {

    // ---------- DB CONFIG ----------
    private static final String DB_URL  = "jdbc:mysql://localhost:3306/quizlango_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    private static Connection conn = null;

    // ---------- GAME STATE ----------
    static Queue<Question> questionQueue = new LinkedList<>();
    static Stack<String>   answerHistory = new Stack<>();
    static String currentPlayer = "";
    static int score = 0;
    static int currentQNum = 0;
    static Question currentQuestion = null;

    // ---------- COLORS ----------
    static final Color BG_DARK      = new Color(15, 15, 35);
    static final Color BG_MID       = new Color(30, 25, 60);
    static final Color ACCENT       = new Color(255, 200, 60);
    static final Color ACCENT_2     = new Color(120, 200, 255);
    static final Color CORRECT_GREEN= new Color(80, 220, 120);
    static final Color WRONG_RED    = new Color(255, 90, 90);
    static final Color CARD_BG      = new Color(40, 38, 80);
    static final Color TEXT_LIGHT   = new Color(240, 240, 255);

    // ---------- FONTS ----------
    static Font titleFont  = new Font("Showcard Gothic", Font.BOLD, 42);
    static Font headerFont = new Font("Trebuchet MS", Font.BOLD, 24);
    static Font bodyFont   = new Font("Segoe UI", Font.PLAIN, 18);
    static Font buttonFont = new Font("Trebuchet MS", Font.BOLD, 18);
    static Font smallFont  = new Font("Segoe UI", Font.PLAIN, 14);

    // ---------- FRAME ----------
    static JFrame frame;
    static JPanel mainPanel;

    // ============================================================
    // DTOs
    // ============================================================
    static class Player {
        String name;
        int score;
        Date datePlayedDate;

        Player(String n, int s) {
            name = n;
            score = s;
            datePlayedDate = new Date();
        }
    }

    static class Question {
        String questionText;
        String[] options;
        int correctAnswer; // 1-4
        String explanation;

        Question(String q, String[] o, int c, String e) {
            questionText = q;
            options = o;
            correctAnswer = c;
            explanation = e;
        }
    }

    // ============================================================
    // MAIN
    // ============================================================
    public static void main(String[] args) {
        // Ensure the DB connection closes on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                    System.out.println("🔌 Shutdown: DB connection closed.");
                }
            } catch (SQLException ignored) { }
        }));

        SwingUtilities.invokeLater(() -> {
            initDatabase();
            buildFrame();
            showWelcomeScreen();
        });
    }

    // ============================================================
    // DATABASE CORE
    // ============================================================
    static Connection getConnection() throws SQLException {
        if (conn == null || conn.isClosed()) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                System.err.println("❌❌ FATAL: MySQL JDBC Driver NOT found on classpath!");
                System.err.println("    → Put mysql-connector-j-x.x.x.jar in ./lib/");
                System.err.println("    → Compile: javac -cp \"lib/*\" QuizLango2Swing.java");
                System.err.println("    → Run:     java  -cp \".;lib/*\" QuizLango2Swing");
                throw new SQLException("JDBC driver missing", e);
            }
            try {
                conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                conn.setAutoCommit(true);
                System.out.println("✅ DB connected: " + DB_URL + " as '" + DB_USER + "'");
            } catch (SQLException e) {
                System.err.println("❌❌ FATAL: Cannot connect to MySQL!");
                System.err.println("    URL:    " + DB_URL);
                System.err.println("    User:   " + DB_USER);
                System.err.println("    Reason: " + e.getMessage());
                String m = e.getMessage() == null ? "" : e.getMessage();
                if (m.contains("Access denied")) {
                    System.err.println("    → Wrong password. Edit DB_PASS in the code.");
                } else if (m.contains("Communications link failure")) {
                    System.err.println("    → MySQL is not running! Start it in XAMPP.");
                } else if (m.contains("Unknown database")) {
                    System.err.println("    → Database quizlango_db missing.");
                }
                throw e;
            }
        }
        return conn;
    }

    static void closeConnection() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                System.out.println("🔌 Database connection closed.");
            }
        } catch (SQLException ignored) { }
    }

    static void initDatabase() {
        // 1) Create DB if missing (fresh connection, don't touch global conn)
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String serverUrl = "jdbc:mysql://localhost:3306/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
            try (Connection c = DriverManager.getConnection(serverUrl, DB_USER, DB_PASS);
                 Statement st = c.createStatement()) {
                st.executeUpdate("CREATE DATABASE IF NOT EXISTS quizlango_db");
                System.out.println("✅ Database 'quizlango_db' verified/created.");
            }
        } catch (Exception e) {
            System.err.println("❌❌ Cannot create database: " + e.getMessage());
            e.printStackTrace();
        }

        // 2) Create tables
        String createPlayers = "CREATE TABLE IF NOT EXISTS players (" +
                "player_id INT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(100) NOT NULL," +
                "score INT NOT NULL DEFAULT 0," +
                "date_played DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)";

        String createAnswers = "CREATE TABLE IF NOT EXISTS answer_history (" +
                "answer_id INT AUTO_INCREMENT PRIMARY KEY," +
                "player_name VARCHAR(100) NOT NULL," +
                "game_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "question_no INT NOT NULL," +
                "question_text VARCHAR(255)," +
                "answer_given CHAR(1)," +
                "correct_answer CHAR(1)," +
                "is_correct BOOLEAN)";

        try (Statement st = getConnection().createStatement()) {
            st.executeUpdate(createPlayers);
            st.executeUpdate(createAnswers);
            System.out.println("✅ Tables 'players' and 'answer_history' ready.");
        } catch (SQLException e) {
            System.err.println("❌❌ Failed to create tables: " + e.getMessage());
            e.printStackTrace();
        }

        // 3) Verify with a SELECT
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM players")) {
            if (rs.next()) {
                System.out.println("✅ 'players' table verified — current rows: " + rs.getInt(1));
            }
        } catch (SQLException e) {
            System.err.println("❌❌ Could not SELECT from 'players': " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void savePlayer(String name, int score) {
        String cleanName = (name == null || name.trim().isEmpty()) ? "Player1" : name.trim();
        if (cleanName.length() > 100) cleanName = cleanName.substring(0, 100);

        System.out.println("💾 savePlayer called: name='" + cleanName + "' score=" + score);

        String sql = "INSERT INTO players (name, score, date_played) VALUES (?, ?, NOW())";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, cleanName);
            ps.setInt(2, score);
            int rows = ps.executeUpdate();
            if (rows == 1) {
                System.out.println("✅ savePlayer SUCCESS: 1 row inserted for '" + cleanName + "'");
            } else {
                System.err.println("⚠️ savePlayer: executeUpdate returned " + rows);
            }
        } catch (SQLException e) {
            System.err.println("❌❌ savePlayer FAILED for '" + cleanName + "'");
            System.err.println("    SQL: " + sql);
            System.err.println("    Error: " + e.getMessage());
            System.err.println("    SQLState: " + e.getSQLState());
            System.err.println("    ErrorCode: " + e.getErrorCode());
            e.printStackTrace();
        }
    }

    static void saveAnswer(String playerName, int qNo, String qText,
                           char given, char correct, boolean isCorrect) {
        String cleanName = (playerName == null || playerName.trim().isEmpty())
                ? "Player1" : playerName.trim();
        if (cleanName.length() > 100) cleanName = cleanName.substring(0, 100);

        String cleanQ = qText == null ? "" : qText;
        if (cleanQ.length() > 250) cleanQ = cleanQ.substring(0, 250);

        System.out.println("💾 saveAnswer Q" + qNo + ": player='" + cleanName
                + "' given=" + given + " correct=" + correct + " isCorrect=" + isCorrect);

        String sql = "INSERT INTO answer_history (player_name, question_no, question_text, " +
                "answer_given, correct_answer, is_correct) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, cleanName);
            ps.setInt(2, qNo);
            ps.setString(3, cleanQ);
            ps.setString(4, String.valueOf(given));
            ps.setString(5, String.valueOf(correct));
            ps.setBoolean(6, isCorrect);
            int rows = ps.executeUpdate();
            if (rows == 1) {
                System.out.println("✅ saveAnswer Q" + qNo + " SUCCESS");
            } else {
                System.err.println("⚠️ saveAnswer Q" + qNo + " returned " + rows);
            }
        } catch (SQLException e) {
            System.err.println("❌❌ saveAnswer Q" + qNo + " FAILED");
            System.err.println("    Error: " + e.getMessage());
            System.err.println("    SQLState: " + e.getSQLState());
            e.printStackTrace();
        }
    }

    static List<Object[]> getLeaderboardFromDB() {
        List<Object[]> rows = new ArrayList<>();
        String sql = "SELECT name, score, date_played FROM players " +
                     "ORDER BY score DESC, date_played ASC";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new Object[]{
                        rs.getString("name"),
                        rs.getInt("score"),
                        rs.getTimestamp("date_played")
                });
            }
        } catch (SQLException e) {
            System.err.println("❌ Failed to load leaderboard: " + e.getMessage());
            e.printStackTrace();
        }
        return rows;
    }

    static List<String> getAnswerHistoryFromDB(String playerName) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT question_no, answer_given, correct_answer, is_correct " +
                     "FROM answer_history WHERE player_name = ? " +
                     "ORDER BY answer_id DESC LIMIT 10";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add("Q" + rs.getInt("question_no")
                            + "   |   You: " + rs.getString("answer_given")
                            + "   |   Correct: " + rs.getString("correct_answer")
                            + (rs.getBoolean("is_correct") ? "   ✅" : "   ❌"));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Failed to load history: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    // ============================================================
    // UI HELPERS
    // ============================================================
    static void buildFrame() {
        frame = new JFrame("QuizLango2 — Mwesit Quiz Game");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 720);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        mainPanel = new GradientPanel();
        mainPanel.setLayout(new BorderLayout());
        frame.setContentPane(mainPanel);
        frame.setVisible(true);
    }

    static class GradientPanel extends JPanel {
        GradientPanel() { setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, BG_DARK, getWidth(), getHeight(), BG_MID));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f));
            g2.setColor(ACCENT);
            g2.fillOval(-80, -80, 300, 300);
            g2.setColor(ACCENT_2);
            g2.fillOval(getWidth() - 220, getHeight() - 220, 320, 320);
            g2.dispose();
        }
    }

    static void clearMain() {
        mainPanel.removeAll();
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    static JLabel makeLabel(String text, Font font, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(font);
        l.setForeground(color);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    static JButton makeGameButton(String text, Color baseColor) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = getModel().isRollover() ? baseColor.brighter() : baseColor;
                if (getModel().isPressed()) c = baseColor.darker();
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
                g2.setColor(new Color(255, 255, 255, 60));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 22, 22);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(buttonFont);
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(340, 52));
        btn.setMaximumSize(new Dimension(340, 52));
        return btn;
    }

    // ============================================================
    // WELCOME SCREEN
    // ============================================================
    static void showWelcomeScreen() {
        clearMain();

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(50, 40, 40, 40));

        JLabel title = makeLabel("QUIZLANGO2", titleFont, ACCENT);
        JLabel subtitle = makeLabel("🤣  MWESIT QUIZ GAME  🤣", headerFont, ACCENT_2);

        content.add(title);
        content.add(Box.createVerticalStrut(6));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(40));

        JLabel nameLbl = makeLabel("Enter your Name:", bodyFont, TEXT_LIGHT);
        JTextField nameField = new JTextField();
        nameField.setFont(bodyFont);
        nameField.setHorizontalAlignment(JTextField.CENTER);
        nameField.setMaximumSize(new Dimension(340, 42));
        nameField.setPreferredSize(new Dimension(340, 42));
        nameField.setBackground(new Color(255, 255, 255, 235));
        nameField.setForeground(new Color(30, 30, 60));
        nameField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ACCENT, 2, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        content.add(nameLbl);
        content.add(Box.createVerticalStrut(8));
        content.add(nameField);
        content.add(Box.createVerticalStrut(25));

        JButton playBtn = makeGameButton("▶   PLAY", new Color(60, 170, 90));
        JButton lbBtn   = makeGameButton("🏆   LEADERBOARD", new Color(200, 150, 30));
        JButton revBtn  = makeGameButton("📜   REVIEW ANSWERS", new Color(60, 130, 200));
        JButton exitBtn = makeGameButton("✖   EXIT", new Color(180, 60, 60));

        for (JButton b : new JButton[]{playBtn, lbBtn, revBtn, exitBtn}) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(b);
            content.add(Box.createVerticalStrut(12));
        }

        playBtn.addActionListener(e -> {
            String n = nameField.getText().trim();
            currentPlayer = n.isEmpty() ? "Player1" : n;
            loadQuestions();
            startGame();
        });
        lbBtn.addActionListener(e -> showLeaderboard());
        revBtn.addActionListener(e -> {
            String n = nameField.getText().trim();
            if (!n.isEmpty()) currentPlayer = n;
            if (currentPlayer.isEmpty()) currentPlayer = "Player1";
            reviewAnswers();
        });
        exitBtn.addActionListener(e -> {
            closeConnection();
            System.exit(0);
        });

        mainPanel.add(content, BorderLayout.CENTER);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    // ============================================================
    // QUESTIONS
    // ============================================================
    static void loadQuestions() {
        questionQueue.clear();
        questionQueue.add(new Question(
                "Ano ang meaning sng PBB nga palabas sa ABSCBN Network ?",
                new String[]{"a. PaBeBekobi", "b. PunongBaranggayBerto",
                             "c. Pinoy Big Brother", "d. Philippine Boy Band"},
                3, "Pinoy Big Brother ky kung ky rosmar daad pinoy big sister abi!"));
        questionQueue.add(new Question(
                "Pila ang legs sang kagang(crab)?",
                new String[]{"a. 6", "b. 8", "c. 10", "d. 4"},
                2, "8 nlang ky ginutod ko kamoy na ky gapangagat!"));
        questionQueue.add(new Question(
                "Kung ang bulak ay cotton, ano naman ang bulaklak ?",
                new String[]{"a. cotton cotton", "b. vagetable", "c. flower", "d. plant"},
                3, "flower ah nagay na kaoverthink!"));
        questionQueue.add(new Question(
                "Kung ang balde ga buslot, ano tawag?",
                new String[]{"a. Leak Bucket", "b. Sad Bucket", "c. Empty Bucket", "d. Bye Bucket"},
                1, "Leak it!"));
        questionQueue.add(new Question(
                "Ano ang una naton nga himuon kung maglinog?",
                new String[]{"a. magselpon", "b. doing the duck cover hold",
                             "c. magpangadi", "d. maghibi"},
                1, "para makapost dayun keep safe ebrewan!"));
        questionQueue.add(new Question(
                "Ano ang color sang langit?",
                new String[]{"a. Green", "b. Red", "c. Blue", "d. Yellow"},
                3, "Blue sky!"));
        questionQueue.add(new Question(
                "Sin-o nag himo sang Java?",
                new String[]{"a. Ser Porras", "b. James Gosling",
                             "c. James Zuckerberg", "d. Kuya Erick"},
                2, "James Gosling ngani!"));
        questionQueue.add(new Question(
                "Ano ang opposite sang 'hot'?",
                new String[]{"a. Warm", "b. Cold", "c. Heat", "d. Fire"},
                2, "Cold gid hw!"));
        questionQueue.add(new Question(
                "Pila ka days ang pahuway sng 2nd year IT in a week?",
                new String[]{"a. 5", "b. 6", "c. 3", "d. 8"},
                3, "3 days! ky online class kung martes haha"));
        questionQueue.add(new Question(
                "Kung wala ka kwarta, ano ka?",
                new String[]{"a. Broke", "b. Happy", "c. Free", "d. TANAN"},
                4, "Tanan na! 😂"));
    }

    // ============================================================
    // GAME SCREEN
    // ============================================================
    static void startGame() {
        score = 0;
        currentQNum = 0;
        answerHistory.clear();
        showNextQuestion();
    }

    static void showNextQuestion() {
        if (questionQueue.isEmpty()) {
            showGameOver();
            return;
        }
        currentQuestion = questionQueue.poll();
        currentQNum++;

        clearMain();
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(20, 60, 20, 60));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        JLabel playerLbl = new JLabel("👤  " + currentPlayer);
        playerLbl.setFont(headerFont);
        playerLbl.setForeground(ACCENT_2);
        JLabel scoreLbl = new JLabel("⭐  Score: " + score);
        scoreLbl.setFont(headerFont);
        scoreLbl.setForeground(ACCENT);
        JLabel qLbl = new JLabel("Q " + currentQNum + " / 10", SwingConstants.CENTER);
        qLbl.setFont(headerFont);
        qLbl.setForeground(TEXT_LIGHT);
        topBar.add(playerLbl, BorderLayout.WEST);
        topBar.add(qLbl, BorderLayout.CENTER);
        topBar.add(scoreLbl, BorderLayout.EAST);

        JProgressBar pb = new JProgressBar(0, 10);
        pb.setValue(currentQNum);
        pb.setForeground(ACCENT);
        pb.setBackground(new Color(60, 60, 90));
        pb.setBorder(new EmptyBorder(10, 0, 10, 0));
        pb.setPreferredSize(new Dimension(100, 12));

        JPanel card = new JPanel();
        card.setOpaque(true);
        card.setBackground(CARD_BG);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ACCENT, 2, true),
                new EmptyBorder(22, 28, 22, 28)));
        card.setMaximumSize(new Dimension(880, 160));

        JTextArea qText = new JTextArea(currentQuestion.questionText);
        qText.setWrapStyleWord(true);
        qText.setLineWrap(true);
        qText.setEditable(false);
        qText.setOpaque(false);
        qText.setFont(headerFont);
        qText.setForeground(TEXT_LIGHT);
        card.add(qText);

        JPanel opts = new JPanel(new GridLayout(4, 1, 10, 10));
        opts.setOpaque(false);
        opts.setBorder(new EmptyBorder(20, 0, 0, 0));

        ButtonGroup bg = new ButtonGroup();
        JToggleButton[] optBtns = new JToggleButton[4];
        for (int i = 0; i < 4; i++) {
            JToggleButton b = new JToggleButton(currentQuestion.options[i]);
            b.setFont(bodyFont);
            b.setForeground(TEXT_LIGHT);
            b.setBackground(new Color(55, 52, 100));
            b.setFocusPainted(false);
            b.setOpaque(true);
            b.setBorder(new CompoundBorder(
                    new LineBorder(new Color(120, 120, 180), 2, true),
                    new EmptyBorder(10, 20, 10, 20)));
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.addActionListener(ev -> b.setBackground(new Color(80, 90, 160)));
            optBtns[i] = b;
            bg.add(b);
            opts.add(b);
        }

        JButton submit = makeGameButton("SUBMIT ANSWER", new Color(60, 170, 90));
        submit.setAlignmentX(Component.CENTER_ALIGNMENT);
        submit.setPreferredSize(new Dimension(260, 48));
        submit.setMaximumSize(new Dimension(260, 48));
        submit.addActionListener(e -> {
            int chosen = -1;
            for (int i = 0; i < 4; i++) {
                if (optBtns[i].isSelected()) {
                    chosen = i + 1;
                    break;
                }
            }
            if (chosen == -1) {
                JOptionPane.showMessageDialog(frame, "Please pick an answer first!",
                        "No answer", JOptionPane.WARNING_MESSAGE);
                return;
            }
            handleAnswer(chosen);
        });

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(card);
        center.add(Box.createVerticalStrut(18));
        opts.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(opts);
        center.add(Box.createVerticalStrut(22));
        center.add(submit);

        JPanel mid = new JPanel(new BorderLayout());
        mid.setOpaque(false);
        mid.add(pb, BorderLayout.NORTH);
        mid.add(center, BorderLayout.CENTER);

        wrapper.add(topBar, BorderLayout.NORTH);
        wrapper.add(mid, BorderLayout.CENTER);

        mainPanel.add(wrapper, BorderLayout.CENTER);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    static void handleAnswer(int chosen) {
        char given = (char) ('a' + chosen - 1);
        char correctLetter = (char) ('a' + currentQuestion.correctAnswer - 1);
        boolean isCorrect = (given == correctLetter);

        answerHistory.push("Q" + currentQNum + ": You answered " + given
                + " | Correct: " + correctLetter);
        saveAnswer(currentPlayer, currentQNum, currentQuestion.questionText,
                given, correctLetter, isCorrect);
        if (isCorrect) score += 10;

        clearMain();
        JPanel fb = new JPanel();
        fb.setOpaque(false);
        fb.setLayout(new BoxLayout(fb, BoxLayout.Y_AXIS));
        fb.setBorder(new EmptyBorder(70, 60, 60, 60));

        JLabel head = new JLabel(isCorrect ? "✅  CORRECT!  +10 points" : "❌  WRONG!",
                SwingConstants.CENTER);
        head.setFont(titleFont.deriveFont(34f));
        head.setForeground(isCorrect ? CORRECT_GREEN : WRONG_RED);
        head.setAlignmentX(Component.CENTER_ALIGNMENT);
        fb.add(head);
        fb.add(Box.createVerticalStrut(20));

        if (!isCorrect) {
            JLabel ansLbl = new JLabel("Correct Answer: "
                    + currentQuestion.options[currentQuestion.correctAnswer - 1]);
            ansLbl.setFont(headerFont);
            ansLbl.setForeground(ACCENT);
            ansLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            fb.add(ansLbl);
            fb.add(Box.createVerticalStrut(15));

            JTextArea exp = new JTextArea(currentQuestion.explanation);
            exp.setWrapStyleWord(true);
            exp.setLineWrap(true);
            exp.setEditable(false);
            exp.setOpaque(false);
            exp.setFont(bodyFont);
            exp.setForeground(TEXT_LIGHT);
            exp.setAlignmentX(Component.CENTER_ALIGNMENT);
            exp.setMaximumSize(new Dimension(700, 80));
            fb.add(exp);
            fb.add(Box.createVerticalStrut(25));
        } else {
            fb.add(Box.createVerticalStrut(20));
        }

        JButton next = makeGameButton("NEXT ▶", new Color(60, 130, 200));
        next.setAlignmentX(Component.CENTER_ALIGNMENT);
        next.setPreferredSize(new Dimension(220, 50));
        next.setMaximumSize(new Dimension(220, 50));
        next.addActionListener(e -> showNextQuestion());
        fb.add(next);

        mainPanel.add(fb, BorderLayout.CENTER);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    // ============================================================
    // GAME OVER
    // ============================================================
    static void showGameOver() {
        System.out.println("=== GAME OVER — saving score ===");
        savePlayer(currentPlayer, score);
        System.out.println("=== Score save completed — showing screen ===");

        clearMain();
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(90, 60, 60, 60));

        JLabel over = new JLabel("GAME OVER", SwingConstants.CENTER);
        over.setFont(titleFont.deriveFont(52f));
        over.setForeground(ACCENT);
        over.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(over);
        p.add(Box.createVerticalStrut(15));

        JLabel nm = new JLabel(currentPlayer, SwingConstants.CENTER);
        nm.setFont(headerFont);
        nm.setForeground(ACCENT_2);
        nm.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(nm);
        p.add(Box.createVerticalStrut(10));

        JLabel sc = new JLabel("Final Score: " + score + " / 100", SwingConstants.CENTER);
        sc.setFont(headerFont.deriveFont(30f));
        sc.setForeground(TEXT_LIGHT);
        sc.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(sc);
        p.add(Box.createVerticalStrut(40));

        JButton lb = makeGameButton("🏆  VIEW LEADERBOARD", new Color(200, 150, 30));
        lb.setAlignmentX(Component.CENTER_ALIGNMENT);
        lb.addActionListener(e -> showLeaderboard());

        JButton home = makeGameButton("🏠  BACK TO MENU", new Color(60, 130, 200));
        home.setAlignmentX(Component.CENTER_ALIGNMENT);
        home.addActionListener(e -> showWelcomeScreen());

        p.add(lb);
        p.add(Box.createVerticalStrut(12));
        p.add(home);

        mainPanel.add(p, BorderLayout.CENTER);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    // ============================================================
    // LEADERBOARD
    // ============================================================
    static void showLeaderboard() {
        clearMain();
        List<Object[]> rows = getLeaderboardFromDB();

        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(35, 80, 35, 80));

        JLabel title = new JLabel("🏆  LEADERBOARD", SwingConstants.CENTER);
        title.setFont(titleFont.deriveFont(34f));
        title.setForeground(ACCENT);
        title.setBorder(new EmptyBorder(0, 0, 20, 0));
        p.add(title, BorderLayout.NORTH);

        String[] cols = {"Rank", "Player", "Score", "Date Played"};
        Object[][] data = new Object[rows.size()][4];
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            data[i][0] = "#" + (i + 1);
            data[i][1] = r[0];
            data[i][2] = r[1] + " pts";
            data[i][3] = r[2];
        }

        DefaultTableModel model = new DefaultTableModel(data, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setFont(bodyFont);
        table.setRowHeight(32);
        table.setForeground(TEXT_LIGHT);
        table.setBackground(CARD_BG);
        table.setGridColor(new Color(80, 80, 130));
        table.setSelectionBackground(new Color(80, 90, 160));
        table.setSelectionForeground(Color.WHITE);
        table.getTableHeader().setFont(headerFont.deriveFont(15f));
        table.getTableHeader().setBackground(new Color(70, 65, 130));
        table.getTableHeader().setForeground(Color.WHITE);

        JScrollPane sp = new JScrollPane(table);
        sp.getViewport().setOpaque(false);
        sp.setBorder(new LineBorder(ACCENT, 2, true));
        p.add(sp, BorderLayout.CENTER);

        JButton back = makeGameButton("◀  BACK", new Color(60, 130, 200));
        back.addActionListener(e -> showWelcomeScreen());
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(18, 0, 0, 0));
        bottom.add(back);
        p.add(bottom, BorderLayout.SOUTH);

        mainPanel.add(p, BorderLayout.CENTER);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    // ============================================================
    // REVIEW ANSWERS
    // ============================================================
    static void reviewAnswers() {
        clearMain();
        List<String> list = getAnswerHistoryFromDB(currentPlayer);

        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(35, 80, 35, 80));

        JLabel title = new JLabel("📜  ANSWER HISTORY — " + currentPlayer, SwingConstants.CENTER);
        title.setFont(titleFont.deriveFont(28f));
        title.setForeground(ACCENT);
        title.setBorder(new EmptyBorder(0, 0, 20, 0));
        p.add(title, BorderLayout.NORTH);

        DefaultListModel<String> lm = new DefaultListModel<>();
        if (list.isEmpty()) {
            lm.addElement("No answers saved for '" + currentPlayer + "' yet!");
        } else {
            for (String s : list) lm.addElement(s);
        }

        JList<String> jlist = new JList<>(lm);
        jlist.setFont(bodyFont);
        jlist.setBackground(CARD_BG);
        jlist.setForeground(TEXT_LIGHT);
        jlist.setSelectionBackground(new Color(80, 90, 160));
        jlist.setFixedCellHeight(34);
        jlist.setBorder(new EmptyBorder(10, 15, 10, 15));

        JScrollPane sp = new JScrollPane(jlist);
        sp.getViewport().setOpaque(false);
        sp.setBorder(new LineBorder(ACCENT, 2, true));
        p.add(sp, BorderLayout.CENTER);

        JButton back = makeGameButton("◀  BACK", new Color(60, 130, 200));
        back.addActionListener(e -> showWelcomeScreen());
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(18, 0, 0, 0));
        bottom.add(back);
        p.add(bottom, BorderLayout.SOUTH);

        mainPanel.add(p, BorderLayout.CENTER);
        mainPanel.revalidate();
        mainPanel.repaint();
    }
}