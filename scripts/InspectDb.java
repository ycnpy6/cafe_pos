import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
public class InspectDb {
  public static void main(String[] a) throws Exception {
    Class.forName("org.sqlite.JDBC");
    String url = "jdbc:sqlite:" + System.getenv("APPDATA") + "/CafePOS/data/cafepos.db";
    try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
      ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM product_ingredients");
      rs.next();
      System.out.println("=== product_ingredients total: " + rs.getInt(1));
      rs.close();
      rs = s.executeQuery("SELECT p.id, p.name, p.is_prepared, COUNT(pi.ingredient_id) AS n FROM products p LEFT JOIN product_ingredients pi ON pi.product_id=p.id GROUP BY p.id ORDER BY p.name");
      while (rs.next()) {
        System.out.printf("%3d  prepared=%d  lines=%d  %s%n",
            rs.getInt(1), rs.getInt(3), rs.getInt(4), rs.getString(2));
      }
      rs.close();
      System.out.println("--- ALL product_ingredients rows ---");
      rs = s.executeQuery("SELECT pi.product_id, pi.ingredient_id, pi.quantity, pi.unit, pi.quantity_base, p.name AS pname, i.name AS iname FROM product_ingredients pi JOIN products p ON p.id=pi.product_id JOIN ingredients i ON i.id=pi.ingredient_id ORDER BY p.name");
      while (rs.next()) {
        System.out.println(rs.getString("pname") + " | " + rs.getString("iname") + " | qty=" + rs.getDouble("quantity") + " " + rs.getString("unit") + " | base=" + rs.getDouble("quantity_base"));
      }
      rs.close();
      System.out.println("--- ingredients matching seed core names ---");
      rs = s.executeQuery("SELECT id, name, unit, unit_base, unit_factor FROM ingredients WHERE name IN ('Café moulu (espresso)','Lait','Glace','Poudre de chocolat','Chocolate Mordjane','Nescafé','Sirop','Lait concentré sucré','Thé','Banane (pièce)') ORDER BY name");
      while (rs.next()) {
        System.out.printf("id=%-3d  %s  unit=%s base=%s factor=%s%n", rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
      }
    }
  }
}
