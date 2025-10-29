package client.GUI;

import client.Client;
import common.Message;
import java.io.IOException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    @FXML
    private Label successLabel;
    
    private Client client;

    public void setClient(Client client) {
        this.client = client;
    }

    @FXML
    private void handleLogin() throws IOException {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Vui lòng nhập đầy đủ thông tin.");
            return;
        }

        String[] credentials = {username, password};
        Message loginMessage = new Message("login", credentials);
        client.sendMessage(loginMessage);
    }
    
    @FXML
    private void handleGoToRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/resources/GUI/RegisterUI.fxml"));
            Parent root = loader.load();
            
            RegisterController registerController = loader.getController();
            registerController.setClient(client);

            Stage stage = (Stage) usernameField.getScene().getWindow();
            Scene scene = new Scene(root);
            if (getClass().getResource("/resources/GUI/style.css") != null) {
                scene.getStylesheets().add(getClass().getResource("/resources/GUI/style.css").toExternalForm());
            }
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showError("Không thể tải màn hình đăng ký!");
        }
    }

    public void showError(String error) {
        Platform.runLater(() -> {
            errorLabel.setText(error);
            if (successLabel != null) {
                successLabel.setText("");
            }
        });
    }
    
    public void showSuccess(String message) {
        Platform.runLater(() -> {
            if (successLabel != null) {
                successLabel.setText(message);
            }
            errorLabel.setText("");
        });
    }
}