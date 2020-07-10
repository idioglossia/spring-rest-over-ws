package labs.psychogen.row.annotations;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Publish {
    String value(); //event
    boolean async();
    String bean() default "";
}
