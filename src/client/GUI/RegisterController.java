package client.GUI;

import client.Client;
import common.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class RegisterController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label errorLabel;

    private Client client;

    public void setClient(Client client) {
        this.client = client;
        this.client.setRegisterController(this);
    }

    @FXML
    public void handleRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Validate input
        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Vui lòng điền đầy đủ thông tin!");
            return;
        }

        if (username.length() < 3) {
            showError("Tên đăng nhập phải có ít nhất 3 ký tự!");
            return;
        }

        if (password.length() < 6) {
            showError("Mật khẩu phải có ít nhất 6 ký tự!");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Mật khẩu xác nhận không khớp!");
            return;
        }

        // Send register request to server
        String[] credentials = {username, password};
        Message registerMessage = new Message("register", credentials);
        try {
            client.sendMessage(registerMessage);
        } catch (IOException e) {
            showError("Lỗi kết nối đến server!");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleBackToLogin() {
        try {
            showLoginScreen();
        } catch (IOException e) {
            e.printStackTrace();
            showError("Không thể quay lại màn hình đăng nhập!");
        }
    }

    public void showError(String message) {
        Platform.runLater(() -> {
            errorLabel.setText(message);
        });
    }

    public void showSuccess(String message) {
        Platform.runLater(() -> {
            try {
                // Show success and go back to login
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/resources/LoginUI.fxml"));
                Parent root = loader.load();
                
                LoginController loginController = loader.getController();
                loginController.setClient(client);
                loginController.showSuccess(message);

                Stage stage = (Stage) usernameField.getScene().getWindow();
                Scene scene = new Scene(root);
                if (getClass().getResource("/resources/style.css") != null) {
                    scene.getStylesheets().add(getClass().getResource("/resources/style.css").toExternalForm());
                }
                stage.setScene(scene);
                stage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void showLoginScreen() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/resources/LoginUI.fxml"));
        Parent root = loader.load();
        
        LoginController loginController = loader.getController();
        loginController.setClient(client);

        Stage stage = (Stage) usernameField.getScene().getWindow();
        Scene scene = new Scene(root);
        if (getClass().getResource("/resources/style.css") != null) {
            scene.getStylesheets().add(getClass().getResource("/resources/style.css").toExternalForm());
        }
        stage.setScene(scene);
        stage.show();
    }
}