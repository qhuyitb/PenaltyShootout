package server;

import common.Message;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameRoom {

    private ClientHandler shooterHandler;
    private ClientHandler goalkeeperHandler;
    private DatabaseManager dbManager;
    private int matchId;
    private int shooterScore;
    private int goalkeeperScore;
    private int currentRound;
    private final int MAX_ROUNDS = 10;
    private final int WIN_SCORE = 5;
    private String shooterDirection;
    private Boolean shooterWantsRematch = null;
    private Boolean goalkeeperWantsRematch = null;
    // Thời gian chờ cho mỗi lượt (ví dụ: 15 giây)
    private final int TURN_TIMEOUT = 15;

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Biến lưu trữ Future của nhiệm vụ chờ
    private ScheduledFuture<?> shooterTimeoutTask;
    private ScheduledFuture<?> goalkeeperTimeoutTask;

    // Biến để kiểm tra xem người chơi đã thực hiện hành động chưa
    private boolean shooterActionReceived = false;
    private boolean goalkeeperActionReceived = false;

    private String goalkeeperDirection;
    
    private ClientHandler player1;
    private ClientHandler player2;

    public GameRoom(ClientHandler player1, ClientHandler player2, DatabaseManager dbManager) throws SQLException {
        this.dbManager = dbManager;
        this.player1 = player1;
        this.player2 = player2;
        
        this.matchId = dbManager.saveMatch(player1.getUser().getId(), player2.getUser().getId(), 0);
        this.shooterScore = 0;
        this.goalkeeperScore = 0;
        this.currentRound = 1;

        // Random chọn người sút và người bắt
        if (new Random().nextBoolean()) {
            this.shooterHandler = player1;
            this.goalkeeperHandler = player2;
        } else {
            this.shooterHandler = player2;
            this.goalkeeperHandler = player1;
        }
    }

    public void startMatch() {
        try {
            // update ingame status for both player
            shooterHandler.getUser().setStatus("ingame");
            goalkeeperHandler.getUser().setStatus("ingame");

            // to do gui message neu can
            String shooterMessage = "Trận đấu bắt đầu! Bạn là người sút.";
            String goalkeeperMessage = "Trận đấu bắt đầu! Bạn là người bắt.";
            shooterHandler.sendMessage(new Message("match_start", shooterMessage));
            goalkeeperHandler.sendMessage(new Message("match_start", goalkeeperMessage));
            requestNextMove();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestNextMove() { 
        try {
            if (checkEndGame()) {
                endMatch();
                return;
            }

            // CHỈ CẦN GỬI CHO SHOOTER
            shooterHandler.sendMessage(new Message("your_turn", TURN_TIMEOUT));
            goalkeeperHandler.sendMessage(new Message("opponent_turn", TURN_TIMEOUT));

            shooterActionReceived = false;
            shooterDirection = null;
            goalkeeperDirection = null;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Xử lý hướng sút từ người sút
    public synchronized void handleShot(String shooterDirection, ClientHandler shooter)
            throws SQLException, IOException {
        this.shooterDirection = shooterDirection;
        shooterActionReceived = true;

        if (shooterTimeoutTask != null && !shooterTimeoutTask.isDone()) {
            shooterTimeoutTask.cancel(true);
        }

        // CHỈ CẦN GỬI CHO GOALKEEPER
        goalkeeperHandler.sendMessage(new Message("goalkeeper_turn", TURN_TIMEOUT));
        shooterHandler.sendMessage(new Message("opponent_turn", TURN_TIMEOUT));

        goalkeeperActionReceived = false;
    }

   
    // Xử lý hướng chặn từ người bắt
    public synchronized void handleGoalkeeper(String goalkeeperDirection, ClientHandler goalkeeper)
            throws SQLException, IOException {
        if (this.shooterDirection == null) {
            shooterHandler.sendMessage(new Message("error", "Hướng sút chưa được thiết lập."));
            goalkeeperHandler.sendMessage(new Message("error", "Hướng sút chưa được thiết lập."));
            return;
        }
        this.goalkeeperDirection = goalkeeperDirection;
        goalkeeperActionReceived = true;

        if (goalkeeperTimeoutTask != null && !goalkeeperTimeoutTask.isDone()) {
            goalkeeperTimeoutTask.cancel(true);
        }

        // Parse hướng và độ cao
        String[] shootParts = shooterDirection.split("-");
        String shootDir = shootParts[0];      // Trái/Giữa/Phải
        String shootHeight = shootParts[1];   // Thấp/Cao

        String[] keeperParts = goalkeeperDirection.split("-");
        String keeperDir = keeperParts[0];
        String keeperHeight = keeperParts[1];

        // Xác định kết quả
        boolean goal = false;

        if (shootDir.equalsIgnoreCase(keeperDir)) {
            // Đúng hướng
            if (shootHeight.equalsIgnoreCase(keeperHeight)) {
                // Đúng cả hướng và độ cao → Chặn được
                goal = false;
            } else {
                // Đúng hướng nhưng sai độ cao → 50% cơ hội
                Random random = new Random();
                goal = random.nextBoolean();
            }
        } else {
            // Sai hướng → Ghi bàn
            goal = true;
        }

        // Cập nhật điểm số ĐÚNG
        if (goal) {
            if (shooterHandler == player1) {
                shooterScore++; // player1 ghi bàn
            } else {
                goalkeeperScore++; // player2 ghi bàn
            }
        }

        // GỬI MESSAGE ĐỂ TRIGGER ANIMATION
        String animationData = shooterDirection + "|" + goalkeeperDirection + "|" + goal;

        if (goal) {
            shooterHandler.sendMessage(new Message("animate_shoot_vao", animationData));
            goalkeeperHandler.sendMessage(new Message("animate_shoot_vao", animationData));
        } else {
            shooterHandler.sendMessage(new Message("animate_shoot_khong_vao", animationData));
            goalkeeperHandler.sendMessage(new Message("animate_shoot_khong_vao", animationData));
        }

        // Gửi kết quả
        String kick_result = (goal ? "win" : "lose") + "-" + shooterDirection + "-" + goalkeeperDirection;
        shooterHandler.sendMessage(new Message("kick_result", kick_result));
        goalkeeperHandler.sendMessage(new Message("kick_result", kick_result));

        // Lưu chi tiết
        dbManager.saveMatchDetails(matchId, currentRound,
                shooterHandler.getUser().getId(),
                goalkeeperHandler.getUser().getId(),
                shooterDirection, goalkeeperDirection, goal ? "win" : "lose");

        // Gửi tỷ số cập nhật
        if (shooterHandler == player1) {
            shooterHandler.sendMessage(new Message("update_score", 
                new int[] { shooterScore, goalkeeperScore, currentRound }));
            goalkeeperHandler.sendMessage(new Message("update_score", 
                new int[] { goalkeeperScore, shooterScore, currentRound }));
        } else {
            shooterHandler.sendMessage(new Message("update_score", 
                new int[] { goalkeeperScore, shooterScore, currentRound }));
            goalkeeperHandler.sendMessage(new Message("update_score", 
                new int[] { shooterScore, goalkeeperScore, currentRound }));
        }

        // Tăng round TRƯỚC
        currentRound++;

        // SWAP vai trò SAU
        ClientHandler temp = shooterHandler;
        shooterHandler = goalkeeperHandler;
        goalkeeperHandler = temp;

        // Kiểm tra kết thúc SAU KHI đã tăng round và swap
        if (checkEndGame()) {
            determineWinner();
        } else {
            // Reset và chuyển lượt
            shooterDirection = null;
            goalkeeperDirection = null;
            shooterActionReceived = false;
            goalkeeperActionReceived = false;
            requestNextMove();
        }
    }

    private void determineWinner() throws SQLException, IOException {
        int winnerId = 0;
        String resultMessage = "";
        String endReason = "normal";

        if (shooterScore > goalkeeperScore) {
            winnerId = player1.getUser().getId();
            resultMessage = player1.getUser().getUsername() + " thắng trận đấu!";
        } else if (goalkeeperScore > shooterScore) {
            winnerId = player2.getUser().getId();
            resultMessage = player2.getUser().getUsername() + " thắng trận đấu!";
        } else {
            resultMessage = "Trận đấu hòa!";
        }

        if (winnerId != 0) {
            dbManager.updateUserPoints(winnerId, 3);
        }
        dbManager.updateMatchWinner(matchId, winnerId, endReason);

        // Thông báo kết quả trận đấu cho cả hai người chơi
        player1.sendMessage(new Message("match_result", 
            (shooterScore > goalkeeperScore) ? "win" : (shooterScore < goalkeeperScore) ? "lose" : "draw"));
        player2.sendMessage(new Message("match_result", 
            (goalkeeperScore > shooterScore) ? "win" : (goalkeeperScore < shooterScore) ? "lose" : "draw"));

        // Tạo một ScheduledExecutorService để trì hoãn việc gửi tin nhắn
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            // Gửi tin nhắn yêu cầu chơi lại sau 5 giây
            shooterHandler.sendMessage(new Message("play_again_request", "Bạn có muốn chơi lại không?"));
            goalkeeperHandler.sendMessage(new Message("play_again_request", "Bạn có muốn chơi lại không?"));
            // Đóng scheduler sau khi hoàn tất
            scheduler.shutdown();
        }, 3, TimeUnit.SECONDS);
    }

    // Xử lý yêu cầu chơi lại
    public synchronized void handlePlayAgainResponse(boolean playAgain, ClientHandler responder)
            throws SQLException, IOException {
        if (responder == player1) {
            shooterWantsRematch = playAgain;
        } else if (responder == player2) {
            goalkeeperWantsRematch = playAgain;
        }

        // Kiểm tra nếu một trong hai người chơi đã thoát
        if (shooterHandler == null || goalkeeperHandler == null) {
            return;
        }

        // Kiểm tra nếu cả hai người chơi đã phản hồi
        if (shooterWantsRematch != null && goalkeeperWantsRematch != null) {
            if (shooterWantsRematch && goalkeeperWantsRematch) {
                // Cả hai người chơi đồng ý chơi lại
                resetGameState();
                startMatch();
            } else {
                // cap nhat status "ingame" -> "online"
                player1.getUser().setStatus("online");
                player2.getUser().setStatus("online");

                dbManager.updateUserStatus(player1.getUser().getId(), "online");
                dbManager.updateUserStatus(player2.getUser().getId(), "online");

                player1.getServer().broadcast(new Message("status_update", player1.getUser().getUsername() + " is online"));
                player2.getServer().broadcast(new Message("status_update", player2.getUser().getUsername() + " is online"));
                // ------------------------------------------------------------//

                // Gửi thông báo kết thúc trận đấu
                player1.sendMessage(new Message("match_end", "Trận đấu kết thúc."));
                player2.sendMessage(new Message("match_end", "Trận đấu kết thúc."));

                // Đặt lại biến
                shooterWantsRematch = null;
                goalkeeperWantsRematch = null;

                // Đưa cả hai người chơi về màn hình chính
                player1.clearGameRoom();
                player2.clearGameRoom();
            }
        }
    }

    private void resetGameState() throws SQLException {
        // Reset game variables
        shooterScore = 0;
        goalkeeperScore = 0;
        currentRound = 1;
        shooterDirection = null;
        shooterWantsRematch = null;
        goalkeeperWantsRematch = null;

        // Swap shooter and goalkeeper roles for fairness
        ClientHandler temp = shooterHandler;
        shooterHandler = goalkeeperHandler;
        goalkeeperHandler = temp;

        // Create a new match in the database
        matchId = dbManager.saveMatch(player1.getUser().getId(), player2.getUser().getId(), 0);
    }

    // Đảm bảo rằng phương thức endMatch() tồn tại và được định nghĩa chính xác
    private void endMatch() throws SQLException, IOException {
        determineWinner();

        // Reset in-game status for both players after match
        if (shooterHandler != null) {
            shooterHandler.getUser().setStatus("online");
            // todo gui message neu can
        }
        if (goalkeeperHandler != null) {
            goalkeeperHandler.getUser().setStatus("online");
            // todo gui message neu can
        }
    }

    public void handlePlayerDisconnect(ClientHandler disconnectedPlayer) throws SQLException, IOException {
        String resultMessageToWinner = "Đối thủ đã thoát. Bạn thắng trận đấu!";
        String resultMessageToLoser = "Bạn đã thoát. Bạn thua trận đấu!";
        int winnerId = 0;
        String endReason = "player_quit";
        ClientHandler otherPlayer = null;

        if (disconnectedPlayer == player1) {
            otherPlayer = player2;
            winnerId = player2.getUser().getId();
        } else if (disconnectedPlayer == player2) {
            otherPlayer = player1;
            winnerId = player1.getUser().getId();
        }

        shooterWantsRematch = false;
        goalkeeperWantsRematch = false;

        if (winnerId != 0) {
            dbManager.updateUserPoints(winnerId, 3);
            dbManager.updateMatchWinner(matchId, winnerId, endReason);
        }

        // cap nhat status "ingame" -> "online"
        otherPlayer.getUser().setStatus("online");
        dbManager.updateUserStatus(otherPlayer.getUser().getId(), "online");
        otherPlayer.getServer()
                .broadcast(new Message("status_update", otherPlayer.getUser().getUsername() + " is online"));

        // cap nhat status "ingame" -> "offline"
        disconnectedPlayer.getUser().setStatus("offline");
        dbManager.updateUserStatus(disconnectedPlayer.getUser().getId(), "offline");
        disconnectedPlayer.getServer()
                .broadcast(new Message("status_update", disconnectedPlayer.getUser().getUsername() + " is offline"));
        // -------------------------------------------------------

        // Gửi thông báo kết thúc trận đấu cho cả hai người chơi
        otherPlayer.sendMessage(new Message("match_end", resultMessageToWinner));
        disconnectedPlayer.sendMessage(new Message("match_end", resultMessageToLoser));

        // Đặt lại trạng thái game room
        shooterWantsRematch = null;
        goalkeeperWantsRematch = null;
        shooterDirection = null;

        // Sử dụng phương thức clearGameRoom() để đặt gameRoom thành null
        if (shooterHandler != null) {
            shooterHandler.clearGameRoom();
        }
        if (goalkeeperHandler != null) {
            goalkeeperHandler.clearGameRoom();
        }

    }

    public void handlePlayerQuit(ClientHandler quittingPlayer) throws SQLException, IOException {
        String resultMessageToLoser = "Bạn đã thoát. Bạn thua trận đấu!";
        String resultMessageToWinner = "Đối thủ đã thoát. Bạn thắng trận đấu!";

        int winnerId = 0;
        String endReason = "player_quit";
        ClientHandler otherPlayer = null;

        if (quittingPlayer == player1) {
            winnerId = player2.getUser().getId();
            otherPlayer = player2;
            shooterWantsRematch = false;
        } else if (quittingPlayer == player2) {
            winnerId = player1.getUser().getId();
            otherPlayer = player1;
            goalkeeperWantsRematch = false;
        }

        if (winnerId != 0) {
            dbManager.updateUserPoints(winnerId, 3);
            dbManager.updateMatchWinner(matchId, winnerId, endReason);
        }

        // cap nhat status "ingame" -> "online"
        shooterHandler.getUser().setStatus("online");
        goalkeeperHandler.getUser().setStatus("online");

        dbManager.updateUserStatus(shooterHandler.getUser().getId(), "online");
        dbManager.updateUserStatus(goalkeeperHandler.getUser().getId(), "online");

        shooterHandler.getServer()
                .broadcast(new Message("status_update", shooterHandler.getUser().getUsername() + " is online"));
        goalkeeperHandler.getServer()
                .broadcast(new Message("status_update", goalkeeperHandler.getUser().getUsername() + " is online"));
        // ------------------------------------------------------------

        // Gửi thông báo kết thúc trận đấu cho cả hai người chơi
        quittingPlayer.sendMessage(new Message("match_end", resultMessageToLoser));
        if (otherPlayer != null) {
            otherPlayer.sendMessage(new Message("match_end", resultMessageToWinner));
        }

        // Đặt lại trạng thái game room
        shooterWantsRematch = null;
        goalkeeperWantsRematch = null;
        shooterDirection = null;

        // Sử dụng phương thức clearGameRoom() để đặt gameRoom thành null
        if (shooterHandler != null) {
            shooterHandler.clearGameRoom();
        }
        if (goalkeeperHandler != null) {
            goalkeeperHandler.clearGameRoom();
        }

        // Không cần gửi thông báo "return_to_main"
    }

    public void startShooterTimeout() {
        try {
            if (checkEndGame()) {
                endMatch();
                return;
            }
            if (!shooterActionReceived) {
                // Người sút không thực hiện hành động trong thời gian quy định
                shooterDirection = "Giữa-Thấp";
                shooterActionReceived = true;
                shooterHandler.sendMessage(
                        new Message("timeout", "Hết giờ! \nHệ thống tự chọn 'Giữa-Thấp' cho bạn."));
                goalkeeperHandler.sendMessage(new Message("opponent_timeout",
                        "Hết giờ! \nHệ thống tự chọn 'Giữa-Thấp' cho đối thủ."));
                // Yêu cầu người bắt chọn hướng chặn
                handleShot(shooterDirection, shooterHandler);

                // Bắt đầu đếm thời gian chờ cho người bắt
                goalkeeperActionReceived = false;
                // startGoalkeeperTimeout();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkEndGame() {
        // currentRound ĐÃ được tăng lên rồi
        // currentRound = 2 nghĩa là vừa đá xong round 1
        // currentRound = 11 nghĩa là vừa đá xong round 10

        // Chưa đủ 10 rounds
        if (currentRound <= MAX_ROUNDS) {
            return false;
        }

        // Vừa đá xong round 10 (currentRound = 11)
        if (currentRound == MAX_ROUNDS + 1) {
            // Kiểm tra hòa không
            return shooterScore != goalkeeperScore;
        }

        // Đã vào sudden death (currentRound >= 12)
        // currentRound = 12 → vừa đá xong round 11 (lượt 1 sudden death)
        // currentRound = 13 → vừa đá xong round 12 (lượt 2 sudden death)

        int suddenDeathRounds = currentRound - (MAX_ROUNDS + 1);

        // Nếu là lượt chẵn (2, 4, 6...) → cả 2 đã đá xong → KIỂM TRA
        // Nếu là lượt lẻ (1, 3, 5...) → mới 1 người đá → CHƯA kiểm tra
        if (suddenDeathRounds % 2 == 0) {
            return shooterScore != goalkeeperScore;
        } else {
            return false;
        }
    }

    public void startGoalkeeperTimeout() {
        try {
            if (!goalkeeperActionReceived) {
                // Người bắt không thực hiện hành động trong thời gian quy định
                goalkeeperDirection = "Giữa-Thấp";
                goalkeeperActionReceived = true;

                goalkeeperHandler.sendMessage(
                        new Message("timeout", "Hết giờ! \nHệ thống tự chọn 'Giữa-Thấp' cho bạn."));
                shooterHandler.sendMessage(new Message("opponent_timeout",
                        "Hết giờ! \nHệ thống tự chọn 'Giữa-Thấp' cho đối thủ."));

                // Tiến hành xử lý kết quả
                handleGoalkeeper(goalkeeperDirection, goalkeeperHandler);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}