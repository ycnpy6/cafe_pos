module com.cafepos {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.slf4j;
    requires atlantafx.base;
    requires java.desktop;

    opens com.cafepos to javafx.fxml;
    opens com.cafepos.controller to javafx.fxml;
    opens com.cafepos.controllers to javafx.fxml;
    opens com.cafepos.model to javafx.fxml;
    exports com.cafepos;
}
