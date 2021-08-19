package net.auoeke.tostring;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.gudenau.lib.unsafe.Unsafe;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import user11681.reflect.Fields;

public class ToStringGenerator implements Function<Object, Object> {
    public static final IdentityHashMap<Class<?>, Function<Object, String>> generators = new IdentityHashMap<>();

    private static final String PROPERTY = "toStringGenerator";

    // This field is used for keeping track of objects that are being stringified in order to avoid cyclic references.
    private static final ThreadLocal<Set<Object>> stringifying = ThreadLocal.withInitial(HashSet::new);

    public static void main(String... args) throws Throwable {
        // The bootstrap class loader does not recognize this class; so we abuse the system properties as a global object store and virtual dispatch by implementing Function.
        System.getProperties().put(PROPERTY, new ToStringGenerator());

        var instrumentation = ByteBuddyAgent.install();
        ObjectNodeTransformer transformer = node -> node.methods.stream().filter(method -> method.name.equals("toString")).findAny().ifPresent(toString -> {
            toString.instructions.clear();
            toString.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "getProperties", "()Ljava/util/Properties;", false);
            toString.visitLdcInsn(PROPERTY);
            toString.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Properties.class), "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
            toString.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Function.class));
            toString.visitVarInsn(Opcodes.ALOAD, 0);
            toString.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(Function.class), "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            toString.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(String.class));
            toString.visitInsn(Opcodes.ARETURN);
        });

        instrumentation.addTransformer(transformer, true);
        instrumentation.retransformClasses(Object.class);
        instrumentation.removeTransformer(transformer);

        System.out.println(new Object() {
            private final int i = 123;
            private final String string = "string";
            private final byte b = 0b01110011;
            private final Object dis = this;
            private final Object object = new Object();
            private final Object anonymous = new Object() {
                private final short s = (short) 0xFFFF;
                private final long time = System.nanoTime();
            };
            private final Object customToString = new Object() {
                @Override
                public String toString() {
                    return "stateless instance of an anonymous Object class";
                }
            };
        });
    }

    @Override
    public final Object apply(Object object) {
        var parents = stringifying.get();
        parents.add(object);

        var string = generators.computeIfAbsent(object.getClass(), type -> {
            var fields = Fields.allInstanceFields(type).toArray(Field[]::new);
            Function<Object, String> defaultString = object2 -> type.getName() + '@' + Integer.toHexString(object2.hashCode());

            return fields.length == 0 ? defaultString : object2 -> {
                var fieldString = new StringBuilder();

                for (var field : fields) {
                    try {
                        var value = Unsafe.trustedLookup.unreflectVarHandle(field).get(object2);
                        fieldString.append(field.getName()).append(": ").append(value == object2 ? "this" : parents.contains(value) ? defaultString.apply(value) : value).append(System.lineSeparator());
                    } catch (Throwable throwable) {
                        throw Unsafe.throwException(throwable);
                    }
                }

                return Stream.of(fieldString.toString().split(System.lineSeparator()))
                    .map(line -> "    " + line + System.lineSeparator())
                    .collect(Collectors.joining("", defaultString.apply(object2) + " {" + System.lineSeparator(), "}"));
            };
        }).apply(object);

        parents.remove(object);

        return string;
    }
}
