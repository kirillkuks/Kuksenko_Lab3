import ru.spbstu.pipeline.RC;

public class Main {
    public static void main(String[] args) {
        if(args.length > 0) {
            Manager manager = new Manager(args[0]);
            RC rc = manager.work();
            if(rc == RC.CODE_SUCCESS) {
                System.out.println("OK!");
            } else {
                System.out.println("Error: " + rc);
            }
        }
    }
}
