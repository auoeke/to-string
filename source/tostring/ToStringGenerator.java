package tostring;

import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.auoeke.reflect.Accessor;
import net.auoeke.reflect.Fields;
import net.auoeke.reflect.Reflect;
import net.auoeke.reflect.Types;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class ToStringGenerator implements Function<Object, String> {
	private static final String PROPERTY = "toStringGenerator";

	private static final IdentityHashMap<Class<?>, Function<Object, String>> generators = new IdentityHashMap<>();

	// This field is used for keeping track of objects that are being stringified in order to avoid cyclic references.
	private static final ThreadLocal<Set<Object>> stringifying = ThreadLocal.withInitial(HashSet::new);

	public static void initialize() {
		// The bootstrap class loader can't find this class so we abuse the system properties as a global object store and virtual dispatch by implementing Function.
		System.getProperties().put(PROPERTY, new ToStringGenerator());

		var instrumentation = Reflect.instrument().value();
		instrumentation.addTransformer(((NodeTransformer) node -> node.methods.stream().filter(method -> method.name.equals("toString")).findAny().ifPresent(toString -> {
			toString.instructions.clear();
			toString.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "getProperties", "()Ljava/util/Properties;", false);
			toString.visitLdcInsn(PROPERTY);
			toString.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Properties.class), "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
			toString.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Function.class));
			toString.visitVarInsn(Opcodes.ALOAD, 0);
			toString.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(Function.class), "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
			toString.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(String.class));
			toString.visitInsn(Opcodes.ARETURN);
		})).ofType(Object.class).singleUse(instrumentation), true);
		instrumentation.retransformClasses(Object.class);
	}

	@Override public final String apply(Object object) {
		var parents = stringifying.get();
		parents.add(object);

		var string = generators.computeIfAbsent(object.getClass(), type -> {
			if (type.isArray()) {
				return array -> Stream.of(Types.box(array)).map(element -> objectString(parents, array, element)).collect(Collectors.joining(", ", defaultString(array) + " [", "]"));
			}

			var fields = Fields.allInstance(type).toList();

			return fields.isEmpty() ? ToStringGenerator::defaultString : object2 -> {
				var fieldString = new StringBuilder();

				for (var field : fields) {
					fieldString.append(field.getName()).append(": ").append(objectString(parents, object2, Accessor.get(object2, field))).append(System.lineSeparator());
				}

				return Stream.of(fieldString.toString().split(System.lineSeparator()))
					.map(line -> "    " + line + System.lineSeparator())
					.collect(Collectors.joining("", defaultString(object2) + " {" + System.lineSeparator(), "}"));
			};
		}).apply(object);

		parents.remove(object);

		return string;
	}

	private static String typeName(Class<?> type) {
		return type.getName().replaceFirst("^java\\.lang\\.", "");
	}

	private static String defaultString(Object object) {
		var type2 = object.getClass();
		return "%s@%x".formatted(type2.isArray() ? "%s[%d]".formatted(typeName(type2.componentType()), Array.getLength(object)) : typeName(type2), object.hashCode());
	}

	private static String objectString(Set<Object> parents, Object original, Object object) {
		return object == original ? "this" : parents.contains(object) ? defaultString(object) : String.valueOf(object);
	}
}
