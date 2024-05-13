import com.mysql.cj.jdbc.MysqlDataSource;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {
    private static String USE_SCHEMA = "USE storefront";
    private static int MYSQL_DB_NOT_FOUND = 1049;

    public static void main(String[] args) {

        var dataSource = new MysqlDataSource();
        dataSource.setServerName("localhost");
        dataSource.setPort(3306);
        dataSource.setUser(System.getenv("MYSQLUSER"));
        dataSource.setPassword(System.getenv("MYSQLPASS"));

        try(Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metadata = conn.getMetaData();
            System.out.println(metadata.getSQLStateType());
            if(!checkSchema(conn)) {
                System.out.println("storefront schema does not exist");
                setUpSchema(conn);
            }

//            int newOrder = addOrder(conn, new String[]{"shoes", "shirt", "socks"});
//            System.out.println("New Order = " + newOrder);
//            removeOrder(conn, new String[]{"shoes", "shirt", "socks"});
            deleteOrder(conn, 6);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean checkSchema(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()){
            statement.execute(USE_SCHEMA);
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("Error Code: " + e.getErrorCode());
            System.err.println("Message: " + e.getMessage());
            if(conn.getMetaData().getDatabaseProductName().equals("MySQL")
            && e.getErrorCode() == MYSQL_DB_NOT_FOUND) {
                return false;
            } else throw e;
        }

        return true;
    }

    private static void setUpSchema(Connection conn) throws SQLException {
        String createSchema = "CREATE SCHEMA storefront";
        String createOrder = """
                CREATE TABLE storefront.order (
                order_id int NOT NULL AUTO_INCREMENT,
                order_date DATETIME NOT NULL,
                PRIMARY KEY (order_id)
                )""";
        String createOrderDetails = """
                CREATE TABLE storefront.order_details (
                order_detail_id int NOT NULL AUTO_INCREMENT,
                item_description text,
                order_id int DEFAULT NULL,
                PRIMARY KEY (order_detail_id),
                KEY FK_ORDERID (order_id),
                CONSTRAINT FK_ORDERID FOREIGN KEY (order_id)
                REFERENCES storefront.order (order_id) ON DELETE CASCADE
                )""";

        try(Statement statement = conn.createStatement()) {
            System.out.println("Creating storefront Database");
            statement.execute(createSchema);
            if(checkSchema(conn)) {
                statement.execute(createOrder);
                System.out.println("Successfully Created Order");
                statement.execute(createOrderDetails);
                System.out.println("Successfully Created Order_Details");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //Challenge 1:
    public static int addOrder(Connection conn, String[] items) throws SQLException {
        int orderId = -1;
        String insertOrder = "INSERT INTO storefront.order (order_date) VALUES ('%s')";
        String insertDetail = "INSERT INTO storefront.order_details " +
                "(order_id, item_description) VALUES (%d, %s)";

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String orderDateTime = LocalDateTime.now().format(dtf);
        System.out.println(orderDateTime);
        String formattedString = insertOrder.formatted(orderDateTime);

        String insertOrderAlternative = "INSERT INTO storefront.order (order_date) " +
                "VALUES ('%1$tF %1$tT')";
        System.out.println(insertOrderAlternative.formatted(LocalDateTime.now()));

        try (Statement statement = conn.createStatement()){
            conn.setAutoCommit(false);
            int inserts = statement.executeUpdate(formattedString, Statement.RETURN_GENERATED_KEYS); //insertOrder

            if(inserts == 1) { //'1' to say we added exactly one new id/row!
                var rs = statement.getGeneratedKeys();
                if(rs.next()) {
                    orderId = rs.getInt(1);
                }
            }

            int count = 0;
            for(var item : items) {
                formattedString = insertDetail.formatted(orderId,
                        statement.enquoteLiteral(item));
                inserts = statement.executeUpdate(formattedString);
                count += inserts;
            }
            if(count != items.length) {
                orderId = -1;
                System.out.println("Number of records inserted do not equal items received!");
                conn.rollback();
            } else {
                conn.commit();
            }
            conn.setAutoCommit(true);
        } catch(SQLException e) {
            conn.rollback();
            throw new RuntimeException(e);
        }

        return orderId;
    }

    public static void removeOrder(Connection conn, String[] items) throws SQLException {
        String deleteOrder = "DELETE FROM storefront.order WHERE order_id=%d";
        String findOrder = "SELECT order_id FROM storefront.order_details WHERE item_description=%s";

        try(Statement statement = conn.createStatement()) {
            conn.setAutoCommit(false);
            for(String item : items) {
                String formatted = findOrder.formatted(
                        statement.enquoteLiteral(item));
                statement.execute(formatted);
                ResultSet rs = statement.getResultSet();
                int id = -1;
                if(rs!=null && rs.next()) {
                    id = rs.getInt(1);
                }
                int changes = statement.executeUpdate(deleteOrder.formatted(id));
                if(changes == 1) {
                    System.out.printf("Delete order of order_id: %d!%n", id);
                } else {
                    System.out.printf("No changes found for item: %s%n", item);
                }
            }
            conn.commit();

        } catch(SQLException e) {
            conn.rollback();
            e.printStackTrace();
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public static void deleteOrder(Connection conn, int id) throws SQLException {
        String deleteOrder = "DELETE FROM %s WHERE order_id=%d";
        String parentQuery = deleteOrder.formatted("storefront.order", id);
        String childQuery = deleteOrder.formatted("storefront.order_details", id);

        try(Statement statement = conn.createStatement()) {
            conn.setAutoCommit(false);
            int changes = statement.executeUpdate(childQuery);
            System.out.printf("Removed %d records%n", changes);
            changes = statement.executeUpdate(parentQuery);
            if(changes==1) {
                conn.commit();
                System.out.println("Removed OrderID: " + id);
            } else {
                conn.rollback();
                System.out.println("Something went wrong with removing OrderID: "
                        + id);
            }
        } catch(SQLException e) {
            conn.rollback();
            e.printStackTrace();
        } finally {
            conn.setAutoCommit(true);
        }
    }

}
