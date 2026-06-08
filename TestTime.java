import java.time.LocalTime;
public class TestTime {
    public static void main(String[] args) {
        LocalTime t1 = LocalTime.parse("00:00");
        System.out.println("t1 equals MIDNIGHT: " + t1.equals(LocalTime.MIDNIGHT));
        System.out.println("t1: " + t1);
    }
}
