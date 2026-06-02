module com.cafepos {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.slf4j;
    requires atlantafx.base;
    requires java.desktop;
    requires org.apache.pdfbox;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;
    requires org.kordamp.ikonli.fontawesome5;

    opens com.cafepos to javafx.fxml;
    opens com.cafepos.controller to javafx.fxml;
    opens com.cafepos.controllers to javafx.fxml;
    opens com.cafepos.model to javafx.fxml;
    opens com.cafepos.ui to javafx.fxml;
    exports com.cafepos;
}
