package net.auoeke.tostring;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import net.gudenau.lib.unsafe.Unsafe;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public interface ObjectNodeTransformer extends ClassFileTransformer {
    @Override
    default byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain protectionDomain, byte[] bytecode) {
        if (type == Object.class) {
            var node = new ClassNode();
            new ClassReader(bytecode).accept(node, 0);

            try {
                this.transform(node);
            } catch (Throwable throwable) {
                throw Unsafe.throwException(throwable);
            }

            var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            node.accept(writer);

            return writer.toByteArray();
        }

        return bytecode;
    }

    void transform(ClassNode node) throws Throwable;
}
