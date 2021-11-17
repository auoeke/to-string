import java.lang.annotation.RetentionPolicy;
import net.auoeke.tostring.ToStringGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.annotation.Testable;

@Testable
public class Tests {
    @BeforeAll
    static void before() {
        ToStringGenerator.initialize();
    }

    @Test
    void test() {
        //noinspection unused
        System.out.println(new Object() {
            private final int i = 123;
            private final String string = "sample text";
            private final byte b = 0b01110011;
            private final float[] array = {1.5F, 27, 49, 35.1F};
            private final Object dis = this;
            private final Object object = new Object();
            private final Object anonymous = new Object() {
                private final short s = (short) 0xFFFF;
                private final long time = System.nanoTime();
                private final RetentionPolicy policy = RetentionPolicy.RUNTIME;
            };
            private final Object customToString = new Object() {
                @Override
                public String toString() {
                    return "stateless instance of an anonymous Object class";
                }
            };
            private final Object[] objectArray = {this.dis, this.object, this.anonymous, this.customToString};
        });
    }
}
