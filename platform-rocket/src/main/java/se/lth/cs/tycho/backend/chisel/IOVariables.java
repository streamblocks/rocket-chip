package se.lth.cs.tycho.backend.chisel;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.backend.c.Backend;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.expr.ExprGlobalVariable;
import se.lth.cs.tycho.ir.expr.ExprInput;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.stmt.StmtWrite;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Module
public interface IOVariables {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Set<VarDecl> IOVariablesRead(IRNode node) {
        Set<VarDecl> read = new LinkedHashSet<>();
        node.forEachChild(child -> read.addAll(IOVariablesRead(child)));
        return read;
    }

    default Set<VarDecl> IOVariablesWrite(IRNode node) {
        Set<VarDecl> write = new LinkedHashSet<>();
        node.forEachChild(child -> write.addAll(IOVariablesWrite(child)));
        return write;
    }

    default Set<VarDecl> IOVariablesRead(ExprGlobalVariable expr) {
        return Collections.singleton(backend().varDecls().declaration(expr));
    }

    default Set<VarDecl> IOVariablesRead(ExprVariable expr) {
        VarDecl decl = backend().varDecls().declaration(expr);

        IRNode parent = backend().tree().parent(decl);
        if (parent instanceof Scope) {
            return Collections.singleton(decl);
        } else if (parent instanceof ActorMachine) {
            return Collections.singleton(decl);
        } else if (parent instanceof NamespaceDecl) {
            return Collections.singleton(decl);
        }

        return Collections.emptySet();
    }

    default Set<VarDecl> IOVariablesRead(VarDecl decl) {
        if (decl.getValue() != null)
            if (decl.getValue() instanceof ExprInput)
                return Collections.singleton(decl);

        return Collections.emptySet();
    }

    default Set<VarDecl> IOVariablesWrite(LValueVariable lval) {
        VarDecl decl = backend().varDecls().declaration(lval);

        IRNode parent = backend().tree().parent(decl);
        if (parent instanceof Scope) {
            return Collections.singleton(decl);
        } else if (parent instanceof ActorMachine) {
            return Collections.singleton(decl);
        } else if (parent instanceof NamespaceDecl) {
            return Collections.singleton(decl);
        }

        return Collections.emptySet();
    }

    default Set<VarDecl> IOVariablesWrite(StmtWrite write){
        Set<VarDecl> declSet = new LinkedHashSet<>();
        for (Expression expr: write.getValues()){
            if(expr instanceof ExprVariable)
                declSet.add(backend().varDecls().declaration((ExprVariable) expr));
        }

        return declSet;
    }
}
