module com.mycompany.basedatoscompostela {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.base;
    requires java.sql;

    opens com.mycompany.basedatoscompostela to javafx.fxml;
    exports com.mycompany.basedatoscompostela;
}
