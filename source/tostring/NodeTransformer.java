package tostring;

import java.security.ProtectionDomain;
import net.auoeke.reflect.ClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

interface NodeTransformer extends ClassTransformer {
	@Override default byte[] transform(Module module, ClassLoader loader, String name, Class<?> type, ProtectionDomain protectionDomain, byte[] bytecode) {
		var node = new ClassNode();
		new ClassReader(bytecode).accept(node, 0);

		this.transform(node);

		var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		node.accept(writer);
		return writer.toByteArray();
	}

	void transform(ClassNode node);
}
